package app.ymusic.m3e.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.data.AppLog

private const val FORUM_URL = "https://t.me/exmusic_forum"

/**
 * После аварийного закрытия: при следующем запуске предлагает сохранить журнал упавшего запуска и отправить его на форум.
 * Вставляется один раз в корень приложения.
 */
@Composable
internal fun CrashReportPrompt() {
    val ctx = LocalContext.current
    var file by remember { mutableStateOf(AppLog.pendingCrashReport()) }
    val f = file ?: return
    val close = { AppLog.markCrashHandled(); file = null }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { out -> out.write(f.readBytes()) } != null
        }.getOrDefault(false)
        if (ok) { AppToast.success("Лог сохранён"); close() } else AppToast.error("Не удалось сохранить лог")
    }
    val stamp = remember { java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Приложение закрылось с ошибкой") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("В прошлый раз ExMusic аварийно закрылся. Сохраните журнал того запуска и отправьте его на форум: так ошибку получится найти и исправить.")
                Text("Форум: t.me/exmusic_forum")
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sendLog(ctx, f); close() }, modifier = Modifier.fillMaxWidth()) { Text("Отправить на форум") }
                    FilledTonalButton(onClick = { saver.launch("exmusic-crash-$stamp.log") }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить лог") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = close) { Text("Не сейчас") } },
    )
}

/** Отдаёт журнал аварии: сначала в Telegram (если установлен), иначе через общее меню «Поделиться». Заодно открывает форум. */
private fun sendLog(ctx: Context, f: java.io.File) {
    val uri: Uri = runCatching {
        androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    }.getOrNull() ?: run { AppToast.error("Не удалось подготовить лог"); return }
    fun intent() = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "ExMusic: журнал аварии")
        putExtra(Intent.EXTRA_TEXT, "Журнал аварии ExMusic. Форум: $FORUM_URL")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val tg = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
        .firstOrNull { pkg -> runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess }
    val ok = runCatching {
        if (tg != null) ctx.startActivity(intent().setPackage(tg))
        else ctx.startActivity(Intent.createChooser(intent(), "Отправить журнал").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!ok) {
        runCatching {
            ctx.startActivity(Intent.createChooser(intent(), "Отправить журнал").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { AppToast.error("Не удалось отправить лог") }
    }
    AppToast.success("Выберите форум ExMusic в списке чатов")
}
