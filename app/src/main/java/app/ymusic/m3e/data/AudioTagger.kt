package app.ymusic.m3e.data

import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * Записывает в скачанный файл теги и обложку: название, исполнитель, альбом, год, жанр, лейбл, картинка.
 *  - mp3 — ID3v2.3 (старый ID3-блок из исходного файла вырезается, чтобы не было двух);
 *  - flac — блоки VORBIS_COMMENT и PICTURE;
 *  - m4a — атомы ilst внутри moov/udta/meta (смещения stco/co64 пересчитываются).
 * Если формат не распознан или разбор не удался, файл копируется как есть.
 */
object AudioTagger {

    /** Пишет в [out] содержимое [src] с тегами. [cover] — JPEG/PNG-байты или null. */
    fun write(src: File, out: OutputStream, ext: String, t: Track, cover: ByteArray?) {
        when (ext) {
            "mp3" -> tagMp3(src, out, t, cover)
            "flac" -> tagFlac(src, out, t, cover)
            "m4a" -> tagM4a(src, out, t, cover)
            else -> copyAll(src, out)
        }
    }

    // ---------- Общее ----------

    private fun copyAll(src: File, out: OutputStream) {
        src.inputStream().use { it.copyTo(out, 64 * 1024) }
    }

    private fun isPng(b: ByteArray) = b.size > 8 && b[0] == 0x89.toByte() && b[1] == 0x50.toByte()
    private fun mimeOf(b: ByteArray) = if (isPng(b)) "image/png" else "image/jpeg"

