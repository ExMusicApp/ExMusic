package app.ymusic.m3e.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.URLDecoder

/** Как обходить блокировку SoundCloud. */
enum class ScProxyMode(val key: String, val title: String) {
    OFF("off", "Выключен"),
    BUILTIN("builtin", "Встроенный обход"),
    CUSTOM("custom", "Свой прокси"),
}

enum class ScProxyType(val key: String, val title: String) {
    SOCKS5("socks5", "SOCKS5"),
    HTTP("http", "HTTP"),
}

/** Свой прокси-сервер. */
data class ScUpstream(
    val type: ScProxyType = ScProxyType.SOCKS5,
    val host: String = "",
    val port: Int = 0,
    val user: String = "",
    val pass: String = "",
) {
    val valid: Boolean get() = host.isNotBlank() && port in 1..65535
}

/** Весь набор настроек одним неизменяемым значением: проксирующие потоки читают его целиком, без «рваных» состояний. */
data class ScProxyConfig(
    val mode: ScProxyMode = ScProxyMode.OFF,
    val upstream: ScUpstream = ScUpstream(),
    /** Приём дробления: -1 — подбирать автоматически, 0..3 — зафиксирован (см. [TlsSplit.plan]). */
    val frag: Int = -1,
)

/** Найденная подбором командная строка для ByeByeDPI и время, за которое открылись API и CDN SoundCloud. */
data class ByeStrategy(val cmd: String, val ms: Long)

/** Настройки обхода блокировки SoundCloud (SharedPreferences «sc_proxy», пароль — в зашифрованных). */
object ScProxySettings {
    private var prefs: SharedPreferences? = null
    private var secure: SharedPreferences? = null

    var config by mutableStateOf(ScProxyConfig())
        private set

    /** Стратегии ByeByeDPI, найденные подбором (пусто — подбор ещё не делали или ничего не сработало). */
    var byeStrategies by mutableStateOf<List<ByeStrategy>>(emptyList())
        private set

    /** Номер выбранной стратегии в [byeStrategies]. */
    var byeChosen by mutableStateOf(0)
        private set

    /**
     * Приём, который сработал в прошлый раз (для режима «авто»), отдельно для сайта и API ("site") и для CDN с
     * обложками и потоками ("cdn"): у провайдеров они часто фильтруются по-разному.
     */
    private val autoMap = java.util.concurrent.ConcurrentHashMap<String, Int>()

    internal fun group(host: String): String = host.lowercase().trimEnd('.').let { if (it.endsWith("sndcdn.com") || it.endsWith("soundcloud.cloud")) "cdn" else "site" }

    fun autoStrategy(host: String): Int = autoMap[group(host)] ?: 0

