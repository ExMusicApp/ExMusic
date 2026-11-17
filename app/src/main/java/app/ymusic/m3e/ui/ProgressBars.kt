package app.ymusic.m3e.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2 * PI).toFloat()

@Composable
private fun wavePhase(wavy: Boolean): State<Float> {
    if (!wavy || ThemeSettings.optimize) return remember { mutableFloatStateOf(0f) }
    val t = rememberInfiniteTransition(label = "wavePhase")
    return t.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
}

private fun waveAmplitudeDp(thicknessDp: Float) = 2f + thicknessDp * 0.25f

fun circularRingSize(button: Dp, thickness: Dp, wavy: Boolean, gap: Dp = 4.dp, waveAmplitude: Dp? = null): Dp {
    val amp = if (wavy) (waveAmplitude?.value ?: waveAmplitudeDp(thickness.value)) else 0f
    return ((button.value / 2f + gap.value + thickness.value + 2f * amp) * 2f).dp
}

private fun linearHeight(wavy: Boolean, thickness: Dp, handle: Boolean): Dp {
    val amp = if (wavy) waveAmplitudeDp(thickness.value) else 0f
    val handleH = if (handle) max(thickness.value + 12f, 24f) else 0f
    return max(thickness.value + amp * 2f, handleH).dp
}

@Composable
fun M3LinearBar(
    progress: Float,
    modifier: Modifier = Modifier,
    wavy: Boolean = false,
    thickness: Dp = 4.dp,
    amplitude: Float = 1f,
    handle: Boolean = false,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    val phase = wavePhase(wavy && amplitude > 0.01f)
    Canvas(modifier.fillMaxWidth().height(linearHeight(wavy, thickness, handle))) {
        val w = size.width
        val cy = size.height / 2f
        val th = thickness.toPx()
        val trackTh = if (wavy) min(th, 4.dp.toPx()) else th
        val gap = 4.dp.toPx()
        val handleW = 4.dp.toPx()
        val p = progress.coerceIn(0f, 1f)
        val minX = th / 2f
        val maxX = w - trackTh / 2f
        val end = minX + p * (w - th).coerceAtLeast(0f)
        val half = if (handle) handleW / 2f + gap else gap / 2f
        val activeEnd = end - half
        val trackStart = end + half

        if (trackStart < maxX) {
            drawLine(trackColor, Offset(trackStart, cy), Offset(maxX, cy), strokeWidth = trackTh, cap = StrokeCap.Round)
            if (maxX - trackStart > 10.dp.toPx()) drawCircle(color, radius = 2.dp.toPx().coerceAtMost(trackTh / 2f), center = Offset(maxX, cy))
        }

        if (activeEnd > minX + 0.5f) {
            if (wavy) {
                val a = waveAmplitudeDp(thickness.value).dp.toPx() * amplitude.coerceIn(0f, 1f)
                val wl = (40f + (thickness.value - 4f) * 2f).dp.toPx()
                val k = TWO_PI / wl
                val path = Path()
                var x = minX
                path.moveTo(x, cy + a * sin(k * x - phase.value))
                while (x < activeEnd) {
                    x = min(x + 3f, activeEnd)
                    path.lineTo(x, cy + a * sin(k * x - phase.value))
                }
                drawPath(path, color, style = Stroke(width = th, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawLine(color, Offset(minX, cy), Offset(activeEnd, cy), strokeWidth = th, cap = StrokeCap.Round)
            }
        }

        if (handle) {
            val hh = max(th + 12.dp.toPx(), 24.dp.toPx())
            drawRoundRect(
                color,
                topLeft = Offset(end - handleW / 2f, cy - hh / 2f),
                size = Size(handleW, hh),
                cornerRadius = CornerRadius(handleW / 2f),
            )
        }
    }
}

private const val HAPTIC_STEPS = 40

internal fun swipeTickStep(distancePx: Float, widthPx: Float): Int =
    (kotlin.math.abs(distancePx) / widthPx.coerceAtLeast(1f) * HAPTIC_STEPS).toInt()

@Composable
fun M3SeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    wavy: Boolean = false,
    thickness: Dp = 4.dp,
    amplitude: Float = 1f,
    minTouchHeight: Dp = 36.dp,
    haptic: Boolean = false,
) {
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val hapticOn by rememberUpdatedState(haptic)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minTouchHeight)
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    if (hapticOn) Haptics.click()
                    change((off.x / size.width).coerceIn(0f, 1f))
                    finish()
                }
            }
            .pointerInput(Unit) {
                var lastStep = -1
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        val v = (off.x / size.width).coerceIn(0f, 1f)
                        lastStep = (v * HAPTIC_STEPS).toInt()
                        if (hapticOn) Haptics.click()
                        change(v)
                    },
                    onDragEnd = { if (hapticOn) Haptics.click(); finish() },
                    onDragCancel = { finish() },
                ) { c, _ ->
                    c.consume()
                    val v = (c.position.x / size.width).coerceIn(0f, 1f)
                    val step = (v * HAPTIC_STEPS).toInt()
                    if (step != lastStep) {
                        lastStep = step
                        if (hapticOn) Haptics.tick()
                    }
                    change(v)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        M3LinearBar(value, wavy = wavy, thickness = thickness, amplitude = amplitude, handle = true)
    }
}

