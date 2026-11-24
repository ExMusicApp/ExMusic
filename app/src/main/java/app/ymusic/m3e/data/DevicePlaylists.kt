package app.ymusic.m3e.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Режим «Локальные файлы»: приложение работает без Яндекс Музыки — только музыка с устройства и свои плейлисты из неё.
 * Включается кнопкой «Пропустить» на экране входа (справа сверху). Хранится в SharedPreferences «local_mode».
 */
object LocalModeSettings {
    private var prefs: SharedPreferences? = null

    var enabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("local_mode", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean("enabled", value)?.apply()
    }
}

/**
 * Плейлист, собранный из файлов устройства. [trackIds] — id локальных треков ("local:<MediaStore id>") по порядку.
 * [folder] — путь папки: плейлист-папка, в нём играют все файлы из этой папки (см. [LocalFolders]); [trackIds] у него пуст.
 */
data class DevicePlaylist(val id: String, val name: String, val trackIds: List<String>, val folder: String? = null) {
    val isFolder: Boolean get() = folder != null
}

/** Свои плейлисты из файлов устройства (режим «Локальные файлы»). Лежат только на телефоне, в SharedPreferences «device_playlists». */
object DevicePlaylists {
    private var prefs: SharedPreferences? = null

    var items by mutableStateOf<List<DevicePlaylist>>(emptyList())
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("device_playlists", Context.MODE_PRIVATE)
        prefs = p
        items = parse(p.getString("items", null))
    }

    private fun parse(raw: String?): List<DevicePlaylist> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("tracks")
                DevicePlaylist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    trackIds = if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it) },
                    folder = o.optString("folder").takeIf { it.isNotBlank() },
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<DevicePlaylist>) {
        items = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().put("id", p.id).put("name", p.name)
                    .put("tracks", JSONArray().also { a -> p.trackIds.forEach { a.put(it) } })
                    .also { o -> p.folder?.let { o.put("folder", it) } },
            )
        }
        prefs?.edit()?.putString("items", arr.toString())?.apply()
    }

    /** Создаёт плейлист (при необходимости сразу с одним треком) и возвращает его. */
    fun create(name: String, firstTrackId: String? = null): DevicePlaylist {
        val p = DevicePlaylist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Плейлист" }, listOfNotNull(firstTrackId))
        save(items + p)
        return p
    }

    /** Создаёт плейлист-папку: название по умолчанию — имя папки, его можно переименовать. */
    fun createFromFolder(folder: String, name: String? = null): DevicePlaylist {
        val p = DevicePlaylist(
            UUID.randomUUID().toString(),
            name?.trim().orEmpty().ifEmpty { LocalFolders.displayName(folder) },
            emptyList(),
            folder,
        )
        save(items + p)
        return p
    }

    fun rename(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        save(items.map { if (it.id == id) it.copy(name = n) else it })
    }

    fun delete(id: String) = save(items.filterNot { it.id == id })

    /** Добавляет треки в конец плейлиста; уже лежащие в нём пропускаются. */
    fun addTracks(id: String, trackIds: Collection<String>) {
        save(items.map { p ->
            if (p.id != id) p else p.copy(trackIds = p.trackIds + trackIds.filter { it !in p.trackIds }.distinct())
        })
    }

    fun removeTrack(id: String, trackId: String) {
        save(items.map { if (it.id == id) it.copy(trackIds = it.trackIds - trackId) else it })
    }
}
