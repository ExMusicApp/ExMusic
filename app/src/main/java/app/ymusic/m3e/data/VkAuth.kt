package app.ymusic.m3e.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Клиент VK, под которым выдан токен. Токен работает только с «родным» User-Agent этого клиента,
 * поэтому в аккаунте сохраняется его id ([id]).
 *
 * Это те же клиенты, что используют сторонние плееры VK (vkaudiotoken и др.): у официального API для музыки открытого доступа нет.
 */
enum class VkClient(val id: String, val clientId: String, val secret: String, val userAgent: String) {
    OFFICIAL(
        "official", "2274003", "hHbZxrka2uZ6jB1inYsH",
        "VKAndroidApp/5.52-4543 (Android 5.1.1; SDK 22; x86_64; unknown Android SDK built for x86_64; en; 320x240)",
    ),

    /**
     * Приложения, через которые вход в браузере у VK ещё открыт (их же использует vkserv.ru/api/token): вход идёт по обычной
     * странице VK (implicit flow), секрет не нужен. Токен таких приложений не привязан к User-Agent, поэтому берём «родной».
     */
    MP3MOD(
        "mp3mod", "6463690", "",
        "VKAndroidApp/5.52-4543 (Android 5.1.1; SDK 22; x86_64; unknown Android SDK built for x86_64; en; 320x240)",
    ),
    VKADMIN(
        "vkadmin", "6121396", "",
        "VKAndroidApp/5.52-4543 (Android 5.1.1; SDK 22; x86_64; unknown Android SDK built for x86_64; en; 320x240)",
    );

    companion object {
        fun fromId(id: String?): VkClient = entries.firstOrNull { it.id == id } ?: OFFICIAL
    }
}

/** Ошибка VK API: [code] — error_code из ответа (5 — токен недействителен, 14 — капча, 6/9 — слишком часто). */
class VkApiException(val code: Int, message: String, val captchaSid: String? = null, val captchaImg: String? = null) :
    RuntimeException(message)

/** Профиль, найденный по токену. */
class VkTokenInfo(val token: String, val userId: Long, val client: VkClient)

/**
 * Вход в VK: только через страницу авторизации VK в WebView.
 *
 * Приложение открывает окно входа VK (OAuth implicit flow, «официальный» клиент VK для Android; Kate Mobile VK закрыл), человек входит, а после входа VK перенаправляет на blank.html с `#access_token=…` в адресе.
 * Адрес перехватывается ([parseToken]), токен проверяется ([checkToken]) и сохраняется.
 * Пароль в приложение не попадает.
 */
object VkAuth {
    const val API_VERSION = "5.131"

    /** Права токена: все, включая audio (8) и offline (65536), как у сервисов выдачи токенов. */
    private const val SCOPE = "1073737727"

    /** Страница входа VK: сразу открывает авторизацию «официального» клиента VK; после входа редирект на blank.html с access_token в адресе. */
    /** Вариант входа: приложение VK и домен (после переезда VK токены приходят на https://api.vk.ru/blank.html). */
    class LoginVariant(val client: VkClient, val ru: Boolean)

    fun loginUrl(v: LoginVariant): String {
        val host = if (v.ru) "oauth.vk.ru" else "oauth.vk.com"
        val redirect = if (v.ru) "https%3A%2F%2Fapi.vk.ru%2Fblank.html" else "https%3A%2F%2Foauth.vk.com%2Fblank.html"
        return "https://$host/authorize?client_id=${v.client.clientId}&scope=$SCOPE" +
            "&redirect_uri=$redirect&display=mobile&response_type=token&revoke=1&v=$API_VERSION"
    }

    /** Единственный способ входа: «VK mp3 mod» на домене vk.ru, токен приходит на api.vk.ru/blank.html (как на vkserv.ru). */
    val LOGIN_VARIANTS: List<LoginVariant> = listOf(LoginVariant(VkClient.MP3MOD, true))

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun friendlyError(e: Throwable): String = when (e) {
        is UnknownHostException -> "Нет связи с vk.com. Проверьте интернет и VPN"
        is IOException -> "Сбой сети при входе в VK. Проверьте интернет и повторите"
        else -> e.message ?: "Не удалось войти в VK"
    }

