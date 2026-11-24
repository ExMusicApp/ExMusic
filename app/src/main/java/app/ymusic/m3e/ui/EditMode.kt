@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Режим редактора интерфейса прямо на экране.
 *
 * Включается в Настройки → Внешний вид → Редактор интерфейса («Режим редактора на экране»). После этого на страницах
 * появляется плавающая кнопка-карандаш. Нажали её — правка включена: все элементы текущей страницы, мини-плеера и дока
 * обводятся рамкой, обычные нажатия на них не срабатывают, а нажатие на элемент выбирает его. Из кнопки-карандаша
 * «выезжает» панель правки: её можно перетаскивать за верхнюю полоску и убрать обратно в карандаш — правка при этом
 * продолжает работать. Панель не обязательна: она сама убирается в карандаш, когда нажали на элемент (его правят ручками на
 * рамке и маленькой панелью рядом с ним) или начали прокручивать страницу; вернуть её можно нажатием на карандаш. Выбранный элемент:
 *  • растягивается за ручки на рамке, как окно в Windows: угловые ручки меняют размер, ручка справа — ширину
 *    (на сколько «кнопок» растянут элемент, если рядом есть свободное место);
 *  • переставляется, если его зажать и перетащить на другой элемент той же группы (только в пределах интерфейса);
 *  • прячется, сбрасывается и меняется местами с другим — кнопками панели или маленькой панелью у самого элемента.
 * Скрытые элементы возвращаются из панели. Без нажатия на плавающую кнопку ничего не редактируется.
 */
internal object LayoutEdit {
    /** Что сейчас правится: [LScreen.HOME], [LScreen.PLAYLISTS], [LScreen.PROFILE], [LScreen.PLAYER], [LScreen.DOCK] или null. Мини-плеер и док правятся вместе со страницей. */
    var pager by mutableStateOf<String?>(null)
        private set

    /** Выбранный элемент (значение enum блока или вкладка дока). */
    var selected by mutableStateOf<Any?>(null)
        private set

    /** Включён режим «поменять местами»: следующее нажатие на элемент той же группы меняет его с выбранным. */
    var swapping by mutableStateOf(false)
        private set

    /** Панель правки открыта. false — она убрана в кнопку-карандаш, но правка продолжает работать. */
    var panelOpen by mutableStateOf(true)
        private set

    /** Положение панели (левый верхний угол, px, в координатах слоя правки). null — рядом с кнопкой-карандашом. */
    var panelPos by mutableStateOf<Offset?>(null)

    /** Рамка открытой панели (координаты корня): жест, начатый на самой панели, её не прячет. */
    var panelBounds by mutableStateOf(Rect.Zero)

    /** Мини-плеер сейчас на экране: его кнопки правятся вместе со страницей. Выставляется слоем правки. */
    var miniOn by mutableStateOf(false)

    /** Область слоя правки в координатах корня: в её пределах можно переносить элементы. */
    var area by mutableStateOf(Rect.Zero)

    /** «Призрак» переносимого элемента (координаты корня) или null, если сейчас ничего не переносят. */
    var dragGhost by mutableStateOf<Rect?>(null)
        private set

    /** Элемент, на место которого встанет переносимый, если сейчас отпустить палец. */
    var dragTarget by mutableStateOf<Any?>(null)
        private set

    private var dragGrab = Offset.Zero
    private var dragSize = Size.Zero

    private class Inst(val block: Any, val rect: Rect)

    /** Рамки всех элементов, которые сейчас на экране (в координатах корня): нужны для ручек и для переноса. */
    private val instances = mutableStateMapOf<Any, Inst>()

    private class ResizeSession(val zoom0: Float, val span0: Int, val w0: Float, val h0: Float)

    private var resize: ResizeSession? = null

    private val homeGroup = HomeGroup()
    private val playlistsGroup = BlockGroup(LScreen.PLAYLISTS, PlaylistsBlock.entries, horizontal = false)
    private val profileGroup = BlockGroup(LScreen.PROFILE, ProfileBlock.entries, horizontal = false)
    private val playerPartsGroup = BlockGroup(LScreen.PLAYER_FULL, PlayerPart.entries, horizontal = false, noZoom = setOf("Cover"))
    private val playerBtnGroup = BlockGroup(
        LScreen.PLAYER, PlayerBtn.entries, horizontal = true, noZoom = setOf("Gap"),
        rule = SlotRule(
            growable = PlayerBtn.entries.filter { it != PlayerBtn.Gap }.toSet(),
            occupying = PlayerBtn.entries.filter { it != PlayerBtn.Gap }.toSet(),
            slots = PlayerBtn.entries.count { it != PlayerBtn.Gap },
        ),
    )
    private val miniGroup = BlockGroup(
        LScreen.MINI, MiniBlock.entries, horizontal = true,
        rule = SlotRule(
            growable = setOf(MiniBlock.Prev, MiniBlock.Next, MiniBlock.Like),
            occupying = setOf(MiniBlock.Prev, MiniBlock.Play, MiniBlock.Next, MiniBlock.Like),
            slots = 4,
        ),
    )
    private val dockGroup = DockGroup()

    fun groups(p: String?): List<EditGroup> = when (p) {
        LScreen.HOME -> listOf(homeGroup)
        LScreen.PLAYLISTS -> listOf(playlistsGroup)
        LScreen.PROFILE -> listOf(profileGroup)
        LScreen.PLAYER -> listOf(playerPartsGroup, playerBtnGroup)
        LScreen.MINI -> listOf(miniGroup)
        LScreen.DOCK -> listOf(dockGroup)
        else -> emptyList()
    }

    /** Все группы, которые сейчас подсвечены: страница, мини-плеер (если он на экране) и док. */
    fun activeGroups(): List<EditGroup> {
        val p = pager ?: return emptyList()
        val out = ArrayList<EditGroup>(groups(p))
        if (miniOn && p != LScreen.MINI) out += groups(LScreen.MINI)
        if (p != LScreen.DOCK) out += groups(LScreen.DOCK)
        return out
    }

