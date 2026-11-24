@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import android.os.Handler
import android.os.Looper
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.ymusic.m3e.R
import app.ymusic.m3e.data.ImageSaver
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Полноэкранное окно просмотра фото исполнителя / обложки альбома.
 *
 * Крестик — слева сверху, фото — посередине, «Скачать» — снизу. Если фото несколько
 * (Яндекс Музыка отдаёт все фото исполнителя), их можно листать вправо-влево, а справа сверху
 * показывается «открытое/все», например «2/7».
 *
 * @param images ссылки на картинки в большом размере; первая открывается сразу (см. [startIndex])
 * @param fileName имя файла при сохранении (к нему добавляется номер, если фото несколько)
 */
@Composable
fun ImageViewerDialog(
    images: List<String>,
    fileName: String,
    onDismiss: () -> Unit,
    startIndex: Int = 0,
) {
    if (images.isEmpty()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, images.lastIndex)) { images.size }
    var saving by remember { mutableStateOf(false) }

    fun doSave() {
        if (saving) return
        saving = true
        val url = images[pager.currentPage]
        val name = if (images.size > 1) "$fileName ${pager.currentPage + 1}" else fileName
        val app = ctx.applicationContext
        scope.launch {
            saveImageWithToast(app, url, name)
            saving = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Фон чёрный в любой теме, поэтому иконки статус-бара и панели навигации в этом окне — светлые.
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: (view as? DialogWindowProvider)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // Фото посередине; при нескольких — листается вправо-влево.
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
            ) { page ->
                ViewerPage(images[page])
            }

            // Верхняя строка: крестик слева, «открытое/все» справа (только если фото несколько).
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.16f),
                        contentColor = Color.White,
                    ),
                ) { Icon(painterResource(R.drawable.ms_close), contentDescription = "Закрыть") }
                if (images.size > 1) {
                    Text(
                        "${pager.currentPage + 1}/${images.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            // Кнопка «Скачать» — внизу по центру.
            Button(
                onClick = { doSave() },
                enabled = !saving,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(bottom = 24.dp).height(56.dp),
                contentPadding = PaddingValues(horizontal = 28.dp),
            ) {
                if (saving) LoadingIndicator(Modifier.size(24.dp))
                else Icon(painterResource(R.drawable.ms_download), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text(if (saving) "Сохранение…" else "Скачать")
            }

            // Тост поверх фото (окно просмотра — отдельное окно, и тост из основного экрана здесь не виден).
            AppToastHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))
        }
    }
}

/** Одна страница просмотра: фото по центру, пока грузится — индикатор. */
@Composable
private fun ViewerPage(url: String) {
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(top = 64.dp, bottom = 104.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(250).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { loading = false },
            onError = { loading = false; failed = true },
            modifier = Modifier.fillMaxSize(),
        )
        if (loading) LoadingIndicator()
        if (failed) Text("Не удалось загрузить фото", color = Color.White.copy(alpha = 0.7f))
    }
}

/** Скачивает и сохраняет картинку в галерею, результат показывает тостом (работает, даже если окно уже закрыли). */
suspend fun saveImageWithToast(app: Context, url: String, name: String) {
    withContext(Dispatchers.IO) {
        val r = ImageSaver.save(app, url, name)
        Handler(Looper.getMainLooper()).post {
            r.fold(
                onSuccess = { dir -> AppToast.show("Сохранено: $dir", ToastKind.Success, long = true) },
                onFailure = { e -> AppToast.error("Не удалось сохранить: ${e.message ?: "ошибка"}", long = true) },
            )
        }
    }
}
