package app.ymusic.m3e.data

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Обход блокировки SoundCloud. Внутри приложения поднимается локальный HTTP-прокси (127.0.0.1, случайный порт),
 * и через него идут ТОЛЬКО запросы к soundcloud.com и sndcdn.com (выбор адресата — [selector]); всё остальное ходит напрямую.
 *
 * Режимы:
 *  • встроенный — имя хоста узнаётся через DoH ([ScDoh]), а первый пакет TLS-рукопожатия дробится ([TlsSplit]), чтобы
 *    DPI не увидел SNI; TLS при этом сквозной: сертификат SoundCloud по-прежнему проверяет само приложение;
 *  • свой прокси — туннель строится через HTTP- или SOCKS5-прокси пользователя.
 *
 * Доступ к локальному порту защищён случайным паролем и списком разрешённых адресатов, поэтому другие приложения
 * не смогут использовать его как открытый прокси.
 */
object ScProxy {
    private const val TAG = "ScProxy"

    private val authHeader = Credentials.basic("exm", UUID.randomUUID().toString())
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "sc-proxy").apply { isDaemon = true } }
    private val connPools = ConcurrentLinkedQueue<ConnectionPool>()

    @Volatile private var server: ServerSocket? = null

    /** Адреса, которые считаются SoundCloud (сайт, API, CDN с обложками и потоками). */
    fun isScHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h == "soundcloud.com" || h.endsWith(".soundcloud.com") || h == "sndcdn.com" || h.endsWith(".sndcdn.com") ||
            h == "soundcloud.cloud" || h.endsWith(".soundcloud.cloud")
    }

    /** Временный апстрим на время подбора стратегий ByeByeDPI: SoundCloud идёт через него, настройки не трогаются. */
    @Volatile private var probeUpstream: ScUpstream? = null

    private fun active(): Boolean {
        if (probeUpstream != null) return true
        val c = ScProxySettings.config
        return when (c.mode) {
            ScProxyMode.OFF -> false
            ScProxyMode.BUILTIN -> true
            ScProxyMode.CUSTOM -> c.upstream.valid
        }
    }

    /** Направляет запросы к SoundCloud в локальный прокси, остальные — напрямую. */
    val selector: ProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val direct = listOf(Proxy.NO_PROXY)
            if (uri == null || uri.scheme != "https") return direct
            val host = uri.host ?: return direct
            if (!isScHost(host) || !active()) return direct
            val port = ensureStarted() ?: return direct
            return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port)))
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
    }

    private val authenticator = Authenticator { _, response ->
        if (response.request.header("Proxy-Authorization") != null) null
        else response.request.newBuilder().header("Proxy-Authorization", authHeader).build()
    }

    /** Подключает к клиенту OkHttp выбор прокси для SoundCloud. Вызывать при создании каждого клиента, ходящего к SoundCloud. */
    fun applyTo(b: OkHttpClient.Builder): OkHttpClient.Builder {
        val cp = ConnectionPool()
        connPools.add(cp)
        return b.proxySelector(selector).proxyAuthenticator(authenticator).connectionPool(cp)
    }

    /** Настройки изменились: уже открытые простаивающие соединения шли по старому пути — закрываем их. */
    fun onSettingsChanged() {
        connPools.forEach { runCatching { it.evictAll() } }
    }

    // ---- Локальный сервер ----

    @Synchronized
    private fun ensureStarted(): Int? {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        return try {
            val ss = ServerSocket(0, 64, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
            server = ss
            Thread({ acceptLoop(ss) }, "sc-proxy-accept").apply { isDaemon = true }.start()
            AppLog.i(TAG, "локальный прокси запущен на 127.0.0.1:${ss.localPort}")
            ss.localPort
        } catch (e: Exception) {
            AppLog.e(TAG, "не удалось запустить локальный прокси", e)
            null
        }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val c = try { ss.accept() } catch (e: IOException) { break }
            pool.execute {
                try { handle(c) } catch (e: Exception) { /* обрыв соединения — обычное дело */ } finally { closeQuiet(c) }
            }
        }
        synchronized(this) { if (server === ss) server = null }
    }

    private fun closeQuiet(c: Closeable?) { try { c?.close() } catch (e: Exception) { } }

    private fun reply(o: OutputStream, status: String, extra: String = "") {
        o.write("HTTP/1.1 $status\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        o.flush()
    }

    private fun readHead(i: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val c = i.read()
            if (c < 0) return null
            sb.append(c.toChar())
            if (sb.length >= 4 && sb.endsWith("\r\n\r\n")) return sb.substring(0, sb.length - 4)
        }
        return null
    }

    private fun readFully(i: InputStream, b: ByteArray) {
        var off = 0
        while (off < b.size) {
            val n = i.read(b, off, b.size - off)
            if (n < 0) throw EOFException()
            off += n
        }
    }

    private fun handle(client: Socket) {
        client.tcpNoDelay = true
        client.soTimeout = 15000
        val cin = BufferedInputStream(client.getInputStream())
        val cout = client.getOutputStream()
        val head = readHead(cin) ?: return
        val lines = head.split("\r\n")
        val parts = lines[0].split(' ')
        if (parts.size < 2 || !parts[0].equals("CONNECT", ignoreCase = true)) { reply(cout, "405 Method Not Allowed"); return }
        val auth = lines.drop(1).firstOrNull { it.startsWith("Proxy-Authorization:", ignoreCase = true) }?.substringAfter(':')?.trim()
        if (auth != authHeader) {
            reply(cout, "407 Proxy Authentication Required", "Proxy-Authenticate: Basic realm=\"exm\"\r\n")
            return
        }
        val target = parts[1]
        val colon = target.lastIndexOf(':')
        val host = if (colon > 0) target.substring(0, colon) else target
        val port = if (colon > 0) target.substring(colon + 1).toIntOrNull() ?: 0 else 443
        if (!isScHost(host) || port != 443) { reply(cout, "403 Forbidden"); return }

        val cfg = ScProxySettings.config
        val upstream = probeUpstream ?: cfg.upstream.takeIf { cfg.mode == ScProxyMode.CUSTOM && it.valid }
        if (upstream != null) {
            val srv = try {
                connectUpstream(upstream, host, port)
            } catch (e: Exception) {
                AppLog.w(TAG, "$host: свой прокси: ${e.message}")
                reply(cout, "502 Bad Gateway")
                return
            }
            reply200(cout)
            pump(client, cin, cout, srv)
            return
        }

        // Встроенный обход: DoH + дробление ClientHello.
        val addrs = try { ScDoh.resolve(host) } catch (e: Exception) {
            AppLog.w(TAG, "$host: DNS не сработал: ${e.message}")
            reply(cout, "502 Bad Gateway"); return
        }
        val first = connectAny(host, addrs, 443) ?: run {
            AppLog.w(TAG, "$host: не удалось подключиться ни к одному адресу ($addrs)")
            reply(cout, "502 Bad Gateway"); return
        }
        reply200(cout)
        val hdr = ByteArray(5)
        readFully(cin, hdr)
        val len = ((hdr[3].toInt() and 0xff) shl 8) or (hdr[4].toInt() and 0xff)
        if (hdr[0].toInt() != 0x16 || len == 0 || len > 16384) {
            // Не ClientHello: передаём как есть.
            first.getOutputStream().write(hdr)
            pump(client, cin, cout, first)
            return
        }
        val rec = ByteArray(5 + len)
        System.arraycopy(hdr, 0, rec, 0, 5)
        val body = ByteArray(len)
        readFully(cin, body)
        System.arraycopy(body, 0, rec, 5, len)

        val opened = openWithHello(host, addrs, first, rec, cfg) ?: run {
            AppLog.w(TAG, "$host: рукопожатие не удалось, следующая попытка начнёт с другого приёма")
            return
        }
        cout.write(opened.first)
        cout.flush()
        pump(client, cin, cout, opened.sock)
    }

    private fun reply200(o: OutputStream) {
        o.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        o.flush()
    }

    private class Opened(val sock: Socket, val first: ByteArray)

    private const val DEFAULT_TTL = 64

    /** Сколько ждём ответ сервера на один приём: DPI обычно молча «съедает» пакеты, так что ждать дольше смысла нет. */
    private const val HELLO_TIMEOUT_MS = 1800
    /** Общий бюджет на перебор приёмов в одном запросе (приложение ждёт ответ около 10 с). */
    private const val HELLO_BUDGET_MS = 8000L

    /**
     * Отправляет ClientHello сервера выбранным приёмом и ждёт первый ответ. Если сервер молчит или рвёт соединение
     * (так ведёт себя DPI), открывает новое соединение и повторяет тот же ClientHello следующим приёмом —
     * приложение этого не замечает. Удачный приём запоминается (отдельно для сайта/API и для CDN).
     * Если за отведённое время все опробованные приёмы не сработали, следующий запрос начнёт уже с нового, а не с тех же.
     */
    private fun openWithHello(host: String, addrs: List<InetAddress>, firstSock: Socket, rec: ByteArray, cfg: ScProxyConfig): Opened? {
        val fixed = cfg.frag >= 0
        val startSt = ScProxySettings.autoStrategy(host)
        val order = when {
            cfg.frag >= 0 -> listOf(cfg.frag)
            else -> TlsSplit.autoOrder(startSt)
        }
        if (order.size > 1) return raceHello(host, addrs, firstSock, rec, order, startSt)
        val deadline = System.currentTimeMillis() + HELLO_BUDGET_MS
        var sock: Socket? = firstSock
        var tried = 0
        for (st in order) {
            if (tried > 0 && System.currentTimeMillis() > deadline) break
            val s = sock ?: connectAny(host, addrs, 443) ?: break
            sock = null
            tried++
            try {
                val first = sendHello(s, rec, st)
                if (!fixed) ScProxySettings.rememberStrategy(host, st)
                AppLog.d(TAG, "$host: рукопожатие прошло (${TlsSplit.STRATEGY_NAMES[st]})")
                return Opened(s, first)
            } catch (e: Exception) {
                closeQuiet(s)
                AppLog.w(TAG, "$host: «${TlsSplit.STRATEGY_NAMES[st]}» не сработал (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        closeQuiet(sock)
        if (!fixed && tried < order.size) ScProxySettings.advanceStrategy(host, startSt, order[tried])
        // Возможно, дело в адресе, а не в приёме: следующий запрос начнёт с другого адреса.
        addrs.firstOrNull()?.let { ScDoh.demote(host, it) }
        return null
    }

    private class Attempt(val opened: Opened?)

    /** Через сколько стартует следующий приём, если предыдущий ещё не ответил и не упал. */
    private const val RACE_STAGGER_MS = 600L

    /**
     * Приёмы пробуются не по очереди, а «гонкой»: каждый следующий стартует через [RACE_STAGGER_MS] (или сразу, если предыдущий
     * уже провалился), на своём соединении с тем же ClientHello. Побеждает первый, от кого пришёл ответ сервера; остальные
     * закрываются. Так запрос приложения ждёт время самого быстрого рабочего приёма, а не сумму таймаутов всех неудачных.
     */
    private fun raceHello(host: String, addrs: List<InetAddress>, firstSock: Socket, rec: ByteArray, order: List<Int>, startSt: Int): Opened? {
        val results = java.util.concurrent.LinkedBlockingQueue<Pair<Int, Attempt>>()
        val deadline = System.currentTimeMillis() + HELLO_BUDGET_MS
        var launched = 0
        var done = 0
        var winner: Opened? = null
        var winnerSt = -1

        fun launch(st: Int, pre: Socket?, only: InetAddress?) {
            launched++
            pool.execute {
                var s: Socket? = pre
                try {
                    s = s ?: connectAny(host, if (only != null) listOf(only) else addrs, 443) ?: throw IOException("не удалось подключиться")
                    results.put(st to Attempt(Opened(s, sendHello(s, rec, st))))
                } catch (e: Exception) {
                    closeQuiet(s)
                    AppLog.d(TAG, "$host: «${TlsSplit.STRATEGY_NAMES[st]}» не сработал (${e.javaClass.simpleName})")
                    results.put(st to Attempt(null))
                }
            }
        }

        fun take(a: Pair<Int, Attempt>) {
            done++
            val o = a.second.opened ?: return
            if (winner == null) { winner = o; winnerSt = a.first } else closeQuiet(o.sock)
        }

        // Часть адресов CDN у провайдера «мёртвая»: на них не проходит ни один приём. Поэтому в гонку между приёмами на первом
        // адресе подмешиваются попытки на других адресах (с приёмом, сработавшим в прошлый раз).
        val extra = addrs.drop(1).take(2)
        val plan = ArrayList<Pair<Int, InetAddress?>>()
        for ((idx, st) in order.withIndex()) {
            plan += st to null
            if (idx < extra.size) plan += startSt to extra[idx]
        }
        for ((idx, att) in plan.withIndex()) {
            launch(att.first, if (idx == 0) firstSock else null, att.second)
            val end = System.currentTimeMillis() + RACE_STAGGER_MS
            while (winner == null) {
                val left = end - System.currentTimeMillis()
                if (left <= 0) break
                val a = results.poll(left, TimeUnit.MILLISECONDS) ?: break
                take(a)
                if (a.second.opened == null) break
            }
            if (winner != null || System.currentTimeMillis() > deadline) break
        }
        while (winner == null && done < launched) {
            take(results.poll(HELLO_TIMEOUT_MS + 3000L, TimeUnit.MILLISECONDS) ?: break)
        }
        // Опоздавшие попытки закрываем, когда они закончатся.
        val rest = launched - done
        if (rest > 0) pool.execute {
            repeat(rest) { results.poll(15, TimeUnit.SECONDS)?.second?.opened?.let { closeQuiet(it.sock) } }
        }
        val w = winner
        if (w != null) {
            ScProxySettings.rememberStrategy(host, winnerSt)
            // Рабочим считаем адрес, на котором сервер ответил на ClientHello, а не просто принял TCP-соединение.
            w.sock.inetAddress?.let { ScDoh.promote(host, it) }
            AppLog.d(TAG, "$host: рукопожатие прошло (${TlsSplit.STRATEGY_NAMES[winnerSt]}, ${w.sock.inetAddress?.hostAddress})")
            return w
        }
        AppLog.w(TAG, "$host: рукопожатие не удалось ни одним приёмом")
        order.getOrNull(launched)?.let { ScProxySettings.advanceStrategy(host, startSt, it) }
        addrs.firstOrNull()?.let { ScDoh.demote(host, it) }
        return null
    }

    private fun sendHello(s: Socket, rec: ByteArray, strategy: Int): ByteArray {
        val out = s.getOutputStream()
        val steps = TlsSplit.plan(rec, strategy)
        steps.forEachIndexed { i, st ->
            if (i > 0) Thread.sleep(if (steps[i - 1].lowTtl) 25L else 6L)
            if (st.lowTtl) writeLowTtl(s, st.data) else { out.write(st.data); out.flush() }
            if (st.urgentAfter) s.sendUrgentData('a'.code)
        }
        s.soTimeout = HELLO_TIMEOUT_MS
        val buf = ByteArray(16384)
        val n = s.getInputStream().read(buf)
        if (n <= 0) throw EOFException("сервер закрыл соединение")
        if (buf[0].toInt() == 0x15) throw IOException("TLS-ошибка в ответ на рукопожатие")
        s.soTimeout = 0
        return buf.copyOf(n)
    }

    /**
     * Отправляет [data] отдельным пакетом с TTL=1: до сервера он не дойдёт (DPI рядом с нами его не увидит), а TCP через
     * пару сотен миллисекунд сам повторит отправку уже с обычным TTL. TTL возвращается сразу после записи.
     */
    private fun writeLowTtl(s: Socket, data: ByteArray) {
        val pfd = ParcelFileDescriptor.fromSocket(s)
        try {
            val fd = pfd.fileDescriptor
            // Читать текущий TTL в публичном API нельзя (Os.getsockoptInt скрыт), возвращаем стандартное для Android значение.
            val v4 = DEFAULT_TTL
            val v6 = DEFAULT_TTL
            val lowered = runCatching { Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, 1) }.isSuccess or
                runCatching { Os.setsockoptInt(fd, OsConstants.IPPROTO_IPV6, OsConstants.IPV6_UNICAST_HOPS, 1) }.isSuccess
            if (!lowered) throw IOException("не удалось задать TTL")
            try {
                val out = s.getOutputStream()
                out.write(data)
                out.flush()
            } finally {
                runCatching { Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TTL, v4) }
                runCatching { Os.setsockoptInt(fd, OsConstants.IPPROTO_IPV6, OsConstants.IPV6_UNICAST_HOPS, v6) }
            }
        } finally {
            try { pfd.close() } catch (e: Exception) { }
        }
    }

    private fun connectAny(host: String, addrs: List<InetAddress>, port: Int): Socket? {
        for (a in addrs.take(3)) {
            val s = Socket()
            try {
                s.tcpNoDelay = true
                s.keepAlive = true
                s.connect(InetSocketAddress(a, port), 2500)
                ScDoh.promote(host, a)
                return s
            } catch (e: IOException) {
                closeQuiet(s)
            }
        }
        return null
    }

    private fun pump(client: Socket, cin: InputStream, cout: OutputStream, server: Socket) {
        client.soTimeout = 0
        server.soTimeout = 0
        val sin = server.getInputStream()
        val sout = server.getOutputStream()
        val down = pool.submit(Runnable {
            try { copy(sin, cout) } catch (e: IOException) { }
            try { client.shutdownOutput() } catch (e: Exception) { }
        })
        try {
            copy(cin, sout)
            try { server.shutdownOutput() } catch (e: Exception) { }
        } catch (e: IOException) {
            closeQuiet(server); closeQuiet(client)
        }
        try { down.get() } catch (e: Exception) { }
        closeQuiet(server)
    }

    private fun copy(i: InputStream, o: OutputStream) {
        val b = ByteArray(16384)
        while (true) {
            val n = i.read(b)
            if (n < 0) return
            o.write(b, 0, n)
            o.flush()
        }
    }

    // ---- Свой прокси (HTTP CONNECT / SOCKS5) ----

    /** Прокси на этом же телефоне (например, ByeByeDPI): у него обычный системный DNS, подменённый провайдером. */
    private fun isLocalUpstream(h: String): Boolean {
        val x = h.trim().lowercase().removePrefix("[").removeSuffix("]")
        return x == "localhost" || x == "::1" || x.startsWith("127.")
    }

    private fun connectUpstream(u: ScUpstream, host: String, port: Int): Socket {
        // Локальному прокси отдаём уже найденный через DoH IP-адрес вместо имени: тогда подменённый DNS провайдера не мешает,
        // а SNI он всё равно берёт из ClientHello и применяет свои приёмы.
        val target: InetAddress? = if (isLocalUpstream(u.host)) {
            runCatching { ScDoh.resolve(host).firstOrNull() }.getOrNull()
        } else null
        val addrs = ScDoh.resolve(u.host)
        val s = connectAny(u.host, addrs, u.port) ?: throw IOException("не удалось подключиться к ${u.host}:${u.port}")
        try {
            s.soTimeout = 10000
            val inp = s.getInputStream()
            val out = s.getOutputStream()
            when (u.type) {
                ScProxyType.HTTP -> {
                    val dest = target?.hostAddress?.let { if (it.contains(':')) "[$it]" else it } ?: host
                    val req = StringBuilder("CONNECT $dest:$port HTTP/1.1\r\nHost: $host:$port\r\n")
                    if (u.user.isNotEmpty()) req.append("Proxy-Authorization: ").append(Credentials.basic(u.user, u.pass)).append("\r\n")
                    req.append("\r\n")
                    out.write(req.toString().toByteArray(Charsets.ISO_8859_1))
                    out.flush()
                    val head = readHead(inp) ?: throw IOException("прокси закрыл соединение")
                    val status = head.substringBefore("\r\n")
                    val code = status.split(' ').getOrNull(1)?.toIntOrNull()
                    if (code == null || code !in 200..299) {
                        throw IOException(if (code == 407) "прокси не принял логин или пароль" else "прокси ответил: $status")
                    }
                }
                ScProxyType.SOCKS5 -> socks5(inp, out, u, host, port, target)
            }
            s.soTimeout = 0
            return s
        } catch (e: Exception) {
            closeQuiet(s)
            throw e
        }
    }

    private fun socks5(inp: InputStream, out: OutputStream, u: ScUpstream, host: String, port: Int, ip: InetAddress? = null) {
        val hasAuth = u.user.isNotEmpty()
        out.write(if (hasAuth) byteArrayOf(5, 2, 0, 2) else byteArrayOf(5, 1, 0))
        out.flush()
        val r = ByteArray(2)
        readFully(inp, r)
        if (r[0].toInt() != 5) throw IOException("это не SOCKS5-прокси")
        when (r[1].toInt() and 0xff) {
            0 -> {}
            2 -> {
                if (!hasAuth) throw IOException("SOCKS5: прокси требует логин и пароль")
                val ub = u.user.toByteArray(Charsets.UTF_8)
                val pb = u.pass.toByteArray(Charsets.UTF_8)
                if (ub.size > 255 || pb.size > 255) throw IOException("SOCKS5: слишком длинный логин или пароль")
                out.write(byteArrayOf(1, ub.size.toByte()) + ub + byteArrayOf(pb.size.toByte()) + pb)
                out.flush()
                val a = ByteArray(2)
                readFully(inp, a)
                if (a[1].toInt() != 0) throw IOException("SOCKS5: прокси не принял логин или пароль")
            }
            else -> throw IOException("SOCKS5: неподдерживаемый способ авторизации")
        }
        val portBytes = byteArrayOf((port shr 8).toByte(), port.toByte())
        if (ip != null) {
            val raw = ip.address
            out.write(byteArrayOf(5, 1, 0, if (raw.size == 4) 1 else 4) + raw + portBytes)
        } else {
            val hb = host.toByteArray(Charsets.US_ASCII)
            if (hb.size > 255) throw IOException("слишком длинное имя хоста")
            out.write(byteArrayOf(5, 1, 0, 3, hb.size.toByte()) + hb + portBytes)
        }
        out.flush()
        val h = ByteArray(4)
        readFully(inp, h)
        val rep = h[1].toInt() and 0xff
        if (rep != 0) {
            val why = when (rep) {
                1 -> "общий сбой"; 2 -> "запрещено правилами прокси"; 3 -> "сеть недоступна"; 4 -> "хост недоступен"
                5 -> "соединение отклонено"; 6 -> "истёк TTL"; 7 -> "команда не поддерживается"; 8 -> "адрес не поддерживается"
                else -> "код $rep"
            }
            throw IOException("SOCKS5: $why")
        }
        val skip = when (h[3].toInt() and 0xff) {
            1 -> 4 + 2
            4 -> 16 + 2
            3 -> { val l = inp.read(); if (l < 0) throw EOFException(); l + 2 }
            else -> throw IOException("SOCKS5: непонятный ответ")
        }
        readFully(inp, ByteArray(skip))
    }

    // ---- Проверка из настроек ----

    class TestResult(val name: String, val ok: Boolean, val ms: Long, val detail: String)

    private val testClient: OkHttpClient by lazy {
        applyTo(OkHttpClient.Builder())
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(14, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .build()
    }

    /**
     * Пробует открыть соединение с API и CDN SoundCloud текущим способом. Любой HTTP-ответ считается успехом: значит, TLS дошёл до сервера.
     * При автоподборе приёма неудачная попытка продвигает перебор дальше, поэтому до трёх попыток подряд: за одну проверку
     * успевают опробоваться почти все приёмы.
     */
    fun test(): List<TestResult> {
        testClient.connectionPool.evictAll()
        val c = ScProxySettings.config
        val rounds = if (c.mode == ScProxyMode.BUILTIN && c.frag < 0) 3 else 1
        return listOf(
            "API" to "https://api.soundcloud.com/",
            "Обложки" to "https://i1.sndcdn.com/",
            "Потоки (HLS)" to "https://cf-hls-media.sndcdn.com/",
            "Потоки (mp3)" to "https://cf-media.sndcdn.com/",
        ).map { (name, url) ->
            val t0 = System.currentTimeMillis()
            var last = "нет ответа"
            for (round in 1..rounds) {
                try {
                    val req = Request.Builder().url(url).header("User-Agent", SoundCloudAuth.UA).build()
                    return@map testClient.newCall(req).execute().use { r ->
                        TestResult(name, true, System.currentTimeMillis() - t0, "HTTP ${r.code}")
                    }
                } catch (e: Exception) {
                    last = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    testClient.connectionPool.evictAll()
                }
            }
            TestResult(name, false, System.currentTimeMillis() - t0, last)
        }
    }

    // ---- Подбор стратегий ByeByeDPI ----

    /** Адреса, которые приложению нужны для работы SoundCloud: API, обложки, потоки. Должны открыться все. */
    private val CHECK_URLS = listOf(
        "https://api.soundcloud.com/",
        "https://i1.sndcdn.com/",
        "https://cf-media.sndcdn.com/",
    )

    private val checkClient: OkHttpClient by lazy {
        testClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /** На время [block] весь трафик SoundCloud идёт через [u] (настройки приложения не меняются). */
    fun <T> withUpstream(u: ScUpstream, block: () -> T): T {
        probeUpstream = u
        onSettingsChanged()
        try {
            return block()
        } finally {
            probeUpstream = null
            onSettingsChanged()
        }
    }

    /**
     * Открывает API и CDN SoundCloud настоящими HTTPS-запросами (параллельно, на свежих соединениях).
     * Любой HTTP-ответ — успех. Возвращает время в мс, если открылись все адреса, иначе null.
     */
    fun quickCheck(): Long? {
        checkClient.connectionPool.evictAll()
        val t0 = System.currentTimeMillis()
        val jobs = CHECK_URLS.map { url ->
            pool.submit(Callable {
                try {
                    val req = Request.Builder().url(url).header("User-Agent", SoundCloudAuth.UA).build()
                    checkClient.newCall(req).execute().use { true }
                } catch (e: Exception) { false }
            })
        }
        val ok = jobs.all { runCatching { it.get() }.getOrDefault(false) }
        checkClient.connectionPool.evictAll()
        return if (ok) System.currentTimeMillis() - t0 else null
    }
}
