@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.FilterQuality
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ymusic.m3e.AppViewModel
import app.ymusic.m3e.R
import app.ymusic.m3e.data.Track

/**
 * Сеанс микса: старый трек [a] и следующий [b] (его может ещё не быть — тогда в окне кнопка «Выбрать следующий трек»).
 * Окно [MixPlayerHost] открывается кнопкой «Замиксовать» ([AppViewModel.mixTracks]) или кнопкой «Микс» в полном плеере.
 */
internal object MixSession {
    var a by mutableStateOf<Track?>(null)
        private set
    var b by mutableStateOf<Track?>(null)
        private set
    var visible by mutableStateOf(false)
        private set

    /** «Не переключать трек»: первый трек остаётся в миксе, даже если в окне переключить трек. */
    var keepFirst by mutableStateOf(false)

    /** Отрывок: позиция первого трека, с которой начинается переход (-1 — как обычно, перед концом). */
    var outMs by mutableLongStateOf(-1L)
        private set

    /** Отрывок: позиция второго трека, с которой он входит (0 — с начала). */
    var inMs by mutableLongStateOf(0L)
        private set

    /** Меняет пару; отрывок сбрасывается только у того трека, который заменили. */
    private fun assign(na: Track?, nb: Track?) {
        if (na?.id != a?.id) outMs = -1L
        if (nb?.id != b?.id) inMs = 0L
        a = na; b = nb
    }

    fun start(a: Track, b: Track) { assign(a, b); visible = true }

    fun setOut(ms: Long) { outMs = ms }
    fun setIn(ms: Long) { inMs = ms.coerceAtLeast(0L) }

    /** Идёт ли сеанс микса с играющим треком [cur] (он старый или следующий в паре, и следующий уже выбран). */
    fun isActiveFor(cur: Track?): Boolean = cur != null && b != null && (cur.id == a?.id || cur.id == b?.id)

    /**
     * Из плеера: если сеанс с играющим треком уже идёт — открывается то же окно (с тем же выбранным треком).
     * Иначе играющий трек становится «старым», а следующий выбирается в окне.
     */
    fun openFromPlayer(cur: Track) {
        if (cur.id != a?.id && cur.id != b?.id) assign(cur, null)
        syncWith(cur, switched = false)
        visible = true
    }

    /**
     * Играющий трек поменялся.
     *  • Микс проиграл — играет уже «следующий» трек пары: он становится первым, второй выбирается заново.
     *  • Трек переключили вручную ([switched]) и он не из пары: без «Не переключать трек» окно переходит на него
     *    (старый трек не запоминается), с ним — пара остаётся, а играющий трек предлагается как следующий.
     */
    fun syncWith(cur: Track?, switched: Boolean = true) {
        if (cur == null) return
        val nb = b
        if (nb != null && cur.id == nb.id) { assign(nb, null); return }
        if (cur.id == a?.id) return
        if (switched && !keepFirst) assign(cur, null)
    }

    /** Первый трек заменён (второго пока нет). */
    fun startFirst(a: Track) { assign(a, null); visible = true }

    fun close() { visible = false }
}

/** Окно микса; вставляется один раз в корень приложения. */
@Composable
internal fun MixPlayerHost(vm: AppViewModel) {
    if (!MixSession.visible) return
    val a = MixSession.a ?: return
    MixPlayerSheet(vm, a, MixSession.b) { MixSession.close() }
}

/**
 * Окно микса. Компактное: шапка, старый трек, настройка перехода, следующий трек (или кнопка выбора) — всё это при
 * нехватке места прокручивается, а плеер (перемотка и кнопки) закреплён внизу и всегда под пальцем. Фон — размытые
 * обложки: сверху старого трека, снизу следующего.
 */
