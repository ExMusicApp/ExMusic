package app.ymusic.m3e

import app.ymusic.m3e.ui.PlayerSettings
import app.ymusic.m3e.ui.MixSession
import app.ymusic.m3e.ui.SearchHistory
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
import app.ymusic.m3e.data.ScUploadNotifier
import app.ymusic.m3e.data.DownloadMode
import app.ymusic.m3e.data.isDownloadBlocked
import app.ymusic.m3e.data.ScDownloads
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
import app.ymusic.m3e.data.MixCategory
import app.ymusic.m3e.ui.ActiveProvider
import app.ymusic.m3e.data.MixSettings
import app.ymusic.m3e.data.PlaylistShelf
import app.ymusic.m3e.data.Provider
import app.ymusic.m3e.data.ScResolved
import app.ymusic.m3e.data.ScIds
import app.ymusic.m3e.data.LyricsSource
import app.ymusic.m3e.data.MusicRouter
import app.ymusic.m3e.data.SoundCloudApi
import app.ymusic.m3e.data.WaveBatch
import app.ymusic.m3e.data.WaveSeen
import app.ymusic.m3e.data.ScProfile
import app.ymusic.m3e.data.ScTokens
import app.ymusic.m3e.data.SoundCloudAuth
import app.ymusic.m3e.data.SpProfile
import app.ymusic.m3e.data.SpotifyAuth
import app.ymusic.m3e.data.VkAuth
import app.ymusic.m3e.data.VkClient
import app.ymusic.m3e.data.VkIds
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

/** Станция «Моей волны». */
const val DEFAULT_WAVE = "user:onyourwave"

/** Сервис активного аккаунта (без аккаунта — Яндекс). */
val UiState.provider: Provider
    get() = accounts.firstOrNull { it.token == activeToken }?.provider ?: account?.provider ?: Provider.YANDEX

/** Вход в VK через страницу VK в WebView: [busy] — токен проверяется, [error] — что пошло не так. */
data class VkLoginUi(
    val busy: Boolean = false,
    val error: String? = null,
)

/** Вход в SoundCloud: [busy] — код меняется на токены, [waiting] — пользователь в браузере. */
data class ScLoginUi(
    val busy: Boolean = false,
    val waiting: Boolean = false,
    val error: String? = null,
)

/** Вход в Spotify: [busy] — по полученной сессии грузится профиль. */
data class SpLoginUi(
    val busy: Boolean = false,
    val error: String? = null,
)

/** Ключ плейлиста в [UiState.playingSource]. */
fun playlistSourceKey(p: PlaylistInfo): String = "pl:${p.ownerUid}:${p.kind}"

data class UiState(
    val loggedIn: Boolean = false,
    /** Все сохранённые аккаунты (активный — тот, у которого токен совпадает с [activeToken]). */
    val accounts: List<StoredAccount> = emptyList(),
    val activeToken: String? = null,
    /** Открыт экран входа для добавления ещё одного аккаунта (поверх приложения). */
    val addingAccount: Boolean = false,
    /** С какой страницы начать вход при добавлении аккаунта: "yandex", "vk", "soundcloud" (null — с приветствия). */
    val addStart: String? = null,
    val loading: Boolean = false,
    /** Идёт обновление свайпом сверху вниз (pull-to-refresh). */
    val refreshing: Boolean = false,
    val error: String? = null,
    val account: Account? = null,
    val liked: List<Track> = emptyList(),
    val playlists: List<PlaylistInfo> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val nowPlaying: Track? = null,
    val isPlaying: Boolean = false,
    /** Трек загружается (буферизация при воспроизведении) — на кнопке плеера крутятся три точки. */
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val openedPlaylist: PlaylistInfo? = null,
    val openedPlaylistTracks: List<Track> = emptyList(),
    val openedPlaylistLoading: Boolean = false,
    val searchArtists: List<ArtistInfo> = emptyList(),
    val openedArtist: ArtistInfo? = null,
    /** Свои треки и альбомы на странице профиля SoundCloud ([scOwnFor] — id пользователя, для которого загружено). */
    val scOwnTracks: List<Track> = emptyList(),
    val scOwnAlbums: List<AlbumInfo> = emptyList(),
    val scOwnLoading: Boolean = false,
    val scOwnFor: Long = 0L,
    /** Идёт загрузка своего трека на SoundCloud и её прогресс (0..1). */
    val scUploading: Boolean = false,
    val scUploadProgress: Float = 0f,
    val openedArtistLoading: Boolean = false,
    val message: String? = null,
    val waveSettings: WaveSettings = WaveSettings(),
    val waveLoading: Boolean = false,
    /** «Моя волна» сейчас в очереди (запущена и не заменена другим списком). */
    val waveOn: Boolean = false,
    val searchAlbums: List<AlbumInfo> = emptyList(),
    val openedAlbum: AlbumInfo? = null,
    val openedAlbumLoading: Boolean = false,
    /** Трек, к которому нужно прокрутить альбом при открытии (открыт с обложки плеера). */
    val albumFocusTrackId: String? = null,
    /** id треков с отметкой «не нравится». */
    val dislikedIds: Set<String> = emptySet(),
    /** Файлы с устройства (MediaStore) и результаты поиска по ним. */
    val localTracks: List<Track> = emptyList(),
    /** Альбомы и исполнители, собранные из [localTracks] (режим «Локальные файлы»). */
    val localLib: LocalLibrary = LocalLibrary.EMPTY,
    val localLoading: Boolean = false,
    val searchLocal: List<Track> = emptyList(),
    /** Отдельный поиск для добавления песен в плейлист. */
    val pickerResults: List<Track> = emptyList(),
    val pickerLoading: Boolean = false,
    /** Режим повтора: Player.REPEAT_MODE_OFF / ALL / ONE. */
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** История прослушанных треков (новые — первыми), хранится на устройстве. */
    val history: List<Track> = emptyList(),
    /** Треки, которые играли в «Моей волне» (новые — первыми), хранятся на устройстве. */
    val waveHistory: List<Track> = emptyList(),
    /** История прослушивания с сервера Яндекс Музыки (с других устройств) + локальная, новые — первыми. */
    val syncedHistory: List<Track> = emptyList(),
    val syncedHistoryLoading: Boolean = false,
    /** Треки из «Моей волны» по данным аккаунта: включает прослушивания с других устройств. */
    val syncedWave: List<Track> = emptyList(),
    /** Подкасты в результатах поиска. */
    val searchPodcasts: List<AlbumInfo> = emptyList(),
    /** Запрос, по которому поиск реально завершился (для надписи «Ничего не нашлось»). */
    val searchedQuery: String = "",
    /** Идёт запрос поиска (живой поиск показывает индикатор). */
    val searchLoading: Boolean = false,
    /** Чарт. */
    val chart: List<Track> = emptyList(),
    val chartLoading: Boolean = false,
    val chartFailed: Boolean = false,
    /** Подборка подкастов и результаты поиска на странице «Подкасты». */
    val podcasts: List<AlbumInfo> = emptyList(),
    val podcastsLoading: Boolean = false,
    /** Запрос, по которому сейчас показаны подкасты (пусто — подборка). */
    val podcastsQuery: String = "",
    /** Нет сети: показаны сохранённые списки, играют только скачанные треки. */
    val offline: Boolean = false,
    /** Альбомы из коллекции аккаунта (в разделе «Альбомы» к ним добавляются альбомы из скачанного). */
    val likedAlbums: List<AlbumInfo> = emptyList(),
    /** Растёт каждый раз, когда сеть вернулась: по нему обложки, которые не успели загрузиться, пробуют снова. */
    val netEpoch: Int = 0,
    /** Станция играющей волны: user:onyourwave — «Моя волна», иначе волна по треку / исполнителю / альбому / плейлисту. */
    val waveStation: String = DEFAULT_WAVE,
    /** Что играло с момента запуска текущего плейлиста / волны (новые — первыми) и название этого списка. */
    val sessionHistory: List<Track> = emptyList(),
    val sessionLabel: String = "",
    /** Что сейчас играет как список: "liked" — «Любимое», "pl:<owner>:<kind>" — плейлист; null — другое (поиск, волна…). */
    val playingSource: String? = null,
    /** «Новинки»: свежие альбомы артистов, которых слушают чаще всего, и их треки. */
    val newReleases: List<AlbumInfo> = emptyList(),
    val newReleaseTracks: List<Track> = emptyList(),
    val newReleasesLoading: Boolean = false,
    val newReleasesFailed: Boolean = false,
    /** Полки «Для вас» (VK: плейлисты, собранные алгоритмами). */
    val shelves: List<PlaylistShelf> = emptyList(),
    val shelvesLoading: Boolean = false,
    /** Настройки VK Микса (категории с вариантами). */
    val mixSettings: MixSettings? = null,
    val mixSettingsLoading: Boolean = false,
)

/** Всё, что грузится при входе и обновлении: профиль, «Любимое», плейлисты и альбомы (null — альбомы не загрузились). */
private class Library(
    val acc: Account,
    val liked: List<Track>,
    val lists: List<PlaylistInfo>,
    val albums: List<AlbumInfo>?,
)

/** Всё, что отдаёт общий поиск. */
private class SearchBundle(
    val tracks: List<Track>,
    val artists: List<ArtistInfo>,
    val albums: List<AlbumInfo>,
    val podcasts: List<AlbumInfo>,
)

/** Тексты текущего трека из всех источников, нашедших текст (Яндекс, LRCLIB, NetEase, Kugou, QQ Music, Genius, lyrics.ovh). */
data class LyricsState(
    val trackId: String? = null,
    val loading: Boolean = false,
    val done: Boolean = false,
    val sources: List<LyricsSource> = emptyList(),
)

/** Полный список треков исполнителя (для страницы «Треки»). */
data class ArtistTracksState(
    val artistId: String? = null,
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
)

/** Ключ истории прослушивания в режиме «Локальные файлы» (без аккаунта). */
private const val LOCAL_OWNER = "local"

private const val EXTRA_ALBUM_ID = "albumId"
private const val EXTRA_ALBUM_TITLE = "albumTitle"
private const val EXTRA_EXPLICIT = "explicit"
private const val EXTRA_DURATION = "durationMs"

/** Сколько ждать загрузку главной, прежде чем запускать «Мою волну» без неё. */
private const val WAVE_WAIT_MS = 20_000L

class AppViewModel : ViewModel() {
    private val app = App.instance
    private val api = app.api
    private val player = app.player
    init {
        // Смена обложки плейлиста убрана (она хранилась только на устройстве): чистим оставшиеся файлы.
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
    /** Треки, которые сервер реально отдал обрезанными (превью): 30-секундный лимит применяем только к ним. */
    private val previewIds = java.util.Collections.synchronizedSet(HashSet<String>())
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
    /** После дозагрузки волны сразу перейти к следующему треку (очередь кончилась, а человек хочет слушать дальше). */
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

    /**
     * Позиция воспроизведения (мс) отдельным потоком: раньше она лежала в [UiState] и обновлялась каждые 500 мс,
     * из-за чего перерисовывались все экраны, подписанные на состояние целиком (главная причина лагов).
     */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    // ---- Все треки исполнителя ----
    private val _artistAll = MutableStateFlow(ArtistTracksState())
    val artistAllTracks: StateFlow<ArtistTracksState> = _artistAll

    // ---- Запрос «открыть развёрнутый плеер» (кнопка виджета) ----
    private val _openPlayer = MutableStateFlow(false)
    val openPlayerRequest: StateFlow<Boolean> = _openPlayer
    fun requestOpenPlayer() { _openPlayer.value = true }

    /** Запрос из распознавания музыки: открыть вкладку «Поиск» и найти этот текст. */
    private val _searchRequest = MutableStateFlow<String?>(null)
    val searchRequest: StateFlow<String?> = _searchRequest
    fun requestSearch(query: String) { _searchRequest.value = query }

    /** Поиск по распознанному треку: сразу открывает поиск, а запрос остаётся в истории поиска. */
    fun requestRecognizedSearch(query: String) {
        SearchHistory.add(query)
        requestSearch(query)
    }
    fun consumeSearchRequest() { _searchRequest.value = null }

    /** Ссылка на трек / альбом / исполнителя / плейлист Яндекс Музыки, которую нужно открыть (из браузера, сообщения или поиска). */
    private val _linkRequest = MutableStateFlow<SharedLink?>(null)
    val linkRequest: StateFlow<SharedLink?> = _linkRequest
    fun requestLink(link: SharedLink) { _linkRequest.value = link }
    fun consumeLinkRequest() { _linkRequest.value = null }

    /** Ссылка на Яндекс Музыку, найденная в буфере обмена: приложение предлагает её открыть (запасной путь, если система не отдаёт ссылки приложению). */
    private val _clipLink = MutableStateFlow<SharedLink?>(null)
    val clipLink: StateFlow<SharedLink?> = _clipLink
    private var lastClipText: String? = null
    private var lastClipStamp: Long = -1L
    fun offerClipboard(text: String?, stamp: Long = 0L) {
        if (text.isNullOrBlank() || (text == lastClipText && stamp == lastClipStamp)) return
        lastClipText = text
        lastClipStamp = stamp
        val link = ShareLinks.parseFromText(text) ?: return
        AppLog.i("Link", "в буфере обмена найдена ссылка: $link")
        _clipLink.value = link
    }
    fun dismissClipLink() { _clipLink.value = null }

    /** Что показать в окне «чем поделились»: заголовок, обложка и треки. */
    data class LinkPreview(
        val link: SharedLink,
        val title: String,
        val subtitle: String,
        val coverUri: String?,
        val tracks: List<Track>,
        val kind: String,
        val focusTrackId: String? = null,
        /** Для профиля: публичные плейлисты пользователя. */
        val playlists: List<PlaylistInfo> = emptyList(),
    )

    /** Загружает данные ссылки для окна-модалки; ошибка пробрасывается наверх, чтобы окно показало её. */
    suspend fun loadLinkPreview(link: SharedLink): LinkPreview = withContext(Dispatchers.IO) {
        if (!_state.value.loggedIn) error(if (link.isVk()) "Войдите в VK Музыку, чтобы открыть ссылку" else "Войдите в Яндекс Музыку, чтобы открыть ссылку")
        if (link.isVk() && app.tokenStore.activeProvider != Provider.VK) error("Чтобы открыть ссылку VK, переключитесь на аккаунт VK Музыки")
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
            is SharedLink.VkTrackLink -> {
                val t = api.tracksByIds(listOf(VkIds.PREFIX + link.raw)).firstOrNull() ?: error("Трек не найден в VK")
                LinkPreview(link, t.title, t.artists, t.coverUri, listOf(t), "Трек", focusTrackId = t.id)
            }
            is SharedLink.VkAlbumLink -> {
                val a = api.album(link.id)
                val sub = listOf(a.artists, a.year.takeIf { it > 0 }?.toString()).filterNot { it.isNullOrBlank() }.joinToString(" · ")
                LinkPreview(link, a.title, sub, a.coverUri, a.tracks, "Плейлист")
            }
            is SharedLink.VkArtistLink -> {
                val a = api.artist(link.id)
                LinkPreview(link, a.name, "Популярные треки", a.coverUri, a.tracks, "Исполнитель")
            }
            is SharedLink.SoundCloudLink -> when (val r = scApi().resolve(link.url)) {
                is ScResolved.Single ->
                    LinkPreview(link, r.track.title, r.track.artists, r.track.coverUri, listOf(r.track), "Трек", focusTrackId = r.track.id)
                is ScResolved.ListRes ->
                    LinkPreview(link, r.info.title, "${r.info.ownerLogin ?: ""}${if (r.info.ownerLogin != null) " · " else ""}${r.tracks.size} треков", r.info.coverUri, r.tracks, "Плейлист")
                is ScResolved.UserRes ->
                    LinkPreview(link, r.artist.name, "Треки исполнителя", r.artist.coverUri, r.artist.tracks, "Исполнитель")
            }
        }
    }

