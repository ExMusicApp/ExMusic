package app.ymusic.m3e.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
internal fun SwipeBack(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var sign by remember { mutableFloatStateOf(1f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val curBack by rememberUpdatedState(onBack)
    val curEnabled by rememberUpdatedState(enabled)
    val density = LocalDensity.current
    val edgePx = with(density) { EDGE_DP.dp.toPx() }
    val epoch = remember { IntArray(1) }
    val touching = remember { BooleanArray(1) }

    suspend fun settleBack(): Unit = coroutineScope {
        val k = ThemeSettings.animSpeed
        val spec = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium * k * k)
        launch { progress.animateTo(0f, spec) }
        launch { exit.animateTo(0f, spec) }
        launch { dragY.animateTo(0f, spec) }
    }

    suspend fun animateClose(dir: Float): Int {
        epoch[0] = epoch[0] + 1
        val my = epoch[0]
        sign = dir
        val d = animMs(CLOSE_MS)
        coroutineScope {
            launch { progress.animateTo(1f, tween(d, easing = EmphasizedDecelerate)) }
            launch { exit.animateTo(1f, tween(d, easing = EmphasizedDecelerate)) }
        }
        curBack()
        return my
    }

    suspend fun verifyClosed(my: Int) {
        delay(600)
        if (epoch[0] == my && curEnabled) settleBack()
    }

    suspend fun finishClose(dir: Float) {
        val my = animateClose(dir)
        verifyClosed(my)
    }

    LaunchedEffect(enabled) {
        if (enabled && !touching[0] && (exit.value > 0f || progress.value > 0f)) {
            epoch[0] = epoch[0] + 1
            progress.snapTo(0f)
            exit.snapTo(0f)
            dragY.snapTo(0f)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(edgePx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (!curEnabled || down.position.x > edgePx) return@awaitEachGesture
                    val slop = viewConfiguration.touchSlop
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var dx = 0f
                    var dy = 0f
                    var dragging = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null) {
                            if (touching[0]) { touching[0] = false; scope.launch { settleBack() } }
                            break
                        }
                        if (!ch.pressed) {
                            touching[0] = false
                            if (dragging) {
                                val vx = tracker.calculateVelocity().x
                                scope.launch {
                                    if (progress.value > 0.33f || vx > 1000f) finishClose(1f) else settleBack()
                                }
                            }
                            break
                        }
                        val d = ch.position - ch.previousPosition
                        tracker.addPosition(ch.uptimeMillis, ch.position)
                        dx += d.x
                        dy += d.y
                        if (!dragging) {
                            if (abs(dx) > slop || abs(dy) > slop) {
                                if (dx > 0f && abs(dx) > abs(dy) * 1.3f) {
                                    dragging = true
                                    touching[0] = true
                                    epoch[0] = epoch[0] + 1
                                    sign = 1f
                                    scope.launch { progress.stop(); exit.stop(); dragY.stop() }
                                } else break
                            }
                        }
                        if (dragging) {
                            ch.consume()
                            val w = widthPx.coerceAtLeast(1f)
                            val p = (dx / w).coerceIn(0f, 1f)
                            val y = dy
                            scope.launch {
                                progress.snapTo(p)
                                dragY.snapTo(y)
                            }
                        }
                    }
                }
            }
            .drawBehind {
                val p = progress.value.coerceIn(0f, 1f)
                val a = SCRIM_ALPHA * (p * 3f).coerceAtMost(1f) * (1f - exit.value)
                if (a > 0.002f) drawRect(Color.Black.copy(alpha = a))
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value.coerceIn(0f, 1f)
                    val x = exit.value.coerceIn(0f, 1f)
                    val s = 1f - MAX_SHRINK * p - EXIT_EXTRA_SHRINK * x
                    scaleX = s
                    scaleY = s
                    val maxShift = (size.width * MAX_SHRINK / 2f - EDGE_MARGIN_DP.dp.toPx()).coerceAtLeast(0f)
                    translationX = sign * (maxShift * p + size.width * EXIT_SLIDE * x)
                    val maxY = size.height * 0.05f
                    translationY = (dragY.value * 0.35f).coerceIn(-maxY, maxY)
                    alpha = (1f - x).coerceIn(0f, 1f)
                    val corner = CORNER_DP.dp.toPx() * (p * 4f).coerceAtMost(1f)
                    if (corner > 0.5f) {
                        shape = RoundedCornerShape(corner)
                        clip = true
                    }
                    shadowElevation = SHADOW_DP.dp.toPx() * (p * 3f).coerceAtMost(1f)
                    horizontalMotionBlur(maxShift * progress.velocity + size.width * EXIT_SLIDE * exit.velocity, maxPx = 36f)
                },
        ) { content() }

        PredictiveBackHandler(enabled && LocalPageActive.current) { events ->
            var dir = 1f
            var startY = Float.NaN
            var committed = false
            try {
                epoch[0] = epoch[0] + 1
                progress.stop(); exit.stop(); dragY.stop()
                exit.snapTo(0f)
                events.collect { e ->
                    dir = if (e.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                    sign = dir
                    if (startY.isNaN()) startY = e.touchY
                    dragY.snapTo(e.touchY - startY)
                    progress.snapTo(e.progress.coerceIn(0f, 1f))
                }
                committed = true
                val my = withContext(NonCancellable) { animateClose(dir) }
                verifyClosed(my)
            } catch (c: CancellationException) {
                if (!committed) withContext(NonCancellable) { settleBack() }
                throw c
            }
        }
    }
}

