package app.ymusic.m3e.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

object AppLinks {
    private const val PREFS = "app_links"
    private const val KEY_ASKED = "asked"

    fun enabled(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return runCatching {
            val mgr = ctx.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java)
            val st = mgr.getDomainVerificationUserState(ctx.packageName) ?: return true
            val hosts = st.hostToStateMap.filterKeys { it.startsWith("music.yandex.") }
            hosts.isEmpty() || hosts.values.any {
                it == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                    it == android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_VERIFIED
            }
        }.getOrDefault(true)
    }

    fun openSettings(ctx: Context) {
        val pkg = Uri.parse("package:${ctx.packageName}")
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg))
            add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        }
        for (i in intents) if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }

    fun wasAsked(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun markAsked(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }
}

@Composable
fun LinkHandlingPrompt() {
    val ctx = LocalContext.current
    var show by remember { mutableStateOf(Build.VERSION.SDK_INT >= 31 && !AppLinks.wasAsked(ctx) && !AppLinks.enabled(ctx)) }
    if (!show) return
    AlertDialog(
        onDismissRequest = { AppLinks.markAsked(ctx); show = false },
        title = { Text("Открывать ссылки Яндекс Музыки здесь?") },
        text = {
            Text(
                "Чтобы ссылки на треки, альбомы, исполнителей и плейлисты (music.yandex.ru) открывались в этом приложении, " +
                    "в настройках включите «Открывать поддерживаемые ссылки» и отметьте адреса music.yandex.*. " +
                    "Это можно сделать и позже: Настройки → Музыка → «Ссылки Яндекс Музыки».",
            )
        },
        confirmButton = {
            TextButton(onClick = { AppLinks.markAsked(ctx); show = false; AppLinks.openSettings(ctx) }) { Text("Открыть настройки") }
        },
        dismissButton = { TextButton(onClick = { AppLinks.markAsked(ctx); show = false }) { Text("Не сейчас") } },
    )
}
