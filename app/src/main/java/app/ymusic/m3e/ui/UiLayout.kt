package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R
import org.json.JSONObject

/** Подпись блока в редакторе: блок волны у VK называется «VK Микс». */
internal val LayoutBlock.label: String get() = if (this === HomeBlock.Wave && (ActiveProvider.isVk || ActiveProvider.isSc)) ActiveProvider.waveTitle else title

/** Блок экрана, который можно переставлять, скрывать и масштабировать в редакторе интерфейса. */
internal interface LayoutBlock {
    val title: String
    val hint: String
}

/** Блоки главного экрана. Порядок в enum — порядок по умолчанию. */
internal enum class HomeBlock(override val title: String, override val hint: String) : LayoutBlock {
    Greeting("Приветствие", "Надпись «Доброе утро, ник» со значком; видна, если приветствие включено в настройках главного экрана"),
    Wave("Моя волна", "Большой блок с кнопкой волны"),
    SearchTile("Плитка поиска", "Видна, если поиск на главной включён «плиткой»"),
    Playlists("Плейлисты", "Ряд карточек плейлистов"),
    Recognize("Распознать музыку", "Плитка распознавания"),
    Chart("Плитка «Чарт»", "Стоит рядом с соседней плиткой, если та тоже видна"),
    New("Плитка «Новинки»", "Стоит рядом с соседней плиткой, если та тоже видна"),
    HistoryTracks("Плитка «История треков»", "Стоит рядом с соседней плиткой, если та тоже видна"),
    Podcasts("Плитка «Подкасты»", "Стоит рядом с соседней плиткой, если та тоже видна"),
    Mixes("Подборки и миксы (SoundCloud)", "Миксы и подборки плейлистов SoundCloud; ниже плиток, чтобы они не сдвигались при загрузке"),
    History("История прослушивания", "Последние треки"),
    Liked("Любимое", "Список лайков"),
}

/** Блоки экрана «Плейлисты». */
internal enum class PlaylistsBlock(override val title: String, override val hint: String) : LayoutBlock {
    Liked("Любимое", "Большая карточка с кнопкой play"),
    Local("С устройства", "Музыка на телефоне (если включена)"),
    Create("Новый плейлист", "Кнопка создания"),
    Playlists("Плейлисты", "Заголовок и ряд карточек"),
    Albums("Альбомы", "Заголовок и ряд альбомов"),
}

/** Блоки страницы профиля. */
internal enum class ProfileBlock(override val title: String, override val hint: String) : LayoutBlock {
    Header("Аватар, ник и логин", "Аватар слева, справа ник и под ним логин"),
    MyTracks("Мои треки (SoundCloud)", "Последние треки пользователя; только для аккаунта SoundCloud"),
    MyAlbums("Мои альбомы (SoundCloud)", "Альбомы и плейлисты пользователя; только для аккаунта SoundCloud"),
    Playlists("Публичные плейлисты", "Плитки всех открытых плейлистов"),
    Refresh("Кнопка «Обновить»", "Обновление данных аккаунта"),
}

/**
 * Кнопки нижнего ряда полного плеера. [Gap] — пустое место, которое раздвигает кнопки: по умолчанию стоит между
 * левой и правой группой. Его можно двигать и убирать.
 */
internal enum class PlayerBtn(override val title: String, override val hint: String) : LayoutBlock {
    Output("Вывод звука", "Выбор устройства"),
    More("Меню «⋮»", "Скачать, информация, в плейлист"),
    Repeat("Повтор / Встряхнуть", "Повтор, а в волне — «Встряхнуть»"),
    Lyrics("Текст песни", "Показать текст"),
    Mix("Микс треков", "Окно микса: следующий трек и переход"),
    Gap("Пустое место", "Раздвигает кнопки"),
    Dislike("Не нравится", "Дизлайк трека"),
    Like("Нравится", "Лайк трека"),
}

/** Идентификаторы экранов в хранилище. */
internal object LScreen {
    const val HOME = "home"
    const val PLAYLISTS = "playlists"
    const val PROFILE = "profile"
    const val PLAYER = "player"

