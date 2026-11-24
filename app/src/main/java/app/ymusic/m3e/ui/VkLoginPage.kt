package app.ymusic.m3e.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import app.ymusic.m3e.data.AppLog
import app.ymusic.m3e.data.VkAuth

/**
 * Вход в VK Музыку — только через страницу VK прямо в приложении (WebView).
 *
 * Сразу открывается авторизация VK. Человек входит, VK перенаправляет на blank.html с `#access_token=…` в адресе —
 * адрес перехватывается ещё до загрузки страницы, токен проверяется и сохраняется ([AppViewModel.vkSubmitToken]).
 */
@Composable
internal fun VkLoginPage(vm: AppViewModel, onBack: () -> Unit) {
    val ui by vm.vkLogin.collectAsStateWithLifecycle()
    // Меняется при «Повторить»: WebView создаётся заново и снова открывает вход.
    var attempt by remember { mutableIntStateOf(0) }
    // Сначала окно входа VK в приложении; если оно не рисуется (бывает на ColorOS / realme UI), кнопка «Через браузер» открывает
    // инструкцию и вход через системный браузер.
    var browser by remember { mutableStateOf(false) }
    // Способ входа (клиент VK): если один отвечает ошибкой на странице входа, «Другой способ» берёт следующий.
    var variant by remember { mutableIntStateOf(0) }
    val loginUrl = VkAuth.loginUrl(VkAuth.LOGIN_VARIANTS[variant % VkAuth.LOGIN_VARIANTS.size])
    val nextVariant: () -> Unit = { variant++; attempt++; Unit }

    fun back() { vm.vkResetLogin(); onBack() }
    BackHandler { back() }

    Box(Modifier.fillMaxSize()) {
        if (browser) {
            androidx.compose.runtime.key(attempt) {
                VkBrowserLogin(loginUrl, onToken = { text -> vm.vkSubmitToken(text) }, onUseEmbedded = { browser = false }, onOtherWay = nextVariant)
            }
        } else {
            androidx.compose.runtime.key(attempt) {
                VkWebView(loginUrl, onToken = { url -> vm.vkSubmitToken(url) })
            }
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)) {
                if (VkAuth.LOGIN_VARIANTS.size > 1) TextButton(onClick = nextVariant) { Text("Другой способ") }
                TextButton(onClick = { browser = true }) { Text("Через браузер") }
            }
        }

        IconButton(
            onClick = { back() },
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
        ) { Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = "Назад") }

        // Токен получен: идёт проверка — страница закрыта индикатором.
        if (ui.busy) {
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Проверяем вход в VK…", style = MaterialTheme.typography.bodyLarge)
            }
        }

        ui.error?.let { err ->
            Column(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(err, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.vkResetLogin(); attempt++ }) { Text("Войти ещё раз") }
            }
        }
    }
}

/** Телефоны на ColorOS / OxygenOS / realme UI (Oplus): у их драйвера GPU WebView бывает «залит» фоном после скрытия оверлея. */
internal fun isOplusDevice(): Boolean {
    val id = (android.os.Build.MANUFACTURER + " " + android.os.Build.BRAND).lowercase()
    return listOf("oppo", "realme", "oneplus").any { it in id }
}

/** Программная отрисовка WebView на Oplus-телефонах: обходит баг композитинга (белый/пустой экран при живом вводе). */
internal fun android.webkit.WebView.applyOplusRenderFix() {
    if (isOplusDevice()) setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
}

// Размытие заднего фона (backdrop-filter) и will-change в вёрстке VK ID создают отдельные GPU-слои — их-то и теряет композитор.
private const val OPLUS_NO_LAYERS_JS =
    "(function(){try{var s=document.getElementById('__exfix');if(!s){s=document.createElement('style');s.id='__exfix';" +
        "(document.head||document.documentElement).appendChild(s);}" +
        "s.textContent='*,*::before,*::after{-webkit-backdrop-filter:none!important;backdrop-filter:none!important;will-change:auto!important}';}catch(e){}})()"

/** На Oplus: убрать «тяжёлые» слои на странице и принудительно перерисовать WebView. */
internal fun android.webkit.WebView.kickRepaintOnOplus() {
    if (!isOplusDevice()) return
    evaluateJavascript(OPLUS_NO_LAYERS_JS, null)
    postInvalidateOnAnimation()
}

/**
 * Страница авторизации VK. Адрес с access_token перехватывается в shouldOverrideUrlLoading / onPageStarted /
 * doUpdateVisitedHistory (на разных версиях WebView токен приходит через разные из них) и в загрузку не пускается.
 */
