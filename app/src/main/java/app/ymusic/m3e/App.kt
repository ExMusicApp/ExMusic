package app.ymusic.m3e

import android.app.Application
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import app.ymusic.m3e.data.YandexMusicApi
import app.ymusic.m3e.player.EqualizerController
import app.ymusic.m3e.player.TrackPreloader
import app.ymusic.m3e.ui.Haptics
import app.ymusic.m3e.ui.NavSettings
import app.ymusic.m3e.ui.PlayerSettings
import app.ymusic.m3e.ui.ThemeSettings
import okhttp3.OkHttpClient

interface LikeBridge {
    fun toggleLike(t: Track)
    fun toggleDislike(t: Track)
}

class App : Application() {
    lateinit var tokenStore: TokenStore
    lateinit var api: YandexMusicApi
    private val playerLazy = lazy { buildPlayer() }
    val player: ExoPlayer by playerLazy
    val playerReady: Boolean get() = playerLazy.isInitialized()
    val equalizer: EqualizerController by lazy { EqualizerController(this, player) }
    val downloads: DownloadStore by lazy { DownloadStore(this, api) }
    val offlineLibrary: OfflineLibrary by lazy { OfflineLibrary(this) }

    val connect: app.ymusic.m3e.data.YandexConnect by lazy {
        app.ymusic.m3e.data.YandexConnect(this, http) { tokenStore.token }
    }

    val playbackStore: PlaybackStore by lazy { PlaybackStore(this) }

    val lyrics: app.ymusic.m3e.data.LyricsRepository by lazy { app.ymusic.m3e.data.LyricsRepository(this, http, api) }

    val likedIds = MutableStateFlow<Set<String>>(emptySet())
    val dislikedIds = MutableStateFlow<Set<String>>(emptySet())

    @Volatile var likeBridge: LikeBridge? = null

    private val http = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        instance = this
        app.ymusic.m3e.data.AppLog.init(this)
        ThemeSettings.init(this)
        PlayerSettings.init(this)
        Haptics.init(this)
        NavSettings.init(this)
        app.ymusic.m3e.ui.TabletSettings.init(this)
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
        api = YandexMusicApi(http) { tokenStore.token }
        api.highQuality = { PlayerSettings.highQuality }
    }

    private fun buildPlayer(): ExoPlayer {
        val plain = DefaultDataSource.Factory(this, OkHttpDataSource.Factory(http))
        val upstream = TrackPreloader.playerDataSourceFactory(this, plain)
        val resolving = ResolvingDataSource.Factory(upstream) { spec: DataSpec ->
            if (spec.uri.scheme == "ymusic") {
                val id = spec.uri.lastPathSegment.orEmpty()
                val local = runCatching { downloads.playableUri(id) }.getOrNull()
                if (local != null) {
                    spec.withUri(local)
                } else {
                    val (url, key) = TrackPreloader.streamUrl(api, id)
                    spec.buildUpon().setUri(Uri.parse(url)).setKey(key).build()
                }
            } else spec
        }
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
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
            .also { it.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(20_000_000L)) }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
