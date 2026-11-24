package app.ymusic.m3e.recognition

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Запись истории распознавания: найденный трек и время, когда он был узнан. */
data class RecHistoryItem(val result: RecResult, val time: Long)

/**
 * История распознавания: найденные треки (новые — первыми), хранится на устройстве в SharedPreferences
 * «recognition_history». Трек попадает сюда, когда его узнали (см. [Recognizer.set]). Повторно узнанный трек
 * поднимается наверх с новым временем, а не дублируется. Можно удалить одну запись или очистить всё.
 */
object RecognitionHistory {
    private const val LIMIT = 100
    private var prefs: SharedPreferences? = null

    var items by mutableStateOf<List<RecHistoryItem>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("recognition_history", Context.MODE_PRIVATE)
        prefs = p
        items = parse(p.getString("items", null))
    }

    private fun parse(raw: String?): List<RecHistoryItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    RecHistoryItem(
                        RecResult(
                            title = o.getString("title"),
                            artist = o.optString("artist"),
                            album = o.optString("album").takeIf { it.isNotBlank() },
                            cover = o.optString("cover").takeIf { it.isNotBlank() },
                        ),
                        o.optLong("time"),
                    )
                }.getOrNull()
            }.take(LIMIT)
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<RecHistoryItem>) {
        items = list
        val arr = JSONArray()
        list.forEach { h ->
            arr.put(
                JSONObject()
                    .put("title", h.result.title).put("artist", h.result.artist)
                    .put("album", h.result.album ?: "").put("cover", h.result.cover ?: "")
                    .put("time", h.time),
            )
        }
        prefs?.edit()?.putString("items", arr.toString())?.apply()
    }

    private fun same(a: RecResult, b: RecResult) =
        a.title.equals(b.title, ignoreCase = true) && a.artist.equals(b.artist, ignoreCase = true)

    /** Добавляет найденный трек в начало; такой же трек переезжает наверх с новым временем. */
    @Synchronized
    fun add(r: RecResult) {
        if (r.title.isBlank()) return
        save((listOf(RecHistoryItem(r, System.currentTimeMillis())) + items.filterNot { same(it.result, r) }).take(LIMIT))
    }

    @Synchronized
    fun remove(item: RecHistoryItem) = save(items.filterNot { it === item || (it.time == item.time && same(it.result, item.result)) })

    @Synchronized
    fun clear() = save(emptyList())
}
