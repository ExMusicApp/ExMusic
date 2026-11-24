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

/**
 * Возврат назад в стиле Material 3 Expressive / Pixel («predictive back»).
 *
 * Пока палец тянет страницу от края:
 *  • страница сжимается (до [MAX_SHRINK]), у неё появляются скруглённые углы и мягкая тень;
 *  • она смещается в сторону свайпа (в пределах экрана, с отступом [EDGE_MARGIN_DP]) и чуть следует за пальцем по вертикали;
 *  • предыдущий экран под ней слегка затемнён.
 * Отпустили за порогом (или быстрым броском) — страница быстро дожимается, уезжает и растворяется (≈170 мс, настраивается
 * в «Внешний вид → Анимации»), затем вызывается [onBack].
 * Иначе — возвращается на место на пружине (M3E: упругий «пространственный» отскок).
 *
 * Работает двумя путями, которые выглядят одинаково:
 *  1. своё касание от левого края ([EDGE_DP]) — кнопочная навигация и жест, начатый чуть правее системной зоны;
 *  2. системный жест «назад» ([PredictiveBackHandler]); чтобы система вообще присылала прогресс жеста, в манифесте
 *     включён `android:enableOnBackInvokedCallback="true"`.
 *
 * [onBack] — то же действие, что и у кнопки «назад» страницы. Оборачивать нужно саму страницу: чем глубже
 * вложена страница, тем позже она регистрирует обработчик и тем раньше он срабатывает.
 */
@Composable
internal fun SwipeBack(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 0..1 — насколько «оттянули» страницу жестом; exit 0..1 — фаза ухода после подтверждения; dragY — сдвиг пальца по вертикали.
    val progress = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val dragY = remember { Animatable(0f) }
    var sign by remember { mutableFloatStateOf(1f) }
    var widthPx by remember { mutableFloatStateOf(0f) }
    val curBack by rememberUpdatedState(onBack)
    val curEnabled by rememberUpdatedState(enabled)
    val density = LocalDensity.current
    val edgePx = with(density) { EDGE_DP.dp.toPx() }
    // Поколение жеста: новый жест (или повторное открытие страницы) делает отложенные «страховочные» возвраты прошлого жеста
    // недействительными — раньше такой возврат через 600 мс мог дёрнуть страницу посреди нового жеста.
    val epoch = remember { IntArray(1) }
    // Палец сейчас ведёт страницу (собственное касание).
    val touching = remember { BooleanArray(1) }

    // Упругий возврат на место: заметный, но не чрезмерный отскок. Скорость анимации — из настроек
    // (жёсткость пружины растёт как квадрат скорости, поэтому и отскок становится быстрее/медленнее).
    suspend fun settleBack(): Unit = coroutineScope {
        val k = ThemeSettings.animSpeed
        val spec = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium * k * k)
        launch { progress.animateTo(0f, spec) }
        launch { exit.animateTo(0f, spec) }
        launch { dragY.animateTo(0f, spec) }
    }

    // Анимация подтверждённого возврата, как в Pixel: сразу после отпускания страница быстро дожимается, уезжает и гаснет.
    // Кривая «emphasized decelerate» резко стартует из текущего положения (без вялого разгона), поэтому
    // анимация короткая и не ощущается задержкой. Затем закрываем страницу. Возвращает номер поколения жеста.
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

    // Если закрытие по какой-то причине не случилось (страница всё ещё на экране) — плавно возвращаем на место.
    // Страница, которая успешно закрылась, к этому моменту уже убрана или отключена (enabled = false), её не трогаем.
    suspend fun verifyClosed(my: Int) {
        delay(600)
        if (epoch[0] == my && curEnabled) settleBack()
    }

    suspend fun finishClose(dir: Float) {
        val my = animateClose(dir)
        verifyClosed(my)
    }

    // Страницу открыли заново, пока она ещё доигрывала уход (она осталась в композиции прозрачной и сдвинутой):
    // мгновенно возвращаем её в исходное состояние, иначе новая страница висела бы пустой до 600 мс.
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
                    // Initial: родитель видит касание раньше вложенных списков и кнопок.
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
                            // Касание пропало без отпускания (отмена жеста системой): страница возвращается на место.
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
                                // Только вправо и преимущественно по горизонтали; иначе это обычная прокрутка.
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
                // Предыдущий экран под страницей слегка затемнён: видно, что страница «приподнята» над ним.
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
                    // Смещение в сторону свайпа, но так, чтобы сжатая страница не вылезала за край (с отступом).
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
                    // Motion blur при возврате и закрытии страницы: по скорости горизонтального сдвига (во время жеста пальцем
                    // скорость нулевая — размытия нет, оно появляется на анимации доводки / ухода).
                    horizontalMotionBlur(maxShift * progress.velocity + size.width * EXIT_SLIDE * exit.velocity, maxPx = 36f)
                },
        ) { content() }

        // После content(): регистрируется позже страничного BackHandler и поэтому срабатывает первым.
        // Скрытая (предсобранная) вкладка «назад» не перехватывает.
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
                // Подтверждённый жест доигрываем до конца даже если обработчик отключили (enabled стал false, когда
                // страница уже закрывается): иначе страница застряла бы на полпути или мигнула обратно.
                val my = withContext(NonCancellable) { animateClose(dir) }
                verifyClosed(my)
            } catch (c: CancellationException) {
                // Жест отменили (вернули палец к краю): страница возвращается на место. Если жест уже подтверждён, страница
                // закрывается — возвращать её нельзя (раньше она на миг «появлялась» снова, а потом уезжала).
                if (!committed) withContext(NonCancellable) { settleBack() }
                throw c
            }
        }
    }
}

