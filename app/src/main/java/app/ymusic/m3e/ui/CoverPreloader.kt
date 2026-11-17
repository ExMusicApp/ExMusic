package app.ymusic.m3e.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object CoverPreloader {
    private val seen = LinkedHashSet<String>()

    fun preload(context: Context, urls: Collection<String?>) {
        if (urls.isEmpty()) return
        val ctx = context.applicationContext
        val loader = SingletonImageLoader.get(ctx)
        for (u in urls) {
            if (u.isNullOrBlank()) continue
            val fresh = synchronized(seen) {
                val added = seen.add(u)
                while (seen.size > 600) seen.remove(seen.first())
                added
            }
            if (!fresh) continue
            loader.enqueue(
                ImageRequest.Builder(ctx).data(u)
                    .memoryCacheKeyExtra("retry", "0")
                    .build(),
            )
        }
    }

    fun reset() { synchronized(seen) { seen.clear() } }
}

@Composable
internal fun rememberSoftBlurredCover(url: String?, sizePx: Int = 96, radius: Int = 8): ImageBitmap? {
    val ctx = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, url, sizePx, radius) {
        if (url == null) { value = null; return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val req = ImageRequest.Builder(ctx).data(url).size(sizePx).allowHardware(false).build()
                val res = SingletonImageLoader.get(ctx).execute(req) as? SuccessResult ?: return@runCatching null
                softBlurBitmap(res.image.toBitmap(), radius).asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

internal fun softBlurBitmap(src: Bitmap, radius: Int, passes: Int = 3): Bitmap {
    val w = src.width
    val h = src.height
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    if (w < 2 || h < 2 || radius < 1) return out
    val px = IntArray(w * h)
    out.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    repeat(passes) {
        blurPass(px, tmp, w, h, radius, true)
        blurPass(tmp, px, w, h, radius, false)
    }
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
}

private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    val div = 2 * r + 1
    for (line in 0 until lines) {
        var a = 0; var rr = 0; var g = 0; var b = 0
        for (k in -r..r) {
            val i = k.coerceIn(0, len - 1)
            val p = src[if (horizontal) line * w + i else i * w + line]
            a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
        }
        for (i in 0 until len) {
            dst[if (horizontal) line * w + i else i * w + line] =
                ((a / div) shl 24) or ((rr / div) shl 16) or ((g / div) shl 8) or (b / div)
            val ai = (i + r + 1).coerceAtMost(len - 1)
            val si = (i - r).coerceAtLeast(0)
            val add = src[if (horizontal) line * w + ai else ai * w + line]
            val sub = src[if (horizontal) line * w + si else si * w + line]
            a += (add ushr 24) - (sub ushr 24)
            rr += ((add shr 16) and 255) - ((sub shr 16) and 255)
            g += ((add shr 8) and 255) - ((sub shr 8) and 255)
            b += (add and 255) - (sub and 255)
        }
    }
}
