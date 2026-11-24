package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Приветствие вверху главного экрана (для управления одной рукой на больших телефонах): значок приложения в цветах темы,
 * «Доброе утро / Добрый день / Добрый вечер / Доброй ночи» по времени телефона и ник, который можно сменить нажатием.
 * По умолчанию выключено; на планшете не показывается.
 */
internal object GreetingSettings {
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    /** Свой ник для приветствия; пусто — берётся имя из аккаунта. */
    var nick by mutableStateOf("")
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("greeting", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false)
        nick = p.getString("nick", "").orEmpty()
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }

    fun updateNick(value: String) {
        nick = value.trim().take(30)
        prefs?.edit()?.putString("nick", nick)?.apply()
    }
}

private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

/** Приветствие по часу на часах телефона: 5–11 утро, 12–17 день, 18–22 вечер, иначе ночь. */
private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}

/** Открыть настройки приложения из любого места экрана (значение подставляет корень приложения). */
internal val LocalOpenSettings = staticCompositionLocalOf<() -> Unit> { {} }

/** Строка над приветствием: какой сервис выбран сейчас и кнопка настроек рядом с этой надписью. */
@Composable
private fun GreetingServiceRow() {
    val openSettings = LocalOpenSettings.current
    val provider = ActiveProvider.value
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.weight(1f, fill = false)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ms_library_music), contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Сервис: ${provider.title}",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = openSettings, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ms_settings), contentDescription = "Настройки", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun HomeGreeting(accountName: String, modifier: Modifier = Modifier) {
    var hour by remember { mutableIntStateOf(currentHour()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); hour = currentHour() }
    }
    var editing by remember { mutableStateOf(false) }
    val nick = GreetingSettings.nick.ifBlank { accountName }
    Column(modifier.fillMaxWidth()) {
    GreetingServiceRow()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Значок приложения (монохромная версия) в цветах Material You: у самой картинки большие поля, поэтому она крупнее плашки и обрезается ею.
        Box(
            Modifier.size(52.dp).clip(rounded(18)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.mipmap.ic_launcher_monochrome), contentDescription = null,
                modifier = Modifier.requiredSize(70.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                greetingFor(hour) + if (nick.isNotBlank()) "," else "",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                nick.ifBlank { "Указать ник" },
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                color = if (nick.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(rounded(12)).clickable { editing = true },
            )
        }
    }
    }
    if (editing) {
        var text by remember { mutableStateOf(GreetingSettings.nick.ifBlank { accountName }) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Как к вам обращаться?") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(30) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Пустое поле — имя из аккаунта") },
                )
            },
            confirmButton = {
                TextButton(onClick = { GreetingSettings.updateNick(if (text.trim() == accountName) "" else text); editing = false }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена") } },
        )
    }
}
