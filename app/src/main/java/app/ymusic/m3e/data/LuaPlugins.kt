package app.ymusic.m3e.data

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.ymusic.m3e.BuildConfig
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JseMathLib
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.LuajavaLib
import java.net.InetAddress
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.net.UnknownHostException
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

interface LuaBridge {
    fun toast(text: String)
    fun currentTrack(): Track?
    fun search(query: String)
    fun startWave(station: String, label: String)

    fun viewModel(): Any? = null
}

private class LuaTimeout(val reason: String) : Error(reason)

object LuaPlugins {
    val PERMISSIONS: Set<String> = setOf("player", "ui", "storage", "net", "search", "theme", "wave")

    fun attach(ctx: Context, player: Player, bridge: LuaBridge) {
        lxCtx = ctx.applicationContext
        lxStartWatchdog()
        lxListener?.let { old -> runCatching { lxPlayer?.removeListener(old) } }
        lxBridge = bridge
        lxPlayer = player
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return
                val t = lxBridge?.currentTrack() ?: return
                luaEmit("track_changed", t)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                luaEmit("play_state", isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) luaEmit("ended")
            }
        }
        lxListener = l
        player.addListener(l)
        sync()
    }

    fun sync() {
        if (lxBridge == null) return
        val want: List<InstalledPlugin> =
            if (PluginSystem.enabled) PluginSystem.plugins.filter { it.enabled } else emptyList()
        luaExec.execute {
            try {
                reconcile(want)
            } catch (t: Throwable) {
                AppLog.e(LX_TAG, "Сбой при запуске плагинов: ${t.message}", t)
            }
        }
    }

    fun settingChanged(p: InstalledPlugin, key: String, value: Any) {
        luaExec.execute {
            val inst = luaRunning[p.id]
            if (inst != null && inst.plugin === p) {
                if (key.startsWith(PluginHooks.KEY_PREFIX) && inst.unsafeBuilt != p.unsafeOn) {
                    luaRunning.remove(p.id)
                    inst.stop()
                    luaFailed.remove(p.id)
                    val want: List<InstalledPlugin> = if (PluginSystem.enabled) PluginSystem.plugins.filter { it.enabled } else emptyList()
                    reconcile(want)
                    return@execute
                }
                if (key.startsWith(PluginHooks.KEY_PREFIX)) inst.evalHooks()
                inst.fire("setting_changed", key, value)
            }
        }
    }

    fun uiEvent(p: InstalledPlugin, entryId: String, index: Int, value: Any?) {
        luaExec.execute {
            val inst = luaRunning[p.id]
            if (inst != null && inst.plugin === p) inst.uiEvent(entryId, index, value)
        }
    }

    fun dialogEvent(p: InstalledPlugin, key: Int, button: Int, input: String?) {
        luaExec.execute {
            val inst = luaRunning[p.id]
            if (inst != null && inst.plugin === p) inst.dialogEvent(key, button, input)
        }
    }

    fun sheetClosed(p: InstalledPlugin, entryId: String) {
        luaExec.execute {
            val inst = luaRunning[p.id]
            if (inst != null && inst.plugin === p) inst.sheetClosed(entryId)
        }
    }

    fun renderPages(p: InstalledPlugin, entryId: String?) {
        luaExec.execute {
            val inst = luaRunning[p.id]
            if (inst != null && inst.plugin === p) inst.scheduleRender(entryId)
        }
    }

    fun action(p: InstalledPlugin, key: String) {
        luaExec.execute {
            val inst = luaRunning[p.id]
            if (inst != null && inst.plugin === p) inst.fire("action", key)
        }
    }

    fun purge(id: String) {
        val ctx = lxCtx ?: return
        luaExec.execute { runCatching { ctx.deleteSharedPreferences(lxStorageName(id)) } }
    }
}

private const val LX_TAG = "Lua"
private const val LX_BUDGET_MS = 3000L
private const val LX_HOOK_STEP = 20000
private const val LX_MAX_SCRIPT = 512 * 1024
private const val LX_MAX_MODULES = 64
private const val LX_MAX_TIMERS = 16
private const val LX_MIN_TIMER_MS = 500L
private const val LX_MAX_HANDLERS = 16
private const val LX_MAX_STORAGE_KEYS = 200
private const val LX_MAX_STORAGE_VALUE = 64 * 1024
private const val LX_MAX_STATIONS = 40
private const val LX_MAX_HTTP_PER_MIN = 30
private const val LX_MAX_HTTP_BODY = 1024L * 1024L
private const val LX_MAX_LOG_LINES = 200

private const val LX_MAX_HEAP_GROWTH = 96L * 1024 * 1024
private const val LX_HANG_GRACE_MS = 5000L
private const val LX_MAX_POSTS = 200
private const val LX_MAX_STRING = 1024 * 1024
private const val LX_MAX_PATTERN_SUBJECT = 16 * 1024
private const val LX_MAX_PATTERN = 120
private const val LX_MAX_BODY_OUT = 256 * 1024
private const val LX_MAX_QUEUE = 500
private const val LX_MAX_UNPACK = 8000
private const val LX_MAX_UTIL = 256 * 1024
private const val LX_MAX_SPLIT = 1000
private const val LX_MAX_QUEUE_VIEW = 100

private val LX_FORBIDDEN_HEADERS = setOf("host", "content-length", "transfer-encoding", "connection", "upgrade", "te", "proxy-authorization", "expect")
private val LX_EVENTS = setOf("track_changed", "play_state", "ended", "unload", "setting_changed", "action")
private val LX_PLAYER_EVENTS = setOf("track_changed", "play_state", "ended")
private val LX_FMT_BAD = Regex("%[-+ #0]*\\d{3,}|%[-+ #0]*\\d*\\.\\d{3,}")
private val LX_STATION_RE = Regex("^[a-z_]{2,20}:[A-Za-z0-9_.:\\-]{1,64}$")
private val LX_KEY_RE = Regex("^[A-Za-z0-9_.:\\-]{1,48}$")
private val LX_PAGE_RE = Regex("^[a-z][a-z0-9_]{0,31}$")
private val LX_NODE_ID_RE = Regex("^[A-Za-z0-9_]{1,32}$")
private val LX_OPTION_RE = Regex("^[A-Za-z0-9_.-]{1,32}$")
private val LX_COLOR_RE = Regex("^#[0-9A-Fa-f]{6}$")
private const val LX_MAX_PAGES = 8
private const val LX_MAX_NODES = 80
private val LX_MODULE_RE = Regex("^[A-Za-z0-9_./\\-]{1,80}$")

private val luaMain = Handler(Looper.getMainLooper())

private fun lxNewExec() = ScheduledThreadPoolExecutor(
    1,
    ThreadFactory { r -> Thread(null, r, "lua-plugins", 4L * 1024 * 1024).apply { isDaemon = true } },
).apply { removeOnCancelPolicy = true }

@Volatile private var luaExec = lxNewExec()

private val luaWatch = ScheduledThreadPoolExecutor(1, ThreadFactory { r -> Thread(r, "lua-watchdog").apply { isDaemon = true } })
@Volatile private var lxWatchStarted = false
@Volatile private var luaActive: LuaInst? = null

@Volatile private var lxCtx: Context? = null
@Volatile private var lxBridge: LuaBridge? = null
@Volatile private var lxPlayer: Player? = null
private var lxListener: Player.Listener? = null

private val luaRunning = ConcurrentHashMap<String, LuaInst>()
private val luaFailed = ConcurrentHashMap<String, InstalledPlugin>()

private fun lxBadAddress(a: InetAddress): Boolean {
    if (a.isAnyLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress || a.isSiteLocalAddress || a.isMulticastAddress) return true
    val b = a.address
    if (b.size == 16 && (b[0].toInt() and 0xFE) == 0xFC) return true
    if (b.size == 4) {
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        val b2 = b[2].toInt() and 0xFF
        if (b0 == 0) return true
        if (b0 == 100 && (b1 and 0xC0) == 64) return true
        if (b0 == 192 && b1 == 0 && b2 == 0) return true
        if (b0 == 198 && (b1 and 0xFE) == 18) return true
        if (b0 >= 240) return true
    }
    if (b.size == 16) {
        val nat64 = b[0].toInt() == 0x00 && b[1].toInt() == 0x64 && (b[2].toInt() and 0xFF) == 0xFF &&
            (b[3].toInt() and 0xFF) == 0x9B && (4 until 12).all { b[it].toInt() == 0 }
        if (nat64 || ((b[0].toInt() and 0xFF) == 0x20 && (b[1].toInt() and 0xFF) == 0x02)) {
            val off = if (nat64) 12 else 2
            val v4 = runCatching { InetAddress.getByAddress(byteArrayOf(b[off], b[off + 1], b[off + 2], b[off + 3])) }.getOrNull()
            if (v4 != null && lxBadAddress(v4)) return true
        }
    }
    return false
}

private val luaDns = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val l = Dns.SYSTEM.lookup(hostname)
        if (l.any { lxBadAddress(it) }) throw UnknownHostException("локальные адреса запрещены")
        return l
    }
}

private val luaHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .dns(luaDns)
        .proxy(java.net.Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}

