package app.ymusic.m3e.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/** Музыка, лежащая на самом устройстве (MediaStore). Нужен доступ к аудиофайлам. */
object LocalMusic {
    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, permission()) == PackageManager.PERMISSION_GRANTED

    /**
     * Все музыкальные файлы устройства, по алфавиту. Вызывать не из главного потока.
     * [withRefs] — режим «Локальные файлы»: у трека есть ссылка на исполнителя (по ней открывается страница исполнителя из файлов).
     */
    fun load(ctx: Context, withRefs: Boolean = false): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
        )
        val albumArt = Uri.parse("content://media/external/audio/albumart")
        val out = ArrayList<Track>()
        ctx.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataCol = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val fileName = c.getString(nameCol)?.takeIf { it.isNotBlank() }
                val edit = LocalTrackEdits.get("local:$id")
                val title = edit?.title
                    ?: c.getString(titleCol)?.takeIf { it.isNotBlank() }
                    ?: fileName?.substringBeforeLast('.')
                    ?: "Без названия"
                val artist = edit?.artist
                    ?: c.getString(artistCol)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                    ?: UNKNOWN_ARTIST
                val album = c.getString(albumCol)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                val albumId = c.getLong(albumIdCol)
                out += Track(
                    id = "local:$id",
                    albumId = null,
                    title = title,
                    artists = artist,
                    coverUri = edit?.coverPath?.let { "file://$it" }
                        ?: if (albumId > 0) ContentUris.withAppendedId(albumArt, albumId).toString() else null,
                    durationMs = c.getLong(durCol),
                    artistRefs = if (withRefs) listOf(ArtistRef(LOCAL_ARTIST_PREFIX + artist, artist)) else emptyList(),
                    albumTitle = album,
                    localUri = ContentUris.withAppendedId(collection, id).toString(),
                    year = c.getInt(yearCol).coerceAtLeast(0),
                    fileName = fileName,
                    localAlbumId = albumId,
                    trackNo = c.getInt(trackCol).coerceAtLeast(0),
                    filePath = if (dataCol >= 0) c.getString(dataCol)?.takeIf { it.isNotBlank() } else null,
                )
            }
        }
        return out.sortedBy { it.title.lowercase() }
    }
}

/** Префикс id исполнителя из файлов устройства (в [ArtistRef.id]): по нему приложение понимает, что страницу нужно открыть локально. */
const val LOCAL_ARTIST_PREFIX = "local-artist:"

const val UNKNOWN_ARTIST = "Неизвестный исполнитель"
const val UNKNOWN_ALBUM = "Неизвестный альбом"

/** Альбом из файлов устройства: треки в порядке номеров. */
data class LocalAlbum(
    val key: String,
    val title: String,
    val artist: String,
    val coverUri: String?,
    val year: Int,
    val tracks: List<Track>,
) {
    /** Тот же альбом в виде [AlbumInfo] — чтобы показывать его теми же карточками, что и альбомы Яндекс Музыки. */
    fun info(): AlbumInfo = AlbumInfo(
        id = key, title = title, artists = artist, coverUri = coverUri, year = year,
        trackCount = tracks.size, tracks = tracks,
    )
}

/** Исполнитель из файлов устройства: все его треки и альбомы, в которых он есть. */
data class LocalArtist(
    val name: String,
    val coverUri: String?,
    val tracks: List<Track>,
    val albums: List<LocalAlbum>,
) {
    fun info(): ArtistInfo = ArtistInfo(
        id = LOCAL_ARTIST_PREFIX + name, name = name, coverUri = coverUri, tracks = tracks,
        albums = albums.map { it.info() },
    )
}

