@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Длительности для быстрого выбора (секунды). */
private val MixPresets = listOf(2, 4, 6, 8, 12)

/** Короткая подпись состояния микса для строки в настройках. */
internal fun mixSummary(): String =
    if (PlayerSettings.crossfade) "Включён · ${PlayerSettings.crossfadeSec} с" else "Выключен"

/**
 * Окно «Микс треков» в стиле Material 3 Expressive: крупная «печенька» с пружинным вращением, живая схема перехода
 * (две кривые равной мощности, ширина наложения меняется вместе с длительностью), волнистый ползунок и
 * соединённая группа кнопок с быстрым выбором. Любое изменение длительности само включает микс.
 */
@Composable
internal fun MixSheet(onDismiss: () -> Unit) {
    val on = PlayerSettings.crossfade
    val sec = PlayerSettings.crossfadeSec

    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MixHeader(on)
            Spacer(Modifier.height(20.dp))
            MixDiagram(on, sec)
            Spacer(Modifier.height(20.dp))

            // Крупное значение: цифра «переезжает» вверх/вниз при смене.
            AnimatedContent(
                targetState = sec,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(fxSpring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { if (up) it else -it } + fadeIn())
                        .togetherWith(slideOutVertically(fxSpring(stiffness = Spring.StiffnessMedium)) { if (up) -it else it } + fadeOut())
                },
                label = "mixSec",
            ) { s ->
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$s", style = MaterialTheme.typography.displayMediumEmphasized,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        " с", style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Text("длительность перехода", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            M3SeekBar(
                value = (sec - 1) / 11f,
                onValueChange = { v ->
                    val s = (v * 11f).roundToInt() + 1
                    if (s != PlayerSettings.crossfadeSec) { Haptics.tick(); setMix(s) }
                },
                onValueChangeFinished = {},
                wavy = on, thickness = 8.dp, amplitude = if (on) 1f else 0f, minTouchHeight = 44.dp,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1 с", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("12 с", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))

            MixPresetGroup(selected = if (on) sec else -1) { Haptics.click(); setMix(it) }
            Spacer(Modifier.height(16.dp))

            Surface(shape = containerShape(20), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(painterResource(R.drawable.ms_info), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Работает во всех сервисах. Микс пропускается при повторе одного трека, на последнем треке очереди и на коротких треках.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Готово") }
        }
    }
}

private fun setMix(sec: Int) {
    PlayerSettings.updateCrossfadeSec(sec)
    if (!PlayerSettings.crossfade) PlayerSettings.updateCrossfade(true)
}

/** Шапка: «печенька» с иконкой (медленно вращается, пока микс включён), заголовок и переключатель. */
@Composable
private fun MixHeader(on: Boolean) {
    val spin = rememberInfiniteTransition(label = "mixSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val bg by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        fxSpring(stiffness = Spring.StiffnessMediumLow), label = "mixBg",
    )
    val scale by animateFloatAsState(if (on) 1f else 0.86f, fxSpring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "mixScale")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(), color = bg,
                modifier = Modifier.fillMaxSize().rotate(if (on && !ThemeSettings.optimize) angle else 0f),
            ) {}
            Icon(
                painterResource(R.drawable.ms_graphic_eq), null,
                Modifier.size(32.dp).rotate(0f),
                tint = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text("Микс треков", style = MaterialTheme.typography.headlineSmallEmphasized)
            Text(
                if (on) "Треки перетекают друг в друга" else "Выключен — треки идут один за другим",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExpressiveSwitch(checked = on, onCheckedChange = { PlayerSettings.updateCrossfade(it) })
    }
}

/**
 * Схема перехода: уходящий трек (основной цвет) и приходящий (третичный). Включён — две плавные кривые, наложение
 * тем шире, чем длиннее микс, по схеме бежит «головка»; выключен — жёсткая склейка. Ширина наложения меняется пружиной.
 */
