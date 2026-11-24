package app.ymusic.m3e.data

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.net.URLEncoder

/** Приставка id треков SoundCloud: «sc_<номер>». Так id не пересекаются с Яндексом и VK и по ним выбирается сервис. */
object ScIds {
    const val PREFIX = "sc_"

    fun isSc(id: String?): Boolean = id != null && id.startsWith(PREFIX)

    fun track(id: Long): String = "$PREFIX$id"

    /** «sc_123», «sc_123:albumId» или «123» → «123». */
    fun raw(id: String): String = id.removePrefix(PREFIX).substringBefore(':')
}

/**
 * Разрешение автора на скачивание трека SoundCloud (поле downloadable в ответе API). Заполняется при разборе треков,
 * по нему интерфейс заранее знает, что у трека скачивание запрещено. Пока флаг неизвестен — скачивание пробуем:
 * окончательно разрешение проверяется перед загрузкой.
 */
object ScDownloads {
    const val FORBIDDEN = "Автор запретил скачивание"
    private val flags = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun note(trackId: String, allowed: Boolean) { flags[trackId] = allowed }

    /** true — автор точно запретил скачивание (или у трека закончились разрешённые скачивания). */
    fun isForbidden(trackId: String): Boolean = flags[trackId] == false
}

/**
 * Миксы SoundCloud. Их id лежит в [PlaylistInfo.ownerUid] и не пересекается с числовыми id плейлистов:
 *  • «sc_mix:track:<id трека>» — микс по треку (сам трек и похожие на него);
 *  • «sc_mix:artist:<id исполнителя>» — микс исполнителя (его треки и похожие);
 *  • «sc_mix:url:<hex ссылки>» — готовый микс SoundCloud (soundcloud.com/discover/sets/…), открытый по ссылке.
 * Ссылка записана шестнадцатеричной строкой, чтобы в id не было символов, ломающих разбор станции волны.
 */
object ScMix {
    const val PREFIX = "sc_mix:"

    fun isMix(id: String?): Boolean = id != null && id.startsWith(PREFIX)

    fun track(trackRawId: String): String = "${PREFIX}track:$trackRawId"
    fun artist(userRawId: String): String = "${PREFIX}artist:$userRawId"
    fun daily(): String = "${PREFIX}daily:me"
    fun fresh(): String = "${PREFIX}fresh:me"
    fun url(link: String): String = "${PREFIX}url:" + link.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    /** «track», «artist» или «url» и аргумент после него. */
    fun parts(id: String): Pair<String, String> {
        val rest = id.removePrefix(PREFIX)
        return rest.substringBefore(':') to rest.substringAfter(':', "")
    }

    fun decodeUrl(hex: String): String =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }.toString(Charsets.UTF_8)
}

/** Что нашлось по ссылке SoundCloud. */
sealed interface ScResolved {
    class Single(val track: Track) : ScResolved
    class ListRes(val info: PlaylistInfo, val tracks: List<Track>) : ScResolved
    class UserRes(val artist: ArtistInfo) : ScResolved
}

private const val API = "https://api.soundcloud.com"

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** Строка поля; JSON null и отсутствие поля дают пустую строку (а не «null»). */
private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k)

/** Стандартные картинки SoundCloud маленькие (-large, 100×100): просим версию побольше. */
private fun bigArt(u: String): String = u.replace("-large.", "-t500x500.")

/** Тело запроса, которое читает файл потоком (без загрузки целиком в память) и сообщает, сколько уже отправлено. */
private class StreamRequestBody(
    private val type: MediaType?,
    private val length: Long,
    private val open: () -> InputStream,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = type
    override fun contentLength(): Long = length
    override fun writeTo(sink: BufferedSink) {
        open().use { input ->
            val buf = ByteArray(64 * 1024)
            var sent = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                sink.write(buf, 0, n)
                sent += n
                if (length > 0) onProgress((sent.toFloat() / length).coerceIn(0f, 1f))
            }
        }
    }
}

/**
 * SoundCloud: вход и профиль, поиск, воспроизведение, лайки, плейлисты, исполнители и открытие ссылок.
 * Публичные данные работают и без аккаунта SoundCloud (по токену самого приложения), личные — только с ним.
 */
class SoundCloudApi(private val tokens: TokenStore) : MusicApi {
    override val caps = ApiCaps(
        dislikes = false, podcasts = false, chart = true, previewLimit = false, connect = false,
        mixSettings = false, recommendations = true, nativeNewReleases = true, waveByEntity = true,
        playlistVisibility = true,
    )
    override var highQuality: () -> Boolean = { false }
    override val plusConfirmedByStream: Boolean = false

    @Volatile private var appToken: ScTokens? = null

    /** Треки, которые сервис отдаёт только 30-секундным фрагментом (без Go+). */
    private val previewIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** Найденная полная версия для превью-трека: id превью → id полной версии (пустая строка — не нашли). */
    private val fullAlt = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Подборки (миксы, похожие, чарт, новинки) без треков-фрагментов: в волне они только обрывают музыку на 30-й секунде. */
    private fun List<Track>.fullOnly(): List<Track> = filterNot { it.id in previewIds }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("SoundCloud: это действие пока недоступно")

    override fun resetSession() { appToken = null }

    // ---- Токены и запросы ----

    private fun activeSc(): StoredAccount? = tokens.active?.takeIf { it.provider == Provider.SOUNDCLOUD }

    @Synchronized
    private fun clientToken(): String {
        appToken?.takeIf { it.expiresAt > System.currentTimeMillis() + 60_000L }?.let { return it.access }
        val t = SoundCloudAuth.clientCredentials()
        appToken = t
        return t.access
    }

    private fun refreshAccount(acc: StoredAccount): String {
        val t = SoundCloudAuth.refresh(acc.refreshToken)
        tokens.updateSoundCloudTokens(acc.token, t)
        return t.access
    }

    /**
     * Запрос к API. Токен берётся у активного аккаунта SoundCloud, при его отсутствии — у любого другого сохранённого,
     * а если аккаунтов нет совсем — токен приложения. [personal] — запрос про «мои» данные: без активного аккаунта SoundCloud он невозможен.
     */
    private fun send(method: String, url: String, personal: Boolean, json: String? = null): String {
        val acc = activeSc() ?: if (personal) error("Аккаунт SoundCloud не выбран") else tokens.accounts.firstOrNull { it.provider == Provider.SOUNDCLOUD }
        if (acc == null) {
            return try {
                SoundCloudAuth.call(method, url, clientToken(), json)
            } catch (e: IOException) {
                if (e.message?.contains("HTTP 401") != true) throw e
                appToken = null
                SoundCloudAuth.call(method, url, clientToken(), json)
            }
        }
        var access = acc.accessToken
        if (acc.refreshToken.isNotBlank() && acc.expiresAt in 1..(System.currentTimeMillis() + 60_000L)) access = refreshAccount(acc)
        return try {
            SoundCloudAuth.call(method, url, access, json)
        } catch (e: IOException) {
            if (e.message?.contains("HTTP 401") != true || acc.refreshToken.isBlank()) throw e
            SoundCloudAuth.call(method, url, refreshAccount(acc), json)
        }
    }

