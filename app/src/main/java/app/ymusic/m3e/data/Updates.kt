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

/**
 * Ветка обновлений. Определяется по последней букве версии: 1.2.0B — beta, 1.2.0S (или просто 1.2.0) — stable.
 * [rank] нужен, чтобы при одинаковом номере stable считался новее beta (1.2.0S новее 1.2.0B).
 */
enum class UpdateChannel(val title: String, val suffix: Char, val rank: Int) {
    Stable("Stable", 'S', 1),
    Beta("Beta", 'B', 0),
}

/** Версия вида `1.2.0B`: числа + буква ветки. */
data class AppVersion(val nums: List<Int>, val channel: UpdateChannel) : Comparable<AppVersion> {
    /** `1.2.0` */
    val number: String get() = nums.joinToString(".")

    /** `1.2.0B` — как в теге релиза. */
    val tag: String get() = number + channel.suffix

    /** Для экранов: `1.2.0 beta` / `1.2.0`. */
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

        /** `v1.2.0B`, `1.2.0S`, `1.2.0 beta`, `1.2.0` (без буквы = stable). Неизвестная буква → null (релиз пропускается). */
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

/** Проверка обновлений по релизам на GitHub (github.com/exmusicapp/exmusic/releases). */
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

    /** Версия установленного приложения (versionName из gradle, например `1.2.0B`). */
    val current: AppVersion =
        AppVersion.parse(BuildConfig.VERSION_NAME) ?: AppVersion(listOf(0, 0, 0), UpdateChannel.Beta)

    /** Выбранная ветка обновлений. По умолчанию — ветка установленной сборки. */
    var channel by mutableStateOf(current.channel)
        private set

    /** Проверять обновления при запуске приложения. */
    var checkOnStart by mutableStateOf(false)
        private set

    /** Идёт проверка. */
    var checking by mutableStateOf(false)
        private set

    /** Найденное при запуске обновление — показывается алертом внизу. */
    var banner by mutableStateOf<UpdateInfo?>(null)
        private set

    /** Результат ручной проверки из настроек — показывается модалкой. */
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

    /** Проверка при запуске: молчит, если обновлений нет или сети нет; найденное — в [banner]. */
    fun checkOnLaunch() {
        if (checkOnStart) check(manual = false)
    }

    /**
     * [manual] = true — результат (обновление / «последняя версия» / ошибка) показывается модалкой [dialog];
     * false — только алерт [banner], если нашлось обновление.
     */
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
            // Stable видит только стабильные релизы; Beta — беты и стабильные (новее из них).
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