    fun groupOf(b: Enum<*>): EditGroup? = activeGroups().firstOrNull { it.owns(b) }

    fun pagerTitle(p: String): String = when (p) {
        LScreen.HOME -> "Главная"
        LScreen.PLAYLISTS -> "Плейлисты"
        LScreen.PROFILE -> "Профиль"
        LScreen.PLAYER -> "Плеер"
        LScreen.MINI -> "Мини-плеер"
        LScreen.DOCK -> "Док"
        else -> p
    }

    /** Экраны хранилища раскладки: части и кнопки плеера правятся вместе, на одной странице «Плеер». */
    private fun pagerOf(screen: String): String = if (screen == LScreen.PLAYER_FULL) LScreen.PLAYER else screen

    /** Подсвечены ли элементы экрана [screen]: правка включена, и это текущая страница, мини-плеер или док. */
    fun isActive(screen: String): Boolean {
        val p = pager ?: return false
        return pagerOf(screen) == p || (screen == LScreen.MINI && miniOn) || screen == LScreen.DOCK
    }

    fun start(p: String) {
        pager = p
        selected = null
        swapping = false
        panelOpen = true
        clearGestures()
    }

    fun stop() {
        pager = null
        selected = null
        swapping = false
        clearGestures()
        instances.clear()
    }

    /** Страница под правкой сменилась (другая вкладка, открыли или закрыли плеер): правка продолжается на новой. */
    fun retarget(p: String) {
        if (pager == null) return
        pager = p
        val s = selected
        if (s is Enum<*> && groupOf(s) == null) selected = null
        swapping = false
        clearGestures()
    }

    private fun clearGestures() {
        dragGhost = null
        dragTarget = null
        resize = null
    }

    @JvmName("updatePanelOpen")
    fun setPanelOpen(open: Boolean) {
        panelOpen = open
        if (!open) swapping = false
    }

    fun select(b: Any?) {
        selected = b
        swapping = false
    }

    fun toggleSwap() { swapping = !swapping }

    /** Нажатие на элемент в режиме правки. Вложенные элементы вызывают это от внешнего к внутреннему: последним выбирается самый глубокий. */
    fun tap(block: Any) {
        val sel = selected
        if (swapping && sel is Enum<*>) {
            val g = groupOf(sel)
            if (block is Enum<*> && block !== sel && g != null && g.owns(block)) {
                g.swap(sel, block)
                swapping = false
            }
            return
        }
        selected = block
        // Панель не обязательна: пока правят выбранную кнопку, она не должна закрывать экран. Вернуть её можно карандашом.
        panelOpen = false
    }

    // ---- Рамки элементов ----

    fun register(id: Any, block: Any, rect: Rect) {
        if (rect.width < 1f || rect.height < 1f) { instances.remove(id); return }
        val old = instances[id]
        if (old != null && old.block === block && old.rect == rect) return
        instances[id] = Inst(block, rect)
    }

    fun unregister(id: Any) { instances.remove(id) }

    /** Общая рамка всех видимых экземпляров блока [block] (у строк списка их несколько) или null, если на экране его нет. */
    fun boundsOf(block: Any): Rect? {
        var l = 0f; var t = 0f; var r = 0f; var b = 0f
        var found = false
        for (i in instances.values) {
            if (i.block !== block) continue
            if (!found) { l = i.rect.left; t = i.rect.top; r = i.rect.right; b = i.rect.bottom; found = true }
            else { l = minOf(l, i.rect.left); t = minOf(t, i.rect.top); r = maxOf(r, i.rect.right); b = maxOf(b, i.rect.bottom) }
        }
        return if (found) Rect(l, t, r, b) else null
    }

    // ---- Перенос зажатого элемента ----

    fun canDrag(b: Any): Boolean = b is Enum<*> && groupOf(b) != null

    fun beginDrag(inst: Rect, pointer: Offset) {
        dragGrab = pointer - inst.topLeft
        dragSize = inst.size
        dragTo(pointer)
    }

    fun dragTo(pointer: Offset) {
        val sel = selected as? Enum<*> ?: return
        val a = area
        // «Призрак» не выходит за пределы интерфейса.
        val maxL = (a.right - dragSize.width).coerceAtLeast(a.left)
        val maxT = (a.bottom - dragSize.height).coerceAtLeast(a.top)
        val tl = pointer - dragGrab
        dragGhost = Rect(Offset(tl.x.coerceIn(a.left, maxL), tl.y.coerceIn(a.top, maxT)), dragSize)
        dragTarget = targetAt(sel, pointer)
    }

    private fun targetAt(sel: Enum<*>, p: Offset): Enum<*>? {
        val g = groupOf(sel) ?: return null
        var best: Enum<*>? = null
        var bestArea = Float.MAX_VALUE
        for (i in instances.values) {
            val b = i.block as? Enum<*> ?: continue
            if (b === sel || !g.owns(b) || g.isHidden(b)) continue
            if (!i.rect.contains(p)) continue
            val ar = i.rect.width * i.rect.height
            if (ar < bestArea) { bestArea = ar; best = b }
        }
        return best
    }

    fun endDrag() {
        val sel = selected as? Enum<*>
        val target = dragTarget as? Enum<*>
        dragGhost = null
        dragTarget = null
        if (sel != null && target != null) groupOf(sel)?.moveTo(sel, target)
    }

    // ---- Растягивание ручками ----

    fun beginResize() {
        val b = selected as? Enum<*> ?: return
        val g = groupOf(b) ?: return
        val r = boundsOf(b) ?: return
        resize = ResizeSession(g.zoom(b) ?: 1f, g.span(b) ?: 1, r.width, r.height)
    }

