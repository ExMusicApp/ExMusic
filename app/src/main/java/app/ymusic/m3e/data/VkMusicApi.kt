package app.ymusic.m3e.data

import android.content.SharedPreferences
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Ключи доступа VK (access_key) к трекам и плейлистам: без них чужие и групповые материалы не открываются. */
object VkKeys {
    private val tracks = ConcurrentHashMap<String, String>()
    private val playlists = ConcurrentHashMap<String, String>()

    /** Как выглядит трек (исполнитель, название, секунды): по этим данным трек находят поиском, если его копия по id недоступна. */
    class Meta(val artist: String, val title: String, val sec: Long)

    private val metas = ConcurrentHashMap<String, Meta>()

    fun rememberTrack(id: String, key: String?) { if (!key.isNullOrBlank()) tracks[id] = key }

    /** Запоминает ключ доступа и описание трека VK (вызывается и при чтении истории / очереди с диска — после перезапуска). */
    fun remember(t: Track) {
        if (!VkIds.isVk(t.id)) return
        rememberTrack(t.id, t.accessKey)
        if (t.title.isNotBlank()) metas[t.id] = Meta(t.artists, t.title, t.durationMs / 1000)
    }

    fun meta(id: String): Meta? = metas[id]
    fun track(id: String): String? = tracks[id]
    fun rememberPlaylist(owner: String, kind: Int, key: String?) { if (!key.isNullOrBlank()) playlists["$owner:$kind"] = key }
    fun playlist(owner: String, kind: Int): String? = playlists["$owner:$kind"]
}

/**
 * VK Музыка через неофициальный API (методы audio.*, catalog.*) с токеном «официального» клиента VK.
 *
 * Соответствие понятий приложения и VK:
 *  - «Любимое» — «Моя музыка» (audio.get); лайк = audio.add, снятие лайка = audio.delete;
 *  - «Моя волна» — VK Микс (audio.getStreamMixAudios), волна по треку — audio.getRecommendations;
 *  - альбомы в коллекции — сохранённые альбомы и плейлисты (audio.getPlaylists);
 *  - чарт, новинки, «Для вас» и недавно прослушанное берутся из каталога (catalog.getAudio).
 *
 * Ответы каталога разбираются мягко: чего в ответе нет, того просто не будет на экране.
 */
