package app.ymusic.m3e.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * «Не повторять треки волны»: трек, который уже звучал в волне («Моя волна» / VK Микс / ExWave), в течение суток
 * снова в волну не попадает. Работает одинаково для всех сервисов и всех станций (волна по треку, исполнителю и т. д.).
 *
 * Список запоминается на устройстве и переживает перезапуск приложения. Функцию можно выключить:
 * Настройки → Внешний вид → Главный экран. Если волна по узкому источнику исчерпалась и новых треков нет,
 * повторы всё равно разрешаются — волна не должна замолкать.
 */
object WaveSeen {
    private const val TTL_MS = 24L * 60 * 60 * 1000
    private const val MAX = 2000

    /** Включена ли фильтрация (по умолчанию да). */
    var enabled by mutableStateOf(true)
        private set

    private var prefs: SharedPreferences? = null
    private val seen = LinkedHashMap<String, Long>()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("wave_seen", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", true)
        synchronized(seen) {
            seen.clear()
            runCatching {
                val o = JSONObject(p.getString("items", null) ?: "{}")
                val now = System.currentTimeMillis()
                val items = ArrayList<Pair<String, Long>>()
                o.keys().forEach { k -> val ts = o.optLong(k); if (now - ts < TTL_MS) items += k to ts }
                items.sortBy { it.second } // порядок по времени нужен для быстрой очистки старого
                for ((k, ts) in items) seen[k] = ts
            }
        }
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }

    /** Трек прозвучал в волне. */
    fun mark(id: String) {
        synchronized(seen) {
            seen.remove(id)
            seen[id] = System.currentTimeMillis()
            prune()
            save()
        }
    }

    /** [tracks] без тех, что звучали в волне за последние 24 часа; при выключенной функции — без изменений. */
    fun filter(tracks: List<Track>): List<Track> {
        if (!enabled || tracks.isEmpty()) return tracks
        synchronized(seen) {
            prune()
            return tracks.filter { it.id !in seen }
        }
    }

    /** Забыть всё (кнопка «Сбросить» при необходимости). */
    fun clear() {
        synchronized(seen) { seen.clear(); save() }
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        val it = seen.entries.iterator()
        while (it.hasNext()) if (now - it.next().value >= TTL_MS) it.remove() else break // записи идут по времени
        while (seen.size > MAX) { val i = seen.entries.iterator(); i.next(); i.remove() }
    }

    private fun save() {
        val o = JSONObject()
        for ((k, v) in seen) o.put(k, v)
        prefs?.edit()?.putString("items", o.toString())?.apply()
    }
}