    /** Угловая ручка: размер. [dx], [dy] — сдвиг пальца от начала; [sx], [sy] — в какую сторону от элемента ручка (+1 вправо/вниз, −1 влево/вверх). */
    fun resizeCorner(dx: Float, dy: Float, sx: Float, sy: Float) {
        val s = resize ?: return
        val b = selected as? Enum<*> ?: return
        val g = groupOf(b) ?: return
        val cur = g.zoom(b) ?: return
        val rx = if (s.w0 > 1f) sx * dx / s.w0 else 0f
        val ry = if (s.h0 > 1f) sy * dy / s.h0 else 0f
        val d = if (abs(rx) >= abs(ry)) rx else ry
        val z = (((s.zoom0 * (1f + d)) * 20f).roundToInt() / 20f).coerceIn(UiLayout.MIN_ZOOM, UiLayout.MAX_ZOOM)
        if (abs(z - cur) > 0.001f) g.setZoom(b, z)
    }

    /** Ручка справа: ширина в слотах. Шаг — ширина одного слота; растягивается, пока рядом есть свободное место. */
    fun resizeWidth(dx: Float) {
        val s = resize ?: return
        val b = selected as? Enum<*> ?: return
        val g = groupOf(b) ?: return
        var cur = g.span(b) ?: return
        val unit = s.w0 / s.span0.coerceAtLeast(1)
        if (unit < 1f) return
        val target = (s.span0 + dx / unit).roundToInt().coerceIn(1, UiLayout.MAX_SPAN)
        while (cur < target && g.canGrow(b)) {
            g.setSpan(b, cur + 1)
            val n = g.span(b) ?: break
            if (n <= cur) break
            cur = n
        }
        while (cur > target && g.canShrink(b)) {
            g.setSpan(b, cur - 1)
            val n = g.span(b) ?: break
            if (n >= cur) break
            cur = n
        }
    }

    fun endResize() { resize = null }
}

/** Запоминает положение элемента на экране: от него считаются координаты пальца при переносе. */
private class RectHolder {
    var origin: Offset = Offset.Zero
    var rect: Rect = Rect.Zero
}

/** Рамка и перехват нажатий для элемента [block] экрана [screen] — работает только пока идёт правка этой страницы. */
@Composable
internal fun Modifier.layoutEdit(screen: String, block: Any): Modifier {
    if (!LayoutEdit.isActive(screen)) return this
    val selected = LayoutEdit.selected === block
    val swapTarget = LayoutEdit.swapping && !selected
    val dropTarget = LayoutEdit.dragTarget === block
    val color = if (swapTarget || dropTarget) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val id = remember { Any() }
    val holder = remember { RectHolder() }
    DisposableEffect(id) { onDispose { LayoutEdit.unregister(id) } }
    return this
        .onGloballyPositioned { c ->
            holder.origin = c.positionInRoot()
            holder.rect = c.boundsInRoot()
            LayoutEdit.register(id, block, holder.rect)
        }
        .drawWithContent {
            drawContent()
            val fill = when {
                dropTarget -> 0.30f
                selected -> 0.14f
                else -> 0.05f
            }
            drawRoundRect(color.copy(alpha = fill), cornerRadius = CornerRadius(10.dp.toPx()))
            drawRoundRect(
                color = color,
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(
                    width = (if (selected || dropTarget) 3.dp else 1.5.dp).toPx(),
                    pathEffect = if (selected || dropTarget) null else PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                ),
            )
        }
        .pointerInput(screen, block) {
            // Проход Initial: нажатие видно раньше, чем кнопки внутри, а «отпускание» забираем себе — их клик отменяется.
            // «Нажатие» не трогаем, поэтому прокрутка списка по элементу работает как обычно.
            // У выбранного элемента долгое нажатие начинает перенос: палец ведёт «призрак», при отпускании элемент встаёт на место
            // того, над которым его отпустили.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val canDrag = LayoutEdit.selected === block && !LayoutEdit.swapping && LayoutEdit.canDrag(block)
                var moved = false
                if (canDrag) {
                    // 0 — ждём, 1 — палец сдвинулся (это прокрутка), 2 — отпустили (это нажатие), 3 — жест пропал
                    var state = 0
                    val finished = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (state == 0) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id }
                            if (ch == null) state = 3
                            else if (ch.changedToUp()) { ch.consume(); state = 2 }
                            else if (!ch.pressed) state = 3
                            else if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) state = 1
                        }
                        true
                    }
                    if (finished == null) {
                        // Долгое нажатие: переносим.
                        LayoutEdit.beginDrag(holder.rect, holder.origin + down.position)
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                            if (ch.changedToUp() || !ch.pressed) break
                            LayoutEdit.dragTo(holder.origin + ch.position)
                        }
                        LayoutEdit.endDrag()
                        return@awaitEachGesture
                    }
                    if (state == 2) { LayoutEdit.tap(block); return@awaitEachGesture }
                    if (state == 3) return@awaitEachGesture
                    moved = true
                }
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (ch.changedToUp()) {
                        if (!moved) {
                            ch.consume()
                            LayoutEdit.tap(block)
                        }
                        break
                    }
                    if (!ch.pressed) break
                }
            }
        }
}

/**
 * Кнопка шириной в [span] обычных кнопок размера [unit]: при span > 1 за кнопкой рисуется «таблетка» нужной ширины,
 * а сама кнопка стоит по центру. Значок остаётся обычного размера.
 */
@Composable
internal fun SpanBox(span: Int, unit: Dp, content: @Composable () -> Unit) {
    if (span <= 1) {
        content()
        return
    }
    Box(
        Modifier.width(unit * span).height(unit).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Прячет панель правки, когда страницу начинают прокручивать (или тянут по экрану): жест, начатый не на самой панели,
 * сдвинувшийся дальше порога касания. Ставится на корневой контейнер приложения; события не забирает — они идут дальше как обычно.
 */
internal fun Modifier.editPanelAutoHide(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (LayoutEdit.pager == null || !LayoutEdit.panelOpen) return@awaitEachGesture
        // Координаты корня: контейнер и слой правки занимают весь экран.
        if (LayoutEdit.panelBounds.contains(down.position + LayoutEdit.area.topLeft)) return@awaitEachGesture
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                LayoutEdit.setPanelOpen(false)
                break
            }
        }
    }
}

