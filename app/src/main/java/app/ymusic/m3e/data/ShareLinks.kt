package app.ymusic.m3e.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLDecoder

sealed class SharedLink {
    data class TrackLink(val trackId: String, val albumId: String?) : SharedLink()
    data class AlbumLink(val albumId: String) : SharedLink()
    data class ArtistLink(val artistId: String) : SharedLink()
    data class PlaylistLink(val owner: String, val kind: Int) : SharedLink()
    data class UserLink(val login: String) : SharedLink()
    data class PlaylistUuidLink(val uuid: String) : SharedLink()
}

object ShareLinks {
    private const val BASE = "https://music.yandex.ru"

    fun track(t: Track): String? {
        if (t.isLocal || t.id.startsWith("local:")) return null
        val album = t.albumId?.takeIf { it.isNotBlank() }
        return if (album != null) "$BASE/album/$album/track/${t.id}" else "$BASE/track/${t.id}"
    }

    fun profile(login: String): String? = login.takeIf { it.isNotBlank() }?.let { "$BASE/users/$it" }

    fun album(a: AlbumInfo): String? = a.id.takeIf { it.isNotBlank() && !it.startsWith("local") }?.let { "$BASE/album/$it" }

    fun artist(id: String): String? = id.takeIf { it.isNotBlank() && !it.startsWith(LOCAL_ARTIST_PREFIX) }?.let { "$BASE/artist/$it" }

    fun playlist(p: PlaylistInfo): String? {
        if (!p.isPublic || p.kind == 3) return null
        val owner = p.ownerLogin?.takeIf { it.isNotBlank() } ?: p.ownerUid
        return "$BASE/users/$owner/playlists/${p.kind}"
    }

    fun parse(raw: String?): SharedLink? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val uri = runCatching { Uri.parse(if (text.contains("://")) text else "https://$text") }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val isDeep = uri.scheme.equals("yandexmusic", ignoreCase = true)
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

    fun parseFromText(text: String): SharedLink? =
        Regex("""(?:https?|yandexmusic)://(?:[a-z0-9-]+\.)*(?:music\.yandex\.[a-z]+|[a-z]+)/?\S*""", RegexOption.IGNORE_CASE)
            .findAll(text).firstNotNullOfOrNull { parse(it.value.trimEnd('.', ',', ')', '!', '»', '"')) }

    fun shareText(ctx: Context, subject: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { ctx.startActivity(Intent.createChooser(send, "Поделиться").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

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
