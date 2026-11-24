package app.ymusic.m3e.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.content.res.Configuration
import android.os.Bundle
import android.graphics.Shader
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.media3.common.Player
import app.ymusic.m3e.App
import app.ymusic.m3e.MainActivity
import app.ymusic.m3e.R
import app.ymusic.m3e.player.PlaybackService
import java.net.HttpURLConnection
import java.net.URL

/** Единственный виджет приложения — плеер: обложка, название, исполнитель, назад / play-pause / вперёд и кнопка открытия приложения. */
class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlayerWidget.update(context)
    }

    // Пользователь изменил размер виджета — выбираем подходящий макет (обложка слева / сверху / без обложки).
    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        PlayerWidget.update(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (val a = intent.action) {
            PlayerWidget.ACTION_PREV, PlayerWidget.ACTION_NEXT, PlayerWidget.ACTION_TOGGLE -> PlayerWidget.handle(context, a)
            else -> super.onReceive(context, intent)
        }
    }
}

object PlayerWidget {
    const val ACTION_PREV = "app.ymusic.m3e.widget.PREV"
    const val ACTION_NEXT = "app.ymusic.m3e.widget.NEXT"
    const val ACTION_TOGGLE = "app.ymusic.m3e.widget.TOGGLE"

    /** Макет виджета зависит от его размера. */
    private enum class Variant(val layout: Int) {
        /** Обложка слева на всю высоту, справа текст и кнопки. */
        Row(R.layout.widget_player),
        /** Узкий, но высокий: обложка сверху, текст и кнопки снизу. */
        Top(R.layout.widget_player_top),
        /** Мелкий: без обложки. */
        Compact(R.layout.widget_player_compact),
    }

    private const val COVER_PX = 512
    private const val PAD_DP = 12
    private const val COVER_GAP_DP = 12
    private const val MIN_TEXT_W_DP = 140
    private const val MIN_ROW_H_DP = 80
    private const val MIN_TOP_H_DP = 180

    private val main = Handler(Looper.getMainLooper())
    private var placeholderKey: Int? = null
    private var placeholderBitmap: Bitmap? = null
    private var cachedUrl: String? = null
    private var cachedBitmap: Bitmap? = null
    private var loadingUrl: String? = null