internal fun pageEnter(): EnterTransition =
    slideInHorizontally(tween(animMs(PAGE_IN_MS), easing = EmphasizedDecelerate)) { (it * PAGE_SLIDE).toInt() } +
        fadeIn(tween(animMs(PAGE_FADE_IN_MS), easing = LinearOutSlowInEasing))

internal fun pageExit(): ExitTransition =
    slideOutHorizontally(tween(animMs(PAGE_OUT_MS), easing = EmphasizedAccelerate)) { (it * PAGE_SLIDE).toInt() } +
        fadeOut(tween(animMs(PAGE_FADE_OUT_MS), delayMillis = animMs(PAGE_OUT_MS - PAGE_FADE_OUT_MS)))

internal fun pageSwapEnter(): EnterTransition =
    fadeIn(tween(animMs(PAGE_SWAP_FADE_MS), easing = LinearOutSlowInEasing)) +
        scaleIn(tween(animMs(PAGE_SWAP_MS), easing = EmphasizedDecelerate), initialScale = PAGE_SWAP_SCALE)

internal fun pageSwapExit(): ExitTransition =
    scaleOut(tween(animMs(PAGE_SWAP_MS), easing = EmphasizedDecelerate), targetScale = 1f - (1f - PAGE_SWAP_SCALE) / 2f) +
        fadeOut(tween(animMs(PAGE_SWAP_FADE_MS), delayMillis = animMs(PAGE_SWAP_MS - PAGE_SWAP_FADE_MS)))

private const val PAGE_SWAP_MS = 320

private const val PAGE_SWAP_FADE_MS = 140

private const val PAGE_SWAP_SCALE = 0.92f

private const val EDGE_DP = 40

private const val MAX_SHRINK = 0.10f

private const val EXIT_EXTRA_SHRINK = 0.06f

private const val EDGE_MARGIN_DP = 8

private const val CORNER_DP = 28

private const val SHADOW_DP = 14

private const val SCRIM_ALPHA = 0.20f

private const val EXIT_SLIDE = 0.22f

private const val CLOSE_MS = 170

internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

internal val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private const val PAGE_IN_MS = 340

private const val PAGE_FADE_IN_MS = 110

private const val PAGE_OUT_MS = 240

private const val PAGE_FADE_OUT_MS = 140

private const val PAGE_SLIDE = 0.25f
