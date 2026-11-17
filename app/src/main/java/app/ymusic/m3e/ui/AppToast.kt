package app.ymusic.m3e.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ToastKind { Info, Success, Error }

object AppToast {
    class Item(val id: Long, val text: String, val kind: ToastKind, val durationMs: Long)

    private var seq = 0L
    private val main = Handler(Looper.getMainLooper())

    var current by mutableStateOf<Item?>(null)
        private set

    internal val hosts = mutableStateListOf<Any>()

    fun show(text: String, kind: ToastKind = ToastKind.Info, long: Boolean = false) {
        if (text.isBlank()) return
        val ms = ((if (long) 3500L else 2000L) + text.length * 60L).coerceAtMost(9000L)
        val post = {
            current = Item(++seq, text, kind, ms)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) post() else main.post { post() }
    }

    fun error(text: String, long: Boolean = false) = show(text, ToastKind.Error, long)

    fun success(text: String) = show(text, ToastKind.Success)

    internal fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

@Composable
fun AppToastHost(modifier: Modifier = Modifier) {
    val token = remember { Any() }
    DisposableEffect(token) {
        AppToast.hosts.add(token)
        onDispose { AppToast.hosts.remove(token) }
    }
    if (AppToast.hosts.lastOrNull() !== token) return
    val item = AppToast.current
    LaunchedEffect(item?.id) {
        if (item != null) {
            delay(item.durationMs)
            AppToast.dismiss(item.id)
        }
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AnimatedContent(
            targetState = item,
            contentKey = { it?.id },
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = if (targetState != null) toastEnter() else EnterTransition.None,
                    initialContentExit = if (initialState != null) toastExit() else ExitTransition.None,
                    sizeTransform = null,
                )
            },
            contentAlignment = Alignment.BottomCenter,
            label = "appToast",
        ) { t ->
            if (t != null) ToastCard(t) else Spacer(Modifier.height(0.dp))
        }
    }
}

private fun toastEnter(): EnterTransition =
    fadeIn(tween(fxMs(110))) + slideInVertically(tween(fxMs(150))) { it / 3 }

private fun toastExit(): ExitTransition = fadeOut(tween(fxMs(100)))

@Composable
private fun ToastCard(t: AppToast.Item) {
    val cs = MaterialTheme.colorScheme
    val container = when (t.kind) {
        ToastKind.Error -> cs.errorContainer
        else -> cs.surfaceContainerHighest
    }
    val onContainer = when (t.kind) {
        ToastKind.Error -> cs.onErrorContainer
        else -> cs.onSurface
    }
    val accent = when (t.kind) {
        ToastKind.Error -> cs.error
        ToastKind.Success -> cs.tertiary
        ToastKind.Info -> cs.primary
    }
    val onAccent = when (t.kind) {
        ToastKind.Error -> cs.onError
        ToastKind.Success -> cs.onTertiary
        ToastKind.Info -> cs.onPrimary
    }
    val icon = when (t.kind) {
        ToastKind.Error -> R.drawable.ms_close
        ToastKind.Success -> R.drawable.ms_check
        ToastKind.Info -> R.drawable.ms_info
    }
    val shape = containerShape(22)

    val scope = rememberCoroutineScope()
    val dragX = remember(t.id) { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 96.dp.toPx() }

    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Surface(
            color = container,
            contentColor = onContainer,
            shape = shape,
            tonalElevation = 3.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .offset { IntOffset(dragX.value.roundToInt(), 0) }
                .graphicsLayer { alpha = (1f - abs(dragX.value) / (dismissPx * 2.2f)).coerceIn(0f, 1f) }
                .shadow(8.dp, shape, ambientColor = accent.copy(alpha = 0.25f), spotColor = accent.copy(alpha = 0.35f))
                .border(1.dp, accent.copy(alpha = 0.28f), shape)
                .pointerInput(t.id) { detectTapGestures { AppToast.dismiss(t.id) } }
                .pointerInput(t.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragX.value) > dismissPx) AppToast.dismiss(t.id)
                            else scope.launch { dragX.animateTo(0f, fxSpring()) }
                        },
                        onDragCancel = { scope.launch { dragX.animateTo(0f, fxSpring()) } },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { dragX.snapTo(dragX.value + delta) }
                    }
                },
        ) {
            Box {
                Row(
                    Modifier.padding(start = 10.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(icon), contentDescription = null, tint = onAccent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        t.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}