private fun lxStorageName(id: String) = "plugin_lua_$id"

private fun luaEmit(event: String, vararg args: Any?) {
    if (!PluginSystem.enabled) return
    val ex = luaExec
    if (ex.queue.size > LX_MAX_QUEUE) return
    ex.execute {
        for (inst in luaRunning.values.toList()) {
            try { inst.fire(event, *args) } catch (t: Throwable) { AppLog.w(LX_TAG, "event $event: ${t.message}") }
        }
    }
}

private fun lxUsedHeap(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

private fun lxStartWatchdog() {
    if (lxWatchStarted) return
    lxWatchStarted = true
    luaWatch.scheduleWithFixedDelay({
        try { lxWatchdogCheck() } catch (t: Throwable) { AppLog.w(LX_TAG, "watchdog: ${t.message}") }
    }, 1, 1, TimeUnit.SECONDS)
}

private fun lxWatchdogCheck() {
    val inst = luaActive ?: return
    if (inst.stopped || inst.inBlocking) return
    if (System.nanoTime() <= inst.deadline + LX_HANG_GRACE_MS * 1_000_000L) return
    luaActive = null
    inst.kill()
    AppLog.e(LX_TAG, "[${inst.id}] скрипт завис, поток Lua перезапущен")
    val old = luaExec
    luaExec = lxNewExec()
    luaRunning.clear()
    luaFailed.clear()
    runCatching { old.shutdownNow() }
    val p = inst.plugin
    luaMain.post {
        p.luaAccent = null
        p.luaAmoled = null
        p.luaStations.clear()
        p.uiEntries.clear()
        p.uiPages.clear()
        PluginSystem.dropUi(p.id)
        p.luaStatus = "Остановлен: скрипт завис"
        PluginSystem.setPluginEnabled(p, false)
        lxBridge?.toast("Плагин «${p.manifest.name}» завис и выключен")
    }
}

private fun reconcile(want: List<InstalledPlugin>) {
    for (id in luaRunning.keys.toList()) {
        val inst = luaRunning[id] ?: continue
        val w = want.firstOrNull { it.id == id }
        if (w == null || w !== inst.plugin) {
            luaRunning.remove(id)
            inst.stop()
        }
    }
    luaFailed.entries.removeAll { e -> want.none { it === e.value } }
    for (p in want) {
        if (luaRunning.containsKey(p.id) || luaFailed[p.id] === p) continue
        val inst = LuaInst(p)
        luaRunning[p.id] = inst
        if (!inst.boot()) {
            luaRunning.remove(p.id)
            luaFailed[p.id] = p
        }
    }
}

private fun <T> luaOnMain(timeoutMs: Long = 3000L, f: () -> T): T? {
    if (Looper.myLooper() === Looper.getMainLooper()) return f()
    val latch = CountDownLatch(1)
    var result: T? = null
    var error: Throwable? = null
    luaMain.post {
        try { result = f() } catch (t: Throwable) { error = t }
        latch.countDown()
    }
    if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
    error?.let { throw it }
    return result
}

private fun LuaTable.fn(name: String, f: (Varargs) -> Varargs) {
    set(name, object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = f(args)
    })
}

private fun lxStr(s: String?): LuaValue = LuaValue.valueOf(s ?: "")

private fun lxTrackTable(t: Track): LuaTable {
    val r = LuaTable()
    r.set("id", lxStr(t.id))
    r.set("title", lxStr(t.title))
    r.set("artists", lxStr(t.artists))
    r.set("album", lxStr(t.albumTitle))
    r.set("duration_ms", LuaValue.valueOf(t.durationMs.toDouble()))
    r.set("cover", lxStr(t.cover(400)))
    r.set("explicit", LuaValue.valueOf(t.explicit))
    r.set("year", LuaValue.valueOf(t.year))
    r.set("genre", lxStr(t.genre))
    r.set("label", lxStr(t.label))
    r.set("version", lxStr(t.version))
    r.set("local", LuaValue.valueOf(t.isLocal))
    return r
}

private fun lxToLua(x: Any?): LuaValue = when (x) {
    null -> LuaValue.NIL
    is Track -> lxTrackTable(x)
    is Boolean -> LuaValue.valueOf(x)
    is Int -> LuaValue.valueOf(x)
    is Double -> LuaValue.valueOf(x)
    is String -> LuaValue.valueOf(x)
    else -> LuaValue.valueOf(x.toString())
}

private fun lxCloneTable(t: LuaTable): LuaTable {
    val c = LuaTable()
    var e = t.next(LuaValue.NIL)
    while (!e.arg1().isnil()) {
        c.set(e.arg1(), e.arg(2))
        e = t.next(e.arg1())
    }
    return c
}

private fun lxClean(s: String, max: Int): String =
    s.map { if (it == '\n' || !it.isISOControl()) it else ' ' }.joinToString("").trim().take(max)

private fun lxBuildPage(r: LuaValue): Pair<List<PluginUiNode>, Array<LuaValue?>> {
    if (!r.istable()) throw LuaError("render должна вернуть таблицу-список компонентов")
    val arr = r.checktable()
    val n = arr.length()
    if (n > LX_MAX_NODES) throw LuaError("на странице не больше $LX_MAX_NODES компонентов")
    val nodes = ArrayList<PluginUiNode>(n)
    val cbs = arrayOfNulls<LuaValue>(n)
    val seen = HashSet<String>()
    for (i in 1..n) {
        val t = arr.get(i)
        if (!t.istable()) throw LuaError("компонент №$i: ожидалась таблица")
        val type = t.get("type").checkjstring()
        val title = lxClean(t.get("title").optjstring(""), 80)
        val summary = lxClean(
            (if (!t.get("summary").isnil()) t.get("summary") else t.get("text")).optjstring(""), 200,
        )
        if (type != "section" && title.isEmpty()) throw LuaError("компонент №$i ($type): нужен title")
        val rawId = t.get("id").optjstring(null)
        if (rawId != null && !LX_NODE_ID_RE.matches(rawId)) throw LuaError("компонент №$i: id — латиница, цифры и _ (до 32 символов)")
        val key = rawId ?: "n$i"
        if (!seen.add(key)) throw LuaError("компонент №$i: id «$key» повторяется")
        val v = t.get("value")
        val cbName = if (type == "button") "on_click" else "on_change"
        val cb = t.get(cbName)
        if (!cb.isnil()) {
            if (!cb.isfunction()) throw LuaError("компонент №$i: $cbName должна быть функцией")
            if (type == "section" || type == "info") throw LuaError("компонент №$i ($type): у него нет $cbName")
            cbs[i - 1] = cb
        }
        val st: PluginSetting
        val value: Any?
        when (type) {
            "section", "info" -> { st = PluginSetting(key, type, title, summary); value = null }
            "button" -> { st = PluginSetting(key, type, title, summary); value = null }
            "switch" -> {
                val b = v.optboolean(false)
                st = PluginSetting(key, type, title, summary, default = b); value = b
            }
            "slider", "number" -> {
                val mn = t.get("min").optdouble(0.0)
                val mx = t.get("max").optdouble(100.0)
                val sp = t.get("step").optdouble(1.0)
                if (mn.isNaN() || mx.isNaN() || sp.isNaN() || mn >= mx || sp <= 0.0 || Math.abs(mn) > 1e9 || Math.abs(mx) > 1e9 || (mx - mn) / sp > 100000.0)
                    throw LuaError("компонент №$i: неверные min/max/step")
                val raw = v.optdouble(mn).let { if (it.isNaN() || it.isInfinite()) mn else it }
                val tmp = PluginSetting(key, type, title, summary, min = mn, max = mx, step = sp)
                val x = PluginSystem.snapSetting(tmp, raw)
                st = tmp.copy(default = x); value = x
            }
            "text" -> {
                val ml = t.get("max_length").optint(100).coerceIn(1, 500)
                val x = v.optjstring("").take(ml)
                st = PluginSetting(key, type, title, summary, default = x, maxLength = ml); value = x
            }
            "choice" -> {
                val oa = t.get("options")
                if (!oa.istable()) throw LuaError("компонент №$i: у choice нужен список options")
                val on = oa.length()
                if (on < 2 || on > 12) throw LuaError("компонент №$i: options — от 2 до 12 вариантов")
                val opts = ArrayList<Pair<String, String>>()
                for (j in 1..on) {
                    val o = oa.get(j)
                    val ov: String
                    val ot: String
                    if (o.istable()) { ov = o.get("value").checkjstring(); ot = lxClean(o.get("title").optjstring(ov), 40) }
                    else { ov = o.checkjstring(); ot = lxClean(ov, 40) }
                    if (!LX_OPTION_RE.matches(ov)) throw LuaError("компонент №$i: value варианта №$j — латиница, цифры, . _ - (до 32)")
                    if (ot.isEmpty()) throw LuaError("компонент №$i: пустая подпись варианта №$j")
                    if (opts.any { it.first == ov }) throw LuaError("компонент №$i: value «$ov» повторяется")
                    opts += ov to ot
                }
                val cur = v.optjstring(opts[0].first).let { c -> if (opts.any { it.first == c }) c else opts[0].first }
                st = PluginSetting(key, type, title, summary, default = cur, options = opts); value = cur
            }
            "color" -> {
                val c = v.optjstring("#6750A4").trim().let { if (LX_COLOR_RE.matches(it)) it.uppercase() else "#6750A4" }
                st = PluginSetting(key, type, title, summary, default = c); value = c
            }
            else -> throw LuaError("компонент №$i: неизвестный тип «$type» (section, info, switch, slider, number, text, choice, color, button)")
        }
        nodes += PluginUiNode(i - 1, st, value)
    }
    return nodes to cbs
}

