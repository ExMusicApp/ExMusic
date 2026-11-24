package app.ymusic.m3e.data

/** Музыкальный сервис, к которому привязан аккаунт. */
enum class Provider(val id: String, val title: String) {
    YANDEX("yandex", "Яндекс Музыка"),
    VK("vk", "VK Музыка"),
    SOUNDCLOUD("soundcloud", "SoundCloud"),
    SPOTIFY("spotify", "Spotify");

    companion object {
        fun fromId(id: String?): Provider = entries.firstOrNull { it.id == id } ?: YANDEX
    }
}

/** Порция треков волны / VK Микса. */
class WaveBatch(val tracks: List<Track>, val batchId: String?)

/** История с сервера: все треки (новые первыми) и те из них, что играли в «Моей волне» / VK Миксе. */
class RemoteHistory(val tracks: List<Track>, val waveTracks: List<Track>)

/** Что умеет сервис: интерфейс прячет или показывает соответствующие кнопки. */
data class ApiCaps(
    val dislikes: Boolean = true,
    val podcasts: Boolean = true,
    val chart: Boolean = true,
    /** Лимит 30 секунд без подписки (только Яндекс). */
    val previewLimit: Boolean = true,
    /** Колонки и «Подключить» (только Яндекс). */
    val connect: Boolean = true,
    /** Настройки волны: классические (Яндекс) или категории VK Микса. */
    val mixSettings: Boolean = false,
    /** Своя подборка «Для вас» (плейлисты, собранные алгоритмами). */
    val recommendations: Boolean = false,
    /** Новинки приходят готовыми с сервера (без подсчёта по любимым артистам). */
    val nativeNewReleases: Boolean = false,
    /** Волна по треку / исполнителю / альбому / плейлисту. */
    val waveByEntity: Boolean = true,
    /** Переключатель «публичный / приватный» у плейлиста. */
    val playlistVisibility: Boolean = true,
)

/** Одна настройка VK Микса: категория с вариантами. */
data class MixOption(val id: String, val title: String, val selected: Boolean)
data class MixCategory(val id: String, val title: String, val options: List<MixOption>)
data class MixSettings(
    val title: String,
    val subtitle: String,
    val categories: List<MixCategory>,
    val multiSelect: Boolean,
)

/** Полка «Для вас»: заголовок и плейлисты (например, «Плейлист дня», «Дежавю», «Тайник»). */
data class PlaylistShelf(val id: String, val title: String, val playlists: List<PlaylistInfo>)

/** Новинки одним ответом: свежие релизы и их треки. */
class NativeReleases(val albums: List<AlbumInfo>, val tracks: List<Track>)

/**
 * Всё, что приложению нужно от музыкального сервиса. Реализации: [YandexMusicApi], [VkMusicApi];
 * [MusicRouter] выбирает нужную по активному аккаунту (а для треков — по префиксу id).
 */
interface MusicApi {
    val caps: ApiCaps
    var highQuality: () -> Boolean
    val plusConfirmedByStream: Boolean

    fun resetSession()
    fun account(): Account

    fun likedTracks(uid: String, limit: Int = Int.MAX_VALUE): List<Track>
    fun tracksByIds(keys: List<String>): List<Track>
    fun likeTrack(uid: String, t: Track)
    fun unlikeTrack(uid: String, t: Track)
    fun dislikeTrack(uid: String, t: Track)
    fun undislikeTrack(uid: String, t: Track)
    fun dislikedTrackIds(uid: String): Set<String>

    fun playlists(uid: String): List<PlaylistInfo>
    fun playlistInfo(ownerUid: String, kind: Int): PlaylistInfo
    fun playlistInfoByUuid(uuid: String): PlaylistInfo
    fun setPlaylistVisibility(ownerUid: String, kind: Int, isPublic: Boolean)
    fun playlistTracks(ownerUid: String, kind: Int): List<Track>
    fun createPlaylist(uid: String, title: String, isPublic: Boolean): PlaylistInfo
    fun renamePlaylist(ownerUid: String, kind: Int, title: String)
    fun deletePlaylist(ownerUid: String, kind: Int)
    fun addTrackToPlaylist(ownerUid: String, kind: Int, track: Track)
    fun removeTrackFromPlaylist(ownerUid: String, kind: Int, track: Track)

    fun waveTracks(afterTrackId: String? = null, station: String = "user:onyourwave"): WaveBatch
    fun waveSettings(): WaveSettings
    fun saveWaveSettings(s: WaveSettings)
    fun waveTrackStarted(trackId: String, batchId: String?, station: String = "user:onyourwave")
    fun waveStarted(station: String)

    fun search(query: String): List<Track>
    fun searchArtists(query: String): List<ArtistInfo>
    fun searchAlbums(query: String): List<AlbumInfo>
    fun searchPodcasts(query: String): List<AlbumInfo>
    fun podcastsLanding(): List<AlbumInfo>

    fun streamUrl(trackId: String): String
    fun downloadUrl(trackId: String): String

    fun artist(id: String): ArtistInfo
    fun artistRecentAlbums(id: String): List<AlbumInfo>
    fun artistAllTracks(id: String): List<Track>
    fun album(id: String): AlbumInfo
    fun likedAlbums(uid: String): List<AlbumInfo>
    fun likeAlbum(uid: String, albumId: String)
    fun unlikeAlbum(uid: String, albumId: String)

    fun chart(): List<Track>
    fun trackLyrics(trackId: String, synced: Boolean): String?
    fun musicHistory(limit: Int = 60): List<Track>
    fun musicHistoryFull(limit: Int = 60): RemoteHistory
    fun trackInfo(t: Track): TrackInfo

    // ---- Необязательное (по умолчанию «нет») ----

    /**
     * Свой способ скачивания (VK: HLS с шифрованием): складывает трек в [dest] и возвращает расширение файла
     * (mp3, aac). null — скачивать по [downloadUrl] обычным запросом.
     */
    fun downloadCustom(trackId: String, dest: java.io.File, onProgress: (Float) -> Unit, check: () -> Unit): String? = null

    /** Категории VK Микса (null — у сервиса таких настроек нет). */
    fun mixSettings(): MixSettings? = null

    /** Сохранить выбор в VK Миксе: строка options, как её ждёт сервер. */
    fun saveMixOptions(options: String) {}

    /** Полки «Для вас»: плейлисты, собранные алгоритмами. */
    fun recommendedShelves(): List<PlaylistShelf> = emptyList()

    /** Новинки, собранные сервером. */
    fun nativeNewReleases(): NativeReleases? = null
}

/** Приставка id треков VK: "vk_<владелец>_<номер>". Так id не пересекаются с Яндексом и по ним выбирается сервис. */
object VkIds {
    const val PREFIX = "vk_"

    fun isVk(id: String?): Boolean = id != null && id.startsWith(PREFIX)

    fun track(ownerId: Long, audioId: Long): String = "$PREFIX${ownerId}_$audioId"

    /** "vk_-123_456" → "-123_456"; null, если это не трек VK. */
    fun raw(id: String): String? = if (isVk(id)) id.removePrefix(PREFIX) else null
}
