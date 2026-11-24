package app.ymusic.m3e.data

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class YandexMusicApi(
    private val http: OkHttpClient,
    private val tokenProvider: () -> String?,
) : MusicApi {
    override val caps = ApiCaps()

    private val base = "https://api.music.yandex.net"
    private val FILE_INFO_SIGN_KEY = "kzqU4XhfCaY6B6JTHODeq5"
    private val LYRICS_SIGN_KEY = "p93jhgh689SBReK6ghtw62"
    private val DESKTOP_CLIENT = "YandexMusicDesktopAppWindows/5.95.0"

    /** true, если get-file-info реально отдал полный/качественный поток — значит Плюс у аккаунта есть. */
    @Volatile override var plusConfirmedByStream: Boolean = false

    /** Когда get-file-info последний раз ответил 403 (чтобы не долбить сервер перед каждым треком). */
    @Volatile private var fileInfoDeniedAt: Long = 0L

    /** Сбрасывает состояние, привязанное к аккаунту (при переключении аккаунта). */
    /** Высокое качество звука включено? Читается при каждом запросе потока, поэтому переключатель действует сразу. */
    @Volatile override var highQuality: () -> Boolean = { true }

    override fun resetSession() {
        plusConfirmedByStream = false
        fileInfoDeniedAt = 0L
    }

    private fun request(
        path: String,
        form: Map<String, String>? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
    ): JSONObject {
        val token = tokenProvider() ?: error("Нет токена")
        val builder = Request.Builder().url(base + path)
            .header("Authorization", "OAuth $token")
            .header("X-Yandex-Music-Client", "YandexMusicAndroid/24023621")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        if (jsonBody != null) {
            builder.post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
        } else if (form != null) {
            val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
            builder.post(body)
        }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(200)}")
            return JSONObject(text)
        }
    }

    /** Профиль с login.yandex.ru: запасной источник uid/логина/имени, если /account/status их не вернул. */
    private fun passportInfo(): JSONObject? = runCatching {
        val token = tokenProvider() ?: return@runCatching null
        val req = Request.Builder().url("https://login.yandex.ru/info?format=json")
            .header("Authorization", "OAuth $token").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else JSONObject(resp.body?.string().orEmpty())
        }
    }.getOrNull()

    override fun account(): Account {
        // 403 от music-accounts не фатален: uid/логин/имя возьмём из паспорта ниже.
        val acc = try {
            request("/account/status").getJSONObject("result")
        } catch (e: Exception) {
            if (e.message?.contains("HTTP 403") == true) JSONObject() else throw e
        }
        val a = acc.optJSONObject("account") ?: JSONObject()
        // Только /account/status: поток (lossless/битрейт) о Плюсе не говорит — get-file-info отдаёт его и без подписки.
        val plus = detectPlus(acc)
        runCatching {
            val sub = acc.optJSONObject("subscription")
            app.ymusic.m3e.data.AppLog.i("Plus", "account/status: hasPlus=$plus, ключи=${acc.keys().asSequence().toList()}, " +
                "plus=${acc.optJSONObject("plus")}, subscription=${sub?.toString()?.take(300)}, permissions=${acc.optJSONObject("permissions")?.toString()?.take(200)}")
        }

        var uid = a.optLong("uid", 0L)
        var login = a.optString("login")
        var name = a.optString("fullName").ifBlank { a.optString("displayName") }

        // Паспорт нужен и для аватара, и как запасной источник uid/логина/имени.
        val passport = passportInfo()
        // Для некоторых токенов Яндекс не отдаёт uid в /account/status — берём из паспорта.
        if (uid == 0L || login.isBlank() || name.isBlank()) {
            val p = passport
            if (p != null) {
                if (uid == 0L) uid = p.optString("id").toLongOrNull() ?: 0L
                if (login.isBlank()) login = p.optString("login")
                if (name.isBlank()) name = p.optString("real_name").ifBlank { p.optString("display_name") }
            }
        }
        // Если пользователя определить не удалось (токен без прав на профиль) — не блокируем вход:
        // поиск, страницы артистов и воспроизведение работают без uid.

        val avatarUrl = passport?.let { p0 ->
            val id = p0.optString("default_avatar_id")
            if (id.isBlank() || p0.optBoolean("is_avatar_empty", false)) null
            else "https://avatars.yandex.net/get-yapic/$id/islands-200"
        }

        return Account(uid = uid, login = login, name = name, hasPlus = plus, avatarUrl = avatarUrl)
    }

    /** Плюс может быть отмечен по-разному в зависимости от токена/версии API — проверяем все известные признаки. */
    private fun detectPlus(acc: JSONObject): Boolean {
        val plusObj = acc.optJSONObject("plus")
        if (plusObj?.optBoolean("hasPlus") == true) return true
        if (acc.optBoolean("hasPlus", false)) return true
        if (acc.optJSONObject("account")?.optBoolean("hasPlus", false) == true) return true

        val sub = acc.optJSONObject("subscription")
        if (sub != null) {
            if (sub.optBoolean("hasAnySubscription")) return true
            if (sub.optJSONObject("autoRenewable") != null || sub.optJSONObject("nonAutoRenewable") != null) return true
            if ((sub.optJSONArray("autoRenewable")?.length() ?: 0) > 0) return true
            if ((sub.optJSONArray("nonAutoRenewable")?.length() ?: 0) > 0) return true
            if (sub.optInt("nonAutoRenewableRemainingDays", 0) > 0) return true
            if (sub.optInt("non_auto_renewable_remaining_days", 0) > 0) return true
            if (sub.optJSONObject("family") != null) return true
        }

        // Права токена: у подписчиков там есть расширенные пермишены.
        val perms = acc.optJSONObject("permissions")?.optJSONArray("values")
        if (perms != null) {
            for (i in 0 until perms.length()) {
                val v = perms.optString(i).lowercase()
                if (v.contains("lossless") || v == "hq" || v.contains("plus") || v.contains("subscription")) return true
            }
        }
        return false
    }

    /** Лайкнутые треки: сначала список id, затем подробности пачкой. */
    override fun likedTracks(uid: String, limit: Int): List<Track> {
        val ids = request("/users/$uid/likes/tracks")
            .getJSONObject("result").getJSONObject("library").getJSONArray("tracks")
        val keys = (0 until minOf(ids.length(), limit)).map {
            val t = ids.getJSONObject(it)
            val album = t.optString("albumId")
            if (album.isNotBlank()) "${t.getString("id")}:$album" else t.getString("id")
        }
        return tracksByIds(keys)
    }

    override fun tracksByIds(keys: List<String>): List<Track> {
        if (keys.isEmpty()) return emptyList()
        val out = mutableListOf<Track>()
        keys.chunked(100).forEach { chunk ->
            val arr = request("/tracks", mapOf("track-ids" to chunk.joinToString(",")))
                .getJSONArray("result")
            out += parseTracks(arr)
        }
        return out
    }

    override fun playlists(uid: String): List<PlaylistInfo> {
        val arr = request("/users/$uid/playlists/list").getJSONArray("result")
        return (0 until arr.length()).map { parsePlaylist(arr.getJSONObject(it), uid) }
    }

    private fun parsePlaylist(p: JSONObject, fallbackUid: String): PlaylistInfo = PlaylistInfo(
        kind = p.getInt("kind"),
        ownerUid = p.optJSONObject("owner")?.optString("uid")?.takeIf { s -> s.isNotBlank() && s != "null" } ?: fallbackUid,
        title = p.optString("title"),
        trackCount = p.optInt("trackCount"),
        coverUri = playlistCoverUri(p),
        visibility = p.optString("visibility").takeIf { s -> s.isNotBlank() && s != "null" },
        ownerLogin = p.optJSONObject("owner")?.optString("login")?.takeIf { s -> s.isNotBlank() && s != "null" },
    )

    /** Сведения о плейлисте (название, владелец, видимость): нужны, когда плейлист открывают по ссылке. */
    override fun playlistInfo(ownerUid: String, kind: Int): PlaylistInfo {
        val res = request("/users/$ownerUid/playlists/$kind").getJSONObject("result")
        return parsePlaylist(res, ownerUid)
    }

    /** Плейлист по новой ссылке /playlists/<uuid>: сервер сам отдаёт владельца и номер. */
    override fun playlistInfoByUuid(uuid: String): PlaylistInfo {
        val res = request("/playlist/$uuid").getJSONObject("result")
        val owner = res.optJSONObject("owner")?.optString("uid").orEmpty()
        return parsePlaylist(res, owner)
    }

    /** Приватный / публичный плейлист (как в Яндекс Музыке): по ссылке открываются только публичные. */
    override fun setPlaylistVisibility(ownerUid: String, kind: Int, isPublic: Boolean) {
        request(
            "/users/$ownerUid/playlists/$kind/visibility",
            mapOf("value" to if (isPublic) "public" else "private"),
        )
    }

    override fun playlistTracks(ownerUid: String, kind: Int): List<Track> {
        val res = request("/users/$ownerUid/playlists/$kind").getJSONObject("result")
        val tracks = res.optJSONArray("tracks") ?: return emptyList()
        val keys = (0 until tracks.length()).map {
            val t = tracks.getJSONObject(it)
            val album = t.optJSONObject("track")?.optJSONArray("albums")
                ?.optJSONObject(0)?.optString("id").orEmpty()
                .ifBlank { t.optString("albumId") }
            val id = t.optString("id")
            if (album.isNotBlank()) "$id:$album" else id
        }
        return tracksByIds(keys)
    }

    // ---- Редактирование плейлистов ----

    private class PlaylistSnapshot(val revision: Int, val ids: List<String>)

    /** Свежая ревизия и «сырой» список id треков (включая недоступные) — нужны для изменения плейлиста. */
    private fun playlistSnapshot(ownerUid: String, kind: Int): PlaylistSnapshot {
        val res = request("/users/$ownerUid/playlists/$kind").getJSONObject("result")
        val arr = res.optJSONArray("tracks") ?: JSONArray()
        val ids = (0 until arr.length()).map { i ->
            val t = arr.getJSONObject(i)
            t.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                ?: t.optJSONObject("track")?.opt("id")?.toString().orEmpty()
        }
        return PlaylistSnapshot(res.optInt("revision", 1), ids)
    }

    private fun changePlaylist(ownerUid: String, kind: Int, revision: Int, diff: JSONArray) {
        request(
            "/users/$ownerUid/playlists/$kind/change",
            mapOf("kind" to kind.toString(), "revision" to revision.toString(), "diff" to diff.toString()),
        )
    }

    override fun createPlaylist(uid: String, title: String, isPublic: Boolean): PlaylistInfo {
        val res = request(
            "/users/$uid/playlists/create",
            mapOf("title" to title, "visibility" to if (isPublic) "public" else "private"),
        ).getJSONObject("result")
        return parsePlaylist(res, uid)
    }

    override fun renamePlaylist(ownerUid: String, kind: Int, title: String) {
        request("/users/$ownerUid/playlists/$kind/name", mapOf("value" to title))
    }

    override fun deletePlaylist(ownerUid: String, kind: Int) {
        request("/users/$ownerUid/playlists/$kind/delete", emptyMap())
    }

    override fun addTrackToPlaylist(ownerUid: String, kind: Int, track: Track) {
        val snap = playlistSnapshot(ownerUid, kind)
        if (snap.ids.contains(track.id)) error("Этот трек уже есть в плейлисте")
        val item = JSONObject().put("id", track.id)
        track.albumId?.let { item.put("albumId", it) }
        val diff = JSONArray().put(
            JSONObject().put("op", "insert").put("at", snap.ids.size).put("tracks", JSONArray().put(item))
        )
        changePlaylist(ownerUid, kind, snap.revision, diff)
    }

    override fun removeTrackFromPlaylist(ownerUid: String, kind: Int, track: Track) {
        val snap = playlistSnapshot(ownerUid, kind)
        val index = snap.ids.indexOf(track.id)
        if (index < 0) error("Трек не найден в плейлисте")
        val diff = JSONArray().put(JSONObject().put("op", "delete").put("from", index).put("to", index + 1))
        changePlaylist(ownerUid, kind, snap.revision, diff)
    }

    // ---- Лайки и дизлайки ----

    private fun trackKey(t: Track): String = if (!t.albumId.isNullOrBlank()) "${t.id}:${t.albumId}" else t.id

    override fun likeTrack(uid: String, t: Track) {
        request("/users/$uid/likes/tracks/add-multiple", mapOf("track-ids" to trackKey(t)))
    }

    override fun unlikeTrack(uid: String, t: Track) {
        request("/users/$uid/likes/tracks/remove", mapOf("track-ids" to trackKey(t)))
    }

    override fun dislikeTrack(uid: String, t: Track) {
        request("/users/$uid/dislikes/tracks/add-multiple", mapOf("track-ids" to trackKey(t)))
    }

    override fun undislikeTrack(uid: String, t: Track) {
        request("/users/$uid/dislikes/tracks/remove", mapOf("track-ids" to trackKey(t)))
    }

    /** id треков, отмеченных «не нравится». */
    override fun dislikedTrackIds(uid: String): Set<String> {
        val arr = request("/users/$uid/dislikes/tracks")
            .getJSONObject("result").getJSONObject("library").optJSONArray("tracks") ?: return emptySet()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.opt("id")?.toString() }.toSet()
    }

    // ---- Моя волна ----

    private val waveStation = "user:onyourwave"

    /**
     * Очередная порция треков волны; afterTrackId — последний трек очереди (продолжение).
     * [station] — станция: по умолчанию «Моя волна» (user:onyourwave), либо волна по треку / исполнителю / альбому /
     * плейлисту: track:<id>, artist:<id>, album:<id>, playlist:<владелец>_<kind>.
     */
    override fun waveTracks(afterTrackId: String?, station: String): WaveBatch {
        val q = if (afterTrackId != null) "&queue=${URLEncoder.encode(afterTrackId, "UTF-8")}" else ""
        val res = request("/rotor/station/$station/tracks?settings2=true$q").getJSONObject("result")
        val seq = res.optJSONArray("sequence") ?: JSONArray()
        val arr = JSONArray()
        for (i in 0 until seq.length()) seq.optJSONObject(i)?.optJSONObject("track")?.let { arr.put(it) }
        return WaveBatch(parseTracks(arr), res.optString("batchId").takeIf { it.isNotBlank() })
    }

    override fun waveSettings(): WaveSettings {
        val arr = request("/rotor/station/$waveStation/info").optJSONArray("result")
        val s = arr?.optJSONObject(0)?.optJSONObject("settings2") ?: return WaveSettings()
        return WaveSettings(
            diversity = s.optString("diversity").ifBlank { "default" },
            moodEnergy = s.optString("moodEnergy").ifBlank { "all" },
            language = s.optString("language").ifBlank { "any" },
        )
    }

    override fun saveWaveSettings(s: WaveSettings) {
        val path = "/rotor/station/$waveStation/settings2"
        val form = mapOf("moodEnergy" to s.moodEnergy, "diversity" to s.diversity, "language" to s.language)
        try {
            request(path, form)
        } catch (e: Exception) {
            // Запасной вариант: некоторые версии API ждут JSON вместо формы.
            request(path, jsonBody = JSONObject(form).toString())
        }
    }

    /** Сообщает волне, что трек начал играть (улучшает рекомендации, не критично при ошибке). */
    override fun waveTrackStarted(trackId: String, batchId: String?, station: String) {
        val body = JSONObject()
            .put("type", "trackStarted")
            .put("timestamp", java.time.Instant.now().toString())
            .put("trackId", trackId)
        val q = batchId?.let { "?batch-id=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
        request("/rotor/station/$station/feedback$q", jsonBody = body.toString())
    }

    /** Сообщает станции, что её включили (для волны по треку / исполнителю / альбому / плейлисту). Ошибка не критична. */
    override fun waveStarted(station: String) {
        val body = JSONObject()
            .put("type", "radioStarted")
            .put("timestamp", java.time.Instant.now().toString())
            .put("from", "ymusic-m3e")
        request("/rotor/station/$station/feedback", jsonBody = body.toString())
    }

    override fun search(query: String): List<Track> {
        val q = URLEncoder.encode(query, "UTF-8")
        val res = request("/search?text=$q&type=track&page=0").getJSONObject("result")
        val arr = res.optJSONObject("tracks")?.optJSONArray("results") ?: return emptyList()
        return parseTracks(arr)
    }

    /**
     * Прямая ссылка на аудиопоток. Сначала новый get-file-info (HMAC-подпись, transports=raw) —
     * он отдаёт полный трек для аккаунта с Плюсом; если не вышло, откатываемся на старый download-info.
     */
    override fun streamUrl(trackId: String): String =
        fileInfoStreamUrl(trackId) ?: legacyStreamUrl(trackId)

    /** Ссылка для скачивания: предпочитаем mp3 (download-info), иначе обычный поток. */
    override fun downloadUrl(trackId: String): String =
        try { legacyStreamUrl(trackId) } catch (e: Exception) { fileInfoStreamUrl(trackId) ?: throw e }

    private fun fileInfoStreamUrl(trackId: String): String? {
        // После 403 не пробуем get-file-info несколько минут — сразу идём в download-info.
        if (System.currentTimeMillis() - fileInfoDeniedAt < 5 * 60_000L) {
            return null
        }
        // Лесенка: если на lossless сервер отвечает not-allowed, пробуем более низкие качества.
        val attempts = if (highQuality()) listOf(
            "lossless" to listOf("flac", "aac", "he-aac", "mp3"),
            "hq" to listOf("aac", "he-aac", "mp3"),
            "nq" to listOf("aac", "he-aac", "mp3"),
            "nq" to listOf("mp3"),
        ) else listOf(
            // Экономный режим: сразу обычное качество, без lossless/hq.
            "nq" to listOf("aac", "he-aac", "mp3"),
            "nq" to listOf("mp3"),
        )
        var denied = 0
        for ((quality, codecs) in attempts) {
            try {
                val url = requestFileInfo(trackId, quality, codecs)
                if (url != null) return url
            } catch (e: Exception) {
                if (e.message?.contains("HTTP 403") == true) denied++
            }
        }
        if (denied == attempts.size) fileInfoDeniedAt = System.currentTimeMillis()
        return null
    }

    private fun requestFileInfo(trackId: String, quality: String, codecs: List<String>): String? {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val transports = "raw"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(FILE_INFO_SIGN_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sign = Base64.getEncoder()
            .encodeToString(mac.doFinal("$ts$trackId$quality${codecs.joinToString("")}$transports".toByteArray(Charsets.UTF_8)))
            .trimEnd('=')
        val q = "ts=$ts&trackId=${URLEncoder.encode(trackId, "UTF-8")}&quality=$quality" +
            "&codecs=${URLEncoder.encode(codecs.joinToString(","), "UTF-8")}" +
            "&transports=$transports&sign=${URLEncoder.encode(sign, "UTF-8")}"
        // Ключ подписи привязан к заголовку десктопного клиента — иначе сервер отвечает 403.
        val info = request("/get-file-info?$q", extraHeaders = mapOf("X-Yandex-Music-Client" to DESKTOP_CLIENT))
            .getJSONObject("result").getJSONObject("downloadInfo")
        val codec = info.optString("codec")
        val bitrate = info.optInt("bitrate")
        if (info.optString("transport", "raw") != "raw") return null
        return info.optString("url").takeIf { it.isNotBlank() }
            ?: info.optJSONArray("urls")?.optString(0)?.takeIf { it.isNotBlank() }
    }

    /** Прямая ссылка на аудиопоток через классический download-info + MD5-подпись. */
    private fun legacyStreamUrl(trackId: String): String {
        val list = request("/tracks/$trackId/download-info").getJSONArray("result")
        var bestUrl: String? = null
        var bestBitrate = -1

        var bestIsMp3 = false
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val codec = item.optString("codec")
            val bitrate = item.optInt("bitrateInKbps", 0)
            if (item.optBoolean("preview", false)) continue // пропускаем 30-секундные превью
            if (codec != "mp3" && codec != "aac") continue
            val isMp3 = codec == "mp3"
            // mp3 предпочтительнее aac. Высокое качество — наибольший битрейт; экономный режим — наибольший
            // не выше 128 кбит/с (а если таких нет, то наименьший из доступных).
            val better = if (highQuality()) {
                bestUrl == null || (isMp3 && !bestIsMp3) || (isMp3 == bestIsMp3 && bitrate > bestBitrate)
            } else {
                fun score(mp3: Boolean, kbps: Int) = (if (mp3) 1_000_000 else 0) + if (kbps <= 128) kbps else 100_000 - kbps
                bestUrl == null || score(isMp3, bitrate) > score(bestIsMp3, bestBitrate)
            }
            if (better) {
                bestBitrate = bitrate
                bestIsMp3 = isMp3
                bestUrl = item.getString("downloadInfoUrl")
            }
        }
        val infoUrl = bestUrl
            ?: list.optJSONObject(0)?.getString("downloadInfoUrl")
            ?: error("Нет доступных потоков для трека $trackId")

        val xml = http.newCall(Request.Builder().url(infoUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) error("Ошибка downloadInfoUrl: HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

        fun xmlTag(tag: String): String =
            Regex("<$tag>(.*?)</$tag>").find(xml)?.groupValues?.get(1)
                ?: error("Не найден тег <$tag> в ответе сервера")

        val host = xmlTag("host")
        val path = xmlTag("path")
        val ts = xmlTag("ts")
        val s = xmlTag("s")

        val signInput = "XGRlBW9FXlekgbPrRHuSiA${path.removePrefix("/")}$s"
        val md5 = MessageDigest.getInstance("MD5")
            .digest(signInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        return "https://$host/get-mp3/$md5/$ts$path"
    }

    private fun parseArtistRefs(arr: JSONArray?): List<ArtistRef> {
        if (arr == null) return emptyList()
        val out = mutableListOf<ArtistRef>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            val cover = o.optJSONObject("cover")?.optString("uri")?.takeIf { it.isNotBlank() }
                ?: o.optString("ogImage").takeIf { it.isNotBlank() }
            out += ArtistRef(id, o.optString("name"), cover)
        }
        return out
    }

    /** Страница артиста: имя, обложка и популярные треки. */
    override fun artist(id: String): ArtistInfo {
        val res = request("/artists/$id/brief-info").getJSONObject("result")
        val a = res.optJSONObject("artist") ?: JSONObject()
        var tracks = res.optJSONArray("popularTracks")?.let { parseTracks(it) }.orEmpty()
        if (tracks.isEmpty()) tracks = runCatching { artistTracks(id) }.getOrDefault(emptyList())
        return ArtistInfo(
            id = id,
            name = a.optString("name"),
            coverUri = artistCoverUri(a),
            tracks = tracks,
            albums = runCatching { artistAlbums(id) }.getOrDefault(emptyList()),
            allCovers = artistAllCovers(res, a),
            monthlyListeners = (res.optJSONObject("stats") ?: a.optJSONObject("stats"))?.optLong("lastMonthListeners", 0L) ?: 0L,
            description = artistDescription(res, a),
        )
    }

    /** Описание исполнителя: объект {text, uri} или строка в artist.description, иначе рукописное описание или поле верхнего уровня. */
    private fun artistDescription(res: JSONObject, a: JSONObject): String {
        fun read(v: Any?): String = when (v) {
            is JSONObject -> v.optString("text")
            is String -> v
            else -> ""
        }.trim().takeIf { it != "null" }.orEmpty()
        return listOf(
            a.opt("description"), a.opt("handMadeDescription"), a.opt("hand_made_description"),
            res.opt("description"), res.opt("handMadeDescription"),
        ).map { read(it) }.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    /** Все фото исполнителя: основное первым, затем allCovers из brief-info (без повторов). */
    private fun artistAllCovers(res: JSONObject, a: JSONObject): List<String> {
        val out = LinkedHashSet<String>()
        artistCoverUri(a)?.let { out += it }
        for (arr in listOf(res.optJSONArray("allCovers"), a.optJSONArray("allCovers"))) {
            if (arr == null) continue
            for (i in 0 until arr.length()) {
                val uri = when (val el = arr.opt(i)) {
                    is JSONObject -> el.optString("uri")
                    is String -> el
                    else -> ""
                }
                if (uri.isNotBlank() && uri != "null") out += uri
            }
        }
        return out.toList()
    }

    // ---- Альбомы ----

    private fun parseAlbum(a: JSONObject): AlbumInfo {
        val artists = a.optJSONArray("artists")?.let { arr ->
            (0 until arr.length()).joinToString(", ") { i -> arr.getJSONObject(i).optString("name") }
        }.orEmpty()
        return AlbumInfo(
            id = a.opt("id")?.toString().orEmpty(),
            title = a.optString("title"),
            artists = artists,
            artistRefs = parseArtistRefs(a.optJSONArray("artists")),
            coverUri = a.optString("coverUri").takeIf { it.isNotBlank() } ?: a.optString("ogImage").takeIf { it.isNotBlank() },
            year = a.optInt("year", 0),
            trackCount = a.optInt("trackCount", 0),
            releaseDate = a.optString("releaseDate").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
        )
    }

    private fun parseAlbums(arr: JSONArray): List<AlbumInfo> = (0 until arr.length()).mapNotNull {
        arr.optJSONObject(it)?.let { o -> parseAlbum(o) }?.takeIf { a -> a.id.isNotBlank() && a.id != "null" }
    }

    /** Альбом с треками (все диски подряд). */
    override fun album(id: String): AlbumInfo {
        val res = request("/albums/$id/with-tracks").getJSONObject("result")
        val base = parseAlbum(res).let { if (it.id.isBlank() || it.id == "null") it.copy(id = id) else it }
        val vols = res.optJSONArray("volumes")
        val arr = JSONArray()
        if (vols != null) {
            for (i in 0 until vols.length()) {
                val vol = vols.optJSONArray(i) ?: continue
                for (j in 0 until vol.length()) vol.optJSONObject(j)?.let { arr.put(it) }
            }
        }
        val tracks = parseTracks(arr).map { t -> t.copy(albumId = t.albumId ?: id, albumTitle = t.albumTitle ?: base.title) }
        return base.copy(tracks = tracks, trackCount = if (base.trackCount > 0) base.trackCount else tracks.size)
    }

    /** Альбомы артиста для «Новинок»: свежие первыми (по дате выхода, а если её нет — по году). */
    override fun artistRecentAlbums(id: String): List<AlbumInfo> =
        artistAlbums(id).sortedByDescending { it.sortDate }

    private fun artistAlbums(id: String): List<AlbumInfo> {
        val arr = request("/artists/$id/direct-albums?page=0&page-size=30")
            .getJSONObject("result").optJSONArray("albums") ?: return emptyList()
        return parseAlbums(arr).sortedByDescending { it.year }
    }

    /**
     * Альбомы, добавленные в «Мою коллекцию» (лайкнутые). Запрос с rich=true отдаёт альбом целиком; если сервер
     * вернул только id, подробности добираются пачкой.
     */
    override fun likedAlbums(uid: String): List<AlbumInfo> {
        val arr = request("/users/$uid/likes/albums?rich=true").optJSONArray("result") ?: return emptyList()
        val out = ArrayList<AlbumInfo>()
        val idsOnly = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val a = o.optJSONObject("album")
            if (a != null) {
                val parsed = parseAlbum(a)
                if (parsed.id.isNotBlank() && parsed.id != "null") out += parsed
            } else {
                o.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }?.let { idsOnly += it }
            }
        }
        idsOnly.chunked(50).forEach { chunk ->
            runCatching {
                val res = request("/albums", mapOf("album-ids" to chunk.joinToString(","))).optJSONArray("result")
                if (res != null) out += parseAlbums(res)
            }
        }
        return out.distinctBy { it.id }
    }

    /** Добавить альбом в «Мою коллекцию» (лайк альбома). */
    override fun likeAlbum(uid: String, albumId: String) {
        request("/users/$uid/likes/albums/add-multiple", mapOf("album-ids" to albumId))
    }

    /** Убрать альбом из коллекции (снять лайк). */
    override fun unlikeAlbum(uid: String, albumId: String) {
        request("/users/$uid/likes/albums/remove", mapOf("album-ids" to albumId))
    }

    override fun searchAlbums(query: String): List<AlbumInfo> {
        val q = URLEncoder.encode(query, "UTF-8")
        val res = request("/search?text=$q&type=album&page=0").getJSONObject("result")
        val arr = res.optJSONObject("albums")?.optJSONArray("results") ?: return emptyList()
        return parseAlbums(arr)
    }

    // ---- Подкасты ----

    /** Подкаст в API — это «альбом» с метой podcast: открывается тем же экраном, эпизоды — его треки. */
    override fun searchPodcasts(query: String): List<AlbumInfo> {
        val q = URLEncoder.encode(query, "UTF-8")
        val res = request("/search?text=$q&type=podcast&page=0").getJSONObject("result")
        val arr = res.optJSONObject("podcasts")?.optJSONArray("results") ?: return emptyList()
        return parseAlbums(arr)
    }

    /** Подборка подкастов для страницы «Подкасты» (лендинг; если он недоступен — пусто, тогда вызывающий ищет сам). */
    override fun podcastsLanding(): List<AlbumInfo> {
        val res = request("/landing3/podcasts").getJSONObject("result")
        val out = LinkedHashMap<String, AlbumInfo>()
        fun walk(n: Any?) {
            when (n) {
                is JSONObject -> {
                    val meta = n.optString("metaType")
                    if (meta == "podcast" && n.has("title")) {
                        parseAlbum(n).takeIf { it.id.isNotBlank() && it.id != "null" }?.let { out.putIfAbsent(it.id, it) }
                    } else {
                        val keys = n.keys()
                        while (keys.hasNext()) walk(n.opt(keys.next()))
                    }
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i))
            }
        }
        walk(res)
        return out.values.toList()
    }

    // ---- Чарт ----

    /** Чарт Яндекс Музыки: треки в порядке позиций. */
    override fun chart(): List<Track> {
        val res = request("/landing3/chart").getJSONObject("result")
        val src = res.optJSONObject("chart")?.optJSONArray("tracks")
            ?: res.optJSONArray("tracks") ?: return emptyList()
        val arr = JSONArray()
        for (i in 0 until src.length()) {
            val o = src.optJSONObject(i) ?: continue
            // Элемент чарта: {id, track:{…}, chart:{position…}}; иногда сразу трек.
            arr.put(o.optJSONObject("track") ?: o)
        }
        return parseTracks(arr)
    }

    // ---- История прослушивания (синхронизируется с аккаунтом: видны треки с других устройств) ----

    /**
     * История прослушанного с сервера (новые — первыми). Структура ответа у /music-history вложенная и меняется,
     * поэтому обходим JSON целиком: полные модели треков берём как есть, а где есть только id — догружаем пачкой.
     */
    /**
     * Текст трека из Яндекс Музыки. [synced] = true — формат LRC с таймкодами, иначе обычный текст.
     * null — текста нет (или нет доступа). Запрос подписывается так же, как в приложении Яндекс Музыки.
     */
    override fun trackLyrics(trackId: String, synced: Boolean): String? {
        val ts = System.currentTimeMillis() / 1000
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(LYRICS_SIGN_KEY.toByteArray(), "HmacSHA256")) }
        val sign = Base64.getEncoder().encodeToString(mac.doFinal("$trackId$ts".toByteArray()))
        val q = "format=${if (synced) "LRC" else "TEXT"}&timeStamp=$ts&sign=${URLEncoder.encode(sign, "UTF-8")}"
        val res = request("/tracks/$trackId/lyrics?$q").optJSONObject("result") ?: return null
        val url = res.optString("downloadUrl").takeIf { it.startsWith("http") } ?: return null
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    override fun musicHistory(limit: Int): List<Track> = musicHistoryFull(limit).tracks

    /** Относится ли «контекст» прослушивания к «Моей волне» (радио user:onyourwave). */
    private fun isWaveContext(ctx: JSONObject): Boolean {
        val s = ctx.toString().lowercase()
        return s.contains("onyourwave") || s.contains("моя волна") || s.contains("my wave") || s.contains("my-wave")
    }

    override fun musicHistoryFull(limit: Int): RemoteHistory {
        val res = request("/music-history?full_models_count=$limit").opt("result")
        val order = LinkedHashMap<String, Track?>()   // id -> трек (null — только id)
        val keys = HashMap<String, String>()          // id -> "id:albumId" для догрузки
        val waveIds = LinkedHashSet<String>()         // id треков, игравших в «Моей волне»
        fun walk(n: Any?, inWave: Boolean) {
            if (order.size >= limit * 2) return
            when (n) {
                is JSONObject -> {
                    val wave = inWave || (n.optJSONObject("context")?.let { isWaveContext(it) } ?: false)
                    if (n.has("durationMs") && n.has("title") && n.has("id")) {
                        parseTracks(JSONArray().put(n)).firstOrNull()?.let { t ->
                            if (order[t.id] == null) order[t.id] = t
                            if (wave) waveIds += t.id
                        }
                        return
                    }
                    val tid = n.optString("trackId").takeIf { it.isNotBlank() && it != "null" }
                    if (tid != null && !n.has("title")) {
                        val album = n.optString("albumId").takeIf { it.isNotBlank() && it != "null" }
                        if (!order.containsKey(tid)) order[tid] = null
                        keys[tid] = if (album != null) "$tid:$album" else tid
                        if (wave) waveIds += tid
                        return
                    }
                    val ks = n.keys()
                    while (ks.hasNext()) walk(n.opt(ks.next()), wave)
                }
                is JSONArray -> for (i in 0 until n.length()) walk(n.opt(i), inWave)
            }
        }
        walk(res, false)
        val missing = order.filterValues { it == null }.keys.take(limit).mapNotNull { keys[it] }
        if (missing.isNotEmpty()) {
            runCatching { tracksByIds(missing) }.getOrDefault(emptyList()).forEach { order[it.id] = it }
        }
        val all = order.values.filterNotNull().take(limit)
        return RemoteHistory(all, all.filter { it.id in waveIds })
    }

    private fun artistTracks(id: String): List<Track> {
        val arr = request("/artists/$id/tracks?page=0&page-size=50")
            .getJSONObject("result").optJSONArray("tracks") ?: return emptyList()
        return parseTracks(arr)
    }

    /** Все треки исполнителя: страницы по 50, пока сервер отдаёт новые (не больше 40 страниц). */
    override fun artistAllTracks(id: String): List<Track> {
        val out = LinkedHashMap<String, Track>()
        var page = 0
        while (page < 40) {
            val res = request("/artists/$id/tracks?page=$page&page-size=50").getJSONObject("result")
            val arr = res.optJSONArray("tracks") ?: break
            if (arr.length() == 0) break
            var added = 0
            for (t in parseTracks(arr)) if (out.putIfAbsent(t.id, t) == null) added++
            if (added == 0) break
            val total = res.optJSONObject("pager")?.optInt("total", 0) ?: 0
            if (total > 0 && out.size >= total) break
            page++
        }
        return out.values.toList()
    }

    override fun searchArtists(query: String): List<ArtistInfo> {
        val q = URLEncoder.encode(query, "UTF-8")
        val res = request("/search?text=$q&type=artist&page=0").getJSONObject("result")
        val arr = res.optJSONObject("artists")?.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<ArtistInfo>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            val id = a.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: continue
            out += ArtistInfo(id = id, name = a.optString("name"), coverUri = artistCoverUri(a))
        }
        return out
    }

    /** Обложка плейлиста: своя (uri), либо первая картинка «мозаики» (itemsUri), либо ogImage. */
    private fun playlistCoverUri(p: JSONObject): String? {
        val c = p.optJSONObject("cover")
        c?.optString("uri")?.takeIf { it.isNotBlank() }?.let { return it }
        val items = c?.optJSONArray("itemsUri")
        if (items != null) {
            for (i in 0 until items.length()) items.optString(i).takeIf { it.isNotBlank() }?.let { return it }
        }
        return p.optString("ogImage").takeIf { it.isNotBlank() }
    }

    private fun artistCoverUri(a: JSONObject): String? =
        a.optJSONObject("cover")?.optString("uri")?.takeIf { it.isNotBlank() }
            ?: a.optString("ogImage").takeIf { it.isNotBlank() }

    private fun parseTracks(arr: JSONArray): List<Track> = (0 until arr.length()).mapNotNull {
        val t = arr.optJSONObject(it) ?: return@mapNotNull null
        // Для аккаунта с Плюсом трек, доступный только премиум-пользователям, тоже годится.
        if (!t.optBoolean("available", true) && !t.optBoolean("availableForPremiumUsers", false)) return@mapNotNull null
        val artists = t.optJSONArray("artists")?.let { a ->
            (0 until a.length()).joinToString(", ") { i -> a.getJSONObject(i).optString("name") }
        }.orEmpty()
        Track(
            id = t.get("id").toString(),
            albumId = t.optJSONArray("albums")?.optJSONObject(0)?.get("id")?.toString(),
            title = t.optString("title"),
            artists = artists,
            coverUri = t.optString("coverUri").takeIf { s -> s.isNotBlank() }
                ?: t.optJSONArray("albums")?.optJSONObject(0)?.optString("coverUri")?.takeIf { s -> s.isNotBlank() }
                ?: t.optString("ogImage").takeIf { s -> s.isNotBlank() },
            durationMs = t.optLong("durationMs"),
            artistRefs = parseArtistRefs(t.optJSONArray("artists")),
            albumTitle = t.optJSONArray("albums")?.optJSONObject(0)?.optString("title")?.takeIf { s -> s.isNotBlank() },
            explicit = t.optString("contentWarning") == "explicit" || t.optBoolean("explicit", false),
            year = t.optJSONArray("albums")?.optJSONObject(0)?.optInt("year") ?: 0,
            label = namesOf(t.optJSONArray("albums")?.optJSONObject(0)?.opt("labels")).firstOrNull()
                ?: t.optJSONObject("major")?.optString("name")?.takeIf { s -> s.isNotBlank() },
            genre = t.optJSONArray("albums")?.optJSONObject(0)?.optString("genre")?.takeIf { s -> s.isNotBlank() },
            version = t.optString("version").takeIf { s -> s.isNotBlank() && s != "null" },
        )
    }

    /** Имена из массива строк или объектов {name}, либо из одной строки. */
    private fun namesOf(v: Any?): List<String> = when (v) {
        is JSONArray -> (0 until v.length()).mapNotNull { i ->
            when (val e = v.opt(i)) {
                is String -> e
                is JSONObject -> e.optString("name")
                else -> null
            }?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        }
        is String -> listOf(v.trim()).filter { it.isNotBlank() && it != "null" }
        else -> emptyList()
    }

    private fun formatReleaseDate(raw: String?): String? {
        val m = Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(raw.orEmpty()) ?: return raw?.takeIf { it.isNotBlank() }
        return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
    }

    /**
     * Подробности о треке: альбом, год, дата выхода, жанр, лейбл, авторы. Что сервер не отдал — остаётся пустым.
     * Авторы текста берутся из «supplement» трека (поля writers и т. п.), авторы музыки — из исполнителей с пометкой composer.
     */
    override fun trackInfo(t: Track): TrackInfo {
        val key = if (t.albumId != null) "${t.id}:${t.albumId}" else t.id
        val o = request("/tracks", mapOf("track-ids" to key)).getJSONArray("result").optJSONObject(0)
            ?: error("Нет данных о треке")
        val alb = o.optJSONArray("albums")?.optJSONObject(0)
        val labels = namesOf(alb?.opt("labels")).ifEmpty {
            listOfNotNull(o.optJSONObject("major")?.optString("name")?.takeIf { it.isNotBlank() })
        }
        val sup = runCatching { request("/tracks/${t.id}/supplement").optJSONObject("result") }.getOrNull()
        val lyr = sup?.optJSONObject("lyrics")
        fun collect(vararg keys: String): List<String> =
            keys.flatMap { k -> namesOf(o.opt(k)) + namesOf(lyr?.opt(k)) + namesOf(sup?.opt(k)) }.distinct()
        val writers = collect("writers", "lyricists", "lyricist", "authors", "textAuthors")
        var composers = collect("composers", "composer")
        if (composers.isEmpty()) {
            val arts = o.optJSONArray("artists")
            if (arts != null) composers = (0 until arts.length()).mapNotNull { i ->
                arts.optJSONObject(i)?.takeIf { it.optBoolean("composer", false) }?.optString("name")?.takeIf { it.isNotBlank() }
            }
        }
        val artists = namesOf(o.optJSONArray("artists")).joinToString(", ").ifBlank { t.artists }
        return TrackInfo(
            title = o.optString("title").ifBlank { t.title },
            version = o.optString("version").takeIf { it.isNotBlank() },
            artists = artists,
            album = alb?.optString("title")?.takeIf { it.isNotBlank() } ?: t.albumTitle,
            albumId = alb?.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" } ?: t.albumId,
            year = alb?.optInt("year") ?: t.year,
            releaseDate = formatReleaseDate(alb?.optString("releaseDate")?.takeIf { it.isNotBlank() } ?: o.optString("releaseDate")),
            genre = alb?.optString("genre")?.takeIf { it.isNotBlank() } ?: t.genre,
            labels = labels,
            writers = writers,
            composers = composers,
            explicit = o.optString("contentWarning") == "explicit" || o.optBoolean("explicit", false) || t.explicit,
            durationMs = o.optLong("durationMs").takeIf { it > 0 } ?: t.durationMs,
        )
    }
}