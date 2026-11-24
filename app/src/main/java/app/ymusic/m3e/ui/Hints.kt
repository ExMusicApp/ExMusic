package app.ymusic.m3e.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

// ---------- Подсказки при долгом нажатии ----------
// Зажали маленькую кнопку (значок без подписи) — над ней появляется её название; действие при этом не срабатывает.
// Название берётся из contentDescription вложенного значка: обёртки кнопок в ExpressiveButtons.kt подставляют
// LocalHintLabel, а Icon ниже (он перекрывает material3.Icon во всём пакете, как и обёртки кнопок) туда пишет.
// Включается и выключается в Настройки → Внешний вид → Навигация → «Подсказки при долгом нажатии».

/** Куда кнопка получает своё название: сюда пишет вложенный [Icon]. */
@Stable
internal class HintLabel {
    var text by mutableStateOf<String?>(null)
}

internal val LocalHintLabel = compositionLocalOf<HintLabel?> { null }

/**
 * Вибро при удержании кнопок. В плеере (мини-плеер и полный) выключено: там маленькие кнопки не «тикают»
 * при удержании и не вибрируют при показе подсказки. Остальные экраны — как раньше.
 */
internal val LocalHoldHaptics = compositionLocalOf { true }

/** Подсказка, которая сейчас на экране: новая гасит прежнюю, чтобы надписи не накладывались друг на друга. */
private var activeHint: HintState? = null

private fun activateHint(state: HintState) {
    val prev = activeHint
    if (prev != null && prev !== state) {
        prev.visible = false
        prev.held = false
    }
    activeHint = state
}

private fun releaseHint(state: HintState) {
    if (activeHint === state) activeHint = null
}

/** Показана ли сейчас подсказка и держит ли ещё палец (пока держит — подсказка не гаснет). */
@Stable
internal class HintState {
    var visible by mutableStateOf(false)
    var held by mutableStateOf(false)
}

/** Всё, что нужно кнопке для подсказки. */
internal class ButtonHint(val state: HintState, val label: HintLabel)

@Composable
internal fun rememberButtonHint(): ButtonHint = remember { ButtonHint(HintState(), HintLabel()) }

/**
 * Значок: то же, что material3.Icon, но сообщает [contentDescription] объемлющей кнопке — тот самый текст подсказки.
 * Остальные перегрузки Icon (ImageVector, ImageBitmap) по-прежнему берутся из material3.
 */
@Composable
internal fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val sink = LocalHintLabel.current
    if (sink != null && contentDescription != null) {
        SideEffect { if (sink.text != contentDescription) sink.text = contentDescription }
    }
    androidx.compose.material3.Icon(painter, contentDescription, modifier, tint)
}

/**
 * Долгое нажатие показывает подсказку и «съедает» отпускание, чтобы кнопка не нажалась.
 * Касания обрабатываются на проходе Initial (раньше самой кнопки) и до таймаута ничего не потребляют,
 * поэтому обычный тап, рябь, вибро и прокрутка списка работают как раньше.
 */
internal fun Modifier.hintOnLongPress(state: HintState, enabled: Boolean, haptic: Boolean = true): Modifier =
    pointerInput(state, enabled, haptic) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var timedOut = false
            try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation(PointerEventPass.Initial)
                }
            } catch (_: PointerEventTimeoutCancellationException) {
                timedOut = true
            }
            if (!timedOut) return@awaitEachGesture // обычный тап, прокрутка или отмена — не наше дело
            activateHint(state) // прежняя подсказка (с другой кнопки) исчезает сразу
            state.visible = true
            state.held = true
            if (haptic) Haptics.click()
            // Ждём, пока палец не поднимут, и гасим все события, чтобы кнопка не сработала.
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            } finally {
                state.held = false
            }
        }
    }

/** Само окошко подсказки: над кнопкой (или под ней, если сверху нет места). Рисуйте внутри содержимого кнопки. */
@Composable
internal fun HintPopup(state: HintState, text: String?) {
    LaunchedEffect(state.visible, state.held) {
        if (state.visible && !state.held) {
            delay(1300)
            state.visible = false
        }
    }
    DisposableEffect(state) { onDispose { releaseHint(state) } }
    if (!state.visible || text.isNullOrBlank()) return
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val provider = remember(gap, margin) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, maxX)
                var y = anchorBounds.top - popupContentSize.height - gap
                if (y < margin) y = anchorBounds.bottom + gap
                return IntOffset(x, y)
            }
        }
    }
    Popup(
        popupPositionProvider = provider,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = false, clippingEnabled = false),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
            )
        }
    }
}

/**
 * Содержимое кнопки-значка: сам значок плюс окошко подсказки. [text] — явная подпись; если её нет, берётся
 * contentDescription вложенного [Icon].
 */
@Composable
internal fun HintContent(hint: ButtonHint, text: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHintLabel provides hint.label) { content() }
    HintPopup(hint.state, text ?: hint.label.text)
}