@Composable
private fun MixPlayerSheet(vm: AppViewModel, a: Track, b: Track?, onDismiss: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cur = s.nowPlaying
    val sec = PlayerSettings.crossfadeSec
    // 0 — выбор не открыт, 1 — выбираем первый (играющий) трек, 2 — следующий.
    var picking by remember { mutableIntStateOf(0) }
    // Микс проиграл или трек переключили — окно обновляется. Первое срабатывание при открытии окна трек не сбрасывает:
    // плеер мог ещё не успеть переключиться на только что выбранную пару.
    var firstSync by remember { mutableStateOf(true) }
    LaunchedEffect(cur?.id) {
        MixSession.syncWith(cur, switched = !firstSync)
        firstSync = false
    }
    // Отрывок: какой трек редактируется (1 — выход первого, 2 — вход второго).
    var pointFor by remember { mutableIntStateOf(0) }
    val outMs = MixSession.outMs
    val inMs = MixSession.inMs
    val foreign = MixSession.keepFirst && cur != null && cur.id != a.id && cur.id != b?.id

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth()) {
            MixBackdrop(a, b, Modifier.matchParentSize())
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 16.dp).padding(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
                MixPlayerHeader(onDismiss)

                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MixTrackCard(
                        label = "Первый трек", track = a,
                        status = when (cur?.id) { a.id -> "Играет"; b?.id -> "Сыграл"; else -> "Изменить" },
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        highlighted = cur?.id == a.id,
                        onClick = { picking = 1 },
                        pointLabel = if (outMs > 0) "Уходит с ${seekTime(outMs)}" else "Уходит в конце",
                        onPoint = { pointFor = 1 },
                    )
                    MixKeepFirstRow(MixSession.keepFirst) { MixSession.keepFirst = it }
                    MixTransitionPanel(sec)
                    if (foreign && cur != null) {
                        MixUseCurrentButton(cur, a) { vm.mixTracks(a, cur) }
                    }
                    if (b != null) {
                        MixTrackCard(
                            label = "Следующий трек", track = b,
                            status = if (cur?.id == b.id) "Играет" else "Изменить",
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            highlighted = cur?.id == b.id,
                            onClick = { picking = 2 },
                            pointLabel = if (inMs > 0) "Входит с ${seekTime(inMs)}" else "Входит с начала",
                            onPoint = { pointFor = 2 },
                        )
                    } else {
                        MixPickNextButton { picking = 2 }
                    }
                }

                MixNowPlaying(vm, cur, s.isPlaying, s.durationMs)
            }
        }
    }

    if (pointFor != 0) {
        val first = pointFor == 1
        val track = if (first) a else b
        if (track != null) {
            MixPointSheet(
                vm = vm, track = track, isOut = first, fadeSec = sec, hasPair = b != null,
                onDismiss = { pointFor = 0 },
            )
        }
    }

    if (picking != 0) {
        val first = picking == 1
        MixNextPicker(
            vm = vm,
            title = if (first) "Выбрать первый трек" else "Выбрать следующий трек",
            exclude = if (first) b else a,
            showUpcoming = !first,
            onDismiss = { picking = 0 },
            onPick = { t -> picking = 0; if (first) vm.mixReplaceFirst(t) else vm.mixWithNext(t) },
        )
    }
}

/** Размытая обложка: на Android 12+ настоящее размытие, ниже — мягкое размытие самой картинки. */
@Composable
private fun BlurredCover(url: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (android.os.Build.VERSION.SDK_INT >= 31) {
        val request = remember(url) { ImageRequest.Builder(ctx).data(url).size(300).build() }
        AsyncImage(
            model = request, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
            modifier = modifier.blur(48.dp, BlurredEdgeTreatment.Unbounded),
        )
    } else {
        val bmp = rememberSoftBlurredCover(url)
        if (bmp != null) Image(bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = modifier)
    }
}

/** Фон окна: сверху размытая обложка предыдущего трека, снизу — следующего; посередине они плавно перетекают. */
@Composable
private fun MixBackdrop(a: Track?, b: Track?, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    Box(modifier.clipToBounds()) {
        // Низ: следующий трек (пока его нет — спокойный третичный цвет).
        Crossfade(targetState = b?.cover(300), animationSpec = tween(fxMs(400)), label = "mixBackdropB") { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) BlurredCover(url, Modifier.fillMaxSize())
                else Box(Modifier.fillMaxSize().background(tertiary.copy(alpha = 0.5f)))
            }
        }
        // Верх: предыдущий трек, плавно растворяется к середине.
        Crossfade(targetState = a?.cover(300), animationSpec = tween(fxMs(400)), label = "mixBackdropA") { url ->
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Black, 0.4f to Color.Black, 0.65f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                if (url != null) BlurredCover(url, Modifier.fillMaxSize())
            }
        }
        // Приглушаем цветом поверхности, чтобы текст и кнопки читались.
        Box(Modifier.fillMaxSize().background(surface.copy(alpha = 0.58f)))
    }
}

