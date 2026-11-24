package app.ymusic.m3e.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLDecoder

/** Что открыла ссылка на Яндекс Музыку. */
sealed class SharedLink {
    data class TrackLink(val trackId: String, val albumId: String?) : SharedLink()
    data class AlbumLink(val albumId: String) : SharedLink()
    data class ArtistLink(val artistId: String) : SharedLink()
    data class PlaylistLink(val owner: String, val kind: Int) : SharedLink()
    /** Новый вид ссылки на плейлист: music.yandex.ru/playlists/<uuid> (в том числе lk.<uuid>). */
    /** Профиль пользователя: его публичные плейлисты. */
    data class UserLink(val login: String) : SharedLink()
    data class PlaylistUuidLink(val uuid: String) : SharedLink()
    /** Ссылка SoundCloud (трек, плейлист, профиль, в том числе короткая on.soundcloud.com/…): что за ней, выясняется при открытии. */
    data class SoundCloudLink(val url: String) : SharedLink()
    /** Трек VK Музыки: [raw] — «владелец_номер[_ключ]» (из vk.com/audio-123_456). */
    data class VkTrackLink(val raw: String) : SharedLink()
    /** Плейлист или альбом VK Музыки: [id] — «владелец_номер[_ключ]» (так же, как id альбома в приложении). */
    data class VkAlbumLink(val id: String) : SharedLink()
    /** Страница исполнителя VK Музыки (vk.com/artist/<домен>). */
    data class VkArtistLink(val id: String) : SharedLink()
}

/**
 * Ссылки для «Поделиться» — обычные адреса music.yandex.ru: их откроет и это приложение, и сайт / приложение Яндекс Музыки.
 * Приватными плейлистами поделиться нельзя: по ссылке их не откроет никто, кроме владельца.
 */
object ShareLinks {
    private const val BASE = "https://music.yandex.ru"

    fun track(t: Track): String? {
        if (t.isLocal || t.id.startsWith("local:")) return null
        VkIds.raw(t.id)?.let { return "https://vk.com/audio$it" }
        if (ScIds.isSc(t.id)) return t.accessKey?.takeIf { it.startsWith("http") }
        val album = t.albumId?.takeIf { it.isNotBlank() }
        return if (album != null) "$BASE/album/$album/track/${t.id}" else "$BASE/track/${t.id}"
    }

    /** Ссылка на профиль: по ней открывается список публичных плейлистов. */
    fun profile(login: String): String? = login.takeIf { it.isNotBlank() }?.let { "$BASE/users/$it" }

    fun album(a: AlbumInfo): String? = if (ScIds.isSc(a.id)) null else vkAlbum(a.id) ?: a.id.takeIf { it.isNotBlank() && !it.startsWith("local") }?.let { "$BASE/album/$it" }

    /** Альбомы и плейлисты VK: id вида «владелец_номер[_ключ]» (у Яндекса — только цифры). */
    private fun vkAlbum(id: String): String? =
        if (Regex("""-?\d+_\d+(_[A-Za-z0-9]+)?""").matches(id)) "https://vk.com/music/album/$id" else null

    fun artist(id: String): String? = when {
        id.isBlank() || id.startsWith(LOCAL_ARTIST_PREFIX) || id.startsWith("q:") || ScIds.isSc(id) -> null
        !id.all { it.isDigit() } -> "https://vk.com/artist/$id" // у Яндекса id только из цифр
        else -> "$BASE/artist/$id"
    }

    /** null — плейлист приватный (или неизвестно, публичный ли он): поделиться нельзя. */
    fun playlist(p: PlaylistInfo): String? {
        if (p.accessKey != null) return "https://vk.com/music/playlist/${p.ownerUid}_${p.kind}_${p.accessKey}"
        if (!p.isPublic || p.kind == 3) return null
        val owner = p.ownerLogin?.takeIf { it.isNotBlank() } ?: p.ownerUid
        return "$BASE/users/$owner/playlists/${p.kind}"
    }

