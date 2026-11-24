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
        /** Extra из виджета: открыть приложение с развёрнутым плеером. */
        const val EXTRA_OPEN_PLAYER = "app.ymusic.m3e.extra.OPEN_PLAYER"

        /** Extra из распознавания музыки: открыть поиск с этим запросом. */
        const val EXTRA_SEARCH_QUERY = "app.ymusic.m3e.extra.SEARCH_QUERY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppIconSettings.init(this)
        // Спрашиваем про уведомления только при чистом запуске и только если доступа ещё нет:
        // после смены разрешений в настройках система перезапускает процесс, и диалог не должен вылезать снова.
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // Проверка обновлений при запуске (если включена в Настройки → Обновления): только при чистом старте.
        if (savedInstanceState == null) app.ymusic.m3e.data.Updates.checkOnLaunch()
        handleIntent(intent)
        setContent { YMusicTheme { AppRoot(vm) } }
    }

    // Все касания проходят здесь: из них вибро берёт силу и скорость нажатия.
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        Haptics.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun onStart() {
        super.onStart()
        // Распознавание музыки: по этому признаку сервис понимает, открылось ли приложение само.
        Recognizer.appForeground = true
    }

    override fun onStop() {
        Recognizer.appForeground = false
        // Уходим в фон: запоминаем трек и позицию, чтобы после перезапуска мини-плеер был готов продолжить.
        vm.saveSession()
        // Выбранная в настройках иконка приложения включается в системе, когда приложение уходит в фон
        // (не при повороте экрана: смена иконки на лету на части прошивок закрывает приложение).
        if (!isChangingConfigurations) AppIconSettings.apply(this)
        super.onStop()
    }

    // Запасной путь для ссылок: некоторые прошивки не отдают ссылки music.yandex.* приложению. Когда окно получило фокус,
    // смотрим, нет ли в буфере обмена такой ссылки, и предлагаем её открыть (Настройки → Редактор интерфейса → Ссылки).
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !app.ymusic.m3e.ui.UiLayout.clipLinks) return
        runCatching {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            val d = cm.primaryClipDescription
            if (d != null && (d.hasMimeType("text/plain") || d.hasMimeType("text/html"))) {
                // Метка времени копирования: ту же ссылку, скопированную ещё раз, предложим снова.
                vm.offerClipboard(cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString(), d.timestamp)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.data?.scheme == "ymusicm3e") vm.onAuthRedirect(intent.data)
        // Ссылка на трек / альбом / исполнителя / плейлист Яндекс Музыки (в том числе из «Поделиться»): открываем страницу в приложении.
        if (intent?.action == Intent.ACTION_VIEW && intent.data?.scheme.let { it == "https" || it == "http" || it == "yandexmusic" }) {
            val raw = intent.data.toString()
            val link = app.ymusic.m3e.data.ShareLinks.parse(raw)
            app.ymusic.m3e.data.AppLog.i("Link", "VIEW-интент: $raw -> ${link ?: "не распознана"}")
            intent.data = null
            if (link != null) vm.requestLink(link)
        }
        // Ссылка, отправленная через «Поделиться» → ExMusic (из браузера, SoundCloud, мессенджера): ищем в тексте ссылку и открываем.
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            intent.removeExtra(Intent.EXTRA_TEXT)
            val link = text?.let { app.ymusic.m3e.data.ShareLinks.parseFromText(it) }
            app.ymusic.m3e.data.AppLog.i("Link", "SEND-интент: $text -> ${link ?: "не распознана"}")
            if (link != null) vm.requestLink(link)
        }
        // Файл плагина (.exm), открытый из файлового менеджера: показываем окно установки.
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
            // Пришли с найденным треком: уведомление с результатом больше не нужно.
            runCatching { getSystemService(android.app.NotificationManager::class.java).cancel(RecognitionService.NOTIF_RESULT) }
            vm.requestRecognizedSearch(it)
        }
    }
}