private fun lxToJson(v: LuaValue, depth: Int = 0): Any? {
    if (depth > 12) throw LuaError("json: слишком глубокая вложенность")
    return when (v.type()) {
        LuaValue.TNIL -> JSONObject.NULL
        LuaValue.TBOOLEAN -> v.toboolean()
        LuaValue.TNUMBER -> {
            val d = v.todouble()
            if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong() else d
        }
        LuaValue.TSTRING -> v.tojstring()
        LuaValue.TTABLE -> lxTableToJson(v.checktable(), depth)
        else -> throw LuaError("json: значение типа «${v.typename()}» нельзя сохранить")
    }
}

private fun lxTableToJson(t: LuaTable, depth: Int): Any {
    val len = t.length()
    var count = 0
    var e = t.next(LuaValue.NIL)
    while (!e.arg1().isnil()) {
        count++
        e = t.next(e.arg1())
    }
    if (count == len) {
        val arr = JSONArray()
        for (i in 1..len) arr.put(lxToJson(t.get(i), depth + 1))
        return arr
    }
    val obj = JSONObject()
    e = t.next(LuaValue.NIL)
    while (!e.arg1().isnil()) {
        obj.put(e.arg1().tojstring(), lxToJson(e.arg(2), depth + 1))
        e = t.next(e.arg1())
    }
    return obj
}

private fun lxFromJson(x: Any?, depth: Int = 0): LuaValue {
    if (depth > 12) throw LuaError("json: слишком глубокая вложенность")
    return when (x) {
        null -> LuaValue.NIL
        JSONObject.NULL -> LuaValue.NIL
        is Boolean -> LuaValue.valueOf(x)
        is Int -> LuaValue.valueOf(x)
        is Long -> LuaValue.valueOf(x.toDouble())
        is Double -> LuaValue.valueOf(x)
        is Number -> LuaValue.valueOf(x.toDouble())
        is String -> LuaValue.valueOf(x)
        is JSONArray -> {
            val t = LuaTable()
            for (i in 0 until x.length()) t.set(i + 1, lxFromJson(x.opt(i), depth + 1))
            t
        }
        is JSONObject -> {
            val t = LuaTable()
            val keys = x.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                t.set(k, lxFromJson(x.opt(k), depth + 1))
            }
            t
        }
        else -> LuaValue.valueOf(x.toString())
    }
}

private fun lxCheckUrl(raw: String): HttpUrl {
    val url = raw.trim().toHttpUrlOrNull() ?: throw LuaError("http: неверный адрес")
    if (!url.isHttps) throw LuaError("http: разрешён только https")
    val host = url.host
    if (host.contains(':') || host.all { it.isDigit() || it == '.' }) {
        val addr = try { InetAddress.getByName(host) } catch (e: Exception) { throw LuaError("http: неверный адрес") }
        if (lxBadAddress(addr)) throw LuaError("http: локальные адреса запрещены")
    }
    if (host.equals("localhost", true) || host.endsWith(".localhost", true) || host.endsWith(".local", true) || host.endsWith(".internal", true))
        throw LuaError("http: локальные адреса запрещены")
    return url
}

private fun lxPad2(n: Int) = if (n < 10) "0$n" else n.toString()

private fun lxDate(fmtRaw: String, ms: Long): LuaValue {
    val fmt = fmtRaw.removePrefix("!")
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    if (fmt.startsWith("*t")) {
        val t = LuaTable()
        t.set("year", LuaValue.valueOf(c.get(Calendar.YEAR)))
        t.set("month", LuaValue.valueOf(c.get(Calendar.MONTH) + 1))
        t.set("day", LuaValue.valueOf(c.get(Calendar.DAY_OF_MONTH)))
        t.set("hour", LuaValue.valueOf(c.get(Calendar.HOUR_OF_DAY)))
        t.set("min", LuaValue.valueOf(c.get(Calendar.MINUTE)))
        t.set("sec", LuaValue.valueOf(c.get(Calendar.SECOND)))
        t.set("wday", LuaValue.valueOf(c.get(Calendar.DAY_OF_WEEK)))
        t.set("yday", LuaValue.valueOf(c.get(Calendar.DAY_OF_YEAR)))
        t.set("isdst", LuaValue.FALSE)
        return t
    }
    val sb = StringBuilder()
    var i = 0
    while (i < fmt.length) {
        val ch = fmt[i]
        if (ch != '%' || i + 1 >= fmt.length) { sb.append(ch); i++; continue }
        i++
        when (val f = fmt[i]) {
            'Y' -> sb.append(c.get(Calendar.YEAR))
            'y' -> sb.append(lxPad2(c.get(Calendar.YEAR) % 100))
            'm' -> sb.append(lxPad2(c.get(Calendar.MONTH) + 1))
            'd' -> sb.append(lxPad2(c.get(Calendar.DAY_OF_MONTH)))
            'H' -> sb.append(lxPad2(c.get(Calendar.HOUR_OF_DAY)))
            'M' -> sb.append(lxPad2(c.get(Calendar.MINUTE)))
            'S' -> sb.append(lxPad2(c.get(Calendar.SECOND)))
            'c' -> sb.append(c.get(Calendar.YEAR)).append('-').append(lxPad2(c.get(Calendar.MONTH) + 1)).append('-')
                .append(lxPad2(c.get(Calendar.DAY_OF_MONTH))).append(' ').append(lxPad2(c.get(Calendar.HOUR_OF_DAY)))
                .append(':').append(lxPad2(c.get(Calendar.MINUTE))).append(':').append(lxPad2(c.get(Calendar.SECOND)))
            'x' -> sb.append(lxPad2(c.get(Calendar.DAY_OF_MONTH))).append('.').append(lxPad2(c.get(Calendar.MONTH) + 1))
                .append('.').append(c.get(Calendar.YEAR))
            'X' -> sb.append(lxPad2(c.get(Calendar.HOUR_OF_DAY))).append(':').append(lxPad2(c.get(Calendar.MINUTE)))
                .append(':').append(lxPad2(c.get(Calendar.SECOND)))
            '%' -> sb.append('%')
            else -> sb.append('%').append(f)
        }
        i++
    }
    return LuaValue.valueOf(sb.toString())
}

private class LxOnce(val orig: LuaValue, val detach: (LxOnce) -> Unit) : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs {
        detach(this)
        return orig.invoke(args)
    }
}

private class LuaInst(val plugin: InstalledPlugin) {
    val id: String = plugin.id
    private val perms: Set<String> = LuaPlugins.PERMISSIONS
    private val g = Globals()

    @Volatile var deadline = Long.MAX_VALUE
    @Volatile var inBlocking = false
    @Volatile var stopped = false
        private set
    private var memStart = 0L
    private var posts = 0
    private var logWindow = 0L
    private var logPerSec = 0
    private var lastWave = 0L
    private var lastSearch = 0L

    private val handlers = HashMap<String, ArrayList<LuaValue>>()

    private val hookHandlers = HashMap<String, LuaValue>()
    private val hookActive = HashSet<String>()
    private val hookPending = HashSet<String>()

    @Volatile var unsafeBuilt = false
        private set
    private val timers = HashMap<Int, ScheduledFuture<*>>()
    private var timerSeq = 0
    private val modules = HashMap<String, LuaValue>()
    private val myStations = ArrayList<PluginStation>()
    private var logCount = 0
    private var lastToast = 0L
    private var lastDialog = 0L
    private val httpTimes = ArrayDeque<Long>()

    private class PageDef(
        val id: String, val title: String, val summary: String, val menu: Boolean, val render: LuaValue,
        val onClose: LuaValue? = null, val transient: Boolean = false,
    )
    private val dialogCbs = HashMap<Int, Array<LuaValue?>>()
    private var dialogSeq = 0
    private val pages = LinkedHashMap<String, PageDef>()
    private val pageCallbacks = HashMap<String, Array<LuaValue?>>()
    private val renderPending = HashSet<String>()
    private val prefs by lazy {
        (lxCtx ?: throw LuaError("хранилище недоступно")).getSharedPreferences(lxStorageName(id), Context.MODE_PRIVATE)
    }

    fun boot(): Boolean {
        val entry = plugin.manifest.lua
        try {
            buildGlobals()
        } catch (t: Throwable) {
            AppLog.e(LX_TAG, "[$id] не удалось создать окружение: ${t.message}", t)
            abort("Не запустился: ${t.message ?: t.javaClass.simpleName}", disable = false)
            return false
        }
        val bytes = PluginSystem.readFile(plugin, entry, LX_MAX_SCRIPT)
        if (bytes == null) {
            abort("Не запустился: файл «$entry» не прочитан", disable = false)
            return false
        }
        val src = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
        val ok = guard("запуск") { g.load(src, "@$entry").call() }
        if (!ok) {
            if (!stopped) abort(status ?: "Не запустился", disable = false)
            return false
        }
        setStatus("Работает")
        AppLog.i(LX_TAG, "[$id] запущен")
        evalHooks()
        return true
    }

