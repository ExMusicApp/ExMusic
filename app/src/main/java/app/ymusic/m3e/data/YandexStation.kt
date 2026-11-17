package app.ymusic.m3e.data

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.net.Inet4Address
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

data class StationSpeaker(
    val id: String,
    val name: String,
    val platform: String?,
    val host: String,
    val port: Int,
)

fun isGenericSpeakerName(name: String?): Boolean {
    val n = name?.trim()?.lowercase().orEmpty()
    if (n.isEmpty()) return true
    if (n.contains("yandexio") || n.contains("receiver") || n.contains("_tcp") || n.contains("._")) return true
    if (n == "яндекс станция" || n == "yandex station") return true
    return Regex("^[0-9a-f\\-]{16,}$").matches(n)
}

data class StationState(
    val scanning: Boolean = false,
    val speakers: List<StationSpeaker> = emptyList(),
    val loginRequired: Boolean = false,
    val activeId: String? = null,
    val playing: Boolean = false,
    val progressMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Double = -1.0,
    val trackId: String? = null,
    val index: Int = -1,
    val receivedAtMs: Long = 0,
)

private class StationAuthException(message: String, val needLogin: Boolean) : IOException(message)

object StationCookies {
    private val urls = listOf(
        "https://passport.yandex.ru",
        "https://yandex.ru",
        "https://ya.ru",
        "https://music.yandex.ru",
        "https://iot.quasar.yandex.ru",
    )

    fun header(): String? = runCatching {
        val cm = CookieManager.getInstance()
        val byName = LinkedHashMap<String, String>()
        for (u in urls) {
            val raw = cm.getCookie(u) ?: continue
            for (part in raw.split(";")) {
                val p = part.trim()
                val i = p.indexOf('=')
                if (i <= 0) continue
                byName.putIfAbsent(p.substring(0, i), p)
            }
        }
        byName.values.joinToString("; ").ifBlank { null }
    }.getOrNull()

    fun hasSession(): Boolean = header()?.contains("Session_id=") == true

    fun clear() {
        runCatching {
            val cm = CookieManager.getInstance()
            for (u in urls) {
                val raw = cm.getCookie(u) ?: continue
                for (part in raw.split(";")) {
                    val name = part.trim().substringBefore('=')
                    if (name.isBlank()) continue
                    for (domain in listOf(".yandex.ru", ".ya.ru", "yandex.ru")) {
                        cm.setCookie(u, "$name=; Max-Age=0; Domain=$domain; Path=/")
                    }
                }
            }
            cm.flush()
        }
    }
}

