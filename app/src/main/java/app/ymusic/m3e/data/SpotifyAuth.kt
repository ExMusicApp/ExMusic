package app.ymusic.m3e.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Профиль Spotify: то, что показывается в списке аккаунтов и в профиле. */
data class SpProfile(
    val id: String,
    val name: String,
    val avatarUrl: String?,
)

/**
 * Вход в Spotify через страницу сайта в WebView: после входа Spotify выставляет куки (главная — sp_dc), они и есть сессия.
 * Куки сохраняются в [TokenStore] и по ним при каждом запуске заново берётся профиль (ник и аватар):
 * сначала токен веб-плеера со страницы open.spotify.com, затем /v1/me.
 */
object SpotifyAuth {
    /** Страница входа; после успешного входа Spotify возвращает на open.spotify.com. */
    const val WEB_LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val COOKIE_URLS = listOf("https://open.spotify.com", "https://accounts.spotify.com", "https://www.spotify.com")

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    /** Ключ аккаунта в [TokenStore]: стабильный (по куки sp_dc), настоящие куки хранятся отдельно. */
    fun accountKey(cookies: String): String {
        val dc = cookieValue(cookies, "sp_dc") ?: cookies
        val h = MessageDigest.getInstance("SHA-256").digest(dc.toByteArray(Charsets.UTF_8))
        return "sp_" + h.joinToString("") { "%02x".format(it) }.take(20)
    }

    fun cookieValue(cookies: String, name: String): String? =
        cookies.split(';').map { it.trim() }.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }

    /** Есть ли в куках сессия (sp_dc). */
    fun hasSession(cookies: String?): Boolean = !cookies.isNullOrBlank() && cookieValue(cookies, "sp_dc") != null

    /** Куки Spotify из WebView одной строкой «имя=значение; …» (для open.spotify.com и accounts.spotify.com). */
    fun webCookies(): String {
        val cm = android.webkit.CookieManager.getInstance()
        val map = LinkedHashMap<String, String>()
        COOKIE_URLS.forEach { u ->
            cm.getCookie(u)?.split(';')?.forEach { part ->
                val p = part.trim()
                val n = p.substringBefore('=', "")
                if (n.isNotBlank()) map[n] = p.substringAfter('=', "")
            }
        }
        return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /** Стирает куки Spotify в WebView: следующий вход начнётся с чистой страницы (и выбор другого аккаунта возможен). */
    fun clearWebCookies() {
        val cm = android.webkit.CookieManager.getInstance()
        COOKIE_URLS.forEach { u ->
            cm.getCookie(u)?.split(';')?.forEach { part ->
                val n = part.trim().substringBefore('=', "")
                if (n.isNotBlank()) {
                    cm.setCookie(u, "$n=; Max-Age=0; Path=/")
                    cm.setCookie(u, "$n=; Max-Age=0; Path=/; Domain=.spotify.com")
                }
            }
        }
        cm.flush()
    }

    private fun get(url: String, cookies: String?, bearer: String? = null, accept: String = "application/json"): String {
        val b = Request.Builder().url(url).header("User-Agent", UA).header("Accept", accept).header("Accept-Language", "en")
        if (!cookies.isNullOrBlank()) b.header("Cookie", cookies)
        if (!bearer.isNullOrBlank()) b.header("Authorization", "Bearer $bearer")
        http.newCall(b.build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${body.take(160)}")
            return body
        }
    }

    /** Токен веб-плеера из страницы open.spotify.com (там лежит JSON сессии с accessToken). Анонимная сессия — значит куки недействительны. */
    private fun tokenFromPage(cookies: String): String? {
        val html = get("https://open.spotify.com/", cookies, accept = "text/html")
        val json = Regex("""<script[^>]*id="session"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?: return null
        val o = runCatching { JSONObject(json.trim()) }.getOrNull() ?: return null
        if (o.optBoolean("isAnonymous", false)) throw IOException("HTTP 401: сессия Spotify недействительна")
        return o.optString("accessToken").takeIf { it.isNotBlank() }
    }

    /** Запасной путь: токен веб-плеера отдельным запросом. */
    private fun tokenFromEndpoint(cookies: String): String? {
        val body = get("https://open.spotify.com/get_access_token?reason=transport&productType=web_player", cookies)
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (o.optBoolean("isAnonymous", false)) throw IOException("HTTP 401: сессия Spotify недействительна")
        return o.optString("accessToken").takeIf { it.isNotBlank() }
    }

    /**
     * Профиль по кукам. [presetToken] — токен, который уже достала страница в WebView (если получилось).
     * Ошибка «HTTP 401…» — сессия недействительна (нужно войти заново); любая другая — временная.
     */
    fun fetchProfile(cookies: String, presetToken: String? = null): SpProfile {
        if (!hasSession(cookies)) throw IOException("HTTP 401: в Spotify не выполнен вход")
        var token = presetToken?.takeIf { it.isNotBlank() }
        var failure: IOException? = null
        if (token == null) token = try { tokenFromPage(cookies) } catch (e: IOException) { if (e.message?.startsWith("HTTP 401") == true) throw e; failure = e; null }
        if (token == null) token = try { tokenFromEndpoint(cookies) } catch (e: IOException) { if (e.message?.startsWith("HTTP 401") == true) throw e; failure = failure ?: e; null }
        if (token == null) throw failure ?: IOException("Spotify не отдал токен сессии")
        val o = JSONObject(get("https://api.spotify.com/v1/me", null, bearer = token))
        val id = o.optString("id")
        if (id.isBlank()) throw IOException("Spotify не вернул профиль")
        val images = o.optJSONArray("images")
        // Берём самую большую картинку (обычно последняя в списке).
        var best: String? = null
        var bestW = -1
        if (images != null) for (i in 0 until images.length()) {
            val im = images.optJSONObject(i) ?: continue
            val u = im.optString("url").takeIf { it.isNotBlank() } ?: continue
            val w = im.optInt("width", 0)
            if (best == null || w >= bestW) { best = u; bestW = w }
        }
        val name = if (o.isNull("display_name")) "" else o.optString("display_name")
        return SpProfile(id = id, name = name.ifBlank { id }, avatarUrl = best)
    }

    fun friendlyError(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            m.startsWith("HTTP 401") || m.startsWith("HTTP 403") -> "Spotify не принял сессию. Войдите ещё раз"
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
                "Нет связи со Spotify. Проверьте интернет"
            m.isNotBlank() -> m
            else -> "Не удалось войти в Spotify"
        }
    }
}