/** Альбомы и исполнители, собранные из списка файлов устройства. */
data class LocalLibrary(
    val albums: List<LocalAlbum> = emptyList(),
    val artists: List<LocalArtist> = emptyList(),
) {
    fun album(key: String): LocalAlbum? = albums.firstOrNull { it.key == key }
    fun artist(name: String): LocalArtist? = artists.firstOrNull { it.name == name }

    companion object {
        val EMPTY = LocalLibrary()

        /** Группирует файлы по альбомам (id альбома в MediaStore, а если его нет — название + исполнитель) и по исполнителям. */
        fun build(tracks: List<Track>): LocalLibrary {
            if (tracks.isEmpty()) return EMPTY
            val order = compareBy<Track>({ if (it.trackNo == 0) Int.MAX_VALUE else it.trackNo }, { it.title.lowercase() })
            val albums = tracks.groupBy { it.localAlbumKey() }.map { (key, list) ->
                val sorted = list.sortedWith(order)
                val artists = sorted.map { it.artists }.distinct()
                LocalAlbum(
                    key = key,
                    title = sorted.first().albumTitle ?: UNKNOWN_ALBUM,
                    artist = if (artists.size == 1) artists[0] else "Разные исполнители",
                    coverUri = sorted.firstNotNullOfOrNull { it.coverUri },
                    year = sorted.maxOf { it.year },
                    tracks = sorted,
                )
            }.sortedBy { it.title.lowercase() }
            val albumsByArtist = HashMap<String, MutableList<LocalAlbum>>()
            albums.forEach { a ->
                a.tracks.map { it.artists }.distinct().forEach { albumsByArtist.getOrPut(it) { ArrayList() }.add(a) }
            }
            val artists = tracks.groupBy { it.artists }.map { (name, list) ->
                val sorted = list.sortedBy { it.title.lowercase() }
                LocalArtist(
                    name = name,
                    coverUri = sorted.firstNotNullOfOrNull { it.coverUri },
                    tracks = sorted,
                    albums = albumsByArtist[name].orEmpty(),
                )
            }.sortedBy { it.name.lowercase() }
            return LocalLibrary(albums, artists)
        }
    }
}

/** Ключ альбома файла: id альбома в MediaStore, а если его нет — название и исполнитель. */
fun Track.localAlbumKey(): String =
    if (localAlbumId > 0) "a$localAlbumId" else "t:${(albumTitle ?: "").lowercase()}|${artists.lowercase()}"

/** Настройка «Файлы с устройства» (Настройки → Офлайн-режим). Хранится в SharedPreferences. */
object LocalFilesSettings {
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("local_files", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", true)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
    }
}

/**
 * Плейлисты-папки: выбранная на устройстве или SD-карте папка работает как отдельный плейлист. Список треков
 * не хранится, а собирается из файлов устройства по пути (включая вложенные папки), поэтому новые файлы
 * в папке появляются в плейлисте сами.
 */
object LocalFolders {
    /** Путь папки, выбранной системным окном (ACTION_OPEN_DOCUMENT_TREE): внутренняя память, SD-карта или USB. */
    fun pathFromTree(uri: Uri): String? = runCatching {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val colon = docId.indexOf(':')
        if (colon < 0) return@runCatching null
        val volume = docId.substring(0, colon)
        val rel = docId.substring(colon + 1).trim('/')
        val root = when {
            volume.equals("primary", ignoreCase = true) -> android.os.Environment.getExternalStorageDirectory().path
            volume.equals("raw", ignoreCase = true) -> return@runCatching rel.let { "/$it".replace("//", "/") }.trimEnd('/')
            volume.equals("home", ignoreCase = true) || volume.equals("downloads", ignoreCase = true) -> return@runCatching null
            else -> "/storage/$volume"
        }
        if (rel.isEmpty()) root else "$root/$rel"
    }.getOrNull()

    /** Имя папки для названия плейлиста по умолчанию. */
    fun displayName(path: String): String {
        val name = path.trimEnd('/').substringAfterLast('/')
        return name.ifBlank { "Музыка" }
    }

    private fun normalize(path: String) = path.trimEnd('/')

    private val cache = HashMap<String, Pair<List<Track>, List<Track>>>()

    /** Файлы устройства из [all], лежащие в папке [folder] или её подпапках, по именам файлов. */
    @Synchronized
    fun tracksIn(all: List<Track>, folder: String): List<Track> {
        val hit = cache[folder]
        if (hit != null && hit.first === all) return hit.second
        val base = normalize(folder) + "/"
        val list = all.filter { it.filePath?.startsWith(base, ignoreCase = true) == true }
            .sortedWith(compareBy({ (it.filePath ?: "").substringBeforeLast('/').lowercase() }, { (it.fileName ?: it.title).lowercase() }))
        cache[folder] = all to list
        return list
    }
}
