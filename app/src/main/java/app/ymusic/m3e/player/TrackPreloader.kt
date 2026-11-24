package app.ymusic.m3e.player

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.ymusic.m3e.data.AppLog
import app.ymusic.m3e.data.MusicApi
import app.ymusic.m3e.data.ScIds
import app.ymusic.m3e.data.SoundCloudAuth
import app.ymusic.m3e.data.VkIds
import androidx.media3.datasource.HttpDataSource
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Предзагрузка следующих треков очереди (по умолчанию 3 вперёд).
 *
 * Что ждёт при переключении трека без предзагрузки: запрос ссылки у API (get-file-info / download-info, иногда несколько
 * запросов подряд) и начало скачивания звука. Здесь оба этапа делаются заранее:
 *  1. ссылка на поток запрашивается в фоне и запоминается (см. [streamUrl]) — плеер берёт её из памяти без сети;
 *  2. первые [PRELOAD_BYTES] байт звука кладутся в дисковый кэш (SimpleCache). Плеер читает эти байты с диска,
 *     а остальное докачивает из сети — звук начинается мгновенно.
 * Кэш общий для всех треков и ограничен по размеру (LRU), поэтому место не раздувается.
 */
object TrackPreloader {
    /** Сколько треков вперёд готовить. */
    const val AHEAD = 3

    /** Сколько байт начала трека класть в кэш: ≈ полминуты FLAC или несколько минут mp3. */
    private const val PRELOAD_BYTES = 5L * 1024 * 1024
    private const val CACHE_LIMIT_BYTES = 256L * 1024 * 1024
    // Ссылки на поток живут недолго (после этого сервер отвечает 403), поэтому держим их в памяти не дольше нескольких минут.
    private const val URL_TTL_MS = 3 * 60_000L

    private const val TAG = "Preload"

    private class Resolved(val url: String, val key: String, val at: Long)

    private val urls = ConcurrentHashMap<String, Resolved>()
    private val urlLocks = ConcurrentHashMap<String, Any>()

    @Volatile private var cache: SimpleCache? = null
    private val cacheLock = Any()

    private val generation = AtomicInteger(0)
    private val worker = Executors.newSingleThreadExecutor(ThreadFactory { r ->
        Thread(r, "track-preload").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    })

    @Volatile private var activeWriter: CacheWriter? = null

    /** Очищает дисковый кэш предзагруженного звука и забывает разрешённые ссылки. Скачанные песни не затрагиваются. */
    fun clearCache(ctx: Context) {
        generation.incrementAndGet()
        runCatching { activeWriter?.cancel() }
        urls.clear()
        runCatching {
            val c = cache(ctx)
            c.keys.toList().forEach { runCatching { c.removeResource(it) } }
        }
    }

    private fun cache(ctx: Context): SimpleCache {
        cache?.let { return it }
        synchronized(cacheLock) {
            cache?.let { return it }
            val dir = File(ctx.applicationContext.cacheDir, "audio_preload").apply { mkdirs() }
            val c = SimpleCache(dir, LeastRecentlyUsedCacheEvictor(CACHE_LIMIT_BYTES), StandaloneDatabaseProvider(ctx.applicationContext))
            cache = c
            return c
        }
    }

    /**
     * Ключ кэша = id трека + «отпечаток» пути ссылки. Если сервер вдруг отдаст другой поток (другой кодек или качество),
     * путь изменится и мы не склеим кусок одного файла с продолжением другого.
     */
    private fun stableKey(id: String, url: String): String {
        val u = Uri.parse(url)
        val segs = u.pathSegments
        // Старый формат ссылок: /get-mp3/<md5>/<ts>/<путь> — md5 и ts меняются при каждом запросе, их отбрасываем.
        val path = if (segs.size > 3 && segs[0] == "get-mp3") segs.drop(3) else segs
        return "ym:$id:" + path.joinToString("/").hashCode()
    }

