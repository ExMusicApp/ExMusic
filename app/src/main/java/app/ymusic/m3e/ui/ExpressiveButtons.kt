@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.SegmentedButton as M3SegmentedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------- Нажатие кнопок в стиле Material 3 Expressive ----------
// Здесь одноимённые обёртки над кнопками Material 3. Они лежат в пакете app.ymusic.m3e.ui и перекрывают
// material3.* (звёздочный импорт) во всех файлах пакета, поэтому существующие вызовы Button / TextButton /
// IconButton / FloatingActionButton / SegmentedButton и т.д. получают анимацию нажатия без правок на местах.
//
// Что делает каждая кнопка (как в спецификации M3E):
// Как в спецификации M3E (m3.material.io → Buttons), кнопка с контейнером при нажатии НЕ сжимается, а меняет форму:
//  • «перетекание» формы: круг/таблетка → скругление 12 dp, пока кнопка нажата (Button, Tonal, Outlined, Text,
//    Filled/FilledTonal IconButton, FAB); анимация — быстрая «пространственная» пружина темы M3E
//    (fastSpatial: damping 0.6, stiffness 800), поэтому при отпускании форма «перелетает» и возвращается с отскоком;
//  • в группе кнопок (Слушать / Вперемешку) нажатая кнопка расширяется на 15 %, соседняя уступает место (animateWidth);
//  • у значков без контейнера (IconButton) и у элементов без доступа к форме (чипы, сегменты, пункты меню) остаётся лёгкое сжатие;
//  • у переключаемых значков (лайк, повтор, текст песни…) — «хлопок» значка при смене состояния.

/** Быстрая пространственная пружина M3E для нажатия: заметный отскок при отпускании. */
private fun pressSpring() = fxSpring<Float>(dampingRatio = 0.6f, stiffness = 800f)

internal class PressState(val source: MutableInteractionSource, val fraction: State<Float>)

/**
 * Состояние нажатия: [fraction] плавно идёт от 0 до 1 (с «перелётом» из-за пружины).
 * Если [source] передан снаружи (например, для группы кнопок), нажатие читается из него.
 */
@Composable
internal fun rememberPress(
    enabled: Boolean,
    source: MutableInteractionSource? = null,
    hold: Boolean = false,
    downTick: Boolean = true,
): PressState {
    val src = source ?: remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // Вибро в момент нажатия и тики с нарастанием при удержании (для кнопок с включённым виброоткликом).
    if (hold) HoldHaptics(pressed && enabled, downTick)
    val fraction = animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = pressSpring(),
        label = "buttonPress",
    )
    return remember(src) { PressState(src, fraction) }
}

internal fun Modifier.pressScale(press: PressState, amount: Float): Modifier = graphicsLayer {
    val v = 1f - amount * press.fraction.value
    scaleX = v
    scaleY = v
}

/** Форма, у которой углы плавно сжимаются до [pressed] (но не больше исходного радиуса) на [f] от 0 до 1. */
internal fun CornerBasedShape.morphed(f: Float, pressed: Dp): CornerBasedShape {
    fun c(base: CornerSize): CornerSize = object : CornerSize {
        override fun toPx(shapeSize: Size, density: Density): Float {
            val b = base.toPx(shapeSize, density)
            val t = minOf(b, with(density) { pressed.toPx() })
            return (b + (t - b) * f).coerceAtLeast(0f)
        }
    }
    return copy(topStart = c(topStart), topEnd = c(topEnd), bottomEnd = c(bottomEnd), bottomStart = c(bottomStart))
}

private fun Shape.pressMorph(press: PressState, pressed: Dp): Shape =
    (this as? CornerBasedShape)?.morphed(press.fraction.value, pressed) ?: this

// ---------- Группа кнопок: нажатая кнопка расширяется, соседние уступают место ----------

/**
 * Вес кнопки в строке-группе. Пока нажата [self] — вес растёт, пока нажата одна из [others] — уменьшается;
 * изменения идут пружиной M3E, поэтому ряд «дышит» при нажатии.
 */
@Composable
internal fun RowScope.groupWeight(
    self: MutableInteractionSource,
    others: List<MutableInteractionSource>,
    amount: Float = 0.15f,
): Modifier {
    val selfPressed by self.collectIsPressedAsState()
    var otherPressed = false
    for (o in others) {
        val p by o.collectIsPressedAsState()
        if (p) otherPressed = true
    }
    val w by animateFloatAsState(
        // Суммарная ширина группы постоянна: нажатая кнопка +amount, остальные делят эту прибавку поровну.
        targetValue = 1f + (if (selfPressed) amount else 0f) -
            (if (otherPressed && !selfPressed) amount / others.size.coerceAtLeast(1) else 0f),
        animationSpec = pressSpring(),
        label = "groupWeight",
    )
    return Modifier.weight(w.coerceAtLeast(0.2f))
}

// ---------- Кнопки ----------

@Composable
internal fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    pressAmount: Float = 0f,
    haptic: HapticKind? = HapticKind.Click,
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberPress(enabled, interactionSource, hold = haptic != null)
    androidx.compose.material3.Button(
        onClick = withHaptic(haptic, onClick),
        modifier = modifier.pressScale(press, pressAmount),
        enabled = enabled,
        shape = shape.pressMorph(press, 12.dp),
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = press.source,
        content = content,
    )
}

@Composable
internal fun FilledTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    pressAmount: Float = 0f,
    haptic: HapticKind? = HapticKind.Click,
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberPress(enabled, interactionSource, hold = haptic != null)
    androidx.compose.material3.FilledTonalButton(
        onClick = withHaptic(haptic, onClick),
        modifier = modifier.pressScale(press, pressAmount),
        enabled = enabled,
        shape = shape.pressMorph(press, 12.dp),
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = press.source,
        content = content,
    )
}

