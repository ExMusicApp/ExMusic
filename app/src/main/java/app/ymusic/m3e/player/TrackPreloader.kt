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
import app.ymusic.m3e.data.YandexMusicApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

object TrackPreloader {
    const val AHEAD = 3

    private const val PRELOAD_BYTES = 5L * 1024 * 1024
    private const val CACHE_LIMIT_BYTES = 256L * 1024 * 1024
    private const val URL_TTL_MS = 10 * 60_000L

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

    private fun stableKey(id: String, url: String): String {
        val u = Uri.parse(url)
        val segs = u.pathSegments
        val path = if (segs.size > 3 && segs[0] == "get-mp3") segs.drop(3) else segs
        return "ym:$id:" + path.joinToString("/").hashCode()
    }

    private fun resolve(api: YandexMusicApi, id: String): Resolved {
        val now = System.currentTimeMillis()
        urls[id]?.let { if (now - it.at < URL_TTL_MS) return it }
        val lock = urlLocks.getOrPut(id) { Any() }
        synchronized(lock) {
            urls[id]?.let { if (System.currentTimeMillis() - it.at < URL_TTL_MS) return it }
            val url = api.streamUrl(id)
            val r = Resolved(url, stableKey(id, url), System.currentTimeMillis())
            if (urls.size > 64) urls.entries.removeAll { now - it.value.at >= URL_TTL_MS }
            urls[id] = r
            return r
        }
    }

    fun streamUrl(api: YandexMusicApi, id: String): Pair<String, String> {
        val r = resolve(api, id)
        return r.url to r.key
    }

    fun forget(id: String) {
        urls.remove(id)
    }

    fun preload(ctx: Context, api: YandexMusicApi, ids: List<String>, skip: (String) -> Boolean) {
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
                    if (gen == generation.get()) AppLog.w(TAG, "трек $id: ${e.javaClass.simpleName}: ${e.message}")
                    urls.remove(id)
                }
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        runCatching { activeWriter?.cancel() }
    }

    @Volatile private var upstreamFactory: DataSource.Factory? = null

    private fun cachedFactory(ctx: Context, plainFactory: DataSource.Factory?): CacheDataSource.Factory {
        if (plainFactory != null) upstreamFactory = plainFactory
        val up = upstreamFactory ?: throw IllegalStateException("предзагрузка не настроена")
        return CacheDataSource.Factory()
            .setCache(cache(ctx))
            .setUpstreamDataSourceFactory(up)
            .setCacheKeyFactory { spec -> spec.key ?: spec.uri.toString() }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun playerDataSourceFactory(ctx: Context, plain: DataSource.Factory): DataSource.Factory {
        val cached = cachedFactory(ctx, plain)
        return DataSource.Factory { RoutingDataSource(cached.createDataSource(), plain.createDataSource()) }
    }

    private class RoutingDataSource(private val cached: DataSource, private val plain: DataSource) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            cached.addTransferListener(transferListener)
            plain.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val scheme = dataSpec.uri.scheme
            val d = if (scheme == "http" || scheme == "https") cached else plain
            active = d
            return d.open(dataSpec)
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
