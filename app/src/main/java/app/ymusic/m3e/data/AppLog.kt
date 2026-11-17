package app.ymusic.m3e.data

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Process
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

object AppLog {
    private const val MAX_BYTES = 1_500_000L

    private val lock = Any()
    private lateinit var dir: File
    private lateinit var current: File
    private lateinit var previous: File
    private var writer: Writer? = null
    private var written = 0L
    private var inited = false
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(app: Application) {
        synchronized(lock) {
            if (inited) return
            inited = true
            dir = File(app.filesDir, "logs").apply { mkdirs() }
            current = File(dir, "current.log")
            previous = File(dir, "previous.log")
            runCatching {
                if (current.exists() && current.length() > 0) {
                    previous.delete()
                    if (!current.renameTo(previous)) {
                        current.copyTo(previous, overwrite = true)
                        current.delete()
                    }
                }
            }
            openWriter(append = false)
        }
        header(app)
        installCrashHandler()
        registerLifecycle(app)
        startLogcat()
    }

    private fun header(app: Application) {
        val ver = runCatching {
            val pi = app.packageManager.getPackageInfo(app.packageName, 0)
            pi.versionName
        }.getOrNull() ?: "?"
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        raw("==================== ЗАПУСК ПРИЛОЖЕНИЯ ====================")
        raw("Время: $stamp")
        raw("Версия: $ver · пакет ${app.packageName}")
        raw("Устройство: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        raw("PID: ${Process.myPid()}")
        raw("============================================================")
        i("App", "Процесс запущен (Application.onCreate)")
    }

    private fun openWriter(append: Boolean) {
        runCatching { writer?.close() }
        writer = runCatching {
            OutputStreamWriter(FileOutputStream(current, append), Charsets.UTF_8) as Writer
        }.getOrNull()
        written = if (append) current.length() else 0L
    }

    private fun raw(line: String) {
        synchronized(lock) {
            if (!inited) return
            runCatching {
                if (written > MAX_BYTES) trimLocked()
                val w = writer ?: return
                val s = line + "\n"
                w.write(s)
                w.flush()
                written += s.length
            }
        }
    }

    private fun trimLocked() {
        runCatching {
            writer?.close()
            val text = current.readText()
            val tail = text.substring(text.length / 2).substringAfter('\n')
            current.writeText("--- начало журнала обрезано (слишком большой) ---\n$tail")
            openWriter(append = true)
        }
    }

    private fun log(level: Char, tag: String, msg: String, t: Throwable? = null) {
        val sb = StringBuilder()
        sb.append(synchronized(fmt) { fmt.format(Date()) }).append(" [APP] ").append(level).append('/').append(tag)
            .append(": ").append(msg)
        if (t != null) sb.append('\n').append(android.util.Log.getStackTraceString(t).trimEnd())
        raw(sb.toString())
    }

    fun d(tag: String, msg: String) = log('D', tag, msg)
    fun i(tag: String, msg: String) = log('I', tag, msg)
    fun w(tag: String, msg: String, t: Throwable? = null) = log('W', tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log('E', tag, msg, t)

    fun currentFile(): File? = if (inited) current else null

    fun previousFile(): File? = if (inited && previous.exists() && previous.length() > 0) previous else null

    fun read(file: File?, maxChars: Int = 0): String {
        if (file == null) return ""
        return synchronized(lock) {
            runCatching {
                val text = file.readText()
                if (maxChars > 0 && text.length > maxChars) text.substring(text.length - maxChars) else text
            }.getOrDefault("")
        }
    }

    private fun installCrashHandler() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, ex ->
            runCatching { e("CRASH", "Необработанное исключение в потоке ${th.name}", ex) }
            runCatching { raw("==================== АВАРИЙНОЕ ЗАКРЫТИЕ ====================") }
            old?.uncaughtException(th, ex)
        }
    }

    private fun registerLifecycle(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            private fun n(a: Activity) = a.javaClass.simpleName
            override fun onActivityCreated(a: Activity, s: Bundle?) =
                i("Lifecycle", "ОТКРЫТИЕ ${n(a)}: onCreate (${if (s == null) "чистый запуск" else "восстановление состояния"})")
            override fun onActivityStarted(a: Activity) = i("Lifecycle", "${n(a)}: onStart")
            override fun onActivityResumed(a: Activity) = i("Lifecycle", "${n(a)}: onResume — на экране")
            override fun onActivityPaused(a: Activity) = i("Lifecycle", "${n(a)}: onPause")
            override fun onActivityStopped(a: Activity) = i("Lifecycle", "${n(a)}: onStop — свёрнуто/закрыто")
            override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(a: Activity) = i(
                "Lifecycle",
                "ЗАКРЫТИЕ ${n(a)}: onDestroy (finishing=${a.isFinishing}, смена конфигурации=${a.isChangingConfigurations})",
            )
        })
        app.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(c: android.content.res.Configuration) {}
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = w("System", "onLowMemory")
            override fun onTrimMemory(level: Int) = d("System", "onTrimMemory level=$level")
        })
    }

    private fun startLogcat() {
        thread(name = "AppLog-logcat", isDaemon = true) {
            var p: java.lang.Process? = null
            try {
                p = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "threadtime", "--pid=${Process.myPid()}"))
                p.inputStream.bufferedReader().useLines { lines -> lines.forEach { raw(it) } }
            } catch (t: Throwable) {
                w("AppLog", "logcat недоступен: ${t.message}")
            } finally {
                runCatching { p?.destroy() }
            }
        }
    }
}
