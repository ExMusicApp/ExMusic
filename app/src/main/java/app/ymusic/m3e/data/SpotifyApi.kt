package app.ymusic.m3e.data

import java.io.IOException

/**
 * Spotify: пока только вход и профиль (ник и аватар). Всё остальное (библиотека, поиск, воспроизведение) пока не подключено:
 * списки пустые, а действия сообщают, что недоступны.
 */
class SpotifyApi(private val tokens: TokenStore) : MusicApi {
    override val caps = ApiCaps(
        dislikes = false, podcasts = false, chart = false, previewLimit = false, connect = false,
        mixSettings = false, recommendations = false, nativeNewReleases = false, waveByEntity = false,
        playlistVisibility = false,
    )
    override var highQuality: () -> Boolean = { false }
    override val plusConfirmedByStream: Boolean = false

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Spotify: это действие пока недоступно")

    override fun resetSession() {}

    /**
     * Профиль активного аккаунта Spotify. Сессия (куки) берётся из хранилища; профиль обновляется по ней.
     * Если сессия недействительна — ошибка «HTTP 401» (приложение выйдет из аккаунта), если сети нет или Spotify
     * ответил иначе — показывается сохранённый профиль.
     */
    override fun account(): Account {
        val stored = tokens.active?.takeIf { it.provider == Provider.SPOTIFY } ?: error("Аккаунт Spotify не выбран")
        val profile = try {
            SpotifyAuth.fetchProfile(stored.spCookies).also { tokens.updateSpotifyProfile(stored.token, it) }
        } catch (e: IOException) {
            if (e.message?.startsWith("HTTP 401") == true) throw e
            null
        }
        val cur = tokens.accounts.firstOrNull { it.token == stored.token } ?: stored
        return Account(
            uid = 0L,
            login = profile?.id ?: cur.login,
            name = profile?.name ?: cur.name.ifBlank { "Spotify" },
            hasPlus = false,
            avatarUrl = profile?.avatarUrl ?: cur.avatarUrl,
            provider = Provider.SPOTIFY,
        )
    }

    override fun likedTracks(uid: String, limit: Int): List<Track> = emptyList()
    override fun tracksByIds(keys: List<String>): List<Track> = emptyList()
    override fun likeTrack(uid: String, t: Track) { unsupported() }
    override fun unlikeTrack(uid: String, t: Track) { unsupported() }
    override fun dislikeTrack(uid: String, t: Track) { unsupported() }
    override fun undislikeTrack(uid: String, t: Track) { unsupported() }
    override fun dislikedTrackIds(uid: String): Set<String> = emptySet()

    override fun playlists(uid: String): List<PlaylistInfo> = emptyList()
    override fun playlistInfo(ownerUid: String, kind: Int): PlaylistInfo = unsupported()
    override fun playlistInfoByUuid(uuid: String): PlaylistInfo = unsupported()
    override fun setPlaylistVisibility(ownerUid: String, kind: Int, isPublic: Boolean) { unsupported() }
    override fun playlistTracks(ownerUid: String, kind: Int): List<Track> = emptyList()
    override fun createPlaylist(uid: String, title: String, isPublic: Boolean): PlaylistInfo = unsupported()
    override fun renamePlaylist(ownerUid: String, kind: Int, title: String) { unsupported() }
    override fun deletePlaylist(ownerUid: String, kind: Int) { unsupported() }
    override fun addTrackToPlaylist(ownerUid: String, kind: Int, track: Track) { unsupported() }
    override fun removeTrackFromPlaylist(ownerUid: String, kind: Int, track: Track) { unsupported() }

    override fun waveTracks(afterTrackId: String?, station: String): WaveBatch = WaveBatch(emptyList(), null)
    override fun waveSettings(): WaveSettings = WaveSettings()
    override fun saveWaveSettings(s: WaveSettings) {}
    override fun waveTrackStarted(trackId: String, batchId: String?, station: String) {}
    override fun waveStarted(station: String) {}

    override fun search(query: String): List<Track> = emptyList()
    override fun searchArtists(query: String): List<ArtistInfo> = emptyList()
    override fun searchAlbums(query: String): List<AlbumInfo> = emptyList()
    override fun searchPodcasts(query: String): List<AlbumInfo> = emptyList()
    override fun podcastsLanding(): List<AlbumInfo> = emptyList()

    override fun streamUrl(trackId: String): String = unsupported()
    override fun downloadUrl(trackId: String): String = unsupported()

    override fun artist(id: String): ArtistInfo = unsupported()
    override fun artistRecentAlbums(id: String): List<AlbumInfo> = emptyList()
    override fun artistAllTracks(id: String): List<Track> = emptyList()
    override fun album(id: String): AlbumInfo = unsupported()
    override fun likedAlbums(uid: String): List<AlbumInfo> = emptyList()
    override fun likeAlbum(uid: String, albumId: String) { unsupported() }
    override fun unlikeAlbum(uid: String, albumId: String) { unsupported() }

    override fun chart(): List<Track> = emptyList()
    override fun trackLyrics(trackId: String, synced: Boolean): String? = null
    override fun musicHistory(limit: Int): List<Track> = emptyList()
    override fun musicHistoryFull(limit: Int): RemoteHistory = RemoteHistory(emptyList(), emptyList())
    override fun trackInfo(t: Track): TrackInfo = unsupported()
}