private const val WRAP_FILL_ID = "wrapFill"

/** В [WrapRow] такой элемент занимает всё свободное место своей строки (как `weight(1f)` в обычном ряду). */
internal fun Modifier.wrapFill(): Modifier = layoutId(WRAP_FILL_ID)

/**
 * Ряд кнопок, у которого лишнее не наезжает на соседей, а переносится на следующую строку. Элементы идут слева направо
 * в заданном порядке; не поместившийся элемент (например, растянутая или увеличенная кнопка) переходит на новую строку вместе
 * со всеми, кто за ним. Элемент с [wrapFill] забирает остаток своей строки, но не меньше [fillMin].
 */
@Composable
internal fun WrapRow(
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    fillMin: Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    val policy = remember(verticalAlignment, fillMin) { WrapRowPolicy(verticalAlignment, fillMin) }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

private class WrapRowPolicy(private val vAlign: Alignment.Vertical, private val fillMin: Dp) : MeasurePolicy {
    private fun isFill(m: Measurable) = m.layoutId == WRAP_FILL_ID

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): androidx.compose.ui.layout.MeasureResult {
        val finite = constraints.hasBoundedWidth
        val maxW = constraints.maxWidth
        val fillMinPx = fillMin.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val fixed = arrayOfNulls<Placeable>(measurables.size)
        measurables.forEachIndexed { i, m -> if (!isFill(m)) fixed[i] = m.measure(loose) }

        // Разбивка на строки: новая строка начинается, когда очередной элемент не помещается.
        val lines = ArrayList<MutableList<Int>>()
        var cur = ArrayList<Int>()
        var curW = 0
        measurables.forEachIndexed { i, m ->
            val w = if (isFill(m)) fillMinPx else fixed[i]!!.width
            if (cur.isNotEmpty() && finite && curW + w > maxW) {
                lines += cur
                cur = ArrayList()
                curW = 0
            }
            cur += i
            curW += w
        }
        if (cur.isNotEmpty()) lines += cur

        // Элементы с wrapFill делят остаток своей строки поровну.
        val placed = arrayOfNulls<Placeable>(measurables.size)
        val lineW = IntArray(lines.size)
        val lineH = IntArray(lines.size)
        lines.forEachIndexed { li, idx ->
            val fixedW = idx.sumOf { fixed[it]?.width ?: 0 }
            val fills = idx.filter { isFill(measurables[it]) }
            val share = if (fills.isEmpty()) 0 else if (finite) ((maxW - fixedW) / fills.size).coerceAtLeast(0) else fillMinPx
            var w = fixedW
            var h = 0
            idx.forEach { i ->
                val p = if (isFill(measurables[i])) {
                    measurables[i].measure(Constraints(minWidth = share, maxWidth = share, maxHeight = constraints.maxHeight)).also { w += it.width }
                } else fixed[i]!!
                placed[i] = p
                h = maxOf(h, p.height)
            }
            lineW[li] = w
            lineH[li] = h
        }
        val width = constraints.constrainWidth(lineW.maxOrNull() ?: 0)
        val height = constraints.constrainHeight(lineH.sum())
        return layout(width, height) {
            var y = 0
            lines.forEachIndexed { li, idx ->
                var x = 0
                idx.forEach { i ->
                    val p = placed[i]!!
                    p.placeRelative(x, y + vAlign.align(p.height, lineH[li]))
                    x += p.width
                }
                y += lineH[li]
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.sumOf { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0
}

/**
 * Плитки главной («Чарт», «Новинки», «История треков», «Подкасты»). Ряд — до [COLS] слотов; по умолчанию плитка занимает 2
 * слота, одинокая плитка ряда — весь ряд. Две подряд идущие видимые плитки одной пары стоят рядом.
 */
internal object HomeTileFlow {
    const val COLS = 4

    private val pairs = listOf(
        listOf(HomeBlock.Chart, HomeBlock.New),
        listOf(HomeBlock.HistoryTracks, HomeBlock.Podcasts),
    )

    fun isTile(b: Any?): Boolean = b is HomeBlock && pairs.any { b in it }

    /** Видимые блоки главной по порядку («Подкасты» без поддержки у сервиса не считаются). */
    fun visible(): List<HomeBlock> = UiLayout.order(LScreen.HOME, HomeBlock.entries).filter {
        !UiLayout.isHidden(LScreen.HOME, it) && (it != HomeBlock.Podcasts || ActiveProvider.caps.podcasts)
    }

    /** Ряды плиток из списка видимых блоков [visible] (в порядке появления). */
    fun rows(visible: List<HomeBlock>): List<List<HomeBlock>> {
        val out = ArrayList<List<HomeBlock>>()
        var i = 0
        while (i < visible.size) {
            val b = visible[i]
            if (!isTile(b)) { i++; continue }
            val pair = pairs.first { b in it }
            val next = visible.getOrNull(i + 1)
            if (next != null && next in pair) { out += listOf(b, next); i += 2 } else { out += listOf(b); i++ }
        }
        return out
    }

    fun rowOf(b: HomeBlock): List<HomeBlock>? = rows(visible()).firstOrNull { b in it }

    /** Ширина плитки в слотах: заданная пользователем или по умолчанию (2; одинокая в ряду — весь ряд). */
    fun span(b: HomeBlock, row: List<HomeBlock>): Int {
        val e = UiLayout.span(LScreen.HOME, b)
        return if (e > 0) e else if (row.size == 1) COLS else 2
    }
}

// ---------- Группы блоков для панели правки ----------

/** Набор блоков одного экрана (или дока), которые правятся вместе: порядок, скрытие, размер, ширина. */
internal abstract class EditGroup(val screen: String, val horizontal: Boolean) {
    abstract fun owns(b: Enum<*>): Boolean
    abstract fun ordered(): List<Enum<*>>
    abstract fun isHidden(b: Enum<*>): Boolean
    abstract fun hiddenBlocks(): List<Enum<*>>
    abstract fun canHide(b: Enum<*>): Boolean
    abstract fun setHidden(b: Enum<*>, hidden: Boolean)

    /** Сдвиг на [delta] позиций среди видимых соседей. */
    abstract fun move(b: Enum<*>, delta: Int)
    abstract fun swap(a: Enum<*>, b: Enum<*>)

    /** Ставит [b] на место [target], остальные сдвигаются (перенос зажатого элемента). */
    abstract fun moveTo(b: Enum<*>, target: Enum<*>)

    /** Размер элемента (1 — исходный) или null, если у него нет размера. */
    open fun zoom(b: Enum<*>): Float? = null
    open fun setZoom(b: Enum<*>, v: Float) {}

    /** Ширина в слотах или null, если элемент растягивать нельзя. */
    open fun span(b: Enum<*>): Int? = null
    open fun canGrow(b: Enum<*>): Boolean = false
    open fun canShrink(b: Enum<*>): Boolean = (span(b) ?: 1) > 1
    open fun setSpan(b: Enum<*>, v: Int) {}
    abstract fun reset(b: Enum<*>)

    /** Есть ли у элементов группы отступы (сверху, снизу, по бокам), в dp. */
    open fun hasGaps(): Boolean = false
    open fun gap(b: Enum<*>, side: Int): Int = 0
    open fun setGap(b: Enum<*>, side: Int, dp: Int) {}

    fun title(b: Enum<*>): String = when (b) {
        is LayoutBlock -> b.label
        is Tab -> b.title
        else -> b.name
    }
}

/** Правило ширины: [growable] можно растягивать, пока сумма ширин [occupying] меньше [slots]. */
internal class SlotRule(val growable: Set<Enum<*>>, val occupying: Set<Enum<*>>, val slots: Int)

@Suppress("UNCHECKED_CAST")
internal open class BlockGroup<E : Enum<E>>(
    screen: String,
    private val all: List<E>,
    horizontal: Boolean,
    private val noZoom: Set<String> = emptySet(),
    private val rule: SlotRule? = null,
) : EditGroup(screen, horizontal) {
    protected fun c(b: Enum<*>): E = b as E

    override fun owns(b: Enum<*>): Boolean = all.any { it === b }
    override fun ordered(): List<Enum<*>> = UiLayout.order(screen, all)
    override fun isHidden(b: Enum<*>): Boolean = UiLayout.isHidden(screen, c(b))
    override fun hiddenBlocks(): List<Enum<*>> = UiLayout.order(screen, all).filter { UiLayout.isHidden(screen, it) }
    override fun canHide(b: Enum<*>): Boolean = true
    override fun setHidden(b: Enum<*>, hidden: Boolean) = UiLayout.setHidden(screen, c(b), hidden)

    override fun move(b: Enum<*>, delta: Int) {
        val list = UiLayout.order(screen, all)
        val i = list.indexOf(c(b))
        if (i < 0) return
        var j = i + delta
        while (j in list.indices && UiLayout.isHidden(screen, list[j])) j += delta
        if (j !in list.indices) return
        UiLayout.move(screen, all, i, j)
    }

    override fun swap(a: Enum<*>, b: Enum<*>) = UiLayout.swap(screen, all, c(a), c(b))

    override fun moveTo(b: Enum<*>, target: Enum<*>) {
        val list = UiLayout.order(screen, all)
        val i = list.indexOf(c(b))
        val j = list.indexOf(c(target))
        if (i < 0 || j < 0 || i == j) return
        UiLayout.move(screen, all, i, j)
    }

    override fun zoom(b: Enum<*>): Float? = if (b.name in noZoom) null else UiLayout.zoom(screen, c(b))
    override fun setZoom(b: Enum<*>, v: Float) = UiLayout.setZoom(screen, c(b), v)

    override fun span(b: Enum<*>): Int? =
        if (rule != null && b in rule.growable) UiLayout.span(screen, c(b)).coerceAtLeast(1) else null

    private fun used(r: SlotRule): Int = r.occupying.sumOf { o -> if (isHidden(o)) 0 else (span(o) ?: 1) }

    override fun canGrow(b: Enum<*>): Boolean {
        val r = rule ?: return false
        if (b !in r.growable) return false
        return (span(b) ?: 1) < UiLayout.MAX_SPAN && used(r) < r.slots
    }

    override fun setSpan(b: Enum<*>, v: Int) = UiLayout.setSpan(screen, c(b), if (v <= 1) 0 else v)
    override fun reset(b: Enum<*>) = UiLayout.resetBlock(screen, c(b))

    // Отступы применяются везде, кроме нижнего ряда кнопок плеера.
    override fun hasGaps(): Boolean = screen != LScreen.PLAYER

    override fun gap(b: Enum<*>, side: Int): Int = when (side) {
        GAP_TOP -> UiLayout.gap(screen, c(b))
        GAP_BOTTOM -> UiLayout.gapBottom(screen, c(b))
        else -> UiLayout.padH(screen, c(b))
    }

    override fun setGap(b: Enum<*>, side: Int, dp: Int) {
        when (side) {
            GAP_TOP -> UiLayout.setGap(screen, c(b), dp)
            GAP_BOTTOM -> UiLayout.setGapBottom(screen, c(b), dp)
            else -> UiLayout.setPadH(screen, c(b), dp)
        }
    }
}

internal const val GAP_TOP = 0
internal const val GAP_BOTTOM = 1
internal const val GAP_SIDES = 2

/** Главная: обычные блоки плюс плитки с шириной в слотах по правилам [HomeTileFlow]. */
internal class HomeGroup : BlockGroup<HomeBlock>(LScreen.HOME, HomeBlock.entries, horizontal = false) {
    override fun span(b: Enum<*>): Int? {
        val t = b as? HomeBlock ?: return null
        if (!HomeTileFlow.isTile(t)) return null
        val row = HomeTileFlow.rowOf(t) ?: return null
        return HomeTileFlow.span(t, row)
    }

    override fun canGrow(b: Enum<*>): Boolean {
        val t = b as? HomeBlock ?: return false
        if (!HomeTileFlow.isTile(t)) return false
        val row = HomeTileFlow.rowOf(t) ?: return false
        val used = row.sumOf { HomeTileFlow.span(it, row) }
        return used < HomeTileFlow.COLS && HomeTileFlow.span(t, row) < HomeTileFlow.COLS
    }

    override fun canShrink(b: Enum<*>): Boolean = (span(b) ?: 1) > 1
    override fun setSpan(b: Enum<*>, v: Int) = UiLayout.setSpan(LScreen.HOME, c(b), v)
}

/** Вкладки нижней панели: порядок, скрытие и размер значков. */
internal class DockGroup : EditGroup(LScreen.DOCK, horizontal = true) {
    override fun owns(b: Enum<*>): Boolean = b is Tab
    override fun ordered(): List<Enum<*>> = NavSettings.tabs
    override fun isHidden(b: Enum<*>): Boolean = b is Tab && b !in NavSettings.tabs
    override fun hiddenBlocks(): List<Enum<*>> = Tab.entries.filter { it !in NavSettings.tabs }
    override fun canHide(b: Enum<*>): Boolean = b is Tab && b.removable && NavSettings.tabs.size > 1
    override fun setHidden(b: Enum<*>, hidden: Boolean) { if (b is Tab) NavSettings.setVisible(b, !hidden) }

    override fun move(b: Enum<*>, delta: Int) { if (b is Tab) NavSettings.move(b, delta) }

    override fun swap(a: Enum<*>, b: Enum<*>) {
        if (a !is Tab || b !is Tab) return
        val list = NavSettings.tabs.toMutableList()
        val i = list.indexOf(a)
        val j = list.indexOf(b)
        if (i < 0 || j < 0 || i == j) return
        list[i] = b
        list[j] = a
        NavSettings.setTabsFromString(list.joinToString(",") { it.name })
    }

    override fun moveTo(b: Enum<*>, target: Enum<*>) {
        if (b !is Tab || target !is Tab) return
        val i = NavSettings.tabs.indexOf(b)
        val j = NavSettings.tabs.indexOf(target)
        if (i < 0 || j < 0 || i == j) return
        NavSettings.reorder(i, j)
    }

    override fun zoom(b: Enum<*>): Float? = UiLayout.dockIconZoom
    override fun setZoom(b: Enum<*>, v: Float) = UiLayout.updateDockIconZoom(v)
    override fun reset(b: Enum<*>) = UiLayout.updateDockIconZoom(1f)
}
// ---------- Плавающая кнопка и панель ----------

/**
 * Слой поверх всего приложения: плавающая кнопка («карандаш»), панель правки и всё, что рисуется поверх выбранного элемента
 * (ручки растягивания, «призрак» при переносе, маленькая панель у элемента). [tab] — открытая вкладка, [playerOpen] —
 * раскрыт ли полный плеер, [miniShown] — виден ли мини-плеер, [hideFab] — скрыть кнопку (открыты настройки или поиск).
 */
@Composable
internal fun LayoutEditOverlay(tab: Tab, playerOpen: Boolean, miniShown: Boolean, hideFab: Boolean) {
    if (!UiLayout.editMode) return
    val current: String? = if (playerOpen) LScreen.PLAYER else when (tab) {
        Tab.Home -> LScreen.HOME
        Tab.Playlists -> LScreen.PLAYLISTS
        Tab.Profile -> LScreen.PROFILE
        else -> null
    }
    val miniAvailable = miniShown && !playerOpen
    SideEffect { LayoutEdit.miniOn = miniAvailable }
    // Страница сменилась, пока шла правка, — правка переходит на новую страницу, выбор сбрасывается, если выбранного не видно.
    LaunchedEffect(current, playerOpen, miniAvailable) { LayoutEdit.retarget(current ?: LScreen.DOCK) }
    val pager = LayoutEdit.pager
    val editing = pager != null
    Box(Modifier.fillMaxSize().onGloballyPositioned { LayoutEdit.area = it.boundsInRoot() }) {
        if (editing) EditHandlesLayer()
        EditPanelHost()
        if (editing || !hideFab) {
            if (editing && !LayoutEdit.panelOpen) {
                // Панель убрана: рядом с карандашом маленькая кнопка «Готово».
                SmallFloatingActionButton(
                    onClick = { LayoutEdit.stop() },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).offset(y = (-64).dp),
                ) { Icon(painterResource(R.drawable.ms_check), contentDescription = "Готово") }
            }
            FloatingActionButton(
                onClick = {
                    if (!editing) LayoutEdit.start(current ?: LScreen.DOCK)
                    else LayoutEdit.setPanelOpen(!LayoutEdit.panelOpen)
                },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = FAB_END),
                containerColor = if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (editing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            ) { Icon(painterResource(R.drawable.ms_edit), contentDescription = if (editing) "Панель правки" else "Править интерфейс") }
        }
    }
}

private val FAB_END = 12.dp
private val FAB_SIZE = 56.dp
private val PANEL_WIDTH = 300.dp

/**
 * Панель правки — плавающее окно: «выезжает» из кнопки-карандаша и уезжает обратно, а пока открыта, её можно перетащить
 * за верхнюю полоску в любое место экрана. Закрытая панель не мешает правке: элементы остаются подсвеченными.
 */
@Composable
private fun EditPanelHost() {
    val pager = LayoutEdit.pager
    val open = pager != null && LayoutEdit.panelOpen
    val prog by animateFloatAsState(if (open) 1f else 0f, animationSpec = tween(240), label = "editPanel")
    if (pager == null || (!open && prog < 0.01f)) return
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val a = LayoutEdit.area
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val fabEnd = with(density) { FAB_END.toPx() }
    val fabSize = with(density) { FAB_SIZE.toPx() }
    val gap = with(density) { 10.dp.toPx() }
    // Центр кнопки-карандаша (она у правого края по центру): из неё панель выезжает.
    val fabCx = a.width - fabEnd - fabSize / 2f
    val fabCy = a.height / 2f

    fun clampPos(p: Offset): Offset {
        val maxX = (a.width - size.width).coerceAtLeast(0f)
        val maxY = (a.height - size.height).coerceAtLeast(top)
        return Offset(p.x.coerceIn(0f, maxX), p.y.coerceIn(top, maxY))
    }

    val defaultPos = Offset(a.width - fabEnd - fabSize - gap - size.width, fabCy - size.height / 2f)
    val pos = clampPos(LayoutEdit.panelPos ?: defaultPos)
    val move by rememberUpdatedState<(Offset) -> Unit> { d -> LayoutEdit.panelPos = clampPos(pos + d) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .graphicsLayer {
                val s = 0.25f + 0.75f * prog
                scaleX = s
                scaleY = s
                alpha = prog
                transformOrigin = TransformOrigin(
                    if (size.width > 0) (fabCx - pos.x) / size.width else 1f,
                    if (size.height > 0) (fabCy - pos.y) / size.height else 0.5f,
                )
            }
            .widthIn(max = PANEL_WIDTH)
            .fillMaxWidth()
            .onSizeChanged { size = it }
            .onGloballyPositioned { LayoutEdit.panelBounds = it.boundsInRoot() },
    ) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
            // Шапка: за неё панель таскают.
            Column(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        move(drag)
                    }
                },
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 4.dp)
                        .width(40.dp).height(4.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Правка: ${LayoutEdit.pagerTitle(pager)}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Убрать панель обратно в карандаш: правка продолжает работать.
                    IconButton(onClick = { LayoutEdit.setPanelOpen(false) }) {
                        Icon(painterResource(R.drawable.ms_chevron_right), contentDescription = "Убрать панель")
                    }
                    TextButton(onClick = { LayoutEdit.stop() }) { Text("Готово") }
                }
            }
            EditPanelBody()
        }
    }
}

