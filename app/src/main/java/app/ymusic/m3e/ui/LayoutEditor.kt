@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.ymusic.m3e.R
import kotlin.math.roundToInt

private enum class EditorTab(val title: String) {
    Dock("Док"), Mini("Мини-плеер"), Player("Плеер"), Glass("Стекло"), Home("Главная"), Playlists("Плейлисты"), Profile("Профиль"),
    Sizes("Размеры"), Look("Вид"), Links("Ссылки"),
}

private val ROW_H = 60.dp

@Composable
internal fun LayoutEditorPage(onBack: () -> Unit) {
    var tabName by rememberSaveable { mutableStateOf(EditorTab.Dock.name) }
    val tab = EditorTab.entries.firstOrNull { it.name == tabName } ?: EditorTab.Dock
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Редактор интерфейса", onBack, subtitle = "Тяните «≡» или жмите стрелки; нажатие на блок — размер и отступы")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EditorTab.entries.forEach { t ->
                FilterChip(selected = t == tab, onClick = { tabName = t.name }, label = { Text(t.title, maxLines = 1) })
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp + dockInset()),
        ) {
            when (tab) {
                EditorTab.Dock -> DockEditor()
                EditorTab.Home -> BlockEditor(LScreen.HOME, HomeBlock.entries, "Порядок блоков на главной. Скрытый блок не показывается.")
                EditorTab.Playlists -> BlockEditor(LScreen.PLAYLISTS, PlaylistsBlock.entries, "Порядок блоков на вкладке «Плейлисты».")
                EditorTab.Profile -> {
                    BlockEditor(LScreen.PROFILE, ProfileBlock.entries, "Страница профиля: аватар слева, справа ник и логин.")
                    Spacer(Modifier.height(16.dp))
                    GlowSwitchRow("Кнопка «Поделиться»", "Под логином: отправляет ссылку на ваш профиль", UiLayout.profileShare) { UiLayout.updateProfileShare(it) }
                    SliderRow("Размер аватара: ${UiLayout.profileAvatarDp} dp", UiLayout.profileAvatarDp.toFloat(), 56f..160f, 0) {
                        UiLayout.updateProfileAvatar(it.roundToInt())
                    }
                    SliderRow("Плейлистов в ряду: ${UiLayout.profileColumns}", UiLayout.profileColumns.toFloat(), 1f..4f, 2) {
                        UiLayout.updateProfileColumns(it.roundToInt())
                    }
                }
                EditorTab.Mini -> MiniEditor()
                EditorTab.Glass -> GlassSettingsSection()
                EditorTab.Player -> {
                    Text("Части полного плеера", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    BlockEditor(
                        LScreen.PLAYER_FULL, PlayerPart.entries,
                        "Порядок частей плеера сверху вниз. Обложка занимает всё свободное место; в альбомной ориентации она слева или справа " +
                            "(зависит от того, выше ли она кнопок управления). Скрытая обложка — управление уходит вниз.",
                        noZoom = setOf("Cover"),
                    )
                    SliderRow("Боковые поля плеера: ${UiStyle.playerSide} dp", UiStyle.playerSide.toFloat(), 0f..48f, 47) { UiStyle.updatePlayerSide(it.roundToInt()) }
                    Spacer(Modifier.height(20.dp))
                    Text("Нижний ряд кнопок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    BlockEditor(LScreen.PLAYER, PlayerBtn.entries, "Нижний ряд кнопок полного плеера. «Пустое место» раздвигает кнопки.", extras = false)
                    Spacer(Modifier.height(16.dp))
                    SliderRow("Размер всех кнопок: ${(UiLayout.playerBtnZoom * 100).roundToInt()}%", UiLayout.playerBtnZoom, UiLayout.MIN_ZOOM..UiLayout.MAX_ZOOM, 19) {
                        UiLayout.updatePlayerBtnZoom((it * 20f).roundToInt() / 20f)
                    }
                }
                EditorTab.Sizes -> SizesEditor()
                EditorTab.Look -> {
                    Text(
                        "Все настройки из «Настройки → Внешний вид»: тема, цвета, шрифт, формы, полоса прогресса, свечение, анимации, главный экран, поиск, док и навигация.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    AppearanceContent()
                }
                EditorTab.Links -> LinksEditor()
            }
        }
    }
}

@Composable
private fun <T> ReorderList(
    items: List<T>,
    keyOf: (T) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    row: @Composable RowScope.(item: T, index: Int, handle: Modifier) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            key(keyOf(item)) { ReorderRow(i, items.size, onMove) { handle -> row(item, i, handle) } }
        }
    }
}