    fun stop() {
        if (stopped) return
        deactivateHooks()
        fire("unload")
        stopped = true
        cleanup()
        luaMain.post { plugin.luaStatus = null }
    }

    fun kill() { stopped = true }

    private fun abort(message: String, disable: Boolean) {
        if (stopped) return
        stopped = true
        cleanup()
        luaRunning.remove(id)
        luaMain.post {
            plugin.luaStatus = message
            if (disable) {
                PluginSystem.setPluginEnabled(plugin, false)
                lxBridge?.toast("Плагин «${plugin.manifest.name}» остановлен: $message")
            }
        }
    }

    private fun cleanup() {
        for (f in timers.values) f.cancel(false)
        timers.clear()
        handlers.clear()
        hookHandlers.clear()
        hookActive.clear()
        hookPending.clear()
        myStations.clear()
        pages.clear()
        pageCallbacks.clear()
        renderPending.clear()
        dialogCbs.clear()
        luaMain.post {
            PluginSystem.dropUi(plugin.id)
            plugin.luaAccent = null
            plugin.luaAmoled = null
            plugin.luaStations.clear()
            plugin.uiEntries.clear()
            plugin.uiPages.clear()
        }
    }

    private var status: String? = null

    private fun setStatus(s: String) {
        status = s
        luaMain.post { plugin.luaStatus = s }
    }

    private fun guard(what: String, block: () -> Unit): Boolean {
        if (stopped) return false
        deadline = System.nanoTime() + LX_BUDGET_MS * 1_000_000L
        logCount = 0
        posts = 0
        memStart = lxUsedHeap()
        luaActive = this
        try {
            block()
            return true
        } catch (e: LuaTimeout) {
            AppLog.w(LX_TAG, "[$id] $what: ${e.reason}, плагин остановлен")
            abort("Остановлен: ${e.reason}", disable = true)
        } catch (e: LuaError) {
            val full = e.message ?: "ошибка"
            AppLog.w(LX_TAG, "[$id] $what: $full")
            setStatus("Ошибка: " + full.lineSequence().firstOrNull().orEmpty().take(160))
        } catch (t: Throwable) {
            AppLog.w(LX_TAG, "[$id] $what: ${t.javaClass.simpleName}: ${t.message}", t)
            setStatus("Сбой: ${t.javaClass.simpleName}")
        } finally {
            if (luaActive === this) luaActive = null
        }
        return false
    }

    fun fire(event: String, vararg args: Any?) {
        val list = handlers[event]?.toList() ?: return
        for (h in list) {
            guard("событие $event") {
                if (args.isEmpty()) h.call()
                else h.invoke(LuaValue.varargsOf(Array<LuaValue>(args.size) { i -> lxToLua(args[i]) }))
            }
            if (stopped) return
        }
    }

    private fun hookAllowed(hid: String): Boolean {
        val cur = plugin.settingsCopy
        return plugin.manifest.hooks.any { it.id == hid } && cur[PluginHooks.MASTER_KEY] == true && cur[PluginHooks.key(hid)] != false
    }

    fun evalHooks(only: String? = null) {
        if (stopped) return
        for (h in plugin.manifest.hooks) {
            if (only != null && h.id != only) continue
            val fn = hookHandlers[h.id] ?: continue
            val want = hookAllowed(h.id)
            val now = h.id in hookActive
            if (want == now && !(want && only != null)) continue
            if (want) hookActive.add(h.id) else hookActive.remove(h.id)
            guard("хук ${h.id}") { fn.call(LuaValue.valueOf(want)) }
            if (stopped) return
        }
    }

    private fun deactivateHooks() {
        for (h in plugin.manifest.hooks) {
            if (h.id !in hookActive) continue
            hookActive.remove(h.id)
            val fn = hookHandlers[h.id] ?: continue
            guard("хук ${h.id}") { fn.call(LuaValue.FALSE) }
            if (stopped) return
        }
    }

    private fun installHooks() {
        val hk = LuaTable()
        fun declared(what: String, id: String) {
            if (plugin.manifest.hooks.none { it.id == id }) {
                throw LuaError("$what: хук «$id» не объявлен в manifest.json (contributes.hooks)")
            }
        }
        hk.fn("on") { a ->
            val id = a.checkjstring(1)
            val f = a.checkfunction(2)
            declared("hooks.on", id)
            hookHandlers[id] = f
            LuaValue.NONE
        }
        hk.fn("off") { a ->
            val id = a.checkjstring(1)
            declared("hooks.off", id)
            hookActive.remove(id)
            LuaValue.valueOf(hookHandlers.remove(id) != null)
        }
        hk.fn("refresh") { a ->
            val only = if (a.isnil(1)) null else a.checkjstring(1).also { declared("hooks.refresh", it) }
            val key = only ?: "*"
            if (hookPending.add(key)) {
                luaExec.execute {
                    hookPending.remove(key)
                    if (!stopped && luaRunning[this.id] === this) {
                        if (only != null) evalHooks(only)
                        else for (h in plugin.manifest.hooks) evalHooks(h.id)
                    }
                }
            }
            LuaValue.NONE
        }
        hk.fn("enabled") { a ->
            val id = a.checkjstring(1)
            declared("hooks.enabled", id)
            LuaValue.valueOf(hookAllowed(id))
        }
        g.set("hooks", hk)
    }

    private fun <T> blocking(f: () -> T): T {
        val t0 = System.nanoTime()
        inBlocking = true
        try {
            return f()
        } finally {
            deadline += System.nanoTime() - t0
            inBlocking = false
        }
    }

    private fun post(f: () -> Unit) {
        if (++posts > LX_MAX_POSTS) throw LuaError("слишком много обращений к интерфейсу за один вызов")
        luaMain.post { f() }
    }

    private fun need(perm: String) {
        if (perm !in perms) throw LuaError("возможность «$perm» недоступна")
    }

    fun scheduleRender(entryId: String?) {
        if (stopped) return
        val ids = if (entryId == null) pages.keys.toList() else listOf(entryId)
        for (pid in ids) {
            if (pid !in pages || !renderPending.add(pid)) continue
            luaExec.execute {
                renderPending.remove(pid)
                if (!stopped && luaRunning[id] === this) renderPage(pid)
            }
        }
    }

    private fun renderPage(pid: String) {
        val def = pages[pid] ?: return
        var built: Pair<List<PluginUiNode>, Array<LuaValue?>>? = null
        guard("страница «$pid»") { built = lxBuildPage(def.render.call()) }
        val b = built ?: return
        pageCallbacks[pid] = b.second
        val pl = plugin
        luaMain.post { pl.uiPages[pid] = b.first }
    }

    fun dialogEvent(key: Int, button: Int, input: String?) {
        val cbs = dialogCbs.remove(key) ?: return
        val cb = if (button >= 0) cbs.getOrNull(button) else cbs.lastOrNull()
        if (cb != null) {
            guard("окно") { if (input != null) cb.call(LuaValue.valueOf(input)) else cb.call() }
        }
        if (!stopped) scheduleRender(null)
    }

    fun sheetClosed(entryId: String) {
        val def = pages[entryId] ?: return
        def.onClose?.let { cb -> guard("закрытие «$entryId»") { cb.call() } }
        if (def.transient && !stopped) {
            pages.remove(entryId)
            pageCallbacks.remove(entryId)
            luaMain.post { plugin.uiEntries.removeAll { it.id == entryId }; plugin.uiPages.remove(entryId) }
        }
    }

    private fun registerPage(d: LuaTable, transient: Boolean, fn: String): String {
        val pid = d.get("id").optjstring(if (transient) "sheet" else null)
            ?: throw LuaError("$fn: нужен id")
        if (!LX_PAGE_RE.matches(pid)) throw LuaError("$fn: id — латиница в нижнем регистре, цифры и _ (до 32 символов, с буквы)")
        val title = d.get("title").checkjstring().trim()
        if (title.isEmpty() || title.length > 60) throw LuaError("$fn: title от 1 до 60 символов")
        val summary = d.get("summary").optjstring("").trim().take(100)
        val menu = !transient && d.get("menu").optboolean(true)
        val render = d.get("render")
        if (!render.isfunction()) throw LuaError("$fn: render должна быть функцией")
        val onClose = d.get("on_close").takeIf { !it.isnil() }
        if (onClose != null && !onClose.isfunction()) throw LuaError("$fn: on_close должна быть функцией")
        if (pid !in pages && pages.size >= LX_MAX_PAGES) throw LuaError("$fn: не больше $LX_MAX_PAGES страниц")
        pages[pid] = PageDef(pid, title, summary, menu, render, onClose, transient)
        val entry = PluginUiEntry(pid, title, summary, menu, transient)
        post {
            val i = plugin.uiEntries.indexOfFirst { it.id == pid }
            if (i >= 0) plugin.uiEntries[i] = entry else plugin.uiEntries.add(entry)
        }
        scheduleRender(pid)
        return pid
    }