@Composable
private fun VkWebView(url: String, onToken: (String) -> Unit) {
    val webViewRef = remember { arrayOfNulls<android.webkit.WebView>(1) }
    DisposableEffect(Unit) {
        onDispose { webViewRef[0]?.let { runCatching { it.stopLoading(); it.destroy() } }; webViewRef[0] = null }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = 48.dp),
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                webViewRef[0] = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Обычный мобильный Chrome вместо «wv»: VK ID не любит встроенные браузеры.
                runCatching {
                    settings.userAgentString = android.webkit.WebSettings.getDefaultUserAgent(ctx)
                        .replace("; wv", "").replace(" Version/4.0", "")
                }
                // Чистая страница входа: можно выбрать другой аккаунт VK.
                runCatching {
                    val cm = android.webkit.CookieManager.getInstance()
                    cm.removeAllCookies(null)
                    cm.flush()
                }
                var done = false
                fun check(url: String?): Boolean {
                    if (done || url == null || !url.contains("access_token=")) return false
                    done = true
                    AppLog.i("VK", "Перехвачен адрес с токеном (токен в журнал не пишется)")
                    onToken(url)
                    return true
                }
                // ColorOS/OxygenOS/realme UI: после закрытия плашки «Войти через приложение» страница VK ID
                // перестаёт рисоваться (виден только фон), хотя ввод работает — обходим программной отрисовкой.
                applyOplusRenderFix()
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView, request: android.webkit.WebResourceRequest): Boolean =
                        check(request.url.toString())

                    override fun onPageStarted(view: android.webkit.WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        if (check(url)) view.stopLoading()
                    }

                    override fun onPageCommitVisible(view: android.webkit.WebView, url: String?) = view.kickRepaintOnOplus()

                    override fun onPageFinished(view: android.webkit.WebView, url: String?) = view.kickRepaintOnOplus()

                    override fun doUpdateVisitedHistory(view: android.webkit.WebView, url: String?, isReload: Boolean) {
                        if (check(url)) view.stopLoading() else view.kickRepaintOnOplus()
                    }

                    override fun onReceivedError(
                        view: android.webkit.WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError,
                    ) {
                        if (request.isForMainFrame) AppLog.w("VK", "Страница входа не открылась: ${error.description} (${request.url.host})")
                    }
                }
                loadUrl(url)
            }
        },
    )
}


private fun clipboardToken(ctx: android.content.Context): String? = runCatching {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
}.getOrNull()?.takeIf { VkAuth.parseToken(it) != null }

/**
 * Вход в VK через системный браузер — запасной вариант, когда WebView не рисует страницу (ColorOS и др.).
 * Страница VK открывается в браузере; после входа VK показывает пустую страницу с токеном в адресе. Адрес копируется
 * и вставляется сюда — приложение берёт токен само (при возврате из браузера ссылка из буфера обмена подхватывается автоматически).
 */
@Composable
private fun VkBrowserLogin(loginUrl: String, onToken: (String) -> Unit, onUseEmbedded: () -> Unit, onOtherWay: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var opened by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    fun submit(t: String) { if (!sent && VkAuth.parseToken(t) != null) { sent = true; onToken(t) } }

    // Вернулись из браузера: если в буфере обмена адрес с токеном — входим сразу.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, opened) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME && opened) clipboardToken(ctx)?.let { text = it; submit(it) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(top = 48.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Вход в VK через браузер", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Как войти", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                listOf(
                    "Нажмите «Открыть VK в браузере» и войдите. После входа откроется пустая страница — так и должно быть.",
                    "Скопируйте адрес из адресной строки целиком (в нём есть access_token=…).",
                    "Вернитесь в приложение: ссылка подхватится сама. Если нет — «Вставить», затем «Войти».",
                ).forEachIndexed { i, t ->
                    Text("${i + 1}. $t", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "Ссылка нужна только приложению, чтобы получить доступ к музыке. Пароль от VK приложение не видит.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                opened = true
                runCatching {
                    ctx.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(loginUrl))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { AppLog.w("VK", "Не удалось открыть браузер: ${it.message}") }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Открыть VK в браузере") }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("Ссылка после входа") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { clipboardToken(ctx)?.let { text = it; submit(it) } ?: run { clipboardText(ctx)?.let { text = it } } },
                modifier = Modifier.weight(1f),
            ) { Text("Вставить") }
            Button(
                onClick = { sent = false; submit(text) },
                enabled = VkAuth.parseToken(text) != null, modifier = Modifier.weight(1f),
            ) { Text("Войти") }
        }
        Spacer(Modifier.height(8.dp))
        // Если в браузере VK пишет «incorrect app» / «direct auth» — это не вы: переключитесь на другой способ и откройте страницу снова.
        if (VkAuth.LOGIN_VARIANTS.size > 1) TextButton(onClick = onOtherWay) { Text("Другой способ входа (если VK пишет ошибку)") }
        TextButton(onClick = onUseEmbedded) { Text("Встроенное окно входа") }
    }
}

private fun clipboardText(ctx: android.content.Context): String? = runCatching {
    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
}.getOrNull()
