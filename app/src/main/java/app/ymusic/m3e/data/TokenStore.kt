package app.ymusic.m3e.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/** Сохранённый аккаунт: токен и кэш профиля (чтобы список аккаунтов показывался без сети). */
data class StoredAccount(
    val token: String,
    val uid: Long = 0L,
    val login: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val hasPlus: Boolean = false,
    /** Сервис аккаунта; для VK ещё и клиент, под которым выдан токен ([VkClient.id]). */
    val provider: Provider = Provider.YANDEX,
    val vkClient: String = "",
    /** Только SoundCloud: [token] — стабильный ключ аккаунта, а настоящие токены лежат здесь. */
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    /** Только SoundCloud: профиль (JSON, см. [ScProfile]) — чтобы данные показывались без сети. */
    val scInfo: String = "",
    /** Только Spotify: куки сессии (главная — sp_dc), полученные при входе через WebView. По ним заново берётся профиль. */
    val spCookies: String = "",
) {
    /** Что показывать как заголовок: имя, иначе логин. */
    val title: String get() = name.ifBlank { login }.ifBlank { "Аккаунт" }
}

/**
 * Хранит OAuth-токены нескольких аккаунтов в зашифрованных SharedPreferences (Android Keystore).
 * [token] — токен активного аккаунта (им пользуется API).
 */
class TokenStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private var list: MutableList<StoredAccount> = mutableListOf()
    private var activeToken: String? = null

    init {
        load()
        // Миграция: раньше хранился один токен под ключом "token".
        val legacy = prefs.getString("token", null)
        if (legacy != null) {
            if (list.none { it.token == legacy }) list.add(StoredAccount(token = legacy))
            if (activeToken == null) activeToken = legacy
            prefs.edit().remove("token").apply()
            save()
        }
        if (activeToken != null && list.none { it.token == activeToken }) activeToken = list.firstOrNull()?.token
    }

    /** Все сохранённые аккаунты. */
    val accounts: List<StoredAccount> @Synchronized get() = list.toList()

    /**
     * Токен активного аккаунта. Присвоение токена добавляет аккаунт (если его ещё нет) и делает активным;
     * присвоение null удаляет активный аккаунт.
     */
    var token: String?
        @Synchronized get() = activeToken
        @Synchronized set(value) {
            if (value == null) {
                activeToken?.let { remove(it) }
            } else {
                if (list.none { it.token == value }) list.add(StoredAccount(token = value))
                activeToken = value
                save()
            }
        }

    /** Активный аккаунт целиком (null — никого нет). */
    val active: StoredAccount? @Synchronized get() = list.firstOrNull { it.token == activeToken }

    /** Сервис активного аккаунта; без аккаунта — Яндекс (прежнее поведение). */
    val activeProvider: Provider @Synchronized get() = active?.provider ?: Provider.YANDEX

    /** Токен активного аккаунта, но только если он принадлежит сервису [p]: чужой токен в чужой API не уходит. */
    @Synchronized
    fun tokenFor(p: Provider): String? = active?.takeIf { it.provider == p }?.token

    /** VK-клиент активного аккаунта (по умолчанию — «официальный»). */
    val activeVkClient: VkClient @Synchronized get() = VkClient.fromId(active?.vkClient)

    /** Добавляет аккаунт VK (или обновляет клиент у существующего с таким токеном) и делает его активным. */
    @Synchronized
    fun addVk(token: String, client: VkClient) {
        val i = list.indexOfFirst { it.token == token }
        if (i < 0) list.add(StoredAccount(token = token, provider = Provider.VK, vkClient = client.id))
        else list[i] = list[i].copy(provider = Provider.VK, vkClient = client.id)
        activeToken = token
        save()
    }

    /** Добавляет аккаунт SoundCloud (или обновляет уже сохранённый) и делает его активным. [key] — [SoundCloudAuth.accountKey]. */
    @Synchronized
    fun addSoundCloud(key: String, tokens: ScTokens, profile: ScProfile) {
        val acc = StoredAccount(
            token = key, uid = profile.id, login = profile.permalink, name = profile.username,
            avatarUrl = profile.avatarUrl, provider = Provider.SOUNDCLOUD,
            accessToken = tokens.access, refreshToken = tokens.refresh, expiresAt = tokens.expiresAt,
            scInfo = profile.toJson(),
        )
        val i = list.indexOfFirst { it.token == key }
        if (i < 0) list.add(acc) else list[i] = acc
        activeToken = key
        save()
    }

    /** Новые токены аккаунта SoundCloud (после обновления по refresh-токену). */
    @Synchronized
    fun updateSoundCloudTokens(key: String, tokens: ScTokens) {
        val i = list.indexOfFirst { it.token == key }
        if (i < 0) return
        list[i] = list[i].copy(accessToken = tokens.access, refreshToken = tokens.refresh, expiresAt = tokens.expiresAt)
        save()
    }

    /** Свежий профиль аккаунта SoundCloud: ник, аватар и остальные данные. */
    @Synchronized
    fun updateSoundCloudProfile(key: String, profile: ScProfile) {
        val i = list.indexOfFirst { it.token == key }
        if (i < 0) return
        list[i] = list[i].copy(
            uid = profile.id, login = profile.permalink, name = profile.username,
            avatarUrl = profile.avatarUrl ?: list[i].avatarUrl, scInfo = profile.toJson(),
        )
        save()
    }

    /**
     * Добавляет аккаунт Spotify (или обновляет сохранённый) и делает его активным. [key] — [SpotifyAuth.accountKey];
     * [profile] может быть null (профиль не успел загрузиться): он подтянется при следующем обновлении.
     */
    @Synchronized
    fun addSpotify(key: String, cookies: String, profile: SpProfile?) {
        val i = list.indexOfFirst { it.token == key }
        val old = list.getOrNull(i)
        val acc = StoredAccount(
            token = key, login = profile?.id ?: old?.login.orEmpty(), name = profile?.name ?: old?.name.orEmpty(),
            avatarUrl = profile?.avatarUrl ?: old?.avatarUrl, provider = Provider.SPOTIFY, spCookies = cookies,
        )
        if (i < 0) list.add(acc) else list[i] = acc
        // Тот же пользователь, вошедший заново (другие куки): старую запись не дублируем.
        if (acc.login.isNotBlank()) list.removeAll { it.token != key && it.provider == Provider.SPOTIFY && it.login == acc.login }
        activeToken = key
        save()
    }

    /** Свежий профиль аккаунта Spotify: ник и аватар. */
    @Synchronized
    fun updateSpotifyProfile(key: String, profile: SpProfile) {
        val i = list.indexOfFirst { it.token == key }
        if (i < 0) return
        list[i] = list[i].copy(login = profile.id, name = profile.name, avatarUrl = profile.avatarUrl ?: list[i].avatarUrl)
        save()
    }

    /** Делает аккаунт с этим токеном активным. */
    @Synchronized
    fun setActive(token: String) {
        if (list.none { it.token == token }) return
        activeToken = token
        save()
    }

    /**
     * Обновляет кэш профиля аккаунта. Если уже есть другой токен того же пользователя (тот же uid или логин),
     * старая запись удаляется, чтобы один аккаунт не дублировался.
     */
    @Synchronized
    fun updateProfile(token: String, acc: Account) {
        val i = list.indexOfFirst { it.token == token }
        if (i < 0) return
        list[i] = list[i].copy(
            uid = acc.uid, login = acc.login, name = acc.name,
            avatarUrl = acc.avatarUrl ?: list[i].avatarUrl, hasPlus = acc.hasPlus,
        )
        val prov = list[i].provider
        list.removeAll { other ->
            other.token != token && other.provider == prov &&
                ((acc.uid != 0L && other.uid == acc.uid) || (acc.login.isNotBlank() && other.login == acc.login))
        }
        save()
    }

    /** Удаляет аккаунт. Если он был активным — активным становится первый из оставшихся (или никто). */
    @Synchronized
    fun remove(token: String) {
        list.removeAll { it.token == token }
        if (activeToken == token) activeToken = list.firstOrNull()?.token
        save()
    }

    /** Выход из всех аккаунтов. */
    @Synchronized
    fun clear() {
        list.clear()
        activeToken = null
        prefs.edit().clear().apply()
    }

    private fun load() {
        val raw = prefs.getString("accounts", null)
        if (raw != null) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val t = o.optString("token")
                    if (t.isBlank() || list.any { it.token == t }) continue
                    list.add(
                        StoredAccount(
                            token = t,
                            uid = o.optLong("uid", 0L),
                            login = o.optString("login"),
                            name = o.optString("name"),
                            avatarUrl = o.optString("avatar").ifBlank { null },
                            hasPlus = o.optBoolean("plus", false),
                            provider = Provider.fromId(o.optString("provider").ifBlank { null }),
                            vkClient = o.optString("vkClient"),
                            accessToken = o.optString("sc_access"),
                            refreshToken = o.optString("sc_refresh"),
                            expiresAt = o.optLong("sc_expires", 0L),
                            scInfo = o.optString("sc_info"),
                            spCookies = o.optString("sp_cookies"),
                        ),
                    )
                }
            }
        }
        activeToken = prefs.getString("active", null)
    }

    private fun save() {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("token", it.token).put("uid", it.uid).put("login", it.login)
                    .put("name", it.name).put("avatar", it.avatarUrl ?: "").put("plus", it.hasPlus)
                    .put("provider", it.provider.id).put("vkClient", it.vkClient)
                    .put("sc_access", it.accessToken).put("sc_refresh", it.refreshToken)
                    .put("sc_expires", it.expiresAt).put("sc_info", it.scInfo)
                    .put("sp_cookies", it.spCookies),
            )
        }
        prefs.edit().apply {
            putString("accounts", arr.toString())
            if (activeToken == null) remove("active") else putString("active", activeToken)
        }.apply()
    }
}
