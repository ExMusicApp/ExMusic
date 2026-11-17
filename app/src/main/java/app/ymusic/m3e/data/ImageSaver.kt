package app.ymusic.m3e.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object ImageSaver {
    private const val DIR = "ExMusic"
    private val http = OkHttpClient()

    private fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").replace(Regex("\\s+"), " ").trim().trimEnd('.', ' ')

    fun save(ctx: Context, url: String, baseName: String): Result<String> = runCatching {
        val name0 = safeName(baseName).take(100).ifBlank { "image" }
        if (url.startsWith("content://") || url.startsWith("file://")) {
            val input = ctx.contentResolver.openInputStream(Uri.parse(url)) ?: error("Не удалось открыть картинку")
            input.use { write(ctx, it, name0, "jpg", "image/jpeg") }
        } else {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) error("Сервер вернул HTTP ${r.code}")
                val body = r.body ?: error("Пустой ответ сервера")
                val ctype = r.header("Content-Type").orEmpty().lowercase()
                val (ext, mime) = when {
                    ctype.contains("png") -> "png" to "image/png"
                    ctype.contains("webp") -> "webp" to "image/webp"
                    else -> "jpg" to "image/jpeg"
                }
                body.byteStream().use { write(ctx, it, name0, ext, mime) }
            }
        }
    }

    private fun write(ctx: Context, input: InputStream, name: String, ext: String, mime: String): String {
        val resolver = ctx.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$DIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                ?: error("Не удалось создать файл в «Pictures»")
            try {
                val out: OutputStream = resolver.openOutputStream(uri) ?: error("Не удалось открыть файл для записи")
                out.use { input.copyTo(it) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            return "${Environment.DIRECTORY_PICTURES}/$DIR"
        }
        val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: ctx.filesDir, DIR).apply { mkdirs() }
        var f = File(dir, "$name.$ext")
        var n = 1
        while (f.exists()) f = File(dir, "$name ($n).$ext").also { n++ }
        try {
            f.outputStream().use { input.copyTo(it) }
        } catch (e: Throwable) {
            runCatching { f.delete() }
            throw e
        }
        return "папка приложения (${Environment.DIRECTORY_PICTURES}/$DIR)"
    }
}