@Composable
private fun EditPanelBody() {
    val sel = LayoutEdit.selected as? Enum<*>
    val g = sel?.let { LayoutEdit.groupOf(it) }
    Column(
        Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sel == null || g == null) {
            Text(
                "Подсвечены все кнопки страницы, мини-плеера и дока. Нажмите на ту, которую хотите изменить.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val hidden = LayoutEdit.activeGroups().flatMap { grp -> grp.hiddenBlocks().map { grp to it } }
            if (hidden.isNotEmpty()) {
                Text("Скрытые — нажмите, чтобы вернуть:", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    hidden.forEach { (grp, b) ->
                        AssistChip(
                            onClick = { grp.setHidden(b, false); LayoutEdit.select(b) },
                            label = { Text(grp.title(b), maxLines = 1) },
                        )
                    }
                }
            }
        } else {
            SelectedControls(g, sel)
        }
    }
}

// ---------- Ручки, «призрак» переноса и панель у выбранного элемента ----------

/** Всё, что рисуется поверх интерфейса для выбранного элемента. */
@Composable
private fun EditHandlesLayer() {
    val sel = LayoutEdit.selected as? Enum<*>
    val g = sel?.let { LayoutEdit.groupOf(it) }
    val origin = LayoutEdit.area.topLeft
    val ghost = LayoutEdit.dragGhost
    if (ghost != null && g != null && sel != null) {
        DragGhost(ghost.translate(-origin.x, -origin.y), g.title(sel))
        return
    }
    if (sel == null || g == null || LayoutEdit.swapping) return
    val r = LayoutEdit.boundsOf(sel) ?: return
    val lr = r.translate(-origin.x, -origin.y)
    ResizeHandles(g, sel, lr)
    // Панель убрана — основные действия остаются под рукой, у самого элемента.
    if (!LayoutEdit.panelOpen) SelectionToolbar(g, sel, lr)
}

