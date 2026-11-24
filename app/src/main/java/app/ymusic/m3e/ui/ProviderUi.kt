package app.ymusic.m3e.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import app.ymusic.m3e.DEFAULT_WAVE
import app.ymusic.m3e.data.ScWaveSource
import app.ymusic.m3e.data.ScWaveSources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ymusic.m3e.AppViewModel
import app.ymusic.m3e.data.ApiCaps
import app.ymusic.m3e.data.Provider

/** Сервис активного аккаунта для интерфейса: подписи и скрытие того, чего сервис не умеет. Обновляется из AppRoot. */
object ActiveProvider {
    var value by mutableStateOf(Provider.YANDEX)
    var caps by mutableStateOf(ApiCaps())

    val isVk: Boolean get() = value == Provider.VK
    val isSc: Boolean get() = value == Provider.SOUNDCLOUD

    /** Название «волны»: у VK — VK Микс, у SoundCloud — ExWave. */
    val waveTitle: String get() = if (isVk) "VK Микс" else if (isSc) "ExWave" else "Моя волна"
}

/** Настройки VK Микса: категории (настроение, занятие, жанры…) с вариантами из ответа сервера. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MixSettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadMixSettings() }
    val settings = state.mixSettings
    var draft: Map<String, Set<String>> by remember(settings) {
        mutableStateOf<Map<String, Set<String>>>(
            settings?.categories?.associate { c -> c.id to c.options.filter { it.selected }.map { it.id }.toSet() }
                ?: emptyMap<String, Set<String>>()
        )
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        ) {
            Text("Настройки VK Микса", style = MaterialTheme.typography.headlineSmall)
            settings?.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (settings == null) {
                Spacer(Modifier.height(24.dp))
                if (state.mixSettingsLoading) CircularProgressIndicator()
                else Text("Не удалось загрузить настройки. Закройте и откройте снова", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                settings.categories.forEach { cat ->
                    Text(
                        cat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        cat.options.forEach { o ->
                            val on = o.id in (draft[cat.id] ?: emptySet<String>())
                            FilterChip(
                                selected = on,
                                onClick = {
                                    val cur: Set<String> = draft[cat.id] ?: emptySet()
                                    val next = when {
                                        on -> cur - o.id
                                        settings.multiSelect -> cur + o.id
                                        else -> setOf(o.id)
                                    }
                                    draft = draft + (cat.id to next)
                                },
                                label = { Text(o.title) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { vm.saveMixSelection(draft); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Применить") }
            }
        }
    }
}

/** Настройки ExWave (SoundCloud): откуда брать музыку. Источники включаются и отключаются; хотя бы один должен остаться. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScWaveSourcesSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hasAccount = state.account != null
    var draft by remember { mutableStateOf(ScWaveSources.enabled) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Источники ExWave", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Выберите, откуда ExWave берёт музыку. Треки из включённых источников чередуются.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            ScWaveSource.entries.forEach { src ->
                val on = src in draft
                val locked = on && draft.size == 1
                val toggle = { if (!locked) draft = if (on) draft - src else draft + src }
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = toggle).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(src.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(src.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (src.needsAccount && !hasAccount) {
                            Text("Нужен вход в аккаунт SoundCloud", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    ExpressiveSwitch(checked = on, onCheckedChange = { toggle() })
                }
            }
            if (draft.size == 1) {
                Text(
                    "Хотя бы один источник должен остаться включённым",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val changed = draft != ScWaveSources.enabled
                    ScWaveSources.update(draft)
                    onDismiss()
                    // Если ExWave уже играет — собираем её заново из новых источников.
                    if (changed && state.waveOn && state.waveStation == DEFAULT_WAVE) vm.restartWave()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Применить") }
        }
    }
}
