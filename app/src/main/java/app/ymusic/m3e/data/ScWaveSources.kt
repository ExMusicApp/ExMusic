package app.ymusic.m3e.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Откуда ExWave (волна SoundCloud) берёт музыку. Каждый источник можно включить или отключить. */
enum class ScWaveSource(val key: String, val title: String, val hint: String, val needsAccount: Boolean) {
    Likes("likes", "Мои лайки", "Треки, которые вы лайкнули на SoundCloud", true),
    Related("related", "Похожие на лайки", "Рекомендации SoundCloud по вашим лайкам; волна продолжается треками, похожими на играющий", true),
    History("history", "История прослушивания", "То, что вы слушали раньше на любых устройствах", true),
    Followings("followings", "Новое от подписок", "Свежие треки исполнителей, на которых вы подписаны", true),
    Playlists("playlists", "Понравившиеся плейлисты", "Треки из плейлистов, которые вы лайкнули", true),
    Popular("popular", "Популярное", "Свежие популярные треки случайного жанра", false),
}

/** Выбранные источники ExWave (SharedPreferences «sc_wave»). Выключить все нельзя: останется хотя бы один. */
object ScWaveSources {
    private var prefs: SharedPreferences? = null

    var enabled: Set<ScWaveSource> by mutableStateOf(ScWaveSource.entries.toSet())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("sc_wave", Context.MODE_PRIVATE)
        prefs = p
        enabled = parse(p.getString("sources", null))
    }

    private fun parse(raw: String?): Set<ScWaveSource> {
        if (raw == null) return ScWaveSource.entries.toSet()
        val set = raw.split(',').mapNotNull { k -> ScWaveSource.entries.firstOrNull { it.key == k.trim() } }.toSet()
        return set.ifEmpty { ScWaveSource.entries.toSet() }
    }

    fun update(sources: Set<ScWaveSource>) {
        val next = sources.ifEmpty { ScWaveSource.entries.toSet() }
        enabled = next
        prefs?.edit()?.putString("sources", next.joinToString(",") { it.key })?.apply()
    }
}
