package app.ymusic.m3e.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * VK отдаёт аудио как HLS (m3u8), чаще всего с шифрованием AES-128 и сегментами MPEG-TS.
 *  - Для воспроизведения достаточно ExoPlayer (модуль HLS): он сам читает ключи и расшифровывает сегменты.
 *    Здесь выбирается вариант потока по настройке качества, если плейлист «главный» (несколько битрейтов).
 *  - Для скачивания сегменты собираются, расшифровываются и «вынимаются» из TS в обычный mp3 / aac.
 */
object VkHls {
    class Variant(val url: String, val bandwidth: Long)

    private fun get(http: OkHttpClient, url: String, ua: String): ByteArray =
        http.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { r ->
            if (!r.isSuccessful) error("HLS: сервер вернул HTTP ${r.code}")
            r.body?.bytes() ?: error("HLS: пустой ответ")
        }

    private fun resolve(base: String, ref: String): String =
        runCatching { URI(base).resolve(ref).toString() }.getOrDefault(ref)

    private val attr = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")

    private fun attrs(line: String): Map<String, String> =
        attr.findAll(line.substringAfter(':')).associate { it.groupValues[1] to it.groupValues[2].trim('"') }

    /**
     * Если [url] — главный плейлист с несколькими вариантами, возвращает ссылку на лучший (при [high]) или самый лёгкий
     * вариант; иначе (обычный плейлист, не m3u8, любая ошибка) — исходную ссылку.
     */
    fun pickVariant(http: OkHttpClient, url: String, ua: String, high: Boolean): String {
        if (!url.contains(".m3u8")) return url
        return try {
            val text = String(get(http, url, ua), Charsets.UTF_8)
            if (!text.contains("#EXT-X-STREAM-INF")) return url
            val variants = ArrayList<Variant>()
            val lines = text.lines()
            for (i in lines.indices) {
                val l = lines[i].trim()
                if (!l.startsWith("#EXT-X-STREAM-INF")) continue
                val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }?.trim() ?: continue
                variants += Variant(resolve(url, next), attrs(l)["BANDWIDTH"]?.toLongOrNull() ?: 0L)
            }
            if (variants.isEmpty()) url
            else (if (high) variants.maxByOrNull { it.bandwidth } else variants.minByOrNull { it.bandwidth })!!.url
        } catch (e: Exception) {
            url
        }
    }

    private class Key(val uri: String, val iv: ByteArray?)

    private class Segment(val url: String, val seq: Long, val key: Key?)

    private fun parseMedia(base: String, text: String): List<Segment> {
        val out = ArrayList<Segment>()
        var seq = 0L
        var key: Key? = null
        for (raw in text.lines()) {
            val l = raw.trim()
            when {
                l.startsWith("#EXT-X-MEDIA-SEQUENCE") -> seq = l.substringAfter(':').trim().toLongOrNull() ?: 0L
                l.startsWith("#EXT-X-KEY") -> {
                    val a = attrs(l)
                    key = if (a["METHOD"] == "AES-128" && !a["URI"].isNullOrBlank()) {
                        Key(resolve(base, a["URI"]!!), a["IV"]?.let(::hex))
                    } else null
                }
                l.isNotEmpty() && !l.startsWith("#") -> { out += Segment(resolve(base, l), seq, key); seq++ }
            }
        }
        return out
    }

    private fun hex(s: String): ByteArray {
        val h = s.removePrefix("0x").removePrefix("0X").padStart(32, '0')
        return ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun seqIv(seq: Long): ByteArray = ByteArray(16).also {
        for (i in 0 until 8) it[15 - i] = ((seq shr (8 * i)) and 0xFF).toByte()
    }

    /**
     * Скачивает трек по ссылке на m3u8 во временный файл [dest] и возвращает расширение: "mp3" или "aac".
     * [onProgress] получает долю 0..1 по числу сегментов; [cancelled] проверяется между сегментами (бросает исключение отмены снаружи).
     */
    fun download(http: OkHttpClient, url: String, ua: String, dest: File, high: Boolean, onProgress: (Float) -> Unit, check: () -> Unit): String {
        val mediaUrl = pickVariant(http, url, ua, high)
        val playlist = String(get(http, mediaUrl, ua), Charsets.UTF_8)
        val segs = parseMedia(mediaUrl, playlist)
        if (segs.isEmpty()) error("HLS: в плейлисте нет сегментов")
        val keys = HashMap<String, ByteArray>()
        val demux = TsAudioDemuxer()
        dest.outputStream().buffered(256 * 1024).use { out ->
            for ((i, s) in segs.withIndex()) {
                check()
                var data = get(http, s.url, ua)
                val k = s.key
                if (k != null) {
                    val keyBytes = keys.getOrPut(k.uri) { get(http, k.uri, ua) }
                    val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
                    c.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(k.iv ?: seqIv(s.seq)))
                    data = c.doFinal(data)
                }
                demux.feed(data, out)
                onProgress((i + 1f) / segs.size)
            }
        }
        return demux.extension()
    }

    /**
     * Вынимает аудио из MPEG-TS: у пакетов нужного потока отбрасывает заголовки PES и пишет «сырой» поток
     * (mp3-кадры или ADTS-AAC). Если в данных нет TS (сегменты уже mp3/aac), пишет их как есть.
     */
    private class TsAudioDemuxer {
        private var audioPid = -1
        private var firstBytes: ByteArray? = null
        private var raw = false

        fun extension(): String {
            val b = firstBytes ?: return "mp3"
            if (b.size >= 2 && (b[0].toInt() and 0xFF) == 0xFF && (b[1].toInt() and 0xF6) == 0xF0) return "aac"
            return "mp3"
        }

        private fun remember(buf: ByteArray, off: Int, len: Int) {
            if (firstBytes == null && len >= 2) firstBytes = buf.copyOfRange(off, off + minOf(len, 4))
        }

        fun feed(seg: ByteArray, out: OutputStream) {
            if (seg.isEmpty()) return
            if (seg[0] != 0x47.toByte()) {
                // Не TS: чистый mp3 / aac.
                raw = true
                remember(seg, 0, seg.size)
                out.write(seg)
                return
            }
            var p = 0
            while (p + 188 <= seg.size) {
                if (seg[p] != 0x47.toByte()) { p++; continue }
                val b1 = seg[p + 1].toInt() and 0xFF
                val b2 = seg[p + 2].toInt() and 0xFF
                val b3 = seg[p + 3].toInt() and 0xFF
                val start = (b1 and 0x40) != 0
                val pid = ((b1 and 0x1F) shl 8) or b2
                val afc = (b3 shr 4) and 0x3
                var off = p + 4
                if (afc == 2) { p += 188; continue }
                if (afc == 3) off += 1 + (seg[off].toInt() and 0xFF)
                val end = p + 188
                if (off >= end) { p += 188; continue }
                if (start) {
                    val isPes = off + 9 <= end && seg[off] == 0.toByte() && seg[off + 1] == 0.toByte() && seg[off + 2] == 1.toByte()
                    if (isPes) {
                        val sid = seg[off + 3].toInt() and 0xFF
                        if (audioPid < 0 && sid in 0xC0..0xDF) audioPid = pid
                        if (pid == audioPid) {
                            val hdr = seg[off + 8].toInt() and 0xFF
                            val s = off + 9 + hdr
                            if (s < end) { remember(seg, s, end - s); out.write(seg, s, end - s) }
                        }
                    }
                } else if (pid == audioPid) {
                    remember(seg, off, end - off)
                    out.write(seg, off, end - off)
                }
                p += 188
            }
        }
    }
}
