package app.ymusic.m3e.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

data class StoredAccount(
    val token: String,
    val uid: Long = 0L,
    val login: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val hasPlus: Boolean = false,
) {
    val title: String get() = name.ifBlank { login }.ifBlank { "Аккаунт" }
}

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
        val legacy = prefs.getString("token", null)
        if (legacy != null) {
            if (list.none { it.token == legacy }) list.add(StoredAccount(token = legacy))
            if (activeToken == null) activeToken = legacy
            prefs.edit().remove("token").apply()
            save()
        }
        if (activeToken != null && list.none { it.token == activeToken }) activeToken = list.firstOrNull()?.token
    }

    val accounts: List<StoredAccount> @Synchronized get() = list.toList()

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

    @Synchronized
    fun setActive(token: String) {
        if (list.none { it.token == token }) return
        activeToken = token
        save()
    }

    @Synchronized
    fun updateProfile(token: String, acc: Account) {
        val i = list.indexOfFirst { it.token == token }
        if (i < 0) return
        list[i] = list[i].copy(
            uid = acc.uid, login = acc.login, name = acc.name,
            avatarUrl = acc.avatarUrl ?: list[i].avatarUrl, hasPlus = acc.hasPlus,
        )
        list.removeAll { other ->
            other.token != token &&
                ((acc.uid != 0L && other.uid == acc.uid) || (acc.login.isNotBlank() && other.login == acc.login))
        }
        save()
    }

    @Synchronized
    fun remove(token: String) {
        list.removeAll { it.token == token }
        if (activeToken == token) activeToken = list.firstOrNull()?.token
        save()
    }

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
                    .put("name", it.name).put("avatar", it.avatarUrl ?: "").put("plus", it.hasPlus),
            )
        }
        prefs.edit().apply {
            putString("accounts", arr.toString())
            if (activeToken == null) remove("active") else putString("active", activeToken)
        }.apply()
    }
}