/** Полупрозрачная копия переносимого элемента: ведёт её палец, не выходя за пределы интерфейса. */
@Composable
private fun DragGhost(rect: Rect, title: String) {
    val density = LocalDensity.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            .border(2.dp, MaterialTheme.colorScheme.primary, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

/**
 * Ручки на рамке выбранного элемента — как у окна в Windows. Угловые меняют размер, средняя справа — ширину в слотах
 * (только у тех, кого можно растянуть).
 */
@Composable
private fun ResizeHandles(g: EditGroup, b: Enum<*>, r: Rect) {
    val a = LayoutEdit.area
    val density = LocalDensity.current
    val margin = with(density) { 20.dp.toPx() }
    fun inside(o: Offset) = Offset(
        o.x.coerceIn(margin, (a.width - margin).coerceAtLeast(margin)),
        o.y.coerceIn(margin, (a.height - margin).coerceAtLeast(margin)),
    )
    if (g.zoom(b) != null) {
        val round = CircleShape
        // sx, sy: в какую сторону от элемента стоит ручка.
        DragHandle(inside(Offset(r.left, r.top)), 14.dp, 14.dp, round, -1f, -1f)
        DragHandle(inside(Offset(r.right, r.top)), 14.dp, 14.dp, round, 1f, -1f)
        DragHandle(inside(Offset(r.left, r.bottom)), 14.dp, 14.dp, round, -1f, 1f)
        DragHandle(inside(Offset(r.right, r.bottom)), 14.dp, 14.dp, round, 1f, 1f)
    }
    if (g.span(b) != null) {
        // Ручка ширины: вытянутая «таблетка» на правой стороне; ширина меняется шагами по слотам.
        DragHandle(inside(Offset(r.right, r.center.y)), 8.dp, 28.dp, RoundedCornerShape(4.dp), 0f, 0f, widthOnly = true)
    }
}

/** Одна ручка: зона нажатия 40 dp вокруг видимой точки. [sx], [sy] — сторона углового растягивания. */
@Composable
private fun DragHandle(center: Offset, w: Dp, h: Dp, shape: Shape, sx: Float, sy: Float, widthOnly: Boolean = false) {
    val density = LocalDensity.current
    val hit = 40.dp
    val half = with(density) { hit.toPx() / 2f }
    Box(
        Modifier
            .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            .size(hit)
            .pointerInput(widthOnly, sx, sy) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero; LayoutEdit.beginResize() },
                    onDragEnd = { LayoutEdit.endResize() },
                    onDragCancel = { LayoutEdit.endResize() },
                ) { change, amount ->
                    change.consume()
                    total += amount
                    if (widthOnly) LayoutEdit.resizeWidth(total.x) else LayoutEdit.resizeCorner(total.x, total.y, sx, sy)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(w, h)
                .background(MaterialTheme.colorScheme.primary, shape)
                .border(2.dp, MaterialTheme.colorScheme.surface, shape),
        )
    }
}

