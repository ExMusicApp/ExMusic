package app.ymusic.m3e

import app.ymusic.m3e.ui.PlayerSettings
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import app.ymusic.m3e.data.Account
import app.ymusic.m3e.data.ConnectDevice
import app.ymusic.m3e.data.ConnectState
import app.ymusic.m3e.data.DownloadMode
import app.ymusic.m3e.data.DownloadNetwork
import app.ymusic.m3e.data.Net
import app.ymusic.m3e.data.TrackInfo
import app.ymusic.m3e.data.DownloadsState
import app.ymusic.m3e.data.AlbumInfo
import app.ymusic.m3e.data.ArtistInfo
import app.ymusic.m3e.data.AppLog
import app.ymusic.m3e.data.ArtistRef
import app.ymusic.m3e.data.LocalFilesSettings
import app.ymusic.m3e.data.LocalMusic
import app.ymusic.m3e.data.LocalLibrary
import app.ymusic.m3e.data.LocalModeSettings
import app.ymusic.m3e.data.LuaBridge
import app.ymusic.m3e.data.LuaPlugins
import app.ymusic.m3e.data.LOCAL_ARTIST_PREFIX
import app.ymusic.m3e.data.PlaybackStore
import app.ymusic.m3e.data.SessionKind
import app.ymusic.m3e.data.ShareLinks
import app.ymusic.m3e.data.SharedLink
import app.ymusic.m3e.data.SessionPosition
import app.ymusic.m3e.data.PlaylistInfo
import app.ymusic.m3e.data.StoredAccount
import app.ymusic.m3e.data.Track
import app.ymusic.m3e.data.WaveSettings
import app.ymusic.m3e.data.albumsFromDownloads
import app.ymusic.m3e.data.coverKey
import app.ymusic.m3e.data.YandexAuth
import app.ymusic.m3e.player.EqState
import app.ymusic.m3e.player.PlaybackService
import app.ymusic.m3e.player.TrackPreloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

const val DEFAULT_WAVE = "user:onyourwave"

fun playlistSourceKey(p: PlaylistInfo): String = "pl:${p.ownerUid}:${p.kind}"

data class UiState(
    val loggedIn: Boolean = false,
    val accounts: List<StoredAccount> = emptyList(),
    val activeToken: String? = null,
    val addingAccount: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val account: Account? = null,
    val liked: List<Track> = emptyList(),
    val playlists: List<PlaylistInfo> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val nowPlaying: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val openedPlaylist: PlaylistInfo? = null,
    val openedPlaylistTracks: List<Track> = emptyList(),
    val openedPlaylistLoading: Boolean = false,
    val searchArtists: List<ArtistInfo> = emptyList(),
    val openedArtist: ArtistInfo? = null,
    val openedArtistLoading: Boolean = false,
    val message: String? = null,
    val waveSettings: WaveSettings = WaveSettings(),
    val waveLoading: Boolean = false,
    val waveOn: Boolean = false,
    val searchAlbums: List<AlbumInfo> = emptyList(),
    val openedAlbum: AlbumInfo? = null,
    val openedAlbumLoading: Boolean = false,
    val albumFocusTrackId: String? = null,
    val dislikedIds: Set<String> = emptySet(),
    val localTracks: List<Track> = emptyList(),
    val localLib: LocalLibrary = LocalLibrary.EMPTY,
    val localLoading: Boolean = false,
    val searchLocal: List<Track> = emptyList(),
    val pickerResults: List<Track> = emptyList(),
    val pickerLoading: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val history: List<Track> = emptyList(),
    val waveHistory: List<Track> = emptyList(),
    val syncedHistory: List<Track> = emptyList(),
    val syncedHistoryLoading: Boolean = false,
    val syncedWave: List<Track> = emptyList(),
    val searchPodcasts: List<AlbumInfo> = emptyList(),
    val searchedQuery: String = "",
    val searchLoading: Boolean = false,
    val chart: List<Track> = emptyList(),
    val chartLoading: Boolean = false,
    val chartFailed: Boolean = false,
    val podcasts: List<AlbumInfo> = emptyList(),
    val podcastsLoading: Boolean = false,
    val podcastsQuery: String = "",
    val offline: Boolean = false,
    val likedAlbums: List<AlbumInfo> = emptyList(),
    val netEpoch: Int = 0,
    val waveStation: String = DEFAULT_WAVE,
    val sessionHistory: List<Track> = emptyList(),
    val sessionLabel: String = "",
    val playingSource: String? = null,
    val newReleases: List<AlbumInfo> = emptyList(),
    val newReleaseTracks: List<Track> = emptyList(),
    val newReleasesLoading: Boolean = false,
    val newReleasesFailed: Boolean = false,
)

private class Library(
    val acc: Account,
    val liked: List<Track>,
    val lists: List<PlaylistInfo>,
    val albums: List<AlbumInfo>?,
)

private class SearchBundle(
    val tracks: List<Track>,
    val artists: List<ArtistInfo>,
    val albums: List<AlbumInfo>,
    val podcasts: List<AlbumInfo>,
)

data class LyricsState(
    val trackId: String? = null,
    val loading: Boolean = false,
    val done: Boolean = false,
    val sources: List<app.ymusic.m3e.data.LyricsSource> = emptyList(),
)

data class ArtistTracksState(
    val artistId: String? = null,
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
)

private const val LOCAL_OWNER = "local"

private const val EXTRA_ALBUM_ID = "albumId"
private const val EXTRA_ALBUM_TITLE = "albumTitle"
private const val EXTRA_EXPLICIT = "explicit"
private const val EXTRA_DURATION = "durationMs"

private const val WAVE_WAIT_MS = 20_000L