    private fun openDialog(
        title: String, text: String, buttons: List<Triple<String, String, LuaValue?>>, dismiss: LuaValue?,
        input: Triple<String, String, Int>?, cancelable: Boolean,
    ): Int? {
        val now = System.currentTimeMillis()
        if (now - lastDialog < 1500L || dialogCbs.size >= 3) return null
        if (title.isEmpty() && text.isEmpty()) return null
        lastDialog = now
        val key = ++dialogSeq
        dialogCbs[key] = Array(buttons.size + 1) { i -> if (i < buttons.size) buttons[i].third else dismiss }
        val d = PluginDialog(
            key = key, pluginId = id, pluginName = plugin.manifest.name, title = title.ifEmpty { plugin.manifest.name }, text = text,
            buttons = buttons.map { PluginDialogButton(it.first, it.second) },
            inputValue = input?.first, inputHint = input?.second ?: "", inputMaxLength = input?.third ?: 100, cancelable = cancelable,
        )
        post { PluginSystem.showDialog(d) }
        return key
    }

    fun uiEvent(entryId: String, index: Int, value: Any?) {
        val cb = pageCallbacks[entryId]?.getOrNull(index)
        if (cb != null) {
            guard("действие на странице «$entryId»") {
                if (value == null) cb.call() else cb.call(lxToLua(value))
            }
        }
        if (!stopped) scheduleRender(entryId)
    }

