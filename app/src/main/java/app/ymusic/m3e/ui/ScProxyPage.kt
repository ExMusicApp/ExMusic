@file:OptIn(ExperimentalMaterial3Api::class)

package app.ymusic.m3e.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R
import app.ymusic.m3e.data.ByeDpiLink
import app.ymusic.m3e.data.ScProxy
import app.ymusic.m3e.data.ScProxyConfig
import app.ymusic.m3e.data.ScProxyMode
import app.ymusic.m3e.data.ScProxySettings
import app.ymusic.m3e.data.ScProxyType
import app.ymusic.m3e.data.ScUpstream
import app.ymusic.m3e.data.TlsSplit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Короткая подпись режима для пункта в настройках. */
internal fun scProxySummary(): String {
    val c = ScProxySettings.config
    return when (c.mode) {
        ScProxyMode.OFF -> "Выключен"
        ScProxyMode.BUILTIN -> "Встроенный: DoH и дробление TLS"
        ScProxyMode.CUSTOM -> when {
            !c.upstream.valid -> "Свой прокси не задан"
            isByeDpi(c) -> "ByeByeDPI (SOCKS5 ${c.upstream.host}:${c.upstream.port})"
            else -> "Свой прокси: ${c.upstream.type.title} ${c.upstream.host}:${c.upstream.port}"
        }
    }
}

