package app.ymusic.m3e.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.ymusic.m3e.data.LocalMusic

/**
 * Запрос доступа к музыке на устройстве.
 *
 * Если пользователь уже дважды отказал, система больше не показывает диалог: запрос сразу возвращается как «отказано»
 * (в журнале — «No requestable permission in the request»). Раньше кнопка «Разрешить доступ» в этом случае просто
 * ничего не делала. Теперь, если по нажатию кнопки ответ пришёл мгновенно (человек не успел бы нажать в диалоге),
 * открываются настройки приложения, где доступ можно включить вручную.
 *
 * Возвращает функцию запуска: аргумент `true` — запрос по нажатию кнопки (с переходом в настройки, если диалога нет),
 * `false` — автоматический запрос при открытии экрана (в настройки сам не уводит).
 */
@Composable
internal fun rememberAudioAccessRequest(onResult: (Boolean) -> Unit): (Boolean) -> Unit {
    val ctx = LocalContext.current
    val curResult by rememberUpdatedState(onResult)
    // [0] — время запуска запроса, [1] — 1, если запрос начат по нажатию кнопки.
    val info = remember { LongArray(2) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val quick = SystemClock.elapsedRealtime() - info[0] < NO_DIALOG_MS
        if (!ok && quick && info[1] == 1L && !LocalMusic.hasPermission(ctx)) openAudioSettings(ctx)
        curResult(ok)
    }
    return remember(launcher) {
        { byButton: Boolean ->
            info[0] = SystemClock.elapsedRealtime()
            info[1] = if (byButton) 1L else 0L
            launcher.launch(LocalMusic.permission())
        }
    }
}

/** Открывает настройки приложения с подсказкой, где включить доступ к музыке. */
internal fun openAudioSettings(ctx: Context) {
    AppToast.show("Откройте «Разрешения» → «Музыка и аудио» и разрешите доступ", long = true)
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
        )
    }
}

/** Ответ быстрее этого времени (мс) значит, что диалог не показывался: нажать в нём кнопку человек не успевает. */
private const val NO_DIALOG_MS = 400L