    /** Блоки мини-плеера ([MiniBlock]). */
    const val MINI = "mini"

    /** Части полного плеера ([PlayerPart]). */
    const val PLAYER_FULL = "player_full"

    /** Вкладки нижней панели (только для режима редактора на экране). */
    const val DOCK = "dock"
}

/**
 * Раскладка интерфейса (SharedPreferences «ui_layout»): порядок и видимость блоков экранов, масштаб и отступ каждого блока,
 * размеры дока, кнопок плеера и аватара профиля, поведение ссылок («чем поделились»).
 * Всё хранится в простых ключах, поэтому раскладку можно выгрузить в JSON и вернуть обратно.
 */
internal object UiLayout {
    private var prefs: SharedPreferences? = null

    /** Счётчик изменений: чтение внутри @Composable подписывает его на перерисовку. */
    private var rev by mutableIntStateOf(0)

    // Значения читаются на каждой строке списков (отступы и масштаб блока), а SharedPreferences + сборка ключа + split
    // на каждую строку при каждой перерисовке заметно нагружали прокрутку. Кэш сбрасывается при любом изменении (rev).
    private val lookupCache = HashMap<String, Any>()
    private var lookupCacheRev = -1

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(key: String, compute: () -> T): T {
        val r = rev // чтение состояния: подписывает перерисовку и сбрасывает кэш при изменениях
        return synchronized(lookupCache) {
            if (r != lookupCacheRev) { lookupCache.clear(); lookupCacheRev = r }
            lookupCache.getOrPut(key, compute) as T
        }
    }

    const val MIN_ZOOM = 0.6f
    const val MAX_ZOOM = 1.6f
    const val MAX_GAP = 64

    var dockZoom by mutableStateOf(1f)
        private set
    var dockIconZoom by mutableStateOf(1f)
        private set
    var playerBtnZoom by mutableStateOf(1f)
        private set
    var profileAvatarDp by mutableIntStateOf(96)
        private set
    var profileColumns by mutableIntStateOf(2)
        private set

    /** Кнопка «Поделиться» под логином на странице профиля. */
    var profileShare by mutableStateOf(true)
        private set

    /** Открывать то, чем поделились (трек, альбом, плейлист, исполнитель), нижней модалкой, а не отдельной страницей. */
    var linkModal by mutableStateOf(true)
        private set

    /** Предлагать открыть ссылку Яндекс Музыки, скопированную в буфер обмена. */
    var clipLinks by mutableStateOf(true)
        private set

