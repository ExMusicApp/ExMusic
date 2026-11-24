package app.ymusic.m3e.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * Скачивание у SoundCloud доступно, только если его разрешил автор трека. true — автор запретил скачивание
 * (флаг приходит вместе с данными трека; пока он неизвестен, скачивание пробуется, а решение принимает SoundCloud).
 */
fun Track.isDownloadBlocked(): Boolean = ScIds.isSc(id) && ScDownloads.isForbidden(id)

/** Трек можно пробовать скачать: не файл с устройства и автор не запретил скачивание. */
fun Track.canDownload(): Boolean = !isLocal && !isDownloadBlocked()

/**
 * Куда сохранять скачанную песню.
 * [Files] — обычный файл в папке скачивания (по умолчанию Download/ExMusic), его видно в файловом менеджере и других плеерах.
 * [App] — «как в Яндекс Музыке»: песня лежит в закрытой папке приложения, слушать её можно только в ExMusic.
 * [Both] — и файл, и копия внутри приложения.
 */
enum class DownloadMode(val title: String, val hint: String) {
    Files("Файлами на устройство", "Файл в папке скачивания — виден в проводнике и других плеерах"),
    App("Только в приложении", "Как в Яндекс Музыке: слушать можно только в ExMusic, файлов в памяти не видно"),
    Both("Файлом и в приложении", "И файл в папке скачивания, и копия внутри приложения");

    val toFiles: Boolean get() = this != App
    val toApp: Boolean get() = this != Files
}

data class DownloadedTrack(
    val id: String,
    val title: String,
    val artists: String,
    val album: String?,
    /** content://- или file://-адрес файла в папке скачивания; пусто — файла нет (трек только в приложении). */
    val uri: String,
    val size: Long,
    val ts: Long,
    /** Путь к копии в закрытой папке приложения; null — копии нет (трек только файлом). */
    val appPath: String? = null,
    /** Расширение файла: mp3, flac, m4a. */
    val ext: String = "mp3",
    /** Полные данные трека — по ним список скачанного открывается без интернета. */
    val meta: Track? = null,
) {
    val inFiles: Boolean get() = uri.isNotBlank()
    val inApp: Boolean get() = appPath != null

    fun toTrack(): Track = meta ?: Track(
        id = id, albumId = null, title = title, artists = artists, coverUri = null, durationMs = 0, albumTitle = album,
    )
}

/** По какой сети разрешено скачивать песни. */
enum class DownloadNetwork(val title: String) {
    Wifi("Только Wi-Fi"),
    Mobile("Только мобильная сеть"),
    Any("Wi-Fi и мобильная сеть"),
}

/** Идущее сейчас массовое скачивание (лайки, плейлист или альбом). */
data class BulkDownload(val name: String, val done: Int, val total: Int)

data class DownloadsState(
    val items: List<DownloadedTrack> = emptyList(),
    /** Скачивающиеся сейчас: id трека → доля 0..1 (-1, если размер неизвестен). */
    val progress: Map<String, Float> = emptyMap(),
    /** Папка в понятном виде, например «Download/ExMusic». */
    val dirLabel: String = DEFAULT_LABEL,
    val isDefault: Boolean = true,
    val network: DownloadNetwork = DownloadNetwork.Any,
    /** Массовое скачивание «Любимого», плейлиста или альбома; null — не идёт. */
    val bulk: BulkDownload? = null,
    /** Куда сохранять по умолчанию (отдельные треки и выбор в окне «Скачать»). */
    val mode: DownloadMode = DownloadMode.Files,
    /** Спрашивать ли, куда сохранять, при скачивании «Любимого», плейлиста или альбома. false — сразу в [mode]. */
    val askMode: Boolean = true,
    /** Ключи списков с включённым автоскачиванием ([AUTO_LIKED] или playlistSourceKey). По умолчанию пусто — всё выключено. */
    val auto: Set<String> = emptySet(),
) {
    val totalBytes: Long get() = items.sumOf { it.size }

    companion object {
        const val DEFAULT_LABEL = "Download/ExMusic"
        /** Ключ автоскачивания для «Любимого». */
        const val AUTO_LIKED = "liked"
    }
}