    /** Нажатия кнопок виджета. Работает, пока жив процесс приложения (плеер живёт в нём). */
    fun handle(context: Context, action: String) {
        val app = App.instance
        if (!app.playerReady) { update(context); return }
        val p = app.player
        if (p.mediaItemCount > 0) {
            when (action) {
                ACTION_PREV -> p.seekToPrevious()
                ACTION_NEXT -> if (p.hasNextMediaItem()) p.seekToNextMediaItem()
                ACTION_TOGGLE -> {
                    val active = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
                    if (active) p.pause() else {
                        if (p.playbackState == Player.STATE_IDLE) p.prepare()
                        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0L)
                        p.play()
                        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
                    }
                }
            }
        }
        update(context)
    }

    /** Перерисовывает все экземпляры виджета по текущему состоянию плеера (вызывать в главном потоке). */
    fun update(context: Context) {
        val ctx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, PlayerWidgetProvider::class.java))
        if (ids.isEmpty()) return

        var title: String? = null
        var artist: String? = null
        var artwork: String? = null
        var playing = false
        var hasMedia = false
        val app = App.instance
        if (app.playerReady) {
            val p = app.player
            hasMedia = p.mediaItemCount > 0 && p.currentMediaItem != null
            if (hasMedia) {
                val md = p.currentMediaItem?.mediaMetadata
                title = md?.title?.toString()
                artist = md?.artist?.toString()
                artwork = md?.artworkUri?.toString()
                playing = p.playWhenReady && (p.playbackState == Player.STATE_READY || p.playbackState == Player.STATE_BUFFERING)
            }
        }
        // Если обложки нет (или она ещё грузится) — рисуем заглушку той же формы, чтобы макет не прыгал.
        val bmp = (if (artwork != null && artwork == cachedUrl) cachedBitmap else null) ?: placeholder(ctx)
        push(ctx, mgr, ids, hasMedia, title, artist, playing, bmp)

        if (artwork != null && artwork != cachedUrl && artwork != loadingUrl) {
            val url = artwork
            loadingUrl = url
            Thread {
                val b = loadCover(ctx, url)
                main.post {
                    loadingUrl = null
                    cachedUrl = url
                    cachedBitmap = b
                    update(ctx)
                }
            }.start()
        }
    }

    /** Выбор макета по текущему размеру экземпляра виджета (в dp). */
    private fun pickVariant(ctx: Context, mgr: AppWidgetManager, id: Int): Variant {
        val o = mgr.getAppWidgetOptions(id)
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (w <= 0 || h <= 0) return Variant.Row
        val iw = w - PAD_DP * 2
        val ih = h - PAD_DP * 2
        return when {
            // Обложка — квадрат на всю высоту; справа должно остаться место под текст и кнопки.
            ih >= MIN_ROW_H_DP && iw - ih - COVER_GAP_DP >= MIN_TEXT_W_DP -> Variant.Row
            // Узко, но высоко — обложка сверху.
            ih >= MIN_TOP_H_DP -> Variant.Top
            // Мелкий виджет — без обложки.
            else -> Variant.Compact
        }
    }

    private fun push(
        ctx: Context, mgr: AppWidgetManager, ids: IntArray,
        hasMedia: Boolean, title: String?, artist: String?, playing: Boolean, cover: Bitmap,
    ) {
        for (id in ids) {
            val variant = pickVariant(ctx, mgr, id)
            val v = RemoteViews(ctx.packageName, variant.layout)
            v.setTextViewText(R.id.w_title, if (hasMedia) title?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.app_name) else ctx.getString(R.string.widget_idle_title))
            v.setTextViewText(R.id.w_artist, if (hasMedia) artist.orEmpty() else ctx.getString(R.string.widget_idle_subtitle))
            v.setImageViewResource(R.id.w_play, if (playing) R.drawable.ms_pause else R.drawable.ms_play_arrow)
            v.setContentDescription(R.id.w_play, ctx.getString(if (playing) R.string.widget_pause else R.string.widget_play))
            if (variant != Variant.Compact) v.setImageViewBitmap(R.id.w_cover, cover)
            v.setOnClickPendingIntent(R.id.w_prev, broadcast(ctx, ACTION_PREV, 1))
            v.setOnClickPendingIntent(R.id.w_play, broadcast(ctx, ACTION_TOGGLE, 2))
            v.setOnClickPendingIntent(R.id.w_next, broadcast(ctx, ACTION_NEXT, 3))
            // Нажатие на обложку / текст — просто открыть приложение.
            val open = PendingIntent.getActivity(
                ctx, 4,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_info, open)
            if (variant != Variant.Compact) v.setOnClickPendingIntent(R.id.w_cover, open)
            // Кнопка «открыть» — приложение сразу с развёрнутым плеером.
            val openPlayer = PendingIntent.getActivity(
                ctx, 5,
                Intent(ctx, MainActivity::class.java)
                    .setAction("app.ymusic.m3e.OPEN_PLAYER")
                    .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_open, openPlayer)
            mgr.updateAppWidget(id, v)
        }
    }

    /** Скруглённая заглушка вместо обложки (когда ничего не играет или обложка ещё грузится). */
    private fun placeholder(ctx: Context): Bitmap {
        val key = ctx.resources.configuration.uiMode
        placeholderBitmap?.let { if (placeholderKey == key) return it }
        val size = COVER_PX
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = ctx.getColor(R.color.widget_btn_bg)
        val r = size * 0.23f
        c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, paint)
        ctx.getDrawable(R.drawable.ms_music_note)?.mutate()?.let { d ->
            d.setTint(ctx.getColor(R.color.widget_text_secondary))
            val icon = (size * 0.34f).toInt()
            val left = (size - icon) / 2
            d.setBounds(left, left, left + icon, left + icon)
            d.draw(c)
        }
        placeholderKey = key
        placeholderBitmap = out
        return out
    }

    private fun broadcast(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, PlayerWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Загружает обложку и сразу скругляет углы (RemoteViews не умеет обрезать по форме). */
    private fun loadCover(ctx: Context, url: String): Bitmap? = runCatching {
        val raw: Bitmap? = if (url.startsWith("http")) {
            // Через общий клиент приложения: обложки SoundCloud идут в обход блокировки.
            val http = app.ymusic.m3e.App.instance.http.newBuilder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS).readTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
            http.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.byteStream()?.let { BitmapFactory.decodeStream(it) }
            }
        } else {
            ctx.contentResolver.openInputStream(Uri.parse(url))?.use { BitmapFactory.decodeStream(it) }
        }
        raw?.let { roundedSquare(it, COVER_PX) }
    }.getOrNull()

    private fun roundedSquare(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val cropped = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val r = size * 0.23f
        Canvas(out).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), r, r, paint)
        return out
    }
}