    /** Режим редактора на экране: на страницах появляется плавающая кнопка, которой включается правка элементов. */
    var editMode by mutableStateOf(false)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("ui_layout", Context.MODE_PRIVATE)
        prefs = p
        migrateHomeTiles(p)
        migrateHomeMixes(p)
        migrateHomeGreeting(p)
        load(p)
    }

    /**
     * Раньше «Чарт и Новинки» и «История треков и Подкасты» были двумя блоками по две плитки. Теперь каждая плитка —
     * отдельный блок: старые порядок, скрытие, масштаб и отступы переносятся на обе новые плитки.
     */
    private fun migrateHomeTiles(p: SharedPreferences) {
        val legacy = mapOf("ChartNew" to listOf("Chart", "New"), "HistoryTiles" to listOf("HistoryTracks", "Podcasts"))
        val e = p.edit()
        var changed = false
        for (key in listOf("order_${LScreen.HOME}", "hidden_${LScreen.HOME}")) {
            val raw = p.getString(key, null) ?: continue
            if (legacy.keys.none { it in raw.split(',') }) continue
            e.putString(key, raw.split(',').filter { it.isNotBlank() }.flatMap { legacy[it.trim()] ?: listOf(it.trim()) }.distinct().joinToString(","))
            changed = true
        }
        p.all.forEach { (k, v) ->
            for ((old, news) in legacy) {
                if (!k.endsWith("_${LScreen.HOME}_$old")) continue
                val prefix = k.removeSuffix(old)
                for (n in news) when (v) {
                    is Int -> e.putInt(prefix + n, v)
                    is Float -> e.putFloat(prefix + n, v)
                    is Boolean -> e.putBoolean(prefix + n, v)
                    is String -> e.putString(prefix + n, v)
                }
                e.remove(k)
                changed = true
            }
        }
        if (changed) e.apply()
    }

    /** Новый блок «Подборки и миксы» в уже сохранённом порядке главной ставим перед историей, а не в самый конец списка. */
    private fun migrateHomeMixes(p: SharedPreferences) {
        val raw = p.getString("order_${LScreen.HOME}", null) ?: return
        val names = raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if ("Mixes" in names) return
        val i = names.indexOf("History").takeIf { it >= 0 } ?: names.size
        val out = names.toMutableList().apply { add(i, "Mixes") }
        p.edit().putString("order_${LScreen.HOME}", out.joinToString(",")).apply()
    }

    /** Новый блок «Приветствие» в уже сохранённом порядке главной ставим первым (как он и стоял раньше), а не в конец списка. */
    private fun migrateHomeGreeting(p: SharedPreferences) {
        val raw = p.getString("order_${LScreen.HOME}", null) ?: return
        val names = raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if ("Greeting" in names) return
        p.edit().putString("order_${LScreen.HOME}", (listOf("Greeting") + names).joinToString(",")).apply()
    }

    private fun load(p: SharedPreferences) {
        dockZoom = p.getFloat("dock_zoom", 1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
        dockIconZoom = p.getFloat("dock_icon_zoom", 1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
        playerBtnZoom = p.getFloat("player_btn_zoom", 1f).coerceIn(MIN_ZOOM, MAX_ZOOM)
        profileAvatarDp = p.getInt("profile_avatar_dp", 96).coerceIn(56, 160)
        profileColumns = p.getInt("profile_columns", 2).coerceIn(1, 4)
        profileShare = p.getBoolean("profile_share", true)
        linkModal = p.getBoolean("link_modal", true)
        clipLinks = p.getBoolean("clip_links", true)
        editMode = p.getBoolean("edit_mode", false)
        rev++
    }

    // ---- Порядок и видимость ----

    /** Блоки экрана [screen] в сохранённом порядке; новые (которых не было при сохранении) добавляются в конец. */
    fun <E> order(screen: String, all: List<E>): List<E> where E : Enum<E> {
        rev
        val raw = prefs?.getString("order_$screen", null) ?: return all
        val saved = raw.split(',').mapNotNull { n -> all.firstOrNull { it.name == n.trim() } }.distinct()
        return saved + all.filter { it !in saved }
    }

    /** Блоки, которые на экране [screen] скрыты, пока пользователь ничего не менял (кнопки мини-плеера «назад», «вперёд», «лайк»). */
    private fun defaultHidden(screen: String): String = if (screen == LScreen.MINI) "Prev,Next,Like" else ""

    fun <E> isHidden(screen: String, block: E): Boolean where E : Enum<E> =
        cached("h|$screen|${block.name}") {
            val raw = prefs?.getString("hidden_$screen", null) ?: defaultHidden(screen)
            block.name in raw.split(',')
        }

    fun <E> setHidden(screen: String, block: E, hidden: Boolean) where E : Enum<E> {
        val cur = (prefs?.getString("hidden_$screen", null) ?: defaultHidden(screen)).split(',').filter { it.isNotBlank() }.toMutableSet()
        if (hidden) cur += block.name else cur -= block.name
        prefs?.edit()?.putString("hidden_$screen", cur.joinToString(","))?.apply()
        rev++
    }

    /** Переставляет блок с позиции [from] на позицию [to]. */
    fun <E> move(screen: String, all: List<E>, from: Int, to: Int) where E : Enum<E> {
        val list = order(screen, all).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        prefs?.edit()?.putString("order_$screen", list.joinToString(",") { it.name })?.apply()
        rev++
    }

    /** Меняет местами два блока в порядке экрана. */
    fun <E> swap(screen: String, all: List<E>, a: E, b: E) where E : Enum<E> {
        val list = order(screen, all).toMutableList()
        val i = list.indexOf(a)
        val j = list.indexOf(b)
        if (i < 0 || j < 0 || i == j) return
        list[i] = b
        list[j] = a
        prefs?.edit()?.putString("order_$screen", list.joinToString(",") { it.name })?.apply()
        rev++
    }

    // ---- Ширина кнопки в «слотах» (режим редактора на экране) ----

    const val MAX_SPAN = 4

    /** Ширина блока в слотах: 0 — не задана (кнопка занимает своё место по умолчанию), 1..[MAX_SPAN] — растянута пользователем. */
    fun <E> span(screen: String, block: E): Int where E : Enum<E> =
        cached("s|$screen|${block.name}") {
            prefs?.getInt("span_${screen}_${block.name}", 0)?.coerceIn(0, MAX_SPAN) ?: 0
        }

    fun <E> setSpan(screen: String, block: E, v: Int) where E : Enum<E> {
        val e = prefs?.edit() ?: return
        if (v <= 0) e.remove("span_${screen}_${block.name}") else e.putInt("span_${screen}_${block.name}", v.coerceIn(1, MAX_SPAN))
        e.apply()
        rev++
    }

    /** Возвращает один блок к исходному виду: размер, ширина и отступы (порядок и скрытие не трогаются). */
    fun <E> resetBlock(screen: String, block: E) where E : Enum<E> {
        val n = block.name
        val e = prefs?.edit() ?: return
        listOf("zoom_${screen}_$n", "span_${screen}_$n", "gap_${screen}_$n", "gapb_${screen}_$n", "padh_${screen}_$n").forEach { e.remove(it) }
        e.apply()
        rev++
    }

    // ---- Масштаб и отступ блока ----

    fun <E> zoom(screen: String, block: E): Float where E : Enum<E> =
        cached("z|$screen|${block.name}") {
            prefs?.getFloat("zoom_${screen}_${block.name}", 1f)?.coerceIn(MIN_ZOOM, MAX_ZOOM) ?: 1f
        }

    fun <E> setZoom(screen: String, block: E, v: Float) where E : Enum<E> {
        prefs?.edit()?.putFloat("zoom_${screen}_${block.name}", v.coerceIn(MIN_ZOOM, MAX_ZOOM))?.apply()
        rev++
    }

    /** Дополнительный отступ сверху, dp. */
    fun <E> gap(screen: String, block: E): Int where E : Enum<E> =
        cached("g|$screen|${block.name}") {
            prefs?.getInt("gap_${screen}_${block.name}", 0)?.coerceIn(0, MAX_GAP) ?: 0
        }

    fun <E> setGap(screen: String, block: E, dp: Int) where E : Enum<E> {
        prefs?.edit()?.putInt("gap_${screen}_${block.name}", dp.coerceIn(0, MAX_GAP))?.apply()
        rev++
    }

    /** Дополнительный отступ снизу, dp. */
    fun <E> gapBottom(screen: String, block: E): Int where E : Enum<E> =
        cached("gb|$screen|${block.name}") {
            prefs?.getInt("gapb_${screen}_${block.name}", 0)?.coerceIn(0, MAX_GAP) ?: 0
        }

    fun <E> setGapBottom(screen: String, block: E, dp: Int) where E : Enum<E> {
        prefs?.edit()?.putInt("gapb_${screen}_${block.name}", dp.coerceIn(0, MAX_GAP))?.apply()
        rev++
    }

    /** Боковые поля блока (слева и справа), dp. */
    fun <E> padH(screen: String, block: E): Int where E : Enum<E> =
        cached("p|$screen|${block.name}") {
            prefs?.getInt("padh_${screen}_${block.name}", 0)?.coerceIn(0, MAX_GAP) ?: 0
        }

    fun <E> setPadH(screen: String, block: E, dp: Int) where E : Enum<E> {
        prefs?.edit()?.putInt("padh_${screen}_${block.name}", dp.coerceIn(0, MAX_GAP))?.apply()
        rev++
    }

    /** Возвращает порядок, видимость, масштаб и отступы одного экрана к значениям по умолчанию. */
    fun resetScreen(screen: String) {
        val e = prefs?.edit() ?: return
        prefs?.all?.keys?.filter {
            it == "order_$screen" || it == "hidden_$screen" || it.startsWith("zoom_${screen}_") || it.startsWith("gap_${screen}_") ||
                it.startsWith("gapb_${screen}_") || it.startsWith("padh_${screen}_") || it.startsWith("span_${screen}_")
        }?.forEach { e.remove(it) }
        e.apply()
        rev++
    }

    // ---- Отдельные значения ----

    private fun putFloat(key: String, v: Float) { prefs?.edit()?.putFloat(key, v)?.apply() }

    fun updateDockZoom(v: Float) { dockZoom = v.coerceIn(MIN_ZOOM, MAX_ZOOM); putFloat("dock_zoom", dockZoom) }
    fun updateDockIconZoom(v: Float) { dockIconZoom = v.coerceIn(MIN_ZOOM, MAX_ZOOM); putFloat("dock_icon_zoom", dockIconZoom) }
    fun updatePlayerBtnZoom(v: Float) { playerBtnZoom = v.coerceIn(MIN_ZOOM, MAX_ZOOM); putFloat("player_btn_zoom", playerBtnZoom) }
    fun updateProfileAvatar(dp: Int) { profileAvatarDp = dp.coerceIn(56, 160); prefs?.edit()?.putInt("profile_avatar_dp", profileAvatarDp)?.apply() }
    fun updateProfileColumns(n: Int) { profileColumns = n.coerceIn(1, 4); prefs?.edit()?.putInt("profile_columns", profileColumns)?.apply() }
    fun updateProfileShare(on: Boolean) { profileShare = on; prefs?.edit()?.putBoolean("profile_share", on)?.apply() }
    fun updateLinkModal(on: Boolean) { linkModal = on; prefs?.edit()?.putBoolean("link_modal", on)?.apply() }
    fun updateClipLinks(on: Boolean) { clipLinks = on; prefs?.edit()?.putBoolean("clip_links", on)?.apply() }
    fun updateEditMode(on: Boolean) {
        editMode = on
        if (!on) LayoutEdit.stop()
        prefs?.edit()?.putBoolean("edit_mode", on)?.apply()
    }

    /** Всё в исходное состояние (настройки внешнего вида из «Тема», «Формы» и т. п. не трогаются). */
    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        prefs?.let { load(it) }
        NavSettings.resetTabs()
        UiStyle.resetAll()
        GlassSettings.resetAll()
    }

    // ---- Выгрузка и загрузка ----

    /** Раскладка одной строкой JSON: её можно скопировать, отправить другу и вставить обратно. */
    fun exportJson(): String {
        val o = JSONObject()
        prefs?.all?.forEach { (k, v) -> o.put(k, v) }
        o.put("dock_tabs", NavSettings.tabs.joinToString(",") { it.name })
        UiStyle.exportInto(o)
        GlassSettings.exportInto(o)
        return o.toString()
    }

    fun importJson(text: String): Boolean {
        val o = runCatching { JSONObject(text.trim()) }.getOrNull() ?: return false
        val e = prefs?.edit() ?: return false
        e.clear()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k == "dock_tabs" || k.startsWith("style_") || k.startsWith("glass_")) continue
            val v = o.get(k)
            // JSON пишет 1.0 как «1»: масштабы читаем как Number и сохраняем как Float.
            if (v is Number && (k.startsWith("zoom_") || k.endsWith("_zoom"))) { e.putFloat(k, v.toFloat()); continue }
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is Number -> e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
            }
        }
        e.apply()
        prefs?.let { migrateHomeTiles(it); migrateHomeGreeting(it); load(it) }
        o.optString("dock_tabs").takeIf { it.isNotBlank() }?.let { NavSettings.setTabsFromString(it) }
        UiStyle.importFrom(o)
        GlassSettings.importFrom(o)
        return true
    }
}