@Composable
private fun MixPlayerHeader(onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = MaterialShapes.Cookie9Sided.toShape(), color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {}
            Icon(
                painterResource(R.drawable.ms_graphic_eq), null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("Микс", style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                "Два трека перетекают друг в друга",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ms_close), contentDescription = "Закрыть") }
    }
}

/** Карточка трека микса: роль, обложка, название, исполнитель и состояние. С [onClick] — нажатием трек можно сменить. */
@Composable
private fun MixTrackCard(
    label: String, track: Track, status: String, container: Color, content: Color, highlighted: Boolean,
    onClick: (() -> Unit)? = null,
    pointLabel: String? = null, onPoint: (() -> Unit)? = null,
) {
    val corner by animateDpAsState(
        if (highlighted) 28.dp else 20.dp,
        fxSpring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "mixCardCorner",
    )
    val color = if (highlighted) container.copy(alpha = 0.9f) else container.copy(alpha = 0.6f)
    val inner: @Composable () -> Unit = {
      Column {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(track.cover(160), Modifier.size(52.dp).clip(rounded(14)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.75f), maxLines = 1)
                Text(
                    track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = content, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artists, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (status.isNotEmpty()) {
                Surface(shape = CircleShape, color = content.copy(alpha = 0.12f)) {
                    Text(
                        status, style = MaterialTheme.typography.labelSmall, color = content,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), maxLines = 1,
                    )
                }
            }
            if (onClick != null) Icon(painterResource(R.drawable.ms_edit), null, Modifier.padding(start = 8.dp, end = 4.dp).size(18.dp), tint = content.copy(alpha = 0.8f))
        }
        if (onPoint != null && pointLabel != null) {
            Surface(
                onClick = onPoint, shape = CircleShape, color = content.copy(alpha = 0.12f),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ms_schedule), null, Modifier.size(16.dp), tint = content)
                    Text(
                        pointLabel, style = MaterialTheme.typography.labelMedium, color = content,
                        modifier = Modifier.padding(start = 6.dp), maxLines = 1,
                    )
                    Text(
                        "· отрывок", style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp), maxLines = 1,
                    )
                }
            }
        }
      }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(corner), color = color, modifier = Modifier.fillMaxWidth()) { inner() }
    } else {
        Surface(shape = RoundedCornerShape(corner), color = color, modifier = Modifier.fillMaxWidth()) { inner() }
    }
}

/** Переключатель «Не переключать трек». */
@Composable
private fun MixKeepFirstRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!checked) }, shape = containerShape(20),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Не переключать трек", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Первый трек запоминается. Переключили трек — он предложится как следующий",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ExpressiveSwitch(checked = checked, onCheckedChange = onChange)
        }
    }
}

