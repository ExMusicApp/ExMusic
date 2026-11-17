package app.ymusic.m3e.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import app.ymusic.m3e.App
import app.ymusic.m3e.MainActivity
import app.ymusic.m3e.R
import app.ymusic.m3e.data.Track
import app.ymusic.m3e.widget.PlayerWidget
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_TIMELINE_CHANGED,
                )
            ) PlayerWidget.update(this@PlaybackService)
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_TIMELINE_CHANGED)) {
                refreshButtons()
            }
        }
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val b = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            if (controller.packageName == packageName || controller.isTrusted) {
                b.add(SessionCommand(CMD_LIKE, Bundle.EMPTY)).add(SessionCommand(CMD_DISLIKE, Bundle.EMPTY))
            }
            val commands = b.build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_LIKE -> toggle(dislike = false)
                CMD_DISLIKE -> toggle(dislike = true)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching { App.instance.equalizer }
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val s = MediaSession.Builder(this, App.instance.player)
            .setCallback(callback)
            .setSessionActivity(openApp)
            .build()
        session = s
        addSession(s)
        App.instance.player.addListener(widgetListener)
        PlayerWidget.update(this)
        refreshButtons()
        scope.launch {
            combine(App.instance.likedIds, App.instance.dislikedIds) { _, _ -> }.collect { refreshButtons() }
        }
        loadLikesIfNeeded()
    }

    private fun loadLikesIfNeeded() {
        val app = App.instance
        if (app.likedIds.value.isNotEmpty() || app.likeBridge != null) return
        val uid = accountApiId() ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { app.likedIds.value = app.api.likedTracks(uid).mapTo(HashSet()) { it.id } }
                runCatching { app.dislikedIds.value = app.api.dislikedTrackIds(uid) }
            }
        }
    }

    private fun accountApiId(): String? {
        val ts = App.instance.tokenStore
        val acc = ts.accounts.firstOrNull { it.token == ts.token } ?: return null
        return if (acc.uid != 0L) acc.uid.toString() else acc.login.ifBlank { "me" }
    }

    private fun currentTrack(): Track? {
        val mi = App.instance.player.currentMediaItem ?: return null
        val md = mi.mediaMetadata
        val ex = md.extras
        return Track(
            id = mi.mediaId,
            albumId = ex?.getString("albumId"),
            title = md.title?.toString().orEmpty(),
            artists = md.artist?.toString().orEmpty(),
            coverUri = md.artworkUri?.toString(),
            durationMs = ex?.getLong("durationMs") ?: 0L,
            albumTitle = ex?.getString("albumTitle"),
            explicit = ex?.getBoolean("explicit") ?: false,
            localUri = mi.localConfiguration?.uri?.takeIf { it.scheme != "ymusic" }?.toString(),
        )
    }

    private fun toggle(dislike: Boolean) {
        val t = currentTrack() ?: return
        if (t.isLocal) return
        val bridge = App.instance.likeBridge
        if (bridge != null) {
            if (dislike) bridge.toggleDislike(t) else bridge.toggleLike(t)
            return
        }
        toggleDirect(t, dislike)
    }

    private fun toggleDirect(t: Track, dislike: Boolean) {
        val app = App.instance
        val uid = accountApiId() ?: return
        val prevLiked = app.likedIds.value
        val prevDis = app.dislikedIds.value
        val wasLiked = t.id in prevLiked
        val wasDisliked = t.id in prevDis
        if (dislike) {
            app.dislikedIds.value = if (wasDisliked) prevDis - t.id else prevDis + t.id
            app.likedIds.value = prevLiked - t.id
            if (!wasDisliked && app.player.hasNextMediaItem()) app.player.seekToNextMediaItem()
        } else {
            app.likedIds.value = if (wasLiked) prevLiked - t.id else prevLiked + t.id
            app.dislikedIds.value = prevDis - t.id
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (dislike) {
                        if (wasDisliked) app.api.undislikeTrack(uid, t)
                        else {
                            app.api.dislikeTrack(uid, t)
                            if (wasLiked) app.api.unlikeTrack(uid, t)
                        }
                    } else {
                        if (wasLiked) app.api.unlikeTrack(uid, t)
                        else {
                            app.api.likeTrack(uid, t)
                            if (wasDisliked) app.api.undislikeTrack(uid, t)
                        }
                    }
                }.isSuccess
            }
            if (!ok) {
                app.likedIds.value = prevLiked
                app.dislikedIds.value = prevDis
            }
        }
    }

    private fun refreshButtons() {
        val s = session ?: return
        val t = currentTrack()
        if (t == null || t.isLocal) {
            s.setMediaButtonPreferences(emptyList())
            return
        }
        val liked = t.id in App.instance.likedIds.value
        val disliked = t.id in App.instance.dislikedIds.value
        val dislikeBtn = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(if (disliked) R.drawable.ms_thumb_down else R.drawable.ms_thumb_down_outline)
            .setDisplayName(if (disliked) "Убрать дизлайк" else "Не нравится")
            .setSessionCommand(SessionCommand(CMD_DISLIKE, Bundle.EMPTY))
            .build()
        val likeBtn = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(if (liked) R.drawable.ms_favorite else R.drawable.ms_favorite_border)
            .setDisplayName(if (liked) "Убрать из любимого" else "Нравится")
            .setSessionCommand(SessionCommand(CMD_LIKE, Bundle.EMPTY))
            .build()
        s.setMediaButtonPreferences(listOf(dislikeBtn, likeBtn))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        runCatching { App.instance.player.removeListener(widgetListener) }
        scope.cancel()
        session?.release()
        session = null
        PlayerWidget.update(this)
        super.onDestroy()
    }

    private companion object {
        const val CMD_LIKE = "app.ymusic.m3e.cmd.LIKE"
        const val CMD_DISLIKE = "app.ymusic.m3e.cmd.DISLIKE"
    }
}