/**
 * Обёртка блока: добавляет отступ сверху и масштабирует содержимое. Масштаб — это плотность экрана внутри блока, поэтому
 * вместе с ним растут значки, отступы и текст блока (ширина по-прежнему равна ширине экрана).
 */
@Composable
internal fun <E> LayoutBlockBox(screen: String, block: E, content: @Composable () -> Unit) where E : Enum<E> {
    val z = UiLayout.zoom(screen, block)
    val g = UiLayout.gap(screen, block)
    val gb = UiLayout.gapBottom(screen, block)
    val ph = UiLayout.padH(screen, block)
    Box(Modifier.padding(start = ph.dp, top = g.dp, end = ph.dp, bottom = gb.dp).layoutEdit(screen, block)) { ZoomBox(z, content) }
}

/** Масштаб содержимого [z] (1 — без изменений). */
@Composable
internal fun ZoomBox(z: Float, content: @Composable () -> Unit) {
    if (z == 1f) {
        content()
    } else {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * z, d.fontScale)) { content() }
    }
}

/** Значок для списка редактора по блоку. */
internal fun blockIcon(b: Any): Int = when (b) {
    HomeBlock.Wave -> R.drawable.ms_graphic_eq
    HomeBlock.Greeting -> R.drawable.ms_person
    HomeBlock.SearchTile -> R.drawable.ms_search
    HomeBlock.Playlists, HomeBlock.Mixes, PlaylistsBlock.Playlists, ProfileBlock.Playlists -> R.drawable.ms_library_music
    HomeBlock.Recognize -> R.drawable.ms_mic
    HomeBlock.Chart -> R.drawable.ms_leaderboard
    HomeBlock.New -> R.drawable.ms_new_releases
    HomeBlock.Podcasts -> R.drawable.ms_podcasts
    HomeBlock.HistoryTracks, HomeBlock.History -> R.drawable.ms_schedule
    HomeBlock.Liked, PlaylistsBlock.Liked, PlayerBtn.Like -> R.drawable.ms_favorite
    PlaylistsBlock.Local -> R.drawable.ms_folder
    PlaylistsBlock.Create -> R.drawable.ms_add
    PlaylistsBlock.Albums -> R.drawable.ms_music_note
    ProfileBlock.Header -> R.drawable.ms_person
    ProfileBlock.MyTracks -> R.drawable.ms_music_note
    ProfileBlock.MyAlbums -> R.drawable.ms_library_music
    ProfileBlock.Refresh -> R.drawable.ms_refresh
    PlayerBtn.Output -> R.drawable.ms_volume_up
    PlayerBtn.More -> R.drawable.ms_more_vert
    PlayerBtn.Repeat -> R.drawable.ms_repeat
    PlayerBtn.Lyrics -> R.drawable.ms_lyrics
    PlayerBtn.Mix -> R.drawable.ms_graphic_eq
    PlayerBtn.Gap -> R.drawable.ms_open_in_full
    PlayerBtn.Dislike -> R.drawable.ms_thumb_down_outline
    else -> extraBlockIcon(b)
}

/** Элемент ленты, обёрнутый в масштаб и отступ блока. [withGap] = false — для вторых и следующих элементов одного блока. */
internal fun <E> LazyListScope.blockItem(
    key: Any, screen: String, block: E, withGap: Boolean = true, content: @Composable () -> Unit,
) where E : Enum<E> {
    item(key = key) {
        val z = UiLayout.zoom(screen, block)
        val g = if (withGap) UiLayout.gap(screen, block) else 0
        val gb = UiLayout.gapBottom(screen, block)
        val ph = UiLayout.padH(screen, block)
        Box(Modifier.padding(start = ph.dp, top = g.dp, end = ph.dp, bottom = gb.dp).layoutEdit(screen, block)) { ZoomBox(z, content) }
    }
}