@Composable
private fun ReorderRow(index: Int, count: Int, onMove: (Int, Int) -> Unit, content: @Composable RowScope.(Modifier) -> Unit) {
    val rowPx = with(LocalDensity.current) { ROW_H.toPx() }
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val idx by rememberUpdatedState(index)
    val cnt by rememberUpdatedState(count)
    val move by rememberUpdatedState(onMove)
    val handle = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { dragging = true; Haptics.tick() },
            onDragEnd = { dragging = false; offset = 0f },
            onDragCancel = { dragging = false; offset = 0f },
        ) { change, drag ->
            change.consume()
            offset += drag.y
            if (offset > rowPx * 0.5f && idx < cnt - 1) {
                move(idx, idx + 1); offset -= rowPx; Haptics.tick()
            } else if (offset < -rowPx * 0.5f && idx > 0) {
                move(idx, idx - 1); offset += rowPx; Haptics.tick()
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().height(ROW_H).zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = offset; shadowElevation = if (dragging) 16f else 0f; shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp); clip = false }
            .padding(vertical = 3.dp),
    ) {
        Row(
            Modifier.fillMaxSize().clip(containerShape(16))
                .background(if (dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer),
            verticalAlignment = Alignment.CenterVertically,
        ) { content(handle) }
    }
}

