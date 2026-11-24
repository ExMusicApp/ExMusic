package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

/**
 * История поиска: последние запросы (новые — первыми), хранятся на устройстве в SharedPreferences «search_history».
 * Запрос попадает в историю, когда его отправили (кнопка поиска на клавиатуре), открыли карточку из результатов
 * или запустили трек из них. Повторный запрос поднимается наверх, а не дублируется.
 * Можно удалить один запрос или всю историю разом. Выключатель — Настройки → Внешний вид → Поиск.
 */
internal object SearchHistory {
    private const val LIMIT = 30
    private var prefs: SharedPreferences? = null

    /** Запросы, новые первыми. */
    var items by mutableStateOf<List<String>>(emptyList())
        private set

    /** Запоминать ли запросы. Выключено — история не пополняется и не показывается (сохранённое не стирается). */
    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("search_history", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", true)
        items = parse(p.getString("items", null))
    }

    private fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }.distinct().take(LIMIT)
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<String>) {
        items = list
        prefs?.edit()?.putString("items", JSONArray(list).toString())?.apply()
    }

    /** Добавляет запрос в начало; такой же запрос (без учёта регистра) переезжает наверх. */
    fun add(query: String) {
        val q = query.trim()
        if (!enabled || q.length < 2) return
        if (items.firstOrNull() == q) return
        save((listOf(q) + items.filterNot { it.equals(q, ignoreCase = true) }).take(LIMIT))
    }

    fun remove(query: String) = save(items.filterNot { it == query })

    fun clear() = save(emptyList())

    fun updateEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }
}