    private fun SharedLink.isVk() = this is SharedLink.VkTrackLink || this is SharedLink.VkAlbumLink || this is SharedLink.VkArtistLink

    /** SoundCloud-часть роутера: ссылки SoundCloud открываются, даже если активен аккаунт другого сервиса. */
    private fun scApi(): SoundCloudApi = (api as MusicRouter).soundCloud

    /** Кнопка поиска на главной: открыть поиск и сразу поставить курсор в поле (с клавиатурой). */
    private val _searchFocus = MutableStateFlow(false)
    val searchFocusRequest: StateFlow<Boolean> = _searchFocus
    fun requestSearchFocus() { _searchFocus.value = true }
    fun consumeSearchFocus() { _searchFocus.value = false }
    fun consumeOpenPlayer() { _openPlayer.value = false }

    /** Режим «Локальные файлы»: запрос открыть страницу из плеера или меню — "artist:<имя>" или "album:<ключ>". */
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

    // ---- Вывод звука ----
    private val _outputId = MutableStateFlow<Int?>(null)
    val outputDeviceId: StateFlow<Int?> = _outputId

    /** Переключает вывод звука на конкретное устройство; null — выбор системы. */
    fun setAudioOutput(d: AudioDeviceInfo?) {
        player.setPreferredAudioDevice(d)
        _outputId.value = d?.id
    }

    // ---- Колонки и устройства Яндекса (Ynison) ----
    val connect = app.connect
    val connectState: StateFlow<ConnectState> = connect.state

    /** Идёт перенос очереди на колонку / обратно: слушатель плеера в это время на события не реагирует. */
    @Volatile private var connectBusy = false
    @Volatile private var lastRemoteSkipAt = 0L

    /** Открывает сессию с устройствами аккаунта (список колонок приходит сам и обновляется на лету). */
    fun openConnect() { if (api.caps.connect) connect.ensureConnected() }

    /** Возможности сервиса активного аккаунта (для интерфейса). */
    fun apiCaps() = api.caps

    /** Окно «Вывод звука» закрыто: если на колонке ничего не играет, соединение с устройствами не держим. */
    fun connectDialogClosed() {
        if (!connect.isRemoteActive()) connect.disconnect()
    }

    fun remoteActive(): Boolean = connect.isRemoteActive()

    init {
        // Колонка по Wi-Fi (Glagol) не ответила или пропала из сети — забираем музыку обратно на телефон и показываем причину.
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

    /** Включает текущую очередь с текущей позиции на колонке; телефон при этом замолкает и работает пультом. */
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

    /** Новый список запущен, пока играет колонка: переносим его на колонку, а телефон молчит. */
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

    /** Вернуть музыку на телефон: продолжается тот же трек с того же места, колонка встаёт на паузу. */
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

    /** Пока играет колонка: кнопка паузы и ползунок берут состояние у неё; смена трека на колонке отражается в плеере телефона. */
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
        // Кнопки в шторке уведомлений и гарнитура управляют плеером телефона — пока играет колонка, пересылаем их ей.
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (connectBusy || !playWhenReady || !connect.isRemoteActive()) return
                player.pause()
                // «Play» сразу после смены трека — часть того же действия (например, трек из истории), паузу колонке не ставим.
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

    // ---- История и сохранение сессии ----

    /** Отпечаток активного аккаунта: чужую историю и очередь после смены аккаунта не показываем. */
    // Без аккаунта в режиме «Локальные файлы» история прослушивания хранится под своим ключом.
    private fun ownerKey(): String? =
        app.tokenStore.token?.hashCode()?.toString() ?: if (LocalModeSettings.enabled) LOCAL_OWNER else null

    private val historyLimit = 300
    private val listenLogLimit = 800

    /** Локальный журнал прослушивания всех сервисов (новые первыми). Грузится из памяти телефона один раз. */
    private var listenLog: List<Track>? = null

    private fun listenLogAll(): List<Track> = listenLog ?: store.loadListenLog().also { listenLog = it }

    /** Сервис трека по его id: у SoundCloud и VK свои приставки, остальное — Яндекс. */
    private fun providerOfTrack(id: String): Provider = when {
        ScIds.isSc(id) -> Provider.SOUNDCLOUD
        VkIds.isVk(id) -> Provider.VK
        else -> Provider.YANDEX
    }

    /**
     * История, которую помнит само приложение: журнал прослушивания (не зависит от аккаунта и входа) вместе с историей
     * текущего аккаунта. Показывается, пока серверная история не загрузилась, а у SoundCloud — вместо неё.
     */
    private fun localHistory(): List<Track> {
        val prov = app.tokenStore.activeProvider
        val log = listenLogAll().filter { !it.isLocal && providerOfTrack(it.id) == prov }
        val own = store.loadHistory(ownerKey())
        val seen = HashSet<String>()
        return (log + own).filter { seen.add(it.id) }.take(historyLimit)
    }

    /** Добавляет трек в начало истории (без дублей) и сохраняет её (и в журнал прослушивания). */
    private fun recordHistory(trackId: String?) {
        val t = queue.firstOrNull { it.id == trackId } ?: return
        if (!t.isLocal) {
            val log = (listOf(t) + listenLogAll().filterNot { it.id == t.id }).take(listenLogLimit)
            listenLog = log
            store.saveListenLog(log)
        }
        if (_state.value.history.firstOrNull()?.id == t.id) return
        val updated = (listOf(t) + _state.value.history.filterNot { it.id == t.id }).take(historyLimit)
        _state.update { it.copy(history = updated, syncedHistory = if (it.syncedHistory.isEmpty()) it.syncedHistory else mergeHistory(updated.take(1), it.syncedHistory)) }
        store.saveHistory(ownerKey(), updated)
    }

    private val sessionLimit = 300

    /**
     * Запоминает трек в истории «с включения»: она начинается заново при запуске плейлиста / волны и позволяет
     * посмотреть, что играло именно в этом списке, а не во всей истории прослушивания.
     */
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

    /** Новый список / новая волна: история «с включения» начинается заново. */
    private fun resetSession(label: String) {
        _state.update { it.copy(sessionHistory = emptyList(), sessionLabel = label) }
        store.saveSessionLog(ownerKey(), label, emptyList())
    }

    private val waveHistoryLimit = 300

    /** Сколько свежих (не звучавших за сутки) треков волны хотим набрать за раз и сколько порций станции готовы запросить ради этого. */
    private val WAVE_FRESH_WANT = 3
    private val WAVE_FRESH_TRIES = 4

    /** Результат [fetchFreshWave]: свежие треки, id последней порции и запасной список повторов. */
    private class WaveFetch(val fresh: List<Track>, val batchId: String?, val fallback: List<Track>)

    /**
     * Берёт порции станции волны, пока не наберётся [want] свежих треков — тех, что не стоят в очереди ([inQueue]) и не
     * звучали в волне за 24 часа ([WaveSeen]). Каждая следующая порция запрашивается от последнего трека предыдущей, поэтому
     * станция идёт вперёд, а не отдаёт то же самое. [fallback] — на случай, когда свежего нет совсем: сначала то, чего нет в
     * очереди, а если и такого нет — всё, кроме недавно игравшего ([recent]).
     */
    private suspend fun fetchFreshWave(station: String, firstSeeds: List<String?>, inQueue: Set<String>, recent: Set<String>, want: Int): WaveFetch {
        val fresh = LinkedHashMap<String, Track>()
        val notQueued = LinkedHashMap<String, Track>()
        val notRecent = LinkedHashMap<String, Track>()
        var batchId: String? = null
        val seeds = firstSeeds.toMutableList()
        var i = 0
        while (i < seeds.size && i < WAVE_FRESH_TRIES && fresh.size < want) {
            val batch = runCatching { withContext(Dispatchers.IO) { api.waveTracks(seeds[i], station) } }.getOrNull()
            i++
            if (batch == null) continue
            batchId = batch.batchId ?: batchId
            val cand = batch.tracks.filter { it.id !in inQueue && it.id !in fresh }
            cand.forEach { notQueued.putIfAbsent(it.id, it) }
            batch.tracks.filter { it.id !in recent }.forEach { notRecent.putIfAbsent(it.id, it) }
            WaveSeen.filter(cand).forEach { fresh.putIfAbsent(it.id, it) }
            val next = batch.tracks.lastOrNull()?.id
            if (next != null && next !in seeds) seeds += next
        }
        val fallback = notQueued.values.toList().ifEmpty { notRecent.values.toList() }
        return WaveFetch(fresh.values.toList(), batchId, fallback)
    }

    /** Запоминает трек, прозвучавший в «Моей волне»: по этим трекам на странице исполнителя собирается вкладка «Из Моей волны». */
    private fun recordWave(trackId: String?) {
        if (!waveActive) return
        val t = queue.firstOrNull { it.id == trackId } ?: return
        WaveSeen.mark(t.id) // 24 часа этот трек в волну не вернётся (можно выключить в настройках)
        if (_state.value.waveHistory.firstOrNull()?.id == t.id) return
        val updated = (listOf(t) + _state.value.waveHistory.filterNot { it.id == t.id }).take(waveHistoryLimit)
        _state.update { it.copy(waveHistory = updated) }
        store.saveWave(ownerKey(), updated)
    }

    // ---- Тексты песен ----
    private val _lyrics = MutableStateFlow(LyricsState())
    val lyrics: StateFlow<LyricsState> = _lyrics
    private var lyricsJob: Job? = null
    private var lyricsFilter = ""

    /** Загружает тексты трека из всех источников параллельно; каждый показывается, как только пришёл. */
    fun loadLyrics(t: Track) {
        val cur = _lyrics.value
        // Выбранные в настройках источники: если набор изменился, текст трека ищется заново.
        val off = PlayerSettings.lyricsOff
        val filter = off.sorted().joinToString(",")
        if (cur.trackId == t.id && lyricsFilter == filter && (cur.loading || cur.done)) return
        lyricsFilter = filter
        lyricsJob?.cancel()
        _lyrics.value = LyricsState(trackId = t.id, loading = true)
        lyricsJob = viewModelScope.launch {
            val repo = app.lyrics
            val order = repo.providers.map { it.id }
            // Текст, привязанный к этому треку вручную, показывается сразу и приоритетнее найденных автоматически.
            repo.pinned(t)?.let { pin ->
                _lyrics.update { if (it.trackId == t.id) it.copy(sources = listOf(pin) + it.sources) else it }
            }
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

    /** Можно ли сейчас запустить микс текущего трека со следующим. */
    fun canMixNext(): Boolean = !connect.isRemoteActive() && player.hasNextMediaItem()

    /** Микс текущего трека со следующим прямо сейчас — независимо от настройки «Микс треков». */
    fun mixWithNext() {
        if (!canMixNext()) { _state.update { it.copy(message = "Дальше в очереди ничего нет — микшировать не с чем") }; return }
        app.crossfade.mixNow()
        _state.update { it.copy(message = "Микс со следующим треком…") }
    }

    /** Два выбранных трека: играет первый, а на втором переходит с наложением (микс включён для этой пары в любом случае). */
    fun mixTracks(a: Track, b: Track) {
        play(listOf(a, b), 0)
        app.crossfade.forceNextTransitionFrom(a.id)
        MixSession.start(a, b)
        applyMixPoints()
        _state.update { it.copy(message = "Микс: «${a.title}» → «${b.title}»") }
    }

    /** Передаёт движку выбранные в окне микса отрывки (где первый трек уходит и откуда входит второй). */
    private fun applyMixPoints() {
        app.crossfade.setMixPoints(MixSession.a?.id, MixSession.outMs, MixSession.b?.id, MixSession.inMs)
    }

    /** Точка, с которой первый трек начинает уходить (мс; отрицательная — как обычно, в конце). */
    fun mixSetOut(ms: Long) { MixSession.setOut(ms); applyMixPoints() }

    /** Точка, с которой входит второй трек (мс; 0 — с начала). */
    fun mixSetIn(ms: Long) { MixSession.setIn(ms); applyMixPoints() }

    /** Прослушать переход: первый трек запускается незадолго до точки выхода, дальше микс идёт сам. */
    fun mixPreview() {
        val a = MixSession.a ?: return
        val b = MixSession.b ?: return
        val aOut = MixSession.outMs
        val lead = PlayerSettings.crossfadeSec * 1000L + 6000L
        val nextId = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex).mediaId else null
        if (_state.value.nowPlaying?.id == a.id && nextId == b.id) {
            app.crossfade.forceNextTransitionFrom(a.id)
            applyMixPoints()
        } else {
            mixTracks(a, b)
        }
        val end = if (aOut > 0) aOut else (a.durationMs - PlayerSettings.crossfadeSec * 1000L)
        if (end > 0) player.seekTo((end - lead).coerceAtLeast(0L))
        player.play()
    }

    /**
     * Из окна микса: первым выбран другой трек [a]. Есть второй — играет [a] и переходит на него с наложением;
     * нет — просто играет [a], а второй выбирается в окне.
     */
    fun mixReplaceFirst(a: Track) {
        val b = MixSession.b?.takeIf { it.id != a.id }
        if (b != null) { mixTracks(a, b); return }
        ensureService()
        play(listOf(a), 0)
        MixSession.startFirst(a)
    }

    /**
     * Из окна микса в плеере: [b] становится следующим после играющего трека (если ещё не следующий), а переход на него
     * делается с наложением, даже когда микс выключен в настройках. Играющий трек не прерывается.
     */
    fun mixWithNext(b: Track) {
        val a = _state.value.nowPlaying ?: return
        ensureService()
        if (!Net.isOnline(app) && !b.isLocal && !app.downloads.isDownloaded(b.id)) {
            _state.update { it.copy(error = "Нет сети, а этот трек не скачан") }
            return
        }
        if (player.mediaItemCount == 0 || queue.isEmpty()) { mixTracks(a, b); return }
        val nextId = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex).mediaId else null
        if (nextId != b.id) {
            val idx = (player.currentMediaItemIndex.coerceAtLeast(0) + 1).coerceAtMost(queue.size)
            queue.add(idx, b)
            player.addMediaItem(idx, toMediaItem(b))
            persistQueue()
        }
        app.crossfade.forceNextTransitionFrom(a.id)
        MixSession.start(a, b)
        applyMixPoints()
        _state.update { it.copy(message = "Микс: «${a.title}» → «${b.title}»") }
    }

    /** Поисковики ручного поиска по включённым в настройках источникам. */
    fun lyricsSearchers(): List<app.ymusic.m3e.data.LyricsSearcher> =
        app.lyrics.searchers.filter { it.id !in PlayerSettings.lyricsOff }

    /**
     * Применяет вариант из ручного поиска к треку [t]: подгружает текст и показывает его.
     * [pin] — ещё и привязать к треку (запомнится между запусками). [onDone] получает id источника для переключателя или null при ошибке.
     */
    fun applyLyricsCandidate(t: Track, c: app.ymusic.m3e.data.LyricsCandidate, pin: Boolean, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val loaded = runCatching { c.load() }.getOrNull()?.takeIf { it.hasText }
            if (loaded == null) {
                _state.update { it.copy(message = "Не удалось загрузить текст из ${c.sourceName}") }
                onDone(null)
                return@launch
            }
            val id = if (pin) "pin" else "manual"
            val src = loaded.copy(id = id, name = c.sourceName)
            if (pin) app.lyrics.pin(t, src)
            _lyrics.update {
                val base = if (it.trackId == t.id) it else LyricsState(trackId = t.id, done = true)
                base.copy(sources = listOf(src) + base.sources.filter { s -> s.id != "manual" && !(pin && s.id == "pin") })
            }
            onDone(id)
        }
    }