/**
 * Скачивание песен: файлом в «Загрузки/ExMusic» (MediaStore, без разрешений) или в выбранную папку, и/или в закрытую
 * папку приложения (слушать можно только в ExMusic). Индекс скачанного хранится в SharedPreferences.
 */
class DownloadStore(private val ctx: Context, private val api: MusicApi) {
    private val prefs = ctx.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val http = ScProxy.applyTo(OkHttpClient.Builder()).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val resolver get() = ctx.contentResolver

    private val _state = MutableStateFlow(
        DownloadsState(items = readItems(), dirLabel = dirLabel(), isDefault = treeUri() == null, network = readNetwork(), mode = readMode(), askMode = prefs.getBoolean("askMode", true), auto = readAuto())
    )
    val state: StateFlow<DownloadsState> = _state

    init { scope.launch { verify() } }

    // ---------- Индекс ----------

    private fun readItems(): List<DownloadedTrack> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            DownloadedTrack(
                id = o.getString("id"), title = o.optString("title"), artists = o.optString("artists"),
                album = o.optString("album").ifBlank { null }, uri = o.optString("uri"),
                size = o.optLong("size"), ts = o.optLong("ts"),
                appPath = o.optString("appPath").ifBlank { null },
                ext = o.optString("ext").ifBlank { "mp3" },
                meta = o.optJSONObject("track")?.let { runCatching { trackFromJson(it) }.getOrNull() },
            )
        }
    }.getOrDefault(emptyList())

    private fun writeItems(list: List<DownloadedTrack>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("artists", it.artists)
                    .put("album", it.album ?: "").put("uri", it.uri).put("size", it.size).put("ts", it.ts)
                    .put("appPath", it.appPath ?: "").put("ext", it.ext)
                    .also { o -> it.meta?.let { m -> o.put("track", m.toJson()) } },
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun commit(update: (List<DownloadedTrack>) -> List<DownloadedTrack>) {
        _state.update { s ->
            val next = update(s.items)
            writeItems(next)
            s.copy(items = next)
        }
    }

    /**
     * Приводит запись в порядок: убирает ссылки на файлы, которых больше нет; запись без единого файла пропадает.
     * Файл считается пропавшим только когда это точно известно ([isGone]): если проверить его не удалось (нет доступа
     * к папке, провайдер файлов не ответил, сразу после запуска), запись остаётся. Раньше такая запись удалялась навсегда,
     * и в списке оставались единицы треков из сотен скачанных.
     */
    private fun fix(d: DownloadedTrack): DownloadedTrack? {
        val app = d.appPath?.takeIf { File(it).exists() }
        val files = if (d.inFiles && !isGone(Uri.parse(d.uri))) d.uri else ""
        return when {
            app == null && files.isEmpty() -> null
            app == d.appPath && files == d.uri -> d
            else -> d.copy(appPath = app, uri = files)
        }
    }

    /** Убирает записи и части записей, чьи файлы удалили вручную. */
    private fun verify() {
        // Файлы проверяются вне блокировки состояния: проверка сотен адресов не должна держать общий update.
        val checked = _state.value.items.associate { it.id to (it to fix(it)) }
        // Запись, изменившаяся за время проверки (трек успели перекачать), остаётся как есть.
        commit { cur ->
            cur.mapNotNull { d ->
                val c = checked[d.id]
                if (c != null && c.first == d) c.second else d
            }
        }
    }

    /** true — файл точно удалён; при любом сомнении (нет доступа, ошибка провайдера) false. */
    private fun isGone(u: Uri): Boolean {
        if (u.scheme == "file") return !File(u.path ?: return false).exists()
        return try {
            resolver.openAssetFileDescriptor(u, "r")?.close()
            false
        } catch (_: java.io.FileNotFoundException) {
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun exists(u: Uri): Boolean = !isGone(u)

    // ---------- Закрытая папка приложения ----------

    private val appDir: File get() = File(ctx.filesDir, "offline").apply { mkdirs() }

    /** Адрес, откуда плеер берёт скачанный трек (копия в приложении важнее файла); null — трек не скачан. */
    fun playableUri(id: String): Uri? {
        val d = _state.value.items.firstOrNull { it.id == id } ?: return null
        d.appPath?.let { p -> File(p).takeIf { it.exists() }?.let { return Uri.fromFile(it) } }
        return if (d.inFiles) Uri.parse(d.uri) else null
    }

    fun isDownloaded(id: String): Boolean = _state.value.items.any { it.id == id }

    // ---------- Режим по умолчанию ----------

    private fun readMode(): DownloadMode =
        DownloadMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: DownloadMode.Files

    fun setMode(m: DownloadMode) {
        prefs.edit().putString("mode", m.name).apply()
        _state.update { it.copy(mode = m) }
    }

    /** Показывать ли окно «Куда сохранять?» при скачивании целого списка. */
    fun setAskMode(ask: Boolean) {
        prefs.edit().putBoolean("askMode", ask).apply()
        _state.update { it.copy(askMode = ask) }
    }

    /** «Не показывать снова»: выбранный способ становится способом по умолчанию, и окно больше не появляется. */
    fun setModeAndStopAsking(m: DownloadMode) {
        prefs.edit().putString("mode", m.name).putBoolean("askMode", false).apply()
        _state.update { it.copy(mode = m, askMode = false) }
    }

    // ---------- Автоскачивание ----------

    private fun readAuto(): Set<String> = prefs.getStringSet("auto", emptySet())?.toSet() ?: emptySet()

    fun isAuto(key: String): Boolean = key in _state.value.auto

    /** Включает/выключает автоскачивание списка. По умолчанию для всех списков выключено. */
    fun setAuto(key: String, on: Boolean) {
        val next = _state.value.auto.let { if (on) it + key else it - key }
        prefs.edit().putStringSet("auto", next).apply()
        _state.update { it.copy(auto = next) }
        if (!on) synchronized(pendingAuto) { pendingAuto.remove(key) }
    }

    private class PendingAuto(val name: String, val tracks: List<Track>, val onFinish: (Int, Int, String?) -> Unit)
    private val pendingAuto = LinkedHashMap<String, PendingAuto>()

    /**
     * Тихо докачивает недостающие треки списка, если для него включено автоскачивание. Если сейчас уже идёт другое
     * массовое скачивание, список встаёт в очередь и докачивается после него.
     */
    fun autoSync(key: String, name: String, tracks: List<Track>, onFinish: (done: Int, failed: Int, stopReason: String?) -> Unit) {
        if (!isAuto(key)) return
        val m = _state.value.mode
        if (tracks.none { it.canDownload() && needs(it, m) }) return
        if (bulkJob?.isActive == true) {
            synchronized(pendingAuto) { pendingAuto[key] = PendingAuto(name, tracks, onFinish) }
            return
        }
        downloadAll(name, tracks, m, onFinish)
    }

    private fun runPendingAuto() {
        val next = synchronized(pendingAuto) {
            val e = pendingAuto.entries.firstOrNull() ?: return
            pendingAuto.remove(e.key)
            e.key to e.value
        }
        autoSync(next.first, next.second.name, next.second.tracks, next.second.onFinish)
    }

    // ---------- Папка ----------

    private fun treeUri(): Uri? = prefs.getString("tree", null)?.let(Uri::parse)

    private fun dirLabel(): String {
        val t = treeUri() ?: return DownloadsState.DEFAULT_LABEL
        val id = runCatching { DocumentsContract.getTreeDocumentId(t) }.getOrNull() ?: return t.toString()
        // «primary:Music/ExMusic» → «Music/ExMusic»
        return id.substringAfter(':', id).ifBlank { "Память устройства" }
    }

    fun setTree(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        prefs.edit().putString("tree", uri.toString()).apply()
        _state.update { it.copy(dirLabel = dirLabel(), isDefault = false) }
    }

    fun resetTree() {
        prefs.edit().remove("tree").apply()
        _state.update { it.copy(dirLabel = dirLabel(), isDefault = true) }
    }

    // ---------- Сеть ----------

    private fun readNetwork(): DownloadNetwork =
        DownloadNetwork.entries.firstOrNull { it.name == prefs.getString("network", null) } ?: DownloadNetwork.Any

    fun setNetwork(m: DownloadNetwork) {
        prefs.edit().putString("network", m.name).apply()
        _state.update { it.copy(network = m) }
    }

    /** null — скачивать можно; иначе причина, по которой нельзя (показывается пользователю). */
    fun networkBlockReason(): String? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return "Нет подключения к интернету"
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return when (_state.value.network) {
            DownloadNetwork.Any -> null
            DownloadNetwork.Wifi -> if (wifi) null else "Скачивание разрешено только по Wi-Fi (настройка «Скачивание песен»)"
            DownloadNetwork.Mobile -> if (mobile && !wifi) null else "Скачивание разрешено только по мобильной сети (настройка «Скачивание песен»)"
        }
    }

    // ---------- Скачивание ----------

    private class Target(val uri: Uri, val out: OutputStream, val finish: () -> Unit)

    private fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").replace(Regex("\\s+"), " ").trim().trimEnd('.', ' ')

    private fun openTarget(name: String, mime: String): Target {
        val tree = treeUri()
        if (tree != null) {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(resolver, parent, mime, name)
                ?: error("Не удалось создать файл в выбранной папке")
            val out = resolver.openOutputStream(doc) ?: error("Не удалось открыть файл для записи")
            return Target(doc, out) {}
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ExMusic")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: error("Не удалось создать файл в «Загрузках»")
            val out = resolver.openOutputStream(uri) ?: error("Не удалось открыть файл для записи")
            return Target(uri, out) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
        }
        // Android 8–9: без разрешения на память — папка приложения
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: ctx.filesDir, "ExMusic").apply { mkdirs() }
        var f = File(dir, name)
        var n = 1
        while (f.exists()) f = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.')}".also { n++ })
        return Target(Uri.fromFile(f), f.outputStream()) {}
    }

    private fun deleteUri(u: Uri) {
        runCatching {
            when {
                u.scheme == "file" -> File(u.path ?: return).delete()
                DocumentsContract.isDocumentUri(ctx, u) -> DocumentsContract.deleteDocument(resolver, u)
                else -> resolver.delete(u, null, null)
            }
        }
    }

    private fun mimeOf(ext: String) = when (ext) { "flac" -> "audio/flac"; "m4a" -> "audio/mp4"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"; else -> "audio/mpeg" }

    /** Обложка для тегов: сначала 1000×1000, потом поменьше; без сети и при ошибке — null (теги всё равно запишутся). */
    private fun fetchCover(t: Track): ByteArray? {
        if (t.coverUri == null) return null
        val client = http.newBuilder().callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
        for (size in intArrayOf(1000, 600, 400)) {
            val url = t.cover(size) ?: continue
            val bytes = runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.bytes()
                }
            }.getOrNull()
            if (bytes != null && bytes.size > 1024 && bytes.size < 8 * 1024 * 1024) return bytes
        }
        return null
    }

    /** Копирует скачанный файл в папку скачивания, записывая теги и обложку. Возвращает адрес нового файла. */
    private fun exportFile(src: File, t: Track, ext: String): Uri {
        val name = (safeName("${t.artists} - ${t.title}").take(120).ifBlank { "track-${t.id}" }) + ".$ext"
        val cover = fetchCover(t)
        val tg = openTarget(name, mimeOf(ext))
        try {
            tg.out.use { out ->
                // Теги и обложка пишутся для mp3, flac и m4a; при ошибке разбора файл копируется как есть.
                AudioTagger.write(src, out, ext, t, cover)
            }
            tg.finish()
        } catch (e: Throwable) {
            deleteUri(tg.uri)
            throw e
        }
        return tg.uri
    }

    /** Скачивает трек в фоне. [mode] — куда сохранить (null — по умолчанию из настроек). [onDone] вызывается из фонового потока. */
    fun download(t: Track, mode: DownloadMode? = null, onDone: (Result<Unit>) -> Unit) {
        if (t.isLocal || t.isDownloadBlocked() || _state.value.progress.containsKey(t.id)) return
        scope.launch { onDone(downloadSuspend(t, mode ?: _state.value.mode)) }
    }

    /** Нужно ли что-то делать с треком, чтобы он лежал там, куда просит [mode]. */
    private fun needs(t: Track, mode: DownloadMode): Boolean {
        val d = _state.value.items.firstOrNull { it.id == t.id } ?: return true
        return (mode.toApp && !d.inApp) || (mode.toFiles && !d.inFiles)
    }

    /**
     * Дополняет уже скачанный трек недостающей частью (из файла в приложение или наоборот) без интернета.
     * false — исходных файлов не осталось, нужно качать заново.
     */
    private fun completeParts(d: DownloadedTrack, t: Track, mode: DownloadMode): Boolean {
        val appFile = d.appPath?.let(::File)?.takeIf { it.exists() }
        val filesOk = d.inFiles && exists(Uri.parse(d.uri))
        if (appFile == null && !filesOk) {
            commit { cur -> cur.filter { it.id != d.id } }
            return false
        }
        var cur = d
        if (mode.toApp && appFile == null) {
            val dest = File(appDir, "${t.id}.${d.ext}")
            val ok = resolver.openInputStream(Uri.parse(d.uri))?.use { input ->
                dest.outputStream().use { input.copyTo(it, 64 * 1024) }
                true
            } ?: false
            if (!ok) { dest.delete(); return false }
            cur = cur.copy(appPath = dest.path)
        }
        if (mode.toFiles && !filesOk) {
            val src = appFile ?: cur.appPath?.let(::File) ?: return false
            cur = cur.copy(uri = exportFile(src, t, d.ext).toString())
        }
        if (cur != d) commit { list -> list.map { if (it.id == d.id) cur else it } }
        return true
    }

    /** Скачивает один трек и ждёт окончания. Учитывает настройку сети. */
    private suspend fun downloadSuspend(t: Track, mode: DownloadMode): Result<Unit> {
        if (t.isLocal) return Result.success(Unit)
        if (t.isDownloadBlocked()) return Result.failure(IllegalStateException(ScDownloads.FORBIDDEN))
        var started = false
        _state.update {
            if (it.progress.containsKey(t.id)) { started = false; it }
            else { started = true; it.copy(progress = it.progress + (t.id to -1f)) }
        }
        if (!started) return Result.success(Unit)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val existing = _state.value.items.firstOrNull { it.id == t.id }
                if (existing != null && completeParts(existing, t, mode)) return@runCatching
                networkBlockReason()?.let { error(it) }
                downloadFresh(t, mode)
            }
        }
        _state.update { it.copy(progress = it.progress - t.id) }
        val err = result.exceptionOrNull()
        if (err is CancellationException) throw err
        return result
    }

    private suspend fun downloadFresh(t: Track, mode: DownloadMode) {
        val tmp = File(appDir, "${t.id}.part")
        var dest: File? = null
        try {
            // VK отдаёт HLS с шифрованием: он скачивается и собирается в обычный файл отдельным путём (см. VkHls).
            val job = kotlin.coroutines.coroutineContext[Job]
            val customExt = if (VkIds.isVk(t.id) || ScIds.isSc(t.id)) {
                api.downloadCustom(
                    t.id, tmp,
                    { f -> _state.update { it.copy(progress = it.progress + (t.id to f)) } },
                    { job?.ensureActive() },
                )
            } else null
            val ext = customExt ?: http.newCall(Request.Builder().url(api.downloadUrl(t.id)).build()).execute().use { r ->
                if (!r.isSuccessful) error("Сервер вернул HTTP ${r.code}")
                val body = r.body ?: error("Пустой ответ сервера")
                val ctype = r.header("Content-Type").orEmpty()
                val kind = when {
                    ctype.contains("flac") -> "flac"
                    ctype.contains("mp4") || ctype.contains("aac") || ctype.contains("m4a") -> "m4a"
                    else -> "mp3"
                }
                val total = body.contentLength()
                var written = 0L
                var lastEmit = 0L
                tmp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            // Отмена массового скачивания обрывает загрузку на следующем куске.
                            kotlin.coroutines.coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                            val now = System.currentTimeMillis()
                            if (total > 0 && now - lastEmit > 200) {
                                lastEmit = now
                                val f = (written.toFloat() / total).coerceIn(0f, 1f)
                                _state.update { it.copy(progress = it.progress + (t.id to f)) }
                            }
                        }
                    }
                }
                kind
            }
            var src = tmp
            if (mode.toApp) {
                val d = File(appDir, "${t.id}.$ext")
                dest = d
                d.delete()
                if (!tmp.renameTo(d)) { tmp.copyTo(d, overwrite = true); tmp.delete() }
                src = d
            }
            val fileUri = if (mode.toFiles) exportFile(src, t, ext).toString() else ""
            val size = src.length()
            if (!mode.toApp) tmp.delete()
            commit { cur ->
                cur.filter { it.id != t.id } + DownloadedTrack(
                    id = t.id, title = t.title, artists = t.artists, album = t.albumTitle, uri = fileUri,
                    size = size, ts = System.currentTimeMillis(),
                    appPath = if (mode.toApp) dest?.path else null, ext = ext, meta = t,
                )
            }
        } catch (e: Throwable) {
            tmp.delete()
            dest?.delete()
            throw e
        }
    }

    // ---------- Массовое скачивание ----------

    private var bulkJob: Job? = null

    /**
     * Скачивает все ещё не скачанные треки по очереди (лайки, плейлист или альбом) в выбранный [mode]; уже скачанные,
     * но не туда, докладываются без интернета. Останавливается, если сеть перестала подходить под настройку.
     * [onFinish]: скачано, ошибок, причина остановки.
     */
    fun downloadAll(name: String, tracks: List<Track>, mode: DownloadMode? = null, onFinish: (done: Int, failed: Int, stopReason: String?) -> Unit) {
        if (bulkJob?.isActive == true) { onFinish(0, 0, "уже идёт другое скачивание"); return }
        val m = mode ?: _state.value.mode
        val todo = tracks.filter { it.canDownload() }.distinctBy { it.id }.filter { needs(it, m) }
        if (todo.isEmpty()) { onFinish(0, 0, null); return }
        _state.update { it.copy(bulk = BulkDownload(name, 0, todo.size)) }
        bulkJob = scope.launch {
            var ok = 0
            var failed = 0
            var reason: String? = null
            try {
                for (t in todo) {
                    ensureActive()
                    // Сеть нужна только для настоящего скачивания, а не для переноса уже скачанного файла.
                    val blocked = if (_state.value.items.any { it.id == t.id }) null else networkBlockReason()
                    if (blocked != null) { reason = blocked; break }
                    val r = downloadSuspend(t, m)
                    if (r.isSuccess) ok++ else {
                        failed++
                        if (ok == 0 && failed >= 3) { reason = r.exceptionOrNull()?.message; break }
                    }
                    _state.update { it.copy(bulk = it.bulk?.copy(done = ok + failed)) }
                }
            } finally {
                _state.update { it.copy(bulk = null) }
            }
            onFinish(ok, failed, reason)
        }
        // Когда задача уже завершена, берём из очереди следующий список с автоскачиванием.
        bulkJob?.invokeOnCompletion { runPendingAuto() }
    }

    fun cancelBulk() {
        synchronized(pendingAuto) { pendingAuto.clear() }
        bulkJob?.cancel()
    }

    private fun deleteEntryFiles(d: DownloadedTrack) {
        if (d.inFiles) deleteUri(Uri.parse(d.uri))
        d.appPath?.let { runCatching { File(it).delete() } }
    }

    fun remove(id: String) {
        scope.launch {
            _state.value.items.firstOrNull { it.id == id }?.let(::deleteEntryFiles)
            commit { cur -> cur.filter { it.id != id } }
        }
    }

    fun removeAll() {
        scope.launch {
            _state.value.items.forEach(::deleteEntryFiles)
            commit { emptyList() }
            runCatching { appDir.listFiles()?.forEach { it.delete() } }
        }
    }
}
