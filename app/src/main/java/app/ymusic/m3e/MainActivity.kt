package app.ymusic.m3e

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.ymusic.m3e.ui.AppIconSettings
import app.ymusic.m3e.ui.AppRoot
import app.ymusic.m3e.ui.Haptics
import app.ymusic.m3e.ui.YMusicTheme
import app.ymusic.m3e.recognition.Recognizer
import app.ymusic.m3e.recognition.RecognitionService

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    companion object {
        const val EXTRA_OPEN_PLAYER = "app.ymusic.m3e.extra.OPEN_PLAYER"

        const val EXTRA_SEARCH_QUERY = "app.ymusic.m3e.extra.SEARCH_QUERY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppIconSettings.init(this)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (savedInstanceState == null) app.ymusic.m3e.data.Updates.checkOnLaunch()
        handleIntent(intent)
        setContent { YMusicTheme { AppRoot(vm) } }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        Haptics.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStart() {
        super.onStart()
        Recognizer.appForeground = true
    }

    override fun onStop() {
        Recognizer.appForeground = false
        vm.saveSession()
        if (!isChangingConfigurations) AppIconSettings.apply(this)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !app.ymusic.m3e.ui.UiLayout.clipLinks) return
        runCatching {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            val d = cm.primaryClipDescription
            if (d != null && (d.hasMimeType("text/plain") || d.hasMimeType("text/html"))) {
                vm.offerClipboard(cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.data?.scheme == "ymusicm3e") vm.onAuthRedirect(intent.data)
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme.let { it == "https" || it == "http" || it == "yandexmusic" }) {
            val raw = intent.data.toString()
            val link = app.ymusic.m3e.data.ShareLinks.parse(raw)
            app.ymusic.m3e.data.AppLog.i("Link", "VIEW-интент: $raw -> ${link ?: "не распознана"}")
            intent.data = null
            if (link != null) vm.requestLink(link)
        }
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme.let { it == "content" || it == "file" }) {
            val uri = intent.data
            intent.data = null
            if (uri != null) app.ymusic.m3e.data.PluginSystem.offerInstall(this, uri)
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_PLAYER)
            vm.requestOpenPlayer()
        }
        intent?.getStringExtra(EXTRA_SEARCH_QUERY)?.takeIf { it.isNotBlank() }?.let {
            intent.removeExtra(EXTRA_SEARCH_QUERY)
            runCatching { getSystemService(android.app.NotificationManager::class.java).cancel(RecognitionService.NOTIF_RESULT) }
            vm.requestSearch(it)
        }
    }
}