class VkMusicApi(
    private val http: OkHttpClient,
    private val tokenProvider: () -> String?,
    private val clientProvider: () -> VkClient,
    private val prefs: SharedPreferences,
) : MusicApi {

    override val caps = ApiCaps(
        dislikes = true, podcasts = false, chart = true, previewLimit = false, connect = false,
        mixSettings = true, recommendations = true, nativeNewReleases = true,
        waveByEntity = true, playlistVisibility = false,
    )

    @Volatile override var highQuality: () -> Boolean = { true }
    override val plusConfirmedByStream: Boolean get() = true

    private val base = "https://api.vk.com/method/"
    private val apiVersion = VkAuth.API_VERSION

    // ---------- Сеть ----------

    @Volatile private var nextSlot = 0L
    private val slotLock = Any()

    /** VK пускает ~3 запроса в секунду на токен: выдерживаем паузу между запросами. */
    private fun throttle() {
        val wait: Long
        synchronized(slotLock) {
            val now = System.currentTimeMillis()
            val at = maxOf(now, nextSlot)
            nextSlot = at + 340
            wait = at - now
        }
        if (wait > 0) Thread.sleep(wait)
    }

    /**
     * Методы, на которые VK ответил «Unknown method passed» (3): для этого токена их нет (например, catalog.* у токенов «VK mp3 mod»).
     * Повторно их не дёргаем — только зря тратят паузу между запросами и забивают журнал. Сбрасывается при смене аккаунта.
     */
    private val deadMethods = ConcurrentHashMap.newKeySet<String>()

    private fun call(method: String, params: Map<String, String> = emptyMap()): JSONObject {
        if (method in deadMethods) throw VkApiException(3, "VK: Unknown method passed (3)")
        val token = tokenProvider() ?: error("Нет токена")
        val client = clientProvider()
        var attempt = 0
        while (true) {
            throttle()
            val form = FormBody.Builder()
                .add("access_token", token).add("v", apiVersion).add("https", "1").add("lang", "ru")
                .apply { params.forEach { (k, v) -> add(k, v) } }
                .build()
            val req = Request.Builder().url(base + method).header("User-Agent", client.userAgent).post(form).build()
            val (code, text) = http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
            if (code == 401) { AppLog.w("VK", "$method: HTTP 401"); error("HTTP 401: токен VK недействителен") }
            if (code !in 200..299) { AppLog.w("VK", "$method: HTTP $code ${text.take(160)}"); error("HTTP $code: ${text.take(160)}") }
            val j = try { JSONObject(text) } catch (e: Exception) {
                AppLog.w("VK", "$method: ответ не JSON: ${text.take(160)}")
                error("VK вернул непонятный ответ")
            }
            val err = j.optJSONObject("error")
            if (err != null) {
                val c = err.optInt("error_code")
                val msg = err.optString("error_msg")
                // Слишком часто — подождать и повторить.
                if ((c == 6 || c == 9) && attempt < 3) { attempt++; Thread.sleep(500L * attempt); continue }
                AppLog.w("VK", "$method: ошибка $c: $msg")
                if (c == 3) deadMethods.add(method)
                if (c == 5) error("HTTP 401: токен VK недействителен ($msg)")
                if (c == 14) throw VkApiException(c, "VK просит капчу — попробуйте позже", err.optString("captcha_sid"), err.optString("captcha_img"))
                if (c == 15 || c == 7 || c == 27 || c == 28) {
                    throw VkApiException(c, "VK не даёт доступ к музыке с этим входом ($c). Выйдите из аккаунта и войдите в VK заново")
                }
                throw VkApiException(c, "VK: $msg ($c)")
            }
            return j
        }
    }

    private fun resp(method: String, params: Map<String, String> = emptyMap()): Any? = call(method, params).opt("response")

    private fun respObj(method: String, params: Map<String, String> = emptyMap()): JSONObject =
        resp(method, params) as? JSONObject ?: JSONObject()

    private fun items(o: JSONObject): List<JSONObject> = o.optJSONArray("items").objects()

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    override fun resetSession() {
        urlCache.clear()
        likedByFuzzy.clear()
        mixSeen.clear()
        catalogCache = null
        catalogCacheAt = 0L
        deadMethods.clear()
        artistNames.clear()
        searchCache = null
    }

    // ---------- Аккаунт ----------

    @Volatile private var myId: Long = 0L

    override fun account(): Account {
        val arr = resp("users.get", mapOf("fields" to "screen_name,photo_200")) as? JSONArray
        val u = arr?.optJSONObject(0) ?: error("VK не вернул профиль")
        val id = u.optLong("id")
        myId = id
        val name = listOf(u.optString("first_name"), u.optString("last_name")).filter { it.isNotBlank() }.joinToString(" ")
        return Account(
            uid = id,
            login = u.optString("screen_name").ifBlank { "id$id" },
            name = name,
            // «Полный доступ»: у VK нет 30-секундного режима, подписка влияет на сам сервер.
            hasPlus = true,
            avatarUrl = u.optString("photo_200").ifBlank { null },
            provider = Provider.VK,
        )
    }

    // ---------- Разбор объектов ----------

    private fun bestPhoto(o: JSONObject?): String? {
        if (o == null) return null
        var bestKey: String? = null
        var bestSize = -1
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            if (!k.startsWith("photo_")) continue
            val n = k.removePrefix("photo_").toIntOrNull() ?: continue
            if (n > bestSize && o.optString(k).startsWith("http")) { bestSize = n; bestKey = k }
        }
        return bestKey?.let { o.optString(it) } ?: o.optString("url").takeIf { it.startsWith("http") }
    }

    private fun bestImage(v: Any?): String? = when (v) {
        is JSONObject -> bestPhoto(v)
        is JSONArray -> {
            // Массив картинок вида [{url, width, height}]: берём самую большую.
            val best = v.objects().filter { it.optString("url").startsWith("http") }
                .maxByOrNull { it.optInt("width", it.optInt("height", 0)) }
            best?.optString("url")
        }
        is String -> v.takeIf { it.startsWith("http") }
        else -> null
    }

    private val genres = mapOf(
        1 to "Рок", 2 to "Поп", 3 to "Рэп и хип-хоп", 4 to "Лёгкая музыка", 5 to "Хаус и танцевальная",
        6 to "Инструментальная", 7 to "Метал", 21 to "Альтернатива", 8 to "Дабстеп", 9 to "Джаз и блюз",
        10 to "Драм-н-бейс", 11 to "Транс", 12 to "Шансон", 13 to "Этническая", 14 to "Акустика и вокал",
        15 to "Регги", 16 to "Классика", 17 to "Инди-поп", 18 to "Речь", 19 to "Электропоп и диско", 22 to "Другое",
    )

    private class CachedUrl(val url: String, val at: Long)

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()
    private val urlTtlMs = 15 * 60_000L

    /** Трек, который уже лайкнут (по отпечаток «исполнитель | название | длительность») → id его копии в «Моей музыке». */
    private val likedByFuzzy = ConcurrentHashMap<String, String>()

    private fun fuzzy(artist: String, title: String, sec: Long): String =
        "${artist.trim().lowercase()}|${title.trim().lowercase()}|$sec"

    /** Имена исполнителей по их id: по имени артиста находят, когда страница каталога недоступна. */
    private val artistNames = ConcurrentHashMap<String, String>()

    private fun artistRefs(a: JSONObject): List<ArtistRef> {
        val out = ArrayList<ArtistRef>()
        for (key in listOf("main_artists", "featured_artists")) {
            for (o in a.optJSONArray(key).objects()) {
                val id = o.optString("id").ifBlank { o.optString("domain") }
                val name = o.optString("name")
                if (name.isNotBlank() && id.isNotBlank() && out.none { it.id == id }) {
                    out += ArtistRef(id, name)
                    artistNames[id] = name
                }
            }
        }
        if (out.isEmpty()) {
            // Без ссылок на артистов: имя уходит в поиск артиста при нажатии.
            a.optString("artist").split(",", " feat. ", " & ").map { it.trim() }.filter { it.isNotBlank() }.take(3)
                .forEach { out += ArtistRef("q:$it", it) }
        }
        return out
    }

    private fun albumRef(alb: JSONObject?): String? {
        if (alb == null || !alb.has("id")) return null
        val key = alb.optString("access_key")
        return "${alb.optLong("owner_id")}_${alb.optLong("id")}" + if (key.isNotBlank()) "_$key" else ""
    }

    private fun parseTrack(a: JSONObject): Track? {
        val aid = a.optLong("id")
        if (aid == 0L) return null
        val owner = a.optLong("owner_id")
        var id = VkIds.track(owner, aid)
        val key = a.optString("access_key").ifBlank { null }
        VkKeys.rememberTrack(id, key)
        a.optString("url").takeIf { it.startsWith("http") }?.let { putUrl(id, it) }
        val artist = a.optString("artist")
        val title = a.optString("title")
        val sec = a.optLong("duration")
        // Тот же трек уже в «Моей музыке» — отдаём id этой копии, чтобы он везде показывался лайкнутым.
        val own = likedByFuzzy[fuzzy(artist, title, sec)]
        var accessKey = key
        if (own != null && own != id) { id = own; accessKey = null }
        val alb = a.optJSONObject("album")
        val sub = a.optString("subtitle").ifBlank { null }
        if (title.isNotBlank()) VkKeys.remember(Track(id, null, title, artist, null, sec * 1000, accessKey = accessKey))
        return Track(
            id = id,
            albumId = albumRef(alb),
            title = title,
            artists = artist,
            coverUri = bestPhoto(alb?.optJSONObject("thumb")) ?: bestImage(a.opt("thumb")),
            durationMs = sec * 1000,
            artistRefs = artistRefs(a),
            albumTitle = alb?.optString("title")?.ifBlank { null },
            explicit = a.optBoolean("is_explicit", false),
            genre = genres[a.optInt("genre_id")],
            version = sub,
            accessKey = accessKey,
        )
    }

    private fun parseTracks(arr: JSONArray?): List<Track> = arr.objects().mapNotNull { parseTrack(it) }

    /** Какого вида ссылки отдаёт VK: HLS (m3u8) или обычный файл. Плеер выбирает источник по этому признаку. */
    private val hlsKind = ConcurrentHashMap<String, Boolean>()
    @Volatile private var lastHls = true

    /** Ожидается ли для трека HLS: по известной ссылке, иначе по виду последней полученной (VK отдаёт один вид на аккаунт). */
    fun expectsHls(id: String): Boolean = hlsKind[id] ?: lastHls

    /** Вид ссылки, если она уже получена от VK; null — неизвестно. */
    fun knownHls(id: String): Boolean? = hlsKind[id]

    /** Плеер выяснил, что поток трека на самом деле HLS / обычный файл (ошибка разбора): запоминаем, чтобы следующие треки открылись верно. */
    fun learnHls(id: String, hls: Boolean) {
        hlsKind[id] = hls
        lastHls = hls
    }

    private fun putUrl(id: String, url: String) {
        val hls = url.substringBefore('?').endsWith(".m3u8") || url.contains(".m3u8")
        if (hlsKind.size > 3000) hlsKind.clear()
        hlsKind[id] = hls
        lastHls = hls
        if (urlCache.size > 500) {
            val now = System.currentTimeMillis()
            urlCache.entries.removeAll { now - it.value.at > urlTtlMs }
        }
        urlCache[id] = CachedUrl(url, System.currentTimeMillis())
    }

    private fun plCover(p: JSONObject): String? =
        bestPhoto(p.optJSONObject("photo")) ?: p.optJSONArray("thumbs").objects().firstNotNullOfOrNull { bestPhoto(it) }
            ?: bestImage(p.opt("cover")) ?: bestPhoto(p.optJSONObject("original")?.optJSONObject("photo"))

    private fun parsePlaylist(p: JSONObject, cover: String? = null): PlaylistInfo {
        val owner = p.optLong("owner_id").toString()
        val kind = p.optLong("id").toInt()
        val key = p.optString("access_key").ifBlank { null }
        VkKeys.rememberPlaylist(owner, kind, key)
        return PlaylistInfo(
            kind = kind, ownerUid = owner, title = p.optString("title"),
            trackCount = p.optInt("count"), coverUri = cover ?: plCover(p),
            // Своим плейлистом можно делиться по ссылке: VK открывает его по ключу доступа.
            visibility = "public", ownerLogin = null, accessKey = key,
            description = p.optString("description").ifBlank { null },
        )
    }

    private fun albumIdOf(p: JSONObject): String {
        val key = p.optString("access_key")
        return "${p.optLong("owner_id")}_${p.optLong("id")}" + if (key.isNotBlank()) "_$key" else ""
    }

    private fun parseAlbum(p: JSONObject): AlbumInfo {
        val artists = p.optJSONArray("main_artists").objects()
        VkKeys.rememberPlaylist(p.optLong("owner_id").toString(), p.optLong("id").toInt(), p.optString("access_key").ifBlank { null })
        // Дата выхода: VK отдаёт год, точной даты нет. Альбомам этого года (и без года) берём дату создания в VK:
        // по ней «Новинки» отличают свежий релиз от старого. Альбомам прошлых лет остаётся один год.
        val year = p.optInt("year")
        val created = p.optLong("create_time")
        val releaseDate = if ((year == 0 || year >= java.time.LocalDate.now().year) && created > 0) {
            java.time.Instant.ofEpochSecond(created).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
        } else ""
        return AlbumInfo(
            id = albumIdOf(p),
            title = p.optString("title"),
            artists = artists.joinToString(", ") { it.optString("name") }.ifBlank { p.optString("subtitle") },
            artistRefs = artists.mapNotNull { o ->
                val id = o.optString("id").ifBlank { o.optString("domain") }
                if (id.isBlank()) null else ArtistRef(id, o.optString("name"))
            },
            coverUri = plCover(p),
            year = year,
            trackCount = p.optInt("count"),
            releaseDate = releaseDate,
        )
    }

    private fun parseArtist(a: JSONObject): ArtistInfo {
        val id = a.optString("id").ifBlank { a.optString("domain") }
        if (id.isNotBlank() && a.optString("name").isNotBlank()) artistNames[id] = a.optString("name")
        // Число слушателей каталог VK отдаёт не всегда и под разными именами: берём первое найденное, нет — поле не показывается.
        val listeners = listOf("listeners", "monthly_listeners", "listeners_count", "listeners_month")
            .firstNotNullOfOrNull { k -> a.optLong(k, 0L).takeIf { it > 0L } } ?: 0L
        return ArtistInfo(
            id = id, name = a.optString("name"), coverUri = bestImage(a.opt("photo")) ?: bestImage(a.opt("image")),
            monthlyListeners = listeners,
            // Каталог VK отдаёт описание не везде и под разными именами: берём первое непустое, нет — блока не будет.
            description = listOf("description", "bio", "about", "biography")
                .firstNotNullOfOrNull { k ->
                    when (val v = a.opt(k)) {
                        is String -> v
                        is JSONObject -> v.optString("text")
                        else -> null
                    }?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                }.orEmpty(),
        )
    }

    /** Разбор "owner_id_playlist_id[_key]". */
    private class AlbumRef(val owner: String, val id: String, val key: String?)

    private fun albumRefOf(s: String): AlbumRef {
        val parts = s.split("_")
        if (parts.size < 2) error("Неверный идентификатор альбома VK")
        return AlbumRef(parts[0], parts[1], parts.getOrNull(2)?.ifBlank { null })
    }

    private fun keyParams(owner: String, id: String, key: String?): Map<String, String> =
        buildMap {
            put("owner_id", owner); put("playlist_id", id)
            (key ?: VkKeys.playlist(owner, id.toIntOrNull() ?: 0))?.let { put("access_key", it) }
        }

    private fun audioParam(t: Track): String {
        val raw = VkIds.raw(t.id) ?: error("Это не трек VK")
        val key = t.accessKey ?: VkKeys.track(t.id)
        return if (key.isNullOrBlank()) raw else "${raw}_$key"
    }

    private fun audioParam(id: String): String {
        val raw = VkIds.raw(id) ?: error("Это не трек VK")
        val key = VkKeys.track(id)
        return if (key.isNullOrBlank()) raw else "${raw}_$key"
    }

    // ---------- Моя музыка и лайки ----------

    override fun likedTracks(uid: String, limit: Int): List<Track> {
        val out = ArrayList<Track>()
        val page = 2000
        var offset = 0
        while (out.size < limit) {
            val r = respObj("audio.get", mapOf("owner_id" to uid, "count" to minOf(page, limit - out.size).toString(), "offset" to offset.toString()))
            val list = items(r)
            if (list.isEmpty()) break
            out += parseTracks(r.optJSONArray("items"))
            offset += list.size
            if (list.size < minOf(page, limit)) break
        }
        // Запоминаем отпечатки лайкнутого: одинаковые треки из поиска и каталога подхватят id своей копии.
        if (limit == Int.MAX_VALUE) {
            likedByFuzzy.clear()
            out.forEach { likedByFuzzy[fuzzy(it.artists, it.title, it.durationMs / 1000)] = it.id }
        }
        return out
    }

    override fun tracksByIds(keys: List<String>): List<Track> {
        val ids = keys.mapNotNull { k -> if (VkIds.isVk(k)) audioParam(k) else null }
        val out = ArrayList<Track>()
        ids.chunked(100).forEach { chunk ->
            val arr = resp("audio.getById", mapOf("audios" to chunk.joinToString(","))) as? JSONArray
            out += parseTracks(arr)
        }
        return out
    }

    private fun ownerOf(t: Track): Long = VkIds.raw(t.id)?.substringBefore('_')?.toLongOrNull() ?: 0L
    private fun audioIdOf(t: Track): Long = VkIds.raw(t.id)?.substringAfter('_')?.toLongOrNull() ?: 0L

    override fun likeTrack(uid: String, t: Track) {
        if (ownerOf(t).toString() == uid) return
        val fz = fuzzy(t.artists, t.title, t.durationMs / 1000)
        if (likedByFuzzy.containsKey(fz)) return
        val params = buildMap {
            put("audio_id", audioIdOf(t).toString()); put("owner_id", ownerOf(t).toString())
            (t.accessKey ?: VkKeys.track(t.id))?.let { put("access_key", it) }
        }
        val newId = (resp("audio.add", params) as? Number)?.toLong()
        if (newId != null && newId > 0) likedByFuzzy[fz] = VkIds.track(uid.toLong(), newId)
    }

    override fun unlikeTrack(uid: String, t: Track) {
        var target: Long? = if (ownerOf(t).toString() == uid) audioIdOf(t) else null
        val fz = fuzzy(t.artists, t.title, t.durationMs / 1000)
        if (target == null) {
            var copy = likedByFuzzy[fz]
            if (copy == null) { likedTracks(uid); copy = likedByFuzzy[fz] }
            target = copy?.let { VkIds.raw(it)?.substringAfter('_')?.toLongOrNull() }
        }
        if (target == null) return
        resp("audio.delete", mapOf("audio_id" to target.toString(), "owner_id" to uid))
        likedByFuzzy.remove(fz)
    }

    // В VK нет «не нравится» в открытом API: дизлайки хранятся на устройстве (id и отпечаток «исполнитель | название | длительность»).
    // Такие треки не попадают в VK Микс и рекомендации, а в интерфейсе помечены как не понравившиеся.
    private fun disIds(): Set<String> = prefs.getStringSet("dis_ids", emptySet()).orEmpty()
    private fun disFz(): Set<String> = prefs.getStringSet("dis_fz", emptySet()).orEmpty()

    override fun dislikeTrack(uid: String, t: Track) {
        val fz = fuzzy(t.artists, t.title, t.durationMs / 1000)
        prefs.edit().putStringSet("dis_ids", disIds() + t.id).putStringSet("dis_fz", disFz() + fz).apply()
        mixSeen += t.id
    }

    override fun undislikeTrack(uid: String, t: Track) {
        val fz = fuzzy(t.artists, t.title, t.durationMs / 1000)
        prefs.edit().putStringSet("dis_ids", disIds() - t.id).putStringSet("dis_fz", disFz() - fz).apply()
    }

    override fun dislikedTrackIds(uid: String): Set<String> = disIds()

    private fun notDisliked(list: List<Track>): List<Track> {
        val fz = disFz()
        if (fz.isEmpty()) return list
        val ids = disIds()
        return list.filter { it.id !in ids && fuzzy(it.artists, it.title, it.durationMs / 1000) !in fz }
    }

    // ---------- Плейлисты ----------

    private fun allPlaylists(uid: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var offset = 0
        while (true) {
            val r = respObj("audio.getPlaylists", mapOf("owner_id" to uid, "count" to "200", "offset" to offset.toString()))
            val list = items(r)
            out += list
            offset += list.size
            if (list.size < 200 || offset >= r.optInt("count", 0)) break
        }
        return out
    }

    /** Свои плейлисты: созданные самим пользователем (не сохранённые чужие и не альбомы). */
    override fun playlists(uid: String): List<PlaylistInfo> =
        allPlaylists(uid).filter { it.optLong("owner_id").toString() == uid && it.optJSONObject("original") == null && it.optInt("type") != 1 }
            .map { parsePlaylist(it) }

    override fun playlistInfo(ownerUid: String, kind: Int): PlaylistInfo {
        val p = respObj("audio.getPlaylistById", keyParams(ownerUid, kind.toString(), null))
        return parsePlaylist(p)
    }

    override fun playlistInfoByUuid(uuid: String): PlaylistInfo = error("Ссылки такого вида в VK не поддерживаются")

    override fun setPlaylistVisibility(ownerUid: String, kind: Int, isPublic: Boolean) =
        error("В VK у плейлиста нет настройки видимости")

    override fun playlistTracks(ownerUid: String, kind: Int): List<Track> {
        val out = ArrayList<Track>()
        var offset = 0
        val page = 2000
        while (true) {
            // Плейлист в audio.get задаётся параметром album_id (playlist_id там не понимается — приходила бы вся «Моя музыка»).
            val kp = keyParams(ownerUid, kind.toString(), null)
            val r = respObj("audio.get", kp + mapOf("album_id" to kind.toString(), "count" to page.toString(), "offset" to offset.toString()))
            val list = items(r)
            if (list.isEmpty()) break
            out += parseTracks(r.optJSONArray("items"))
            offset += list.size
            if (list.size < page) break
        }
        return out
    }

    override fun createPlaylist(uid: String, title: String, isPublic: Boolean): PlaylistInfo {
        val r = respObj("audio.createPlaylist", mapOf("owner_id" to uid, "title" to title))
        val id = r.optLong("id").toInt()
        if (id == 0) error("VK не создал плейлист")
        return PlaylistInfo(kind = id, ownerUid = r.optLong("owner_id", uid.toLong()).toString(), title = title, trackCount = 0, coverUri = null, visibility = "public", accessKey = r.optString("access_key").ifBlank { null })
    }

    override fun renamePlaylist(ownerUid: String, kind: Int, title: String) {
        resp("audio.editPlaylist", mapOf("owner_id" to ownerUid, "playlist_id" to kind.toString(), "title" to title))
    }

    override fun deletePlaylist(ownerUid: String, kind: Int) {
        resp("audio.deletePlaylist", mapOf("owner_id" to ownerUid, "playlist_id" to kind.toString()))
    }

    override fun addTrackToPlaylist(ownerUid: String, kind: Int, track: Track) {
        resp("audio.addToPlaylist", mapOf("owner_id" to ownerUid, "playlist_id" to kind.toString(), "audio_ids" to audioParam(track)))
    }

    override fun removeTrackFromPlaylist(ownerUid: String, kind: Int, track: Track) {
        resp("audio.removeFromPlaylist", mapOf("owner_id" to ownerUid, "playlist_id" to kind.toString(), "audio_ids" to audioParam(track)))
    }

    // ---------- Поиск ----------

    private class SearchCache(val q: String, val tracks: List<Track>, val at: Long)

    @Volatile private var searchCache: SearchCache? = null

    /** Поиск треков с запоминанием на 15 секунд: поиск артистов и поиск треков по одному запросу делят один ответ VK. */
    private fun searchCached(query: String): List<Track> {
        searchCache?.let { if (it.q == query && System.currentTimeMillis() - it.at < 15_000L) return it.tracks }
        val r = searchUncached(query)
        searchCache = SearchCache(query, r, System.currentTimeMillis())
        return r
    }

    override fun search(query: String): List<Track> = searchCached(query)

    private fun searchUncached(query: String): List<Track> {
        val direct = runCatching {
            parseTracks(respObj("audio.search", mapOf("q" to query, "count" to "100", "auto_complete" to "1", "sort" to "0")).optJSONArray("items"))
        }.onFailure { AppLog.w("VK", "audio.search не сработал: ${it.message}") }
        direct.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        // Прямой поиск пуст или упал — пробуем поиск каталога.
        val viaCatalog = runCatching { catalogSearch(query).tracks }
            .onFailure { AppLog.w("VK", "catalog.getAudioSearch не сработал: ${it.message}") }
        viaCatalog.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        direct.exceptionOrNull()?.let { throw it }
        return emptyList()
    }

    /** Карточки артистов: из каталога VK, а если он недоступен — из исполнителей найденных треков. */
    override fun searchArtists(query: String): List<ArtistInfo> {
        val real = searchArtistsReal(query)
        if (real.isNotEmpty()) return real
        return runCatching { artistsFromTracks(query, searchCached(query)) }
            .onFailure { AppLog.w("VK", "артисты по найденным трекам не собрались: ${it.message}") }.getOrDefault(emptyList())
    }

    private fun artistsFromTracks(query: String, tracks: List<Track>): List<ArtistInfo> {
        class Acc(val ref: ArtistRef, var cover: String?, var n: Int)
        val acc = LinkedHashMap<String, Acc>()
        for (t in tracks) for (r in t.artistRefs) {
            val a = acc.getOrPut(r.name.trim().lowercase()) { Acc(r, null, 0) }
            a.n++
            if (a.cover == null) a.cover = t.coverUri
        }
        val q = query.trim().lowercase()
        fun matches(n: String) = n.isNotEmpty() && q.isNotEmpty() && (n.contains(q) || q.contains(n))
        val sorted = acc.entries.sortedWith(compareByDescending<Map.Entry<String, Acc>> { matches(it.key) }.thenByDescending { it.value.n })
        val hit = sorted.filter { matches(it.key) }
        val pick = if (hit.isNotEmpty()) hit.take(8) else sorted.take(3)
        return pick.map { ArtistInfo(id = it.value.ref.id, name = it.value.ref.name, coverUri = it.value.cover) }
    }

    private fun searchArtistsReal(query: String): List<ArtistInfo> {
        val direct = runCatching { items(respObj("audio.searchArtists", mapOf("q" to query, "count" to "20"))).map { parseArtist(it) } }
            .onFailure { AppLog.w("VK", "audio.searchArtists не сработал: ${it.message}") }.getOrNull()
        if (!direct.isNullOrEmpty()) return direct.filter { it.id.isNotBlank() }
        return runCatching { catalogSearch(query).artists }
            .onFailure { AppLog.w("VK", "поиск артистов в каталоге не сработал: ${it.message}") }.getOrDefault(emptyList())
    }

    override fun searchAlbums(query: String): List<AlbumInfo> {
        val direct = runCatching { items(respObj("audio.searchAlbums", mapOf("q" to query, "count" to "20"))).map { parseAlbum(it) } }
            .onFailure { AppLog.w("VK", "audio.searchAlbums не сработал: ${it.message}") }.getOrNull()
        if (!direct.isNullOrEmpty()) return direct
        return runCatching { catalogSearch(query).albums }
            .onFailure { AppLog.w("VK", "поиск альбомов в каталоге не сработал: ${it.message}") }.getOrDefault(emptyList())
    }

    private fun isPodcast(p: JSONObject): Boolean =
        p.optBoolean("is_podcast") || p.has("podcast_info") || p.optString("type").contains("podcast", true) ||
            p.optString("subtitle").contains("подкаст", true) || p.optString("title").contains("подкаст", true)

    /** Поиск подкастов: поиск каталога, из которого берутся выпуски-«плейлисты», помеченные как подкаст. */
    override fun searchPodcasts(query: String): List<AlbumInfo> =
        if (!caps.podcasts) emptyList() else runCatching {
            val d = catalog("catalog.getAudioSearch", mapOf("query" to query, "need_blocks" to "1", "extended" to "1", "fields" to "photo_50,photo_100"))
            (d.blocks().flatMap { it.playlists } + d.playlists.values).filter { isPodcast(it) }.distinctBy { albumIdOf(it) }.map { parseAlbum(it) }
        }.onFailure { AppLog.w("VK", "поиск подкастов не сработал: ${it.message}") }.getOrDefault(emptyList())

    /** Главная подкастов: каталог подкастов VK, а если его нет — подкасты из раздела «Обзор». */
    override fun podcastsLanding(): List<AlbumInfo> {
        if (!caps.podcasts) return emptyList()
        val viaCatalog = runCatching {
            val d = catalog("catalog.getPodcasts", mapOf("need_blocks" to "1", "extended" to "1", "fields" to "photo_50,photo_100"))
            val blocks = d.blocks().ifEmpty { d.sections.flatMap { d.blocksOf(it) } }
            (blocks.flatMap { it.playlists }.ifEmpty { d.playlists.values.toList() }).distinctBy { albumIdOf(it) }.map { parseAlbum(it) }
        }.onFailure { AppLog.w("VK", "catalog.getPodcasts не сработал: ${it.message}") }.getOrDefault(emptyList())
        if (viaCatalog.isNotEmpty()) return viaCatalog
        return runCatching {
            val h = home()
            (h.explore + h.general).flatMap { it.playlists }.filter { isPodcast(it) }.distinctBy { albumIdOf(it) }.map { parseAlbum(it) }
        }.getOrDefault(emptyList())
    }

    private class SearchParts(val artists: List<ArtistInfo>, val albums: List<AlbumInfo>, val tracks: List<Track>)

    private fun catalogSearch(query: String): SearchParts {
        val d = catalog("catalog.getAudioSearch", mapOf("query" to query, "need_blocks" to "1", "extended" to "1", "fields" to "photo_50,photo_100"))
        val albums = d.playlists.values.filter { it.optInt("type") == 1 || it.optJSONArray("main_artists") != null }.map { parseAlbum(it) }
        val audios = d.blocks().flatMap { it.audios }.ifEmpty { d.audios.values.toList() }
        return SearchParts(d.artists.values.map { parseArtist(it) }.filter { it.id.isNotBlank() }, albums, audios.mapNotNull { parseTrack(it) })
    }

    // ---------- Потоки ----------

    private fun freshUrl(trackId: String): String {
        urlCache[trackId]?.let { if (System.currentTimeMillis() - it.at < urlTtlMs) return it.url }
        val byId = runCatching {
            val arr = resp("audio.getById", mapOf("audios" to audioParam(trackId))) as? JSONArray
            val a = arr?.optJSONObject(0)
            // Заодно запоминаем описание трека: пригодится для поиска копии, если эта окажется недоступной.
            if (a != null) parseTrack(a)
            a?.optString("url").orEmpty()
        }
        val direct = byId.getOrNull().orEmpty()
        if (direct.isNotBlank()) {
            putUrl(trackId, direct)
            AppLog.i("VK", "Поток $trackId: ${if (hlsKind[trackId] == true) "HLS" else "обычный файл"}")
            return direct
        }
        // Копии нет или у неё нет ссылки (трек убрали из «Моей музыки», пропал ключ доступа, ограничение): ищем такой же трек поиском.
        AppLog.w("VK", "audio.getById не дал ссылку для $trackId (${byId.exceptionOrNull()?.message ?: "пустой url"}), ищу копию поиском")
        urlViaSearch(trackId)?.let { putUrl(trackId, it); return it }
        byId.exceptionOrNull()?.let { throw it }
        error("Трек недоступен в VK (ограничение правообладателя или нет подписки)")
    }

    /** Ссылка на такой же трек (тот же исполнитель, название и длительность), найденная поиском. */
    private fun urlViaSearch(trackId: String): String? {
        val m = VkKeys.meta(trackId) ?: return null
        val found = runCatching {
            items(respObj("audio.search", mapOf("q" to "${m.artist} ${m.title}".trim(), "count" to "30", "auto_complete" to "1")))
        }.onFailure { AppLog.w("VK", "поиск копии трека не сработал: ${it.message}") }.getOrNull().orEmpty()
        val title = m.title.trim().lowercase()
        val best = found.filter { it.optString("url").isNotBlank() }
            .filter { it.optString("title").trim().lowercase() == title }
            .minByOrNull { if (m.sec > 0) kotlin.math.abs(it.optLong("duration") - m.sec) else 0L }
            ?: return null
        if (m.sec > 0 && kotlin.math.abs(best.optLong("duration") - m.sec) > 3) return null
        return best.optString("url")
    }

    override fun streamUrl(trackId: String): String {
        val url = freshUrl(trackId)
        return VkHls.pickVariant(http, url, clientProvider().userAgent, highQuality())
    }

    override fun downloadUrl(trackId: String): String = freshUrl(trackId)

    override fun downloadCustom(trackId: String, dest: File, onProgress: (Float) -> Unit, check: () -> Unit): String? {
        val url = freshUrl(trackId)
        if (!url.contains(".m3u8")) return null
        return VkHls.download(http, url, clientProvider().userAgent, dest, highQuality(), onProgress, check)
    }

    // ---------- Артисты и альбомы ----------

    override fun artist(id: String): ArtistInfo {
        if (id.startsWith("q:")) {
            val found = searchArtistsReal(id.removePrefix("q:")).firstOrNull { it.id.isNotBlank() && !it.id.startsWith("q:") }
            return if (found != null) artist(found.id) else artistFallback(id)
        }
        val d = try {
            catalog("catalog.getAudioArtist", mapOf("artist_id" to id, "need_blocks" to "1", "extended" to "1", "fields" to "photo_50,photo_100"))
        } catch (e: VkApiException) {
            // Каталога у этого входа нет (или он отказал): страницу артиста собираем из поиска.
            if (e.code == 14) throw e
            return artistFallback(id)
        }
        val meta = d.artists[id] ?: d.artists.values.firstOrNull { it.optString("domain") == id } ?: d.artists.values.firstOrNull()
        val info = meta?.let { parseArtist(it) } ?: ArtistInfo(id = id, name = id, coverUri = null)
        val blocks = d.blocks()
        val tracks = blocks.firstOrNull { it.audios.isNotEmpty() }?.audios?.mapNotNull { parseTrack(it) }
            ?: d.audios.values.mapNotNull { parseTrack(it) }.take(30)
        val albumBlocks = blocks.filter { it.playlists.isNotEmpty() }.flatMap { it.playlists }
        val albums = (albumBlocks.ifEmpty { d.playlists.values.toList() }).distinctBy { albumIdOf(it) }.map { parseAlbum(it) }
        return info.copy(id = id, tracks = tracks.take(50), albums = albums)
    }

    /** Имя исполнителя для поиска: по сохранённому id, из «q:имя» или из адреса страницы (vk.com/artist/<домен>). */
    private fun artistNameOf(id: String): String? =
        (if (id.startsWith("q:")) id.removePrefix("q:") else artistNames[id] ?: id.takeIf { !it.all { c -> c.isDigit() } }?.replace('_', ' ')?.replace('-', ' '))
            ?.trim()?.takeIf { it.isNotBlank() }

    private fun artistTracksLegacy(name: String, count: Int): List<Track> {
        val raw = respObj("audio.search", mapOf("q" to name, "performer_only" to "1", "count" to count.toString(), "sort" to "2", "auto_complete" to "1"))
        val all = parseTracks(raw.optJSONArray("items"))
        val n = name.trim().lowercase()
        return all.filter { t -> t.artists.lowercase().contains(n) || t.artistRefs.any { it.name.lowercase() == n } }.ifEmpty { all }
    }

    /** Страница артиста без каталога: его треки и альбомы находятся поиском по имени. */
    private fun artistFallback(id: String): ArtistInfo {
        val name = artistNameOf(id) ?: error("Артист не найден в VK")
        val tracks = artistTracksLegacy(name, 50)
        val n = name.lowercase()
        // Имя в том виде, как его пишет VK (регистр, «ё»): берём у первого трека, где оно совпало.
        val shown = tracks.asSequence().flatMap { it.artistRefs.asSequence() }.firstOrNull { it.name.lowercase() == n }?.name ?: name
        val albums = runCatching {
            items(respObj("audio.searchAlbums", mapOf("q" to name, "count" to "20")))
                .filter { p -> p.optString("subtitle").lowercase().contains(n) || p.optJSONArray("main_artists").objects().any { it.optString("name").lowercase() == n } }
                .map { parseAlbum(it) }
        }.onFailure { AppLog.w("VK", "альбомы артиста не найдены: ${it.message}") }.getOrDefault(emptyList())
        AppLog.i("VK", "Страница артиста «$shown» собрана из поиска: треков ${tracks.size}, альбомов ${albums.size}")
        return ArtistInfo(id = id, name = shown, coverUri = tracks.firstNotNullOfOrNull { it.coverUri }, tracks = tracks.take(50), albums = albums)
    }

    override fun artistRecentAlbums(id: String): List<AlbumInfo> =
        artist(id).albums.sortedByDescending { it.sortDate }.take(8)

    override fun artistAllTracks(id: String): List<Track> {
        if (id.startsWith("q:")) return artist(id).tracks
        val d = try {
            catalog("catalog.getAudioArtist", mapOf("artist_id" to id, "need_blocks" to "0"))
        } catch (e: VkApiException) {
            if (e.code == 14) throw e
            val name = artistNameOf(id) ?: return artist(id).tracks
            return artistTracksLegacy(name, 300)
        }
        val sectionId = d.sections.firstOrNull { Regex("трек|песн|top|audio", RegexOption.IGNORE_CASE).containsMatchIn(it.optString("title") + it.optString("url")) }
            ?.optString("id")?.ifBlank { null }
        if (sectionId == null) return artist(id).tracks
        val out = LinkedHashMap<String, Track>()
        var from = ""
        var page = 0
        while (page < 40) {
            val p = mutableMapOf("section_id" to sectionId, "need_blocks" to "1", "extended" to "1", "fields" to "photo_50,photo_100")
            if (from.isNotEmpty()) p["start_from"] = from
            val sec = catalog("catalog.getSection", p)
            val before = out.size
            sec.blocks().flatMap { it.audios }.mapNotNull { parseTrack(it) }.forEach { out.putIfAbsent(it.id, it) }
            from = sec.section?.optString("next_from").orEmpty()
            page++
            if (from.isEmpty() || out.size == before) break
        }
        return out.values.toList().ifEmpty { artist(id).tracks }
    }

    override fun album(id: String): AlbumInfo {
        val r = albumRefOf(id)
        val p = respObj("audio.getPlaylistById", keyParams(r.owner, r.id, r.key))
        val info = parseAlbum(p)
        val tracks = playlistTracks(r.owner, r.id.toInt())
        return info.copy(tracks = tracks, trackCount = tracks.size.takeIf { it > 0 } ?: info.trackCount)
    }

    /** Сохранённые альбомы и чужие плейлисты («Моя музыка → Плейлисты»): здесь они лежат как «альбомы». */
    override fun likedAlbums(uid: String): List<AlbumInfo> =
        allPlaylists(uid).filter { it.optLong("owner_id").toString() != uid || it.optJSONObject("original") != null || it.optInt("type") == 1 }
            .map { p ->
                val o = p.optJSONObject("original")
                val src = if (o != null) JSONObject(p.toString()).apply {
                    put("owner_id", o.optLong("owner_id")); put("id", o.optLong("playlist_id")); put("access_key", o.optString("access_key"))
                } else p
                parseAlbum(src)
            }

    override fun likeAlbum(uid: String, albumId: String) {
        val r = albumRefOf(albumId)
        val params = buildMap {
            put("owner_id", r.owner); put("playlist_id", r.id)
            (r.key ?: VkKeys.playlist(r.owner, r.id.toIntOrNull() ?: 0))?.let { put("access_key", it) }
        }
        resp("audio.followPlaylist", params)
    }

    override fun unlikeAlbum(uid: String, albumId: String) {
        val r = albumRefOf(albumId)
        // Сохранённая копия лежит у пользователя; находим её по ссылке на оригинал.
        val copy = allPlaylists(uid).firstOrNull { p ->
            val o = p.optJSONObject("original")
            o != null && o.optLong("owner_id").toString() == r.owner && o.optLong("playlist_id").toString() == r.id
        } ?: allPlaylists(uid).firstOrNull { it.optLong("owner_id").toString() == r.owner && it.optLong("id").toString() == r.id }
        val target = copy ?: return
        resp("audio.deletePlaylist", mapOf("owner_id" to target.optLong("owner_id").toString(), "playlist_id" to target.optLong("id").toString()))
    }

    // ---------- Тексты и информация ----------

    override fun trackLyrics(trackId: String, synced: Boolean): String? {
        val r = runCatching { respObj("audio.getLyrics", mapOf("audio_id" to audioParam(trackId))) }.getOrNull() ?: return null
        val lyr = r.optJSONObject("lyrics") ?: return null
        val ts = lyr.optJSONArray("timestamps")
        if (synced) {
            if (ts == null) return null
            val sb = StringBuilder()
            var any = false
            for (row in ts.objects()) {
                val line = row.optString("line")
                val begin = when {
                    row.has("begin") -> row.optLong("begin", -1)
                    row.has("start") -> row.optLong("start", -1)
                    row.has("time") -> row.optLong("time", -1)
                    else -> -1L
                }
                if (begin < 0) continue
                any = true
                sb.append("[%02d:%02d.%02d]%s\n".format(begin / 60000, (begin / 1000) % 60, (begin % 1000) / 10, line))
            }
            return if (any) sb.toString() else null
        }
        val textArr = lyr.optJSONArray("text")
        val lines = if (textArr != null) (0 until textArr.length()).map { textArr.optString(it) }
        else ts.objects().map { it.optString("line") }
        return lines.joinToString("\n").trim().takeIf { it.isNotBlank() }
    }

    override fun trackInfo(t: Track): TrackInfo {
        val arr = resp("audio.getById", mapOf("audios" to audioParam(t))) as? JSONArray
        val a = arr?.optJSONObject(0) ?: error("Нет данных о треке")
        val alb = a.optJSONObject("album")
        return TrackInfo(
            title = a.optString("title").ifBlank { t.title },
            version = a.optString("subtitle").ifBlank { null },
            artists = a.optString("artist").ifBlank { t.artists },
            album = alb?.optString("title")?.ifBlank { null } ?: t.albumTitle,
            albumId = albumRef(alb) ?: t.albumId,
            genre = genres[a.optInt("genre_id")] ?: t.genre,
            explicit = a.optBoolean("is_explicit", false) || t.explicit,
            durationMs = a.optLong("duration") * 1000,
        )
    }

    // ---------- VK Микс и волны ----------

    private val mixSeen = LinkedHashSet<String>()

    private fun savedMixOptions(): String = prefs.getString("mix_options", "").orEmpty()

    override fun mixSettings(): MixSettings? {
        val s = respObj("audio.getStreamMixSettings", mapOf("mix_id" to "common")).optJSONObject("settings") ?: return null
        val saved = runCatching { JSONObject(savedMixOptions()) }.getOrNull()
        val cats = s.optJSONArray("mix_categories").objects().map { c ->
            val cid = c.optString("id")
            val chosen = saved?.optJSONArray(cid)?.let { a -> (0 until a.length()).map { a.optString(it) } }
            MixCategory(
                id = cid, title = c.optString("title"),
                options = c.optJSONArray("options").objects().map { o ->
                    MixOption(o.optString("id"), o.optString("title"), if (chosen != null) o.optString("id") in chosen else o.optBoolean("selected"))
                },
            )
        }.filter { it.options.isNotEmpty() }
        if (cats.isEmpty()) return null
        return MixSettings(s.optString("title"), s.optString("subtitle"), cats, s.optBoolean("multi_select", false))
    }

    override fun saveMixOptions(options: String) {
        prefs.edit().putString("mix_options", options).apply()
        mixSeen.clear()
    }

    private fun mixBatch(mixId: String, entityId: String?): WaveBatch {
        var tracks: List<Track> = emptyList()
        for (attempt in 0 until 3) {
            val params = mutableMapOf("mix_id" to mixId, "count" to "10")
            if (!entityId.isNullOrBlank()) params["entity_id"] = entityId
            savedMixOptions().takeIf { it.isNotBlank() }?.let { params["options"] = it }
            val r = resp("audio.getStreamMixAudios", params)
            val arr = (r as? JSONArray) ?: (r as? JSONObject)?.optJSONArray("items")
            tracks = notDisliked(parseTracks(arr))
            val fresh = tracks.filter { it.id !in mixSeen }
            if (fresh.isNotEmpty()) { tracks = fresh; break }
        }
        tracks.forEach { mixSeen += it.id }
        while (mixSeen.size > 300) mixSeen.remove(mixSeen.first())
        return WaveBatch(tracks, null)
    }

    private fun recommendations(targetRaw: String?): List<Track> {
        val params = mutableMapOf("count" to "50", "shuffle" to "1")
        if (targetRaw != null) params["target_audio"] = targetRaw
        val r = resp("audio.getRecommendations", params)
        val arr = (r as? JSONObject)?.optJSONArray("items") ?: (r as? JSONArray)
        return notDisliked(parseTracks(arr))
    }

    override fun waveTracks(afterTrackId: String?, station: String): WaveBatch {
        val s = station.trim()
        return when {
            s == "user:onyourwave" || s == "vkmix:common" || s.isEmpty() -> mixBatch("common", null)
            s.startsWith("vkmix:") -> {
                val rest = s.removePrefix("vkmix:").split(":", limit = 2)
                mixBatch(rest[0], rest.getOrNull(1))
            }
            s.startsWith("track:") -> WaveBatch(recommendations(audioParamOrNull(s.removePrefix("track:"))), null)
            s.startsWith("artist:") -> {
                val aid = s.removePrefix("artist:")
                val seed = artist(aid).tracks.firstOrNull()
                WaveBatch(recommendations(seed?.let { audioParam(it) }).ifEmpty { artist(aid).tracks.shuffled() }, null)
            }
            s.startsWith("album:") -> {
                val seed = album(s.removePrefix("album:")).tracks.firstOrNull()
                WaveBatch(recommendations(seed?.let { audioParam(it) }), null)
            }
            s.startsWith("playlist:") -> {
                val parts = s.removePrefix("playlist:").split("_")
                val seed = if (parts.size >= 2) playlistTracks(parts[0], parts[1].toIntOrNull() ?: 0).firstOrNull() else null
                WaveBatch(recommendations(seed?.let { audioParam(it) }), null)
            }
            else -> mixBatch("common", null)
        }
    }

    private fun audioParamOrNull(id: String): String? = if (VkIds.isVk(id)) audioParam(id) else null

    // У VK Микса те же «настройки волны» не используются: см. mixSettings().
    override fun waveSettings(): WaveSettings = WaveSettings()
    override fun saveWaveSettings(s: WaveSettings) {}
    override fun waveTrackStarted(trackId: String, batchId: String?, station: String) {}
    override fun waveStarted(station: String) {}

    // ---------- Каталог: чарт, новинки, «Для вас», история ----------

    private class Block(
        val id: String,
        val title: String,
        val type: String,
        val audios: List<JSONObject>,
        val playlists: List<JSONObject>,
        val nextFrom: String,
        val layout: String,
    )

    private class Catalog(
        val audios: Map<String, JSONObject>,
        val playlists: Map<String, JSONObject>,
        val artists: Map<String, JSONObject>,
        val sections: List<JSONObject>,
        val section: JSONObject?,
        val recommended: Map<String, JSONObject>,
        val block: JSONObject?,
    ) {
        fun blocks(): List<Block> {
            val sec = section ?: sections.firstOrNull() ?: return emptyList()
            return parseBlocks(sec.optJSONArray("blocks"))
        }

        fun blocksOf(sec: JSONObject): List<Block> = parseBlocks(sec.optJSONArray("blocks"))

        private fun key(o: JSONObject) = "${o.opt("owner_id")}_${o.opt("id")}"

        private fun parseBlocks(arr: JSONArray?): List<Block> {
            val out = ArrayList<Block>()
            var heading = ""
            if (arr == null) return out
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                val layout = b.optJSONObject("layout")
                val type = b.optString("data_type")
                val title = b.optString("title").ifBlank { layout?.optString("title").orEmpty() }.ifBlank { heading }
                if (type == "none") {
                    if (layout?.optString("name") != "separator") heading = title
                    continue
                }
                heading = ""
                val aud = ids(b.optJSONArray("audios_ids")).mapNotNull { audios[it] }
                val pls = ids(b.optJSONArray("playlists_ids")).mapNotNull { id ->
                    val base = playlists[id]
                    base?.let { p -> recommended[id]?.let { r -> JSONObject(p.toString()).apply { r.optJSONObject("cover")?.let { c -> put("cover", c) } } } ?: p }
                }
                out += Block(b.optString("id"), title, type, aud, pls, b.optString("next_from"), layout?.optString("name").orEmpty())
            }
            return out
        }

        private fun ids(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.opt(it).toString() }
    }

    private fun catalog(method: String, params: Map<String, String>): Catalog {
        val r = respObj(method, params)
        if (r.length() == 0) AppLog.w("VK", "$method: пустой ответ")
        fun dict(name: String, keyFn: (JSONObject) -> String): Map<String, JSONObject> =
            r.optJSONArray(name).objects().associateBy(keyFn)
        val cat = r.optJSONObject("catalog")
        val sections = cat?.optJSONArray("sections").objects()
        val section = r.optJSONObject("section")
            ?: sections.firstOrNull { it.optString("id") == cat?.optString("default_section") }
            ?: sections.firstOrNull()
        val recs = dict("recommended_playlists") { "${it.opt("owner_id")}_${it.opt("id")}" }
        val audios = HashMap<String, JSONObject>(dict("audios") { "${it.opt("owner_id")}_${it.opt("id")}" })
        // Рекомендованные плейлисты приносят свои треки внутри.
        recs.values.forEach { rp -> rp.optJSONArray("audios").objects().forEach { a -> audios.putIfAbsent("${a.opt("owner_id")}_${a.opt("id")}", a) } }
        return Catalog(
            audios = audios,
            playlists = dict("playlists") { "${it.opt("owner_id")}_${it.opt("id")}" },
            artists = dict("artists") { it.optString("id").ifBlank { it.optString("domain") } },
            sections = sections, section = section, recommended = recs, block = r.optJSONObject("block"),
        )
    }

    private class Cached(val explore: List<Block>, val general: List<Block>)

    @Volatile private var catalogCache: Cached? = null
    @Volatile private var catalogCacheAt = 0L

    /** Разделы «Обзор» и «Моя музыка» каталога: из них берутся чарт, новинки, «Для вас» и история. Кэш на 3 минуты. */
    private fun home(force: Boolean = false): Cached {
        val now = System.currentTimeMillis()
        catalogCache?.let { if (!force && now - catalogCacheAt < 3 * 60_000L) return it }
        val me = if (myId != 0L) myId else account().uid
        fun load(section: String): List<Block> = runCatching {
            val d = catalog("catalog.getAudio", mapOf(
                "owner_id" to me.toString(), "url" to "https://vk.com/audios$me?section=$section",
                "need_blocks" to "1", "extended" to "1", "fields" to "photo_50,photo_100",
            ))
            // Разделы могут идти несколькими вкладками: берём блоки всех, что пришли с содержимым.
            val first = d.blocks()
            if (first.isNotEmpty()) first else d.sections.flatMap { d.blocksOf(it) }
        }.onFailure { AppLog.w("VK", "каталог ($section) не загрузился: ${it.message}") }.getOrDefault(emptyList())
        val c = Cached(explore = load("explore"), general = load("general"))
        // Пустой каталог (сбой сети, капча, отказ) не запоминаем: иначе «Повторить» три минуты возвращало бы тот же пустой ответ.
        if (c.explore.isNotEmpty() || c.general.isNotEmpty()) {
            catalogCache = c
            catalogCacheAt = now
        }
        return c
    }

    /** Дочитывает блок «Показать все»: порциями по next_from, пока не наберётся [max] или не кончится. */
    private fun expand(b: Block, max: Int): List<JSONObject> {
        val out = ArrayList(b.audios)
        var from = b.nextFrom
        var guard = 0
        while (from.isNotEmpty() && out.size < max && guard < 10 && b.id.isNotBlank()) {
            guard++
            val d = runCatching { catalog("catalog.getBlockItems", mapOf("block_id" to b.id, "start_from" to from, "extended" to "1", "fields" to "photo_50,photo_100")) }.getOrNull() ?: break
            val ids = d.block?.optJSONArray("audios_ids")
            val more = if (ids != null) (0 until ids.length()).mapNotNull { d.audios[ids.opt(it).toString()] } else d.audios.values.toList()
            if (more.isEmpty()) break
            out += more.filter { m -> out.none { it.opt("id") == m.opt("id") && it.opt("owner_id") == m.opt("owner_id") } }
            from = d.block?.optString("next_from") ?: d.section?.optString("next_from").orEmpty()
        }
        return out
    }

    private val chartRe = Regex("чарт|топ|популяр|chart|top|hits|хит", RegexOption.IGNORE_CASE)
    private val newRe = Regex("новинк|новые|релиз|new|свеж", RegexOption.IGNORE_CASE)
    private val historyRe = Regex("недавн|истори|прослушан|recent|history|listened", RegexOption.IGNORE_CASE)

    override fun chart(): List<Track> {
        val viaCatalog = runCatching {
            val blocks = home().explore.filter { it.type == "music_audios" }
            val b = blocks.firstOrNull { chartRe.containsMatchIn(it.title) } ?: blocks.firstOrNull { it.audios.size >= 10 } ?: blocks.firstOrNull()
            if (b == null) emptyList() else expand(b, 100).mapNotNull { parseTrack(it) }
        }.getOrDefault(emptyList())
        if (viaCatalog.isNotEmpty()) return viaCatalog
        // Каталога нет: чарт — «популярное» VK (audio.getPopular).
        val r = resp("audio.getPopular", mapOf("count" to "100", "only_eng" to "0"))
        val arr = (r as? JSONArray) ?: (r as? JSONObject)?.optJSONArray("items")
        return notDisliked(parseTracks(arr))
    }

    override fun nativeNewReleases(): NativeReleases? {
        val h = home()
        val all = h.explore + h.general
        // Блок ищем по названию и по содержимому (а не только по типу): VK называет и размечает его по-разному.
        val albumBlock = all.firstOrNull {
            (it.type == "music_playlists" || it.playlists.isNotEmpty()) && it.type != "music_recommended_playlists" && newRe.containsMatchIn(it.title)
        }
        val trackBlock = all.firstOrNull { (it.type == "music_audios" || it.audios.isNotEmpty()) && newRe.containsMatchIn(it.title) }
        if (albumBlock == null && trackBlock == null) {
            AppLog.w("VK", "Новинки: в каталоге нет подходящего блока (блоков: ${all.size}): " + all.take(30).joinToString("; ") { "«${it.title}» [${it.type}]" })
            return null
        }
        val albums = albumBlock?.playlists.orEmpty().map { parseAlbum(it) }
        val tracks = trackBlock?.let { runCatching { expand(it, 60) }.getOrDefault(it.audios) }.orEmpty().mapNotNull { parseTrack(it) }
        if (albums.isEmpty() && tracks.isEmpty()) {
            AppLog.w("VK", "Новинки: блок найден, но пуст (альбомы «${albumBlock?.title}», треки «${trackBlock?.title}»)")
            return null
        }
        return NativeReleases(albums, tracks)
    }

    override fun recommendedShelves(): List<PlaylistShelf> {
        val h = home()
        val out = ArrayList<PlaylistShelf>()
        // Сначала блоки «рекомендованные плейлисты» (собраны алгоритмами: «Плейлист дня», «Дежавю», «Тайник»…), затем обычные подборки.
        val sorted = (h.general + h.explore).filter { it.playlists.isNotEmpty() }
            .sortedBy { if (it.type == "music_recommended_playlists" || it.layout == "recomms_slider") 0 else 1 }
        for (b in sorted) {
            if (newRe.containsMatchIn(b.title) && b.type != "music_recommended_playlists") continue
            val pls = b.playlists.filter { it.optInt("type") != 1 }.map { parsePlaylist(it) }
            if (pls.isEmpty()) continue
            out += PlaylistShelf(b.id.ifBlank { b.title }, b.title.ifBlank { "Для вас" }, pls)
            if (out.size >= 8) break
        }
        return out
    }

    override fun musicHistory(limit: Int): List<Track> = musicHistoryFull(limit).tracks

    override fun musicHistoryFull(limit: Int): RemoteHistory {
        val h = home()
        val b = (h.general + h.explore).firstOrNull { it.type == "music_audios" && historyRe.containsMatchIn(it.title) }
            ?: run {
                AppLog.i("VK", "В каталоге нет блока истории — показывается локальная история приложения")
                return RemoteHistory(emptyList(), emptyList())
            }
        val tracks = expand(b, limit).mapNotNull { parseTrack(it) }.take(limit)
        return RemoteHistory(tracks, emptyList())
    }
}