/** Маленькая панель под (или над) выбранным элементом: пока основная панель убрана, здесь «Скрыть», «Сброс» и снять выбор. */
@Composable
private fun SelectionToolbar(g: EditGroup, b: Enum<*>, r: Rect) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val a = LayoutEdit.area
    val top = WindowInsets.statusBars.getTop(density).toFloat()
    val gap = with(density) { 26.dp.toPx() }
    val below = r.bottom + gap
    val y = if (below + size.height <= a.height) below else (r.top - gap - size.height).coerceAtLeast(top)
    val x = (r.center.x - size.width / 2f).coerceIn(0f, (a.width - size.width).coerceAtLeast(0f))
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .onSizeChanged { size = it },
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                g.title(b), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(max = 120.dp).padding(horizontal = 6.dp),
            )
            TextButton(onClick = { g.setHidden(b, true); LayoutEdit.select(null) }, enabled = g.canHide(b)) { Text("Скрыть") }
            TextButton(onClick = { g.reset(b) }) { Text("Сброс") }
            IconButton(onClick = { LayoutEdit.select(null) }) {
                Icon(painterResource(R.drawable.ms_close), contentDescription = "Снять выбор")
            }
        }
    }
}

@Composable
private fun SelectedControls(g: EditGroup, b: Enum<*>) {
    Text(g.title(b), style = MaterialTheme.typography.titleSmall, maxLines = 1)
    Text(
        "Тяните за точки на рамке, чтобы растянуть. Зажмите элемент и перетащите на другой — они поменяются местами.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // Стрелки: у вертикальных списков вверх / вниз, у рядов кнопок влево / вправо.
        FilledTonalIconButton(onClick = { g.move(b, -1) }) {
            Icon(painterResource(if (g.horizontal) R.drawable.ms_arrow_back else R.drawable.ms_arrow_upward), contentDescription = if (g.horizontal) "Влево" else "Вверх")
        }
        FilledTonalIconButton(onClick = { g.move(b, 1) }) {
            Icon(
                painterResource(if (g.horizontal) R.drawable.ms_arrow_back else R.drawable.ms_arrow_upward),
                contentDescription = if (g.horizontal) "Вправо" else "Вниз",
                modifier = Modifier.rotate(180f),
            )
        }
        FilledTonalButton(onClick = { LayoutEdit.toggleSwap() }) { Text(if (LayoutEdit.swapping) "Отмена" else "Поменять") }
        FilledTonalButton(onClick = { g.setHidden(b, true); LayoutEdit.select(null) }, enabled = g.canHide(b)) { Text("Скрыть") }
        FilledTonalButton(onClick = { g.reset(b) }) { Text("Сброс") }
    }
    if (g.hasGaps()) {
        listOf(GAP_TOP to "Сверху", GAP_BOTTOM to "Снизу", GAP_SIDES to "По бокам").forEach { (side, label) ->
            val v = g.gap(b, side)
            StepperRow(
                label = label, value = "$v dp",
                canMinus = v > 0, canPlus = v < UiLayout.MAX_GAP,
                onMinus = { g.setGap(b, side, (v - 4).coerceAtLeast(0)) },
                onPlus = { g.setGap(b, side, (v + 4).coerceAtMost(UiLayout.MAX_GAP)) },
            )
        }
    }
    if (LayoutEdit.swapping) {
        Text(
            "Нажмите на элемент, с которым поменять местами.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
        )
    }
    val z = g.zoom(b)
    if (z != null) {
        StepperRow(
            label = "Размер", value = "${(z * 100).roundToInt()}%",
            canMinus = z > UiLayout.MIN_ZOOM + 0.001f, canPlus = z < UiLayout.MAX_ZOOM - 0.001f,
            onMinus = { g.setZoom(b, (((z - 0.1f) * 20f).roundToInt() / 20f).coerceAtLeast(UiLayout.MIN_ZOOM)) },
            onPlus = { g.setZoom(b, (((z + 0.1f) * 20f).roundToInt() / 20f).coerceAtMost(UiLayout.MAX_ZOOM)) },
        )
    }
    val sp = g.span(b)
    if (sp != null) {
        val grow = g.canGrow(b)
        StepperRow(
            label = "Ширина", value = "$sp из ${UiLayout.MAX_SPAN}",
            canMinus = g.canShrink(b), canPlus = grow,
            onMinus = { g.setSpan(b, sp - 1) },
            onPlus = { g.setSpan(b, sp + 1) },
        )
        if (!grow && sp < UiLayout.MAX_SPAN) {
            Text(
                "Растянуть нельзя: рядом нет свободного места. Скройте соседнюю кнопку.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, canMinus: Boolean, canPlus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(76.dp))
        FilledTonalIconButton(onClick = onMinus, enabled = canMinus) { Text("−") }
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(76.dp))
        FilledTonalIconButton(onClick = onPlus, enabled = canPlus) { Text("+") }
    }
}
