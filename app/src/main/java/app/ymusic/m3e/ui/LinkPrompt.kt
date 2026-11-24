package app.ymusic.m3e.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Открытие ссылок Яндекс Музыки (music.yandex.ru и др.) в этом приложении.
 *
 * Манифест уже объявляет эти адреса, но с Android 12 система отдаёт веб-ссылки приложению, только если пользователь
 * сам включил для него «Открывать поддерживаемые ссылки» (подтвердить чужой домен приложение не может).
 * Поэтому при первом запуске показываем окно с переходом в нужные системные настройки; то же есть в Настройки → Музыка.
 */
object AppLinks {
    private const val PREFS = "app_links"
    private const val KEY_ASKED = "asked"
    private const val KEY_ASKED_VK = "asked_vk"

    /** Включено ли для приложения открытие ссылок Яндекс Музыки. До Android 12 вопрос решает системное окно «Открыть с помощью». */
    fun enabled(ctx: Context, vk: Boolean = false): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return runCatching {
            val mgr = ctx.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java)
            val st = mgr.getDomainVerificationUserState(ctx.packageName) ?: return true
            // Нужны обе группы адресов: Яндекс Музыка и SoundCloud (если в манифесте они есть). Хватает одного выбранного адреса в группе.
            fun groupOk(match: (String) -> Boolean): Boolean {
                val hosts = st.hostToStateMap.filterKeys(match)
                return hosts.isEmpty() || hosts.values.any {
                    it == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                        it == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                }
            }
            // У VK Музыки свои адреса (vk.com, vk.ru): нужен хотя бы один выбранный.
            if (vk) groupOk { it == "vk.com" || it.endsWith(".vk.com") || it == "vk.ru" || it.endsWith(".vk.ru") }
            else groupOk { it.startsWith("music.yandex.") } && groupOk { it.endsWith("soundcloud.com") }
        }.getOrDefault(true)
    }

    /** Системные настройки «Открывать по умолчанию» для этого приложения (запасной вариант — карточка приложения). */
    fun openSettings(ctx: Context) {
        val pkg = Uri.parse("package:${ctx.packageName}")
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
        for (i in intents) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }

    fun wasAsked(ctx: Context, vk: Boolean = false): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(if (vk) KEY_ASKED_VK else KEY_ASKED, false)

    fun markAsked(ctx: Context, vk: Boolean = false) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(if (vk) KEY_ASKED_VK else KEY_ASKED, true).apply()
    }
}

/** Единственный вопрос про ссылки: показывается один раз, и только если система ещё не отдаёт ссылки приложению. */
@Composable
fun LinkHandlingPrompt() {
    val ctx = LocalContext.current
    // У аккаунта VK свой вопрос про адреса vk.com / vk.ru; у остальных — про Яндекс Музыку и SoundCloud.
    val vk = ActiveProvider.isVk
    // Для VK то же самое объясняет окно VkFirstRunPrompt (там же про ссылки), второе окно не нужно.
    var show by remember(vk) { mutableStateOf(!vk && Build.VERSION.SDK_INT >= 31 && !AppLinks.wasAsked(ctx) && !AppLinks.enabled(ctx)) }
    if (!show) return
    AlertDialog(
        onDismissRequest = { AppLinks.markAsked(ctx, vk); show = false },
        title = { Text(if (vk) "Открывать ссылки VK Музыки здесь?" else "Открывать ссылки Яндекс Музыки и SoundCloud здесь?") },
        text = {
            Text(
                if (vk) {
                    "Чтобы ссылки на треки, плейлисты, альбомы и исполнителей VK Музыки (vk.com, vk.ru) открывались в этом приложении, " +
                        "в настройках включите «Открывать поддерживаемые ссылки» и отметьте адреса vk.com и vk.ru. " +
                        "Если на вашей прошивке (например, ColorOS) такого пункта нет — отправляйте ссылку через «Поделиться» → ExMusic " +
                        "или просто скопируйте её и откройте приложение. " +
                        "Это можно сделать и позже: Настройки → «Ссылки VK Музыки»."
                } else {
                    "Чтобы ссылки на треки, альбомы, исполнителей и плейлисты (music.yandex.ru, soundcloud.com) открывались в этом приложении, " +
                        "в настройках включите «Открывать поддерживаемые ссылки» и отметьте адреса music.yandex.* и soundcloud.com. " +
                        "Если на вашей прошивке (например, ColorOS) такого пункта нет — отправляйте ссылку через «Поделиться» → ExMusic " +
                        "или просто скопируйте её и откройте приложение. " +
                        "Это можно сделать и позже: Настройки → Музыка → «Ссылки Яндекс Музыки»."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { AppLinks.markAsked(ctx, vk); show = false; AppLinks.openSettings(ctx) }) { Text("Открыть настройки") }
        },
        dismissButton = { TextButton(onClick = { AppLinks.markAsked(ctx, vk); show = false }) { Text("Не сейчас") } },
    )
}

private const val VK_INFO_KEY = "vk_info_hidden"

/**
 * Первое открытие аккаунта VK: что нужно сделать и что в VK работает иначе. Показывается при каждом входе в VK,
 * пока не отмечено «Больше не показывать».
 */
@Composable
fun VkFirstRunPrompt() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("app_links", Context.MODE_PRIVATE) }
    var show by remember(ActiveProvider.isVk) { mutableStateOf(ActiveProvider.isVk && !prefs.getBoolean(VK_INFO_KEY, false)) }
    var never by remember { mutableStateOf(false) }
    if (!show) return
    val close = {
        if (never) prefs.edit().putBoolean(VK_INFO_KEY, true).apply()
        show = false
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("VK Музыка: что нужно знать") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(
                    "1. Ссылки. Чтобы ссылки на треки, плейлисты и исполнителей VK (vk.com, vk.ru) открывались в этом приложении, " +
                        "нажмите «Открыть настройки», включите «Открывать поддерживаемые ссылки» и отметьте адреса vk.com и vk.ru. " +
                        "Если такого пункта нет (например, ColorOS) — отправляйте ссылку через «Поделиться» → ExMusic или скопируйте её и откройте приложение.\n\n" +
                        "2. Если потоковая музыка не играет (скачанные треки играют, остальные нет):\n" +
                        "• выйдите из аккаунта VK и войдите заново — токен мог устареть;\n" +
                        "• отключите VPN или прокси на время прослушивания: ссылки VK привязаны к вашему IP и при его смене перестают работать;\n" +
                        "• очистите кэш: Настройки → Кэш (в том числе предзагруженный звук), затем перезапустите приложение;\n" +
                        "• выключите предзагрузку треков и плавный переход (кроссфейд) в настройках плеера;\n" +
                        "• если ничего не помогло — скачайте трек и слушайте скачанный, а журнал из Настройки → Для разработчиков отправьте разработчику.\n\n" +
                        "3. Отличия от Яндекс Музыки. Подкастов в VK нет. Чарт — это «популярное» VK, страницы исполнителей собираются из поиска, " +
                        "поэтому у части артистов может быть меньше информации.",
                )
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clickable { never = !never }.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(checked = never, onCheckedChange = { never = it })
                    Text("Больше не показывать")
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("Понятно") } },
        dismissButton = { TextButton(onClick = { close(); AppLinks.openSettings(ctx) }) { Text("Открыть настройки") } },
    )
}