    private fun get(url: String, personal: Boolean = false): String = send("GET", url, personal)

    // ---- Загрузка своего трека ----

    /**
     * Загружает аудиофайл на SoundCloud в аккаунт пользователя (POST /tracks). Трек сразу виден всем, если [isPublic],
     * иначе доступен только владельцу и тем, у кого есть секретная ссылка.
     * [open] отдаёт новый поток файла при каждом вызове (нужно для повтора запроса после обновления токена);
     * [length] — размер файла в байтах (-1, если неизвестен); [onProgress] получает долю 0..1.
     */
    fun uploadTrack(
        title: String, description: String, genre: String, tags: String, isPublic: Boolean,
        fileName: String, mime: String, length: Long, open: () -> InputStream, onProgress: (Float) -> Unit,
        artwork: ByteArray? = null,
    ): Track {
        val acc = activeSc() ?: error("Аккаунт SoundCloud не выбран")
        var access = acc.accessToken
        if (acc.refreshToken.isNotBlank() && acc.expiresAt in 1..(System.currentTimeMillis() + 60_000L)) access = refreshAccount(acc)
        // Загрузка долгая: у общего клиента короткие таймауты записи и чтения, для файла они свои.
        val http = SoundCloudAuth.client().newBuilder()
            .writeTimeout(5, TimeUnit.MINUTES).readTimeout(3, TimeUnit.MINUTES).callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        fun attempt(token: String): String {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("track[title]", title)
                .addFormDataPart("track[sharing]", if (isPublic) "public" else "private")
            if (description.isNotBlank()) form.addFormDataPart("track[description]", description)
            if (genre.isNotBlank()) form.addFormDataPart("track[genre]", genre)
            if (tags.isNotBlank()) form.addFormDataPart("track[tag_list]", tags)
            // Обложка (JPEG): без неё SoundCloud показывает аватар.
            if (artwork != null) form.addFormDataPart("track[artwork_data]", "cover.jpg", artwork.toRequestBody("image/jpeg".toMediaType()))
            form.addFormDataPart(
                "track[asset_data]", fileName,
                StreamRequestBody(mime.toMediaTypeOrNull(), length, open, onProgress),
            )
            val req = Request.Builder().url("$API/tracks")
                .header("accept", "application/json; charset=utf-8")
                .header("Authorization", "OAuth $token")
                .post(form.build()).build()
            http.newCall(req).execute().use { r ->
                val body = r.body?.string().orEmpty()
                // Ответ сервера пишем в журнал: по нему видно, принял ли SoundCloud трек и в каком он состоянии.
                AppLog.i("ScUpload", "POST /tracks → HTTP ${r.code}: ${body.take(900)}")
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${SoundCloudAuth.errorText(body)}")
                return body
            }
        }
        val body = try {
            attempt(access)
        } catch (e: IOException) {
            if (e.message?.contains("HTTP 401") != true || acc.refreshToken.isBlank()) throw e
            attempt(refreshAccount(acc))
        }
        return toTrack(JSONObject(body)) ?: throw IOException("SoundCloud принял файл, но не вернул данные трека")
    }

    // ---- Редактирование своих треков и альбомов ----

    /** Поля своего трека или альбома, которые можно изменить. [tags] — строка тегов в формате SoundCloud. */
    class ScEditable(
        val title: String, val description: String, val genre: String, val tags: String,
        val isPublic: Boolean, val coverUrl: String?,
    )

    /** Текущие значения полей своего трека ([album] = false) или альбома (плейлиста) для окна редактирования. */
    fun editable(id: String, album: Boolean): ScEditable {
        val raw = ScIds.raw(id)
        val url = if (album) "$API/playlists/$raw?show_tracks=false" else "$API/tracks/$raw"
        val o = JSONObject(get(url, personal = true))
        return ScEditable(
            title = o.str("title"), description = o.str("description"), genre = o.str("genre"), tags = o.str("tag_list"),
            isPublic = o.str("sharing") != "private",
            coverUrl = o.str("artwork_url").takeIf { it.isNotBlank() }?.let { bigArt(it) },
        )
    }