@Composable
internal fun MixDiagram(on: Boolean, sec: Int, height: androidx.compose.ui.unit.Dp = 148.dp) {
    val overlapTarget = if (on) (0.12f + 0.46f * (sec - 1) / 11f) else 0f
    val overlap by animateFloatAsState(overlapTarget, fxSpring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "overlap")
    val infinite = rememberInfiniteTransition(label = "mixHead")
    val head by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "head")
    val outgoing = MaterialTheme.colorScheme.primary
    val incoming = MaterialTheme.colorScheme.tertiary
    val line = MaterialTheme.colorScheme.outlineVariant
    val onSurf = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(shape = containerShape(28), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().height(height)) {
        Box {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = if (height < 120.dp) 16.dp else 22.dp)) {
                val w = size.width
                val h = size.height
                val mid = w / 2f
                val half = (overlap * w / 2f).coerceAtLeast(0.5f)
                val x0 = mid - half
                val x1 = mid + half
                // Базовая линия.
                drawLine(line, Offset(0f, h), Offset(w, h), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)

                fun curve(out: Boolean): Path = Path().apply {
                    moveTo(0f, if (out) 0f else h)
                    val n = 48
                    if (out) {
                        lineTo(x0, 0f)
                        for (i in 0..n) { val p = i / n.toFloat(); lineTo(x0 + (x1 - x0) * p, h * (1f - cos(p * PI.toFloat() / 2))) }
                        lineTo(x1, h)
                        lineTo(w, h)
                    } else {
                        lineTo(x0, h)
                        for (i in 0..n) { val p = i / n.toFloat(); lineTo(x0 + (x1 - x0) * p, h * (1f - sin(p * PI.toFloat() / 2))) }
                        lineTo(x1, 0f)
                        lineTo(w, 0f)
                    }
                }
                // Заливка под кривыми и сами линии.
                for (out in listOf(true, false)) {
                    val c = if (out) outgoing else incoming
                    val path = curve(out)
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(if (out) w else w, h); lineTo(if (out) 0f else 0f, h); close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(c.copy(alpha = 0.30f), c.copy(alpha = 0.02f))))
                    drawPath(path, c, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // «Головка» бежит по схеме.
                val hx = head * w
                drawLine(onSurf.copy(alpha = 0.35f), Offset(hx, 0f), Offset(hx, h), strokeWidth = 1.5.dp.toPx())
                val p = ((hx - x0) / (x1 - x0).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val yOut = if (hx < x0) 0f else if (hx > x1) h else h * (1f - cos(p * PI.toFloat() / 2))
                val yIn = if (hx < x0) h else if (hx > x1) 0f else h * (1f - sin(p * PI.toFloat() / 2))
                drawCircle(outgoing, 6.dp.toPx(), Offset(hx, yOut))
                drawCircle(incoming, 6.dp.toPx(), Offset(hx, yIn))
            }
            Text("Трек 1", Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 8.dp), style = MaterialTheme.typography.labelSmall, color = outgoing, fontWeight = FontWeight.SemiBold)
            Text("Трек 2", Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = incoming, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Соединённая группа кнопок: у выбранной форма полностью скруглена, у остальных — маленький радиус между соседями;
 * при выборе углы пружинят.
 */
@Composable
internal fun MixPresetGroup(selected: Int, minHeight: androidx.compose.ui.unit.Dp = 56.dp, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        MixPresets.forEachIndexed { i, s ->
            val sel = s == selected
            val corner by animateFloatAsState(if (sel) 28f else 8f, fxSpring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "presetCorner")
            val outerL = if (i == 0) 28f else corner
            val outerR = if (i == MixPresets.lastIndex) 28f else corner
            val shape = RoundedCornerShape(topStart = outerL.dp, bottomStart = outerL.dp, topEnd = outerR.dp, bottomEnd = outerR.dp)
            val container by animateColorAsState(
                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                fxSpring(stiffness = Spring.StiffnessMediumLow), label = "presetBg",
            )
            Surface(
                onClick = { onPick(s) }, shape = shape, color = container,
                modifier = Modifier.weight(if (sel) 1.25f else 1f).heightIn(min = minHeight),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$s с", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
