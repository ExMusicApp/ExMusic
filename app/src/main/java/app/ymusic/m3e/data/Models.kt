package app.ymusic.m3e.data

data class Account(
    val uid: Long,
    val login: String,
    val name: String,
    val hasPlus: Boolean,
    val avatarUrl: String? = null,
) {
    val apiId: String get() = if (uid != 0L) uid.toString() else login.ifBlank { "me" }
}

data class Track(
    val id: String,
    val albumId: String?,
    val title: String,
    val artists: String,
    val coverUri: String?,
    val durationMs: Long,
    val artistRefs: List<ArtistRef> = emptyList(),
    val albumTitle: String? = null,
    val explicit: Boolean = false,
    val localUri: String? = null,
    val year: Int = 0,
    val label: String? = null,
    val genre: String? = null,
    val version: String? = null,
    val fileName: String? = null,
    val localAlbumId: Long = 0L,
    val trackNo: Int = 0,
    val filePath: String? = null,
) {
    val isLocal: Boolean get() = localUri != null

    fun cover(size: Int = 400): String? =
        coverUri?.let { buildCoverUrl(it, size) }
}

data class ArtistRef(val id: String, val name: String, val coverUri: String? = null) {
    fun cover(size: Int = 200): String? = coverUri?.let { buildCoverUrl(it, size) }
}

data class ArtistInfo(
    val id: String,
    val name: String,
    val coverUri: String?,
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumInfo> = emptyList(),
    val allCovers: List<String> = emptyList(),
) {
    fun cover(size: Int = 400): String? =
        coverUri?.let { buildCoverUrl(it, size) }

    fun photos(size: Int = 1000): List<String> =
        if (allCovers.isNotEmpty()) allCovers.map { buildCoverUrl(it, size) }
        else listOfNotNull(cover(size))
}

data class AlbumInfo(
    val id: String,
    val title: String,
    val artists: String = "",
    val artistRefs: List<ArtistRef> = emptyList(),
    val coverUri: String? = null,
    val year: Int = 0,
    val trackCount: Int = 0,
    val tracks: List<Track> = emptyList(),
    val releaseDate: String = "",
) {
    fun cover(size: Int = 400): String? =
        coverUri?.let { buildCoverUrl(it, size) }

    val sortDate: String
        get() = releaseDate.take(10).ifBlank { if (year > 0) "$year-01-01" else "" }
}

data class PlaylistInfo(
    val kind: Int,
    val ownerUid: String,
    val title: String,
    val trackCount: Int,
    val coverUri: String?,
    val visibility: String? = null,
    val ownerLogin: String? = null,
) {
    fun cover(size: Int = 400): String? =
        coverUri?.let { buildCoverUrl(it, size) }

    val isPublic: Boolean get() = visibility == "public"
}

internal fun buildCoverUrl(uri: String, size: Int): String {
    if (uri.startsWith("content://") || uri.startsWith("file://")) return uri
    val sized = uri.replace("%%", "${size}x$size")
    return if (sized.startsWith("http")) sized else "https://" + sized.removePrefix("//")
}

data class WaveSettings(
    val diversity: String = "default",
    val moodEnergy: String = "all",
    val language: String = "any",
)

fun PlaylistInfo.isEditableBy(acc: Account?): Boolean =
    acc != null && kind != 3 &&
        (ownerUid == acc.uid.toString() || ownerUid == acc.apiId || (acc.login.isNotBlank() && ownerUid == acc.login))

fun PlaylistInfo.coverKey(): String = "$ownerUid:$kind"

data class TrackInfo(
    val title: String,
    val version: String? = null,
    val artists: String = "",
    val album: String? = null,
    val albumId: String? = null,
    val year: Int = 0,
    val releaseDate: String? = null,
    val genre: String? = null,
    val labels: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val explicit: Boolean = false,
    val durationMs: Long = 0,
)
