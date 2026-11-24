package app.ymusic.m3e.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ymusic.m3e.AppViewModel
import app.ymusic.m3e.R
import app.ymusic.m3e.data.SpotifyAuth

/** Достаёт токен веб-плеера из страницы open.spotify.com (JSON сессии в <script id="session">); пусто, если его там нет. */
private const val SESSION_TOKEN_JS =
    "(function(){try{var e=document.getElementById('session');if(e){var o=JSON.parse(e.textContent);if(o&&!o.isAnonymous&&o.accessToken)return o.accessToken}}catch(x){}return ''})()"

/**
 * Вход в Spotify: страница входа Spotify открывается прямо в приложении (WebView). Когда вход пройден, Spotify выставляет куки сессии —
 * они забираются из WebView, сохраняются, и по ним загружается профиль (ник и аватар). Пароль приложение не видит.
 */
@Composable
internal fun SpotifyLoginPage(vm: AppViewModel, onBack: () -> Unit) {
    val ui by vm.spLogin.collectAsStateWithLifecycle()
    // Номер попытки: «Войти снова» пересоздаёт страницу. Куки прежней сессии стираются один раз на попытку (и не при повороте экрана).
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    @Suppress("UNUSED_VARIABLE")
    val started = rememberSaveable(attempt) { vm.spBeginLogin(); true }

    fun back() { vm.spResetLogin(); onBack() }
    BackHandler { back() }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            ui.busy -> Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Завершаем вход…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            ui.error != null -> Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Вход через Spotify", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { attempt++ },
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(56.dp),
                    shape = rounded(28),
                ) { Text("Войти снова") }
            }

            else -> SpotifyWebLogin(key = attempt, onSession = { cookies, token -> vm.spSubmitSession(cookies, token) })
        }
        IconButton(onClick = { back() }, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
            Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = "Назад")
        }
    }
}

/** WebView со страницей входа Spotify. Как только на open.spotify.com появилась сессия (куки sp_dc), отдаёт куки и токен наружу. */
@Composable
private fun SpotifyWebLogin(key: Int, onSession: (cookies: String, token: String?) -> Unit) {
    androidx.compose.runtime.key(key) {
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(top = 48.dp),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Без метки «; wv»: некоторые страницы входа иначе считают WebView «не настоящим браузером».
                    settings.userAgentString = settings.userAgentString.replace("; wv", "")
                    applyOplusRenderFix() // ColorOS: тот же баг пустой отрисовки WebView, что и при входе в VK
                    val cm = android.webkit.CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(this, true)
                    var done = false
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: android.webkit.WebView, url: String?) {
                            view.kickRepaintOnOplus()
                            if (done) return
                            val host = runCatching { android.net.Uri.parse(url).host }.getOrNull().orEmpty()
                            if (host != "open.spotify.com") return
                            val cookies = SpotifyAuth.webCookies()
                            if (!SpotifyAuth.hasSession(cookies)) return
                            done = true
                            cm.flush()
                            view.evaluateJavascript(SESSION_TOKEN_JS) { raw ->
                                val token = raw?.trim()?.trim('"')?.takeIf { it.isNotBlank() && it != "null" }
                                onSession(cookies, token)
                            }
                        }
                    }
                    loadUrl(SpotifyAuth.WEB_LOGIN_URL)
                }
            },
            onRelease = { it.stopLoading(); it.destroy() },
        )
    }
}