@Composable
internal fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    pressAmount: Float = 0f,
    haptic: HapticKind? = HapticKind.Click,
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberPress(enabled, interactionSource, hold = haptic != null)
    androidx.compose.material3.OutlinedButton(
        onClick = withHaptic(haptic, onClick),
        modifier = modifier.pressScale(press, pressAmount),
        enabled = enabled,
        shape = shape.pressMorph(press, 12.dp),
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = press.source,
        content = content,
    )
}

@Composable
internal fun TextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    haptic: HapticKind? = HapticKind.Tick,
    content: @Composable RowScope.() -> Unit,
) {
    val press = rememberPress(enabled, interactionSource, hold = haptic != null)
    androidx.compose.material3.TextButton(
        onClick = withHaptic(haptic, onClick),
        modifier = modifier,
        enabled = enabled,
        shape = shape.pressMorph(press, 12.dp),
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = press.source,
        content = content,
    )
}

// ---------- Кнопки-значки ----------

@Composable
internal fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    haptic: HapticKind? = HapticKind.Tick,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    val press = rememberPress(enabled, interactionSource, hold = haptic != null)
    val tip = rememberButtonHint()
    androidx.compose.material3.IconButton(
        onClick = withHaptic(haptic, onClick),
        modifier = modifier.pressScale(press, 0.14f).hintOnLongPress(tip.state, NavSettings.hints, LocalHoldHaptics.current),
        enabled = enabled,
        colors = colors,
        interactionSource = press.source,
    ) { HintContent(tip, hint, content) }
}

@Composable
internal fun FilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    haptic: HapticKind? = HapticKind.Click,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    val press = rememberPress(enabled, interactionSource, hold = haptic != null)
    val tip = rememberButtonHint()
    androidx.compose.material3.FilledIconButton(
        onClick = withHaptic(haptic, onClick),
        modifier = modifier.hintOnLongPress(tip.state, NavSettings.hints, LocalHoldHaptics.current),
        enabled = enabled,
        shape = shape.pressMorph(press, 12.dp),
        colors = colors,
        interactionSource = press.source,
    ) { HintContent(tip, hint, content) }
}

@Composable
internal fun FilledTonalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    haptic: HapticKind? = HapticKind.Click,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    val press = rememberPress(enabled, interactionSource, hold = haptic != null)
    val tip = rememberButtonHint()
    androidx.compose.material3.FilledTonalIconButton(
        onClick = withHaptic(haptic, onClick),
        modifier = modifier.hintOnLongPress(tip.state, NavSettings.hints, LocalHoldHaptics.current),
        enabled = enabled,
        shape = shape.pressMorph(press, 12.dp),
        colors = colors,
        interactionSource = press.source,
    ) { HintContent(tip, hint, content) }
}

/**
 * Переключаемый значок (лайк, повтор, текст песни…): то же сжатие при нажатии, а при смене состояния значок
 * «хлопает» — на мгновение уменьшается и пружинно отскакивает с перелётом.
 */
@Composable
internal fun IconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    haptic: HapticKind? = HapticKind.Click,
    hint: String? = null,
    content: @Composable () -> Unit,
) {
    val press = rememberPress(enabled, interactionSource, hold = haptic != null)
    val tip = rememberButtonHint()
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) { first = false; return@LaunchedEffect }
        pop.snapTo(0.6f)
        pop.animateTo(1f, fxSpring(dampingRatio = 0.35f, stiffness = 500f))
    }
    androidx.compose.material3.IconToggleButton(
        checked = checked,
        onCheckedChange = { v -> haptic?.let { Haptics.perform(it) }; onCheckedChange(v) },
        modifier = modifier.pressScale(press, 0.14f).hintOnLongPress(tip.state, NavSettings.hints, LocalHoldHaptics.current),
        enabled = enabled,
        interactionSource = press.source,
    ) {
        Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, contentAlignment = Alignment.Center) {
            HintContent(tip, hint, content)
        }
    }
}

@Composable
internal fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.shape,
    interactionSource: MutableInteractionSource? = null,
    haptic: HapticKind? = HapticKind.Click,
    content: @Composable () -> Unit,
) {
    val press = rememberPress(true, interactionSource, hold = haptic != null)
    androidx.compose.material3.FloatingActionButton(
        onClick = withHaptic(haptic, onClick),
        modifier = modifier,
        shape = shape.pressMorph(press, 12.dp),
        interactionSource = press.source,
        content = content,
    )
}

/** Сегментная кнопка (выбор из нескольких): сжимается при нажатии, как остальные кнопки. */
@Composable
internal fun SingleChoiceSegmentedButtonRowScope.SegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: HapticKind? = HapticKind.Tick,
    label: @Composable () -> Unit,
) {
    val press = rememberPress(enabled, hold = haptic != null)
    M3SegmentedButton(
        selected = selected,
        onClick = withHaptic(haptic, onClick),
        shape = shape,
        modifier = modifier.pressScale(press, 0.04f),
        enabled = enabled,
        interactionSource = press.source,
        label = label,
    )
}

@Composable
internal fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: HapticKind? = HapticKind.Tick,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val press = rememberPress(enabled, hold = haptic != null)
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = withHaptic(haptic, onClick),
        label = label,
        modifier = modifier.pressScale(press, 0.06f),
        enabled = enabled,
        leadingIcon = leadingIcon,
        interactionSource = press.source,
    )
}

@Composable
internal fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val press = rememberPress(enabled)
    androidx.compose.material3.DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.pressScale(press, 0.04f),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        interactionSource = press.source,
    )
}