    private fun imageSize(b: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeByteArray(b, 0, b.size, o) }
        return o.outWidth.coerceAtLeast(0) to o.outHeight.coerceAtLeast(0)
    }

    private fun InputStream.readFully(n: Int): ByteArray? {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = read(buf, off, n - off)
            if (r < 0) return null
            off += r
        }
        return buf
    }

    private fun usable(s: String?) = !s.isNullOrBlank()

    // ---------- MP3 / ID3v2.3 ----------

    private fun tagMp3(src: File, out: OutputStream, t: Track, cover: ByteArray?) {
        val tag = id3(t, cover)
        src.inputStream().buffered().use { raw ->
            // Пропускаем уже имеющийся ID3v2-блок исходного файла.
            raw.mark(16)
            val head = raw.readFully(10)
            var skip = 0L
            if (head != null && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                val size = ((head[6].toInt() and 0x7f) shl 21) or ((head[7].toInt() and 0x7f) shl 14) or
                    ((head[8].toInt() and 0x7f) shl 7) or (head[9].toInt() and 0x7f)
                val footer = if ((head[5].toInt() and 0x10) != 0) 10 else 0
                skip = (10 + size + footer).toLong()
            }
            raw.reset()
            var left = skip
            while (left > 0) {
                val s = raw.skip(left)
                if (s <= 0) break
                left -= s
            }
            out.write(tag)
            raw.copyTo(out, 64 * 1024)
        }
    }

    private fun id3(t: Track, cover: ByteArray?): ByteArray {
        fun frame(id: String, data: ByteArray): ByteArray {
            val head = ByteArray(10)
            id.toByteArray(Charsets.ISO_8859_1).copyInto(head)
            head[4] = (data.size ushr 24).toByte(); head[5] = (data.size ushr 16).toByte()
            head[6] = (data.size ushr 8).toByte(); head[7] = data.size.toByte()
            return head + data
        }
        fun text(id: String, s: String?): ByteArray {
            if (!usable(s)) return ByteArray(0)
            return frame(id, byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + s!!.toByteArray(Charsets.UTF_16LE))
        }
        val pic = if (cover != null && cover.isNotEmpty()) {
            val data = ByteArrayOutputStream().apply {
                write(0)                                              // кодировка ISO-8859-1 для mime и описания
                write(mimeOf(cover).toByteArray(Charsets.ISO_8859_1)); write(0)
                write(3)                                              // тип: обложка (front)
                write(0)                                              // пустое описание
                write(cover)
            }.toByteArray()
            frame("APIC", data)
        } else ByteArray(0)
        val body = text("TIT2", t.title + (t.version?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")) +
            text("TPE1", t.artists) + text("TPE2", t.artists) + text("TALB", t.albumTitle) +
            text("TYER", t.year.takeIf { it > 0 }?.toString()) + text("TCON", t.genre) + text("TPUB", t.label) + pic
        if (body.isEmpty()) return body
        val n = body.size
        val header = byteArrayOf(
            0x49, 0x44, 0x33, 3, 0, 0,
            ((n shr 21) and 0x7f).toByte(), ((n shr 14) and 0x7f).toByte(), ((n shr 7) and 0x7f).toByte(), (n and 0x7f).toByte(),
        )
        return header + body
    }

    // ---------- FLAC ----------

    private fun tagFlac(src: File, out: OutputStream, t: Track, cover: ByteArray?) {
        src.inputStream().buffered().use { inp ->
            val magic = inp.readFully(4)
            if (magic == null || String(magic, Charsets.ISO_8859_1) != "fLaC") { copyAll(src, out); return }
            val keep = ArrayList<Pair<Int, ByteArray>>()
            var last = false
            while (!last) {
                val h = inp.readFully(4) ?: break
                last = (h[0].toInt() and 0x80) != 0
                val type = h[0].toInt() and 0x7f
                val len = ((h[1].toInt() and 0xff) shl 16) or ((h[2].toInt() and 0xff) shl 8) or (h[3].toInt() and 0xff)
                val data = inp.readFully(len) ?: break
                // Старые комментарии, картинки и padding выбрасываем.
                if (type != 4 && type != 6 && type != 1) keep += type to data
            }
            keep += 4 to vorbisComment(t)
            if (cover != null && cover.isNotEmpty()) keep += 6 to flacPicture(cover)
            out.write(magic)
            keep.forEachIndexed { i, (type, data) ->
                val flag = if (i == keep.lastIndex) 0x80 else 0
                out.write(flag or type)
                out.write((data.size ushr 16) and 0xff); out.write((data.size ushr 8) and 0xff); out.write(data.size and 0xff)
                out.write(data)
            }
            inp.copyTo(out, 64 * 1024)
        }
    }

    private fun le32(n: Int) = byteArrayOf(n.toByte(), (n ushr 8).toByte(), (n ushr 16).toByte(), (n ushr 24).toByte())

    private fun vorbisComment(t: Track): ByteArray {
        val fields = ArrayList<String>()
        fun add(k: String, v: String?) { if (usable(v)) fields += "$k=$v" }
        add("TITLE", t.title + (t.version?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""))
        add("ARTIST", t.artists); add("ALBUMARTIST", t.artists); add("ALBUM", t.albumTitle)
        add("DATE", t.year.takeIf { it > 0 }?.toString()); add("GENRE", t.genre); add("LABEL", t.label)
        val vendor = "ExMusic".toByteArray(Charsets.UTF_8)
        val o = ByteArrayOutputStream()
        o.write(le32(vendor.size)); o.write(vendor); o.write(le32(fields.size))
        fields.forEach { val b = it.toByteArray(Charsets.UTF_8); o.write(le32(b.size)); o.write(b) }
        return o.toByteArray()
    }

    private fun flacPicture(img: ByteArray): ByteArray {
        val (w, h) = imageSize(img)
        val mime = mimeOf(img).toByteArray(Charsets.ISO_8859_1)
        val bo = ByteArrayOutputStream()
        DataOutputStream(bo).apply {
            writeInt(3)                       // обложка (front)
            writeInt(mime.size); write(mime)
            writeInt(0)                       // описание пустое
            writeInt(w); writeInt(h); writeInt(24); writeInt(0)
            writeInt(img.size); write(img)
        }
        return bo.toByteArray()
    }

    // ---------- M4A / MP4 ----------

    private class Box(val type: String, val offset: Long, val size: Long)

    private fun u32(b: ByteArray, p: Int) =
        ((b[p].toLong() and 0xff) shl 24) or ((b[p + 1].toLong() and 0xff) shl 16) or ((b[p + 2].toLong() and 0xff) shl 8) or (b[p + 3].toLong() and 0xff)

    private fun u64(b: ByteArray, p: Int) = (u32(b, p) shl 32) or u32(b, p + 4)

    private fun put32(b: ByteArray, p: Int, v: Long) {
        b[p] = (v ushr 24).toByte(); b[p + 1] = (v ushr 16).toByte(); b[p + 2] = (v ushr 8).toByte(); b[p + 3] = v.toByte()
    }

    private fun put64(b: ByteArray, p: Int, v: Long) { put32(b, p, v ushr 32); put32(b, p + 4, v and 0xffffffffL) }

    private fun typeAt(b: ByteArray, p: Int) = String(b, p, 4, Charsets.ISO_8859_1)

    private fun topBoxes(f: RandomAccessFile): List<Box>? {
        val res = ArrayList<Box>()
        var pos = 0L
        val len = f.length()
        val h = ByteArray(16)
        while (pos + 8 <= len) {
            f.seek(pos)
            f.readFully(h, 0, 8)
            var size = u32(h, 0)
            val type = typeAt(h, 4)
            if (size == 1L) { f.readFully(h, 8, 8); size = u64(h, 8) }
            else if (size == 0L) size = len - pos
            if (size < 8 || pos + size > len) return null
            res += Box(type, pos, size)
            pos += size
        }
        return res
    }

    private fun box(type: ByteArray, payload: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        DataOutputStream(o).apply { writeInt(payload.size + 8); write(type); write(payload) }
        return o.toByteArray()
    }

    private fun box(type: String, payload: ByteArray) = box(type.toByteArray(Charsets.ISO_8859_1), payload)

    private fun ilstItem(name: ByteArray, flags: Int, payload: ByteArray): ByteArray {
        val d = ByteArrayOutputStream().apply {
            DataOutputStream(this).apply { writeInt(flags); writeInt(0); write(payload) }
        }.toByteArray()
        return box(name, box("data", d))
    }

    private fun ilstText(code: String, s: String?): ByteArray {
        if (!usable(s)) return ByteArray(0)
        // «©» — байт 0xA9
        val name = code.map { if (it == '@') 0xA9.toByte() else it.code.toByte() }.toByteArray()
        return ilstItem(name, 1, s!!.toByteArray(Charsets.UTF_8))
    }

    private fun buildUdta(t: Track, cover: ByteArray?): ByteArray {
        val items = ByteArrayOutputStream()
        items.write(ilstText("@nam", t.title + (t.version?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")))
        items.write(ilstText("@ART", t.artists))
        items.write(ilstText("aART", t.artists))
        items.write(ilstText("@alb", t.albumTitle))
        items.write(ilstText("@day", t.year.takeIf { it > 0 }?.toString()))
        items.write(ilstText("@gen", t.genre))
        if (cover != null && cover.isNotEmpty()) {
            items.write(ilstItem("covr".toByteArray(Charsets.ISO_8859_1), if (isPng(cover)) 14 else 13, cover))
        }
        val ilst = box("ilst", items.toByteArray())
        val hdlr = box(
            "hdlr",
            ByteArrayOutputStream().apply {
                DataOutputStream(this).apply {
                    writeInt(0); writeInt(0); write("mdir".toByteArray()); write("appl".toByteArray())
                    writeInt(0); writeInt(0); write(0)
                }
            }.toByteArray(),
        )
        val meta = box("meta", byteArrayOf(0, 0, 0, 0) + hdlr + ilst)
        return box("udta", meta)
    }

    /** Сдвигает смещения чанков в stco/co64 на [delta], если они указывают после старого moov. */
    private fun shiftOffsets(b: ByteArray, start: Int, end: Int, oldMoovEnd: Long, delta: Long) {
        var p = start
        while (p + 8 <= end) {
            val size = u32(b, p).toInt()
            if (size < 8 || p + size > end) return
            when (typeAt(b, p + 4)) {
                "trak", "mdia", "minf", "stbl", "edts" -> shiftOffsets(b, p + 8, p + size, oldMoovEnd, delta)
                "stco" -> {
                    val n = u32(b, p + 12).toInt()
                    for (i in 0 until n) {
                        val q = p + 16 + i * 4
                        if (q + 4 > p + size) break
                        val v = u32(b, q)
                        if (v >= oldMoovEnd) put32(b, q, v + delta)
                    }
                }
                "co64" -> {
                    val n = u32(b, p + 12).toInt()
                    for (i in 0 until n) {
                        val q = p + 16 + i * 8
                        if (q + 8 > p + size) break
                        val v = u64(b, q)
                        if (v >= oldMoovEnd) put64(b, q, v + delta)
                    }
                }
            }
            p += size
        }
    }

    private fun tagM4a(src: File, out: OutputStream, t: Track, cover: ByteArray?) {
        RandomAccessFile(src, "r").use { f ->
            val boxes = topBoxes(f)
            val moov = boxes?.firstOrNull { it.type == "moov" }
            if (boxes == null || moov == null || moov.size > 64L * 1024 * 1024) { copyAll(src, out); return }
            val hdrLen = if (moov.size > 0xffffffffL) 16 else 8
            val old = ByteArray(moov.size.toInt())
            f.seek(moov.offset); f.readFully(old)
            // Дети moov без старого udta + новый udta.
            val kids = ByteArrayOutputStream()
            var p = hdrLen
            while (p + 8 <= old.size) {
                val sz = u32(old, p).toInt()
                if (sz < 8 || p + sz > old.size) { copyAll(src, out); return }
                if (typeAt(old, p + 4) != "udta") kids.write(old, p, sz)
                p += sz
            }
            kids.write(buildUdta(t, cover))
            val body = kids.toByteArray()
            val newMoov = ByteArray(body.size + 8)
            put32(newMoov, 0, newMoov.size.toLong())
            "moov".toByteArray(Charsets.ISO_8859_1).copyInto(newMoov, 4)
            body.copyInto(newMoov, 8)
            val delta = newMoov.size.toLong() - moov.size
            shiftOffsets(newMoov, 8, newMoov.size, moov.offset + moov.size, delta)

            val buf = ByteArray(64 * 1024)
            for (b in boxes) {
                if (b === moov) { out.write(newMoov); continue }
                f.seek(b.offset)
                var left = b.size
                while (left > 0) {
                    val r = f.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (r < 0) break
                    out.write(buf, 0, r)
                    left -= r
                }
            }
        }
    }
}