/** Обход блокировки SoundCloud: режим, встроенный обход, свой прокси и проверка соединения. */
@Composable
internal fun ScProxyPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = ScProxySettings.config

    Column(Modifier.fillMaxSize()) {
        SubPageBar("Обход блокировки SoundCloud (BETA)", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = 24.dp + dockInset())) {

            SettingsSection("Режим", R.drawable.ms_cloud_off, startExpanded = true) {
                Text(
                    "Встроенный обход пока не работает как надо.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    "Действует только на SoundCloud (soundcloud.com и sndcdn.com): поиск, треки, обложки, скачивание. " +
                        "Остальные сервисы приложения ходят напрямую.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ModeRow(ScProxyMode.OFF, "Запросы идут напрямую, как обычно", cfg.mode)
                ModeRow(ScProxyMode.BUILTIN, "Адреса через DNS-over-HTTPS, рукопожатие TLS дробится против DPI. Свой сервер не нужен", cfg.mode)
                ModeRow(
                    ScProxyMode.CUSTOM,
                    if (cfg.upstream.valid) "${cfg.upstream.type.title} ${cfg.upstream.host}:${cfg.upstream.port}" else "Свой HTTP- или SOCKS5-прокси (настройте ниже)",
                    cfg.mode,
                )
                if (cfg.mode == ScProxyMode.CUSTOM && !cfg.upstream.valid) {
                    Text(
                        "Прокси не задан, поэтому SoundCloud сейчас ходит напрямую. Заполните данные ниже и сохраните.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (cfg.mode == ScProxyMode.BUILTIN) {
                SettingsSection("Встроенный обход", R.drawable.ms_settings, startExpanded = true) {
                    Text(
                        "Первый пакет TLS-рукопожатия (в нём открытым текстом лежит имя сайта) режется на части, чтобы DNS и DPI провайдера " +
                            "не опознали SoundCloud. Шифрование и проверка сертификата не затрагиваются. Если приём не срабатывает, " +
                            "приложение само пробует следующий и запоминает рабочий.\n\n" +
                            "Не помогает, если провайдер блокирует сами IP-адреса SoundCloud: тогда нужен свой прокси. Если встроенный обход не " +
                            "справляется, попробуйте раздел «ByeByeDPI» ниже: у него больше приёмов, а ExMusic сам подберёт для него стратегию.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    FragRow(-1, "Автоматически", "Подбирать приём самому", cfg.frag)
                    (0 until TlsSplit.STRATEGIES).forEach { i ->
                        val hint = when (i) {
                            0 -> "Первый пакет режется надвое посередине имени сайта"
                            1 -> "ClientHello раскладывается по двум TLS-записям"
                            2 -> "Три TLS-записи: до имени, половина имени, остальное"
                            3 -> "Только DNS-over-HTTPS, без дробления"
                            4 -> "Первый кусок уходит с TTL=1 и доходит позже второго (как disorder в ByeByeDPI)"
                            5 -> "Между частями имени вставляется срочный байт: сервер его отбрасывает, DPI сбивается"
                            else -> "Две TLS-записи, первая доходит позже (TLS-записи и disorder)"
                        }
                        FragRow(i, TlsSplit.STRATEGY_NAMES[i].replaceFirstChar { it.uppercase() }, hint, cfg.frag)
                    }
                }
            }

            ByeDpiSection(ctx, cfg)

            key(cfg.upstream) { UpstreamSection(ctx, cfg.upstream, startExpanded = cfg.mode == ScProxyMode.CUSTOM) }

            SettingsSection("Проверка", R.drawable.ms_check, startExpanded = true) {
                var busy by remember { mutableStateOf(false) }
                var results by remember { mutableStateOf<List<ScProxy.TestResult>?>(null) }
                Text(
                    "Проверка идёт текущим способом: ${scProxySummary().lowercase()}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Button(
                    onClick = {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                results = withContext(Dispatchers.IO) { ScProxy.test() }
                                busy = false
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(56.dp),
                ) { Text(if (busy) "Проверяем…" else "Проверить соединение") }
                results?.forEach { r ->
                    Text(
                        (if (r.ok) "✓ " else "✕ ") + r.name + ": " + r.detail + ", ${r.ms} мс",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (r.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                Text(
                    "Любой ответ сервера (даже «HTTP 403») означает, что соединение дошло. «✕» — до SoundCloud достучаться не удалось.\n\n" +
                        "Вход в аккаунт SoundCloud открывается в браузере телефона и этим способом не обходится: если сайт не открывается " +
                        "в браузере, войдите через VPN, дальше приложение работает само.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun isByeDpi(c: ScProxyConfig): Boolean =
    c.mode == ScProxyMode.CUSTOM && c.upstream.type == ScProxyType.SOCKS5 && c.upstream.host == "127.0.0.1" && c.upstream.port == ByeDpiLink.PORT

/** Связка с приложением ByeByeDPI: ExMusic подбирает для него стратегию и вставляет её в его настройки. */
@Composable
private fun ByeDpiSection(ctx: Context, cfg: ScProxyConfig) {
    SettingsSection("ByeByeDPI", R.drawable.ms_settings, startExpanded = false) {
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
        var busy by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf("") }
        val cancel = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        DisposableEffect(Unit) { onDispose { cancel.set(true) } }

        val installed = ByeDpiLink.installed(ctx)
        val found = ScProxySettings.byeStrategies
        val chosen = ScProxySettings.byeChosen

        Text(
            "ByeByeDPI — популярное в России приложение против DPI. ExMusic умеет работать через него: SoundCloud идёт в локальный " +
                "SOCKS5 ByeByeDPI (127.0.0.1:${ByeDpiLink.PORT}), остальные сервисы ходят напрямую.\n\n" +
                "1. Установите ByeByeDPI. В его настройках должен быть включён режим «Командная строка» (по умолчанию так и есть). " +
                "Режим «Прокси» или VPN — любой; для VPN его один раз нужно запустить самому и дать разрешение.\n" +
                "2. Нажмите «Подобрать стратегии»: ExMusic сам запускает ByeByeDPI с разными стратегиями и проверяет, открываются ли API, " +
                "обложки и потоки SoundCloud.\n" +
                "3. Когда стратегии найдены, «Использовать ByeByeDPI» станет доступна: она вставит выбранную стратегию в ByeByeDPI и " +
                "направит SoundCloud через него.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            when {
                !installed -> "ByeByeDPI не найден на этом телефоне"
                isByeDpi(cfg) -> "ByeByeDPI установлен, SoundCloud идёт через него"
                else -> "ByeByeDPI установлен"
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        // Подбор стратегий
        Button(
            onClick = {
                if (!busy) {
                    busy = true; status = null; cancel.set(false); progress = "Запускаем ByeByeDPI…"
                    scope.launch {
                        val res = withContext(Dispatchers.IO) {
                            ByeDpiLink.probe(ctx, maxFound = 3, cancelled = { cancel.get() }) { checked, total, n ->
                                progress = "Проверено $checked из $total, найдено рабочих: $n"
                            }
                        }
                        busy = false
                        if (res.found.isNotEmpty()) {
                            ScProxySettings.setByeStrategies(res.found)
                            status = "Найдено стратегий: ${res.found.size}. Теперь можно нажать «Использовать ByeByeDPI»." to true
                        } else if (res.startFailed) {
                            status = "ByeByeDPI не запустился. Откройте его, нажмите «Подключиться» (в режиме VPN дайте разрешение) и повторите подбор." to false
                        } else if (!res.cancelled) {
                            status = "Ни одна из ${res.checked} стратегий не открыла SoundCloud. Если провайдер блокирует сами IP-адреса, нужен свой прокси или VPN." to false
                        } else status = "Подбор остановлен" to false
                    }
                }
            },
            enabled = installed && !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(56.dp),
        ) { Text(if (busy) "Подбираем…" else if (found.isEmpty()) "Подобрать стратегии" else "Подобрать заново") }
        if (busy) {
            Text(progress, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            FilledTonalButton(onClick = { cancel.set(true) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { Text("Остановить") }
        }

        // Найденные стратегии
        if (found.isNotEmpty()) {
            Text(
                "Найденные стратегии (быстрейшая первой). Выбранная вставляется в ByeByeDPI:",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            found.forEachIndexed { i, st ->
                ListItem(
                    headlineContent = { Text(st.cmd.replace("{sni}", "google.com"), style = MaterialTheme.typography.bodySmall) },
                    supportingContent = { Text("${st.ms} мс") },
                    leadingContent = { RadioButton(selected = chosen == i, onClick = null) },
                    modifier = Modifier.clickable(enabled = !busy) { ScProxySettings.chooseByeStrategy(i) },
                )
            }
        }

        // Использовать: доступно только когда есть подобранные стратегии
        Button(
            onClick = {
                val cmd = found.getOrNull(chosen)?.cmd ?: return@Button
                ScProxySettings.setUpstream(ByeDpiLink.upstream)
                ScProxySettings.setMode(ScProxyMode.CUSTOM)
                status = "Вставляем стратегию в ByeByeDPI…" to true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { ByeDpiLink.applyBlocking(ctx, cmd) }
                    status = if (ok) "Готово: стратегия вставлена в ByeByeDPI, SoundCloud идёт через него." to true
                    else "Стратегия записана, но ByeByeDPI не запустился: откройте его и нажмите «Подключиться»." to false
                }
            },
            enabled = installed && found.isNotEmpty() && !busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(56.dp),
        ) { Text("Использовать ByeByeDPI") }
        if (found.isEmpty()) {
            Text(
                "Кнопка станет доступна, когда подберутся стратегии.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        FilledTonalButton(
            onClick = {
                status = if (!installed) {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/romanvht/ByeByeDPI/releases/latest"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    "Открыта страница загрузки ByeByeDPI" to true
                } else if (ByeDpiLink.start(ctx)) {
                    "Запрос на запуск отправлен. Если ничего не произошло, откройте ByeByeDPI и нажмите «Подключиться» сами." to true
                } else "Не удалось запустить ByeByeDPI: откройте его и нажмите «Подключиться»." to false
            },
            enabled = !busy,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) { Text(if (installed) "Запустить ByeByeDPI" else "Скачать ByeByeDPI") }
        status?.let { (msg, ok) ->
            Text(
                msg, style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ModeRow(mode: ScProxyMode, hint: String, current: ScProxyMode) {
    ListItem(
        headlineContent = { Text(mode.title) },
        supportingContent = { Text(hint) },
        leadingContent = { RadioButton(selected = current == mode, onClick = null) },
        modifier = Modifier.clickable { ScProxySettings.setMode(mode) },
    )
}

@Composable
private fun FragRow(value: Int, title: String, hint: String, current: Int) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        leadingContent = { RadioButton(selected = current == value, onClick = null) },
        modifier = Modifier.clickable { ScProxySettings.setFrag(value) },
    )
}

@Composable
private fun UpstreamSection(ctx: Context, saved: ScUpstream, startExpanded: Boolean) {
    SettingsSection("Свой прокси", R.drawable.ms_cloud_download, startExpanded = startExpanded) {
        var link by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(saved.type) }
        var host by remember { mutableStateOf(saved.host) }
        var port by remember { mutableStateOf(if (saved.port > 0) saved.port.toString() else "") }
        var user by remember { mutableStateOf(saved.user) }
        var pass by remember { mutableStateOf(saved.pass) }
        var linkBad by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

        fun applyLink(text: String) {
            link = text
            status = null
            if (text.isBlank()) { linkBad = false; return }
            val u = ScProxySettings.parseLink(text, type)
            if (u == null) { linkBad = true; return }
            linkBad = false
            type = u.type; host = u.host; port = u.port.toString(); user = u.user; pass = u.pass
        }

        Text(
            "Вставьте ссылку или адрес — поля заполнятся сами. Подходят socks5://логин:пароль@хост:порт, http://хост:порт, " +
                "ссылки Telegram (t.me/socks?…) и хост:порт.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = link,
            onValueChange = { applyLink(it) },
            label = { Text("Ссылка или хост:порт") },
            isError = linkBad,
            supportingText = if (linkBad) ({ Text("Не удалось разобрать адрес") }) else null,
            singleLine = true,
            shape = containerShape(16),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        FilledTonalButton(
            onClick = {
                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val t = cb?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                if (t.isNotBlank()) applyLink(t)
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        ) { Text("Вставить из буфера") }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            ScProxyType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = type == t,
                    onClick = { type = t; status = null },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = ScProxyType.entries.size),
                    label = { Text(t.title, maxLines = 1) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = host, onValueChange = { host = it; status = null },
                label = { Text("Адрес") }, singleLine = true, shape = containerShape(16),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = port, onValueChange = { port = it.filter(Char::isDigit).take(5); status = null },
                label = { Text("Порт") }, singleLine = true, shape = containerShape(16),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = user, onValueChange = { user = it; status = null },
            label = { Text("Логин (необязательно)") }, singleLine = true, shape = containerShape(16),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it; status = null },
            label = { Text("Пароль (необязательно)") }, singleLine = true, shape = containerShape(16),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Button(
            onClick = {
                val u = ScUpstream(type, host.trim(), port.toIntOrNull() ?: 0, user, pass)
                if (u.valid) {
                    ScProxySettings.setUpstream(u)
                    ScProxySettings.setMode(ScProxyMode.CUSTOM)
                    status = "Сохранено, SoundCloud теперь идёт через этот прокси" to true
                } else status = "Укажите адрес и порт от 1 до 65535" to false
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(56.dp),
        ) { Text("Сохранить и включить") }
        status?.let { (msg, ok) ->
            Text(
                msg, style = MaterialTheme.typography.bodySmall,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