    private fun resolve(api: MusicApi, id: String): Resolved {
        val now = System.currentTimeMillis()
        urls[id]?.let { if (now - it.at < URL_TTL_MS) return it }
        val lock = urlLocks.getOrPut(id) { Any() }
        synchronized(lock) {
            urls[id]?.let { if (System.currentTimeMillis() - it.at < URL_TTL_MS) return it }
            val url = api.streamUrl(id)
            // HLS (VK) — это плейлист и много сегментов, а не один файл: ключа кэша нет, сегменты кэшируются по ссылкам.
            val r = Resolved(url, if (VkIds.isVk(id) || ScIds.isSc(id)) "" else stableKey(id, url), System.currentTimeMillis())
            if (urls.size > 64) urls.entries.removeAll { now - it.value.at >= URL_TTL_MS }
            urls[id] = r
            return r
        }
    }

    /** Ссылка и ключ кэша для воспроизведения: из памяти, если трек уже готовили, иначе запрос к API. */
    fun streamUrl(api: MusicApi, id: String): Pair<String, String> {
        val r = resolve(api, id)
        return r.url to r.key
    }

    /** Забыть ссылку трека (например, после ошибки воспроизведения — вдруг протухла). */
    fun forget(id: String) {
        urls.remove(id)
    }

    /**
     * Готовит треки [ids] (в порядке воспроизведения). Предыдущая подготовка прерывается: очередь изменилась.
     * [skip] — треки, которые готовить не надо (скачанные и локальные файлы).
     */
    fun preload(ctx: Context, api: MusicApi, ids: List<String>, skip: (String) -> Boolean) {
        val gen = generation.incrementAndGet()
        runCatching { activeWriter?.cancel() }
        if (ids.isEmpty()) return
        worker.execute {
            for (id in ids) {
                if (gen != generation.get()) return@execute
                if (skip(id)) continue
                try {
                    val r = resolve(api, id)
                    if (gen != generation.get()) return@execute
                    // Для HLS заранее готовится только ссылка: начало «файла» на диск не кладётся.
                    if (r.key.isEmpty()) continue
                    val c = cache(ctx)
                    if (c.isCached(r.key, 0, PRELOAD_BYTES)) continue
                    val upstream = cachedFactory(ctx, plainFactory = null).createDataSourceForDownloading()
                    val spec = DataSpec.Builder().setUri(r.url).setKey(r.key).setPosition(0).setLength(PRELOAD_BYTES).build()
                    val writer = CacheWriter(upstream, spec, null, null)
                    activeWriter = writer
                    try {
                        writer.cache()
                    } finally {
                        if (activeWriter === writer) activeWriter = null
                    }
                } catch (e: Exception) {
                    // Отмена, обрыв сети, 403 — не страшно: трек просто загрузится обычным путём при переключении.
                    if (gen == generation.get()) AppLog.w(TAG, "трек $id: ${e.javaClass.simpleName}: ${e.message}")
                    urls.remove(id)
                }
            }
        }
    }

    /** Остановить подготовку (например, очередь очищена). */
    fun cancel() {
        generation.incrementAndGet()
        runCatching { activeWriter?.cancel() }
    }

    /** Токен SoundCloud для запросов плеера (ставится из App). */
    @Volatile var scAuth: (() -> String?)? = null

