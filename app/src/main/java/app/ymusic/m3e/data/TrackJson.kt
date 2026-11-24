package app.ymusic.m3e.data

import org.json.JSONArray
import org.json.JSONObject

/** Сохранение треков, альбомов и плейлистов в JSON (офлайн-кэш и индекс скачанного). */

internal fun Track.toJson(): JSONObject = JSONObject()
    .put("id", id).put("albumId", albumId ?: "").put("title", title).put("artists", artists)
    .put("cover", coverUri ?: "").put("dur", durationMs).put("album", albumTitle ?: "")
    .put("explicit", explicit).put("year", year).put("label", label ?: "").put("genre", genre ?: "")
    .put("version", version ?: "")
    .put("akey", accessKey ?: "")
    .put(
        "refs",
        JSONArray().also { arr ->
            artistRefs.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("cover", it.coverUri ?: "")) }
        },
    )

internal fun trackFromJson(o: JSONObject): Track {
    val refs = o.optJSONArray("refs")
    return Track(
        id = o.getString("id"),
        albumId = o.optString("albumId").ifBlank { null },
        title = o.optString("title"),
        artists = o.optString("artists"),
        coverUri = o.optString("cover").ifBlank { null },
        durationMs = o.optLong("dur"),
        artistRefs = if (refs == null) emptyList() else List(refs.length()) { i ->
            val r = refs.getJSONObject(i)
            ArtistRef(r.optString("id"), r.optString("name"), r.optString("cover").ifBlank { null })
        },
        albumTitle = o.optString("album").ifBlank { null },
        explicit = o.optBoolean("explicit"),
        year = o.optInt("year"),
        label = o.optString("label").ifBlank { null },
        genre = o.optString("genre").ifBlank { null },
        version = o.optString("version").ifBlank { null },
        accessKey = o.optString("akey").ifBlank { null },
    ).also { t -> VkKeys.remember(t) }
}

internal fun List<Track>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it.toJson()) } }

internal fun tracksFromJson(a: JSONArray?): List<Track> =
    if (a == null) emptyList() else (0 until a.length()).mapNotNull { runCatching { trackFromJson(a.getJSONObject(it)) }.getOrNull() }

internal fun PlaylistInfo.toJson(): JSONObject = JSONObject()
    .put("kind", kind).put("owner", ownerUid).put("title", title).put("count", trackCount).put("cover", coverUri ?: "")
    .put("visibility", visibility ?: "").put("akey", accessKey ?: "")

internal fun playlistFromJson(o: JSONObject) = PlaylistInfo(
    kind = o.getInt("kind"), ownerUid = o.optString("owner"), title = o.optString("title"),
    trackCount = o.optInt("count"), coverUri = o.optString("cover").ifBlank { null },
    visibility = o.optString("visibility").ifBlank { null },
    accessKey = o.optString("akey").ifBlank { null },
).also { p -> if (p.accessKey != null) VkKeys.rememberPlaylist(p.ownerUid, p.kind, p.accessKey) }

internal fun AlbumInfo.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("artists", artists).put("cover", coverUri ?: "")
    .put("year", year).put("count", trackCount)
    .put(
        "refs",
        JSONArray().also { arr ->
            artistRefs.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("cover", it.coverUri ?: "")) }
        },
    )
    .put("tracks", tracks.toJsonArray())

internal fun albumFromJson(o: JSONObject): AlbumInfo {
    val refs = o.optJSONArray("refs")
    return AlbumInfo(
        id = o.getString("id"), title = o.optString("title"), artists = o.optString("artists"),
        artistRefs = if (refs == null) emptyList() else List(refs.length()) { i ->
            val r = refs.getJSONObject(i)
            ArtistRef(r.optString("id"), r.optString("name"), r.optString("cover").ifBlank { null })
        },
        coverUri = o.optString("cover").ifBlank { null }, year = o.optInt("year"),
        trackCount = o.optInt("count"), tracks = tracksFromJson(o.optJSONArray("tracks")),
    )
}
