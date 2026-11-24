@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import app.ymusic.m3e.R
import app.ymusic.m3e.data.StoredAccount

/*
 * Смена аккаунта по долгому нажатию на «Профиль» в доке.
 *
 *  • Удержание значка «Профиль» (док, островок, боковая панель, островок планшета) открывает мини-табличку с аккаунтами
 *    и кнопкой «Добавить аккаунт»; всё приложение под ней размывается (Android 12+, ниже — затемнение).
 *  • Как контекстное меню в iOS: не отпуская палец, можно провести к нужному аккаунту и отпустить — выбор сработает.
 *    Если отпустить палец не над строкой, табличка остаётся открытой — дальше обычные нажатия.
 *  • Закрыть: «назад», нажатие на свободное место или выбор строки.
 *  • На самой кнопке «Профиль» тики при удержании отключены (см. pressableHold в Screens.kt и rememberPress в доке).
 */

/** Ключ строки «Добавить аккаунт» в [AccountSwitcherState.bounds]. */
private const val ADD_KEY = "\u0000add"

/** Состояние таблички: открыта ли она, где якорь (значок «Профиль»), над какой строкой палец. */
@Stable
internal class AccountSwitcherState {
    /** Есть ли что показывать (есть сохранённые аккаунты, не режим «Локальные файлы»). Выставляется корневым экраном. */
    var enabled by mutableStateOf(false)

    /** Табличка должна быть открыта (цель анимации). */
    var open by mutableStateOf(false)
        private set

    /** Табличка присутствует в композиции: открыта или ещё закрывается. */
    var shown by mutableStateOf(false)

    /** 0 — скрыта, 1 — открыта; с «перелётом» пружины. Читается в фазе рисования, поэтому без перекомпоновки. */
    val progress = Animatable(0f)

    /** Область значка, по которому удерживали, в координатах окна. */
    var anchor by mutableStateOf(Rect.Zero)
        private set

    /** Ключ строки под пальцем (токен аккаунта или [ADD_KEY]) — пока палец не отпущен. */
    var hovered by mutableStateOf<String?>(null)
        private set

    /** Границы строк в координатах окна — по ним определяется строка под пальцем. */
    val bounds = mutableStateMapOf<String, Rect>()

    /** Выбор строки (ставит само окно). */
    var onPick: ((String) -> Unit)? = null

    fun show(from: Rect) {
        anchor = from
        hovered = null
        open = true
    }

    fun close() {
        open = false
        hovered = null
    }

    /** Палец сдвинулся в точку [p] (координаты окна). */
    fun hoverAt(p: Offset) {
        // Пока табличка только выезжает, её строки ещё не на своих местах.
        if (progress.value < 0.5f) return
        val k = bounds.entries.firstOrNull { it.value.contains(p) }?.key
        if (k != hovered) {
            hovered = k
            if (k != null) Haptics.tick()
        }
    }

    /** Палец отпущен: если он был над строкой — выбираем её, иначе табличка остаётся открытой. */
    fun release() {
        val k = hovered
        hovered = null
        if (k != null) onPick?.invoke(k)
    }
}

internal val LocalAccountSwitcher = compositionLocalOf<AccountSwitcherState?> { null }

/**
 * Долгое нажатие на вкладку «Профиль» открывает табличку аккаунтов; жест продолжается, пока палец прижат
 * (проведите к аккаунту и отпустите). На остальные вкладки не действует. Касания обрабатываются на проходе Initial и до
 * таймаута ничего не потребляют — обычный тап, двойной тап на «Профиль» и вибро нажатия работают как раньше.
 */
@Composable
internal fun Modifier.accountSwitcherGesture(tab: Tab): Modifier {
    val sw = LocalAccountSwitcher.current
    if (tab != Tab.Profile || sw == null) return this
    // Во время правки раскладки долгое нажатие принадлежит редактору.
    val on = sw.enabled && LayoutEdit.pager == null
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onGloballyPositioned { holder[0] = it }
        .pointerInput(sw, on) {
            if (!on) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                var long = false
                try {
                    withTimeout(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeout
                            // Отпустили или ушли за порог касания — это не долгое нажатие.
                            if (!ch.pressed || (ch.position - down.position).getDistance() > slop) return@withTimeout
                        }
                    }
                } catch (_: PointerEventTimeoutCancellationException) {
                    long = true
                }
                if (!long) return@awaitEachGesture
                val c = holder[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                sw.show(c.boundsInWindow())
                Haptics.click()
                // Дальше жест наш: гасим события, чтобы вкладка не нажалась, и ведём палец по строкам таблички.
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                    ev.changes.forEach { it.consume() }
                    if (!ch.pressed) {
                        sw.release()
                        break
                    }
                    if (c.isAttached) sw.hoverAt(c.localToWindow(ch.position))
                }
            }
        }
}

