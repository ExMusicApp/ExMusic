package app.ymusic.m3e.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** Токены SoundCloud: [expiresAt] — момент (мс), когда истекает [access]. */
data class ScTokens(val access: String, val refresh: String, val expiresAt: Long)

/** Профиль SoundCloud: ник, аватар и остальные данные из /me. */
data class ScProfile(
    val id: Long,
    /** Ник (отображаемое имя). */
    val username: String,
    /** Адрес профиля: soundcloud.com/<permalink>. */
    val permalink: String,
    val fullName: String = "",
    val avatarUrl: String? = null,
    val city: String = "",
    val country: String = "",
    val description: String = "",
    val plan: String = "",
    val followers: Int = 0,
    val followings: Int = 0,
    val tracks: Int = 0,
    val playlists: Int = 0,
    val likes: Int = 0,
    val profileUrl: String = "",
) {
    fun toJson(): String = JSONObject()
        .put("id", id).put("username", username).put("permalink", permalink).put("fullName", fullName)
        .put("avatar", avatarUrl ?: "").put("city", city).put("country", country).put("description", description)
        .put("plan", plan).put("followers", followers).put("followings", followings).put("tracks", tracks)
        .put("playlists", playlists).put("likes", likes).put("url", profileUrl)
        .toString()

    companion object {
        fun fromJson(raw: String?): ScProfile? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val o = JSONObject(raw)
                ScProfile(
                    id = o.optLong("id"), username = o.optString("username"), permalink = o.optString("permalink"),
                    fullName = o.optString("fullName"), avatarUrl = o.optString("avatar").ifBlank { null },
                    city = o.optString("city"), country = o.optString("country"), description = o.optString("description"),
                    plan = o.optString("plan"), followers = o.optInt("followers"), followings = o.optInt("followings"),
                    tracks = o.optInt("tracks"), playlists = o.optInt("playlists"), likes = o.optInt("likes"),
                    profileUrl = o.optString("url"),
                )
            }.getOrNull()
        }
    }
}

/**
 * Вход в SoundCloud по OAuth 2.1 (код авторизации + PKCE): открываем страницу входа в браузере,
 * SoundCloud возвращает пользователя по ссылке [REDIRECT_URI], код меняем на токены и читаем профиль (/me).
 *
 * Важно: [REDIRECT_URI] должен быть добавлен в настройках приложения SoundCloud (раздел Redirect URIs).
 */
object SoundCloudAuth {
    const val CLIENT_ID = "I5zoOAjQh3AmFB8Eo0GuF46vipb49ouZ"
    const val CLIENT_SECRET = "Np1yX8mrGAvHDVdpMXhwgPHBJlDWIqRi"
    const val REDIRECT_URI = "ymusicm3e://soundcloud"

    private const val AUTH_URL = "https://secure.soundcloud.com/authorize"
    private const val TOKEN_URL = "https://secure.soundcloud.com/oauth/token"
    private const val ME_URL = "https://api.soundcloud.com/me"

    private const val PREFS = "soundcloud_auth"

    // SoundCloud (защита от ботов) отвечает 403 с HTML-страницей на запросы со «служебным» User-Agent — представляемся обычным мобильным браузером.
    const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val http = ScProxy.applyTo(OkHttpClient.Builder())
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", UA).build()) }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun randomUrlSafe(bytes: Int): String {
        val b = ByteArray(bytes)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** Готовит вход: запоминает code_verifier и state (переживают перезапуск приложения, пока пользователь в браузере) и возвращает адрес страницы входа. */
    fun beginLogin(ctx: Context): String {
        val verifier = randomUrlSafe(48)
        val state = randomUrlSafe(16)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("verifier", verifier).putString("state", state).apply()
        return Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build().toString()
    }

