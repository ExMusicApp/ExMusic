@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R
import app.ymusic.m3e.data.ImageSaver
import app.ymusic.m3e.data.QrCode
import app.ymusic.m3e.data.QrShare
import app.ymusic.m3e.data.ShareLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Что показывает окно «Поделиться»: заголовок, текст для системного меню и сама ссылка. */
internal data class ShareRequest(val title: String, val text: String, val link: String)

/** Общая точка входа: любая кнопка «Поделиться» вызывает [open], а окно рисуется один раз в AppRoot. */
internal object ShareQrHost {
    var request by mutableStateOf<ShareRequest?>(null)
        private set

    fun open(title: String, text: String, link: String) { request = ShareRequest(title, text, link) }
    fun close() { request = null }
}

/** Нижнее окно: QR-код с кнопками QR, ниже — поле ссылки со своими кнопками «Скопировать» и «Отправить». */
@Composable
internal fun ShareQrSheet(req: ShareRequest, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Тёмные модули — более тёмный из пары акцентных цветов, фон — более светлый: код читается и в светлой, и в тёмной теме.
    val a = MaterialTheme.colorScheme.primaryContainer
    val b = MaterialTheme.colorScheme.onPrimaryContainer
    val bg = if (a.luminance() >= b.luminance()) a else b
    var fg = if (a.luminance() >= b.luminance()) b else a
    if (bg.luminance() - fg.luminance() < 0.35f) fg = Color(0xFF111111)
    val bmp: Bitmap? = remember(req.link, bg, fg) { QrCode.bitmap(req.link, fg.toArgb(), bg.toArgb()) }
    var askSave by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                req.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (bmp != null) {
                // QR — обычная картинка: без нажатия и без «качания».
                Surface(
                    shape = RoundedCornerShape(28.dp), color = bg,
                    modifier = Modifier.size(224.dp),
                ) {
                    Image(bmp.asImageBitmap(), "QR-код", Modifier.fillMaxSize().padding(6.dp))
                }
                Spacer(Modifier.height(16.dp))
                // Под QR — кнопки только для самого QR-кода: скачать картинку и отправить её.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { askSave = true }, contentPadding = PaddingValues(horizontal = 18.dp)) {
                        Icon(painterResource(R.drawable.ms_download), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Скачать QR")
                    }
                    FilledTonalButton(onClick = { QrShare.sendQr(ctx, bmp, req.link) }, contentPadding = PaddingValues(horizontal = 18.dp)) {
                        Icon(painterResource(R.drawable.ms_share), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Отправить QR")
                    }
                }
            } else {
                Text("Ссылка слишком длинная для QR-кода", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            // Кнопки ссылки живут прямо в поле: скопировать и отправить именно ссылку (отдельно от кнопок QR-кода).
            OutlinedTextField(
                value = req.link, onValueChange = {}, readOnly = true, singleLine = true,
                label = { Text("Ссылка") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { QrShare.copyLink(ctx, req.link); AppToast.success("Ссылка скопирована") }) {
                            Icon(painterResource(R.drawable.ms_content_copy), "Скопировать ссылку")
                        }
                        IconButton(onClick = { ShareLinks.shareText(ctx, req.title, req.text) }) {
                            Icon(painterResource(R.drawable.ms_share), "Отправить ссылку")
                        }
                    }
                },
            )
        }
    }

    if (askSave && bmp != null) {
        AlertDialog(
            onDismissRequest = { askSave = false },
            title = { Text("Скачать QR-код?") },
            text = { Text("Картинка будет сохранена в галерею (Pictures/ExMusic).") },
            confirmButton = {
                TextButton(onClick = {
                    askSave = false
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { ImageSaver.savePng(ctx, bmp, "QR " + req.title) }
                        r.onSuccess { AppToast.success("QR-код сохранён: $it") }
                            .onFailure { AppToast.error(it.message ?: "Не удалось сохранить QR-код") }
                    }
                }) { Text("Скачать") }
            },
            dismissButton = { TextButton(onClick = { askSave = false }) { Text("Отмена") } },
        )
    }
}
