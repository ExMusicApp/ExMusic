package app.ymusic.m3e.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Главный цвет обложки: оттенок и насыщенность самых ярких пикселей (серые, чёрные и белые не считаются).
 * Используется для подкраски мини-плеера под играющий трек.
 */
internal object CoverColor {
    // Ключ: адрес обложки, у цвета по краю — с припиской «#edge».
    private val cache = object : LinkedHashMap<String, FloatArray?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray?>?): Boolean = size > 200
    }

    /** [оттенок 0..360, насыщенность 0..1] или null, если обложка почти без цвета (или не загрузилась). */
    suspend fun hueSat(ctx: Context, url: String, edge: Boolean = false): FloatArray? {
        val key = if (edge) "$url#edge" else url
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val r = withContext(Dispatchers.Default) {
            runCatching {
                val app = ctx.applicationContext
                val req = ImageRequest.Builder(app).data(url).size(64, 64).allowHardware(false).build()
                val res = SingletonImageLoader.get(app).execute(req) as? SuccessResult ?: return@runCatching null
                val bmp = res.image.toBitmap()
                // Свечение берёт цвет по краю обложки; если край без цвета (серый, чёрный, белый) — главный цвет всей обложки.
                if (edge) analyze(bmp, edgeOnly = true) ?: analyze(bmp) else analyze(bmp)
            }.getOrNull()
        }
        synchronized(cache) { cache[key] = r }
        return r
    }

    /** [edgeOnly] — учитываются только пиксели в рамке по краю обложки (около восьмой части ширины). */
    private fun analyze(src: Bitmap, edgeOnly: Boolean = false): FloatArray? {
        val bmp = Bitmap.createScaledBitmap(src, 32, 32, true)
        val buckets = 12
        val w = FloatArray(buckets)
        val sx = FloatArray(buckets)
        val cx = FloatArray(buckets)
        val sat = FloatArray(buckets)
        val hsv = FloatArray(3)
        val px = IntArray(32 * 32)
        bmp.getPixels(px, 0, 32, 0, 0, 32, 32)
        val ring = 4
        for ((i, p) in px.withIndex()) {
            if (edgeOnly) {
                val x = i % 32; val y = i / 32
                if (x in ring until 32 - ring && y in ring until 32 - ring) continue
            }
            android.graphics.Color.colorToHSV(p, hsv)
            val s = hsv[1]; val v = hsv[2]
            if (s < 0.15f || v < 0.15f) continue
            val weight = s * v
            val b = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            val rad = Math.toRadians(hsv[0].toDouble())
            w[b] += weight
            sx[b] += (sin(rad) * weight).toFloat()
            cx[b] += (cos(rad) * weight).toFloat()
            sat[b] += s * weight
        }
        var best = 0
        for (i in 1 until buckets) if (w[i] > w[best]) best = i
        if (w[best] < (if (edgeOnly) 3f else 8f)) return null
        var hue = Math.toDegrees(atan2(sx[best].toDouble(), cx[best].toDouble())).toFloat()
        if (hue < 0f) hue += 360f
        return floatArrayOf(hue, (sat[best] / w[best]).coerceIn(0f, 1f))
    }
}

/** Цвет обложки трека [url] (оттенок и насыщенность) для подкраски; null — подкраска выключена или цвета нет. */
@Composable
internal fun rememberCoverHue(url: String?, enabled: Boolean, edge: Boolean = false): FloatArray? {
    val ctx = LocalContext.current
    val state = produceState<FloatArray?>(initialValue = null, url, enabled, edge) {
        value = if (!enabled || url.isNullOrBlank()) null else CoverColor.hueSat(ctx, url, edge)
    }
    // Выключили настройку — подкраска пропадает сразу, не дожидаясь пересчёта.
    return if (enabled) state.value else null
}

/**
 * Подкрашивает [base] в оттенок обложки: светлота остаётся от [base], поэтому текст и значки поверх него читаются так же,
 * как на обычном цвете темы (светлая тема — светлая подложка, тёмная — тёмная).
 */
internal fun coverTinted(hs: FloatArray, base: Color): Color {
    val r = base.red; val g = base.green; val b = base.blue
    val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
    val l = (mx + mn) / 2f
    val s = (hs[1] * 0.85f).coerceIn(0.28f, 0.62f)
    return hslToColor(hs[0], s, l, base.alpha)
}

/** Яркий «светящийся» цвет из цвета обложки: насыщенный и средней светлоты, одинаково хорошо виден в светлой и тёмной теме. */
internal fun coverGlow(hs: FloatArray): Color = hslToColor(hs[0], hs[1].coerceIn(0.6f, 0.95f), 0.55f, 1f)

/**
 * Цвет свечения плеера и мини-плеера. «По обложке» (Настройки → Оформление → Свечение) — цвет обложки играющего трека,
 * взятый по краю обложки (а не самый частый цвет всей картинки), плавно перетекающий при смене трека; «По цвету системы» или обложка без цвета — основной цвет темы.
 */
@Composable
internal fun rememberGlowColor(url: String?): Color {
    val fallback = MaterialTheme.colorScheme.primary
    val hs = rememberCoverHue(url, ThemeSettings.glowFromCover && ThemeSettings.glowPlayer && !ThemeSettings.optimize, edge = true)
    return animateColorAsState(if (hs != null) coverGlow(hs) else fallback, tween(600), label = "glowColor").value
}

private fun hslToColor(h: Float, s: Float, l: Float, a: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = (h % 360f) / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color((r1 + m).coerceIn(0f, 1f), (g1 + m).coerceIn(0f, 1f), (b1 + m).coerceIn(0f, 1f), a)
}