    /** Есть ли начатый вход (ссылку-возврат принимаем только в этом случае). */
    fun hasPending(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("verifier", null) != null

    /** Результат возврата из браузера. */
    sealed interface Redirect {
        class Code(val code: String, val verifier: String) : Redirect
        class Failed(val message: String) : Redirect
    }

    /** Разбирает ссылку возврата. null — это не наш возврат (нет начатого входа или не совпал state). */
    fun parseRedirect(ctx: Context, data: Uri?): Redirect? {
        if (data == null || data.scheme != "ymusicm3e" || data.host != "soundcloud") return null
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val verifier = sp.getString("verifier", null) ?: return null
        val state = sp.getString("state", null)
        if (state.isNullOrBlank() || data.getQueryParameter("state") != state) return null
        sp.edit().clear().apply()
        val err = data.getQueryParameter("error")
        if (err != null) {
            return Redirect.Failed(
                if (err == "access_denied") "Вход в SoundCloud отменён"
                else data.getQueryParameter("error_description") ?: "SoundCloud вернул ошибку: $err",
            )
        }
        val code = data.getQueryParameter("code")
        return if (code.isNullOrBlank()) Redirect.Failed("SoundCloud не вернул код входа") else Redirect.Code(code, verifier)
    }

    /** Сбросить начатый вход (ушли со страницы входа). */
    fun cancelLogin(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun tokenRequest(form: FormBody): ScTokens {
        val req = Request.Builder().url(TOKEN_URL)
            .header("accept", "application/json; charset=utf-8")
            .post(form).build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${errorText(body)}")
            val o = JSONObject(body)
            val access = o.optString("access_token")
            if (access.isBlank()) throw IOException("SoundCloud не вернул токен")
            val sec = o.optLong("expires_in", 3600L)
            return ScTokens(access, o.optString("refresh_token"), System.currentTimeMillis() + sec * 1000L)
        }
    }

    internal fun errorText(body: String): String = if (body.trimStart().startsWith("<")) "сервер вернул веб-страницу вместо ответа" else runCatching {
        val o = JSONObject(body)
        // Ошибки проверки (422) приходят списком: {"errors":[{"error_message":"…"}]}.
        val list = o.optJSONArray("errors")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val e = a.opt(i)
                (e as? JSONObject)?.let { it.optString("error_message").ifBlank { it.optString("message") } } ?: (e as? String)
            }.filter { it.isNotBlank() }.joinToString("; ")
        }.orEmpty()
        list.ifBlank { o.optString("error_description") }.ifBlank { o.optString("error") }.ifBlank { o.optString("message") }
    }.getOrDefault("").ifBlank { body.take(120) }

    fun exchangeCode(code: String, verifier: String): ScTokens = tokenRequest(
        FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("redirect_uri", REDIRECT_URI)
            .add("code_verifier", verifier)
            .add("code", code)
            .build(),
    )

    fun refresh(refreshToken: String): ScTokens {
        val t = tokenRequest(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", refreshToken)
                .build(),
        )
        // SoundCloud может не вернуть новый refresh-токен — тогда остаётся прежний.
        return if (t.refresh.isBlank()) t.copy(refresh = refreshToken) else t
    }

    /** Профиль текущего пользователя. 401 приходит как IOException("HTTP 401…"). */
    fun fetchMe(access: String): ScProfile {
        val req = Request.Builder().url(ME_URL)
            .header("accept", "application/json; charset=utf-8")
            .header("Authorization", "OAuth $access")
            .build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${errorText(body)}")
            val o = JSONObject(body)
            val id = o.optLong("id")
            if (id == 0L) throw IOException("SoundCloud не вернул профиль")
            val permalink = o.optString("permalink")
            val fullName = o.optString("full_name").ifBlank {
                listOf(o.optString("first_name"), o.optString("last_name")).filter { it.isNotBlank() }.joinToString(" ")
            }
            return ScProfile(
                id = id,
                username = o.optString("username").ifBlank { fullName }.ifBlank { permalink },
                permalink = permalink,
                fullName = fullName,
                // Стандартный аватар маленький (-large, 100×100): просим версию побольше.
                avatarUrl = o.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" }?.replace("-large.", "-t500x500."),
                city = o.optString("city").takeIf { it != "null" }.orEmpty(),
                country = (o.optString("country").takeIf { it != "null" } ?: o.optString("country_code").takeIf { it != "null" }).orEmpty(),
                description = o.optString("description").takeIf { it != "null" }.orEmpty(),
                plan = o.optString("plan").takeIf { it != "null" }.orEmpty(),
                followers = o.optInt("followers_count"),
                followings = o.optInt("followings_count"),
                tracks = o.optInt("track_count"),
                playlists = o.optInt("playlist_count", o.optInt("playlists_count")),
                likes = o.optInt("public_favorites_count", o.optInt("likes_count")),
                profileUrl = o.optString("permalink_url").ifBlank { if (permalink.isNotBlank()) "https://soundcloud.com/$permalink" else "" },
            )
        }
    }

    /** Общий HTTP-клиент SoundCloud (с «браузерным» User-Agent). */
    internal fun client(): OkHttpClient = http

    /** Запрос к API SoundCloud: [access] — токен (null — без авторизации). Ошибка приходит как IOException("HTTP код: текст"). */
    internal fun call(method: String, url: String, access: String?, json: String? = null): String {
        var attempt = 0
        while (true) {
            val b = Request.Builder().url(url).header("accept", "application/json; charset=utf-8")
            if (!access.isNullOrBlank()) b.header("Authorization", "OAuth $access")
            when (method) {
                "GET" -> b.get()
                "DELETE" -> b.delete()
                else -> b.method(method, if (json.isNullOrEmpty()) ByteArray(0).toRequestBody(null) else json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            }
            var waitSec = -1L
            val t0 = System.currentTimeMillis()
            val shortUrl = url.removePrefix("https://").take(110)
            try {
                http.newCall(b.build()).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    if (r.isSuccessful) {
                        AppLog.d("ScApi", "$method $shortUrl → ${r.code}, ${body.length} Б, ${System.currentTimeMillis() - t0} мс")
                        return body
                    }
                    AppLog.w("ScApi", "$method $shortUrl → HTTP ${r.code}: ${errorText(body)} (${System.currentTimeMillis() - t0} мс)")
                    // 429 «слишком много запросов»: если сервер просит подождать недолго (лимит в минуту) — ждём и повторяем, иначе ошибка.
                    if (r.code == 429 && attempt < 2) {
                        val ra = r.header("Retry-After")?.trim()?.toLongOrNull() ?: 2L
                        if (ra in 0..5) waitSec = ra
                    }
                    if (waitSec < 0) throw IOException("HTTP ${r.code}: ${errorText(body)}")
                }
            } catch (e: IOException) {
                if (e.message?.startsWith("HTTP ") != true) AppLog.w("ScApi", "$method $shortUrl: ${e.javaClass.simpleName}: ${e.message} (${System.currentTimeMillis() - t0} мс)")
                throw e
            }
            attempt++
            Thread.sleep(waitSec * 1000L)
        }
    }

    /** Токен самого приложения (client credentials): для публичных данных — поиск, ссылки, воспроизведение — когда нет аккаунта SoundCloud. */
    fun clientCredentials(): ScTokens {
        val basic = Base64.encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val form = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()
        val req = Request.Builder().url(TOKEN_URL)
            .header("accept", "application/json; charset=utf-8")
            .header("Authorization", "Basic $basic")
            .post(form).build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${errorText(body)}")
            val o = JSONObject(body)
            val access = o.optString("access_token")
            if (access.isBlank()) throw IOException("SoundCloud не вернул токен")
            return ScTokens(access, "", System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L)
        }
    }

    /** Ключ аккаунта в [TokenStore]: настоящие токены хранятся отдельно, а ключ не меняется при их обновлении. */
    fun accountKey(id: Long): String = "sc_$id"

    fun friendlyError(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
                "Нет связи с SoundCloud. Проверьте интернет и повторите"
            m.contains("HTTP 400") || m.contains("invalid_grant") || m.contains("redirect_uri", ignoreCase = true) ->
                "SoundCloud отклонил вход: ${m.substringAfter(": ", m)}. Проверьте, что адрес $REDIRECT_URI добавлен в Redirect URIs приложения SoundCloud"
            m.contains("HTTP 429") -> "SoundCloud временно ограничил число запросов. Подождите немного и повторите"
            m.contains("HTTP 401") || m.contains("HTTP 403") -> "SoundCloud не принял ключи приложения или токен: $m"
            m.isNotBlank() -> m
            else -> "Не удалось войти в SoundCloud"
        }
    }
}