/** При включённом «Не переключать трек»: предлагает выбрать играющий сейчас трек следующим и сразу вернуться к первому. */
@Composable
private fun MixUseCurrentButton(cur: Track, first: Track, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.95f),
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(cur.cover(160), Modifier.size(52.dp).clip(rounded(14)))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    "Выбрать этот трек следующим", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f), maxLines = 1,
                )
                Text(
                    cur.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Играет сейчас · вернёмся к «${first.title}»", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(painterResource(R.drawable.ms_skip_previous), null, Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/**
 * Выбор отрывка. Для первого трека — с какого места он начнёт уходить (и тут же вступит второй), для второго — с какого
 * места он входит. Длительность перехода и плавность те же, что в настройке выше; «Прослушать» запускает переход.
 */
@Composable
private fun MixPointSheet(vm: AppViewModel, track: Track, isOut: Boolean, fadeSec: Int, hasPair: Boolean, onDismiss: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dur = track.durationMs.takeIf { it > 0 } ?: if (s.nowPlaying?.id == track.id) s.durationMs else 0L
    // Выход: оставляем хвост на сам переход; вход: хотя бы несколько секунд трека после точки.
    val maxMs = (if (isOut) dur - 2000 else dur - 6000).coerceAtLeast(0L)
    val saved = if (isOut) MixSession.outMs else MixSession.inMs
    val default = if (isOut) (dur - fadeSec * 1000L).coerceAtLeast(0L) else 0L
    var value by remember { mutableLongStateOf(if (saved > 0) saved.coerceAtMost(maxMs) else default) }
    val set: (Long) -> Unit = { v ->
        value = v.coerceIn(0L, maxMs)
        if (isOut) vm.mixSetOut(value) else vm.mixSetIn(value)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(track.cover(160), Modifier.size(52.dp).clip(rounded(14)))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        if (isOut) "Откуда уходит первый трек" else "С какого места входит второй",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${track.title} · ${track.artists}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ms_close), contentDescription = "Закрыть") }
            }
            if (dur <= 0) {
                Text(
                    "Длительность трека пока неизвестна — запустите его и откройте окно снова.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        seekTime(value), style = MaterialTheme.typography.displaySmallEmphasized,
                        color = if (isOut) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        if (isOut) "переход начнётся здесь и продлится $fadeSec с" else "второй трек начнётся отсюда и нарастёт за $fadeSec с",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                M3SeekBar(
                    value = if (maxMs > 0) value.toFloat() / maxMs else 0f,
                    onValueChange = { v -> set((v * maxMs).toLong()) },
                    onValueChangeFinished = {},
                    wavy = true, thickness = 8.dp, amplitude = 1f, minTouchHeight = 44.dp, haptic = true,
                )
                Row(Modifier.fillMaxWidth()) {
                    Text("0:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(seekTime(dur), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { Haptics.click(); set(value - 5000) }, modifier = Modifier.weight(1f)) { Text("−5 с") }
                    FilledTonalButton(onClick = { Haptics.click(); set(value + 5000) }, modifier = Modifier.weight(1f)) { Text("+5 с") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            Haptics.click()
                            if (isOut) { MixSession.setOut(-1L); vm.mixSetOut(-1L); value = default }
                            else { MixSession.setIn(0L); vm.mixSetIn(0L); value = 0L }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(if (isOut) "В конце трека" else "С начала") }
                    if (hasPair) {
                        FilledTonalButton(
                            onClick = { Haptics.click(); vm.mixPreview(); onDismiss() },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        ) {
                            Icon(painterResource(R.drawable.ms_play_arrow), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Прослушать")
                        }
                    }
                }
            }
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Готово") }
        }
    }
}

/** Вместо карточки следующего трека, пока он не выбран. */
@Composable
private fun MixPickNextButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ms_add), null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    "Выбрать следующий трек", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    "Из очереди, истории, лайков или поиска", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(painterResource(R.drawable.ms_chevron_right), null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/** Настройка перехода в одной карточке: схема, значение с ползунком в одну строку и быстрые значения. */
@Composable
private fun MixTransitionPanel(sec: Int) {
    Surface(shape = containerShape(24), color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            MixDiagram(true, sec, height = 84.dp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(64.dp)) {
                    AnimatedContent(
                        targetState = sec,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically(fxSpring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { if (up) it else -it } + fadeIn())
                                .togetherWith(slideOutVertically(fxSpring(stiffness = Spring.StiffnessMedium)) { if (up) -it else it } + fadeOut())
                        },
                        label = "mixPlayerSec",
                    ) { v ->
                        Text("$v с", style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    Text("переход", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                M3SeekBar(
                    value = (sec - 1) / 11f,
                    onValueChange = { v ->
                        val n = (v * 11f).roundToInt() + 1
                        if (n != PlayerSettings.crossfadeSec) { Haptics.tick(); PlayerSettings.updateCrossfadeSec(n) }
                    },
                    onValueChangeFinished = {},
                    modifier = Modifier.weight(1f),
                    wavy = true, thickness = 6.dp, amplitude = 1f, minTouchHeight = 40.dp,
                )
            }
            Spacer(Modifier.height(4.dp))
            MixPresetGroup(selected = sec, minHeight = 44.dp) { Haptics.click(); PlayerSettings.updateCrossfadeSec(it) }
        }
    }
}

/** Плеер, закреплённый внизу окна: перемотка и кнопки «назад», «играть / пауза», «вперёд». */
@Composable
private fun MixNowPlaying(vm: AppViewModel, cur: Track?, playing: Boolean, durationMs: Long) {
    Surface(shape = containerShape(24), color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            val seek = rememberSeek(vm, cur?.id.orEmpty(), durationMs, playing)
            M3SeekBar(
                value = seek.shown, onValueChange = seek.onChange, onValueChangeFinished = seek.onFinish,
                wavy = true, thickness = 6.dp, amplitude = seek.amp, minTouchHeight = 40.dp, haptic = true,
            )
            SeekTimes(seek, durationMs)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { vm.previous() }, modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ms_skip_previous), contentDescription = "Предыдущий трек")
                }
                Spacer(Modifier.width(16.dp))
                // Кнопка «пружинит» формой: на паузе круглая, во время игры — «квадрат» с мягкими углами.
                val corner by animateDpAsState(
                    if (playing) 22.dp else 32.dp,
                    fxSpring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "mixPlayCorner",
                )
                FilledIconButton(
                    onClick = { vm.togglePlay() }, shape = RoundedCornerShape(corner),
                    modifier = Modifier.size(width = 88.dp, height = 64.dp),
                ) {
                    Icon(
                        painterResource(if (playing) R.drawable.ms_pause else R.drawable.ms_play_arrow),
                        contentDescription = if (playing) "Пауза" else "Играть", modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                FilledTonalIconButton(onClick = { vm.next() }, modifier = Modifier.size(52.dp)) {
                    Icon(painterResource(R.drawable.ms_skip_next), contentDescription = "Следующий трек")
                }
            }
        }
    }
}