class AppViewModel : ViewModel() {
    private val app = App.instance
    private val api = app.api
    private val player = app.player
    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                java.io.File(app.filesDir, "playlist_covers").deleteRecursively()
                app.getSharedPreferences("playlist_covers", android.content.Context.MODE_PRIVATE).edit().clear().apply()
            }
        }
    }
    private val store: PlaybackStore = app.playbackStore
    private val queue = mutableListOf<Track>()
    private var warnedPreviewFor: String? = null
    private var warnedNoPlus = false
    @Volatile private var lastNoPlusCutAt = 0L
    private val NO_PLUS_LIMIT_MS = 30_000L
    private var waveActive = false
        set(value) {
            field = value
            _state.update { if (it.waveOn == value) it else it.copy(waveOn = value) }
        }
    private var waveBatchId: String? = null
    private var waveLoadingMore = false
    private var advanceAfterLoad = false
    private var waveStationId: String = DEFAULT_WAVE
        set(value) {
            field = value
            _state.update { if (it.waveStation == value) it else it.copy(waveStation = value) }
        }

    private val _state = MutableStateFlow(
        UiState(
            loggedIn = app.tokenStore.token != null,
            accounts = app.tokenStore.accounts,
            activeToken = app.tokenStore.token,
        ),
    )
    val state: StateFlow<UiState> = _state

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _artistAll = MutableStateFlow(ArtistTracksState())
    val artistAllTracks: StateFlow<ArtistTracksState> = _artistAll

    private val _openPlayer = MutableStateFlow(false)
    val openPlayerRequest: StateFlow<Boolean> = _openPlayer
    fun requestOpenPlayer() { _openPlayer.value = true }

    private val _searchRequest = MutableStateFlow<String?>(null)
    val searchRequest: StateFlow<String?> = _searchRequest
    fun requestSearch(query: String) { _searchRequest.value = query }
    fun consumeSearchRequest() { _searchRequest.value = null }

    private val _linkRequest = MutableStateFlow<SharedLink?>(null)
    val linkRequest: StateFlow<SharedLink?> = _linkRequest
    fun requestLink(link: SharedLink) { _linkRequest.value = link }
    fun consumeLinkRequest() { _linkRequest.value = null }

    private val _clipLink = MutableStateFlow<SharedLink?>(null)
    val clipLink: StateFlow<SharedLink?> = _clipLink
    private var lastClipText: String? = null
    fun offerClipboard(text: String?) {
        if (text.isNullOrBlank() || text == lastClipText) return
        lastClipText = text
        val link = ShareLinks.parseFromText(text) ?: return
        AppLog.i("Link", "в буфере обмена найдена ссылка: $link")
        _clipLink.value = link
    }
    fun dismissClipLink() { _clipLink.value = null }

    data class LinkPreview(
        val link: SharedLink,
        val title: String,
        val subtitle: String,
        val coverUri: String?,
        val tracks: List<Track>,
        val kind: String,
        val focusTrackId: String? = null,
        val playlists: List<PlaylistInfo> = emptyList(),
    )

    suspend fun loadLinkPreview(link: SharedLink): LinkPreview = withContext(Dispatchers.IO) {
        if (!_state.value.loggedIn) error("Войдите в Яндекс Музыку, чтобы открыть ссылку")
        when (link) {
            is SharedLink.TrackLink -> {
                val key = if (link.albumId != null) "${link.trackId}:${link.albumId}" else link.trackId
                val t = api.tracksByIds(listOf(key)).firstOrNull() ?: error("Трек не найден")
                LinkPreview(link, t.title, t.artists, t.coverUri, listOf(t), "Трек", focusTrackId = t.id)
            }
            is SharedLink.AlbumLink -> {
                val a = api.album(link.albumId)
                val sub = listOf(a.artists, a.year.takeIf { it > 0 }?.toString()).filterNot { it.isNullOrBlank() }.joinToString(" · ")
                LinkPreview(link, a.title, sub, a.coverUri, a.tracks, "Альбом")
            }
            is SharedLink.ArtistLink -> {
                val a = api.artist(link.artistId)
                LinkPreview(link, a.name, "Популярные треки", a.coverUri, a.tracks, "Исполнитель")
            }
            is SharedLink.PlaylistLink -> {
                val info = api.playlistInfo(link.owner, link.kind)
                val tracks = api.playlistTracks(info.ownerUid, info.kind)
                LinkPreview(link, info.title, "${info.ownerLogin ?: ""}${if (info.ownerLogin != null) " · " else ""}${tracks.size} треков", info.coverUri, tracks, "Плейлист")
            }
            is SharedLink.UserLink -> {
                val pls = api.playlists(link.login).filter { it.isPublic && it.kind != 3 }
                LinkPreview(link, link.login, "Публичных плейлистов: ${pls.size}", null, emptyList(), "Профиль", playlists = pls)
            }
            is SharedLink.PlaylistUuidLink -> {
                val info = api.playlistInfoByUuid(link.uuid)
                val tracks = api.playlistTracks(info.ownerUid, info.kind)
                LinkPreview(link, info.title, "${info.ownerLogin ?: ""}${if (info.ownerLogin != null) " · " else ""}${tracks.size} треков", info.coverUri, tracks, "Плейлист")
            }
        }
    }

    private val _searchFocus = MutableStateFlow(false)
    val searchFocusRequest: StateFlow<Boolean> = _searchFocus
    fun requestSearchFocus() { _searchFocus.value = true }
    fun consumeSearchFocus() { _searchFocus.value = false }
    fun consumeOpenPlayer() { _openPlayer.value = false }

    private val _localNav = MutableStateFlow<String?>(null)
    val localNavRequest: StateFlow<String?> = _localNav
    fun requestLocalNav(page: String) { _localNav.value = page }
    fun consumeLocalNav() { _localNav.value = null }

    fun loadAllArtistTracks(id: String) {
        val cur = _artistAll.value
        if (cur.artistId == id && (cur.loading || cur.tracks.isNotEmpty())) return
        _artistAll.value = ArtistTracksState(artistId = id, loading = true)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.artistAllTracks(id) } }
                .onSuccess { r -> _artistAll.update { if (it.artistId == id) it.copy(tracks = r, loading = false) else it } }
                .onFailure { _artistAll.update { if (it.artistId == id) it.copy(loading = false) else it } }
        }
    }

    private val _outputId = MutableStateFlow<Int?>(null)
    val outputDeviceId: StateFlow<Int?> = _outputId

    fun setAudioOutput(d: AudioDeviceInfo?) {
        player.setPreferredAudioDevice(d)
        _outputId.value = d?.id
    }

    val connect = app.connect
    val connectState: StateFlow<ConnectState> = connect.state

    @Volatile private var connectBusy = false
    @Volatile private var lastRemoteSkipAt = 0L

    fun openConnect() = connect.ensureConnected()

    fun connectDialogClosed() {
        if (!connect.isRemoteActive()) connect.disconnect()
    }

    fun remoteActive(): Boolean = connect.isRemoteActive()

    init {
        connect.onRemoteLost = { msg ->
            viewModelScope.launch {
                _state.update { it.copy(message = msg) }
                connect.clearError()
                releaseConnect()
            }
        }
    }

    private fun repeatName(): String = when (player.repeatMode) {
        Player.REPEAT_MODE_ALL -> "ALL"
        Player.REPEAT_MODE_ONE -> "ONE"
        else -> "NONE"
    }

    fun playOnConnectDevice(d: ConnectDevice) {
        if (d.offline) {
            _state.update { it.copy(message = "«${d.title}» сейчас не в сети") }
            return
        }
        val idx = player.currentMediaItemIndex
        if (queue.isEmpty() || idx !in queue.indices) {
            _state.update { it.copy(message = "Сначала включите трек, потом выберите колонку") }
            return
        }
        val pos = if (connect.isRemoteActive()) connect.estimatedProgress() else player.currentPosition.coerceAtLeast(0)
        connectBusy = true
        try {
            if (!connect.playOn(d, queue.toList(), idx, pos, repeatName())) {
                connect.state.value.error?.let { e -> _state.update { it.copy(message = e) } }
                connect.clearError()
                return
            }
            player.pause()
        } finally {
            connectBusy = false
        }
        _state.update { it.copy(message = "Играет на «${d.title}»", isPlaying = true, isBuffering = false) }
    }

    private fun pushQueueToRemote() {
        val id = connect.state.value.remoteId ?: return
        val d = connect.state.value.devices.firstOrNull { it.id == id } ?: return
        val idx = player.currentMediaItemIndex
        if (idx !in queue.indices) return
        if (!connect.playOn(d, queue.toList(), idx, 0, repeatName())) {
            connect.state.value.error?.let { e -> _state.update { it.copy(message = e) } }
            connect.clearError()
        }
    }

    fun releaseConnect() {
        if (connect.state.value.remoteId == null) return
        val (id, pos, wasPlaying) = connect.release()
        connectBusy = true
        try {
            var idx = player.currentMediaItemIndex.coerceAtLeast(0)
            if (id != null && player.currentMediaItem?.mediaId != id) {
                var best = -1
                for (i in queue.indices) {
                    if (queue[i].id == id && (best < 0 || kotlin.math.abs(i - idx) < kotlin.math.abs(best - idx))) best = i
                }
                if (best >= 0) idx = best
            }
            if (player.mediaItemCount > 0) {
                player.seekTo(idx, pos)
                if (wasPlaying) {
                    player.prepare()
                    player.play()
                }
            }
        } finally {
            connectBusy = false
        }
        syncPlaying()
    }

    fun setConnectVolume(d: ConnectDevice, v: Float) = connect.setVolume(d, v.toDouble())

    private fun syncRemote() {
        val s = connect.state.value
        val playing = !s.paused
        _state.update { if (it.isPlaying == playing && !it.isBuffering) it else it.copy(isPlaying = playing, isBuffering = false) }
        val id = s.currentTrackId ?: return
        if (player.currentMediaItem?.mediaId == id) return
        val cur = player.currentMediaItemIndex.coerceAtLeast(0)
        var best = -1
        for (i in queue.indices) {
            if (queue[i].id == id && (best < 0 || kotlin.math.abs(i - cur) < kotlin.math.abs(best - cur))) best = i
        }
        if (best < 0) return
        connectBusy = true
        try {
            player.seekTo(best, 0)
            player.pause()
        } finally {
            connectBusy = false
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (connectBusy || !playWhenReady || !connect.isRemoteActive()) return
                player.pause()
                if (System.currentTimeMillis() - lastRemoteSkipAt > 600) connect.togglePause()
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                if (connectBusy || !connect.isRemoteActive()) return
                val id = item?.mediaId ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    if (connect.skipToTrack(id)) lastRemoteSkipAt = System.currentTimeMillis()
                }
            }
        })
    }

    private fun ownerKey(): String? =
        app.tokenStore.token?.hashCode()?.toString() ?: if (LocalModeSettings.enabled) LOCAL_OWNER else null

    private val historyLimit = 30

    private fun recordHistory(trackId: String?) {
        val t = queue.firstOrNull { it.id == trackId } ?: return
        if (_state.value.history.firstOrNull()?.id == t.id) return
        val updated = (listOf(t) + _state.value.history.filterNot { it.id == t.id }).take(historyLimit)
        _state.update { it.copy(history = updated, syncedHistory = if (it.syncedHistory.isEmpty()) it.syncedHistory else mergeHistory(updated.take(1), it.syncedHistory)) }
        store.saveHistory(ownerKey(), updated)
    }

    private val sessionLimit = 300

    private fun recordSession(trackId: String?) {
        val t = queue.firstOrNull { it.id == trackId } ?: return
        if (_state.value.sessionHistory.firstOrNull()?.id == t.id) return
        val updated = (listOf(t) + _state.value.sessionHistory.filterNot { it.id == t.id }).take(sessionLimit)
        _state.update { it.copy(sessionHistory = updated) }
        store.saveSessionLog(ownerKey(), _state.value.sessionLabel, updated)
    }

    private fun setSessionLabel(label: String) {
        _state.update { it.copy(sessionLabel = label) }
        store.saveSessionLog(ownerKey(), label, _state.value.sessionHistory)
    }

    private fun resetSession(label: String) {
        _state.update { it.copy(sessionHistory = emptyList(), sessionLabel = label) }
        store.saveSessionLog(ownerKey(), label, emptyList())
    }

    private val waveHistoryLimit = 300

    private fun recordWave(trackId: String?) {
        if (!waveActive) return
        val t = queue.firstOrNull { it.id == trackId } ?: return
        if (_state.value.waveHistory.firstOrNull()?.id == t.id) return
        val updated = (listOf(t) + _state.value.waveHistory.filterNot { it.id == t.id }).take(waveHistoryLimit)
        _state.update { it.copy(waveHistory = updated) }
        store.saveWave(ownerKey(), updated)
    }

    private val _lyrics = MutableStateFlow(LyricsState())
    val lyrics: StateFlow<LyricsState> = _lyrics
    private var lyricsJob: Job? = null
    private var lyricsFilter = ""

    fun loadLyrics(t: Track) {
        val cur = _lyrics.value
        val off = PlayerSettings.lyricsOff
        val filter = off.sorted().joinToString(",")
        if (cur.trackId == t.id && lyricsFilter == filter && (cur.loading || cur.done)) return
        lyricsFilter = filter
        lyricsJob?.cancel()
        _lyrics.value = LyricsState(trackId = t.id, loading = true)
        lyricsJob = viewModelScope.launch {
            val repo = app.lyrics
            val order = repo.providers.map { it.id }
            coroutineScope {
                repo.providers.filter { it.id !in off }.forEach { p ->
                    launch {
                        val r = runCatching { p.fetch(t) }.getOrNull()?.takeIf { it.hasText }
                        if (r != null) {
                            _lyrics.update {
                                if (it.trackId != t.id) it
                                else it.copy(sources = (it.sources + r).sortedBy { s -> order.indexOf(s.id) })
                            }
                        }
                    }
                }
            }
            _lyrics.update { if (it.trackId == t.id) it.copy(loading = false, done = true) else it }
        }
    }

    fun reloadLyrics(t: Track) {
        lyricsJob?.cancel()
        app.lyrics.forgetMisses(t)
        _lyrics.value = LyricsState()
        loadLyrics(t)
    }

    fun neighborTrack(next: Boolean): Track? {
        val idx = if (next) player.nextMediaItemIndex else player.previousMediaItemIndex
        if (idx == C.INDEX_UNSET || idx < 0 || idx >= player.mediaItemCount) return null
        val id = player.getMediaItemAt(idx).mediaId
        return queue.firstOrNull { it.id == id }
    }

    fun upcomingTracks(count: Int): List<Track> {
        val tl = player.currentTimeline
        if (tl.isEmpty) return emptyList()
        val rm = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else player.repeatMode
        val out = ArrayList<Track>()
        var idx = player.currentMediaItemIndex
        for (n in 0 until count) {
            idx = tl.getNextWindowIndex(idx, rm, player.shuffleModeEnabled)
            if (idx == C.INDEX_UNSET || idx < 0 || idx >= player.mediaItemCount) break
            val id = player.getMediaItemAt(idx).mediaId
            queue.firstOrNull { it.id == id }?.let { out += it }
        }
        return out
    }

    private fun persistPosition() {
        if (player.mediaItemCount == 0 || queue.isEmpty()) return
        store.savePosition(
            SessionPosition(
                index = (player.currentMediaItemIndex - queueOffset).coerceAtLeast(0),
                positionMs = player.currentPosition.coerceAtLeast(0),
                kind = if (waveActive) SessionKind.Wave else SessionKind.Queue,
                waveBatchId = waveBatchId,
                shuffle = player.shuffleModeEnabled,
                waveStation = waveStationId.takeIf { waveActive && it != DEFAULT_WAVE },
            ),
        )
    }

    private var queueOffset = 0

    private fun persistQueue() {
        val cur = player.currentMediaItemIndex.coerceAtLeast(0)
        val from = (cur - 100).coerceAtLeast(0).coerceAtMost((queue.size - 1).coerceAtLeast(0))
        val to = minOf(queue.size, from + 600)
        queueOffset = from
        store.saveQueue(ownerKey(), queue.subList(from, to).toList())
        persistPosition()
    }

    private fun restoreSession() {
        val owner = ownerKey() ?: return
        val tracks = store.loadQueue(owner)
        if (tracks.isEmpty()) return
        val pos = store.loadPosition()
        val idx = pos.index.coerceIn(0, tracks.lastIndex)
        queueOffset = 0
        queue.clear(); queue += tracks
        player.shuffleModeEnabled = pos.shuffle
        player.setMediaItems(tracks.map { toMediaItem(it) }, idx, pos.positionMs.coerceAtLeast(0))
        if (pos.kind == SessionKind.Wave) {
            waveActive = true
            waveBatchId = pos.waveBatchId
            waveStationId = pos.waveStation ?: DEFAULT_WAVE
        }
        _state.update { it.copy(nowPlaying = tracks[idx]) }
    }

    fun saveSession() = persistPosition()

    private val likeBridge = object : LikeBridge {
        override fun toggleLike(t: Track) = this@AppViewModel.toggleLike(t)
        override fun toggleDislike(t: Track) = this@AppViewModel.toggleDislike(t)
    }

    override fun onCleared() {
        runCatching {
            (app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(netCallback)
        }
        persistPosition()
        connect.onRemoteLost = null
        if (app.likeBridge === likeBridge) app.likeBridge = null
        super.onCleared()
    }

    fun toggleLike(t: Track) {
        val acc = _state.value.account ?: return
        val prevLiked = _state.value.liked
        val prevDis = _state.value.dislikedIds
        val wasLiked = prevLiked.any { it.id == t.id }
        val wasDisliked = t.id in prevDis
        _state.update { s ->
            s.copy(
                liked = if (wasLiked) s.liked.filterNot { it.id == t.id } else listOf(t) + s.liked,
                dislikedIds = s.dislikedIds - t.id,
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (wasLiked) api.unlikeTrack(acc.apiId, t)
                    else {
                        api.likeTrack(acc.apiId, t)
                        if (wasDisliked) api.undislikeTrack(acc.apiId, t)
                    }
                }
            }.onSuccess {
                ownerKey()?.let { k -> app.offlineLibrary.saveLiked(k, _state.value.liked) }
            }.onFailure { e ->
                _state.update { it.copy(liked = prevLiked, dislikedIds = prevDis, error = e.message ?: "Не удалось изменить лайк") }
            }
        }
    }

    fun toggleDislike(t: Track) {
        val acc = _state.value.account ?: return
        val prevLiked = _state.value.liked
        val prevDis = _state.value.dislikedIds
        val wasLiked = prevLiked.any { it.id == t.id }
        val wasDisliked = t.id in prevDis
        _state.update { s ->
            s.copy(
                liked = s.liked.filterNot { it.id == t.id },
                dislikedIds = if (wasDisliked) s.dislikedIds - t.id else s.dislikedIds + t.id,
            )
        }
        if (!wasDisliked && _state.value.nowPlaying?.id == t.id && player.hasNextMediaItem()) player.seekToNextMediaItem()
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (wasDisliked) api.undislikeTrack(acc.apiId, t)
                    else {
                        api.dislikeTrack(acc.apiId, t)
                        if (wasLiked) api.unlikeTrack(acc.apiId, t)
                    }
                }
            }.onFailure { e ->
                _state.update { it.copy(liked = prevLiked, dislikedIds = prevDis, error = e.message ?: "Не удалось изменить дизлайк") }
            }
        }
    }

    private fun syncPlaying() {
        if (connect.isRemoteActive()) return
        val st = player.playbackState
        val on = player.playWhenReady && (st == Player.STATE_READY || st == Player.STATE_BUFFERING)
        val buf = player.playWhenReady && st == Player.STATE_BUFFERING
        _state.update { if (it.isPlaying == on && it.isBuffering == buf) it else it.copy(isPlaying = on, isBuffering = buf) }
    }

    @Volatile private var hadInternet = Net.isOnline(app)
    @Volatile private var reconnectJob: Job? = null
    private var prefetchJob: Job? = null

    private val netCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!ok) return
            val gained = !hadInternet || _state.value.offline
            hadInternet = true
            if (gained) onBackOnline()
        }

        override fun onLost(network: Network) {
            if (!Net.isOnline(app)) {
                hadInternet = false
                _state.update { it.copy(offline = true) }
            }
        }
    }

    private fun onBackOnline() {
        if (!_state.value.loggedIn) return
        if (reconnectJob?.isActive == true) return
        _state.update { it.copy(offline = false, netEpoch = it.netEpoch + 1) }
        startReconnect()
    }

    fun retryIfOffline() {
        if (_state.value.loggedIn && _state.value.offline && Net.isOnline(app)) onBackOnline()
    }

    private fun startReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch { reloadAfterReconnect() }
    }

    private suspend fun reloadAfterReconnect() {
        var wait = 1500L
        for (attempt in 0 until 6) {
            if (attempt > 0) {
                delay(wait)
                wait = (wait * 2).coerceAtMost(15_000L)
            }
            val tok = app.tokenStore.token ?: return
            val r = runCatching { fetchLibrary() }
            val lib = r.getOrNull()
            if (lib != null) {
                applyLibrary(tok, lib)
                _state.update { it.copy(netEpoch = it.netEpoch + 1) }
                reloadOpened()
                return
            }
            val e = r.exceptionOrNull()
            if (e is CancellationException) throw e
            if (e?.message?.contains("HTTP 401") == true) {
                if (tok == app.tokenStore.token && logout()) {
                    _state.update { it.copy(message = "Сессия аккаунта истекла — выполнен вход в другой аккаунт") }
                }
                return
            }
        }
        _state.update { it.copy(offline = true) }
    }

    private suspend fun reloadOpened() {
        val s0 = _state.value
        coroutineScope {
            s0.openedPlaylist?.let { p ->
                launch {
                    runCatching { withContext(Dispatchers.IO) { api.playlistTracks(p.ownerUid, p.kind) } }.onSuccess { r ->
                        ownerKey()?.let { k -> app.offlineLibrary.savePlaylist(k, p, r) }
                        _state.update { s ->
                            if (s.openedPlaylist?.kind == p.kind && s.openedPlaylist?.ownerUid == p.ownerUid) {
                                s.copy(openedPlaylistTracks = r, openedPlaylistLoading = false)
                            } else s
                        }
                    }
                }
            }
            s0.openedAlbum?.let { a ->
                launch {
                    runCatching { withContext(Dispatchers.IO) { api.album(a.id) } }.onSuccess { full ->
                        val merged = full.copy(title = full.title.ifBlank { a.title }, coverUri = full.coverUri ?: a.coverUri)
                        ownerKey()?.let { k -> app.offlineLibrary.saveAlbum(k, merged) }
                        _state.update { s ->
                            if (s.openedAlbum?.id == a.id) s.copy(openedAlbum = merged, openedAlbumLoading = false) else s
                        }
                    }
                }
            }
            s0.openedArtist?.let { a ->
                launch {
                    runCatching { withContext(Dispatchers.IO) { api.artist(a.id) } }.onSuccess { full ->
                        _state.update { s ->
                            if (s.openedArtist?.id != a.id) s
                            else s.copy(
                                openedArtist = full.copy(name = full.name.ifBlank { a.name }, coverUri = full.coverUri ?: a.coverUri),
                                openedArtistLoading = false,
                            )
                        }
                    }
                }
            }
            if (s0.chartFailed) launch { loadChart(force = true) }
        }
    }

    val downloads: StateFlow<DownloadsState> = app.downloads.state

    fun downloadTrack(t: Track, mode: DownloadMode? = null) {
        if (t.isLocal) return
        app.downloads.download(t, mode) { r ->
            _state.update {
                if (r.isSuccess) it.copy(message = "Скачано: ${t.title}")
                else it.copy(error = "Не удалось скачать: ${r.exceptionOrNull()?.message ?: "ошибка"}")
            }
        }
    }

    fun toggleDownload(t: Track) {
        if (app.downloads.state.value.items.any { it.id == t.id }) removeDownload(t) else downloadTrack(t)
    }

    fun removeDownload(t: Track) {
        app.downloads.remove(t.id)
        _state.update { it.copy(message = "Удалено из скачанных") }
    }

    fun downloadAll(name: String, tracks: List<Track>, mode: DownloadMode? = null) {
        app.downloads.downloadAll(name, tracks, mode) { ok, failed, reason ->
            _state.update {
                when {
                    reason != null -> it.copy(error = "Скачивание остановлено: $reason" + if (ok > 0) " (скачано: $ok)" else "")
                    failed > 0 -> it.copy(message = "Скачано: $ok, не удалось: $failed")
                    ok == 0 -> it.copy(message = "«$name»: всё уже сохранено")
                    else -> it.copy(message = "«$name»: скачано $ok")
                }
            }
        }
    }

    fun setAutoDownload(key: String, on: Boolean, name: String, tracks: List<Track>) {
        app.downloads.setAuto(key, on)
        _state.update { it.copy(message = if (on) "Автоскачивание «$name» включено" else "Автоскачивание «$name» выключено") }
        if (on) autoSync(key, name, tracks)
    }

    private fun autoSync(key: String, name: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        app.downloads.autoSync(key, name, tracks) { ok, _, _ ->
            if (ok > 0) _state.update { it.copy(message = "Автоскачивание «$name»: скачано $ok") }
        }
    }

    private fun startAutoDownloadWatcher() {
        viewModelScope.launch {
            _state.map { Triple(it.liked, it.openedPlaylist, it.openedPlaylistTracks) }
                .distinctUntilChanged()
                .collect { (liked, pl, plTracks) ->
                    val auto = app.downloads.state.value.auto
                    if (auto.isEmpty()) return@collect
                    if (DownloadsState.AUTO_LIKED in auto) autoSync(DownloadsState.AUTO_LIKED, "Любимое", liked)
                    if (pl != null) playlistSourceKey(pl).let { k -> if (k in auto) autoSync(k, pl.title, plTracks) }
                }
        }
    }

    fun cancelBulkDownload() {
        app.downloads.cancelBulk()
        _state.update { it.copy(message = "Скачивание отменено") }
    }
    fun setDownloadNetwork(m: DownloadNetwork) = app.downloads.setNetwork(m)
    fun setDownloadMode(m: DownloadMode) = app.downloads.setMode(m)
    fun setAskDownloadMode(ask: Boolean) = app.downloads.setAskMode(ask)
    fun setDownloadModeAndStopAsking(m: DownloadMode) = app.downloads.setModeAndStopAsking(m)

    suspend fun fetchTrackInfo(t: Track): TrackInfo? =
        withContext(Dispatchers.IO) { runCatching { api.trackInfo(t) }.getOrNull() }

    fun deleteDownload(id: String) = app.downloads.remove(id)
    fun deleteAllDownloads() { app.downloads.removeAll(); _state.update { it.copy(message = "Все скачанные песни удалены") } }
    fun setDownloadDir(uri: Uri) = app.downloads.setTree(uri)
    fun resetDownloadDir() = app.downloads.resetTree()

    val eqState: StateFlow<EqState> = app.equalizer.state
    fun setEqEnabled(on: Boolean) = app.equalizer.setEnabled(on)
    fun setEqPreset(id: String) = app.equalizer.setPreset(id)
    fun setEqBand(index: Int, db: Float) = app.equalizer.setBand(index, db)

    init {
        startAutoDownloadWatcher()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaying()
                if (isPlaying) {
                    recordHistory(player.currentMediaItem?.mediaId)
                    recordWave(player.currentMediaItem?.mediaId)
                    recordSession(player.currentMediaItem?.mediaId)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncPlaying()
                if (!playWhenReady) persistPosition()
            }

            override fun onRepeatModeChanged(repeatMode: Int) =
                _state.update { it.copy(repeatMode = repeatMode) }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                schedulePreload()
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                schedulePreload()
                val id = item?.mediaId
                val found = queue.firstOrNull { it.id == id }
                if (found != null) _state.update { s -> if (s.nowPlaying?.id == found.id) s else s.copy(nowPlaying = found) }
                if (player.playWhenReady) { recordHistory(id); recordWave(id); recordSession(id) }
                persistPosition()
                if (waveActive && id != null) {
                    val st = waveStationId
                    val batch = waveBatchId
                    viewModelScope.launch(Dispatchers.IO) { runCatching { api.waveTrackStarted(id, batch, st) } }
                    if (player.mediaItemCount - player.currentMediaItemIndex <= 3) loadMoreWave()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlaying()
                if (playbackState == Player.STATE_ENDED && waveActive) loadMoreWave(advance = true)
                if (playbackState != Player.STATE_READY) return
                val t = queue.firstOrNull { it.id == player.currentMediaItem?.mediaId } ?: return
                val dur = player.duration
                _state.update { s ->
                    val acc = s.account
                    val fixed = if (acc != null && !acc.hasPlus && api.plusConfirmedByStream) acc.copy(hasPlus = true) else acc
                    s.copy(account = fixed)
                }
                if (!t.isLocal && dur > 0 && t.durationMs > 0 && dur < t.durationMs * 0.7 && warnedPreviewFor != t.id) {
                    warnedPreviewFor = t.id
                    val acc = _state.value.account
                    val noPlus = acc != null && !acc.hasPlus
                    if (noPlus) {
                        if (!warnedNoPlus) {
                            warnedNoPlus = true
                            _state.update { it.copy(error = "Без Яндекс Плюса треки и подкасты играют только по 30 секунд. С подпиской они будут звучать целиком.") }
                        }
                    } else {
                        _state.update {
                            it.copy(error = "Сервер отдаёт только фрагмент трека (${dur / 1000} с из ${t.durationMs / 1000} с). Аккаунт не даёт полный доступ к этому треку.")
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                player.currentMediaItem?.mediaId?.let { TrackPreloader.forget(it) }
                _state.update { it.copy(error = "Не удалось воспроизвести: ${error.errorCodeName}") }
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else if (waveActive) {
                    loadMoreWave(advance = true)
                }
                syncPlaying()
            }
        })
        LuaPlugins.attach(app, player, object : LuaBridge {
            override fun toast(text: String) { _state.update { it.copy(message = text) } }
            override fun currentTrack(): Track? = _state.value.nowPlaying
            override fun search(query: String) { requestSearch(query) }
            override fun startWave(station: String, label: String) { this@AppViewModel.startWave(listOf(station), label) }
            override fun viewModel(): Any = this@AppViewModel
        })
        viewModelScope.launch {
            var tick = 0
            while (true) {
                val remote = connect.isRemoteActive()
                _positionMs.value = if (remote) connect.estimatedProgress() else player.currentPosition.coerceAtLeast(0)
                if (remote) syncRemote()
                if (noPlusLimited(_state.value.nowPlaying)) {
                    if (!remote) {
                        if (player.isPlaying && player.currentPosition >= NO_PLUS_LIMIT_MS) cutNoPlusTrack()
                    } else if (!connect.state.value.paused && connect.estimatedProgress() >= NO_PLUS_LIMIT_MS &&
                        System.currentTimeMillis() - lastNoPlusCutAt > 3000
                    ) {
                        lastNoPlusCutAt = System.currentTimeMillis()
                        cutNoPlusRemote()
                    }
                }
                _state.update {
                    val d = player.duration.coerceAtLeast(0)
                    val full = if (d > 0) d else (it.nowPlaying?.durationMs ?: 0L)
                    val dur = if (noPlusLimited(it.nowPlaying) && full > 0) minOf(full, NO_PLUS_LIMIT_MS) else full
                    if (it.durationMs == dur) it else it.copy(durationMs = dur)
                }
                if (++tick % 10 == 0 && player.isPlaying) persistPosition()
                delay(500)
            }
        }
        viewModelScope.launch {
            _state.map { it.liked }.distinctUntilChanged().collect { l -> app.likedIds.value = l.mapTo(HashSet()) { it.id } }
        }
        viewModelScope.launch {
            _state.map { it.dislikedIds }.distinctUntilChanged().collect { app.dislikedIds.value = it }
        }
        app.likeBridge = likeBridge
        _state.update { it.copy(repeatMode = player.repeatMode, offline = !Net.isOnline(app)) }
        runCatching {
            (app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .registerDefaultNetworkCallback(netCallback)
        }
        if (player.mediaItemCount > 0) {
            for (i in 0 until player.mediaItemCount) {
                val mi = player.getMediaItemAt(i)
                val uri = mi.localConfiguration?.uri
                val ex = mi.mediaMetadata.extras
                queue += Track(
                    id = mi.mediaId,
                    albumId = ex?.getString(EXTRA_ALBUM_ID),
                    title = mi.mediaMetadata.title?.toString().orEmpty(),
                    artists = mi.mediaMetadata.artist?.toString().orEmpty(),
                    coverUri = mi.mediaMetadata.artworkUri?.toString(),
                    durationMs = ex?.getLong(EXTRA_DURATION) ?: 0L,
                    albumTitle = ex?.getString(EXTRA_ALBUM_TITLE),
                    explicit = ex?.getBoolean(EXTRA_EXPLICIT) ?: false,
                    localUri = uri?.takeIf { it.scheme != "ymusic" }?.toString(),
                )
            }
            val cur = queue.firstOrNull { it.id == player.currentMediaItem?.mediaId }
            _state.update { it.copy(nowPlaying = cur) }
            syncPlaying()
        } else if (_state.value.loggedIn) {
            restoreSession()
        }
        if (_state.value.loggedIn) {
            val log = store.loadSessionLog(ownerKey())
            _state.update {
                it.copy(
                    history = store.loadHistory(ownerKey()), waveHistory = store.loadWave(ownerKey()),
                    sessionHistory = log.tracks, sessionLabel = log.label,
                )
            }
            refresh()
        } else if (LocalModeSettings.enabled) {
            loadLocalHistory()
        }
    }

    fun loadLocalHistory() {
        if (_state.value.loggedIn) return
        _state.update { it.copy(history = store.loadHistory(LOCAL_OWNER)) }
    }

    private fun freshState(loggedIn: Boolean) = UiState(
        loggedIn = loggedIn,
        accounts = app.tokenStore.accounts,
        activeToken = app.tokenStore.token,
    )

    private fun resetSession() {
        runCatching {
            if (connect.state.value.remoteId != null) connect.release()
            connect.disconnect()
        }
        player.stop()
        player.clearMediaItems()
        queue.clear()
        waveActive = false
        cancelPendingWave()
        api.resetSession()
        store.clear()
        app.likedIds.value = emptySet()
        app.dislikedIds.value = emptySet()
        _artistAll.value = ArtistTracksState()
    }

    fun onTokenReceived(token: String) {
        if (token.isBlank()) return
        val prev = app.tokenStore.token
        app.tokenStore.token = token
        if (prev != null && prev != token) resetSession()
        _state.value = freshState(loggedIn = true)
        refresh()
    }

    fun onAuthRedirect(data: Uri?) {
        val st = _state.value
        if (st.loggedIn && !st.addingAccount) return
        val token = YandexAuth.parseToken(data?.toString()) ?: return
        onTokenReceived(token)
    }

    fun startAddAccount() = _state.update { it.copy(addingAccount = true, error = null) }

    fun cancelAddAccount() = _state.update { it.copy(addingAccount = false, error = null) }

    fun switchAccount(token: String) {
        if (token == app.tokenStore.token) return
        if (app.tokenStore.accounts.none { it.token == token }) return
        resetSession()
        app.tokenStore.setActive(token)
        _state.value = freshState(loggedIn = true)
        refresh()
    }

    fun logoutAccount(token: String): Boolean {
        val wasActive = token == app.tokenStore.token
        if (!wasActive) {
            app.tokenStore.remove(token)
            _state.update { it.copy(accounts = app.tokenStore.accounts, activeToken = app.tokenStore.token) }
            return true
        }
        resetSession()
        app.tokenStore.remove(token)
        val hasNext = app.tokenStore.token != null
        _state.value = freshState(loggedIn = hasNext)
        if (hasNext) refresh()
        return hasNext
    }

    fun logout(): Boolean = app.tokenStore.token?.let { logoutAccount(it) } ?: false

    fun logoutAll() {
        resetSession()
        app.tokenStore.clear()
        _state.value = freshState(loggedIn = false)
    }

    private suspend fun fetchLibrary(): Library = withContext(Dispatchers.IO) {
        val acc = api.account()
        val unknownUser = acc.uid == 0L && acc.login.isBlank()
        val liked = runCatching { api.likedTracks(acc.apiId) }
            .getOrElse { if (unknownUser) emptyList() else throw it }
        val lists = runCatching { api.playlists(acc.apiId) }
            .getOrElse { if (unknownUser) emptyList() else throw it }
        val albums = if (unknownUser) emptyList() else runCatching { api.likedAlbums(acc.apiId) }.getOrNull()
        Library(acc, liked, lists, albums)
    }

    private fun applyLibrary(tok: String?, lib: Library) {
        if (tok != app.tokenStore.token) return
        if (tok != null) app.tokenStore.updateProfile(tok, lib.acc)
        _state.update {
            it.copy(
                loading = false, account = lib.acc, liked = lib.liked, playlists = lib.lists, offline = false,
                likedAlbums = lib.albums ?: it.likedAlbums,
                accounts = app.tokenStore.accounts, activeToken = app.tokenStore.token,
            )
        }
        ownerKey()?.let { k -> app.offlineLibrary.saveLibrary(k, lib.liked, lib.lists, lib.albums) }
        loadDislikes(lib.acc)
        loadSyncedHistory(force = true)
        prefetchPlaylists(lib.lists)
        resumePendingWave()
    }

    private fun prefetchPlaylists(lists: List<PlaylistInfo>) {
        val k = ownerKey() ?: return
        val tok = app.tokenStore.token
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            for (p in lists.take(60)) {
                if (tok != app.tokenStore.token) return@launch
                if (app.offlineLibrary.load(k).playlistTracks.containsKey(p.coverKey())) continue
                val r = try {
                    api.playlistTracks(p.ownerUid, p.kind)
                } catch (e: Exception) {
                    if (e is java.io.IOException) return@launch else continue
                }
                app.offlineLibrary.savePlaylist(k, p, r)
            }
        }
    }

    fun refresh() {
        if (!_state.value.loggedIn) { reloadLocalTracks(); return }
        val tok = app.tokenStore.token
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            if (!Net.isOnline(app)) {
                restoreOffline(java.io.IOException("offline"))
                return@launch
            }
            runCatching { fetchLibrary() }
                .onSuccess { lib -> applyLibrary(tok, lib) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    if (tok != app.tokenStore.token) return@onFailure
                    val authBroken = e.message?.contains("HTTP 401") == true
                    if (authBroken && logout()) {
                        _state.update { it.copy(message = "Сессия аккаунта истекла — выполнен вход в другой аккаунт") }
                        return@onFailure
                    }
                    if (!authBroken && restoreOffline(e)) return@onFailure
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun pullRefresh(searchQuery: String = "") {
        if (!_state.value.loggedIn) { reloadLocalTracks(); return }
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val s0 = _state.value
            val tok = app.tokenStore.token
            coroutineScope {
                launch {
                    runCatching { fetchLibrary() }
                        .onSuccess { lib -> applyLibrary(tok, lib) }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            if (tok != app.tokenStore.token) return@onFailure
                            if (e.message?.contains("HTTP 401") == true && logout()) {
                                _state.update { it.copy(message = "Сессия аккаунта истекла — выполнен вход в другой аккаунт") }
                                return@onFailure
                            }
                            if (restoreOffline(e)) return@onFailure
                            _state.update { it.copy(error = e.message) }
                        }
                }
                s0.openedPlaylist?.let { p ->
                    launch {
                        runCatching { withContext(Dispatchers.IO) { api.playlistTracks(p.ownerUid, p.kind) } }
                            .onSuccess { r ->
                                _state.update { s ->
                                    if (s.openedPlaylist?.kind == p.kind) s.copy(openedPlaylistTracks = r) else s
                                }
                            }
                    }
                }
                s0.openedArtist?.let { a ->
                    launch {
                        runCatching { withContext(Dispatchers.IO) { api.artist(a.id) } }
                            .onSuccess { full ->
                                _state.update { s ->
                                    if (s.openedArtist?.id != a.id) s
                                    else s.copy(
                                        openedArtist = full.copy(
                                            name = full.name.ifBlank { a.name },
                                            coverUri = full.coverUri ?: a.coverUri,
                                        ),
                                    )
                                }
                            }
                    }
                    val all = _artistAll.value
                    if (all.artistId == a.id) {
                        launch {
                            runCatching { withContext(Dispatchers.IO) { api.artistAllTracks(a.id) } }
                                .onSuccess { r -> _artistAll.update { if (it.artistId == a.id) it.copy(tracks = r) else it } }
                        }
                    }
                }
                s0.openedAlbum?.let { a ->
                    launch {
                        runCatching { withContext(Dispatchers.IO) { api.album(a.id) } }
                            .onSuccess { full ->
                                _state.update { s ->
                                    if (s.openedAlbum?.id != a.id) s
                                    else s.copy(
                                        openedAlbum = full.copy(
                                            title = full.title.ifBlank { a.title },
                                            coverUri = full.coverUri ?: a.coverUri,
                                        ),
                                    )
                                }
                            }
                    }
                }
                if (LocalFilesSettings.enabled && LocalMusic.hasPermission(app)) {
                    launch {
                        runCatching { withContext(Dispatchers.IO) { LocalMusic.load(app) } }
                            .onSuccess { r -> _state.update { it.copy(localTracks = r) } }
                    }
                }
                if (searchQuery.isNotBlank()) {
                    launch {
                        runCatching {
                            withContext(Dispatchers.IO) { runSearch(searchQuery) }
                        }.onSuccess { b -> applySearch(searchQuery, b) }
                    }
                }
            }
            _state.update { it.copy(refreshing = false) }
        }
    }

    private fun storedAccount(): Account? =
        app.tokenStore.accounts.firstOrNull { it.token == app.tokenStore.token }
            ?.let { Account(it.uid, it.login, it.name, it.hasPlus, it.avatarUrl) }

    private fun restoreOffline(e: Throwable): Boolean {
        if (e !is java.io.IOException && Net.isOnline(app)) return false
        val snap = ownerKey()?.let { app.offlineLibrary.load(it) }
        val hasCache = snap != null && !snap.isEmpty
        val hasDownloads = app.downloads.state.value.items.isNotEmpty()
        _state.update { s ->
            s.copy(
                loading = false, offline = true,
                account = s.account ?: storedAccount(),
                liked = if (s.liked.isEmpty() && snap != null) snap.liked else s.liked,
                playlists = if (s.playlists.isEmpty() && snap != null) snap.playlists else s.playlists,
                likedAlbums = if (s.likedAlbums.isEmpty() && snap != null) snap.likedAlbums else s.likedAlbums,
                message = when {
                    hasCache || hasDownloads -> "Нет сети — показаны сохранённые списки, играют скачанные треки"
                    else -> "Нет сети"
                },
            )
        }
        if (Net.isOnline(app)) startReconnect()
        return true
    }

    private fun cachedPlaylist(p: PlaylistInfo): List<Track>? =
        ownerKey()?.let { app.offlineLibrary.load(it).playlistTracks[p.coverKey()] }

    private fun cachedAlbum(id: String): AlbumInfo? =
        ownerKey()?.let { app.offlineLibrary.load(it).albums[id] }

    private fun offlineAlbum(id: String): AlbumInfo? =
        cachedAlbum(id) ?: albumsFromDownloads(app.downloads.state.value.items).firstOrNull { it.id == id }

    private fun loadDislikes(acc: Account) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.dislikedTrackIds(acc.apiId) } }
                .onSuccess { ids -> _state.update { it.copy(dislikedIds = ids) } }
        }
    }

    private var searchJob: Job? = null
    private var searchSeq = 0
    private var inFlightQuery: String? = null

    fun search(query: String, live: Boolean = false) {
        if (query.isBlank()) return
        ShareLinks.parse(query)?.let { link ->
            if (!live) requestLink(link)
            return
        }
        if (query.trim() == inFlightQuery) return
        searchJob?.cancel()
        val seq = ++searchSeq
        inFlightQuery = query.trim()
        _state.update { it.copy(searchLoading = true) }
        searchJob = viewModelScope.launch {
            try {
                val b = withContext(Dispatchers.IO) { runSearch(query) }
                applySearch(query, b)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!live) _state.update { it.copy(error = e.message) }
            } finally {
                if (seq == searchSeq) {
                    inFlightQuery = null
                    _state.update { it.copy(searchLoading = false) }
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchSeq++
        inFlightQuery = null
        _state.update {
            it.copy(
                searchResults = emptyList(), searchArtists = emptyList(), searchAlbums = emptyList(),
                searchPodcasts = emptyList(), searchedQuery = "", searchLoading = false,
            )
        }
    }

    private fun runSearch(query: String): SearchBundle {
        val artists = runCatching { api.searchArtists(query) }.getOrDefault(emptyList())
        val albums = runCatching { api.searchAlbums(query) }.getOrDefault(emptyList())
        val podcasts = runCatching { api.searchPodcasts(query) }.getOrDefault(emptyList())
        return SearchBundle(api.search(query), artists, albums, podcasts)
    }

    private fun applySearch(query: String, b: SearchBundle) {
        _state.update {
            it.copy(
                searchResults = b.tracks, searchArtists = b.artists, searchAlbums = b.albums,
                searchPodcasts = b.podcasts, searchedQuery = query.trim(),
            )
        }
    }

    fun loadChart(force: Boolean = false) {
        val st = _state.value
        if (st.chartLoading || (!force && st.chart.isNotEmpty())) return
        _state.update { it.copy(chartLoading = true, chartFailed = false) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.chart() } }
                .onSuccess { r -> _state.update { it.copy(chart = r, chartLoading = false, chartFailed = r.isEmpty()) } }
                .onFailure { _state.update { it.copy(chartLoading = false, chartFailed = true) } }
        }
    }

    private val newReleaseDays = 120L

    private fun topArtistIds(listened: List<Track>, liked: List<Track>, limit: Int = 12): List<String> {
        val score = HashMap<String, Int>()
        listened.forEach { t -> t.artistRefs.forEach { r -> score[r.id] = (score[r.id] ?: 0) + 2 } }
        liked.forEach { t -> t.artistRefs.forEach { r -> score[r.id] = (score[r.id] ?: 0) + 1 } }
        return score.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    fun loadNewReleases(force: Boolean = false) {
        val st = _state.value
        if (st.newReleasesLoading || !st.loggedIn || (!force && st.newReleases.isNotEmpty())) return
        val tok = app.tokenStore.token
        _state.update { it.copy(newReleasesLoading = true, newReleasesFailed = false) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val s0 = _state.value
                    var listened = (s0.history + s0.syncedHistory + s0.waveHistory).distinctBy { it.id }
                    if (s0.syncedHistory.isEmpty()) {
                        listened = (listened + runCatching { api.musicHistoryFull(150).tracks }.getOrDefault(emptyList())).distinctBy { it.id }
                    }
                    val ids = topArtistIds(listened, s0.liked)
                    val cutoff = java.time.LocalDate.now().minusDays(newReleaseDays).toString()
                    val albums = coroutineScope {
                        ids.map { id -> async { runCatching { api.artistRecentAlbums(id) }.getOrDefault(emptyList()) } }.awaitAll()
                    }.flatten()
                        .filter { it.sortDate.isNotEmpty() && it.sortDate >= cutoff }
                        .distinctBy { it.id }
                        .sortedByDescending { it.sortDate }
                        .take(24)
                    val tracks = coroutineScope {
                        albums.take(8).map { a -> async { runCatching { api.album(a.id).tracks }.getOrDefault(emptyList()) } }.awaitAll()
                    }.flatten().distinctBy { it.id }.take(80)
                    albums to tracks
                }
            }.onSuccess { (albums, tracks) ->
                if (tok != app.tokenStore.token) return@onSuccess
                _state.update { it.copy(newReleases = albums, newReleaseTracks = tracks, newReleasesLoading = false, newReleasesFailed = false) }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                _state.update { it.copy(newReleasesLoading = false, newReleasesFailed = true) }
            }
        }
    }

    fun loadSyncedHistory(force: Boolean = false) {
        val st = _state.value
        if (st.syncedHistoryLoading || !st.loggedIn) return
        if (!force && st.syncedHistory.isNotEmpty() && st.syncedHistory.size > st.history.size) return
        val tok = app.tokenStore.token
        _state.update { it.copy(syncedHistoryLoading = true) }
        viewModelScope.launch {
            val remote = runCatching { withContext(Dispatchers.IO) { api.musicHistoryFull(150) } }.getOrNull()
            if (tok != app.tokenStore.token) return@launch
            _state.update {
                it.copy(
                    syncedHistory = mergeHistory(it.history, remote?.tracks.orEmpty()),
                    syncedWave = remote?.waveTracks.orEmpty().ifEmpty { it.syncedWave },
                    syncedHistoryLoading = false,
                )
            }
        }
    }

    private fun mergeHistory(local: List<Track>, remote: List<Track>): List<Track> {
        val seen = HashSet<String>()
        return (local + remote).filter { seen.add(it.id) }.take(100)
    }

    private var podcastJob: Job? = null

    fun searchPodcastsPage(query: String) {
        podcastJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(podcasts = emptyList(), podcastsQuery = "", podcastsLoading = false) }
            loadPodcasts()
            return
        }
        _state.update { it.copy(podcastsLoading = true) }
        podcastJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.searchPodcasts(q) } }
                .onSuccess { l -> _state.update { it.copy(podcasts = l, podcastsQuery = q, podcastsLoading = false) } }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(podcastsLoading = false, error = e.message) }
                }
        }
    }

    fun loadPodcasts() {
        val st = _state.value
        if (st.podcastsLoading || st.podcasts.isNotEmpty()) return
        _state.update { it.copy(podcastsLoading = true) }
        podcastJob = viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                runCatching { api.podcastsLanding() }.getOrDefault(emptyList()).ifEmpty {
                    runCatching { api.searchPodcasts("подкаст") }.getOrDefault(emptyList())
                }
            }
            _state.update { it.copy(podcasts = list, podcastsLoading = false) }
        }
    }

    private var localSearchJob: Job? = null

    fun loadLocalTracks() {
        if (!LocalMusic.hasPermission(app) || _state.value.localLoading) return
        _state.update { it.copy(localLoading = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadLocalWithLibrary() } }
                .onSuccess { (r, lib) -> _state.update { it.copy(localTracks = r, localLib = lib, localLoading = false) } }
                .onFailure { e -> _state.update { it.copy(localLoading = false, error = e.message ?: "Не удалось прочитать файлы с устройства") } }
        }
    }

    private fun loadLocalWithLibrary(): Pair<List<Track>, LocalLibrary> {
        val tracks = LocalMusic.load(app, withRefs = LocalModeSettings.enabled)
        return tracks to LocalLibrary.build(tracks)
    }

    private fun reloadLocalTracks() {
        if (_state.value.refreshing) return
        if (!LocalMusic.hasPermission(app)) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadLocalWithLibrary() } }
                .onSuccess { (r, lib) -> _state.update { it.copy(localTracks = r, localLib = lib, refreshing = false) } }
                .onFailure { _state.update { it.copy(refreshing = false) } }
        }
    }

    fun searchLocal(query: String) {
        localSearchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty() || !LocalFilesSettings.enabled || !LocalMusic.hasPermission(app)) {
            _state.update { if (it.searchLocal.isEmpty()) it else it.copy(searchLocal = emptyList()) }
            return
        }
        localSearchJob = viewModelScope.launch {
            val found = try {
                withContext(Dispatchers.IO) {
                    LocalMusic.load(app).filter {
                        it.title.contains(q, ignoreCase = true) ||
                            it.artists.contains(q, ignoreCase = true) ||
                            (it.albumTitle?.contains(q, ignoreCase = true) == true)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(searchLocal = found) }
        }
    }

    fun openArtist(ref: ArtistRef) {
        if (ref.id.startsWith(LOCAL_ARTIST_PREFIX)) { requestLocalNav("artist:" + ref.name); return }
        showArtist(ArtistInfo(ref.id, ref.name, null))
    }

    fun openArtist(a: ArtistInfo) = showArtist(a)

    private fun showArtist(a: ArtistInfo, record: Boolean = true) {
        if (record) rememberOpenedPage(PageEntry.Artist(a.id)) else Unit
        _state.update {
            it.copy(
                openedArtist = a, openedArtistLoading = true,
                openedAlbum = null, openedAlbumLoading = false, albumFocusTrackId = null,
                openedPlaylist = null, openedPlaylistTracks = emptyList(), openedPlaylistLoading = false,
            )
        }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.artist(a.id) } }
                .onSuccess { full ->
                    _state.update { s ->
                        if (s.openedArtist?.id != a.id) s
                        else s.copy(
                            openedArtist = full.copy(
                                name = full.name.ifBlank { a.name },
                                coverUri = full.coverUri ?: a.coverUri,
                            ),
                            openedArtistLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { s ->
                        if (s.openedArtist?.id != a.id) s else s.copy(openedArtistLoading = false, error = e.message)
                    }
                }
        }
    }

    fun closeArtist() {
        if (restorePreviousPage()) return
        _state.update { it.copy(openedArtist = null, openedArtistLoading = false) }
    }

    fun closeAllPages() {
        pageBack.clear()
        _state.update {
            it.copy(
                openedArtist = null, openedArtistLoading = false,
                openedAlbum = null, openedAlbumLoading = false, albumFocusTrackId = null,
            )
        }
    }

    private sealed class PageEntry {
        data class Artist(val info: ArtistInfo) : PageEntry() {
            constructor(id: String) : this(ArtistInfo(id, "", null))
        }
        data class Album(val info: AlbumInfo) : PageEntry() {
            constructor(id: String) : this(AlbumInfo(id = id, title = ""))
        }
    }

    private val pageBack = ArrayDeque<PageEntry>()

    private fun PageEntry.sameAs(o: PageEntry): Boolean = when {
        this is PageEntry.Artist && o is PageEntry.Artist -> info.id == o.info.id
        this is PageEntry.Album && o is PageEntry.Album -> info.id == o.info.id
        else -> false
    }

    private fun rememberOpenedPage(next: PageEntry) {
        val s = _state.value
        val current: PageEntry? = when {
            s.openedAlbum != null -> PageEntry.Album(s.openedAlbum)
            s.openedArtist != null -> PageEntry.Artist(s.openedArtist)
            else -> null
        }
        if (current == null) { pageBack.clear(); return }
        if (current.sameAs(next)) return
        val top = pageBack.lastOrNull()
        if (top != null && top.sameAs(next)) { pageBack.removeLast(); return }
        pageBack.addLast(current)
        while (pageBack.size > 16) pageBack.removeFirst()
    }

    private fun restorePreviousPage(): Boolean {
        val prev = pageBack.removeLastOrNull() ?: return false
        when (prev) {
            is PageEntry.Artist -> showArtist(prev.info, record = false)
            is PageEntry.Album -> showAlbum(prev.info, null, record = false)
        }
        return true
    }

    fun openAlbum(a: AlbumInfo) = showAlbum(a)

    fun openAlbum(id: String, title: String?) = showAlbum(AlbumInfo(id = id, title = title.orEmpty()))

    fun openAlbumAtTrack(id: String, title: String?, trackId: String) =
        showAlbum(AlbumInfo(id = id, title = title.orEmpty()), trackId)

    private fun showAlbum(a: AlbumInfo, focusTrackId: String? = null, record: Boolean = true) {
        if (record) rememberOpenedPage(PageEntry.Album(a))
        _state.update {
            it.copy(
                openedAlbum = a, openedAlbumLoading = true, albumFocusTrackId = focusTrackId,
                openedArtist = null, openedArtistLoading = false,
                openedPlaylist = null, openedPlaylistTracks = emptyList(), openedPlaylistLoading = false,
            )
        }
        viewModelScope.launch {
            if (!Net.isOnline(app)) {
                offlineAlbum(a.id)?.let { cached ->
                    _state.update { s ->
                        if (s.openedAlbum?.id != a.id) s
                        else s.copy(openedAlbum = cached, openedAlbumLoading = false, offline = true, message = "Нет сети — показан сохранённый альбом")
                    }
                    return@launch
                }
            }
            runCatching { withContext(Dispatchers.IO) { api.album(a.id) } }
                .onSuccess { full ->
                    val merged = full.copy(title = full.title.ifBlank { a.title }, coverUri = full.coverUri ?: a.coverUri)
                    ownerKey()?.let { k -> app.offlineLibrary.saveAlbum(k, merged) }
                    _state.update { s ->
                        if (s.openedAlbum?.id != a.id) s else s.copy(openedAlbum = merged, openedAlbumLoading = false)
                    }
                }
                .onFailure { e ->
                    val cached = offlineAlbum(a.id)
                    _state.update { s ->
                        when {
                            s.openedAlbum?.id != a.id -> s
                            cached != null -> s.copy(openedAlbum = cached, openedAlbumLoading = false, offline = true, message = "Нет сети — показан сохранённый альбом")
                            else -> s.copy(openedAlbumLoading = false, error = e.message)
                        }
                    }
                    if (cached != null && Net.isOnline(app)) startReconnect()
                }
        }
    }

    fun closeAlbum() {
        if (restorePreviousPage()) return
        _state.update { it.copy(openedAlbum = null, openedAlbumLoading = false, albumFocusTrackId = null) }
    }

    fun openPlaylist(p: PlaylistInfo) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.playlistTracks(p.ownerUid, p.kind) } }
                .onSuccess { tracks ->
                    ownerKey()?.let { k -> app.offlineLibrary.savePlaylist(k, p, tracks) }
                    play(tracks, 0, source = playlistSourceKey(p))
                }
                .onFailure { e ->
                    val cached = cachedPlaylist(p)
                    if (cached != null) play(cached, 0, source = playlistSourceKey(p)) else _state.update { it.copy(error = e.message) }
                }
        }
    }

    fun showPlaylist(p: PlaylistInfo) {
        pageBack.clear()
        _state.update {
            it.copy(
                openedPlaylist = p, openedPlaylistTracks = emptyList(), openedPlaylistLoading = true,
                openedArtist = null, openedArtistLoading = false,
                openedAlbum = null, openedAlbumLoading = false, albumFocusTrackId = null,
            )
        }
        viewModelScope.launch {
            if (!Net.isOnline(app)) {
                cachedPlaylist(p)?.let { cached ->
                    _state.update { it.copy(openedPlaylistTracks = cached, openedPlaylistLoading = false, offline = true, message = "Нет сети — показан сохранённый плейлист") }
                    return@launch
                }
            }
            runCatching { withContext(Dispatchers.IO) { api.playlistTracks(p.ownerUid, p.kind) } }
                .onSuccess { r ->
                    ownerKey()?.let { k -> app.offlineLibrary.savePlaylist(k, p, r) }
                    _state.update { it.copy(openedPlaylistTracks = r, openedPlaylistLoading = false) }
                }
                .onFailure { e ->
                    val cached = cachedPlaylist(p)
                    if (cached != null) {
                        _state.update { it.copy(openedPlaylistTracks = cached, openedPlaylistLoading = false, offline = true, message = "Нет сети — показан сохранённый плейлист") }
                        if (Net.isOnline(app)) startReconnect()
                    } else _state.update { it.copy(openedPlaylistLoading = false, error = e.message) }
                }
        }
    }

    fun closePlaylist() = _state.update {
        it.copy(openedPlaylist = null, openedPlaylistTracks = emptyList(), openedPlaylistLoading = false)
    }

    fun openLink(link: SharedLink) {
        if (!_state.value.loggedIn) {
            _state.update { it.copy(error = "Войдите в Яндекс Музыку, чтобы открыть ссылку") }
            return
        }
        when (link) {
            is SharedLink.AlbumLink -> openAlbum(link.albumId, null)
            is SharedLink.ArtistLink -> openArtist(ArtistRef(link.artistId, ""))
            is SharedLink.TrackLink -> {
                val album = link.albumId
                if (album != null) openAlbumAtTrack(album, null, link.trackId)
                else viewModelScope.launch {
                    runCatching { withContext(Dispatchers.IO) { api.tracksByIds(listOf(link.trackId)) } }
                        .onSuccess { found ->
                            val t = found.firstOrNull()
                            if (t == null) _state.update { it.copy(error = "Трек не найден") }
                            else play(listOf(t), 0)
                        }
                        .onFailure { e -> _state.update { it.copy(error = e.message ?: "Не удалось открыть трек") } }
                }
            }
            is SharedLink.UserLink -> _state.update { it.copy(error = "Профиль открывается окном со списком плейлистов") }
            is SharedLink.PlaylistUuidLink -> viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { api.playlistInfoByUuid(link.uuid) } }
                    .onSuccess { info -> showPlaylist(info) }
                    .onFailure { _state.update { s -> s.copy(error = "Не удалось открыть плейлист: он приватный или удалён") } }
            }
            is SharedLink.PlaylistLink -> viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { api.playlistInfo(link.owner, link.kind) } }
                    .onSuccess { info -> showPlaylist(info) }
                    .onFailure {
                        _state.update { s ->
                            s.copy(error = "Не удалось открыть плейлист: он приватный или удалён")
                        }
                    }
            }
        }
    }

    fun setPlaylistPublic(p: PlaylistInfo, isPublic: Boolean) {
        if (_state.value.account == null) return
        val vis = if (isPublic) "public" else "private"
        fun PlaylistInfo.sameAs(o: PlaylistInfo) = kind == o.kind && ownerUid == o.ownerUid
        _state.update { s ->
            s.copy(
                playlists = s.playlists.map { if (it.sameAs(p)) it.copy(visibility = vis) else it },
                openedPlaylist = s.openedPlaylist?.let { if (it.sameAs(p)) it.copy(visibility = vis) else it },
            )
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { playlistMutex.withLock { api.setPlaylistVisibility(p.ownerUid, p.kind, isPublic) } }
            }.onSuccess {
                _state.update { it.copy(message = if (isPublic) "Плейлист стал публичным" else "Плейлист стал приватным") }
                ownerKey()?.let { k -> app.offlineLibrary.saveLibrary(k, _state.value.liked, _state.value.playlists) }
            }.onFailure { e ->
                _state.update { s ->
                    s.copy(
                        playlists = s.playlists.map { if (it.sameAs(p)) it.copy(visibility = p.visibility) else it },
                        openedPlaylist = s.openedPlaylist?.let { if (it.sameAs(p)) it.copy(visibility = p.visibility) else it },
                        error = e.message ?: "Не удалось изменить доступ к плейлисту",
                    )
                }
            }
        }
    }

    fun toggleAlbumLike(album: AlbumInfo) {
        val acc = _state.value.account ?: return
        if (album.id.isBlank()) return
        val prev = _state.value.likedAlbums
        val wasLiked = prev.any { it.id == album.id }
        val shown = album.copy(tracks = emptyList())
        _state.update { s ->
            s.copy(likedAlbums = if (wasLiked) s.likedAlbums.filterNot { it.id == album.id } else listOf(shown) + s.likedAlbums)
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (wasLiked) api.unlikeAlbum(acc.apiId, album.id) else api.likeAlbum(acc.apiId, album.id)
                }
            }.onSuccess {
                ownerKey()?.let { k -> app.offlineLibrary.saveLibrary(k, _state.value.liked, _state.value.playlists, _state.value.likedAlbums) }
            }.onFailure { e ->
                _state.update { it.copy(likedAlbums = prev, error = e.message ?: "Не удалось изменить лайк альбома") }
            }
        }
    }

    fun reloadLocalAfterEdit() {
        if (!LocalMusic.hasPermission(app)) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    if (LocalModeSettings.enabled) loadLocalWithLibrary() else LocalMusic.load(app) to _state.value.localLib
                }
            }.onSuccess { (r, lib) -> _state.update { it.copy(localTracks = r, localLib = lib) } }
        }
    }

    private var preloadJob: kotlinx.coroutines.Job? = null

    private fun schedulePreload() {
        preloadJob?.cancel()
        if (!PlayerSettings.preload) { TrackPreloader.cancel(); return }
        preloadJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            kotlinx.coroutines.delay(400)
            val tl = player.currentTimeline
            var idx = player.currentMediaItemIndex
            if (tl.isEmpty || idx < 0 || idx >= tl.windowCount) { TrackPreloader.cancel(); return@launch }
            val ids = ArrayList<String>(TrackPreloader.AHEAD)
            for (i in 0 until TrackPreloader.AHEAD) {
                idx = tl.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
                if (idx == C.INDEX_UNSET) break
                val mi = player.getMediaItemAt(idx)
                if (mi.localConfiguration?.uri?.scheme == "ymusic") ids += mi.mediaId
            }
            TrackPreloader.preload(app, api, ids) { id -> runCatching { app.downloads.playableUri(id) != null }.getOrDefault(false) }
        }
    }

    private fun toMediaItem(t: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(t.localUri ?: "ymusic://track/${t.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title).setArtist(t.artists)
                    .setArtworkUri(t.cover()?.let(Uri::parse))
                    .setExtras(Bundle().apply {
                        putString(EXTRA_ALBUM_ID, t.albumId)
                        putString(EXTRA_ALBUM_TITLE, t.albumTitle)
                        putBoolean(EXTRA_EXPLICIT, t.explicit)
                        putLong(EXTRA_DURATION, t.durationMs)
                    })
                    .build()
            )
            .build()

    fun play(all: List<Track>, startIndex: Int, shuffle: Boolean = false, source: String? = null) {
        if (all.isEmpty()) return
        var tracks = all
        var start = startIndex
        if (!Net.isOnline(app)) {
            val tapped = all.getOrNull(startIndex)
            val playable = all.filter { it.isLocal || app.downloads.isDownloaded(it.id) }
            if (playable.isEmpty()) {
                _state.update { it.copy(error = "Нет сети, а скачанных треков здесь нет") }
                return
            }
            if (playable.size < all.size) {
                _state.update { it.copy(message = "Нет сети — играют скачанные треки (${playable.size} из ${all.size})") }
            }
            tracks = playable
            start = tapped?.let { t -> playable.indexOfFirst { it.id == t.id }.takeIf { it >= 0 } } ?: 0
        }
        waveActive = false
        val st = _state.value
        val src = source ?: when {
            all == st.liked -> "liked"
            st.openedPlaylist != null && all == st.openedPlaylistTracks ->
                playlistSourceKey(st.openedPlaylist)
            else -> null
        }
        _state.update { if (it.playingSource == src) it else it.copy(playingSource = src) }
        resetSession(
            when {
                src == "liked" -> "Любимое"
                src == "device" -> "Все файлы"
                src != null && src.startsWith("pl:") ->
                    (st.playlists + listOfNotNull(st.openedPlaylist)).firstOrNull { playlistSourceKey(it) == src }?.title
                        ?.let { "Плейлист «$it»" } ?: "Плейлист"
                else -> "Очередь"
            },
        )
        val toRemote = connect.isRemoteActive()
        if (toRemote) connectBusy = true
        queue.clear(); queue += tracks
        val items = tracks.map { toMediaItem(it) }
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(items, start, 0)
        player.prepare()
        if (toRemote) player.pause() else player.play()
        ensureService()
        persistQueue()
        if (toRemote) {
            try { pushQueueToRemote() } finally { connectBusy = false }
        }
    }

    private fun ensureService() {
        runCatching { app.startService(Intent(app, PlaybackService::class.java)) }
    }

    @Volatile private var wavePending = false
    private var wavePendingJob: Job? = null

    private fun libraryReady(): Boolean {
        val s = _state.value
        return !s.loading && !s.offline && reconnectJob?.isActive != true && Net.isOnline(app)
    }

    private var wavePendingStations: List<String> = listOf(DEFAULT_WAVE)
    private var wavePendingLabel: String = "Моя волна"

    fun startWave(stations: List<String> = listOf(DEFAULT_WAVE), label: String = "Моя волна") {
        if (_state.value.waveLoading) return
        if (!libraryReady()) {
            _state.update { it.copy(waveLoading = true, message = "Главная ещё загружается — волна запустится сразу после загрузки") }
            wavePending = true
            wavePendingStations = stations
            wavePendingLabel = label
            wavePendingJob?.cancel()
            wavePendingJob = viewModelScope.launch {
                delay(WAVE_WAIT_MS)
                if (wavePending) { wavePending = false; launchWave(wavePendingStations, wavePendingLabel) }
            }
            retryIfOffline()
            return
        }
        _state.update { it.copy(waveLoading = true) }
        launchWave(stations, label)
    }

    fun restartWave() {
        val st = _state.value
        if (st.waveOn && st.waveStation != DEFAULT_WAVE) startWave(listOf(st.waveStation), st.sessionLabel.ifBlank { "Волна" })
        else startWave()
    }

    private fun resumePendingWave() {
        if (!wavePending) return
        wavePending = false
        wavePendingJob?.cancel()
        launchWave(wavePendingStations, wavePendingLabel)
    }

    private fun cancelPendingWave() {
        wavePending = false
        wavePendingJob?.cancel()
    }

    private fun launchWave(stations: List<String> = listOf(DEFAULT_WAVE), label: String = "Моя волна") {
        viewModelScope.launch {
            var error: Throwable? = null
            var found: Pair<String, app.ymusic.m3e.data.YandexMusicApi.WaveBatch>? = null
            for (st in stations) {
                val r = runCatching { withContext(Dispatchers.IO) { api.waveTracks(null, st) } }
                val batch = r.getOrNull()
                if (batch != null && batch.tracks.isNotEmpty()) { found = st to batch; break }
                if (r.isFailure) error = r.exceptionOrNull()
            }
            val f = found
            if (f == null) {
                _state.update {
                    it.copy(error = if (error != null) (error.message ?: "Не удалось запустить волну") else "Волна не вернула треков")
                }
            } else {
                val (station, batch) = f
                if (player.repeatMode == Player.REPEAT_MODE_ONE) player.repeatMode = Player.REPEAT_MODE_OFF
                play(batch.tracks, 0)
                waveStationId = station
                waveActive = true
                waveBatchId = batch.batchId
                setSessionLabel(label)
                persistQueue()
                if (station != DEFAULT_WAVE) {
                    viewModelScope.launch(Dispatchers.IO) { runCatching { api.waveStarted(station) } }
                }
            }
            _state.update { it.copy(waveLoading = false) }
        }
    }

    private fun loadMoreWave(advance: Boolean = false) {
        if (advance) advanceAfterLoad = true
        if (waveLoadingMore) return
        waveLoadingMore = true
        val station = waveStationId
        viewModelScope.launch {
            val before = player.mediaItemCount
            var batchId: String? = null
            var fresh: List<Track> = emptyList()
            val seeds = listOf(queue.lastOrNull()?.id, player.currentMediaItem?.mediaId).distinct()
            for (seed in seeds) {
                val batch = runCatching { withContext(Dispatchers.IO) { api.waveTracks(seed, station) } }.getOrNull() ?: continue
                batchId = batch.batchId ?: batchId
                fresh = batch.tracks.filter { t -> queue.none { it.id == t.id } }
                if (fresh.isEmpty()) {
                    val recent = queue.takeLast(30).mapTo(HashSet()) { it.id }
                    fresh = batch.tracks.filter { it.id !in recent }
                }
                if (fresh.isNotEmpty()) break
            }
            if (waveActive && waveStationId == station) {
                if (fresh.isNotEmpty()) {
                    queue += fresh
                    player.addMediaItems(fresh.map { toMediaItem(it) })
                }
                waveBatchId = batchId ?: waveBatchId
                persistQueue()
                val go = advanceAfterLoad
                advanceAfterLoad = false
                if (go && player.mediaItemCount > before) {
                    if (player.playbackState == Player.STATE_ENDED || player.currentMediaItemIndex == before - 1) {
                        player.seekTo(before, 0)
                        player.prepare()
                        player.play()
                    }
                }
            } else {
                advanceAfterLoad = false
            }
            waveLoadingMore = false
        }
    }

    fun startWaveByTrack(t: Track) = startWave(listOf("track:${t.id}"), "Волна по треку «${t.title}»")

    fun startWaveByArtist(a: ArtistRef) = startWave(listOf("artist:${a.id}"), "Волна: ${a.name}")

    fun startWaveByAlbum(albumId: String, title: String?) =
        startWave(listOf("album:$albumId"), "Волна по альбому" + (title?.takeIf { it.isNotBlank() }?.let { " «$it»" } ?: ""))

    fun startWaveByPlaylist(owner: String, kind: String, title: String) =
        startWave(listOf("playlist:${owner}_$kind", "playlist:$owner:$kind"), "Волна по плейлисту «$title»")

    fun loadWaveSettings() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.waveSettings() } }
                .onSuccess { ws -> _state.update { it.copy(waveSettings = ws) } }
        }
    }

    fun saveWaveSettings(ws: WaveSettings) {
        _state.update { it.copy(waveSettings = ws) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.saveWaveSettings(ws) } }
                .onSuccess {
                    _state.update { it.copy(message = "Настройки волны сохранены") }
                    if (waveActive && waveStationId == DEFAULT_WAVE) startWave()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Не удалось сохранить настройки") } }
        }
    }

    private val playlistMutex = Mutex()

    private fun mutatePlaylists(okMessage: String, afterOk: (() -> Unit)? = null, block: (Account) -> Unit) {
        val acc = _state.value.account ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { playlistMutex.withLock { block(acc); api.playlists(acc.apiId) } }
            }.onSuccess { lists ->
                _state.update { s ->
                    val cur = s.openedPlaylist
                    val fresh = cur?.let { c -> lists.firstOrNull { it.kind == c.kind && it.ownerUid == c.ownerUid } }
                    s.copy(
                        playlists = lists,
                        openedPlaylist = fresh,
                        openedPlaylistTracks = if (cur != null && fresh == null) emptyList() else s.openedPlaylistTracks,
                        message = okMessage,
                    )
                }
                refreshOpenedTracks()
                afterOk?.invoke()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Не удалось сохранить изменения") }
            }
        }
    }

    private fun refreshOpenedTracks() {
        val p = _state.value.openedPlaylist ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.playlistTracks(p.ownerUid, p.kind) } }
                .onSuccess { r ->
                    _state.update { s ->
                        if (s.openedPlaylist?.kind == p.kind) s.copy(openedPlaylistTracks = r) else s
                    }
                }
        }
    }

    fun createPlaylist(title: String, isPublic: Boolean, addTrack: Track? = null, openAfter: Boolean = false) {
        val name = title.trim()
        if (name.isEmpty()) return
        var created: PlaylistInfo? = null
        mutatePlaylists(
            if (addTrack != null) "Плейлист создан, трек добавлен" else "Плейлист создан",
            afterOk = if (openAfter) ({
                created?.let { c ->
                    val fresh = _state.value.playlists.firstOrNull { it.kind == c.kind && it.ownerUid == c.ownerUid } ?: c
                    showPlaylist(fresh)
                }
            }) else null,
        ) { acc ->
            val p = api.createPlaylist(acc.apiId, name, isPublic)
            created = p
            if (addTrack != null) api.addTrackToPlaylist(p.ownerUid, p.kind, addTrack)
        }
    }

    private var pickerJob: Job? = null

    fun searchForPlaylist(query: String) {
        pickerJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(pickerResults = emptyList(), pickerLoading = false) }
            return
        }
        _state.update { it.copy(pickerLoading = true) }
        pickerJob = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.search(q) } }
                .onSuccess { r -> _state.update { it.copy(pickerResults = r, pickerLoading = false) } }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(pickerLoading = false, error = e.message) }
                }
        }
    }

    fun clearPlaylistSearch() {
        pickerJob?.cancel()
        _state.update { it.copy(pickerResults = emptyList(), pickerLoading = false) }
    }

    fun renamePlaylist(p: PlaylistInfo, title: String) {
        val name = title.trim()
        if (name.isEmpty()) return
        mutatePlaylists("Плейлист переименован") { api.renamePlaylist(p.ownerUid, p.kind, name) }
    }

    fun deletePlaylist(p: PlaylistInfo) {
        mutatePlaylists("Плейлист удалён") { api.deletePlaylist(p.ownerUid, p.kind) }
    }

    fun addTrackToPlaylist(p: PlaylistInfo, t: Track) {
        mutatePlaylists("Добавлено в «${p.title}»") { api.addTrackToPlaylist(p.ownerUid, p.kind, t) }
    }

    fun removeTrackFromPlaylist(p: PlaylistInfo, t: Track) {
        mutatePlaylists("Убрано из «${p.title}»") { api.removeTrackFromPlaylist(p.ownerUid, p.kind, t) }
    }

    fun togglePlay() {
        if (connect.isRemoteActive()) { connect.togglePause(); return }
        ensureService()
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE) {
            player.pause()
            return
        }
        when (player.playbackState) {
            Player.STATE_ENDED -> {
                if (waveActive) { loadMoreWave(advance = true); return }
                player.seekTo(0, 0)
            }
            Player.STATE_IDLE -> if (player.mediaItemCount > 0) player.prepare()
        }
        player.play()
    }
    private fun noPlusLimited(t: Track?): Boolean {
        val a = _state.value.account
        return a != null && !a.hasPlus && t != null && !t.isLocal
    }

    private fun cutNoPlusTrack() {
        if (!warnedNoPlus) {
            warnedNoPlus = true
            _state.update { it.copy(error = "Без Яндекс Плюса треки и подкасты играют только по 30 секунд. С подпиской они будут звучать целиком.") }
        }
        if (waveActive || player.hasNextMediaItem()) next()
        else { player.pause(); player.seekTo(0) }
    }

    private fun cutNoPlusRemote() {
        if (!warnedNoPlus) {
            warnedNoPlus = true
            _state.update { it.copy(error = "Без Яндекс Плюса треки и подкасты играют только по 30 секунд. С подпиской они будут звучать целиком.") }
        }
        if (!connect.skip(1)) {
            connect.seekTo(0)
            if (!connect.state.value.paused) connect.togglePause()
        }
    }

    fun next() {
        if (connect.isRemoteActive()) { connect.skip(1); return }
        if (waveActive && !player.hasNextMediaItem()) { loadMoreWave(advance = true); return }
        player.seekToNextMediaItem()
    }

    fun playFromHistory(t: Track) {
        ensureService()
        if (!Net.isOnline(app) && !t.isLocal && !app.downloads.isDownloaded(t.id)) {
            _state.update { it.copy(error = "Нет сети, а этот трек не скачан") }
            return
        }
        if (player.mediaItemCount == 0 || queue.isEmpty()) { play(listOf(t), 0); return }
        val cur = player.currentMediaItemIndex.coerceAtLeast(0)
        var idx = (cur + 1 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == t.id }
            ?: (0 until cur).firstOrNull { player.getMediaItemAt(it).mediaId == t.id }
        if (idx == null) {
            idx = cur + 1
            queue.add(idx.coerceAtMost(queue.size), t)
            player.addMediaItem(idx, toMediaItem(t))
            persistQueue()
        }
        player.seekTo(idx, 0)
        player.prepare()
        player.play()
    }
    fun skipPrevious() {
        if (connect.isRemoteActive()) { connect.skip(-1); return }
        player.seekToPreviousMediaItem()
    }
    fun previous() {
        if (connect.isRemoteActive()) {
            if (connect.estimatedProgress() > 3_000 || !connect.skip(-1)) connect.seekTo(0)
            return
        }
        player.seekToPrevious()
    }
    fun seekTo(ms: Long) {
        if (connect.isRemoteActive()) { connect.seekTo(ms); return }
        player.seekTo(ms)
    }

    fun livePosition(): Long = if (connect.isRemoteActive()) connect.estimatedProgress() else player.currentPosition

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    fun dismissError() = _state.update { it.copy(error = null) }
    fun dismissMessage() = _state.update { it.copy(message = null) }
}
