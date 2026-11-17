package app.ymusic.m3e.recognition

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin

object ShazamSignature {
    const val SAMPLE_RATE = 16000

    private const val WINDOW = 2048
    private const val HOP = 128
    private const val BINS = WINDOW / 2 + 1
    private const val RING = 256
    private const val DELAY = 46
    private const val MAX_SECONDS = 3.1
    private const val MAX_PEAKS = 255

    class Peak(val pass: Int, val magnitude: Int, val bin: Int)

    class Result(val numberSamples: Int, val bands: List<List<Peak>>) {
        val sampleMs: Int get() = (numberSamples.toLong() * 1000 / SAMPLE_RATE).toInt()
    }

    private val hann = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2.0 * PI * (it + 1) / (WINDOW + 1)) }

    private class Fft(val n: Int) {
        private val cosT = DoubleArray(n / 2) { cos(2.0 * PI * it / n) }
        private val sinT = DoubleArray(n / 2) { sin(2.0 * PI * it / n) }
        private val rev = IntArray(n).also { r ->
            val bits = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
        }

        fun transform(re: DoubleArray, im: DoubleArray) {
            for (i in 0 until n) {
                val j = rev[i]
                if (j > i) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var size = 2
            while (size <= n) {
                val half = size / 2
                val step = n / size
                var start = 0
                while (start < n) {
                    var k = 0
                    for (j in start until start + half) {
                        val wr = cosT[k]; val wi = -sinT[k]
                        val xr = re[j + half] * wr - im[j + half] * wi
                        val xi = re[j + half] * wi + im[j + half] * wr
                        re[j + half] = re[j] - xr; im[j + half] = im[j] - xi
                        re[j] += xr; im[j] += xi
                        k += step
                    }
                    start += size
                }
                size *= 2
            }
        }
    }

    private class FrameRing {
        val data = Array(RING) { DoubleArray(BINS) }
        var pos = 0
        var written = 0
        fun get(offset: Int): DoubleArray = data[Math.floorMod(pos + offset, RING)]
        fun append(frame: DoubleArray) {
            data[pos] = frame
            pos = (pos + 1) % RING
            written++
        }
    }

    fun generate(pcm: ShortArray, start: Int = 0): Result? {
        val fft = Fft(WINDOW)
        val fftRing = FrameRing()
        val spreadRing = FrameRing()
        val window = DoubleArray(WINDOW)
        val re = DoubleArray(WINDOW)
        val im = DoubleArray(WINDOW)
        val bands = List(4) { ArrayList<Peak>() }
        var peakCount = 0
        var samples = 0
        var pos = start

        while (pos + HOP <= pcm.size && (samples.toDouble() / SAMPLE_RATE < MAX_SECONDS || peakCount < MAX_PEAKS)) {
            System.arraycopy(window, HOP, window, 0, WINDOW - HOP)
            for (i in 0 until HOP) window[WINDOW - HOP + i] = pcm[pos + i].toDouble()
            for (i in 0 until WINDOW) { re[i] = window[i] * hann[i]; im[i] = 0.0 }
            fft.transform(re, im)
            val power = DoubleArray(BINS)
            for (i in 0 until BINS) power[i] = max((re[i] * re[i] + im[i] * im[i]) / (1 shl 17), 1e-10)
            fftRing.append(power)

            spread(fftRing, spreadRing)
            if (spreadRing.written >= DELAY) peakCount += recognize(fftRing, spreadRing, bands)

            pos += HOP
            samples += HOP
        }
        if (peakCount == 0) return null
        return Result(samples, bands)
    }

    private val TIME_SPREAD = intArrayOf(-1, -3, -6)

    private fun spread(fftRing: FrameRing, spreadRing: FrameRing) {
        val last = fftRing.get(-1).copyOf()
        for (p in 0 until BINS) {
            if (p < BINS - 2) last[p] = max(last[p], max(last[p + 1], last[p + 2]))
            var m = last[p]
            for (o in TIME_SPREAD) {
                val former = spreadRing.get(o)
                m = max(former[p], m)
                former[p] = m
            }
        }
        spreadRing.append(last)
    }

    private val FREQ_NEIGHBORS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
    private val TIME_NEIGHBORS = intArrayOf(-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249)

    private fun recognize(fftRing: FrameRing, spreadRing: FrameRing, bands: List<MutableList<Peak>>): Int {
        val cur = fftRing.get(-DELAY)
        val ref = spreadRing.get(-49)
        var found = 0
        for (b in 10 until 1015) {
            val v = cur[b]
            if (v < 1.0 / 64 || v < ref[b - 1]) continue
            var maxN = 0.0
            for (o in FREQ_NEIGHBORS) maxN = max(maxN, ref[b + o])
            if (v <= maxN) continue
            for (o in TIME_NEIGHBORS) maxN = max(maxN, spreadRing.get(o)[b - 1])
            if (v <= maxN) continue

            val pass = spreadRing.written - DELAY
            val mag = ln(max(1.0 / 64, v)) * 1477.3 + 6144
            val magBefore = ln(max(1.0 / 64, cur[b - 1])) * 1477.3 + 6144
            val magAfter = ln(max(1.0 / 64, cur[b + 1])) * 1477.3 + 6144
            val var1 = mag * 2 - magBefore - magAfter
            if (var1 <= 0) continue
            val var2 = (magAfter - magBefore) * 32 / var1
            val corrected = (b * 64 + var2).toInt()
            val hz = corrected * (SAMPLE_RATE / 2.0 / 1024 / 64)
            val band = when {
                hz < 250 -> continue
                hz < 520 -> 0
                hz < 1450 -> 1
                hz < 3500 -> 2
                hz <= 5500 -> 3
                else -> continue
            }
            bands[band].add(Peak(pass, mag.toInt().coerceIn(0, 65535), corrected.coerceIn(0, 65535)))
            found++
        }
        return found
    }

    fun encode(r: Result): ByteArray {
        val body = java.io.ByteArrayOutputStream()
        for ((band, peaks) in r.bands.withIndex()) {
            if (peaks.isEmpty()) continue
            val pb = java.io.ByteArrayOutputStream()
            var pass = 0
            for (p in peaks) {
                if (p.pass - pass >= 255) {
                    pb.write(0xFF)
                    pb.write(le32(p.pass))
                    pass = p.pass
                }
                pb.write(p.pass - pass)
                pb.write(le16(p.magnitude))
                pb.write(le16(p.bin))
                pass = p.pass
            }
            val data = pb.toByteArray()
            body.write(le32(0x60030040 + band))
            body.write(le32(data.size))
            body.write(data)
            repeat((4 - data.size % 4) % 4) { body.write(0) }
        }
        val contents = body.toByteArray()

        val header = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0xcafe2580L.toInt())
        header.putInt(0)
        header.putInt(contents.size + 8)
        header.putInt(0x94119c00L.toInt())
        header.putInt(0); header.putInt(0); header.putInt(0)
        header.putInt(3 shl 27)
        header.putInt(0); header.putInt(0)
        header.putInt((r.numberSamples + SAMPLE_RATE * 0.24).toInt())
        header.putInt((15 shl 19) + 0x40000)

        val out = ByteBuffer.allocate(48 + 8 + contents.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put(header.array())
        out.putInt(0x40000000)
        out.putInt(contents.size + 8)
        out.put(contents)
        val bytes = out.array()
        val crc = CRC32().also { it.update(bytes, 8, bytes.size - 8) }.value
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(4, crc.toInt())
        return bytes
    }

    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun le16(v: Int) = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array()

    fun resampleTo16k(src: ShortArray, fromRate: Int): ShortArray {
        if (fromRate == SAMPLE_RATE) return src
        val ratio = fromRate.toDouble() / SAMPLE_RATE
        val outLen = (src.size / ratio).toInt()
        val half = 24
        val fc = 0.45 / ratio
        val out = ShortArray(outLen)
        for (n in 0 until outLen) {
            val t = n * ratio
            val i0 = floor(t).toInt()
            var acc = 0.0
            for (i in (i0 - half + 1)..(i0 + half)) {
                if (i < 0 || i >= src.size) continue
                val u = i - t
                val s = if (u == 0.0) 2 * fc else sin(2 * PI * fc * u) / (PI * u)
                val w = 0.5 + 0.5 * cos(PI * u / half)
                acc += src[i] * s * w
            }
            out[n] = acc.coerceIn(-32768.0, 32767.0).toInt().toShort()
        }
        return out
    }
}