    fun init(context: Context) {
        val app = context.applicationContext
        val p = app.getSharedPreferences("sc_proxy", Context.MODE_PRIVATE)
        prefs = p
        secure = runCatching {
            EncryptedSharedPreferences.create(
                app, "sc_proxy_secure",
                MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
        val mode = ScProxyMode.entries.firstOrNull { it.key == p.getString("mode", "off") } ?: ScProxyMode.OFF
        val type = ScProxyType.entries.firstOrNull { it.key == p.getString("type", "socks5") } ?: ScProxyType.SOCKS5
        config = ScProxyConfig(
            mode = mode,
            upstream = ScUpstream(
                type = type,
                host = p.getString("host", "").orEmpty(),
                port = p.getInt("port", 0),
                user = p.getString("user", "").orEmpty(),
                pass = runCatching { secure?.getString("pass", "") }.getOrNull().orEmpty(),
            ),
            frag = p.getInt("frag", -1).coerceIn(-1, TlsSplit.STRATEGIES - 1),
        )
        byeStrategies = runCatching {
            val arr = org.json.JSONArray(p.getString("bye_strategies", "[]"))
            List(arr.length()) { i -> arr.getJSONObject(i).let { ByeStrategy(it.getString("cmd"), it.optLong("ms")) } }
        }.getOrDefault(emptyList())
        byeChosen = p.getInt("bye_chosen", 0).coerceIn(0, maxOf(0, byeStrategies.size - 1))
        val legacy = p.getInt("auto", 0).coerceIn(0, TlsSplit.STRATEGIES - 1)
        listOf("site", "cdn").forEach { g ->
            autoMap[g] = p.getInt("auto_$g", legacy).coerceIn(0, TlsSplit.STRATEGIES - 1)
        }
    }

    fun setMode(mode: ScProxyMode) {
        config = config.copy(mode = mode)
        prefs?.edit()?.putString("mode", mode.key)?.apply()
        ScProxy.onSettingsChanged()
    }

    fun setUpstream(u: ScUpstream) {
        config = config.copy(upstream = u)
        prefs?.edit()
            ?.putString("type", u.type.key)?.putString("host", u.host)?.putInt("port", u.port)?.putString("user", u.user)
            ?.apply()
        // Если шифрованное хранилище недоступно, пароль живёт только до закрытия приложения.
        runCatching { secure?.edit()?.putString("pass", u.pass)?.apply() }
        ScProxy.onSettingsChanged()
    }

    @JvmName("updateByeStrategies")
    fun setByeStrategies(list: List<ByeStrategy>) {
        byeStrategies = list
        byeChosen = 0
        val arr = org.json.JSONArray()
        list.forEach { arr.put(org.json.JSONObject().put("cmd", it.cmd).put("ms", it.ms)) }
        prefs?.edit()?.putString("bye_strategies", arr.toString())?.putInt("bye_chosen", 0)?.apply()
    }

    fun chooseByeStrategy(i: Int) {
        byeChosen = i.coerceIn(0, maxOf(0, byeStrategies.size - 1))
        prefs?.edit()?.putInt("bye_chosen", byeChosen)?.apply()
    }

    fun setFrag(frag: Int) {
        config = config.copy(frag = frag.coerceIn(-1, TlsSplit.STRATEGIES - 1))
        prefs?.edit()?.putInt("frag", config.frag)?.apply()
        ScProxy.onSettingsChanged()
    }

    /** Приём [s] сработал для [host]: в следующий раз начинаем с него. */
    internal fun rememberStrategy(host: String, s: Int) {
        val g = group(host)
        if (autoMap.put(g, s) == s) return
        prefs?.edit()?.putInt("auto_$g", s)?.apply()
    }

    /**
     * Ни один из опробованных приёмов не сработал: следующий запрос начнёт с [to], а не будет снова упираться в те же.
     * Применяется, только если за это время другой запрос не успел запомнить удачный приём (он остаётся [from]).
     */
    internal fun advanceStrategy(host: String, from: Int, to: Int) {
        val g = group(host)
        if (autoMap.replace(g, from, to)) prefs?.edit()?.putInt("auto_$g", to)?.apply()
    }

    /**
     * Разбирает ссылку или строку с адресом прокси: socks5://user:pass@host:port, http://host:port,
     * tg://socks?server=…&port=…&user=…&pass=…, https://t.me/socks?…, host:port, host:port:user:pass, user:pass@host:port.
     * [defaultType] — какой тип считать, если в строке он не указан. null — не удалось разобрать.
     */
    fun parseLink(raw: String, defaultType: ScProxyType = ScProxyType.SOCKS5): ScUpstream? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        fun dec(x: String) = runCatching { URLDecoder.decode(x.replace("+", "%2B"), "UTF-8") }.getOrDefault(x)

        // Ссылки Telegram: tg://socks?… и t.me/socks?…
        val tg = Regex("""^(?:tg://|https?://(?:www\.)?t\.me/)socks\?(.+)$""", RegexOption.IGNORE_CASE).find(s)
        if (tg != null) {
            val q = tg.groupValues[1].split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).lowercase() to dec(it.substring(i + 1))
            }.toMap()
            val port = q["port"]?.toIntOrNull() ?: return null
            val host = q["server"]?.takeIf { it.isNotBlank() } ?: return null
            return ScUpstream(ScProxyType.SOCKS5, host, port, q["user"].orEmpty(), q["pass"].orEmpty()).takeIf { it.valid }
        }

        var type = defaultType
        val scheme = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*)://""").find(s)
        if (scheme != null) {
            type = when (scheme.groupValues[1].lowercase()) {
                "socks5", "socks5h", "socks" -> ScProxyType.SOCKS5
                "http" -> ScProxyType.HTTP
                else -> return null
            }
            s = s.substring(scheme.value.length)
        }
        s = s.trimEnd('/')
        if (s.contains('/')) return null

        var user = ""
        var pass = ""
        val hostPort: String
        val at = s.lastIndexOf('@')
        if (at >= 0) {
            val cred = s.substring(0, at)
            val i = cred.indexOf(':')
            user = dec(if (i >= 0) cred.substring(0, i) else cred)
            pass = if (i >= 0) dec(cred.substring(i + 1)) else ""
            hostPort = s.substring(at + 1)
        } else if (!s.startsWith("[") && s.count { it == ':' } == 3) {
            // host:port:user:pass
            val p = s.split(':')
            if (p[1].toIntOrNull() == null) return null
            user = p[2]; pass = p[3]
            hostPort = p[0] + ":" + p[1]
        } else hostPort = s

        val host: String
        val portStr: String
        if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            if (end < 0 || hostPort.getOrNull(end + 1) != ':') return null
            host = hostPort.substring(1, end)
            portStr = hostPort.substring(end + 2)
        } else {
            val i = hostPort.lastIndexOf(':')
            if (i <= 0) return null
            host = hostPort.substring(0, i)
            portStr = hostPort.substring(i + 1)
        }
        val port = portStr.toIntOrNull() ?: return null
        return ScUpstream(type, host.trim(), port, user, pass).takeIf { it.valid }
    }
}