@Composable
private fun DragHandle(modifier: Modifier) {
    Box(modifier.width(44.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Text("≡", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArrowButtons(index: Int, count: Int, onMove: (Int, Int) -> Unit) {
    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(36.dp)) {
        Icon(painterResource(R.drawable.ms_arrow_upward), contentDescription = "Выше", modifier = Modifier.size(20.dp))
    }
    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < count - 1, modifier = Modifier.size(36.dp)) {
        Icon(painterResource(R.drawable.ms_arrow_upward), contentDescription = "Ниже", modifier = Modifier.size(20.dp).rotate(180f))
    }
}

@Composable
private fun <E> BlockEditor(
    screen: String, all: List<E>, description: String,
    noZoom: Set<String> = emptySet(),
    extras: Boolean = true,
) where E : Enum<E>, E : LayoutBlock {
    val ordered = UiLayout.order(screen, all)
    var selName by rememberSaveable(screen) { mutableStateOf<String?>(null) }
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
    ReorderList(
        items = ordered, keyOf = { it.name },
        onMove = { f, t -> UiLayout.move(screen, all, f, t) },
    ) { b, i, handle ->
        val hidden = UiLayout.isHidden(screen, b)
        DragHandle(handle)
        Row(
            Modifier.weight(1f).fillMaxHeight().clickable { selName = if (selName == b.name) null else b.name },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(blockIcon(b)), null, tint = if (hidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    b.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selName == b.name) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (hidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    b.hint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ArrowButtons(i, ordered.size) { f, t -> UiLayout.move(screen, all, f, t) }
        Switch(checked = !hidden, onCheckedChange = { UiLayout.setHidden(screen, b, !it) }, modifier = Modifier.padding(end = 8.dp))
    }
    val sel = ordered.firstOrNull { it.name == selName }
    Spacer(Modifier.height(12.dp))
    if (sel == null) {
        Text("Нажмите на блок, чтобы изменить его размер и отступы.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Surface(shape = containerShape(20), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(sel.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val z = UiLayout.zoom(screen, sel)
                if (sel.name !in noZoom) {
                    SliderRow("Размер блока: ${(z * 100).roundToInt()}%", z, UiLayout.MIN_ZOOM..UiLayout.MAX_ZOOM, 19) {
                        UiLayout.setZoom(screen, sel, (it * 20f).roundToInt() / 20f)
                    }
                }
                if (extras) {
                    val g = UiLayout.gap(screen, sel)
                    SliderRow("Отступ сверху: $g dp", g.toFloat(), 0f..UiLayout.MAX_GAP.toFloat(), 0) { UiLayout.setGap(screen, sel, it.roundToInt()) }
                    val gb = UiLayout.gapBottom(screen, sel)
                    SliderRow("Отступ снизу: $gb dp", gb.toFloat(), 0f..UiLayout.MAX_GAP.toFloat(), 0) { UiLayout.setGapBottom(screen, sel, it.roundToInt()) }
                    val ph = UiLayout.padH(screen, sel)
                    SliderRow("Поля слева и справа: $ph dp", ph.toFloat(), 0f..UiLayout.MAX_GAP.toFloat(), 0) { UiLayout.setPadH(screen, sel, it.roundToInt()) }
                }
                TextButton(onClick = {
                    UiLayout.setZoom(screen, sel, 1f); UiLayout.setGap(screen, sel, 0)
                    UiLayout.setGapBottom(screen, sel, 0); UiLayout.setPadH(screen, sel, 0)
                }) { Text("Сбросить размер и отступы") }
            }
        }
    }
    TextButton(onClick = { UiLayout.resetScreen(screen); selName = null }, modifier = Modifier.padding(top = 4.dp)) {
        Text("Вернуть порядок и размеры по умолчанию")
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun DockEditor() {
    val tabs = NavSettings.tabs
    val hidden = Tab.entries.filter { it !in tabs }
    Text("Кнопки дока: порядок сверху вниз — слева направо в панели.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    DockPreview(tabs)
    Spacer(Modifier.height(8.dp))
    ReorderList(items = tabs, keyOf = { it.name }, onMove = { f, t -> NavSettings.reorder(f, t) }) { t, i, handle ->
        DragHandle(handle)
        Icon(painterResource(t.icon), null)
        Spacer(Modifier.width(10.dp))
        Text(t.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ArrowButtons(i, tabs.size) { f, to -> NavSettings.reorder(f, to) }
        IconButton(onClick = { NavSettings.setVisible(t, false) }, enabled = t.removable && tabs.size > 1) {
            Icon(painterResource(R.drawable.ms_close), contentDescription = "Убрать из панели")
        }
    }
    if (hidden.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text("Скрыты", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        hidden.forEach { t ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(containerShape(16))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(t.icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(t.title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { NavSettings.setVisible(t, true) }) { Text("Добавить") }
            }
        }
    }
    Text("«Профиль» убрать нельзя: через него открываются настройки.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

    Spacer(Modifier.height(16.dp))
    Text("Вид дока", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    GlowSwitchRow("Островок", "Плавающая капсула вместо панели на всю ширину", ThemeSettings.dockIsland) { ThemeSettings.updateDockIsland(it) }
    GlowSwitchRow("Автоскрытие", "Прячется при прокрутке вниз", ThemeSettings.dockAutoHide) { ThemeSettings.updateDockAutoHide(it) }
    GlowSwitchRow("Листание экранов", "Свайп влево-вправо переключает вкладки", NavSettings.swipeTabs) { NavSettings.updateSwipeTabs(it) }
    GlowSwitchRow("Подсказки кнопок", "Долгое нажатие показывает название кнопки", NavSettings.hints) { NavSettings.updateHints(it) }
    Text("Подписи вкладок", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DockLabels.entries.forEach { m ->
            FilterChip(selected = NavSettings.dockLabels == m, onClick = { NavSettings.updateDockLabels(m) }, label = { Text(m.title) })
        }
    }
    SliderRow("Размер всего дока: ${(UiLayout.dockZoom * 100).roundToInt()}%", UiLayout.dockZoom, UiLayout.MIN_ZOOM..UiLayout.MAX_ZOOM, 19) {
        UiLayout.updateDockZoom((it * 20f).roundToInt() / 20f)
    }
    SliderRow("Размер значков: ${(UiLayout.dockIconZoom * 100).roundToInt()}%", UiLayout.dockIconZoom, UiLayout.MIN_ZOOM..UiLayout.MAX_ZOOM, 19) {
        UiLayout.updateDockIconZoom((it * 20f).roundToInt() / 20f)
    }

    Text(
        "Размытие дока и островка настраивается только в Настройки → Внешний вид → Матовое стекло.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )

    Text("Островок: положение и размеры", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    GlowSwitchRow(
        "Последняя вкладка — отдельная кнопка",
        "Последняя кнопка дока (обычно «Профиль») уходит в отдельный кружок справа от капсулы. Только в режиме «Островок»",
        UiStyle.dockDetachLast,
    ) { UiStyle.updateDockDetachLast(it) }
    if (UiStyle.dockDetachLast) {
        SliderRow("Расстояние до отдельной кнопки: ${UiStyle.dockDetachGap} dp", UiStyle.dockDetachGap.toFloat(), 0f..32f, 31) { UiStyle.updateDockDetachGap(it.roundToInt()) }
    }
    Text("Положение островка", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("По центру", "Слева", "Справа").forEachIndexed { i, title ->
            FilterChip(selected = UiStyle.dockAlign == i, onClick = { UiStyle.updateDockAlign(i) }, label = { Text(title) })
        }
    }
    SliderRow("Боковые поля: ${UiStyle.dockSide} dp", UiStyle.dockSide.toFloat(), 0f..48f, 47) { UiStyle.updateDockSide(it.roundToInt()) }
    SliderRow("Отступ от нижнего края: ${UiStyle.dockBottom} dp", UiStyle.dockBottom.toFloat(), 0f..64f, 63) { UiStyle.updateDockBottom(it.roundToInt()) }
    SliderRow("Скругление капсулы: ${UiStyle.dockCorner} dp", UiStyle.dockCorner.toFloat(), 8f..40f, 31) { UiStyle.updateDockCorner(it.roundToInt()) }
    SliderRow("Расстояние между вкладками: ${UiStyle.dockSpacing} dp", UiStyle.dockSpacing.toFloat(), 0f..16f, 15) { UiStyle.updateDockSpacing(it.roundToInt()) }
    SliderRow("Отступ внутри вкладки по бокам: ${UiStyle.dockPadH} dp", UiStyle.dockPadH.toFloat(), 6f..28f, 21) { UiStyle.updateDockPadH(it.roundToInt()) }
    SliderRow("Отступ внутри вкладки сверху и снизу: ${UiStyle.dockPadV} dp", UiStyle.dockPadV.toFloat(), 4f..22f, 17) { UiStyle.updateDockPadV(it.roundToInt()) }
    SliderRow("Размер подписей: ${(UiStyle.dockLabelScale * 100).roundToInt()}%", UiStyle.dockLabelScale, 0.7f..1.4f, 13) {
        UiStyle.updateDockLabelScale((it * 20f).roundToInt() / 20f)
    }
    TextButton(onClick = {
        NavSettings.resetTabs(); UiLayout.updateDockZoom(1f); UiLayout.updateDockIconZoom(1f); UiStyle.resetDock()
    }) {
        Text("Вернуть док по умолчанию")
    }
}

@Composable
private fun MiniEditor() {
    BlockEditor(
        LScreen.MINI, MiniBlock.entries,
        "Блоки мини-плеера слева направо. «Предыдущий», «Следующий» и «Нравится» по умолчанию выключены — включите нужные. " +
            "Блок «Название и исполнитель» занимает всё свободное место.",
    )
    Spacer(Modifier.height(16.dp))
    Text("Размер и вид", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SliderRow("Размер всего мини-плеера: ${(UiStyle.miniZoom * 100).roundToInt()}%", UiStyle.miniZoom, 0.7f..1.4f, 13) {
        UiStyle.updateMiniZoom((it * 20f).roundToInt() / 20f)
    }
    val c = UiStyle.miniCorner
    SliderRow(if (c < 0) "Скругление: как в приложении" else "Скругление: $c dp", c.toFloat(), -1f..40f, 40) { UiStyle.updateMiniCorner(it.roundToInt()) }
    SliderRow("Боковые поля: +${UiStyle.miniSide} dp", UiStyle.miniSide.toFloat(), 0f..32f, 31) { UiStyle.updateMiniSide(it.roundToInt()) }
    SliderRow("Отступ снизу: +${UiStyle.miniBottom} dp", UiStyle.miniBottom.toFloat(), 0f..32f, 31) { UiStyle.updateMiniBottom(it.roundToInt()) }
    GlowSwitchRow(
        "Матовое стекло мини-плеера",
        "Полупрозрачный мини-плеер с размытым экраном за ним. Силу размытия и плотность — на вкладке «Стекло»",
        GlassSettings.miniGlass,
    ) { GlassSettings.updateMiniGlass(it) }
    TextButton(onClick = { UiStyle.resetMini() }) { Text("Вернуть размер и вид мини-плеера") }
}

@Composable
private fun DockPreview(tabs: List<Tab>) {
    val mode = NavSettings.dockLabels
    Surface(
        shape = if (ThemeSettings.dockIsland) rounded(32) else containerShape(16),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ZoomBox(UiLayout.dockZoom.coerceAtMost(1.2f)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, t ->
                    val sel = i == 0
                    val fg = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(
                        Modifier.clip(containerShape(16)).background(if (sel) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ZoomBox(UiLayout.dockIconZoom) { Icon(painterResource(t.icon), null, tint = fg) }
                        if (mode != DockLabels.Hidden) Text(t.title, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SizesEditor() {
    Text("Масштаб всего приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    UiScaleSetting()
    Spacer(Modifier.height(8.dp))
    Text("Док", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SliderRow("Размер дока: ${(UiLayout.dockZoom * 100).roundToInt()}%", UiLayout.dockZoom, UiLayout.MIN_ZOOM..UiLayout.MAX_ZOOM, 19) {
        UiLayout.updateDockZoom((it * 20f).roundToInt() / 20f)
    }
    SliderRow("Размер значков дока: ${(UiLayout.dockIconZoom * 100).roundToInt()}%", UiLayout.dockIconZoom, UiLayout.MIN_ZOOM..UiLayout.MAX_ZOOM, 19) {
        UiLayout.updateDockIconZoom((it * 20f).roundToInt() / 20f)
    }
    Spacer(Modifier.height(8.dp))
    Text("Плеер", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SliderRow("Кнопки плеера: ${(UiLayout.playerBtnZoom * 100).roundToInt()}%", UiLayout.playerBtnZoom, UiLayout.MIN_ZOOM..UiLayout.MAX_ZOOM, 19) {
        UiLayout.updatePlayerBtnZoom((it * 20f).roundToInt() / 20f)
    }
    SliderRow("Боковые поля плеера: ${UiStyle.playerSide} dp", UiStyle.playerSide.toFloat(), 0f..48f, 47) { UiStyle.updatePlayerSide(it.roundToInt()) }
    Spacer(Modifier.height(8.dp))
    Text("Мини-плеер", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SliderRow("Размер мини-плеера: ${(UiStyle.miniZoom * 100).roundToInt()}%", UiStyle.miniZoom, 0.7f..1.4f, 13) {
        UiStyle.updateMiniZoom((it * 20f).roundToInt() / 20f)
    }
    Spacer(Modifier.height(8.dp))
    Text("Профиль", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SliderRow("Аватар: ${UiLayout.profileAvatarDp} dp", UiLayout.profileAvatarDp.toFloat(), 56f..160f, 0) { UiLayout.updateProfileAvatar(it.roundToInt()) }
    SliderRow("Плейлистов в ряду: ${UiLayout.profileColumns}", UiLayout.profileColumns.toFloat(), 1f..4f, 2) { UiLayout.updateProfileColumns(it.roundToInt()) }
    Spacer(Modifier.height(8.dp))
    Text("Размер каждого блока на экранах меняется во вкладках «Главная», «Плейлисты», «Профиль», «Мини-плеер» и «Плеер»: нажмите на блок.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    var confirm by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirm = true }, modifier = Modifier.padding(top = 16.dp)) { Text("Сбросить всю раскладку") }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Сбросить раскладку?") },
            text = { Text("Порядок, видимость и размеры блоков, дока, мини-плеера и плеера вернутся к значениям по умолчанию, матовое стекло выключится. Тема, цвета и формы не изменятся.") },
            confirmButton = { TextButton(onClick = { UiLayout.resetAll(); confirm = false }) { Text("Сбросить") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun LinksEditor() {
    val ctx = LocalContext.current
    Text("Что делать со ссылками Яндекс Музыки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    GlowSwitchRow(
        "Открывать модалкой",
        "Плейлист, альбом, трек или исполнитель, которым с вами поделились, открывается нижним окном с кнопками «Слушать» и «Вперемешку». Выключите — откроется обычная страница",
        UiLayout.linkModal,
    ) { UiLayout.updateLinkModal(it) }
    GlowSwitchRow(
        "Предлагать ссылку из буфера обмена",
        "Если система не передаёт ссылки приложению, скопируйте ссылку и откройте приложение — оно предложит её открыть",
        UiLayout.clipLinks,
    ) { UiLayout.updateClipLinks(it) }
    OutlinedButton(onClick = { AppLinks.openSettings(ctx) }) { Text("Системные настройки ссылок") }

    Spacer(Modifier.height(24.dp))
    Text("Копия раскладки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        "Порядок и размеры блоков, док и кнопки плеера одной строкой. Можно переслать другу или сохранить.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            runCatching {
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("layout", UiLayout.exportJson()))
                AppToast.show("Раскладка скопирована")
            }
        }) { Text("Скопировать") }
        FilledTonalButton(onClick = {
            val text = runCatching {
                ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
            }.getOrNull()
            if (text != null && UiLayout.importJson(text)) AppToast.show("Раскладка применена") else AppToast.error("В буфере нет раскладки")
        }) { Text("Вставить") }
    }
}
