package app.ymusic.m3e.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.ymusic.m3e.MainActivity
import app.ymusic.m3e.R
import app.ymusic.m3e.ui.AppLang

/**
 * Push-уведомления о загрузке трека на SoundCloud: ход отправки файла (с полосой прогресса), обработка на сервере
 * и итог — «готов», «не принят», «удалён / заблокирован». Итог приходит отдельным всплывающим уведомлением,
 * поэтому о нём узнаешь, даже если приложение свёрнуто.
 */
object ScUploadNotifier {
    private const val CH_PROGRESS = "sc_upload_progress"
    private const val CH_RESULT = "sc_upload_result"
    private const val ID_PROGRESS = 7101
    private const val ID_RESULT = 7102

    private var lastUpdate = 0L
    private var lastPct = -1

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    private fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val m = nm(ctx)
        m.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, AppLang.tr("Загрузка на SoundCloud"), NotificationManager.IMPORTANCE_LOW).apply {
                description = AppLang.tr("Ход отправки трека на SoundCloud")
            },
        )
        m.createNotificationChannel(
            NotificationChannel(CH_RESULT, AppLang.tr("Итог загрузки на SoundCloud"), NotificationManager.IMPORTANCE_HIGH).apply {
                description = AppLang.tr("Трек готов, отклонён или удалён SoundCloud")
            },
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 7103,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Файл уходит на сервер. [pct] — 0..100; обновляется не чаще раза в 0,7 с. */
    fun uploading(ctx: Context, title: String, pct: Int, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (pct == lastPct || now - lastUpdate < 700)) return
        lastUpdate = now; lastPct = pct
        post(ctx, ID_PROGRESS, CH_PROGRESS) { b ->
            b.setSmallIcon(R.drawable.ms_cloud_download)
                .setContentTitle(AppLang.tr("Загрузка на SoundCloud"))
                .setContentText("«$title» · $pct%")
                .setProgress(100, pct.coerceIn(0, 100), false)
                .setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        }
    }

    /** Файл отправлен, SoundCloud его обрабатывает. */
    fun processing(ctx: Context, title: String) {
        post(ctx, ID_PROGRESS, CH_PROGRESS) { b ->
            b.setSmallIcon(R.drawable.ms_cloud_download)
                .setContentTitle(AppLang.tr("SoundCloud обрабатывает трек"))
                .setContentText("«$title»")
                .setProgress(0, 0, true)
                .setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        }
    }

    /** Итог: [ok] — трек готов; иначе [text] объясняет, что пошло не так. */
    fun result(ctx: Context, title: String, ok: Boolean, text: String) {
        runCatching { nm(ctx).cancel(ID_PROGRESS) }
        post(ctx, ID_RESULT, CH_RESULT) { b ->
            b.setSmallIcon(if (ok) R.drawable.ms_music_note else R.drawable.ms_cloud_off)
                .setContentTitle(AppLang.tr(if (ok) "Трек загружен на SoundCloud" else "Загрузка на SoundCloud не удалась"))
                .setContentText(if (ok) "«$title»" else text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (ok) "«$title» · $text" else "«$title»\n$text"))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }
    }

    /** Убирает уведомление о ходе загрузки (например, если итог показывать не нужно). */
    fun clearProgress(ctx: Context) {
        runCatching { nm(ctx).cancel(ID_PROGRESS) }
        lastPct = -1
    }

    private fun post(ctx: Context, id: Int, channel: String, build: (NotificationCompat.Builder) -> Unit) {
        runCatching {
            val app = ctx.applicationContext
            ensureChannels(app)
            val b = NotificationCompat.Builder(app, channel).setContentIntent(openApp(app))
            build(b)
            // Без разрешения на уведомления notify просто ничего не покажет.
            nm(app).notify(id, b.build())
        }
    }
}
