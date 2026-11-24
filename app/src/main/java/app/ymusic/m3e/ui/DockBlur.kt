package app.ymusic.m3e.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Высота нижней панели (мини-плеер + док). Экраны уходят ПОД панель, а чтобы последний элемент списка
 * не прятался за ней, прокручиваемые списки добавляют это значение к нижнему отступу ([dockInset]).
 */
internal val LocalDockInset = compositionLocalOf { 0.dp }

@Composable
internal fun dockInset(): Dp = LocalDockInset.current

/** Что рисуется под док-панелью. */
internal enum class DockBackdropMode {
    /** Ничего (резерв: под панелью виден экран как есть). */
    None,

    /** Сплошная подложка цвета панели (размытие выключено). */
    Solid,

    /** Размытый экран позади панели (Android 12+); на более старых — почти непрозрачная подложка. */
    Blur,
}

/**
 * Общее состояние размытия за доком. Содержимое экрана записывается в слой [content]; под доком этот же слой
 * рисуется ещё раз — через слой [blur] с эффектом размытия, — поэтому за панелью виден тот же экран, но размытый.
 */
@Stable
internal class DockBackdrop(val content: GraphicsLayer, val blur: GraphicsLayer) {
    /** Левый верхний угол экранной области (в координатах окна). */
    var contentOrigin by mutableStateOf(Offset.Zero)

    /** Левый верхний угол нижней панели (в координатах окна). */
    var barOrigin by mutableStateOf(Offset.Zero)

    /** Высота дока (без мини-плеера): именно эта полоса получает подложку. */
    var dockHeightPx by mutableIntStateOf(0)

    internal var appliedRadius = -1f
}

@Composable
internal fun rememberDockBackdrop(): DockBackdrop {
    val content = rememberGraphicsLayer()
    val blur = rememberGraphicsLayer()
    return remember(content, blur) { DockBackdrop(content, blur) }
}

/** Экранная область: запоминает своё положение и (если [record]) записывает рисунок в слой для размытия. */
internal fun Modifier.dockBackdropSource(b: DockBackdrop, record: Boolean): Modifier =
    this.onGloballyPositioned { b.contentOrigin = it.positionInRoot() }
        .then(
            if (!record) Modifier else Modifier.drawWithContent {
                val layer = b.content
                layer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                ) { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
        )

/**
 * Подложка под доком. Рисуется на самой нижней панели ПОЗАДИ мини-плеера и дока, поэтому ничто не обрезает
 * свечение мини-плеера: оно ложится поверх подложки.
 *
 * [softEdge] > 0 — верхняя кромка подложки не резкая, а плавно «растворяется» на этой высоте над доком
 * (прогрессивное размытие: под островком экран размыт всё сильнее к самому низу, без видимой линии).
 */
internal fun Modifier.dockBackdropStrip(
    b: DockBackdrop,
    mode: DockBackdropMode,
    solid: Color,
    tint: Color,
    blurRadius: Dp = 28.dp,
    softEdge: Dp = 0.dp,
): Modifier =
    this.onGloballyPositioned { b.barOrigin = it.positionInRoot() }
        .drawBehind {
            val h = b.dockHeightPx.toFloat()
            if (mode == DockBackdropMode.None || h <= 0f || size.height <= 0f) return@drawBehind
            val fade = softEdge.toPx().coerceAtLeast(0f)
            // С мягкой кромкой подложка начинается выше дока (может выйти за верх панели — рисунок не обрезается).
            val top = if (fade > 0f) size.height - h - fade else (size.height - h).coerceAtLeast(0f)
            val stripSize = Size(size.width, size.height - top)
            if (mode == DockBackdropMode.Solid) {
                drawRect(solid, Offset(0f, top), stripSize)
                return@drawBehind
            }
            if (Build.VERSION.SDK_INT >= 31) {
                val r = blurRadius.toPx()
                // Слой берём с запасом сверху: размытие на верхней кромке подмешивает то, что выше дока, а не «растягивает» край.
                val margin = r * 2f
                val layerTop = top - margin
                val lw = size.width.roundToInt().coerceAtLeast(1)
                val lh = (size.height - layerTop).roundToInt().coerceAtLeast(1)
                if (b.appliedRadius != r) {
                    b.blur.renderEffect = BlurEffect(r, r, TileMode.Clamp)
                    b.appliedRadius = r
                }
                b.blur.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = IntSize(lw, lh),
                ) {
                    translate(
                        b.contentOrigin.x - b.barOrigin.x,
                        b.contentOrigin.y - b.barOrigin.y - layerTop,
                    ) { drawLayer(b.content) }
                }
                if (fade > 0f) {
                    // Размытие + оттенок рисуем во временный слой и «стираем» верхнюю кромку градиентом (DstIn).
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(0f, top, size.width, size.height), Paint())
                        clipRect(0f, top, size.width, size.height) {
                            translate(0f, layerTop) { drawLayer(b.blur) }
                        }
                        drawRect(tint, Offset(0f, top), stripSize)
                        drawRect(Color.White.copy(alpha = 0.06f), Offset(0f, top), stripSize) // матовая вуаль
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent, 1f to Color.Black,
                                startY = top, endY = top + fade,
                            ),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, fade),
                            blendMode = BlendMode.DstIn,
                        )
                        canvas.restore()
                    }
                } else {
                    clipRect(0f, top, size.width, size.height) {
                        translate(0f, layerTop) { drawLayer(b.blur) }
                    }
                    drawRect(tint, Offset(0f, top), stripSize)
                    drawRect(Color.White.copy(alpha = 0.06f), Offset(0f, top), stripSize) // матовая вуаль
                }
            } else {
                // Android 8–11: размытия слоя нет, поэтому почти сплошная подложка (с мягкой кромкой, если она нужна).
                val base = solid.copy(alpha = 0.94f)
                if (fade > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.Transparent, base), startY = top, endY = top + fade,
                        ),
                        topLeft = Offset(0f, top), size = Size(size.width, fade),
                    )
                    drawRect(base, Offset(0f, top + fade), Size(size.width, size.height - top - fade))
                } else {
                    drawRect(base, Offset(0f, top), stripSize)
                }
            }
        }
