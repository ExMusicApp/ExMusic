package app.ymusic.m3e.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.data.ScDoh
import app.ymusic.m3e.player.TrackPreloader
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Кэш приложения: обложки (Coil), предзагруженный звук, тексты песен, QR-коды. Скачанные песни, плейлисты и настройки не затрагиваются. */
internal object CacheTools {
    private fun dirs(ctx: Context): List<File> =
        listOf("image_cache", "audio_preload", "lyrics", "qr").map { File(ctx.applicationContext.cacheDir, it) }

    private fun sizeOf(f: File): Long =
        if (f.isDirectory) (f.listFiles()?.sumOf { sizeOf(it) } ?: 0L) else f.length()

    fun size(ctx: Context): Long = dirs(ctx).sumOf { sizeOf(it) }

    /** Блокирует поток: вызывать из фонового. */
    fun clear(ctx: Context) {
        val app = ctx.applicationContext
        runCatching { TrackPreloader.clearCache(app) }
        runCatching {
            val loader = SingletonImageLoader.get(app)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching { ScDoh.clearCache() }
        // Тексты и QR — обычные файлы; звук и обложки уже очищены своими кэшами.
        listOf("lyrics", "qr").forEach { n -> runCatching { File(app.cacheDir, n).listFiles()?.forEach { it.deleteRecursively() } } }
    }
}

/** Страница настроек «Кэш»: размер и очистка. */
@Composable
internal fun CachePage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { size = withContext(Dispatchers.IO) { CacheTools.size(ctx) } }

    Column(Modifier.fillMaxSize()) {
        SubPageBar("Кэш", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberTopScrollState()).padding(bottom = 24.dp + dockInset())) {
            Text(
                "В кэше лежат обложки, предзагруженный звук следующих треков, тексты песен и QR-коды. Приложение само заново " +
                    "загрузит нужное. Скачанные песни, плейлисты, аккаунты и настройки не затрагиваются.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                "Размер кэша: " + (size?.let { Formatter.formatShortFileSize(ctx, it) } ?: "считаем…"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Button(
                onClick = {
                    if (!busy) {
                        busy = true; note = null
                        scope.launch {
                            val before = withContext(Dispatchers.IO) { CacheTools.size(ctx) }
                            withContext(Dispatchers.IO) { CacheTools.clear(ctx) }
                            val after = withContext(Dispatchers.IO) { CacheTools.size(ctx) }
                            size = after
                            note = "Кэш очищен, освобождено ${Formatter.formatShortFileSize(ctx, (before - after).coerceAtLeast(0))}"
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(56.dp),
            ) { Text(if (busy) "Очищаем…" else "Очистить кэш") }
            note?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