/**
 * Выбор следующего трека для микса. Пока запрос пуст: первым — следующий трек очереди, затем строка истории и лайки.
 * С запросом — результаты поиска.
 */
@Composable
private fun MixNextPicker(
    vm: AppViewModel, title: String, exclude: Track?, showUpcoming: Boolean,
    onDismiss: () -> Unit, onPick: (Track) -> Unit,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val upcoming = remember(exclude?.id, showUpcoming) { if (showUpcoming) vm.upcomingTracks(1).firstOrNull { it.id != exclude?.id } else null }
    val history = remember(s.history, exclude?.id) { s.history.filter { it.id != exclude?.id }.distinctBy { it.id }.take(30) }
    val liked = remember(s.liked, exclude?.id) { s.liked.filter { it.id != exclude?.id }.distinctBy { it.id } }

    LaunchedEffect(Unit) { vm.clearPlaylistSearch() }
    DisposableEffect(Unit) { onDispose { vm.clearPlaylistSearch() } }
    LaunchedEffect(query) { delay(400); vm.searchForPlaylist(query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Row(Modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ms_close), contentDescription = "Закрыть") }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Треки, исполнители") },
                leadingIcon = { Icon(painterResource(R.drawable.ms_search), null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(painterResource(R.drawable.ms_close), contentDescription = "Очистить")
                    }
                },
                singleLine = true, shape = rounded(32),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.searchForPlaylist(query) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (query.isNotBlank()) {
                val results = s.pickerResults.filter { it.id != exclude?.id }.distinctBy { it.id }
                when {
                    s.pickerLoading && results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    results.isEmpty() && !s.pickerLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(results, key = { it.id }) { t -> MixPickRow(t) { onPick(t) } }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (upcoming != null) {
                        item(key = "nextHeader") { MixPickHeader("Следующий в очереди") }
                        item(key = "nextTrack") { MixPickRow(upcoming) { onPick(upcoming) } }
                    }
                    if (history.isNotEmpty()) {
                        item(key = "histHeader") { MixPickHeader("История") }
                        item(key = "histRow") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(history, key = { it.id }) { t ->
                                    Column(Modifier.width(96.dp).clip(rounded(16)).clickable { onPick(t) }) {
                                        Cover(t.cover(200), Modifier.size(96.dp).clip(rounded(16)))
                                        Text(
                                            t.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                                        )
                                        Text(
                                            t.artists, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (liked.isNotEmpty()) {
                        item(key = "likedHeader") { MixPickHeader("Лайки") }
                        items(liked, key = { "l:${it.id}" }) { t -> MixPickRow(t) { onPick(t) } }
                    }
                    if (upcoming == null && history.isEmpty() && liked.isEmpty()) item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Введите название трека или исполнителя", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MixPickHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
    )
}

@Composable
private fun MixPickRow(t: Track, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(t.artists, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Cover(t.cover(200), Modifier.size(52.dp).clip(rounded(14))) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
