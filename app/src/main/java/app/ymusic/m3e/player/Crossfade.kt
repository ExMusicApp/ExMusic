package app.ymusic.m3e.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.ymusic.m3e.data.AppLog
import app.ymusic.m3e.ui.PlayerSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Микс треков (кроссфейд), как в Spotify: конец текущего трека плавно затихает, а следующий уже нарастает поверх него.
 *
 * Один ExoPlayer не умеет играть два трека одновременно, поэтому используется второй, временный плеер («хвост»):
 *  1. за [ARM_AHEAD_MS] до начала микса хвостовой плеер получает тот же трек, перематывается на место микса и ждёт на паузе;
 *  2. когда до конца остаётся ровно время микса, хвост запускается с позиции основного плеера, а основной переходит
 *     на следующий трек с нулевой громкости — очередь, уведомление, текст и история работают как при обычной смене трека;
 *  3. громкость плавно перетекает (равная мощность: sin/cos), потом хвост останавливается.
 * Хвост использует тот же источник данных, что и основной плеер, поэтому микс работает для всех сервисов
 * (Яндекс, VK, SoundCloud, файлы на устройстве). Если хвост не успел подготовиться, трек просто переключается как обычно.
 * Микс не включается при повторе одного трека, на последнем треке очереди и на слишком коротких треках.
 */