    /**
     * Запросы плеера к SoundCloud: «браузерный» User-Agent (со служебным SoundCloud отвечает ошибкой) и, для api.soundcloud.com,
     * заголовок Authorization — без него плейлист HLS отдаётся с кодом 429. На другие сервисы это не влияет.
     */
    private fun withSoundCloudHeaders(spec: DataSpec): DataSpec {
        val host = spec.uri.host?.lowercase() ?: return spec
        val isApi = host == "api.soundcloud.com" || host == "api-v2.soundcloud.com"
        val isSc = isApi || host == "soundcloud.com" || host.endsWith(".soundcloud.com") || host.endsWith(".sndcdn.com") || host.endsWith(".soundcloud.cloud")
        if (!isSc) return spec
        val h = HashMap<String, String>()
        h["User-Agent"] = SoundCloudAuth.UA
        if (isApi) runCatching { scAuth?.invoke() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { h["Authorization"] = "OAuth $it" }
        return spec.withAdditionalHeaders(h)
    }

    @Volatile private var upstreamFactory: DataSource.Factory? = null

    /** Фабрика источника, который пишет и читает кэш. [plainFactory] нужен только при первом вызове (из App). */
    private fun cachedFactory(ctx: Context, plainFactory: DataSource.Factory?): CacheDataSource.Factory {
        if (plainFactory != null) upstreamFactory = plainFactory
        val up = upstreamFactory ?: throw IllegalStateException("предзагрузка не настроена")
        return CacheDataSource.Factory()
            .setCache(cache(ctx))
            .setUpstreamDataSourceFactory(up)
            .setCacheKeyFactory { spec -> spec.key ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Фабрика источника данных для плеера: http(s) идёт через кэш предзагрузки, а скачанные файлы (file://, content://)
     * читаются напрямую — их незачем дублировать в кэше.
     */
    fun playerDataSourceFactory(ctx: Context, plain: DataSource.Factory): DataSource.Factory {
        val cached = cachedFactory(ctx, plain)
        return DataSource.Factory { RoutingDataSource(cached.createDataSource(), plain.createDataSource()) }
    }

    /**
     * Защита от «протухшей» ссылки. Плеер может открыть поток заново в любой момент трека — когда кончились байты в дисковом
     * кэше, оборвалось соединение или сменилась сеть. Ссылка при этом берётся из памяти ([streamUrl]) и к этому времени уже
     * могла устареть: сервер отвечает 403, и раньше трек из-за этого пропускался.
     *
     * Эта обёртка стоит над ResolvingDataSource. Получив 401/403/410 на трек из очереди (ymusic://), она забывает старую
     * ссылку, просит у API новую и открывает поток заново с того же байта — плеер ничего не замечает.
     */
    class ExpiredLinkRecoveringDataSource(private val inner: DataSource) : DataSource {
        private var spec: DataSpec? = null
        private var consumed = 0L
        private var recoveries = 0

        override fun addTransferListener(transferListener: TransferListener) = inner.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            spec = dataSpec
            consumed = 0L
            recoveries = 0
            return try {
                inner.open(dataSpec)
            } catch (e: IOException) {
                if (!canRecover(dataSpec, e)) throw e
                reopen(dataSpec, e)
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            try {
                val n = inner.read(buffer, offset, length)
                if (n > 0) consumed += n
                return n
            } catch (e: IOException) {
                val s = spec ?: throw e
                if (!canRecover(s, e)) throw e
                reopen(s.subrange(consumed), e)
                val n = inner.read(buffer, offset, length)
                if (n > 0) consumed += n
                return n
            }
        }

        override fun getUri(): Uri? = inner.uri

        override fun getResponseHeaders(): Map<String, List<String>> = inner.responseHeaders

        override fun close() {
            try { inner.close() } finally { spec = null }
        }

        private fun canRecover(s: DataSpec, e: IOException): Boolean =
            s.uri.scheme == "ymusic" && recoveries < MAX_RECOVERIES && expiredCode(e) != 0

        private fun reopen(s: DataSpec, cause: IOException): Long {
            recoveries++
            val id = s.uri.lastPathSegment.orEmpty()
            AppLog.w(TAG, "трек $id: ссылка устарела (HTTP ${expiredCode(cause)}), запрашиваю новую и продолжаю с байта $consumed")
            forget(id)
            runCatching { inner.close() }
            return inner.open(s)
        }

        private fun expiredCode(e: Throwable?): Int {
            var c = e
            while (c != null) {
                if (c is HttpDataSource.InvalidResponseCodeException && (c.responseCode == 401 || c.responseCode == 403 || c.responseCode == 410)) {
                    return c.responseCode
                }
                c = c.cause
            }
            return 0
        }

        private companion object {
            const val MAX_RECOVERIES = 2
        }
    }

    private class RoutingDataSource(private val cached: DataSource, private val plain: DataSource) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            cached.addTransferListener(transferListener)
            plain.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme
            val web = scheme == "http" || scheme == "https"
            val d = if (web) cached else plain
            active = d
            return d.open(if (web) withSoundCloudHeaders(dataSpec) else dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            (active ?: throw IllegalStateException("источник не открыт")).read(buffer, offset, length)

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            try { active?.close() } finally { active = null }
        }
    }
}