class YandexStation(
    ctx: Context,
    base: OkHttpClient,
    private val appToken: () -> String?,
    private val onLost: (String) -> Unit,
) {
    private companion object {
        const val TAG = "YandexStation"

        const val QUASAR_DEVICES = "https://iot.quasar.yandex.ru/m/v3/user/devices"
        const val QUASAR_PAGE = "https://yandex.ru/quasar"
        const val PASSPORT_TOKEN_BY_SESSIONID = "https://mobileproxy.passport.yandex.net/1/bundle/oauth/token_by_sessionid"
        const val PASSPORT_SHORT_INFO = "https://mobileproxy.passport.yandex.net/1/bundle/account/short_info/?avatar_size=islands-300"
        const val MOBILE_OAUTH_TOKEN = "https://oauth.mobile.yandex.net/1/token"
        const val GLAGOL_TOKEN = "https://quasar.yandex.net/glagol/token"

        const val PASSPORT_CLIENT_ID = "c0ebe342af7d48fbbbfcf2d2eedb8f9e"
        const val PASSPORT_CLIENT_SECRET = "ad0a908f0aa341a182a37ecd75bc319e"
        const val MUSIC_CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
        const val MUSIC_CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300"

        const val MDNS_SERVICE = "_yandexio._tcp"
        const val SCAN_WINDOW_MS = 30_000L
        const val RESPONSE_TIMEOUT_MS = 8_000L
        const val ADVANCE_LEAD_MS = 1_500L
        const val VOLUME_STEP = 0.05
    }

    private val app = ctx.applicationContext
    private val http = base.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(StationState())
    val state: StateFlow<StationState> = _state

    private val wsClient: OkHttpClient = run {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    val all = okhttp3.Dns.SYSTEM.lookup(hostname)
                    if (all.isEmpty() || all.any { !(it.isSiteLocalAddress || it.isLinkLocalAddress || it.isLoopbackAddress || (it.address.size == 16 && (it.address[0].toInt() and 0xFE) == 0xFC)) })
                        throw java.net.UnknownHostException("Колонка должна быть в локальной сети")
                    return all
                }
            })
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile private var derivedMusicToken: String? = null
    @Volatile private var appTokenWorks: Boolean? = null
    private val authMutex = Mutex()

    private fun readJson(r: Response): JSONObject? =
        runCatching { JSONObject(r.body?.string().orEmpty()) }.getOrNull()

    private fun fetchXToken(cookie: String): String {
        val body = FormBody.Builder()
            .add("client_id", PASSPORT_CLIENT_ID)
            .add("client_secret", PASSPORT_CLIENT_SECRET)
            .build()
        val req = Request.Builder().url(PASSPORT_TOKEN_BY_SESSIONID).post(body)
            .header("Ya-Client-Host", "passport.yandex.ru")
            .header("Ya-Client-Cookie", cookie)
            .build()
        http.newCall(req).execute().use { r ->
            val j = readJson(r)
            val t = j?.optString("access_token").orEmpty()
            if (!r.isSuccessful || t.isBlank()) throw StationAuthException("Не удалось войти в Яндекс (x_token, HTTP ${r.code})", needLogin = true)
            return t
        }
    }

    private fun validateXToken(xToken: String) {
        runCatching {
            val req = Request.Builder().url(PASSPORT_SHORT_INFO).header("Authorization", "OAuth $xToken").build()
            http.newCall(req).execute().use { r ->
                val j = readJson(r)
                if (!r.isSuccessful || j?.optString("status") != "ok") Log.w(TAG, "x_token validation failed: HTTP ${r.code}")
            }
        }
    }

    private fun fetchMusicToken(xToken: String): String {
        val body = FormBody.Builder()
            .add("grant_type", "x-token")
            .add("access_token", xToken)
            .add("client_id", MUSIC_CLIENT_ID)
            .add("client_secret", MUSIC_CLIENT_SECRET)
            .build()
        val req = Request.Builder().url(MOBILE_OAUTH_TOKEN).post(body).build()
        http.newCall(req).execute().use { r ->
            val j = readJson(r)
            val t = j?.optString("access_token").orEmpty()
            if (!r.isSuccessful || t.isBlank()) throw StationAuthException("Не удалось получить токен для колонки (HTTP ${r.code})", needLogin = true)
            return t
        }
    }

    private fun fetchConversationToken(musicToken: String, deviceId: String, platform: String): String {
        val url = Uri.parse(GLAGOL_TOKEN).buildUpon()
            .appendQueryParameter("device_id", deviceId)
            .appendQueryParameter("platform", platform)
            .build().toString()
        val req = Request.Builder().url(url).header("Authorization", "OAuth $musicToken").build()
        http.newCall(req).execute().use { r ->
            val j = readJson(r)
            val t = j?.optString("token").orEmpty()
            if (r.code in 400..499 || (r.isSuccessful && (j?.optString("status") != "ok" || t.isBlank()))) {
                throw StationAuthException("Яндекс не выдал токен колонки (HTTP ${r.code})", needLogin = false)
            }
            if (!r.isSuccessful) throw IOException("Яндекс не выдал токен колонки (HTTP ${r.code})")
            return t
        }
    }

    private suspend fun conversationToken(deviceId: String, platform: String): String = authMutex.withLock {
        withContext(Dispatchers.IO) {
            derivedMusicToken?.let { t ->
                try {
                    return@withContext fetchConversationToken(t, deviceId, platform)
                } catch (e: StationAuthException) {
                    derivedMusicToken = null
                }
            }
            val own = appToken()
            if (!own.isNullOrBlank() && appTokenWorks != false) {
                try {
                    val t = fetchConversationToken(own, deviceId, platform)
                    appTokenWorks = true
                    return@withContext t
                } catch (e: StationAuthException) {
                    appTokenWorks = false
                }
            }
            val cookie = StationCookies.header()
                ?: throw StationAuthException("Для управления колонкой нужен вход в Яндекс", needLogin = true)
            val x = fetchXToken(cookie)
            validateXToken(x)
            val music = fetchMusicToken(x)
            derivedMusicToken = music
            try {
                fetchConversationToken(music, deviceId, platform)
            } catch (e: StationAuthException) {
                derivedMusicToken = null
                throw StationAuthException("Колонка не приняла токен этого аккаунта", needLogin = true)
            }
        }
    }

    private class AccountSpeaker(val iotId: String, val name: String, val glagolId: String?, val platform: String?)

    @Volatile private var accountSpeakers: List<AccountSpeaker> = emptyList()

    private fun quasarGet(url: String, cookie: String): JSONObject? {
        val req = Request.Builder().url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("Cookie", cookie)
            .header("Referer", QUASAR_PAGE)
            .build()
        http.newCall(req).execute().use { r ->
            val j = readJson(r)
            if (!r.isSuccessful || j == null || j.optString("status") == "error") {
                Log.w(TAG, "quasar request failed: HTTP ${r.code}")
                return null
            }
            return j
        }
    }

    private fun looksLikeSpeaker(d: JSONObject): Boolean {
        val itemType = d.optString("item_type").ifBlank { d.optString("itemType") }
        if (itemType.isNotBlank() && itemType != "device") return false
        val type = d.optString("type").ifBlank { d.optString("device_type") }.lowercase()
        val name = d.optString("name").lowercase()
        if (listOf("smart_speaker", "station", "speaker", "yandex.station").any { type.contains(it) }) return true
        if (listOf("станция", "алиса", "alice").any { name.contains(it) }) return true
        val ser = (d.optJSONArray("capabilities")?.toString().orEmpty() + d.optJSONArray("directives")?.toString().orEmpty() +
            d.optJSONObject("quasar_info")?.toString().orEmpty()).lowercase()
        return ser.contains("quasar") || ser.contains("speaker") || ser.contains("media")
    }

    private fun loadAccountSpeakers(cookie: String): List<AccountSpeaker> {
        val root = quasarGet(QUASAR_DEVICES, cookie) ?: return emptyList()
        if (root.optString("status") != "ok") return emptyList()
        val out = ArrayList<AccountSpeaker>()
        val households = root.optJSONArray("households") ?: return emptyList()
        for (h in 0 until households.length()) {
            val all = households.optJSONObject(h)?.optJSONArray("all") ?: continue
            for (i in 0 until all.length()) {
                val d = all.optJSONObject(i) ?: continue
                if (!looksLikeSpeaker(d)) continue
                val id = d.optString("id")
                if (id.isBlank()) continue
                val cfg = quasarGet("https://iot.quasar.yandex.ru/m/user/devices/${Uri.encode(id)}/configuration", cookie)
                val qi = cfg?.optJSONObject("quasar_info") ?: cfg?.optJSONObject("quasarInfo")
                out += AccountSpeaker(
                    iotId = id,
                    name = d.optString("name").ifBlank { id },
                    glagolId = (qi?.optString("device_id")?.ifBlank { null } ?: qi?.optString("deviceId")?.ifBlank { null }),
                    platform = qi?.optString("platform")?.ifBlank { null },
                )
            }
        }
        return out
    }

    private suspend fun refreshAccountSpeakers() {
        for (attempt in 0 until 3) {
            val cookie = StationCookies.header() ?: return
            val loaded = runCatching { loadAccountSpeakers(cookie) }
                .onFailure { Log.w(TAG, "account speakers failed: ${it.javaClass.simpleName}") }
                .getOrDefault(emptyList())
            if (loaded.isNotEmpty()) {
                accountSpeakers = loaded
                publishSpeakers()
                return
            }
            delay(2_000L * (attempt + 1))
        }
        publishSpeakers()
    }

    private val localFound = ConcurrentHashMap<String, StationSpeaker>()
    private var nsd: NsdManager? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var scanStopJob: Job? = null
    private val resolveMutex = Mutex()

    private fun publishSpeakers() {
        val acc = accountSpeakers
        val used = HashSet<AccountSpeaker>()
        val locals = localFound.values.toList()
        val matched = HashMap<String, AccountSpeaker>()
        for (sp in locals) {
            val a = acc.firstOrNull { it !in used && it.glagolId != null && it.glagolId.equals(sp.id, ignoreCase = true) }
            if (a != null) { used += a; matched[sp.id] = a }
        }
        for (sp in locals) {
            if (sp.id in matched) continue
            val a = acc.firstOrNull { x -> x !in used && sp.platform != null && x.platform.equals(sp.platform, true) && namesMatch(x.name, sp.name) }
            if (a != null) { used += a; matched[sp.id] = a }
        }
        for (sp in locals) {
            if (sp.id in matched || sp.platform == null) continue
            val cand = acc.filter { x -> x !in used && x.platform.equals(sp.platform, true) }
            if (cand.size == 1) { used += cand[0]; matched[sp.id] = cand[0] }
        }
        val list = locals.map { sp ->
            val a = matched[sp.id]
            sp.copy(
                name = a?.name?.takeIf { it.isNotBlank() } ?: sp.name,
                platform = sp.platform ?: a?.platform,
            )
        }.sortedBy { it.name.lowercase() }
        _state.update { it.copy(speakers = list) }
    }

    private fun namesMatch(a: String, b: String): Boolean {
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }

    @Suppress("DEPRECATION")
    private fun toSpeaker(si: NsdServiceInfo): StationSpeaker? {
        val txt = HashMap<String, String>()
        si.attributes?.forEach { (k, v) -> txt[k] = String(v ?: ByteArray(0), Charsets.UTF_8) }
        val deviceId = txt["deviceId"] ?: txt["device_id"] ?: txt["id"] ?: return null
        val addr: java.net.InetAddress? = if (Build.VERSION.SDK_INT >= 34) {
            val l = si.hostAddresses
            l.firstOrNull { it is Inet4Address } ?: l.firstOrNull()
        } else {
            si.host
        }
        if (addr == null) return null
        val host = addr.hostAddress?.substringBefore('%') ?: return null
        val port = si.port
        if (port <= 0) return null
        return StationSpeaker(
            id = deviceId,
            name = listOf(txt["name"], txt["deviceName"], si.serviceName)
                .firstOrNull { !isGenericSpeakerName(it) }?.trim() ?: "Яндекс Станция",
            platform = txt["platform"],
            host = if (host.contains(':')) "[$host]" else host,
            port = port,
        )
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveOnce(si: NsdServiceInfo): StationSpeaker? = withTimeoutOrNull(6_000) {
        suspendCancellableCoroutine<StationSpeaker?> { cont ->
            val mgr = nsd
            if (mgr == null) {
                cont.resume(null)
            } else {
                mgr.resolveService(si, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(runCatching { toSpeaker(serviceInfo) }.getOrNull())
                    }
                })
            }
        }
    }

    private fun resolveAndStore(si: NsdServiceInfo) {
        scope.launch {
            resolveMutex.withLock {
                for (attempt in 0 until 3) {
                    val sp = resolveOnce(si)
                    if (sp != null) {
                        localFound[si.serviceName.orEmpty()] = sp
                        publishSpeakers()
                        return@withLock
                    }
                    delay(700L * (attempt + 1))
                }
            }
        }
    }

    private fun startDiscovery() {
        if (discovery != null) {
            scheduleScanStop()
            return
        }
        val mgr = app.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsd = mgr
        runCatching {
            val wifi = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("yandex-station")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _state.update { it.copy(scanning = true) }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType?.contains("_yandexio") == true) resolveAndStore(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (localFound.remove(serviceInfo.serviceName.orEmpty()) != null) publishSpeakers()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _state.update { it.copy(scanning = false) }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS start failed: $errorCode")
                discovery = null
                _state.update { it.copy(scanning = false) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS stop failed: $errorCode")
            }
        }
        discovery = l
        runCatching { mgr.discoverServices(MDNS_SERVICE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { discovery = null; Log.w(TAG, "mDNS discover failed", it) }
        scheduleScanStop()
    }

    private fun scheduleScanStop() {
        scanStopJob?.cancel()
        scanStopJob = scope.launch {
            delay(SCAN_WINDOW_MS)
            stopDiscovery()
        }
    }

    private fun stopDiscovery() {
        scanStopJob?.cancel()
        scanStopJob = null
        val l = discovery
        discovery = null
        if (l != null) runCatching { nsd?.stopServiceDiscovery(l) }
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
        _state.update { it.copy(scanning = false) }
    }

    fun start() {
        startDiscovery()
        scope.launch { refreshAccountSpeakers() }
        scope.launch { probeAuth() }
    }

    fun rescan() {
        localFound.clear()
        publishSpeakers()
        stopDiscovery()
        start()
    }

    fun shutdown() {
        stopDiscovery()
        closeLink()
        resetPlayback()
        localFound.clear()
        accountSpeakers = emptyList()
        _state.update { StationState() }
    }

    fun afterLogin() {
        derivedMusicToken = null
        appTokenWorks = null
        _state.update { it.copy(loginRequired = false) }
        scope.launch { refreshAccountSpeakers() }
        scope.launch { probeAuth() }
    }

    private suspend fun probeAuth() {
        var sp: StationSpeaker? = null
        for (i in 0 until 12) {
            sp = _state.value.speakers.firstOrNull { it.platform != null }
            if (sp != null) break
            delay(1_000)
        }
        val target = sp ?: return
        val platform = target.platform ?: return
        try {
            conversationToken(target.id, platform)
            _state.update { it.copy(loginRequired = false) }
        } catch (e: StationAuthException) {
            if (e.needLogin) _state.update { it.copy(loginRequired = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "probe failed: ${e.javaClass.simpleName}")
        }
    }

    fun speaker(id: String): StationSpeaker? = _state.value.speakers.firstOrNull { it.id == id }

    private inner class Link(val sp: StationSpeaker, val token: String) {
        @Volatile var socket: WebSocket? = null
        @Volatile var closedByUs = false
        val firstState = CompletableDeferred<Unit>()
        private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
        @Volatile var halfRttMs = 0L
        private var openedAt = 0L

        fun open() {
            val req = Request.Builder().url("wss://${sp.host}:${sp.port}").build()
            socket = wsClient.newWebSocket(req, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    openedAt = System.currentTimeMillis()
                    send(JSONObject().put("command", "softwareVersion"))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val m = runCatching { JSONObject(text) }.getOrNull() ?: return
                    val rid = m.optString("requestId").ifBlank { m.optString("id") }
                    pending.remove(rid)?.complete(m)
                    val st = m.optJSONObject("state")
                    if (st != null) {
                        if (!firstState.isCompleted) {
                            if (openedAt > 0) halfRttMs = (System.currentTimeMillis() - openedAt) / 2
                            firstState.complete(Unit)
                        }
                        if (this@Link === link) onGlagolState(st)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!firstState.isCompleted) firstState.completeExceptionally(IOException("Колонка закрыла соединение ($code)"))
                    if (!closedByUs) linkDropped(this@Link)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!firstState.isCompleted) firstState.completeExceptionally(IOException(t.message ?: "Нет связи с колонкой"))
                    if (!closedByUs) linkDropped(this@Link)
                }
            })
        }

        fun send(payload: JSONObject): Boolean {
            val ws = socket ?: return false
            val msg = JSONObject()
                .put("conversationToken", token)
                .put("id", UUID.randomUUID().toString())
                .put("payload", payload)
                .put("sentTime", System.currentTimeMillis())
            return ws.send(msg.toString())
        }

        fun close() {
            closedByUs = true
            runCatching { socket?.close(1000, null) }
            socket = null
        }
    }

    @Volatile private var link: Link? = null
    private val linkMutex = Mutex()

    private fun closeLink() {
        val l = link
        link = null
        l?.close()
    }

    private suspend fun ensureLink(sp: StationSpeaker): Link = linkMutex.withLock {
        link?.takeIf { it.sp.id == sp.id && it.sp.host == sp.host && it.sp.port == sp.port && !it.closedByUs && it.socket != null }
            ?.let { return@withLock it }
        closeLink()
        val platform = sp.platform ?: throw IOException("У колонки не определена платформа (platform)")
        val token = conversationToken(sp.id, platform)
        val l = Link(sp, token)
        link = l
        l.open()
        try {
            val ok = withTimeoutOrNull(RESPONSE_TIMEOUT_MS) { l.firstState.await() }
            if (ok == null) throw IOException("Колонка не отвечает. Проверьте, что телефон и колонка в одной Wi-Fi сети")
        } catch (e: Exception) {
            l.close()
            if (link === l) link = null
            throw e
        }
        l
    }

    private var reconnectJob: Job? = null

    private fun linkDropped(l: Link) {
        if (link !== l || _state.value.activeId == null) return
        link = null
        l.socket = null
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (attempt in 1..3) {
                delay(1_500L * attempt)
                val sp = speaker(l.sp.id) ?: l.sp
                try {
                    ensureLink(sp)
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "reconnect $attempt failed: ${e.message}")
                }
            }
            fail("Связь с колонкой потеряна")
        }
    }

    private fun fail(msg: String, needLogin: Boolean = false) {
        advanceJob?.cancel()
        closeLink()
        _state.update { it.copy(activeId = null, playing = false, loginRequired = it.loginRequired || needLogin) }
        onLost(msg)
    }

    private fun normalizeVolume(v: Double): Double =
        if (v.isNaN() || v < 0) -1.0 else (if (v > 1.0) v / 100.0 else v).coerceIn(0.0, 1.0)

    private fun extractPlaying(st: JSONObject): Boolean? {
        if (st.has("playing")) return st.optBoolean("playing")
        val ps = st.optJSONObject("playerState")
        val raw = (ps?.opt("status") ?: st.opt("status") ?: st.opt("playbackState"))
        val s = when (raw) {
            is String -> raw
            is JSONObject -> raw.optString("value")
            else -> null
        }?.lowercase() ?: return null
        return when (s) {
            "playing", "play", "running" -> true
            "paused", "pause", "stopped", "stop", "idle" -> false
            else -> null
        }
    }

    private var lastEndAdvanceAt = 0L

    private fun onGlagolState(st: JSONObject) {
        val now = System.currentTimeMillis()
        val ps = st.optJSONObject("playerState")
        val prev = _state.value
        val playing = extractPlaying(st) ?: prev.playing
        val durationMs = if (ps != null && ps.has("duration")) (ps.optDouble("duration", 0.0) * 1000).toLong() else prev.durationMs
        val progressMs = if (ps != null && ps.has("progress")) (ps.optDouble("progress", 0.0) * 1000).toLong() else prev.progressMs
        val volume = if (st.has("volume")) normalizeVolume(st.optDouble("volume", -1.0)) else prev.volume
        val reportedId = ps?.optString("id")?.substringBefore(':')?.ifBlank { null }

        var idx = index
        var trackId = queue.getOrNull(idx)?.id ?: prev.trackId
        if (reportedId != null && reportedId != trackId) {
            val j = queue.indexOfFirst { it.id == reportedId }
            if (j >= 0) {
                idx = j
                index = j
                trackId = reportedId
            } else if (now - trackStartedAt > 4_000) {
                trackId = reportedId
            }
        }

        val endedNaturally = prev.playing && !playing && prev.durationMs > 0 && prev.progressMs >= prev.durationMs - 2_500
        _state.update {
            it.copy(
                playing = playing,
                durationMs = if (durationMs > 0) durationMs else queue.getOrNull(idx)?.durationMs ?: 0L,
                progressMs = progressMs,
                volume = volume,
                trackId = trackId,
                index = idx,
                receivedAtMs = now,
            )
        }
        if (endedNaturally && now - lastEndAdvanceAt > 5_000 && now - trackStartedAt > 5_000) {
            lastEndAdvanceAt = now
            advance()
        } else {
            scheduleAdvance()
        }
    }

    @Volatile private var queue: List<Track> = emptyList()
    @Volatile private var index = -1
    @Volatile private var repeat = "NONE"
    @Volatile private var trackStartedAt = 0L
    private var advanceJob: Job? = null

    private fun estimatedProgressMs(): Long {
        val s = _state.value
        if (!s.playing) return s.progressMs
        val p = s.progressMs + (System.currentTimeMillis() - s.receivedAtMs)
        return if (s.durationMs > 0) p.coerceAtMost(s.durationMs) else p
    }

    private fun scheduleAdvance() {
        advanceJob?.cancel()
        val s = _state.value
        if (s.activeId == null || !s.playing || queue.isEmpty()) return
        val duration = if (s.durationMs > 0) s.durationMs else queue.getOrNull(index)?.durationMs ?: 0L
        if (duration <= 0) return
        val wait = (duration - estimatedProgressMs() - ADVANCE_LEAD_MS).coerceAtLeast(300L)
        advanceJob = scope.launch {
            delay(wait)
            val cur = _state.value
            if (cur.activeId != null && cur.playing) advance()
        }
    }

    private fun nextIndex(delta: Int): Int {
        if (queue.isEmpty()) return -1
        if (delta > 0 && repeat == "ONE") return index
        val n = index + delta
        return when {
            n in queue.indices -> n
            repeat == "ALL" -> if (n < 0) queue.lastIndex else 0
            else -> -1
        }
    }

    private fun advance() {
        val n = nextIndex(1)
        if (n >= 0) startTrack(n, 0L)
    }

    private fun entityId(t: Track): String = if (!t.albumId.isNullOrBlank()) "${t.id}:${t.albumId}" else t.id

    private fun startTrack(i: Int, positionMs: Long) {
        val t = queue.getOrNull(i) ?: return
        val l = link ?: return
        val now = System.currentTimeMillis()
        index = i
        trackStartedAt = now
        l.send(JSONObject().put("command", "playMusic").put("type", "track").put("id", entityId(t)))
        _state.update {
            it.copy(
                playing = true, progressMs = positionMs.coerceAtLeast(0), durationMs = t.durationMs,
                trackId = t.id, index = i, receivedAtMs = now,
            )
        }
        scheduleAdvance()
        if (positionMs > 1_000) {
            scope.launch {
                delay(500)
                val sampledAt = now
                val pos = positionMs + (System.currentTimeMillis() - sampledAt) + l.halfRttMs
                l.send(JSONObject().put("command", "rewind").put("position", pos / 1000.0))
                _state.update { it.copy(progressMs = pos, receivedAtMs = System.currentTimeMillis()) }
                scheduleAdvance()
            }
        }
    }

    private fun resetPlayback() {
        advanceJob?.cancel()
        reconnectJob?.cancel()
        queue = emptyList()
        index = -1
    }

    fun play(sp: StationSpeaker, tracks: List<Track>, startIndex: Int, positionMs: Long, repeatMode: String) {
        queue = tracks
        index = startIndex
        repeat = repeatMode
        val now = System.currentTimeMillis()
        val cur = tracks.getOrNull(startIndex)
        _state.update {
            it.copy(
                activeId = sp.id, playing = true, progressMs = positionMs.coerceAtLeast(0),
                durationMs = cur?.durationMs ?: 0L, trackId = cur?.id, index = startIndex, receivedAtMs = now,
            )
        }
        scope.launch {
            try {
                ensureLink(sp)
                if (_state.value.activeId != sp.id) return@launch
                startTrack(startIndex, positionMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StationAuthException) {
                fail(
                    if (e.needLogin) "Для управления колонкой нужен вход в Яндекс — кнопка «Войти» в окне «Вывод звука»"
                    else e.message ?: "Колонка не приняла токен",
                    needLogin = e.needLogin,
                )
            } catch (e: Exception) {
                fail(e.message?.takeIf { it.isNotBlank() } ?: "Не удалось включить музыку на колонке")
            }
        }
    }

    fun pause() {
        val l = link ?: return
        val p = estimatedProgressMs()
        advanceJob?.cancel()
        l.send(JSONObject().put("command", "stop"))
        _state.update { it.copy(playing = false, progressMs = p, receivedAtMs = System.currentTimeMillis()) }
    }

    fun resume() {
        val l = link ?: return
        l.send(JSONObject().put("command", "play"))
        _state.update { it.copy(playing = true, receivedAtMs = System.currentTimeMillis()) }
        scheduleAdvance()
    }

    fun seek(ms: Long) {
        val l = link ?: return
        val pos = ms.coerceAtLeast(0)
        l.send(JSONObject().put("command", "rewind").put("position", (pos + l.halfRttMs) / 1000.0))
        _state.update { it.copy(progressMs = pos, receivedAtMs = System.currentTimeMillis()) }
        scheduleAdvance()
    }

    fun skip(delta: Int): Boolean {
        val n = nextIndex(delta)
        if (n < 0 || link == null) return false
        startTrack(n, 0L)
        return true
    }

    fun skipToTrack(trackId: String): Boolean {
        if (link == null) return false
        var best = -1
        for (i in queue.indices) {
            if (queue[i].id == trackId && (best < 0 || kotlin.math.abs(i - index) < kotlin.math.abs(best - index))) best = i
        }
        if (best < 0 || best == index) return false
        startTrack(best, 0L)
        return true
    }

    fun setVolume(v: Double) {
        val l = link ?: return
        val q = Math.round(v.coerceIn(0.0, 1.0) / VOLUME_STEP) * VOLUME_STEP
        l.send(JSONObject().put("command", "setVolume").put("volume", q))
        _state.update { it.copy(volume = q) }
    }

    fun release() {
        link?.send(JSONObject().put("command", "stop"))
        resetPlayback()
        closeLink()
        _state.update { it.copy(activeId = null, playing = false) }
    }
}
