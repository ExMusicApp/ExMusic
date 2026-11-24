@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)

package app.ymusic.m3e.ui

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import app.ymusic.m3e.data.LyricsSourceOptions
import app.ymusic.m3e.data.UpdateChannel
import app.ymusic.m3e.data.UpdateInfo
import app.ymusic.m3e.data.UpdateResult
import app.ymusic.m3e.data.Updates
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.clickable as foundationClickable
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import app.ymusic.m3e.player.EqPresets
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import app.ymusic.m3e.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ymusic.m3e.AppViewModel
import app.ymusic.m3e.provider
import app.ymusic.m3e.playlistSourceKey
import app.ymusic.m3e.DEFAULT_WAVE
import app.ymusic.m3e.data.AlbumInfo
import app.ymusic.m3e.data.ArtistInfo
import app.ymusic.m3e.data.ArtistRef
import app.ymusic.m3e.data.DownloadMode
import app.ymusic.m3e.data.canDownload
import app.ymusic.m3e.data.isDownloadBlocked
import app.ymusic.m3e.data.DownloadNetwork
import app.ymusic.m3e.data.TrackInfo
import app.ymusic.m3e.data.LyricLine
import app.ymusic.m3e.data.LyricsSource
import app.ymusic.m3e.data.LyricsCandidate
import androidx.compose.foundation.horizontalScroll
import app.ymusic.m3e.LyricsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import app.ymusic.m3e.data.DownloadsState
import app.ymusic.m3e.data.PlaylistInfo
import app.ymusic.m3e.data.Track
import app.ymusic.m3e.data.StoredAccount
import app.ymusic.m3e.data.YandexAuth
import app.ymusic.m3e.data.coverKey
import app.ymusic.m3e.data.albumsFromDownloads
import app.ymusic.m3e.data.mergeAlbums
import app.ymusic.m3e.data.isEditableBy
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.ymusic.m3e.data.DeviceCode
import app.ymusic.m3e.recognition.FoundAction
import app.ymusic.m3e.recognition.RecognitionSettings
import app.ymusic.m3e.recognition.Recognizer
import app.ymusic.m3e.data.LocalFilesSettings
import app.ymusic.m3e.data.LocalFolders
import app.ymusic.m3e.data.LocalMusic
import app.ymusic.m3e.data.LocalTrackEdits
import app.ymusic.m3e.data.ShareLinks
import app.ymusic.m3e.data.AppLog
import app.ymusic.m3e.data.SharedLink
import app.ymusic.m3e.data.UNKNOWN_ARTIST
import androidx.compose.ui.graphics.toArgb
import app.ymusic.m3e.data.LocalModeSettings
import app.ymusic.m3e.data.DevicePlaylist
import app.ymusic.m3e.data.DevicePlaylists
import app.ymusic.m3e.data.localAlbumKey
import app.ymusic.m3e.data.InstalledPlugin
import app.ymusic.m3e.data.PluginPreview
import app.ymusic.m3e.data.PluginSystem
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** Открыть поиск (кнопка поиска на главной): вкладка «Поиск», а если её убрали из панели — отдельная страница поверх. */
private val LocalOpenSearch = staticCompositionLocalOf<() -> Unit> { {} }

/** Растёт, когда сеть вернулась: обложки, которые не успели загрузиться, пробуют ещё раз. */
private val LocalNetEpoch = compositionLocalOf { 0 }

/**
 * Обложка: заглушка с нотой под картинкой + плавное появление; пустого места не бывает.
 * Если картинка не загрузилась (не было сети), при возвращении сети она запрашивается заново сама.
 */
@Composable
internal fun Cover(model: Any?, modifier: Modifier = Modifier, placeholder: Int = R.drawable.ms_music_note) {
    val epoch = LocalNetEpoch.current
    var failed by remember(model) { mutableStateOf(false) }
    var attempt by remember(model) { mutableIntStateOf(0) }
    LaunchedEffect(epoch) { if (failed) attempt = epoch }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(placeholder), null,
            Modifier.fillMaxSize(0.4f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model != null) {
            val ctx = LocalContext.current
            val request = remember(model, attempt) {
                ImageRequest.Builder(ctx).data(model)
                    .let { if (ThemeSettings.optimize) it.crossfade(false) else it.crossfade(160) }
                    .memoryCacheKeyExtra("retry", attempt.toString())
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { failed = true },
                onSuccess = { failed = false },
            )
        }
    }
}

/** Клик с «пружинным» сжатием при нажатии. */
@Composable
private fun Modifier.pressable(shape: Shape, onClick: () -> Unit): Modifier = pressableHold(shape, true, onClick)

/** То же, что [pressable], но [hold] = false отключает тики при удержании (кнопка «Профиль»: удержание открывает смену аккаунта). */
@Composable
private fun Modifier.pressableHold(shape: Shape, hold: Boolean, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    HoldHaptics(pressed && hold, downTick = false)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .foundationClickable(interactionSource = source, indication = LocalIndication.current, onClick = withHaptic(HapticKind.Tick, onClick))
}

/**
 * Клик по всей карточке с «пружинным» сжатием, но без обрезки по форме: рябь рисуется отдельно
 * (Modifier.indication на обложке с тем же source), поэтому скругление не задевает текст под обложкой.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.pressCard(source: MutableInteractionSource, onClick: () -> Unit, onLongClick: (() -> Unit)? = null): Modifier {
    val pressed by source.collectIsPressedAsState()
    HoldHaptics(pressed, downTick = false)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressCard",
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = onClick)
}

/**
 * Все `Modifier.clickable` в этом файле перекрыты этой версией: строки списков, пункты настроек, вкладки дока
 * и прочие нажимаемые элементы получают ту же пружинную анимацию нажатия, что и кнопка «Моя волна»
 * (сжатие + рябь), без правок на местах.
 */
private fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: androidx.compose.ui.semantics.Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    HoldHaptics(pressed && enabled, downTick = false)
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = fxSpring(dampingRatio = 0.6f, stiffness = 800f),
        label = "clickPress",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .foundationClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = withHaptic(HapticKind.Tick, onClick),
        )
}

// ---------- Обновление свайпом сверху вниз + выразительные элементы Material 3 Expressive ----------

/** «Потяни, чтобы обновить»: PullToRefreshBox с выразительным индикатором загрузки (LoadingIndicator) из M3E. */
@Composable
private fun Refreshable(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable BoxScope.() -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/**
 * Счётчик «вернуться наверх»: растёт при нажатии на левый верхний угол экрана (как на iPhone) и при повторном
 * нажатии на уже выбранную вкладку в доке. Все прокручиваемые экраны подписаны на него через [ScrollToTopEffect].
 */
private val LocalScrollTopTick = compositionLocalOf { 0 }

/**
 * Внутренние страницы вкладок (открытый плейлист, «Чарт», «Любимое»...). Пока такая страница открыта, она
 * регистрируется здесь; нажатие на уже выбранную вкладку в доке закрывает все такие страницы и возвращает
 * к главной странице раздела. Если закрывать нечего, нажатие прокручивает экран наверх.
 */
private class InnerPages {
    private val resets = LinkedHashMap<Any, () -> Unit>()
    fun register(key: Any, reset: () -> Unit) { resets[key] = reset }
    fun unregister(key: Any) { resets.remove(key) }

    /** Сколько открытых окон сейчас запрещают листать вкладки свайпом в сторону (см. [PagerLockEffect]). */
    var pagerLocks by androidx.compose.runtime.mutableIntStateOf(0)
    val pagerLocked: Boolean get() = pagerLocks > 0

    /** true — была открыта хотя бы одна внутренняя страница, и она закрыта. */
    fun resetAll(): Boolean {
        if (resets.isEmpty()) return false
        resets.values.toList().forEach { it() }
        return true
    }
}

private val LocalInnerPages = staticCompositionLocalOf { InnerPages() }

/**
 * Страница вкладки сейчас на экране? Вкладки держатся собранными заранее (TabPager), чтобы смена вкладки не собирала экран
 * с нуля. Скрытая страница не должна перехватывать «назад», блокировать свайп вкладок, влиять на кнопку «вверх» и
 * реагировать на «наверх» — всё это учитывает только активная.
 */
internal val LocalPageActive = compositionLocalOf { true }

/** Обычный BackHandler, но у скрытой (предсобранной) вкладки не срабатывает. Остальной код вызывает его как раньше. */
@Composable
private fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled && LocalPageActive.current, onBack = onBack)
}

/** Пока окно на экране, вкладки нельзя листать свайпом в сторону: жест остаётся за самим окном («назад»). */
@Composable
private fun PagerLockEffect() {
    val pages = LocalInnerPages.current
    val pageActive = LocalPageActive.current
    DisposableEffect(pages, pageActive) {
        if (!pageActive) return@DisposableEffect onDispose { }
        pages.pagerLocks++
        onDispose { pages.pagerLocks-- }
    }
}

/** Пока [open] — страница [key] считается внутренней; [reset] возвращает на главную страницу раздела. */
@Composable
private fun InnerPageEffect(key: Any, open: Boolean, reset: () -> Unit) {
    val pages = LocalInnerPages.current
    val current by rememberUpdatedState(reset)
    val pageActive = LocalPageActive.current
    DisposableEffect(pages, key, open, pageActive) {
        if (open && pageActive) pages.register(key) { current() }
        onDispose { pages.unregister(key) }
    }
}

/**
 * Учёт прокрутки для кнопки «вверх»: каждый прокручиваемый экран сообщает, можно ли ему прокрутиться назад
 * (то есть он не на самом верху). У каждого слоя (вкладки, исполнитель, альбом) свой учёт; настройки его не получают.
 */
private class ScrollTracker {
    val scrolled = mutableStateMapOf<Any, Boolean>()
    val canScrollUp: Boolean get() = scrolled.values.any { it }
}

private val LocalScrollTracker = staticCompositionLocalOf<ScrollTracker?> { null }

/**
 * Экран прокручен достаточно далеко, чтобы кнопка «вверх» была нужна: не на пару пикселей от верха и не «залипший» список
 * (его уже убрали с экрана или он опустел, а состояние помнит старую позицию).
 */
private fun androidx.compose.foundation.gestures.ScrollableState.farFromTop(): Boolean = when (this) {
    is LazyListState ->
        canScrollBackward && layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty() &&
            (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > layoutInfo.viewportSize.height / 3)
    is LazyGridState ->
        canScrollBackward && layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty() &&
            (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > layoutInfo.viewportSize.height / 3)
    is androidx.compose.foundation.ScrollState -> value > 240
    else -> canScrollBackward
}

@Composable
private fun ReportScroll(state: androidx.compose.foundation.gestures.ScrollableState) {
    val tracker = LocalScrollTracker.current ?: return
    // Скрытая вкладка не должна включать кнопку «вверх» на чужом экране.
    if (!LocalPageActive.current) return
    DisposableEffect(tracker, state) { onDispose { tracker.scrolled.remove(state) } }
    LaunchedEffect(tracker, state) {
        snapshotFlow { state.farFromTop() }.collect { tracker.scrolled[state] = it }
    }
}

/** Выполняет [action] при каждом сигнале «наверх» (но не при первом показе экрана — иначе собьётся своя прокрутка). */
@Composable
private fun ScrollToTopEffect(action: suspend () -> Unit) {
    val tick = LocalScrollTopTick.current
    val initial = remember { tick }
    val current by rememberUpdatedState(action)
    val pageActive = LocalPageActive.current
    LaunchedEffect(tick) { if (tick != initial && pageActive) current() }
}

/**
 * Плавная прокрутка наверх по кнопке «вверх», левому верхнему углу и повторному нажатию на вкладку: один непрерывный
 * заезд с торможением в конце (длительность растёт с расстоянием, но не дольше ~0,6 с). Раньше список сначала мгновенно
 * «телепортировался» к началу, а потом доезжал пружиной — выглядело рывком.
 */
private fun topScrollSpec(distancePx: Float): androidx.compose.animation.core.AnimationSpec<Float> {
    val ms = (320 + distancePx / 7f).toInt().coerceIn(320, 620)
    return tween(animMs(ms), easing = EmphasizedDecelerate)
}

private suspend fun LazyListState.scrollToTop() {
    if (!canScrollBackward) return
    val vp = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat().coerceAtLeast(1f)
    // Очень далеко: сначала быстрый разгон на пару экранов, затем подхватываем рядом с началом и плавно тормозим.
    if (firstVisibleItemIndex > 8) {
        animateScrollBy(-vp * 1.4f, tween(animMs(170), easing = FastOutLinearInEasing))
        if (firstVisibleItemIndex > 6) scrollToItem(6)
    }
    val vis = layoutInfo.visibleItemsInfo
    val step = when {
        vis.size >= 2 -> ((vis.last().offset - vis.first().offset).toFloat() / (vis.size - 1)).coerceAtLeast(1f)
        vis.size == 1 -> vis.first().size.toFloat().coerceAtLeast(1f)
        else -> 200f
    }
    val distance = firstVisibleItemIndex * step + firstVisibleItemScrollOffset
    if (distance > 0f) animateScrollBy(-distance, topScrollSpec(distance))
    // Оценка высот могла быть неточной — дотягиваем до самого верха без анимации.
    if (canScrollBackward) scrollToItem(0)
}

private suspend fun LazyGridState.scrollToTop() {
    if (!canScrollBackward) return
    val vp = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat().coerceAtLeast(1f)
    if (firstVisibleItemIndex > 24) {
        animateScrollBy(-vp * 1.4f, tween(animMs(170), easing = FastOutLinearInEasing))
        if (firstVisibleItemIndex > 12) scrollToItem(12)
    }
    val vis = layoutInfo.visibleItemsInfo
    val ys = vis.map { it.offset.y }.distinct().sorted()
    val rowStep = when {
        ys.size >= 2 -> ((ys.last() - ys.first()).toFloat() / (ys.size - 1)).coerceAtLeast(1f)
        vis.isNotEmpty() -> vis.first().size.height.toFloat().coerceAtLeast(1f)
        else -> 200f
    }
    val perRow = if (ys.isEmpty()) 1 else vis.count { it.offset.y == ys.first() }.coerceAtLeast(1)
    val distance = (firstVisibleItemIndex / perRow) * rowStep + firstVisibleItemScrollOffset
    if (distance > 0f) animateScrollBy(-distance, topScrollSpec(distance))
    if (canScrollBackward) scrollToItem(0)
}

/** Прокрутка обычного (не ленивого) экрана наверх тем же плавным заездом. */
private suspend fun ScrollState.smoothToTop() {
    if (value <= 0) return
    animateScrollTo(0, topScrollSpec(value.toFloat()))
}

/** Состояние прокрутки обычного экрана: откликается на «наверх» (левый верхний угол, повторное нажатие на вкладку). */
@Composable
internal fun rememberTopScrollState(): ScrollState {
    val s = rememberScrollState()
    ScrollToTopEffect { s.smoothToTop() }
    ReportScroll(s)
    return s
}

/** То же для экрана на LazyColumn. */
@Composable
private fun rememberTopListState(): LazyListState {
    val s = rememberLazyListState()
    ScrollToTopEffect { s.scrollToTop() }
    ReportScroll(s)
    return s
}

/**
 * «Наверх» по нажатию в верхней части экрана, как тап по статус-бару на iPhone и в Chrome. Android не передаёт
 * приложению касания по самой строке состояния («шторке»), поэтому зона начинается сразу под ней и занимает
 * верхний левый угол (примерно высота верхней панели). Касание считается, только если его не обработал
 * никто другой: кнопки, строки и прокрутка в этой зоне работают как обычно, а нажатие по пустому месту
 * или заголовку возвращает наверх.
 */
@Composable
private fun Modifier.cornerTapToTop(onTap: () -> Unit): Modifier {
    val density = LocalDensity.current
    val zoneH = WindowInsets.statusBars.getTop(density) + with(density) { 56.dp.toPx() }
    val current by rememberUpdatedState(onTap)
    return pointerInput(zoneH) {
        val zoneW = size.width * 0.5f
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.x > zoneW || down.position.y > zoneH) return@awaitEachGesture
            // Ждём отпускания на проходе Final: к этому моменту дочерние элементы уже сказали, обработали ли они касание.
            var handledByOthers = false
            var upTime = -1L
            while (upTime < 0L) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (change.isConsumed) handledByOthers = true
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                if (!change.pressed) upTime = change.uptimeMillis
            }
            if (!handledByOthers && upTime - down.uptimeMillis < 400L) current()
        }
    }
}

/** Общий размер обложки и кнопки play/pause в мини-плеере. */
private val MiniControlSize = 48.dp

/** Высота гребня волны в круговом индикаторе мини-плеера: небольшая, чтобы кольцо не наезжало на кнопку play/pause. */
private val MiniWaveAmp = 2.dp

/** Размер кольца вокруг кнопки play/pause мини-плеера (кнопка — [MiniControlSize] минус 12 dp): волна на кнопку не заходит. */
private fun miniRingSize(wavy: Boolean): Dp =
    maxOf(MiniControlSize + 4.dp, circularRingSize(MiniControlSize - 12.dp, 3.dp, wavy, gap = 2.dp, waveAmplitude = MiniWaveAmp))

/** Положение шторки полного плеера: 0 — открыта, [closedPx] — спрятана под нижним краем. Тянется пальцем и пружинит. */
private class PlayerSheetState(
    val offset: Animatable<Float, AnimationVector1D>,
    val closedPx: Float,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    fun open() { scope.launch { offset.animateTo(0f, tween(animMs(320), easing = FastOutSlowInEasing)) } }
    fun close() { scope.launch { offset.animateTo(closedPx, tween(animMs(220), easing = FastOutSlowInEasing)) } }
    fun dragBy(dy: Float) { scope.launch { offset.snapTo((offset.value + dy).coerceIn(0f, closedPx)) } }

    /** Отпустили, вытягивая плеер вверх от мини-плеера: короткого свайпа или рывка достаточно. */
    fun settleOpening(velocityY: Float) {
        if (velocityY < -900f || offset.value < closedPx * 0.9f) open() else close()
    }

    /** Отпустили, утягивая открытый плеер вниз. */
    fun settleClosing(velocityY: Float) {
        if (velocityY > 1500f || offset.value > closedPx * 0.18f) close() else open()
    }
}

/**
 * Нижняя панель, которая при скрытии не оставляет пустого места: высота плавно сжимается до [minHeightPx]
 * (высота системной панели навигации), а содержимое уезжает вниз и растворяется. [fraction]: 0 — показана, 1 — скрыта.
 */
@Composable
private fun CollapsingDock(
    fraction: State<Float>,
    minHeightPx: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier.graphicsLayer { alpha = 1f - fraction.value },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val fullH = placeables.sumOf { it.height }
        val w = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val shrink = (fullH - minHeightPx).coerceAtLeast(0) * fraction.value
        val h = (fullH - shrink).roundToInt().coerceAtLeast(0)
        layout(w.coerceAtLeast(constraints.minWidth), h) {
            var y = 0
            placeables.forEach { it.place(0, y); y += it.height }
        }
    }
}

/** Нижний отступ + высота нижней панели: экран уходит под док, а последний элемент остаётся над ним. */
@Composable
private fun PaddingValues.withDockInset(): PaddingValues {
    val ld = androidx.compose.ui.platform.LocalLayoutDirection.current
    val inset = dockInset()
    return PaddingValues(
        start = calculateStartPadding(ld), top = calculateTopPadding(),
        end = calculateEndPadding(ld), bottom = calculateBottomPadding() + inset,
    )
}

/** LazyColumn, обновляемый свайпом вниз: [AppViewModel.pullRefresh] перезагружает данные текущего экрана. */
@Composable
private fun PullLazyColumn(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    searchQuery: String = "",
    content: LazyListScope.() -> Unit,
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    ScrollToTopEffect { state.scrollToTop() }
    ReportScroll(state)
    val padded = contentPadding.withDockInset()
    val wide = LocalWide.current
    Refreshable(ui.refreshing, { vm.pullRefresh(searchQuery) }, modifier) {
        // На планшете строки не растягиваются на весь экран: список центрируется и занимает не больше 900 dp.
        LazyColumn(
            (if (wide) Modifier.fillMaxHeight().widthIn(max = 900.dp).align(Alignment.TopCenter) else Modifier.fillMaxSize()),
            state = state, contentPadding = padded, content = content,
        )
    }
}

/** LazyVerticalGrid, обновляемый свайпом вниз. */
@Composable
private fun PullLazyGrid(
    vm: AppViewModel,
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    searchQuery: String = "",
    content: LazyGridScope.() -> Unit,
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    ScrollToTopEffect { state.scrollToTop() }
    ReportScroll(state)
    val padded = contentPadding.withDockInset()
    Refreshable(ui.refreshing, { vm.pullRefresh(searchQuery) }, modifier) {
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = padded,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}

/**
 * Страница «шапка + список» (альбом, плейлист, исполнитель...). На телефоне — одна лента: шапка, кнопки, потом список.
 * На планшете — две колонки: слева шапка и кнопки в своей карточке (со своей прокруткой), справа список треков.
 * [header] и [content] — две части одной и той же ленты, поэтому на телефоне всё выглядит как раньше.
 */
@Composable
private fun SplitPullColumn(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    header: LazyListScope.() -> Unit,
    content: LazyListScope.() -> Unit,
) {
    if (!LocalWide.current) {
        PullLazyColumn(vm, modifier, state = state, contentPadding = PaddingValues(bottom = 24.dp)) { header(); content() }
        return
    }
    TwoPane(
        modifier = modifier,
        left = {
            Surface(
                shape = rounded(32), color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, end = 6.dp, bottom = 12.dp + dockInset()),
            ) {
                CompositionLocalProvider(LocalInSplitPane provides true) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        content = header,
                    )
                }
            }
        },
        right = {
            PullLazyColumn(vm, Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), content = content)
        },
    )
}

/** Motion blur включён (настройка, не «Оптимизация», Android 12+). */
internal fun motionBlurActive(): Boolean =
    ThemeSettings.motionBlur && !ThemeSettings.optimize && android.os.Build.VERSION.SDK_INT >= 31

/**
 * Размытие по горизонтали, пропорциональное скорости сдвига [velocityPxPerSec] (px/с). Вызывать из graphicsLayer, где
 * в том же блоке читается значение самой анимации, — тогда блок перерисовывается каждый кадр. Пока сдвиг стоит или
 * ведётся пальцем (snapTo), скорость нулевая и размытия нет.
 */
internal fun androidx.compose.ui.graphics.GraphicsLayerScope.horizontalMotionBlur(velocityPxPerSec: Float, maxPx: Float = 28f) {
    if (!motionBlurActive()) return
    val k = ThemeSettings.motionBlurStrength
    val b = (kotlin.math.abs(velocityPxPerSec) / 1000f * 2.5f * k).coerceAtMost(maxPx * k)
    if (b > 0.8f) renderEffect = androidx.compose.ui.graphics.BlurEffect(b, 0f, androidx.compose.ui.graphics.TileMode.Clamp)
}

/** Плавное пропадание и смещение элементов списка; выключается в Настройки → Внешний вид → Анимации. */
private fun androidx.compose.foundation.lazy.LazyItemScope.removeAnim(): Modifier =
    if (ThemeSettings.animRemove && !ThemeSettings.optimize) {
        Modifier.animateItem(
            fadeOutSpec = tween(animMs(240)),
            placementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1)),
        )
    } else Modifier.animateItem(placementSpec = null, fadeOutSpec = null)

/** То же для элементов сетки (плейлисты). */
private fun androidx.compose.foundation.lazy.grid.LazyGridItemScope.removeAnim(): Modifier =
    if (ThemeSettings.animRemove && !ThemeSettings.optimize) {
        Modifier.animateItem(
            fadeOutSpec = tween(animMs(240)),
            placementSpec = spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1)),
        )
    } else Modifier.animateItem(placementSpec = null, fadeOutSpec = null)

/** Switch в стиле M3E: в положении «включено» на бегунке появляется галочка. */
@Composable
internal fun ExpressiveSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { cb -> { v: Boolean -> Haptics.tick(); cb(v) } },
        thumbContent = if (checked) ({
            Icon(painterResource(R.drawable.ms_check), null, Modifier.size(SwitchDefaults.IconSize))
        }) else null,
    )
}

/** Метка explicit (18+) рядом с названием трека. */
@Composable
private fun ExplicitMark(modifier: Modifier = Modifier, size: Int = 16) {
    Icon(
        painterResource(R.drawable.ms_explicit), contentDescription = "Explicit",
        modifier = modifier.size(size.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Плавное появление элемента списка (с небольшой задержкой по номеру); index < 0 — без анимации. */
@Composable
private fun Modifier.appear(index: Int): Modifier {
    val p = remember { Animatable(if (index < 0 || ThemeSettings.optimize) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (index >= 0 && !ThemeSettings.optimize) {
            delay(index.coerceAtMost(8) * 30L)
            // Короткий tween вместо длинной пружины: десятки строк не «дрожат» по секунде каждая — меньше кадров на анимацию.
            p.animateTo(1f, tween(fxMs(260), easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        val v = p.value
        // Анимация закончилась — слой не трогаем (без лишнего полупрозрачного слоя на каждой строке списка).
        if (v < 1f) {
            alpha = v.coerceIn(0f, 1f)
            scaleX = 0.9f + 0.1f * v
            scaleY = 0.9f + 0.1f * v
            translationY = (1f - v) * 28.dp.toPx()
        }
    }
}

/** Что играет прямо сейчас ("liked" / "pl:<owner>:<kind>"), а если музыка на паузе — null. По нему светятся карточки плейлистов. */
private val LocalPlayingSource = compositionLocalOf<String?> { null }

/** Открывает страницу артиста; предоставляется в AppRoot и используется строками треков. */
/** Открыть плейлист страницей (переключает на вкладку «Плейлисты»). */
private val LocalShowPlaylist = staticCompositionLocalOf<(PlaylistInfo) -> Unit> { {} }
private val LocalOpenArtist = staticCompositionLocalOf<(ArtistRef) -> Unit> { {} }

/** Открывает диалог «Добавить в плейлист» для трека; предоставляется в AppRoot. */
private val LocalAddToPlaylist = staticCompositionLocalOf<(Track) -> Unit> { {} }

/** Открывает страницу альбома по id (и названию для заголовка); предоставляется в AppRoot. */
private val LocalOpenAlbum = staticCompositionLocalOf<(String, String?) -> Unit> { { _, _ -> } }

/** Состояние скачанных песен (для меню трека); предоставляется в AppRoot. */
private val LocalDownloads = compositionLocalOf { DownloadsState() }

/** Скачать трек / удалить скачанный; предоставляется в AppRoot. */
private val LocalToggleDownload = staticCompositionLocalOf<(Track) -> Unit> { {} }

/** Скачать трек в выбранное место (файлом / в приложение / и туда и туда); предоставляется в AppRoot. */
private val LocalDownloadTo = staticCompositionLocalOf<(Track, DownloadMode) -> Unit> { { _, _ -> } }

/** Удалить скачанный трек; предоставляется в AppRoot. */
private val LocalRemoveDownload = staticCompositionLocalOf<(Track) -> Unit> { {} }

/** Спросить, куда скачать трек (окно выбора режима); предоставляется в AppRoot. */
private val LocalPickDownloadMode = staticCompositionLocalOf<(Track) -> Unit> { {} }

/** Открывает окно «Информация о треке»; предоставляется в AppRoot. */
private val LocalShowTrackInfo = staticCompositionLocalOf<(Track) -> Unit> { {} }

/** Открывает окно «Изменить название, исполнителя и обложку» для файла с устройства; предоставляется в AppRoot. */
private val LocalEditLocalTrack = staticCompositionLocalOf<(Track) -> Unit> { {} }

/** Окно «Изменить» своего трека (false) или альбома (true) SoundCloud по id. */
private val LocalEditScItem = staticCompositionLocalOf<(String, Boolean) -> Unit> { { _, _ -> } }

/** Id исполнителя (с префиксом SoundCloud) для активного аккаунта SoundCloud; null — аккаунт другой. Нужен, чтобы узнать «свои» треки и альбомы. */
private val LocalScMyId = staticCompositionLocalOf<String?> { null }

/** Нет сети: треки, которых нет на устройстве, показываются приглушёнными. */
private val LocalOffline = compositionLocalOf { false }

/**
 * Запуск волны «по…» из карточек, страниц и меню трека (в плеере для этого есть [WaveFromSheet]).
 * Предоставляется в AppRoot; null — волны нет (режим «Локальные файлы» или нет сети), кнопки тогда не показываются.
 */
private class WaveActions(
    val track: (Track) -> Unit,
    val album: (AlbumInfo) -> Unit,
    val artist: (ArtistRef) -> Unit,
    val playlist: (PlaylistInfo) -> Unit,
    val liked: () -> Unit,
)

private val LocalWaveActions = staticCompositionLocalOf<WaveActions?> { null }

/** Маленькая круглая кнопка с волной в углу обложки карточки: включает волну по этому альбому / исполнителю / плейлисту. */
@Composable
private fun WaveBadge(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f))
            .clickable(onClickLabel = "Включить волну", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ms_graphic_eq), contentDescription = "Включить волну",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** id лайкнутых и id скачанных треков — для значков в списках; предоставляются в AppRoot. */
private val LocalLikedIds = compositionLocalOf<Set<String>> { emptySet() }
private val LocalDownloadedIds = compositionLocalOf<Set<String>> { emptySet() }

/** Значки рядом с треком: «скачан» и «в лайках». Если показывать нечего — ничего не рисуется. */
@Composable
private fun TrackMarks(t: Track, modifier: Modifier = Modifier) {
    val liked = t.id in LocalLikedIds.current
    val downloaded = !t.isLocal && t.id in LocalDownloadedIds.current
    if (!liked && !downloaded) return
    val tint = MaterialTheme.colorScheme.primary
    Row(modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (downloaded) {
            Icon(painterResource(R.drawable.ms_download_done), contentDescription = "Скачан", modifier = Modifier.size(16.dp), tint = tint)
        }
        if (downloaded && liked) Spacer(Modifier.width(4.dp))
        if (liked) {
            Icon(painterResource(R.drawable.ms_favorite), contentDescription = "В «Мне нравится»", modifier = Modifier.size(16.dp), tint = tint)
        }
    }
}

/** Пункт меню «Выбрать для микса» / «Убрать из выбора» (выбор двух треков для микса, см. [MixPicker]). */
@Composable
private fun MixPickMenuItem(t: Track, close: () -> Unit) {
    val sel = MixPicker.indexOf(t) >= 0
    DropdownMenuItem(
        text = { Text(if (sel) "Убрать из выбора для микса" else "Выбрать для микса") },
        leadingIcon = { Icon(painterResource(R.drawable.ms_graphic_eq), null) },
        onClick = { close(); MixPicker.toggle(t) },
    )
}

/** Пункт меню плеера: запустить микс текущего трека со следующим прямо сейчас. */
@Composable
private fun MixNextMenuItem(vm: AppViewModel, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Запустить микс со следующим") },
        leadingIcon = { Icon(painterResource(R.drawable.ms_graphic_eq), null) },
        enabled = vm.canMixNext(),
        onClick = { close(); vm.mixWithNext() },
    )
}

/**
 * Пункты меню трека про скачивание и информацию (общие для меню строки трека и меню «⋮» в плеере).
 * Не скачан — «Скачать» (по настройке) и «Скачать как…»; скачан частично — можно доложить недостающее; всегда — «Информация о треке».
 */
@Composable
private fun TrackActionItems(t: Track, close: () -> Unit) {
    val dl = LocalDownloads.current
    val downloadTo = LocalDownloadTo.current
    val remove = LocalRemoveDownload.current
    val pickMode = LocalPickDownloadMode.current
    val showInfo = LocalShowTrackInfo.current
    val d = dl.items.firstOrNull { it.id == t.id }
    val busy = dl.progress.containsKey(t.id)
    when {
        busy -> DropdownMenuItem(
            text = { Text("Скачивается…") },
            leadingIcon = { Icon(painterResource(R.drawable.ms_download), null) },
            enabled = false,
            onClick = {},
        )
        // SoundCloud: автор запретил скачивание — пункт показываем, но недоступный, с пояснением.
        d == null && t.isDownloadBlocked() -> DropdownMenuItem(
            text = {
                Column {
                    Text("Скачать")
                    Text(app.ymusic.m3e.data.ScDownloads.FORBIDDEN, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            leadingIcon = { Icon(painterResource(R.drawable.ms_download), null) },
            enabled = false,
            onClick = {},
        )
        d == null -> {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Скачать")
                        Text(dl.mode.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = { Icon(painterResource(R.drawable.ms_download), null) },
                onClick = { close(); downloadTo(t, dl.mode) },
            )
            DropdownMenuItem(
                text = { Text("Скачать как…") },
                leadingIcon = { Icon(painterResource(R.drawable.ms_cloud_download), null) },
                onClick = { close(); pickMode(t) },
            )
        }
        else -> {
            if (!d.inFiles) DropdownMenuItem(
                text = { Text("Сохранить файлом (${dl.dirLabel})") },
                leadingIcon = { Icon(painterResource(R.drawable.ms_folder), null) },
                onClick = { close(); downloadTo(t, DownloadMode.Files) },
            )
            if (!d.inApp) DropdownMenuItem(
                text = { Text("Сохранить в приложении") },
                leadingIcon = { Icon(painterResource(R.drawable.ms_smartphone), null) },
                onClick = { close(); downloadTo(t, DownloadMode.App) },
            )
            DropdownMenuItem(
                text = { Text("Удалить из скачанных") },
                leadingIcon = { Icon(painterResource(R.drawable.ms_delete), null) },
                onClick = { close(); remove(t) },
            )
        }
    }
    // Свой трек SoundCloud: можно поменять название, описание, жанр, теги, обложку и видимость.
    val scMyId = LocalScMyId.current
    val editScItem = LocalEditScItem.current
    if (scMyId != null && app.ymusic.m3e.data.ScIds.isSc(t.id) && t.artistRefs.any { it.id == scMyId }) {
        DropdownMenuItem(
            text = { Text("Изменить трек") },
            leadingIcon = { Icon(painterResource(R.drawable.ms_edit), null) },
            onClick = { close(); editScItem(t.id, false) },
        )
    }
    DropdownMenuItem(
        text = { Text("Информация о треке") },
        leadingIcon = { Icon(painterResource(R.drawable.ms_info), null) },
        onClick = { close(); showInfo(t) },
    )
    // Поделиться: у трека Яндекс Музыки — ссылка, у файла с устройства — сам файл.
    val shareCtx = LocalContext.current
    DropdownMenuItem(
        text = { Text(if (t.isLocal) "Поделиться файлом" else "Поделиться") },
        leadingIcon = { Icon(painterResource(R.drawable.ms_share), null) },
        onClick = {
            close()
            if (t.isLocal) ShareLinks.shareFile(shareCtx, t)
            else ShareLinks.track(t)?.let { link ->
                ShareQrHost.open(t.title, "${t.title} — ${t.artists}\n$link", link)
            }
        },
    )
}

/**
 * Окно выбора, куда скачивать: файлами, только в приложении или и то и другое.
 * [showRemember] — добавляет «Не показывать снова»: выбранный способ станет основным, и окно больше не появится.
 */
@Composable
private fun DownloadModeDialog(
    title: String,
    text: String?,
    initial: DownloadMode,
    dirLabel: String,
    onDismiss: () -> Unit,
    showRemember: Boolean = false,
    onPick: (DownloadMode, Boolean) -> Unit,
) {
    var sel by remember { mutableStateOf(initial) }
    var dontAsk by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (text != null) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                DownloadMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(containerShape(16)).clickable { sel = m }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = sel == m, onClick = { sel = m })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(m.title, style = MaterialTheme.typography.bodyLarge)
                            Text(m.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (sel.toFiles) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Папка: $dirLabel", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showRemember) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(containerShape(16)).clickable { dontAsk = !dontAsk }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text("Не показывать снова", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Выбранный способ станет основным. Вернуть вопрос можно в Настройки → Скачивание песен.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(sel, showRemember && dontAsk) }) { Text("Скачать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun infoDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** «Информация о треке»: как в Яндекс Музыке — исполнитель, альбом, год, лейбл, авторы и т. д. Пустые поля не показываются. */
@Composable
private fun TrackInfoSheet(vm: AppViewModel, t: Track, onDismiss: () -> Unit) {
    var loading by remember(t.id) { mutableStateOf(true) }
    var info by remember(t.id) { mutableStateOf<TrackInfo?>(null) }
    LaunchedEffect(t.id) {
        info = vm.fetchTrackInfo(t)
        loading = false
    }
    // Подписка только на загрузку этого трека: перерисовки шторки от чужих загрузок зациклили бы её анимацию (см. RecentTracksSheet).
    val dNow by remember(vm, t.id) {
        vm.downloads.map { st -> st.items.firstOrNull { it.id == t.id } }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = vm.downloads.value.items.firstOrNull { it.id == t.id })
    val d = dNow
    val i = info
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(model = t.cover(300), modifier = Modifier.size(72.dp).clip(rounded(16)))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        (i?.title ?: t.title) + (i?.version?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        i?.artists?.ifBlank { null } ?: t.artists, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (loading) LoadingIndicator(Modifier.size(32.dp))
            }
            val album = i?.album ?: t.albumTitle
            val year = i?.year?.takeIf { it > 0 } ?: t.year.takeIf { it > 0 }
            val labels = i?.labels?.takeIf { it.isNotEmpty() } ?: listOfNotNull(t.label)
            val genre = i?.genre ?: t.genre
            i?.artists?.takeIf { it.isNotBlank() }?.let { InfoRow("Исполнитель", it) } ?: t.artists.takeIf { it.isNotBlank() }?.let { InfoRow("Исполнитель", it) }
            album?.takeIf { it.isNotBlank() }?.let { InfoRow("Альбом", it) }
            i?.releaseDate?.let { InfoRow("Дата выхода", it) } ?: year?.let { InfoRow("Год выпуска", it.toString()) }
            genre?.takeIf { it.isNotBlank() }?.let { InfoRow("Жанр", it) }
            if (labels.isNotEmpty()) InfoRow("Лейбл", labels.joinToString(", "))
            i?.writers?.takeIf { it.isNotEmpty() }?.let { InfoRow("Автор текста", it.joinToString(", ")) }
            i?.composers?.takeIf { it.isNotEmpty() }?.let { InfoRow("Композитор", it.joinToString(", ")) }
            val dur = i?.durationMs?.takeIf { it > 0 } ?: t.durationMs.takeIf { it > 0 }
            dur?.let { InfoRow("Длительность", infoDuration(it)) }
            if (i?.explicit ?: t.explicit) InfoRow("Возрастное ограничение", "18+ (нецензурная лексика)")
            d?.let {
                InfoRow(
                    "Скачано",
                    listOfNotNull(
                        if (it.inFiles) "файлом (${vm.downloads.value.dirLabel})" else null,
                        if (it.inApp) "в приложении" else null,
                    ).joinToString(" и ") + " · ${it.ext.uppercase()} · ${formatSize(it.size)}",
                )
            }
            if (!loading && i == null) {
                Text(
                    "Подробности не загрузились — возможно, нет сети.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}


// ---------- Режим «Локальные файлы» (без Яндекс Музыки) ----------

private enum class LocalTab(val title: String, val icon: Int) {
    Files("Файлы", R.drawable.ms_folder),
    Playlists("Плейлисты", R.drawable.ms_library_music),
}

private fun List<Track>.filterByQuery(q: String): List<Track> =
    if (q.isEmpty()) this else filter {
        it.title.contains(q, ignoreCase = true) || it.artists.contains(q, ignoreCase = true) ||
            (it.albumTitle?.contains(q, ignoreCase = true) == true)
    }

/**
 * Приложение без Яндекс Музыки: музыка с устройства и свои плейлисты из неё. Плеер (мини и полный) — те же, что и в
 * обычном режиме. Включается кнопкой «Пропустить» на экране входа; из него можно вернуться ко входу в Яндекс.
 */
@Composable
private fun LocalModeRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(LocalTab.Files) }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    var trackToAdd by remember { mutableStateOf<Track?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showExit by remember { mutableStateOf(false) }
    val pickFolder = rememberFolderPlaylistPicker { p -> tab = LocalTab.Playlists; openedId = p.id }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { AppToast.error(it); vm.dismissError() } }
    LaunchedEffect(state.message) { state.message?.let { AppToast.show(it); vm.dismissMessage() } }

    // Доступ к аудио: диалог показываем один раз, дальше — кнопка «Разрешить доступ».
    var granted by remember { mutableStateOf(LocalMusic.hasPermission(ctx)) }
    val request = rememberAudioAccessRequest { ok ->
        granted = ok
        if (ok) vm.loadLocalTracks()
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (granted) vm.loadLocalTracks()
        else if (!asked) { asked = true; request(false) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = LocalMusic.hasPermission(ctx)
        if (now != granted) {
            granted = now
            if (now) vm.loadLocalTracks()
        } else if (now) vm.loadLocalTracks()
    }

    // Полный плеер — шторка поверх, как в обычном режиме.
    val sheetScope = rememberCoroutineScope()
    val closedPx = with(LocalDensity.current) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * ctx.resources.displayMetrics.density
    }
    // Поворот экрана пересоздаёт Activity: открытый плеер должен остаться открытым (раньше он «вылетал» на главную).
    var playerWasOpen by rememberSaveable { mutableStateOf(false) }
    val sheet = remember { PlayerSheetState(Animatable(if (playerWasOpen) 0f else closedPx), closedPx, sheetScope) }
    LaunchedEffect(sheet) { snapshotFlow { sheet.offset.value < sheet.closedPx * 0.5f }.collect { playerWasOpen = it } }
    val playerVisible by remember { derivedStateOf { sheet.offset.value < sheet.closedPx - 1f } }
    val openPlayerRequested by vm.openPlayerRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayerRequested) {
        if (openPlayerRequested) {
            if (state.nowPlaying != null) sheet.open()
            vm.consumeOpenPlayer()
        }
    }

    val playlists = DevicePlaylists.items
    val opened = playlists.firstOrNull { it.id == openedId }
    LaunchedEffect(opened, openedId) { if (openedId != null && opened == null) openedId = null }
    BackHandler(enabled = opened != null) { openedId = null }
    BackHandler(enabled = opened == null && tab != LocalTab.Files) { tab = LocalTab.Files }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.playerGlassBackdrop(
                enabled = GlassSettings.playerGlass && !ThemeSettings.optimize && state.nowPlaying != null,
                progress = { 1f - sheet.offset.value / sheet.closedPx.coerceAtLeast(1f) },
            ),
            snackbarHost = { AppToastHost() },
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = state.nowPlaying != null,
                        enter = fadeIn(tween(animMs(250))) + slideInVertically(tween(animMs(350))) { it },
                        exit = fadeOut(tween(animMs(200))) + slideOutVertically(tween(animMs(250))) { it },
                    ) {
                        CompositionLocalProvider(LocalHoldHaptics provides false) { MiniPlayer(vm, sheet, island = false) }
                    }
                    NavigationBar {
                        LocalTab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t; openedId = null },
                                icon = { Icon(painterResource(t.icon), contentDescription = t.title) },
                                label = { Text(t.title) },
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when {
                    opened != null -> LocalPlaylistPage(vm, opened, onBack = { openedId = null })
                    tab == LocalTab.Files -> LocalFilesTab(
                        vm, granted = granted, onRequest = { request(true) },
                        onAdd = { trackToAdd = it }, onExit = { showExit = true },
                    )
                    else -> LocalPlaylistsTab(
                        vm, onOpen = { openedId = it.id }, onCreate = { showCreate = true }, onFolder = pickFolder, onExit = { showExit = true },
                    )
                }
            }
        }
        if (playerVisible && state.nowPlaying != null) {
            CompositionLocalProvider(LocalHoldHaptics provides false) { PlayerSheet(vm, sheet) }
        }
    }

    trackToAdd?.let { t -> DevicePlaylistChooser(t, onDismiss = { trackToAdd = null }) }
    if (showCreate) {
        DevicePlaylistNameDialog(
            title = "Новый плейлист", initial = "", confirm = "Создать",
            onDismiss = { showCreate = false },
            onConfirm = { name -> val p = DevicePlaylists.create(name); showCreate = false; tab = LocalTab.Playlists; openedId = p.id },
        )
    }
    if (showExit) {
        AlertDialog(
            onDismissRequest = { showExit = false },
            title = { Text("Войти в Яндекс Музыку?") },
            text = { Text("Откроется экран входа. Ваши плейлисты из файлов устройства сохранятся — к ним можно вернуться кнопкой «Пропустить».") },
            confirmButton = { TextButton(onClick = { showExit = false; LocalModeSettings.updateEnabled(false) }) { Text("Войти") } },
            dismissButton = { TextButton(onClick = { showExit = false }) { Text("Отмена") } },
        )
    }
}

/** Строка трека режима «Локальные файлы»: обложка, название, исполнитель и меню (добавить в плейлист / убрать из него). */
@Composable
private fun DeviceTrackRow(t: Track, onAdd: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    val editLocal = LocalEditLocalTrack.current
    val rowCtx = LocalContext.current
    ListItem(
        headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(t.artists, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Box {
                Cover(model = t.cover(200), modifier = Modifier.size(52.dp).clip(rounded(14)))
                MixPickBadge(t, Modifier.size(52.dp))
            }
        },
        trailingContent = {
            run {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(painterResource(R.drawable.ms_more_vert), contentDescription = "Меню трека")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (onAdd != null) {
                            DropdownMenuItem(
                                text = { Text("Добавить в плейлист") },
                                leadingIcon = { Icon(painterResource(R.drawable.ms_add), null) },
                                onClick = { menu = false; onAdd() },
                            )
                        }
                        MixPickMenuItem(t) { menu = false }
                        DropdownMenuItem(
                            text = { Text("Изменить название, исполнителя и обложку") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_edit), null) },
                            onClick = { menu = false; editLocal(t) },
                        )
                        DropdownMenuItem(
                            text = { Text("Поделиться файлом") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_share), null) },
                            onClick = { menu = false; ShareLinks.shareFile(rowCtx, t) },
                        )
                        if (onRemove != null) {
                            DropdownMenuItem(
                                text = { Text("Убрать из плейлиста") },
                                leadingIcon = { Icon(painterResource(R.drawable.ms_delete), null) },
                                onClick = { menu = false; onRemove() },
                            )
                        }
                    }
                }
            }
        },
        colors = MixPicker.rowColors(t),
        modifier = Modifier.padding(horizontal = 8.dp).clip(containerShape(24))
            .combinedClickable(
                onClick = { if (MixPicker.active) MixPicker.toggle(t) else onClick() },
                onLongClick = { MixPicker.toggle(t) },
            ),
    )
}

/** «Слушать» и «Вперемешку» в одну строку. */
@Composable
private fun DevicePlayRow(enabled: Boolean, onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val playSrc = remember { MutableInteractionSource() }
        val shuffleSrc = remember { MutableInteractionSource() }
        Button(
            onClick = onPlay, enabled = enabled,
            modifier = groupWeight(playSrc, listOf(shuffleSrc)).height(52.dp),
            interactionSource = playSrc,
        ) {
            Icon(painterResource(R.drawable.ms_play_arrow), null); Spacer(Modifier.width(6.dp)); FitText("Слушать")
        }
        FilledTonalButton(
            onClick = onShuffle, enabled = enabled,
            modifier = groupWeight(shuffleSrc, listOf(playSrc)).height(52.dp),
            interactionSource = shuffleSrc,
        ) {
            Icon(painterResource(R.drawable.ms_shuffle), null); Spacer(Modifier.width(6.dp)); FitText("Вперемешку")
        }
    }
}

/** Заголовок вкладки режима «Локальные файлы» с кнопкой возврата ко входу в Яндекс. */
@Composable
private fun DeviceHeader(title: String, subtitle: String, onExit: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
        IconButton(onClick = onExit) {
            Icon(painterResource(R.drawable.ms_person), contentDescription = "Войти в Яндекс Музыку")
        }
    }
}

@Composable
private fun DeviceSearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Поиск по файлам") },
        leadingIcon = { Icon(painterResource(R.drawable.ms_search), null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(painterResource(R.drawable.ms_close), contentDescription = "Очистить поиск")
                }
            }
        },
        shape = rounded(28),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Вкладка «Файлы»: вся музыка с устройства, поиск, «Слушать» / «Вперемешку», меню «Добавить в плейлист». */
@Composable
private fun LocalFilesTab(
    vm: AppViewModel,
    granted: Boolean,
    onRequest: () -> Unit,
    onAdd: (Track) -> Unit,
    onExit: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val shown = remember(state.localTracks, q) { state.localTracks.filterByQuery(q) }
    val keys = remember(shown) { trackKeys(shown) }
    Column(Modifier.fillMaxSize()) {
        DeviceHeader("Локальные файлы", "${state.localTracks.size} треков на устройстве", onExit)
        DeviceSearchField(query) { query = it }
        DevicePlayRow(
            enabled = shown.isNotEmpty(),
            onPlay = { vm.play(shown, 0) },
            onShuffle = { vm.play(shown, 0, shuffle = true) },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (!granted) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Нужен доступ к музыке на устройстве",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Разрешите доступ к аудиофайлам, чтобы увидеть и воспроизвести их здесь.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Разрешить доступ") }
                        TextButton(onClick = {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${ctx.packageName}"),
                                )
                            )
                        }) { Text("Открыть настройки приложения") }
                    }
                }
            } else if (state.localLoading && state.localTracks.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() } }
            } else if (shown.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (q.isEmpty()) "Музыки на устройстве не найдено" else "Ничего не нашлось",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexed(shown, key = { i, _ -> keys[i] }) { i, t ->
                DeviceTrackRow(t, onAdd = { onAdd(t) }) { vm.play(shown, i) }
            }
        }
    }
}

/** Вкладка «Плейлисты»: свои плейлисты из файлов устройства. */
@Composable
private fun LocalPlaylistsTab(
    vm: AppViewModel, onOpen: (DevicePlaylist) -> Unit, onCreate: () -> Unit, onFolder: () -> Unit, onExit: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val playlists = DevicePlaylists.items
    val known = remember(state.localTracks) { state.localTracks.mapTo(HashSet()) { it.id } }
    Column(Modifier.fillMaxSize()) {
        DeviceHeader("Плейлисты", "Из файлов вашего устройства", onExit) {
            IconButton(onClick = onFolder) {
                Icon(painterResource(R.drawable.ms_folder), contentDescription = "Плейлист из папки")
            }
            FilledTonalButton(onClick = onCreate) {
                Icon(painterResource(R.drawable.ms_add), null); Spacer(Modifier.width(6.dp)); Text("Создать")
            }
        }
        if (playlists.isEmpty()) {
            EmptyNote("Плейлистов пока нет. Создайте первый и добавьте в него треки с устройства") {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onCreate) { Text("Создать плейлист") }
                TextButton(onClick = onFolder) { Text("Из папки на устройстве") }
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
                items(playlists, key = { it.id }) { p ->
                    val count = when {
                        p.folder != null -> LocalFolders.tracksIn(state.localTracks, p.folder).size
                        known.isEmpty() -> p.trackIds.size
                        else -> p.trackIds.count { it in known }
                    }
                    ListItem(
                        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("$count треков") },
                        leadingContent = {
                            Box(
                                Modifier.size(52.dp).clip(rounded(14)).background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.ms_library_music), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(horizontal = 8.dp).clip(containerShape(24)).clickable { onOpen(p) },
                    )
                }
            }
        }
    }
}

/** Страница плейлиста из файлов устройства: слушать, добавить треки, переименовать, удалить. */
@Composable
private fun LocalPlaylistPage(vm: AppViewModel, pl: DevicePlaylist, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val byId = remember(state.localTracks) { state.localTracks.associateBy { it.id } }
    val tracks = remember(pl.trackIds, pl.folder, byId) { pl.resolve(byId, state.localTracks) }
    val keys = remember(tracks) { trackKeys(tracks) }
    var showPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SubPageBar(pl.name, onBack, subtitle = if (pl.folder != null) "${tracks.size} треков · ${pl.folder}" else "${tracks.size} треков")
        DevicePlayRow(
            enabled = tracks.isNotEmpty(),
            onPlay = { vm.play(tracks, 0) },
            onShuffle = { vm.play(tracks, 0, shuffle = true) },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pl.folder == null) {
                FilledTonalButton(onClick = { showPicker = true }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(painterResource(R.drawable.ms_add), null); Spacer(Modifier.width(6.dp)); FitText("Добавить треки")
                }
            } else Spacer(Modifier.weight(1f))
            IconButton(onClick = { showRename = true }) {
                Icon(painterResource(R.drawable.ms_edit), contentDescription = "Переименовать")
            }
            IconButton(onClick = { showDelete = true }) {
                Icon(painterResource(R.drawable.ms_delete), contentDescription = "Удалить плейлист")
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
            if (tracks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (pl.folder != null) "В папке пока нет музыки, которую видит система. Положите файлы в папку — они появятся здесь"
                            else "В плейлисте пока пусто. Нажмите «Добавить треки», чтобы собрать его из файлов устройства",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            itemsIndexed(tracks, key = { i, _ -> keys[i] }) { i, t ->
                DeviceTrackRow(t, onRemove = if (pl.folder == null) ({ DevicePlaylists.removeTrack(pl.id, t.id) }) else null) { vm.play(tracks, i) }
            }
        }
    }
    if (showPicker) {
        DeviceSongPicker(
            tracks = state.localTracks, already = pl.trackIds.toSet(),
            onDismiss = { showPicker = false },
            onAdd = { ids -> DevicePlaylists.addTracks(pl.id, ids); showPicker = false },
        )
    }
    if (showRename) {
        DevicePlaylistNameDialog(
            title = "Переименовать", initial = pl.name, confirm = "Сохранить",
            onDismiss = { showRename = false },
            onConfirm = { DevicePlaylists.rename(pl.id, it); showRename = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Удалить плейлист?") },
            text = { Text("«${pl.name}» будет удалён. Сами файлы на устройстве останутся.") },
            confirmButton = { TextButton(onClick = { DevicePlaylists.delete(pl.id); showDelete = false }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Отмена") } },
        )
    }
}

/** Окно выбора треков с устройства для плейлиста: галочки, поиск, кнопка «Добавить (N)». */
@Composable
private fun DeviceSongPicker(tracks: List<Track>, already: Set<String>, onDismiss: () -> Unit, onAdd: (List<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val q = query.trim()
    val list = remember(tracks, q) { tracks.filterByQuery(q) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                SubPageBar("Добавить треки", onDismiss, subtitle = if (picked.isEmpty()) "Отметьте нужные" else "Выбрано: ${picked.size}")
                DeviceSearchField(query) { query = it }
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(list, key = { it.id }) { t ->
                        val inList = t.id in already
                        ListItem(
                            headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    if (inList) "${t.artists} · уже в плейлисте" else t.artists,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingContent = { Cover(model = t.cover(200), modifier = Modifier.size(48.dp).clip(rounded(12))) },
                            trailingContent = { Checkbox(checked = inList || t.id in picked, onCheckedChange = null, enabled = !inList) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(enabled = !inList) {
                                picked = if (t.id in picked) picked - t.id else picked + t.id
                            },
                        )
                    }
                }
                Button(
                    onClick = { onAdd(picked.toList()) },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                ) { Text(if (picked.isEmpty()) "Добавить" else "Добавить (${picked.size})") }
            }
        }
    }
}

/** Выбор плейлиста для трека из меню «Добавить в плейлист» (или создание нового плейлиста с этим треком). */
@Composable
private fun DevicePlaylistChooser(track: Track, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        DevicePlaylistNameDialog(
            title = "Новый плейлист", initial = "", confirm = "Создать",
            onDismiss = { creating = false },
            onConfirm = { name ->
                val p = DevicePlaylists.create(name, track.id)
                AppToast.success("Добавлено в «${p.name}»")
                onDismiss()
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в плейлист") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("Новый плейлист") },
                        leadingContent = { Icon(painterResource(R.drawable.ms_add), null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(containerShape(16)).clickable { creating = true },
                    )
                }
                items(DevicePlaylists.items.filter { !it.isFolder }, key = { it.id }) { p ->
                    val has = track.id in p.trackIds
                    ListItem(
                        headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { if (has) Text("Уже добавлен") },
                        leadingContent = { Icon(painterResource(R.drawable.ms_library_music), null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clip(containerShape(16)).clickable(enabled = !has) {
                            DevicePlaylists.addTracks(p.id, listOf(track.id))
                            AppToast.success("Добавлено в «${p.name}»")
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/**
 * Плейлист из папки: системное окно выбора папки (внутренняя память или SD-карта), затем окно с названием —
 * по умолчанию это имя папки, его можно сразу поменять. Возвращает действие «открыть выбор папки».
 */
@Composable
private fun rememberFolderPlaylistPicker(onCreated: (DevicePlaylist) -> Unit): () -> Unit {
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = LocalFolders.pathFromTree(uri)
        if (path == null) AppToast.error("Не удалось определить папку. Выберите папку во внутренней памяти или на SD-карте")
        else pending = path
    }
    pending?.let { path ->
        DevicePlaylistNameDialog(
            title = "Плейлист из папки", initial = LocalFolders.displayName(path), confirm = "Создать",
            onDismiss = { pending = null },
            onConfirm = { name -> pending = null; onCreated(DevicePlaylists.createFromFolder(path, name)) },
            note = "Играют все треки из папки и её подпапок: $path",
        )
    }
    return { runCatching { launcher.launch(null) } }
}

/** Окно с полем «название» для создания и переименования плейлиста из файлов устройства. */
@Composable
private fun DevicePlaylistNameDialog(
    title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit,
    note: String? = null,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                placeholder = { Text("Название плейлиста") }, modifier = Modifier.fillMaxWidth(),
                supportingText = if (note != null) ({ Text(note) }) else null,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}


// ---------- Режим «Локальные файлы» в основном интерфейсе ----------
// Те же вкладки, док-панель, плеер и настройки, что и с аккаунтом. Вместо данных Яндекс Музыки — файлы телефона:
// альбомы, исполнители, имена файлов, свои плейлисты и поиск. Вход через Яндекс — в профиле и в настройках.

/** Доступ к аудиофайлам устройства: выдан ли он и как его запросить; предоставляется в AppRoot. */
private class AudioAccess(val granted: Boolean, val request: () -> Unit)

private val LocalAudioAccess = compositionLocalOf { AudioAccess(true) {} }

/**
 * Страницы режима «Локальные файлы», открытые поверх вкладок (стопкой): "files", "albums", "artists", "playlists",
 * "history", "album:<ключ>", "artist:<имя>", "pl:<id плейлиста>". Нажатие на вкладку в доке закрывает их все.
 */
private class DeviceNav {
    val stack = mutableStateListOf<String>()
    val top: String? get() = stack.lastOrNull()
    private fun detail(p: String?) = p != null && (p.startsWith("album:") || p.startsWith("artist:") || p.startsWith("pl:"))
    /** Страница альбома, исполнителя или плейлиста, открытая из такой же страницы, заменяет её, а не ложится сверху. */
    fun open(page: String) {
        val top = stack.lastOrNull()
        if (top == page) return
        if (detail(top) && detail(page)) stack[stack.lastIndex] = page else stack.add(page)
    }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun clear() { stack.clear() }
}

private val LocalDeviceNav = staticCompositionLocalOf { DeviceNav() }

/** Ключ в [UiState.playingSource], когда играют «Все файлы». */
private const val DEVICE_SOURCE = "device"

private fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    // Английский: только «один» и «много»; русский и белорусский — три формы. Слова переводятся словарём.
    if (AppLang.code == "en") return AppLang.tr(if (n == 1) one else many)
    return AppLang.tr(if (m10 == 1 && m100 != 11) one else if (m10 in 2..4 && m100 !in 12..14) few else many)
}

/** «1 234 567 слушателей в месяц» (число с неразрывными пробелами между разрядами). */
private fun listenersText(n: Long): String {
    val num = String.format(java.util.Locale.US, "%,d", n).replace(',', '\u00A0')
    // Форма слова зависит от последних цифр числа.
    val k = (n % 1_000_000_000L).toInt()
    return "$num " + plural(k, "слушатель", "слушателя", "слушателей") + " " + AppLang.tr("в месяц")
}

/** «1 234 подписчика»: у SoundCloud слушателей нет, только подписчики. */
private fun followersText(n: Long): String {
    val num = String.format(java.util.Locale.US, "%,d", n).replace(',', '\u00A0')
    return "$num " + plural((n % 1_000_000_000L).toInt(), "подписчик", "подписчика", "подписчиков")
}

private fun tracksWord(n: Int) = "$n " + plural(n, "трек", "трека", "треков")
private fun albumsWord(n: Int) = "$n " + plural(n, "альбом", "альбома", "альбомов")
private fun artistsWord(n: Int) = "$n " + plural(n, "исполнитель", "исполнителя", "исполнителей")

/** Поиск по файлам: название, исполнитель, альбом и имя файла. */
private fun List<Track>.filterFiles(q: String): List<Track> =
    if (q.isEmpty()) this else filter {
        it.title.contains(q, ignoreCase = true) || it.artists.contains(q, ignoreCase = true) ||
            (it.albumTitle?.contains(q, ignoreCase = true) == true) ||
            (it.fileName?.contains(q, ignoreCase = true) == true)
    }

/** Треки плейлиста из файлов устройства (те, что ещё лежат на телефоне), по порядку. */
private fun DevicePlaylist.resolve(byId: Map<String, Track>, all: List<Track>): List<Track> =
    if (folder != null) LocalFolders.tracksIn(all, folder) else trackIds.mapNotNull { byId[it] }

/** Блок «Нужен доступ к музыке»: кнопка запроса и переход в настройки приложения. */
@Composable
private fun DeviceAccessNotice() {
    val access = LocalAudioAccess.current
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Нужен доступ к музыке на устройстве",
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Разрешите доступ к аудиофайлам, чтобы увидеть и воспроизвести их здесь.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = access.request, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Разрешить доступ") }
        TextButton(onClick = {
            ctx.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${ctx.packageName}"),
                )
            )
        }) { Text("Открыть настройки приложения") }
    }
}

/** Пустой список или загрузка под шапкой: «читаем файлы», «ничего не найдено» или просьба о доступе. */
private fun LazyListScope.deviceStatusItems(loading: Boolean, empty: Boolean, granted: Boolean, emptyText: String) {
    if (!granted) {
        item(key = "dev_access") { DeviceAccessNotice() }
    } else if (loading && empty) {
        item(key = "dev_loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        }
    } else if (empty) {
        item(key = "dev_empty") {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/**
 * Строка трека из файлов устройства: обложка, название, «исполнитель · альбом» и имя файла. Меню: добавить в плейлист,
 * перейти к исполнителю и к альбому, убрать из плейлиста (если строка внутри плейлиста).
 */
@Composable
private fun DeviceRow(t: Track, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null, onClick: () -> Unit) {
    val nav = LocalDeviceNav.current
    val addToPlaylist = LocalAddToPlaylist.current
    val editLocal = LocalEditLocalTrack.current
    val rowCtx = LocalContext.current
    ListItem(
        headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(t.artists, t.albumTitle).joinToString(" · "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                t.fileName?.let { f ->
                    Text(
                        f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        },
        leadingContent = { Cover(model = t.cover(200), modifier = Modifier.size(52.dp).clip(rounded(14))) },
        trailingContent = {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ms_more_vert), contentDescription = "Меню трека")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Добавить в плейлист") },
                        leadingIcon = { Icon(painterResource(R.drawable.ms_add), null) },
                        onClick = { menu = false; addToPlaylist(t) },
                    )
                    DropdownMenuItem(
                        text = { Text("К исполнителю") },
                        leadingIcon = { Icon(painterResource(R.drawable.ms_person), null) },
                        onClick = { menu = false; nav.open("artist:" + t.artists) },
                    )
                    DropdownMenuItem(
                        text = { Text("К альбому") },
                        leadingIcon = { Icon(painterResource(R.drawable.ms_library_music), null) },
                        onClick = { menu = false; nav.open("album:" + t.localAlbumKey()) },
                    )
                    DropdownMenuItem(
                        text = { Text("Изменить название, исполнителя и обложку") },
                        leadingIcon = { Icon(painterResource(R.drawable.ms_edit), null) },
                        onClick = { menu = false; editLocal(t) },
                    )
                    DropdownMenuItem(
                        text = { Text("Поделиться файлом") },
                        leadingIcon = { Icon(painterResource(R.drawable.ms_share), null) },
                        onClick = { menu = false; ShareLinks.shareFile(rowCtx, t) },
                    )
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Убрать из плейлиста") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_delete), null) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.padding(horizontal = 8.dp).clip(containerShape(24)).clickable(onClick = onClick),
    )
}

/** Карточка плейлиста из файлов устройства — как карточка плейлиста Яндекс Музыки. */
@Composable
private fun DevicePlaylistCard(
    name: String,
    count: Int,
    cover: String?,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Column(
        modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier.width(140.dp))
            .pressCard(source, onClick, onLongClick),
    ) {
        Cover(
            model = cover,
            placeholder = R.drawable.ms_library_music,
            modifier = (if (fill) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(140.dp))
                .clip(rounded(28))
                .indication(source, LocalIndication.current),
        )
        FitText(
            name, maxLines = 2,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            tracksWord(count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

/** Верхний блок главной в режиме «Локальные файлы»: сколько музыки на телефоне и кнопки «Слушать» / «Вперемешку». */
@Composable
private fun DeviceHero(
    tracks: Int,
    albums: Int,
    artists: Int,
    playing: Boolean,
    searchInCorner: Boolean,
    onSearch: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Surface(
        shape = rounded(32),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .playGlow(
                active = playing, enabled = ThemeSettings.glowPlaylists,
                color = MaterialTheme.colorScheme.primary, cornerRadius = 32.dp, spread = 14.dp,
                shape = rounded(32),
            ),
    ) {
        Column(Modifier.padding(start = 20.dp + shapeInset(), end = 12.dp + shapeInset(), top = 12.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text("Моя музыка", style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(
                        listOf(tracksWord(tracks), albumsWord(albums), artistsWord(artists)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (searchInCorner) {
                    IconButton(onClick = onSearch) {
                        Icon(painterResource(R.drawable.ms_search), contentDescription = "Поиск")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val playSrc = remember { MutableInteractionSource() }
                val shuffleSrc = remember { MutableInteractionSource() }
                Button(
                    onClick = onPlay, enabled = tracks > 0,
                    modifier = groupWeight(playSrc, listOf(shuffleSrc)).height(52.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    interactionSource = playSrc,
                ) {
                    Icon(painterResource(R.drawable.ms_play_arrow), null); Spacer(Modifier.width(6.dp)); FitText("Слушать")
                }
                FilledTonalButton(
                    onClick = onShuffle, enabled = tracks > 0,
                    modifier = groupWeight(shuffleSrc, listOf(playSrc)).height(52.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    interactionSource = shuffleSrc,
                ) {
                    Icon(painterResource(R.drawable.ms_shuffle), null); Spacer(Modifier.width(6.dp)); FitText("Вперемешку")
                }
            }
        }
    }
}

// ---- Главная ----

@Composable
private fun LocalHomeScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val access = LocalAudioAccess.current
    val openSearch = LocalOpenSearch.current
    var showRecognize by remember { mutableStateOf(false) }
    val lib = state.localLib
    val tracks = state.localTracks
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val playlists = DevicePlaylists.items
    val recent = remember(state.history) { state.history.filter { it.isLocal } }
    val searchInCorner = NavSettings.homeSearch && NavSettings.homeSearchCorner
    PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "hero") {
            DeviceHero(
                tracks = tracks.size, albums = lib.albums.size, artists = lib.artists.size,
                playing = state.isPlaying && state.playingSource == DEVICE_SOURCE,
                searchInCorner = searchInCorner, onSearch = openSearch,
                onPlay = { vm.play(tracks, 0, source = DEVICE_SOURCE) },
                onShuffle = { vm.play(tracks, 0, shuffle = true, source = DEVICE_SOURCE) },
            )
        }
        if (NavSettings.homeSearch && !NavSettings.homeSearchCorner) {
            item(key = "search_tile") {
                HomeTile(
                    title = "Поиск", subtitle = "Треки, альбомы, исполнители, файлы", icon = R.drawable.ms_search,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    onClick = openSearch,
                )
            }
        }
        deviceStatusItems(state.localLoading, tracks.isEmpty(), access.granted, "Музыки на устройстве не найдено")
        if (playlists.isNotEmpty()) {
            item(key = "pl_title") { SectionLink("Плейлисты") { nav.open("playlists") } }
            item(key = "pl_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(playlists, key = { it.id }) { p ->
                        val list = p.resolve(byId, state.localTracks)
                        DevicePlaylistCard(p.name, list.size, list.firstNotNullOfOrNull { it.coverUri }) { nav.open("pl:" + p.id) }
                    }
                }
            }
        }
        item(key = "recognize_tile") {
            HomeTile(
                title = "Распознать музыку", subtitle = "С микрофона или со звука устройства", icon = R.drawable.ms_mic,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) { showRecognize = true }
        }
        item(key = "album_artist_tiles") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeTile(
                    title = "Альбомы", subtitle = lib.albums.size.toString(), icon = R.drawable.ms_library_music,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                ) { nav.open("albums") }
                HomeTile(
                    title = "Исполнители", subtitle = lib.artists.size.toString(), icon = R.drawable.ms_person,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                ) { nav.open("artists") }
            }
        }
        item(key = "files_history_tiles") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HomeTile(
                    title = "Все файлы", icon = R.drawable.ms_folder,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                ) { nav.open("files") }
                HomeTile(
                    title = "История", icon = R.drawable.ms_schedule,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                ) { nav.open("history") }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent_title") { SectionLink("Недавно играли") { nav.open("history") } }
            val shown = recent.take(6)
            itemsIndexed(shown, key = { i, t -> "r_${t.id}_$i" }) { i, t ->
                DeviceRow(t) { vm.play(recent, i) }
            }
        }
        if (lib.albums.isNotEmpty()) {
            item(key = "albums_title") { SectionLink("Альбомы", count = lib.albums.size) { nav.open("albums") } }
            item(key = "albums_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lib.albums.take(14), key = { it.key }) { a -> AlbumCard(a.info()) { nav.open("album:" + a.key) } }
                }
            }
        }
        if (lib.artists.isNotEmpty()) {
            item(key = "artists_title") { SectionLink("Исполнители", count = lib.artists.size) { nav.open("artists") } }
            item(key = "artists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lib.artists.take(14), key = { it.name }) { a -> ArtistCard(a.info()) { nav.open("artist:" + a.name) } }
                }
            }
        }
        if (tracks.isNotEmpty()) {
            item(key = "files_title") { SectionLink("Все файлы", count = tracks.size) { nav.open("files") } }
            val shown = tracks.take(5)
            itemsIndexed(shown, key = { _, t -> "f_" + t.id }) { _, t ->
                DeviceRow(t) { vm.play(tracks, tracks.indexOf(t), source = DEVICE_SOURCE) }
            }
        }
    }
    if (showRecognize) {
        ModalBottomSheet(onDismissRequest = { showRecognize = false }) {
            RecognitionContent(
                onClose = { showRecognize = false },
                inApp = true,
                onOpenInApp = { r -> showRecognize = false; vm.requestRecognizedSearch(r.query); Recognizer.reset() },
            )
        }
    }
}

// ---- Плейлисты ----

@Composable
private fun LocalPlaylistsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val access = LocalAudioAccess.current
    val lib = state.localLib
    val tracks = state.localTracks
    val byId = remember(tracks) { tracks.associateBy { it.id } }
    val playlists = DevicePlaylists.items
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DevicePlaylist?>(null) }
    val pickFolder = rememberFolderPlaylistPicker { p -> nav.open("pl:" + p.id) }
    PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) {
        item(key = "all_files") {
            val shape = rounded(32)
            val active = state.playingSource == DEVICE_SOURCE
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).appear(0)
                    .playGlow(
                        active = LocalPlayingSource.current == DEVICE_SOURCE, enabled = ThemeSettings.glowPlaylists,
                        color = MaterialTheme.colorScheme.primary, cornerRadius = 32.dp, spread = 14.dp,
                        shape = shape,
                    )
                    .pressable(shape) { nav.open("files") },
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp + shapeInset(), vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Все файлы",
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            tracksWord(tracks.size),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    FilledIconButton(
                        onClick = { if (active) vm.togglePlay() else vm.play(tracks, 0, source = DEVICE_SOURCE) },
                        enabled = tracks.isNotEmpty(),
                        haptic = HapticKind.Heavy,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painterResource(if (active && state.isPlaying) R.drawable.ms_pause else R.drawable.ms_play_arrow),
                            contentDescription = if (active && state.isPlaying) "Пауза" else "Играть все файлы",
                        )
                    }
                }
            }
        }
        item(key = "create") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp).appear(1),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { showCreate = true },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ms_add), null)
                    Spacer(Modifier.width(8.dp)); FitText("Новый плейлист")
                }
                FilledTonalButton(
                    onClick = pickFolder,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ms_folder), null)
                    Spacer(Modifier.width(8.dp)); FitText("Из папки")
                }
            }
        }
        deviceStatusItems(state.localLoading, tracks.isEmpty(), access.granted, "Музыки на устройстве не найдено")

        // ---- Плейлисты ----
        item(key = "playlists_title") {
            Box(Modifier.padding(top = 8.dp)) { SectionLink("Плейлисты") { nav.open("playlists") } }
        }
        if (playlists.isEmpty()) {
            item(key = "playlists_empty") {
                Text(
                    "Плейлистов пока нет. Создайте первый и добавьте в него треки с устройства",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            item(key = "playlists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(playlists, key = { it.id }) { p ->
                        val list = p.resolve(byId, state.localTracks)
                        DevicePlaylistCard(
                            p.name, list.size, list.firstNotNullOfOrNull { it.coverUri },
                            onLongClick = { deleteTarget = p },
                        ) { nav.open("pl:" + p.id) }
                    }
                }
            }
        }

        // ---- Альбомы ----
        item(key = "albums_title") { SectionLink("Альбомы", count = lib.albums.size.takeIf { it > 0 }) { nav.open("albums") } }
        if (lib.albums.isEmpty()) {
            item(key = "albums_empty") {
                Text(
                    "Здесь появятся альбомы из файлов на телефоне",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            item(key = "albums_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lib.albums.take(20), key = { it.key }) { a -> AlbumCard(a.info()) { nav.open("album:" + a.key) } }
                }
            }
        }

        // ---- Исполнители ----
        item(key = "artists_title") { SectionLink("Исполнители", count = lib.artists.size.takeIf { it > 0 }) { nav.open("artists") } }
        if (lib.artists.isEmpty()) {
            item(key = "artists_empty") {
                Text(
                    "Здесь появятся исполнители из файлов на телефоне",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            item(key = "artists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(lib.artists.take(20), key = { it.name }) { a -> ArtistCard(a.info()) { nav.open("artist:" + a.name) } }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить плейлист?") },
            text = { Text("«${target.name}» будет удалён. Сами файлы на устройстве останутся.") },
            confirmButton = { TextButton(onClick = { DevicePlaylists.delete(target.id); deleteTarget = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
    if (showCreate) {
        DevicePlaylistNameDialog(
            title = "Новый плейлист", initial = "", confirm = "Создать",
            onDismiss = { showCreate = false },
            onConfirm = { name -> val p = DevicePlaylists.create(name); showCreate = false; nav.open("pl:" + p.id) },
        )
    }
}

// ---- Поиск ----

@Composable
private fun LocalSearchScreen(vm: AppViewModel, active: Boolean = true) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val access = LocalAudioAccess.current
    var query by rememberSaveable { mutableStateOf("") }
    // Запрос из распознавания музыки: подставляем в поле.
    val searchRequest by vm.searchRequest.collectAsStateWithLifecycle()
    LaunchedEffect(searchRequest) {
        searchRequest?.let { r -> query = r; vm.consumeSearchRequest() }
    }
    // Поиск идёт по уже прочитанным файлам, с небольшой задержкой после последней буквы.
    var q by remember { mutableStateOf(query.trim()) }
    LaunchedEffect(query) { delay(150); q = query.trim() }
    val lib = state.localLib
    val tracksFound = remember(q, state.localTracks) { if (q.isEmpty()) emptyList() else state.localTracks.filterFiles(q) }
    val artistsFound = remember(q, lib) {
        if (q.isEmpty()) emptyList() else lib.artists.filter { it.name.contains(q, ignoreCase = true) }
    }
    val albumsFound = remember(q, lib) {
        if (q.isEmpty()) emptyList()
        else lib.albums.filter { it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) }
    }
    val byId = remember(state.localTracks) { state.localTracks.associateBy { it.id } }
    val playlistsFound = if (q.isEmpty()) emptyList() else DevicePlaylists.items.filter { it.name.contains(q, ignoreCase = true) }

    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val hideKeyboard: () -> Unit = { keyboard?.hide(); focusManager.clearFocus() }
    val autoKeyboard = ThemeSettings.searchAutoKeyboard
    val activeNow by rememberUpdatedState(active)
    LaunchedEffect(autoKeyboard, active) {
        if (autoKeyboard && active) {
            delay(120)
            if (!activeNow) return@LaunchedEffect
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(active) {
        if (!active) { keyboard?.hide(); focusManager.clearFocus() }
    }
    // Кнопка поиска на главной: ставим курсор в поле и открываем клавиатуру.
    val focusPending by vm.searchFocusRequest.collectAsStateWithLifecycle()
    LaunchedEffect(focusPending, active) {
        if (focusPending && active) {
            delay(150)
            if (!activeNow) return@LaunchedEffect
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            vm.consumeSearchFocus()
        }
    }
    // Прокрутили список — клавиатура прячется.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12 }
            .distinctUntilChanged()
            .collect { scrolled -> if (scrolled) { keyboard?.hide(); focusManager.clearFocus() } }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Треки, альбомы, исполнители, файлы") },
            leadingIcon = { Icon(painterResource(R.drawable.ms_search), null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ms_close), contentDescription = "Очистить поиск")
                    }
                }
            },
            singleLine = true,
            shape = rounded(32),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { SearchHistory.add(query); keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                .focusProperties { canFocus = active }
                .focusRequester(focusRequester),
        )
        PullLazyColumn(
            vm,
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (!access.granted) item(key = "dev_access") { DeviceAccessNotice() }
            if (query.isBlank()) {
                if (SearchHistory.enabled && SearchHistory.items.isNotEmpty()) {
                    item(key = "search_history_head") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("История поиска", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                            TextButton(onClick = { SearchHistory.clear() }) { Text("Очистить") }
                        }
                    }
                    items(SearchHistory.items, key = { "sh_$it" }) { h ->
                        SearchHistoryRow(
                            h,
                            modifier = removeAnim(),
                            onClick = { query = h; SearchHistory.add(h); hideKeyboard() },
                            onRemove = { SearchHistory.remove(h) },
                        )
                    }
                } else if (access.granted) {
                    item(key = "search_hint") {
                        Text(
                            "Ищите по названию трека, исполнителю, альбому, плейлисту или имени файла",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                if (artistsFound.isNotEmpty()) {
                    item(key = "res_artists_title") { SectionTitle("Исполнители") }
                    item(key = "res_artists") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(artistsFound.take(12), key = { it.name }) { a ->
                                ArtistCard(a.info()) { hideKeyboard(); SearchHistory.add(query); nav.open("artist:" + a.name) }
                            }
                        }
                    }
                }
                if (albumsFound.isNotEmpty()) {
                    item(key = "res_albums_title") { SectionTitle("Альбомы") }
                    item(key = "res_albums") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(albumsFound.take(14), key = { it.key }) { a ->
                                AlbumCard(a.info()) { hideKeyboard(); SearchHistory.add(query); nav.open("album:" + a.key) }
                            }
                        }
                    }
                }
                if (playlistsFound.isNotEmpty()) {
                    item(key = "res_playlists_title") { SectionTitle("Плейлисты") }
                    item(key = "res_playlists") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(playlistsFound, key = { it.id }) { p ->
                                val list = p.resolve(byId, state.localTracks)
                                DevicePlaylistCard(p.name, list.size, list.firstNotNullOfOrNull { it.coverUri }) {
                                    hideKeyboard(); SearchHistory.add(query); nav.open("pl:" + p.id)
                                }
                            }
                        }
                    }
                }
                if (tracksFound.isNotEmpty()) {
                    if (artistsFound.isNotEmpty() || albumsFound.isNotEmpty() || playlistsFound.isNotEmpty()) item(key = "res_tracks_title") { SectionTitle("Треки") }
                    val keys = trackKeys(tracksFound)
                    itemsIndexed(tracksFound, key = { i, _ -> "res_" + keys[i] }) { i, t ->
                        DeviceRow(t) { hideKeyboard(); SearchHistory.add(query); vm.play(tracksFound, i) }
                    }
                }
                if (artistsFound.isEmpty() && albumsFound.isEmpty() && playlistsFound.isEmpty() && tracksFound.isEmpty() &&
                    q == query.trim() && access.granted
                ) {
                    item(key = "nothing_found") {
                        Text(
                            "Ничего не нашлось",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---- Профиль ----

@Composable
private fun DeviceStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = rounded(20), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.titleLargeEmphasized)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun LocalProfileScreen(vm: AppViewModel, onOpenSettings: () -> Unit, onLogin: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val access = LocalAudioAccess.current
    val profileScroll = rememberScrollState()
    ScrollToTopEffect { profileScroll.smoothToTop() }
    ReportScroll(profileScroll)
    val wide = LocalWide.current
    val profilePane: @Composable () -> Unit = {
    Box(Modifier.fillMaxSize()) {
    Refreshable(state.refreshing, { vm.pullRefresh() }) {
    Column(
        Modifier.fillMaxSize().verticalScroll(profileScroll)
            .padding(start = 24.dp, end = 24.dp, top = if (wide) 24.dp else 56.dp, bottom = 24.dp + dockInset()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = MaterialShapes.Cookie9Sided.toShape(), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ms_smartphone), null, Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Локальные файлы", style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(
            "Музыка с вашего телефона",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceStat(state.localTracks.size, "треков", Modifier.weight(1f))
            DeviceStat(state.localLib.albums.size, "альбомов", Modifier.weight(1f))
            DeviceStat(state.localLib.artists.size, "исполнителей", Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = rounded(28),
        ) {
            Icon(painterResource(R.drawable.ms_person), null); Spacer(Modifier.width(8.dp)); Text("Войти через Яндекс")
        }
        Text(
            "«Любимое», плейлисты и «Моя волна» из Яндекс Музыки. Ваши плейлисты из файлов сохранятся",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        FilledTonalButton(onClick = { vm.refresh() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(painterResource(R.drawable.ms_refresh), null); Spacer(Modifier.width(8.dp)); Text("Обновить список файлов")
        }
        if (!access.granted) DeviceAccessNotice()
    }
    }
    if (!wide) IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
    ) {
        Icon(painterResource(R.drawable.ms_settings), contentDescription = "Настройки")
    }
    }
    }
    if (wide) {
        TwoPane(
            left = {
                Surface(
                    shape = rounded(32), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
                ) { profilePane() }
            },
            right = { SettingsScreen(vm, local = true, onLogin = onLogin, embedded = true, onClose = {}) },
        )
    } else profilePane()
}

// ---- Страницы поверх вкладок ----

/** Нужная страница режима «Локальные файлы» по её ключу (см. [DeviceNav]). */
@Composable
private fun DevicePageHost(vm: AppViewModel, page: String) {
    when {
        page == "files" -> DeviceFilesPage(vm)
        page == "albums" -> DeviceAlbumsPage(vm)
        page == "artists" -> DeviceArtistsPage(vm)
        page == "playlists" -> DevicePlaylistsPage(vm)
        page == "history" -> DeviceHistoryPage(vm)
        page.startsWith("album:") -> DeviceAlbumPage(vm, page.removePrefix("album:"))
        page.startsWith("artist:") -> DeviceArtistPage(vm, page.removePrefix("artist:"))
        page.startsWith("pl:") -> DevicePlaylistPage(vm, page.removePrefix("pl:"))
    }
}

/** Все файлы с устройства: слушать, вперемешку, поиск по названию, исполнителю, альбому и имени файла. */
@Composable
private fun DeviceFilesPage(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val access = LocalAudioAccess.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val shown = remember(state.localTracks, q) { state.localTracks.filterFiles(q) }
    val keys = remember(shown) { trackKeys(shown) }
    val src = if (q.isEmpty()) DEVICE_SOURCE else null
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        SplitPullColumn(vm, Modifier.fillMaxSize(), header = {
            item {
                PlaylistHeader(
                    title = "Все файлы",
                    count = state.localTracks.size,
                    cover = null,
                    onEdit = null,
                    placeholderIcon = R.drawable.ms_folder,
                    countText = tracksWord(state.localTracks.size) + " на устройстве",
                )
            }
            item {
                PlayButtonsRow(
                    enabled = shown.isNotEmpty(),
                    onBack = { nav.back() },
                    onPlay = { vm.play(shown, 0, source = src) },
                    onShuffle = { vm.play(shown, 0, shuffle = true, source = src) },
                )
            }
            item { DeviceSearchField(query) { query = it } }
        }) {
            deviceStatusItems(
                state.localLoading, shown.isEmpty(), access.granted,
                if (q.isEmpty()) "Музыки на устройстве не найдено" else "Ничего не нашлось",
            )
            itemsIndexed(shown, key = { i, _ -> keys[i] }) { i, t ->
                DeviceRow(t, modifier = removeAnim().appear(if (i < 12) i else -1)) { vm.play(shown, i, source = src) }
            }
        }
    }
}

/** Все альбомы из файлов устройства сеткой. */
@Composable
private fun DeviceAlbumsPage(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val albums = state.localLib.albums
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("Альбомы", { nav.back() }, subtitle = albumsWord(albums.size))
            PullLazyGrid(
                vm,
                columns = AlbumGridCells,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (albums.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("Альбомов пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                gridItems(albums, key = { it.key }) { a ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        AlbumCard(a.info(), width = maxWidth) { nav.open("album:" + a.key) }
                    }
                }
            }
        }
    }
}

/** Все исполнители из файлов устройства сеткой. */
@Composable
private fun DeviceArtistsPage(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val artists = state.localLib.artists
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("Исполнители", { nav.back() }, subtitle = artistsWord(artists.size))
            PullLazyGrid(
                vm,
                columns = GridCells.Adaptive(112.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (artists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("Исполнителей пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                gridItems(artists, key = { it.name }) { a ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        ArtistCard(a.info()) { nav.open("artist:" + a.name) }
                    }
                }
            }
        }
    }
}

/** Все плейлисты из файлов устройства сеткой. */
@Composable
private fun DevicePlaylistsPage(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val byId = remember(state.localTracks) { state.localTracks.associateBy { it.id } }
    val playlists = DevicePlaylists.items
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DevicePlaylist?>(null) }
    val pickFolder = rememberFolderPlaylistPicker { p -> nav.open("pl:" + p.id) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("Плейлисты", { nav.back() }, subtitle = "${playlists.size} шт.")
            PullLazyGrid(
                vm,
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { showCreate = true },
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Icon(painterResource(R.drawable.ms_add), null)
                            Spacer(Modifier.width(8.dp)); FitText("Новый плейлист")
                        }
                        FilledTonalButton(
                            onClick = pickFolder,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Icon(painterResource(R.drawable.ms_folder), null)
                            Spacer(Modifier.width(8.dp)); FitText("Из папки")
                        }
                    }
                }
                if (playlists.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("Плейлистов пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                gridItems(playlists, key = { it.id }) { p ->
                    val list = p.resolve(byId, state.localTracks)
                    DevicePlaylistCard(
                        p.name, list.size, list.firstNotNullOfOrNull { it.coverUri },
                        modifier = removeAnim(), fill = true,
                        onLongClick = { deleteTarget = p },
                    ) { nav.open("pl:" + p.id) }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить плейлист?") },
            text = { Text("«${target.name}» будет удалён. Сами файлы на устройстве останутся.") },
            confirmButton = { TextButton(onClick = { DevicePlaylists.delete(target.id); deleteTarget = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } },
        )
    }
    if (showCreate) {
        DevicePlaylistNameDialog(
            title = "Новый плейлист", initial = "", confirm = "Создать",
            onDismiss = { showCreate = false },
            onConfirm = { name -> val p = DevicePlaylists.create(name); showCreate = false; nav.open("pl:" + p.id) },
        )
    }
}

/** История прослушанных файлов. */
@Composable
private fun DeviceHistoryPage(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val recent = remember(state.history) { state.history.filter { it.isLocal } }
    val keys = remember(recent) { trackKeys(recent) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("История", { nav.back() }, subtitle = tracksWord(recent.size))
            PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (recent.isNotEmpty()) {
                    item {
                        DevicePlayRow(
                            enabled = true,
                            onPlay = { vm.play(recent, 0) },
                            onShuffle = { vm.play(recent, 0, shuffle = true) },
                        )
                    }
                } else {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Вы ещё ничего не слушали", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                itemsIndexed(recent, key = { i, _ -> keys[i] }) { i, t ->
                    DeviceRow(t, modifier = removeAnim()) { vm.play(recent, i) }
                }
            }
        }
    }
}

/** Альбом из файлов устройства: обложка, исполнитель, год, треки по порядку номеров и имена файлов. */
@Composable
private fun DeviceAlbumPage(vm: AppViewModel, key: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val album = state.localLib.album(key)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (album == null) {
            Column(Modifier.fillMaxSize()) {
                SubPageBar("Альбом", { nav.back() })
                if (state.localLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else {
                    EmptyNote("Альбом не найден") {}
                }
            }
        } else {
            val tracks = album.tracks
            val keys = remember(tracks) { trackKeys(tracks) }
            val artistKnown = state.localLib.artist(album.artist) != null
            SplitPullColumn(vm, Modifier.fillMaxSize(), header = {
                item {
                    PlaylistHeader(
                        title = album.title,
                        count = tracks.size,
                        cover = album.coverUri,
                        onEdit = null,
                        subtitle = album.artist,
                        countText = listOfNotNull(album.year.takeIf { it > 0 }?.toString(), tracksWord(tracks.size)).joinToString(" · "),
                        placeholderIcon = R.drawable.ms_library_music,
                        onSubtitleClick = if (artistKnown) ({ nav.open("artist:" + album.artist) }) else null,
                    )
                }
                item {
                    PlayButtonsRow(
                        enabled = tracks.isNotEmpty(),
                        onBack = { nav.back() },
                        onPlay = { vm.play(tracks, 0) },
                        onShuffle = { vm.play(tracks, 0, shuffle = true) },
                    )
                }
            }) {
                itemsIndexed(tracks, key = { i, _ -> keys[i] }) { i, t ->
                    DeviceRow(t, modifier = removeAnim().appear(if (i < 12) i else -1)) { vm.play(tracks, i) }
                }
            }
        }
    }
}

/** Исполнитель из файлов устройства: его альбомы и все треки с именами файлов. */
@Composable
private fun DeviceArtistPage(vm: AppViewModel, name: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val artist = state.localLib.artist(name)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (artist == null) {
            Column(Modifier.fillMaxSize()) {
                SubPageBar("Исполнитель", { nav.back() })
                if (state.localLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else {
                    EmptyNote("Исполнитель не найден") {}
                }
            }
        } else {
            val tracks = artist.tracks
            val keys = remember(tracks) { trackKeys(tracks) }
            SplitPullColumn(vm, Modifier.fillMaxSize(), header = {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Cover(
                                model = artist.coverUri,
                                placeholder = R.drawable.ms_person,
                                modifier = Modifier.size(if (LocalInSplitPane.current) 200.dp else 160.dp).clip(ThemeSettings.avatarShape.asShape()),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.headlineMediumEmphasized,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Text(
                                listOf(tracksWord(tracks.size), albumsWord(artist.albums.size)).joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        PlayButtonsRow(
                            enabled = tracks.isNotEmpty(),
                            onBack = { nav.back() },
                            onPlay = { vm.play(tracks, 0) },
                            onShuffle = { vm.play(tracks, 0, shuffle = true) },
                        )
                    }
                }
            }) {
                if (artist.albums.isNotEmpty()) {
                    item { SectionTitle("Альбомы") }
                    item {
                        // Три карточки в ряд, остальные листаются — как на странице артиста Яндекс Музыки.
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val cardW = ((maxWidth - 32.dp - 24.dp) / 3).coerceAtLeast(96.dp)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(artist.albums, key = { it.key }) { a ->
                                    AlbumCard(a.info(), width = cardW) { nav.open("album:" + a.key) }
                                }
                            }
                        }
                    }
                }
                item { SectionTitle("Треки") }
                itemsIndexed(tracks, key = { i, _ -> keys[i] }) { i, t ->
                    DeviceRow(t, modifier = removeAnim().appear(if (i < 12) i else -1)) { vm.play(tracks, i) }
                }
            }
        }
    }
}

/** Плейлист из файлов устройства: слушать, добавить треки, переименовать (нажатие на название или обложку), удалить. */
@Composable
private fun DevicePlaylistPage(vm: AppViewModel, id: String) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = LocalDeviceNav.current
    val pl = DevicePlaylists.items.firstOrNull { it.id == id }
    // Плейлист удалили — страница закрывается сама.
    LaunchedEffect(pl == null) { if (pl == null) nav.back() }
    if (pl == null) return
    val byId = remember(state.localTracks) { state.localTracks.associateBy { it.id } }
    val tracks = remember(pl.trackIds, pl.folder, byId) { pl.resolve(byId, state.localTracks) }
    val keys = remember(tracks) { trackKeys(tracks) }
    var showPicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        SplitPullColumn(vm, Modifier.fillMaxSize(), header = {
            item {
                PlaylistHeader(
                    title = pl.name,
                    count = tracks.size,
                    cover = tracks.firstNotNullOfOrNull { it.coverUri },
                    onEdit = null,
                    placeholderIcon = R.drawable.ms_library_music,
                    countText = tracksWord(tracks.size),
                    subtitle = pl.folder,
                )
            }
            // Кнопки без подписей, в один-два ряда.
            item(key = "device_pl_actions") {
                IconActionRows(
                    listOfNotNull(
                        PageAction(R.drawable.ms_arrow_back, "Назад") { nav.back() },
                        PageAction(R.drawable.ms_play_arrow, "Слушать", kind = 1, enabled = tracks.isNotEmpty()) { vm.play(tracks, 0) },
                        PageAction(R.drawable.ms_shuffle, "Вперемешку", enabled = tracks.isNotEmpty()) { vm.play(tracks, 0, shuffle = true) },
                        if (pl.folder == null) PageAction(R.drawable.ms_add, "Добавить треки") { showPicker = true } else null,
                        PageAction(R.drawable.ms_edit, "Редактировать плейлист") { showRename = true },
                        PageAction(R.drawable.ms_delete, "Удалить плейлист", kind = 2) { showDelete = true },
                    ),
                )
            }
        }) {
            if (tracks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (pl.folder != null) "В папке пока нет музыки, которую видит система. Положите файлы в папку — они появятся здесь"
                            else "В плейлисте пока пусто. Нажмите «Добавить треки», чтобы собрать его из файлов устройства",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
            itemsIndexed(tracks, key = { i, _ -> keys[i] }) { i, t ->
                DeviceRow(
                    t,
                    modifier = removeAnim().appear(if (i < 12) i else -1),
                    onRemove = if (pl.folder == null) ({ DevicePlaylists.removeTrack(pl.id, t.id) }) else null,
                ) { vm.play(tracks, i) }
            }
        }
    }
    if (showPicker) {
        DeviceSongPicker(
            tracks = state.localTracks, already = pl.trackIds.toSet(),
            onDismiss = { showPicker = false },
            onAdd = { ids -> DevicePlaylists.addTracks(pl.id, ids); showPicker = false },
        )
    }
    if (showRename) {
        DevicePlaylistNameDialog(
            title = "Переименовать", initial = pl.name, confirm = "Сохранить",
            onDismiss = { showRename = false },
            onConfirm = { DevicePlaylists.rename(pl.id, it); showRename = false },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Удалить плейлист?") },
            text = { Text("«${pl.name}» будет удалён. Сами файлы на устройстве останутся.") },
            confirmButton = { TextButton(onClick = { DevicePlaylists.delete(pl.id); showDelete = false }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Сервис активного аккаунта (Яндекс / VK): от него зависят подписи и доступные кнопки.
    val activeProvider = state.provider
    SideEffect {
        ActiveProvider.value = activeProvider
        ActiveProvider.caps = vm.apiCaps()
    }
    // Планшетная раскладка (боковая панель, две колонки): окно шире 600 dp. Настраивается в Настройки → Внешний вид → Планшеты.
    val wide = rememberIsWide()
    val compactH = rememberIsCompactHeight()
    // Режим «Локальные файлы»: включается кнопкой «Пропустить» на экране входа. Тот же интерфейс (вкладки, док, плеер,
    // настройки), но вместо Яндекс Музыки — музыка с телефона; войти в Яндекс можно в профиле и в настройках.
    val local = !state.loggedIn && LocalModeSettings.enabled
    if (!state.loggedIn && !local) {
        LoginScreen(
            vm = vm,
            error = state.error,
            onToken = { vm.onTokenReceived(it) },
            onSkip = { LocalModeSettings.updateEnabled(true); vm.loadLocalHistory() },
        )
        return
    }
    // Вошли в Яндекс Музыку — режим «Локальные файлы» выключается: после выхода из аккаунта снова будет экран входа.
    if (state.loggedIn) {
        LaunchedEffect(Unit) { if (LocalModeSettings.enabled) LocalModeSettings.updateEnabled(false) }
    }
    // Ссылки music.yandex.* должны открываться в этом приложении: один раз предлагаем включить это в настройках системы.
    LinkHandlingPrompt()
    // Аккаунт VK: окно с тем, что нужно сделать для корректной работы (с галочкой «Больше не показывать»).
    if (state.loggedIn) VkFirstRunPrompt()
    // Выбор платформы для входа поверх локального режима (кнопка в профиле и в настройках).
    var showLogin by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loggedIn) { if (state.loggedIn) showLogin = false }
    // Доступ к аудиофайлам: диалог показываем один раз, дальше — кнопка «Разрешить доступ».
    val appCtx = LocalContext.current
    var audioGranted by remember { mutableStateOf(LocalMusic.hasPermission(appCtx)) }
    val requestAudio = rememberAudioAccessRequest { ok ->
        audioGranted = ok
        if (ok && local) vm.loadLocalTracks()
    }
    var audioAsked by rememberSaveable { mutableStateOf(false) }
    if (local) {
        LaunchedEffect(Unit) {
            if (audioGranted) vm.loadLocalTracks()
            else if (!audioAsked) { audioAsked = true; requestAudio(false) }
        }
        // Вернулись в приложение: доступ могли выдать или отозвать в настройках, а файлы — измениться.
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            val now = LocalMusic.hasPermission(appCtx)
            audioGranted = now
            if (now) vm.loadLocalTracks()
        }
    }
    val deviceNav = remember { DeviceNav() }
    val deviceTracker = remember { ScrollTracker() }
    // Выход из режима (вход в аккаунт): открытые страницы файлов закрываем.
    LaunchedEffect(local) { if (!local) deviceNav.clear() }

    // rememberSaveable: Android убивает процесс, когда разрешение меняют в системных настройках;
    // после возврата пользователь остаётся на том же экране, а не выбрасывается на главную.
    // Набор и порядок экранов настраиваются (Настройки → Внешний вид → Навигация).
    val tabs = NavSettings.tabs
    var tab by rememberSaveable { mutableStateOf(NavSettings.tabs.first()) }
    // История переходов между вкладками: системная кнопка «назад» возвращает на предыдущую вкладку
    // (например, с «Поиска», открытого с главной, — обратно на главную), а с первой вкладки выходит из приложения.
    val tabHistory = remember { mutableStateListOf<Tab>() }
    val goTab: (Tab) -> Unit = { t ->
        if (t != tab) {
            tabHistory.remove(tab)
            tabHistory.add(tab)
            while (tabHistory.size > 8) tabHistory.removeAt(0)
            tab = t
        }
    }
    // Экран убрали из панели, пока он был открыт, — переходим на первый из оставшихся.
    LaunchedEffect(tabs) {
        tabHistory.removeAll { it !in tabs }
        if (tab !in tabs) tab = tabs.first()
    }
    // Поиск как отдельная страница поверх — нужна, только если вкладку «Поиск» убрали из панели.
    var searchOverlay by rememberSaveable { mutableStateOf(false) }
    val searchTracker = remember { ScrollTracker() }
    // Вернулись в приложение, а оно всё ещё «офлайн», хотя сеть уже есть, — пробуем загрузить заново.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.retryIfOffline() }
    // Предзагрузка обложек: текущий трек, соседние и ближайшие в очереди (во всех размерах, что нужны плееру, мини-плееру
    // и фону) грузятся заранее, а с ними — первые строки списков (любимое, история, поиск, плейлисты).
    LaunchedEffect(state.netEpoch) { if (state.netEpoch > 0) CoverPreloader.reset() }
    val preloadNowId = state.nowPlaying?.id
    LaunchedEffect(preloadNowId, state.netEpoch) {
        if (preloadNowId == null) return@LaunchedEffect
        val cur = listOfNotNull(state.nowPlaying)
        CoverPreloader.preload(appCtx, cur.flatMap { listOf(it.cover(160), it.cover(300), it.cover(600)) })
        delay(250)
        val near = listOfNotNull(vm.neighborTrack(false)) + vm.upcomingTracks(5)
        CoverPreloader.preload(appCtx, near.flatMap { listOf(it.cover(160), it.cover(300), it.cover(600)) })
    }
    LaunchedEffect(
        state.liked.size, state.liked.firstOrNull()?.id, state.history.size, state.history.firstOrNull()?.id,
        state.searchResults.size, state.searchResults.firstOrNull()?.id, state.playlists.size, state.netEpoch,
    ) {
        delay(400)
        val tr = state.liked.take(14) + state.history.take(14) + state.searchResults.take(14)
        CoverPreloader.preload(
            appCtx,
            tr.flatMap { listOf(it.cover(200), it.cover(300)) } + state.playlists.take(14).flatMap { listOf(it.cover(), it.cover(200)) },
        )
    }
    // Полный плеер — шторка: её вытягивают свайпом вверх от мини-плеера (как в Яндекс Музыке), открывают нажатием
    // на мини-плеер или кнопкой виджета. Положение шторки хранится в одном Animatable.
    val sheetScope = rememberCoroutineScope()
    val closedPx = with(LocalDensity.current) {
        // В физических пикселях: масштаб интерфейса меняет плотность, и «dp экрана → px» дал бы неверную высоту.
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp *
            LocalContext.current.resources.displayMetrics.density
    }
    // Поворот экрана пересоздаёт Activity: открытый плеер должен остаться открытым (раньше он «вылетал» на главную).
    var playerWasOpen by rememberSaveable { mutableStateOf(false) }
    val sheet = remember { PlayerSheetState(Animatable(if (playerWasOpen) 0f else closedPx), closedPx, sheetScope) }
    LaunchedEffect(sheet) { snapshotFlow { sheet.offset.value < sheet.closedPx * 0.5f }.collect { playerWasOpen = it } }
    val playerVisible by remember { derivedStateOf { sheet.offset.value < sheet.closedPx - 1f } }
    val openPlayerRequested by vm.openPlayerRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openPlayerRequested) {
        if (openPlayerRequested) {
            if (state.nowPlaying != null) sheet.open()
            vm.consumeOpenPlayer()
        }
    }
    LaunchedEffect(state.nowPlaying == null) { if (state.nowPlaying == null) sheet.close() }
    // «Наверх»: левый верхний угол экрана или повторное нажатие на выбранную вкладку.
    var scrollTopTick by remember { mutableIntStateOf(0) }
    var lastProfileTap by remember { mutableLongStateOf(0L) }
    var resyncTick by remember { mutableIntStateOf(0) }
    val tabsTracker = remember { ScrollTracker() }
    val artistTracker = remember { ScrollTracker() }
    val albumTracker = remember { ScrollTracker() }
    val innerPages = remember { InnerPages() }
    val snackbar = remember { SnackbarHostState() }
    var trackToAdd by remember { mutableStateOf<Track?>(null) }
    var showCreateForTrack by remember { mutableStateOf(false) }
    // «Информация о треке» и «Скачать как…» для отдельного трека.
    var infoTrack by remember { mutableStateOf<Track?>(null) }
    var editLocalTrack by remember { mutableStateOf<Track?>(null) }
    // Свой трек/альбом SoundCloud, который сейчас редактируется (id, это альбом).
    var editSc by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val scMyId = remember(state.accounts, state.activeToken) {
        state.accounts.firstOrNull { it.token == state.activeToken && it.provider == app.ymusic.m3e.data.Provider.SOUNDCLOUD }
            ?.let { app.ymusic.m3e.data.ScProfile.fromJson(it.scInfo) }?.let { app.ymusic.m3e.data.ScIds.PREFIX + it.id }
    }
    var modeTrack by remember { mutableStateOf<Track?>(null) }
    // «Включить волну» из меню трека: окно выбора (по треку / исполнителю / альбому).
    var waveTrack by remember { mutableStateOf<Track?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Окно «чем поделились» (Редактор интерфейса → Ссылки → «Открывать модалкой»).
    var previewLink by remember { mutableStateOf<SharedLink?>(null) }
    // Мини-плеер показываем/прячем с небольшой задержкой на скрытие: короткие «пустые» моменты
    // при смене очереди не должны запускать анимацию выезда/появления заново.
    val hasTrack = state.nowPlaying != null
    var miniVisible by remember { mutableStateOf(hasTrack) }
    LaunchedEffect(hasTrack) {
        if (hasTrack) miniVisible = true else { delay(1500); miniVisible = false }
    }
    LaunchedEffect(state.error) {
        state.error?.let { AppToast.error(it); vm.dismissError() }
    }
    LaunchedEffect(state.message) {
        state.message?.let { AppToast.show(it); vm.dismissMessage() }
    }

    // Док-панель: автоскрытие при прокрутке вниз (если включено в Настройки → Оформление).
    val autoHide = ThemeSettings.dockAutoHide
    var dockHidden by remember { mutableStateOf(false) }
    LaunchedEffect(autoHide, tab, showSettings, scrollTopTick) { dockHidden = false }
    val dockFrac = animateFloatAsState(
        targetValue = if (autoHide && dockHidden) 1f else 0f,
        animationSpec = tween(fxMs(260)),
        label = "dockHide",
    )
    val navInsetPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    // Подложка дока: размытый экран позади (по умолчанию), сплошная панель (блюр выключен) или ничего (островок).
    val backdrop = rememberDockBackdrop()
    // Мини-плеер и фон полного плеера берут размытый экран из того же слоя («Матовое стекло»).
    GlassHost.backdrop = backdrop
    // Матовое стекло полного плеера: всё приложение под плеером (вместе с доком и мини-плеером) размывается прямо на месте,
    // сила размытия растёт вместе с выезжающей шторкой. Отдельная запись экрана в слой для этого не нужна.
    // Островок тоже получает размытие под собой (с мягкой верхней кромкой); без размытия под ним ничего нет, как раньше.
    val dockMode = when {
        ThemeSettings.dockBlur && !ThemeSettings.optimize -> DockBackdropMode.Blur
        ThemeSettings.dockIsland -> DockBackdropMode.None
        else -> DockBackdropMode.Solid
    }
    val hideConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    if (available.y < -6f) dockHidden = true else if (available.y > 6f) dockHidden = false
                }
                return Offset.Zero
            }
        }
    }

    // Нажатия на вкладки дока: повторное нажатие на выбранную — наверх; двойное нажатие на «Профиль» — настройки.
    val onDockSelect: (Tab) -> Unit = { t ->
        val now = android.os.SystemClock.uptimeMillis()
        val doubleProfile = !wide && t == Tab.Profile && now - lastProfileTap < 450L
        if (t == Tab.Profile) lastProfileTap = if (doubleProfile) 0L else now
        if (doubleProfile) Haptics.double() else Haptics.tick()
        if (doubleProfile) {
            goTab(Tab.Profile)
            searchOverlay = false
            deviceNav.clear()
            vm.closeAllPages()
            showSettings = true
        } else {
            if (t == tab) {
                // Нажатие на текущий раздел: закрываем всё открытое поверх него (настройки, исполнитель, альбом,
                // плейлист, «Чарт»...) и возвращаемся на его главную страницу. Если и так на ней — прокрутка наверх.
                val hadOverlay = showSettings || searchOverlay || state.openedArtist != null || state.openedAlbum != null ||
                    deviceNav.top != null
                val hadInner = innerPages.resetAll()
                if (!hadOverlay && !hadInner) scrollTopTick++
                resyncTick++
            }
            goTab(t)
            showSettings = false
            searchOverlay = false
            deviceNav.clear()
            vm.closeAllPages()
        }
    }

    // Переход на «Поиск» по кнопке с главной идёт с анимацией (плавное затухание → появление поиска), даже если
    // между ними есть другие вкладки. Флаг гасится сам, когда пейджер взял его в работу.
    var searchJump by remember { mutableStateOf(false) }
    // Кнопка поиска на главной: открыть поиск и поставить курсор в поле.
    val openSearch: () -> Unit = {
        showSettings = false
        deviceNav.clear()
        vm.closeAllPages()
        vm.requestSearchFocus()
        if (Tab.Search in tabs) {
            if (tab != Tab.Search && (NavSettings.swipeTabs || ThemeSettings.animTabScroll) && tabs.size > 1) searchJump = true
            goTab(Tab.Search); searchOverlay = false
        } else searchOverlay = true
    }

    // Распознанный трек: открываем «Поиск» и ищем его (SearchScreen сам подхватит запрос).
    val searchRequest by vm.searchRequest.collectAsStateWithLifecycle()
    LaunchedEffect(searchRequest) {
        if (searchRequest != null) {
            showSettings = false
            deviceNav.clear()
            vm.closeAllPages()
            innerPages.resetAll()
            if (Tab.Search in tabs) { goTab(Tab.Search); searchOverlay = false } else searchOverlay = true
            sheet.close()
        }
    }

    // Ссылка, которой поделились (трек, альбом, исполнитель, плейлист): закрываем лишнее и открываем страницу.
    val linkRequest by vm.linkRequest.collectAsStateWithLifecycle()
    LaunchedEffect(linkRequest) {
        val link = linkRequest ?: return@LaunchedEffect
        showSettings = false
        searchOverlay = false
        deviceNav.clear()
        vm.closeAllPages()
        innerPages.resetAll()
        sheet.close()
        vm.consumeLinkRequest()
        AppLog.i("Link", "открываем: $link, модалка=${UiLayout.linkModal}")
        val isPlaylist = link is SharedLink.PlaylistLink || link is SharedLink.PlaylistUuidLink
        // Модалка — по настройке; профиль всегда окном; плейлист страницей нужен вкладке «Плейлисты» — если её нет в доке, тоже окном.
        if (UiLayout.linkModal || link is SharedLink.UserLink || (isPlaylist && Tab.Playlists !in tabs) || local) {
            previewLink = link
        } else {
            if (isPlaylist) goTab(Tab.Playlists)
            vm.openLink(link)
        }
    }

    // Ссылка Яндекс Музыки в буфере обмена: предлагаем открыть (если система не передала её приложению).
    val clipLink by vm.clipLink.collectAsStateWithLifecycle()
    clipLink?.let { cl ->
        AlertDialog(
            onDismissRequest = { vm.dismissClipLink() },
            title = { Text("Открыть ссылку?") },
            text = {
                Text(
                    if (cl is SharedLink.SoundCloudLink) "В буфере обмена ссылка на SoundCloud. Открыть её в приложении?"
                    else if (cl is SharedLink.VkTrackLink || cl is SharedLink.VkAlbumLink || cl is SharedLink.VkArtistLink) "В буфере обмена ссылка на VK Музыку (${linkKindTitle(cl)}). Открыть её в приложении?"
                    else "В буфере обмена ссылка на Яндекс Музыку (${linkKindTitle(cl)}). Открыть её в приложении?"
                )
            },
            confirmButton = { TextButton(onClick = { vm.dismissClipLink(); vm.requestLink(cl) }) { Text("Открыть") } },
            dismissButton = { TextButton(onClick = { vm.dismissClipLink() }) { Text("Не сейчас") } },
        )
    }

    // Окно «Поделиться» с QR-кодом (открывается кнопками «Поделиться» у трека, альбома, исполнителя, плейлиста, профиля).
    ShareQrHost.request?.let { r -> ShareQrSheet(r, onDismiss = { ShareQrHost.close() }) }

    // Исполнитель или альбом из файлов, выбранный в плеере: открываем его страницу в режиме «Локальные файлы».
    val localNavRequest by vm.localNavRequest.collectAsStateWithLifecycle()
    LaunchedEffect(localNavRequest) {
        val r = localNavRequest ?: return@LaunchedEffect
        if (local) {
            val page = if (r.startsWith("album-of:")) {
                val id = r.removePrefix("album-of:")
                state.localLib.albums.firstOrNull { a -> a.tracks.any { it.id == id } }?.let { "album:" + it.key }
            } else r
            if (page != null) {
                showSettings = false
                searchOverlay = false
                deviceNav.open(page)
                sheet.close()
            }
        }
        vm.consumeLocalNav()
    }

    // Смена аккаунта по удержанию «Профиля» в доке: табличка поверх приложения, под ней размытие.
    val accountSwitcher = remember { AccountSwitcherState() }
    var showAccountPicker by remember { mutableStateOf(false) }
    SideEffect { accountSwitcher.enabled = !local && state.accounts.isNotEmpty() }
    val downloadsState by vm.downloads.collectAsStateWithLifecycle()
    val likedIds = remember(state.liked) { state.liked.mapTo(HashSet()) { it.id } }
    val downloadedIds = remember(downloadsState.items) { downloadsState.items.mapTo(HashSet()) { it.id } }
    // Волна «по…» с карточек и страниц; в режиме «Локальные файлы» и без сети её нет.
    val waveActions = remember(vm, local, state.offline) {
        if (local || state.offline) null else WaveActions(
            track = { t -> waveTrack = t },
            album = { a -> vm.startWaveByAlbum(a.id, a.title) },
            artist = { r -> vm.startWaveByArtist(r) },
            playlist = { p -> vm.startWaveByPlaylist(p.ownerUid, p.kind.toString(), p.title) },
            liked = { vm.state.value.account?.apiId?.let { uid -> vm.startWaveByPlaylist(uid, "3", "Любимое") } },
        )
    }
    CompositionLocalProvider(
        LocalWaveActions provides waveActions,
        LocalLikedIds provides likedIds,
        LocalDownloadedIds provides downloadedIds,
        LocalScrollTopTick provides scrollTopTick,
        LocalInnerPages provides innerPages,
        LocalDownloads provides downloadsState,
        LocalToggleDownload provides { t: Track -> vm.toggleDownload(t) },
        LocalDownloadTo provides { t: Track, m: DownloadMode -> vm.downloadTrack(t, m) },
        LocalRemoveDownload provides { t: Track -> vm.removeDownload(t) },
        LocalPickDownloadMode provides { t: Track -> modeTrack = t },
        LocalShowTrackInfo provides { t: Track -> infoTrack = t },
        LocalEditLocalTrack provides { t: Track -> editLocalTrack = t },
        LocalEditScItem provides { id: String, album: Boolean -> editSc = id to album },
        LocalScMyId provides scMyId,
        LocalOffline provides state.offline,
        LocalPlayingSource provides (if (state.isPlaying) state.playingSource else null),
        LocalNetEpoch provides state.netEpoch,
        LocalOpenArtist provides { ref: ArtistRef -> vm.openArtist(ref) },
        LocalShowPlaylist provides { p: PlaylistInfo ->
            showSettings = false
            if (Tab.Playlists in tabs) { goTab(Tab.Playlists); vm.showPlaylist(p) }
            else previewLink = SharedLink.PlaylistLink(p.ownerUid, p.kind)
        },
        LocalAddToPlaylist provides { t: Track -> trackToAdd = t },
        LocalOpenAlbum provides { id: String, title: String? -> vm.openAlbum(id, title) },
        LocalOpenSearch provides openSearch,
        LocalOpenSettings provides { showSettings = true },
        LocalAudioAccess provides AudioAccess(audioGranted) { requestAudio(true) },
        LocalDeviceNav provides deviceNav,
        LocalWide provides wide,
        LocalCompactHeight provides compactH,
        LocalAccountSwitcher provides accountSwitcher,
    ) {
    // «Назад» на вкладке: на предыдущую из истории, а если истории нет — на первую вкладку. Слои поверх
    // (настройки, исполнитель, альбом, плеер, внутренние страницы) регистрируют свои обработчики позже и срабатывают раньше.
    BackHandler(enabled = !searchOverlay && (tabHistory.isNotEmpty() || tab != tabs.first())) {
        val prev = if (tabHistory.isNotEmpty()) tabHistory.removeAt(tabHistory.lastIndex) else null
        tab = prev?.takeIf { it in tabs } ?: tabs.first()
    }
    BackHandler(enabled = searchOverlay) { searchOverlay = false }
    Box(Modifier.fillMaxSize().cornerTapToTop { scrollTopTick++ }.accountSwitcherBlur(accountSwitcher).editPanelAutoHide()) {
    // Мини-плеер: в обычной раскладке стоит над доком, в планшетной — внизу области справа от боковой панели.
    val miniBlock: @Composable () -> Unit = {
        androidx.compose.animation.AnimatedVisibility(
            visible = miniVisible,
            enter = fadeIn(tween(animMs(250))) + slideInVertically(tween(animMs(350))) { it },
            exit = fadeOut(tween(animMs(200))) + slideOutVertically(tween(animMs(250))) { it },
        ) {
            CompositionLocalProvider(LocalHoldHaptics provides false) {
                ZoomBox(UiStyle.miniZoom) { MiniPlayer(vm, sheet, island = ThemeSettings.dockIsland) }
            }
        }
    }
    // Боковая панель — только в широкой раскладке и если выбрана; иначе внизу тот же док / островок, плывущий поверх контента.
    val railMode = wide && TabletSettings.sideRail
    // Планшет + островок: вертикальный островок только со значками, справа поверх контента; внизу остаётся лишь мини-плеер.
    val sideIsland = wide && !railMode && ThemeSettings.dockIsland && TabletSettings.islandPos != IslandPos.Bottom
    val scaffoldBlock: @Composable () -> Unit = {
    Scaffold(
        // В планшетной раскладке слева стоит боковая панель — она сама учитывает левый край, а здесь он не нужен.
        contentWindowInsets = if (railMode) WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.End)
        else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = {
            Column {
                UpdateBanner()
                MixPickerBar(vm)
                AppToastHost()
            }
        },
        bottomBar = {
            if (railMode || sideIsland) {
                Column {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(Modifier.widthIn(max = 760.dp)) { miniBlock() }
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            } else Column(
                // Подложка рисуется позади мини-плеера и дока: свечение мини-плеера ложится поверх неё и не обрезается.
                Modifier.dockBackdropStrip(
                    backdrop, dockMode,
                    solid = MaterialTheme.colorScheme.surfaceContainer,
                    tint = MaterialTheme.colorScheme.surface.copy(alpha = GlassSettings.dockTintAlpha(ThemeSettings.dockIsland)),
                    blurRadius = GlassSettings.dockBlurDp.dp,
                    softEdge = if (ThemeSettings.dockIsland) 40.dp else 0.dp,
                ),
            ) {
                // Мини-плеер остаётся на месте и при скрытии дока просто опускается к нижней системной панели,
                // потому что сам док сжимает свою высоту (CollapsingDock) и пустого места не оставляет.
                if (wide) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.widthIn(max = 760.dp)) { miniBlock() }
                } else miniBlock()
                CollapsingDock(
                    fraction = dockFrac, minHeightPx = navInsetPx,
                    modifier = Modifier.onSizeChanged { backdrop.dockHeightPx = it.height },
                ) {
                    AppDock(tabs = tabs, tab = tab, island = ThemeSettings.dockIsland, onSelect = onDockSelect)
                }
            }
        },
    ) { pad ->
        // Экран занимает всю высоту и уходит ПОД нижнюю панель (за ней он размыт); нижний отступ списков — dockInset().
        val layoutDir = androidx.compose.ui.platform.LocalLayoutDirection.current
        CompositionLocalProvider(LocalDockInset provides pad.calculateBottomPadding()) {
        Box(
            Modifier
                .padding(
                    start = pad.calculateStartPadding(layoutDir),
                    top = pad.calculateTopPadding(),
                    end = pad.calculateEndPadding(layoutDir),
                )
                .fillMaxSize()
                .nestedScroll(hideConnection)
                .dockBackdropSource(backdrop, record = dockMode == DockBackdropMode.Blur || GlassSettings.anyBackdropNeeded),
        ) {
            val pageContent: @Composable (Tab) -> Unit = { target ->
                CompositionLocalProvider(
                    LocalScrollTracker provides tabsTracker,
                    LocalPageActive provides (target == tab),
                ) {
                    when (target) {
                        Tab.Home -> if (local) LocalHomeScreen(vm) else HomeScreen(vm)
                        Tab.Search -> if (local) LocalSearchScreen(vm, active = tab == Tab.Search) else SearchScreen(vm, active = tab == Tab.Search)
                        Tab.Playlists -> if (local) LocalPlaylistsScreen(vm) else PlaylistsScreen(vm)
                        Tab.Profile ->
                            if (local) LocalProfileScreen(vm, onOpenSettings = { showSettings = true }, onLogin = { showLogin = true })
                            else ProfileScreen(vm, onOpenSettings = { showSettings = true })
                    }
                }
            }
            // Листание экранов свайпом (отключается в Настройки → Внешний вид → Навигация). Пока открыты настройки,
            // исполнитель, альбом или поиск поверх, листание выключено, чтобы не сдвигать экран под ними.
            val overlayOpen = showSettings || searchOverlay || state.openedArtist != null || state.openedAlbum != null ||
                deviceNav.top != null
            if ((NavSettings.swipeTabs || ThemeSettings.animTabScroll) && tabs.size > 1) {
                TabPager(
                    tabs = tabs, selected = tab, onSelected = { goTab(it) },
                    swipeEnabled = NavSettings.swipeTabs && !overlayOpen && !innerPages.pagerLocked, content = pageContent,
                    searchJump = searchJump, onSearchJumpConsumed = { searchJump = false },
                    resyncTick = resyncTick,
                )
            } else {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(animMs(260), delayMillis = animMs(60))) + scaleIn(tween(animMs(260), delayMillis = animMs(60)), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(animMs(120)))
                    },
                    label = "tabs",
                ) { target -> pageContent(target) }
            }
            // Поиск отдельной страницей — только если вкладку «Поиск» убрали из панели.
            AnimatedVisibility(
                visible = searchOverlay,
                enter = fadeIn(tween(animMs(220))) + slideInVertically(tween(animMs(320))) { it / 6 },
                exit = fadeOut(tween(animMs(160))) + slideOutVertically(tween(animMs(240))) { it / 8 },
            ) {
                CompositionLocalProvider(LocalScrollTracker provides searchTracker) {
                    SwipeBack(onBack = { searchOverlay = false }) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxSize()) {
                                SubPageBar("Поиск", onBack = { searchOverlay = false })
                                Box(Modifier.weight(1f).fillMaxWidth()) {
                                    if (local) LocalSearchScreen(vm, active = true) else SearchScreen(vm, active = true)
                                }
                            }
                        }
                    }
                }
            }
            // Настройки рисуются раньше исполнителя и альбома: если страница исполнителя/альбома
            // открыта при открытых настройках (например, из полного плеера), она должна быть поверх.
            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(tween(animMs(220))) + slideInVertically(tween(animMs(320))) { it / 6 },
                exit = fadeOut(tween(animMs(160))) + slideOutVertically(tween(animMs(240))) { it / 8 },
            ) {
                CompositionLocalProvider(LocalScrollTracker provides null) {
                    SettingsScreen(vm, local = local, onLogin = { showSettings = false; showLogin = true }) { showSettings = false }
                }
            }
            // Исполнитель и альбом живут в одном слое: страница, открытая из другой (альбом → исполнитель и наоборот),
            // заменяет её плавной сменой, а не наезжает поверх. Открытие с нуля и закрытие — как раньше (справа / вправо).
            val pageLayer: String? = when {
                state.openedAlbum != null -> "album"
                state.openedArtist != null -> "artist"
                else -> null
            }
            val swapAnim = ThemeSettings.animPageSwap && !ThemeSettings.optimize
            AnimatedContent(
                targetState = pageLayer,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    when {
                        initialState == null ->
                            androidx.compose.animation.ContentTransform(pageEnter(), androidx.compose.animation.ExitTransition.None, sizeTransform = null)
                        targetState == null ->
                            androidx.compose.animation.ContentTransform(androidx.compose.animation.EnterTransition.None, pageExit(), sizeTransform = null)
                        swapAnim ->
                            androidx.compose.animation.ContentTransform(pageSwapEnter(), pageSwapExit(), sizeTransform = null)
                        else ->
                            androidx.compose.animation.ContentTransform(pageEnter(), pageExit(), sizeTransform = null)
                    }
                },
                label = "pageLayer",
            ) { layer ->
                when (layer) {
                    "artist" -> CompositionLocalProvider(LocalScrollTracker provides artistTracker) { ArtistScreen(vm) }
                    "album" -> CompositionLocalProvider(LocalScrollTracker provides albumTracker) {
                        SwipeBack(enabled = state.openedAlbum != null, onBack = { vm.closeAlbum() }) { AlbumScreen(vm) }
                    }
                    else -> Unit
                }
            }
            // Режим «Локальные файлы»: страницы альбома, исполнителя, плейлиста, всех файлов и т. д. поверх вкладок.
            if (local) {
                var lastDevicePage by remember { mutableStateOf<String?>(null) }
                deviceNav.top?.let { lastDevicePage = it }
                BackHandler(enabled = deviceNav.top != null) { deviceNav.back() }
                AnimatedVisibility(
                    visible = deviceNav.top != null,
                    enter = pageEnter(),
                    exit = pageExit(),
                ) {
                    CompositionLocalProvider(LocalScrollTracker provides deviceTracker) {
                        SwipeBack(enabled = deviceNav.top != null, onBack = { deviceNav.back() }) {
                            Crossfade(targetState = lastDevicePage, animationSpec = tween(animMs(180)), label = "devicePages") { p ->
                                if (p != null) DevicePageHost(vm, p)
                            }
                        }
                    }
                }
            }
            UpdateDialog()
            if (state.loading) {
                LoadingIndicator(Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            }
            // Кнопка «вверх»: появляется, когда верхний слой прокручен, исчезает на самом верху; в настройках её нет.
            val topTracker = when {
                deviceNav.top != null -> deviceTracker
                state.openedAlbum != null -> albumTracker
                state.openedArtist != null -> artistTracker
                searchOverlay -> searchTracker
                showSettings -> null
                else -> tabsTracker
            }
            val showToTop = topTracker?.canScrollUp == true
            AnimatedVisibility(
                visible = showToTop,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp + dockInset()),
                enter = fadeIn(tween(fxMs(200))) +
                    slideInVertically(fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 2 } +
                    scaleIn(fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), initialScale = 0.5f),
                exit = fadeOut(tween(fxMs(150))) +
                    slideOutVertically(tween(fxMs(190), easing = FastOutLinearInEasing)) { it / 2 } +
                    scaleOut(tween(fxMs(190)), targetScale = 0.5f),
            ) {
                ToTopButton(onClick = { scrollTopTick++ })
            }
        }
        }
    }
    }
    Box(
        Modifier.fillMaxSize().playerGlassBackdrop(
            enabled = GlassSettings.playerGlass && !ThemeSettings.optimize && state.nowPlaying != null,
            progress = { 1f - sheet.offset.value / sheet.closedPx.coerceAtLeast(1f) },
        ),
    ) {
    if (railMode) {
        // Планшет: слева боковая панель с экранами, справа — сам экран с мини-плеером внизу.
        Row(Modifier.fillMaxSize()) {
            AppRail(tabs = tabs, tab = tab, compact = compactH, onSelect = onDockSelect)
            Box(Modifier.weight(1f).fillMaxHeight()) { scaffoldBlock() }
        }
    } else scaffoldBlock()
    if (sideIsland) {
        val pos = TabletSettings.islandPos
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.systemBars.only(
                    when (pos) {
                        IslandPos.Left -> WindowInsetsSides.Start + WindowInsetsSides.Vertical
                        IslandPos.Right -> WindowInsetsSides.End + WindowInsetsSides.Vertical
                        else -> WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    },
                ),
            ),
            contentAlignment = when (pos) {
                IslandPos.Left -> Alignment.CenterStart
                IslandPos.Right -> Alignment.CenterEnd
                else -> Alignment.TopCenter
            },
        ) {
            SideIsland(tabs = tabs, tab = tab, pos = pos, onSelect = onDockSelect)
        }
    }
    }
    // Добавление ещё одного аккаунта: сразу страница входа через Яндекс (выбирать тип учётной записи не нужно);
    // «назад» закрывает экран входа и возвращает в настройки.
    if (state.addingAccount) {
        LoginScreen(
            vm = vm,
            error = state.error,
            onToken = { vm.onTokenReceived(it) },
            onCancel = { vm.cancelAddAccount() },
            startOn = state.addStart,
        )
    }
    // Добавление аккаунта из режима «Локальные файлы»: без приветственной страницы — сразу нижнее окно выбора платформы,
    // а после выбора открывается страница входа этого сервиса. После входа режим выключается, плейлисты из файлов остаются.
    if (local && showLogin) {
        ProviderPickerSheet(
            onPick = { showLogin = false; vm.startAddAccount(it) },
            onDismiss = { showLogin = false },
        )
    }
    }

    // Табличка смены аккаунта (удержание «Профиля» в доке) и выбор сервиса для нового аккаунта.
    AccountSwitcherOverlay(
        state = accountSwitcher,
        accounts = state.accounts,
        activeToken = state.activeToken,
        onSwitch = { vm.switchAccount(it) },
        onAdd = { showAccountPicker = true },
    )
    if (showAccountPicker) {
        ProviderPickerSheet(
            onPick = { showAccountPicker = false; vm.startAddAccount(it) },
            onDismiss = { showAccountPicker = false },
        )
    }

    if (playerVisible && state.nowPlaying != null) {
        CompositionLocalProvider(LocalHoldHaptics provides false) { PlayerSheet(vm, sheet) }
    }

    trackToAdd?.let { track ->
        if (local) {
            // Режим «Локальные файлы»: плейлисты из файлов устройства.
            DevicePlaylistChooser(track, onDismiss = { trackToAdd = null })
        } else if (showCreateForTrack) {
            CreatePlaylistDialog(
                onDismiss = { showCreateForTrack = false },
                onCreate = { title, isPublic ->
                    vm.createPlaylist(title, isPublic, track)
                    showCreateForTrack = false
                    trackToAdd = null
                },
            )
        } else {
            AddToPlaylistDialog(
                playlists = state.playlists.filter { it.isEditableBy(state.account) },
                onDismiss = { trackToAdd = null },
                onPick = { p -> vm.addTrackToPlaylist(p, track); trackToAdd = null },
                onCreate = { showCreateForTrack = true },
            )
        }
    }

    previewLink?.let { l ->
        SharedLinkSheet(
            vm, l,
            onOpenPage = {
                previewLink = null
                if (l is SharedLink.PlaylistLink || l is SharedLink.PlaylistUuidLink) {
                    if (Tab.Playlists in tabs) goTab(Tab.Playlists)
                }
                vm.openLink(l)
            },
            // Убираем окно, только если оно всё ещё про эту ссылку: запоздавшее закрытие не должно гасить новую.
            onDismiss = { if (previewLink == l) previewLink = null },
        )
    }
    infoTrack?.let { t -> TrackInfoSheet(vm, t) { infoTrack = null } }
    editLocalTrack?.let { t -> EditLocalTrackDialog(vm, t) { editLocalTrack = null } }
    editSc?.let { (id, isAlbum) ->
        ScEditDialog(
            vm, id, isAlbum, onDismiss = { editSc = null },
            onSave = { title, description, genre, tags, isPublic, cover ->
                if (isAlbum) vm.editScAlbum(id, title, description, genre, tags, isPublic)
                else vm.editScTrack(id, title, description, genre, tags, isPublic, cover)
            },
        )
    }
    // Плагины: окно установки снизу и сообщение, если файл не получилось прочитать.
    PluginSystem.pending?.let { pv -> PluginInstallSheet(pv) }
    PluginSystem.error?.let { msg ->
        AlertDialog(
            onDismissRequest = { PluginSystem.clearError() },
            title = { Text("Не удалось открыть плагин") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { PluginSystem.clearError() }) { Text("Понятно") } },
        )
    }
    PluginSystem.luaDialogs.lastOrNull()?.let { d -> key(d.pluginId, d.key) { PluginLuaDialog(d) } }
    // Нижняя модалка, которую открыл сам плагин (ui.sheet / ui.open_page).
    PluginSystem.sheetReq?.let { r ->
        val sp = PluginSystem.plugins.firstOrNull { it.id == r.pluginId }
        if (sp != null && sp.uiEntries.any { it.id == r.entryId }) {
            key(r.pluginId, r.entryId) {
                PluginSettingsSheet(sp, r.entryId, closing = r.closing) { PluginSystem.finishSheet() }
            }
        } else {
            LaunchedEffect(r) { PluginSystem.finishSheet() }
        }
    }
    waveTrack?.let { t -> WaveFromSheet(vm, t, fromPlayer = false) { waveTrack = null } }
    modeTrack?.let { t ->
        DownloadModeDialog(
            title = t.title, text = null, initial = downloadsState.mode, dirLabel = downloadsState.dirLabel,
            onDismiss = { modeTrack = null },
            onPick = { m, _ -> modeTrack = null; vm.downloadTrack(t, m) },
        )
    }
    // Окно запущенного микса (кнопка «Замиксовать»): старый трек, настройка перехода, следующий трек и плеер.
    MixPlayerHost(vm)
    // После аварийного закрытия: предложить сохранить журнал и отправить его на форум.
    CrashReportPrompt()
    // Режим редактора на экране: плавающая кнопка и панель правки поверх всего (включается в редакторе интерфейса).
    LayoutEditOverlay(tab = tab, playerOpen = playerVisible && state.nowPlaying != null, miniShown = miniVisible, hideFab = showSettings || searchOverlay)
    }
}

/** Название типа ссылки для окон. */
private fun linkKindTitle(l: SharedLink): String = when (l) {
    is SharedLink.TrackLink -> "трек"
    is SharedLink.AlbumLink -> "альбом"
    is SharedLink.ArtistLink -> "исполнитель"
    is SharedLink.PlaylistLink, is SharedLink.PlaylistUuidLink -> "плейлист"
    is SharedLink.UserLink -> "профиль"
    is SharedLink.SoundCloudLink -> "ссылка SoundCloud"
    is SharedLink.VkTrackLink -> "трек VK"
    is SharedLink.VkAlbumLink -> "плейлист VK"
    is SharedLink.VkArtistLink -> "исполнитель VK"
}

/**
 * Окно «чем поделились»: плейлист, альбом, трек, исполнитель или профиль открываются нижней модалкой — с обложкой,
 * кнопками «Слушать» / «Вперемешку» и списком треков (у профиля — публичные плейлисты). «Открыть страницу» ведёт на полную страницу.
 */
@Composable
private fun SharedLinkSheet(vm: AppViewModel, link: SharedLink, onOpenPage: () -> Unit, onDismiss: () -> Unit) {
    var preview by remember(link) { mutableStateOf<AppViewModel.LinkPreview?>(null) }
    var failure by remember(link) { mutableStateOf<String?>(null) }
    val showPlaylist = LocalShowPlaylist.current
    LaunchedEffect(link) {
        try {
            preview = vm.loadLinkPreview(link)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Link", "не удалось загрузить $link: ${e.message}")
            failure = e.message ?: "Не удалось открыть ссылку"
        }
    }
    // Любое закрытие анимированное (кнопки, «Открыть страницу», касание фона, «назад»): сначала шторка плавно уезжает вниз,
    // и только потом окно убирается. Повторные нажатия во время анимации игнорируются.
    val sheetState = rememberModalBottomSheetState()
    val closeScope = rememberCoroutineScope()
    var closing by remember(link) { mutableStateOf(false) }
    val closeThen: (() -> Unit) -> Unit = { after ->
        if (!closing) {
            closing = true
            closeScope.launch { sheetState.hide() }.invokeOnCompletion { after() }
        }
    }
    val close: () -> Unit = { closeThen(onDismiss) }
    ModalBottomSheet(onDismissRequest = close, sheetState = sheetState) {
        val pv = preview
        val err = failure
        when {
            err != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Не удалось открыть ${linkKindTitle(link)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (err.contains("HTTP 40")) "Он приватный или удалён" else err,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = close, modifier = Modifier.padding(top = 8.dp)) { Text("Закрыть") }
            }
            pv == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(40.dp)) }
            else -> LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(
                            model = pv.coverUri?.let { app.ymusic.m3e.data.buildCoverUrl(it, 400) },
                            modifier = Modifier.size(104.dp).clip(if (pv.link is SharedLink.ArtistLink || pv.link is SharedLink.VkArtistLink) ThemeSettings.avatarShape.asShape() else rounded(24)),
                            placeholder = if (pv.link is SharedLink.UserLink) R.drawable.ms_person else R.drawable.ms_music_note,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pv.kind, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(pv.title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (pv.subtitle.isNotBlank()) {
                                Text(pv.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                item {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (pv.tracks.isNotEmpty()) {
                            val start = pv.focusTrackId?.let { id -> pv.tracks.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
                            Button(onClick = { vm.play(pv.tracks, start); close() }) {
                                Icon(painterResource(R.drawable.ms_play_arrow), null); Spacer(Modifier.width(6.dp)); Text("Слушать")
                            }
                            if (pv.tracks.size > 1) FilledTonalButton(onClick = { vm.play(pv.tracks, 0, shuffle = true); close() }) {
                                Icon(painterResource(R.drawable.ms_shuffle), null); Spacer(Modifier.width(6.dp)); Text("Вперемешку")
                            }
                        }
                        if (pv.link !is SharedLink.TrackLink && pv.link !is SharedLink.VkTrackLink && pv.link !is SharedLink.UserLink) {
                            OutlinedButton(onClick = { closeThen(onOpenPage) }) { Text("Открыть страницу") }
                        }
                    }
                }
                if (pv.link is SharedLink.UserLink) {
                    item {
                        if (pv.playlists.isEmpty()) {
                            Text(
                                "У этого пользователя нет публичных плейлистов",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                    items(pv.playlists.chunked(2)) { rowItems ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            rowItems.forEach { p ->
                                PlaylistCard(p, modifier = Modifier.weight(1f), fill = true) { close(); showPlaylist(p) }
                            }
                            if (rowItems.size < 2) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                itemsIndexed(pv.tracks.take(150)) { i, t ->
                    TrackRow(t) { vm.play(pv.tracks, i) }
                }
            }
        }
    }
}

/**
 * Кнопка «вверх»: при нажатии стрелка «улетает» вверх и возвращается снизу с пружинкой, а сам список в это время плавно
 * едет к началу (см. [topScrollSpec]).
 */
@Composable
private fun ToTopButton(onClick: () -> Unit) {
    val lift = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    FloatingActionButton(
        onClick = {
            Haptics.tick()
            onClick()
            scope.launch {
                launch {
                    press.animateTo(0.9f, tween(fxMs(80)))
                    press.animateTo(1f, fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                }
                lift.snapTo(0f)
                lift.animateTo(-14f, tween(fxMs(130), easing = FastOutLinearInEasing))
                lift.snapTo(14f)
                lift.animateTo(0f, fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        },
        shape = rounded(16),
        modifier = Modifier.graphicsLayer { scaleX = press.value; scaleY = press.value },
    ) {
        Icon(
            painterResource(R.drawable.ms_arrow_upward), contentDescription = "Наверх",
            modifier = Modifier.graphicsLayer { translationY = lift.value * density },
        )
    }
}

/** Док-панель: обычная (на всю ширину) или «островок» — плавающая капсула, где подпись только у выбранной вкладки. */
/**
 * Боковая панель экранов для планшетов (вместо нижнего дока): значки по центру по вертикали, у выбранного — «пилюля»
 * Material 3 Expressive, которая плавно меняет цвет и ширину. Тот же набор экранов и порядок, что в доке.
 */
@Composable
private fun AppRail(tabs: List<Tab>, tab: Tab, compact: Boolean, onSelect: (Tab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxHeight().width(if (compact) 80.dp else 96.dp),
    ) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 14.dp, Alignment.CenterVertically),
        ) {
            tabs.forEach { t -> RailItem(t, t == tab, compact) { onSelect(t) } }
        }
    }
}

@Composable
private fun RailItem(t: Tab, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = fxSpring(stiffness = Spring.StiffnessMediumLow),
        label = "railBg",
    )
    val indicatorW by animateDpAsState(
        if (selected) 64.dp else 48.dp,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "railIndicator",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val sw = LocalAccountSwitcher.current
    Column(
        Modifier.width(if (compact) 72.dp else 84.dp)
            .accountSwitcherGesture(t)
            .pressableHold(rounded(20), hold = !(t == Tab.Profile && sw?.enabled == true)) { onClick() }
            .padding(vertical = if (compact) 4.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(indicatorW).height(if (compact) 28.dp else 32.dp).clip(rounded(16)).background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(t.icon), contentDescription = t.title, tint = fg)
        }
        Text(
            t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Островок планшета: вертикальная капсула справа поверх контента, только значки (название — по долгому нажатию). */
@Composable
private fun SideIsland(tabs: List<Tab>, tab: Tab, pos: IslandPos, onSelect: (Tab) -> Unit) {
    val vertical = pos == IslandPos.Left || pos == IslandPos.Right
    val glassOn = GlassSettings.islandGlass && !ThemeSettings.optimize
    val alpha = if (ThemeSettings.optimize) 1f
        else if (glassOn) GlassSettings.solidAlpha(GlassSettings.islandAlpha) else GlassSettings.dockCapsuleAlpha
    val glass = alpha < 0.995f
    val probe = remember { mutableStateOf(Offset.Zero) }
    val shape = rounded(UiStyle.dockCorner)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
        tonalElevation = if (glass) 0.dp else 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(
            start = if (pos == IslandPos.Left) 12.dp else 0.dp, end = if (pos == IslandPos.Right) 12.dp else 0.dp,
            top = if (pos == IslandPos.Top) 8.dp else 0.dp,
        )
            .glassOriginProbe(probe)
            .glassBehind(glassOn, GlassSettings.islandBlurDp, shape) { probe.value },
    ) {
        val items: @Composable () -> Unit = {
            tabs.forEach { t ->
                val sel = t == tab
                val tip = rememberButtonHint()
                val bg by animateColorAsState(
                    if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = fxSpring(stiffness = Spring.StiffnessMediumLow),
                    label = "sideIslandBg",
                )
                val fg = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                val sw = LocalAccountSwitcher.current
                val switcher = t == Tab.Profile && sw?.enabled == true
                Box(
                    Modifier.accountSwitcherGesture(t)
                        .hintOnLongPress(tip.state, NavSettings.hints && !switcher)
                        .pressableHold(rounded((UiStyle.dockCorner - 6).coerceAtLeast(6)), hold = !switcher) { onSelect(t) }
                        .background(bg)
                        .padding(UiStyle.dockPadH.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ZoomBox(UiLayout.dockIconZoom) { Icon(painterResource(t.icon), contentDescription = t.title, tint = fg) }
                    HintPopup(tip.state, t.title)
                }
            }
        }
        if (vertical) Column(
            Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(UiStyle.dockSpacing.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { items() } else Row(
            Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(UiStyle.dockSpacing.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { items() }
    }
}

@Composable
private fun AppDock(tabs: List<Tab>, tab: Tab, island: Boolean, onSelect: (Tab) -> Unit) {
    // Превращение панели в островок и обратно: старый вид сжимается к низу и растворяется, новый вырастает
    // пружиной; высота меняется плавно (SizeTransform).
    AnimatedContent(
        targetState = island,
        transitionSpec = {
            val origin = TransformOrigin(0.5f, 1f)
            val enter = fadeIn(tween(fxMs(220), delayMillis = 70)) +
                scaleIn(
                    fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    initialScale = if (targetState) 0.72f else 0.9f, transformOrigin = origin,
                ) +
                slideInVertically(fxSpring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it / 3 }
            val exit = fadeOut(tween(fxMs(130))) +
                scaleOut(tween(fxMs(220), easing = FastOutSlowInEasing), targetScale = if (targetState) 1.08f else 0.72f, transformOrigin = origin) +
                slideOutVertically(tween(fxMs(220), easing = FastOutSlowInEasing)) { it / 4 }
            (enter togetherWith exit).using(
                SizeTransform(clip = false) { _, _ ->
                    fxSpring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                },
            )
        },
        contentAlignment = Alignment.BottomCenter,
        label = "dockMorph",
    ) { isl -> ZoomBox(UiLayout.dockZoom) { DockContent(tabs, tab, isl, onSelect) } }
}

@Composable
private fun DockContent(tabs: List<Tab>, tab: Tab, island: Boolean, onSelect: (Tab) -> Unit) {
    if (!island) {
        // Выразительная короткая панель навигации Material 3 Expressive.
        // При максимальном размере экрана или шрифта вкладки не помещаются в ряд, и последняя уезжала за край (но
        // оставалась нажимаемой). В этом случае подпись показывается только у выбранной вкладки, а масштаб шрифта
        // подписей ограничен — все вкладки остаются на месте.
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val dens = LocalDensity.current
        val mode = NavSettings.dockLabels
        // «Рядом» занимает больше ширины, чем «под значком», поэтому порог для узкого режима выше.
        val need = if (mode == DockLabels.Beside) 128f else 92f
        val narrow = mode != DockLabels.Hidden &&
            (cfg.screenWidthDp.toFloat() / tabs.size.coerceAtLeast(1) < need * UiLayout.dockZoom || dens.fontScale > 1.2f)
        CompositionLocalProvider(
            LocalDensity provides if (narrow) Density(dens.density, fontScale = dens.fontScale.coerceAtMost(1f)) else dens,
        ) {
            ShortNavigationBar(containerColor = Color.Transparent) {
                tabs.forEach {
                    // Тики при удержании и на вкладках дока; нажатие уже даёт отклик в onSelect, поэтому без отдельного тика «вниз».
                    // «Профиль»: удержание открывает смену аккаунта, поэтому тиков при удержании на нём нет.
                    val switcherOn = it == Tab.Profile && LocalAccountSwitcher.current?.enabled == true
                    val press = rememberPress(true, hold = !switcherOn, downTick = false)
                    ShortNavigationBarItem(
                        selected = tab == it,
                        onClick = { onSelect(it) },
                        modifier = Modifier.pressScale(press, 0.08f).layoutEdit(LScreen.DOCK, it).accountSwitcherGesture(it),
                        interactionSource = press.source,
                        iconPosition = if (mode == DockLabels.Beside) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top,
                        icon = { ZoomBox(UiLayout.dockIconZoom) { Icon(painterResource(it.icon), if (narrow || mode == DockLabels.Hidden) it.title else null) } },
                        label = if (mode == DockLabels.Hidden || (narrow && tab != it)) null else {
                            { Text(it.title, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
                        },
                    )
                }
            }
        }
        return
    }
    // Островок: вкладки в капсуле. Размеры, отступы, положение и «отдельная последняя кнопка» — в редакторе интерфейса.
    val lastDetached = UiStyle.dockDetachLast && tabs.size >= 2
    val mainTabs = if (lastDetached) tabs.dropLast(1) else tabs
    val glassOn = GlassSettings.islandGlass && !ThemeSettings.optimize
    val capsuleAlpha = if (ThemeSettings.optimize) 1f
        else if (glassOn) GlassSettings.solidAlpha(GlassSettings.islandAlpha) else GlassSettings.dockCapsuleAlpha
    val capsuleGlass = capsuleAlpha < 0.995f
    val capsuleProbe = remember { mutableStateOf(Offset.Zero) }
    val detachProbe = remember { mutableStateOf(Offset.Zero) }
    val capsuleShape = rounded(UiStyle.dockCorner)
    val capsuleColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = capsuleAlpha)
    val itemCorner = (UiStyle.dockCorner - 6).coerceAtLeast(6)

    @Composable
    fun IslandItem(t: Tab, iconOnly: Boolean) {
        val sel = t == tab
        // В островке у невыбранных вкладок только значок — долгое нажатие показывает название.
        val tip = rememberButtonHint()
        val bg by animateColorAsState(
            if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            animationSpec = fxSpring(stiffness = Spring.StiffnessMediumLow),
            label = "islandBg",
        )
        val fg = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        val lm = NavSettings.dockLabels
        // «Под значком» и «Рядом» показывают подписи у всех вкладок; «Скрыть» — только значки.
        val showAll = lm != DockLabels.Hidden && (lm == DockLabels.Below || tabs.size <= 3)
        val showTitle = !iconOnly && lm != DockLabels.Hidden && (sel || showAll)
        val padH = if (iconOnly) UiStyle.dockPadH else UiStyle.dockPadH + (if (sel || showTitle) 2 else 0)
        val padV = if (iconOnly) UiStyle.dockPadH else if (lm == DockLabels.Below) (UiStyle.dockPadV - 4).coerceAtLeast(2) else UiStyle.dockPadV
        val switcherOn = t == Tab.Profile && LocalAccountSwitcher.current?.enabled == true
        val itemMod = Modifier.layoutEdit(LScreen.DOCK, t).accountSwitcherGesture(t)
            .hintOnLongPress(tip.state, NavSettings.hints && !showTitle && !switcherOn)
            .pressableHold(rounded(itemCorner), hold = !switcherOn) { onSelect(t) }
            .background(bg)
            .padding(horizontal = padH.dp, vertical = padV.dp)
            .animateContentSize(fxSpring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
        if (lm == DockLabels.Below && !iconOnly) {
            Column(itemMod, horizontalAlignment = Alignment.CenterHorizontally) {
                ZoomBox(UiLayout.dockIconZoom) { Icon(painterResource(t.icon), contentDescription = t.title, tint = fg) }
                Text(
                    t.title, maxLines = 1, color = fg,
                    style = MaterialTheme.typography.labelSmall.scaled(UiStyle.dockLabelScale),
                )
                HintPopup(tip.state, t.title)
            }
        } else {
            Row(itemMod, verticalAlignment = Alignment.CenterVertically) {
                ZoomBox(UiLayout.dockIconZoom) { Icon(painterResource(t.icon), contentDescription = t.title, tint = fg) }
                if (showTitle) {
                    Text(
                        t.title, Modifier.padding(start = 8.dp), maxLines = 1, color = fg,
                        style = MaterialTheme.typography.labelLarge.scaled(UiStyle.dockLabelScale),
                    )
                }
                HintPopup(tip.state, t.title)
            }
        }
    }

    Box(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = UiStyle.dockSide.dp, end = UiStyle.dockSide.dp, top = 4.dp, bottom = UiStyle.dockBottom.dp),
        contentAlignment = when (UiStyle.dockAlign) {
            1 -> Alignment.CenterStart
            2 -> Alignment.CenterEnd
            else -> Alignment.Center
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiStyle.dockDetachGap.dp)) {
            Surface(
                shape = capsuleShape,
                color = capsuleColor,
                tonalElevation = if (capsuleGlass) 0.dp else 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.glassOriginProbe(capsuleProbe)
                    .glassBehind(glassOn, GlassSettings.islandBlurDp, capsuleShape) { capsuleProbe.value },
            ) {
                Row(
                    Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(UiStyle.dockSpacing.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    mainTabs.forEach { t -> IslandItem(t, false) }
                }
            }
            if (lastDetached) {
                // Отдельная круглая кнопка справа от капсулы (например, «Профиль» / настройки).
                Surface(
                    shape = capsuleShape,
                    color = capsuleColor,
                    tonalElevation = if (capsuleGlass) 0.dp else 3.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.glassOriginProbe(detachProbe)
                        .glassBehind(glassOn, GlassSettings.islandBlurDp, capsuleShape) { detachProbe.value },
                ) {
                    Box(Modifier.padding(6.dp)) { IslandItem(tabs.last(), true) }
                }
            }
        }
    }
}

/**
 * Экраны вкладок, которые листаются свайпом влево/вправо. Страница и выбранная вкладка дока связаны в обе стороны:
 * свайп меняет вкладку, нажатие на вкладку в доке листает к странице.
 */
@Composable
private fun TabPager(
    tabs: List<Tab>,
    selected: Tab,
    onSelected: (Tab) -> Unit,
    swipeEnabled: Boolean,
    content: @Composable (Tab) -> Unit,
    searchJump: Boolean = false,
    onSearchJumpConsumed: () -> Unit = {},
    resyncTick: Int = 0,
) {
    val pager = rememberPagerState(initialPage = tabs.indexOf(selected).coerceAtLeast(0)) { tabs.size }
    val curTabs by rememberUpdatedState(tabs)
    val curSelected by rememberUpdatedState(selected)
    val onSel by rememberUpdatedState(onSelected)
    val curSearchJump by rememberUpdatedState(searchJump)
    val onJumpConsumed by rememberUpdatedState(onSearchJumpConsumed)
    // Эффект перехода на поиск: 1 — страница видна полностью, 0 — скрыта (затухание + лёгкое сжатие и сдвиг вниз).
    val jumpFx = remember { Animatable(1f) }
    // Сколько вкладок уже собрано заранее (см. прогрев ниже и перед анимацией перехода).
    var warm by remember { mutableIntStateOf(0) }
    // Вертикальная смена вкладки: -1..1, 0 — страница на месте; знак — в какую сторону уезжает / откуда приезжает страница.
    val slideFx = remember { Animatable(0f) }
    // Стиль анимации: по горизонтали, по вертикали или по положению панели (боковая — вертикально, нижняя — горизонтально).
    val verticalStyle = when (ThemeSettings.tabAnimStyle) {
        1 -> true
        2 -> LocalWide.current
        else -> false
    }
    val curVertical by rememberUpdatedState(verticalStyle)
    // Motion blur при листании: размытие по горизонтали растёт со скоростью страницы (Android 12+).
    val mbOn = ThemeSettings.motionBlur && !ThemeSettings.optimize && android.os.Build.VERSION.SDK_INT >= 31
    val mbStrength = ThemeSettings.motionBlurStrength
    var blurX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pager, mbOn, mbStrength) {
        if (!mbOn) { blurX = 0f; return@LaunchedEffect }
        snapshotFlow { pager.isScrollInProgress }.collect { moving ->
            if (!moving) return@collect
            var last = pager.currentPage + pager.currentPageOffsetFraction
            var lastT = withFrameNanos { it }
            var smooth = 0f
            while (true) {
                val now = withFrameNanos { it }
                val pos = pager.currentPage + pager.currentPageOffsetFraction
                val dt = (now - lastT) / 1_000_000_000f
                val v = if (dt > 0f) kotlin.math.abs(pos - last) / dt else 0f // страниц в секунду
                smooth += (v.coerceAtMost(8f) - smooth) * 0.35f
                blurX = (smooth * 12f * mbStrength).coerceAtMost(60f * mbStrength)
                last = pos; lastT = now
                if (!pager.isScrollInProgress && smooth < 0.2f) break
            }
            blurX = 0f
        }
    }
    // Выбор вкладки по странице — только когда страницу листает сам пользователь (палец/бросок). Всё, что листает
    // приложение (нажатие на вкладку в доке), не должно менять выбранную вкладку по дороге: раньше из-за этого
    // нажатие на «Плейлисты» иногда кидало на «Поиск».
    val programmatic = remember { IntArray(1) } // сколько наших переходов идёт сейчас
    val userDriven = remember { booleanArrayOf(false) } // страницу сейчас листает палец (или бросок после него)
    LaunchedEffect(pager) {
        pager.interactionSource.interactions.collectLatest {
            when (it) {
                // Флаг «листает палец» живёт до конца листания и сбрасывается только там (см. ниже). Раньше он сбрасывался
                // по таймеру после отпускания пальца: если страница уже переходила за середину позже, выбор вкладки терялся,
                // и страница «отскакивала» назад к старой вкладке, а в доке оставалась не та — экран зацикливало туда-сюда.
                is androidx.compose.foundation.interaction.DragInteraction.Start -> userDriven[0] = true
                else -> {}
            }
        }
    }
    // Листание кончилось: палец больше не ведёт страницу. Если страница при этом оказалась не там, где выбранная вкладка
    // (никто из пользователей её не листал), возвращаем её на выбранную — так экран не «уезжает» сам на другой раздел.
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val fm = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collectLatest { moving ->
            if (moving) return@collectLatest
            val byUser = userDriven[0]
            userDriven[0] = false
            if (programmatic[0] > 0) return@collectLatest
            if (byUser) {
                // Листал пользователь: вкладка в доке должна совпасть со страницей, на которой всё остановилось.
                val t = curTabs.getOrNull(pager.currentPage)
                if (t != null && t != curSelected) onSel(t)
                return@collectLatest
            }
            delay(150)
            val i = curTabs.indexOf(curSelected)
            if (i >= 0 && programmatic[0] == 0 && !userDriven[0] && !pager.isScrollInProgress && pager.currentPage != i) {
                if (curSelected != Tab.Search) { kb?.hide(); fm.clearFocus() }
                pager.scrollToPage(i)
            }
        }
    }
    // Страница сменилась свайпом → выбираем её вкладку в доке сразу, как только пройдена половина страницы
    // (а не после полной остановки), поэтому подсветка в доке не опаздывает.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { p ->
            if (programmatic[0] > 0 || !userDriven[0]) return@collect
            val t = curTabs.getOrNull(p) ?: return@collect
            if (t != curSelected) onSel(t)
        }
    }
    // Нажали вкладку в доке (или поменяли порядок экранов) → страница следом. Соседнюю листаем плавно на точное
    // расстояние одной страницы (так анимация не может проскочить дальше нажатой вкладки), дальнюю — тоже плавно, пролистывая промежуточные, на точное расстояние (раньше — сразу).
    LaunchedEffect(selected, tabs) {
        val i = tabs.indexOf(selected)
        val fancy = curSearchJump
        if (fancy) onJumpConsumed()
        if (i < 0) return@LaunchedEffect
        if (pager.currentPage == i) {
            // Страница уже «та», но могла застыть между экранами (переход отменили на середине) — выравниваем.
            if (pager.currentPageOffsetFraction != 0f && !pager.isScrollInProgress) pager.scrollToPage(i)
            return@LaunchedEffect
        }
        programmatic[0]++
        try {
            // Нужная вкладка могла быть ещё не собрана (прогрев идёт по одной): её первая сборка внутри анимации
            // съедала кадры, и переход выглядел как мгновенный скачок. Собираем все вкладки до анимации и ждём пару кадров.
            val need = kotlin.math.abs(pager.currentPage - i).coerceAtMost((tabs.size - 1).coerceAtLeast(0))
            if (warm < need) {
                warm = need
                withFrameNanos { }
                withFrameNanos { }
            }
            // Любая вкладка (в том числе дальняя): страницы пролистываются по пути на точное расстояние, без перелёта.
            // Чем дальше вкладка, тем дольше проскролл. Выключается в Настройки → Внешний вид → Анимации.
            if (!ThemeSettings.optimize && ThemeSettings.animTabScroll && curVertical) {
                // Вертикальный вариант: текущая страница уезжает вверх (вниз) и гаснет, нужная приезжает с противоположной стороны.
                val dir = if (i > pager.currentPage) 1f else -1f
                slideFx.snapTo(0f)
                slideFx.animateTo(-dir, tween(animMs(170), easing = FastOutLinearInEasing))
                pager.scrollToPage(i)
                slideFx.snapTo(dir)
                slideFx.animateTo(0f, tween(animMs(300), easing = FastOutSlowInEasing))
            } else if (!ThemeSettings.optimize && ThemeSettings.animTabScroll && (kotlin.math.abs(pager.currentPage - i) >= 1)) {
                val step = (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing).toFloat()
                if (step > 0f) {
                    val dist = (i - pager.currentPage) * step - pager.currentPageOffsetFraction * pager.layoutInfo.pageSize
                    val hops = kotlin.math.abs(pager.currentPage - i)
                    pager.animateScrollBy(
                        dist,
                        if (hops > 1) tween(animMs(260 + 90 * hops), easing = FastOutSlowInEasing) else fxSpring(stiffness = Spring.StiffnessMediumLow),
                    )
                } else pager.animateScrollToPage(i)
            } else if (!ThemeSettings.optimize) {
                // Проскролл выключен: текущий экран гаснет, нужный появляется на его месте.
                jumpFx.animateTo(0f, tween(animMs(150), easing = FastOutSlowInEasing))
                pager.scrollToPage(i)
                jumpFx.animateTo(1f, tween(animMs(300), easing = FastOutSlowInEasing))
            } else pager.scrollToPage(i)
            // Если страницу всё же «снесло» (долгая первая отрисовка раздела), ставим ровно на нажатую вкладку.
            if (pager.currentPage != i || pager.currentPageOffsetFraction != 0f) pager.scrollToPage(i)
        } finally {
            programmatic[0]--
            if (jumpFx.value != 1f) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { jumpFx.snapTo(1f) }
            if (slideFx.value != 0f) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { slideFx.snapTo(0f) }
        }
    }
    // Началось листание (свайп в сторону) — прячем клавиатуру и снимаем фокус с поля, чтобы она не висела на чужом экране.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { moving ->
            if (moving && programmatic[0] == 0) { keyboard?.hide(); focusManager.clearFocus() }
        }
    }
    // Вкладка сменилась другим путём (док, «назад», кнопка поиска с главной уходит на «Поиск» — там клавиатура
    // нужна, её откроет сам SearchScreen): уходим с поиска — клавиатура прячется.
    LaunchedEffect(selected) {
        if (selected != Tab.Search) { keyboard?.hide(); focusManager.clearFocus() }
    }
    // Чувствительность листания (Настройки → Внешний вид → Навигация): чем выше, тем меньше надо провести пальцем,
    // чтобы страница поехала, и тем меньший сдвиг (без броска) переключает экран.
    val sens = NavSettings.swipeSensitivity
    val baseConfig = LocalViewConfiguration.current
    val pagerConfig = remember(baseConfig, sens) {
        val slopFactor = 1.6f - 1.2f * sens // 0 → ×1.6, 0.5 → ×1, 1 → ×0.4
        object : ViewConfiguration by baseConfig {
            override val touchSlop: Float get() = baseConfig.touchSlop * slopFactor
        }
    }
    val fling = PagerDefaults.flingBehavior(state = pager, snapPositionalThreshold = 0.8f - 0.6f * sens)
    // Вкладки собираются заранее и остаются собранными: смена вкладки больше не строит экран с нуля (это и давало
    // подвисание). Прогреваем по одной после запуска, чтобы не тормозить старт. Вернуть как было: beyondViewportPageCount = 0.
    // Повторное нажатие на уже выбранную вкладку: если страница пейджера разошлась с доком — принудительно выравниваем.
    LaunchedEffect(resyncTick) {
        if (resyncTick == 0) return@LaunchedEffect
        val i = curTabs.indexOf(curSelected)
        if (i >= 0 && !pager.isScrollInProgress && (pager.currentPage != i || pager.currentPageOffsetFraction != 0f)) {
            AppLog.w("Tabs", "страница пейджера (${pager.currentPage}) разошлась с вкладкой ($i) — выравниваем")
            pager.scrollToPage(i)
        }
    }
    LaunchedEffect(tabs.size) {
        delay(2500) // сразу после запуска главный поток занят (JIT, первая сборка экранов) — прогрев откладываем
        while (warm < tabs.size - 1) {
            while (pager.isScrollInProgress) delay(100)
            warm++
            delay(200)
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides pagerConfig) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = warm.coerceAtMost((tabs.size - 1).coerceAtLeast(0)),
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val v = jumpFx.value
                if (v < 1f) {
                    alpha = v
                    val sc = 0.94f + 0.06f * v
                    scaleX = sc; scaleY = sc
                    translationY = (1f - v) * 20.dp.toPx()
                }
                val sv = slideFx.value
                if (sv != 0f) {
                    translationY = sv * size.height * 0.16f
                    alpha = (1f - kotlin.math.abs(sv)).coerceIn(0f, 1f)
                }
                if (mbOn) {
                    val bx = blurX
                    val by = kotlin.math.abs(sv) * 28f * mbStrength
                    if (bx > 0.8f || by > 0.8f) {
                        renderEffect = androidx.compose.ui.graphics.BlurEffect(bx, by, androidx.compose.ui.graphics.TileMode.Clamp)
                    }
                }
            },
            userScrollEnabled = swipeEnabled,
            flingBehavior = fling,
            key = { page -> tabs.getOrNull(page)?.name ?: page },
        ) { page ->
            // Внутри страниц (списки, ряды) порог касания обычный: он меняется только для самого пейджера.
            CompositionLocalProvider(LocalViewConfiguration provides baseConfig) {
                tabs.getOrNull(page)?.let { content(it) }
            }
        }
    }
}

// ---------- Вход ----------

private enum class LoginPage { Intro, Yandex, Vk, SoundCloud, Spotify }

@Composable
private fun LoginScreen(
    vm: AppViewModel,
    error: String?,
    onToken: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    /** Сразу открыть страницу входа через Яндекс (при добавлении аккаунта, когда тип уже выбран). */
    startOnYandex: Boolean = false,
    /** Сразу открыть страницу входа выбранного сервиса (добавление аккаунта): "yandex", "vk" или "soundcloud". */
    startOn: String? = null,
    /** «Пропустить» справа сверху на приветствии: войти без Яндекс Музыки (режим «Локальные файлы»). */
    onSkip: (() -> Unit)? = null,
) {
    val directStart = startOnYandex || startOn != null
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pollJob by remember { mutableStateOf<Job?>(null) }
    var deviceCode by remember { mutableStateOf<DeviceCode?>(null) }
    var requesting by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    // Две страницы: приветствие с кнопками входа и страница с кодом для входа через Яндекс.
    var page by remember {
        mutableStateOf(
            when {
                startOnYandex || startOn == PICK_YANDEX -> LoginPage.Yandex
                startOn == PICK_VK -> LoginPage.Vk
                startOn == PICK_SOUNDCLOUD -> LoginPage.SoundCloud
                // Spotify пока закрыт («В разработке»): остаёмся на приветствии.
                else -> LoginPage.Intro
            },
        )
    }
    // В начале экран входа «заезжает» справа.
    val intro = remember { MutableTransitionState(false).apply { targetState = true } }

    fun openVerification(url: String) {
        androidx.browser.customtabs.CustomTabsIntent.Builder()
            .build()
            .launchUrl(ctx, android.net.Uri.parse(url))
    }

    fun copyCode(code: String, notify: Boolean = true) {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("code", code))
        if (notify) AppToast.success("Код скопирован")
    }

    // Вход через ya.ru/device: получаем код, открываем страницу и ждём подтверждения
    fun startDeviceLogin() {
        pollJob?.cancel()
        authError = null
        deviceCode = null
        requesting = true
        pollJob = scope.launch {
            try {
                val dc = withContext(Dispatchers.IO) { YandexAuth.requestDeviceCode() }
                deviceCode = dc
                requesting = false
                copyCode(dc.userCode, notify = false)
                val token = withContext(Dispatchers.IO) { YandexAuth.pollToken(dc) }
                deviceCode = null
                onToken(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                requesting = false
                deviceCode = null
                authError = YandexAuth.friendlyError(e)
            }
        }
    }

    // Закрытие страницы входа (добавление аккаунта / вход поверх локального режима): весь экран уезжает вправо с затуханием,
    // и только после анимации сообщаем наверх, что экран можно убрать.
    var closing by remember { mutableStateOf(false) }
    val closeProgress by animateFloatAsState(
        targetValue = if (closing) 1f else 0f,
        animationSpec = tween(fxMs(320), easing = FastOutSlowInEasing),
        label = "loginClose",
    )
    fun closeScreen() {
        if (closing || onCancel == null) return
        closing = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            delay(fxMs(320).toLong() + 60L)
            onCancel?.invoke()
        }
    }

    // Назад со страницы входа в Яндекс: останавливаем ожидание и возвращаемся на первую страницу
    fun goBack() {
        pollJob?.cancel()
        pollJob = null
        requesting = false
        deviceCode = null
        authError = null
        if (directStart) closeScreen() else page = LoginPage.Intro
    }

    LaunchedEffect(Unit) { if (startOnYandex || startOn == PICK_YANDEX) startDeviceLogin() }

    // При добавлении аккаунта «назад» на первой странице закрывает экран входа.
    BackHandler(enabled = onCancel != null && page == LoginPage.Intro) { closeScreen() }

    DisposableEffect(Unit) {
        onDispose { pollJob?.cancel() }
    }

    Surface(
        Modifier.fillMaxSize().graphicsLayer {
            if (closeProgress > 0f) {
                translationX = size.width * 0.5f * closeProgress
                alpha = 1f - closeProgress
            }
        },
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedVisibility(
            visibleState = intro,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(fxMs(300))) + slideInHorizontally(tween(fxMs(450))) { it / 2 },
        ) {
        // Приветствие → вход: «мягкий» переход с масштабом; дальше вперёд страница въезжает справа, назад — слева.
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                when {
                    targetState != LoginPage.Intro ->
                        (fadeIn(tween(fxMs(260), delayMillis = 60)) + slideInHorizontally(tween(fxMs(360))) { it }) togetherWith
                            (fadeOut(tween(fxMs(160))) + slideOutHorizontally(tween(fxMs(300))) { -it / 3 })
                    else ->
                        (fadeIn(tween(fxMs(260), delayMillis = 60)) + slideInHorizontally(tween(fxMs(360))) { -it }) togetherWith
                            (fadeOut(tween(fxMs(160))) + slideOutHorizontally(tween(fxMs(300))) { it / 3 })
                }
            },
            label = "loginPages",
        ) { target ->
        when (target) {
            LoginPage.Intro -> LoginIntro(
                error = error,
                onPick = { which ->
                    when (which) {
                        PICK_YANDEX -> { page = LoginPage.Yandex; startDeviceLogin() }
                        PICK_VK -> page = LoginPage.Vk
                        PICK_SOUNDCLOUD -> page = LoginPage.SoundCloud
                        PICK_SPOTIFY -> Unit // В разработке
                    }
                },
                onCancel = if (onCancel != null) ({ closeScreen() }) else null,
                onSkip = if (onCancel == null && !directStart) onSkip else null,
            )

            LoginPage.Vk -> VkLoginPage(vm = vm, onBack = { if (directStart) closeScreen() else page = LoginPage.Intro })

            LoginPage.SoundCloud -> SoundCloudLoginPage(vm = vm, onBack = { if (directStart) closeScreen() else page = LoginPage.Intro })

            LoginPage.Spotify -> SpotifyLoginPage(vm = vm, onBack = { if (directStart) closeScreen() else page = LoginPage.Intro })

            LoginPage.Yandex -> {
                BackHandler { goBack() }
                Box(Modifier.fillMaxSize()) {
                    // Кнопка «назад» — сверху слева
                    IconButton(
                        onClick = { goBack() },
                        modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
                    ) {
                        Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = "Назад")
                    }
                    Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Вход через Яндекс",
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Код уже скопирован. Откройте ya.ru/device и вставьте его — приложение войдёт автоматически",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        // Код в контейнере с моноширинным шрифтом: нажатие копирует его
                        val codePress = rememberPress(deviceCode != null)
                        Surface(
                            shape = containerShape(28),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().pressScale(codePress, 0.04f)
                                .clip(containerShape(28))
                                .combinedClickable(
                                    interactionSource = codePress.source,
                                    indication = LocalIndication.current,
                                    enabled = deviceCode != null,
                                    onClick = {
                                        Haptics.click()
                                        deviceCode?.let { copyCode(it.userCode) }
                                    },
                                ),
                        ) {
                            Box(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 28.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                val dc = deviceCode
                                if (dc != null) {
                                    Text(
                                        dc.userCode,
                                        fontFamily = GoogleSansCode,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 40.sp,
                                        letterSpacing = 4.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else if (requesting) {
                                    LoadingIndicator(Modifier.size(40.dp))
                                } else {
                                    Text(
                                        "— — — —",
                                        fontFamily = GoogleSansCode,
                                        fontSize = 40.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Нажмите на код, чтобы скопировать снова",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        // «Открыть ya.ru/device»
                        FilledTonalButton(
                            onClick = { deviceCode?.let { openVerification(it.verificationUrl) } },
                            enabled = deviceCode != null,
                            modifier = Modifier.height(56.dp),
                            contentPadding = PaddingValues(horizontal = 28.dp),
                            shape = rounded(28),
                        ) {
                            Icon(painterResource(R.drawable.ms_open_in_new), null)
                            Spacer(Modifier.width(8.dp))
                            Text("Открыть ya.ru/device")
                        }
                        (authError ?: error)?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            if (deviceCode == null && !requesting) {
                                TextButton(onClick = { startDeviceLogin() }) { Text("Повторить") }
                            }
                        }
                    }
                }
            }
        }
        }
        }
    }
}

/**
 * Приветствие перед входом (Material 3 Expressive): иконка приложения «вылетает» на пружине с поворотом, фигура-«печенька» медленно вращается; затем слова заголовка по очереди всплывают,
 * а снизу выезжает кнопка «Войти через Яндекс».
 */
@Composable
private fun LoginIntro(
    error: String?,
    onPick: (String) -> Unit,
    onCancel: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val words = remember { listOf("Добро", "пожаловать", "в", "ExMusic!") }
    val iconScale = remember { Animatable(0f) }
    val iconTurn = remember { Animatable(-120f) }
    val wordIn = remember { words.map { Animatable(0f) } }
    val button = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { iconScale.animateTo(1f, fxSpring(dampingRatio = 0.42f, stiffness = 130f)) }
        launch { iconTurn.animateTo(0f, fxSpring(dampingRatio = 0.55f, stiffness = 70f)) }
        delay(650)
        words.indices.forEach { i ->
            launch { wordIn[i].animateTo(1f, fxSpring(dampingRatio = 0.65f, stiffness = 320f)) }
            delay(120)
        }
        delay(200)
        button.animateTo(1f, fxSpring(dampingRatio = 0.6f, stiffness = 300f))
    }

    val loop = androidx.compose.animation.core.rememberInfiniteTransition(label = "introLoop")
    val spinAnim by loop.animateFloat(
        0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "introSpin",
    )
    val floatAnim by loop.animateFloat(
        -1f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "introFloat",
    )
    val driftAnim by loop.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "introDrift",
    )
    // «Оптимизация»: фон и значок на экране приветствия стоят на месте.
    val spin = if (ThemeSettings.optimize) 0f else spinAnim
    val float = if (ThemeSettings.optimize) 0f else floatAnim
    val drift = if (ThemeSettings.optimize) 0f else driftAnim
    val primary = MaterialTheme.colorScheme.primary
    val blobA = MaterialTheme.colorScheme.primaryContainer
    val blobB = MaterialTheme.colorScheme.tertiaryContainer
    val cookie = MaterialShapes.Cookie12Sided.toShape()

    Box(
        Modifier.fillMaxSize().drawBehind {
            // Два больших мягких пятна медленно «плывут» на фоне.
            val r = size.width * 0.95f
            val a = Offset(size.width * (0.1f + 0.15f * drift), size.height * (0.12f + 0.06f * drift))
            val b = Offset(size.width * (0.9f - 0.15f * drift), size.height * (0.88f - 0.06f * drift))
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(blobA.copy(alpha = 0.75f), Color.Transparent), center = a, radius = r),
                radius = r, center = a,
            )
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(blobB.copy(alpha = 0.6f), Color.Transparent), center = b, radius = r),
                radius = r, center = b,
            )
        },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp, vertical = 24.dp)) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(200.dp).graphicsLayer { translationY = float * 8.dp.toPx() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = iconScale.value; scaleY = iconScale.value
                            rotationZ = iconTurn.value + spin
                        }.clip(cookie).background(Color(0xFFB16A4A)),
                    )
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.mipmap.ic_launcher_foreground),
                        contentDescription = "ExMusic",
                        modifier = Modifier.fillMaxSize(0.92f).graphicsLayer {
                            scaleX = iconScale.value; scaleY = iconScale.value
                            rotationZ = iconTurn.value * 0.4f
                            alpha = iconScale.value.coerceIn(0f, 1f)
                        },
                    )
                }
                Spacer(Modifier.height(36.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    words.forEachIndexed { i, w ->
                        Text(
                            w,
                            style = MaterialTheme.typography.displaySmallEmphasized,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (i == words.lastIndex) primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer {
                                val v = wordIn[i].value
                                alpha = v.coerceIn(0f, 1f)
                                translationY = (1f - v) * 48.dp.toPx()
                                val sc = 0.85f + 0.15f * v
                                scaleX = sc; scaleY = sc
                            },
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = button.value.coerceIn(0f, 1f)
                    translationY = (1f - button.value) * 140.dp.toPx()
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                error?.let {
                    Text(
                        it, color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                // Одна кнопка «Дальше»: снизу выезжает шторка с выбором сервиса (Яндекс.Музыка, VK Музыка, SoundCloud).
                Button(
                    onClick = { showPicker = true },
                    enabled = button.value > 0.6f,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = rounded(32),
                ) {
                    Text("Дальше", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                if (onCancel != null) {
                    TextButton(onClick = onCancel) { Text("Отмена", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
        // «Пропустить» — справа сверху: вход без Яндекс Музыки, только файлы устройства.
        if (onSkip != null) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = iconScale.value.coerceIn(0f, 1f) },
            ) { Text("Пропустить", style = MaterialTheme.typography.titleMedium) }
        }
    }
    if (showPicker) {
        ProviderPickerSheet(
            onPick = { showPicker = false; onPick(it) },
            onDismiss = { showPicker = false },
        )
    }
}

@Suppress("SetJavaScriptEnabled")
@Composable
private fun YandexAuthDialog(
    onDismiss: () -> Unit,
    onTokenExtracted: (String) -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            shape = containerShape(28),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Вход в Яндекс ID", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ms_close), contentDescription = "Закрыть")
                    }
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            settings.userAgentString = settings.userAgentString.replace("; wv", "")

                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: android.webkit.WebView?,
                                    request: android.webkit.WebResourceRequest?,
                                ): Boolean {
                                    val url = request?.url?.toString()
                                    val token = if (YandexAuth.isTrustedAuthUrl(url)) YandexAuth.parseToken(url) else null
                                    if (token != null) {
                                        onTokenExtracted(token)
                                        return true
                                    }
                                    // Только https: никаких intent:// , file:// , http:// внутри окна входа.
                                    return request?.url?.scheme != "https"
                                }

                                override fun onPageStarted(
                                    view: android.webkit.WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    val token = if (YandexAuth.isTrustedAuthUrl(url)) YandexAuth.parseToken(url) else null
                                    if (token != null) {
                                        onTokenExtracted(token)
                                    }
                                }
                            }
                            loadUrl(YandexAuth.authorizeUrl().toString())
                        }
                    }
                )
            }
        }
    }
}
/**
 * Значок «Моей волны». Пока волна играет — бегущая волна из столбиков (по ним проходит синусоида);
 * на паузе и когда волна не запущена — обычный статичный значок волны. Переключение — плавное.
 */
@Composable
private fun WaveIcon(playing: Boolean, color: Color, modifier: Modifier = Modifier.size(24.dp)) {
    Crossfade(targetState = playing, animationSpec = tween(fxMs(250)), label = "waveIcon") { on ->
        if (on && !ThemeSettings.optimize) {
            val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "waveRun")
            val phase by t.animateFloat(
                initialValue = 0f,
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(1100, easing = LinearEasing),
                ),
                label = "wavePhase",
            )
            androidx.compose.foundation.Canvas(modifier) {
                val n = 5
                val gap = size.width * 0.09f
                val bw = (size.width - gap * (n - 1)) / n
                for (i in 0 until n) {
                    // каждый столбик сдвинут по фазе — получается волна, бегущая слева направо
                    val k = 0.5f + 0.5f * kotlin.math.sin(phase - i * 0.9f)
                    val h = size.height * (0.28f + 0.72f * k)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(i * (bw + gap), (size.height - h) / 2f),
                        size = Size(bw, h),
                        cornerRadius = CornerRadius(bw / 2f),
                    )
                }
            }
        } else {
            Icon(painterResource(R.drawable.ms_graphic_eq), null, modifier, tint = color)
        }
    }
}

// ---------- Главная (минимум: герой-карточка, плейлисты, любимое) ----------

/** Вложенные страницы главного экрана. */
private enum class HomePage { Chart, History, Podcasts, NewReleases, NewAlbums, NewTracks }

@Composable
private fun HomeScreen(vm: AppViewModel) {
    var page by rememberSaveable { mutableStateOf<HomePage?>(null) }
    InnerPageEffect("home", page != null) { page = null }
    Crossfade(targetState = page, animationSpec = tween(animMs(220)), label = "homePage") { p ->
        when (p) {
            null -> HomeMain(vm, onOpen = { page = it })
            HomePage.Chart -> SwipeBack(onBack = { page = null }) { ChartPage(vm) { page = null } }
            HomePage.History -> SwipeBack(onBack = { page = null }) { HistoryPage(vm) { page = null } }
            HomePage.Podcasts -> SwipeBack(onBack = { page = null }) { PodcastsPage(vm) { page = null } }
            HomePage.NewReleases -> SwipeBack(onBack = { page = null }) { NewReleasesPage(vm, onOpen = { page = it }) { page = null } }
            // Окна «Новые альбомы» / «Новые треки»: «назад» возвращает в «Новинки», вкладки в сторону не листаются.
            HomePage.NewAlbums -> SwipeBack(onBack = { page = HomePage.NewReleases }) { NewAlbumsPage(vm) { page = HomePage.NewReleases } }
            HomePage.NewTracks -> SwipeBack(onBack = { page = HomePage.NewReleases }) { NewTracksPage(vm) { page = HomePage.NewReleases } }
        }
    }
}

@Composable
private fun HomeMain(vm: AppViewModel, onOpen: (HomePage) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showWaveSettings by remember { mutableStateOf(false) }
    var showRecognize by remember { mutableStateOf(false) }
    // Блок «Моей волны» занимает всю верхнюю часть главной. Если он закреплён (Настройки → Внешний вид → Главный экран),
    // то после прокрутки сверху остаётся его компактная версия.
    val pinWave = ThemeSettings.pinWave
    // Плитка относится только к «Моей волне»: если играет волна по исполнителю / альбому / плейлисту, плитка не «горит».
    val homeWaveOn = state.waveOn && state.waveStation == DEFAULT_WAVE
    val wavePlaying = homeWaveOn && state.isPlaying
    // Волна уже в очереди: повторное нажатие ставит на паузу / продолжает, а не запускает новую.
    // SoundCloud: у сервиса своей волны нет, поэтому при запуске ExWave коротко объясняем, что это (можно отключить галочкой).
    var showExWaveHint by remember { mutableStateOf(false) }
    val onWaveStart: () -> Unit = {
        if (homeWaveOn) vm.togglePlay()
        else if (ActiveProvider.isSc && !NavSettings.waveHintHidden(state.activeToken)) showExWaveHint = true
        else vm.startWave()
    }
    if (showExWaveHint) {
        var dontShow by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showExWaveHint = false },
            title = { Text("ExWave") },
            text = {
                Column {
                    Text("Случайный микс из выбранных источников: лайки, похожие треки, подписки и новинки SoundCloud. Источники меняются кнопкой с шестерёнкой, новый микс — кнопкой со стрелками")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { dontShow = !dontShow },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = dontShow, onCheckedChange = { dontShow = it })
                        Text("Больше не показывать")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (dontShow) NavSettings.hideWaveHint(state.activeToken)
                    showExWaveHint = false
                    vm.startWave()
                }) { Text("Запустить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (dontShow) NavSettings.hideWaveHint(state.activeToken)
                    showExWaveHint = false
                }) { Text("Отмена") }
            },
        )
    }
    val onWaveShake: () -> Unit = { vm.startWave() }
    val onWaveSettings: () -> Unit = { showWaveSettings = true }
    // История для главной: серверная (с других устройств) вместе с локальной; пока не загрузилась — локальная.
    val historyList = state.syncedHistory.ifEmpty { state.history }
    val openSearch = LocalOpenSearch.current
    // Поиск на главной: плиткой или значком в углу блока волны — одна настройка для всех сервисов, включая SoundCloud.
    val searchInCorner = NavSettings.homeSearch && NavSettings.homeSearchCorner
    val listState = rememberLazyListState()
    // Компактная «Моя волна» сверху нужна, пока большой блок волны не виден на экране — где бы он ни стоял
    // (первым, в середине или в самом низу списка). Если блок скрыт в редакторе, закреплять нечего.
    val waveFirst = !UiLayout.isHidden(LScreen.HOME, HomeBlock.Wave) && !ActiveProvider.isSc
    val heroGone by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.none { it.key == "wave" } } }

    val wideHome = LocalWide.current
    // Планшет: слева «Моя волна», поиск и плитки, справа плейлисты, история и «Любимое».
    val homeLeftBlocks = setOf(HomeBlock.Wave, HomeBlock.SearchTile, HomeBlock.Recognize,
        HomeBlock.Chart, HomeBlock.New, HomeBlock.HistoryTracks, HomeBlock.Podcasts)
    // VK: подборки «Для вас» подгружаются при открытии главной.
    LaunchedEffect(state.loggedIn, state.provider) { if (state.loggedIn) vm.loadShelves() }
    val homeBlocks: LazyListScope.(Boolean?) -> Unit = { leftSide ->
        // Блоки главной идут в порядке, который задан в редакторе интерфейса (Настройки → Внешний вид → Редактор интерфейса).
        UiLayout.order(LScreen.HOME, HomeBlock.entries).forEach { blk ->
            if (UiLayout.isHidden(LScreen.HOME, blk)) return@forEach
            if (leftSide != null && (blk in homeLeftBlocks) != leftSide) return@forEach
            when (blk) {
                // Приветствие (Настройки → Внешний вид → Главный экран): обычный блок главной, по умолчанию первый, поэтому при
                // прокрутке уходит вместе со списком. Порядок, размер и отступы — в редакторе интерфейса; на планшете не показывается.
                HomeBlock.Greeting -> if (GreetingSettings.enabled && leftSide == null) item(key = "greeting") {
                    LayoutBlockBox(LScreen.HOME, blk) {
                        HomeGreeting(state.account?.name?.ifBlank { null } ?: state.account?.login.orEmpty())
                    }
                }
                HomeBlock.Wave -> item(key = "wave") {
                    LayoutBlockBox(LScreen.HOME, blk) {
                        WaveHero(
                            loading = state.waveLoading,
                            playing = wavePlaying,
                            waveOn = homeWaveOn,
                            searchInCorner = searchInCorner,
                            onSearch = openSearch,
                            onStart = onWaveStart,
                            onShake = onWaveShake,
                            onSettings = onWaveSettings,
                        )
                    }
                }
                // Поиск плиткой (Настройки → Внешний вид → Главный экран → «Поиск на главной»); значком в углу блока волны — другой вариант.
                HomeBlock.SearchTile -> if (NavSettings.homeSearch && !NavSettings.homeSearchCorner) {
                    item(key = "search_tile") {
                        LayoutBlockBox(LScreen.HOME, blk) {
                            HomeTile(
                                title = "Поиск", subtitle = "Треки, исполнители, подкасты", icon = R.drawable.ms_search,
                                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                                content = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                                onClick = openSearch,
                            )
                        }
                    }
                }
                HomeBlock.Playlists -> {
                    if (state.playlists.isNotEmpty()) item(key = "home_playlists") {
                        LayoutBlockBox(LScreen.HOME, blk) {
                            Column {
                                SectionTitle("Плейлисты")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) { items(state.playlists) { PlaylistCard(it) { vm.openPlaylist(it) } } }
                            }
                        }
                    }
                    // VK: подборки «Для вас» и плейлисты, собранные алгоритмами («Плейлист дня», «Дежавю», «Тайник»…).
                    // У SoundCloud подборки и миксы подгружаются дольше всего и вынесены в свой блок ниже плиток (HomeBlock.Mixes),
                    // иначе при их появлении все плитки и кнопки под ними «улетали» вниз.
                    if (ActiveProvider.caps.recommendations && !ActiveProvider.isSc) state.shelves.forEach { shelf ->
                        item(key = "shelf_" + shelf.id) {
                            LayoutBlockBox(LScreen.HOME, blk) {
                                Column {
                                    SectionTitle(shelf.title)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) { items(shelf.playlists, key = { "${it.ownerUid}_${it.kind}" }) { PlaylistCard(it) { vm.openPlaylist(it) } } }
                                }
                            }
                        }
                    }
                }
                // SoundCloud: миксы и подборки плейлистов. Стоят после плиток, поэтому их загрузка ничего не сдвигает.
                HomeBlock.Mixes -> if (ActiveProvider.isSc && ActiveProvider.caps.recommendations) state.shelves.forEach { shelf ->
                    item(key = "shelf_" + shelf.id) {
                        LayoutBlockBox(LScreen.HOME, blk) {
                            Column {
                                SectionTitle(shelf.title)
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) { items(shelf.playlists, key = { "${it.ownerUid}_${it.kind}" }) { PlaylistCard(it) { vm.openPlaylist(it) } } }
                            }
                        }
                    }
                }
                // «Распознать музыку», под ней — «Чарт» и «Новинки» рядом, ниже — история треков и подкасты.
                HomeBlock.Recognize -> item(key = "recognize_tile") {
                    LayoutBlockBox(LScreen.HOME, blk) {
                        HomeTile(
                            title = "Распознать музыку", subtitle = "С микрофона или со звука устройства", icon = R.drawable.ms_mic,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        ) { showRecognize = true }
                    }
                }
                // Четыре плитки — отдельные блоки: каждую можно убрать по одной. Две подряд идущие видимые плитки одной пары
                // («Чарт» и «Новинки», «История треков» и «Подкасты») стоят рядом, одинокая плитка занимает всю ширину.
                // Ширину плитки можно менять в режиме редактора на экране: ряд — 4 слота, по умолчанию плитка занимает 2,
                // растянуть её можно, пока в ряду есть свободные слоты (см. [HomeTileFlow]).
                HomeBlock.Chart, HomeBlock.New, HomeBlock.HistoryTracks, HomeBlock.Podcasts -> {
                    val visible = HomeTileFlow.visible().filter { leftSide == null || (it in homeLeftBlocks) == leftSide }
                    // Ряд рисуется целиком вместе с первой плиткой, остальные плитки ряда сами по себе пропускаем.
                    val row = HomeTileFlow.rows(visible).firstOrNull { it.first() == blk }
                    if (row != null) item(key = "home_tiles_" + blk.name) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { tile ->
                                Box(Modifier.weight(HomeTileFlow.span(tile, row).toFloat())) {
                                    LayoutBlockBox(LScreen.HOME, tile) {
                                        when (tile) {
                                            HomeBlock.Chart -> HomeTile(
                                                title = "Чарт", subtitle = "Топ сейчас", icon = R.drawable.ms_leaderboard,
                                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                                content = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { onOpen(HomePage.Chart) }
                                            HomeBlock.New -> HomeTile(
                                                title = "Новинки", subtitle = if (ActiveProvider.isVk || ActiveProvider.isSc) "Свежие релизы" else "Ваши артисты", icon = R.drawable.ms_new_releases,
                                                container = MaterialTheme.colorScheme.secondaryContainer,
                                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { onOpen(HomePage.NewReleases) }
                                            HomeBlock.HistoryTracks -> HomeTile(
                                                title = "История треков", icon = R.drawable.ms_schedule,
                                                container = MaterialTheme.colorScheme.secondaryContainer,
                                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { onOpen(HomePage.History) }
                                            else -> HomeTile(
                                                title = "Подкасты", icon = R.drawable.ms_podcasts,
                                                container = MaterialTheme.colorScheme.primaryContainer,
                                                content = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) { onOpen(HomePage.Podcasts) }
                                        }
                                    }
                                }
                            }
                            // Свободные слоты ряда (плитки уменьшили) остаются пустыми справа.
                            val free = HomeTileFlow.COLS - row.sumOf { HomeTileFlow.span(it, row) }
                            if (free > 0) Spacer(Modifier.weight(free.toFloat()))
                        }
                    }
                }
                // История прослушивания (синхронизируется с Яндекс Музыкой и SoundCloud).
                HomeBlock.History -> if (historyList.isNotEmpty() || state.syncedHistoryLoading) {
                    item(key = "history_title") {
                        LayoutBlockBox(LScreen.HOME, blk) { SectionLink("История прослушивания") { onOpen(HomePage.History) } }
                    }
                    if (historyList.isEmpty()) {
                        item(key = "history_loading") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(32.dp)) }
                        }
                    }
                    val shown = historyList.take(6)
                    // Нажатие на трек играет только его; вся история в очередь идёт кнопкой «Слушать» на странице истории.
                    itemsIndexed(shown, key = { _, t -> "h_" + t.id }, contentType = { _, _ -> "home_track" }) { _, t ->
                        LayoutBlockBox(LScreen.HOME, blk) { TrackRow(t) { vm.play(listOf(t), 0) } }
                    }
                }
                HomeBlock.Liked -> if (state.liked.isNotEmpty()) {
                    item(key = "liked_title") { LayoutBlockBox(LScreen.HOME, blk) { SectionTitle("Любимое") } }
                    itemsIndexed(state.liked.take(50), contentType = { _, _ -> "home_track" }) { i, t ->
                        LayoutBlockBox(LScreen.HOME, blk) { TrackRow(t) { vm.play(state.liked, i) } }
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    if (wideHome) {
        TwoPane(
            left = {
                PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) { homeBlocks(true) }
            },
            right = {
                PullLazyColumn(vm, Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) { homeBlocks(false) }
            },
        )
    } else {
        PullLazyColumn(
            vm,
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            homeBlocks(null)
        }
    }
    // Компактная «Моя волна» сверху, когда большой блок уже прокручен.
    AnimatedVisibility(
        visible = pinWave && heroGone && waveFirst && !wideHome,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = fadeIn(tween(animMs(180))) + slideInVertically(tween(animMs(220))) { -it },
        exit = fadeOut(tween(animMs(120))) + slideOutVertically(tween(animMs(160))) { -it },
    ) {
        WavePinnedBar(
            loading = state.waveLoading, playing = wavePlaying, waveOn = homeWaveOn,
            onStart = onWaveStart, onShake = onWaveShake, onSettings = onWaveSettings,
        )
    }
    }
    if (showWaveSettings) {
        if (ActiveProvider.isSc) ScWaveSourcesSheet(vm) { showWaveSettings = false }
        else if (ActiveProvider.caps.mixSettings) MixSettingsSheet(vm) { showWaveSettings = false }
        else WaveSettingsSheet(vm) { showWaveSettings = false }
    }
    if (showRecognize) {
        ModalBottomSheet(onDismissRequest = { showRecognize = false }) {
            RecognitionContent(
                onClose = { showRecognize = false },
                inApp = true,
                onOpenInApp = { r -> showRecognize = false; vm.requestRecognizedSearch(r.query); Recognizer.reset() },
            )
        }
    }
}

/** Строка-кнопка поиска на главной: выглядит как поле ввода, нажатие открывает поиск с клавиатурой. */
@Composable
private fun HomeSearchPill(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 4.dp).pressCard(source, onClick),
        shape = rounded(32),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.heightIn(min = 56.dp).indication(source, LocalIndication.current).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ms_search), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            FitText(
                "Треки, исполнители, подкасты",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
            )
        }
    }
}

/** Небольшая прямоугольная плитка главного экрана: значок и подпись. */
@Composable
private fun HomeTile(
    title: String,
    icon: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.pressCard(source, onClick),
        shape = rounded(20),
        color = container,
        contentColor = content,
    ) {
        Row(
            Modifier.heightIn(min = 60.dp).indication(source, LocalIndication.current)
                .padding(horizontal = 16.dp + shapeInset(), vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                FitText(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                if (subtitle != null) {
                    FitText(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }
    }
}

/** Страница «Чарт»: треки по местам, как в Яндекс Музыке. */
@Composable
private fun ChartPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    LaunchedEffect(Unit) { vm.loadChart() }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Чарт", onBack, subtitle = "Самые популярные треки")
        when {
            state.chart.isNotEmpty() -> PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Группа кнопок M3E: нажатая кнопка расширяется, соседняя уступает ей место.
                        val playSrc = remember { MutableInteractionSource() }
                        val shuffleSrc = remember { MutableInteractionSource() }
                        Button(
                            onClick = { vm.play(state.chart, 0) },
                            modifier = groupWeight(playSrc, listOf(shuffleSrc)).height(52.dp),
                            interactionSource = playSrc,
                        ) {
                            Icon(painterResource(R.drawable.ms_play_arrow), null); Spacer(Modifier.width(6.dp)); FitText("Слушать")
                        }
                        FilledTonalButton(
                            onClick = { vm.play(state.chart, 0, shuffle = true) },
                            modifier = groupWeight(shuffleSrc, listOf(playSrc)).height(52.dp),
                            interactionSource = shuffleSrc,
                        ) {
                            Icon(painterResource(R.drawable.ms_shuffle), null); Spacer(Modifier.width(6.dp)); FitText("Вперемешку")
                        }
                    }
                }
                itemsIndexed(state.chart, key = { i, t -> "c_${i}_${t.id}" }) { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${i + 1}",
                            modifier = Modifier.width(44.dp).padding(start = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (i < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TrackRow(t, modifier = Modifier.weight(1f)) { vm.play(state.chart, i) }
                    }
                }
            }
            state.chartLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            else -> EmptyNote(if (state.chartFailed) "Не удалось загрузить чарт" else "Ничего не нашлось") {
                TextButton(onClick = { vm.loadChart(force = true) }) { Text("Повторить") }
            }
        }
    }
}

/**
 * Страница «Новинки»: свежие альбомы и треки артистов, которых вы слушаете чаще всего (по истории и «Любимому»).
 * Свежие — выпущенные за последние четыре месяца.
 */
@Composable
private fun NewReleasesPage(vm: AppViewModel, onOpen: (HomePage) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    LaunchedEffect(Unit) { vm.loadNewReleases() }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Новинки", onBack, subtitle = "Новые релизы ваших любимых артистов")
        when {
            state.newReleases.isNotEmpty() || state.newReleaseTracks.isNotEmpty() -> PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.newReleaseTracks.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val playSrc = remember { MutableInteractionSource() }
                            val shuffleSrc = remember { MutableInteractionSource() }
                            Button(
                                onClick = { vm.play(state.newReleaseTracks, 0) },
                                modifier = groupWeight(playSrc, listOf(shuffleSrc)).height(52.dp),
                                interactionSource = playSrc,
                            ) {
                                Icon(painterResource(R.drawable.ms_play_arrow), null); Spacer(Modifier.width(6.dp)); FitText("Слушать")
                            }
                            FilledTonalButton(
                                onClick = { vm.play(state.newReleaseTracks, 0, shuffle = true) },
                                modifier = groupWeight(shuffleSrc, listOf(playSrc)).height(52.dp),
                                interactionSource = shuffleSrc,
                            ) {
                                Icon(painterResource(R.drawable.ms_shuffle), null); Spacer(Modifier.width(6.dp)); FitText("Вперемешку")
                            }
                        }
                    }
                }
                if (state.newReleases.isNotEmpty()) {
                    item { SectionLink("Новые альбомы", count = state.newReleases.size) { onOpen(HomePage.NewAlbums) } }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) { items(state.newReleases, key = { it.id }) { a -> AlbumCard(a) { vm.openAlbum(a) } } }
                    }
                }
                if (state.newReleaseTracks.isNotEmpty()) {
                    item { SectionLink("Новые треки", count = state.newReleaseTracks.size) { onOpen(HomePage.NewTracks) } }
                    itemsIndexed(state.newReleaseTracks, key = { i, t -> "n_${i}_${t.id}" }) { i, t ->
                        TrackRow(t) { vm.play(state.newReleaseTracks, i) }
                    }
                }
            }
            state.newReleasesLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            else -> EmptyNote(
                if (state.newReleasesFailed) "Не удалось загрузить новинки"
                else "Пока нет новинок от ваших артистов. Слушайте музыку — и свежие релизы появятся здесь",
            ) {
                TextButton(onClick = { vm.loadNewReleases(force = true) }) { Text("Повторить") }
            }
        }
    }
}

/** Окно «Новые альбомы»: все новые релизы ваших артистов сеткой. Вкладки в сторону не листаются. */
@Composable
private fun NewAlbumsPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    PagerLockEffect()
    LaunchedEffect(Unit) { vm.loadNewReleases() }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Новые альбомы", onBack, subtitle = "Свежие релизы ваших любимых артистов")
        when {
            state.newReleases.isNotEmpty() -> PullLazyGrid(
                vm, GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(state.newReleases, key = { it.id }) { a -> AlbumCard(a, width = 150.dp) { vm.openAlbum(a) } }
            }
            state.newReleasesLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            else -> EmptyNote(
                if (state.newReleasesFailed) "Не удалось загрузить новые альбомы" else "Пока нет новых альбомов",
            ) {
                TextButton(onClick = { vm.loadNewReleases(force = true) }) { Text("Повторить") }
            }
        }
    }
}

/** Окно «Новые треки»: все новые треки ваших артистов списком. Вкладки в сторону не листаются. */
@Composable
private fun NewTracksPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    PagerLockEffect()
    LaunchedEffect(Unit) { vm.loadNewReleases() }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Новые треки", onBack, subtitle = "Свежие треки ваших любимых артистов")
        when {
            state.newReleaseTracks.isNotEmpty() -> PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val playSrc = remember { MutableInteractionSource() }
                        val shuffleSrc = remember { MutableInteractionSource() }
                        Button(
                            onClick = { vm.play(state.newReleaseTracks, 0) },
                            modifier = groupWeight(playSrc, listOf(shuffleSrc)).height(52.dp),
                            interactionSource = playSrc,
                        ) {
                            Icon(painterResource(R.drawable.ms_play_arrow), null); Spacer(Modifier.width(6.dp)); FitText("Слушать")
                        }
                        FilledTonalButton(
                            onClick = { vm.play(state.newReleaseTracks, 0, shuffle = true) },
                            modifier = groupWeight(shuffleSrc, listOf(playSrc)).height(52.dp),
                            interactionSource = shuffleSrc,
                        ) {
                            Icon(painterResource(R.drawable.ms_shuffle), null); Spacer(Modifier.width(6.dp)); FitText("Вперемешку")
                        }
                    }
                }
                itemsIndexed(state.newReleaseTracks, key = { i, t -> "nt_${i}_${t.id}" }) { i, t ->
                    TrackRow(t) { vm.play(state.newReleaseTracks, i) }
                }
            }
            state.newReleasesLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            else -> EmptyNote(
                if (state.newReleasesFailed) "Не удалось загрузить новые треки" else "Пока нет новых треков",
            ) {
                TextButton(onClick = { vm.loadNewReleases(force = true) }) { Text("Повторить") }
            }
        }
    }
}

/** Страница «История треков»: всё, что слушали на всех устройствах аккаунта (плюс на этом устройстве). */
@Composable
private fun HistoryPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    LaunchedEffect(Unit) { vm.loadSyncedHistory(force = true) }
    val list = state.syncedHistory.ifEmpty { state.history }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("История треков", onBack, subtitle = if (ActiveProvider.isVk) "Недавно прослушанное в VK" else if (ActiveProvider.isSc) "Синхронизируется с SoundCloud" else "Синхронизируется с Яндекс Музыкой")
        when {
            list.isNotEmpty() -> PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Группа кнопок M3E: нажатая кнопка расширяется, соседняя уступает ей место.
                        val playSrc = remember { MutableInteractionSource() }
                        val shuffleSrc = remember { MutableInteractionSource() }
                        Button(
                            onClick = { vm.play(list, 0) },
                            modifier = groupWeight(playSrc, listOf(shuffleSrc)).height(52.dp),
                            interactionSource = playSrc,
                        ) {
                            Icon(painterResource(R.drawable.ms_play_arrow), null); Spacer(Modifier.width(6.dp)); FitText("Слушать")
                        }
                        FilledTonalButton(
                            onClick = { vm.play(list, 0, shuffle = true) },
                            modifier = groupWeight(shuffleSrc, listOf(playSrc)).height(52.dp),
                            interactionSource = shuffleSrc,
                        ) {
                            Icon(painterResource(R.drawable.ms_shuffle), null); Spacer(Modifier.width(6.dp)); FitText("Вперемешку")
                        }
                    }
                }
                itemsIndexed(list, key = { i, t -> "hp_${i}_${t.id}" }) { _, t -> TrackRow(t) { vm.play(listOf(t), 0) } }
            }
            state.syncedHistoryLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            else -> EmptyNote("Ничего не нашлось") {}
        }
    }
}

/** Страница «Подкасты»: подборка и поиск по подкастам. Выпуски открываются как альбом. */
@Composable
private fun PodcastsPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Открыли что-то из результатов — запрос уходит в историю поиска.
    val hideKeyboard: () -> Unit = { SearchHistory.add(query); keyboard?.hide(); focusManager.clearFocus() }
    // Пустой запрос — подборка (грузится сразу), непустой — поиск с небольшой задержкой на ввод.
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(400)
        vm.searchPodcastsPage(query)
    }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Подкасты", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Найти подкаст") },
            leadingIcon = { Icon(painterResource(R.drawable.ms_search), null) },
            singleLine = true,
            shape = rounded(32),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.searchPodcastsPage(query); hideKeyboard() }),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        when {
            state.podcasts.isNotEmpty() -> PullLazyGrid(
                vm, GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                gridItems(state.podcasts, key = { it.id }) { a -> AlbumCard(a, width = 150.dp, showWave = false) { hideKeyboard(); vm.openAlbum(a) } }
            }
            state.podcastsLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            else -> EmptyNote("Ничего не нашлось") {}
        }
    }
}

/** Пустое состояние страницы: короткая надпись и, при необходимости, кнопка действия. */
@Composable
private fun EmptyNote(text: String, action: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action()
    }
}

/**
 * Верхняя часть главной: большой блок «Моей волны». Сверху — «Встряхнуть» и «Настройки» (и значок поиска в углу, если выбран
 * такой вариант), снизу — большая кнопка «Моя волна» с бегущей волной, которая же ставит волну на паузу. Пока волна играет,
 * светится сама кнопка «Моя волна», а не весь блок (Настройки → Внешний вид → Свечение → «Свечение «Моей волны»»).
 * Фон блока рисуется без обрезки (не через Surface), чтобы свет кнопки выходил за её края поверх блока.
 */
@Composable
private fun WaveHero(
    loading: Boolean,
    playing: Boolean,
    waveOn: Boolean,
    searchInCorner: Boolean,
    onSearch: () -> Unit,
    onStart: () -> Unit,
    onShake: () -> Unit,
    onSettings: () -> Unit,
) {
    val tonal = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    Box(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, rounded(32)),
    ) {
      CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp + shapeInset() / 2, vertical = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onShake, enabled = !loading, colors = tonal, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ms_refresh), contentDescription = if (ActiveProvider.isVk) "Встряхнуть VK Микс" else if (ActiveProvider.isSc) "Новая волна" else "Встряхнуть Мою волну")
                }
                FilledTonalIconButton(onClick = onSettings, colors = tonal, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ms_settings), contentDescription = if (ActiveProvider.isVk) "Настройки VK Микса" else if (ActiveProvider.isSc) "Источники волны" else "Настройки Моей волны")
                }
                Spacer(Modifier.weight(1f))
                if (searchInCorner) {
                    FilledTonalIconButton(onClick = onSearch, colors = tonal, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ms_search), contentDescription = "Поиск")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            WaveMainButton(loading = loading, playing = playing, waveOn = waveOn, onClick = onStart, modifier = Modifier.fillMaxWidth())
        }
      }
    }
}

/** Компактная «Моя волна», закреплённая сверху, когда большой блок главной прокручен. */
@Composable
private fun WavePinnedBar(
    loading: Boolean,
    playing: Boolean,
    waveOn: Boolean,
    onStart: () -> Unit,
    onShake: () -> Unit,
    onSettings: () -> Unit,
) {
    // Непрозрачный фон: карточки уходят под панель, а не просвечивают сквозь неё. Свечение — у самой кнопки «Моя волна».
    // Не Surface: он обрезает содержимое по границам, а свет кнопки должен выходить за них.
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WaveMainButton(loading = loading, playing = playing, waveOn = waveOn, onClick = onStart, modifier = Modifier.weight(1f), compact = true)
            FilledTonalIconButton(onClick = onShake, enabled = !loading, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ms_refresh), contentDescription = if (ActiveProvider.isVk) "Встряхнуть VK Микс" else if (ActiveProvider.isSc) "Встряхнуть волну" else "Встряхнуть Мою волну")
            }
            FilledTonalIconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(R.drawable.ms_settings), contentDescription = if (ActiveProvider.isVk) "Настройки VK Микса" else if (ActiveProvider.isSc) "Источники волны" else "Настройки Моей волны")
            }
        }
    }
}

/**
 * Большая кнопка «Моя волна»: на фоне бегущие волны (пока играет; на паузе почти замирают), слева значок-волна,
 * справа — «пауза» или «играть». Нажатие запускает волну, а если она уже в очереди — ставит на паузу и продолжает.
 */
@Composable
private fun WaveMainButton(
    loading: Boolean,
    playing: Boolean,
    waveOn: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        haptic = HapticKind.Heavy,
        enabled = !loading,
        modifier = modifier.height(if (compact) 56.dp else 76.dp)
            // Свечение рисуется под кнопкой и повторяет её форму; пока играет именно «Моя волна».
            .playGlow(
                active = playing, enabled = ThemeSettings.glowWave,
                color = MaterialTheme.colorScheme.primary,
                cornerRadius = if (compact) 24.dp else 28.dp,
                spread = if (compact) 12.dp else 24.dp,
                strength = if (compact) 0.5f else 0.65f,
                shape = rounded(if (compact) 24 else 28),
            ),
        shape = rounded(if (compact) 24 else 28),
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            WaveBackdrop(playing = playing, color = LocalContentColor.current, modifier = Modifier.matchParentSize())
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp + shapeInset()), verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    LoadingIndicator(Modifier.size(28.dp), color = LocalContentColor.current)
                } else {
                    WaveIcon(playing = playing, color = LocalContentColor.current)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ActiveProvider.waveTitle, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1)
                    if (!compact) {
                        Text(
                            when {
                                loading -> "Подбираем музыку…"
                                playing -> "Играет · нажмите для паузы"
                                waveOn -> "На паузе · нажмите, чтобы продолжить"
                                else -> "Нажмите, чтобы запустить"
                            },
                            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Crossfade(targetState = playing, animationSpec = tween(fxMs(200)), label = "waveBtnState") { on ->
                    Icon(
                        painterResource(if (on) R.drawable.ms_pause else R.drawable.ms_play_arrow),
                        contentDescription = if (on) "Пауза" else "Играть",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

/**
 * Волна («вода») на фоне кнопки: одна мягкая синусоида. Пока [playing] — плавно бежит, иначе успокаивается
 * (амплитуда падает, фаза замирает на месте). При включённой «Оптимизации» не рисуется вовсе.
 * Бесконечная анимация работает, только пока играет.
 *
 * Если включено «Вода реагирует на наклон» (Настройки → Внешний вид), уровень воды следует за акселерометром:
 * телефон наклонили — вода стекает в нижнюю сторону, покачали — плещется (пружина с лёгким «перелётом»).
 */
@Composable
private fun WaveBackdrop(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    if (ThemeSettings.optimize) return
    val amp by animateFloatAsState(targetValue = if (playing) 1f else 0.2f, animationSpec = tween(700), label = "waveBackdropAmp")
    val phase: State<Float>? = if (playing) {
        val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "waveBackdrop")
        tr.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(3600, easing = LinearEasing)),
            label = "waveBackdropPhase",
        )
    } else null
    // Наклон телефона: датчик → пружина. Читается только в Canvas, поэтому перекомпозиции от датчика нет.
    val tiltOn = ThemeSettings.waveTilt
    val tiltRaw = rememberTilt(tiltOn)
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(tiltOn) {
        if (!tiltOn) {
            tilt.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
        } else {
            snapshotFlow { tiltRaw.value }.collectLatest { target ->
                tilt.animateTo(target, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    // Последняя фаза: на паузе волна замирает там, где была, а не прыгает в начало.
    val last = remember { FloatArray(1) }
    Canvas(modifier) {
        val ph = phase?.value ?: last[0]
        last[0] = ph
        val w = size.width
        val h = size.height
        val twoPi = (2 * Math.PI).toFloat()
        val tl = tilt.value
        // Чем сильнее вода «догоняет» наклон (пружина ещё качается), тем выше рябь.
        val churn = if (tiltOn) (kotlin.math.abs(tilt.targetValue - tl) * 1.6f).coerceAtMost(1.5f) else 0f
        val path = androidx.compose.ui.graphics.Path()
        val base = h * 0.68f
        val ampPx = h * 0.08f * (amp + churn) + h * 0.01f
        path.moveTo(0f, h)
        var x = 0f
        while (x <= w + 8f) {
            // Поверхность наклонена: справа глубже при tl > 0 (правый край ниже), слева глубже при tl < 0.
            val slope = -tl * h * 0.7f * (x / w - 0.5f)
            val y = (base + slope + ampPx * kotlin.math.sin(x / w * twoPi * 1.4f + ph)).coerceIn(0f, h)
            path.lineTo(x, y)
            x += 8f
        }
        path.lineTo(w, h)
        path.close()
        drawPath(path, color.copy(alpha = 0.13f))
    }
}

/** Кнопка «Моя волна» и кнопка её настроек. Пока волна играет, значок — бегущая волна; иначе статичный значок волны. */
@Composable
private fun WaveRow(
    loading: Boolean,
    playing: Boolean,
    waveOn: Boolean,
    onStart: () -> Unit,
    onShake: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onStart,
            haptic = HapticKind.Heavy,
            enabled = !loading,
            modifier = Modifier.weight(1f).height(64.dp)
                .playGlow(
                    active = playing, enabled = ThemeSettings.glowWave,
                    color = MaterialTheme.colorScheme.primary, cornerRadius = 32.dp, spread = 12.dp, strength = 0.6f,
                    shape = rounded(32),
                ),
            shape = rounded(32),
        ) {
            if (loading) {
                LoadingIndicator(Modifier.size(28.dp), color = LocalContentColor.current)
            } else {
                WaveIcon(playing = playing, color = LocalContentColor.current)
            }
            Spacer(Modifier.width(8.dp))
            Text(ActiveProvider.waveTitle, style = MaterialTheme.typography.titleMediumEmphasized)
        }
        // Пока волна в очереди, между ней и настройками появляется «Встряхнуть»: подбирает новую подборку.
        AnimatedVisibility(
            visible = waveOn,
            enter = fadeIn(tween(fxMs(200))) + expandHorizontally(fxSpring(dampingRatio = 0.6f, stiffness = 800f)) +
                scaleIn(fxSpring(dampingRatio = 0.6f, stiffness = 800f), initialScale = 0.5f),
            exit = fadeOut(tween(fxMs(150))) + shrinkHorizontally(tween(fxMs(200))) + scaleOut(tween(fxMs(150)), targetScale = 0.5f),
        ) {
            FilledTonalIconButton(
                onClick = onShake,
                enabled = !loading,
                modifier = Modifier.padding(end = 0.dp).size(64.dp),
            ) {
                Icon(painterResource(R.drawable.ms_refresh), contentDescription = if (ActiveProvider.isVk) "Встряхнуть VK Микс" else if (ActiveProvider.isSc) "Встряхнуть волну" else "Встряхнуть Мою волну")
            }
        }
        FilledTonalIconButton(
            onClick = onSettings,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(painterResource(R.drawable.ms_settings), contentDescription = if (ActiveProvider.isVk) "Настройки VK Микса" else if (ActiveProvider.isSc) "Источники волны" else "Настройки Моей волны")
        }
    }
}

@Composable
private fun WaveSettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    // Только настройки волны, а не всё состояние плеера (оно меняется каждую долю секунды и перерисовывало бы шторку).
    val waveSettings by remember(vm) {
        vm.state.map { it.waveSettings }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = vm.state.value.waveSettings)
    LaunchedEffect(Unit) { vm.loadWaveSettings() }
    var draft by remember(waveSettings) { mutableStateOf(waveSettings) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp),
        ) {
            Text("Настройки «Моей волны»", style = MaterialTheme.typography.headlineSmallEmphasized)
            WaveOptionGroup(
                "Характер",
                listOf("Любимое" to "favorite", "Незнакомое" to "discover", "Популярное" to "popular", "По умолчанию" to "default"),
                draft.diversity,
            ) { draft = draft.copy(diversity = it) }
            WaveOptionGroup(
                "Настроение",
                listOf("Бодрое" to "active", "Весёлое" to "fun", "Спокойное" to "calm", "Грустное" to "sad", "Любое" to "all"),
                draft.moodEnergy,
            ) { draft = draft.copy(moodEnergy = it) }
            WaveOptionGroup(
                "Язык",
                listOf("Русский" to "russian", "Иностранный" to "not-russian", "Без слов" to "without-words", "Любой" to "any"),
                draft.language,
            ) { draft = draft.copy(language = it) }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.saveWaveSettings(draft); onDismiss() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = rounded(28),
            ) { Text("Применить") }
        }
    }
}

@Composable
private fun WaveOptionGroup(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

/**
 * Растягивает содержимое на [extra] в каждую сторону за пределы родительских отступов.
 * Нужен там, где строки списка (у них свой отступ 16dp) лежат внутри экрана с собственным боковым отступом.
 */
private fun Modifier.bleedH(extra: Dp): Modifier = layout { measurable, constraints ->
    val e = extra.roundToPx()
    val w = if (constraints.hasBoundedWidth) constraints.maxWidth + e * 2 else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-e, 0) }
}

@Composable
private fun SectionTitle(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleLargeEmphasized,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
)

@Composable
private fun PlaylistCard(
    p: PlaylistInfo,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val glowing = LocalPlayingSource.current == playlistSourceKey(p)
    val waveActions = LocalWaveActions.current
    Column(
        modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier.width(140.dp))
            .pressCard(source, onClick, onLongClick),
    ) {
        // Свечение рисуется под обложкой и до её clip, поэтому видно вокруг неё.
        Box(
            Modifier.playGlow(
                active = glowing, enabled = ThemeSettings.glowPlaylists,
                color = MaterialTheme.colorScheme.primary, cornerRadius = 28.dp, spread = 14.dp,
                shape = rounded(28),
            ),
        ) {
            Cover(
                model = p.cover(),
                modifier = (if (fill) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(140.dp))
                    .clip(rounded(28))
                    .indication(source, LocalIndication.current),
            )
            if (waveActions != null) {
                WaveBadge({ waveActions.playlist(p) }, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        FitText(
            p.title, maxLines = 2,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            tracksWord(p.trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun TrackRow(t: Track, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val openArtist = LocalOpenArtist.current
    val addToPlaylist = LocalAddToPlaylist.current
    val openAlbum = LocalOpenAlbum.current
    val showInfo = LocalShowTrackInfo.current
    val waveActions = LocalWaveActions.current
    // Без сети треки, которых нет на устройстве, приглушены (играть их нельзя).
    val unavailable = LocalOffline.current && !t.isLocal && LocalDownloads.current.items.none { it.id == t.id }
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                // Приписка к названию («минус», «a.k.a. …»): серым после названия, нажатие открывает информацию о треке.
                t.version?.let { v ->
                    Text(
                        v, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).widthIn(max = 150.dp)
                            .clip(textShape(6)).clickable { showInfo(t) }.padding(horizontal = 4.dp),
                    )
                }
                if (t.explicit) { Spacer(Modifier.width(6.dp)); ExplicitMark() }
            }
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackMarks(t)
                Text(
                    t.artists, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).then(
                        if (t.artistRefs.isNotEmpty())
                            Modifier.clip(textShape(6)).clickable { openArtist(t.artistRefs.first()) }
                        else Modifier
                    ),
                )
            }
        },
        leadingContent = {
            Box {
                Cover(model = t.cover(200), modifier = Modifier.size(52.dp).clip(rounded(14)))
                MixPickBadge(t, Modifier.size(52.dp))
            }
        },
        trailingContent = {
            if (!t.isLocal) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ms_more_vert), contentDescription = "Меню трека")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Добавить в плейлист") },
                        leadingIcon = { Icon(painterResource(R.drawable.ms_add), null) },
                        onClick = { menu = false; addToPlaylist(t) },
                    )
                    if (t.albumId != null) {
                        DropdownMenuItem(
                            text = { Text("Перейти к альбому") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_library_music), null) },
                            onClick = { menu = false; openAlbum(t.albumId, t.albumTitle) },
                        )
                    }
                    if (waveActions != null) {
                        DropdownMenuItem(
                            text = { Text("Включить волну") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_graphic_eq), null) },
                            onClick = { menu = false; waveActions.track(t) },
                        )
                    }
                    MixPickMenuItem(t) { menu = false }
                    TrackActionItems(t) { menu = false }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Убрать из плейлиста") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_delete), null) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
            }
        },
        colors = MixPicker.rowColors(t),
        // Выразительная строка: крупное скругление, нажатие подсвечивает всю «плитку».
        // Долгое нажатие выбирает трек для микса; пока что-то выбрано, обычное нажатие отмечает/снимает трек.
        modifier = modifier.padding(horizontal = 8.dp).clip(containerShape(24))
            .combinedClickable(
                onClick = { if (MixPicker.active) MixPicker.toggle(t) else onClick() },
                onLongClick = { MixPicker.toggle(t) },
            )
            .alpha(if (unavailable) 0.4f else 1f),
    )
}

// ---------- Поиск ----------

@Composable
private fun SearchScreen(vm: AppViewModel, active: Boolean = true) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Поле помнит последний запрос: вернулись на вкладку — в поле тот же текст, что и у результатов под ним.
    var query by remember { mutableStateOf(vm.state.value.searchedQuery) }
    // Запрос из распознавания музыки: подставляем в поле и сразу ищем.
    val searchRequest by vm.searchRequest.collectAsStateWithLifecycle()
    LaunchedEffect(searchRequest) {
        searchRequest?.let { q -> query = q; vm.search(q); vm.consumeSearchRequest() }
    }
    // Поиск по файлам устройства идёт сразу при вводе (с небольшой задержкой).
    LaunchedEffect(query) { delay(250); vm.searchLocal(query) }
    // Живой поиск: результаты в Яндекс Музыке появляются, пока вы печатаете (Настройки → Внешний вид → Навигация).
    // Пустое поле очищает результаты. Выключено — как раньше, поиск по «Enter».
    val liveSearch = NavSettings.liveSearch
    LaunchedEffect(query, liveSearch) {
        val q = query.trim()
        if (q.isEmpty()) {
            if (vm.state.value.searchedQuery.isNotEmpty()) vm.clearSearch()
            return@LaunchedEffect
        }
        if (!liveSearch || q == vm.state.value.searchedQuery) return@LaunchedEffect
        delay(350) // не шлём запрос на каждую букву
        vm.search(q, live = true)
    }

    // Клавиатура: при открытии поиска всегда появляется; при прокрутке страницы вниз прячется,
    // а когда вернулись наверх до упора — появляется снова.
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Нажали на карточку (исполнитель, альбом, подкаст, трек) — клавиатура прячется и поле теряет фокус.
    val hideKeyboard: () -> Unit = { keyboard?.hide(); focusManager.clearFocus() }
    // Автоматическое появление клавиатуры — по настройке (Оформление → Поиск), по умолчанию выключено.
    // «Поиск» живёт в пейджере вместе с другими вкладками и может быть собран, когда выбрана не она (соседняя страница
    // при листании). Такой «неактивный» поиск не имеет права брать фокус и открывать клавиатуру: иначе при нажатии на
    // «Плейлисты» система прокручивала пейджер к полю ввода, на секунду показывала поиск, а клавиатура оставалась.
    val autoKeyboard = ThemeSettings.searchAutoKeyboard
    val activeNow by rememberUpdatedState(active)
    LaunchedEffect(autoKeyboard, active) {
        if (autoKeyboard && active) {
            delay(120) // даём экрану появиться, иначе клавиатура открывается рывком
            if (!activeNow) return@LaunchedEffect
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }
    // Ушли с «Поиска» — клавиатура прячется, поле теряет фокус.
    LaunchedEffect(active) {
        if (!active) { keyboard?.hide(); focusManager.clearFocus() }
    }
    // Кнопка поиска на главной: ставим курсор в поле и открываем клавиатуру (когда «Поиск» уже стал выбранной вкладкой).
    val focusPending by vm.searchFocusRequest.collectAsStateWithLifecycle()
    LaunchedEffect(focusPending, active) {
        if (focusPending && active) {
            delay(150)
            if (!activeNow) return@LaunchedEffect
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
            vm.consumeSearchFocus()
        }
    }
    LaunchedEffect(listState) {
        var hiddenByScroll = false
        snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 12 }
            .distinctUntilChanged()
            .collect { scrolled ->
                if (scrolled) {
                    hiddenByScroll = true
                    keyboard?.hide()
                    focusManager.clearFocus()
                } else if (hiddenByScroll) {
                    hiddenByScroll = false
                    if (ThemeSettings.searchAutoKeyboard && activeNow) {
                        runCatching { focusRequester.requestFocus() }
                        keyboard?.show()
                    }
                }
            }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Треки, исполнители, подкасты") },
            leadingIcon = { Icon(painterResource(R.drawable.ms_search), null) },
            // Пока идёт запрос — индикатор, иначе (если что-то введено) — кнопка «Очистить».
            trailingIcon = {
                if (state.searchLoading) {
                    LoadingIndicator(Modifier.size(28.dp))
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ms_close), contentDescription = "Очистить поиск")
                    }
                }
            },
            singleLine = true,
            shape = rounded(32),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(query); SearchHistory.add(query); keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                .focusProperties { canFocus = active } // на неактивной вкладке поле вообще не может получить фокус
                .focusRequester(focusRequester),
        )
        // Планшет: слева альбомы, подкасты и исполнители, справа треки. Пустая колонка говорит, чего не нашлось.
        val wideSearch = LocalWide.current && query.isNotBlank()
        if (wideSearch) {
            val localShown = if (LocalFilesSettings.enabled) state.searchLocal else emptyList()
            val anyLeft = state.searchArtists.isNotEmpty() || state.searchAlbums.isNotEmpty() || state.searchPodcasts.isNotEmpty()
            val anyRight = state.searchResults.isNotEmpty() || localShown.isNotEmpty()
            val finished = state.searchedQuery == query.trim()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!anyLeft && !anyRight) {
                    if (finished) SearchEmptyNote("Ничего не найдено", Modifier.fillMaxSize())
                } else TwoPane(
                    left = {
                        if (anyLeft) {
                            PullLazyGrid(
                                vm,
                                columns = GridCells.Adaptive(116.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                searchQuery = query,
                            ) {
                                if (state.searchArtists.isNotEmpty()) {
                                    item(key = "ws_artists", span = { GridItemSpan(maxLineSpan) }) { SearchPaneTitle("Исполнители") }
                                    gridItems(state.searchArtists.take(24)) { a ->
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                                            ArtistCard(a) { hideKeyboard(); vm.openArtist(a) }
                                        }
                                    }
                                }
                                if (state.searchAlbums.isNotEmpty()) {
                                    item(key = "ws_albums", span = { GridItemSpan(maxLineSpan) }) { SearchPaneTitle("Альбомы") }
                                    gridItems(state.searchAlbums.take(30)) { a ->
                                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                                            AlbumCard(a, width = maxWidth) { hideKeyboard(); vm.openAlbum(a) }
                                        }
                                    }
                                }
                                if (state.searchPodcasts.isNotEmpty()) {
                                    item(key = "ws_podcasts", span = { GridItemSpan(maxLineSpan) }) { SearchPaneTitle("Подкасты") }
                                    gridItems(state.searchPodcasts.take(30)) { a ->
                                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                                            AlbumCard(a, width = maxWidth, showWave = false) { hideKeyboard(); vm.openAlbum(a) }
                                        }
                                    }
                                }
                            }
                        } else if (finished) {
                            SearchEmptyNote("Альбомы, подкасты и исполнители не найдены", Modifier.fillMaxSize())
                        }
                    },
                    right = {
                        if (anyRight) {
                            PullLazyColumn(
                                vm,
                                Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(bottom = 24.dp),
                                searchQuery = query,
                            ) {
                                if (localShown.isNotEmpty()) {
                                    item(key = "ws_local_title") { SectionTitle("С устройства") }
                                    itemsIndexed(localShown) { i, t ->
                                        TrackRow(t) { hideKeyboard(); vm.play(localShown, i) }
                                    }
                                }
                                if (state.searchResults.isNotEmpty()) {
                                    item(key = "ws_tracks_title") { SectionTitle("Треки") }
                                    itemsIndexed(state.searchResults) { i, t ->
                                        TrackRow(t) { hideKeyboard(); vm.play(state.searchResults, i) }
                                    }
                                }
                            }
                        } else if (finished) {
                            SearchEmptyNote("Треки не найдены", Modifier.fillMaxSize())
                        }
                    },
                )
            }
        } else PullLazyColumn(
            vm,
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            searchQuery = query,
        ) {
            // История поиска: пока поле пустое. Нажатие повторяет запрос, крестик убирает одну запись, «Очистить» — всю историю.
            if (SearchHistory.enabled && query.isBlank() && SearchHistory.items.isNotEmpty()) {
                item(key = "search_history_head") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("История поиска", style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                        TextButton(onClick = { SearchHistory.clear() }) { Text("Очистить") }
                    }
                }
                items(SearchHistory.items, key = { "sh_$it" }) { q ->
                    SearchHistoryRow(
                        q,
                        modifier = removeAnim(),
                        onClick = { query = q; vm.search(q); SearchHistory.add(q); keyboard?.hide(); focusManager.clearFocus() },
                        onRemove = { SearchHistory.remove(q) },
                    )
                }
            }
            if (LocalFilesSettings.enabled && state.searchLocal.isNotEmpty()) {
                item { SectionTitle("С устройства") }
                itemsIndexed(state.searchLocal) { i, t ->
                    TrackRow(t) { hideKeyboard(); vm.play(state.searchLocal, i) }
                }
            }
            if (state.searchArtists.isNotEmpty()) {
                item { SectionTitle("Исполнители") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { items(state.searchArtists.take(10)) { a -> ArtistCard(a) { hideKeyboard(); vm.openArtist(a) } } }
                }
            }
            if (state.searchAlbums.isNotEmpty()) {
                item { SectionTitle("Альбомы") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { items(state.searchAlbums.take(12)) { a -> AlbumCard(a) { hideKeyboard(); vm.openAlbum(a) } } }
                }
            }
            if (state.searchPodcasts.isNotEmpty()) {
                item { SectionTitle("Подкасты") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { items(state.searchPodcasts.take(12)) { a -> AlbumCard(a, showWave = false) { hideKeyboard(); vm.openAlbum(a) } } }
                }
            }
            val hasOther = state.searchArtists.isNotEmpty() || state.searchAlbums.isNotEmpty() ||
                state.searchPodcasts.isNotEmpty() || (LocalFilesSettings.enabled && state.searchLocal.isNotEmpty())
            if (hasOther && state.searchResults.isNotEmpty()) {
                item { SectionTitle("Треки") }
            }
            itemsIndexed(state.searchResults) { i, t ->
                TrackRow(t) { hideKeyboard(); vm.play(state.searchResults, i) }
            }
            // Поиск по этому запросу завершился, а показывать нечего.
            if (!hasOther && state.searchResults.isEmpty() && query.isNotBlank() && state.searchedQuery == query.trim()) {
                item(key = "nothing_found") {
                    Text(
                        "Ничего не нашлось",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Подпись пустой области поиска на планшете («Треки не найдены», «Ничего не найдено»): по центру области. */
@Composable
private fun SearchEmptyNote(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun SearchPaneTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

/** Строка истории поиска: значок, запрос и крестик, который убирает его из истории. */
@Composable
private fun SearchHistoryRow(q: String, modifier: Modifier = Modifier, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ms_history), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            q, modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ms_close), contentDescription = "Удалить из истории поиска")
        }
    }
}

@Composable
private fun ArtistCard(a: ArtistInfo, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val waveActions = LocalWaveActions.current
    Column(
        Modifier.width(104.dp).pressCard(source, onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Cover(model = a.cover(200), modifier = Modifier.size(96.dp).clip(ThemeSettings.avatarShape.asShape()).indication(source, LocalIndication.current))
            if (waveActions != null) {
                WaveBadge({ waveActions.artist(ArtistRef(a.id, a.name, a.coverUri)) }, Modifier.align(Alignment.BottomEnd), size = 32.dp)
            }
        }
        Text(
            a.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AlbumCard(a: AlbumInfo, width: Dp = 140.dp, modifier: Modifier = Modifier, showWave: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val waveActions = LocalWaveActions.current
    Column(modifier.width(width).pressCard(source, onClick)) {
        Box {
            Cover(
                model = a.cover(300),
                modifier = Modifier.size(width).clip(rounded(28)).indication(source, LocalIndication.current),
            )
            if (showWave && waveActions != null) {
                WaveBadge({ waveActions.album(a) }, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Text(
            a.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        val sub = listOfNotNull(a.year.takeIf { it > 0 }?.toString(), a.artists.takeIf { it.isNotBlank() }).joinToString(" · ")
        if (sub.isNotBlank()) {
            Text(
                sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            )
        }
    }
}

// ---------- Альбом ----------

@Composable
private fun AlbumScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var lastAlbum by remember { mutableStateOf<AlbumInfo?>(null) }
    state.openedAlbum?.let { lastAlbum = it }
    val album = lastAlbum ?: return
    BackHandler(enabled = state.openedAlbum != null) { vm.closeAlbum() }
    val keys = remember(album.tracks) { trackKeys(album.tracks) }
    var showArtists by remember(album.id) { mutableStateOf(false) }
    // Обложка альбома в большом размере: открывается в окне просмотра по нажатию на обложку.
    var showCover by remember(album.id) { mutableStateOf(false) }
    val bigCover = album.cover(1000) ?: album.tracks.firstOrNull()?.cover(1000)
    // Открыт с обложки плеера — прокручиваем список к играющему треку (один раз после загрузки).
    val focusId = state.albumFocusTrackId
    val listState = rememberLazyListState()
    val isWideNow = LocalWide.current
    var scrolled by remember(album.id, focusId) { mutableStateOf(false) }
    LaunchedEffect(album.tracks, state.openedAlbumLoading, focusId) {
        if (focusId != null && !scrolled && !state.openedAlbumLoading) {
            val i = album.tracks.indexOfFirst { it.id == focusId }
            if (i >= 0) {
                val before = if (isWideNow) 0 else 2 // шапка и ряды кнопок (на планшете они в левой колонке)
                listState.scrollToItem(before + i)
                scrolled = true
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        SplitPullColumn(vm, Modifier.fillMaxSize(), state = listState, header = {
            item {
                val count = if (album.tracks.isNotEmpty()) album.tracks.size else album.trackCount
                PlaylistHeader(
                    title = album.title,
                    count = count,
                    cover = album.cover(400) ?: album.tracks.firstOrNull()?.cover(400),
                    onEdit = null,
                    subtitle = album.artists.takeIf { it.isNotBlank() },
                    countText = listOfNotNull(album.year.takeIf { it > 0 }?.toString(), "$count треков").joinToString(" · "),
                    onSubtitleClick = if (album.artistRefs.isNotEmpty()) ({
                        // Один исполнитель — окно выбора не нужно, сразу открываем его страницу.
                        val only = album.artistRefs.singleOrNull()
                        if (only != null) vm.openArtist(only) else showArtists = true
                    }) else null,
                    onCoverClick = if (bigCover != null) ({ showCover = true }) else null,
                )
            }
            // Кнопки без подписей, в один-два ряда: назад, слушать, вперемешку, волна, в коллекцию, поделиться.
            item(key = "album_actions") {
                val shareCtx = LocalContext.current
                val albumLiked = state.likedAlbums.any { it.id == album.id }
                val wave = LocalWaveActions.current
                val shareLink = ShareLinks.album(album)
                val downloadActions = if (!state.openedAlbumLoading && album.tracks.isNotEmpty()) downloadAllActions(vm, album.title, album.tracks) else emptyList()
                IconActionRows(
                    listOfNotNull(
                        PageAction(R.drawable.ms_arrow_back, "Назад") { vm.closeAlbum() },
                        PageAction(R.drawable.ms_play_arrow, "Слушать", kind = 1, enabled = album.tracks.isNotEmpty()) { vm.play(album.tracks, 0) },
                        PageAction(R.drawable.ms_shuffle, "Вперемешку", enabled = album.tracks.isNotEmpty()) { vm.play(album.tracks, 0, shuffle = true) },
                        wave?.let { w -> PageAction(R.drawable.ms_graphic_eq, "Включить волну", enabled = album.tracks.isNotEmpty()) { w.album(album) } },
                        // Пока альбом грузится, у него нет названия и обложки — лайк станет доступен, как только загрузится.
                        PageAction(
                            if (albumLiked) R.drawable.ms_favorite else R.drawable.ms_favorite_border,
                            if (albumLiked) "Убрать из коллекции" else "В коллекцию",
                            kind = if (albumLiked) 1 else 0,
                            enabled = !state.openedAlbumLoading && album.title.isNotBlank(),
                        ) { vm.toggleAlbumLike(album) },
                        // Свой альбом SoundCloud: название, описание, жанр, теги и видимость.
                        run {
                            val myId = LocalScMyId.current
                            val editSc = LocalEditScItem.current
                            if (myId != null && app.ymusic.m3e.data.ScIds.isSc(album.id) && album.artistRefs.any { it.id == myId }) {
                                PageAction(R.drawable.ms_edit, "Изменить альбом") { editSc(album.id, true) }
                            } else null
                        },
                        shareLink?.let { link ->
                            PageAction(R.drawable.ms_share, "Поделиться") {
                                val head = listOf(album.title, album.artists).filter { it.isNotBlank() }.joinToString(" — ")
                                ShareQrHost.open(album.title, "$head\n$link", link)
                            }
                        },
                    ) + downloadActions,
                )
            }
        }) {
            if (state.openedAlbumLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
            } else if (album.tracks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Треков не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            itemsIndexed(album.tracks, key = { i, _ -> keys[i] }) { i, t ->
                TrackRow(t, modifier = removeAnim().appear(if (i < 12) i else -1)) { vm.play(album.tracks, i) }
            }
        }
        if (showArtists) {
            ArtistsSheet(
                artists = album.artistRefs,
                onDismiss = { showArtists = false },
                onPick = { ref -> showArtists = false; vm.openArtist(ref) },
            )
        }
        if (showCover && bigCover != null) {
            ImageViewerDialog(
                images = listOf(bigCover),
                fileName = listOf(album.artists, album.title).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Обложка" },
                onDismiss = { showCover = false },
            )
        }
    }
}

/** Модалка «Исполнители»: список авторов альбома с аватарками; нажатие открывает страницу исполнителя. */
@Composable
private fun ArtistsSheet(artists: List<ArtistRef>, onDismiss: () -> Unit, onPick: (ArtistRef) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(
                "Исполнители",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
            )
            artists.forEach { ref ->
                ListItem(
                    headlineContent = { Text(ref.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Cover(
                            model = ref.cover(200),
                            placeholder = R.drawable.ms_person,
                            modifier = Modifier.size(56.dp).clip(ThemeSettings.avatarShape.asShape()),
                        )
                    },
                    trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onPick(ref) },
                )
            }
        }
    }
}

// ---------- Артист ----------

/** Форма аватарки исполнителя из настроек. */
@Composable
private fun AvatarShape.asShape(): Shape = when (this) {
    AvatarShape.Cookie12 -> MaterialShapes.Cookie12Sided.toShape()
    AvatarShape.Cookie9 -> MaterialShapes.Cookie9Sided.toShape()
    AvatarShape.Cookie6 -> MaterialShapes.Cookie6Sided.toShape()
    AvatarShape.Circle -> CircleShape
    AvatarShape.Clover -> MaterialShapes.Clover4Leaf.toShape()
    AvatarShape.Sunny -> MaterialShapes.Sunny.toShape()
    AvatarShape.Flower -> MaterialShapes.Flower.toShape()
    AvatarShape.Puffy -> MaterialShapes.Puffy.toShape()
    AvatarShape.SoftBurst -> MaterialShapes.SoftBurst.toShape()
    AvatarShape.Rounded -> androidx.compose.foundation.shape.RoundedCornerShape(28)
}

@Composable
private fun ArtistScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var lastArtist by remember { mutableStateOf<ArtistInfo?>(null) }
    state.openedArtist?.let { lastArtist = it }
    val artist = lastArtist ?: return
    BackHandler(enabled = state.openedArtist != null) { vm.closeArtist() }
    // Понравившиеся треки этого исполнителя и то, что слушали из него в «Моей волне» (как на странице исполнителя в Яндекс Музыке).
    val likedByArtist = remember(state.liked, artist.id) { state.liked.filter { l -> l.artistRefs.any { it.id == artist.id } } }
    // «Из Моей волны»: локальные прослушивания плюс те, что аккаунт запомнил с других устройств.
    LaunchedEffect(artist.id) { vm.loadSyncedHistory() }
    val waveByArtist = remember(state.waveHistory, state.syncedWave, artist.id) {
        val seen = HashSet<String>()
        (state.waveHistory + state.syncedWave)
            .filter { w -> w.artistRefs.any { it.id == artist.id } && seen.add(w.id) }
    }
    // Самый свежий альбом артиста (по дате выхода, а если её нет — по году).
    val latestAlbum = remember(artist.albums) { artist.albums.maxByOrNull { it.sortDate } }
    // Нажатие на аватарку исполнителя открывает его фото в окне просмотра.
    var showPhoto by remember(artist.id) { mutableStateOf(false) }
    var subPage by remember(artist.id) { mutableStateOf<ArtistSub?>(null) }
    var lastSub by remember(artist.id) { mutableStateOf(ArtistSub.Tracks) }
    subPage?.let { lastSub = it }
    BackHandler(enabled = subPage != null) { subPage = null }
    // Жест «назад» страницы исполнителя; пока открыта вложенная страница (Треки, Альбомы...), жест достаётся ей.
    SwipeBack(enabled = state.openedArtist != null && subPage == null, onBack = { vm.closeArtist() }) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
        SplitPullColumn(vm, Modifier.fillMaxSize(), header = {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val avatarShape = ThemeSettings.avatarShape.asShape()
                        Cover(
                            model = artist.cover(400),
                            modifier = Modifier.size(if (LocalInSplitPane.current) 200.dp else 160.dp).then(
                                if (artist.coverUri != null) Modifier.pressable(avatarShape) { showPhoto = true }
                                else Modifier.clip(avatarShape)
                            ),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            artist.name,
                            style = MaterialTheme.typography.headlineMediumEmphasized,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        // Слушатели за месяц, если сервис их отдаёт; иначе подписчики; нет ни того, ни другого — строки нет.
                        val statLine = if (artist.monthlyListeners > 0) listenersText(artist.monthlyListeners)
                            else if (artist.followers > 0) followersText(artist.followers) else null
                        if (statLine != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                statLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        // «Об артисте»: описание от сервиса (если есть). Отключается в Настройки → Внешний вид → Страница артиста.
                        if (ThemeSettings.artistBio && artist.bio.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            ArtistBioCard(artist.id, artist.bio)
                        }
                    }
                    PlayButtonsRow(
                        enabled = artist.tracks.isNotEmpty(),
                        onBack = { vm.closeArtist() },
                        onPlay = { vm.play(artist.tracks, 0) },
                        onShuffle = { vm.play(artist.tracks, 0, shuffle = true) },
                        onWave = LocalWaveActions.current?.let { w -> { w.artist(ArtistRef(artist.id, artist.name, artist.coverUri)) } },
                    )
                    val shareCtx = LocalContext.current
                    PageActionsRow(
                        liked = null, onLike = null,
                        onShare = ShareLinks.artist(artist.id)?.let { link ->
                            { ShareQrHost.open(artist.name, "${artist.name}\n$link", link) }
                        },
                    )
                }
            }
            // Последний выпуск артиста: прямоугольная плитка над треками и под кнопками. Нажатие открывает альбом.
            if (latestAlbum != null) {
                item(key = "latest_album") { LatestAlbumTile(latestAlbum) { vm.openAlbum(latestAlbum) } }
            }
        }) {
            if (state.openedArtistLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
            } else if (artist.tracks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Треков не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item { SectionLink("Треки") { subPage = ArtistSub.Tracks } }
            }
            itemsIndexed(artist.tracks.take(3)) { i, t ->
                TrackRow(t) { vm.play(artist.tracks, i) }
            }
            if (artist.albums.isNotEmpty()) {
                item { SectionLink("Альбомы") { subPage = ArtistSub.Albums } }
                item {
                    // Карточки считаются от ширины экрана: ровно три альбома помещаются в ряд, остальные листаются.
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardW = ((maxWidth - 32.dp - 24.dp) / 3).coerceAtLeast(96.dp)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) { items(artist.albums.take(10)) { a -> AlbumCard(a, width = cardW) { vm.openAlbum(a) } } }
                    }
                }
            }
            if (likedByArtist.isNotEmpty()) {
                item(key = "likedHead") { SectionLink("Понравившиеся", count = likedByArtist.size) { subPage = ArtistSub.Liked } }
                itemsIndexed(likedByArtist.take(3), key = { i, t -> "liked:${t.id}:$i" }) { i, t ->
                    TrackRow(t, modifier = removeAnim()) { vm.play(likedByArtist, i) }
                }
            }
            if (waveByArtist.isNotEmpty()) {
                item(key = "waveHead") { SectionLink(if (ActiveProvider.isVk) "Из VK Микса" else if (ActiveProvider.isSc) "Из волны" else "Из Моей волны", count = waveByArtist.size) { subPage = ArtistSub.Wave } }
                itemsIndexed(waveByArtist.take(3), key = { i, t -> "wave:${t.id}:$i" }) { i, t ->
                    TrackRow(t, modifier = removeAnim()) { vm.play(waveByArtist, i) }
                }
            }
        }
        if (showPhoto) {
            ImageViewerDialog(
                images = artist.photos(1000),
                fileName = artist.name.ifBlank { "Исполнитель" },
                onDismiss = { showPhoto = false },
            )
        }
        AnimatedVisibility(
            visible = subPage != null,
            enter = fadeIn(tween(animMs(200))) + slideInHorizontally(tween(animMs(280))) { it / 5 },
            exit = fadeOut(tween(animMs(150))) + slideOutHorizontally(tween(animMs(220))) { it / 5 },
        ) {
            SwipeBack(onBack = { subPage = null }) {
                when (lastSub) {
                    ArtistSub.Tracks -> ArtistTracksPage(vm, artist) { subPage = null }
                    ArtistSub.Albums -> ArtistAlbumsPage(vm, artist) { subPage = null }
                    ArtistSub.Liked -> ArtistLibPage(vm, "Понравившиеся", artist, likedByArtist) { subPage = null }
                    ArtistSub.Wave -> ArtistLibPage(vm, if (ActiveProvider.isVk) "Из VK Микса" else if (ActiveProvider.isSc) "Из волны" else "Из Моей волны", artist, waveByArtist) { subPage = null }
                }
            }
        }
        }
    }
    }
}

/**
 * «Об артисте» в стиле Material 3 Expressive: тональная карточка с крупным скруглением, значком в акцентном круге и
 * превью текста в три строки. Нажатие в любом месте карточки раскрывает описание целиком (пружинная анимация высоты,
 * стрелка в круге поворачивается). Если текст целиком помещается в три строки, стрелки нет и карточка не нажимается.
 */
@Composable
private fun ArtistBioCard(artistId: String, text: String) {
    var expanded by remember(artistId) { mutableStateOf(false) }
    // Влезает ли текст в три строки: измеряется в свёрнутом виде и дальше не меняется, пока не сменился артист.
    var canExpand by remember(artistId, text) { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bioArrow",
    )
    val toggle: () -> Unit = { if (canExpand) expanded = !expanded }
    Surface(
        modifier = Modifier.fillMaxWidth()
            .let { if (canExpand) it.pressCard(source, toggle) else it },
        shape = containerShape(28),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (canExpand) Modifier.indication(source, LocalIndication.current) else Modifier)
                .animateContentSize(animationSpec = fxSpring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                .padding(start = 16.dp + shapeInset(), end = 12.dp + shapeInset(), top = 14.dp, bottom = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(rounded(12)).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ms_info), null,
                        Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Об артисте",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (canExpand) {
                    Box(
                        Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ms_expand_more),
                            contentDescription = if (expanded) "Свернуть" else "Развернуть",
                            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = arrow },
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { r -> if (!expanded && r.hasVisualOverflow) canExpand = true },
            )
        }
    }
}

/** Плитка «Новинка» / «Последний альбом» на странице артиста: обложка, название, дата выхода и число треков. */
@Composable
private fun LatestAlbumTile(a: AlbumInfo, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    // Свежим считается релиз не старше четырёх месяцев — тогда плитка подписана «Новинка».
    val recent = remember(a.id) {
        a.sortDate.isNotEmpty() && a.sortDate >= java.time.LocalDate.now().minusDays(120).toString()
    }
    val dateText = remember(a.id) {
        val d = a.releaseDate.take(10)
        if (d.length == 10 && d[4] == '-' && d[7] == '-') "${d.substring(8, 10)}.${d.substring(5, 7)}.${d.substring(0, 4)}"
        else if (a.year > 0) a.year.toString() else ""
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).pressCard(source, onClick),
        shape = rounded(20),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().indication(source, LocalIndication.current)
                .padding(horizontal = 12.dp + shapeInset(), vertical = 12.dp + shapeInset() / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(model = a.cover(300), modifier = Modifier.size(76.dp).clip(rounded(16)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (recent) "Новинка" else "Последний альбом",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    a.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                val sub = listOfNotNull(
                    dateText.takeIf { it.isNotBlank() },
                    a.trackCount.takeIf { it > 0 }?.let { "$it ${songsWord(it)}" },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(painterResource(R.drawable.ms_chevron_right), contentDescription = null)
        }
    }
}

private enum class ArtistSub { Tracks, Albums, Liked, Wave }

/** Заголовок раздела со стрелочкой: нажатие открывает полный список. */
@Composable
private fun SectionLink(text: String, count: Int? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(containerShape(16)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLargeEmphasized)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(4.dp))
        Icon(painterResource(R.drawable.ms_chevron_right), contentDescription = null)
    }
}

/** Верхняя панель вложенной страницы: «назад», заголовок и необязательная подпись. */
@Composable
internal fun SubPageBar(title: String, onBack: () -> Unit, subtitle: String? = null, modifier: Modifier = Modifier, showBack: Boolean = true) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = "Назад")
            }
        } else Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).entrance(offsetDp = 6).padding(start = 4.dp, end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Все треки исполнителя. */
@Composable
private fun ArtistTracksPage(vm: AppViewModel, artist: ArtistInfo, onBack: () -> Unit) {
    // Популярные треки приходят сразу, остальные догружаются страницами в фоне.
    val all by vm.artistAllTracks.collectAsStateWithLifecycle()
    LaunchedEffect(artist.id) { vm.loadAllArtistTracks(artist.id) }
    val loaded = all.takeIf { it.artistId == artist.id }
    val tracks = loaded?.tracks?.takeIf { it.isNotEmpty() } ?: artist.tracks
    val keys = remember(tracks) { trackKeys(tracks) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("Треки", onBack, subtitle = artist.name)
            PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(tracks, key = { i, _ -> keys[i] }) { i, t ->
                    TrackRow(t, modifier = removeAnim().appear(if (i < 12) i else -1)) { vm.play(tracks, i) }
                }
                if (loaded?.loading == true) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/** Полный список понравившихся / прослушанных в «Моей волне» треков исполнителя. */
@Composable
private fun ArtistLibPage(vm: AppViewModel, title: String, artist: ArtistInfo, tracks: List<Track>, onBack: () -> Unit) {
    val keys = remember(tracks) { trackKeys(tracks) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(title, onBack, subtitle = "${artist.name} · ${tracks.size} ${songsWord(tracks.size)}")
            PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                itemsIndexed(tracks, key = { i, _ -> keys[i] }) { i, t ->
                    TrackRow(t, modifier = removeAnim().appear(if (i < 12) i else -1)) { vm.play(tracks, i) }
                }
            }
        }
    }
}

/** Все альбомы исполнителя. */
@Composable
private fun ArtistAlbumsPage(vm: AppViewModel, artist: ArtistInfo, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar("Альбомы", onBack, subtitle = artist.name)
            PullLazyGrid(
                vm,
                columns = AlbumGridCells,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(artist.albums) { a ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        AlbumCard(a, width = maxWidth) { vm.openAlbum(a) }
                    }
                }
            }
        }
    }
}

// ---------- Плейлисты ----------

private sealed interface PlPage {
    data object Grid : PlPage
    data object Liked : PlPage
    data object Local : PlPage
    data object AllPlaylists : PlPage
    data object AllAlbums : PlPage
    data class Detail(val p: PlaylistInfo) : PlPage
}

/** Ключи элементов списка: id трека + номер повтора, чтобы не было дублей и работала анимация удаления. */
private fun trackKeys(tracks: List<Track>): List<String> {
    val seen = HashMap<String, Int>()
    return tracks.map { t ->
        val n = seen[t.id] ?: 0
        seen[t.id] = n + 1
        "${t.id}#$n"
    }
}

@Composable
private fun PlaylistsScreen(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showLiked by rememberSaveable { mutableStateOf(false) }
    var showLocal by rememberSaveable { mutableStateOf(false) }
    var showAllPlaylists by rememberSaveable { mutableStateOf(false) }
    var showAllAlbums by rememberSaveable { mutableStateOf(false) }
    val opened = state.openedPlaylist
    InnerPageEffect("playlists", opened != null || showLiked || showLocal || showAllPlaylists || showAllAlbums) {
        vm.closePlaylist(); showLiked = false; showLocal = false; showAllPlaylists = false; showAllAlbums = false
    }
    val page: PlPage = when {
        opened != null -> PlPage.Detail(opened)
        showLiked -> PlPage.Liked
        showLocal -> PlPage.Local
        showAllPlaylists -> PlPage.AllPlaylists
        showAllAlbums -> PlPage.AllAlbums
        else -> PlPage.Grid
    }
    AnimatedContent(
        targetState = page,
        contentKey = { if (it is PlPage.Detail) "detail" else it.toString() },
        transitionSpec = {
            // «Назад» — на главную страницу раздела или из открытого плейлиста; иначе «вперёд».
            if (targetState is PlPage.Grid || initialState is PlPage.Detail) {
                (fadeIn(tween(animMs(260), delayMillis = animMs(60))) + slideInHorizontally(tween(animMs(320))) { -it / 5 }) togetherWith
                    (fadeOut(tween(animMs(140))) + slideOutHorizontally(tween(animMs(260))) { it / 5 })
            } else {
                (fadeIn(tween(animMs(260), delayMillis = animMs(60))) + slideInHorizontally(tween(animMs(320))) { it / 5 }) togetherWith
                    (fadeOut(tween(animMs(140))) + slideOutHorizontally(tween(animMs(260))) { -it / 5 })
            }
        },
        label = "playlistPages",
    ) { target ->
        when (target) {
            PlPage.Grid -> PlaylistsMain(
                vm,
                onOpenLocal = { showLocal = true },
                onOpenLiked = { showLiked = true },
                onOpenAllPlaylists = { showAllPlaylists = true },
                onOpenAllAlbums = { showAllAlbums = true },
            )
            PlPage.Liked -> SwipeBack(onBack = { showLiked = false }) { LikedPage(vm) { showLiked = false } }
            PlPage.Local -> SwipeBack(onBack = { showLocal = false }) { LocalPage(vm) { showLocal = false } }
            PlPage.AllPlaylists -> SwipeBack(onBack = { showAllPlaylists = false }) { AllPlaylistsPage(vm) { showAllPlaylists = false } }
            PlPage.AllAlbums -> SwipeBack(onBack = { showAllAlbums = false }) { AllAlbumsPage(vm) { showAllAlbums = false } }
            is PlPage.Detail -> SwipeBack(onBack = { vm.closePlaylist() }) { PlaylistDetail(vm, target.p) }
        }
    }
}

/** Альбомы раздела: из коллекции аккаунта плюс те, что скачаны (их видно и без сети). */
@Composable
private fun rememberAlbumList(vm: AppViewModel): Pair<List<AlbumInfo>, Set<String>> {
    val state by vm.state.collectAsStateWithLifecycle()
    val dl = LocalDownloads.current
    val albums = remember(state.likedAlbums, dl.items) { mergeAlbums(state.likedAlbums, albumsFromDownloads(dl.items)).distinctBy { it.id } }
    val downloaded = remember(dl.items) { dl.items.mapNotNullTo(HashSet<String>()) { it.meta?.albumId } }
    return albums to downloaded
}

private fun PlaylistInfo.listKey() = "${ownerUid}:${kind}"

@Composable
private fun DeletePlaylistDialog(target: PlaylistInfo, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить плейлист?") },
        text = { Text("«${target.title}» будет удалён без возможности восстановления.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/**
 * Главная страница раздела: «Любимое», «С устройства», новый плейлист, затем «Плейлисты» и «Альбомы» —
 * горизонтальные ряды, которые листаются в сторону. Заголовок раздела нажимается и открывает полный список.
 */
@Composable
private fun PlaylistsMain(
    vm: AppViewModel,
    onOpenLocal: () -> Unit,
    onOpenLiked: () -> Unit,
    onOpenAllPlaylists: () -> Unit,
    onOpenAllAlbums: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val (albums, downloadedAlbumIds) = rememberAlbumList(vm)
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PlaylistInfo?>(null) }
    val ctx = LocalContext.current
    // Если доступ уже выдан — заранее считаем, сколько файлов на устройстве.
    LaunchedEffect(Unit) { if (LocalFilesSettings.enabled && LocalMusic.hasPermission(ctx)) vm.loadLocalTracks() }
    val wideLib = LocalWide.current
    // Планшет: слева «Любимое», «С устройства» и «Новый плейлист», справа плейлисты и альбомы сеткой.
    val plLeftBlocks = setOf(PlaylistsBlock.Liked, PlaylistsBlock.Local, PlaylistsBlock.Create)
    val plBlocks: LazyListScope.() -> Unit = {
        UiLayout.order(LScreen.PLAYLISTS, PlaylistsBlock.entries).forEach { blk ->
            if (UiLayout.isHidden(LScreen.PLAYLISTS, blk)) return@forEach
            if (wideLib && blk !in plLeftBlocks) return@forEach
            when (blk) {
                PlaylistsBlock.Liked -> {
                blockItem("liked", LScreen.PLAYLISTS, PlaylistsBlock.Liked) {
                    val shape = rounded(32)
                    Surface(
                        shape = shape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).appear(0)
                            .playGlow(
                                active = LocalPlayingSource.current == "liked", enabled = ThemeSettings.glowPlaylists,
                                color = MaterialTheme.colorScheme.primary, cornerRadius = 32.dp, spread = 14.dp,
                                shape = shape,
                            )
                            .pressable(shape, onOpenLiked),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 24.dp + shapeInset(), vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Любимое",
                                    style = MaterialTheme.typography.titleLargeEmphasized,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    "${state.liked.size} треков",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            // Кнопка плей нажимается сама по себе (играет «Любимое» / ставит на паузу), а вся остальная
                            // область карточки открывает список лайков.
                            val likedActive = state.playingSource == "liked"
                            FilledIconButton(
                                onClick = {
                                    when {
                                        likedActive -> vm.togglePlay()
                                        else -> vm.play(state.liked, 0)
                                    }
                                },
                                enabled = state.liked.isNotEmpty(),
                                haptic = HapticKind.Heavy,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    painterResource(if (likedActive && state.isPlaying) R.drawable.ms_pause else R.drawable.ms_play_arrow),
                                    contentDescription = if (likedActive && state.isPlaying) "Пауза" else "Играть Любимое",
                                )
                            }
                        }
                    }
                }
                }
                PlaylistsBlock.Local -> {
                if (LocalFilesSettings.enabled) {
                    blockItem("local", LScreen.PLAYLISTS, PlaylistsBlock.Local) {
                        val shape = rounded(32)
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)
                                .appear(1).pressable(shape, onOpenLocal),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 24.dp + shapeInset(), vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "С устройства",
                                        style = MaterialTheme.typography.titleLargeEmphasized,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Text(
                                        if (state.localTracks.isNotEmpty()) "${state.localTracks.size} треков" else "Музыка на телефоне",
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Icon(
                                    painterResource(R.drawable.ms_folder), null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                }
                PlaylistsBlock.Create -> {
                blockItem("create", LScreen.PLAYLISTS, PlaylistsBlock.Create) {
                    FilledTonalButton(
                        onClick = { showCreate = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp).height(56.dp).appear(2),
                    ) {
                        Icon(painterResource(R.drawable.ms_add), null)
                        Spacer(Modifier.width(8.dp)); Text("Новый плейлист")
                    }
                }

                }
                PlaylistsBlock.Playlists -> {
                // ---- Плейлисты: ряд, который листается в сторону ----
                blockItem("playlists_title", LScreen.PLAYLISTS, PlaylistsBlock.Playlists) {
                    Box(Modifier.padding(top = 8.dp)) { SectionLink("Плейлисты", onClick = onOpenAllPlaylists) }
                }
                if (state.playlists.isEmpty()) {
                    blockItem("playlists_empty", LScreen.PLAYLISTS, PlaylistsBlock.Playlists, withGap = false) {
                        Text(
                            "Плейлистов пока нет",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    blockItem("playlists_row", LScreen.PLAYLISTS, PlaylistsBlock.Playlists, withGap = false) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.playlists, key = { it.listKey() }) { p ->
                                PlaylistCard(
                                    p,
                                    onLongClick = if (p.isEditableBy(state.account)) ({ deleteTarget = p }) else null,
                                ) { vm.showPlaylist(p) }
                            }
                        }
                    }
                }

                }
                PlaylistsBlock.Albums -> {
                // ---- Альбомы: после плейлистов, тоже в сторону ----
                blockItem("albums_title", LScreen.PLAYLISTS, PlaylistsBlock.Albums) { SectionLink("Альбомы", onClick = onOpenAllAlbums) }
                if (albums.isEmpty()) {
                    blockItem("albums_empty", LScreen.PLAYLISTS, PlaylistsBlock.Albums, withGap = false) {
                        Text(
                            "Здесь появятся альбомы, которые вы добавите в коллекцию или скачаете",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    blockItem("albums_row", LScreen.PLAYLISTS, PlaylistsBlock.Albums, withGap = false) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(albums, key = { it.id }) { a ->
                                AlbumCard(
                                    a,
                                    // Без сети альбомы, которых нет среди скачанного, приглушены.
                                    modifier = Modifier.alpha(if (state.offline && a.id !in downloadedAlbumIds) 0.4f else 1f),
                                ) { vm.openAlbum(a) }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    if (wideLib) {
        TwoPane(
            left = {
                PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) { plBlocks() }
            },
            right = {
                PullLazyGrid(
                    vm,
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    UiLayout.order(LScreen.PLAYLISTS, PlaylistsBlock.entries).forEach { blk ->
                        if (UiLayout.isHidden(LScreen.PLAYLISTS, blk)) return@forEach
                        when (blk) {
                            PlaylistsBlock.Playlists -> {
                                item(key = "wpl_title", span = { GridItemSpan(maxLineSpan) }) {
                                    SectionLink("Плейлисты", onClick = onOpenAllPlaylists)
                                }
                                if (state.playlists.isEmpty()) {
                                    item(key = "wpl_empty", span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            "Плейлистов пока нет",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                } else {
                                    gridItems(state.playlists, key = { "wpl_" + it.listKey() }) { pl ->
                                        PlaylistCard(
                                            pl,
                                            modifier = removeAnim(),
                                            fill = true,
                                            onLongClick = if (pl.isEditableBy(state.account)) ({ deleteTarget = pl }) else null,
                                        ) { vm.showPlaylist(pl) }
                                    }
                                }
                            }
                            PlaylistsBlock.Albums -> {
                                item(key = "wal_title", span = { GridItemSpan(maxLineSpan) }) {
                                    SectionLink("Альбомы", onClick = onOpenAllAlbums)
                                }
                                if (albums.isEmpty()) {
                                    item(key = "wal_empty", span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            "Здесь появятся альбомы, которые вы добавите в коллекцию или скачаете",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        )
                                    }
                                } else {
                                    gridItems(albums, key = { "wal_" + it.id }) { a ->
                                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                                            AlbumCard(
                                                a, width = maxWidth,
                                                modifier = Modifier.alpha(if (state.offline && a.id !in downloadedAlbumIds) 0.4f else 1f),
                                            ) { vm.openAlbum(a) }
                                        }
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            },
        )
    } else {
        PullLazyColumn(vm, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)) { plBlocks() }
    }
    deleteTarget?.let { target ->
        DeletePlaylistDialog(target, onDismiss = { deleteTarget = null }) { vm.deletePlaylist(target); deleteTarget = null }
    }
    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { title, isPublic -> vm.createPlaylist(title, isPublic, openAfter = true); showCreate = false },
        )
    }
}

/** Все плейлисты сеткой (открывается нажатием на заголовок «Плейлисты»). */
@Composable
private fun AllPlaylistsPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<PlaylistInfo?>(null) }
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Плейлисты", onBack, subtitle = "${state.playlists.size} шт.")
        PullLazyGrid(
            vm,
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.playlists.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Плейлистов пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            gridItems(state.playlists, key = { it.listKey() }) { p ->
                PlaylistCard(
                    p,
                    modifier = removeAnim(),
                    fill = true,
                    onLongClick = if (p.isEditableBy(state.account)) ({ deleteTarget = p }) else null,
                ) { vm.showPlaylist(p) }
            }
        }
    }
    deleteTarget?.let { target ->
        DeletePlaylistDialog(target, onDismiss = { deleteTarget = null }) { vm.deletePlaylist(target); deleteTarget = null }
    }
}

/** Все альбомы сеткой (открывается нажатием на заголовок «Альбомы»). */
@Composable
private fun AllAlbumsPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val (albums, downloadedAlbumIds) = rememberAlbumList(vm)
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Альбомы", onBack, subtitle = "${albums.size} шт.")
        PullLazyGrid(
            vm,
            columns = AlbumGridCells,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (albums.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Альбомов пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            gridItems(albums, key = { it.id }) { a ->
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    AlbumCard(
                        a, width = maxWidth,
                        modifier = Modifier.alpha(if (state.offline && a.id !in downloadedAlbumIds) 0.4f else 1f),
                    ) { vm.openAlbum(a) }
                }
            }
        }
    }
}

/** Шапка плейлиста: обложка слева, справа название и число треков. */
@Composable
private fun PlaylistHeader(
    title: String,
    count: Int,
    cover: Any?,
    onEdit: (() -> Unit)?,
    subtitle: String? = null,
    countText: String? = null,
    placeholderIcon: Int = R.drawable.ms_music_note,
    onSubtitleClick: (() -> Unit)? = null,
    /** Нажатие на обложку (если не задано редактирование): например, открыть её в окне просмотра. */
    onCoverClick: (() -> Unit)? = null,
    /** Автоскачивание: null — кнопки нет; иначе текущее состояние (по умолчанию выключено) и переключатель. */
    autoDownload: Boolean? = null,
    onAutoDownloadChange: ((Boolean) -> Unit)? = null,
) {
    if (LocalInSplitPane.current) {
        // Левая колонка планшета: крупная обложка сверху, под ней название и подпись по центру.
        val coverShape = rounded(32)
        val compact = LocalCompactHeight.current
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Cover(
                model = cover,
                placeholder = placeholderIcon,
                modifier = (if (compact) Modifier.size(132.dp) else Modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f))
                    .then(
                        when {
                            onEdit != null -> Modifier.pressable(coverShape, onEdit)
                            onCoverClick != null -> Modifier.pressable(coverShape, onCoverClick)
                            else -> Modifier.clip(coverShape)
                        }
                    ),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = if (onEdit != null) Modifier.pressable(textShape(10), onEdit) else Modifier,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (onSubtitleClick != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    modifier = Modifier.padding(top = 2.dp)
                        .then(if (onSubtitleClick != null) Modifier.pressable(textShape(6), onSubtitleClick) else Modifier),
                )
            }
            Text(
                countText ?: "$count треков",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (autoDownload != null && onAutoDownloadChange != null) {
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = autoDownload,
                    onClick = { onAutoDownloadChange(!autoDownload) },
                    label = { Text(if (autoDownload) "Автоскачивание: вкл" else "Автоскачивание: выкл", maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (autoDownload) R.drawable.ms_download_done else R.drawable.ms_download),
                            contentDescription = null, modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val coverShape = rounded(28)
        Cover(
            model = cover,
            placeholder = placeholderIcon,
            modifier = Modifier.size(104.dp)
                .then(
                    when {
                        onEdit != null -> Modifier.pressable(coverShape, onEdit)
                        onCoverClick != null -> Modifier.pressable(coverShape, onCoverClick)
                        else -> Modifier.clip(coverShape)
                    }
                ),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val titleShape = textShape(10)
            Text(
                title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onEdit != null) Modifier.pressable(titleShape, onEdit) else Modifier,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (onSubtitleClick != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    modifier = Modifier.padding(top = 2.dp)
                        .then(if (onSubtitleClick != null) Modifier.pressable(textShape(6), onSubtitleClick) else Modifier),
                )
            }
            Text(
                countText ?: "$count треков",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (autoDownload != null && onAutoDownloadChange != null) {
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = autoDownload,
                    onClick = { onAutoDownloadChange(!autoDownload) },
                    label = { Text(if (autoDownload) "Автоскачивание: вкл" else "Автоскачивание: выкл", maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (autoDownload) R.drawable.ms_download_done else R.drawable.ms_download),
                            contentDescription = null, modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }
    }
}

/** Кнопка страницы без подписи. [kind]: 0 — тональная, 1 — основная (залитая), 2 — опасное действие (удалить). */
private class PageAction(
    val icon: Int,
    val label: String,
    val kind: Int = 0,
    val enabled: Boolean = true,
    /** Ход скачивания 0..1: вокруг кнопки рисуется кольцо прогресса. */
    val progress: Float? = null,
    val onClick: () -> Unit,
)

/**
 * Кнопки страницы альбома / плейлиста: только значки (название — в подсказке при долгом нажатии).
 * Между кнопками нормальные отступы (M3E: 8dp). Первый ряд заполняется до конца, остаток кнопок — вторым рядом
 * слева; если кнопки помещаются в один ряд — ряд один.
 */
@Composable
private fun IconActionRows(actions: List<PageAction>) {
    if (actions.isEmpty()) return
    val btn = 56.dp
    val gap = 8.dp
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 12.dp)) {
        // Сколько кнопок помещается в ряд с учётом промежутков: n*56 + (n-1)*8 <= ширины.
        val perRow = ((maxWidth.value + gap.value) / (btn.value + gap.value)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            val rows = actions.chunked(perRow)
            rows.forEachIndexed { idx, row ->
                // Неполный последний ряд (остаток) растягивается на всю ширину; полные ряды — круглые кнопки.
                val stretch = rows.size > 1 && idx == rows.lastIndex && row.size < perRow
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { a ->
                        if (stretch) RoundActionButton(a, Modifier.weight(1f)) else RoundActionButton(a)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundActionButton(a: PageAction, modifier: Modifier = Modifier) {
    // Обычная кнопка 56x56; в растянутом ряду ширину задаёт modifier (weight), высота та же.
    val wide = modifier != Modifier
    val m = if (wide) Modifier.fillMaxWidth().height(56.dp) else Modifier.size(56.dp)
    // M3E: основная кнопка — «квадратный круг» (скруглённый квадрат), остальные — круглые; при нажатии форма морфится.
    val primaryShape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (a.kind) {
            1 -> FilledIconButton(onClick = a.onClick, modifier = m, enabled = a.enabled, shape = primaryShape) {
                Icon(painterResource(a.icon), contentDescription = a.label)
            }
            2 -> FilledTonalIconButton(
                onClick = a.onClick, modifier = m, enabled = a.enabled,
                colors = IconButtonDefaults.filledTonalIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Icon(painterResource(a.icon), contentDescription = a.label) }
            else -> FilledTonalIconButton(onClick = a.onClick, modifier = m, enabled = a.enabled) {
                Icon(painterResource(a.icon), contentDescription = a.label)
            }
        }
        val pr = a.progress
        if (pr != null) {
            androidx.compose.material3.CircularProgressIndicator(
                progress = { pr.coerceIn(0f, 1f) },
                modifier = Modifier.size(56.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}

/**
 * «Скачать всё» для альбомов и плейлистов в виде круглых кнопок для [IconActionRows]: скачать / докачать,
 * при скачивании — отмена с кольцом прогресса, когда всё скачано — «скачать другим способом». Окно выбора, куда
 * скачивать, показывается отсюда же.
 */
@Composable
private fun downloadAllActions(vm: AppViewModel, name: String, tracks: List<Track>): List<PageAction> {
    val dl = LocalDownloads.current
    val offline = LocalOffline.current
    val remote = remember(tracks) { tracks.filter { it.canDownload() }.distinctBy { it.id } }
    val have = remember(dl.items) { dl.items.mapTo(HashSet()) { it.id } }
    val missing = remote.count { it.id !in have }
    val bulk = dl.bulk
    var ask by remember { mutableStateOf(false) }
    if (remote.isEmpty()) return emptyList()
    if (ask) {
        DownloadModeDialog(
            title = "Скачать «$name»",
            text = if (missing == 0) "Всё уже скачано. Можно дополнительно сохранить треки в другое место."
            else "Треков к скачиванию: $missing. Куда сохранять?",
            initial = dl.mode, dirLabel = dl.dirLabel,
            onDismiss = { ask = false },
            showRemember = dl.askMode,
            onPick = { m, dontAsk ->
                ask = false
                if (dontAsk) vm.setDownloadModeAndStopAsking(m)
                vm.downloadAll(name, remote, m)
            },
        )
    }
    return when {
        bulk != null -> listOf(
            PageAction(
                R.drawable.ms_close, "Отменить скачивание «${bulk.name}»: ${bulk.done} из ${bulk.total}",
                progress = bulk.done.toFloat() / bulk.total.coerceAtLeast(1),
            ) { vm.cancelBulkDownload() },
        )
        missing == 0 -> listOf(
            PageAction(
                R.drawable.ms_download_done, "Все треки скачаны (${remote.size}). Нажмите, чтобы скачать другим способом",
                kind = 1, enabled = !offline,
            ) { ask = true },
        )
        else -> listOfNotNull(
            PageAction(
                R.drawable.ms_download,
                if (offline) "Нет сети — скачано ${remote.size - missing} из ${remote.size}"
                else if (missing == remote.size) "Скачать все ($missing)" else "Докачать ($missing из ${remote.size})",
                enabled = !offline,
            ) { if (dl.askMode) ask = true else vm.downloadAll(name, remote, null) },
            // Окно выбора отключено; эта кнопка открывает его вручную для разового другого способа.
            if (!dl.askMode) PageAction(R.drawable.ms_cloud_download, "Скачать как…", enabled = !offline) { ask = true } else null,
        )
    }
}

@Composable
private fun PlayButtonsRow(
    enabled: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDelete: (() -> Unit)? = null,
    /** Включить волну по этому альбому / исполнителю / плейлисту; null — кнопки нет. */
    onWave: (() -> Unit)? = null,
) {
    // Всё в одну строку: «назад» + две кнопки поровну. На узких экранах иконки в кнопках скрываются, чтобы текст влез.
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        // Назад + волна + удалить + две основные кнопки на узком экране не помещаются с подписями — остаются значки.
        val iconsOnly = onWave != null && onDelete != null && maxWidth < 420.dp
        val icons = maxWidth >= 340.dp || iconsOnly
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = "Назад")
            }
            // Группа кнопок M3E: нажатая кнопка расширяется, соседняя уступает ей место.
            val playSrc = remember { MutableInteractionSource() }
            val shuffleSrc = remember { MutableInteractionSource() }
            Button(
                onClick = onPlay, enabled = enabled,
                modifier = groupWeight(playSrc, listOf(shuffleSrc)).height(56.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                interactionSource = playSrc,
            ) {
                if (icons) { Icon(painterResource(R.drawable.ms_play_arrow), if (iconsOnly) "Слушать" else null); if (!iconsOnly) Spacer(Modifier.width(6.dp)) }
                if (!iconsOnly) FitText("Слушать")
            }
            FilledTonalButton(
                onClick = onShuffle, enabled = enabled,
                modifier = groupWeight(shuffleSrc, listOf(playSrc)).height(56.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                interactionSource = shuffleSrc,
            ) {
                if (icons) { Icon(painterResource(R.drawable.ms_shuffle), if (iconsOnly) "Вперемешку" else null); if (!iconsOnly) Spacer(Modifier.width(6.dp)) }
                if (!iconsOnly) FitText("Вперемешку")
            }
            if (onWave != null) {
                FilledTonalIconButton(onClick = onWave, enabled = enabled, modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ms_graphic_eq), contentDescription = "Включить волну")
                }
            }
            if (onDelete != null) {
                FilledTonalIconButton(
                    onClick = onDelete, modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(R.drawable.ms_delete), contentDescription = "Удалить плейлист")
                }
            }
        }
    }
}

/**
 * Строка «Скачать всё» для «Любимого», плейлистов и альбомов: по нажатию спрашивает, куда скачивать (файлами,
 * только в приложении или и то и другое); потом показывает прогресс с отменой.
 */
@Composable
private fun DownloadAllRow(vm: AppViewModel, name: String, tracks: List<Track>) {
    val dl = LocalDownloads.current
    val offline = LocalOffline.current
    val remote = remember(tracks) { tracks.filter { it.canDownload() }.distinctBy { it.id } }
    if (remote.isEmpty()) return
    val have = remember(dl.items) { dl.items.mapTo(HashSet()) { it.id } }
    val missing = remote.count { it.id !in have }
    val bulk = dl.bulk
    var ask by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
        if (offline) {
            Text(
                "Нет сети — можно слушать скачанные: ${remote.size - missing} из ${remote.size}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        when {
            bulk != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Скачивание «${bulk.name}»: ${bulk.done} из ${bulk.total}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        M3LinearBar(progress = bulk.done.toFloat() / bulk.total.coerceAtLeast(1), wavy = true, thickness = 4.dp)
                    }
                    TextButton(onClick = { vm.cancelBulkDownload() }) { Text("Отмена") }
                }
            }
            missing == 0 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ms_check), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Все треки скачаны (${remote.size})", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (!offline) TextButton(onClick = { ask = true }) { Text("Другой способ") }
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        // Выбор «не показывать снова» сделан — качаем сразу способом по умолчанию.
                        onClick = { if (dl.askMode) ask = true else vm.downloadAll(name, remote, null) },
                        enabled = !offline,
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Icon(painterResource(R.drawable.ms_download), null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (missing == remote.size) "Скачать все ($missing)" else "Докачать ($missing из ${remote.size})", maxLines = 1)
                    }
                    if (!dl.askMode) {
                        // Окно выбора отключено; кнопка открывает его вручную для разового другого способа.
                        FilledTonalIconButton(onClick = { ask = true }, enabled = !offline, modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.ms_cloud_download), contentDescription = "Скачать как…")
                        }
                    }
                }
            }
        }
    }
    if (ask) {
        DownloadModeDialog(
            title = "Скачать «$name»",
            text = if (missing == 0) "Всё уже скачано. Можно дополнительно сохранить треки в другое место."
            else "Треков к скачиванию: $missing. Куда сохранять?",
            initial = dl.mode, dirLabel = dl.dirLabel,
            onDismiss = { ask = false },
            showRemember = dl.askMode,
            onPick = { m, dontAsk ->
                ask = false
                if (dontAsk) vm.setDownloadModeAndStopAsking(m)
                vm.downloadAll(name, remote, m)
            },
        )
    }
}

@Composable
private fun LikedPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    BackHandler { onBack() }
    // Список читаем один раз в композиции: ключи и строки обязаны браться из одного и того же списка, иначе при
    // обновлении списка во время расчёта ленты ключи отстают и приложение падает (IndexOutOfBounds).
    val liked = state.liked
    val keys = remember(liked) { trackKeys(liked) }
    SplitPullColumn(vm, Modifier.fillMaxSize(), header = {
        item {
            PlaylistHeader(
                title = "Любимое",
                count = liked.size,
                cover = liked.firstOrNull()?.cover(400),
                onEdit = null,
                autoDownload = if (ActiveProvider.isSc) null else DownloadsState.AUTO_LIKED in LocalDownloads.current.auto,
                onAutoDownloadChange = { vm.setAutoDownload(DownloadsState.AUTO_LIKED, it, "Любимое", liked) },
            )
        }
        item {
            PlayButtonsRow(
                enabled = liked.isNotEmpty(),
                onBack = onBack,
                onPlay = { vm.play(liked, 0) },
                onShuffle = { vm.play(liked, 0, shuffle = true) },
                onWave = LocalWaveActions.current?.let { w -> { w.liked() } },
            )
        }
        item { DownloadAllRow(vm, "Любимое", liked) }
    }) {
        itemsIndexed(liked, key = { i, _ -> keys[i] }) { i, t ->
            TrackRow(t, modifier = removeAnim().appear(if (i < 12) i else -1)) { vm.play(liked, i) }
        }
    }
}

/** Папка «С устройства»: при первом заходе просит доступ к аудио, затем показывает файлы телефона. */
@Composable
private fun LocalPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    BackHandler { onBack() }
    var granted by remember { mutableStateOf(LocalMusic.hasPermission(ctx)) }
    val request = rememberAudioAccessRequest { ok ->
        granted = ok
        if (ok) vm.loadLocalTracks()
    }
    // Диалог доступа показываем сам только один раз; после перезапуска процесса (например, когда
    // разрешение сменили в настройках) пользователь видит кнопку «Разрешить доступ», а не неожиданный диалог.
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (granted) vm.loadLocalTracks()
        else if (!asked) { asked = true; request(false) }
    }
    // Вернулись из системных настроек: доступ могли как выдать, так и отозвать.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = LocalMusic.hasPermission(ctx)
        if (now != granted) {
            granted = now
            if (now) vm.loadLocalTracks()
        }
    }
    // Список читаем один раз в композиции: ключи и строки обязаны браться из одного и того же списка. Раньше после
    // выдачи доступа список загружался прямо во время расчёта ленты, ключи отставали, и приложение падало.
    val tracks = state.localTracks
    val keys = remember(tracks) { trackKeys(tracks) }
    SplitPullColumn(vm, Modifier.fillMaxSize(), header = {
        item {
            PlaylistHeader(
                title = "С устройства",
                count = tracks.size,
                cover = null,
                onEdit = null,
                placeholderIcon = R.drawable.ms_folder,
            )
        }
        item {
            PlayButtonsRow(
                enabled = tracks.isNotEmpty(),
                onBack = onBack,
                onPlay = { vm.play(tracks, 0) },
                onShuffle = { vm.play(tracks, 0, shuffle = true) },
            )
        }
    }) {
        if (!granted) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Нужен доступ к музыке на устройстве",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Разрешите доступ к аудиофайлам, чтобы увидеть и воспроизвести их здесь.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { request(true) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text("Разрешить доступ") }
                    TextButton(onClick = {
                        ctx.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${ctx.packageName}"),
                            )
                        )
                    }) { Text("Открыть настройки приложения") }
                }
            }
        } else if (state.localLoading && tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        } else if (tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Музыки на устройстве не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        itemsIndexed(tracks, key = { i, _ -> keys[i] }) { i, t ->
            TrackRow(t, modifier = removeAnim().appear(if (i < 12) i else -1)) { vm.play(tracks, i) }
        }
    }
}

@Composable
private fun PlaylistDetail(vm: AppViewModel, initial: PlaylistInfo) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Запоминаем последнее состояние, чтобы экран красиво уезжал, а не пустел при закрытии.
    var last by remember { mutableStateOf(initial) }
    state.openedPlaylist?.let { last = it }
    var lastTracks by remember { mutableStateOf(state.openedPlaylistTracks) }
    if (state.openedPlaylist != null) lastTracks = state.openedPlaylistTracks
    val p = last
    val tracks = lastTracks
    val editable = p.isEditableBy(state.account)
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val cover = p.cover() ?: tracks.firstOrNull()?.cover(400)
    val keys = remember(tracks) { trackKeys(tracks) }

    BackHandler(enabled = state.openedPlaylist != null) { vm.closePlaylist() }
    Box(Modifier.fillMaxSize()) {
    SplitPullColumn(vm, Modifier.fillMaxSize(), header = {
        item {
            PlaylistHeader(
                title = p.title,
                count = if (state.openedPlaylistLoading) p.trackCount else tracks.size,
                cover = cover,
                // Правка — кнопкой «Редактировать» в ряду кнопок, не нажатием на обложку или название.
                onEdit = null,
                autoDownload = if (ActiveProvider.isSc) null else playlistSourceKey(p) in LocalDownloads.current.auto,
                onAutoDownloadChange = { vm.setAutoDownload(playlistSourceKey(p), it, p.title, tracks) },
            )
        }
        // Кнопки без подписей, в один-два ряда. Публичность меняется в окне редактирования.
        item(key = "playlist_actions") {
            val shareCtx = LocalContext.current
            val wave = LocalWaveActions.current
            // Чужой плейлист, который удалось открыть, — публичный (даже если сервер не прислал поле видимости).
            val shareable = if (editable || p.visibility != null) p else p.copy(visibility = "public")
            val link = if (p.kind != 3) ShareLinks.playlist(shareable) else null
            val downloadActions = if (!state.openedPlaylistLoading && tracks.isNotEmpty()) downloadAllActions(vm, p.title, tracks) else emptyList()
            IconActionRows(
                listOfNotNull(
                    PageAction(R.drawable.ms_arrow_back, "Назад") { vm.closePlaylist() },
                    PageAction(R.drawable.ms_play_arrow, "Слушать", kind = 1, enabled = tracks.isNotEmpty()) { vm.play(tracks, 0) },
                    PageAction(R.drawable.ms_shuffle, "Вперемешку", enabled = tracks.isNotEmpty()) { vm.play(tracks, 0, shuffle = true) },
                    wave?.let { w -> PageAction(R.drawable.ms_graphic_eq, "Включить волну", enabled = tracks.isNotEmpty()) { w.playlist(p) } },
                    // «Мне нравится» (kind = 3) — системный и всегда приватный: «Поделиться» нет.
                    if (p.kind != 3) PageAction(
                        R.drawable.ms_share,
                        if (link != null) "Поделиться плейлистом" else "Поделиться нельзя — плейлист приватный",
                        enabled = link != null,
                    ) { if (link != null) ShareQrHost.open(p.title, "${p.title}\n$link", link) } else null,
                    if (editable) PageAction(R.drawable.ms_edit, "Редактировать плейлист") { showEdit = true } else null,
                    if (editable) PageAction(R.drawable.ms_delete, "Удалить плейлист", kind = 2) { showDelete = true } else null,
                ) + downloadActions,
            )
        }
    }) {
        if (state.openedPlaylistLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
        if (state.offline && !state.openedPlaylistLoading && tracks.isEmpty()) {
            item {
                Text(
                    "Нет сети, а этот плейлист ещё не сохранён на устройстве. Откройте его один раз при подключении к интернету — дальше он будет доступен без сети.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else if (editable && !state.openedPlaylistLoading && tracks.isEmpty()) {
            item { EmptyPlaylistPrompt(onAdd = { showPicker = true }) }
        }
        itemsIndexed(tracks, key = { i, _ -> keys[i] }) { i, t ->
            TrackRow(
                t,
                onRemove = if (editable) ({ vm.removeTrackFromPlaylist(p, t) }) else null,
                modifier = removeAnim().appear(if (i < 12) i else -1),
            ) { vm.play(tracks, i) }
        }
    }
    AnimatedVisibility(
        visible = showPicker,
        enter = fadeIn(tween(animMs(220))) + slideInVertically(tween(animMs(320))) { it / 6 },
        exit = fadeOut(tween(animMs(160))) + slideOutVertically(tween(animMs(240))) { it / 8 },
    ) {
        SwipeBack(onBack = { showPicker = false }) { AddSongsPage(vm, p, tracks) { showPicker = false } }
    }
    }
    if (showEdit && editable) {
        EditPlaylistSheet(
            title = p.title,
            // «Мне нравится» (kind = 3) всегда приватный — переключателя нет.
            isPublic = if (p.kind != 3 && ActiveProvider.caps.playlistVisibility) p.isPublic else null,
            onPublicChange = { vm.setPlaylistPublic(p, it) },
            // Окно закрывается само — с анимацией; здесь только действия.
            onDismiss = { showEdit = false },
            onSave = { name -> if (name.trim() != p.title) vm.renamePlaylist(p, name) },
            onDelete = { showDelete = true },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Удалить плейлист?") },
            text = { Text("«${p.title}» будет удалён без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = { vm.deletePlaylist(p); showDelete = false }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Отмена") } },
        )
    }
}

/**
 * Окно редактирования своего плейлиста: название и удаление. Закрывается с анимацией (шторка уезжает вниз):
 * [onSave] и [onDelete] только выполняют действие, а [onDismiss] вызывается после анимации.
 */
@Composable
private fun EditPlaylistSheet(
    title: String,
    isPublic: Boolean?,
    onPublicChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(title) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Сначала действие, затем шторка уезжает вниз, и только потом окно убирается из композиции.
    fun closeThen(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Редактирование плейлиста",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isPublic != null) {
                Spacer(Modifier.height(16.dp))
                // Публичность плейлиста (как в Яндекс Музыке): по ссылке публичный открывает и слушает кто угодно.
                Surface(shape = containerShape(20), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                if (isPublic) "Публичный плейлист" else "Приватный плейлист",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (isPublic) "Любой, у кого есть ссылка, сможет открыть и послушать его"
                                else "Видите только вы — поделиться им нельзя",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ExpressiveSwitch(checked = isPublic, onCheckedChange = onPublicChange)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { closeThen { onSave(name) } },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = rounded(28),
            ) { Text("Сохранить") }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { closeThen(onDelete) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(painterResource(R.drawable.ms_delete), null)
                Spacer(Modifier.width(8.dp)); Text("Удалить плейлист")
            }
        }
    }
}

/** Пустой плейлист: подсказка про смену названия и кнопка, открывающая поиск песен. */
@Composable
private fun EmptyPlaylistPrompt(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "В плейлисте пока пусто",
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Название и доступ меняются кнопкой «Редактировать» выше.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = rounded(28),
        ) {
            Icon(painterResource(R.drawable.ms_search), null)
            Spacer(Modifier.width(8.dp)); Text("Добавить песни")
        }
    }
}

/** Отдельный поиск: находим песни и добавляем их прямо в открытый плейлист. */
@Composable
private fun AddSongsPage(vm: AppViewModel, playlist: PlaylistInfo, tracks: List<Track>, onClose: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf(setOf<String>()) }
    val focus = remember { FocusRequester() }
    val inPlaylist = remember(tracks) { tracks.map { it.id }.toSet() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { vm.clearPlaylistSearch(); runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { vm.clearPlaylistSearch() } }
    LaunchedEffect(query) { delay(400); vm.searchForPlaylist(query) }
    // Если добавление не удалось, снимаем «крутилки», чтобы можно было нажать ещё раз.
    LaunchedEffect(state.error) { if (state.error != null) pending = emptySet() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            SubPageBar(title = "Добавить песни", onBack = onClose, subtitle = playlist.title)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Треки, исполнители") },
                leadingIcon = { Icon(painterResource(R.drawable.ms_search), null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(painterResource(R.drawable.ms_close), contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = rounded(32),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchForPlaylist(query) }),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .focusRequester(focus),
            )
            // Пока запрос пустой — показываем песни из «Любимого», чтобы их можно было добавить сразу.
            val results = if (query.isBlank()) state.liked else state.pickerResults
            when {
                query.isNotBlank() && state.pickerLoading && results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                query.isBlank() && results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Введите название трека или исполнителя", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                results.isEmpty() && !state.pickerLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(state = rememberTopListState(), contentPadding = PaddingValues(bottom = 24.dp + dockInset())) {
                    if (query.isBlank()) item(key = "likedHeader") {
                        Text(
                            "Из ваших лайков",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }
                    itemsIndexed(results, key = { i, t -> "${t.id}:$i" }) { _, t ->
                        val done = t.id in inPlaylist
                        val busy = !done && t.id in pending
                        val add = {
                            if (!done && !busy) {
                                pending = pending + t.id
                                vm.addTrackToPlaylist(playlist, t)
                            }
                        }
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    if (t.explicit) { Spacer(Modifier.width(6.dp)); ExplicitMark() }
                                }
                            },
                            supportingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TrackMarks(t)
                                    Text(t.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                }
                            },
                            leadingContent = {
                                Cover(model = t.cover(200), modifier = Modifier.size(52.dp).clip(rounded(14)))
                            },
                            trailingContent = {
                                when {
                                    done -> AnimatedCheck(
                                        checked = true, tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(12.dp), appear = true,
                                    )
                                    busy -> LoadingIndicator(Modifier.padding(12.dp).size(24.dp))
                                    else -> IconButton(onClick = { add() }) {
                                        Icon(painterResource(R.drawable.ms_add), contentDescription = "Добавить в плейлист")
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { add() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый плейлист") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ActiveProvider.caps.playlistVisibility) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Публичный", Modifier.weight(1f))
                        ExpressiveSwitch(checked = isPublic, onCheckedChange = { isPublic = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title, isPublic) }, enabled = title.isNotBlank()) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun RenamePlaylistDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить плейлист") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(title) }, enabled = title.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun AddToPlaylistDialog(
    playlists: List<PlaylistInfo>,
    onDismiss: () -> Unit,
    onPick: (PlaylistInfo) -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в плейлист") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Новый плейлист") },
                        leadingContent = { Icon(painterResource(R.drawable.ms_add), null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onCreate),
                    )
                }
                items(playlists) { p ->
                    ListItem(
                        headlineContent = { Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${p.trackCount} треков") },
                        leadingContent = {
                            Cover(model = p.cover(200), modifier = Modifier.size(44.dp).clip(rounded(12)))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(p) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ---------- Профиль ----------

@Composable
private fun ProfileScreen(vm: AppViewModel, onOpenSettings: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val profileScroll = rememberScrollState()
    ScrollToTopEffect { profileScroll.smoothToTop() }
    ReportScroll(profileScroll)
    val ctx = LocalContext.current
    val showPlaylist = LocalShowPlaylist.current
    val account = state.account
    // Аккаунт SoundCloud: под шапкой профиля показываем счётчики (треки, плейлисты, подписчики и т. д.).
    val scProfile = remember(state.accounts, state.activeToken) {
        state.accounts.firstOrNull { it.token == state.activeToken && it.provider == app.ymusic.m3e.data.Provider.SOUNDCLOUD }
            ?.let { app.ymusic.m3e.data.ScProfile.fromJson(it.scInfo) }
    }
    // Публичные плейлисты самого пользователя (без «Любимого» и чужих): их видят все, у кого есть ссылка.
    val publicPlaylists = remember(state.playlists, account) {
        state.playlists.filter { it.isPublic && it.kind != 3 && it.isEditableBy(account) }
    }
    // Свои треки и альбомы SoundCloud (только для аккаунта SoundCloud).
    LaunchedEffect(scProfile?.id) { scProfile?.let { vm.loadScOwn(it.id) } }
    LaunchedEffect(state.refreshing) { if (state.refreshing) scProfile?.let { vm.loadScOwn(it.id, force = true) } }
    val wide = LocalWide.current
    val profilePane: @Composable () -> Unit = {
    Box(Modifier.fillMaxSize()) {
    Refreshable(state.refreshing, { vm.pullRefresh() }) {
    Column(
        Modifier.fillMaxSize().verticalScroll(profileScroll)
            .padding(start = 20.dp, end = 20.dp, top = if (wide) 24.dp else 56.dp, bottom = 24.dp + dockInset()),
    ) {
        // Блоки профиля идут в порядке из редактора интерфейса.
        UiLayout.order(LScreen.PROFILE, ProfileBlock.entries).forEach { blk ->
            if (UiLayout.isHidden(LScreen.PROFILE, blk)) return@forEach
            if ((blk == ProfileBlock.MyTracks || blk == ProfileBlock.MyAlbums) && scProfile == null) return@forEach
            LayoutBlockBox(LScreen.PROFILE, blk) {
                when (blk) {
                    // Слева аватарка, справа ник, под ним юзернейм и кнопка «Поделиться».
                    ProfileBlock.Header -> Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = ThemeSettings.avatarShape.asShape(),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.size(UiLayout.profileAvatarDp.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(painterResource(R.drawable.ms_person), null, Modifier.size((UiLayout.profileAvatarDp / 2).dp))
                                account?.avatarUrl?.let { url ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(400).build(),
                                        contentDescription = "Аватар",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            val nick = account?.name?.ifBlank { null } ?: account?.login.orEmpty()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    nick, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                )
                                // Значок Яндекс Плюса у ника — только если подписка есть.
                                if (account?.hasPlus == true && account.provider == app.ymusic.m3e.data.Provider.YANDEX) PlusBadge(Modifier.padding(start = 8.dp).size(22.dp))
                            }
                            if (!account?.login.isNullOrBlank()) {
                                Text(
                                    "@" + account?.login.orEmpty(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val profileLink = if (account?.provider == app.ymusic.m3e.data.Provider.SOUNDCLOUD) scProfile?.profileUrl?.ifBlank { null }
                                else account?.login?.let { ShareLinks.profile(it) }
                            if (UiLayout.profileShare && profileLink != null) {
                                Spacer(Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = { ShareQrHost.open(nick, "$nick\n$profileLink", profileLink) },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                ) {
                                    Icon(painterResource(R.drawable.ms_share), null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Поделиться")
                                }
                            }
                        }
                    }
                    if (scProfile != null) SoundCloudStats(scProfile, Modifier.padding(top = 4.dp, bottom = 4.dp))
                    }
                    // Треки пользователя SoundCloud: первые пять, нажатие на заголовок открывает страницу исполнителя со всем списком.
                    ProfileBlock.MyTracks -> Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        val sc = scProfile
                        val tracks = state.scOwnTracks
                        Row(
                            Modifier.fillMaxWidth().clip(containerShape(16)).clickable {
                                if (sc != null) vm.openArtist(ArtistRef(app.ymusic.m3e.data.ScIds.PREFIX + sc.id, sc.username, sc.avatarUrl))
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Мои треки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (tracks.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text("${tracks.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(painterResource(R.drawable.ms_chevron_right), contentDescription = null)
                        }
                        // Загрузка своей музыки на SoundCloud: трек появляется в профиле и виден всем.
                        ScUploadSection(vm, state.scUploading, state.scUploadProgress, Modifier.padding(bottom = 8.dp))
                        when {
                            tracks.isEmpty() && state.scOwnLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                            tracks.isEmpty() -> Text("Своих треков пока нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // Строки треков имеют свой отступ 16dp, а экран профиля — 20dp: без выравнивания треки уезжали вправо на 36dp.
                            else -> Column(Modifier.fillMaxWidth().bleedH(16.dp)) {
                                tracks.take(5).forEachIndexed { i, t -> TrackRow(t) { vm.play(tracks, i) } }
                            }
                        }
                    }
                    // Альбомы (плейлисты) пользователя SoundCloud: ряд карточек.
                    ProfileBlock.MyAlbums -> Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        val albums = state.scOwnAlbums
                        if (albums.isEmpty() && state.scOwnLoading) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        } else {
                            Text(
                                "Мои альбомы" + if (albums.isNotEmpty()) " · ${albums.size}" else "",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                            if (albums.isEmpty()) {
                                Text("Своих альбомов пока нет", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(albums.take(20), key = { it.id }) { a -> AlbumCard(a, width = 140.dp) { vm.openAlbum(a) } }
                                }
                            }
                        }
                    }
                    // Все публичные плейлисты блоками.
                    ProfileBlock.Playlists -> Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text(
                            "Публичные плейлисты" + if (publicPlaylists.isNotEmpty()) " · ${publicPlaylists.size}" else "",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        if (publicPlaylists.isEmpty()) {
                            Text(
                                "Пока нет открытых плейлистов. Откройте плейлист и сделайте его публичным — он появится здесь.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            val cols = UiLayout.profileColumns.coerceAtLeast(1)
                            publicPlaylists.chunked(cols).forEach { rowItems ->
                                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    rowItems.forEach { p ->
                                        PlaylistCard(p, modifier = Modifier.weight(1f), fill = true) { showPlaylist(p) }
                                    }
                                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    ProfileBlock.Refresh -> FilledTonalButton(
                        onClick = { vm.refresh() },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(56.dp),
                    ) {
                        Icon(painterResource(R.drawable.ms_refresh), null); Spacer(Modifier.width(8.dp)); Text("Обновить")
                    }
                }
            }
        }
    }
    }
    // Настройки — в правом верхнем углу (на планшете они стоят справа от профиля, кнопка не нужна).
    if (!wide) IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
    ) {
        Icon(painterResource(R.drawable.ms_settings), contentDescription = "Настройки")
    }
    }
    }
    if (wide) {
        // Планшет: слева профиль, справа меню настроек.
        TwoPane(
            left = {
                Surface(
                    shape = rounded(32), color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
                ) { profilePane() }
            },
            right = { SettingsScreen(vm, local = false, embedded = true, onClose = {}) },
        )
    } else profilePane()
}

// ---------- Настройки ----------

/**
 * Появление элемента при открытии страницы: плавно проявляется и «подплывает» снизу с небольшой задержкой по номеру
 * ([index]), поэтому группы и кнопки выстраиваются каскадом. Играет один раз для страницы (флаг сохраняется, поэтому при
 * возврате элемента в ленту после прокрутки анимация не повторяется). При включённой «Оптимизации» отключена.
 */
@Composable
private fun Modifier.entrance(index: Int = 0, offsetDp: Int = 14): Modifier {
    if (ThemeSettings.optimize) return this
    var played by rememberSaveable { mutableStateOf(false) }
    val p = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            delay(animMs(40 + index.coerceAtMost(8) * 45).toLong())
            p.animateTo(1f, tween(animMs(380), easing = EmphasizedDecelerate))
            played = true
        }
    }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * offsetDp * density
    }
}

/** Счётчик очередности элементов на странице: каждая следующая группа появляется чуть позже предыдущей. */
private class EntranceCounter { var n = 0 }

private val LocalEntrance = staticCompositionLocalOf<EntranceCounter?> { null }

/** Глубина страницы настроек: по ней понятно, открываем мы страницу (вглубь) или возвращаемся. */
private fun SettingsPage.depth(): Int = when (this) {
    SettingsPage.Main -> 0
    SettingsPage.LogsCurrent, SettingsPage.LogsPrevious, SettingsPage.Layout, SettingsPage.Developers -> 2
    else -> 1
}

private enum class SettingsPage { Main, Accounts, Equalizer, Appearance, Player, Haptics, Recognition, Downloads, Offline, Plugins, Updates, About, Developers, Developer, LogsCurrent, LogsPrevious, Layout, ScProxy, Cache }

private val APP_VERSION: String get() = app.ymusic.m3e.data.Updates.current.display

@Composable
private fun SettingsScreen(vm: AppViewModel, local: Boolean = false, onLogin: () -> Unit = {}, embedded: Boolean = false, onClose: () -> Unit) {
    var page by remember { mutableStateOf(SettingsPage.Main) }
    val searchCtx = LocalContext.current
    // Подсветка пункта, выбранного в поиске, гаснет сама.
    LaunchedEffect(SettingsHighlight.stamp) {
        if (SettingsHighlight.stamp != 0) { delay(3600); SettingsHighlight.clear() }
    }
    // Встроенные настройки (справа от профиля на планшете): вложенная страница не даёт листать вкладки, а повторное
    // нажатие на вкладку «Профиль» возвращает на главную страницу настроек.
    if (embedded && page != SettingsPage.Main) PagerLockEffect()
    InnerPageEffect("embeddedSettings", embedded && page != SettingsPage.Main) { page = SettingsPage.Main }
    // Плагин, чей пункт («settings_entry») открыт в Настройках: его настройки показываются нижней модалкой.
    var pluginEntry by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val uiState by vm.state.collectAsStateWithLifecycle()
    BackHandler(enabled = !embedded || page != SettingsPage.Main) {
        when (page) {
            SettingsPage.Main -> onClose()
            SettingsPage.LogsCurrent, SettingsPage.LogsPrevious -> page = SettingsPage.Developer
            SettingsPage.Layout -> page = SettingsPage.Appearance
            SettingsPage.Developers -> page = SettingsPage.About
            else -> page = SettingsPage.Main
        }
    }
    // Жест «назад»: на главной странице настроек закрывает их целиком, на вложенных (внизу) — возвращает на главную.
    SwipeBack(enabled = page == SettingsPage.Main && !embedded, onBack = onClose) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                // Вглубь (в раздел, в логи): новая страница выезжает справа, прежняя чуть уходит влево.
                // Назад: прежняя уезжает вправо поверх, под ней проявляется предыдущая.
                val forward = targetState.depth() >= initialState.depth() && targetState != SettingsPage.Main
                if (forward) {
                    androidx.compose.animation.ContentTransform(
                        targetContentEnter = pageEnter(),
                        initialContentExit = slideOutHorizontally(tween(animMs(300), easing = EmphasizedDecelerate)) { -(it * 0.12f).toInt() } +
                            fadeOut(tween(animMs(220), delayMillis = animMs(80))),
                        targetContentZIndex = 1f,
                        sizeTransform = null,
                    )
                } else {
                    androidx.compose.animation.ContentTransform(
                        targetContentEnter = slideInHorizontally(tween(animMs(320), easing = EmphasizedDecelerate)) { -(it * 0.12f).toInt() } +
                            fadeIn(tween(animMs(220))),
                        initialContentExit = pageExit(),
                        targetContentZIndex = -1f,
                        sizeTransform = null,
                    )
                }
            },
            label = "settingsPages",
        ) { p ->
            val body: @Composable () -> Unit = {
            when (p) {
                SettingsPage.Main -> SettingsMain(
                    onBack = onClose,
                    embedded = embedded,
                    accountTitle = uiState.account?.name?.ifBlank { null } ?: uiState.account?.login.orEmpty(),
                    accountLogin = uiState.account?.login.orEmpty(),
                    accountAvatar = uiState.account?.avatarUrl,
                    accountCount = uiState.accounts.size,
                    local = local,
                    onLogin = onLogin,
                    onAccounts = { page = SettingsPage.Accounts },
                    onEqualizer = { page = SettingsPage.Equalizer },
                    onAppearance = { page = SettingsPage.Appearance },
                    onPlayer = { page = SettingsPage.Player },
                    onHaptics = { page = SettingsPage.Haptics },
                    onRecognition = { page = SettingsPage.Recognition },
                    onScProxy = { page = SettingsPage.ScProxy },
                    onSearchOpen = { key ->
                        when (key) {
                            "main" -> Unit
                            "links" -> AppLinks.openSettings(searchCtx)
                            else -> runCatching { SettingsPage.valueOf(key) }.getOrNull()?.let { page = it }
                        }
                    },
                    onDownloads = { page = SettingsPage.Downloads },
                    onCache = { page = SettingsPage.Cache },
                    onOffline = { page = SettingsPage.Offline },
                    onPlugins = { page = SettingsPage.Plugins },
                    onPluginEntry = { id, entry -> pluginEntry = id to entry },
                    onUpdates = { page = SettingsPage.Updates },
                    onAbout = { page = SettingsPage.About },
                    onDeveloper = { page = SettingsPage.Developer },
                    onLogout = { vm.logout() },
                )
                SettingsPage.Accounts -> AccountsPage(vm) { page = SettingsPage.Main }
                SettingsPage.Equalizer -> EqualizerPage(vm) { page = SettingsPage.Main }
                SettingsPage.Appearance -> AppearancePage(onBack = { page = SettingsPage.Main }, onOpenEditor = { page = SettingsPage.Layout })
                SettingsPage.Layout -> LayoutEditorPage { page = SettingsPage.Appearance }
                SettingsPage.Player -> PlayerSettingsPage { page = SettingsPage.Main }
                SettingsPage.Haptics -> HapticsSettingsPage { page = SettingsPage.Main }
                SettingsPage.Recognition -> RecognitionSettingsPage { page = SettingsPage.Main }
                SettingsPage.ScProxy -> ScProxyPage { page = SettingsPage.Main }
                SettingsPage.Downloads -> DownloadsPage(vm) { page = SettingsPage.Main }
                SettingsPage.Cache -> CachePage { page = SettingsPage.Main }
                SettingsPage.Offline -> OfflinePage { page = SettingsPage.Main }
                SettingsPage.Plugins -> PluginsPage { page = SettingsPage.Main }
                SettingsPage.Updates -> UpdatesPage { page = SettingsPage.Main }
                SettingsPage.About -> AboutPage(onBack = { page = SettingsPage.Main }, onDevelopers = { page = SettingsPage.Developers })
                SettingsPage.Developers -> DevelopersPage { page = SettingsPage.About }
                SettingsPage.Developer -> DeveloperPage(
                    onBack = { page = SettingsPage.Main },
                    onCurrent = { page = SettingsPage.LogsCurrent },
                    onPrevious = { page = SettingsPage.LogsPrevious },
                )
                SettingsPage.LogsCurrent -> LogViewerPage(
                    title = "Логи", previous = false, onBack = { page = SettingsPage.Developer },
                )
                SettingsPage.LogsPrevious -> LogViewerPage(
                    title = "Логи последнего запуска", previous = true, onBack = { page = SettingsPage.Developer },
                )
            }
            }
            // Каждая страница непрозрачная и со своим счётчиком появления элементов: при переходе тексты двух страниц
            // не просвечивают друг через друга, а группы внутри выстраиваются каскадом.
            val entranceCounter = remember { EntranceCounter() }
            CompositionLocalProvider(LocalEntrance provides entranceCounter) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    if (p == SettingsPage.Main) body() else {
                        val back = when (p) {
                            SettingsPage.LogsCurrent, SettingsPage.LogsPrevious -> SettingsPage.Developer
                            SettingsPage.Layout -> SettingsPage.Appearance
                            SettingsPage.Developers -> SettingsPage.About
                            else -> SettingsPage.Main
                        }
                        SwipeBack(onBack = { page = back }, content = body)
                    }
                }
            }
        }
    }
    }
    pluginEntry?.let { (id, entryId) ->
        val sp = PluginSystem.plugins.firstOrNull { it.id == id }
        val has = sp != null && (if (entryId == null) sp.manifest.settings.isNotEmpty() else sp.uiEntries.any { it.id == entryId })
        if (sp != null && has) PluginSettingsSheet(sp, entryId) { pluginEntry = null }
        else pluginEntry = null
    }
}

@Composable
private fun SettingsMain(
    onBack: () -> Unit,
    embedded: Boolean = false,
    accountTitle: String,
    accountLogin: String,
    accountAvatar: String?,
    accountCount: Int,
    local: Boolean = false,
    onLogin: () -> Unit = {},
    onAccounts: () -> Unit,
    onEqualizer: () -> Unit,
    onAppearance: () -> Unit,
    onPlayer: () -> Unit,
    onHaptics: () -> Unit,
    onRecognition: () -> Unit,
    onScProxy: () -> Unit = {},
    onSearchOpen: (String) -> Unit = {},
    onDownloads: () -> Unit,
    onCache: () -> Unit,
    onOffline: () -> Unit,
    onPlugins: () -> Unit = {},
    onPluginEntry: (String, String?) -> Unit = { _, _ -> },
    onUpdates: () -> Unit,
    onAbout: () -> Unit,
    onDeveloper: () -> Unit,
    onLogout: () -> Unit,
) {
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val logoutColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
    Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = 24.dp + dockInset())) {
        SubPageBar("Настройки", onBack, showBack = !embedded)
        // Поиск по названиям и описаниям пунктов (в том числе внутри страниц): пока запрос не пуст, вместо групп показываются результаты.
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val searchCtx2 = LocalContext.current
        SettingsSearchField(searchQuery, onSearch = { SettingsSearchHistory.add(searchCtx2, searchQuery) }) { searchQuery = it }
        if (searchQuery.isNotBlank()) {
            SettingsSearchResults(searchQuery) { e ->
                SettingsSearchHistory.add(searchCtx2, searchQuery)
                SettingsHighlight.set(e)
                searchQuery = ""
                onSearchOpen(e.page)
            }
        } else {
        SettingsSearchHistoryRow(searchCtx2) { searchQuery = it }
        // Material 3 Expressive: пункты собраны в группы с крупным скруглением снаружи и тонкими разделителями-зазорами.
        // Каждая группа — свой раздел со своей подписью.
        if (local) {
            // Режим «Локальные файлы»: вместо аккаунта — вход через Яндекс.
            SettingsGroup {
                ListItem(
                    headlineContent = { Text("Войти через Яндекс") },
                    supportingContent = { Text("«Любимое», плейлисты и «Моя волна» из Яндекс Музыки") },
                    leadingContent = { AccountAvatar(null, 40.dp) },
                    trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                    colors = itemColors,
                    modifier = Modifier.clickable(onClick = onLogin),
                )
            }
        } else
        SettingsGroup {
            ListItem(
                headlineContent = { Text(accountTitle.ifBlank { "Аккаунты" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        buildString {
                            if (accountLogin.isNotBlank() && accountLogin != accountTitle) append(accountLogin).append(" · ")
                            append(if (accountCount > 1) "Аккаунтов: $accountCount" else "Аккаунты")
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = { AccountAvatar(accountAvatar, 40.dp) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onAccounts),
            )
        }
        SettingsLabel("Интерфейс")
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Внешний вид") },
                supportingContent = { Text("Тема, цвета, шрифт, формы, размер, стекло, свечение, анимации, главная, поиск и док-панель") },
                leadingContent = { Icon(painterResource(R.drawable.ms_palette), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onAppearance),
            )
            ListItem(
                headlineContent = { Text("Вибрация") },
                supportingContent = { Text(if (Haptics.enabled) "Виброотклик: ${Haptics.strength.title.lowercase()}" else "Выключена") },
                leadingContent = { Icon(painterResource(R.drawable.ms_vibration), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onHaptics),
            )
            LanguageSettingItem(itemColors)
        }
        SettingsLabel("Музыка")
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Плеер") },
                supportingContent = { Text("Качество звука, мини-плеер, полоса прогресса, тексты песен, волна") },
                leadingContent = { Icon(painterResource(R.drawable.ms_music_note), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onPlayer),
            )
            ListItem(
                headlineContent = { Text("Эквалайзер") },
                leadingContent = { Icon(painterResource(R.drawable.ms_graphic_eq), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onEqualizer),
            )
            ListItem(
                headlineContent = { Text("Распознавание музыки") },
                supportingContent = { Text("Ключ сервиса, длина записи, плитка в шторке") },
                leadingContent = { Icon(painterResource(R.drawable.ms_mic), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onRecognition),
            )
        }
        SettingsLabel("Сеть")
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Обход блокировки SoundCloud (BETA)") },
                supportingContent = { Text(scProxySummary()) },
                leadingContent = { Icon(painterResource(R.drawable.ms_cloud_download), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onScProxy),
            )
            val linksCtx = LocalContext.current
            ListItem(
                headlineContent = { Text(if (ActiveProvider.isSc) "Ссылки SoundCloud" else if (ActiveProvider.isVk) "Ссылки VK Музыки" else "Ссылки Яндекс Музыки") },
                supportingContent = { Text(if (ActiveProvider.isSc) "Открывать ссылки soundcloud.com в этом приложении" else if (ActiveProvider.isVk) "Открывать ссылки vk.com и vk.ru на музыку в этом приложении" else "Открывать ссылки music.yandex.ru в этом приложении") },
                leadingContent = { Icon(painterResource(R.drawable.ms_open_in_new), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable { AppLinks.openSettings(linksCtx) },
            )
        }
        SettingsLabel("Данные")
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Скачивание песен") },
                supportingContent = { Text("Куда сохранять, папка, сеть и список скачанного") },
                leadingContent = { Icon(painterResource(R.drawable.ms_download), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onDownloads),
            )
            ListItem(
                headlineContent = { Text("Кэш") },
                supportingContent = { Text("Размер и очистка: обложки, тексты, предзагруженный звук") },
                leadingContent = { Icon(painterResource(R.drawable.ms_delete), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onCache),
            )
            if (!local) ListItem(
                headlineContent = { Text("Офлайн-режим") },
                supportingContent = { Text("Файлы с устройства") },
                leadingContent = { Icon(painterResource(R.drawable.ms_cloud_off), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onOffline),
            )
        }
        SettingsLabel("Приложение")
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Плагины") },
                supportingContent = {
                    Text(if (PluginSystem.enabled) "Включены, установлено: ${PluginSystem.plugins.size}" else "Выключены")
                },
                leadingContent = { Icon(painterResource(R.drawable.ms_extension), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onPlugins),
            )
            // Свои пункты плагинов (contributes.settings_entry): видны, пока плагин и система плагинов включены.
            if (PluginSystem.enabled) PluginSystem.plugins.filter { it.enabled && it.manifest.menuEntry != null && it.manifest.settings.isNotEmpty() }.forEach { pl ->
                val me = pl.manifest.menuEntry!!
                key(pl.id) {
                    ListItem(
                        headlineContent = { Text(me.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(me.summary.ifBlank { "Плагин · ${pl.manifest.name}" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { PluginIcon(pl.iconBytes, 40.dp) },
                        trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                        colors = itemColors,
                        modifier = Modifier.clickable { onPluginEntry(pl.id, null) },
                    )
                }
            }
            // Страницы, которые плагины создали сами из Lua (ui.settings_page с menu = true).
            if (PluginSystem.enabled) PluginSystem.plugins.filter { it.enabled }.forEach { pl ->
                pl.uiEntries.filter { it.menu && !it.transient }.forEach { e ->
                    key(pl.id, e.id) {
                        ListItem(
                            headlineContent = { Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(e.summary.ifBlank { "Плагин · ${pl.manifest.name}" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { PluginIcon(pl.iconBytes, 40.dp) },
                            trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                            colors = itemColors,
                            modifier = Modifier.clickable { onPluginEntry(pl.id, e.id) },
                        )
                    }
                }
            }
            ListItem(
                headlineContent = { Text("Обновления") },
                supportingContent = { Text("Версия ${app.ymusic.m3e.data.Updates.current.tag} · ветка ${app.ymusic.m3e.data.Updates.channel.title}") },
                leadingContent = { Icon(painterResource(R.drawable.ms_download), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onUpdates),
            )
            ListItem(
                headlineContent = { Text("О проекте") },
                leadingContent = { Icon(painterResource(R.drawable.ms_info), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onAbout),
            )
            ListItem(
                headlineContent = { Text("Для разработчиков") },
                supportingContent = { Text("Логи клиента и логи последнего запуска") },
                leadingContent = { Icon(painterResource(R.drawable.ms_info), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onDeveloper),
            )
        }
        if (!local) {
        Spacer(Modifier.height(8.dp))
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Выйти из аккаунта", color = MaterialTheme.colorScheme.error) },
                leadingContent = {
                    Icon(painterResource(R.drawable.ms_logout), null, tint = MaterialTheme.colorScheme.error)
                },
                colors = logoutColors,
                modifier = Modifier.clickable(onClick = onLogout),
            )
        }
        }
        }
        // Пасхалка: несколько быстрых нажатий подряд по этой надписи.
        var egg by remember { mutableStateOf(false) }
        var eggTaps by remember { mutableStateOf(0) }
        var eggLast by remember { mutableStateOf(0L) }
        Text(
            "*Не является официальным продуктом Яндекс",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures {
                        val now = android.os.SystemClock.uptimeMillis()
                        eggTaps = if (now - eggLast < 700L) eggTaps + 1 else 1
                        eggLast = now
                        Haptics.tick()
                        if (eggTaps >= 5) { eggTaps = 0; egg = true }
                    }
                }
                .padding(horizontal = 24.dp, vertical = 24.dp),
        )
        if (egg) EasterEgg { egg = false }
    }
}

/**
 * Пасхалка: на весь экран (с растяжением) вылетает картинка с криком «А-а-а!», затем появляется алерт.
 */
@Composable
private fun EasterEgg(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scale = remember { Animatable(0.05f) }
    val turn = remember { Animatable(-25f) }
    var shake by remember { mutableStateOf(0f) }
    var alert by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val mp = runCatching {
            android.media.MediaPlayer.create(ctx, R.raw.easter_scream)?.apply {
                setVolume(1f, 1f)
                setOnCompletionListener { it.release() }
                start()
            }
        }.getOrNull()
        onDispose { runCatching { mp?.release() } }
    }
    LaunchedEffect(Unit) {
        Haptics.heavy()
        launch { turn.animateTo(0f, fxSpring(dampingRatio = 0.35f, stiffness = 180f)) }
        launch { scale.animateTo(1f, fxSpring(dampingRatio = 0.45f, stiffness = 260f)) }
        // Тряска, пока кричит.
        repeat(26) { i ->
            shake = if (i % 2 == 0) 1f else -1f
            if (i % 4 == 0) Haptics.heavy()
            delay(45)
        }
        shake = 0f
        delay(250)
        alert = true
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = false, dismissOnClickOutside = false,
        ),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.easter_cat),
            contentDescription = null,
            contentScale = ContentScale.FillBounds, // растянута на весь экран
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale.value; scaleY = scale.value
                rotationZ = turn.value + shake * 1.5f
                translationX = shake * 14.dp.toPx()
            },
        )
    }
    if (alert) {
        AlertDialog(
            onDismissRequest = onDone,
            text = {
                Text(
                    "НЕ ВАЙБКОД ВСЕ САМ✅✅✅✅✅✅",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = onDone) { Text("OK") } },
        )
    }
}

/** Группа пунктов настроек в стиле M3E: крупное скругление снаружи, 2 dp между пунктами. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    val counter = LocalEntrance.current
    val order = remember { counter?.let { it.n++ } ?: 0 }
    Column(
        Modifier.fillMaxWidth().entrance(order).padding(horizontal = 16.dp, vertical = 6.dp).clip(containerShape(28)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** Круглая «печенька» с аватаром аккаунта (или значком человека, если аватара нет). */
@Composable
internal fun AccountAvatar(avatarUrl: String?, size: Dp) {
    Surface(
        shape = MaterialShapes.Cookie9Sided.toShape(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ms_person), null, Modifier.size(size / 2))
            avatarUrl?.let { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(300).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Значок Яндекс Плюса: наклонный плюс в кружке акцентного цвета темы (вместо градиентной картинки), фон прозрачный. */
@Composable
private fun PlusBadge(modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.primary
    val fg = MaterialTheme.colorScheme.onPrimary
    Canvas(modifier) {
        val k = size.minDimension / 256f
        val circle = androidx.compose.ui.graphics.Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(0f, 0f, 256f * k, 256f * k))
        }
        drawCircle(bg, radius = size.minDimension / 2f, center = center)
        clipPath(circle) {
            val plus = androidx.compose.ui.graphics.Path().apply {
                // горизонтальная перекладина
                moveTo(50f * k, 95f * k); lineTo(256f * k, 95f * k); lineTo(256f * k, 128f * k); lineTo(42f * k, 128f * k); close()
                // наклонная вертикальная перекладина
                moveTo(165f * k, 0f); lineTo(205f * k, 0f); lineTo(142f * k, 205f * k); lineTo(105f * k, 205f * k); close()
            }
            drawPath(plus, fg)
        }
    }
}

/** Аккаунты: переключение, выход из одного или из всех, вход в новый. */
@Composable
private fun AccountsPage(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var logoutTarget by remember { mutableStateOf<StoredAccount?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    // «Добавить аккаунт»: снизу выезжает шторка с выбором сервиса.
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Аккаунты", onBack)
        LazyColumn(Modifier.fillMaxSize(), state = rememberTopListState(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp + dockInset())) {
            items(state.accounts, key = { it.token }) { a ->
                val active = a.token == state.activeToken
                val sub = listOfNotNull(
                    a.provider.title,
                    a.login.takeIf { it.isNotBlank() && it != a.title },
                    if (a.provider == app.ymusic.m3e.data.Provider.SOUNDCLOUD) {
                        app.ymusic.m3e.data.ScProfile.fromJson(a.scInfo)?.let { "${compactCount(it.followers)} подписчиков" }
                    } else null,
                    if (a.hasPlus && a.provider == app.ymusic.m3e.data.Provider.YANDEX) "Плюс" else null,
                    if (active) "Активный" else null,
                ).joinToString(" · ")
                ListItem(
                    headlineContent = { Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (sub.isNotEmpty()) ({ Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                    leadingContent = { AccountAvatar(a.avatarUrl, 48.dp) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (active) AnimatedCheck(true, tint = LocalContentColor.current)
                            IconButton(onClick = { logoutTarget = a }) {
                                Icon(painterResource(R.drawable.ms_logout), contentDescription = "Выйти из аккаунта")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable(enabled = !active) { vm.switchAccount(a.token) },
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = rounded(28),
                ) {
                    Icon(painterResource(R.drawable.ms_add), null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить аккаунт")
                }
                if (state.accounts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { confirmAll = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(painterResource(R.drawable.ms_logout), null)
                        Spacer(Modifier.width(8.dp))
                        Text("Выйти из всех аккаунтов")
                    }
                }
            }
        }
    }

    if (showPicker) {
        ProviderPickerSheet(
            onPick = { showPicker = false; vm.startAddAccount(it) },
            onDismiss = { showPicker = false },
        )
    }

    logoutTarget?.let { a ->
        val isActive = a.token == state.activeToken
        AlertDialog(
            onDismissRequest = { logoutTarget = null },
            title = { Text("Выйти из аккаунта?") },
            text = {
                Text(
                    buildString {
                        append("Аккаунт «${a.title}» будет удалён с этого устройства.")
                        if (isActive && state.accounts.size > 1) append(" Приложение переключится на другой аккаунт.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.logoutAccount(a.token); logoutTarget = null }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { logoutTarget = null }) { Text("Отмена") } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Выйти из всех аккаунтов?") },
            text = { Text("Все аккаунты будут удалены с этого устройства, воспроизведение остановится.") },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; vm.logoutAll() }) {
                    Text("Выйти", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Отмена") } },
        )
    }
}

/** Выпадающий список (комбобокс): поле только для чтения, по нажатию раскрывается меню на всю ширину поля. */
@Composable
private fun <T> ComboBox(
    selectedText: String,
    options: List<T>,
    optionLabel: (T) -> String,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    optionFont: (T) -> androidx.compose.ui.text.font.FontFamily? = { null },
    optionSupporting: (T) -> String? = { null },
) {
    var expanded by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "comboArrow")
    val fieldShape = rounded(16)
    Box(Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            shape = fieldShape,
            trailingIcon = {
                Icon(painterResource(R.drawable.ms_expand_more), null, Modifier.graphicsLayer { rotationZ = arrow })
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Прозрачный слой поверх поля: нажатие в любом месте открывает список.
        Box(Modifier.matchParentSize().clip(fieldShape).clickable { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(with(density) { widthPx.toDp() }),
        ) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(optionLabel(o), fontFamily = optionFont(o), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            optionSupporting(o)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    trailingIcon = if (isSelected(o)) ({ Icon(painterResource(R.drawable.ms_check), null) }) else null,
                    onClick = { expanded = false; onSelect(o) },
                )
            }
        }
    }
}

/** Настройки плеера: звук, мини-плеер, полный плеер, полоса прогресса, тексты песен и волна. */
@Composable
private fun PlayerSettingsPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Плеер", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = dockInset())) {
            SettingsSection("Звук", R.drawable.ms_graphic_eq, startExpanded = true) {
            val tokens = (LocalContext.current.applicationContext as app.ymusic.m3e.App).tokenStore
            val hqAcc = tokens.accounts.firstOrNull { it.token == tokens.token }
            val hasPlusHq = hqAcc?.hasPlus == true || hqAcc?.provider == app.ymusic.m3e.data.Provider.VK
            ListItem(
                headlineContent = { Text("Высокое качество") },
                supportingContent = {
                    Text(
                        if (!hasPlusHq) "Нужен Яндекс Плюс: без подписки доступен только обычный поток"
                        else if (hqAcc?.provider == app.ymusic.m3e.data.Provider.VK) "VK: если сервер отдаёт несколько битрейтов — выбирается лучший, иначе единственный поток"
                        else if (PlayerSettings.highQuality) "Лучший доступный поток: lossless (FLAC) или до 320 кбит/с"
                        else "Экономный поток (~128 кбит/с): меньше трафика и места"
                    )
                },
                leadingContent = { Icon(painterResource(R.drawable.ms_graphic_eq), null) },
                trailingContent = { ExpressiveSwitch(checked = hasPlusHq && PlayerSettings.highQuality, onCheckedChange = if (hasPlusHq) ({ PlayerSettings.updateHighQuality(it) }) else null) },
                modifier = if (hasPlusHq) Modifier.clickable { PlayerSettings.updateHighQuality(!PlayerSettings.highQuality) } else Modifier,
            )
            Text(
                "Качество применяется к следующим трекам и к новым загрузкам. Для lossless нужен Яндекс Плюс; если сервер " +
                    "не отдаёт его для вашего аккаунта, приложение автоматически выберет лучшее из доступных.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Предзагрузка треков") },
                supportingContent = {
                    Text(
                        if (PlayerSettings.preload) "Следующие 3 трека заранее получают ссылку и первые секунды звука: переключение без ожидания"
                        else "Трек начинает загружаться только в момент переключения"
                    )
                },
                leadingContent = { Icon(painterResource(R.drawable.ms_cloud_download), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.preload, onCheckedChange = { PlayerSettings.updatePreload(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updatePreload(!PlayerSettings.preload) },
            )
            var showMix by rememberSaveable { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("Микс треков") },
                supportingContent = { Text(mixSummary() + " · плавный переход между треками, как «Кроссфейд» в Spotify") },
                leadingContent = { Icon(painterResource(R.drawable.ms_graphic_eq), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.crossfade, onCheckedChange = { PlayerSettings.updateCrossfade(it) }) },
                modifier = Modifier.clickable { showMix = true },
            )
            if (showMix) MixSheet { showMix = false }
            }
            SettingsSection("Мини-плеер", R.drawable.ms_music_note) {
            ListItem(
                headlineContent = { Text("Перематывание треков в мини-плеере") },
                supportingContent = { Text("Ползунок под названием трека — в том же стиле, что и в полном плеере") },
                leadingContent = { Icon(painterResource(R.drawable.ms_schedule), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.miniSeek, onCheckedChange = { PlayerSettings.updateMiniSeek(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updateMiniSeek(!PlayerSettings.miniSeek) },
            )
            ListItem(
                headlineContent = { Text("Изменение громкости в мини-плеере") },
                supportingContent = { Text("Ползунок системной громкости музыки") },
                leadingContent = { Icon(painterResource(R.drawable.ms_volume_up), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.miniVolume, onCheckedChange = { PlayerSettings.updateMiniVolume(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updateMiniVolume(!PlayerSettings.miniVolume) },
            )
            ListItem(
                headlineContent = { Text("Цвет мини-плеера по обложке") },
                supportingContent = { Text("Подложка мини-плеера подстраивается под цвет обложки играющего трека") },
                leadingContent = { Icon(painterResource(R.drawable.ms_palette), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.miniCoverColor, onCheckedChange = { PlayerSettings.updateMiniCoverColor(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updateMiniCoverColor(!PlayerSettings.miniCoverColor) },
            )
            Text(
                "Когда включены оба пункта, ползунок переключается между перемоткой и громкостью кнопкой справа от него. " +
                    "Свайп влево/вправо по мини-плееру переключает треки, свайп вверх — открывает плеер.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            }
            SettingsSection("Полный плеер", R.drawable.ms_open_in_full) {
            ListItem(
                headlineContent = { Text("Громкость в полном плеере") },
                supportingContent = { Text("Ползунок системной громкости под кнопками управления; значок динамика слева сворачивает его") },
                leadingContent = { Icon(painterResource(R.drawable.ms_volume_up), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.fullVolume, onCheckedChange = { PlayerSettings.updateFullVolume(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updateFullVolume(!PlayerSettings.fullVolume) },
            )
            ListItem(
                headlineContent = { Text("Размытый фон плеера") },
                supportingContent = { Text("Обложка текущего трека, размытая, на заднем фоне полного плеера") },
                leadingContent = { Icon(painterResource(R.drawable.ms_open_in_full), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.playerBgBlur, onCheckedChange = { PlayerSettings.updatePlayerBgBlur(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updatePlayerBgBlur(!PlayerSettings.playerBgBlur) },
            )
            ListItem(
                headlineContent = { Text("Размытие текста песни в плеере") },
                supportingContent = {
                    Text(
                        if (android.os.Build.VERSION.SDK_INT >= 31) "Строки, которые ещё не поются, размыты — чётко видна только текущая"
                        else "Работает на Android 12 и новее — на этом устройстве размытие недоступно",
                    )
                },
                leadingContent = { Icon(painterResource(R.drawable.ms_lyrics), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.lyricsBlur, onCheckedChange = { PlayerSettings.updateLyricsBlur(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updateLyricsBlur(!PlayerSettings.lyricsBlur) },
            )
            ListItem(
                headlineContent = { Text("Размытие текста на весь экран") },
                supportingContent = {
                    Text(
                        if (android.os.Build.VERSION.SDK_INT >= 31) "Отдельно от плеера: в тексте песни на весь экран ещё не спетые строки тоже размыты"
                        else "Работает на Android 12 и новее — на этом устройстве размытие недоступно",
                    )
                },
                leadingContent = { Icon(painterResource(R.drawable.ms_lyrics), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.lyricsBlurFull, onCheckedChange = { PlayerSettings.updateLyricsBlurFull(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updateLyricsBlurFull(!PlayerSettings.lyricsBlurFull) },
            )
            }
            SettingsSection("Полоса прогресса", R.drawable.ms_schedule, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            SeekStyleSection()
            }
            SettingsSection("Тексты песен", R.drawable.ms_lyrics) {
            Text(
                "Откуда брать текст. Включите только нужные источники — остальные опрашиваться не будут. " +
                    "Ремиксы, live и другие версии не путаются с оригиналом: текст берётся только у записи той же версии.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListItem(
                headlineContent = { Text("Подтверждать выбор текста") },
                supportingContent = { Text("При выборе текста в ручном поиске показывать окно с подтверждением") },
                leadingContent = { Icon(painterResource(R.drawable.ms_search), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.lyricsConfirm, onCheckedChange = { PlayerSettings.updateLyricsConfirm(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updateLyricsConfirm(!PlayerSettings.lyricsConfirm) },
            )
            ListItem(
                headlineContent = { Text("Привязка текста к треку") },
                supportingContent = {
                    Text("Выбранный вручную текст запоминается для этого трека и показывается первым. В окне подтверждения — галочка; если окно отключено, текст привязывается сразу")
                },
                leadingContent = { Icon(painterResource(R.drawable.ms_lyrics), null) },
                trailingContent = { ExpressiveSwitch(checked = PlayerSettings.lyricsPin, onCheckedChange = { PlayerSettings.updateLyricsPin(it) }) },
                modifier = Modifier.clickable { PlayerSettings.updateLyricsPin(!PlayerSettings.lyricsPin) },
            )
            val lyricIds = LyricsSourceOptions.map { it.first }
            LyricsSourceOptions.forEach { (id, name, desc) ->
                val on = id !in PlayerSettings.lyricsOff
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(desc) },
                    leadingContent = { Icon(painterResource(R.drawable.ms_lyrics), null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { PlayerSettings.onlyLyricsSource(id, lyricIds) }) { Text("Только он") }
                            ExpressiveSwitch(checked = on, onCheckedChange = { PlayerSettings.setLyricsSource(id, it, lyricIds) })
                        }
                    },
                    modifier = Modifier.clickable { PlayerSettings.setLyricsSource(id, !on, lyricIds) },
                )
            }
            if (PlayerSettings.lyricsOff.isNotEmpty()) {
                TextButton(onClick = { PlayerSettings.resetLyricsSources() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Включить все источники")
                }
            }
            }
            SettingsSection("Волна", R.drawable.ms_graphic_eq, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            GlowSwitchRow(
                if (ActiveProvider.isVk) "Не повторять треки VK Микса" else if (ActiveProvider.isSc) "Не повторять треки ExWave" else "Не повторять треки волны",
                "Трек, который уже прозвучал в волне, в течение 24 часов в неё не вернётся. Работает для «Моей волны», VK Микса и ExWave. Выключите, чтобы треки могли повторяться",
                app.ymusic.m3e.data.WaveSeen.enabled,
            ) { app.ymusic.m3e.data.WaveSeen.updateEnabled(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- Для разработчиков ----------

/** Раздел «Для разработчиков»: журнал текущего запуска и полный журнал прошлого запуска. */
@Composable
private fun DeveloperPage(onBack: () -> Unit, onCurrent: () -> Unit, onPrevious: () -> Unit) {
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = 24.dp + dockInset())) {
        SubPageBar("Для разработчиков", onBack)
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Логи") },
                supportingContent = { Text("Журнал клиента с момента запуска: можно скопировать или сохранить в .log") },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onCurrent),
            )
            ListItem(
                headlineContent = { Text("Логи последнего запуска") },
                supportingContent = {
                    Text(
                        if (app.ymusic.m3e.data.AppLog.previousFile() != null)
                            "Полный журнал прошлого запуска: открытие, работа, закрытие"
                        else "Пока пусто: прошлого запуска ещё не было",
                    )
                },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onPrevious),
            )
        }
    }
}

/**
 * Просмотр журнала. [previous] = false — текущий запуск, true — полный журнал прошлого запуска.
 * «Сохранить .log» открывает выбор места сохранения, «Скопировать лог» кладёт весь журнал в буфер обмена.
 */
@Composable
private fun LogViewerPage(title: String, previous: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(previous, reload) {
        text = withContext(Dispatchers.IO) {
            val f = if (previous) app.ymusic.m3e.data.AppLog.previousFile() else app.ymusic.m3e.data.AppLog.currentFile()
            app.ymusic.m3e.data.AppLog.read(f)
        }
    }
    val full = text.orEmpty()
    // На экране показываем хвост (последние строки), в файл и в буфер уходит весь журнал целиком.
    val lines = remember(full) { full.lineSequence().toList().takeLast(3000) }
    val saveName = remember(previous) {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        if (previous) "ymusic-last-run-$stamp.log" else "ymusic-$stamp.log"
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(full.toByteArray(Charsets.UTF_8)) } != null
        }.getOrDefault(false)
        if (ok) AppToast.success("Лог сохранён") else AppToast.error("Не удалось сохранить лог")
    }
    Column(Modifier.fillMaxSize()) {
        SubPageBar(title, onBack)
        Row(Modifier.fillMaxWidth().entrance(0).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { saver.launch(saveName) }, enabled = full.isNotEmpty(), modifier = Modifier.weight(1f)) {
                Text("Сохранить .log", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FilledTonalButton(
                onClick = {
                    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cb?.setPrimaryClip(android.content.ClipData.newPlainText("log", full))
                    AppToast.success("Лог скопирован")
                },
                enabled = full.isNotEmpty(), modifier = Modifier.weight(1f),
            ) { Text("Скопировать лог", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (!previous) {
            TextButton(onClick = { reload++ }, modifier = Modifier.entrance(1, offsetDp = 10).padding(horizontal = 8.dp)) { Text("Обновить") }
        }
        Spacer(Modifier.height(4.dp))
        // Загрузка → пустой журнал / текст журнала: плавная смена, текст появляется со сдвигом снизу.
        val phase = when {
            text == null -> 0
            full.isEmpty() -> 1
            else -> 2
        }
        AnimatedContent(
            targetState = phase,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(animMs(320), delayMillis = animMs(60)), 0f) +
                    slideInVertically(tween(animMs(380), delayMillis = animMs(60), easing = EmphasizedDecelerate)) { it / 14 }) togetherWith
                    fadeOut(tween(animMs(140)))
            },
            label = "logPhase",
        ) { ph ->
            when (ph) {
                0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                1 -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (previous) "Журнала прошлого запуска нет: приложение запускалось впервые после установки или обновления."
                        else "Журнал пока пуст.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                else -> {
                    // Журнал — один выделяемый текст внутри обычной прокрутки, а не сотни отдельных строк в ленте: при
                    // выделении протяжкой лента выгружала строки из-под пальца, и Compose падал (NoSuchElementException в
                    // SelectionManager). Цвета строк сохранены.
                    val errColor = MaterialTheme.colorScheme.error
                    val warnColor = MaterialTheme.colorScheme.tertiary
                    val markColor = MaterialTheme.colorScheme.primary
                    val baseColor = MaterialTheme.colorScheme.onSurface
                    val logText = remember(lines, errColor, warnColor, markColor, baseColor) {
                        androidx.compose.ui.text.buildAnnotatedString {
                            lines.forEachIndexed { idx, l ->
                                val c = when {
                                    " E/" in l || "АВАРИЙН" in l -> errColor
                                    " W/" in l -> warnColor
                                    "ОТКРЫТИЕ" in l || "ЗАКРЫТИЕ" in l || "ЗАПУСК" in l -> markColor
                                    else -> baseColor
                                }
                                withStyle(androidx.compose.ui.text.SpanStyle(color = c)) { append(l) }
                                if (idx != lines.lastIndex) append('\n')
                            }
                        }
                    }
                    androidx.compose.foundation.text.selection.SelectionContainer(Modifier.fillMaxSize()) {
                        Column(
                            Modifier.fillMaxSize()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + dockInset()),
                        ) {
                            Text(
                                logText,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Вибрация: включение/выключение виброотклика и выбор его силы. */
@Composable
private fun HapticsSettingsPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Вибрация", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = dockInset())) {
            SettingsSection("Виброотклик", R.drawable.ms_vibration, startExpanded = true) {
            ListItem(
                headlineContent = { Text("Виброотклик") },
                supportingContent = { Text("Лёгкая отдача при нажатиях, перемотке и переключениях") },
                leadingContent = { Icon(painterResource(R.drawable.ms_vibration), null) },
                trailingContent = {
                    ExpressiveSwitch(checked = Haptics.enabled, onCheckedChange = {
                        Haptics.updateEnabled(it)
                        if (it) Haptics.click()
                    })
                },
                modifier = Modifier.clickable {
                    Haptics.updateEnabled(!Haptics.enabled)
                    if (Haptics.enabled) Haptics.click()
                },
            )
            }
            SettingsSection("Сила", R.drawable.ms_vibration) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                HapticStrength.entries.forEachIndexed { i, st ->
                    SegmentedButton(
                        selected = Haptics.strength == st,
                        onClick = {
                            Haptics.updateStrength(st)
                            Haptics.click()
                        },
                        enabled = Haptics.enabled,
                        haptic = null,
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = HapticStrength.entries.size),
                        label = { Text(st.title, maxLines = 1) },
                    )
                }
            }
            ListItem(
                headlineContent = { Text("Сила нажатия") },
                supportingContent = { Text("Сильно нажал — вибрация сильнее, быстро тапнул — совсем слабая, обычное нажатие — средняя") },
                trailingContent = {
                    ExpressiveSwitch(checked = Haptics.pressureSensitive, onCheckedChange = { Haptics.updatePressureSensitive(it) })
                },
                modifier = Modifier.clickable { Haptics.updatePressureSensitive(!Haptics.pressureSensitive) },
            )
            }
            SettingsSection("Тики при удержании", R.drawable.ms_schedule) {
            ListItem(
                headlineContent = { Text("Тики при удержании") },
                supportingContent = { Text("Нарастающая вибрация, пока палец держит кнопку. Выключите — останется только отклик на нажатие") },
                trailingContent = {
                    ExpressiveSwitch(checked = Haptics.holdTicks, onCheckedChange = { Haptics.updateHoldTicks(it) })
                },
                modifier = Modifier.clickable { Haptics.updateHoldTicks(!Haptics.holdTicks) },
            )
            var delayMs by remember { mutableFloatStateOf(Haptics.holdDelayMs.toFloat()) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "${delayMs.roundToInt()} мс — столько нужно держать кнопку, чтобы пошли тики. Больше — обычные нажатия и тапы тиков не дают",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = delayMs,
                    onValueChange = { delayMs = (it / 50f).roundToInt() * 50f },
                    onValueChangeFinished = {
                        Haptics.updateHoldDelay(delayMs.roundToInt())
                        Haptics.click()
                    },
                    valueRange = Haptics.MIN_HOLD_DELAY_MS.toFloat()..Haptics.MAX_HOLD_DELAY_MS.toFloat(),
                    enabled = Haptics.enabled && Haptics.holdTicks,
                )
            }
            }
            SettingsSection("Проверка", R.drawable.ms_check) {
            FilledTonalButton(
                onClick = {},
                enabled = Haptics.enabled,
                haptic = HapticKind.Heavy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(56.dp),
            ) {
                Icon(painterResource(R.drawable.ms_vibration), null)
                Spacer(Modifier.width(8.dp))
                Text("Проверить")
            }
            Text(
                "Попробуйте кнопку «Проверить»: нажмите её слабо, обычно, сильно и быстрым тычком, а потом задержите палец — пойдут тики, каждый сильнее предыдущего. За «обычное» берётся ваше среднее " +
                    "нажатие — приложение подстраивается под вас и экран. На телефонах, где экран не передаёт давление, " +
                    "работает только скорость нажатия; в диалогах и шторках вибрация всегда средняя.\n\n" +
                    "Вибрация есть у кнопок, «Моей волны», плей/паузы, ползунка перемотки песни (не громкости), " +
                    "переключателей и вкладок. Двойное нажатие по «Профилю» открывает настройки с двойным импульсом. " +
                    "Выключите, чтобы отключить её везде.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Распознавание музыки: ключ сервиса, длина записи и плитка для шторки быстрых настроек. */
@Composable
private fun RecognitionSettingsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Распознавание музыки", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = 24.dp + dockInset())) {
            SettingsSection("Сервис", R.drawable.ms_mic, startExpanded = true) {
            Text(
                "Трек определяет Shazam, настройка не нужна: приложение записывает короткий фрагмент, считает из него отпечаток " +
                    "на телефоне и отправляет только отпечаток. Найденный трек открывается в поиске музыкального сервиса аккаунта (Яндекс или VK). " +
                    "Если Shazam не узнал песню, можно указать ключ AudD как запасной вариант (необязательно).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            TokenField(Modifier.padding(horizontal = 16.dp))
            }
            SettingsSection("Запись", R.drawable.ms_schedule) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                ComboBox(
                    selectedText = "Длина записи: ${RecognitionSettings.clipSeconds} с",
                    options = RecognitionSettings.clipOptions,
                    optionLabel = { "$it с" },
                    isSelected = { it == RecognitionSettings.clipSeconds },
                    onSelect = { RecognitionSettings.updateClipSeconds(it) },
                )
                Text(
                    "Чем длиннее запись, тем выше шанс узнать трек, но тем дольше ждать",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                )
            }
            }
            SettingsSection("Когда трек узнан", R.drawable.ms_music_note) {
            // Выдали ли «Поверх других приложений» — проверяем заново, когда вернулись из системных настроек.
            var permTick by remember { mutableIntStateOf(0) }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permTick++ }
            val canOpen = remember(permTick) { app.ymusic.m3e.recognition.RecognitionService.canOpenFromBackground(ctx) }
            Column(Modifier.padding(horizontal = 16.dp)) {
                ComboBox(
                    selectedText = "Действие: ${RecognitionSettings.onFound.title}",
                    options = FoundAction.entries.toList(),
                    optionLabel = { it.title },
                    isSelected = { it == RecognitionSettings.onFound },
                    onSelect = { RecognitionSettings.updateOnFound(it) },
                    optionSupporting = { it.hint },
                )
                Text(
                    "Это работает, когда окно распознавания закрыто или свёрнуто (например, слушали звук другого приложения). " +
                        "Пока окно на экране, трек показывается прямо в нём. " + RecognitionSettings.onFound.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                )
                if (RecognitionSettings.onFound != FoundAction.Notify) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (canOpen) "Доступ «Поверх других приложений» выдан: приложение откроется с найденным треком даже из фона"
                        else "Из фона Android открывает приложение, только если выдан доступ «Поверх других приложений». Без него придёт всплывающее уведомление",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    if (!canOpen) {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:" + ctx.packageName),
                                        ),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) { Text("Выдать доступ «Поверх других приложений»") }
                    }
                }
            }
            }
            SettingsSection("Шторка быстрых настроек", R.drawable.ms_add) {
            Text(
                "Плитка «Распознать музыку» открывает окно распознавания поверх любого экрана, без запуска приложения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (Build.VERSION.SDK_INT >= 33) {
                FilledTonalButton(
                    onClick = {
                        val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
                        sbm.requestAddTileService(
                            android.content.ComponentName(ctx, app.ymusic.m3e.recognition.RecognizeTileService::class.java),
                            "Распознать музыку",
                            android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ms_mic),
                            ctx.mainExecutor,
                        ) { }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ms_add), null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить плитку в шторку")
                }
            } else {
                Text(
                    "Потяните шторку вниз, нажмите значок карандаша (изменить) и перетащите плитку «Распознать музыку» в активные.",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            }
        }
    }
}

/**
 * Подраздел страницы настроек: плитка-заголовок, по нажатию раскрывается и показывает свои настройки.
 * Так длинные страницы («Внешний вид», «Плеер» и др.) собраны в группы, а не идут одним длинным списком.
 * [contentPadding] — горизонтальный отступ содержимого (у страниц, где строки сами идут от края до края, он 0).
 */
@Composable
internal fun SettingsSection(
    title: String,
    icon: Int,
    startExpanded: Boolean = false,
    contentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Раздел, выбранный в поиске по настройкам, открывается сам.
    var open by rememberSaveable(title) { mutableStateOf(startExpanded || SettingsHighlight.section == title) }
    val arrow by animateFloatAsState(if (open) 90f else 0f, label = "sectionArrow")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        Surface(
            onClick = { open = !open },
            shape = containerShape(20),
            color = if (open) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null)
                Spacer(Modifier.width(16.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(painterResource(R.drawable.ms_chevron_right), null, Modifier.rotate(arrow))
            }
        }
        AnimatedVisibility(
            visible = open,
            enter = androidx.compose.animation.expandVertically(tween(animMs(240))) + fadeIn(tween(animMs(200))),
            exit = androidx.compose.animation.shrinkVertically(tween(animMs(200))) + fadeOut(tween(animMs(120))),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), content = {
                Column(Modifier.fillMaxWidth().padding(horizontal = contentPadding), content = content)
            })
        }
    }
}

/** Подпись раздела внутри страницы настроек. */
@Composable
private fun SettingsLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** Строка раздела «Акценты»: часть интерфейса, пояснение и текущий цвет; нажатие открывает выбор цвета. */
@Composable
private fun AccentRoleRow(title: String, hint: String, color: Color, custom: Boolean, onReset: () -> Unit, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        leadingContent = {
            Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        },
        trailingContent = if (custom) ({
            IconButton(onClick = onReset) { Icon(painterResource(R.drawable.ms_close), contentDescription = "Сбросить цвет") }
        }) else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    )
}

/**
 * Ряд «Мне нравится / Поделиться» под кнопками воспроизведения страницы (альбом, исполнитель).
 * [liked] == null или [onLike] == null — кнопки лайка нет; [onShare] == null — кнопки «Поделиться» нет.
 */
@Composable
private fun PageActionsRow(
    liked: Boolean?,
    onLike: (() -> Unit)?,
    onShare: (() -> Unit)?,
    likeEnabled: Boolean = true,
) {
    val showLike = liked != null && onLike != null
    if (!showLike && onShare == null) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showLike && onLike != null) {
            val icon = if (liked == true) R.drawable.ms_favorite else R.drawable.ms_favorite_border
            val label = if (liked == true) "В коллекции" else "В коллекцию"
            if (liked == true) {
                Button(onClick = onLike, enabled = likeEnabled, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label, maxLines = 1)
                }
            } else {
                FilledTonalButton(onClick = onLike, enabled = likeEnabled, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(label, maxLines = 1)
                }
            }
        }
        if (onShare != null) {
            FilledTonalButton(onClick = onShare, modifier = Modifier.weight(1f).height(48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(painterResource(R.drawable.ms_share), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); FitText("Поделиться")
            }
        }
    }
}

/**
 * Доступ к плейлисту: у своего — переключатель «Публичный / приватный» (как в Яндекс Музыке) и «Поделиться»;
 * у чужого — только «Поделиться». Приватным плейлистом поделиться нельзя: кнопка неактивна.
 */
@Composable
private fun PlaylistShareRow(p: PlaylistInfo, editable: Boolean, onTogglePublic: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    // Чужой плейлист, который удалось открыть, — публичный (даже если сервер не прислал поле видимости).
    val shareable = if (editable || p.visibility != null) p else p.copy(visibility = "public")
    val link = ShareLinks.playlist(shareable)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (editable) {
            Surface(shape = containerShape(20), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            if (p.isPublic) "Публичный плейлист" else "Приватный плейлист",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (p.isPublic) "Любой, у кого есть ссылка, сможет открыть и послушать его"
                            else "Видите только вы — поделиться им нельзя",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ExpressiveSwitch(checked = p.isPublic, onCheckedChange = onTogglePublic)
                }
            }
        }
        FilledTonalButton(
            onClick = {
                if (link != null) ShareQrHost.open(p.title, "${p.title}\n$link", link)
            },
            enabled = link != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(painterResource(R.drawable.ms_share), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (link != null) "Поделиться плейлистом" else "Поделиться нельзя — плейлист приватный", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Окно «Изменить название, исполнителя и обложку» для файла с устройства. Сам файл не меняется — правка хранится в приложении
 * и действует во всех списках, альбомах и в плеере.
 */
@Composable
private fun EditLocalTrackDialog(vm: AppViewModel, t: Track, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val existing = remember(t.id) { LocalTrackEdits.get(t.id) }
    var title by remember(t.id) { mutableStateOf(t.title) }
    var artist by remember(t.id) { mutableStateOf(t.artists.takeIf { it != UNKNOWN_ARTIST }.orEmpty()) }
    var pickedCover by remember(t.id) { mutableStateOf<android.net.Uri?>(null) }
    var resetCover by remember(t.id) { mutableStateOf(false) }
    var saving by remember(t.id) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { pickedCover = uri; resetCover = false }
    }
    // Обложка из самого файла / альбома (то, что вернётся при сбросе).
    val original = remember(t.id) {
        if (t.localAlbumId > 0) android.content.ContentUris.withAppendedId(
            android.net.Uri.parse("content://media/external/audio/albumart"), t.localAlbumId,
        ).toString() else null
    }
    val preview: Any? = when {
        pickedCover != null -> pickedCover
        resetCover -> original
        else -> t.cover(400)
    }
    val canResetCover = pickedCover != null || (!resetCover && existing?.coverPath != null)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Изменить трек") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(
                    model = preview,
                    modifier = Modifier.size(140.dp).pressable(rounded(24)) { picker.launch("image/*") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { picker.launch("image/*") }) { Text("Выбрать обложку") }
                    if (canResetCover) {
                        TextButton(onClick = { pickedCover = null; resetCover = true }) { Text("Исходная") }
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Исполнитель") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Файл на устройстве не меняется: правка действует только в приложении. Пустое поле вернёт название или исполнителя из файла.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    val typed = artist.trim()
                    val artistToSave = when {
                        typed.isBlank() -> null
                        existing?.artist == null && typed == t.artists -> null
                        else -> typed
                    }
                    val typedTitle = title.trim()
                    val titleToSave = when {
                        typedTitle.isBlank() -> null
                        existing?.title == null && typedTitle == t.title -> null
                        else -> typedTitle
                    }
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { LocalTrackEdits.update(t.id, artistToSave, pickedCover, resetCover, titleToSave) }
                        saving = false
                        if (ok) { vm.reloadLocalAfterEdit(); onDismiss() }
                        else AppToast.error("Не удалось сохранить: проверьте, что выбрана картинка", long = true)
                    }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(
                        enabled = !saving,
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { LocalTrackEdits.reset(t.id) }
                                vm.reloadLocalAfterEdit()
                                onDismiss()
                            }
                        },
                    ) { Text("Сбросить всё") }
                }
                TextButton(enabled = !saving, onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun AppearancePage(onBack: () -> Unit, onOpenEditor: () -> Unit = {}) {
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Внешний вид", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberTopScrollState())
                .padding(top = 8.dp, bottom = 8.dp + dockInset()),
        ) {
            // Вход в редактор интерфейса: порядок и размеры блоков, кнопки дока и плеера, ссылки.
            Surface(
                onClick = onOpenEditor,
                shape = containerShape(20),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ms_edit), null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Редактор интерфейса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Переставляйте и масштабируйте блоки, кнопки дока и плеера; здесь же весь «Внешний вид» и открытие ссылок модалкой",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Icon(painterResource(R.drawable.ms_chevron_right), null)
                }
            }
            AppearanceContent()
        }
    }
}

/** Все настройки внешнего вида одним столбцом (без прокрутки): общий вид для страницы «Внешний вид» и для вкладки «Вид» редактора. */
@Composable
internal fun AppearanceContent() {
    val mode = ThemeSettings.mode
    val accent = ThemeSettings.accent
    val fontMode = ThemeSettings.fontMode
    val fontName = ThemeSettings.customFontName
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Окна выбора цвета: основной акцент и отдельные цвета приложения.
    var pickMain by remember { mutableStateOf(false) }
    var pickSlot by remember { mutableStateOf<AccentSlot?>(null) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { ThemeSettings.importFont(uri) }
            if (ok) ThemeSettings.updateFontMode(FontMode.File)
            else AppToast.error("Не удалось загрузить шрифт: нужен корректный .ttf или .otf", long = true)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        run {
            OptimizeSwitchCard()
            SettingsLabel("Оформление")
            SettingsSection("Тема", R.drawable.ms_palette, contentPadding = 16.dp) {
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { ThemeSettings.updateMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size),
                        label = { Text(m.title, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { ThemeSettings.updateAmoled(!ThemeSettings.amoled) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("AMOLED", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (mode == ThemeMode.Light) "Включится вместе с тёмной темой"
                        else "Чисто чёрный фон в тёмной теме — экономит заряд на OLED-экранах",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpressiveSwitch(checked = ThemeSettings.amoled, onCheckedChange = { ThemeSettings.updateAmoled(it) })
            }
            }
            SettingsSection("Цвета", R.drawable.ms_palette, contentPadding = 16.dp) {
            Spacer(Modifier.height(12.dp))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                FilterChip(
                    selected = accent == null,
                    onClick = { ThemeSettings.updateAccent(null) },
                    label = { Text("Цвета системы") },
                )
                Spacer(Modifier.height(12.dp))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentColors.forEach { (_, argb) ->
                    val selected = accent == argb
                    Box(
                        Modifier.size(52.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { ThemeSettings.updateAccent(argb) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedCheck(selected, tint = Color.White)
                    }
                }
                // Свои цвета: нажатие — выбрать, долгое нажатие — убрать из списка.
                ThemeSettings.customColors.forEach { argb ->
                    val selected = accent == argb
                    Box(
                        Modifier.size(52.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .combinedClickable(
                                onClick = { ThemeSettings.updateAccent(argb) },
                                onLongClick = { ThemeSettings.removeCustomColor(argb) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedCheck(selected, tint = Color.White)
                    }
                }
                // Колорпикер: любой цвет по своему вкусу.
                Box(
                    Modifier.size(52.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(onClickLabel = "Выбрать свой цвет") { pickMain = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ms_palette), contentDescription = "Свой цвет",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp),
                    )
                }
            }
            if (ThemeSettings.customColors.isNotEmpty()) {
                Text(
                    "Свои цвета: нажмите, чтобы выбрать, долгое нажатие — убрать.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            }
            SettingsSection("Акценты", R.drawable.ms_palette, contentPadding = 8.dp) {
                Text(
                    "Выберите, какими цветами раскрасить приложение. «Основной» — главный акцент, остальные его дополняют; " +
                        "то, что не задано, подбирается от основного цвета.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                )
                val cs = MaterialTheme.colorScheme
                AccentRoleRow(
                    title = "Основной", hint = "Кнопки, ползунки, переключатели, активные элементы",
                    color = accent?.let { Color(it) } ?: cs.primary, custom = accent != null,
                    onReset = { ThemeSettings.updateAccent(null) }, onClick = { pickMain = true },
                )
                AccentSlot.entries.forEach { slot ->
                    val own = ThemeSettings.slotColors[slot]
                    AccentRoleRow(
                        title = slot.title, hint = slot.hint,
                        color = own?.let { Color(it) } ?: when (slot) {
                            AccentSlot.Secondary -> cs.secondary
                            AccentSlot.Tertiary -> cs.tertiary
                            AccentSlot.Surface -> cs.surfaceContainerHigh
                        },
                        custom = own != null,
                        onReset = { ThemeSettings.updateSlot(slot, null) },
                        onClick = { pickSlot = slot },
                    )
                }
                if (ThemeSettings.slotColors.isNotEmpty()) {
                    TextButton(onClick = { ThemeSettings.resetSlots() }, modifier = Modifier.align(Alignment.End)) {
                        Text("Сбросить дополнительные цвета")
                    }
                }
            }
            if (pickMain) {
                ColorPickerDialog(
                    title = "Основной цвет",
                    initial = accent ?: MaterialTheme.colorScheme.primary.toArgb(),
                    onDismiss = { pickMain = false },
                    onPick = { c -> ThemeSettings.updateAccent(c); ThemeSettings.addCustomColor(c); pickMain = false },
                    onReset = { ThemeSettings.updateAccent(null); pickMain = false },
                )
            }
            pickSlot?.let { slot ->
                ColorPickerDialog(
                    title = slot.title,
                    initial = ThemeSettings.slotColors[slot] ?: when (slot) {
                        AccentSlot.Secondary -> MaterialTheme.colorScheme.secondary.toArgb()
                        AccentSlot.Tertiary -> MaterialTheme.colorScheme.tertiary.toArgb()
                        AccentSlot.Surface -> MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
                    },
                    onDismiss = { pickSlot = null },
                    onPick = { c -> ThemeSettings.updateSlot(slot, c); ThemeSettings.addCustomColor(c); pickSlot = null },
                    onReset = { ThemeSettings.updateSlot(slot, null); pickSlot = null },
                )
            }
            SettingsSection("Шрифт", R.drawable.ms_edit, contentPadding = 16.dp) {
            Spacer(Modifier.height(12.dp))
            ComboBox(
                selectedText = when (fontMode) {
                    FontMode.File -> fontName?.let { "Из файла: $it" } ?: FontMode.File.title
                    else -> fontMode.title
                },
                options = FontMode.entries.toList(),
                optionLabel = { it.title },
                isSelected = { it == fontMode },
                optionFont = {
                    when (it) {
                        FontMode.System -> androidx.compose.ui.text.font.FontFamily.Default
                        FontMode.GoogleSans -> GoogleSans
                        FontMode.File -> ThemeSettings.customFont
                    }
                },
                optionSupporting = { if (it == FontMode.File) (fontName ?: "Выбрать .ttf или .otf") else null },
                onSelect = { m ->
                    if (m == FontMode.File && ThemeSettings.customFont == null) {
                        // Файл ещё не выбран — сразу открываем выбор.
                        fontPicker.launch(arrayOf("*/*"))
                    } else {
                        ThemeSettings.updateFontMode(m)
                    }
                },
            )
            if (fontMode == FontMode.File) {
                Spacer(Modifier.height(12.dp))
                ListItem(
                    headlineContent = { Text(fontName ?: "Файл не выбран", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("Поддерживаются .ttf и .otf. Нажмите, чтобы выбрать другой") },
                    leadingContent = { Icon(painterResource(R.drawable.ms_folder), null) },
                    modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { fontPicker.launch(arrayOf("*/*")) },
                )
            }
            }
            SettingsSection("Иконка приложения", R.drawable.ms_smartphone, contentPadding = 16.dp) {
            AppIconSection()
            }
            SettingsSection("Формы", R.drawable.ms_open_in_full, contentPadding = 16.dp) {
            Text("Форма элементов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Как «Форма значков» в Android: выбранная форма применяется к обложкам, карточкам, плиткам, кнопкам и панелям",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconShape.entries.forEach { sh ->
                    val selected = ThemeSettings.iconShape == sh
                    Column(
                        // Фиксированная форма области нажатия: иначе она менялась бы вместе с выбранной формой
                        // и срезала превью остальных.
                        Modifier.width(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .clickable { ThemeSettings.updateIconShape(sh) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(68.dp)
                                .clip(sh.shape(28.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!selected) Icon(painterResource(R.drawable.ms_music_note), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            AnimatedCheck(selected, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(
                            sh.title, Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Форма аватарок исполнителей", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Применяется к фото исполнителей: на странице исполнителя, в поиске и в списке авторов альбома",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarShape.entries.forEach { sh ->
                    val selected = ThemeSettings.avatarShape == sh
                    Column(
                        Modifier.width(96.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .clickable { ThemeSettings.updateAvatarShape(sh) }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(68.dp)
                                .clip(sh.asShape())
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!selected) Icon(painterResource(R.drawable.ms_person), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            AnimatedCheck(selected, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Text(
                            sh.title, Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 2,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            }
            SettingsSection("Матовое стекло (размытая прозрачность)", R.drawable.ms_palette, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            GlassSettingsSection()
            }
            SettingsSection("Размер интерфейса", R.drawable.ms_open_in_full, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            UiScaleSetting()
            }
            SettingsSection("Свечение", R.drawable.ms_music_note, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            GlowSwitchRow(
                "Свечение в плеере",
                "Обложка в полном плеере и мини-плеер мягко светятся, пока играет музыка. На паузе свечения нет",
                ThemeSettings.glowPlayer,
            ) { ThemeSettings.updateGlowPlayer(it) }
            if (ThemeSettings.glowPlayer) {
                Text(
                    "Цвет свечения плеера",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ThemeSettings.glowFromCover,
                        onClick = { ThemeSettings.updateGlowFromCover(true) },
                        label = { Text("По обложке") },
                    )
                    FilterChip(
                        selected = !ThemeSettings.glowFromCover,
                        onClick = { ThemeSettings.updateGlowFromCover(false) },
                        label = { Text("По цвету системы") },
                    )
                }
            }
            GlowSwitchRow(
                if (ActiveProvider.isVk) "Свечение VK Микса" else if (ActiveProvider.isSc) "Свечение волны" else "Свечение «Моей волны»",
                if (ActiveProvider.isVk) "Весь верхний блок VK Микса светится, пока играет микс. Выключите, чтобы блок был без свечения" else if (ActiveProvider.isSc) "Весь верхний блок волны светится, пока играет волна. Выключите, чтобы блок был без свечения" else "Весь верхний блок «Моей волны» светится, пока играет волна. Выключите, чтобы блок был без свечения",
                ThemeSettings.glowWave,
            ) { ThemeSettings.updateGlowWave(it) }
            GlowSwitchRow(
                "Свечение плейлистов",
                "Светится карточка плейлиста или «Любимого», который сейчас играет",
                ThemeSettings.glowPlaylists,
            ) { ThemeSettings.updateGlowPlaylists(it) }
            }
            AnimationSection()
            SettingsLabel("Главная, поиск и навигация")
            InterfaceSection()
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * «Оптимизация»: один переключатель на все эффекты. Включено — свечение, размытие дока, бегущие волны, переходы
 * и пружинки выключены; выключено — всё работает по отдельным настройкам, как раньше.
 */
@Composable
private fun OptimizeSwitchCard() {
    val on = ThemeSettings.optimize
    Surface(
        onClick = { ThemeSettings.updateOptimize(!on) },
        shape = containerShape(20),
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ms_refresh), null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Оптимизация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Выключает все визуальные эффекты и анимации: свечение, размытие дока, бегущие волны, переходы. " +
                        "Выключите переключатель — всё вернётся, как было настроено",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExpressiveSwitch(checked = on, onCheckedChange = { ThemeSettings.updateOptimize(it) })
        }
    }
}

/** Скорость анимации: ползунок применяется, когда палец отпущен. Влияет на переходы между экранами и жест «назад». */
@Composable
private fun AnimationSection() {
    SettingsSection("Анимации", R.drawable.ms_refresh, contentPadding = 16.dp) {
        var draft by remember { mutableFloatStateOf(ThemeSettings.animSpeed) }
        Text(
            "Скорость анимации: ${String.format(java.util.Locale.US, "%.2f", draft).trimEnd('0').trimEnd('.')}×. " +
                "Влияет на переходы между экранами, открытие страниц и жест «назад». Больше — быстрее, меньше — медленнее",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = draft,
            onValueChange = { draft = ((it * 20f).roundToInt() / 20f) },
            onValueChangeFinished = { ThemeSettings.updateAnimSpeed(draft) },
            valueRange = ThemeSettings.MIN_ANIM_SPEED..ThemeSettings.MAX_ANIM_SPEED,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Медленнее", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Быстрее", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (draft != 1f) {
            TextButton(onClick = { draft = 1f; ThemeSettings.updateAnimSpeed(1f) }) { Text("Сбросить до 1×") }
        }
        Spacer(Modifier.height(8.dp))
        GlowSwitchRow(
            "Кружок → галочка",
            "Отметка выбора (цвет, форма, активный аккаунт, «уже в плейлисте») сначала появляется кружком, который стягивается в галочку",
            ThemeSettings.animCheck,
        ) { ThemeSettings.updateAnimCheck(it) }
        GlowSwitchRow(
            "Загрузка трека: кружок и три точки",
            "Пока трек грузится, кнопка плеера превращается в кружок, затем в три пульсирующие точки, а потом собирается обратно",
            ThemeSettings.animLoading,
        ) { ThemeSettings.updateAnimLoading(it) }
        GlowSwitchRow(
            "Пропадание элементов",
            "Убранный из списка трек, альбом или плейлист плавно гаснет, а остальные плавно сдвигаются. Выключите — исчезают сразу",
            ThemeSettings.animRemove,
        ) { ThemeSettings.updateAnimRemove(it) }
        GlowSwitchRow(
            "Смена страниц: альбом ↔ исполнитель",
            "Открытая страница исполнителя, альбома или плейлиста плавно заменяет прежнюю, а не наезжает поверх неё. Выключите — как раньше",
            ThemeSettings.animPageSwap,
        ) { ThemeSettings.updateAnimPageSwap(it) }
        GlowSwitchRow(
            "Проскролл между вкладками",
            "Переход на дальнюю вкладку (например, «Главная» → «Поиск» и обратно) пролистывает все страницы по пути. Выключите — экран плавно гаснет и сменяется",
            ThemeSettings.animTabScroll,
        ) { ThemeSettings.updateAnimTabScroll(it) }
        if (ThemeSettings.animTabScroll) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Направление смены вкладок", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "«По положению панели»: у нижней панели страницы листаются влево и вправо, у боковой (планшет) — вверх и вниз. Направление зависит от того, дальше или ближе нужная вкладка",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf(0 to "Влево · вправо", 1 to "Вверх · вниз", 2 to "По положению панели").forEach { (v, title) ->
                        FilterChip(
                            selected = ThemeSettings.tabAnimStyle == v,
                            onClick = { ThemeSettings.updateTabAnimStyle(v) },
                            label = { Text(title) },
                        )
                    }
                }
            }
        }
        GlowSwitchRow(
            "Motion blur",
            if (android.os.Build.VERSION.SDK_INT >= 31) "Размытие по направлению движения: при листании вкладок, выезде и сворачивании плеера, жесте «назад» на страницах, свайпе мини-плеера и обложки. Выключите — без размытия"
            else "Работает на Android 12 и новее — на этом устройстве размытие недоступно",
            ThemeSettings.motionBlur,
        ) { ThemeSettings.updateMotionBlur(it) }
        if (ThemeSettings.motionBlur && android.os.Build.VERSION.SDK_INT >= 31) {
            var mb by remember { mutableFloatStateOf(ThemeSettings.motionBlurStrength) }
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Сила motion blur", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = mb,
                    onValueChange = { mb = it },
                    onValueChangeFinished = { ThemeSettings.updateMotionBlurStrength(mb) },
                    valueRange = 0f..1f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Слабее", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Сильнее", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        GlowSwitchRow(
            "Окно распознавания музыки",
            "Плавная смена «слушаю» → «ищу» → «найдено». Выключите — состояния меняются сразу",
            ThemeSettings.animRecognition,
        ) { ThemeSettings.updateAnimRecognition(it) }
    }
}

/** Строка настроек «название + пояснение + переключатель» для пункта «Свечение». */
@Composable
internal fun GlowSwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ExpressiveSwitch(checked = checked, onCheckedChange = onChange)
    }
}




/** Выбор стиля полосы прогресса (общий для полного плеера и мини-плеера). Живёт в «Плеер». */
@Composable
private fun SeekStyleSection() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Один стиль для полного плеера и мини-плеера. Круговой вариант рисуется вокруг кнопки play/pause, по кольцу можно перематывать",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SeekBarStyle.entries.forEach { st ->
            val selected = ThemeSettings.seekStyle == st
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(containerShape(20))
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { ThemeSettings.updateSeekStyle(st) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    if (!st.circular) {
                        Spacer(Modifier.height(6.dp))
                        SeekStylePreview(st)
                    }
                }
                if (st.circular) {
                    Spacer(Modifier.width(12.dp))
                    SeekStylePreview(st)
                }
            }
        }
    }
}

/** Главный экран, поиск, док-панель, навигация и планшеты. Часть страницы «Внешний вид». */
@Composable
private fun InterfaceSection() {
            SettingsSection("Главный экран", R.drawable.ms_home, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            // Для управления одной рукой на больших телефонах; на планшете приветствия нет.
            if (!LocalWide.current) GlowSwitchRow(
                "Приветствие сверху",
                "Вверху главного экрана: значок, «Доброе утро / день / вечер / Доброй ночи» по времени телефона и ваш ник (нажмите на него, чтобы сменить). При прокрутке скрывается",
                GreetingSettings.enabled,
            ) { GreetingSettings.updateEnabled(it) }
            GlowSwitchRow(
                "Поиск на главной",
                if (ActiveProvider.isVk) "Кнопка поиска на главной: нажатие открывает поиск с клавиатурой. Её можно показать плиткой или значком в углу блока VK Микса" else if (ActiveProvider.isSc) "Кнопка поиска на главной: нажатие открывает поиск с клавиатурой. Её можно показать плиткой или значком в углу блока волны" else "Кнопка поиска на главной: нажатие открывает поиск с клавиатурой. Её можно показать плиткой или значком в углу блока «Моей волны»",
                NavSettings.homeSearch,
            ) { NavSettings.updateHomeSearch(it) }
            if (NavSettings.homeSearch) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !NavSettings.homeSearchCorner,
                        onClick = { NavSettings.updateHomeSearchCorner(false) },
                        label = { Text("Плиткой") },
                    )
                    FilterChip(
                        selected = NavSettings.homeSearchCorner,
                        onClick = { NavSettings.updateHomeSearchCorner(true) },
                        label = { Text("Значок в углу") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable { ThemeSettings.updatePinWave(!ThemeSettings.pinWave) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(if (ActiveProvider.isVk) "Закреплённый VK Микс" else if (ActiveProvider.isSc) "Закреплённая волна" else "Закреплённая «Моя волна»", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (ActiveProvider.isVk) "Когда большой блок VK Микса не виден на экране (прокручен или стоит в самом низу), компактная кнопка остаётся сверху. Выключите, чтобы блок просто прокручивался вместе со списком" else if (ActiveProvider.isSc) "Когда большой блок волны не виден на экране (прокручен или стоит в самом низу), компактная кнопка волны остаётся сверху. Выключите, чтобы блок просто прокручивался вместе со списком" else "Когда большой блок «Моей волны» не виден на экране (прокручен или стоит в самом низу), компактная кнопка волны остаётся сверху. Выключите, чтобы блок просто прокручивался вместе со списком",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpressiveSwitch(checked = ThemeSettings.pinWave, onCheckedChange = { ThemeSettings.updatePinWave(it) })
            }
            GlowSwitchRow(
                "Вода реагирует на наклон",
                if (ActiveProvider.isVk) "Волна на кнопке VK Микса стекает в нижнюю сторону и плещется, когда вы наклоняете или крутите телефон (акселерометр)" else if (ActiveProvider.isSc) "Волна на кнопке стекает в нижнюю сторону и плещется, когда вы наклоняете или крутите телефон (акселерометр)" else "Волна на кнопке «Моя волна» стекает в нижнюю сторону и плещется, когда вы наклоняете или крутите телефон (акселерометр)",
                ThemeSettings.waveTilt,
            ) { ThemeSettings.updateWaveTilt(it) }
            }
            SettingsSection("Поиск", R.drawable.ms_search, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable { ThemeSettings.updateSearchAutoKeyboard(!ThemeSettings.searchAutoKeyboard) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Клавиатура при входе в поиск", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Сразу показывать клавиатуру, когда открывается вкладка «Поиск». По умолчанию выключено",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpressiveSwitch(checked = ThemeSettings.searchAutoKeyboard, onCheckedChange = { ThemeSettings.updateSearchAutoKeyboard(it) })
            }
            GlowSwitchRow(
                "Живой поиск",
                "Результаты появляются сразу, пока вы печатаете. Выключите, чтобы искать только по кнопке поиска на клавиатуре",
                NavSettings.liveSearch,
            ) { NavSettings.updateLiveSearch(it) }
            GlowSwitchRow(
                "История поиска",
                "Под пустым полем поиска показываются последние запросы. Выключите, чтобы запросы не запоминались и не показывались",
                SearchHistory.enabled,
            ) { SearchHistory.updateEnabled(it) }
            if (SearchHistory.items.isNotEmpty()) {
                TextButton(onClick = { SearchHistory.clear() }) { Text("Очистить историю поиска (${SearchHistory.items.size})") }
            }
            }
            SettingsSection("Страница артиста", R.drawable.ms_person, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            GlowSwitchRow(
                "Информация об артисте",
                "Раскрываемая карточка «Об артисте» под именем на странице исполнителя: показывает описание или биографию, если сервис их отдаёт. Нажмите на карточку, чтобы прочитать целиком",
                ThemeSettings.artistBio,
            ) { ThemeSettings.updateArtistBio(it) }
            }
            SettingsSection("Док-панель", R.drawable.ms_library_music, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable { ThemeSettings.updateDockIsland(!ThemeSettings.dockIsland) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Островок", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Плавающая капсула вместо панели на всю ширину; мини-плеер тоже становится островком. Выключите, чтобы вернуть обычный вид",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpressiveSwitch(checked = ThemeSettings.dockIsland, onCheckedChange = { ThemeSettings.updateDockIsland(it) })
            }
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable { ThemeSettings.updateDockAutoHide(!ThemeSettings.dockAutoHide) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Автоскрытие", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Док-панель прячется при прокрутке вниз и возвращается при прокрутке вверх",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExpressiveSwitch(checked = ThemeSettings.dockAutoHide, onCheckedChange = { ThemeSettings.updateDockAutoHide(it) })
            }
            Text(
                "Размытие под док-панелью и матовое стекло островка — в разделе «Матовое стекло».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text("Подписи вкладок", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Где показывать названия вкладок: под значком, рядом со значком или скрыть. По умолчанию — рядом. " +
                    "В островке «рядом» подписывает все вкладки, пока их не больше трёх, иначе — только выбранную",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DockLabels.values().forEach { m ->
                    FilterChip(
                        selected = NavSettings.dockLabels == m,
                        onClick = { NavSettings.updateDockLabels(m) },
                        label = { Text(m.title) },
                    )
                }
            }
            }
            SettingsSection("Навигация", R.drawable.ms_smartphone, contentPadding = 16.dp) {
            Spacer(Modifier.height(8.dp))
            GlowSwitchRow(
                "Листание экранов",
                "Свайп влево и вправо переключает экраны в порядке док-панели. Выключите, чтобы переключать только нажатием на вкладки",
                NavSettings.swipeTabs,
            ) { NavSettings.updateSwipeTabs(it) }
            if (NavSettings.swipeTabs) {
                var sens by remember { mutableFloatStateOf(NavSettings.swipeSensitivity) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Чувствительность листания", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            sens < 0.35f -> "Низкая — нужен длинный и уверенный свайп"
                            sens > 0.65f -> "Высокая — достаточно короткого движения пальцем"
                            else -> "Обычная"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = sens,
                        onValueChange = { sens = it; NavSettings.updateSwipeSensitivity(it) },
                        valueRange = 0f..1f,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            NavTabsEditor()
            GlowSwitchRow(
                "Подсказки кнопок",
                "Зажмите маленькую кнопку без подписи — появится её название (особенно полезно в плеере), а действие не сработает",
                NavSettings.hints,
            ) { NavSettings.updateHints(it) }
            }
            SettingsSection("Планшеты", R.drawable.ms_smartphone, contentPadding = 16.dp) {
                Text(
                    "Широкая раскладка: вместо нижней панели — боковая, слева кнопки и профиль, справа списки треков и настройки, мини-плеер внизу списка. " +
                        "«Авто» включает её, когда окно шире 600 dp (планшеты, а также телефон в альбомной ориентации); " +
                        "«Только планшеты» оставляет на телефоне обычный вид даже в альбомной ориентации",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    TabletMode.entries.forEach { m ->
                        FilterChip(
                            selected = TabletSettings.mode == m,
                            onClick = { TabletSettings.update(m) },
                            label = { Text(m.title) },
                        )
                    }
                }
                GlowSwitchRow(
                    "В вертикальном положении",
                    "Планшетная раскладка работает и в портретной ориентации. Выключите — в портретной будет обычный телефонный вид",
                    TabletSettings.inPortrait,
                ) { TabletSettings.updateInPortrait(it) }
                Text("Положение островка", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    IslandPos.entries.forEach { p ->
                        FilterChip(selected = TabletSettings.islandPos == p, onClick = { TabletSettings.updateIslandPos(p) }, label = { Text(p.title) })
                    }
                }
                Text(
                    "Навигация на планшете: «Островок / док» — при включённом островке это капсула только со значками поверх контента (положение — ниже; «Снизу» — обычный островок с подписями), " +
                        "«Боковая панель» — кнопки слева",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    FilterChip(selected = !TabletSettings.sideRail, onClick = { TabletSettings.updateSideRail(false) }, label = { Text("Островок / док") })
                    FilterChip(selected = TabletSettings.sideRail, onClick = { TabletSettings.updateSideRail(true) }, label = { Text("Боковая панель") })
                }
            }
}

/**
 * Какие экраны есть в панели и в каком порядке. Порядок сверху вниз — слева направо в доке. Экран можно убрать
 * и вернуть; «Профиль» убрать нельзя, потому что через него открываются настройки.
 */
@Composable
private fun NavTabsEditor() {
    val tabs = NavSettings.tabs
    val hidden = Tab.entries.filter { it !in tabs }
    Text("Экраны в панели", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Порядок сверху вниз — слева направо в панели. «Профиль» убрать нельзя: через него открываются настройки",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    tabs.forEachIndexed { i, t ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp)
                .clip(containerShape(20))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(t.icon), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(t.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { NavSettings.move(t, -1) }, enabled = i > 0) {
                Icon(painterResource(R.drawable.ms_arrow_upward), contentDescription = "Сдвинуть выше (левее в панели)")
            }
            IconButton(onClick = { NavSettings.move(t, 1) }, enabled = i < tabs.size - 1) {
                Icon(painterResource(R.drawable.ms_arrow_upward), contentDescription = "Сдвинуть ниже (правее в панели)", modifier = Modifier.rotate(180f))
            }
            IconButton(onClick = { NavSettings.setVisible(t, false) }, enabled = t.removable && tabs.size > 1) {
                Icon(painterResource(R.drawable.ms_close), contentDescription = "Убрать из панели")
            }
        }
    }
    if (hidden.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Скрыты", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        hidden.forEach { t ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(containerShape(20))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(t.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(
                    t.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { NavSettings.setVisible(t, true) }) { Text("Добавить") }
            }
        }
    }
    if (tabs != Tab.entries.toList()) {
        TextButton(onClick = { NavSettings.resetTabs() }, modifier = Modifier.padding(top = 4.dp)) {
            Text("Вернуть порядок по умолчанию")
        }
    }
}

private fun formatSize(b: Long): String = when {
    b < 1024 -> "$b Б"
    b < 1024 * 1024 -> "${b / 1024} КБ"
    b < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f МБ", b / 1024f / 1024f)
    else -> String.format(java.util.Locale.US, "%.2f ГБ", b / 1024f / 1024f / 1024f)
}

private fun songsWord(n: Int): String = plural(n, "песня", "песни", "песен")

/** Скачивание песен: папка, размер, список скачанного с удалением. */
@Composable
private fun DownloadsPage(vm: AppViewModel, onBack: () -> Unit) {
    val dl by vm.downloads.collectAsStateWithLifecycle()
    var confirmAll by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setDownloadDir(uri)
    }
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Скачивание песен", onBack)
        LazyColumn(Modifier.fillMaxSize(), state = rememberTopListState(), contentPadding = PaddingValues(bottom = 24.dp + dockInset())) {
            item {
                SettingsSection("Сеть", R.drawable.ms_cloud_download, startExpanded = true) {
                Text(
                    "Относится к «Любимому», плейлистам и отдельным трекам",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    DownloadNetwork.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = dl.network == m,
                            onClick = { vm.setDownloadNetwork(m) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = DownloadNetwork.entries.size),
                            label = {
                                Text(
                                    when (m) { DownloadNetwork.Wifi -> "Wi-Fi"; DownloadNetwork.Mobile -> "Мобильная"; DownloadNetwork.Any -> "Обе" },
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                }
                SettingsSection("Куда сохранять по умолчанию", R.drawable.ms_download) {
                Text(
                    "Основной способ: для отдельных треков из меню «⋮» и для «Любимого», плейлистов и альбомов, если не спрашивать каждый раз.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
                DownloadMode.entries.forEach { m ->
                    ListItem(
                        headlineContent = { Text(m.title) },
                        supportingContent = { Text(m.hint) },
                        leadingContent = { RadioButton(selected = dl.mode == m, onClick = { vm.setDownloadMode(m) }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { vm.setDownloadMode(m) },
                    )
                }
                ListItem(
                    headlineContent = { Text("Спрашивать, куда сохранять") },
                    supportingContent = { Text("Окно выбора при скачивании «Любимого», плейлиста или альбома") },
                    leadingContent = { Icon(painterResource(R.drawable.ms_cloud_download), null) },
                    trailingContent = { ExpressiveSwitch(checked = dl.askMode, onCheckedChange = { vm.setAskDownloadMode(it) }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { vm.setAskDownloadMode(!dl.askMode) },
                )
                Spacer(Modifier.height(8.dp))
                }
                SettingsSection("Папка скачивания", R.drawable.ms_folder) {
                ListItem(
                    headlineContent = { Text("Папка скачивания") },
                    supportingContent = { Text(dl.dirLabel + " (для режимов с файлами)", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(painterResource(R.drawable.ms_folder), null) },
                    modifier = Modifier.clickable { picker.launch(null) },
                )
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { picker.launch(null) }) { Text("Изменить папку") }
                    if (!dl.isDefault) TextButton(onClick = { vm.resetDownloadDir() }) { Text("По умолчанию") }
                }
                }
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = {
                        Text(
                            if (dl.items.isEmpty()) "Скачанных песен нет"
                            else "${dl.items.size} ${songsWord(dl.items.size)} · ${formatSize(dl.totalBytes)}",
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    trailingContent = {
                        if (dl.items.isNotEmpty()) {
                            TextButton(onClick = { confirmAll = true }) {
                                Text("Удалить все", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                )
            }
            if (dl.items.isNotEmpty()) {
                item {
                    val all = dl.items.sortedByDescending { it.ts }.map { it.toTrack() }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { vm.play(all, 0) }, modifier = Modifier.weight(1f).height(48.dp)) { FitText("Слушать скачанное") }
                        FilledTonalButton(onClick = { vm.play(all, 0, shuffle = true) }, modifier = Modifier.weight(1f).height(48.dp)) { FitText("Вперемешку") }
                    }
                }
            }
            items(dl.items.sortedByDescending { it.ts }, key = { it.id }) { d ->
                ListItem(
                    headlineContent = { Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            listOfNotNull(d.artists.ifBlank { null }, d.album).joinToString(" · ") + " · " + formatSize(d.size) +
                                " · " + listOfNotNull(if (d.inFiles) "файл" else null, if (d.inApp) "в приложении" else null).joinToString(" + "),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { vm.deleteDownload(d.id) }) {
                            Icon(painterResource(R.drawable.ms_delete), contentDescription = "Удалить")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        val all = dl.items.sortedByDescending { it.ts }.map { it.toTrack() }
                        vm.play(all, all.indexOfFirst { it.id == d.id }.coerceAtLeast(0))
                    },
                )
            }
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Удалить все скачанные песни?") },
            text = { Text("Файлы будут удалены с устройства (${dl.items.size} шт., ${formatSize(dl.totalBytes)}).") },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; vm.deleteAllDownloads() }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Отмена") } },
        )
    }
}

// ---------- Плагины ----------

/** Иконка плагина из его .exm; если иконки нет — значок «пазл». */
@Composable
private fun PluginIcon(icon: ByteArray?, size: Dp) {
    val bmp = remember(icon) {
        icon?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()?.asImageBitmap() }
    }
    Surface(shape = rounded(14), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(size)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painterResource(R.drawable.ms_extension), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(size * 0.5f),
                )
            }
        }
    }
}

private fun pluginNum(d: Double): String =
    if (d == Math.rint(d) && Math.abs(d) < 1e12) d.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", d).trimEnd('0').trimEnd('.')

/**
 * Настройки плагина в нижней модалке (открывается кнопкой с иконкой настроек в карточке плагина). Поля описаны в манифесте
 * плагина, а рисует их приложение (код плагина в интерфейс не попадает). Значения сохраняются сразу; скрипт узнаёт о них
 * через событие setting_changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginSettingsSheet(p: InstalledPlugin, entryId: String? = null, closing: Boolean = false, onDismiss: () -> Unit) {
    val m = p.manifest
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Lua попросил закрыть шторку (ui.close_page): сначала она плавно уезжает вниз, потом окно снимается.
    LaunchedEffect(closing) { if (closing) { runCatching { sheetState.hide() }; onDismiss() } }
    // Страницы, которые плагин создал из Lua, рисует сам Lua (render): при открытии просим свежую отрисовку.
    LaunchedEffect(p, entryId) { PluginSystem.requestPage(p, entryId) }
    val entries = p.uiEntries.filter { !it.transient }
    val shown = if (entryId == null) entries else p.uiEntries.filter { it.id == entryId }
    val declared = if (entryId == null) m.settings else emptyList()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberTopScrollState()).navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PluginIcon(p.iconBytes, 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (entryId != null) shown.firstOrNull()?.title ?: m.name else m.name,
                        style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    val sub = if (entryId != null) shown.firstOrNull()?.summary?.ifBlank { null } ?: "Плагин · ${m.name}"
                    else {
                        val n = declared.count { it.hasValue || it.type == "button" }
                        if (n > 0) "Настройки · $n " + plural(n, "параметр", "параметра", "параметров") else "Настройки"
                    }
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            declared.forEach { st -> if (PluginSystem.isSettingVisible(p, st)) PluginSettingField(p, st) }
            shown.forEach { e ->
                if (entryId == null) {
                    Text(
                        e.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                    )
                }
                key(e.id) { PluginDynPage(p, e.id) }
            }
            if (declared.any { it.hasValue }) {
                TextButton(onClick = { PluginSystem.resetSettings(p) }, modifier = Modifier.align(Alignment.End)) {
                    Text("Сбросить настройки")
                }
            }
        }
    }
}

/** Поле настроек из манифеста плагина. */
@Composable
private fun PluginSettingField(p: InstalledPlugin, st: app.ymusic.m3e.data.PluginSetting) {
    // «Отключить песочницу»: включить можно только после предупреждения; выключить — сразу.
    var warn by remember { mutableStateOf(false) }
    PluginSettingField(
        st = st, cur = p.settingValues[st.key], live = PluginSystem.enabled && p.enabled,
        onSet = { v ->
            if (st.key == app.ymusic.m3e.data.PluginHooks.UNSAFE_KEY && v == true) {
                warn = true
            } else {
                PluginSystem.setSetting(p, st.key, v)
            }
        },
        onPress = { PluginSystem.pressSettingButton(p, st.key) },
    )
    if (warn) {
        AlertDialog(
            onDismissRequest = { warn = false },
            title = { Text("ОПАСНО!", color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    "Вы отключаете песочницу для плагина «${p.manifest.name}». Его скрипт сможет выполнять Java-код с правами " +
                        "приложения: читать и менять данные и токены ExMusic, ходить в сеть, удалять файлы и ломать приложение. " +
                        "Ограничения песочницы для него действовать не будут.\n\nВключайте только если полностью доверяете автору плагина.",
                )
            },
            confirmButton = {
                TextButton(onClick = { warn = false; PluginSystem.setSetting(p, st.key, true) }) {
                    Text("Всё равно отключить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { warn = false }) { Text("Отмена") } },
        )
    }
}

/**
 * Страница настроек, которую плагин создал из Lua (ui.settings_page): рисуются компоненты из последнего render(),
 * действия пользователя уходят обратно в Lua-колбэки.
 */
@Composable
private fun PluginDynPage(p: InstalledPlugin, entryId: String) {
    val nodes = p.uiPages[entryId]
    val live = PluginSystem.enabled && p.enabled
    if (nodes == null) {
        Text(
            if (live) (p.luaStatus?.takeIf { it.startsWith("Ошибка") || it.startsWith("Сбой") } ?: "Загрузка…")
            else "Включите плагин и систему плагинов",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        return
    }
    nodes.forEach { n ->
        key(n.st.key) {
            PluginSettingField(
                st = n.st, cur = n.value, live = live,
                onSet = { PluginSystem.uiEvent(p, entryId, n.index, it) },
                onPress = { PluginSystem.uiEvent(p, entryId, n.index, null) },
            )
        }
    }
}

/** Общая отрисовка одного поля: [cur] — текущее значение, [onSet] — пользователь изменил, [onPress] — нажал кнопку. */
@Composable
private fun PluginSettingField(
    st: app.ymusic.m3e.data.PluginSetting,
    cur: Any?,
    live: Boolean,
    onSet: (Any?) -> Unit,
    onPress: () -> Unit,
) {
    if (st.type == "section") {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp)) {
            Text(st.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (st.summary.isNotBlank()) {
                Text(st.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    if (st.type == "info") {
        Column(
            Modifier.fillMaxWidth().clip(containerShape(20)).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)).padding(16.dp),
        ) {
            Text(st.title, style = MaterialTheme.typography.titleSmall)
            if (st.summary.isNotBlank()) {
                Text(st.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(containerShape(20)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
    ) {
        @Composable fun header() {
            Text(st.title, style = MaterialTheme.typography.titleMedium)
            if (st.summary.isNotBlank()) {
                Text(st.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (st.type) {
            "switch" -> Row(
                Modifier.fillMaxWidth().clickable { onSet(!(cur as? Boolean ?: false)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) { header() }
                Spacer(Modifier.width(12.dp))
                ExpressiveSwitch(checked = cur as? Boolean ?: false, onCheckedChange = { onSet(it) })
            }
            "slider" -> {
                var draft by remember(st.key, cur) { mutableFloatStateOf(((cur as? Double) ?: (st.default as? Double) ?: st.min).toFloat()) }
                header()
                Text(
                    pluginNum(draft.toDouble()),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Slider(
                    value = draft,
                    onValueChange = { draft = PluginSystem.snapSetting(st, it.toDouble()).toFloat() },
                    onValueChangeFinished = { onSet(draft.toDouble()) },
                    valueRange = st.min.toFloat()..st.max.toFloat(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pluginNum(st.min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pluginNum(st.max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            "number" -> {
                var text by remember(st.key) { mutableStateOf(pluginNum((cur as? Double) ?: (st.default as? Double) ?: st.min)) }
                var focused by remember(st.key) { mutableStateOf(false) }
                LaunchedEffect(cur) {
                    val v = cur as? Double
                    if (!focused && v != null && text.replace(',', '.').toDoubleOrNull() != v) text = pluginNum(v)
                }
                val parsed = text.replace(',', '.').toDoubleOrNull()
                val ok = parsed != null && parsed >= st.min && parsed <= st.max
                header()
                OutlinedTextField(
                    value = text,
                    onValueChange = { t ->
                        if (t.length <= 16) {
                            text = t
                            val d = t.replace(',', '.').toDoubleOrNull()
                            if (d != null && d >= st.min && d <= st.max) onSet(d)
                        }
                    },
                    isError = !ok,
                    supportingText = { Text("От ${pluginNum(st.min)} до ${pluginNum(st.max)}") },
                    singleLine = true,
                    shape = rounded(16),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onFocusChanged { focused = it.isFocused },
                )
            }
            "text" -> {
                var text by remember(st.key) { mutableStateOf((cur as? String) ?: (st.default as? String) ?: "") }
                var focused by remember(st.key) { mutableStateOf(false) }
                LaunchedEffect(cur) { val v = cur as? String; if (!focused && v != null && v != text) text = v }
                header()
                OutlinedTextField(
                    value = text,
                    onValueChange = { t -> val v = t.take(st.maxLength); text = v; onSet(v) },
                    supportingText = { Text("${text.length} / ${st.maxLength}") },
                    singleLine = true,
                    shape = rounded(16),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).onFocusChanged { focused = it.isFocused },
                )
            }
            "choice" -> {
                header()
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    st.options.forEach { (value, title) ->
                        FilterChip(
                            selected = cur == value,
                            onClick = { onSet(value) },
                            label = { Text(title) },
                        )
                    }
                }
            }
            "color" -> {
                val hexRe = remember { Regex("^#[0-9A-Fa-f]{6}$") }
                val curHex = (cur as? String) ?: (st.default as? String) ?: "#6750A4"
                var text by remember(st.key) { mutableStateOf(curHex) }
                LaunchedEffect(cur) { val v = cur as? String; if (v != null && !v.equals(text.trim(), true)) text = v }
                val valid = hexRe.matches(text.trim())
                val shown = runCatching { android.graphics.Color.parseColor(if (valid) text.trim() else curHex) }.getOrDefault(0xFF6750A4.toInt())
                header()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Color(shown))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { t ->
                            if (t.length <= 7) {
                                text = t
                                if (hexRe.matches(t.trim())) onSet(t.trim())
                            }
                        },
                        isError = !valid,
                        supportingText = { Text("Формат #RRGGBB") },
                        singleLine = true,
                        shape = rounded(16),
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf("#6750A4", "#B3261E", "#E8710A", "#F9AB00", "#188038", "#0B8A8A", "#1A73E8", "#C2185B").forEach { hex ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    if (hex.equals(curHex, true)) 3.dp else 1.dp,
                                    if (hex.equals(curHex, true)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                )
                                .clickable { text = hex; onSet(hex) },
                        )
                    }
                }
            }
            "button" -> {
                if (st.summary.isNotBlank()) {
                    Text(st.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                FilledTonalButton(
                    onClick = onPress,
                    enabled = live,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(st.title) }
                if (!live) {
                    Text(
                        "Включите плагин и систему плагинов, чтобы кнопка работала",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

private fun pluginSize(b: Long): String = if (b >= 1024 * 1024) "%.1f МБ".format(b / 1048576.0) else "${maxOf(1L, b / 1024)} КБ"

/**
 * Карточка плагина в списке. Появляется с анимацией (раскрывается по высоте, выезжает справа и проявляется), а при
 * удалении [removing] — сворачивается и уезжает; по окончании анимации вызывается [onRemoved] (тогда плагин и стирается).
 */
@Composable
private fun PluginRow(
    p: InstalledPlugin,
    on: Boolean,
    removing: Boolean,
    onAskDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    onRemoved: () -> Unit,
) {
    val m = p.manifest
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    // Начинаем «скрытыми» и сразу переключаемся на «видимые»: так карточка, которая только что появилась, проигрывает вход.
    val visible = remember(p) { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(removing) { if (removing) visible.targetState = false }
    LaunchedEffect(removing, visible.currentState, visible.targetState) {
        if (removing && !visible.currentState && !visible.targetState) onRemoved()
    }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(tween(fxMs(260))) +
            expandVertically(fxSpring(dampingRatio = 0.75f, stiffness = 380f)) +
            slideInHorizontally(fxSpring(dampingRatio = 0.75f, stiffness = 380f)) { it / 3 } +
            scaleIn(fxSpring(dampingRatio = 0.7f, stiffness = 420f), initialScale = 0.9f),
        exit = fadeOut(tween(fxMs(200))) +
            shrinkVertically(tween(fxMs(280), easing = FastOutSlowInEasing)) +
            slideOutHorizontally(tween(fxMs(260), easing = FastOutSlowInEasing)) { it / 2 } +
            scaleOut(tween(fxMs(240)), targetScale = 0.9f),
    ) {
        Column(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(m.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        Text(
                            listOfNotNull("v${m.version}", m.author.ifBlank { null }).joinToString(" · "),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (m.description.isNotBlank()) Text(m.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        p.luaStatus?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                },
                leadingContent = { PluginIcon(p.iconBytes, 44.dp) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExpressiveSwitch(checked = p.enabled, onCheckedChange = { PluginSystem.setPluginEnabled(p, it) })
                        if (m.settings.isNotEmpty() || p.uiEntries.any { !it.transient }) {
                            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                                Icon(painterResource(R.drawable.ms_settings), contentDescription = "Настройки плагина")
                            }
                        }
                        IconButton(onClick = onAskDelete, modifier = Modifier.size(36.dp)) {
                            Icon(painterResource(R.drawable.ms_delete), contentDescription = "Удалить плагин")
                        }
                    }
                },
                colors = itemColors,
                modifier = Modifier.alpha(if (on) 1f else 0.5f),
            )
        }
    }
}

/** Настройки → Плагины: главный выключатель, список установленных, установка из файла .exm. */
@Composable
private fun PluginsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) PluginSystem.offerInstall(ctx, uri)
    }
    var toDelete by remember { mutableStateOf<InstalledPlugin?>(null) }
    var settingsFor by remember { mutableStateOf<String?>(null) }
    // Плагины, которые прямо сейчас «уезжают»: стираются по окончании анимации (см. PluginRow).
    val removing = remember { mutableStateListOf<String>() }
    val on = PluginSystem.enabled
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = 24.dp + dockInset())) {
        SubPageBar("Плагины", onBack)
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Система плагинов") },
                supportingContent = { Text(if (on) "Включена: включённые плагины работают" else "Выключена: плагины установлены, но не действуют") },
                leadingContent = { Icon(painterResource(R.drawable.ms_extension), null) },
                trailingContent = { ExpressiveSwitch(checked = on, onCheckedChange = { PluginSystem.updateEnabled(it) }) },
                colors = itemColors,
                modifier = Modifier.clickable { PluginSystem.updateEnabled(!on) },
            )
        }
        Text(
            "Плагин — один файл .exm с манифестом и Lua-скриптами внутри. Скрипт работает в песочнице: может следить за " +
                "плеером, показывать уведомления, менять цвет приложения и добавлять станции волны.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        )
        SettingsLabel("Установленные")
        AnimatedVisibility(
            visible = PluginSystem.plugins.isEmpty(),
            enter = fadeIn(tween(fxMs(220))) + expandVertically(),
            exit = fadeOut(tween(fxMs(120))) + shrinkVertically(),
        ) {
            Text(
                "Плагинов пока нет. Откройте файл .exm из файлового менеджера или нажмите кнопку ниже.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
        if (PluginSystem.plugins.isNotEmpty()) {
            SettingsGroup {
                PluginSystem.plugins.toList().forEach { p ->
                    key(p.id) {
                        PluginRow(
                            p = p, on = on, removing = p.id in removing,
                            onAskDelete = { toDelete = p },
                            onOpenSettings = { settingsFor = p.id },
                            onRemoved = { PluginSystem.uninstall(p); removing.remove(p.id) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = !PluginSystem.busy,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
        ) {
            Icon(painterResource(R.drawable.ms_add), null)
            Spacer(Modifier.width(8.dp))
            Text("Установить из файла .exm")
        }
    }
    settingsFor?.let { id ->
        val sp = PluginSystem.plugins.firstOrNull { it.id == id }
        if (sp != null && (sp.manifest.settings.isNotEmpty() || sp.uiEntries.any { !it.transient })) PluginSettingsSheet(sp) { settingsFor = null }
        else settingsFor = null
    }
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить плагин «${p.manifest.name}»?") },
            text = { Text("Файл плагина будет удалён с устройства. Установить его снова можно из файла .exm.") },
            confirmButton = {
                TextButton(onClick = { toDelete = null; if (p.id !in removing) removing.add(p.id) }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

/** Диалог, который открыл Lua-скрипт: заголовок, текст, необязательное поле ввода и до трёх кнопок. */
@Composable
private fun PluginLuaDialog(d: app.ymusic.m3e.data.PluginDialog) {
    var input by remember(d) { mutableStateOf(d.inputValue ?: "") }
    val answer = { b: Int -> PluginSystem.answerDialog(d, b, if (d.inputValue != null) input else null) }
    AlertDialog(
        onDismissRequest = { if (d.cancelable) answer(-1) },
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = d.cancelable, dismissOnClickOutside = d.cancelable),
        title = { Text(d.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.text.isNotBlank()) Text(d.text)
                if (d.inputValue != null) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.take(d.inputMaxLength) },
                        placeholder = { Text(d.inputHint) },
                        supportingText = { Text("${input.length} / ${d.inputMaxLength}") },
                        shape = rounded(16),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Плагин «${d.pluginName}»",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                d.buttons.forEachIndexed { i, b ->
                    when (b.style) {
                        "primary" -> Button(onClick = { answer(i) }) { Text(b.title, maxLines = 1) }
                        "danger" -> TextButton(onClick = { answer(i) }) { Text(b.title, maxLines = 1, color = MaterialTheme.colorScheme.error) }
                        else -> TextButton(onClick = { answer(i) }) { Text(b.title, maxLines = 1) }
                    }
                }
            }
        },
    )
}

/** Окно снизу при установке плагина: сведения о нём и кнопка «Установить» (или «Обновить»). */
@Composable
private fun PluginInstallSheet(pv: PluginPreview) {
    val m = pv.manifest
    val old = pv.installed
    val sheetState = rememberModalBottomSheetState()
    val sheetScope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    // Включать ли хуки плагина: для нового плагина выключено, для обновления — как было у установленного.
    var hooksOn by remember(pv) { mutableStateOf(PluginSystem.plugins.firstOrNull { it.id == m.id }?.hooksOn == true) }
    // Кнопки «Отмена» и «Установить» закрывают окно с анимацией: сначала шторка плавно уезжает вниз, и только потом
    // действие выполняется (раньше окно пропадало мгновенно, потому что плагин снимался с «ожидания» сразу).
    fun closeThen(action: () -> Unit) {
        if (closing) return
        closing = true
        sheetScope.launch {
            runCatching { sheetState.hide() }
            action()
        }
    }
    ModalBottomSheet(onDismissRequest = { PluginSystem.dismissPending() }, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberTopScrollState()).navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PluginIcon(pv.iconBytes, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull("v${m.version}", m.author.ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (m.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(m.description, style = MaterialTheme.typography.bodyMedium)
            }
            if (m.hooks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Хуки плагина", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                m.hooks.forEach { h ->
                    Text("• ${h.title}", style = MaterialTheme.typography.bodyMedium)
                    if (h.summary.isNotBlank()) {
                        Text(
                            h.summary, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                if (m.unsafeJava) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Плагин может попросить отключить для него песочницу и выполнять Java-код. Это ОПАСНО: по умолчанию выключено, " +
                            "включается только вручную в настройках плагина после предупреждения.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                // Вопрос при установке: по умолчанию хуки выключены; при обновлении стоит прежний выбор пользователя.
                Row(
                    Modifier.fillMaxWidth().clip(rounded(16)).clickable { hooksOn = !hooksOn }
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Включить хуки?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Код хуков находится в самом плагине. Это можно поменять позже в настройках плагина",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    ExpressiveSwitch(checked = hooksOn, onCheckedChange = { hooksOn = it })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "id: ${m.id}\nAPI ${m.api} · файлов: ${m.fileCount} · ${pluginSize(pv.fileBytes)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (old != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (old.version == m.version) "Этот плагин уже установлен (v${old.version}): он будет заменён."
                    else "Уже установлена версия v${old.version}: она будет заменена на v${m.version}.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { closeThen { PluginSystem.dismissPending() } }, modifier = Modifier.weight(1f).height(56.dp)) { Text("Отмена") }
                Button(onClick = { closeThen { PluginSystem.confirmInstall(hooksOn) } }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(if (old == null) "Установить" else "Обновить")
                }
            }
        }
    }
}

@Composable
private fun OfflinePage(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Офлайн-режим", onBack)
        ListItem(
            headlineContent = { Text("Файлы с устройства") },
            supportingContent = { Text("Папка «С устройства» в плейлистах и группа в поиске") },
            leadingContent = { Icon(painterResource(R.drawable.ms_folder), null) },
            trailingContent = {
                ExpressiveSwitch(checked = LocalFilesSettings.enabled, onCheckedChange = { LocalFilesSettings.updateEnabled(it) })
            },
            modifier = Modifier.clickable { LocalFilesSettings.updateEnabled(!LocalFilesSettings.enabled) },
        )
    }
}

/** О проекте: шапка с градиентом, версия и ветка, ссылки и переход к странице «Разработчики». */
@Composable
private fun AboutPage(onBack: () -> Unit, onDevelopers: () -> Unit) {
    val ctx = LocalContext.current
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = 24.dp + dockInset())) {
        SubPageBar("О проекте", onBack)
        // Шапка: прямоугольник с закруглениями, градиент акцентного цвета, значок и название по центру по высоте.
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(132.dp)
                .clip(containerShape(28))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(scheme.primary, androidx.compose.ui.graphics.lerp(scheme.primary, scheme.tertiary, 0.6f)),
                    ),
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ms_exmusic_logo), contentDescription = null,
                    tint = scheme.onPrimary, modifier = Modifier.size(84.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text("ExMusic", style = MaterialTheme.typography.displaySmallEmphasized, color = scheme.onPrimary, maxLines = 1)
            }
        }
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Версия ExMusic") },
                supportingContent = { Text(APP_VERSION) },
                leadingContent = { Icon(painterResource(R.drawable.ms_info), null) },
                colors = itemColors,
            )
            ListItem(
                headlineContent = { Text("Ветка разработки") },
                supportingContent = { Text(app.ymusic.m3e.data.Updates.current.channel.title) },
                leadingContent = { Icon(painterResource(R.drawable.ms_new_releases), null) },
                colors = itemColors,
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            ListItem(
                headlineContent = { Text("GitHub") },
                leadingContent = { Icon(painterResource(R.drawable.ms_open_in_new), null) },
                colors = itemColors,
                modifier = Modifier.clickable { openUrl(ctx, "https://github.com/ExMusicApp/") },
            )
            ListItem(
                headlineContent = { Text("Telegram") },
                leadingContent = { Icon(painterResource(R.drawable.ms_open_in_new), null) },
                colors = itemColors,
                modifier = Modifier.clickable { openUrl(ctx, "https://t.me/ExMusicApp") },
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Поддержать проект", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("Откроется страница пожертвований DonationAlerts") },
                leadingContent = { Icon(painterResource(R.drawable.ms_payments), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_open_in_new), null) },
                colors = ListItemDefaults.colors(
                    containerColor = scheme.primaryContainer,
                    headlineColor = scheme.onPrimaryContainer,
                    supportingColor = scheme.onPrimaryContainer,
                    leadingIconColor = scheme.onPrimaryContainer,
                    trailingIconColor = scheme.onPrimaryContainer,
                ),
                modifier = Modifier.clickable { openUrl(ctx, "https://donationalerts.com/r/asdlog") },
            )
        }
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            ListItem(
                headlineContent = { Text("Разработчики") },
                leadingContent = { Icon(painterResource(R.drawable.ms_person), null) },
                trailingContent = { Icon(painterResource(R.drawable.ms_chevron_right), null) },
                colors = itemColors,
                modifier = Modifier.clickable(onClick = onDevelopers),
            )
        }
    }
}

/** Круглая «печенька» с аватаркой из ресурсов (страница «Разработчики»). */
@Composable
private fun DevAvatar(res: Int) {
    androidx.compose.foundation.Image(
        painterResource(res), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.size(44.dp).clip(MaterialShapes.Cookie9Sided.toShape()),
    )
}

/** Разработчики: команда проекта и те, кто поддержал (группы блоков, как в настройках). */
@Composable
private fun DevelopersPage(onBack: () -> Unit) {
    val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = 24.dp + dockInset())) {
        SubPageBar("Разработчики", onBack)
        SettingsGroup {
            listOf(
                Triple("fernando", "Основатель проекта", R.drawable.av_fernando),
                Triple("AFGAN Store", "Со-основатель проекта", R.drawable.av_afgan),
                Triple("dani4xde", "Тестер", R.drawable.av_dani4xde),
                Triple("NeoJuii", "Тестер", R.drawable.av_neojuii),
                Triple("skovorodka", "Тестер", R.drawable.av_skovorodka),
            ).forEach { (name, role, avatar) ->
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text(role) },
                    leadingContent = { DevAvatar(avatar) },
                    colors = itemColors,
                )
            }
        }
        SettingsLabel("Поддержавшие")
        SettingsGroup {
            listOf("Внд.фф" to R.drawable.av_vnd, "Cat_Existen" to R.drawable.av_cat_existen).forEach { (name, avatar) ->
                ListItem(
                    headlineContent = { Text(name) },
                    leadingContent = { DevAvatar(avatar) },
                    colors = itemColors,
                )
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun AboutPageClassic(onBack: () -> Unit) {
    // Большие мягкие пятна акцентного цвета в верхнем левом и нижнем правом углах:
    // почти ровная заливка с плавно размытым краем (градиент работает на любой версии Android).
    val spot = MaterialTheme.colorScheme.primary.copy(alpha = 0.40f)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.matchParentSize().drawBehind {
                val r = size.width * 1.0f
                fun softSpot(center: androidx.compose.ui.geometry.Offset) = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to spot,
                    0.55f to spot,
                    0.75f to spot.copy(alpha = spot.alpha * 0.45f),
                    1f to Color.Transparent,
                    center = center,
                    radius = r,
                )
                drawRect(brush = softSpot(androidx.compose.ui.geometry.Offset(0f, 0f)))
                drawRect(brush = softSpot(androidx.compose.ui.geometry.Offset(size.width, size.height)))
            },
        )
        SubPageBar("О проекте", onBack, modifier = Modifier.align(Alignment.TopStart))
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ExMusic", style = MaterialTheme.typography.displayMediumEmphasized)
            Spacer(Modifier.height(4.dp))
            Text(
                APP_VERSION,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Ссылки внизу страницы: «Мы в Telegram • GitHub», под ними — «Сайт».
        val ctx = LocalContext.current
        val linkStyles = androidx.compose.ui.text.TextLinkStyles(
            style = androidx.compose.ui.text.SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
            ),
        )
        fun androidx.compose.ui.text.AnnotatedString.Builder.link(label: String, url: String) {
            withLink(androidx.compose.ui.text.LinkAnnotation.Clickable(url, linkStyles) { openUrl(ctx, url) }) { append(label) }
        }
        val dot = MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp + dockInset()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    link("Мы в Telegram", "https://t.me/ExMusicApp")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = dot)) { append(" • ") }
                    link("GitHub", "https://github.com/ExMusicApp/")
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                androidx.compose.ui.text.buildAnnotatedString { link("Сайт", "https://exmusicapp.github.io") },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private fun freqLabel(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        (if (k % 1f == 0f) k.toInt().toString() else String.format(java.util.Locale.US, "%.1f", k)) + " кГц"
    } else "$hz Гц"

/** Вертикальный слайдер: обычный Slider, повёрнутый на 270°. */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        enabled = enabled,
        modifier = modifier
            .graphicsLayer {
                rotationZ = 270f
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    Constraints(
                        minWidth = constraints.minHeight,
                        maxWidth = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) { placeable.place(-placeable.width, 0) }
            },
    )
}

@Composable
private fun EqualizerPage(vm: AppViewModel, onBack: () -> Unit) {
    val eq by vm.eqState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Эквалайзер", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberTopScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp + dockInset()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Выключатель
            Surface(shape = containerShape(28), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Эквалайзер", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    ExpressiveSwitch(checked = eq.enabled, onCheckedChange = { vm.setEqEnabled(it) })
                }
            }
            // Выбор пресета
            var menu by remember { mutableStateOf(false) }
            Box {
                Surface(
                    onClick = { menu = true },
                    enabled = eq.enabled,
                    shape = containerShape(28),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Пресет", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(EqPresets.byId(eq.presetId).title, style = MaterialTheme.typography.titleMedium)
                        }
                        Icon(painterResource(R.drawable.ms_expand_more), null)
                    }
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    EqPresets.all.filter { it.gains != null }.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.title) },
                            onClick = { menu = false; vm.setEqPreset(preset.id) },
                        )
                    }
                }
            }
            // Полосы
            Surface(shape = containerShape(28), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    eq.bandsDb.forEachIndexed { i, db ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                db.roundToInt().let { if (it > 0) "+$it" else "$it" },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(Modifier.height(220.dp).width(48.dp), contentAlignment = Alignment.Center) {
                                VerticalSlider(
                                    value = db,
                                    onValueChange = { vm.setEqBand(i, it) },
                                    valueRange = eq.minDb..eq.maxDb,
                                    enabled = eq.enabled,
                                )
                            }
                            Text(
                                eq.freqsHz.getOrNull(i)?.let { freqLabel(it) }.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Плеер ----------

/**
 * Шторка полного плеера со своим жестом (вместо ModalBottomSheet, у которого якоря пересчитывались
 * при перерисовке и анимация зацикливалась). Положение хранится в [PlayerSheetState] и общее с мини-плеером:
 * мини-плеер вытягивает шторку вверх пальцем, а здесь её можно утянуть обратно вниз. Одна анимация за раз.
 */
@Composable
private fun PlayerSheet(vm: AppViewModel, sheet: PlayerSheetState) {
    val closedPx = sheet.closedPx
    BackHandler { sheet.close() }

    val dragState = androidx.compose.foundation.gestures.rememberDraggableState { d -> sheet.dragBy(d) }
    // Состояния «текст песни» и «вывод звука» живут здесь: в альбомной ориентации кнопки для них стоят в шапке.
    val sNow by vm.state.collectAsStateWithLifecycle()
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // Затемнение под шторкой (при перетаскивании вниз проступает фон приложения)
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = (1f - (sheet.offset.value / closedPx)).coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.32f))
        )
        // Плеер на весь экран: ничего не прокручивается. Закрыть — кнопка-стрелка сверху,
        // свайп вниз в любом месте плеера или системная кнопка «назад».
        // «Матовое стекло» фона плеера (выключено по умолчанию): полупрозрачная подложка и размытое приложение за ней.
        val sheetShape: Shape =
            if (ThemeSettings.iconShape == IconShape.Default) androidx.compose.foundation.shape.RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
            else MaterialTheme.shapes.extraLarge
        val playerGlassOn = GlassSettings.playerGlass && !ThemeSettings.optimize
        val motionBlurOn = ThemeSettings.motionBlur && !ThemeSettings.optimize && android.os.Build.VERSION.SDK_INT >= 31
        val motionBlurK = ThemeSettings.motionBlurStrength
        Surface(
            shape = sheetShape,
            color = if (playerGlassOn) {
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = GlassSettings.solidAlpha(GlassSettings.playerAlpha))
            } else MaterialTheme.colorScheme.surfaceContainerLow,
            // Цвет контента задаём явно: у полупрозрачной подложки Surface не находит «свой» onSurface, и текст (название трека) выходил чёрным.
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = if (playerGlassOn) 0.dp else 1.dp,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = sheet.offset.value
                    // Motion blur при выезде и сворачивании плеера: размытие по вертикали пропорционально скорости шторки.
                    if (motionBlurOn) {
                        val by = (kotlin.math.abs(sheet.offset.velocity) / 1000f * 3f * motionBlurK).coerceAtMost(36f * motionBlurK)
                        if (by > 0.8f) renderEffect = androidx.compose.ui.graphics.BlurEffect(0f, by, androidx.compose.ui.graphics.TileMode.Clamp)
                    }
                }
                // Матовое стекло: само приложение под плеером размывается на месте (см. Modifier.playerGlassBackdrop у корня),
                // а здесь поверх него — молочная вуаль и тонкая светлая кромка.
                .glassFrost(playerGlassOn, sheetShape)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // Размытая обложка текущего трека на фоне плеера (не рисуется при «Матовом стекле» фона плеера и при «Оптимизации»).
            if (PlayerSettings.playerBgBlur && (!playerGlassOn || android.os.Build.VERSION.SDK_INT < 31) && !ThemeSettings.optimize) PlayerBackdrop(sNow.nowPlaying)
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Та же проверка, что была в FullPlayer: область под шапкой (52 dp) с отступами по 20 dp.
            val landscape = (maxWidth - 40.dp) > (maxHeight - 52.dp) * 1.1f
            Column(
                Modifier.fillMaxSize()
                    .draggable(
                        state = dragState,
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        onDragStarted = { sheet.offset.stop() },
                        onDragStopped = { v -> sheet.settleClosing(v) },
                    ),
            ) {
                Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { sheet.close() }) {
                        Icon(painterResource(R.drawable.ms_expand_more), contentDescription = "Свернуть плеер")
                    }
                    val nowT = sNow.nowPlaying
                    if (landscape && nowT != null) {
                        // Альбомная ориентация: маленькие кнопки (звук, ещё, повтор, текст, дизлайк, лайк) — наверху, без прокрутки.
                        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            PlayerBottomRow(vm, nowT, lyricsOn = showLyrics, onLyrics = { showLyrics = !showLyrics }) { showOutput = true }
                        }
                    } else {
                        Text(
                            "Сейчас играет",
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Недавно прослушанные: кнопка открывает шторку со списком (как в Яндекс Музыке).
                    var showHistory by remember { mutableStateOf(false) }
                    // Наша обёртка: отклик на нажатие и подсказка при долгом нажатии; тиков при удержании в плеере нет (LocalHoldHaptics = false).
                    FilledTonalIconButton(onClick = { showHistory = true }) {
                        Icon(painterResource(R.drawable.ms_history), contentDescription = "История прослушивания")
                    }
                    if (showHistory) RecentTracksSheet(vm, onNav = { sheet.close() }) { showHistory = false }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    FullPlayer(
                        vm, landscape,
                        showLyrics = showLyrics, onToggleLyrics = { showLyrics = !showLyrics },
                        showOutput = showOutput, setShowOutput = { showOutput = it },
                    ) { sheet.close() }
                }
            }
            }
        }
    }
}

/**
 * Фон полного плеера: обложка текущего трека, сильно размытая и приглушённая цветом поверхности, чтобы текст оставался читаемым.
 * На Android 12+ размытие настоящее; на старых обложка берётся в крошечном размере и растягивается, что тоже даёт мягкий фон.
 */
@Composable
private fun PlayerBackdrop(track: Track?) {
    val cover = track?.cover(300)
    val ctx = LocalContext.current
    val scrim = MaterialTheme.colorScheme.surfaceContainerLow
    Crossfade(targetState = cover, animationSpec = tween(fxMs(400)), label = "playerBackdrop") { url ->
        Box(Modifier.fillMaxSize()) {
            if (url != null) {
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val request = remember(url) {
                        ImageRequest.Builder(ctx).data(url).size(300).build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize().blur(56.dp, BlurredEdgeTreatment.Unbounded),
                    )
                } else {
                    // Android 8–11: настоящего размытия нет, поэтому обложку размываем сами (раньше брали 40 px — выходила пиксельной).
                    val bmp = rememberSoftBlurredCover(url)
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Box(Modifier.fillMaxSize().background(scrim.copy(alpha = 0.62f)))
            }
        }
    }
}

/** Срез состояния для шторки «История»: без позиции трека и прочего, что меняется постоянно. */
private data class RecentSheetSnap(
    val nowId: String?,
    val syncedHistory: List<Track>,
    val history: List<Track>,
    val sessionHistory: List<Track>,
    val sessionLabel: String,
)

/**
 * История в плеере: шторка Material 3 с двумя вкладками. «С включения» — только то, что играло с момента запуска
 * текущего плейлиста / «Моей волны» / волны по источнику; «Вся история» — всё прослушанное. Нажатие на трек играет его сразу,
 * но плейлист или волна не заменяются и продолжаются после него. Играющий сейчас трек в списке не повторяется.
 */
@Composable
private fun RecentTracksSheet(vm: AppViewModel, onNav: () -> Unit, onDismiss: () -> Unit) {
    // Важно: подписываемся не на весь vm.state, а на узкий срез. Состояние плеера обновляется на каждом тике позиции,
    // и перерисовка шторки на каждый тик заставляла ModalBottomSheet пересчитывать якоря: если долго тянуть шторку вверх,
    // анимация подъёма зацикливалась и шторка залипала. Срез меняется только когда реально меняются списки / трек.
    val s by remember(vm) {
        vm.state
            .map { RecentSheetSnap(it.nowPlaying?.id, it.syncedHistory, it.history, it.sessionHistory, it.sessionLabel) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = vm.state.value.let { RecentSheetSnap(it.nowPlaying?.id, it.syncedHistory, it.history, it.sessionHistory, it.sessionLabel) },
    )
    val nowId = s.nowId
    var tab by remember { mutableIntStateOf(if (s.sessionHistory.isNotEmpty()) 0 else 1) }
    // Как на главной: серверная история (с других устройств) вместе с локальной; пока не загрузилась — локальная.
    val all = remember(s.syncedHistory, s.history, nowId) {
        s.syncedHistory.ifEmpty { s.history }.filter { it.id != nowId }
    }
    val session = remember(s.sessionHistory, nowId) { s.sessionHistory.filter { it.id != nowId } }
    val list = if (tab == 0) session else all
    val sessionName = s.sessionLabel.ifBlank { "Текущий список" }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                "История",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("С включения") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Вся история") })
            }
            Text(
                when {
                    tab == 0 && list.isEmpty() -> "$sessionName: пока нет других треков — они появятся по мере прослушивания"
                    tab == 0 -> "$sessionName · ${list.size} шт., сначала самые свежие"
                    list.isEmpty() -> "Здесь появятся треки, которые вы послушаете"
                    else -> "${list.size} шт., сначала самые свежие"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (list.isNotEmpty()) {
                // Шторка лежит поверх полного плеера: исполнитель/альбом открываются под ним, поэтому
                // сначала закрываем шторку и сворачиваем плеер, и только потом показываем карточку.
                CompositionLocalProvider(
                    LocalOpenArtist provides { ref: ArtistRef -> onDismiss(); onNav(); vm.openArtist(ref) },
                    LocalOpenAlbum provides { id: String, title: String? -> onDismiss(); onNav(); vm.openAlbum(id, title) },
                ) {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(bottom = 24.dp)) {
                        itemsIndexed(list, key = { i, t -> "${tab}:${t.id}#$i" }) { _, t ->
                            // Трек играет сразу, а очередь (плейлист / волна) остаётся и продолжается после него.
                            TrackRow(t) { vm.playFromHistory(t); onDismiss() }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Срез состояния для шторки «Включить волну по…». */
private data class WaveSheetSnap(val playlists: List<PlaylistInfo>, val playingSource: String?, val apiId: String?)

/**
 * «Включить волну по…»: как в Яндекс Музыке, волна подбирается не из всей музыки аккаунта, а по этому треку,
 * исполнителю, альбому или плейлисту, из которого играет музыка. Текущий трек и очередь заменяются новой волной.
 */
@Composable
private fun WaveFromSheet(vm: AppViewModel, t: Track, fromPlayer: Boolean = true, onDismiss: () -> Unit) {
    // Узкий срез состояния: без позиции трека, иначе шторка перерисовывается на каждом тике и её анимация зацикливается.
    val s by remember(vm) {
        vm.state.map { WaveSheetSnap(it.playlists, it.playingSource, it.account?.apiId) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = vm.state.value.let { WaveSheetSnap(it.playlists, it.playingSource, it.account?.apiId) })
    class Opt(val title: String, val sub: String, val run: () -> Unit)
    val opts = ArrayList<Opt>()
    opts += Opt("По этому треку", t.title) { vm.startWaveByTrack(t) }
    t.artistRefs.take(4).forEach { ref ->
        opts += Opt("По исполнителю", ref.name) { vm.startWaveByArtist(ref) }
    }
    val albumId = t.albumId
    if (!albumId.isNullOrBlank()) {
        opts += Opt("По альбому", t.albumTitle?.takeIf { it.isNotBlank() } ?: "Альбом трека") { vm.startWaveByAlbum(albumId, t.albumTitle) }
    }
    // Плейлист берётся из того, что играет сейчас, поэтому из меню трека (где это может быть другой список) его не предлагаем.
    val src = if (fromPlayer) s.playingSource else null
    if (src != null && src.startsWith("pl:")) {
        val p = s.playlists.firstOrNull { playlistSourceKey(it) == src }
        val parts = src.split(":")
        if (p != null) {
            opts += Opt("По плейлисту", p.title) { vm.startWaveByPlaylist(p.ownerUid, p.kind.toString(), p.title) }
        } else if (parts.size >= 3) {
            opts += Opt("По плейлисту", "Плейлист, из которого играет музыка") { vm.startWaveByPlaylist(parts[1], parts[2], "Плейлист") }
        }
    } else if (src == "liked") {
        val uid = s.apiId
        if (uid != null) opts += Opt("По плейлисту", "Любимое") { vm.startWaveByPlaylist(uid, "3", "Любимое") }
    }
    // Станции из плагинов (если система плагинов включена).
    PluginSystem.activeStations.forEach { (pl, st) ->
        opts += Opt(st.title, "Плагин «${pl.manifest.name}»" + (st.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")) {
            vm.startWave(listOf(st.station), "Волна: ${st.title}")
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Включить волну",
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                "Бесконечная подборка похожей музыки. Текущая очередь заменится волной",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                itemsIndexed(opts, key = { i, o -> "${o.title}#$i" }) { _, o ->
                    val shape = containerShape(20)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                            .pressable(shape) { o.run(); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ms_graphic_eq), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                o.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(painterResource(R.drawable.ms_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Ползунок размера интерфейса: применяется, когда палец отпущен (иначе ползунок «убегает» из-под пальца). */
@Composable
internal fun UiScaleSetting() {
    var draft by remember { mutableFloatStateOf(ThemeSettings.uiScale) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "${(draft * 100).roundToInt()}% — размер значков, отступов и текста во всём приложении. Меняется после того, как отпустите ползунок",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = draft,
            onValueChange = { draft = ((it * 20f).roundToInt() / 20f) },
            onValueChangeFinished = { ThemeSettings.updateUiScale(draft) },
            valueRange = ThemeSettings.MIN_UI_SCALE..ThemeSettings.MAX_UI_SCALE,
        )
        if (draft != 1f) {
            TextButton(onClick = { draft = 1f; ThemeSettings.updateUiScale(1f) }) { Text("Сбросить до 100%") }
        }
    }
}

// ---------- Мини-плеер ----------

private enum class MiniSlider { Seek, Volume }

/**
 * Громкость музыки с отслеживанием кнопок громкости. Пока музыка играет на колонке Яндекса (Wi-Fi или Ynison),
 * ползунок управляет громкостью колонки, а не телефона.
 */
private class VolumeState(private val am: android.media.AudioManager) {
    private val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var value by mutableFloatStateOf(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max)
        private set
    var dragging = false

    /** Колонка, на которой сейчас играет музыка (null — управляем громкостью телефона). */
    var remote: app.ymusic.m3e.data.ConnectDevice? = null
    var sendRemote: ((app.ymusic.m3e.data.ConnectDevice, Float) -> Unit)? = null
    private var lastSent = 0L

    fun refresh() {
        if (dragging) return
        val r = remote
        if (r != null) {
            if (r.volume >= 0.0) value = r.volume.coerceIn(0.0, 1.0).toFloat()
        } else {
            value = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / max
        }
    }

    fun set(v: Float) {
        dragging = true
        value = v.coerceIn(0f, 1f)
        val r = remote
        if (r != null) {
            // Не чаще раза в 250 мс, чтобы колонка менялась сразу, но без спама.
            val now = System.currentTimeMillis()
            if (now - lastSent > 250) { lastSent = now; sendRemote?.invoke(r, value) }
        } else {
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0)
        }
    }

    /** Палец отпущен: для колонки досылаем итоговое значение. */
    fun finish() {
        dragging = false
        remote?.let { sendRemote?.invoke(it, value) }
    }
}

@Composable
private fun rememberVolume(vm: AppViewModel, active: Boolean): VolumeState {
    val ctx = LocalContext.current
    val st = remember { VolumeState(ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager) }
    val cs by vm.connectState.collectAsStateWithLifecycle()
    st.sendRemote = { d, v -> vm.setConnectVolume(d, v) }
    st.remote = if (vm.remoteActive()) {
        cs.devices.firstOrNull { it.id == cs.remoteId && it.volumeSteps > 0 }
    } else null
    // Громкость колонки изменилась (кнопки на колонке, голос, другой клиент) или сменили источник — обновляем ползунок.
    LaunchedEffect(active, st.remote?.id, st.remote?.volume) { if (active) st.refresh() }
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose { }
        st.refresh()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: android.content.Intent?) = st.refresh()
        }
        androidx.core.content.ContextCompat.registerReceiver(
            ctx, receiver, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { ctx.unregisterReceiver(receiver) } }
    }
    return st
}

/**
 * Компактный мини-плеер. Свайп влево/вправо по обложке и названию переключает трек (как в Spotify);
 * по настройкам «Плеер» снизу появляется тонкий ползунок перемотки и/или громкости (кнопка справа переключает).
 */
@Composable
private fun MiniPlayer(vm: AppViewModel, sheet: PlayerSheetState, island: Boolean) {
    val s by vm.state.collectAsStateWithLifecycle()
    // Запоминаем последний трек, чтобы мини-плеер красиво уезжал, а не исчезал.
    var lastTrack by remember { mutableStateOf<Track?>(null) }
    if (s.nowPlaying != null) lastTrack = s.nowPlaying
    val miniPos by vm.positionMs.collectAsStateWithLifecycle()
    val progress by animateFloatAsState(
        targetValue = (miniPos.toFloat() / s.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(fxMs(500), easing = LinearEasing),
        label = "miniProgress",
    )
    val seekOn = PlayerSettings.miniSeek
    val volOn = PlayerSettings.miniVolume
    var pick by remember { mutableStateOf(MiniSlider.Seek) }
    val mode: MiniSlider? = when {
        seekOn && volOn -> pick
        seekOn -> MiniSlider.Seek
        volOn -> MiniSlider.Volume
        else -> null
    }
    val trackId = lastTrack?.id.orEmpty()
    val seek = rememberSeek(vm, trackId, s.durationMs, s.isPlaying && mode == MiniSlider.Seek)
    val vol = rememberVolume(vm, active = mode == MiniSlider.Volume)
    val offset = remember { Animatable(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    val t = lastTrack ?: return
    // Для анимации смены трека: идёт ли переключение свайпом (у него своя анимация) и какой трек был следующим.
    val swipeSwitch = remember { BooleanArray(1) }
    val dirMemo = remember { arrayOfNulls<String>(1) }
    val nextIdOfShown = remember(t.id) { vm.neighborTrack(true)?.id }
    SideEffect { dirMemo[0] = nextIdOfShown }
    val style = ThemeSettings.seekStyle
    val progressColor = (if (island) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.12f)
    val showFill = mode != MiniSlider.Seek && !style.circular
    // Стиль ползунка общий с полным плеером. В круговом стиле перемотка — это само кольцо вокруг кнопки play/pause.
    val linearBar = mode != null && !(style.circular && mode == MiniSlider.Seek)

    // Свайп вверх по мини-плееру вытягивает полный плеер вслед за пальцем (как в Яндекс Музыке).
    val openDrag = androidx.compose.foundation.gestures.rememberDraggableState { d -> sheet.dragBy(d) }
    // Плавное превращение мини-плеера в островок и обратно: углы, цвет, тень и отступы перетекают пружиной.
    val morph by animateFloatAsState(
        targetValue = if (island) 1f else 0f,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "miniMorph",
    )
    // Скругление: из редактора интерфейса (−1 — как раньше: зависит от «Островка»).
    val miniRadius = if (UiStyle.miniCorner >= 0) UiStyle.miniCorner.dp
        else maxOf(0.dp, scaledDp(24) + (scaledDp(32) - scaledDp(24)) * morph)
    val miniShape = cornerShape(miniRadius)
    // «Матовое стекло» мини-плеера (выключено по умолчанию): полупрозрачная подложка и размытый экран за ней.
    val miniGlassOn = GlassSettings.miniGlass && !ThemeSettings.optimize
    val miniProbe = remember { mutableStateOf(Offset.Zero) }
    // Подкраска под обложку играющего трека (настройка «Цвет мини-плеера по обложке»): цвет плавно перетекает при смене трека.
    val tintHs = rememberCoverHue(t.cover(160), PlayerSettings.miniCoverColor)
    val secBase = MaterialTheme.colorScheme.secondaryContainer
    val highBase = MaterialTheme.colorScheme.surfaceContainerHigh
    val tintSec by animateColorAsState(tintHs?.let { coverTinted(it, secBase) } ?: secBase, tween(fxMs(500)), label = "miniTintSec")
    val tintHigh by animateColorAsState(tintHs?.let { coverTinted(it, highBase) } ?: highBase, tween(fxMs(500)), label = "miniTintHigh")
    val miniBase = androidx.compose.ui.graphics.lerp(tintSec, tintHigh, morph.coerceIn(0f, 1f))
    val miniSurfaceColor = if (miniGlassOn) miniBase.copy(alpha = GlassSettings.solidAlpha(GlassSettings.miniAlpha)) else miniBase
    val miniGlowColor = rememberGlowColor(t.cover(160))
    Surface(
        // Док-панель в виде островка — и мини-плеер «островок»: парящая капсула с тенью.
        shape = miniShape,
        color = miniSurfaceColor,
        // Без подкраски цвет содержимого выбирается как раньше; с подкраской — как у обычной подложки мини-плеера.
        contentColor = if (tintHs != null) androidx.compose.ui.graphics.lerp(
            MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.onSurface, morph.coerceIn(0f, 1f),
        ) else contentColorFor(miniSurfaceColor),
        tonalElevation = if (miniGlassOn) 0.dp else (3f * morph).coerceAtLeast(0f).dp,
        shadowElevation = if (miniGlassOn) 0.dp else (8f * morph).coerceAtLeast(0f).dp,
        modifier = Modifier
            .padding(
                start = (12f + 4f * morph + UiStyle.miniSide).dp, top = (3f + 2f * morph).dp,
                end = (12f + 4f * morph + UiStyle.miniSide).dp, bottom = (3f + 2f * morph + UiStyle.miniBottom).dp,
            )
            // Свечение мини-плеера: тот же переключатель, что и у полного плеера; на паузе не рисуется.
            .playGlow(
                active = s.isPlaying, enabled = ThemeSettings.glowPlayer,
                color = miniGlowColor,
                cornerRadius = miniRadius,
                spread = 12.dp, strength = 0.5f,
                shape = miniShape,
            )
            .fillMaxWidth()
            .glassOriginProbe(miniProbe)
            .glassBehind(miniGlassOn, GlassSettings.miniBlurDp, miniShape) { miniProbe.value }
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .draggable(
                state = openDrag,
                orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                onDragStarted = { sheet.offset.stop() },
                onDragStopped = { v -> sheet.settleOpening(v) },
            )
            .pointerInput(Unit) {
                // Вибро-тики при пролистывании — по шагам, как у ползунка перемотки.
                var lastTick = 0
                detectHorizontalDragGestures(
                    onDragStart = { lastTick = 0; scope.launch { offset.stop() } },
                    onDragEnd = {
                        val o = offset.value
                        scope.launch {
                            if (kotlin.math.abs(o) > widthPx * 0.2f) {
                                val dir = if (o < 0f) -1f else 1f
                                swipeSwitch[0] = true
                                try {
                                    offset.animateTo(dir * widthPx, tween(fxMs(130)))
                                    Haptics.click()
                                    if (dir < 0f) vm.next() else vm.skipPrevious()
                                    offset.snapTo(-dir * widthPx * 0.4f)
                                    offset.animateTo(0f, fxSpring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                                } finally {
                                    swipeSwitch[0] = false
                                }
                            } else {
                                offset.animateTo(0f, fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, drag ->
                    change.consume()
                    val moved = offset.value + drag
                    val step = swipeTickStep(moved, widthPx)
                    if (step != lastTick) { lastTick = step; Haptics.tick() }
                    scope.launch { offset.snapTo(moved) }
                }
            },
    ) {
        // Нажатие висит внутри Surface: рябь обрезается формой мини-плеера и не вылезает прямоугольником за скругление.
        Column(
            Modifier.fillMaxWidth().clickable { sheet.open() }.drawBehind {
                if (showFill) drawRect(progressColor, size = Size(size.width * progress, size.height))
            },
        ) {
            // Не поместившаяся кнопка переносится на вторую строку мини-плеера, а не наезжает на соседнюю.
            WrapRow(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = if (!linearBar) 8.dp else 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Блоки мини-плеера (обложка, текст, кнопки) идут в порядке из редактора интерфейса. Обложка и текст «уезжают»
                // за пальцем при свайпе; при смене трека кнопками / автопереходом «выпрыгивают» сбоку пружиной
                // (при свайпе анимация не нужна — там трек уже приехал за пальцем).
                val trackSpec: AnimatedContentTransitionScope<Track>.() -> ContentTransform = {
                    if (swipeSwitch[0]) {
                        androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                    } else {
                        val sgn = if (targetState.id == dirMemo[0]) 1 else -1
                        (slideInHorizontally(fxSpring(dampingRatio = 0.62f, stiffness = 420f)) { it * sgn / 2 } +
                            fadeIn(tween(fxMs(160))) +
                            scaleIn(fxSpring(dampingRatio = 0.62f, stiffness = 420f), initialScale = 0.82f)) togetherWith
                            (slideOutHorizontally(tween(fxMs(180))) { -it * sgn / 3 } + fadeOut(tween(fxMs(120))))
                    }
                }
                val miniCorner by animateDpAsState(
                    targetValue = if (s.isPlaying) 14.dp else 24.dp,
                    animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "miniCorner",
                )
                // Кнопка ровно по центру и того же размера, что обложка: системный минимум 48 dp отключён,
                // иначе кнопка в 40 dp «съезжала» внутри круга прогресса и выбивалась из ряда.
                val playButton: @Composable () -> Unit = {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        FilledIconButton(
                            onClick = { vm.togglePlay() },
                            haptic = HapticKind.Heavy,
                            modifier = Modifier.size(if (style.circular) MiniControlSize - 12.dp else MiniControlSize),
                            shape = cornerShape(miniCorner),
                        ) {
                            LoadingDotsMorph(loading = s.isBuffering, size = 24.dp) {
                                Crossfade(targetState = s.isPlaying, animationSpec = tween(fxMs(200)), label = "miniPlayPause") { playing ->
                                    Icon(
                                        painterResource(if (playing) R.drawable.ms_pause else R.drawable.ms_play_arrow),
                                        contentDescription = if (playing) "Пауза" else "Играть",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                val miniOrder = UiLayout.order(LScreen.MINI, MiniBlock.entries)
                miniOrder.forEach { blk ->
                    if (UiLayout.isHidden(LScreen.MINI, blk)) return@forEach
                    if (blk == MiniBlock.Like && t.isLocal) return@forEach
                    val bz = UiLayout.zoom(LScreen.MINI, blk)
                    val slot = Modifier.padding(
                        start = UiLayout.padH(LScreen.MINI, blk).dp, top = UiLayout.gap(LScreen.MINI, blk).dp,
                        end = UiLayout.padH(LScreen.MINI, blk).dp, bottom = UiLayout.gapBottom(LScreen.MINI, blk).dp,
                    ).layoutEdit(LScreen.MINI, blk)
                    val miniSpan = UiLayout.span(LScreen.MINI, blk).coerceAtLeast(1)
                    when (blk) {
                        MiniBlock.Cover -> Box(
                            slot.graphicsLayer {
                                translationX = offset.value
                                alpha = (1f - kotlin.math.abs(offset.value) / widthPx * 0.9f).coerceIn(0f, 1f)
                                horizontalMotionBlur(offset.velocity)
                            },
                        ) {
                            ZoomBox(bz) {
                                AnimatedContent(
                                    targetState = t,
                                    contentKey = { it.id },
                                    transitionSpec = trackSpec,
                                    label = "miniCover",
                                ) { tr ->
                                    Cover(model = tr.cover(160), modifier = Modifier.size(MiniControlSize).clip(rounded(14)))
                                }
                            }
                        }
                        MiniBlock.Info -> Box(
                            slot.wrapFill().graphicsLayer {
                                translationX = offset.value
                                alpha = (1f - kotlin.math.abs(offset.value) / widthPx * 0.9f).coerceIn(0f, 1f)
                                horizontalMotionBlur(offset.velocity)
                            },
                        ) {
                            ZoomBox(bz) {
                                AnimatedContent(
                                    targetState = t,
                                    contentKey = { it.id },
                                    transitionSpec = trackSpec,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = "miniTrack",
                                ) { tr ->
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                tr.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (tr.explicit) { Spacer(Modifier.width(5.dp)); ExplicitMark(size = 13) }
                                        }
                                        Text(tr.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        MiniBlock.Prev -> Box(slot) {
                            ZoomBox(bz) {
                                SpanBox(miniSpan, 40.dp) {
                                    IconButton(onClick = { vm.previous() }, modifier = Modifier.size(40.dp)) {
                                        Icon(painterResource(R.drawable.ms_skip_previous), contentDescription = "Предыдущий трек", modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                        MiniBlock.Next -> Box(slot) {
                            ZoomBox(bz) {
                                SpanBox(miniSpan, 40.dp) {
                                    IconButton(onClick = { vm.next() }, modifier = Modifier.size(40.dp)) {
                                        Icon(painterResource(R.drawable.ms_skip_next), contentDescription = "Следующий трек", modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                        MiniBlock.Like -> Box(slot) {
                            ZoomBox(bz) {
                                val miniLiked = s.liked.any { it.id == t.id }
                                SpanBox(miniSpan, 40.dp) {
                                    IconButton(onClick = { vm.toggleLike(t) }, modifier = Modifier.size(40.dp)) {
                                        Icon(
                                            painterResource(if (miniLiked) R.drawable.ms_favorite else R.drawable.ms_favorite_border),
                                            contentDescription = "Нравится",
                                            tint = if (miniLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }
                        MiniBlock.Play -> Box(slot) {
                            ZoomBox(bz) {
                        if (style.circular) {
                            if (mode == MiniSlider.Seek) {
                                M3CircularSeek(
                                    value = seek.shown, onValueChange = seek.onChange, onValueChangeFinished = seek.onFinish, haptic = true,
                                    size = miniRingSize(style.wavy), wavy = style.wavy, thickness = 3.dp, amplitude = seek.amp,
                                    ringBand = 8.dp, waveAmplitude = MiniWaveAmp,
                                ) { playButton() }
                            } else {
                                M3CircularBar(progress = progress, size = miniRingSize(style.wavy), wavy = style.wavy, thickness = 3.dp, amplitude = if (s.isPlaying) 1f else 0f, waveAmplitude = MiniWaveAmp) { playButton() }
                            }
                        } else playButton()
                            }
                        }
                    }
                }
                // Без блока «Название и исполнитель» свободное место всё равно остаётся справа от кнопок.
                if (UiLayout.isHidden(LScreen.MINI, MiniBlock.Info)) Spacer(Modifier.wrapFill())
            }
            if (linearBar) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mode == MiniSlider.Seek) {
                        M3SeekBar(
                            value = seek.shown, onValueChange = seek.onChange, onValueChangeFinished = seek.onFinish, haptic = true,
                            modifier = Modifier.weight(1f), wavy = style.wavy, thickness = style.thickness.dp,
                            amplitude = seek.amp, minTouchHeight = 28.dp,
                        )
                    } else {
                        M3SeekBar(
                            value = vol.value, onValueChange = { vol.set(it) }, onValueChangeFinished = { vol.finish() },
                            modifier = Modifier.weight(1f), wavy = style.wavy, thickness = style.thickness.dp,
                            amplitude = if (s.isPlaying) 1f else 0f, minTouchHeight = 28.dp,
                        )
                    }
                    if (seekOn && volOn) {
                        IconButton(
                            onClick = { pick = if (mode == MiniSlider.Seek) MiniSlider.Volume else MiniSlider.Seek },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                painterResource(if (mode == MiniSlider.Seek) R.drawable.ms_schedule else R.drawable.ms_volume_up),
                                contentDescription = if (mode == MiniSlider.Seek) "Сейчас: перемотка. Переключить на громкость" else "Сейчас: громкость. Переключить на перемотку",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- Полный плеер ----------

/** Состояние перемотки: плавная позиция из плеера, перетаскивание не дёргает трек до отпускания. */
internal class SeekUi(
    val shown: Float,
    val dur: Long,
    val amp: Float,
    val onChange: (Float) -> Unit,
    val onFinish: () -> Unit,
)

@Composable
internal fun rememberSeek(vm: AppViewModel, trackId: String, durationMs: Long, isPlaying: Boolean): SeekUi {
    val dur = durationMs.coerceAtLeast(1)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var live by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(trackId, dur, isPlaying, dragging) {
        live = (vm.livePosition().toFloat() / dur).coerceIn(0f, 1f)
        if (isPlaying && !dragging) {
            // Раз в ~66 мс (≈15 кадров/с), а не каждый кадр: сдвиг полосы за это время меньше пикселя, зато
            // плеер перерисовывается в 4 раза реже (волна анимируется отдельно и остаётся плавной).
            var last = 0L
            while (true) {
                val now = withFrameMillis { it }
                if (now - last < 66L) continue
                last = now
                live = (vm.livePosition().toFloat() / dur).coerceIn(0f, 1f)
            }
        }
    }
    val waveAmp by animateFloatAsState(
        targetValue = if (isPlaying && !dragging) 1f else 0f,
        animationSpec = fxSpring(stiffness = Spring.StiffnessLow),
        label = "seekWave",
    )
    return SeekUi(
        shown = if (dragging) dragValue else live,
        dur = dur,
        amp = waveAmp,
        onChange = { dragging = true; dragValue = it },
        onFinish = {
            vm.seekTo((dragValue * dur).toLong())
            live = dragValue
            dragging = false
        },
    )
}

@Composable
internal fun SeekTimes(seek: SeekUi, durationMs: Long) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(seekTime((seek.shown * seek.dur).toLong()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(seekTime(durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Память анимации смены трека в [PlayerCover]: какой трек был, какой следовал за ним, направление и сила эффекта. */
private class SwitchMemo {
    var id: String? = null
    var nextId: String? = null
    var bySwipe = false
    var dir = 1
    var travel = 0f
    var wobble = 0f
    var fade = false
    /** Куда провернулось «кольцо» обложек при смене трека не свайпом: 1 — вперёд, -1 — назад, 0 — не крутили. */
    var ringDir = 0
}

/**
 * Обложка подстраивается под свободное место: плеер целиком помещается на любом экране.
 * Свайп влево/вправо по обложке переключает трек (как в Яндекс Музыке): обложка уезжает за пальцем, а соседние
 * треки выглядывают по бокам. Нажатие открывает обложку крупно — в том же окне, что и фото исполнителя,
 * с кнопкой «Скачать» внизу. К альбому ведёт его название ниже.
 */
@Composable
private fun PlayerCover(
    vm: AppViewModel, t: Track, isPlaying: Boolean, modifier: Modifier, clipCovers: Boolean = false,
    /** Кнопка «Волна по…» в углу обложки; null — не показывать. */
    onWaveFrom: (() -> Unit)? = null,
) {
    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "coverScale",
    )
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCover by remember(t.id) { mutableStateOf(false) }
    val bigCover = t.cover(1000)
    val saveName = listOf(t.artists, t.title).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Обложка" }

    val offset = remember { Animatable(0f) }
    // Во время переключения держим «снимок» трёх обложек, чтобы после смены трека не мигнул лишний кадр.
    var frozen by remember { mutableStateOf<Triple<Track?, Track, Track?>?>(null) }
    val curId by rememberUpdatedState(t.id)
    val glowColor = rememberGlowColor(t.cover(160))
    val live = remember(t.id, isPlaying) { Triple(vm.neighborTrack(false), t, vm.neighborTrack(true)) }
    val trio = frozen ?: live
    val trioNow by rememberUpdatedState(trio)

    // «Кольцо» обложек: при смене трека кнопкой (или из уведомления/виджета) обложки не появляются из ниоткуда, а
    // проворачиваются на шаг по кругу — так же, как при свайпе. rot измеряется в шагах между обложками и бежит к 0.
    // Выставляем его прямо в композиции, чтобы первый кадр с новым треком уже стоял «в начале» поворота (без мигания).
    var rot by remember { mutableFloatStateOf(0f) }
    val memo = remember { SwitchMemo() }
    remember(t.id) {
        val old = memo.id
        val dir = when {
            old == null || old == t.id || memo.bySwipe -> 0
            live.first?.id == old -> 1   // ушли вперёд: прежняя обложка теперь слева
            live.third?.id == old -> -1  // ушли назад: прежняя обложка теперь справа
            else -> 0                    // прыжок на далёкий трек — старая «влетающая» анимация
        }
        memo.ringDir = dir
        if (dir != 0) rot = (rot + dir).coerceIn(-1f, 1f)
        dir
    }

    // Анимация смены трека: новая обложка «влетает» с поворотом и пружинным отскоком. Если трек сменили свайпом —
    // обложка уже приехала за пальцем, поэтому там только лёгкий «толчок» масштаба.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(t.id) {
        val oldId = memo.id
        val fwd = t.id == memo.nextId
        memo.id = t.id
        memo.nextId = vm.neighborTrack(true)?.id
        if (oldId == null || oldId == t.id) return@LaunchedEffect
        val bySwipe = memo.bySwipe
        val ringed = memo.ringDir != 0
        memo.bySwipe = false
        memo.dir = if (fwd) 1 else -1
        // Свайп и поворот кольца уже сами привезли обложку — «влёт» с поворотом нужен только при прыжке на далёкий трек.
        val soft = bySwipe || ringed
        memo.travel = if (soft) 0f else 0.55f
        memo.wobble = if (soft) 0f else 14f
        memo.fade = !soft
        if (ringed) launch {
            androidx.compose.animation.core.animate(
                initialValue = rot, targetValue = 0f,
                animationSpec = fxSpring(dampingRatio = 0.8f, stiffness = 260f),
            ) { v, _ -> rot = v }
        }
        pop.snapTo(if (bySwipe) 0.35f else if (ringed) 0.6f else 0f)
        pop.animateTo(1f, fxSpring(dampingRatio = 0.52f, stiffness = 170f))
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val raw = if (maxWidth < maxHeight) maxWidth else maxHeight
        val side = if (raw > 420.dp) 420.dp else raw
        val density = LocalDensity.current
        val sidePx = with(density) { side.toPx() }
        val stepPx = sidePx + with(density) { 20.dp.toPx() }
        val corner = if (side < 180.dp) 24 else 36
        if (side >= 72.dp) {
            Box(
                Modifier.fillMaxSize().pointerInput(side) {
                    // Вибро-тики при пролистывании обложки — по шагам, как у ползунка перемотки.
                    var lastTick = 0
                    detectHorizontalDragGestures(
                        onDragStart = { lastTick = 0; scope.launch { offset.stop() } },
                        onDragEnd = {
                            val o = offset.value
                            val (prev, _, next) = trioNow
                            val thr = sidePx * 0.22f
                            val dir = when {
                                o < -thr && next != null -> -1
                                o > thr && prev != null -> 1
                                else -> 0
                            }
                            scope.launch {
                                if (dir == 0) {
                                    offset.animateTo(0f, fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                } else {
                                    frozen = trioNow
                                    val startId = curId
                                    offset.animateTo(dir * stepPx, tween(fxMs(170)))
                                    Haptics.click()
                                    memo.bySwipe = true
                                    if (dir < 0) vm.next() else vm.skipPrevious()
                                    kotlinx.coroutines.withTimeoutOrNull(700) {
                                        snapshotFlow { curId }.first { it != startId }
                                    }
                                    offset.snapTo(0f)
                                    frozen = null
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, drag ->
                        change.consume()
                        val (prev, _, next) = trioNow
                        val target = offset.value + drag
                        // Если в эту сторону трека нет — обложка «тянется» с сопротивлением и возвращается.
                        val blocked = (target < 0 && next == null) || (target > 0 && prev == null)
                        val moved = if (blocked) offset.value + drag * 0.3f else target
                        val step = swipeTickStep(moved, sidePx)
                        if (step != lastTick) { lastTick = step; Haptics.tick() }
                        scope.launch { offset.snapTo(moved) }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                // Свечение рисуется отдельно от обложек и вне их обрезки: в альбомной ориентации соседние обложки
                // прячутся по краю панели, а свет должен выходить за него и не упираться в рамки.
                Spacer(
                    Modifier.size(side)
                        .graphicsLayer {
                            translationX = offset.value
                            val near = if (rot == 0f) {
                                (1f - kotlin.math.abs(offset.value) / stepPx).coerceIn(0f, 1f)
                            } else {
                                // Идёт поворот кольца: свет остаётся по центру и «дышит» между уходящей и приходящей обложкой.
                                val u = offset.value / stepPx + rot
                                (1f - 2f * kotlin.math.abs(u - Math.round(u))).coerceIn(0f, 1f)
                            }
                            val sc = coverScale * (0.9f + 0.1f * near)
                            scaleX = sc; scaleY = sc
                        }
                        .playGlow(
                            active = isPlaying, enabled = ThemeSettings.glowPlayer,
                            color = glowColor,
                            cornerRadius = corner.dp, spread = 44.dp, strength = 0.7f,
                            shape = rounded(corner),
                        ),
                )
                Box(
                    Modifier.fillMaxSize().then(if (clipCovers) Modifier.clipToBounds() else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    @Composable
                    fun Slot(track: Track?, k: Int, onTop: Boolean) {
                        if (track == null) return
                        Cover(
                            model = track.cover(600),
                            modifier = Modifier.size(side)
                                .graphicsLayer {
                                    val x = offset.value + (rot + k) * stepPx
                                    translationX = x
                                    val near = (1f - kotlin.math.abs(x) / stepPx).coerceIn(0f, 1f)
                                    // Кольцо: обложка, уходящая вбок, «заезжает за спину» — уменьшается, приподнимается
                                    // и тускнеет, а приходящая так же выезжает вперёд. Центральная всегда сверху.
                                    translationY = -(1f - near) * sidePx * 0.07f
                                    // Соседние обложки видны только пока их тянут или проворачивают: в покое они полностью
                                    // прозрачны и не выглядывают по бокам полосами.
                                    alpha = if (onTop) 0.45f + 0.55f * near else near * near
                                    var sc = coverScale * (0.8f + 0.2f * near)
                                    if (onTop) {
                                        // «Прикольная» анимка смены трека (pop идёт 0 → 1 с перелётом).
                                        val p = pop.value
                                        translationX = x + (1f - p) * memo.travel * sidePx * memo.dir
                                        rotationZ = (1f - p) * memo.wobble * memo.dir
                                        sc *= 0.78f + 0.22f * p
                                        if (memo.fade) alpha = p.coerceIn(0f, 1f)
                                    }
                                    scaleX = sc; scaleY = sc
                                    horizontalMotionBlur(offset.velocity, maxPx = 32f)
                                }
                                .clip(rounded(corner))
                                .then(
                                    if (onTop && bigCover != null && frozen == null) Modifier.pointerInput(bigCover) {
                                        detectTapGestures(onTap = { showCover = true })
                                    } else Modifier
                                ),
                        )
                    }
                    Slot(trio.first, -1, false)
                    Slot(trio.third, 1, false)
                    Slot(trio.second, 0, true)
                }
                // Кнопка «Волна по…» в углу обложки: при свайпе гаснет, чтобы не мешать.
                if (onWaveFrom != null && frozen == null) {
                    Box(
                        Modifier.size(side).graphicsLayer { scaleX = coverScale; scaleY = coverScale },
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        FilledTonalIconButton(
                            onClick = onWaveFrom,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.padding(10.dp).size(44.dp)
                                .graphicsLayer { alpha = (1f - kotlin.math.abs(offset.value) / (stepPx * 0.25f)).coerceIn(0f, 1f) },
                        ) {
                            Icon(painterResource(R.drawable.ms_graphic_eq), contentDescription = "Включить волну по этому треку, исполнителю, альбому или плейлисту")
                        }
                    }
                }
            }
        }
    }
    if (showCover && bigCover != null) {
        ImageViewerDialog(
            images = listOf(bigCover),
            fileName = saveName,
            onDismiss = { showCover = false },
        )
    }
}

/** Название, исполнители (нажатие открывает исполнителя) и альбом (нажатие открывает альбом). */
@Composable
private fun PlayerTrackInfo(vm: AppViewModel, t: Track, onNav: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                t.title, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f, fill = false),
            )
            if (t.explicit) { Spacer(Modifier.width(8.dp)); ExplicitMark(size = 18) }
        }
        // Приписка к названию («минус», «a.k.a. …»): нажатие открывает информацию о треке.
        t.version?.let { v ->
            val showInfo = LocalShowTrackInfo.current
            Text(
                v, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp).widthIn(max = 320.dp)
                    .clip(textShape(6)).clickable { showInfo(t) }.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(2.dp))
        if (t.artistRefs.isEmpty()) {
            Text(
                t.artists, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                items(t.artistRefs) { ref ->
                    val chipPress = rememberPress(true)
                    AssistChip(
                        onClick = { vm.openArtist(ref); onNav() },
                        label = { Text(ref.name, maxLines = 1) },
                        modifier = Modifier.pressScale(chipPress, 0.06f),
                        interactionSource = chipPress.source,
                    )
                }
            }
        }
        val album = t.albumTitle?.takeIf { it.isNotBlank() }
        if (album != null) {
            // Альбом — выразительная «таблетка» Material 3: тональный фон, значок, название и стрелка перехода.
            val albumId = t.albumId
            // Файл с устройства: нажатие открывает его альбом среди файлов (режим «Локальные файлы»).
            val deviceAlbum = albumId == null && t.isLocal && LocalModeSettings.enabled
            val shape = containerShape(20)
            Row(
                Modifier.padding(top = 10.dp).widthIn(max = 340.dp)
                    .then(
                        if (albumId != null) Modifier.pressable(shape) { vm.openAlbumAtTrack(albumId, t.albumTitle, t.id); onNav() }
                        else if (deviceAlbum) Modifier.pressable(shape) { vm.requestLocalNav("album-of:" + t.id); onNav() }
                        else Modifier.clip(shape)
                    )
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(start = 12.dp, end = if (albumId != null || deviceAlbum) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ms_library_music), contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    album,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (albumId != null || deviceAlbum) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        painterResource(R.drawable.ms_chevron_right), contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(vm: AppViewModel, isPlaying: Boolean, seek: SeekUi, style: SeekBarStyle) {
    val buffering = vm.state.collectAsStateWithLifecycle().value.isBuffering
    val playCorner by animateDpAsState(
        targetValue = if (isPlaying) 24.dp else 36.dp,
        animationSpec = fxSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "playCorner",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ms_skip_previous), contentDescription = "Предыдущий трек")
        }
        val playButton: @Composable () -> Unit = {
            FilledIconButton(
                onClick = { vm.togglePlay() },
                haptic = HapticKind.Heavy,
                modifier = Modifier.size(if (style.circular) 64.dp else 72.dp),
                shape = cornerShape(playCorner),
            ) {
                LoadingDotsMorph(loading = buffering, size = 32.dp) {
                    Crossfade(targetState = isPlaying, animationSpec = tween(fxMs(200)), label = "playPause") { playing ->
                        Icon(
                            painterResource(if (playing) R.drawable.ms_pause else R.drawable.ms_play_arrow),
                            contentDescription = if (playing) "Пауза" else "Играть",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
        if (style.circular) {
            // Круговой индикатор вокруг кнопки: по кольцу можно перематывать
            M3CircularSeek(
                value = seek.shown, onValueChange = seek.onChange, onValueChangeFinished = seek.onFinish, haptic = true,
                size = maxOf(92.dp, circularRingSize(64.dp, style.thickness.dp, style.wavy)),
                wavy = style.wavy, thickness = style.thickness.dp, amplitude = seek.amp,
            ) { playButton() }
        } else playButton()
        FilledTonalIconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
            Icon(painterResource(R.drawable.ms_skip_next), contentDescription = "Следующий трек")
        }
    }
}

@Composable
private fun PlayerBottomRow(vm: AppViewModel, t: Track, lyricsOn: Boolean, onLyrics: () -> Unit, onOutput: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val isLiked = s.liked.any { it.id == t.id }
    val isDisliked = t.id in s.dislikedIds
    // Порядок, видимость и размер кнопок задаются в редакторе интерфейса (вкладка «Плеер»).
    val btnOrder = UiLayout.order(LScreen.PLAYER, PlayerBtn.entries)
    // Кнопки, которые не помещаются (растянули или увеличили), переносятся на следующую строку, а не наезжают на соседей.
    WrapRow(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        btnOrder.forEach { btn ->
            if (UiLayout.isHidden(LScreen.PLAYER, btn)) return@forEach
            if ((btn == PlayerBtn.Dislike || btn == PlayerBtn.Like) && t.isLocal) return@forEach
            if (btn == PlayerBtn.Gap) { Spacer(Modifier.wrapFill().layoutEdit(LScreen.PLAYER, btn)); return@forEach }
            ZoomBox(UiLayout.playerBtnZoom * UiLayout.zoom(LScreen.PLAYER, btn)) {
              Box(Modifier.layoutEdit(LScreen.PLAYER, btn)) {
              SpanBox(UiLayout.span(LScreen.PLAYER, btn).coerceAtLeast(1), 48.dp) {
                when (btn) {
                    PlayerBtn.Output -> {
            IconButton(onClick = onOutput) {
                Icon(painterResource(R.drawable.ms_volume_up), contentDescription = "Вывод звука")
            }
                    }
                    PlayerBtn.More -> {
            if (!t.isLocal) {
                // «⋮»: скачать любой трек (не только из лайков), информация о треке, добавить в плейлист.
                val dl by vm.downloads.collectAsStateWithLifecycle()
                val downloaded = dl.items.any { it.id == t.id }
                val progress = dl.progress[t.id]
                val addToPlaylist = LocalAddToPlaylist.current
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        if (progress != null) {
                            if (progress >= 0f) CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(28.dp))
                            else LoadingIndicator(Modifier.size(28.dp))
                        } else {
                            Icon(
                                painterResource(R.drawable.ms_more_vert),
                                contentDescription = "Ещё",
                                tint = if (downloaded) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Добавить в плейлист") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_add), null) },
                            onClick = { menu = false; addToPlaylist(t) },
                        )
                        MixNextMenuItem(vm) { menu = false }
                        TrackActionItems(t) { menu = false }
                    }
                }
            }
            if (t.isLocal) {
                // У файла с устройства меню короче: свой исполнитель и обложка, отправить файл.
                val editLocal = LocalEditLocalTrack.current
                val localCtx = LocalContext.current
                var lmenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { lmenu = true }) {
                        Icon(painterResource(R.drawable.ms_more_vert), contentDescription = "Ещё")
                    }
                    DropdownMenu(expanded = lmenu, onDismissRequest = { lmenu = false }) {
                        MixNextMenuItem(vm) { lmenu = false }
                        DropdownMenuItem(
                            text = { Text("Изменить название, исполнителя и обложку") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_edit), null) },
                            onClick = { lmenu = false; editLocal(t) },
                        )
                        DropdownMenuItem(
                            text = { Text("Поделиться файлом") },
                            leadingIcon = { Icon(painterResource(R.drawable.ms_share), null) },
                            onClick = { lmenu = false; ShareLinks.shareFile(localCtx, t) },
                        )
                    }
                }
            }
                    }
                    PlayerBtn.Repeat -> {
            // Пока играет «Моя волна», на месте «Повтора» (для бесконечной волны он не нужен) стоит «Встряхнуть»:
            // так в ряду остаётся столько же кнопок, и плеер не растягивается и не прокручивается.
            Crossfade(targetState = s.waveOn, animationSpec = tween(fxMs(200)), label = "repeatOrShake") { wave ->
                if (wave) {
                    IconButton(onClick = { vm.restartWave() }, enabled = !s.waveLoading) {
                        if (s.waveLoading) LoadingIndicator(Modifier.size(28.dp))
                        else Icon(painterResource(R.drawable.ms_refresh), contentDescription = if (ActiveProvider.isVk) "Встряхнуть VK Микс" else if (ActiveProvider.isSc) "Встряхнуть волну" else "Встряхнуть Мою волну")
                    }
                } else {
                val repeatOn = s.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF
                IconToggleButton(checked = repeatOn, onCheckedChange = { vm.cycleRepeat() }) {
                    Icon(
                        painterResource(if (s.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) R.drawable.ms_repeat_one else R.drawable.ms_repeat),
                        contentDescription = when (s.repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_ONE -> "Повтор: один трек"
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> "Повтор: весь список"
                            else -> "Повтор выключен"
                        },
                        tint = if (repeatOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                }
            }
                    }
                    PlayerBtn.Lyrics -> {
            IconToggleButton(checked = lyricsOn, onCheckedChange = { onLyrics() }) {
                Icon(
                    painterResource(R.drawable.ms_lyrics),
                    contentDescription = if (lyricsOn) "Скрыть текст песни" else "Текст песни",
                    tint = if (lyricsOn) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
                    }
                    PlayerBtn.Mix -> {
            IconButton(onClick = { MixSession.openFromPlayer(t) }) {
                Icon(
                    painterResource(R.drawable.ms_graphic_eq), contentDescription = "Микс треков",
                    tint = if (MixSession.isActiveFor(t)) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
                    }
                    PlayerBtn.Dislike -> if (ActiveProvider.caps.dislikes) {
            IconToggleButton(checked = isDisliked, onCheckedChange = { vm.toggleDislike(t) }) {
                Icon(
                    painterResource(if (isDisliked) R.drawable.ms_thumb_down else R.drawable.ms_thumb_down_outline),
                    contentDescription = "Не нравится",
                    tint = if (isDisliked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
                    }
                    PlayerBtn.Like -> {
            IconToggleButton(checked = isLiked, onCheckedChange = { vm.toggleLike(t) }) {
                Icon(
                    painterResource(if (isLiked) R.drawable.ms_favorite else R.drawable.ms_favorite_border),
                    contentDescription = "Нравится",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                )
            }
                    }
                    PlayerBtn.Gap -> Unit
                }
              }
              }
            }
        }
    }
}

/**
 * Полный плеер без прокрутки: обложка занимает всё свободное место (сжимается на маленьких экранах),
 * остальное — компактный блок управления. В альбомной ориентации обложка слева, управление справа.
 */
@Composable
private fun FullPlayer(
    vm: AppViewModel,
    landscape: Boolean,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    showOutput: Boolean,
    setShowOutput: (Boolean) -> Unit,
    onArtistOpened: () -> Unit = {},
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val t = s.nowPlaying ?: return
    val seek = rememberSeek(vm, t.id, s.durationMs, s.isPlaying)
    val style = ThemeSettings.seekStyle
    var lyricsFull by remember { mutableStateOf(false) }
    var showWaveFrom by remember { mutableStateOf(false) }
    // Выбранный вручную источник текста действует и на панели, и на весь экран; при смене трека сбрасывается.
    var lyricsPick by remember(t.id) { mutableStateOf<String?>(null) }

    // На месте обложки — либо она сама (со свайпом для смены трека), либо текст песни.
    @Composable
    fun CoverOrLyrics(modifier: Modifier, clipCovers: Boolean = false) {
        // Обложка остаётся в композиции всё время и лишь гаснет, а текст кладётся поверх в тех же границах. Раньше здесь был
        // Crossfade: он пересоздавал обложку и на секунду мерил её по размеру блока текста — отсюда обрезка при открытии текста.
        val lyricsA by animateFloatAsState(if (showLyrics) 1f else 0f, tween(fxMs(220)), label = "coverLyrics")
        Box(modifier) {
            PlayerCover(
                vm, t, s.isPlaying,
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - lyricsA; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha },
                clipCovers,
                onWaveFrom = if (s.loggedIn && !t.isLocal && !showLyrics) ({ showWaveFrom = true }) else null,
            )
            if (showLyrics || lyricsA > 0.001f) {
                // pointerInput не даёт касаниям (свайп по пустым местам текста) проваливаться к обложке под ним и менять трек.
                LyricsPanel(
                    vm, t, s.isPlaying, lyricsPick, { lyricsPick = it }, { lyricsFull = true },
                    Modifier.fillMaxSize().graphicsLayer { alpha = lyricsA }.pointerInput(Unit) { detectTapGestures { } },
                )
            }
        }
    }

    // Части плеера (обложка, название, перемотка, управление, громкость, нижний ряд кнопок): порядок, видимость,
    // размер и отступы — в редакторе интерфейса (вкладка «Плеер»).
    val partOrder = UiLayout.order(LScreen.PLAYER_FULL, PlayerPart.entries)

    @Composable
    fun PartContent(p: PlayerPart) {
        when (p) {
            PlayerPart.Info -> {
                // При смене трека название и исполнители «выпрыгивают» снизу, а старые уезжают вверх.
                AnimatedContent(
                    targetState = t,
                    contentKey = { it.id },
                    transitionSpec = {
                        (slideInVertically(fxSpring(dampingRatio = 0.7f, stiffness = 380f)) { it / 2 } +
                            fadeIn(tween(fxMs(220), delayMillis = 50)) +
                            scaleIn(fxSpring(dampingRatio = 0.7f, stiffness = 380f), initialScale = 0.92f)) togetherWith
                            (slideOutVertically(tween(fxMs(160))) { -it / 3 } + fadeOut(tween(fxMs(120)))) using
                            SizeTransform(clip = false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = "playerTrackInfo",
                ) { tr -> PlayerTrackInfo(vm, tr, onArtistOpened) }
            }
            PlayerPart.Seek -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!style.circular) {
                        M3SeekBar(
                            value = seek.shown, onValueChange = seek.onChange, onValueChangeFinished = seek.onFinish, haptic = true,
                            wavy = style.wavy, thickness = style.thickness.dp, amplitude = seek.amp,
                        )
                    }
                    SeekTimes(seek, s.durationMs)
                }
            }
            PlayerPart.Controls -> PlayerControls(vm, s.isPlaying, seek, style)
            PlayerPart.Volume -> PlayerVolumeRow(vm, s.isPlaying, style)
            PlayerPart.Buttons -> PlayerBottomRow(vm, t, lyricsOn = showLyrics, onLyrics = onToggleLyrics) { setShowOutput(true) }
            PlayerPart.Cover -> Unit
        }
    }

    // Одна часть управления с отступом сверху «как было» и размером / отступами из редактора.
    @Composable
    fun PartSlot(p: PlayerPart) {
        if (p == PlayerPart.Cover) return
        if (UiLayout.isHidden(LScreen.PLAYER_FULL, p)) return
        // В альбомной ориентации нижний ряд кнопок перенесён в шапку (см. PlayerSheet).
        if (p == PlayerPart.Buttons && landscape) return
        if (p == PlayerPart.Volume && !PlayerSettings.fullVolume) return
        val top = when (p) {
            PlayerPart.Seek -> 6
            PlayerPart.Controls -> 4
            PlayerPart.Volume -> 2
            PlayerPart.Buttons -> 4
            else -> 0
        }
        Spacer(Modifier.height(top.dp))
        LayoutBlockBox(LScreen.PLAYER_FULL, p) { PartContent(p) }
    }

    @Composable
    fun Controls(modifier: Modifier) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            partOrder.forEach { p -> PartSlot(p) }
        }
    }

    val coverHidden = UiLayout.isHidden(LScreen.PLAYER_FULL, PlayerPart.Cover)

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = UiStyle.playerSide.dp)) {
        if (landscape) {
            val widePlayer = LocalWide.current
            Row(
                Modifier.fillMaxSize().padding(horizontal = if (widePlayer) 16.dp else 0.dp, vertical = if (widePlayer) 4.dp else 0.dp).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (widePlayer) 40.dp else 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Обложка слева, если в редакторе она стоит выше блока «Кнопки управления», иначе справа.
                val coverFirst = partOrder.indexOf(PlayerPart.Cover) < partOrder.indexOf(PlayerPart.Controls)

                @Composable
                fun CoverSlot() {
                    // Соседние обложки при свайпе выезжают за край и прячутся за ним, а не заходят под элементы управления.
                    if (!coverHidden) CoverOrLyrics(Modifier.weight(1f).fillMaxHeight().layoutEdit(LScreen.PLAYER_FULL, PlayerPart.Cover), clipCovers = true)
                }
                if (coverFirst) CoverSlot()
                // Блок управления никогда не прокручивается: если он выше экрана (низкая альбомная ориентация),
                // он сам немного уменьшается и по центру помещается целиком.
                androidx.compose.ui.layout.Layout(
                    content = { Controls(Modifier) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { measurables, constraints ->
                    val p = measurables.first().measure(
                        constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity),
                    )
                    val sc = if (p.height > constraints.maxHeight) constraints.maxHeight.toFloat() / p.height else 1f
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        p.placeWithLayer(
                            (constraints.maxWidth - p.width) / 2,
                            (constraints.maxHeight - p.height) / 2,
                        ) { scaleX = sc; scaleY = sc }
                    }
                }
                if (!coverFirst) CoverSlot()
            }
        } else {
            // На планшете в портретной ориентации плеер не растягивается на весь экран: колонка не шире 640 dp, по центру.
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = if (LocalWide.current) 640.dp else Dp.Infinity).fillMaxSize().padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Без обложки управление уходит вниз, как и раньше.
                if (coverHidden) Spacer(Modifier.weight(1f))
                partOrder.forEach { p ->
                    if (p == PlayerPart.Cover) {
                        if (!coverHidden) {
                            Box(
                                Modifier.weight(1f).fillMaxWidth()
                                    .padding(
                                        start = UiLayout.padH(LScreen.PLAYER_FULL, p).dp, top = UiLayout.gap(LScreen.PLAYER_FULL, p).dp,
                                        end = UiLayout.padH(LScreen.PLAYER_FULL, p).dp, bottom = UiLayout.gapBottom(LScreen.PLAYER_FULL, p).dp,
                                    ).layoutEdit(LScreen.PLAYER_FULL, p),
                            ) { CoverOrLyrics(Modifier.fillMaxSize()) }
                            Spacer(Modifier.height(8.dp))
                        }
                    } else PartSlot(p)
                }
            }
        }
    }
    if (showOutput) AudioOutputDialog(vm) { setShowOutput(false) }
    if (showWaveFrom) WaveFromSheet(vm, t) { showWaveFrom = false }
    if (lyricsFull) LyricsFullscreen(vm, t, s.isPlaying, s.durationMs, lyricsPick, { lyricsPick = it }) { lyricsFull = false }
}

/**
 * Громкость в полном плеере: значок динамика слева сворачивает и разворачивает ползунок (выбор запоминается),
 * ползунок занимает всю оставшуюся ширину строки и не выходит за рамки плеера. Полностью выключается в Настройки → Плеер.
 */
@Composable
private fun PlayerVolumeRow(vm: AppViewModel, playing: Boolean, style: SeekBarStyle) {
    val vol = rememberVolume(vm, true)
    val open = PlayerSettings.fullVolumeOpen
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { PlayerSettings.updateFullVolumeOpen(!open) }, modifier = Modifier.size(40.dp)) {
            Icon(
                painterResource(R.drawable.ms_volume_up),
                contentDescription = if (open) "Скрыть ползунок громкости" else "Показать ползунок громкости",
                tint = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.weight(1f),
            enter = fadeIn(tween(fxMs(180))) + expandHorizontally(tween(fxMs(220))),
            exit = fadeOut(tween(fxMs(120))) + shrinkHorizontally(tween(fxMs(180))),
        ) {
            M3SeekBar(
                value = vol.value, onValueChange = { vol.set(it) }, onValueChangeFinished = { vol.finish() },
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
                wavy = style.wavy, thickness = style.thickness.dp,
                amplitude = if (playing) 1f else 0f, minTouchHeight = 36.dp,
            )
        }
    }
}

/** Подпись источника на переключателе: «LRCLIB · в такт» — если у текста есть таймкоды. */
private fun LyricsSource.chipLabel(): String {
    val base = when (id) {
        "pin" -> "Закреплён · $name"
        "manual" -> "Вручную · $name"
        else -> name
    }
    return if (isSynced) "$base · в такт" else base
}

/**
 * Источник, который показываем: выбранный на переключателе, затем найденный вручную («manual»),
 * затем привязанный к треку («pin»), иначе первый с таймкодами, иначе первый попавшийся.
 */
private fun pickLyricsSource(sources: List<LyricsSource>, pick: String?): LyricsSource? =
    sources.firstOrNull { it.id == pick } ?: sources.firstOrNull { it.id == "manual" } ?: sources.firstOrNull { it.id == "pin" }
        ?: sources.firstOrNull { it.isSynced } ?: sources.firstOrNull()

@Composable
private fun LyricsSourceChips(
    sources: List<LyricsSource>, selected: String?, onPick: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Отдельная кнопка ручного поиска текста — всегда первая в ряду.
        AssistChip(
            onClick = onSearch,
            label = { Text("Искать") },
            leadingIcon = { Icon(painterResource(R.drawable.ms_search), null, Modifier.size(18.dp)) },
        )
        sources.forEach { s ->
            FilterChip(selected = s.id == selected, onClick = { onPick(s.id) }, label = { Text(s.chipLabel()) })
        }
    }
}

/** Содержимое текста: загрузка, «не найден», строки в такт или обычный текст. [big] — крупный шрифт для полного экрана. */
@Composable
private fun LyricsBody(
    vm: AppViewModel, t: Track, playing: Boolean, cur: LyricsState, src: LyricsSource?, big: Boolean,
    onSearch: () -> Unit, modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        when {
            src == null && !cur.done -> LoadingIndicator(Modifier.align(Alignment.Center))
            src == null -> Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Текст этой песни не найден",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.reloadLyrics(t) }) { Text("Искать снова") }
                TextButton(onClick = onSearch) { Text("Искать самому") }
            }
            src.isSynced -> SyncedLyrics(vm, t.id, src.synced, playing, big)
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    src.plain.orEmpty(),
                    style = (if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
                        .copy(lineHeight = if (big) 34.sp else 28.sp),
                )
            }
        }
    }
}

/**
 * Текст песни на месте обложки. Источников несколько: Яндекс Музыка, LRCLIB, NetEase, Kugou, QQ Music, Genius, lyrics.ovh.
 * Тексты с таймкодами подсвечиваются в такт песне, по строке можно нажать и перемотать. Если текст нашли несколько
 * источников, сверху появляется переключатель; по умолчанию берётся первый с таймкодами. Кнопка справа разворачивает текст на весь экран.
 */
@Composable
private fun LyricsPanel(
    vm: AppViewModel, t: Track, playing: Boolean,
    pick: String?, onPick: (String) -> Unit, onExpand: () -> Unit,
    modifier: Modifier,
) {
    val ly by vm.lyrics.collectAsStateWithLifecycle()
    LaunchedEffect(t.id) { vm.loadLyrics(t) }
    val cur = ly.takeIf { it.trackId == t.id } ?: LyricsState(trackId = t.id, loading = true)
    val src = pickLyricsSource(cur.sources, pick)
    var searching by remember(t.id) { mutableStateOf(false) }
    if (searching) LyricsSearchDialog(vm, t, onDismiss = { searching = false }) { id -> onPick(id); searching = false }
    // Если у плеера свой фон (размытая обложка или матовое стекло), блок текста не закрывает его собственной подложкой.
    val playerHasOwnBg = !ThemeSettings.optimize && (PlayerSettings.playerBgBlur || GlassSettings.playerGlass)
    Surface(
        modifier, shape = containerShape(28),
        color = if (playerHasOwnBg) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                LyricsSourceChips(cur.sources, src?.id, onPick, { searching = true }, Modifier.weight(1f))
                IconButton(onClick = onExpand) {
                    Icon(painterResource(R.drawable.ms_open_in_full), contentDescription = "Текст на весь экран")
                }
            }
            LyricsBody(vm, t, playing, cur, src, big = false, onSearch = { searching = true }, modifier = Modifier.weight(1f))
            if (src != null) {
                LyricsFooter(vm, t, src, Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
            }
        }
    }
}

/**
 * Текст песни на весь экран: крупные строки в такт, сверху — трек и переключатель источников, снизу — перемотка,
 * пауза/воспроизведение и переключение песен. При смене трека текст подгружается сам.
 */
@Composable
private fun LyricsFullscreen(
    vm: AppViewModel, t: Track, playing: Boolean, durationMs: Long,
    pick: String?, onPick: (String) -> Unit, onDismiss: () -> Unit,
) {
    val ly by vm.lyrics.collectAsStateWithLifecycle()
    LaunchedEffect(t.id) { vm.loadLyrics(t) }
    val cur = ly.takeIf { it.trackId == t.id } ?: LyricsState(trackId = t.id, loading = true)
    val src = pickLyricsSource(cur.sources, pick)
    var searching by remember(t.id) { mutableStateOf(false) }
    if (searching) LyricsSearchDialog(vm, t, onDismiss = { searching = false }) { id -> onPick(id); searching = false }
    val seek = rememberSeek(vm, t.id, durationMs, playing)
    val style = ThemeSettings.seekStyle

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Фон: сильно размытая обложка трека (при «Оптимизации» — обычная поверхность), поверх — «матовое стекло» под текстом.
        val glass = !ThemeSettings.optimize
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            if (glass) PlayerBackdrop(t)
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
                val landscape = maxWidth > maxHeight * 1.1f
                val glassMod = if (glass) Modifier
                    .clip(rounded(28))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), rounded(28))
                else Modifier

                @Composable
                fun Header() {
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onDismiss) {
                                Icon(painterResource(R.drawable.ms_expand_more), contentDescription = "Свернуть текст")
                            }
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(t.artists, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        LyricsSourceChips(cur.sources, src?.id, onPick, { searching = true }, Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp))
                    }
                }

                @Composable
                fun Transport(modifier: Modifier) {
                    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!style.circular) {
                            M3SeekBar(
                                value = seek.shown, onValueChange = seek.onChange, onValueChangeFinished = seek.onFinish, haptic = true,
                                wavy = style.wavy, thickness = style.thickness.dp, amplitude = seek.amp,
                            )
                        }
                        SeekTimes(seek, durationMs)
                        Spacer(Modifier.height(4.dp))
                        PlayerControls(vm, playing, seek, style)
                        if (src != null) {
                            LyricsFooter(vm, t, src, Modifier.padding(top = 4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (landscape) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1.4f).fillMaxHeight()) {
                            Header()
                            LyricsBody(vm, t, playing, cur, src, big = true, onSearch = { searching = true }, modifier = Modifier.weight(1f).then(glassMod))
                        }
                        Transport(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).wrapContentHeight(Alignment.CenterVertically))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Header()
                        LyricsBody(vm, t, playing, cur, src, big = true, onSearch = { searching = true }, modifier = Modifier.weight(1f).then(glassMod))
                        Transport(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Подпись «Текст: источник»; у привязанного к треку текста — пометка и кнопка «Открепить». */
@Composable
private fun LyricsFooter(vm: AppViewModel, t: Track, src: LyricsSource, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (src.id == "pin") "Текст: ${src.name} · закреплён за треком" else "Текст: ${src.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (src.id == "pin") {
            TextButton(onClick = { vm.unpinLyrics(t) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Открепить", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Ручной поиск текста. Строка поиска как в поисковике: по мере набора все включённые в настройках источники
 * предлагают варианты — исполнитель, название, источник и есть ли таймкоды («в такт»). Выбор варианта — с окном
 * подтверждения и галочкой «Привязать текст к этому треку» (оба шага отключаются в настройках).
 * [onPicked] получает id источника на переключателе («manual» или «pin»).
 */
@Composable
private fun LyricsSearchDialog(vm: AppViewModel, t: Track, onDismiss: () -> Unit, onPicked: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember { mutableStateMapOf<String, List<LyricsCandidate>>() }
    var pending by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<LyricsCandidate?>(null) }
    var applying by remember { mutableStateOf(false) }
    val searchers = remember { vm.lyricsSearchers() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results.clear(); pending = 0; return@LaunchedEffect }
        delay(350)   // ждём, пока человек допишет
        results.clear()
        pending = searchers.size
        coroutineScope {
            searchers.forEach { s ->
                launch {
                    val r = try { s.search(q) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                    results[s.id] = r
                    pending--
                }
            }
        }
    }

    fun apply(c: LyricsCandidate, pin: Boolean) {
        if (applying) return
        applying = true
        vm.applyLyricsCandidate(t, c, pin) { id ->
            applying = false
            if (id != null) onPicked(id)
        }
    }

    // Варианты разных источников чередуются: сначала лучший у каждого, потом вторые и т. д.
    val lists = searchers.map { results[it.id].orEmpty() }
    val merged = buildList {
        for (rank in 0 until (lists.maxOfOrNull { it.size } ?: 0)) lists.forEach { l -> l.getOrNull(rank)?.let(::add) }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = "Назад")
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Исполнитель или название") },
                        leadingIcon = { Icon(painterResource(R.drawable.ms_search), null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(painterResource(R.drawable.ms_close), contentDescription = "Очистить")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        shape = rounded(28),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                }
                Box(Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 16.dp, vertical = 1.dp)) {
                    if (pending > 0) LinearProgressIndicator(Modifier.fillMaxSize())
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        merged.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(merged, key = { i, c -> "$i|${c.key}" }) { _, c ->
                                ListItem(
                                    headlineContent = { Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        Column {
                                            Text(c.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(c.sourceName, style = MaterialTheme.typography.labelMedium)
                                                Text(" · ", style = MaterialTheme.typography.labelMedium)
                                                Text(
                                                    if (c.synced) "в такт" else "без тактов",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (c.synced) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (c.synced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        if (c.durationMs > 0) {
                                            val sec = c.durationMs / 1000
                                            Text("%d:%02d".format(sec / 60, sec % 60), style = MaterialTheme.typography.labelMedium)
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        if (PlayerSettings.lyricsConfirm) confirm = c
                                        else apply(c, PlayerSettings.lyricsPin)
                                    },
                                )
                            }
                        }
                        pending > 0 -> LoadingIndicator(Modifier.align(Alignment.Center))
                        else -> Text(
                            if (query.trim().length < 2) "Начните вводить название песни или исполнителя — варианты появятся из всех включённых источников"
                            else "Ничего не найдено",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        )
                    }
                    if (applying) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                }
            }
        }
    }

    confirm?.let { c ->
        var pin by remember(c) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Использовать этот текст?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(c.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${c.sourceName} · ${if (c.synced) "в такт" else "без тактов"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (c.synced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    c.preview?.let { pv ->
                        Surface(
                            shape = containerShape(16), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Text(pv, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (PlayerSettings.lyricsPin) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp).clip(containerShape(16)).clickable { pin = !pin }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = pin, onCheckedChange = { pin = it })
                            Text("Привязать текст к этому треку", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirm = null; apply(c, PlayerSettings.lyricsPin && pin) }) { Text("Выбрать") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена") } },
        )
    }
}

/**
 * Текст с таймкодами: текущая строка подсвечена и держится в верхней части панели, нажатие на строку перематывает к ней.
 * Длинная строка не обрезается: шрифт сам уменьшается, пока строка не поместится в панель (в том числе в низкой, альбомной).
 */
@Composable
private fun SyncedLyrics(vm: AppViewModel, trackId: String, lines: List<LyricLine>, playing: Boolean, big: Boolean = false) {
    var pos by remember(trackId) { mutableLongStateOf(vm.livePosition()) }
    LaunchedEffect(trackId, playing) {
        pos = vm.livePosition()
        while (playing) { delay(100); pos = vm.livePosition() }
    }
    val idx = lines.indexOfLast { it.timeMs <= pos + 250 }
    val listState = rememberLazyListState()
    // Раньше к отступу списка добавлялся ещё и сдвиг строки на треть высоты: в низкой панели строка уезжала к нижнему краю и обрезалась.
    // Теперь отступ сверху — доля высоты панели, строка встаёт точно в его начало, а снизу отступ нужен, чтобы последние строки могли подняться.
    val base = MaterialTheme.colorScheme.onSurface
    // Размытие строк: чем дальше строка от текущей, тем она мутнее. Пока текст листают пальцем, он чёткий, чтобы его можно было читать.
    // В плеере и на весь экран размытие включается отдельными переключателями.
    val blurOn = (if (big) PlayerSettings.lyricsBlurFull else PlayerSettings.lyricsBlur) && !ThemeSettings.optimize && android.os.Build.VERSION.SDK_INT >= 31
    val scrolling = listState.isScrollInProgress
    val lineStyle = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge
    val baseSp = lineStyle.fontSize.value
    val itemPad = if (big) 10.dp else 8.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        val itemPadPx = with(androidx.compose.ui.platform.LocalDensity.current) { (itemPad * 2).toPx() }
        val topPad = maxHeight * 0.18f
        // Строка начинается на 18% высоты и должна целиком уместиться в оставшееся место с небольшим запасом снизу.
        val maxLinePx = (heightPx * 0.74f - itemPadPx).coerceAtLeast(40f)
        LaunchedEffect(idx) {
            if (idx >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem(idx, scrollOffset = 0)
        }
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad, bottom = maxHeight * 0.8f),
        ) {
            itemsIndexed(lines) { i, l ->
                val color by animateColorAsState(if (i == idx) base else base.copy(alpha = 0.38f), tween(fxMs(250)), label = "lyricLine")
                val blurTarget = if (!blurOn || scrolling || idx < 0 || i == idx) 0f else (1.5f + kotlin.math.abs(i - idx) * 1.2f).coerceAtMost(6f)
                val blurDp by animateFloatAsState(blurTarget, tween(fxMs(300)), label = "lyricBlur")
                // Подгонка размера: если строка выше доступного места, шрифт уменьшается ступенями (не меньше 45% обычного).
                var fs by remember(l.text, baseSp, maxLinePx) { mutableFloatStateOf(baseSp) }
                Text(
                    l.text.ifBlank { "♪" },
                    style = lineStyle,
                    fontSize = fs.sp,
                    lineHeight = (fs * 1.25f).sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    onTextLayout = { r ->
                        if (r.size.height > maxLinePx && fs > baseSp * 0.45f) fs = (fs * 0.92f).coerceAtLeast(baseSp * 0.45f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val r = blurDp * density
                            if (r > 0.5f) renderEffect = androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal)
                        }
                        .clip(textShape(10)).clickable { vm.seekTo(l.timeMs) }.padding(horizontal = 4.dp, vertical = itemPad),
                )
            }
        }
    }
}

internal fun seekTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Типы устройств вывода, которые имеет смысл предлагать (динамик, проводные, Bluetooth, USB, HDMI и т.д.). */
private val OutputTypes = setOf(2, 3, 4, 8, 9, 11, 12, 22, 23, 26, 27, 30)

private fun outputLabel(d: android.media.AudioDeviceInfo): String {
    val name = d.productName?.toString().orEmpty().ifBlank { "Устройство" }
    return when (d.type) {
        2 -> "Динамик устройства"
        3, 4 -> "Проводные наушники"
        9 -> "HDMI"
        11, 12, 22 -> "USB · $name"
        else -> name
    }
}

/** Выбор устройства вывода звука: «Авто», подключённые к телефону и колонки/устройства Яндекса (как в Яндекс Музыке). */
@Composable
private fun AudioOutputDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val selected by vm.outputDeviceId.collectAsStateWithLifecycle()
    val cs by vm.connectState.collectAsStateWithLifecycle()
    val devices = remember {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in OutputTypes }
    }
    // Список колонок приходит из аккаунта Яндекса; закрыли окно, а на колонке ничего не играет — соединение закрываем.
    DisposableEffect(Unit) {
        vm.openConnect()
        onDispose { vm.connectDialogClosed() }
    }
    val remoteId = if (vm.remoteActive()) cs.remoteId else null
    val yandexDevices = cs.devices.filter { it.canPlay && !it.offline }
    // Вход в Яндекс для управления колонкой по Wi-Fi (Glagol): cookies берутся из WebView.
    var showStationLogin by remember { mutableStateOf(false) }
    @Composable
    fun Option(label: String, isSelected: Boolean, sub: String? = null, icon: Int? = null, onPick: () -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(containerShape(16)).clickable(onClick = onPick).padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Spacer(Modifier.width(12.dp))
            if (icon != null) {
                Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (sub != null) {
                    Text(
                        sub, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Вывод звука") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Выбор любого из этих вариантов возвращает музыку с колонки на телефон.
                Option("Авто (как выберет система)", remoteId == null && selected == null) {
                    vm.releaseConnect(); vm.setAudioOutput(null); onDismiss()
                }
                devices.forEach { d ->
                    Option(outputLabel(d), remoteId == null && selected == d.id) {
                        vm.releaseConnect(); vm.setAudioOutput(d); onDismiss()
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Устройства Яндекса", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                    )
                    if (cs.connecting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                yandexDevices.forEach { d ->
                    val sub = buildString {
                        append(d.kindLabel)
                        if (d.id == remoteId) append(if (cs.paused) " · пауза" else " · играет")
                        else if (d.id == cs.activeId) append(" · сейчас играет")
                    }
                    Option(
                        d.title, d.id == remoteId, sub,
                        if (d.isSpeaker || d.isTv) R.drawable.ms_volume_up else R.drawable.ms_smartphone,
                    ) {
                        vm.playOnConnectDevice(d)
                        onDismiss()
                    }
                }
                if (cs.stationLoginRequired) {
                    Text(
                        "Колонка найдена в Wi-Fi, но для управления ей нужен вход в аккаунт Яндекса.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    TextButton(onClick = { showStationLogin = true }) { Text("Войти в Яндекс") }
                }
                if (cs.connected && yandexDevices.isEmpty()) {
                    Text(
                        "Устройств не найдено. Включите колонку и проверьте, что она в сети и привязана к этому аккаунту Яндекса.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                if (!cs.connected && !cs.connecting && yandexDevices.isEmpty()) {
                    Text(
                        cs.error ?: "Нет соединения с устройствами Яндекса",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    TextButton(onClick = { vm.connect.clearError(); vm.openConnect(); vm.connect.stationRescan() }) { Text("Повторить") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
    if (showStationLogin) {
        StationLoginDialog(
            onDone = { showStationLogin = false; vm.connect.stationLoginDone() },
            onDismiss = { showStationLogin = false },
        )
    }
}


// ---------- Обновления (GitHub releases) ----------

private fun openUrl(ctx: Context, url: String) {
    // Адрес приходит из ответа GitHub: открываем только https, чтобы не запустить чужую схему (intent:, file: и т.п.).
    if (!url.startsWith("https://", ignoreCase = true)) return
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Настройки → Обновления: ветка (stable / beta), проверка при запуске и ручная проверка. */
@Composable
private fun UpdatesPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        SubPageBar("Обновления", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = dockInset())) {
            ListItem(
                headlineContent = { Text("Установленная версия") },
                supportingContent = { Text("${Updates.current.tag} · ${Updates.current.channel.title}") },
                leadingContent = { Icon(painterResource(R.drawable.ms_info), null) },
            )
            SettingsLabel("Ветка обновлений")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                UpdateChannel.entries.forEachIndexed { i, ch ->
                    SegmentedButton(
                        selected = Updates.channel == ch,
                        onClick = { Updates.updateChannel(ch) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = UpdateChannel.entries.size),
                        label = { Text(ch.title, maxLines = 1) },
                    )
                }
            }
            Text(
                "Stable — только стабильные релизы (версия с буквой S, например 1.2.0S). " +
                    "Beta — бета-сборки (1.2.0B) и стабильные релизы, что новее.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SettingsLabel("Проверка")
            ListItem(
                headlineContent = { Text("Проверять при запуске") },
                supportingContent = { Text("Если найдётся новая версия, внизу появится уведомление") },
                leadingContent = { Icon(painterResource(R.drawable.ms_refresh), null) },
                trailingContent = {
                    ExpressiveSwitch(checked = Updates.checkOnStart, onCheckedChange = { Updates.updateCheckOnStart(it) })
                },
                modifier = Modifier.clickable { Updates.updateCheckOnStart(!Updates.checkOnStart) },
            )
            ListItem(
                headlineContent = { Text("Проверить обновления") },
                supportingContent = { Text(if (Updates.checking) "Проверяем…" else "Релизы на GitHub, ветка ${Updates.channel.title}") },
                leadingContent = { Icon(painterResource(R.drawable.ms_download), null) },
                trailingContent = {
                    if (Updates.checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                },
                modifier = Modifier.clickable(enabled = !Updates.checking) { Updates.check(manual = true) },
            )
            ListItem(
                headlineContent = { Text("Страница релизов") },
                supportingContent = { Text("github.com/${Updates.REPO}") },
                leadingContent = { Icon(painterResource(R.drawable.ms_open_in_new), null) },
                modifier = Modifier.clickable { openUrl(ctx, Updates.RELEASES_PAGE) },
            )
        }
    }
}

/** Алерт внизу экрана: найдено обновление при проверке на запуске. */
@Composable
private fun UpdateBanner() {
    val info = Updates.banner
    val ctx = LocalContext.current
    // Держим последнее найденное, чтобы карточка красиво уезжала, а не пустела.
    var last by remember { mutableStateOf<UpdateInfo?>(null) }
    if (info != null) last = info
    AnimatedVisibility(
        visible = info != null,
        enter = fadeIn(tween(fxMs(200))) + slideInVertically(tween(fxMs(300))) { it / 2 },
        exit = fadeOut(tween(fxMs(150))) + slideOutVertically(tween(fxMs(200))) { it / 2 },
    ) {
        val shown = last ?: return@AnimatedVisibility
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            shape = containerShape(24),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ms_download), null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Доступно обновление ${shown.version.tag}", style = MaterialTheme.typography.titleMedium)
                        Text("Сейчас установлена ${Updates.current.tag}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { Updates.dismissBanner() }) { Text("Позже") }
                    TextButton(onClick = {
                        openUrl(ctx, shown.apkUrl ?: shown.pageUrl)
                        Updates.dismissBanner()
                    }) { Text("Скачать") }
                }
            }
        }
    }
}

/** Модалка с итогом ручной проверки: есть обновление / стоит последняя версия / ошибка. */
@Composable
private fun UpdateDialog() {
    val res = Updates.dialog ?: return
    val ctx = LocalContext.current
    val cur = Updates.current
    when (res) {
        is UpdateResult.Available -> {
            val info = res.info
            AlertDialog(
                onDismissRequest = { Updates.dismissDialog() },
                title = { Text("Доступно обновление") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("Установлена: ${cur.tag}\nНовая: ${info.version.tag}")
                        if (info.notes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                info.notes.take(900) + if (info.notes.length > 900) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        openUrl(ctx, info.apkUrl ?: info.pageUrl)
                        Updates.dismissDialog()
                    }) { Text("Скачать") }
                },
                dismissButton = { TextButton(onClick = { Updates.dismissDialog() }) { Text("Позже") } },
            )
        }
        UpdateResult.UpToDate -> AlertDialog(
            onDismissRequest = { Updates.dismissDialog() },
            title = { Text("Обновлений нет") },
            text = { Text("Установлена последняя версия: ${cur.tag}\nВетка обновлений: ${Updates.channel.title}") },
            confirmButton = { TextButton(onClick = { Updates.dismissDialog() }) { Text("Ок") } },
        )
        is UpdateResult.Failed -> AlertDialog(
            onDismissRequest = { Updates.dismissDialog() },
            title = { Text("Не удалось проверить обновления") },
            text = { Text("Установлена версия ${cur.tag}.\n${res.message}") },
            confirmButton = {
                TextButton(onClick = {
                    Updates.dismissDialog()
                    Updates.check(manual = true)
                }) { Text("Повторить") }
            },
            dismissButton = { TextButton(onClick = { Updates.dismissDialog() }) { Text("Закрыть") } },
        )
    }
}
