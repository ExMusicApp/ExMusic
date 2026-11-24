package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ymusic.m3e.R

/**
 * Экраны нижней панели. Порядок в enum — порядок по умолчанию (Главная, Плейлисты, Поиск, Профиль).
 * Реальный порядок и набор настраиваются: Настройки → Внешний вид → Навигация → «Экраны».
 * «Профиль» убрать нельзя: через него открываются настройки.
 */
/** Где показывать подписи вкладок в доке: под значком, рядом со значком или скрыть. */
internal enum class DockLabels(val title: String) { Below("Под значком"), Beside("Рядом"), Hidden("Скрыть") }

internal enum class Tab(val title: String, val icon: Int, val removable: Boolean = true) {
    Home("Главная", R.drawable.ms_home),
    Playlists("Плейлисты", R.drawable.ms_library_music),
    Search("Поиск", R.drawable.ms_search),
    Profile("Профиль", R.drawable.ms_person, removable = false),
}

/**
 * Настройки навигации, поиска и подсказок (хранятся в SharedPreferences «nav»):
 *  • какие экраны есть в панели и в каком порядке;
 *  • листание экранов свайпом;
 *  • кнопка поиска на главном экране;
 *  • живой поиск (результаты появляются прямо во время ввода);
 *  • подсказки при долгом нажатии на маленькие кнопки.
 */
internal object NavSettings {
    private var prefs: SharedPreferences? = null

    /** Экраны, показанные в панели, по порядку слева направо. Всегда содержит «Профиль». */
    var tabs by mutableStateOf(Tab.entries.toList())
        private set

    /** Листание экранов свайпом влево/вправо. */
    var swipeTabs by mutableStateOf(true)
        private set

    /** Чувствительность листания экранов, 0..1 (0.5 — как в системе). Больше — легче и короче свайп. */
    var swipeSensitivity by mutableStateOf(0.5f)
        private set

    /** Строка-кнопка поиска на главном экране. */
    var homeSearch by mutableStateOf(true)
        private set

    /**
     * Окно с пояснением при запуске ExWave (SoundCloud) показывается, пока не поставлена галочка «Больше не показывать».
     * Отметка общая для всех аккаунтов и не сбрасывается ни при переключении аккаунта, ни при повторном входе.
     */
    var waveHintOptOut by mutableStateOf(false)
        private set

    /** Где показывать поиск на главной: false — плиткой под блоком «Моей волны», true — значком в правом верхнем углу блока (по умолчанию — значок). */
    var homeSearchCorner by mutableStateOf(true)
        private set

    /** Живой поиск: запрос уходит сам, пока вы печатаете (с небольшой задержкой). Выключено — поиск по «Enter». */
    var liveSearch by mutableStateOf(true)
        private set

    /** Подсказка с названием кнопки при долгом нажатии. */
    var hints by mutableStateOf(true)
        private set

    /** Подписи вкладок в доке. По умолчанию — рядом со значком. */
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
        waveHintOptOut = p.getBoolean("wave_hint_hidden", false) || !p.getString("wave_hint_opt_out", "").isNullOrEmpty()
        SearchHistory.init(context)
        liveSearch = p.getBoolean("live_search", true)
        hints = p.getBoolean("hints", true)
        dockLabels = runCatching { DockLabels.valueOf(p.getString("dock_labels", null) ?: "Beside") }.getOrDefault(DockLabels.Beside)
    }

    /** Разбирает сохранённый список; неизвестные имена и повторы отбрасываются, «Профиль» возвращается, если пропал. */
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

    /** Сдвигает экран на [delta] позиций (−1 — влево, +1 — вправо). */
    fun move(tab: Tab, delta: Int) {
        val list = tabs.toMutableList()
        val i = list.indexOf(tab)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        list.removeAt(i)
        list.add(j, tab)
        saveTabs(list)
    }

    /** Показать экран в панели (добавляется в конец) или убрать. Последний экран и «Профиль» убрать нельзя. */
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

    /** Переставляет экран с позиции [from] на позицию [to] (перетаскивание в редакторе интерфейса). */
    fun reorder(from: Int, to: Int) {
        val list = tabs.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        saveTabs(list)
    }

    /** Порядок экранов из строки «Home,Search,…» (импорт раскладки). */
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

    /** Прятать ли окно ExWave (для всех аккаунтов). */
    fun waveHintHidden(@Suppress("UNUSED_PARAMETER") token: String? = null): Boolean = waveHintOptOut

    /** Галочка «Больше не показывать»: окно ExWave больше не появляется. */
    fun hideWaveHint(@Suppress("UNUSED_PARAMETER") token: String? = null) {
        waveHintOptOut = true
        prefs?.edit()?.putBoolean("wave_hint_hidden", true)?.remove("wave_hint_opt_out")?.apply()
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
