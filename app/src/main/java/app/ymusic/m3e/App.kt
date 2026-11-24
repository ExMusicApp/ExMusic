package app.ymusic.m3e

import android.app.Application
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.ymusic.m3e.data.DownloadStore
import app.ymusic.m3e.data.LocalFilesSettings
import app.ymusic.m3e.data.OfflineLibrary
import app.ymusic.m3e.data.PlaybackStore
import app.ymusic.m3e.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import app.ymusic.m3e.data.TokenStore
import app.ymusic.m3e.recognition.RecognitionSettings
import app.ymusic.m3e.data.MusicApi
import app.ymusic.m3e.data.MusicRouter
import app.ymusic.m3e.data.Provider
import app.ymusic.m3e.data.VkIds
import app.ymusic.m3e.data.VkMusicApi
import app.ymusic.m3e.data.YandexMusicApi
import app.ymusic.m3e.player.EqualizerController
import app.ymusic.m3e.player.TrackPreloader
import app.ymusic.m3e.ui.Haptics
import app.ymusic.m3e.ui.NavSettings
import app.ymusic.m3e.ui.PlayerSettings
import app.ymusic.m3e.ui.ThemeSettings
import okhttp3.OkHttpClient

/**
 * Мост между шторкой уведомлений (PlaybackService) и интерфейсом: пока жив AppViewModel, лайки идут через него
 * (обновляется весь UI); если приложение закрыто, а музыка играет, их выполняет сам [App].
 */
interface LikeBridge {
    fun toggleLike(t: Track)
    fun toggleDislike(t: Track)
}

class App : Application(), coil3.SingletonImageLoader.Factory {
    lateinit var tokenStore: TokenStore
    /** Единый API: сам выбирает Яндекс Музыку или VK Музыку по активному аккаунту. */
    lateinit var api: MusicApi
    lateinit var yandexApi: YandexMusicApi
    lateinit var vkApi: VkMusicApi

    /** Как поставлен в очередь трек VK: true — потоком HLS, false — скачанным файлом (см. AppViewModel.toMediaItem). */
    val vkHlsMode = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val playerLazy = lazy { buildPlayer() }
    val player: ExoPlayer by playerLazy
    /** Плеер уже создан (виджет не должен создавать его сам). */
    val playerReady: Boolean get() = playerLazy.isInitialized()
    /** Источник данных плеера: его же берёт временный плеер микса треков (см. [CrossfadeController]), поэтому микс работает во всех сервисах. */
    private var playerSourceFactory: DataSource.Factory? = null

    /** Второй плеер для микса: играет затихающий хвост трека, звуковой фокус не берёт — им управляет основной. */
    private fun buildTailPlayer(): ExoPlayer {
        val f = checkNotNull(playerSourceFactory) { "плеер ещё не создан" }
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(f).also { it.setLoadErrorHandlingPolicy(app.ymusic.m3e.player.RateLimitPolicy()) })
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                false,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /** Микс треков (кроссфейд). Запускается вместе со службой воспроизведения; работает, только если включён в настройках. */
    val crossfade: app.ymusic.m3e.player.CrossfadeController by lazy {
        app.ymusic.m3e.player.CrossfadeController(player) { buildTailPlayer() }
    }

    val equalizer: EqualizerController by lazy { EqualizerController(this, player) }
    val downloads: DownloadStore by lazy { DownloadStore(this, api) }
    val offlineLibrary: OfflineLibrary by lazy { OfflineLibrary(this) }

    /** Колонки и другие устройства Яндекса (как «Подключить» в Яндекс Музыке): телефон работает пультом. */
    val connect: app.ymusic.m3e.data.YandexConnect by lazy {
        app.ymusic.m3e.data.YandexConnect(this, http) { tokenStore.tokenFor(Provider.YANDEX) }
    }

    val playbackStore: PlaybackStore by lazy { PlaybackStore(this) }

    /** Тексты песен: Genius + Яндекс Музыка (с таймкодами). */
    val lyrics: app.ymusic.m3e.data.LyricsRepository by lazy { app.ymusic.m3e.data.LyricsRepository(this, http, api) }

    /** id лайкнутых / дизлайкнутых треков активного аккаунта — для кнопок в шторке уведомлений. */
    val likedIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikedIds = MutableStateFlow<Set<String>>(emptySet())

    /** Ставится, пока жив AppViewModel. */
    @Volatile var likeBridge: LikeBridge? = null

    /** Общий HTTP-клиент: запросы к SoundCloud идут через обход блокировки (см. [app.ymusic.m3e.data.ScProxy]), остальные — напрямую. */
    val http: OkHttpClient = app.ymusic.m3e.data.ScProxy.applyTo(OkHttpClient.Builder()).build()

