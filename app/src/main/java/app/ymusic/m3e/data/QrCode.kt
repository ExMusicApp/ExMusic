package app.ymusic.m3e.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Генератор QR-кодов без сторонних библиотек: режим «байты», коррекция ошибок уровня M, версии 1–10
 * (до 213 байт — для ссылок music.yandex.ru этого с запасом). Алгоритм проверен декодером на всех версиях.
 */
object QrCode {
    private val ECC_PER_BLOCK = intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26)
    private val NUM_BLOCKS = intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5)

    private fun rawModules(v: Int): Int {
        var r = (16 * v + 128) * v + 64
        if (v >= 2) {
            val n = v / 7 + 2
            r -= (25 * n - 10) * n - 55
            if (v >= 7) r -= 36
        }
        return r
    }

    private fun dataCodewords(v: Int): Int = rawModules(v) / 8 - ECC_PER_BLOCK[v] * NUM_BLOCKS[v]

    private fun gfMul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun divisor(degree: Int): IntArray {
        val r = IntArray(degree)
        r[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in r.indices) {
                r[j] = gfMul(r[j], root)
                if (j + 1 < r.size) r[j] = r[j] xor r[j + 1]
            }
            root = gfMul(root, 2)
        }
        return r
    }

    private fun remainder(data: IntArray, div: IntArray): IntArray {
        val r = IntArray(div.size)
        for (b in data) {
            val f = b xor r[0]
            for (i in 0 until r.size - 1) r[i] = r[i + 1]
            r[r.size - 1] = 0
            for (i in div.indices) r[i] = r[i] xor gfMul(div[i], f)
        }
        return r
    }

    /** Матрица модулей (true — тёмный) или null, если текст не помещается. */
    fun encode(text: String): Array<BooleanArray>? {
        val data = text.toByteArray(Charsets.UTF_8)
        var ver = 0
        for (v in 1..10) {
            val cc = if (v < 10) 8 else 16
            if (4 + cc + 8 * data.size <= dataCodewords(v) * 8) { ver = v; break }
        }
        if (ver == 0) return null
        val ccBits = if (ver < 10) 8 else 16

        val bits = ArrayList<Int>()
        fun put(value: Int, n: Int) { for (i in n - 1 downTo 0) bits.add((value ushr i) and 1) }
        put(4, 4)
        put(data.size, ccBits)
        for (b in data) put(b.toInt() and 0xFF, 8)
        val cap = dataCodewords(ver) * 8
        put(0, minOf(4, cap - bits.size))
        while (bits.size % 8 != 0) bits.add(0)
        val cw = ArrayList<Int>()
        var bi = 0
        while (bi < bits.size) {
            var v = 0
            for (j in 0 until 8) v = v or (bits[bi + j] shl (7 - j))
            cw.add(v)
            bi += 8
        }
        var pad = 0xEC
        while (cw.size < dataCodewords(ver)) { cw.add(pad); pad = pad xor 0xEC xor 0x11 }

        // Коррекция ошибок и перемежение блоков.
        val nb = NUM_BLOCKS[ver]
        val el = ECC_PER_BLOCK[ver]
        val raw = rawModules(ver) / 8
        val ns = nb - raw % nb
        val sl = raw / nb
        val div = divisor(el)
        val blocks = ArrayList<IntArray>()
        var k = 0
        for (i in 0 until nb) {
            val dl = sl - el + (if (i < ns) 0 else 1)
            val dat = IntArray(dl) { cw[k + it] }
            k += dl
            val ecc = remainder(dat, div)
            val full = IntArray(sl + 1)
            for (j in dat.indices) full[j] = dat[j]
            // У коротких блоков в позиции sl-el стоит заглушка (при перемежении пропускается).
            for (j in ecc.indices) full[sl + 1 - el + j] = ecc[j]
            blocks.add(full)
        }
        val all = ArrayList<Int>()
        for (i in 0..sl) {
            for (j in 0 until nb) {
                if (i != sl - el || j >= ns) all.add(blocks[j][i])
            }
        }

        val size = ver * 4 + 17
        val mod = Array(size) { BooleanArray(size) }
        val fn = Array(size) { BooleanArray(size) }
        fun setf(x: Int, y: Int, d: Boolean) { mod[y][x] = d; fn[y][x] = true }

        for (i in 0 until size) { setf(6, i, i % 2 == 0); setf(i, 6, i % 2 == 0) }
        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = maxOf(Math.abs(dx), Math.abs(dy))
                val xx = cx + dx
                val yy = cy + dy
                if (xx in 0 until size && yy in 0 until size) setf(xx, yy, dist != 2 && dist != 4)
            }
        }
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
        if (ver > 1) {
            val n = ver / 7 + 2
            val step = if (ver == 32) 26 else (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2
            val pos = IntArray(n)
            pos[0] = 6
            for (i in 1 until n) pos[i] = size - 7 - (n - 1 - i) * step
            for (i in 0 until n) for (j in 0 until n) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                for (dy in -2..2) for (dx in -2..2) setf(pos[i] + dx, pos[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
        }
        fun format(mask: Int) {
            var r = mask
            for (i in 0 until 10) r = (r shl 1) xor ((r ushr 9) * 0x537)
            val b = ((mask shl 10) or r) xor 0x5412
            fun g(i: Int) = ((b ushr i) and 1) != 0
            for (i in 0..5) setf(8, i, g(i))
            setf(8, 7, g(6)); setf(8, 8, g(7)); setf(7, 8, g(8))
            for (i in 9..14) setf(14 - i, 8, g(i))
            for (i in 0..7) setf(size - 1 - i, 8, g(i))
            for (i in 8..14) setf(8, size - 15 + i, g(i))
            setf(8, size - 8, true)
        }
        format(0)
        if (ver >= 7) {
            var r = ver
            for (i in 0 until 12) r = (r shl 1) xor ((r ushr 11) * 0x1F25)
            val b = (ver shl 12) or r
            for (i in 0 until 18) {
                val c = ((b ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val bb = i / 3
                setf(a, bb, c); setf(bb, a, c)
            }
        }
        // Размещение данных змейкой.
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0..1) {
                    val x = right - j
                    val up = ((right + 1) and 2) == 0
                    val y = if (up) size - 1 - vert else vert
                    if (!fn[y][x] && i < all.size * 8) {
                        mod[y][x] = ((all[i shr 3] ushr (7 - (i and 7))) and 1) != 0
                        i++
                    }
                }
            }
            right -= 2
        }
        fun apply(m: Int) {
            for (y in 0 until size) for (x in 0 until size) {
                if (fn[y][x]) continue
                val inv = when (m) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (inv) mod[y][x] = !mod[y][x]
            }
        }
        fun penalty(): Int {
            var p = 0
            for (t in 0 until 2) for (a in 0 until size) {
                fun at(b: Int) = if (t == 0) mod[a][b] else mod[b][a]
                var run = 1
                for (b in 1 until size) {
                    if (at(b) == at(b - 1)) {
                        run++
                        if (run == 5) p += 3 else if (run > 5) p += 1
                    } else run = 1
                }
                val sb = StringBuilder(size)
                for (b in 0 until size) sb.append(if (at(b)) '1' else '0')
                val s = sb.toString()
                for (pat in arrayOf("10111010000", "00001011101")) {
                    var st = 0
                    while (true) {
                        val f = s.indexOf(pat, st)
                        if (f < 0) break
                        p += 40
                        st = f + 1
                    }
                }
            }
            for (y in 0 until size - 1) for (x in 0 until size - 1) {
                val c = mod[y][x]
                if (c == mod[y][x + 1] && c == mod[y + 1][x] && c == mod[y + 1][x + 1]) p += 3
            }
            var dark = 0
            for (y in 0 until size) for (x in 0 until size) if (mod[y][x]) dark++
            val tot = size * size
            val kk = (Math.abs(dark * 20 - tot * 10) + tot - 1) / tot - 1
            p += maxOf(kk, 0) * 10
            return p
        }
        var best = 0
        var bestPenalty = Int.MAX_VALUE
        for (m in 0 until 8) {
            apply(m); format(m)
            val pp = penalty()
            apply(m)
            if (pp < bestPenalty) { bestPenalty = pp; best = m }
        }
        apply(best); format(best)
        return mod
    }

    /**
     * Картинка QR-кода: модули цветом [dark] на фоне [light], вокруг — тихая зона [quiet] модулей.
     * Модули слегка скруглены. Размер стороны — около [targetPx] пикселей (кратно числу модулей).
     */
    fun bitmap(text: String, dark: Int, light: Int, targetPx: Int = 768, quiet: Int = 2): Bitmap? {
        val m = encode(text) ?: return null
        val n = m.size
        val total = n + 2 * quiet
        val cell = (targetPx / total).coerceAtLeast(4)
        val side = cell * total
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(light)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dark }
        val r = cell * 0.28f
        val rect = RectF()
        for (y in 0 until n) for (x in 0 until n) {
            if (!m[y][x]) continue
            val l = (x + quiet) * cell.toFloat()
            val t = (y + quiet) * cell.toFloat()
            // Соседние тёмные модули сливаются: не скругляем стороны, которыми они соприкасаются.
            rect.set(l, t, l + cell, t + cell)
            c.drawRoundRect(rect, r, r, paint)
            if (x + 1 < n && m[y][x + 1]) c.drawRect(l + cell / 2f, t, l + cell, t + cell, paint)
            if (y + 1 < n && m[y + 1][x]) c.drawRect(l, t + cell / 2f, l + cell, t + cell, paint)
        }
        return bmp
    }
}
