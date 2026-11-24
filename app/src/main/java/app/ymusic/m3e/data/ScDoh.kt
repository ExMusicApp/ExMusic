package app.ymusic.m3e.data

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * DNS поверх HTTPS (RFC 8484): имена SoundCloud узнаём у публичных DoH-серверов, а не у DNS провайдера,
 * который подменяет ответы для заблокированных сайтов. Серверы адресуются по IP (без SNI и без обращения к системному DNS).
 */
internal object ScDoh {
    private const val TAG = "ScDoh"

    private val SERVERS = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/dns-query",
        "https://9.9.9.9/dns-query",
        "https://1.0.0.1/dns-query",
        "https://8.8.4.4/dns-query",
    )

    /** Обычный DNS по UDP: запасной путь на случай, если DoH до серверов не доходит (его провайдер тоже иногда режет). */
    private val UDP_SERVERS = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "77.88.8.8")

    private class Entry(val addrs: List<InetAddress>, val expires: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "sc-doh").apply { isDaemon = true } }

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .callTimeout(4500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    private class Answer(val addrs: List<InetAddress>, val ttl: Long)

    /**
     * Адреса хоста: сначала DoH (с кэшем), потом обычный DNS по UDP к публичным серверам, потом устаревший кэш и в самом конце
     * системный DNS. Первый в списке — самый свежий удачный.
     */
    fun resolve(host: String): List<InetAddress> {
        literal(host)?.let { return listOf(it) }
        val key = host.lowercase()
        val cached = cache[key]
        if (cached != null && cached.expires > System.currentTimeMillis()) return cached.addrs
        val fresh = lookup(key)
        if (fresh != null) {
            val ttl = fresh.ttl.coerceIn(60L, 600L)
            cache[key] = Entry(fresh.addrs, System.currentTimeMillis() + ttl * 1000L)
            return fresh.addrs
        }
        if (cached != null) {
            AppLog.w(TAG, "$key: DNS недоступен, берём прошлые адреса")
            return cached.addrs
        }
        // Последняя надежда: системный DNS (если он подменяет адрес, соединение просто не установится).
        val sys = InetAddress.getAllByName(host).toList()
        if (sys.isEmpty()) throw IOException("DNS не вернул адресов для $host")
        return sys
    }

    /** Сначала DoH, при неудаче — UDP-DNS. null, если не ответил никто. */
    private fun lookup(host: String): Answer? {
        for (viaDoh in booleanArrayOf(true, false)) {
            try {
                val a = ask(host, 1, viaDoh).takeIf { it.addrs.isNotEmpty() } ?: ask(host, 28, viaDoh)
                if (a.addrs.isNotEmpty()) return a
            } catch (e: Exception) {
                AppLog.w(TAG, "${if (viaDoh) "DoH" else "UDP-DNS"} для $host не сработал: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return null
    }

    /** Запомнить адрес, к которому удалось подключиться: в следующий раз пробуем его первым. */
    fun promote(host: String, addr: InetAddress) {
        val key = host.lowercase()
        val e = cache[key] ?: return
        if (e.addrs.firstOrNull() == addr) return
        cache[key] = Entry(listOf(addr) + e.addrs.filter { it != addr }, e.expires)
    }

    /** Адрес не помог (рукопожатие не прошло ни одним приёмом): в следующий раз пробуем остальные адреса раньше него. */
    fun demote(host: String, addr: InetAddress) {
        val key = host.lowercase()
        val e = cache[key] ?: return
        if (e.addrs.size < 2 || e.addrs.first() != addr) return
        cache[key] = Entry(e.addrs.filter { it != addr } + addr, e.expires)
    }

    fun clearCache() = cache.clear()

    /** IP-адрес, записанный в виде текста, возвращает как есть, имена — null (без обращения к сети). */
    private fun literal(host: String): InetAddress? {
        val h = host.removePrefix("[").removeSuffix("]")
        val v4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(h)
        val v6 = h.contains(':') && Regex("""^[0-9a-fA-F:.%]+$""").matches(h)
        return if (v4 || v6) runCatching { InetAddress.getByName(h) }.getOrNull() else null
    }

    private fun ask(host: String, type: Int, viaDoh: Boolean): Answer {
        val tasks: List<Callable<Answer>> = if (viaDoh) {
            val dns = Base64.encodeToString(buildQuery(host, type), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            SERVERS.map { base ->
                Callable {
                    val req = Request.Builder().url("$base?dns=$dns").header("Accept", "application/dns-message").build()
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                        val bytes = r.body?.bytes() ?: throw IOException("пустой ответ")
                        parse(bytes)
                    }
                }
            }
        } else {
            UDP_SERVERS.map { ip ->
                Callable {
                    val q = buildQuery(host, type)
                    DatagramSocket().use { s ->
                        s.soTimeout = 1500
                        s.send(DatagramPacket(q, q.size, InetAddress.getByName(ip), 53))
                        val buf = ByteArray(1500)
                        val p = DatagramPacket(buf, buf.size)
                        while (true) {
                            s.receive(p)
                            if (p.length >= 12 && buf[0] == q[0] && buf[1] == q[1]) break
                        }
                        parse(buf.copyOf(p.length))
                    }
                }
            }
        }
        // Серверы опрашиваются одновременно: берём первый нормальный ответ.
        return try {
            pool.invokeAny(tasks, if (viaDoh) 4500L else 2000L, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw IOException(if (viaDoh) "все DoH-серверы недоступны" else "все DNS-серверы недоступны", e.cause)
        } catch (e: java.util.concurrent.TimeoutException) {
            throw IOException("DNS: таймаут", e)
        }
    }

    private fun buildQuery(host: String, type: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val id = ByteArray(2).also { SecureRandom().nextBytes(it) }
        out.write(id)
        out.write(byteArrayOf(0x01, 0x00)) // RD
        out.write(byteArrayOf(0, 1, 0, 0, 0, 0, 0, 0)) // QDCOUNT=1
        for (label in host.trimEnd('.').split('.')) {
            val b = label.toByteArray(Charsets.US_ASCII)
            out.write(b.size)
            out.write(b)
        }
        out.write(0)
        out.write(byteArrayOf((type shr 8).toByte(), type.toByte(), 0, 1)) // QTYPE, QCLASS=IN
        return out.toByteArray()
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)

    /** Пропускает имя (с учётом сжатия указателями) и возвращает позицию после него. */
    private fun skipName(b: ByteArray, start: Int): Int {
        var p = start
        while (true) {
            if (p >= b.size) throw IOException("битый DNS-ответ")
            val l = b[p].toInt() and 0xff
            when {
                l == 0 -> return p + 1
                (l and 0xC0) == 0xC0 -> return p + 2
                else -> p += 1 + l
            }
        }
    }

    private fun parse(b: ByteArray): Answer {
        if (b.size < 12) throw IOException("короткий DNS-ответ")
        if ((u16(b, 2) and 0x000F) != 0) throw IOException("DNS: код ошибки ${u16(b, 2) and 0xF}")
        val qd = u16(b, 4)
        val an = u16(b, 6)
        var p = 12
        repeat(qd) { p = skipName(b, p) + 4 }
        val addrs = ArrayList<InetAddress>()
        var ttl = Long.MAX_VALUE
        repeat(an) {
            p = skipName(b, p)
            if (p + 10 > b.size) throw IOException("битый DNS-ответ")
            val type = u16(b, p)
            val t = (u16(b, p + 4).toLong() shl 16) or u16(b, p + 6).toLong()
            val rdLen = u16(b, p + 8)
            p += 10
            if (p + rdLen > b.size) throw IOException("битый DNS-ответ")
            if ((type == 1 && rdLen == 4) || (type == 28 && rdLen == 16)) {
                val a = InetAddress.getByAddress(b.copyOfRange(p, p + rdLen))
                // Подмена провайдера обычно даёт «пустые» или локальные адреса — такие отбрасываем.
                if (!(a.isAnyLocalAddress || a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress)) {
                    addrs.add(a)
                    ttl = minOf(ttl, t)
                }
            }
            p += rdLen
        }
        return Answer(addrs, if (ttl == Long.MAX_VALUE) 60L else ttl)
    }
}
