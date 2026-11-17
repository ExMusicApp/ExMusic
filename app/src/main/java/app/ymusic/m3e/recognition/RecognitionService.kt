package app.ymusic.m3e.recognition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.ymusic.m3e.MainActivity
import app.ymusic.m3e.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RecognitionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                job?.cancel()
                Recognizer.reset()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> startListening(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startListening(intent: Intent) {
        val source = runCatching { RecSource.valueOf(intent.getStringExtra(EXTRA_SOURCE).orEmpty()) }.getOrDefault(RecSource.Mic)
        if (job?.isActive == true) return
        val secs = RecognitionSettings.clipSeconds
        Recognizer.set(RecState.Listening(source, secs))
        startAsForeground(source)
        job = scope.launch {
            var projection: MediaProjection? = null
            try {
                if (source == RecSource.Device) {
                    val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                    @Suppress("DEPRECATION")
                    val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
                    check(data != null) { "Нет разрешения на запись звука устройства" }
                    val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val mp = checkNotNull(mpm.getMediaProjection(code, data)) { "Не удалось получить доступ к звуку устройства" }
                    projection = mp
                    mp.registerCallback(object : MediaProjection.Callback() {}, Handler(mainLooper))
                }
                val result = Recognizer.listen(source, projection, secs)
                Recognizer.set(result)
                (result as? RecState.Found)?.let { deliver(it.result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Recognizer.set(RecState.Error(e.message ?: "Не удалось записать звук"))
            } finally {
                runCatching { projection?.stop() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startAsForeground(source: RecSource) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Распознавание музыки", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecognitionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ms_graphic_eq)
            .setContentTitle("Слушаю музыку…")
            .setContentText(if (source == RecSource.Device) "Звук с устройства" else "Микрофон")
            .setOngoing(true)
            .addAction(0, "Отмена", stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            val type = if (source == RecSource.Device) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            else if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
            startForeground(NOTIF_LISTENING, n, type)
        } else {
            startForeground(NOTIF_LISTENING, n)
        }
    }

    private suspend fun deliver(r: RecResult) {
        if (Recognizer.uiShown > 0) return
        when (RecognitionSettings.onFound) {
            FoundAction.Notify -> notifyResult(r)
            FoundAction.Both -> { notifyResult(r); openApp(r) }
            FoundAction.OpenApp -> {
                openApp(r)
                delay(1200)
                if (!Recognizer.appForeground) notifyResult(r)
            }
        }
    }

    private fun openApp(r: RecResult) {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SEARCH_QUERY, r.query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private suspend fun notifyResult(r: RecResult) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULT, "Результат распознавания", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Название и исполнитель найденного трека"
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SEARCH_QUERY, r.query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cover = r.cover?.let { Recognizer.fetchCover(it) }
        val b = NotificationCompat.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ms_music_note)
            .setContentTitle(r.title)
            .setContentText(r.artist.ifBlank { "Найти в Яндекс Музыке" })
            .setSubText("Найти в Яндекс Музыке")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (cover != null) b.setLargeIcon(cover)
        runCatching { nm.notify(NOTIF_RESULT, b.build()) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "app.ymusic.m3e.recognition.START"
        const val ACTION_STOP = "app.ymusic.m3e.recognition.STOP"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "recognition"
        private const val CHANNEL_RESULT = "recognition_result"
        private const val NOTIF_LISTENING = 4201

        const val NOTIF_RESULT = 4202

        fun canOpenFromBackground(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        fun start(ctx: Context, source: RecSource, resultCode: Int = 0, data: Intent? = null) {
            if (Recognizer.busy) return
            val i = Intent(ctx, RecognitionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SOURCE, source.name)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
            if (data != null) i.putExtra(EXTRA_DATA, data)
            ctx.startForegroundService(i)
        }

        fun cancel(ctx: Context) {
            ctx.startService(Intent(ctx, RecognitionService::class.java).setAction(ACTION_STOP))
        }
    }
}
