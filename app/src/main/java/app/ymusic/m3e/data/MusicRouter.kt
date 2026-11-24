package app.ymusic.m3e.data

import java.io.File

/**
 * Единая точка входа для приложения: пересылает вызовы тому сервису, к которому относится активный аккаунт.
 * Вызовы, привязанные к конкретному треку (поток, скачивание, текст, информация), идут по префиксу id трека:
 * «vk_…» — в VK, «sc_…» — в SoundCloud, остальное — в Яндекс.
 */
class MusicRouter(
    val yandex: YandexMusicApi,
    val vk: VkMusicApi,
    val soundCloud: SoundCloudApi,
    val spotify: SpotifyApi,
    private val activeProvider: () -> Provider,
) : MusicApi {

    private val cur: MusicApi get() = when (activeProvider()) {
        Provider.VK -> vk
        Provider.SOUNDCLOUD -> soundCloud
        Provider.SPOTIFY -> spotify
        else -> yandex
    }
    private fun forTrack(id: String): MusicApi = when {
        VkIds.isVk(id) -> vk
        ScIds.isSc(id) -> soundCloud
        else -> yandex
    }

    override val caps: ApiCaps get() = cur.caps

    override var highQuality: () -> Boolean
        get() = cur.highQuality
        set(value) { yandex.highQuality = value; vk.highQuality = value; soundCloud.highQuality = value; spotify.highQuality = value }

    override val plusConfirmedByStream: Boolean get() = cur.plusConfirmedByStream

    override fun resetSession() { yandex.resetSession(); vk.resetSession(); soundCloud.resetSession(); spotify.resetSession() }
    override fun account(): Account = cur.account()

    override fun likedTracks(uid: String, limit: Int): List<Track> = cur.likedTracks(uid, limit)
    override fun tracksByIds(keys: List<String>): List<Track> = when {
        keys.isNotEmpty() && keys.all { VkIds.isVk(it) } -> vk.tracksByIds(keys)
        keys.isNotEmpty() && keys.all { ScIds.isSc(it) } -> soundCloud.tracksByIds(keys)
        else -> cur.tracksByIds(keys)
    }
    override fun likeTrack(uid: String, t: Track) = cur.likeTrack(uid, t)
    override fun unlikeTrack(uid: String, t: Track) = cur.unlikeTrack(uid, t)
    override fun dislikeTrack(uid: String, t: Track) = cur.dislikeTrack(uid, t)
    override fun undislikeTrack(uid: String, t: Track) = cur.undislikeTrack(uid, t)
    override fun dislikedTrackIds(uid: String): Set<String> = cur.dislikedTrackIds(uid)

    override fun playlists(uid: String): List<PlaylistInfo> = cur.playlists(uid)
    override fun playlistInfo(ownerUid: String, kind: Int): PlaylistInfo = cur.playlistInfo(ownerUid, kind)
    override fun playlistInfoByUuid(uuid: String): PlaylistInfo = cur.playlistInfoByUuid(uuid)
    override fun setPlaylistVisibility(ownerUid: String, kind: Int, isPublic: Boolean) =
        cur.setPlaylistVisibility(ownerUid, kind, isPublic)
    override fun playlistTracks(ownerUid: String, kind: Int): List<Track> = cur.playlistTracks(ownerUid, kind)
    override fun createPlaylist(uid: String, title: String, isPublic: Boolean): PlaylistInfo = cur.createPlaylist(uid, title, isPublic)
    override fun renamePlaylist(ownerUid: String, kind: Int, title: String) = cur.renamePlaylist(ownerUid, kind, title)
    override fun deletePlaylist(ownerUid: String, kind: Int) = cur.deletePlaylist(ownerUid, kind)
    override fun addTrackToPlaylist(ownerUid: String, kind: Int, track: Track) = cur.addTrackToPlaylist(ownerUid, kind, track)
    override fun removeTrackFromPlaylist(ownerUid: String, kind: Int, track: Track) = cur.removeTrackFromPlaylist(ownerUid, kind, track)

    override fun waveTracks(afterTrackId: String?, station: String): WaveBatch = cur.waveTracks(afterTrackId, station)
    override fun waveSettings(): WaveSettings = cur.waveSettings()
    override fun saveWaveSettings(s: WaveSettings) = cur.saveWaveSettings(s)
    override fun waveTrackStarted(trackId: String, batchId: String?, station: String) = cur.waveTrackStarted(trackId, batchId, station)
    override fun waveStarted(station: String) = cur.waveStarted(station)

    override fun search(query: String): List<Track> = cur.search(query)
    override fun searchArtists(query: String): List<ArtistInfo> = cur.searchArtists(query)
    override fun searchAlbums(query: String): List<AlbumInfo> = cur.searchAlbums(query)
    override fun searchPodcasts(query: String): List<AlbumInfo> = cur.searchPodcasts(query)
    override fun podcastsLanding(): List<AlbumInfo> = cur.podcastsLanding()

    override fun streamUrl(trackId: String): String = forTrack(trackId).streamUrl(trackId)
    override fun downloadUrl(trackId: String): String = forTrack(trackId).downloadUrl(trackId)
    override fun downloadCustom(trackId: String, dest: File, onProgress: (Float) -> Unit, check: () -> Unit): String? =
        forTrack(trackId).downloadCustom(trackId, dest, onProgress, check)

    override fun artist(id: String): ArtistInfo = cur.artist(id)
    override fun artistRecentAlbums(id: String): List<AlbumInfo> = cur.artistRecentAlbums(id)
    override fun artistAllTracks(id: String): List<Track> = cur.artistAllTracks(id)
    override fun album(id: String): AlbumInfo = cur.album(id)
    override fun likedAlbums(uid: String): List<AlbumInfo> = cur.likedAlbums(uid)
    override fun likeAlbum(uid: String, albumId: String) = cur.likeAlbum(uid, albumId)
    override fun unlikeAlbum(uid: String, albumId: String) = cur.unlikeAlbum(uid, albumId)

    override fun chart(): List<Track> = cur.chart()
    override fun trackLyrics(trackId: String, synced: Boolean): String? = forTrack(trackId).trackLyrics(trackId, synced)
    override fun musicHistory(limit: Int): List<Track> = cur.musicHistory(limit)
    override fun musicHistoryFull(limit: Int): RemoteHistory = cur.musicHistoryFull(limit)
    override fun trackInfo(t: Track): TrackInfo = forTrack(t.id).trackInfo(t)

    override fun mixSettings(): MixSettings? = cur.mixSettings()
    override fun saveMixOptions(options: String) = cur.saveMixOptions(options)
    override fun recommendedShelves(): List<PlaylistShelf> = cur.recommendedShelves()
    override fun nativeNewReleases(): NativeReleases? = cur.nativeNewReleases()
}