/**
 * Появление страницы, открытой поверх другой (исполнитель, альбом...): в ту же сторону, откуда страницу возвращает жест
 * «назад», — справа. Страница быстро становится непрозрачной, поэтому экран под ней (например, результаты поиска) не
 * просвечивает сквозь неё, как это было при медленном «проявлении» со сдвигом снизу.
 */
internal fun pageEnter(): EnterTransition =
    slideInHorizontally(tween(animMs(PAGE_IN_MS), easing = EmphasizedDecelerate)) { (it * PAGE_SLIDE).toInt() } +
        fadeIn(tween(animMs(PAGE_FADE_IN_MS), easing = LinearOutSlowInEasing))

/**
 * Уход страницы кнопкой «назад»: уезжает вправо, как при жесте (после жеста она уже невидима, и анимация ничего не
 * добавляет). Затухание начинается ближе к концу, чтобы под страницей не просвечивал предыдущий экран.
 */
internal fun pageExit(): ExitTransition =
    slideOutHorizontally(tween(animMs(PAGE_OUT_MS), easing = EmphasizedAccelerate)) { (it * PAGE_SLIDE).toInt() } +
        fadeOut(tween(animMs(PAGE_FADE_OUT_MS), delayMillis = animMs(PAGE_OUT_MS - PAGE_FADE_OUT_MS)))

/**
 * Замена одной страницы другой (открыт альбом, из него открыли исполнителя, и наоборот): новая страница быстро
 * проявляется и «подрастает» из чуть меньшего размера прямо поверх прежней. Прежняя в это время остаётся непрозрачной
 * (слегка сжимается) и гаснет, только когда новая уже закрыла её, поэтому экран под страницами не просвечивает.
 * Отключается в Настройки → Внешний вид → Анимации.
 */
internal fun pageSwapEnter(): EnterTransition =
    fadeIn(tween(animMs(PAGE_SWAP_FADE_MS), easing = LinearOutSlowInEasing)) +
        scaleIn(tween(animMs(PAGE_SWAP_MS), easing = EmphasizedDecelerate), initialScale = PAGE_SWAP_SCALE)

internal fun pageSwapExit(): ExitTransition =
    scaleOut(tween(animMs(PAGE_SWAP_MS), easing = EmphasizedDecelerate), targetScale = 1f - (1f - PAGE_SWAP_SCALE) / 2f) +
        fadeOut(tween(animMs(PAGE_SWAP_FADE_MS), delayMillis = animMs(PAGE_SWAP_MS - PAGE_SWAP_FADE_MS)))

/** Длительность замены страницы (мс) при скорости анимации 1×. */
private const val PAGE_SWAP_MS = 320

/** Длительность проявления новой страницы при замене (мс). */
private const val PAGE_SWAP_FADE_MS = 140

/** С какого размера «вырастает» новая страница при замене. */
private const val PAGE_SWAP_SCALE = 0.92f

/** Ширина полосы у левого края, с которой начинается собственный жест «назад». */
private const val EDGE_DP = 40

/** На сколько сжимается страница к концу жеста (0.10 → до 90 % размера, как в Android 14+). */
private const val MAX_SHRINK = 0.10f

/** Дополнительное сжатие на фазе ухода. */
private const val EXIT_EXTRA_SHRINK = 0.06f

/** Отступ сжатой страницы от края экрана. */
private const val EDGE_MARGIN_DP = 8

/** Скругление углов страницы во время жеста. */
private const val CORNER_DP = 28

/** Тень страницы во время жеста. */
private const val SHADOW_DP = 14

/** Затемнение предыдущего экрана. */
private const val SCRIM_ALPHA = 0.20f

/** Насколько (в долях ширины) страница уезжает на фазе ухода. */
private const val EXIT_SLIDE = 0.22f

/** Длительность подтверждённого возврата (мс) при скорости анимации 1×. */
private const val CLOSE_MS = 170

/** Material 3 «emphasized decelerate»: быстрый старт и мягкое замедление в конце. */
internal val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** Material 3 «emphasized accelerate»: плавный разгон и быстрый уход. */
internal val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Длительность появления страницы (мс) при скорости анимации 1×. */
private const val PAGE_IN_MS = 340

/** Длительность проявления страницы (мс): короткая, чтобы страница быстро стала непрозрачной. */
private const val PAGE_FADE_IN_MS = 110

/** Длительность ухода страницы кнопкой «назад» (мс). */
private const val PAGE_OUT_MS = 240

/** Длительность затухания страницы в конце ухода (мс). */
private const val PAGE_FADE_OUT_MS = 140

/** Насколько (в долях ширины) страница выезжает справа и уезжает обратно. */
private const val PAGE_SLIDE = 0.25f