    /** Разбирает ссылку на трек / альбом / исполнителя / плейлист Яндекс Музыки; для остальных адресов — null. */
    fun parse(raw: String?): SharedLink? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val uri = runCatching { Uri.parse(if (text.contains("://")) text else "https://$text") }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host in SC_HOSTS) return parseSoundCloud(uri, host)
        if (host in VK_HOSTS) return parseVk(uri)
        // yandexmusic://album/123, yandexmusic://track/456 — «глубокие» ссылки самого приложения Яндекс Музыки: тип стоит в адресе вместо хоста.
        val isDeep = uri.scheme.equals("yandexmusic", ignoreCase = true)
        // Подходят music.yandex.ru / .com / .by / .kz / .uz и мобильные m.music.yandex.*
        if (!isDeep && !(host.startsWith("music.yandex.") || host.contains(".music.yandex."))) return null
        val parts = if (isDeep) listOf(host) + uri.pathSegments else uri.pathSegments
        val seg = parts.map { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        if (seg.isEmpty()) return null
        return when (seg[0]) {
            "album" -> when {
                seg.size >= 4 && seg[2] == "track" -> SharedLink.TrackLink(seg[3], seg[1])
                seg.size >= 2 -> SharedLink.AlbumLink(seg[1])
                else -> null
            }
            "track" -> seg.getOrNull(1)?.let { SharedLink.TrackLink(it, null) }
            "artist", "artists" -> seg.getOrNull(1)?.let { SharedLink.ArtistLink(it) }
            "playlists" -> seg.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { SharedLink.PlaylistUuidLink(it) }
            "users" -> {
                val kind = seg.getOrNull(3)?.toIntOrNull()
                when {
                    seg.size >= 4 && seg[2] == "playlists" && kind != null -> SharedLink.PlaylistLink(seg[1], kind)
                    seg.size == 2 || (seg.size == 3 && seg[2] == "playlists") -> SharedLink.UserLink(seg[1])
                    else -> null
                }
            }
            else -> null
        }
    }

    private val VK_HOSTS = setOf("vk.com", "www.vk.com", "m.vk.com", "vk.ru", "www.vk.ru", "m.vk.ru", "music.vk.com", "music.vk.ru", "www.music.vk.com", "www.music.vk.ru")
    private val VK_TRACK_RE = Regex("""audio(-?\d+_\d+(?:_[A-Za-z0-9]+)?)""")
    private val VK_ALBUM_RE = Regex("""-?\d+_\d+(?:_[A-Za-z0-9]+)?""")
    private val VK_PLAYLIST_Z_RE = Regex("""audio_(?:playlist|album)(-?\d+_\d+)(?:[/_]([A-Za-z0-9]+))?""")

    /**
     * Ссылки VK Музыки: vk.com/audio-1_2[_ключ] (трек), vk.com/music/playlist/-1_2_ключ и /music/album/… (плейлист, альбом),
     * vk.com/artist/<домен> (исполнитель), а также адреса вида vk.com/audios123?z=audio_playlist-1_2%2Fключ.
     * Ключ доступа запоминается: по нему открываются чужие и закрытые плейлисты.
     */
    private fun parseVk(uri: Uri): SharedLink? {
        val seg = uri.pathSegments.filter { it.isNotBlank() }.map { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        val first = seg.firstOrNull()?.lowercase().orEmpty()
        fun album(id: String): SharedLink? {
            if (!VK_ALBUM_RE.matches(id)) return null
            val p = id.split("_")
            p.getOrNull(2)?.let { VkKeys.rememberPlaylist(p[0], p[1].toIntOrNull() ?: 0, it) }
            return SharedLink.VkAlbumLink(id)
        }
        // Параметр z (окно внутри страницы): audio_playlist-1_2/ключ или audio-1_2_ключ; в старых адресах — act=audio_playlist…&access_hash=…
        val z = uri.getQueryParameter("z") ?: uri.getQueryParameter("act")
        if (z != null) {
            VK_PLAYLIST_Z_RE.find(z)?.let { m ->
                val key = m.groupValues[2].ifBlank { uri.getQueryParameter("access_hash").orEmpty() }
                return album(m.groupValues[1] + if (key.isNotBlank()) "_$key" else "")
            }
            VK_TRACK_RE.find(z)?.let { return SharedLink.VkTrackLink(it.groupValues[1]) }
        }
        VK_TRACK_RE.matchEntire(seg.firstOrNull().orEmpty())?.let { return SharedLink.VkTrackLink(it.groupValues[1]) }
        // vk.com/audio_playlist-1_2_ключ, vk.com/audio_playlist-1_2/ключ (так выглядят ссылки «Поделиться» в части приложений VK).
        Regex("""audio_(?:playlist|album)(-?\d+_\d+)(?:_([A-Za-z0-9]+))?""").matchEntire(seg.firstOrNull().orEmpty())?.let { m ->
            val key = m.groupValues[2].ifBlank { seg.getOrNull(1).orEmpty().ifBlank { uri.getQueryParameter("access_hash").orEmpty() } }
            return album(m.groupValues[1] + if (key.isNotBlank()) "_$key" else "")
        }
        return when (first) {
            "music" -> when (seg.getOrNull(1)?.lowercase()) {
                "album", "playlist" -> seg.getOrNull(2)?.let { album(it) }
                "artist" -> seg.getOrNull(2)?.let { SharedLink.VkArtistLink(it) }
                else -> null
            }
            "artist" -> seg.getOrNull(1)?.let { SharedLink.VkArtistLink(it) }
            // Адреса отдельного сайта музыки: music.vk.com/playlist/…, /album/…, /artist/…
            "playlist", "album" -> if (uri.host?.contains("music.") == true) seg.getOrNull(1)?.let { album(it) } else null
            "audio" -> uri.getQueryParameter("z")?.let { VK_TRACK_RE.find(it)?.let { m -> SharedLink.VkTrackLink(m.groupValues[1]) } }
            else -> null
        }
    }

    private val SC_HOSTS = setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com", "on.soundcloud.com", "soundcloud.app.goo.gl")

    /** Первые части адреса, которые не ведут на трек, плейлист или профиль (служебные страницы сайта). */
    private val SC_RESERVED = setOf(
        "discover", "you", "stream", "upload", "settings", "search", "charts", "pages", "feed", "messages", "notifications",
        "jobs", "terms-of-use", "popular", "mobile", "tracks", "people", "logout", "signin", "connect", "download", "imprint", "creators",
    )

    /** Вкладки профиля SoundCloud (soundcloud.com/<ник>/likes и т. п.): открываем сам профиль. */
    private val SC_PROFILE_TABS = setOf("tracks", "albums", "sets", "reposts", "likes", "popular-tracks", "following", "followers", "comments", "spotlight")

    /** Ссылка SoundCloud: трек, плейлист (/sets/), профиль или короткая on.soundcloud.com/…; null — служебная страница сайта. */
    private fun parseSoundCloud(uri: Uri, host: String): SharedLink? {
        val seg = uri.pathSegments.filter { it.isNotBlank() }
        if (seg.isEmpty()) return null
        if (host == "on.soundcloud.com" || host == "soundcloud.app.goo.gl") return SharedLink.SoundCloudLink("https://$host/${seg.joinToString("/")}")
        // Готовые миксы SoundCloud: soundcloud.com/discover/sets/<микс> — открываем, остальные страницы discover служебные.
        if (seg[0].lowercase() == "discover" && seg.size >= 3 && seg[1].lowercase() == "sets") {
            return SharedLink.SoundCloudLink("https://soundcloud.com/${seg.joinToString("/")}")
        }
        if (seg[0].lowercase() in SC_RESERVED) return null
        val path = if (seg.size == 2 && seg[1].lowercase() in SC_PROFILE_TABS) seg.take(1) else seg
        return SharedLink.SoundCloudLink("https://soundcloud.com/${path.joinToString("/")}")
    }

    /** Ссылка внутри произвольного текста (например, скопированная из сообщения): берём первую подходящую. */
    fun parseFromText(text: String): SharedLink? =
        Regex("""(?:https?|yandexmusic)://(?:[a-z0-9-]+\.)*(?:music\.yandex\.[a-z]+|[a-z]+)/?\S*""", RegexOption.IGNORE_CASE)
            .findAll(text).firstNotNullOfOrNull { parse(it.value.trimEnd('.', ',', ')', '!', '»', '"')) }
            // Ссылка без «https://» (скопирована как soundcloud.com/… или music.yandex.ru/…): тоже подходит.
            ?: Regex("""(?:[a-z0-9-]+\.)*(?:soundcloud\.com|vk\.com|vk\.ru|music\.yandex\.[a-z]+)/\S+""", RegexOption.IGNORE_CASE)
                .findAll(text).firstNotNullOfOrNull { parse(it.value.trimEnd('.', ',', ')', '!', '»', '"')) }

    /** Открывает системное окно «Поделиться» с текстом. */
    fun shareText(ctx: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { ctx.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Файл локального трека целиком: для файлов с устройства ссылки нет, отправляется сам файл. */
    fun shareFile(ctx: Context, t: Track) {
        val uri = t.localUri?.let { Uri.parse(it) } ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(t.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(Intent.createChooser(send, "Поделиться файлом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Действия окна «Поделиться» с QR-кодом: копирование ссылки / картинки, отправка и сохранение QR. */
object QrShare {
    fun copyLink(ctx: Context, link: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("link", link))
    }

    private fun qrUri(ctx: Context, bmp: android.graphics.Bitmap): Uri {
        val dir = java.io.File(ctx.cacheDir, "qr").apply { mkdirs() }
        val f = java.io.File(dir, "qr.png")
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        return androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }

    /** Кладёт QR-код в буфер обмена картинкой. */
    fun copyQr(ctx: Context, bmp: android.graphics.Bitmap): Boolean = runCatching {
        val uri = qrUri(ctx, bmp)
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newUri(ctx.contentResolver, "QR", uri))
    }.isSuccess

    /** Системное окно «Поделиться» с картинкой QR-кода и ссылкой. */
    fun sendQr(ctx: Context, bmp: android.graphics.Bitmap, link: String) {
        runCatching {
            val uri = qrUri(ctx, bmp)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, link)
                clipData = android.content.ClipData.newRawUri("QR", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(send, "Отправить QR-код").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
