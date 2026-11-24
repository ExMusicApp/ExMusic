package app.ymusic.m3e.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ymusic.m3e.data.StationCookies

/**
 * Вход в Яндекс для управления колонкой по Wi-Fi (Glagol).
 *
 * Как в PulseSync: cookies берутся из сессии браузера (там — Electron session, здесь — WebView), из них main-часть получает
 * x_token и «производный» music_token (см. data/YandexStation.kt). Cookies хранит сам WebView, приложение их никуда не копирует и не логирует.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StationLoginDialog(onDone: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Вход в Яндекс для колонок",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("Закрыть") }
                }
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            var finished = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean =
                                    request.url.scheme != "https"

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    maybeDone(view, url)
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    maybeDone(view, url)
                                }

                                private fun maybeDone(view: WebView, url: String?) {
                                    if (finished) return
                                    val u = url.orEmpty()
                                    // Вход завершён: появилась сессия, и мы ушли со страниц авторизации.
                                    val onAuthPage = u.contains("passport.yandex.") && (u.contains("/auth") || u.contains("/registration"))
                                    if (!onAuthPage && StationCookies.hasSession()) {
                                        finished = true
                                        cm.flush()
                                        view.post { onDone() }
                                    }
                                }
                            }
                            loadUrl("https://passport.yandex.ru/auth?retpath=" + android.net.Uri.encode("https://yandex.ru/quasar"))
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }
        }
    }
}