    /** Меняет свой трек. Пустое описание, жанр или теги очищают поле. [artwork] — новая обложка (JPEG); null — оставить как есть. */
    fun editTrack(id: String, title: String, description: String, genre: String, tags: String, isPublic: Boolean, artwork: ByteArray? = null): Track {
        val raw = ScIds.raw(id)
        val url = "$API/tracks/$raw"
        val body = if (artwork == null) {
            val t = JSONObject().put("title", title).put("description", description).put("genre", genre)
                .put("tag_list", tags).put("sharing", if (isPublic) "public" else "private")
            send("PUT", url, true, JSONObject().put("track", t).toString())
        } else {
            val acc = activeSc() ?: error("Аккаунт SoundCloud не выбран")
            var access = acc.accessToken
            if (acc.refreshToken.isNotBlank() && acc.expiresAt in 1..(System.currentTimeMillis() + 60_000L)) access = refreshAccount(acc)
            val http = SoundCloudAuth.client().newBuilder().writeTimeout(2, TimeUnit.MINUTES).readTimeout(2, TimeUnit.MINUTES).build()
            fun attempt(token: String): String {
                val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("track[title]", title)
                    .addFormDataPart("track[description]", description)
                    .addFormDataPart("track[genre]", genre)
                    .addFormDataPart("track[tag_list]", tags)
                    .addFormDataPart("track[sharing]", if (isPublic) "public" else "private")
                    .addFormDataPart("track[artwork_data]", "cover.jpg", artwork.toRequestBody("image/jpeg".toMediaType()))
                val req = Request.Builder().url(url)
                    .header("accept", "application/json; charset=utf-8")
                    .header("Authorization", "OAuth $token")
                    .put(form.build()).build()
                http.newCall(req).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    AppLog.i("ScEdit", "PUT /tracks/$raw → HTTP ${r.code}: ${text.take(400)}")
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${SoundCloudAuth.errorText(text)}")
                    return text
                }
            }
            try {
                attempt(access)
            } catch (e: IOException) {
                if (e.message?.contains("HTTP 401") != true || acc.refreshToken.isBlank()) throw e
                attempt(refreshAccount(acc))
            }
        }
        return toTrack(JSONObject(body)) ?: throw IOException("SoundCloud не вернул изменённый трек")
    }

    /** Меняет свой альбом (плейлист): название, описание, жанр, теги и видимость. Состав треков не трогается. */
    fun editAlbum(id: String, title: String, description: String, genre: String, tags: String, isPublic: Boolean): AlbumInfo {
        val body = plBody {
            put("title", title); put("description", description); put("genre", genre); put("tag_list", tags)
            put("sharing", if (isPublic) "public" else "private")
        }
        val o = JSONObject(send("PUT", "$API/playlists/${ScIds.raw(id)}", true, body))
        if (o.optLong("id") == 0L) throw IOException("SoundCloud не вернул изменённый альбом")
        return toAlbum(o)
    }

    /**
     * Что сейчас с загруженным треком на сервере: «processing» (обрабатывается), «finished» (готов), «failed» (SoundCloud не смог
     * его обработать), «gone» (трека нет — удалён, например по жалобе на авторские права) или null, если узнать не удалось.
     */
    fun uploadedTrackState(trackId: String): String? {
        val raw = ScIds.raw(trackId)
        return try {
            val body = get("$API/tracks/$raw", personal = true)
            val o = JSONObject(body)
            val state = o.str("state").ifBlank { "finished" }
            AppLog.i("ScUpload", "GET /tracks/$raw → state=$state sharing=${o.str("sharing")} access=${o.str("access")} policy=${o.str("policy")} url=${o.str("permalink_url")}")
            // access = blocked — трек заблокирован (чаще всего из-за авторских прав).
            if (o.str("access") == "blocked") "gone" else state
        } catch (e: IOException) {
            val m = e.message.orEmpty()
            AppLog.w("ScUpload", "GET /tracks/$raw: $m")
            if (m.startsWith("HTTP 404")) "gone" else null
        }
    }

    /**
     * Токен для запросов плеера к api.soundcloud.com (плейлист HLS и сегменты): без заголовка Authorization SoundCloud
     * отвечает 429. Берётся тот же токен, что и у обычных запросов (аккаунт или токен приложения); при ошибке — null.
     */
    fun playbackAuth(): String? = runCatching {
        val acc = activeSc() ?: tokens.accounts.firstOrNull { it.provider == Provider.SOUNDCLOUD }
        if (acc == null) clientToken()
        else if (acc.refreshToken.isNotBlank() && acc.expiresAt in 1..(System.currentTimeMillis() + 60_000L)) refreshAccount(acc)
        else acc.accessToken
    }.getOrNull()

    /** Ошибки сервера (кроме 401) в списках библиотеки не должны ломать загрузку всего профиля; сетевые ошибки пробрасываются. */
    private inline fun <T> soft(empty: T, block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        val m = e.message.orEmpty()
        if (m.startsWith("HTTP ") && !m.startsWith("HTTP 401")) empty else throw e
    }

    /** Ответ-список: либо массив, либо {collection, next_href}. */
    private fun parseList(body: String): Pair<JSONArray, String?> {
        val t = body.trimStart()
        if (t.startsWith("[")) return JSONArray(t) to null
        val o = JSONObject(t)
        return (o.optJSONArray("collection") ?: JSONArray()) to o.str("next_href").ifBlank { null }
    }

    /** Читает страницы по next_href, пока не наберётся [limit] элементов. */
    private fun <T> paged(firstUrl: String, personal: Boolean, limit: Int, map: (JSONObject) -> T?): List<T> {
        val out = ArrayList<T>()
        var url: String? = firstUrl
        var pages = 0
        while (url != null && out.size < limit && pages < 25) {
            pages++
            val (arr, next) = parseList(get(url, personal))
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i)?.let(map) ?: continue
                out.add(item)
            }
            url = next
        }
        return if (out.size > limit) out.subList(0, limit).toList() else out
    }

    // ---- Разбор ответов ----

    private fun toTrack(o: JSONObject): Track? {
        val id = o.optLong("id")
        if (id == 0L || o.str("access") == "blocked") return null
        // access = "preview": SoundCloud (Go+-трек) отдаёт без подписки только 30 секунд — запоминаем, чтобы подобрать полную версию.
        if (o.str("access") == "preview" || o.str("policy") == "SNIP") previewIds.add(ScIds.track(id)) else previewIds.remove(ScIds.track(id))
        // Автор разрешает скачивание только если downloadable = true (и у трека остались скачивания).
        if (o.has("downloadable")) ScDownloads.note(ScIds.track(id), o.optBoolean("downloadable") && o.optBoolean("has_downloads_left", true))
        val user = o.optJSONObject("user")
        val artist = user?.str("username").orEmpty()
        val uid = user?.optLong("id") ?: 0L
        val avatar = user?.str("avatar_url").orEmpty().takeIf { it.isNotBlank() }?.let { bigArt(it) }
        val art = o.str("artwork_url").takeIf { it.isNotBlank() }?.let { bigArt(it) } ?: avatar
        return Track(
            id = ScIds.track(id),
            albumId = null,
            title = o.str("title"),
            artists = artist,
            coverUri = art,
            durationMs = o.optLong("duration"),
            artistRefs = if (uid != 0L) listOf(ArtistRef(ScIds.PREFIX + uid, artist, avatar)) else emptyList(),
            year = o.optInt("release_year"),
            label = o.str("label_name").ifBlank { null },
            genre = o.str("genre").ifBlank { null },
            // Для SoundCloud в этом поле лежит ссылка на трек (по ней работает «Поделиться»).
            accessKey = o.str("permalink_url").ifBlank { null },
        )
    }

    private fun toTracks(arr: JSONArray?): List<Track> =
        if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { toTrack(it) } }

    private fun toPlaylist(o: JSONObject): PlaylistInfo {
        val user = o.optJSONObject("user")
        val firstArt = o.optJSONArray("tracks")?.optJSONObject(0)?.str("artwork_url").orEmpty()
        val art = o.str("artwork_url").ifBlank { firstArt }.ifBlank { user?.str("avatar_url").orEmpty() }
        return PlaylistInfo(
            kind = 0,
            // У SoundCloud номер плейлиста не помещается в Int, поэтому весь id лежит в ownerUid, а kind всегда 0.
            ownerUid = ScIds.PREFIX + o.optLong("id"),
            title = o.str("title"),
            trackCount = o.optInt("track_count", o.optJSONArray("tracks")?.length() ?: 0),
            coverUri = art.takeIf { it.isNotBlank() }?.let { bigArt(it) },
            visibility = o.str("sharing").ifBlank { null },
            ownerLogin = user?.str("username")?.ifBlank { null },
        )
    }

    private fun toAlbum(o: JSONObject, withTracks: Boolean = false): AlbumInfo {
        val p = toPlaylist(o)
        val user = o.optJSONObject("user")
        val refs = user?.let { u ->
            listOf(ArtistRef(ScIds.PREFIX + u.optLong("id"), u.str("username"), u.str("avatar_url").takeIf { it.isNotBlank() }?.let { bigArt(it) }))
        }.orEmpty()
        return AlbumInfo(
            id = p.ownerUid, title = p.title, artists = p.ownerLogin.orEmpty(), artistRefs = refs, coverUri = p.coverUri,
            year = o.str("created_at").take(4).toIntOrNull() ?: 0, trackCount = p.trackCount,
            tracks = if (withTracks) toTracks(o.optJSONArray("tracks")) else emptyList(),
        )
    }

    private fun toArtist(o: JSONObject, tracks: List<Track> = emptyList(), albums: List<AlbumInfo> = emptyList()): ArtistInfo =
        ArtistInfo(
            id = ScIds.PREFIX + o.optLong("id"),
            name = o.str("username").ifBlank { o.str("permalink") },
            coverUri = o.str("avatar_url").takeIf { it.isNotBlank() }?.let { bigArt(it) },
            tracks = tracks, albums = albums,
            followers = o.optLong("followers_count", 0L).coerceAtLeast(0L),
            description = o.str("description").trim(),
        )

    // ---- Аккаунт ----

    /** Профиль активного аккаунта SoundCloud; при истёкшем токене обновляет его по refresh-токену. */
    override fun account(): Account {
        val stored = tokens.active?.takeIf { it.provider == Provider.SOUNDCLOUD } ?: error("Аккаунт SoundCloud не выбран")
        val key = stored.token
        var access = stored.accessToken
        var refresh = stored.refreshToken
        // Токен почти истёк — обновляем заранее (за минуту до конца).
        if (refresh.isNotBlank() && stored.expiresAt in 1..(System.currentTimeMillis() + 60_000L)) {
            val t = SoundCloudAuth.refresh(refresh)
            tokens.updateSoundCloudTokens(key, t)
            access = t.access; refresh = t.refresh
        }
        val profile = try {
            SoundCloudAuth.fetchMe(access)
        } catch (e: IOException) {
            if (e.message?.contains("HTTP 401") != true || refresh.isBlank()) throw e
            val t = SoundCloudAuth.refresh(refresh)
            tokens.updateSoundCloudTokens(key, t)
            SoundCloudAuth.fetchMe(t.access)
        }
        tokens.updateSoundCloudProfile(key, profile)
        return Account(
            uid = profile.id,
            login = profile.permalink,
            name = profile.username,
            hasPlus = false,
            avatarUrl = profile.avatarUrl,
            provider = Provider.SOUNDCLOUD,
        )
    }

    // ---- Лайки ----

    override fun likedTracks(uid: String, limit: Int): List<Track> = soft<List<Track>>(emptyList()) {
        paged("$API/me/likes/tracks?limit=200&linked_partitioning=true", true, limit) { toTrack(it) }
    }

    override fun tracksByIds(keys: List<String>): List<Track> {
        val ids = keys.filter { ScIds.isSc(it) || it.all { c -> c.isDigit() } }.map { ScIds.raw(it) }.distinct()
        if (ids.isEmpty()) return emptyList()
        val found = HashMap<String, Track>()
        ids.chunked(50).forEach { chunk ->
            val (arr, _) = parseList(get("$API/tracks?ids=${chunk.joinToString(",")}"))
            toTracks(arr).forEach { found[ScIds.raw(it.id)] = it }
        }
        return ids.mapNotNull { found[it] }
    }

    override fun likeTrack(uid: String, t: Track) { send("POST", "$API/likes/tracks/${ScIds.raw(t.id)}", true) }
    override fun unlikeTrack(uid: String, t: Track) { send("DELETE", "$API/likes/tracks/${ScIds.raw(t.id)}", true) }
    override fun dislikeTrack(uid: String, t: Track) { unsupported() }
    override fun undislikeTrack(uid: String, t: Track) { unsupported() }
    override fun dislikedTrackIds(uid: String): Set<String> = emptySet()

    // ---- Плейлисты ----

    override fun playlists(uid: String): List<PlaylistInfo> = soft<List<PlaylistInfo>>(emptyList()) {
        val mine = uid.isBlank() || activeSc()?.uid?.toString() == uid
        val url = if (mine) "$API/me/playlists?show_tracks=false&limit=200&linked_partitioning=true"
        else "$API/users/${ScIds.raw(uid)}/playlists?show_tracks=false&limit=200&linked_partitioning=true"
        paged(url, mine, 400) { toPlaylist(it) }
    }

    override fun playlistInfo(ownerUid: String, kind: Int): PlaylistInfo {
        if (ScMix.isMix(ownerUid)) return mixInfo(ownerUid)
        return toPlaylist(JSONObject(get("$API/playlists/${ScIds.raw(ownerUid)}?show_tracks=false")))
    }

    override fun playlistInfoByUuid(uuid: String): PlaylistInfo = unsupported()
    override fun setPlaylistVisibility(ownerUid: String, kind: Int, isPublic: Boolean) {
        send("PUT", "$API/playlists/${ScIds.raw(ownerUid)}", true, plBody { put("sharing", if (isPublic) "public" else "private") })
    }

    override fun playlistTracks(ownerUid: String, kind: Int): List<Track> {
        if (ScMix.isMix(ownerUid)) return mixTracks(ownerUid)
        return paged("$API/playlists/${ScIds.raw(ownerUid)}/tracks?limit=200&linked_partitioning=true", false, Int.MAX_VALUE) { toTrack(it) }
    }

    // ---- Миксы ----

    /** Название и обложка микса: для миксов по треку и исполнителю берутся у самого трека / исполнителя. */
    private fun mixInfo(id: String): PlaylistInfo {
        val (kind, arg) = ScMix.parts(id)
        return when (kind) {
            "track" -> {
                val t = runCatching { tracksByIds(listOf(arg)).firstOrNull() }.getOrNull()
                PlaylistInfo(0, id, "Микс: " + (t?.title ?: "по треку"), 50, t?.coverUri, null, t?.artists?.ifBlank { null })
            }
            "artist" -> {
                val u = runCatching { JSONObject(get("$API/users/$arg")) }.getOrNull()
                PlaylistInfo(0, id, "Микс: " + (u?.str("username")?.ifBlank { null } ?: "исполнитель"), 50,
                    u?.str("avatar_url")?.takeIf { it.isNotBlank() }?.let { bigArt(it) }, null, null)
            }
            "daily" -> PlaylistInfo(0, id, "Микс дня", 50, null, null, "SoundCloud")
            "fresh" -> PlaylistInfo(0, id, "Микс новинок", 50, null, null, "SoundCloud")
            else -> PlaylistInfo(0, id, "Микс SoundCloud", 0, null, null, null)
        }
    }

    /** Треки микса. Миксы по треку и исполнителю собираются из похожих треков SoundCloud, готовые миксы читаются по ссылке. */
    private fun mixTracks(id: String): List<Track> {
        val (kind, arg) = ScMix.parts(id)
        return when (kind) {
            "track" -> {
                val first = runCatching { tracksByIds(listOf(arg)) }.getOrDefault(emptyList())
                (first + related(arg)).distinctBy { it.id }
            }
            "artist" -> {
                val own = runCatching { artistAllTracks(arg) }.getOrDefault(emptyList()).shuffled().take(30)
                (own + own.take(3).flatMap { related(it.id) }).distinctBy { it.id }
            }
            // Личные миксы: «Микс дня» — лайки вперемешку с похожими треками, «Микс новинок» — свежее от подписок.
            "daily" -> defaultWave(null).distinctBy { it.id }
            "fresh" -> runCatching { nativeNewReleases()?.tracks }.getOrNull().orEmpty().distinctBy { it.id }
            "url" -> systemPlaylistTracks(JSONObject(get("$API/resolve?url=${enc(ScMix.decodeUrl(arg))}")))
            else -> emptyList()
        }
    }

    /**
     * Треки готового микса SoundCloud (system-playlist): в ответе они могут лежать «заглушками» только с номером —
     * такие догружаются отдельным запросом, порядок микса сохраняется.
     */
    private fun systemPlaylistTracks(o: JSONObject): List<Track> {
        val arr = o.optJSONArray("tracks") ?: return emptyList()
        val items = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optLong("id") != 0L }
        val byId = HashMap<String, Track>()
        items.filter { it.str("title").isNotBlank() }.forEach { j -> toTrack(j)?.let { byId[it.id] = it } }
        val missing = items.filter { it.str("title").isBlank() }.map { it.optLong("id").toString() }
        if (missing.isNotEmpty()) runCatching { tracksByIds(missing) }.getOrDefault(emptyList()).forEach { byId[it.id] = it }
        return items.mapNotNull { byId[ScIds.track(it.optLong("id"))] }
    }

    /** Полки миксов для главной: по недавно лайкнутым трекам и по их исполнителям. Без аккаунта и лайков миксов нет. */
    private fun mixShelves(): List<PlaylistShelf> {
        val likes = runCatching { likedTracks("", 60) }.getOrDefault(emptyList())
        val out = ArrayList<PlaylistShelf>()
        // Личные миксы есть всегда (без лайков «Микс дня» собирается из популярного).
        out += PlaylistShelf("sc_mix_mine", "Ваши миксы", listOf(
            PlaylistInfo(0, ScMix.daily(), "Микс дня", 50, likes.firstOrNull()?.coverUri, null, "SoundCloud"),
            PlaylistInfo(0, ScMix.fresh(), "Микс новинок", 50, likes.getOrNull(1)?.coverUri ?: likes.firstOrNull()?.coverUri, null, "SoundCloud"),
        ))
        if (likes.isEmpty()) return out
        val byTrack = likes.take(8).map { t ->
            PlaylistInfo(0, ScMix.track(ScIds.raw(t.id)), "Микс: ${t.title}", 50, t.coverUri, null, t.artists.ifBlank { null })
        }
        if (byTrack.isNotEmpty()) out += PlaylistShelf("sc_mix_tracks", "Миксы по вашим трекам", byTrack)
        val seen = HashSet<String>()
        val byArtist = likes.mapNotNull { t ->
            val ref = t.artistRefs.firstOrNull() ?: return@mapNotNull null
            if (!seen.add(ref.id)) return@mapNotNull null
            PlaylistInfo(0, ScMix.artist(ScIds.raw(ref.id)), "Микс: ${ref.name}", 50, ref.coverUri ?: t.coverUri, null, null)
        }.take(8)
        if (byArtist.isNotEmpty()) out += PlaylistShelf("sc_mix_artists", "Миксы исполнителей", byArtist)
        return out
    }

    /** Тело запроса плейлиста: {"playlist": {…}}. */
    private fun plBody(build: JSONObject.() -> Unit): String = JSONObject().put("playlist", JSONObject().apply(build)).toString()

    private fun trackRefs(ids: List<Long>): JSONArray = JSONArray().also { a -> ids.forEach { a.put(JSONObject().put("id", it)) } }

    /** Номера всех треков плейлиста (включая недоступные для показа): нужны, чтобы изменить состав целиком. */
    private fun playlistTrackIds(ownerUid: String): List<Long> {
        val arr = JSONObject(get("$API/playlists/${ScIds.raw(ownerUid)}?show_tracks=true", true)).optJSONArray("tracks") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optLong("id")?.takeIf { id -> id != 0L } }
    }

    override fun createPlaylist(uid: String, title: String, isPublic: Boolean): PlaylistInfo {
        val body = plBody { put("title", title); put("sharing", if (isPublic) "public" else "private") }
        val o = JSONObject(send("POST", "$API/playlists", true, body))
        if (o.optLong("id") == 0L) error("SoundCloud не создал плейлист")
        return toPlaylist(o)
    }

    override fun renamePlaylist(ownerUid: String, kind: Int, title: String) {
        send("PUT", "$API/playlists/${ScIds.raw(ownerUid)}", true, plBody { put("title", title) })
    }

    override fun deletePlaylist(ownerUid: String, kind: Int) { send("DELETE", "$API/playlists/${ScIds.raw(ownerUid)}", true) }

    override fun addTrackToPlaylist(ownerUid: String, kind: Int, track: Track) {
        val id = ScIds.raw(track.id).toLongOrNull() ?: error("Этот трек нельзя добавить в плейлист SoundCloud")
        val ids = playlistTrackIds(ownerUid)
        if (id in ids) return
        send("PUT", "$API/playlists/${ScIds.raw(ownerUid)}", true, plBody { put("tracks", trackRefs(ids + id)) })
    }

    override fun removeTrackFromPlaylist(ownerUid: String, kind: Int, track: Track) {
        val id = ScIds.raw(track.id).toLongOrNull() ?: return
        val ids = playlistTrackIds(ownerUid)
        if (id !in ids) return
        send("PUT", "$API/playlists/${ScIds.raw(ownerUid)}", true, plBody { put("tracks", trackRefs(ids - id)) })
    }

    // ---- Волна (у SoundCloud нет) ----

    override fun waveTracks(afterTrackId: String?, station: String): WaveBatch {
        val s = station.trim()
        val list: List<Track> = when {
            s.startsWith("track:") -> related(s.removePrefix("track:"))
            s.startsWith("artist:") -> {
                val own = runCatching { artistAllTracks(s.removePrefix("artist:")) }.getOrDefault(emptyList()).shuffled().take(30)
                (own + own.take(2).flatMap { related(it.id) }).distinctBy { it.id }
            }
            s.startsWith("album:") || s.startsWith("playlist:") -> {
                val id = Regex("""sc_mix:(?:track|artist|url|daily|fresh):[0-9A-Za-z]+""").find(s)?.value ?: Regex("""sc_\d+""").find(s)?.value
                val own = if (id != null) runCatching { playlistTracks(id, 0) }.getOrDefault(emptyList()).shuffled().take(30) else emptyList()
                (own + own.take(2).flatMap { related(it.id) }).distinctBy { it.id }
            }
            else -> defaultWave(afterTrackId)
        }
        return WaveBatch(list.take(50), null)
    }

    /** Похожие треки (рекомендации SoundCloud по треку). */
    private fun related(trackId: String): List<Track> {
        if (!ScIds.isSc(trackId) && !trackId.all { it.isDigit() }) return emptyList()
        return runCatching { paged("$API/tracks/${ScIds.raw(trackId)}/related?limit=50&linked_partitioning=true", false, 50) { toTrack(it) } }
            .getOrDefault(emptyList()).fullOnly()
    }

    /**
     * ExWave SoundCloud: у сервиса своей волны нет, поэтому она собирается из источников, выбранных в настройках волны
     * (лайки, похожие, история, подписки, плейлисты, популярное). Треки разных источников чередуются; если ничего не нашлось
     * (например, нет аккаунта) — берётся популярное. После первой порции, если включены «Похожие», продолжение идёт от последнего трека.
     */
    private fun defaultWave(after: String?): List<Track> {
        val src = ScWaveSources.enabled
        if (after != null && ScIds.isSc(after) && ScWaveSource.Related in src) {
            val r = related(after)
            if (r.isNotEmpty()) return r.shuffled()
        }
        val likes = if (ScWaveSource.Likes in src || ScWaveSource.Related in src)
            runCatching { likedTracks("", 150) }.getOrDefault(emptyList()).shuffled() else emptyList()
        val mine = if (ScWaveSource.Likes in src) likes.take(12) else emptyList()
        val rel = if (ScWaveSource.Related in src) likes.take(3).flatMap { related(it.id) }.shuffled() else emptyList()
        val hist = if (ScWaveSource.History in src)
            runCatching { musicHistory(100) }.getOrDefault(emptyList()).fullOnly().shuffled().take(12) else emptyList()
        val follow = if (ScWaveSource.Followings in src)
            runCatching { paged("$API/me/followings/tracks?limit=50&linked_partitioning=true", true, 80) { toTrack(it) } }
                .getOrDefault(emptyList()).fullOnly().shuffled().take(15) else emptyList()
        val lists = if (ScWaveSource.Playlists in src)
            runCatching { likedAlbums("").shuffled().take(3).flatMap { playlistTracks(it.id, 0).shuffled().take(8) } }
                .getOrDefault(emptyList()).fullOnly().shuffled() else emptyList()
        // Свои источники чередуются: свой, потом из другого источника.
        val groups = listOf(mine, rel, hist, follow, lists).filter { it.isNotEmpty() }.map { it.iterator() }
        val out = ArrayList<Track>()
        while (groups.any { it.hasNext() }) groups.forEach { if (it.hasNext()) out += it.next() }
        if (ScWaveSource.Popular in src) {
            // Популярное подмешивается, чтобы каждый запуск отличался; если это единственный источник — берём больше.
            val genres = listOf("pop", "hip-hop", "electronic", "rock", "dance", "indie", "house", "trap").shuffled()
            val take = genres.take(if (out.isEmpty()) 2 else 1)
            val extra = take.flatMap { g ->
                runCatching {
                    genreTracks(g, recent = true).sortedByDescending { it.optLong("playback_count") }.take(30)
                        .mapNotNull { toTrack(it) }.fullOnly().shuffled().take(if (out.isEmpty()) 15 else 6)
                }.getOrDefault(emptyList())
            }.shuffled()
            if (out.isEmpty()) out += extra else extra.forEachIndexed { i, t -> out.add(minOf(out.size, 2 + i * 4), t) }
        }
        AppLog.i("ScWave", "волна: лайки=${mine.size}, похожие=${rel.size}, история=${hist.size}, подписки=${follow.size}, плейлисты=${lists.size}, итого=${out.size}")
        if (out.isEmpty()) {
            return runCatching { chart() }.onFailure { AppLog.w("ScWave", "чарт для волны не загрузился: ${it.javaClass.simpleName}: ${it.message}") }
                .getOrDefault(emptyList()).shuffled()
        }
        return out.distinctBy { it.id }
    }
    override fun waveSettings(): WaveSettings = WaveSettings()
    override fun saveWaveSettings(s: WaveSettings) {}
    override fun waveTrackStarted(trackId: String, batchId: String?, station: String) {}
    override fun waveStarted(station: String) {}

    // ---- Поиск ----

    override fun search(query: String): List<Track> {
        if (query.isBlank()) return emptyList()
        return paged("$API/tracks?q=${enc(query.trim())}&limit=40&linked_partitioning=true", false, 40) { toTrack(it) }
    }

    override fun searchArtists(query: String): List<ArtistInfo> {
        if (query.isBlank()) return emptyList()
        return paged("$API/users?q=${enc(query.trim())}&limit=20&linked_partitioning=true", false, 20) { toArtist(it) }
    }

    override fun searchAlbums(query: String): List<AlbumInfo> {
        if (query.isBlank()) return emptyList()
        return paged("$API/playlists?q=${enc(query.trim())}&show_tracks=false&limit=20&linked_partitioning=true", false, 20) { toAlbum(it) }
    }

    override fun searchPodcasts(query: String): List<AlbumInfo> = emptyList()
    override fun podcastsLanding(): List<AlbumInfo> = emptyList()

    // ---- Воспроизведение ----

    private fun streams(trackId: String): JSONObject = JSONObject(get("$API/tracks/${ScIds.raw(trackId)}/streams"))

    /** Лучший HLS-вариант (m3u8): при высоком качестве AAC 160, иначе MP3 128; Opus — последний запасной вариант. */
    private fun hlsUrl(o: JSONObject): String {
        val order = if (highQuality()) listOf("hls_aac_160_url", "hls_mp3_128_url", "hls_aac_96_url", "hls_opus_64_url")
        else listOf("hls_mp3_128_url", "hls_aac_160_url", "hls_aac_96_url", "hls_opus_64_url")
        return order.firstNotNullOfOrNull { k -> o.str(k).takeIf { it.isNotBlank() } } ?: ""
    }

    private fun progressiveUrl(o: JSONObject): String =
        o.str("http_mp3_128_url").ifBlank { o.str("preview_mp3_128_url") }

    /**
     * Ссылка для воспроизведения: HLS (плеер получает его с типом m3u8, см. AppViewModel.toMediaItem).
     * Если у трека есть только прямой mp3, он заворачивается в одно-сегментный m3u8, чтобы тип источника не менялся.
     */
    override fun streamUrl(trackId: String): String = streamUrlOf(trackId, allowAlt = true)

    /** Нормализованное название для сравнения: без скобок («feat.», «Remix» и т. п.), регистра и знаков. */
    private fun normTitle(s: String): String =
        s.lowercase().replace(Regex("""[\(\[][^\)\]]*[\)\]]"""), " ").replace(Regex("""[^\p{L}\p{N}]+"""), "")

    /**
     * Полная версия трека-фрагмента: тот же трек, загруженный целиком (у исполнителя или в перезаливе). Ищем среди
     * играбельных (access=playable) треков по «исполнитель название»; подходит трек с тем же названием и почти той же длиной
     * (разница до 3% или 4 секунд). Результат запоминается; null — полной версии нет.
     */
    private fun findFullVersion(trackId: String): String? {
        fullAlt[trackId]?.let { return it.ifBlank { null } }
        val me = JSONObject(get("$API/tracks/${ScIds.raw(trackId)}"))
        if (me.str("access") == "playable") { previewIds.remove(trackId); fullAlt[trackId] = ""; return null }
        val title = me.str("title")
        val artist = me.optJSONObject("user")?.str("username").orEmpty()
        val dur = me.optLong("duration")
        val myTitle = normTitle(title)
        val myArtist = normTitle(artist)
        if (myTitle.isBlank() || dur <= 0) { fullAlt[trackId] = ""; return null }
        val tol = maxOf(4_000L, dur * 3 / 100)
        val queries = listOf("$artist $title", title).distinct()
        var best: JSONObject? = null
        var bestScore = Long.MAX_VALUE
        for (q in queries) {
            val list = runCatching {
                paged("$API/tracks?q=${enc(q.trim())}&access=playable&limit=40&linked_partitioning=true", false, 40) { it }
            }.getOrDefault(emptyList())
            for (c in list) {
                val cid = ScIds.track(c.optLong("id"))
                if (cid == trackId || c.str("access") != "playable") continue
                val cTitle = normTitle(c.str("title"))
                val cArtist = normTitle(c.optJSONObject("user")?.str("username").orEmpty())
                val titleOk = cTitle == myTitle || (cTitle.contains(myTitle) && myTitle.length >= 6)
                // Название должно совпасть, а исполнитель — быть в названии трека или в имени загрузившего.
                val artistOk = myArtist.isBlank() || cArtist.contains(myArtist) || myArtist.contains(cArtist) || cTitle.contains(myArtist)
                val diff = kotlin.math.abs(c.optLong("duration") - dur)
                if (!titleOk || !artistOk || diff > tol) continue
                val score = diff + (if (cArtist == myArtist) 0 else 2_000L) + (if (cTitle == myTitle) 0 else 1_000L)
                if (score < bestScore) { bestScore = score; best = c }
            }
            if (best != null) break
        }
        val alt = best?.let { ScIds.track(it.optLong("id")) }
        if (alt != null) toTrack(best!!)
        fullAlt[trackId] = alt.orEmpty()
        return alt
    }

    private fun streamUrlOf(trackId: String, allowAlt: Boolean): String {
        val o = streams(trackId)
        // Только фрагмент (в ответе нет полных потоков или трек помечен как превью): ищем полную версию этого трека.
        val onlyPreview = hlsUrl(o).isBlank() && o.str("http_mp3_128_url").isBlank() && o.str("preview_mp3_128_url").isNotBlank()
        if (allowAlt && (onlyPreview || trackId in previewIds)) {
            val alt = runCatching { findFullVersion(trackId) }.getOrNull()
            if (alt != null) {
                val r = runCatching { streamUrlOf(alt, allowAlt = false) }.getOrNull()
                if (r != null) return r
            }
        }
        val hls = hlsUrl(o)
        if (hls.isNotBlank()) return hls
        val direct = progressiveUrl(o).ifBlank { error("SoundCloud: у этого трека нет доступного потока") }
        val sec = runCatching { JSONObject(get("$API/tracks/${ScIds.raw(trackId)}")).optLong("duration") / 1000.0 }
            .getOrDefault(0.0).let { if (it > 0.0) it else 600.0 }
        val playlist = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:${Math.ceil(sec).toLong()}\n" +
            "#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXTINF:$sec,\n$direct\n#EXT-X-ENDLIST\n"
        return "data:application/vnd.apple.mpegurl;base64," +
            android.util.Base64.encodeToString(playlist.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
    }

    /**
     * Скачивание SoundCloud — только то, что разрешил автор: официальный файл через /tracks/{id}/download у треков с
     * downloadable = true. Потоки для воспроизведения (HLS/mp3) для скачивания НЕ используются. Если автор запретил
     * скачивание, выбрасывается ошибка «Автор запретил скачивание».
     */
    override fun downloadUrl(trackId: String): String = error("SoundCloud: скачивание идёт только через официальный файл автора")

    override fun downloadCustom(trackId: String, dest: File, onProgress: (Float) -> Unit, check: () -> Unit): String? {
        val raw = ScIds.raw(trackId)
        // Свежие данные о треке: автор мог изменить настройки, а в кэше флага может не быть.
        val info = JSONObject(get("$API/tracks/$raw"))
        val allowed = info.optBoolean("downloadable") && info.optBoolean("has_downloads_left", true)
        ScDownloads.note(trackId, allowed)
        if (!allowed) throw IOException(ScDownloads.FORBIDDEN)
        check()
        val token = playbackAuth() ?: error("Аккаунт SoundCloud не выбран")
        // Файл может быть большим (wav/flac): таймаут всего запроса снимаем. Токен при редиректе на CDN OkHttp не передаёт.
        val http = SoundCloudAuth.client().newBuilder().callTimeout(0, TimeUnit.MILLISECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        val req = Request.Builder().url("$API/tracks/$raw/download")
            .header("Authorization", "OAuth $token")
            .build()
        http.newCall(req).execute().use { r ->
            if (r.code == 401 || r.code == 403 || r.code == 404) {
                ScDownloads.note(trackId, false)
                throw IOException(ScDownloads.FORBIDDEN)
            }
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${SoundCloudAuth.errorText(r.body?.string().orEmpty())}")
            val body = r.body ?: error("Пустой ответ сервера")
            val total = body.contentLength()
            var written = 0L
            var lastEmit = 0L
            dest.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        check()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastEmit > 200) { lastEmit = now; onProgress((written.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
            if (written == 0L) error("SoundCloud не отдал файл")
        }
        return sniffExt(dest)
    }

    /** Формат скачанного файла по первым байтам (автор мог загрузить wav, flac, m4a или mp3). */
    private fun sniffExt(f: File): String {
        val h = ByteArray(12)
        val n = f.inputStream().use { it.read(h) }
        fun at(i: Int, s: String) = n >= i + s.length && String(h, i, s.length, Charsets.ISO_8859_1) == s
        return when {
            at(0, "RIFF") -> "wav"
            at(0, "fLaC") -> "flac"
            at(4, "ftyp") -> "m4a"
            at(0, "OggS") -> "ogg"
            else -> "mp3"
        }
    }

    // ---- Исполнители и альбомы (плейлисты) ----

    override fun artist(id: String): ArtistInfo {
        val raw = ScIds.raw(id)
        val u = JSONObject(get("$API/users/$raw"))
        val tracks = runCatching { paged("$API/users/$raw/tracks?limit=50&linked_partitioning=true", false, 50) { toTrack(it) } }.getOrDefault(emptyList())
        val albums = runCatching { artistRecentAlbums(id) }.getOrDefault(emptyList())
        return toArtist(u, tracks, albums)
    }

    override fun artistRecentAlbums(id: String): List<AlbumInfo> =
        paged("$API/users/${ScIds.raw(id)}/playlists?show_tracks=false&limit=50&linked_partitioning=true", false, 50) { toAlbum(it) }

    override fun artistAllTracks(id: String): List<Track> =
        paged("$API/users/${ScIds.raw(id)}/tracks?limit=200&linked_partitioning=true", false, 1000) { toTrack(it) }

    override fun album(id: String): AlbumInfo {
        val o = JSONObject(get("$API/playlists/${ScIds.raw(id)}?show_tracks=false"))
        return toAlbum(o).copy(tracks = playlistTracks(id, 0))
    }

    override fun likedAlbums(uid: String): List<AlbumInfo> = soft<List<AlbumInfo>>(emptyList()) {
        paged("$API/me/likes/playlists?limit=100&linked_partitioning=true", true, 300) { toAlbum(it) }
    }

    override fun likeAlbum(uid: String, albumId: String) { send("POST", "$API/likes/playlists/${ScIds.raw(albumId)}", true) }
    override fun unlikeAlbum(uid: String, albumId: String) { send("DELETE", "$API/likes/playlists/${ScIds.raw(albumId)}", true) }

    /** Свежие треки одного жанра: сначала с отбором по дате (последние 60 дней), без него — если сервис такой отбор не принял. */
    private fun genreTracks(genre: String, recent: Boolean): List<JSONObject> {
        val base = "$API/tracks?genres=${enc(genre)}&limit=50&linked_partitioning=true"
        if (recent) {
            val from = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(60)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val r = runCatching { paged("$base&created_at%5Bfrom%5D=${enc(from)}", false, 50) { it } }.getOrDefault(emptyList())
            if (r.isNotEmpty()) return r
        }
        return runCatching { paged(base, false, 50) { it } }.getOrDefault(emptyList())
    }

    /** Чарта в открытом API SoundCloud нет: «Популярное» собирается из свежих треков основных жанров по числу прослушиваний. */
    override fun chart(): List<Track> {
        val genres = listOf("pop", "hip-hop", "electronic", "rock", "dance", "indie", "house", "trap")
        val raw = genres.flatMap { genreTracks(it, recent = true) }
        return raw.distinctBy { it.optLong("id") }
            .sortedByDescending { it.optLong("playback_count") }
            .mapNotNull { toTrack(it) }
            .fullOnly()
            .take(100)
    }

    /** Новинки: свежие треки тех, на кого подписан пользователь; без аккаунта или подписок — новые треки популярных жанров. */
    override fun nativeNewReleases(): NativeReleases? {
        val followed = runCatching { paged("$API/me/followings/tracks?limit=50&linked_partitioning=true", true, 80) { toTrack(it) } }
            .getOrDefault(emptyList())
        val tracks = followed.ifEmpty {
            listOf("pop", "hip-hop", "electronic", "rock").flatMap { genreTracks(it, recent = true) }
                .distinctBy { it.optLong("id") }
                .sortedByDescending { it.str("created_at") }
                .mapNotNull { toTrack(it) }.take(80)
        }
        return NativeReleases(emptyList(), tracks.distinctBy { it.id }.fullOnly())
    }

    /** «Для вас»: лайкнутые плейлисты и подборки по настроению и жанрам (поиск плейлистов). */
    override fun recommendedShelves(): List<PlaylistShelf> {
        val out = ArrayList<PlaylistShelf>()
        out += runCatching { mixShelves() }.getOrDefault(emptyList())
        runCatching { likedAlbums("") }.getOrDefault(emptyList()).takeIf { it.isNotEmpty() }?.let { liked ->
            out += PlaylistShelf("sc_liked", "Понравившиеся плейлисты", liked.map {
                PlaylistInfo(0, it.id, it.title, it.trackCount, it.coverUri, null, it.artists.ifBlank { null })
            })
        }
        listOf("chill" to "Для расслабления", "workout" to "Для тренировки", "lofi" to "Lo-fi", "hip hop" to "Хип-хоп", "electronic" to "Электроника", "rock" to "Рок")
            .forEach { (q, title) ->
                val lists = runCatching {
                    paged("$API/playlists?q=${enc(q)}&show_tracks=false&limit=30&linked_partitioning=true", false, 30) { toPlaylist(it) }
                }.getOrDefault(emptyList()).filter { it.trackCount >= 5 }.take(20)
                if (lists.isNotEmpty()) out += PlaylistShelf("sc_$q", title, lists)
            }
        return out
    }
    override fun trackLyrics(trackId: String, synced: Boolean): String? = null

    /**
     * История прослушивания аккаунта SoundCloud (/me/play-history/tracks): всё, что играло на любых устройствах, новые первыми.
     * Элементы могут прийти и самими треками, и обёрткой {played_at, track}. Без аккаунта SoundCloud истории нет.
     */
    override fun musicHistory(limit: Int): List<Track> = musicHistoryFull(limit).tracks

    override fun musicHistoryFull(limit: Int): RemoteHistory {
        if (activeSc() == null) return RemoteHistory(emptyList(), emptyList())
        val n = limit.coerceIn(1, 200)
        val tracks = soft<List<Track>>(emptyList()) {
            paged("$API/me/play-history/tracks?limit=$n&linked_partitioning=true", true, n) { o ->
                toTrack(o.optJSONObject("track") ?: o)
            }
        }
        return RemoteHistory(tracks.distinctBy { it.id }, emptyList())
    }

    override fun trackInfo(t: Track): TrackInfo {
        val date = runCatching {
            JSONObject(get("$API/tracks/${ScIds.raw(t.id)}")).str("created_at").take(10).replace('/', '-')
                .split("-").takeIf { it.size == 3 }?.reversed()?.joinToString(".")
        }.getOrNull()
        return TrackInfo(
            title = t.title, artists = t.artists, year = t.year, releaseDate = date, genre = t.genre,
            labels = listOfNotNull(t.label), durationMs = t.durationMs,
        )
    }

    // ---- Ссылки ----

    /** Короткие ссылки (on.soundcloud.com/…) раскрываем переходом; остальные возвращаем как есть. */
    private fun expand(url: String): String {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return url
        if (host != "on.soundcloud.com" && host != "soundcloud.app.goo.gl") return url
        val req = okhttp3.Request.Builder().url(url).get().build()
        SoundCloudAuth.client().newCall(req).execute().use { r -> return r.request.url.toString() }
    }

    /** Определяет, на что указывает ссылка SoundCloud: трек, плейлист (сет) или профиль. */
    fun resolve(url: String): ScResolved {
        val clean = expand(url).substringBefore('?').substringBefore('#')
        val o = JSONObject(get("$API/resolve?url=${enc(clean)}"))
        return when (o.str("kind")) {
            "track" -> ScResolved.Single(toTrack(o) ?: error("Этот трек недоступен"))
            "playlist" -> {
                val info = toPlaylist(o)
                ScResolved.ListRes(info, playlistTracks(info.ownerUid, 0))
            }
            // Готовый микс SoundCloud (soundcloud.com/discover/sets/…): треки лежат в самом ответе, id у него не число.
            "system-playlist" -> {
                val tracks = systemPlaylistTracks(o)
                if (tracks.isEmpty()) error("В миксе нет доступных треков")
                val art = o.str("artwork_url").ifBlank { o.str("calculated_artwork_url") }.ifBlank { tracks.first().coverUri.orEmpty() }
                ScResolved.ListRes(
                    PlaylistInfo(0, ScMix.url(clean), o.str("title").ifBlank { "Микс SoundCloud" }, tracks.size,
                        art.takeIf { it.isNotBlank() }?.let { bigArt(it) }, null, o.str("made_for_username").ifBlank { null }),
                    tracks,
                )
            }
            "user" -> ScResolved.UserRes(artist(ScIds.PREFIX + o.optLong("id")))
            else -> error("Эту ссылку SoundCloud открыть нельзя")
        }
    }
}
