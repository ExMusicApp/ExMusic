@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.AppViewModel
import app.ymusic.m3e.R
import app.ymusic.m3e.data.Track

/**
 * Выбор двух треков для микса. Работает в любом списке: долгое нажатие на строку или пункт «Выбрать для микса» в меню «⋮».
 * Пока выбран хотя бы один трек, нажатие на строку отмечает/снимает трек, а внизу появляется панель с кнопкой «Замиксовать».
 * Выбрано уже два — новый отметка заменяет самую раннюю. Первый выбранный играет первым.
 */
internal object MixPicker {
    val picked: SnapshotStateList<Track> = mutableStateListOf()

    val active: Boolean get() = picked.isNotEmpty()

    /** 0 — первый, 1 — второй, -1 — не выбран. */
    fun indexOf(t: Track): Int = picked.indexOfFirst { it.id == t.id }

    fun toggle(t: Track) {
        Haptics.tick()
        val i = indexOf(t)
        if (i >= 0) { picked.removeAt(i); return }
        if (picked.size >= 2) picked.removeAt(0)
        picked.add(t)
    }

    fun clear() = picked.clear()

    /** Цвета строки трека: выбранная для микса подсвечена. */
    @Composable
    fun rowColors(t: Track): ListItemColors {
        val sel = indexOf(t) >= 0
        val bg by animateColorAsState(
            if (sel) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent,
            fxSpring(stiffness = Spring.StiffnessMediumLow), label = "mixPickBg",
        )
        return ListItemDefaults.colors(containerColor = bg)
    }
}

/** Номер выбранного трека поверх обложки (1 или 2). */
@Composable
internal fun MixPickBadge(t: Track, modifier: Modifier = Modifier) {
    val idx = MixPicker.indexOf(t)
    if (idx < 0) return
    Box(
        modifier.clip(rounded(14)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("${idx + 1}", style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

/** Панель внизу экрана: сколько выбрано и кнопка «Замиксовать». */
@Composable
internal fun MixPickerBar(vm: AppViewModel) {
    AnimatedVisibility(
        visible = MixPicker.active,
        enter = fadeIn(fxSpring(stiffness = Spring.StiffnessMedium)) + expandVertically(fxSpring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val picked = MixPicker.picked.toList()
        Surface(
            shape = containerShape(28), color = MaterialTheme.colorScheme.tertiaryContainer,
            tonalElevation = 3.dp, shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { MixPicker.clear() }) {
                    Icon(painterResource(R.drawable.ms_close), contentDescription = "Отменить выбор")
                }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        if (picked.size < 2) "Выберите ещё один трек" else "Готово к миксу",
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        picked.mapIndexed { i, t -> "${i + 1}. ${t.title}" }.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = {
                        val l = MixPicker.picked.toList()
                        if (l.size == 2) { MixPicker.clear(); vm.mixTracks(l[0], l[1]) }
                    },
                    enabled = picked.size == 2,
                ) {
                    Icon(painterResource(R.drawable.ms_graphic_eq), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Замиксовать")
                }
            }
        }
    }
}