    private fun buildGlobals() {
        val pkgStub = LuaTable()
        pkgStub.set("loaded", LuaTable())
        g.set("package", pkgStub)
        g.load(BaseLib())
        g.load(Bit32Lib())
        g.load(TableLib())
        g.load(StringLib())
        g.load(JseMathLib())
        g.load(DebugLib())
        if (plugin.unsafeOn) {
            g.load(LuajavaLib())
            unsafeBuilt = true
            AppLog.w(LX_TAG, "[$id] песочница отключена пользователем: доступен Java-код (luajava)")
        }
        LoadState.install(g)
        LuaC.install(g)
        g.set("package", LuaValue.NIL)

        val strLib = g.get("string").checktable()
        for (n in arrayOf("find", "match", "gmatch", "gsub")) {
            val orig = strLib.get(n)
            strLib.fn(n) { a ->
                if (a.arg(1).tojstring().length > LX_MAX_PATTERN_SUBJECT || a.arg(2).tojstring().length > LX_MAX_PATTERN)
                    throw LuaError("string.$n: слишком длинная строка или шаблон")
                orig.invoke(a)
            }
        }
        val origGsub = strLib.get("gsub")
        strLib.fn("gsub") { a ->
            val repl = a.arg(3)
            if (repl.isstring()) {
                val worst = a.arg(1).tojstring().length.toLong().coerceAtLeast(1L) * (repl.tojstring().length.toLong() + 1L)
                if (worst > LX_MAX_STRING) throw LuaError("string.gsub: результат может оказаться больше 1 МБ")
            }
            origGsub.invoke(a)
        }
        val origByte = strLib.get("byte")
        strLib.fn("byte") { a ->
            val len = a.arg(1).tojstring().length
            val i = a.optint(2, 1)
            val j = a.optint(3, i)
            val from = if (i < 0) len + i + 1 else i
            val to = if (j < 0) len + j + 1 else j
            if (to - from + 1 > LX_MAX_UNPACK) throw LuaError("string.byte: не больше $LX_MAX_UNPACK значений за раз")
            origByte.invoke(a)
        }
        val tblLib = g.get("table").checktable()
        val origUnpack = tblLib.get("unpack")
        if (!origUnpack.isnil()) {
            tblLib.fn("unpack") { a ->
                val i = a.optint(2, 1)
                val j = if (a.isnil(3)) a.arg(1).checktable().length() else a.checkint(3)
                if (j.toLong() - i.toLong() + 1L > LX_MAX_UNPACK) throw LuaError("table.unpack: не больше $LX_MAX_UNPACK значений за раз")
                origUnpack.invoke(a)
            }
        }
        val origRep = strLib.get("rep")
        strLib.fn("rep") { a ->
            val times = a.optdouble(2, 0.0)
            val sepLen = if (a.isnil(3)) 0 else a.arg(3).tojstring().length
            if (times > 0 && (a.arg(1).tojstring().length + sepLen) * times > LX_MAX_STRING) throw LuaError("string.rep: результат больше 1 МБ")
            origRep.invoke(a)
        }
        val origFormat = strLib.get("format")
        strLib.fn("format") { a ->
            if (LX_FMT_BAD.containsMatchIn(a.arg(1).tojstring())) throw LuaError("string.format: слишком большая ширина или точность")
            origFormat.invoke(a)
        }

        g.set("string", lxCloneTable(g.get("string").checktable()))
        val origGetmt = g.get("getmetatable")
        g.fn("getmetatable") { a -> if (a.arg(1).type() == LuaValue.TSTRING) LuaValue.NIL else origGetmt.invoke(a) }
        for (n in arrayOf("dofile", "loadfile", "load", "loadstring", "collectgarbage")) g.set(n, LuaValue.NIL)

        g.set("__hook", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                if (System.nanoTime() > deadline) throw LuaTimeout("скрипт работает дольше ${LX_BUDGET_MS / 1000} с")
                if (lxUsedHeap() - memStart > LX_MAX_HEAP_GROWTH) throw LuaTimeout("скрипт занял слишком много памяти")
                return LuaValue.NONE
            }
        })
        try {
            g.load("debug.sethook(__hook, '', $LX_HOOK_STEP)", "=sandbox").call()
        } catch (t: Throwable) {
            AppLog.w(LX_TAG, "[$id] счётчик инструкций не включился: ${t.message}")
        }
        g.load("debug = nil; __hook = nil", "=sandbox").call()

        val logFn = object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val now = System.currentTimeMillis()
                if (now - logWindow > 1000L) { logWindow = now; logPerSec = 0 }
                if (++logPerSec <= 40 && ++logCount <= LX_MAX_LOG_LINES) {
                    val sb = StringBuilder()
                    for (i in 1..args.narg()) {
                        if (i > 1) sb.append('\t')
                        sb.append(args.arg(i).tojstring())
                    }
                    AppLog.i("$LX_TAG/$id", sb.toString().map { if (it == '\n' || it == '\r') ' ' else it }.joinToString("").take(500))
                }
                return LuaValue.NONE
            }
        }
        g.set("print", logFn)
        g.set("log", logFn)

        installBasics()
        installUtil()
        installJson()
        installSettings()
        installHooks()
        if ("storage" in perms) installStorage()
        if ("ui" in perms) installUi()
        if ("player" in perms) installPlayer()
        if ("net" in perms) installHttp()
        if ("wave" in perms) installWave()
        if ("theme" in perms) installTheme()
        installAppAndMisc()
        installTimersAndEvents()
        installRequire()
    }

    private fun installBasics() {
        val pl = LuaTable()
        pl.set("id", lxStr(plugin.manifest.id))
        pl.set("name", lxStr(plugin.manifest.name))
        pl.set("version", lxStr(plugin.manifest.version))
        pl.set("author", lxStr(plugin.manifest.author))
        pl.fn("read_file") { a ->
            val name = a.checkjstring(1)
            if (name.startsWith("/") || name.contains("..") || name.contains("\\")) throw LuaError("plugin.read_file: недопустимое имя")
            val b = PluginSystem.readFile(plugin, name)
            if (b == null) LuaValue.NIL else LuaValue.valueOf(String(b, Charsets.UTF_8))
        }
        g.set("plugin", pl)

        val os = LuaTable()
        os.fn("time") { a ->
            if (a.istable(1)) {
                val t = a.checktable(1)
                val c = Calendar.getInstance()
                c.set(
                    t.get("year").optint(1970), t.get("month").optint(1) - 1, t.get("day").optint(1),
                    t.get("hour").optint(12), t.get("min").optint(0), t.get("sec").optint(0),
                )
                LuaValue.valueOf((c.timeInMillis / 1000L).toDouble())
            } else LuaValue.valueOf((System.currentTimeMillis() / 1000L).toDouble())
        }
        os.fn("clock") { LuaValue.valueOf(System.nanoTime() / 1e9) }
        os.fn("date") { a ->
            val fmt = a.optjstring(1, "%c") ?: "%c"
            val ms = if (a.isnil(2)) System.currentTimeMillis() else (a.checkdouble(2) * 1000.0).toLong()
            lxDate(fmt, ms)
        }
        g.set("os", os)
    }

    private fun installUtil() {
        val u = LuaTable()
        fun str(a: Varargs, i: Int, what: String): String {
            val v = a.checkjstring(i)
            if (v.length > LX_MAX_UTIL) throw LuaError("util.$what: строка больше ${LX_MAX_UTIL / 1024} КБ")
            return v
        }
        fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
        u.fn("url_encode") { a -> lxStr(URLEncoder.encode(str(a, 1, "url_encode"), "UTF-8").replace("+", "%20")) }
        u.fn("url_decode") { a ->
            try { lxStr(URLDecoder.decode(str(a, 1, "url_decode").replace("+", "%2B"), "UTF-8")) } catch (e: IllegalArgumentException) { LuaValue.NIL }
        }
        u.fn("base64_encode") { a -> lxStr(Base64.getEncoder().encodeToString(str(a, 1, "base64_encode").toByteArray(Charsets.UTF_8))) }
        u.fn("base64_decode") { a ->
            try { lxStr(String(Base64.getDecoder().decode(str(a, 1, "base64_decode").trim()), Charsets.UTF_8)) } catch (e: IllegalArgumentException) { LuaValue.NIL }
        }
        u.fn("sha256") { a -> lxStr(hex(MessageDigest.getInstance("SHA-256").digest(str(a, 1, "sha256").toByteArray(Charsets.UTF_8)))) }
        u.fn("hmac_sha256") { a ->
            val key = str(a, 1, "hmac_sha256").toByteArray(Charsets.UTF_8)
            if (key.isEmpty()) throw LuaError("util.hmac_sha256: пустой ключ")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            lxStr(hex(mac.doFinal(str(a, 2, "hmac_sha256").toByteArray(Charsets.UTF_8))))
        }
        u.fn("uuid") { lxStr(UUID.randomUUID().toString()) }
        u.fn("trim") { a -> lxStr(str(a, 1, "trim").trim()) }
        u.fn("starts_with") { a -> LuaValue.valueOf(str(a, 1, "starts_with").startsWith(str(a, 2, "starts_with"))) }
        u.fn("ends_with") { a -> LuaValue.valueOf(str(a, 1, "ends_with").endsWith(str(a, 2, "ends_with"))) }
        u.fn("split") { a ->
            val sep = str(a, 2, "split")
            if (sep.isEmpty()) throw LuaError("util.split: пустой разделитель")
            val max = a.optint(3, LX_MAX_SPLIT).coerceIn(1, LX_MAX_SPLIT)
            val t = LuaTable()
            val parts = str(a, 1, "split").split(sep, limit = max)
            parts.forEachIndexed { i, x -> t.set(i + 1, lxStr(x)) }
            t
        }
        u.fn("clamp") { a ->
            val lo = a.checkdouble(2)
            val hi = a.checkdouble(3)
            LuaValue.valueOf(a.checkdouble(1).coerceIn(minOf(lo, hi), maxOf(lo, hi)))
        }
        u.fn("format_duration") { a ->
            val total = (a.checkdouble(1).coerceIn(0.0, 3.6e9) / 1000.0).toLong()
            val h = total / 3600
            val m = (total % 3600) / 60
            val sc = total % 60
            lxStr(if (h > 0) "$h:${lxPad2(m.toInt())}:${lxPad2(sc.toInt())}" else "$m:${lxPad2(sc.toInt())}")
        }
        g.set("util", u)
    }

    private fun installJson() {
        val js = LuaTable()
        js.fn("encode") { a -> LuaValue.valueOf(lxToJson(a.arg(1)).toString()) }
        js.fn("decode") { a ->
            try {
                lxFromJson(JSONTokener(a.checkjstring(1)).nextValue())
            } catch (e: LuaError) {
                throw e
            } catch (e: Exception) {
                LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf("json: неверный формат"))
            }
        }
        g.set("json", js)
    }

    private fun installSettings() {
        val st = LuaTable()
        st.fn("get") { a ->
            val k = a.checkjstring(1)
            val def = plugin.manifest.settings.firstOrNull { it.key == k && it.hasValue }
                ?: throw LuaError("settings.get: в манифесте нет настройки «$k»")
            lxToLua(plugin.settingsCopy[k] ?: def.default)
        }
        st.fn("all") {
            val t = LuaTable()
            for (d in plugin.manifest.settings) if (d.hasValue) t.set(d.key, lxToLua(plugin.settingsCopy[d.key] ?: d.default))
            t
        }
        st.fn("set") { a ->
            val k = a.checkjstring(1)
            if (k.startsWith(PluginHooks.KEY_PREFIX)) throw LuaError("settings.set: хуки включает и выключает только пользователь")
            val def = plugin.manifest.settings.firstOrNull { it.key == k && it.hasValue }
                ?: throw LuaError("settings.set: в манифесте нет настройки «$k»")
            val v = a.arg(2)
            val raw: Any? = when (v.type()) {
                LuaValue.TBOOLEAN -> v.toboolean()
                LuaValue.TNUMBER -> v.todouble()
                LuaValue.TSTRING -> v.tojstring()
                else -> null
            }
            val nv = if (raw != null) PluginSystem.normalizeSetting(def, raw) else null
            if (nv != null) {
                plugin.settingsCopy = HashMap(plugin.settingsCopy).apply { put(k, nv) }
                post { PluginSystem.setSetting(plugin, k, nv, notify = false) }
            }
            LuaValue.valueOf(nv != null)
        }
        g.set("settings", st)
    }

    private fun installStorage() {
        fun key(k: String): String {
            if (!LX_KEY_RE.matches(k)) throw LuaError("storage: ключ — 1–48 символов: латиница, цифры, . _ : -")
            return k
        }
        val st = LuaTable()
        st.fn("get") { a ->
            val raw = prefs.getString(key(a.checkjstring(1)), null)
            if (raw == null) a.arg(2)
            else try { lxFromJson(JSONArray(raw).opt(0)) } catch (e: Exception) { a.arg(2) }
        }
        st.fn("set") { a ->
            val k = key(a.checkjstring(1))
            val v = a.arg(2)
            if (v.isnil()) {
                prefs.edit().remove(k).apply()
            } else {
                val s = JSONArray().put(lxToJson(v)).toString()
                if (s.length > LX_MAX_STORAGE_VALUE) throw LuaError("storage: значение больше ${LX_MAX_STORAGE_VALUE / 1024} КБ")
                if (!prefs.contains(k) && prefs.all.size >= LX_MAX_STORAGE_KEYS) throw LuaError("storage: не больше $LX_MAX_STORAGE_KEYS ключей")
                prefs.edit().putString(k, s).apply()
            }
            LuaValue.NONE
        }
        st.fn("delete") { a -> prefs.edit().remove(key(a.checkjstring(1))).apply(); LuaValue.NONE }
        st.fn("clear") { prefs.edit().clear().apply(); LuaValue.NONE }
        st.fn("keys") {
            val t = LuaTable()
            prefs.all.keys.sorted().forEachIndexed { i, k -> t.set(i + 1, LuaValue.valueOf(k)) }
            t
        }
        g.set("storage", st)
    }

    private fun installUi() {
        val ui = LuaTable()
        ui.fn("toast") { a ->
            val text = a.arg(1).tojstring().map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().take(200)
            val now = System.currentTimeMillis()
            if (text.isNotEmpty() && now - lastToast >= 1500L) {
                lastToast = now
                val b = lxBridge
                if (b != null) post { b.toast(text) }
            }
            LuaValue.NONE
        }
        fun fnOrNil(v: LuaValue, what: String): LuaValue? {
            if (v.isnil()) return null
            if (!v.isfunction()) throw LuaError("$what должна быть функцией")
            return v
        }
        fun ret(k: Int?): Varargs = if (k == null) LuaValue.NIL else LuaValue.valueOf(k)
        ui.fn("alert") { a ->
            val cb = fnOrNil(a.arg(3), "ui.alert: on_close")
            ret(openDialog(lxClean(a.arg(1).tojstring(), 80), lxClean(a.arg(2).tojstring(), 600),
                listOf(Triple("Понятно", "primary", cb)), cb, null, true))
        }
        ui.fn("confirm") { a ->
            val yes = fnOrNil(a.arg(3), "ui.confirm: on_yes")
            val no = fnOrNil(a.arg(4), "ui.confirm: on_no")
            ret(openDialog(lxClean(a.arg(1).tojstring(), 80), lxClean(a.arg(2).tojstring(), 600),
                listOf(Triple("Отмена", "default", no), Triple("ОК", "primary", yes)), no, null, true))
        }
        ui.fn("prompt") { a ->
            val ok = fnOrNil(a.arg(4), "ui.prompt: on_submit")
            val cancel = fnOrNil(a.arg(5), "ui.prompt: on_cancel")
            ret(openDialog(lxClean(a.arg(1).tojstring(), 80), lxClean(a.arg(2).tojstring(), 600),
                listOf(Triple("Отмена", "default", cancel), Triple("ОК", "primary", ok)), cancel,
                Triple(a.arg(3).optjstring("").take(500), "", 500), true))
        }
        ui.fn("dialog") { a ->
            val d = a.checktable(1)
            val bt = d.get("buttons")
            val buttons = ArrayList<Triple<String, String, LuaValue?>>()
            if (!bt.isnil()) {
                if (!bt.istable()) throw LuaError("ui.dialog: buttons — список таблиц")
                val n = bt.length()
                if (n > 3) throw LuaError("ui.dialog: не больше 3 кнопок")
                for (i in 1..n) {
                    val b = bt.get(i)
                    if (!b.istable()) throw LuaError("ui.dialog: кнопка №$i — таблица {title=, style=, on_click=}")
                    val t = lxClean(b.get("title").checkjstring(), 24)
                    if (t.isEmpty()) throw LuaError("ui.dialog: у кнопки №$i пустой title")
                    val style = b.get("style").optjstring("default")
                    if (style != "default" && style != "primary" && style != "danger") throw LuaError("ui.dialog: style — default, primary или danger")
                    buttons += Triple(t, style, fnOrNil(b.get("on_click"), "ui.dialog: on_click кнопки №$i"))
                }
            }
            if (buttons.isEmpty()) buttons += Triple("Понятно", "primary", null)
            val inp = d.get("input")
            val input: Triple<String, String, Int>? = when {
                inp.isnil() || (inp.isboolean() && !inp.toboolean()) -> null
                inp.istable() -> {
                    val ml = inp.get("max_length").optint(100).coerceIn(1, 500)
                    Triple(inp.get("value").optjstring("").take(ml), lxClean(inp.get("hint").optjstring(""), 60), ml)
                }
                else -> Triple("", "", 100)
            }
            ret(openDialog(lxClean(d.get("title").optjstring(""), 80), lxClean(d.get("text").optjstring(""), 600), buttons,
                fnOrNil(d.get("on_dismiss"), "ui.dialog: on_dismiss"), input, d.get("cancelable").optboolean(true)))
        }
        ui.fn("settings_page") { a -> registerPage(a.checktable(1), false, "ui.settings_page"); LuaValue.NONE }
        ui.fn("sheet") { a ->
            val pid = registerPage(a.checktable(1), true, "ui.sheet")
            post { PluginSystem.openSheet(plugin.id, pid) }
            LuaValue.valueOf(pid)
        }
        ui.fn("open_page") { a ->
            val pid = a.checkjstring(1)
            if (pid !in pages) throw LuaError("ui.open_page: нет страницы «$pid»")
            post { PluginSystem.openSheet(plugin.id, pid) }
            scheduleRender(pid)
            LuaValue.NONE
        }
        ui.fn("close_page") { a ->
            val pid = a.optjstring(1, null)
            post { PluginSystem.requestCloseSheet(plugin.id, pid) }
            LuaValue.NONE
        }
        ui.fn("refresh") { a -> scheduleRender(a.optjstring(1, null)); LuaValue.NONE }
        ui.fn("remove_page") { a ->
            val pid = a.checkjstring(1)
            if (pages.remove(pid) != null) {
                pageCallbacks.remove(pid)
                post { plugin.uiEntries.removeAll { it.id == pid }; plugin.uiPages.remove(pid) }
            }
            LuaValue.NONE
        }
        g.set("ui", ui)
    }

    private fun installPlayer() {
        fun p(): Player = lxPlayer ?: throw LuaError("плеер ещё не готов")
        fun <T> onMain(f: () -> T): T? = blocking { luaOnMain(3000L, f) }
        val pl = LuaTable()
        pl.fn("play") {
            onMain { val x = p(); if (x.playbackState == Player.STATE_IDLE) x.prepare(); x.play() }
            LuaValue.NONE
        }
        pl.fn("pause") { onMain { p().pause() }; LuaValue.NONE }
        pl.fn("toggle") {
            onMain {
                val x = p()
                if (x.isPlaying) x.pause() else { if (x.playbackState == Player.STATE_IDLE) x.prepare(); x.play() }
            }
            LuaValue.NONE
        }
        pl.fn("next") { onMain { val x = p(); if (x.hasNextMediaItem()) x.seekToNextMediaItem() }; LuaValue.NONE }
        pl.fn("previous") { onMain { p().seekToPrevious() }; LuaValue.NONE }
        pl.fn("seek") { a ->
            val ms = a.checkdouble(1).toLong().coerceAtLeast(0L)
            onMain { p().seekTo(ms) }
            LuaValue.NONE
        }
        pl.fn("position") { LuaValue.valueOf((onMain { p().currentPosition } ?: 0L).toDouble()) }
        pl.fn("duration") {
            val d = onMain { p().duration } ?: 0L
            LuaValue.valueOf((if (d < 0L) 0L else d).toDouble())
        }
        pl.fn("is_playing") { LuaValue.valueOf(onMain { p().isPlaying } ?: false) }
        pl.fn("volume") { LuaValue.valueOf((onMain { p().volume } ?: 1f).toDouble()) }
        pl.fn("set_volume") { a ->
            val v = a.checkdouble(1).coerceIn(0.0, 1.0).toFloat()
            onMain { p().volume = v }
            LuaValue.NONE
        }
        pl.fn("speed") { LuaValue.valueOf((onMain { p().playbackParameters.speed } ?: 1f).toDouble()) }
        pl.fn("set_speed") { a ->
            val v = a.checkdouble(1).coerceIn(0.5, 2.0).toFloat()
            onMain { p().setPlaybackSpeed(v) }
            LuaValue.NONE
        }
        pl.fn("queue") {
            val items = onMain {
                val x = p()
                val from = x.currentMediaItemIndex.coerceAtLeast(0)
                val to = minOf(x.mediaItemCount, from + LX_MAX_QUEUE_VIEW)
                (from until to).map { i ->
                    val m = x.getMediaItemAt(i)
                    Triple(i + 1, m.mediaId, (m.mediaMetadata.title?.toString() ?: "") to (m.mediaMetadata.artist?.toString() ?: ""))
                }
            } ?: emptyList()
            val t = LuaTable()
            items.forEachIndexed { k, (idx, mid, ta) ->
                val e = LuaTable()
                e.set("index", LuaValue.valueOf(idx))
                e.set("id", lxStr(mid.take(200)))
                e.set("title", lxStr(ta.first.take(300)))
                e.set("artists", lxStr(ta.second.take(300)))
                t.set(k + 1, e)
            }
            t
        }
        pl.fn("queue_size") { LuaValue.valueOf(onMain { p().mediaItemCount } ?: 0) }
        pl.fn("queue_index") {
            val i = onMain { p().currentMediaItemIndex } ?: -1
            if (i < 0) LuaValue.NIL else LuaValue.valueOf(i + 1)
        }
        pl.fn("skip_to") { a ->
            val n = a.checkint(1) - 1
            val ok = onMain { val x = p(); if (n in 0 until x.mediaItemCount) { x.seekTo(n, 0L); true } else false } ?: false
            LuaValue.valueOf(ok)
        }
        pl.fn("seek_by") { a ->
            val d = a.checkdouble(1).coerceIn(-3_600_000.0, 3_600_000.0).toLong()
            onMain {
                val x = p()
                val dur = x.duration.let { if (it < 0L) Long.MAX_VALUE else it }
                x.seekTo((x.currentPosition + d).coerceIn(0L, dur))
            }
            LuaValue.NONE
        }
        pl.fn("current") {
            val t = lxBridge?.currentTrack()
            if (t == null) LuaValue.NIL else lxTrackTable(t)
        }
        g.set("player", pl)
    }

    private fun installHttp() {
        fun allow(): Boolean {
            val now = System.currentTimeMillis()
            while (httpTimes.isNotEmpty() && now - httpTimes.first() > 60_000L) httpTimes.removeFirst()
            if (httpTimes.size >= LX_MAX_HTTP_PER_MIN) return false
            httpTimes.addLast(now)
            return true
        }

        fun request(method: String, a: Varargs): Varargs {
            if (!allow()) throw LuaError("http: не больше $LX_MAX_HTTP_PER_MIN запросов в минуту")
            val url = lxCheckUrl(a.checkjstring(1))
            var contentType = "text/plain; charset=utf-8"
            var bodyText = ""
            var hdrIdx = 2
            val hasBody = method == "POST" || method == "PUT" || method == "PATCH"
            if (hasBody) {
                hdrIdx = 3
                val b = a.arg(2)
                if (b.istable()) {
                    bodyText = lxToJson(b).toString()
                    contentType = "application/json; charset=utf-8"
                } else if (!b.isnil()) {
                    bodyText = b.tojstring()
                }
            }
            if (bodyText.length > LX_MAX_BODY_OUT) throw LuaError("http: тело запроса больше ${LX_MAX_BODY_OUT / 1024} КБ")
            val builder = Request.Builder().url(url).header("User-Agent", "ExMusic-Lua/${BuildConfig.VERSION_NAME}")
            val h = a.arg(hdrIdx)
            if (h.istable()) {
                val ht = h.checktable()
                var n = 0
                var e = ht.next(LuaValue.NIL)
                while (!e.arg1().isnil()) {
                    if (++n > 16) throw LuaError("http: не больше 16 заголовков")
                    val name = e.arg1().tojstring()
                    val value = e.arg(2).tojstring()
                    if (name.equals("content-type", true)) contentType = value
                    else if (name.lowercase() in LX_FORBIDDEN_HEADERS) throw LuaError("http: заголовок «$name» менять нельзя")
                    else try { builder.header(name, value) } catch (ex: IllegalArgumentException) { throw LuaError("http: неверный заголовок «$name»") }
                    e = ht.next(e.arg1())
                }
            }
            val mt = try { contentType.toMediaTypeOrNull() } catch (ex: Exception) { null }
            val req = when (method) {
                "POST" -> builder.post(bodyText.toRequestBody(mt))
                "PUT" -> builder.put(bodyText.toRequestBody(mt))
                "PATCH" -> builder.patch(bodyText.toRequestBody(mt))
                "DELETE" -> builder.delete()
                "HEAD" -> builder.head()
                else -> builder.get()
            }.build()
            var res: Varargs = LuaValue.NONE
            blocking {
                res = try {
                    luaHttp.newCall(req).execute().use { r ->
                        val t = LuaTable()
                        t.set("status", LuaValue.valueOf(r.code))
                        t.set("ok", LuaValue.valueOf(r.isSuccessful))
                        t.set("body", LuaValue.valueOf(r.peekBody(LX_MAX_HTTP_BODY).string()))
                        val hs = LuaTable()
                        var cnt = 0
                        for (name in r.headers.names()) {
                            if (++cnt > 32) break
                            hs.set(name.lowercase(), LuaValue.valueOf(r.header(name) ?: ""))
                        }
                        t.set("headers", hs)
                        t
                    }
                } catch (ex: java.io.IOException) {
                    LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf(ex.message ?: "ошибка сети"))
                }
            }
            return res
        }

        val http = LuaTable()
        http.fn("get") { a -> request("GET", a) }
        http.fn("post") { a -> request("POST", a) }
        http.fn("put") { a -> request("PUT", a) }
        http.fn("patch") { a -> request("PATCH", a) }
        http.fn("delete") { a -> request("DELETE", a) }
        http.fn("head") { a -> request("HEAD", a) }
        g.set("http", http)
    }

    private fun installWave() {
        fun push() {
            val copy = myStations.toList()
            post {
                plugin.luaStations.clear()
                plugin.luaStations.addAll(copy)
            }
        }
        val wave = LuaTable()
        wave.fn("start") { a ->
            val st = a.checkjstring(1).trim()
            if (!LX_STATION_RE.matches(st)) throw LuaError("wave.start: неверный идентификатор станции «$st» (пример: genre:rock)")
            val label = a.optjstring(2, "Волна")?.take(40) ?: "Волна"
            val now = System.currentTimeMillis()
            if (now - lastWave < 10_000L) throw LuaError("wave.start: не чаще раза в 10 секунд")
            lastWave = now
            val b = lxBridge
            if (b != null) post { b.startWave(st, label) }
            LuaValue.NONE
        }
        wave.fn("add_station") { a ->
            val title = a.checkjstring(1).trim()
            val st = a.checkjstring(2).trim()
            val sub = a.optjstring(3, "")?.trim()?.take(60) ?: ""
            if (title.isEmpty() || title.length > 40) throw LuaError("wave.add_station: название от 1 до 40 символов")
            if (!LX_STATION_RE.matches(st)) throw LuaError("wave.add_station: неверный идентификатор станции «$st» (пример: genre:rock)")
            val i = myStations.indexOfFirst { it.station == st }
            val item = PluginStation(title, st, sub)
            if (i >= 0) myStations[i] = item
            else {
                if (myStations.size >= LX_MAX_STATIONS) throw LuaError("wave.add_station: не больше $LX_MAX_STATIONS станций")
                myStations.add(item)
            }
            push()
            LuaValue.NONE
        }
        wave.fn("clear_stations") { myStations.clear(); push(); LuaValue.NONE }
        g.set("wave", wave)
    }

    private fun installTheme() {
        val th = LuaTable()
        th.fn("set_accent") { a ->
            val v = a.arg(1)
            val color: Int? = if (v.isnil()) null else try {
                Color.parseColor(v.checkjstring().trim()) or 0xFF000000.toInt()
            } catch (e: IllegalArgumentException) {
                throw LuaError("theme.set_accent: цвет нужен в виде #RRGGBB")
            }
            post { plugin.luaAccent = color }
            LuaValue.NONE
        }
        th.fn("set_amoled") { a ->
            val v = a.arg(1)
            val on: Boolean? = if (v.isnil()) null else v.toboolean()
            post { plugin.luaAmoled = on }
            LuaValue.NONE
        }
        g.set("theme", th)
    }

    private fun installAppAndMisc() {
        val ap = LuaTable()
        ap.set("version", lxStr(BuildConfig.VERSION_NAME))
        ap.set("api", LuaValue.valueOf(PluginSystem.API_VERSION))
        if ("search" in perms) {
            ap.fn("search") { a ->
                val q = a.checkjstring(1).trim().take(200)
                val now = System.currentTimeMillis()
                if (now - lastSearch < 2000L) throw LuaError("app.search: не чаще раза в 2 секунды")
                lastSearch = now
                val b = lxBridge
                if (q.isNotEmpty() && b != null) post { b.search(q) }
                LuaValue.NONE
            }
        }
        if (plugin.unsafeOn) {
            ap.fn("context") { lxCtx?.let { CoerceJavaToLua.coerce(it) } ?: LuaValue.NIL }
            ap.fn("viewmodel") { a ->
                val vm = luaOnMain(3000L) { lxBridge?.viewModel() }
                if (vm == null) LuaValue.NIL else CoerceJavaToLua.coerce(vm)
            }
        }
        g.set("app", ap)
    }

    private fun installTimersAndEvents() {
        fun add(ms: Long, repeat: Boolean, f: LuaValue): Int {
            if (timers.size >= LX_MAX_TIMERS) throw LuaError("timer: не больше $LX_MAX_TIMERS таймеров")
            val tid = ++timerSeq
            val task = Runnable {
                if (!stopped) {
                    if (!repeat) timers.remove(tid)
                    guard("таймер") { f.call() }
                }
            }
            timers[tid] = if (repeat) luaExec.scheduleWithFixedDelay(task, ms, ms, TimeUnit.MILLISECONDS)
            else luaExec.schedule(task, ms, TimeUnit.MILLISECONDS)
            return tid
        }

        val tm = LuaTable()
        tm.fn("after") { a ->
            val ms = maxOf(LX_MIN_TIMER_MS, a.checkdouble(1).toLong())
            LuaValue.valueOf(add(ms, false, a.checkfunction(2)))
        }
        tm.fn("every") { a ->
            val ms = maxOf(LX_MIN_TIMER_MS, a.checkdouble(1).toLong())
            LuaValue.valueOf(add(ms, true, a.checkfunction(2)))
        }
        tm.fn("cancel") { a ->
            timers.remove(a.checkint(1))?.cancel(false)
            LuaValue.NONE
        }
        tm.fn("now") { LuaValue.valueOf(System.currentTimeMillis().toDouble()) }
        g.set("timer", tm)

        tm.fn("cancel_all") {
            for (f in timers.values.toList()) f.cancel(false)
            timers.clear()
            LuaValue.NONE
        }

        val ev = LuaTable()
        fun register(what: String, a: Varargs, once: Boolean) {
            val name = a.checkjstring(1)
            val f = a.checkfunction(2)
            if (name !in LX_EVENTS) throw LuaError("$what: неизвестное событие «$name» (есть: ${LX_EVENTS.joinToString()})")
            if (name in LX_PLAYER_EVENTS) need("player")
            val l = handlers.getOrPut(name) { ArrayList() }
            if (l.size >= LX_MAX_HANDLERS) throw LuaError("$what: не больше $LX_MAX_HANDLERS обработчиков на событие")
            if (!once) { l.add(f); return }
            l.add(LxOnce(f) { self -> handlers[name]?.remove(self) })
        }
        ev.fn("on") { a -> register("events.on", a, false); LuaValue.NONE }
        ev.fn("once") { a -> register("events.once", a, true); LuaValue.NONE }
        ev.fn("off") { a ->
            val name = a.checkjstring(1)
            val f = a.checkfunction(2)
            val l = handlers[name]
            val idx = l?.indexOfFirst { it === f || (it is LxOnce && it.orig === f) } ?: -1
            if (idx >= 0) l!!.removeAt(idx)
            LuaValue.valueOf(idx >= 0)
        }
        g.set("events", ev)
    }

    private fun installRequire() {
        g.fn("require") { a ->
            val name = a.checkjstring(1)
            if (!LX_MODULE_RE.matches(name) || name.contains("..") || name.startsWith("/")) throw LuaError("require: недопустимое имя «$name»")
            val cached = modules[name]
            if (cached != null) {
                cached
            } else {
                if (modules.size >= LX_MAX_MODULES) throw LuaError("require: не больше $LX_MAX_MODULES модулей")
                val path = if (name.contains('/')) name else name.replace('.', '/')
                val bytes = PluginSystem.readFile(plugin, "$path.lua", LX_MAX_SCRIPT)
                    ?: PluginSystem.readFile(plugin, "$path/init.lua", LX_MAX_SCRIPT)
                    ?: throw LuaError("require: модуль «$name» не найден в плагине")
                modules[name] = LuaValue.TRUE
                val chunk = g.load(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF"), "@$path.lua")
                val r = chunk.call(LuaValue.valueOf(name))
                val res: LuaValue = if (r.isnil()) LuaValue.TRUE else r
                modules[name] = res
                res
            }
        }
    }
}
