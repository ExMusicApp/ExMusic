package app.ymusic.m3e.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** Сохранённые на устройстве списки аккаунта: «Любимое», плейлисты, их треки, альбомы коллекции и открытые альбомы. */
data class OfflineSnapshot(
    val liked: List<Track> = emptyList(),
    val playlists: List<PlaylistInfo> = emptyList(),
    val playlistTracks: Map<String, List<Track>> = emptyMap(),
    /** Открытые альбомы вместе с треками (ключ — id альбома). */
    val albums: Map<String, AlbumInfo> = emptyMap(),
    /** Альбомы из коллекции аккаунта (без треков) — для раздела «Альбомы» без сети. */
    val likedAlbums: List<AlbumInfo> = emptyList(),
) {
    val isEmpty: Boolean get() = liked.isEmpty() && playlists.isEmpty() && likedAlbums.isEmpty()
}

/**
 * Кэш списков для работы без интернета. Пишется при каждой успешной загрузке («Любимое», плейлисты, плейлист, альбом),
 * читается, когда сети нет. Для каждого аккаунта свой файл. Сами песни лежат в [DownloadStore].
 */
class OfflineLibrary(private val ctx: Context) {
    private val io = Executors.newSingleThreadExecutor()
    private val memory = HashMap<String, OfflineSnapshot>()

    private fun file(owner: String) = File(ctx.filesDir, "offline_library_$owner.json")

    @Synchronized
    fun load(owner: String): OfflineSnapshot = memory.getOrPut(owner) { read(owner) }

    private fun read(owner: String): OfflineSnapshot = runCatching {
        val f = file(owner)
        if (!f.exists()) return OfflineSnapshot()
        val o = JSONObject(f.readText())
        val pls = o.optJSONArray("playlists")
        val pt = o.optJSONObject("playlistTracks")
        val al = o.optJSONObject("albums")
        val la = o.optJSONArray("likedAlbums")
        OfflineSnapshot(
            liked = tracksFromJson(o.optJSONArray("liked")),
            playlists = if (pls == null) emptyList() else (0 until pls.length()).mapNotNull { runCatching { playlistFromJson(pls.getJSONObject(it)) }.getOrNull() },
            playlistTracks = pt?.keys()?.asSequence()?.associateWith { tracksFromJson(pt.optJSONArray(it)) } ?: emptyMap(),
            albums = al?.keys()?.asSequence()?.mapNotNull { k -> runCatching { k to albumFromJson(al.getJSONObject(k)) }.getOrNull() }?.toMap() ?: emptyMap(),
            likedAlbums = if (la == null) emptyList() else (0 until la.length()).mapNotNull { runCatching { albumFromJson(la.getJSONObject(it)) }.getOrNull() },
        )
    }.getOrDefault(OfflineSnapshot())

    @Synchronized
    private fun update(owner: String, change: (OfflineSnapshot) -> OfflineSnapshot) {
        val next = change(memory.getOrPut(owner) { read(owner) })
        memory[owner] = next
        io.execute { write(owner, next) }
    }

    private fun write(owner: String, s: OfflineSnapshot) {
        runCatching {
            val o = JSONObject()
                .put("liked", s.liked.toJsonArray())
                .put("playlists", JSONArray().also { a -> s.playlists.forEach { a.put(it.toJson()) } })
                .put("playlistTracks", JSONObject().also { j -> s.playlistTracks.forEach { (k, v) -> j.put(k, v.toJsonArray()) } })
                .put("albums", JSONObject().also { j -> s.albums.forEach { (k, v) -> j.put(k, v.toJson()) } })
                // Альбомы коллекции — без треков, чтобы файл не раздувался.
                .put("likedAlbums", JSONArray().also { a -> s.likedAlbums.forEach { a.put(it.copy(tracks = emptyList()).toJson()) } })
            val tmp = File(file(owner).path + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file(owner))
        }
    }

    /** Сохраняет «Любимое» и плейлисты; [albums] == null — альбомы не менять (не удалось загрузить). */
    fun saveLibrary(owner: String, liked: List<Track>, playlists: List<PlaylistInfo>, albums: List<AlbumInfo>? = null) =
        update(owner) { it.copy(liked = liked, playlists = playlists, likedAlbums = albums ?: it.likedAlbums) }

    fun saveLiked(owner: String, liked: List<Track>) =
        update(owner) { it.copy(liked = liked) }

    fun savePlaylist(owner: String, p: PlaylistInfo, tracks: List<Track>) =
        update(owner) { it.copy(playlistTracks = it.playlistTracks + (p.coverKey() to tracks)) }

    fun saveAlbum(owner: String, a: AlbumInfo) {
        if (a.tracks.isEmpty()) return
        update(owner) { it.copy(albums = it.albums + (a.id to a)) }
    }

    fun clear(owner: String) {
        synchronized(this) { memory.remove(owner) }
        runCatching { file(owner).delete() }
    }
}

/**
 * Альбомы, собранные из скачанных треков: работают без сети и без кэша. Треки группируются по id альбома,
 * внутри — в порядке скачивания.
 */
fun albumsFromDownloads(items: List<DownloadedTrack>): List<AlbumInfo> {
    val groups = LinkedHashMap<String, MutableList<Track>>()
    items.sortedByDescending { it.ts }.forEach { d ->
        val t = d.meta ?: return@forEach
        val id = t.albumId ?: return@forEach
        groups.getOrPut(id) { mutableListOf() }.add(t)
    }
    return groups.mapNotNull { (id, list) ->
        val tracks = list.reversed()
        val first = tracks.first()
        val title = tracks.firstNotNullOfOrNull { it.albumTitle?.takeIf { s -> s.isNotBlank() } } ?: return@mapNotNull null
        val refs = tracks.flatMap { it.artistRefs.take(1) }.distinctBy { it.id }.take(3)
        AlbumInfo(
            id = id,
            title = title,
            artists = refs.joinToString(", ") { it.name }.ifBlank { first.artists },
            artistRefs = refs,
            coverUri = tracks.firstNotNullOfOrNull { it.coverUri },
            year = tracks.firstOrNull { it.year > 0 }?.year ?: 0,
            trackCount = tracks.size,
            tracks = tracks,
        )
    }
}

/** Альбомы коллекции плюс альбомы из скачанного (без повторов): так раздел «Альбомы» полон и без сети. */
fun mergeAlbums(liked: List<AlbumInfo>, downloaded: List<AlbumInfo>): List<AlbumInfo> {
    val seen = liked.mapTo(HashSet()) { it.id }
    return liked + downloaded.filter { it.id !in seen }
}