    /** Убирает привязку текста к треку: дальше текст ищется автоматически. */
    fun unpinLyrics(t: Track) {
        viewModelScope.launch {
            app.lyrics.unpin(t)
            _lyrics.update { if (it.trackId == t.id) it.copy(sources = it.sources.filter { s -> s.id != "pin" }) else it }
        }
    }

    /** Повторный поиск текста: сбрасывает запомненные промахи и опрашивает все источники заново. */
    fun reloadLyrics(t: Track) {
        lyricsJob?.cancel()
        app.lyrics.forgetMisses(t)
        _lyrics.value = LyricsState()
        loadLyrics(t)
    }

    /** Соседний трек очереди (следующий/предыдущий с учётом перемешивания и повтора) — для свайпа по обложке. */
    fun neighborTrack(next: Boolean): Track? {
        val idx = if (next) player.nextMediaItemIndex else player.previousMediaItemIndex
        if (idx == C.INDEX_UNSET || idx < 0 || idx >= player.mediaItemCount) return null
        val id = player.getMediaItemAt(idx).mediaId
        return queue.firstOrNull { it.id == id }
    }

    /** Ближайшие [count] треков очереди после текущего (с учётом перемешивания и повтора) — для предзагрузки обложек. */
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

    /** Лёгкое сохранение «где остановились»: индекс, позиция, режим (список или волна). */
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
        // Очередь сохраняем отдельно — только когда она менялась (см. persistQueue).
    }

    /** С какого элемента очереди начинается сохранённый кусок (длинные списки сохраняем окном вокруг текущего трека). */
    private var queueOffset = 0

    /** Тяжёлое сохранение: очередь (до 600 треков вокруг текущего). Вызывается при запуске списка и догрузке волны. */
    private fun persistQueue() {
        val cur = player.currentMediaItemIndex.coerceAtLeast(0)
        val from = (cur - 100).coerceAtLeast(0).coerceAtMost((queue.size - 1).coerceAtLeast(0))
        val to = minOf(queue.size, from + 600)
        queueOffset = from
        store.saveQueue(ownerKey(), queue.subList(from, to).toList())
        persistPosition()
    }

    /**
     * После перезапуска приложения возвращаем последний трек в мини-плеер: очередь и позиция восстанавливаются,
     * но воспроизведение не начинается и сеть не трогается, пока человек не нажмёт play. Если слушали «Мою волну»,
     * она продолжится как волна (треки подгружаются), если плейлист — как этот же плейлист.
     */
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

    /** Запомнить, где остановились (вызывается, когда приложение уходит в фон). */
    fun saveSession() = persistPosition()

    // Лайки из шторки уведомлений идут сюда, пока жив ViewModel.
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

    // ---- Лайк / дизлайк ----
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
        if (!api.caps.dislikes) {
            _state.update { it.copy(message = "В VK Музыке нет дизлайков") }
            return
        }
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
        // Как в Яндекс Музыке: дизлайк играющего трека пропускает его.
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
        // Играет колонка: состояние кнопки берём у неё (см. syncRemote).
        if (connect.isRemoteActive()) return
        val st = player.playbackState
        val on = player.playWhenReady && (st == Player.STATE_READY || st == Player.STATE_BUFFERING)
        val buf = player.playWhenReady && st == Player.STATE_BUFFERING
        _state.update { if (it.isPlaying == on && it.isBuffering == buf) it else it.copy(isPlaying = on, isBuffering = buf) }
    }

    // Следим за сетью: пропала — «офлайн»; вернулась и интернет реально есть (VALIDATED) — сами обновляем всё.
    // Раньше обновление шло в момент onAvailable, когда DNS ещё не работает: запрос падал, и приложение оставалось офлайн.
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

    /** Сеть появилась: снимаем «офлайн», заставляем обложки загрузиться заново и подтягиваем данные с повторами. */
    private fun onBackOnline() {
        if (!_state.value.loggedIn) return
        if (reconnectJob?.isActive == true) return
        _state.update { it.copy(offline = false, netEpoch = it.netEpoch + 1) }
        startReconnect()
    }

    /** Приложение вернулось на экран: если мы всё ещё «офлайн», а сеть есть — пробуем ещё раз. */
    fun retryIfOffline() {
        if (_state.value.loggedIn && _state.value.offline && Net.isOnline(app)) onBackOnline()
    }

    private fun startReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch { reloadAfterReconnect() }
    }

    /** Грузит данные аккаунта до успеха (с нарастающей паузой), затем обновляет открытые страницы. */
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
                // Данные пришли: ещё раз даём обложкам шанс загрузиться (первая попытка могла упасть до готовности сети).
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
        // Сервер так и не ответил: остаёмся на сохранённых списках.
        _state.update { it.copy(offline = true) }
    }

    /** После возвращения сети обновляет то, что открыто на экране: плейлист, альбом, исполнителя, чарт. */
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

    // ---- Скачивание песен ----
    val downloads: StateFlow<DownloadsState> = app.downloads.state

    fun downloadTrack(t: Track, mode: DownloadMode? = null) {
        if (t.isLocal) return
        if (t.isDownloadBlocked()) {
            _state.update { it.copy(message = ScDownloads.FORBIDDEN) }
            return
        }
        app.downloads.download(t, mode) { r ->
            _state.update {
                if (r.isSuccess) it.copy(message = "Скачано: ${t.title}")
                else if (r.exceptionOrNull()?.message == ScDownloads.FORBIDDEN) it.copy(message = ScDownloads.FORBIDDEN)
                else it.copy(error = "Не удалось скачать: ${r.exceptionOrNull()?.message ?: "ошибка"}")
            }
        }
    }

    /** Скачать трек (куда — по настройке) или удалить уже скачанный. */
    fun toggleDownload(t: Track) {
        if (app.downloads.state.value.items.any { it.id == t.id }) removeDownload(t) else downloadTrack(t)
    }

    fun removeDownload(t: Track) {
        app.downloads.remove(t.id)
        _state.update { it.copy(message = "Удалено из скачанных") }
    }

    /** Скачать все ещё не скачанные треки из списка («Любимое», плейлист или альбом) в выбранный режим. */
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

    /** Включён ли автоскачивание для списка ([DownloadsState.auto]); по умолчанию выключено. */
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

    /** Следит за «Любимым» и открытым плейлистом: новые треки в списках с включённым автоскачиванием качаются сами. */
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
    /** Спрашивать ли «Куда сохранять?» при скачивании целого списка. */
    fun setAskDownloadMode(ask: Boolean) = app.downloads.setAskMode(ask)
    /** «Не показывать снова»: [m] становится способом по умолчанию, окно больше не появляется. */
    fun setDownloadModeAndStopAsking(m: DownloadMode) = app.downloads.setModeAndStopAsking(m)

    /** Подробности о треке для окна «Информация о треке» (null — не удалось получить, например нет сети). */
    suspend fun fetchTrackInfo(t: Track): TrackInfo? =
        withContext(Dispatchers.IO) { runCatching { api.trackInfo(t) }.getOrNull() }

    fun deleteDownload(id: String) = app.downloads.remove(id)
    fun deleteAllDownloads() { app.downloads.removeAll(); _state.update { it.copy(message = "Все скачанные песни удалены") } }
    fun setDownloadDir(uri: Uri) = app.downloads.setTree(uri)
    fun resetDownloadDir() = app.downloads.resetTree()

    // ---- Эквалайзер ----
    val eqState: StateFlow<EqState> = app.equalizer.state
    fun setEqEnabled(on: Boolean) = app.equalizer.setEnabled(on)
    fun setEqPreset(id: String) = app.equalizer.setPreset(id)
    fun setEqBand(index: Int, db: Float) = app.equalizer.setBand(index, db)

    init {
        startAutoDownloadWatcher()
        player.addListener(object : Player.Listener {
            // «Играет» = пользователь хочет слушать и плеер не закончил/не упал. Во время буферизации
            // и смены трека значок и обложка не «моргают» (раньше isPlaying на миг становился false).
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
                // Очередь изменилась (новый список, волна подгрузила треки, трек добавили) — обновляем предзагрузку.
                schedulePreload()
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                schedulePreload()
                val id = item?.mediaId
                // Не обнуляем nowPlaying, если трек временно не нашёлся в очереди (смена очереди, пустой переход):
                // иначе мини-плеер на миг исчезал и «выпрыгивал» заново — анимация зацикливалась.
                val found = queue.firstOrNull { it.id == id }
                if (found != null) _state.update { s -> if (s.nowPlaying?.id == found.id) s else s.copy(nowPlaying = found) }
                // «Прослушанным» считаем трек, который реально играет (при восстановлении сессии пауза — не записываем).
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
                // Очередь волны кончилась — не замираем на последнем треке, а подгружаем продолжение и играем дальше.
                if (playbackState == Player.STATE_ENDED && waveActive) loadMoreWave(advance = true)
                if (playbackState != Player.STATE_READY) return
                val t = queue.firstOrNull { it.id == player.currentMediaItem?.mediaId } ?: return
                val dur = player.duration
                // Если реальная длина потока сильно короче трека — сервер отдаёт только превью.
                if (!t.isLocal && dur > 0 && t.durationMs > 0 && dur < t.durationMs * 0.7 && warnedPreviewFor != t.id) {
                    warnedPreviewFor = t.id
                    previewIds.add(t.id)
                    // Без Плюса Яндекс отдаёт только 30-секундные фрагменты — говорим об этом прямо (один раз за запуск).
                    val acc = _state.value.account
                    val noPlus = acc != null && acc.provider == Provider.YANDEX && !acc.hasPlus
                    if (noPlus) {
                        if (!warnedNoPlus) {
                            warnedNoPlus = true
                            _state.update { it.copy(error = "Без Яндекс Плюса треки и подкасты играют только по 30 секунд. С подпиской они будут звучать целиком.") }
                        }
                    } else if (ScIds.isSc(t.id) && player.hasNextMediaItem()) {
                        // SoundCloud: полной версии не нашлось, а Go+-трек играл бы только 30 секунд — идём дальше.
                        _state.update { it.copy(message = "«${t.title}» у SoundCloud доступен только как 30-секундный фрагмент (нужен Go+) — пропущен") }
                        player.seekToNextMediaItem()
                        player.prepare()
                        player.play()
                    } else {
                        _state.update {
                            it.copy(error = "Сервер отдаёт только фрагмент трека (${dur / 1000} с из ${t.durationMs / 1000} с). Аккаунт не даёт полный доступ к этому треку.")
                        }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Ссылка могла протухнуть — при повторной попытке запросим новую. Ошибку мог вызвать и следующий трек
                // (его открывает предзагрузка плеера), поэтому ссылку забываем и у него.
                player.currentMediaItem?.mediaId?.let { TrackPreloader.forget(it) }
                // VK: поток не того вида (файл вместо HLS и наоборот) — перезапускаем с верным типом источника.
                player.currentMediaItem?.let { if (retypeVkItem(it, error)) { syncPlaying(); return } }
                player.nextMediaItemIndex.takeIf { it != androidx.media3.common.C.INDEX_UNSET }
                    ?.let { TrackPreloader.forget(player.getMediaItemAt(it).mediaId) }
                // 429 (SoundCloud ограничил запросы): не перескакиваем по очереди — это только расходует лимит.
                // Ждём и повторяем тот же трек, а после двух неудач сообщаем и останавливаемся.
                val curId = player.currentMediaItem?.mediaId
                if (curId != null && rateLimitCode(error) == 429) {
                    if (rateLimitedId != curId) { rateLimitedId = curId; rateLimitRetries = 0 }
                    if (rateLimitRetries < 2) {
                        rateLimitRetries++
                        val waitMs = (rateLimitRetries * 3000L).coerceAtLeast(retryAfterMs(error))
                        rateLimitJob?.cancel()
                        rateLimitJob = viewModelScope.launch {
                            kotlinx.coroutines.delay(waitMs)
                            if (player.currentMediaItem?.mediaId == curId) { player.prepare(); player.play() }
                        }
                    } else {
                        rateLimitedId = null
                        _state.update { it.copy(error = "SoundCloud временно ограничил число запросов (429). Подождите немного и запустите трек снова") }
                    }
                    syncPlaying()
                    return
                }
                // 401/403/410: ссылка на поток устарела. Не пропускаем трек, а повторяем его с того же места по новой ссылке
                // (не больше двух раз за минуту, чтобы не зациклиться, если доступ закрыт по-настоящему).
                val httpCode = rateLimitCode(error)
                if (curId != null && (httpCode == 401 || httpCode == 403 || httpCode == 410)) {
                    val now = System.currentTimeMillis()
                    if (expiredRetryId != curId || now - expiredRetryAt > 60_000L) { expiredRetryId = curId; expiredRetries = 0 }
                    if (expiredRetries < 2) {
                        expiredRetries++
                        expiredRetryAt = now
                        AppLog.w("Player", "HTTP $httpCode: ссылка устарела, повтор ($expiredRetries/2) с позиции ${player.currentPosition} мс")
                        player.prepare()
                        player.play()
                        syncPlaying()
                        return
                    }
                }
                _state.update { it.copy(error = "Не удалось воспроизвести: ${error.errorCodeName}") }
                // Трек не загрузился (ссылка протухла, пропала сеть) — не застреваем: идём к следующему.
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
        // Lua-плагины (подключаются после слушателя выше, чтобы nowPlaying к их событиям уже был обновлён): события плеера и команды из скриптов (если система плагинов включена).
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
                // Без Плюса все треки и подкасты Яндекса обрезаются до 30 секунд: дошли до отметки — идём к следующему.
                if (noPlusLimited(_state.value.nowPlaying)) {
                    if (!remote) {
                        if (player.isPlaying && player.currentPosition >= NO_PLUS_LIMIT_MS) cutNoPlusTrack()
                    } else if (!connect.state.value.paused && connect.estimatedProgress() >= NO_PLUS_LIMIT_MS &&
                        System.currentTimeMillis() - lastNoPlusCutAt > 3000
                    ) {
                        // Колонка / устройство Яндекса: тоже пропускаем трек на 30-й секунде (пауза 3 с, пока колонка подтвердит смену).
                        lastNoPlusCutAt = System.currentTimeMillis()
                        cutNoPlusRemote()
                    }
                }
                _state.update {
                    val d = player.duration.coerceAtLeast(0)
                    // Пока плеер не подготовлен (восстановленная сессия) — длительность берём из данных трека.
                    val full = if (d > 0) d else (it.nowPlaying?.durationMs ?: 0L)
                    // Шкала перемотки без Плюса тоже 30 секунд, чтобы она совпадала с тем, что реально играет.
                    val dur = if (noPlusLimited(it.nowPlaying) && full > 0) minOf(full, NO_PLUS_LIMIT_MS) else full
                    // Состояние меняем только при смене длительности — иначе лишние перерисовки всех экранов.
                    if (it.durationMs == dur) it else it.copy(durationMs = dur)
                }
                // Раз в ~5 секунд запоминаем позицию, чтобы после закрытия приложения продолжить с того же места.
                if (++tick % 10 == 0 && player.isPlaying) persistPosition()
                delay(500)
            }
        }
        // Лайки/дизлайки для шторки уведомлений: сервис знает состояние, даже когда экран закрыт.
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
        // Интерфейс мог быть закрыт, пока плеер играл (виджет, уведомление): восстанавливаем очередь из плеера.
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
                    history = localHistory(), waveHistory = store.loadWave(ownerKey()),
                    sessionHistory = log.tracks, sessionLabel = log.label,
                )
            }
            refresh()
        } else if (LocalModeSettings.enabled) {
            loadLocalHistory()
        }
    }

    /** Режим «Локальные файлы» включён: подгружаем историю прослушанных файлов. */
    fun loadLocalHistory() {
        if (_state.value.loggedIn) return
        _state.update { it.copy(history = store.loadHistory(LOCAL_OWNER)) }
    }

    // ---- Авторизация ----

    /** Состояние «после входа/смены аккаунта»: всё очищено, кроме списка аккаунтов и своих обложек. */
    private fun freshState(loggedIn: Boolean) = UiState(
        loggedIn = loggedIn,
        accounts = app.tokenStore.accounts,
        activeToken = app.tokenStore.token,
        history = if (loggedIn) localHistory() else emptyList(),
    )

    /** Останавливает плеер и сбрасывает всё, что относится к прежнему аккаунту. */
    private fun resetSession() {
        // Смена аккаунта: колонку отпускаем и закрываем сессию — у другого аккаунта свои устройства.
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
        app.tokenStore.token = token // добавляет аккаунт и делает его активным
        if (prev != null && prev != token) resetSession()
        _state.value = freshState(loggedIn = true)
        refresh()
    }

    // ---- Вход в VK ----

    private val _vkLogin = MutableStateFlow(VkLoginUi())
    val vkLogin: StateFlow<VkLoginUi> = _vkLogin

    /** Сбросить состояние входа (при закрытии экрана входа). */
    fun vkResetLogin() {
        _vkLogin.value = VkLoginUi()
    }

    /**
     * Страница входа VK в WebView вернула адрес редиректа с access_token (или сам токен): токен проверяется
     * (подбирается клиент, проверяется доступ к музыке), потом добавляется аккаунт.
     */
    fun vkSubmitToken(text: String) {
        val token = VkAuth.parseToken(text)
        if (token == null) {
            _vkLogin.update { it.copy(error = "VK не вернул токен. Попробуйте войти ещё раз") }
            return
        }
        if (_vkLogin.value.busy) return
        _vkLogin.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val info = runCatching { withContext(Dispatchers.IO) { VkAuth.checkToken(token) } }
            val ok = info.getOrNull()
            if (ok == null) {
                val e = info.exceptionOrNull()
                if (e is CancellationException) throw e
                AppLog.e("VK", "Вход: токен не принят", e)
                _vkLogin.update {
                    it.copy(busy = false, error = if (e != null) VkAuth.friendlyError(e) else "VK не принял этот токен: у него нет доступа к музыке. Войдите ещё раз")
                }
                return@launch
            }
            AppLog.i("VK", "Вход выполнен: uid=${ok.userId}, клиент=${ok.client.id}")
            vkResetLogin()
            onVkTokenReceived(ok.token, ok.client)
        }
    }

    fun onVkTokenReceived(token: String, client: VkClient) {
        if (token.isBlank()) return
        val prev = app.tokenStore.token
        app.tokenStore.addVk(token, client)
        if (prev != null && prev != token) resetSession()
        _state.value = freshState(loggedIn = true)
        refresh()
    }

    // ---- Вход в SoundCloud ----

    private val _scLogin = MutableStateFlow(ScLoginUi())
    val scLogin: StateFlow<ScLoginUi> = _scLogin

    /** Начинает вход: возвращает адрес страницы SoundCloud, которую нужно открыть в браузере. */
    fun scBeginLogin(): String {
        _scLogin.value = ScLoginUi(waiting = true)
        return SoundCloudAuth.beginLogin(app)
    }

    /** Закрыли страницу входа SoundCloud: забываем начатый вход. */
    fun scResetLogin() {
        SoundCloudAuth.cancelLogin(app)
        _scLogin.value = ScLoginUi()
    }

    private fun onSoundCloudRedirect(data: Uri?) {
        when (val r = SoundCloudAuth.parseRedirect(app, data)) {
            null -> return
            is SoundCloudAuth.Redirect.Failed -> _scLogin.value = ScLoginUi(error = r.message)
            is SoundCloudAuth.Redirect.Code -> {
                _scLogin.value = ScLoginUi(busy = true)
                viewModelScope.launch {
                    val res = runCatching {
                        withContext(Dispatchers.IO) {
                            val tokens = SoundCloudAuth.exchangeCode(r.code, r.verifier)
                            tokens to SoundCloudAuth.fetchMe(tokens.access)
                        }
                    }
                    val ok = res.getOrNull()
                    if (ok == null) {
                        val e = res.exceptionOrNull()
                        if (e is CancellationException) throw e
                        _scLogin.value = ScLoginUi(error = e?.let { SoundCloudAuth.friendlyError(it) } ?: "Не удалось войти в SoundCloud")
                        return@launch
                    }
                    _scLogin.value = ScLoginUi()
                    onSoundCloudLoggedIn(ok.first, ok.second)
                }
            }
        }
    }

    private fun onSoundCloudLoggedIn(tokens: ScTokens, profile: ScProfile) {
        val key = SoundCloudAuth.accountKey(profile.id)
        val prev = app.tokenStore.token
        app.tokenStore.addSoundCloud(key, tokens, profile)
        if (prev != null && prev != key) resetSession()
        _state.value = freshState(loggedIn = true)
        refresh()
    }

    // ---- Вход в Spotify ----

    private val _spLogin = MutableStateFlow(SpLoginUi())
    val spLogin: StateFlow<SpLoginUi> = _spLogin

    /** Начало входа: куки Spotify в WebView стираются, чтобы страница входа была чистой (и можно было выбрать другой аккаунт). */
    fun spBeginLogin() {
        runCatching { SpotifyAuth.clearWebCookies() }
        _spLogin.value = SpLoginUi()
    }

    fun spResetLogin() { _spLogin.value = SpLoginUi() }

    /**
     * Страница Spotify в WebView вошла: [cookies] — куки сессии, [token] — токен веб-плеера, если страница его отдала.
     * Профиль берётся сразу; если Spotify временно не ответил, аккаунт всё равно сохраняется, а профиль подтянется позже.
     */
    fun spSubmitSession(cookies: String, token: String?) {
        if (_spLogin.value.busy) return
        if (!SpotifyAuth.hasSession(cookies)) {
            _spLogin.value = SpLoginUi(error = "Spotify не выдал сессию. Попробуйте войти ещё раз")
            return
        }
        _spLogin.value = SpLoginUi(busy = true)
        viewModelScope.launch {
            val res = runCatching { withContext(Dispatchers.IO) { SpotifyAuth.fetchProfile(cookies, token) } }
            val e = res.exceptionOrNull()
            if (e is CancellationException) throw e
            // Сессия недействительна — это ошибка входа; прочее (сеть, смена страницы Spotify) — не повод терять вход.
            if (e != null && e.message?.startsWith("HTTP 401") == true) {
                _spLogin.value = SpLoginUi(error = SpotifyAuth.friendlyError(e))
                return@launch
            }
            _spLogin.value = SpLoginUi()
            onSpotifyLoggedIn(cookies, res.getOrNull())
            if (e != null) _state.update { it.copy(message = "Вход в Spotify выполнен, профиль загрузится позже") }
        }
    }

    private fun onSpotifyLoggedIn(cookies: String, profile: SpProfile?) {
        val key = SpotifyAuth.accountKey(cookies)
        val prev = app.tokenStore.token
        app.tokenStore.addSpotify(key, cookies, profile)
        if (prev != null && prev != key) resetSession()
        _state.value = freshState(loggedIn = true)
        refresh()
    }

    // ---- VK Микс и «Для вас» ----

    fun loadShelves(force: Boolean = false) {
        val st = _state.value
        if (!api.caps.recommendations || st.shelvesLoading || !st.loggedIn || (!force && st.shelves.isNotEmpty())) return
        val tok = app.tokenStore.token
        _state.update { it.copy(shelvesLoading = true) }
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { api.recommendedShelves() } }
            if (tok != app.tokenStore.token) return@launch
            _state.update { it.copy(shelves = r.getOrDefault(it.shelves), shelvesLoading = false) }
        }
    }

    fun loadMixSettings() {
        if (!api.caps.mixSettings || _state.value.mixSettingsLoading) return
        _state.update { it.copy(mixSettingsLoading = true) }
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { api.mixSettings() } }
            _state.update {
                it.copy(mixSettings = r.getOrNull() ?: it.mixSettings, mixSettingsLoading = false,
                    error = r.exceptionOrNull()?.takeIf { e -> e !is CancellationException }?.message ?: it.error)
            }
        }
    }

    /** Сохраняет выбор в VK Миксе ([chosen]: категория → выбранные варианты) и перезапускает микс, если он играет. */
    fun saveMixSelection(chosen: Map<String, Set<String>>) {
        val o = org.json.JSONObject().put("id", System.currentTimeMillis())
        chosen.forEach { (cat, ids) -> if (ids.isNotEmpty()) o.put(cat, org.json.JSONArray(ids.toList())) }
        val cats = _state.value.mixSettings?.categories.orEmpty().map { c ->
            c.copy(options = c.options.map { it.copy(selected = it.id in (chosen[c.id] ?: emptySet())) })
        }
        _state.update { s -> s.copy(mixSettings = s.mixSettings?.copy(categories = cats)) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.saveMixOptions(o.toString()) } }
            if (waveActive && waveStationId == DEFAULT_WAVE) startWave()
        }
    }

    fun onAuthRedirect(data: Uri?) {
        // Возврат из входа SoundCloud (ymusicm3e://soundcloud): принимаем, только если вход начат в приложении.
        if (data?.host == "soundcloud") { onSoundCloudRedirect(data); return }
        // Ссылка ymusicm3e:// доступна любому приложению: токен принимаем только пока открыт экран входа.
        val st = _state.value
        if (st.loggedIn && !st.addingAccount) return
        val token = YandexAuth.parseToken(data?.toString()) ?: return
        onTokenReceived(token)
    }

    /** Открыть экран входа, чтобы добавить ещё один аккаунт. */
    fun startAddAccount(start: String? = null) = _state.update { it.copy(addingAccount = true, addStart = start, error = null) }

    fun cancelAddAccount() = _state.update { it.copy(addingAccount = false, addStart = null, error = null) }

    /** Переключиться на другой сохранённый аккаунт. */
    fun switchAccount(token: String) {
        if (token == app.tokenStore.token) return
        if (app.tokenStore.accounts.none { it.token == token }) return
        resetSession()
        app.tokenStore.setActive(token)
        _state.value = freshState(loggedIn = true)
        refresh()
    }

    /**
     * Выйти из аккаунта с этим токеном. Если это активный аккаунт и есть другие — произойдёт переключение
     * на следующий; если аккаунтов не осталось — вернётся экран входа. Возвращает true, если после выхода
     * приложение осталось в системе (на другом аккаунте).
     */
    fun logoutAccount(token: String): Boolean {
        // Выход из Spotify: сессия живёт ещё и в куках WebView — без их очистки следующий вход пройдёт сам, в тот же аккаунт.
        if (app.tokenStore.accounts.any { it.token == token && it.provider == Provider.SPOTIFY }) runCatching { SpotifyAuth.clearWebCookies() }
        val wasActive = token == app.tokenStore.token
        if (!wasActive) {
            app.tokenStore.remove(token)
            _state.update { it.copy(accounts = app.tokenStore.accounts, activeToken = app.tokenStore.token) }
            return true
        }
        resetSession()
        app.tokenStore.remove(token) // активным станет следующий аккаунт, если он есть
        val hasNext = app.tokenStore.token != null
        _state.value = freshState(loggedIn = hasNext)
        if (hasNext) refresh()
        return hasNext
    }

    /** Выйти из текущего аккаунта (при 401 и по кнопке «Выйти из аккаунта»). */
    fun logout(): Boolean = app.tokenStore.token?.let { logoutAccount(it) } ?: false

    /** Выйти из всех аккаунтов. */
    fun logoutAll() {
        if (app.tokenStore.accounts.any { it.provider == Provider.SPOTIFY }) runCatching { SpotifyAuth.clearWebCookies() }
        resetSession()
        app.tokenStore.clear()
        _state.value = freshState(loggedIn = false)
    }

    // ---- Данные ----

    /** Профиль, «Любимое», плейлисты и альбомы коллекции одним заходом. Бросает исключение, если сервер не ответил. */
    private suspend fun fetchLibrary(): Library = withContext(Dispatchers.IO) {
        val acc = api.account()
        // Пользователь не определён — лайки/плейлисты могут быть недоступны, это не ошибка входа.
        val unknownUser = acc.uid == 0L && acc.login.isBlank()
        val liked = runCatching { api.likedTracks(acc.apiId) }
            .getOrElse { if (unknownUser) emptyList() else throw it }
        val lists = runCatching { api.playlists(acc.apiId) }
            .getOrElse { if (unknownUser) emptyList() else throw it }
        // Альбомы — не критично: если не загрузились, остаётся прежний список.
        val albums = if (unknownUser) emptyList() else runCatching { api.likedAlbums(acc.apiId) }.getOrNull()
        Library(acc, liked, lists, albums)
    }

    /** Применяет загруженное и сохраняет в офлайн-кэш. Чужие данные (после смены аккаунта) не применяются. */
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

    /** Докачивает в кэш треки плейлистов, которые ещё ни разу не открывали: без сети они откроются целиком. */
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
        // Без аккаунта (режим «Локальные файлы») обновлять нечего, кроме списка файлов.
        if (!_state.value.loggedIn) { reloadLocalTracks(); return }
        val tok = app.tokenStore.token
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // Сети нет — не ждём таймаутов, сразу показываем сохранённое.
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

    /**
     * Обновление свайпом сверху вниз: перезагружает данные аккаунта (лайки, плейлисты) и всё, что сейчас
     * открыто — плейлист, исполнителя, альбом, файлы с устройства, поиск ([searchQuery], если не пустой).
     * Не трогает флаг [UiState.loading], поэтому глобальный индикатор сверху не дублируется.
     */
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

    // ---- Работа без интернета ----

    private fun storedAccount(): Account? =
        app.tokenStore.accounts.firstOrNull { it.token == app.tokenStore.token }
            ?.let { Account(it.uid, it.login, it.name, it.hasPlus, it.avatarUrl, it.provider) }

    /**
     * Сети нет (или сервер недоступен): показываем сохранённые «Любимое» и плейлисты, а играть можно скачанное.
     * Возвращает true, если ошибка — сетевая и списки удалось показать (тогда обычную ошибку не выводим).
     */
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
        // Сеть по данным системы есть, но сервер не ответил (сеть только что включилась, плохой сигнал): пробуем снова сами.
        if (Net.isOnline(app)) startReconnect()
        return true
    }

    private fun cachedPlaylist(p: PlaylistInfo): List<Track>? =
        ownerKey()?.let { app.offlineLibrary.load(it).playlistTracks[p.coverKey()] }

    private fun cachedAlbum(id: String): AlbumInfo? =
        ownerKey()?.let { app.offlineLibrary.load(it).albums[id] }

    /** Альбом без сети: сохранённый целиком или, если его не открывали, собранный из скачанных треков. */
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
    /** Запрос, который уже летит на сервер: тот же текст второй раз не отправляем (например, «Enter» после живого поиска). */
    private var inFlightQuery: String? = null

    /**
     * Поиск по Яндекс Музыке. [live] — поиск по ходу ввода: незавершённый предыдущий запрос отменяется,
     * а сетевые ошибки не показываются всплывашкой (пока человек печатает, это только мешало бы).
     */
    fun search(query: String, live: Boolean = false) {
        if (query.isBlank()) return
        // Вставили ссылку на Яндекс Музыку — открываем её страницу, а не ищем текст ссылки.
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
                // Более новый запрос сам управляет индикатором.
                if (seq == searchSeq) {
                    inFlightQuery = null
                    _state.update { it.copy(searchLoading = false) }
                }
            }
        }
    }

    /** Очищает результаты поиска (поле ввода опустело). */
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

    // ---- Чарт ----

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

    // ---- Новинки ----

    /** Свежим считается релиз не старше этого срока. */
    private val newReleaseDays = 120L

    /**
     * Артисты, которых слушают чаще всего: по истории (в том числе серверной) и «Любимому», история весит вдвое больше.
     * Каждый трек засчитывается всем его исполнителям; возвращаются id самых частых.
     */
    private fun topArtistIds(listened: List<Track>, liked: List<Track>, limit: Int = 12): List<String> {
        val score = HashMap<String, Int>()
        listened.forEach { t -> t.artistRefs.forEach { r -> score[r.id] = (score[r.id] ?: 0) + 2 } }
        liked.forEach { t -> t.artistRefs.forEach { r -> score[r.id] = (score[r.id] ?: 0) + 1 } }
        return score.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /**
     * Загружает новинки: берёт любимых артистов, у каждого смотрит альбомы за последние [newReleaseDays] дней,
     * затем подтягивает треки самых свежих альбомов. Запросы идут параллельно.
     */
    fun loadNewReleases(force: Boolean = false) {
        val st = _state.value
        if (st.newReleasesLoading || !st.loggedIn || (!force && st.newReleases.isNotEmpty())) return
        val tok = app.tokenStore.token
        _state.update { it.copy(newReleasesLoading = true, newReleasesFailed = false) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    // Сервис сам отдаёт новинки (VK) — считать по любимым артистам не нужно.
                    // Пустой ответ сервиса не считается: тогда новинки считаются по любимым артистам.
                    api.nativeNewReleases()?.takeIf { it.albums.isNotEmpty() || it.tracks.isNotEmpty() }
                        ?.let { return@withContext it.albums to it.tracks }
                    val s0 = _state.value
                    var listened = (s0.history + s0.syncedHistory + s0.waveHistory).distinctBy { it.id }
                    // Серверная история ещё не подтянулась — берём её сами, чтобы выбор артистов был точным.
                    if (s0.syncedHistory.isEmpty()) {
                        listened = (listened + runCatching { api.musicHistoryFull(150).tracks }.getOrDefault(emptyList())).distinctBy { it.id }
                    }
                    val ids = topArtistIds(listened, s0.liked)
                    val cutoffDate = java.time.LocalDate.now().minusDays(newReleaseDays)
                    val cutoff = cutoffDate.toString()
                    val found = coroutineScope {
                        ids.map { id -> async { runCatching { api.artistRecentAlbums(id) }.getOrDefault(emptyList()) } }.awaitAll()
                    }.flatten()
                    AppLog.i("Новинки", "артистов ${ids.size}, найдено альбомов ${found.size}, с точной датой ${found.count { it.releaseDate.isNotBlank() }}, с годом ${found.count { it.year > 0 }}")
                    val albums = found
                        // Есть точная дата выхода — сравниваем с ней; у VK дата не приходит, только год — тогда берём релизы этого года и новее.
                        .filter { a -> if (a.releaseDate.isNotBlank()) a.sortDate >= cutoff else a.year >= cutoffDate.year && a.year > 0 }
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

    // ---- История прослушивания (с сервера) ----

    /** Загружает историю с аккаунта Яндекса и добавляет к ней локальную (чтобы только что прослушанное было сверху). */
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
                    syncedHistory = mergeHistory(it.history + localHistory(), remote?.tracks.orEmpty()),
                    syncedWave = remote?.waveTracks.orEmpty().ifEmpty { it.syncedWave },
                    syncedHistoryLoading = false,
                )
            }
        }
    }

    /** Локальные (новые) треки сверху, затем серверные без повторов. */
    private fun mergeHistory(local: List<Track>, remote: List<Track>): List<Track> {
        val seen = HashSet<String>()
        return (local + remote).filter { seen.add(it.id) }.take(historyLimit)
    }

    // ---- Подкасты ----

    private var podcastJob: Job? = null

    /** Поиск на странице «Подкасты»; пустой запрос возвращает подборку. */
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

    // ---- Файлы с устройства ----

    private var localSearchJob: Job? = null

    /** Читает музыку с устройства (если выдан доступ к аудио). */
    fun loadLocalTracks() {
        if (!LocalMusic.hasPermission(app) || _state.value.localLoading) return
        _state.update { it.copy(localLoading = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadLocalWithLibrary() } }
                .onSuccess { (r, lib) -> _state.update { it.copy(localTracks = r, localLib = lib, localLoading = false) } }
                .onFailure { e -> _state.update { it.copy(localLoading = false, error = e.message ?: "Не удалось прочитать файлы с устройства") } }
        }
    }

    /** Файлы устройства и собранные из них альбомы с исполнителями. Вызывать не из главного потока. */
    private fun loadLocalWithLibrary(): Pair<List<Track>, LocalLibrary> {
        val tracks = LocalMusic.load(app, withRefs = LocalModeSettings.enabled)
        return tracks to LocalLibrary.build(tracks)
    }

    /** Обновление свайпом в режиме «Локальные файлы»: заново читает файлы устройства. */
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

    /** Ищет по файлам с устройства (название, исполнитель, альбом). Пустой запрос очищает группу. */
    fun searchLocal(query: String) {
        localSearchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty() || !LocalFilesSettings.enabled || !LocalMusic.hasPermission(app)) {
            _state.update { if (it.searchLocal.isEmpty()) it else it.copy(searchLocal = emptyList()) }
            return
        }
        localSearchJob = viewModelScope.launch {
            // Чтение MediaStore бросает SecurityException, если доступ к аудио отозвали между проверкой и запросом.
            val found = try {
                withContext(Dispatchers.IO) {
                    LocalMusic.load(app).filter {
                        it.title.contains(q, ignoreCase = true) ||
                            it.artists.contains(q, ignoreCase = true) ||
                            (it.albumTitle?.contains(q, ignoreCase = true) == true)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // отменённый предыдущий поиск не должен затирать результаты нового
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(searchLocal = found) }
        }
    }

    // ---- Артисты ----

    fun openArtist(ref: ArtistRef) {
        // Исполнитель из файлов устройства: страница открывается в разделе «Локальные файлы», а не из Яндекс Музыки.
        if (ref.id.startsWith(LOCAL_ARTIST_PREFIX)) { requestLocalNav("artist:" + ref.name); return }
        showArtist(ArtistInfo(ref.id, ref.name, null))
    }

    fun openArtist(a: ArtistInfo) = showArtist(a)

    private fun showArtist(a: ArtistInfo, record: Boolean = true) {
        if (record) rememberOpenedPage(PageEntry.Artist(a.id)) else Unit
        // Страница исполнителя заменяет открытые альбом и плейлист, а не ложится поверх них.
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

    /**
     * «Назад» со страницы исполнителя: если её открыли из альбома (или другого исполнителя), возвращает ту страницу,
     * а не выкидывает на вкладку, с которой всё началось (раньше из альбома, открытого с исполнителя, «назад» кидало в поиск).
     */
    fun closeArtist() {
        if (restorePreviousPage()) return
        _state.update { it.copy(openedArtist = null, openedArtistLoading = false) }
    }

    /** Закрывает сразу все открытые страницы (исполнитель, альбом) и забывает цепочку переходов: нажатие на вкладку, поиск и т. п. */
    fun closeAllPages() {
        pageBack.clear()
        _state.update {
            it.copy(
                openedArtist = null, openedArtistLoading = false,
                openedAlbum = null, openedAlbumLoading = false, albumFocusTrackId = null,
            )
        }
    }

    // ---- Цепочка страниц «исполнитель ⇄ альбом» ----

    /** Страница, с которой перешли дальше: возвращаемся на неё кнопкой «назад». */
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

    /**
     * Перед открытием страницы [next] запоминает ту, что открыта сейчас. Если пользователь возвращается на ту же страницу,
     * с которой пришёл (альбом → исполнитель → тот же альбом), цепочка не растёт по кругу, а сворачивается.
     */
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

    /** true — предыдущая страница восстановлена; false — цепочка пуста и страницу надо просто закрыть. */
    private fun restorePreviousPage(): Boolean {
        val prev = pageBack.removeLastOrNull() ?: return false
        when (prev) {
            is PageEntry.Artist -> showArtist(prev.info, record = false)
            is PageEntry.Album -> showAlbum(prev.info, null, record = false)
        }
        return true
    }

    // ---- Альбомы ----

    fun openAlbum(a: AlbumInfo) = showAlbum(a)

    fun openAlbum(id: String, title: String?) = showAlbum(AlbumInfo(id = id, title = title.orEmpty()))

    /** Открывает альбом и прокручивает список к треку trackId (например, к играющему). */
    fun openAlbumAtTrack(id: String, title: String?, trackId: String) =
        showAlbum(AlbumInfo(id = id, title = title.orEmpty()), trackId)

    private fun showAlbum(a: AlbumInfo, focusTrackId: String? = null, record: Boolean = true) {
        if (record) rememberOpenedPage(PageEntry.Album(a))
        // Альбом заменяет открытые страницу исполнителя и плейлист.
        _state.update {
            it.copy(
                openedAlbum = a, openedAlbumLoading = true, albumFocusTrackId = focusTrackId,
                openedArtist = null, openedArtistLoading = false,
                openedPlaylist = null, openedPlaylistTracks = emptyList(), openedPlaylistLoading = false,
            )
        }
        viewModelScope.launch {
            // Сети нет — сразу показываем сохранённое, не ждём таймаута.
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

    /** «Назад» из альбома: возвращает страницу, с которой его открыли (например, исполнителя), а если её не было — закрывает альбом. */
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
        // Плейлист заменяет открытые страницы исполнителя и альбома.
        _state.update {
            it.copy(
                openedPlaylist = p, openedPlaylistTracks = emptyList(), openedPlaylistLoading = true,
                openedArtist = null, openedArtistLoading = false,
                openedAlbum = null, openedAlbumLoading = false, albumFocusTrackId = null,
            )
        }
        viewModelScope.launch {
            // Сети нет — сразу показываем сохранённое, не ждём таймаута.
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

    /** Открывает страницу по ссылке, которой с вами поделились. Приватный плейлист по ссылке не откроется — владелец закрыл его. */
    fun openLink(link: SharedLink) {
        if (!_state.value.loggedIn) {
            _state.update { it.copy(error = if (link.isVk()) "Войдите в VK Музыку, чтобы открыть ссылку" else "Войдите в Яндекс Музыку, чтобы открыть ссылку") }
            return
        }
        if (link.isVk() && app.tokenStore.activeProvider != Provider.VK) {
            _state.update { it.copy(error = "Чтобы открыть ссылку VK, переключитесь на аккаунт VK Музыки") }
            return
        }
        when (link) {
            is SharedLink.VkAlbumLink -> openAlbum(link.id, null)
            is SharedLink.VkArtistLink -> openArtist(ArtistRef(link.id, ""))
            is SharedLink.VkTrackLink -> viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { api.tracksByIds(listOf(VkIds.PREFIX + link.raw)) } }
                    .onSuccess { found ->
                        val t = found.firstOrNull()
                        if (t == null) _state.update { it.copy(error = "Трек не найден в VK") } else play(listOf(t), 0)
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        AppLog.e("VK", "Ссылка на трек не открылась: ${link.raw}", e)
                        _state.update { it.copy(error = e.message ?: "Не удалось открыть трек VK") }
                    }
            }
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
            is SharedLink.SoundCloudLink -> viewModelScope.launch {
                runCatching { withContext(Dispatchers.IO) { scApi().resolve(link.url) } }
                    .onSuccess { r ->
                        // Страницы плейлиста и исполнителя строит активный сервис; если сейчас открыт другой, просто играем треки.
                        val inSc = app.tokenStore.activeProvider == Provider.SOUNDCLOUD
                        when (r) {
                            is ScResolved.Single -> play(listOf(r.track), 0)
                            is ScResolved.ListRes ->
                                if (inSc) showPlaylist(r.info)
                                else if (r.tracks.isEmpty()) _state.update { it.copy(error = "В плейлисте нет треков") }
                                else play(r.tracks, 0)
                            is ScResolved.UserRes ->
                                if (inSc) openArtist(r.artist)
                                else if (r.artist.tracks.isEmpty()) _state.update { it.copy(error = "У исполнителя нет треков") }
                                else play(r.artist.tracks, 0)
                        }
                    }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        _state.update { it.copy(error = SoundCloudAuth.friendlyError(e).takeIf { m -> m.isNotBlank() } ?: "Не удалось открыть ссылку SoundCloud") }
                    }
            }
        }
    }

    /** Приватный / публичный плейлист. Переключатель меняется сразу; если сервер не принял — возвращается обратно. */
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

    /** Лайк альбома: нажатие добавляет его в «Мою коллекцию», повторное — убирает. Значок меняется сразу, при ошибке возвращается. */
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

    /** Исполнитель или обложка локального трека изменены: перечитываем файлы, чтобы правка появилась везде. */
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

    // ---- Плеер ----

    /**
     * Предзагрузка: следующие [TrackPreloader.AHEAD] треков очереди (с учётом перемешивания) заранее получают ссылку
     * на поток и первые мегабайты звука в кэш. Скачанные и локальные файлы пропускаются — они и так открываются мгновенно.
     * Вызывается при каждой смене очереди и трека; повторные вызовы подряд склеиваются, лишняя работа прерывается.
     */
    private var preloadJob: kotlinx.coroutines.Job? = null

    private fun schedulePreload() {
        preloadJob?.cancel()
        if (!PlayerSettings.preload) { TrackPreloader.cancel(); return }
        preloadJob = viewModelScope.launch(Dispatchers.Main.immediate) {
            // Небольшая задержка: при быстром листании треков и при загрузке очереди пачкой готовим только итоговые.
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

    private var rateLimitRetries = 0
    private var expiredRetries = 0
    private var expiredRetryId: String? = null
    private var expiredRetryAt = 0L
    private var rateLimitedId: String? = null
    private var rateLimitJob: kotlinx.coroutines.Job? = null

    /** Код HTTP-ответа, из-за которого упало воспроизведение (0 — не HTTP-ошибка). Ошибка запроса ссылки приходит текстом «HTTP 429: …». */
    private fun rateLimitCode(e: Throwable?): Int {
        var c: Throwable? = e
        while (c != null) {
            if (c is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) return c.responseCode
            if (c.message?.startsWith("HTTP 429") == true) return 429
            c = c.cause
        }
        return 0
    }

    private fun retryAfterMs(e: Throwable?): Long {
        var c: Throwable? = e
        while (c != null) {
            if (c is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                val sec = c.headerFields.entries.firstOrNull { it.key.equals("Retry-After", true) }?.value?.firstOrNull()?.trim()?.toLongOrNull()
                if (sec != null) return (sec * 1000L).coerceIn(0L, 15_000L)
            }
            c = c.cause
        }
        return 0L
    }

    // ---- Свои треки и альбомы SoundCloud (страница профиля) ----

    /** Загружает треки и альбомы самого пользователя SoundCloud для профиля. [force] — обновить, даже если уже загружено. */
    fun loadScOwn(uid: Long, force: Boolean = false) {
        val s = _state.value
        if (s.scOwnLoading) return
        if (!force && s.scOwnFor == uid) return
        _state.update { it.copy(scOwnLoading = true) }
        viewModelScope.launch {
            val a = runCatching { withContext(Dispatchers.IO) { api.artist(ScIds.PREFIX + uid) } }.getOrNull()
            _state.update {
                if (a == null) it.copy(scOwnLoading = false)
                else {
                    // Только что загруженные треки SoundCloud обрабатывает не сразу и в списке их ещё может не быть — держим их сверху.
                    val have = a.tracks.mapTo(HashSet()) { t -> t.id }
                    scPendingUploads.removeAll { p -> p.id in have }
                    it.copy(scOwnTracks = scPendingUploads.toList() + a.tracks, scOwnAlbums = a.albums, scOwnFor = uid, scOwnLoading = false)
                }
            }
        }
    }

    /** Текущие поля своего трека/альбома SoundCloud для окна редактирования; null — загрузить не удалось. */
    suspend fun scEditable(id: String, album: Boolean): app.ymusic.m3e.data.SoundCloudApi.ScEditable? {
        val sc = (app.api as? MusicRouter)?.soundCloud ?: return null
        return runCatching { withContext(Dispatchers.IO) { sc.editable(id, album) } }
            .onFailure { AppLog.w("ScEdit", "Не удалось получить поля ($id): ${it.message}") }.getOrNull()
    }

    /** Сохраняет изменения своего трека SoundCloud; список «Мои треки» обновляется сразу. */
    fun editScTrack(id: String, title: String, description: String, genre: String, tags: String, isPublic: Boolean, cover: Uri?) {
        val sc = (app.api as? MusicRouter)?.soundCloud ?: return
        val resolver = app.contentResolver
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val art = cover?.let { coverJpeg(resolver, it) }
                    if (cover != null && art == null) throw java.io.IOException("Не удалось прочитать картинку")
                    sc.editTrack(id, title, description, genre, tags, isPublic, art)
                }
            }
            r.onSuccess { t ->
                val i = scPendingUploads.indexOfFirst { p -> p.id == t.id }
                if (i >= 0) scPendingUploads[i] = t
                _state.update { s ->
                    s.copy(
                        scOwnTracks = s.scOwnTracks.map { o -> if (o.id == t.id) t else o },
                        message = "«${t.title}» обновлён",
                    )
                }
                _state.value.scOwnFor.takeIf { it != 0L }?.let { uid -> loadScOwn(uid, force = true) }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                AppLog.e("ScEdit", "Не удалось изменить трек", e)
                _state.update { it.copy(error = "Не удалось сохранить трек: ${e.message ?: "ошибка"}") }
            }
        }
    }

    /** Сохраняет изменения своего альбома SoundCloud. */
    fun editScAlbum(id: String, title: String, description: String, genre: String, tags: String, isPublic: Boolean) {
        val sc = (app.api as? MusicRouter)?.soundCloud ?: return
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { sc.editAlbum(id, title, description, genre, tags, isPublic) } }
            r.onSuccess { a ->
                _state.update { s ->
                    s.copy(
                        scOwnAlbums = s.scOwnAlbums.map { o -> if (o.id == a.id) o.copy(title = a.title) else o },
                        openedAlbum = s.openedAlbum?.let { o -> if (o.id == a.id) o.copy(title = a.title) else o },
                        message = "Альбом «${a.title}» обновлён",
                    )
                }
                _state.value.scOwnFor.takeIf { it != 0L }?.let { uid -> loadScOwn(uid, force = true) }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                AppLog.e("ScEdit", "Не удалось изменить альбом", e)
                _state.update { it.copy(error = "Не удалось сохранить альбом: ${e.message ?: "ошибка"}") }
            }
        }
    }

    /** Загруженные на SoundCloud треки, которых ещё нет в списке «Мои треки» с сервера (идёт обработка). */
    private val scPendingUploads = mutableListOf<Track>()

    /** Тип файла для загрузки: тип от системы, если он аудио, иначе по расширению. */
    private fun scUploadMime(resolver: android.content.ContentResolver, uri: Uri, name: String): String {
        val sys = resolver.getType(uri)?.takeIf { it.startsWith("audio/") && it != "audio/*" }
        val byExt = when (name.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "mp4", "m4b" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav", "wave" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/opus"
            "aif", "aiff" -> "audio/aiff"
            "wma" -> "audio/x-ms-wma"
            "amr" -> "audio/amr"
            else -> null
        }
        return byExt ?: sys ?: "audio/mpeg"
    }

    /** Загружает файл [uri] на SoundCloud в аккаунт пользователя. Видно всем, если [isPublic]. */
    fun uploadScTrack(uri: Uri, title: String, description: String, genre: String, tags: String, isPublic: Boolean, cover: Uri? = null) {
        if (_state.value.scUploading) return
        val sc = (app.api as? MusicRouter)?.soundCloud ?: return
        val resolver = app.contentResolver
        _state.update { it.copy(scUploading = true, scUploadProgress = 0f) }
        ScUploadNotifier.uploading(app, title, 0, force = true)
        viewModelScope.launch {
            // Загрузка долгая: не даём процессору заснуть, пока файл уходит (с запасом по времени на случай сбоя).
            val wake = runCatching {
                (app.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
                    .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "ymusic:scUpload")
                    .apply { acquire(60 * 60 * 1000L) }
            }.getOrNull()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    var name = "track"
                    var size = -1L
                    resolver.query(uri, null, null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (ni >= 0) c.getString(ni)?.let { name = it }
                            if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                        }
                    }
                    if (size == 0L) throw java.io.IOException("Файл пустой")
                    val mime = scUploadMime(resolver, uri, name)
                    // SoundCloud определяет формат и по имени: без расширения подставляем его по типу файла.
                    if ('.' !in name) name += when (mime) {
                        "audio/mp4" -> ".m4a"; "audio/aac" -> ".aac"; "audio/wav" -> ".wav"; "audio/flac" -> ".flac"
                        "audio/ogg" -> ".ogg"; "audio/opus" -> ".opus"; "audio/aiff" -> ".aiff"; else -> ".mp3"
                    }
                    var lastShown = 0
                    val artwork = cover?.let { coverJpeg(resolver, it) }
                    sc.uploadTrack(
                        title = title, description = description, genre = genre, tags = tags, isPublic = isPublic,
                        fileName = name, mime = mime, length = size, artwork = artwork,
                        open = { resolver.openInputStream(uri) ?: throw java.io.IOException("Не удалось открыть файл") },
                        onProgress = { p ->
                            // Обновляем состояние не чаще чем на каждый процент — иначе лишние перерисовки.
                            val pct = (p * 100).toInt()
                            if (pct != lastShown) {
                                lastShown = pct; _state.update { it.copy(scUploadProgress = p) }
                                ScUploadNotifier.uploading(app, title, pct)
                            }
                        },
                    )
                }
            }
            runCatching { if (wake?.isHeld == true) wake.release() }
            result.onSuccess { t ->
                // Сразу показываем трек в «Моих треках», а список с сервера обновляем позже: пока идёт обработка, его там может не быть.
                scPendingUploads.removeAll { p -> p.id == t.id }
                scPendingUploads.add(0, t)
                _state.update { it.copy(scOwnTracks = listOf(t) + it.scOwnTracks.filter { o -> o.id != t.id }) }
                _state.update {
                    it.copy(
                        scUploading = false, scUploadProgress = 0f,
                        message = "«${t.title}» отправлен на SoundCloud, идёт обработка…",
                    )
                }
                ScUploadNotifier.processing(app, t.title)
                // SoundCloud принимает файл и обрабатывает его дальше: следим за состоянием и сообщаем, чем всё кончилось
                // (обработка может завершиться ошибкой, а трек — исчезнуть, например из-за авторских прав).
                viewModelScope.launch {
                    var verdict: String? = null
                    for (i in 0 until 45) {
                        kotlinx.coroutines.delay(if (i < 3) 3_000L else 5_000L)
                        val st = withContext(Dispatchers.IO) { runCatching { sc.uploadedTrackState(t.id) }.getOrNull() }
                        if (st == "finished" || st == "failed" || st == "gone") { verdict = st; break }
                    }
                    when (verdict) {
                        "finished" -> {
                            _state.update { it.copy(message = "«${t.title}» готов на SoundCloud") }
                            ScUploadNotifier.result(app, t.title, true, if (isPublic) "Готов и виден всем" else "Готов (приватный, по секретной ссылке)")
                            _state.value.scOwnFor.takeIf { it != 0L }?.let { uid -> loadScOwn(uid, force = true) }
                        }
                        null -> ScUploadNotifier.result(
                            app, t.title, false,
                            "Обработка идёт дольше обычного. Загляните в профиль SoundCloud позже: если трека там нет, загрузите заново",
                        )
                        "failed", "gone" -> {
                            scPendingUploads.removeAll { p -> p.id == t.id }
                            ScUploadNotifier.result(
                                app, t.title, false,
                                if (verdict == "failed") "SoundCloud не смог обработать файл. Попробуйте другой (mp3, wav, flac)"
                                else "SoundCloud удалил или заблокировал трек — чаще всего из-за авторских прав",
                            )
                            _state.update {
                                it.copy(
                                    scOwnTracks = it.scOwnTracks.filter { o -> o.id != t.id },
                                    error = if (verdict == "failed") "SoundCloud не смог обработать «${t.title}». Попробуйте другой файл (mp3, wav, flac)"
                                    else "SoundCloud удалил или заблокировал «${t.title}» после загрузки — чаще всего из-за авторских прав. Загружайте только свою музыку",
                                )
                            }
                        }
                    }
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                val m = e.message.orEmpty()
                val text = when {
                    m.contains("HTTP 401") || m.contains("HTTP 403") ->
                        "SoundCloud не разрешил загрузку ($m). Войдите в аккаунт SoundCloud заново — возможно, у приложения нет прав на загрузку"
                    m.contains("HTTP 413") -> "Файл слишком большой для SoundCloud"
                    m.contains("HTTP 422") -> "SoundCloud отклонил файл: ${m.substringAfter(": ", m)}"
                    m.contains("HTTP 400") -> "SoundCloud не принял данные трека: ${m.substringAfter(": ", m)}"
                    m.contains("HTTP 5") -> "Сервер SoundCloud сейчас не отвечает ($m). Повторите позже"
                    e is java.net.SocketTimeoutException || e is java.net.SocketException || e is javax.net.ssl.SSLException ->
                        "Соединение прервалось во время загрузки. Проверьте интернет и повторите"
                    else -> SoundCloudAuth.friendlyError(e)
                }
                _state.update { it.copy(scUploading = false, scUploadProgress = 0f, error = "Не удалось загрузить трек: $text") }
                ScUploadNotifier.result(app, title, false, text)
            }
        }
    }

    /** Обложка для SoundCloud: уменьшаем до 1500 px по длинной стороне и сжимаем в JPEG (так файл гарантированно небольшой). */
    private fun coverJpeg(resolver: android.content.ContentResolver, uri: Uri): ByteArray? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val k = 1500f / maxOf(bmp.width, bmp.height)
        val out = if (k < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
        java.io.ByteArrayOutputStream().also { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }.getOrNull()

    /** Ждать ли для трека VK поток HLS; для остальных сервисов — всегда да (SoundCloud отдаёт только HLS). */
    private fun vkExpectsHls(id: String): Boolean =
        if (VkIds.isVk(id)) (api as? MusicRouter)?.vk?.expectsHls(id) ?: true else true

    private val vkRetyped = HashMap<String, Long>()

    /**
     * Поток VK оказался не того вида, какой ждал плеер («#EXTM3U» не найден — это обычный файл; либо формат файла не узнан — это HLS).
     * Меняем тип у трека (и у следующих в очереди) и запускаем заново. Не больше раза в полминуты на трек, чтобы не зациклиться.
     */
    private fun retypeVkItem(item: MediaItem, error: androidx.media3.common.PlaybackException): Boolean {
        if (!VkIds.isVk(item.mediaId)) return false
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        val isHlsItem = item.localConfiguration?.mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8
        val wantHls = when {
            // Ссылка точно m3u8, а плейлист не разобрался: тип источника ни при чём (искать причину надо в ответе сервера) — не переключаем.
            isHlsItem && (api as? MusicRouter)?.vk?.knownHls(item.mediaId) == true -> return false
            isHlsItem && causes.any { it is androidx.media3.common.ParserException && it.message?.contains("EXTM3U") == true } -> false
            !isHlsItem && causes.any { it is androidx.media3.exoplayer.source.UnrecognizedInputFormatException } -> true
            else -> return false
        }
        val now = System.currentTimeMillis()
        if (now - (vkRetyped[item.mediaId] ?: 0L) < 30_000L) return false
        vkRetyped[item.mediaId] = now
        if (vkRetyped.size > 200) vkRetyped.entries.removeAll { now - it.value > 30_000L }
        (api as? MusicRouter)?.vk?.learnHls(item.mediaId, wantHls)
        AppLog.w("VK", "Поток ${item.mediaId} оказался ${if (wantHls) "HLS" else "обычным файлом"}, перезапускаю с другим типом источника")
        val cur = player.currentMediaItemIndex
        val pos = player.currentPosition.coerceAtLeast(0L)
        for (i in cur until player.mediaItemCount) {
            val mi = player.getMediaItemAt(i)
            if (!VkIds.isVk(mi.mediaId) || mi.localConfiguration?.uri?.scheme != "ymusic") continue
            val has = mi.localConfiguration?.mimeType == androidx.media3.common.MimeTypes.APPLICATION_M3U8
            val want = if (i == cur) wantHls else vkExpectsHls(mi.mediaId)
            if (has == want) continue
            val b = mi.buildUpon().setMimeType(if (want) androidx.media3.common.MimeTypes.APPLICATION_M3U8 else null)
            player.replaceMediaItem(i, b.build())
        }
        TrackPreloader.forget(item.mediaId)
        player.seekTo(cur, pos)
        player.prepare()
        player.play()
        return true
    }

    private fun toMediaItem(t: Track): MediaItem {
        // VK отдаёт HLS: ExoPlayer нужно сказать об этом явно (по ссылке ymusic://… тип не определить).
        // Скачанный трек VK — обычный файл. Выбранный тип запоминаем: источник данных (App) должен ему следовать.
        // SoundCloud тоже отдаёт HLS (hls_* в /streams), поэтому обрабатывается так же, как VK.
        val hlsSource = VkIds.isVk(t.id) || ScIds.isSc(t.id)
        val vkStream = hlsSource && t.localUri == null && runCatching { app.downloads.playableUri(t.id) }.getOrNull() == null
        if (hlsSource) app.vkHlsMode[t.id] = vkStream
        return MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(t.localUri ?: "ymusic://track/${t.id}")
            // VK бывает отдаёт не HLS, а обычный файл: тип берём по известной ссылке (иначе плеер ждёт #EXTM3U и падает).
            .apply { if (vkStream && vkExpectsHls(t.id)) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8) }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title).setArtist(t.artists)
                    .setArtworkUri(t.cover()?.let(Uri::parse))
                    // albumId нужен лайку из шторки уведомлений (ключ трека вида id:albumId).
                    .setExtras(Bundle().apply {
                        putString(EXTRA_ALBUM_ID, t.albumId)
                        putString(EXTRA_ALBUM_TITLE, t.albumTitle)
                        putBoolean(EXTRA_EXPLICIT, t.explicit)
                        putLong(EXTRA_DURATION, t.durationMs)
                    })
                    .build()
            )
            .build()
    }

    fun play(all: List<Track>, startIndex: Int, shuffle: Boolean = false, source: String? = null) {
        if (all.isEmpty()) return
        var tracks = all
        var start = startIndex
        // Без сети играем только то, что скачано: остальные треки в очереди просто падали бы с ошибкой.
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
        // Запоминаем, какой список играет, чтобы подсветить его карточку (свечение в плейлистах).
        val st = _state.value
        val src = source ?: when {
            all == st.liked -> "liked"
            st.openedPlaylist != null && all == st.openedPlaylistTracks ->
                playlistSourceKey(st.openedPlaylist)
            else -> null
        }
        _state.update { if (it.playingSource == src) it else it.copy(playingSource = src) }
        // История «с включения» начинается заново вместе с новым списком.
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
        // Если сейчас играет колонка — новый список уходит на неё, а телефон молчит.
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

    /** Сервис нужен для фонового воспроизведения и уведомления (в том числе после восстановления сессии). */
    private fun ensureService() {
        runCatching { app.startService(Intent(app, PlaybackService::class.java)) }
    }

    // ---- Моя волна ----

    /** Волна, нажатая до того, как загрузилась главная (или пока нет сети): запустится сама сразу после загрузки. */
    @Volatile private var wavePending = false
    private var wavePendingJob: Job? = null

    /** Главная загружена: не идёт загрузка, нет «офлайна» и не работает переподключение. */
    private fun libraryReady(): Boolean {
        val s = _state.value
        return !s.loading && !s.offline && reconnectJob?.isActive != true && Net.isOnline(app)
    }

    /** Станции и название волны, нажатой до окончания загрузки главной. */
    private var wavePendingStations: List<String> = listOf(DEFAULT_WAVE)
    private var wavePendingLabel: String = "Моя волна"

    /**
     * Запускает волну. Без аргументов — «Моя волна»; иначе волна по треку / исполнителю / альбому / плейлисту:
     * [stations] — варианты идентификатора станции (пробуются по очереди, пока один не вернёт треки).
     */
    fun startWave(stations: List<String> = listOf(DEFAULT_WAVE), label: String = ActiveProvider.waveTitle) {
        if (_state.value.waveLoading) return
        if (!libraryReady()) {
            // Раньше волна стартовала «вслепую» в разгар загрузки/без сети, падала, и нажатие пропадало.
            // Теперь запоминаем нажатие, показываем загрузку на кнопке и запускаем волну, как только данные придут.
            _state.update { it.copy(waveLoading = true, message = "Главная ещё загружается — волна запустится сразу после загрузки") }
            wavePending = true
            wavePendingStations = stations
            wavePendingLabel = label
            wavePendingJob?.cancel()
            wavePendingJob = viewModelScope.launch {
                delay(WAVE_WAIT_MS)
                // Не дождались: пробуем сами — если сети нет, ошибка придёт обычным путём, а кнопка освободится.
                if (wavePending) { wavePending = false; launchWave(wavePendingStations, wavePendingLabel) }
            }
            // Если приложение считает, что сети нет, а она уже появилась — не ждём таймера, а переподключаемся сразу.
            retryIfOffline()
            return
        }
        _state.update { it.copy(waveLoading = true) }
        launchWave(stations, label)
    }

    /** «Встряхнуть»: заново подобрать треки той же волны (своей или по исполнителю / альбому / плейлисту). */
    fun restartWave() {
        val st = _state.value
        if (st.waveOn && st.waveStation != DEFAULT_WAVE) startWave(listOf(st.waveStation), st.sessionLabel.ifBlank { "Волна" })
        else startWave()
    }

    /** Данные загрузились: если волну нажали раньше времени — запускаем её. */
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

    private fun launchWave(stations: List<String> = listOf(DEFAULT_WAVE), label: String = ActiveProvider.waveTitle) {
        viewModelScope.launch {
            var error: Throwable? = null
            var found: Pair<String, WaveBatch>? = null
            for (st in stations) {
                val r = runCatching { withContext(Dispatchers.IO) { api.waveTracks(null, st) } }
                val first = r.getOrNull()
                // Уже звучавшие за сутки треки не берём. Вся порция уже слушалась — просим следующие порции станции
                // (от последнего трека предыдущей), а не отдаём повторы; повторы — только если свежего нет вовсе.
                var tracks = first?.let { WaveSeen.filter(it.tracks) }.orEmpty()
                var batchId = first?.batchId
                if (first != null && tracks.isEmpty() && first.tracks.isNotEmpty()) {
                    val more = fetchFreshWave(st, listOf(first.tracks.last().id), emptySet(), emptySet(), WAVE_FRESH_WANT)
                    tracks = more.fresh
                    batchId = more.batchId ?: batchId
                    if (tracks.isEmpty()) tracks = first.tracks // свежих нет совсем: волна всё равно должна запуститься
                }
                val batch = if (tracks.isNotEmpty()) WaveBatch(tracks, batchId) else null
                if (batch != null) { found = st to batch; break }
                if (r.isFailure) error = r.exceptionOrNull()
            }
            val f = found
            if (f == null) {
                _state.update {
                    it.copy(error = if (error != null) (error.message ?: "Не удалось запустить волну") else "Волна не вернула треков")
                }
            } else {
                val (station, batch) = f
                // «Повтор одного трека» не должен превращать волну в бесконечный повтор первого трека.
                if (player.repeatMode == Player.REPEAT_MODE_ONE) player.repeatMode = Player.REPEAT_MODE_OFF
                play(batch.tracks, 0)
                waveStationId = station
                waveActive = true
                waveBatchId = batch.batchId
                setSessionLabel(label)
                persistQueue() // теперь с пометкой «это волна»
                if (station != DEFAULT_WAVE) {
                    viewModelScope.launch(Dispatchers.IO) { runCatching { api.waveStarted(station) } }
                }
            }
            _state.update { it.copy(waveLoading = false) }
        }
    }

    /**
     * Дозагружает волну. [advance] — очередь уже кончилась (или трек не загрузился): как только придут новые
     * треки, переходим к первому из них и продолжаем играть, а не замираем на последнем треке.
     */
    private fun loadMoreWave(advance: Boolean = false) {
        if (advance) advanceAfterLoad = true
        if (waveLoadingMore) return
        waveLoadingMore = true
        val station = waveStationId
        viewModelScope.launch {
            val before = player.mediaItemCount
            var batchId: String? = null
            var fresh: List<Track> = emptyList()
            // Сначала продолжение от последнего трека очереди, потом от играющего сейчас.
            val seeds = listOf(queue.lastOrNull()?.id, player.currentMediaItem?.mediaId).distinct()
            // Треки, звучавшие в волне за последние 24 часа, пропускаем (если включено в настройках): если порция целиком
            // из слышанного, берём следующие порции станции. Повтор — только когда свежего нет совсем (лучше повтор, чем тишина).
            val inQueue = queue.mapTo(HashSet()) { it.id }
            val recent = queue.takeLast(30).mapTo(HashSet()) { it.id }
            val got = fetchFreshWave(station, seeds, inQueue, recent, WAVE_FRESH_WANT)
            batchId = got.batchId ?: batchId
            fresh = got.fresh.ifEmpty { got.fallback }
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
                    // Очередь могла закончиться (ENDED) или человек стоит на последнем треке — идём к первому из новых.
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

    // ---- Волна по конкретному треку / исполнителю / альбому / плейлисту (как «Моя волна» в Яндекс Музыке) ----

    fun startWaveByTrack(t: Track) = startWave(listOf("track:${t.id}"), "Волна по треку «${t.title}»")

    fun startWaveByArtist(a: ArtistRef) = startWave(listOf("artist:${a.id}"), "Волна: ${a.name}")

    fun startWaveByAlbum(albumId: String, title: String?) =
        startWave(listOf("album:$albumId"), "Волна по альбому" + (title?.takeIf { it.isNotBlank() }?.let { " «$it»" } ?: ""))

    /** Станция плейлиста называется по-разному в разных версиях API (владелец_номер или владелец:номер) — пробуем оба вида. */
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
                    // Если «Моя волна» уже играет — перезапускаем её с новыми настройками.
                    if (waveActive && waveStationId == DEFAULT_WAVE) startWave()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Не удалось сохранить настройки") } }
        }
    }

    // ---- Плейлисты: редактирование ----

    // Изменения плейлистов идут строго по очереди: иначе быстрые добавления конфликтуют по ревизии.
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

    /** [openAfter] — сразу открыть созданный плейлист (там пустое состояние с кнопкой «Добавить песни»). */
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

    // ---- Свой поиск для добавления песен в плейлист ----

    private var pickerJob: Job? = null

    /** Ищет треки Яндекс Музыки, не трогая результаты вкладки «Поиск». */
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
            // Очередь доиграла до конца: волна подгружает продолжение (а не крутит первый трек), список — начинаем сначала;
            // после ошибки — готовим плеер заново.
            Player.STATE_ENDED -> {
                if (waveActive) { loadMoreWave(advance = true); return }
                player.seekTo(0, 0)
            }
            Player.STATE_IDLE -> if (player.mediaItemCount > 0) player.prepare()
        }
        player.play()
    }
    /** Без Плюса: треки и подкасты Яндекса (не файлы с устройства) играют только первые 30 секунд. */
    private fun noPlusLimited(t: Track?): Boolean {
        val a = _state.value.account
        return a != null && a.provider == Provider.YANDEX && !a.hasPlus && t != null && !t.isLocal
    }

    /** Достигнут предел в 30 секунд: переходим к следующему треку, а если он последний — останавливаемся в начале. */
    private fun cutNoPlusTrack() {
        if (!warnedNoPlus) {
            warnedNoPlus = true
            _state.update { it.copy(error = "Без Яндекс Плюса треки и подкасты играют только по 30 секунд. С подпиской они будут звучать целиком.") }
        }
        if (waveActive || player.hasNextMediaItem()) next()
        else { player.pause(); player.seekTo(0) }
    }

    /** То же для колонки: следующий трек её очереди, а если он последний — перемотка в начало и пауза. */
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
        // Последний трек волны: подгружаем продолжение и переходим к нему, а не стоим на месте.
        if (waveActive && !player.hasNextMediaItem()) { loadMoreWave(advance = true); return }
        player.seekToNextMediaItem()
    }

    /**
     * Трек из истории: играет сразу, но очередь (плейлист / волна) не заменяется — после него продолжается тот же список.
     * Если трек уже есть в очереди, переходим к нему, иначе вставляем его следующим.
     */
    fun playFromHistory(t: Track) {
        ensureService()
        if (!Net.isOnline(app) && !t.isLocal && !app.downloads.isDownloaded(t.id)) {
            _state.update { it.copy(error = "Нет сети, а этот трек не скачан") }
            return
        }
        if (player.mediaItemCount == 0 || queue.isEmpty()) { play(listOf(t), 0); return }
        val cur = player.currentMediaItemIndex.coerceAtLeast(0)
        // Ищем сначала впереди текущего места, потом позади: в волне один трек мог попасть в очередь дважды.
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
    /** Строго предыдущий трек (для свайпа в мини-плеере), без «перемотки в начало». */
    fun skipPrevious() {
        if (connect.isRemoteActive()) { connect.skip(-1); return }
        player.seekToPreviousMediaItem()
    }
    // seekToPrevious: если трек играет дольше нескольких секунд — перематывает в начало, иначе идёт к предыдущему.
    fun previous() {
        if (connect.isRemoteActive()) {
            // Как у плеера: дольше нескольких секунд — в начало трека, иначе к предыдущему.
            if (connect.estimatedProgress() > 3_000 || !connect.skip(-1)) connect.seekTo(0)
            return
        }
        player.seekToPrevious()
    }
    fun seekTo(ms: Long) {
        if (connect.isRemoteActive()) { connect.seekTo(ms); return }
        player.seekTo(ms)
    }

    /** Текущая позиция плеера «вживую» — для плавного ползунка (состояние UiState обновляется реже). */
    fun livePosition(): Long = if (connect.isRemoteActive()) connect.estimatedProgress() else player.currentPosition

    /** Повтор: выкл → весь список → один трек → выкл. */
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