/** Размытие всего приложения под табличкой (Android 12+). Без размытия остаётся одно затемнение. */
@Composable
internal fun Modifier.accountSwitcherBlur(sw: AccountSwitcherState): Modifier {
    if (!sw.shown || ThemeSettings.optimize || Build.VERSION.SDK_INT < 31) return this
    return this.graphicsLayer {
        val r = sw.progress.value.coerceIn(0f, 1f) * 24.dp.toPx()
        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
    }
}

/**
 * Сама табличка: затемнение с размытием позади, под ним — мини-меню в стиле Material 3 Expressive
 * (группа аккаунтов и отдельная группа «Добавить аккаунт», скругления и цвета тем, пружинное появление).
 */
@Composable
internal fun AccountSwitcherOverlay(
    state: AccountSwitcherState,
    accounts: List<StoredAccount>,
    activeToken: String?,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val curSwitch by rememberUpdatedState(onSwitch)
    val curAdd by rememberUpdatedState(onAdd)
    val curActive by rememberUpdatedState(activeToken)
    DisposableEffect(state) {
        state.onPick = { key ->
            state.close()
            if (key == ADD_KEY) curAdd() else if (key != curActive) curSwitch(key)
        }
        onDispose { state.onPick = null }
    }
    LaunchedEffect(state.open) {
        if (state.open) {
            state.shown = true
            state.progress.animateTo(1f, fxSpring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
        } else if (state.shown) {
            state.progress.animateTo(0f, tween<Float>(fxMs(170), easing = FastOutLinearInEasing))
            state.shown = false
        }
    }
    // Аккаунты кончились (вышли из последнего) или включился другой режим — закрываем.
    LaunchedEffect(state.enabled) { if (!state.enabled && state.open) state.close() }
    if (!state.shown) return
    // Обработчик «назад» создаётся только на время показа: он новее остальных и срабатывает первым.
    BackHandler(enabled = state.open) { state.close() }

    val blurred = Build.VERSION.SDK_INT >= 31 && !ThemeSettings.optimize
    val density = LocalDensity.current
    val marginPx = with(density) { 16.dp.roundToPx() }
    val gapPx = with(density) { 12.dp.roundToPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val pivot = remember { floatArrayOf(0.5f, 1f) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        // Затемнение: нажатие по свободному месту закрывает табличку.
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = state.progress.value.coerceIn(0f, 1f) }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = if (blurred) 0.28f else 0.52f))
                .pointerInput(Unit) { detectTapGestures { state.close() } },
        )
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box(
                    Modifier.graphicsLayer {
                        val p = state.progress.value
                        val s = 0.62f + 0.38f * p
                        scaleX = s
                        scaleY = s
                        alpha = (p * 1.6f).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(pivot[0], pivot[1])
                    },
                ) {
                    SwitcherMenu(state, accounts, activeToken) { state.onPick?.invoke(it) }
                }
            },
        ) { measurables, c ->
            val menu = measurables.first().measure(
                Constraints(
                    maxWidth = (c.maxWidth - 2 * marginPx).coerceAtLeast(0),
                    maxHeight = (c.maxHeight - 2 * marginPx).coerceAtLeast(0),
                ),
            )
            layout(c.maxWidth, c.maxHeight) {
                val a = state.anchor.translate(-origin.x, -origin.y)
                val w = c.maxWidth
                val h = c.maxHeight
                val maxX = (w - menu.width - marginPx).coerceAtLeast(marginPx)
                val maxY = (h - menu.height - marginPx).coerceAtLeast(marginPx)
                val x: Int
                val y: Int
                when {
                    // Нижний док / островок: табличка над значком (если сверху тесно — под ним), по центру значка.
                    a.center.y > h * 0.78f || a.center.y < h * 0.18f -> {
                        x = (a.center.x - menu.width / 2f).toInt().coerceIn(marginPx, maxX)
                        val above = a.center.y > h * 0.5f
                        y = (if (above) a.top - gapPx - menu.height else a.bottom + gapPx).toInt().coerceIn(marginPx, maxY)
                    }
                    // Боковая панель / островок планшета: табличка сбоку от значка, по его высоте.
                    a.center.x < w / 2f -> {
                        x = (a.right + gapPx).toInt().coerceIn(marginPx, maxX)
                        y = (a.center.y - menu.height / 2f).toInt().coerceIn(marginPx, maxY)
                    }
                    else -> {
                        x = (a.left - gapPx - menu.width).toInt().coerceIn(marginPx, maxX)
                        y = (a.center.y - menu.height / 2f).toInt().coerceIn(marginPx, maxY)
                    }
                }
                // Табличка «вырастает» из значка, по которому удерживали.
                pivot[0] = ((a.center.x - x) / menu.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                pivot[1] = ((a.center.y - y) / menu.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                menu.place(x, y)
            }
        }
    }
}

