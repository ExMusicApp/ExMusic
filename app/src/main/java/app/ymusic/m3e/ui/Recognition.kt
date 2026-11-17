@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.ymusic.m3e.R
import app.ymusic.m3e.recognition.FoundAction
import app.ymusic.m3e.recognition.RecHistoryItem
import app.ymusic.m3e.recognition.RecSource
import app.ymusic.m3e.recognition.RecognitionHistory
import app.ymusic.m3e.recognition.RecState
import app.ymusic.m3e.recognition.RecResult
import app.ymusic.m3e.recognition.RecognitionService
import app.ymusic.m3e.recognition.RecognitionSettings
import app.ymusic.m3e.recognition.Recognizer
import coil3.compose.AsyncImage

@Composable
fun RecognitionContent(onClose: () -> Unit, onOpenInApp: (RecResult) -> Unit, autoOpen: Boolean = false) {
    val ctx = LocalContext.current
    val state by Recognizer.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var shown = false
        fun mark(v: Boolean) {
            if (v != shown) { shown = v; Recognizer.uiShown += if (v) 1 else -1 }
        }
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) mark(true) else if (e == Lifecycle.Event.ON_PAUSE) mark(false)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        mark(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs); mark(false) }
    }
    LaunchedEffect(state) {
        val f = state as? RecState.Found
        if (autoOpen && f != null && RecognitionSettings.onFound != FoundAction.Notify) onOpenInApp(f.result)
    }
    var pendingSource by remember { mutableStateOf<RecSource?>(null) }

    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            RecognitionService.start(ctx, RecSource.Device, r.resultCode, data)
        } else {
            Recognizer.set(RecState.Error("Без разрешения на запись экрана звук устройства услышать нельзя"))
        }
    }
    fun launchDevice() {
        if (Build.VERSION.SDK_INT < 29) {
            Recognizer.set(RecState.Error("Звук устройства можно записывать на Android 10 и новее. Используйте микрофон"))
            return
        }
        val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection.launch(mpm.createScreenCaptureIntent())
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val src = pendingSource
        pendingSource = null
        if (!granted) {
            Recognizer.set(RecState.Error("Нужен доступ к микрофону (для записи звука устройства он тоже требуется)"))
        } else if (src == RecSource.Mic) {
            RecognitionService.start(ctx, RecSource.Mic)
        } else if (src == RecSource.Device) {
            launchDevice()
        }
    }
    fun begin(source: RecSource) {
        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!has) {
            pendingSource = source
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else if (source == RecSource.Mic) {
            RecognitionService.start(ctx, RecSource.Mic)
        } else launchDevice()
    }

    var showHistory by remember { mutableStateOf(false) }
    BackHandler(enabled = showHistory && LocalPageActive.current) { showHistory = false }
    val switchAnim = ThemeSettings.animRecognition && !ThemeSettings.optimize
    AnimatedContent(
        targetState = showHistory,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            if (switchAnim) {
                val toHistory = targetState
                val dir = if (toHistory) 1 else -1
                (slideInHorizontally(tween(animMs(320))) { dir * it / 3 } + fadeIn(tween(animMs(240), delayMillis = animMs(40)))) togetherWith
                    (slideOutHorizontally(tween(animMs(280))) { -dir * it / 3 } + fadeOut(tween(animMs(160)))) using
                    SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) }
            } else EnterTransition.None togetherWith ExitTransition.None
        },
        label = "recHistorySwitch",
    ) { historyOpen ->
    if (historyOpen) {
        RecognitionHistoryPanel(onBack = { showHistory = false }, onOpen = onOpenInApp)
    } else
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "Распознать музыку", style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.align(Alignment.Center),
            )
            IconButton(onClick = { showHistory = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(painterResource(R.drawable.ms_history), contentDescription = "История распознавания")
            }
        }
        Spacer(Modifier.height(16.dp))
        val animOn = ThemeSettings.animRecognition && !ThemeSettings.optimize
        AnimatedContent(
            targetState = state,
            modifier = Modifier.fillMaxWidth(),
            contentKey = { it::class },
            transitionSpec = {
                if (animOn) {
                    (fadeIn(tween(animMs(220), delayMillis = animMs(60))) +
                        scaleIn(tween(animMs(260), delayMillis = animMs(60)), initialScale = 0.92f)) togetherWith
                        fadeOut(tween(animMs(120)))
                } else EnterTransition.None togetherWith ExitTransition.None
            },
            label = "recState",
        ) { s ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when (s) {
            is RecState.Listening -> {
                LoadingIndicator(Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    if (s.source == RecSource.Device) "Слушаю звук устройства…" else "Слушаю через микрофон…",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Осталось ${s.secondsLeft} с",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (s.matching) {
                    Text(
                        "Сверяю с базой — как только узнаю, покажу сразу",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { RecognitionService.cancel(ctx) }) { Text("Отмена") }
            }
            is RecState.Searching -> {
                LoadingIndicator(Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("Ищу совпадение…", style = MaterialTheme.typography.titleMedium)
            }
            is RecState.Found -> {
                val r = s.result
                androidx.compose.material3.Surface(
                    Modifier.size(160.dp), shape = containerShape(28),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ms_music_note), null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (r.cover != null) {
                            AsyncImage(r.cover, null, Modifier.size(160.dp), contentScale = ContentScale.Crop)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    r.title, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    r.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                r.album?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { onOpenInApp(r) }, Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(painterResource(R.drawable.ms_search), null)
                    Spacer(Modifier.width(8.dp))
                    Text("Найти в Яндекс Музыке")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { Recognizer.reset() }) { Text("Распознать ещё") }
            }
            else -> {
                if (s is RecState.NotFound) {
                    Text(
                        "Ничего не нашлось. Попробуйте ещё раз, когда играет припев, или сделайте звук громче",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                } else if (s is RecState.Error) {
                    Text(s.message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SourceButton("С микрофона", R.drawable.ms_mic, Modifier.weight(1f)) { begin(RecSource.Mic) }
                    SourceButton("С устройства", R.drawable.ms_smartphone, Modifier.weight(1f)) { begin(RecSource.Device) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "«С устройства» слушает то, что играет в других приложениях. Когда система спросит про запись экрана, выберите «Весь экран»: изображение не записывается, нужен только звук",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        }
        }
    }
    }
}

@Composable
private fun RecognitionHistoryPanel(onBack: () -> Unit, onOpen: (RecResult) -> Unit) {
    val items = RecognitionHistory.items
    val fmt = remember { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault()) }
    Column(
        Modifier.fillMaxWidth().animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f)).verticalScroll(rememberScrollState())
            .navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ms_arrow_back), contentDescription = "Назад") }
            Text(
                "История распознавания", style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (items.isNotEmpty()) TextButton(onClick = { RecognitionHistory.clear() }) { Text("Очистить") }
        }
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) {
            Text(
                "Пока пусто. Найденные треки появятся здесь",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
        }
        items.forEach { h -> RecognitionHistoryRow(h, fmt.format(java.util.Date(h.time)), onOpen) }
    }
}

@Composable
private fun RecognitionHistoryRow(h: RecHistoryItem, time: String, onOpen: (RecResult) -> Unit) {
    val r = h.result
    Row(
        Modifier.fillMaxWidth().clip(containerShape(16)).clickable { onOpen(r) }.padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Surface(
            Modifier.size(52.dp), shape = containerShape(12),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ms_music_note), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.cover != null) AsyncImage(r.cover, null, Modifier.size(52.dp), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                r.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { RecognitionHistory.remove(h) }) {
            Icon(painterResource(R.drawable.ms_close), contentDescription = "Убрать из истории")
        }
    }
}

@Composable
private fun SourceButton(text: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(72.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(icon), null)
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun TokenField(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(RecognitionSettings.auddToken) }
    Column(modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; RecognitionSettings.updateToken(it) },
            label = { Text("Ключ AudD (необязательно)") },
            supportingText = { Text("Нужен только как запасной вариант: dashboard.audd.io") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = containerShape(16),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