class CrossfadeController(
    private val main: ExoPlayer,
    private val newTail: () -> ExoPlayer,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var tail: ExoPlayer? = null
    private var armedKey: String? = null
    private var failedKey: String? = null
    private var fading = false
    private var fadeStart = 0L
    private var fadeMs = 0L
    private var baseVolume = 1f
    /** Свои переходы (seekToNext) не считаем действием пользователя. */
    private var selfUntil = 0L
    /** id трека, после которого микс нужен всегда, даже при выключенной настройке (пара треков из выбора). */
    private var forceFor: String? = null
    /** Ручной микс «прямо сейчас»: ключ трека, позиция, где стартует переход, и крайний срок подготовки хвоста. */
    private var manualKey: String? = null
    private var manualAt = 0L
    private var manualDeadline = 0L
    /** Отрывок для пары микса: трек [outFor] уходит с позиции [outMs] (-1 — как обычно, ближе к концу), трек [inFor] входит с позиции [inMs]. */
    private var outFor: String? = null
    private var outMs = -1L
    private var inFor: String? = null
    private var inMs = 0L

    private val tailListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            AppLog.w("Crossfade", "хвостовой плеер: ${error.errorCodeName}")
            failedKey = armedKey
            if (fading) finish() else disarm()
        }
    }

    private val mainListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) { if (fading) finish() else disarm() }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (SystemClock.uptimeMillis() < selfUntil) return
            // Пользователь сам переключил трек или очередь сменилась — микс отменяется.
            if (forceFor != null && mediaItem?.mediaId != forceFor) forceFor = null
            if (fading) finish() else disarm()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int,
        ) {
            if (SystemClock.uptimeMillis() < selfUntil) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SKIP) {
                if (fading) finish() else disarm()
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val delay = runCatching { step() }.getOrElse { AppLog.w("Crossfade", "сбой: ${it.message}"); finish(); 500L }
            handler.postDelayed(this, delay)
        }
    }

    fun start() {
        if (running) return
        running = true
        main.addListener(mainListener)
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        runCatching { main.removeListener(mainListener) }
        finish()
        tail?.let { runCatching { it.release() } }
        tail = null
    }

    private fun currentKey() = "${main.currentMediaItem?.mediaId}#${main.currentMediaItemIndex}"

    /**
     * Микс пары треков: переход с трека [mediaId] на следующий делается с наложением, даже если микс выключен в настройках.
     * Вызывать сразу после запуска очереди, где [mediaId] играет первым.
     */
    fun forceNextTransitionFrom(mediaId: String) { forceFor = mediaId }

    /**
     * Отрывки пары: [aId] начинает уходить с позиции [outMs] (отрицательное — по умолчанию, ближе к концу трека),
     * а [bId] входит не с начала, а с позиции [inMs]. Действует только на переход пары (см. [forceNextTransitionFrom]).
     */
    fun setMixPoints(aId: String?, outMs: Long, bId: String?, inMs: Long) {
        outFor = aId; this.outMs = outMs
        inFor = bId; this.inMs = inMs.coerceAtLeast(0L)
        // Точка выхода поменялась — хвост нужно подготовить заново на нужное место.
        if (!fading) disarm()
    }

    /** Запустить микс текущего трека со следующим прямо сейчас (через пару секунд, пока готовится хвост). */
    fun mixNow() {
        manualKey = currentKey()
        manualDeadline = SystemClock.uptimeMillis() + 10_000
        disarm()
    }

    /** Ручной микс: готовим хвост чуть впереди и переходим, как только он готов. */
    private fun stepManual(key: String): Long {
        if (!main.hasNextMediaItem()) { manualKey = null; return 400L }
        if (!main.isPlaying) return 200L
        val dur = main.duration
        if (dur <= 0) return 200L
        val pos = main.currentPosition
        if (armedKey != key) {
            val at = pos + MANUAL_LEAD_MS
            if (dur - at < 1500) {   // конец трека уже рядом — обычная смена
                manualKey = null
                selfUntil = SystemClock.uptimeMillis() + 800
                main.seekToNextMediaItem()
                return 400L
            }
            manualAt = at
            manualDeadline = SystemClock.uptimeMillis() + 10_000
            arm(key, at)
            return 100L
        }
        val t = tail
        if (SystemClock.uptimeMillis() > manualDeadline || key == failedKey) {
            manualKey = null
            disarm()
            selfUntil = SystemClock.uptimeMillis() + 800
            main.seekToNextMediaItem()
            return 400L
        }
        if (t != null && t.playbackState == Player.STATE_READY && pos >= manualAt) {
            val fade = minOf(PlayerSettings.crossfadeSec * 1000L, dur - pos - 300).coerceAtLeast(800L)
            manualKey = null
            begin(t, fade)
            return 20L
        }
        return 40L
    }

    /** Один шаг: возвращает, через сколько мс вызвать его снова. */
    private fun step(): Long {
        if (fading) return stepFade()
        manualKey?.let { mk -> if (currentKey() == mk) return stepManual(mk) else manualKey = null }
        val forced = forceFor != null && forceFor == main.currentMediaItem?.mediaId
        if ((!PlayerSettings.crossfade && !forced) || !main.isPlaying || main.repeatMode == Player.REPEAT_MODE_ONE || !main.hasNextMediaItem()) {
            disarm()
            return 400L
        }
        val dur = main.duration
        val fade = PlayerSettings.crossfadeSec * 1000L
        val pos = main.currentPosition
        // Выбранная точка выхода: переход стартует в ней, а не перед концом трека. Если её уже проскочили — как обычно.
        val custom = forced && outMs > 0 && outFor == main.currentMediaItem?.mediaId &&
            dur > 0 && outMs <= dur - 1500 && pos <= outMs + 2000
        if (dur <= 0 || (!custom && dur < 2 * fade + 4000)) { disarm(); return 500L }
        val key = "${main.currentMediaItem?.mediaId}#${main.currentMediaItemIndex}"
        if (key == failedKey) return 500L
        val startAt = if (custom) outMs else dur - fade
        val untilStart = startAt - pos
        val fadeLen = if (custom) minOf(fade, dur - startAt).coerceAtLeast(500L) else fade
        return when {
            untilStart > ARM_AHEAD_MS -> {
                if (armedKey != null) disarm()
                (untilStart - ARM_AHEAD_MS).coerceIn(100L, 1000L)
            }
            untilStart > 0 -> {
                if (armedKey != key) arm(key, startAt)
                if (untilStart < 400) 30L else 100L
            }
            else -> {
                val t = tail
                if (armedKey == key && t != null && t.playbackState == Player.STATE_READY) {
                    begin(t, fadeLen)
                    20L
                } else {
                    // Не успел подготовиться — обычная смена трека.
                    failedKey = key
                    disarm()
                    500L
                }
            }
        }
    }

    private fun arm(key: String, atMs: Long) {
        val item = main.currentMediaItem ?: return
        val t = tail ?: newTail().also { it.addListener(tailListener); tail = it }
        armedKey = key
        t.volume = 0f
        t.playWhenReady = false
        t.playbackParameters = main.playbackParameters
        t.setMediaItem(item, atMs)
        t.prepare()
    }

    private fun disarm() {
        if (armedKey == null) return
        armedKey = null
        tail?.let { runCatching { it.stop(); it.clearMediaItems() } }
    }

    private fun begin(t: Player, fade: Long) {
        forceFor = null
        // Следующий трек пары входит с выбранного места, а не с начала.
        val nextIdx = main.nextMediaItemIndex
        val enterAt = if (nextIdx != C.INDEX_UNSET && inMs > 0 && inFor != null &&
            main.getMediaItemAt(nextIdx).mediaId == inFor) inMs else 0L
        baseVolume = main.volume.takeIf { it > 0.01f } ?: 1f
        fadeMs = fade
        fadeStart = SystemClock.uptimeMillis()
        fading = true
        t.seekTo(main.currentPosition)
        t.volume = baseVolume
        t.play()
        selfUntil = SystemClock.uptimeMillis() + 800
        main.volume = 0f
        if (enterAt > 0) main.seekTo(nextIdx, enterAt) else main.seekToNextMediaItem()
    }

    private fun stepFade(): Long {
        val p = ((SystemClock.uptimeMillis() - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
        main.volume = baseVolume * sin(p * PI.toFloat() / 2)
        tail?.volume = baseVolume * cos(p * PI.toFloat() / 2)
        if (p >= 1f || tail?.playbackState == Player.STATE_ENDED) { finish(); return 100L }
        return 40L
    }

    /** Завершает микс: хвост замолкает, основной плеер возвращается к обычной громкости. */
    private fun finish() {
        val wasFading = fading
        fading = false
        armedKey = null
        tail?.let { runCatching { it.stop(); it.clearMediaItems(); it.volume = 0f } }
        if (wasFading) main.volume = baseVolume
    }

    private companion object {
        /** За сколько до начала микса готовить хвостовой плеер. */
        const val ARM_AHEAD_MS = 5000L
        /** Ручной микс: через сколько после нажатия стартует переход (за это время готовится хвостовой плеер). */
        const val MANUAL_LEAD_MS = 2500L
    }
}