@Composable
private fun SwitcherMenu(
    state: AccountSwitcherState,
    accounts: List<StoredAccount>,
    activeToken: String?,
    onPick: (String) -> Unit,
) {
    val groupColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.width(284.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(shape = containerShape(28), color = groupColor, shadowElevation = 8.dp) {
            Column(
                Modifier.heightIn(max = 344.dp).verticalScroll(rememberScrollState()).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                accounts.forEach { a ->
                    key(a.token) {
                        val sub = listOfNotNull(
                            a.provider.title,
                            a.login.takeIf { it.isNotBlank() && it != a.title },
                        ).joinToString(" · ")
                        SwitcherRow(
                            key = a.token,
                            state = state,
                            active = a.token == activeToken,
                            title = a.title,
                            subtitle = sub,
                            onClick = { onPick(a.token) },
                            leading = { AccountAvatar(a.avatarUrl, 40.dp) },
                        )
                    }
                }
            }
        }
        Surface(shape = containerShape(28), color = groupColor, shadowElevation = 8.dp) {
            Box(Modifier.padding(6.dp)) {
                SwitcherRow(
                    key = ADD_KEY,
                    state = state,
                    active = false,
                    title = "Добавить аккаунт",
                    subtitle = null,
                    onClick = { onPick(ADD_KEY) },
                    leading = {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ms_add), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    },
                )
            }
        }
    }
}

/**
 * Строка таблички. Под пальцем (или при нажатии) строка заливается цветом контейнера темы, а её углы «перетекают» —
 * как у выразительных меню Material 3; активный аккаунт отмечен галочкой.
 */
@Composable
private fun SwitcherRow(
    key: String,
    state: AccountSwitcherState,
    active: Boolean,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val hovered = state.hovered == key
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        when {
            hovered -> cs.primaryContainer
            active -> cs.secondaryContainer
            else -> Color.Transparent
        },
        animationSpec = fxSpring(stiffness = Spring.StiffnessMediumLow),
        label = "switcherRowBg",
    )
    val fg by animateColorAsState(
        when {
            hovered -> cs.onPrimaryContainer
            active -> cs.onSecondaryContainer
            else -> cs.onSurface
        },
        animationSpec = fxSpring(stiffness = Spring.StiffnessMediumLow),
        label = "switcherRowFg",
    )
    val corner by animateDpAsState(
        if (hovered) 28.dp else 18.dp,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switcherRowCorner",
    )
    val lead by animateFloatAsState(
        if (hovered) 1.12f else 1f,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "switcherRowLead",
    )
    DisposableEffect(key) { onDispose { state.bounds.remove(key) } }
    CompositionLocalProvider(LocalContentColor provides fg) {
        Row(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { state.bounds[key] = it.boundsInWindow() }
                .clip(cornerShape(corner))
                .background(bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.graphicsLayer { scaleX = lead; scaleY = lead }) { leading() }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = fg.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (active) AnimatedCheck(true, tint = fg)
        }
    }
}
