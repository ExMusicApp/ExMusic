package app.ymusic.m3e.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Галочка с анимацией «кружок → галочка»: на миг появляется кружок и стягивается в галочку, которая прорисовывается
 * штрихом; снятие отмечает всё то же самое в обратную сторону. Отключается в Настройки → Внешний вид → Анимации.
 * [appear] — отыграть появление, когда элемент только вошёл в композицию (например, «уже в плейлисте»).
 */
@Composable
internal fun AnimatedCheck(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    appear: Boolean = false,
) {
    var started by remember { mutableStateOf(!appear) }
    LaunchedEffect(Unit) { started = true }
    val on = ThemeSettings.animCheck && !ThemeSettings.optimize
    val p by animateFloatAsState(
        targetValue = if (checked && started) 1f else 0f,
        animationSpec = if (on) tween(animMs(360), easing = FastOutSlowInEasing) else snap(),
        label = "checkMorph",
    )
    Canvas(modifier.size(size)) {
        if (p <= 0f) return@Canvas
        val w = this.size.minDimension
        val stroke = w * 0.1f
        // Кружок: вспыхивает в начале и стягивается в центр, пока рисуется галочка (без анимации — не рисуется).
        val ring = if (on) (4f * p * (1f - p)).coerceIn(0f, 1f) else 0f
        if (ring > 0f) {
            drawCircle(
                tint.copy(alpha = tint.alpha * ring), radius = w * (0.46f - 0.2f * p),
                center = center, style = Stroke(stroke * 0.8f),
            )
        }
        val path = Path().apply {
            moveTo(w * 0.22f, w * 0.52f)
            lineTo(w * 0.42f, w * 0.71f)
            lineTo(w * 0.78f, w * 0.31f)
        }
        val seg = Path()
        val pm = PathMeasure().apply { setPath(path, false) }
        val frac = if (on) ((p - 0.25f) / 0.75f).coerceIn(0f, 1f) else 1f
        pm.getSegment(0f, pm.length * frac, seg, true)
        drawPath(seg, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Значок кнопки «играть/пауза» с состоянием загрузки: при загрузке трека значок сжимается в кружок, кружок
 * распадается на три точки, которые пульсируют; когда трек загрузился, точки собираются обратно в кружок, и
 * проявляется значок. Отключается в Настройки → Внешний вид → Анимации.
 */
@Composable
internal fun LoadingDotsMorph(
    loading: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = LocalContentColor.current,
    content: @Composable () -> Unit,
) {
    val on = ThemeSettings.animLoading && !ThemeSettings.optimize
    val q by animateFloatAsState(
        targetValue = if (loading && on) 1f else 0f,
        animationSpec = tween(animMs(420), easing = FastOutSlowInEasing),
        label = "dotsMorph",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        // Значок гаснет и сжимается к центру в первой трети перехода и возвращается в последней.
        val iconA = (1f - q * 3f).coerceIn(0f, 1f)
        if (iconA > 0f) {
            Box(Modifier.graphicsLayer { alpha = iconA; scaleX = 0.6f + 0.4f * iconA; scaleY = 0.6f + 0.4f * iconA }) { content() }
        }
        if (q > 0f) {
            val pulse = rememberInfiniteTransition(label = "dotsPulse")
            val t by pulse.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                label = "dotsT",
            )
            Canvas(Modifier.size(size)) {
                val w = this.size.minDimension
                val c = center
                // Фаза 1 (q 0…0.4): кружок вырастает. Фаза 2 (0.4…1): кружок стягивается в среднюю точку,
                // две боковые точки выезжают из центра. Обратный ход — то же задом наперёд.
                val grow = (q / 0.4f).coerceIn(0f, 1f)
                val split = ((q - 0.4f) / 0.6f).coerceIn(0f, 1f)
                val r0 = w * 0.2f
                val rDot = w * 0.085f
                val gap = w * 0.24f * split
                val settled = split // пульсация набирает силу, когда точки разошлись
                fun pulseOf(i: Int): Float {
                    val ph = ((t - i / 3f) % 1f + 1f) % 1f
                    val bump = if (ph < 0.5f) kotlin.math.sin(ph * 2f * Math.PI.toFloat()) else 0f
                    return 1f + 0.35f * bump * settled
                }
                val rMid = (r0 + (rDot - r0) * split) * grow
                if (rMid > 0f) drawCircle(tint, rMid * pulseOf(1), c)
                if (split > 0f) {
                    drawCircle(tint, rDot * pulseOf(0), Offset(c.x - gap, c.y))
                    drawCircle(tint, rDot * pulseOf(2), Offset(c.x + gap, c.y))
                }
            }
        }
    }
}
