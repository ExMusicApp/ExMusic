package app.ymusic.m3e.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ymusic.m3e.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class UpdateChannel(val title: String, val suffix: Char, val rank: Int) {
    Stable("Stable", 'S', 1),
    Beta("Beta", 'B', 0),
}

data class AppVersion(val nums: List<Int>, val channel: UpdateChannel) : Comparable<AppVersion> {
    val number: String get() = nums.joinToString(".")

    val tag: String get() = number + channel.suffix

    val display: String get() = if (channel == UpdateChannel.Beta) "$number beta" else number

    override fun compareTo(other: AppVersion): Int {
        val n = maxOf(nums.size, other.nums.size)
        for (i in 0 until n) {
            val a = nums.getOrElse(i) { 0 }
            val b = other.nums.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return channel.rank.compareTo(other.channel.rank)
    }

    companion object {
        private val re = Regex("""^[vV]?\s*(\d+(?:\.\d+)*)\s*[-_ ]?\s*([A-Za-z]*)$""")

        fun parse(text: String?): AppVersion? {
            val m = re.matchEntire(text?.trim().orEmpty()) ?: return null
            val nums = m.groupValues[1].split('.').mapNotNull { it.toIntOrNull() }
            if (nums.isEmpty()) return null
            val channel = when (m.groupValues[2].lowercase()) {
                "", "s", "stable" -> UpdateChannel.Stable
                "b", "beta" -> UpdateChannel.Beta
                else -> return null
            }
            return AppVersion(nums, channel)
        }
    }
}

data class UpdateInfo(
    val version: AppVersion,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

object Updates {
    const val REPO = "exmusicapp/exmusic"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases?per_page=30"
    private const val TAG = "Updates"

    private var prefs: SharedPreferences? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    val current: AppVersion =
        AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(listOf(0, 0, 0), UpdateChannel.Beta)

    var channel by mutableStateOf(current.channel)
        private set

    var checkOnStart by mutableStateOf(false)
        private set

    var checking by mutableStateOf(false)
        private set

    var banner by mutableStateOf<UpdateInfo?>(null)
        private set

    var dialog by mutableStateOf<UpdateResult?>(null)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        prefs = p
        channel = p.getString("channel", null)
            ?.let { name -> UpdateChannel.entries.firstOrNull { it.name == name } }
            ?: current.channel
        checkOnStart = p.getBoolean("check_on_start", false)
    }

    fun updateChannel(value: UpdateChannel) {
        channel = value
        prefs?.edit()?.putString("channel", value.name)?.apply()
    }

    fun updateCheckOnStart(value: Boolean) {
        checkOnStart = value
        prefs?.edit()?.putBoolean("check_on_start", value)?.apply()
    }

    fun dismissBanner() { banner = null }
    fun dismissDialog() { dialog = null }

    fun checkOnLaunch() {
        if (checkOnStart) check(manual = false)
    }

    fun check(manual: Boolean) {
        if (checking) return
        checking = true
        scope.launch {
            val result = runCatching { fetch(channel) }
                .getOrElse { UpdateResult.Failed(it.message ?: it.javaClass.simpleName) }
            AppLog.i(TAG, "Проверка обновлений (${channel.title}, ручная=$manual): $result")
            withContext(Dispatchers.Main) {
                checking = false
                if (manual) {
                    banner = null
                    dialog = result
                } else if (result is UpdateResult.Available) {
                    banner = result.info
                }
            }
        }
    }

    private fun fetch(channel: UpdateChannel): UpdateResult {
        val req = Request.Builder()
            .url(API_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ExMusic/${current.tag}")
            .build()
        val body = try {
            http.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> return UpdateResult.Failed("Релизы не найдены (404). Репозиторий $REPO закрыт или в нём ещё нет релизов.")
                    resp.code == 403 || resp.code == 429 ->
                        return UpdateResult.Failed("GitHub временно ограничил запросы (${resp.code}). Попробуйте позже.")
                    !resp.isSuccessful -> return UpdateResult.Failed("GitHub вернул ошибку ${resp.code}.")
                }
                resp.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            return UpdateResult.Failed("Нет соединения с GitHub: ${e.message ?: e.javaClass.simpleName}")
        }

        val arr = try {
            JSONArray(body)
        } catch (e: Exception) {
            return UpdateResult.Failed("Не удалось разобрать ответ GitHub.")
        }

        var best: UpdateInfo? = null
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            if (r.optBoolean("draft")) continue
            val tag = r.optString("tag_name")
            val v = AppVersion.parse(tag) ?: AppVersion.parse(r.optString("name")) ?: continue
            if (channel == UpdateChannel.Stable && v.channel != UpdateChannel.Stable) continue
            if (best != null && v <= best.version) continue
            var apk: String? = null
            r.optJSONArray("assets")?.let { assets ->
                for (j in 0 until assets.length()) {
                    val a = assets.optJSONObject(j) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                        break
                    }
                }
            }
            best = UpdateInfo(
                version = v,
                title = r.optString("name").ifBlank { tag },
                notes = r.optString("body").trim(),
                pageUrl = r.optString("html_url").ifBlank { RELEASES_PAGE },
                apkUrl = apk,
            )
        }
        return if (best != null && best.version > current) UpdateResult.Available(best) else UpdateResult.UpToDate
    }
}