@Composable
fun M3CircularBar(
    progress: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    wavy: Boolean = false,
    thickness: Dp = 6.dp,
    amplitude: Float = 1f,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    waveAmplitude: Dp? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val phase = wavePhase(wavy && amplitude > 0.01f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val th = thickness.toPx()
            val trackTh = if (wavy) min(th, 4.dp.toPx()) else th
            val baseAmp = waveAmplitude ?: waveAmplitudeDp(thickness.value).dp
            val ampPx = if (wavy) baseAmp.toPx() * amplitude.coerceIn(0f, 1f) else 0f
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val r = (min(this.size.width, this.size.height) - max(th, trackTh) - 2f * baseAmp.toPx().let { if (wavy) it else 0f }) / 2f
            val p = progress.coerceIn(0f, 1f)
            val arcTopLeft = Offset(cx - r, cy - r)
            val arcSize = Size(2f * r, 2f * r)
            val gapDeg = Math.toDegrees(((4.dp.toPx() + max(th, trackTh)) / r).toDouble()).toFloat()

            fun track(startDeg: Float, sweepDeg: Float) {
                if (sweepDeg <= 0.5f) return
                drawArc(trackColor, startDeg, sweepDeg, false, arcTopLeft, arcSize, style = Stroke(trackTh, cap = StrokeCap.Round))
            }

            if (p <= 0.002f) {
                track(-90f, 360f)
                return@Canvas
            }
            val activeStart = -90f
            val full = p >= 0.998f
            val activeSweep = if (full) 360f else (360f * p - gapDeg / 2f).coerceAtLeast(0f)

            if (!full) track(activeStart + 360f * p + gapDeg / 2f, 360f * (1f - p) - gapDeg / 2f - gapDeg / 2f)

            if (activeSweep > 0.5f) {
                if (wavy) {
                    val waves = max(6, (2f * PI.toFloat() * r / 24.dp.toPx()).roundToInt())
                    val path = Path()
                    var deg = activeStart
                    val endDeg = activeStart + activeSweep
                    var first = true
                    while (true) {
                        val rad = Math.toRadians(deg.toDouble()).toFloat()
                        val rr = r + ampPx * sin(waves * (rad + PI.toFloat() / 2f) - phase.value)
                        val x = cx + rr * kotlin.math.cos(rad)
                        val y = cy + rr * kotlin.math.sin(rad)
                        if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                        if (deg >= endDeg) break
                        deg = min(deg + 2f, endDeg)
                    }
                    drawPath(path, color, style = Stroke(th, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else {
                    drawArc(color, activeStart, activeSweep, false, arcTopLeft, arcSize, style = Stroke(th, cap = StrokeCap.Round))
                }
            }
        }
        content()
    }
}

@Composable
fun M3CircularSeek(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    wavy: Boolean = false,
    thickness: Dp = 6.dp,
    amplitude: Float = 1f,
    ringBand: Dp = 22.dp,
    waveAmplitude: Dp? = null,
    haptic: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val hapticOn by rememberUpdatedState(haptic)
    fun frac(o: Offset, w: Int, h: Int): Float {
        val ang = atan2(o.y - h / 2f, o.x - w / 2f)
        return (((ang + PI.toFloat() / 2f) / TWO_PI) + 1f) % 1f
    }
    fun onRing(o: Offset, w: Int, h: Int, ringBand: Float): Boolean {
        val dx = o.x - w / 2f
        val dy = o.y - h / 2f
        return sqrt(dx * dx + dy * dy) >= min(w, h) / 2f - ringBand
    }
    M3CircularBar(
        progress = value,
        size = size,
        wavy = wavy,
        thickness = thickness,
        amplitude = amplitude,
        waveAmplitude = waveAmplitude,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (onRing(o, this.size.width, this.size.height, ringBand.toPx())) {
                        if (hapticOn) Haptics.click()
                        change(frac(o, this.size.width, this.size.height))
                        finish()
                    }
                }
            }
            .pointerInput(Unit) {
                var active = false
                var lastStep = -1
                detectDragGestures(
                    onDragStart = { o ->
                        active = onRing(o, this.size.width, this.size.height, ringBand.toPx())
                        if (active) {
                            val v = frac(o, this.size.width, this.size.height)
                            lastStep = (v * HAPTIC_STEPS).toInt()
                            if (hapticOn) Haptics.click()
                            change(v)
                        }
                    },
                    onDragEnd = { if (active) { if (hapticOn) Haptics.click(); finish() }; active = false },
                    onDragCancel = { if (active) finish(); active = false },
                ) { c, _ ->
                    if (active) {
                        c.consume()
                        val v = frac(c.position, this.size.width, this.size.height)
                        val step = (v * HAPTIC_STEPS).toInt()
                        if (step != lastStep) {
                            lastStep = step
                            if (hapticOn) Haptics.tick()
                        }
                        change(v)
                    }
                }
            },
        content = content,
    )
}

@Composable
fun SeekStylePreview(style: SeekBarStyle, modifier: Modifier = Modifier) {
    if (style.circular) {
        M3CircularBar(progress = 0.4f, size = 44.dp, modifier = modifier, wavy = style.wavy, thickness = 5.dp)
    } else {
        M3LinearBar(progress = 0.4f, modifier = modifier, wavy = style.wavy, thickness = style.thickness.dp)
    }
}
