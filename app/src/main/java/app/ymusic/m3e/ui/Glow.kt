package app.ymusic.m3e.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp

@Composable
fun Modifier.playGlow(
    active: Boolean,
    enabled: Boolean,
    color: Color,
    cornerRadius: Dp,
    spread: Dp,
    strength: Float = 0.55f,
    shape: Shape? = null,
): Modifier {
    val show = active && enabled && !ThemeSettings.optimize
    val fade by animateFloatAsState(
        targetValue = if (show) 1f else 0f,
        animationSpec = tween(if (show) 700 else 450),
        label = "glowFade",
    )
    val pulse: State<Float>? = if (show || fade > 0.01f) {
        val tr = rememberInfiniteTransition(label = "glowPulse")
        val a = tr.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glowA",
        )
        val b = tr.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3100, easing = LinearEasing), RepeatMode.Reverse),
            label = "glowB",
        )
        remember(a, b) { derivedStateOf { a.value * 0.6f + b.value * 0.4f } }
    } else null
    val density = androidx.compose.ui.platform.LocalDensity.current
    val blurPx = with(density) { spread.toPx() } * 0.6f
    val paint = remember(blurPx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = android.graphics.BlurMaskFilter(blurPx.coerceAtLeast(1f), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }
    val cache = remember { GlowPathCache() }
    val fallbackPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    return this.drawBehind {
        if (fade <= 0.01f) return@drawBehind
        val p = pulse?.value ?: 0.5f
        if (shape != null && size.width > 1f && size.height > 1f) {
            drawShapedGlow(shape, cache, paint, fallbackPaint, color, spread.toPx(), strength, fade, p)
            return@drawBehind
        }
        val basePx = cornerRadius.toPx()
        val grow = spread.toPx() * (0.15f + 0.2f * p)
        val alpha = (strength * fade * (0.75f + 0.45f * p)).coerceIn(0f, 1f)
        if (android.os.Build.VERSION.SDK_INT < 28) {
            val spreadPx = spread.toPx()
            val steps = 36
            for (i in steps downTo 1) {
                val f = i / steps.toFloat()
                val g = spreadPx * f
                val a = (alpha * 0.22f * kotlin.math.exp(-3.2f * f * f)).coerceIn(0f, 1f)
                drawRoundRect(
                    color = color.copy(alpha = a),
                    topLeft = androidx.compose.ui.geometry.Offset(-g, -g),
                    size = androidx.compose.ui.geometry.Size(size.width + 2f * g, size.height + 2f * g),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(basePx + g),
                )
            }
            return@drawBehind
        }
        paint.color = color.copy(alpha = alpha).toArgb()
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                -grow, -grow, size.width + grow, size.height + grow,
                basePx + grow, basePx + grow, paint,
            )
        }
    }
}

private class GlowPathCache {
    var size: Size = Size.Zero
    var shape: Shape? = null
    var base: android.graphics.Path? = null
    var uniformRadius: Float = -1f
    val tmp = android.graphics.Path()
    val matrix = android.graphics.Matrix()
}

private fun DrawScope.drawShapedGlow(
    shape: Shape,
    cache: GlowPathCache,
    blurPaint: android.graphics.Paint,
    fallbackPaint: android.graphics.Paint,
    color: Color,
    spreadPx: Float,
    strength: Float,
    fade: Float,
    p: Float,
) {
    var base = cache.base
    if (base == null || cache.size != size || cache.shape != shape) {
        cache.uniformRadius = -1f
        base = when (val o = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Generic -> o.path.asAndroidPath()
            is Outline.Rounded -> {
                val rr = o.roundRect
                val r = rr.topLeftCornerRadius
                if (r.x == r.y && rr.topRightCornerRadius == r && rr.bottomRightCornerRadius == r && rr.bottomLeftCornerRadius == r) {
                    cache.uniformRadius = r.x
                }
                androidx.compose.ui.graphics.Path().apply { addRoundRect(rr) }.asAndroidPath()
            }
            is Outline.Rectangle -> androidx.compose.ui.graphics.Path().apply { addRect(o.rect) }.asAndroidPath()
        }
        cache.base = base; cache.size = size; cache.shape = shape
    }
    val basePath: android.graphics.Path = base!!
    val w = size.width
    val h = size.height
    val alpha = (strength * fade * (0.75f + 0.45f * p)).coerceIn(0f, 1f)
    fun draw(canvas: android.graphics.Canvas, g: Float, paint: android.graphics.Paint) {
        cache.matrix.setScale((w + 2f * g) / w, (h + 2f * g) / h, w / 2f, h / 2f)
        cache.tmp.set(basePath)
        cache.tmp.transform(cache.matrix)
        canvas.drawPath(cache.tmp, paint)
    }
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        if (android.os.Build.VERSION.SDK_INT < 28) {
            val steps = 36
            for (i in steps downTo 1) {
                val f = i / steps.toFloat()
                val a = (alpha * 0.22f * kotlin.math.exp(-3.2f * f * f)).coerceIn(0f, 1f)
                fallbackPaint.color = color.copy(alpha = a).toArgb()
                draw(nc, spreadPx * f, fallbackPaint)
            }
        } else {
            blurPaint.color = color.copy(alpha = alpha).toArgb()
            val g = spreadPx * (0.15f + 0.2f * p)
            val u = cache.uniformRadius
            if (u >= 0f) {
                nc.drawRoundRect(-g, -g, w + g, h + g, u, u, blurPaint)
            } else {
                draw(nc, g, blurPaint)
            }
        }
    }
}