    /** Обложки (Coil) грузятся тем же клиентом, чтобы картинки SoundCloud тоже шли в обход блокировки. */
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader =
        coil3.ImageLoader.Builder(context)
            .components { add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Журнал — первым делом: при старте он сохраняет предыдущий запуск целиком и ловит аварийные закрытия.
        app.ymusic.m3e.data.AppLog.init(this)
        ThemeSettings.init(this)
        PlayerSettings.init(this)
        Haptics.init(this)
        NavSettings.init(this)
        app.ymusic.m3e.data.WaveSeen.init(this)
        app.ymusic.m3e.ui.GreetingSettings.init(this)
        app.ymusic.m3e.data.ScWaveSources.init(this)
        app.ymusic.m3e.data.ScProxySettings.init(this)
        app.ymusic.m3e.ui.TabletSettings.init(this)
        app.ymusic.m3e.ui.AppLang.init(this)
        app.ymusic.m3e.ui.UiLayout.init(this)
        app.ymusic.m3e.ui.UiStyle.init(this)
        app.ymusic.m3e.ui.GlassSettings.init(this)
        LocalFilesSettings.init(this)
        app.ymusic.m3e.data.LocalModeSettings.init(this)
        app.ymusic.m3e.data.DevicePlaylists.init(this)
        app.ymusic.m3e.data.LocalTrackEdits.init(this)
        RecognitionSettings.init(this)
        app.ymusic.m3e.recognition.RecognitionHistory.init(this)
        app.ymusic.m3e.data.Updates.init(this)
        app.ymusic.m3e.data.PluginSystem.init(this)
        tokenStore = TokenStore(this)
        // Токен отдаётся только «своему» сервису: токен VK никогда не уходит в Яндекс и наоборот.
        yandexApi = YandexMusicApi(http) { tokenStore.tokenFor(Provider.YANDEX) }
        vkApi = VkMusicApi(
            http, { tokenStore.tokenFor(Provider.VK) }, { tokenStore.activeVkClient },
            getSharedPreferences("vk_music", MODE_PRIVATE),
        )
        api = MusicRouter(yandexApi, vkApi, app.ymusic.m3e.data.SoundCloudApi(tokenStore), app.ymusic.m3e.data.SpotifyApi(tokenStore)) { tokenStore.activeProvider }
        // Высокое качество — только с Плюсом: без подписки всегда обычный поток, какой бы ни был переключатель.
        // У VK Плюса нет: переключатель выбирает лучший вариант потока, если сервер их отдаёт несколько.
        api.highQuality = {
            val a = tokenStore.active
            PlayerSettings.highQuality && a != null && (a.hasPlus || a.provider == Provider.VK)
        }
    }

    private fun isVkMediaHost(host: String?): Boolean {
        val h = host?.lowercase() ?: return false
        return listOf("vkuseraudio.net", "vkuseraudio.com", "vk-cdn.net", "userapi.com", "vkuser.net", "vk.com", "vk.ru").any { h == it || h.endsWith(".$it") }
    }

    private fun buildPlayer(): ExoPlayer {
        // Треки в очереди имеют URI вида ymusic://track/<id>; настоящая ссылка
        // запрашивается у API в момент загрузки, чтобы не протухала.
        // Верхний слой читает http(s), file:// и content://, поэтому скачанный трек (копия в приложении или файл
        // в «Загрузках») играет без интернета, а всё остальное идёт по ссылке из API.
        val plain = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(http))
        // Внизу — источник с дисковым кэшем предзагрузки (см. TrackPreloader): первые мегабайты следующих треков уже на диске.
        val upstream = TrackPreloader.playerDataSourceFactory(this, plain)
        val resolving = ResolvingDataSource.Factory(upstream) { spec: DataSpec ->
            if (spec.uri.scheme == "ymusic") {
                val id = spec.uri.lastPathSegment.orEmpty()
                // Трек VK, поставленный в очередь как HLS, играет потоком, даже если его скачали позже (тип источника уже выбран).
                val forceStream = VkIds.isVk(id) && vkHlsMode[id] == true
                // Скачанное важнее потока: и быстрее, и работает без сети.
                val local = if (forceStream) null else runCatching { downloads.playableUri(id) }.getOrNull()
                if (local != null) {
                    spec.withUri(local)
                } else {
                    // Ссылка и ключ кэша: из памяти, если трек уже готовили заранее, иначе запрос к API.
                    val (url, key) = TrackPreloader.streamUrl(api, id)
                    val b = spec.buildUpon().setUri(Uri.parse(url))
                    // VK: сервер потока ждёт «родной» User-Agent клиента, под которым выдан токен.
                    if (VkIds.isVk(id)) b.setHttpRequestHeaders(spec.httpRequestHeaders + ("User-Agent" to tokenStore.activeVkClient.userAgent))
                    // У HLS ключа нет: сегменты кэшируются по своим ссылкам.
                    if (key.isNotEmpty()) b.setKey(key)
                    b.build()
                }
            } else if (isVkMediaHost(spec.uri.host)) {
                // Сегменты и ключи HLS у VK тоже запрашиваются с «родным» User-Agent.
                spec.withAdditionalHeaders(mapOf("User-Agent" to tokenStore.activeVkClient.userAgent))
            } else spec
        }
        // Если ссылка успела устареть (403) — берём новую и продолжаем с того же места, а не пропускаем трек.
        val recovering = DataSource.Factory { TrackPreloader.ExpiredLinkRecoveringDataSource(resolving.createDataSource()) }
        // Токен SoundCloud для запросов плеера (плейлист HLS без него получает 429).
        TrackPreloader.scAuth = { (api as? MusicRouter)?.soundCloud?.playbackAuth() }
        playerSourceFactory = recovering
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(recovering).also { it.setLoadErrorHandlingPolicy(app.ymusic.m3e.player.RateLimitPolicy()) })
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            // Плеер сам заранее готовит и буферизует начало следующего трека (~20 с), пока играет текущий.
            .also { it.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(20_000_000L)) }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
