package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ymusic.m3e.R

internal enum class DockLabels(val title: String) { Below("Под значком"), Beside("Рядом"), Hidden("Скрыть") }

internal enum class Tab(val title: String, val icon: Int, val removable: Boolean = true) {
    Home("Главная", R.drawable.ms_home),
    Playlists("Плейлисты", R.drawable.ms_library_music),
    Search("Поиск", R.drawable.ms_search),
    Profile("Профиль", R.drawable.ms_person, removable = false),
}

internal object NavSettings {
    private var prefs: SharedPreferences? = null

    var tabs by mutableStateOf(Tab.entries.toList())
        private set

    var swipeTabs by mutableStateOf(true)
        private set

    var swipeSensitivity by mutableStateOf(0.5f)
        private set

    var homeSearch by mutableStateOf(true)
        private set

    var homeSearchCorner by mutableStateOf(true)
        private set

    var liveSearch by mutableStateOf(true)
        private set

    var hints by mutableStateOf(true)
        private set

    var dockLabels by mutableStateOf(DockLabels.Beside)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("nav", Context.MODE_PRIVATE)
        prefs = p
        tabs = parseTabs(p.getString("tabs", null))
        swipeTabs = p.getBoolean("swipe_tabs", true)
        swipeSensitivity = p.getFloat("swipe_sensitivity", 0.5f).coerceIn(0f, 1f)
        homeSearch = p.getBoolean("home_search", true)
        homeSearchCorner = p.getBoolean("home_search_corner", true)
        SearchHistory.init(context)
        liveSearch = p.getBoolean("live_search", true)
        hints = p.getBoolean("hints", true)
        dockLabels = runCatching { DockLabels.valueOf(p.getString("dock_labels", null) ?: "Beside") }.getOrDefault(DockLabels.Beside)
    }

    private fun parseTabs(raw: String?): List<Tab> {
        if (raw == null) return Tab.entries.toList()
        val list = raw.split(',')
            .mapNotNull { n -> Tab.entries.firstOrNull { it.name == n.trim() } }
            .distinct()
            .toMutableList()
        if (Tab.Profile !in list) list += Tab.Profile
        return list
    }

    private fun saveTabs(list: List<Tab>) {
        tabs = list
        prefs?.edit()?.putString("tabs", list.joinToString(",") { it.name })?.apply()
    }

    fun move(tab: Tab, delta: Int) {
        val list = tabs.toMutableList()
        val i = list.indexOf(tab)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        list.removeAt(i)
        list.add(j, tab)
        saveTabs(list)
    }

    fun setVisible(tab: Tab, visible: Boolean) {
        val list = tabs.toMutableList()
        if (visible) {
            if (tab !in list) list += tab
        } else {
            if (!tab.removable || list.size <= 1) return
            list.remove(tab)
        }
        saveTabs(list)
    }

    fun resetTabs() = saveTabs(Tab.entries.toList())

    fun reorder(from: Int, to: Int) {
        val list = tabs.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        saveTabs(list)
    }

    fun setTabsFromString(raw: String) = saveTabs(parseTabs(raw))

    fun updateSwipeTabs(on: Boolean) {
        swipeTabs = on
        prefs?.edit()?.putBoolean("swipe_tabs", on)?.apply()
    }

    fun updateSwipeSensitivity(v: Float) {
        swipeSensitivity = v.coerceIn(0f, 1f)
        prefs?.edit()?.putFloat("swipe_sensitivity", swipeSensitivity)?.apply()
    }

    fun updateHomeSearch(on: Boolean) {
        homeSearch = on
        prefs?.edit()?.putBoolean("home_search", on)?.apply()
    }

    fun updateHomeSearchCorner(corner: Boolean) {
        homeSearchCorner = corner
        prefs?.edit()?.putBoolean("home_search_corner", corner)?.apply()
    }

    fun updateLiveSearch(on: Boolean) {
        liveSearch = on
        prefs?.edit()?.putBoolean("live_search", on)?.apply()
    }

    fun updateHints(on: Boolean) {
        hints = on
        prefs?.edit()?.putBoolean("hints", on)?.apply()
    }

    fun updateDockLabels(v: DockLabels) {
        dockLabels = v
        prefs?.edit()?.putString("dock_labels", v.name)?.apply()
    }
}
