package app.ymusic.m3e.data

import android.net.Uri
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Ответ oauth.yandex.ru/device/code: пользователь вводит [userCode] на [verificationUrl] (ya.ru/device). */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val intervalSec: Int,
    val expiresInSec: Int,
)

/**
 * DNS для входа. Обычное разрешение имён по умолчанию; если система не смогла (например, пока приложение в фоне, а вы подтверждаете код
 * в браузере, Android режет ему сеть, или DNS-фильтр отдаёт пустой ответ) — запасной путь через DNS-over-HTTPS (Cloudflare, затем Google).
 * Запасной путь работает только для адресов Яндекса и только после сбоя системного DNS.
 */
private object AuthDns : Dns {
    private val allowed = setOf("oauth.yandex.ru", "ya.ru", "passport.yandex.ru")

    private val boot: OkHttpClient by lazy {
        OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            if (hostname !in allowed) throw e
            for (url in listOf(
                "https://1.1.1.1/dns-query?name=$hostname&type=A",
                "https://8.8.8.8/resolve?name=$hostname&type=A",
            )) {
                val found = runCatching { doh(url) }.getOrNull()
                if (!found.isNullOrEmpty()) return found
            }
            throw e
        }
    }

    private fun doh(url: String): List<InetAddress> {
        val req = Request.Builder().url(url).header("accept", "application/dns-json").build()
        boot.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            val answers = JSONObject(r.body?.string().orEmpty()).optJSONArray("Answer") ?: return emptyList()
            val out = ArrayList<InetAddress>()
            for (i in 0 until answers.length()) {
                val a = answers.optJSONObject(i) ?: continue
                if (a.optInt("type") != 1) continue
                val ip = a.optString("data")
                // Только IPv4-литералы: getByName на цифрах имён не разрешает.
                if (ip.isNotEmpty() && ip.all { it.isDigit() || it == '.' }) out += InetAddress.getByName(ip)
            }
            return out
        }
    }
}

object YandexAuth {
    const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
    const val CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300"

    private val http = OkHttpClient.Builder()
        .dns(AuthDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Понятный текст вместо «Unable to resolve host…». */
    fun friendlyError(e: Throwable): String = when (e) {
        is UnknownHostException -> "Нет связи с oauth.yandex.ru. Проверьте интернет, VPN и «Частный DNS» и нажмите «Повторить»"
        is IOException -> "Сбой сети при входе. Проверьте интернет и нажмите «Повторить»"
        else -> e.message ?: "Не удалось войти"
    }

    /** Шаг 1 device-flow: получаем код, который пользователь вводит на ya.ru/device. */
    fun requestDeviceCode(): DeviceCode {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("device_id", UUID.randomUUID().toString())
            .add("device_name", "EshaMusic")
            .build()
        val req = Request.Builder().url("https://oauth.yandex.ru/device/code").post(body).build()
        // Сеть в момент открытия экрана бывает ещё не готова (смена Wi-Fi/мобильной сети, пробуждение): пробуем до трёх раз.
        var last: IOException? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) Thread.sleep(1200L * attempt)
            try {
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) error("Не удалось получить код входа (HTTP ${resp.code})")
                    val j = JSONObject(text)
                    return DeviceCode(
                        deviceCode = j.getString("device_code"),
                        userCode = j.getString("user_code"),
                        verificationUrl = j.optString("verification_url").ifBlank { "https://ya.ru/device" },
                        intervalSec = j.optInt("interval", 5).coerceAtLeast(1),
                        expiresInSec = j.optInt("expires_in", 300),
                    )
                }
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("Не удалось получить код входа")
    }

    /** Шаг 2 device-flow: опрашиваем oauth.yandex.ru/token, пока пользователь не подтвердит вход. */
    suspend fun pollToken(dc: DeviceCode): String {
        var interval = dc.intervalSec
        var netFailures = 0
        val deadline = System.currentTimeMillis() + dc.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val body = FormBody.Builder()
                .add("grant_type", "device_code")
                .add("code", dc.deviceCode)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build()
            val req = Request.Builder().url("https://oauth.yandex.ru/token").post(body).build()
            // Пока вы подтверждаете код в браузере, приложение в фоне, и Android может на время закрыть ему сеть
            // («Unable to resolve host»). Раньше первый такой сбой обрывал вход и стирал код; теперь просто ждём следующей попытки.
            val answer = try {
                http.newCall(req).execute().use { it.isSuccessful to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                netFailures++
                if (netFailures >= 24) throw e
                continue
            }
            netFailures = 0
            val (ok, text) = answer
            val j = runCatching { JSONObject(text) }.getOrNull()
            if (ok) {
                val token = j?.optString("access_token").orEmpty()
                if (token.isNotBlank()) return token
                error("Яндекс не вернул токен")
            }
            when (val err = j?.optString("error").orEmpty()) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> error("Код истёк, попробуйте ещё раз")
                else -> error("Ошибка входа: ${err.ifBlank { "неизвестная" }}")
            }
        }
        error("Код истёк, попробуйте ещё раз")
    }

    fun authorizeUrl(): Uri =
        Uri.parse("https://oauth.yandex.ru/authorize").buildUpon()
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("client_id", CLIENT_ID)
            .build()

    /** Токен принимаем только со страниц Яндекса по https: иначе любая страница в WebView могла бы подсунуть чужой токен. */
    fun isTrustedAuthUrl(url: String?): Boolean {
        val u = runCatching { Uri.parse(url ?: return false) }.getOrNull() ?: return false
        val h = u.host?.lowercase() ?: return false
        return u.scheme == "https" && (h == "ya.ru" || h == "yandex.ru" || h == "yandex.com" || h.endsWith(".yandex.ru") || h.endsWith(".yandex.com"))
    }

    /** Достаёт access_token из скопированной ссылки редиректа или напрямую из строки. */
    fun parseToken(input: String?): String? {
        val text = input?.trim() ?: return null
        if (text.startsWith("y0_") && !text.contains(" ")) return text

        val fragment = Uri.parse(text).fragment ?: return null
        return fragment.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == "access_token" }
            ?.get(1)
    }
}