    /** Токен из адреса редиректа (`…#access_token=…&user_id=…`) или просто из текста; null — токена в тексте нет. */
    fun parseToken(input: String?): String? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null
        Regex("""access_token=([A-Za-z0-9._\-]+)""").find(text)?.let { return it.groupValues[1] }
        // Просто токен, вставленный без адреса.
        if (Regex("""[A-Za-z0-9._\-]{50,}""").matches(text)) return text
        return null
    }

    /**
     * Проверяет токен и подбирает клиент: токен работает с музыкой только под тем клиентом (User-Agent), которым выдан,
     * а под чужим audio.* отвечает «Unknown method passed» (при этом «Микс» может работать). Поэтому по очереди пробуем
     * все клиенты и берём тот, у которого открывается поиск по музыке. Если ни у одного — токен принимается только
     * при рабочем users.get (тогда доступен лишь «Микс»), иначе null.
     */
    fun checkToken(token: String): VkTokenInfo? {
        var last: Exception? = null
        var flood = false
        var aliveClient: VkClient? = null
        var aliveUid = 0L
        for (client in VkClient.entries) {
            var attempt = 0
            while (true) {
                try {
                    // Поиск — самый «строгий» метод музыки: если он открыт, остальное тоже.
                    apiCall(token, client, "audio.search", mapOf("q" to "a", "count" to "1"))
                    val uid = runCatching {
                        apiCall(token, client, "users.get", emptyMap()).optJSONArray("response")?.optJSONObject(0)?.optLong("id", 0L) ?: 0L
                    }.getOrDefault(0L)
                    AppLog.i("VK", "Проверка токена: клиент=${client.id}, музыка открыта, uid=$uid")
                    return VkTokenInfo(token, uid, client)
                } catch (e: VkApiException) {
                    if (e.code in setOf(6, 9) && attempt < 3) {
                        attempt++
                        flood = true
                        AppLog.w("VK", "Проверка токена (${client.id}): ${e.message}, повтор $attempt")
                        Thread.sleep(1500L * attempt)
                        continue
                    }
                    if (e.code in setOf(6, 9)) flood = true
                    AppLog.w("VK", "Проверка токена (${client.id}): ошибка ${e.code}: ${e.message}")
                    last = e
                    if (aliveClient == null && e.code == 3) {
                        // Метод закрыт для этого клиента, но сам токен может быть живым (работает «Микс»).
                        runCatching {
                            apiCall(token, client, "users.get", emptyMap()).optJSONArray("response")?.optJSONObject(0)?.optLong("id", 0L) ?: 0L
                        }.getOrNull()?.takeIf { it != 0L }?.let { aliveClient = client; aliveUid = it }
                    }
                    break
                } catch (e: Exception) {
                    AppLog.w("VK", "Проверка токена (${client.id}) не удалась: ${e.message}")
                    last = e
                    break
                }
            }
        }
        aliveClient?.let {
            AppLog.w("VK", "Музыка закрыта у всех клиентов, токен живой: доступен только «Микс» (клиент=${it.id})")
            return VkTokenInfo(token, aliveUid, it)
        }
        if (flood) {
            AppLog.w("VK", "VK ограничил частоту запросов — токен принят без проверки")
            return VkTokenInfo(token, 0L, VkClient.OFFICIAL)
        }
        if (last is IOException) throw last
        return null
    }

    private fun apiCall(token: String, client: VkClient, method: String, extra: Map<String, String>): JSONObject {
        val form = okhttp3.FormBody.Builder()
            .add("access_token", token).add("v", API_VERSION).add("https", "1").add("lang", "ru")
            .apply { extra.forEach { (k, v) -> add(k, v) } }
            .build()
        val req = Request.Builder().url("https://api.vk.com/method/$method")
            .header("User-Agent", client.userAgent).post(form).build()
        val text = http.newCall(req).execute().use { it.body?.string().orEmpty() }
        val j = JSONObject(text)
        j.optJSONObject("error")?.let { throw VkApiException(it.optInt("error_code"), it.optString("error_msg")) }
        return j
    }
}
