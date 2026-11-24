package app.ymusic.m3e.ui

import kotlin.math.roundToInt
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

enum class ThemeMode(val title: String) {
    System("Системная"),
    Light("Светлая"),
    Dark("Тёмная"),
}

/** Шрифт приложения: системный, Google Sans (встроенный) или пользовательский файл. */
enum class FontMode(val title: String) {
    System("Системный"),
    GoogleSans("Google Sans"),
    File("Из файла"),
}

/**
 * Вид полосы прогресса/перемотки (варианты Material 3 Expressive):
 * линейная плоская разной толщины, линейная волнистая, круговая плоская и круговая волнистая.
 */
enum class SeekBarStyle(val title: String, val wavy: Boolean, val circular: Boolean, val thickness: Int) {
    Wavy("Линейная волнистая", wavy = true, circular = false, thickness = 4),
    WavyBold("Линейная волнистая, толстая", wavy = true, circular = false, thickness = 8),
    Flat("Линейная, тонкая (4 dp)", wavy = false, circular = false, thickness = 4),
    FlatMedium("Линейная, средняя (8 dp)", wavy = false, circular = false, thickness = 8),
    FlatLarge("Линейная, толстая (12 dp)", wavy = false, circular = false, thickness = 12),
    FlatXLarge("Линейная, очень толстая (16 dp)", wavy = false, circular = false, thickness = 16),
    CircleFlat("Круговая", wavy = false, circular = true, thickness = 6),
    CircleWavy("Круговая волнистая", wavy = true, circular = true, thickness = 6),
}

/** Акцентные цвета на выбор (ARGB). */
val AccentColors: List<Pair<String, Int>> = listOf(
    "Фиолетовый" to 0xFF6B4EFF.toInt(),
    "Синий" to 0xFF1E88E5.toInt(),
    "Бирюзовый" to 0xFF00ACC1.toInt(),
    "Зелёный" to 0xFF43A047.toInt(),
    "Лаймовый" to 0xFF9E9D24.toInt(),
    "Жёлтый" to 0xFFF9A825.toInt(),
    "Оранжевый" to 0xFFFB8C00.toInt(),
    "Красный" to 0xFFE53935.toInt(),
    "Розовый" to 0xFFD81B60.toInt(),
    "Серый" to 0xFF607D8B.toInt(),
)

/**
 * Что можно перекрасить отдельно от основного акцента (Внешний вид → Цвета → «Акценты»).
 * Основной цвет — это [ThemeSettings.accent]; здесь — остальные цвета приложения.
 */
enum class AccentSlot(val key: String, val title: String, val hint: String) {
    Secondary("secondary", "Дополнительный", "Фильтры, чипы, второстепенные кнопки"),
    Tertiary("tertiary", "Контрастный", "Бейджи, выделения, свечение и мелкие акценты"),
    Surface("surface", "Фон и поверхности", "Оттенок фона, карточек и панелей"),
}

/** Настройки оформления: тема (системная/светлая/тёмная), акцентный цвет и шрифт. Хранятся в SharedPreferences. */
object ThemeSettings {
    private const val FONT_FILE_NAME = "custom_font.ttf"
    private const val MAX_FONT_BYTES = 30L * 1024 * 1024

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    var mode by mutableStateOf(ThemeMode.System)
        private set

    /** ARGB выбранного акцента; null — цвета системы (Material You) либо стандартные. */
    var accent by mutableStateOf<Int?>(null)
        private set

    /** Свои цвета для частей схемы (ARGB): дополнительный, контрастный, оттенок фона. Нет записи — цвет считается от основного акцента. */
    var slotColors by mutableStateOf<Map<AccentSlot, Int>>(emptyMap())
        private set

    /** Сохранённые пользователем цвета (из окна выбора цвета) — показываются рядом с готовой палитрой. */
    var customColors by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Выбранный режим шрифта. */
    var fontMode by mutableStateOf(FontMode.GoogleSans)
        private set

    /** Вид полосы перемотки в плеере. */
    var seekStyle by mutableStateOf(SeekBarStyle.Wavy)
        private set

    /** AMOLED: в тёмной теме (своей или системной) фон и поверхности становятся чисто чёрными. */
    var amoled by mutableStateOf(false)
        private set

    /** Док-панель в виде «островка» (плавающая капсула). Выключено — обычная панель на всю ширину. */
    var dockIsland by mutableStateOf(true)
        private set

    /** Автоскрытие док-панели при прокрутке вниз. */
    var dockAutoHide by mutableStateOf(false)
        private set

    /** Размытие содержимого за док-панелью (панель прозрачная, под ней размытый экран). Выключено — сплошная панель. */
    var dockBlur by mutableStateOf(false)
        private set

    /** «Моя волна» закреплена сверху главного экрана и не исчезает при прокрутке. */
    var pinWave by mutableStateOf(true)
        private set

    /** Блок «Об артисте» под именем на странице исполнителя (если сервис отдаёт описание). */
    var artistBio by mutableStateOf(true)
        private set

    /** Свечение вокруг обложки в полном плеере, пока играет музыка. */
    var glowPlayer by mutableStateOf(true)
        private set

    /** Цвет свечения плеера: true — по цвету обложки трека, false — по цвету системы (основной цвет темы). */
    var glowFromCover by mutableStateOf(true)
        private set

    /** Размер интерфейса: множитель плотности (0.8–1.3). 1 — как задано в системе. Меняет и значки, и текст. */
    var uiScale by mutableStateOf(1f)
        private set

    /** Свечение кнопки «Моя волна», пока играет волна. */
    var glowWave by mutableStateOf(true)
        private set

    /** Свечение карточки играющего плейлиста и «Любимого». */
    var glowPlaylists by mutableStateOf(true)
        private set

    /** «Вода» на кнопке «Моя волна» наклоняется и плещется вслед за акселерометром телефона. */
    var waveTilt by mutableStateOf(true)
        private set

    /** Форма карточек, обложек, кнопок и панелей (как «Форма значков» в AOSP). */
    var iconShape by mutableStateOf(IconShape.Default)
        private set

    /** Форма аватарок исполнителей. */
    var avatarShape by mutableStateOf(AvatarShape.Cookie12)
        private set

    /** Показывать клавиатуру автоматически при входе во вкладку «Поиск». По умолчанию выключено. */
    var searchAutoKeyboard by mutableStateOf(false)
        private set

    /** Скорость анимаций переходов и жеста «назад»: 1 — как задумано, больше — быстрее, меньше — медленнее. */
    var animSpeed by mutableStateOf(1f)
        private set

    /**
     * «Оптимизация»: одним переключателем выключает все визуальные эффекты и анимации (свечение, размытие дока,
     * бегущие волны, переходы, пружинки кнопок). Остальные настройки не трогаются: выключили оптимизацию —
     * всё возвращается ровно так, как было настроено.
     */
    var optimize by mutableStateOf(false)
        private set

    /** Анимация «кружок → галочка» у отметок выбора. Включается отдельно от остальных. */
    var animCheck by mutableStateOf(true)
        private set

    /** Анимация «значок → кружок → три точки» (и обратно) на кнопке плеера, пока грузится трек. */
    var animLoading by mutableStateOf(true)
        private set

    /** Плавное пропадание элементов из списков (удалили трек, плейлист и т. д.). */
    var animRemove by mutableStateOf(true)
        private set

    /** Переход между вкладками проскроллом через все промежуточные страницы (например, «Главная» → «Поиск» и обратно). */
    var animTabScroll by mutableStateOf(true)
        private set

    /** Стиль анимации смены вкладок: 0 — по горизонтали, 1 — по вертикали, 2 — по положению панели (боковая — вертикально, нижняя — горизонтально). */
    var tabAnimStyle by mutableStateOf(0)
        private set

    /** Motion blur: размытие по направлению движения при листании вкладок и открытии плеера (Android 12+). */
    var motionBlur by mutableStateOf(true)
        private set

    /** Сила motion blur, 0..1. */
    var motionBlurStrength by mutableStateOf(0.5f)
        private set

    /** Страница исполнителя / альбома заменяет открытую (альбом → исполнитель) плавной сменой, а не наезжает поверх. */
    var animPageSwap by mutableStateOf(true)
        private set

    /** Плавная смена состояний окна распознавания музыки (слушаю → ищу → найдено). */
    var animRecognition by mutableStateOf(true)
        private set

    /** Загруженный из файла шрифт; null — файл не выбран или повреждён. */
    var customFont by mutableStateOf<FontFamily?>(null)
        private set

    /** Имя выбранного файла шрифта (для отображения). */
    var customFontName by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val p = app.getSharedPreferences("theme", Context.MODE_PRIVATE)
        prefs = p
        mode = ThemeMode.entries.firstOrNull { it.name == p.getString("mode", null) } ?: ThemeMode.System
        accent = if (p.contains("accent")) p.getInt("accent", 0) else null
        slotColors = AccentSlot.entries.mapNotNull { s -> if (p.contains("slot_" + s.key)) s to p.getInt("slot_" + s.key, 0) else null }.toMap()
        customColors = p.getString("custom_colors", null)?.split(',')?.mapNotNull { it.trim().toLongOrNull()?.toInt() }.orEmpty()
        fontMode = FontMode.entries.firstOrNull { it.name == p.getString("font_mode", null) } ?: FontMode.GoogleSans
        seekStyle = SeekBarStyle.entries.firstOrNull { it.name == p.getString("seek_style", null) } ?: SeekBarStyle.Wavy
        amoled = p.getBoolean("amoled", false)
        uiScale = p.getFloat("ui_scale", 1f).coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        dockIsland = p.getBoolean("dock_island", true)
        dockAutoHide = p.getBoolean("dock_auto_hide", false)
        dockBlur = p.getBoolean("dock_blur", false)
        pinWave = p.getBoolean("pin_wave", true)
        artistBio = p.getBoolean("artist_bio", true)
        glowPlayer = p.getBoolean("glow_player", true)
        glowFromCover = p.getBoolean("glow_from_cover", true)
        glowWave = p.getBoolean("glow_wave", true)
        glowPlaylists = p.getBoolean("glow_playlists", true)
        waveTilt = p.getBoolean("wave_tilt", true)
        iconShape = IconShape.entries.firstOrNull { it.name == p.getString("icon_shape", null) } ?: IconShape.Default
        avatarShape = AvatarShape.entries.firstOrNull { it.name == p.getString("avatar_shape", null) } ?: AvatarShape.Cookie12
        searchAutoKeyboard = p.getBoolean("search_auto_keyboard", false)
        animSpeed = p.getFloat("anim_speed", 1f).coerceIn(MIN_ANIM_SPEED, MAX_ANIM_SPEED)
        optimize = p.getBoolean("optimize", false)
        animCheck = p.getBoolean("anim_check", true)
        animLoading = p.getBoolean("anim_loading", true)
        animRemove = p.getBoolean("anim_remove", true)
        animTabScroll = p.getBoolean("anim_tab_scroll", true)
        tabAnimStyle = p.getInt("tab_anim_style", 0).coerceIn(0, 2)
        motionBlur = p.getBoolean("motion_blur", true)
        motionBlurStrength = p.getFloat("motion_blur_strength", 0.5f).coerceIn(0f, 1f)
        animPageSwap = p.getBoolean("anim_page_swap", true)
        animRecognition = p.getBoolean("anim_recognition", true)
        customFontName = p.getString("font_name", null)
        customFont = loadCustomFont(app)
        if (customFont == null) customFontName = null
    }

    fun updateMode(m: ThemeMode) {
        mode = m
        prefs?.edit()?.putString("mode", m.name)?.apply()
    }

    fun updateAccent(argb: Int?) {
        accent = argb
        prefs?.edit()?.apply { if (argb == null) remove("accent") else putInt("accent", argb) }?.apply()
    }

    /** Задаёт (или при null — сбрасывает) свой цвет для части схемы. */
    fun updateSlot(slot: AccentSlot, argb: Int?) {
        slotColors = if (argb == null) slotColors - slot else slotColors + (slot to argb)
        prefs?.edit()?.apply { if (argb == null) remove("slot_" + slot.key) else putInt("slot_" + slot.key, argb) }?.apply()
    }

    fun resetSlots() {
        slotColors = emptyMap()
        prefs?.edit()?.apply { AccentSlot.entries.forEach { remove("slot_" + it.key) } }?.apply()
    }

    /** Запоминает цвет из окна выбора цвета (до 12 штук, свежие первыми, без повторов). */
    fun addCustomColor(argb: Int) {
        customColors = (listOf(argb) + customColors.filter { it != argb }).take(12)
        prefs?.edit()?.putString("custom_colors", customColors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })?.apply()
    }

    fun removeCustomColor(argb: Int) {
        customColors = customColors.filter { it != argb }
        prefs?.edit()?.putString("custom_colors", customColors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })?.apply()
    }

    fun updateUiScale(v: Float) {
        uiScale = ((v * 20f).roundToInt() / 20f).coerceIn(MIN_UI_SCALE, MAX_UI_SCALE) // шаг 5%
        prefs?.edit()?.putFloat("ui_scale", uiScale)?.apply()
    }

    fun updateAnimSpeed(v: Float) {
        animSpeed = ((v * 20f).roundToInt() / 20f).coerceIn(MIN_ANIM_SPEED, MAX_ANIM_SPEED) // шаг 0,05×
        prefs?.edit()?.putFloat("anim_speed", animSpeed)?.apply()
    }

    fun updateAnimCheck(on: Boolean) {
        animCheck = on
        prefs?.edit()?.putBoolean("anim_check", on)?.apply()
    }

    fun updateAnimLoading(on: Boolean) {
        animLoading = on
        prefs?.edit()?.putBoolean("anim_loading", on)?.apply()
    }

    fun updateAnimRemove(on: Boolean) {
        animRemove = on
        prefs?.edit()?.putBoolean("anim_remove", on)?.apply()
    }

    fun updateTabAnimStyle(v: Int) {
        tabAnimStyle = v.coerceIn(0, 2)
        prefs?.edit()?.putInt("tab_anim_style", tabAnimStyle)?.apply()
    }

    fun updateMotionBlur(on: Boolean) {
        motionBlur = on
        prefs?.edit()?.putBoolean("motion_blur", on)?.apply()
    }

    fun updateMotionBlurStrength(v: Float) {
        motionBlurStrength = v.coerceIn(0f, 1f)
        prefs?.edit()?.putFloat("motion_blur_strength", motionBlurStrength)?.apply()
    }

    fun updateAnimTabScroll(on: Boolean) {
        animTabScroll = on
        prefs?.edit()?.putBoolean("anim_tab_scroll", on)?.apply()
    }

    fun updateAnimPageSwap(on: Boolean) {
        animPageSwap = on
        prefs?.edit()?.putBoolean("anim_page_swap", on)?.apply()
    }

    fun updateAnimRecognition(on: Boolean) {
        animRecognition = on
        prefs?.edit()?.putBoolean("anim_recognition", on)?.apply()
    }

    fun updateOptimize(on: Boolean) {
        optimize = on
        prefs?.edit()?.putBoolean("optimize", on)?.apply()
    }

    const val MIN_ANIM_SPEED = 0.5f
    const val MAX_ANIM_SPEED = 2.5f

    const val MIN_UI_SCALE = 0.8f
    const val MAX_UI_SCALE = 1.3f

    fun updateAmoled(on: Boolean) {
        amoled = on
        prefs?.edit()?.putBoolean("amoled", on)?.apply()
    }

    fun updateDockIsland(on: Boolean) {
        dockIsland = on
        prefs?.edit()?.putBoolean("dock_island", on)?.apply()
    }

    fun updateDockBlur(on: Boolean) {
        dockBlur = on
        prefs?.edit()?.putBoolean("dock_blur", on)?.apply()
    }

    fun updateDockAutoHide(on: Boolean) {
        dockAutoHide = on
        prefs?.edit()?.putBoolean("dock_auto_hide", on)?.apply()
    }

    fun updateIconShape(sh: IconShape) {
        iconShape = sh
        prefs?.edit()?.putString("icon_shape", sh.name)?.apply()
    }

    fun updateAvatarShape(sh: AvatarShape) {
        avatarShape = sh
        prefs?.edit()?.putString("avatar_shape", sh.name)?.apply()
    }

    fun updateSearchAutoKeyboard(on: Boolean) {
        searchAutoKeyboard = on
        prefs?.edit()?.putBoolean("search_auto_keyboard", on)?.apply()
    }

    fun updateArtistBio(on: Boolean) {
        artistBio = on
        prefs?.edit()?.putBoolean("artist_bio", on)?.apply()
    }

    fun updatePinWave(on: Boolean) {
        pinWave = on
        prefs?.edit()?.putBoolean("pin_wave", on)?.apply()
    }

    fun updateGlowPlayer(on: Boolean) {
        glowPlayer = on
        prefs?.edit()?.putBoolean("glow_player", on)?.apply()
    }

    fun updateGlowFromCover(on: Boolean) {
        glowFromCover = on
        prefs?.edit()?.putBoolean("glow_from_cover", on)?.apply()
    }

    fun updateGlowWave(on: Boolean) {
        glowWave = on
        prefs?.edit()?.putBoolean("glow_wave", on)?.apply()
    }

    fun updateWaveTilt(on: Boolean) {
        waveTilt = on
        prefs?.edit()?.putBoolean("wave_tilt", on)?.apply()
    }

    fun updateGlowPlaylists(on: Boolean) {
        glowPlaylists = on
        prefs?.edit()?.putBoolean("glow_playlists", on)?.apply()
    }

    fun updateSeekStyle(st: SeekBarStyle) {
        seekStyle = st
        prefs?.edit()?.putString("seek_style", st.name)?.apply()
    }

    fun updateFontMode(m: FontMode) {
        fontMode = m
        prefs?.edit()?.putString("font_mode", m.name)?.apply()
    }

    /**
     * Копирует выбранный файл шрифта (.ttf/.otf) во внутреннее хранилище приложения и применяет его.
     * Вызывать из фонового потока. Возвращает true, если шрифт валиден и принят.
     */
    fun importFont(uri: Uri): Boolean {
        val ctx = appContext ?: return false
        val target = File(ctx.filesDir, FONT_FILE_NAME)
        val tmp = File(ctx.filesDir, "$FONT_FILE_NAME.tmp")
        return try {
            var total = 0L
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FONT_BYTES) error("Файл слишком большой")
                        out.write(buf, 0, n)
                    }
                }
            } ?: return false
            // Проверяем, что файл действительно шрифт (иначе createFromFile бросит исключение).
            if (!isValidFont(tmp)) error("Не шрифт")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            val name = displayName(ctx, uri) ?: "Свой шрифт"
            val family = FontFamily(Font(target, FontWeight.Normal))
            customFont = family
            customFontName = name
            prefs?.edit()?.putString("font_name", name)?.apply()
            true
        } catch (_: Throwable) {
            tmp.delete()
            false
        }
    }

    /** На части версий Android createFromFile для битого файла молча возвращает DEFAULT вместо исключения. */
    private fun isValidFont(f: File): Boolean = try {
        val tf = Typeface.createFromFile(f)
        tf != null && tf !== Typeface.DEFAULT
    } catch (_: Throwable) {
        false
    }

    private fun loadCustomFont(ctx: Context): FontFamily? {
        val f = File(ctx.filesDir, FONT_FILE_NAME)
        if (!f.exists()) return null
        return try {
            if (!isValidFont(f)) return null
            FontFamily(Font(f, FontWeight.Normal))
        } catch (_: Throwable) {
            null
        }
    }

    private fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Throwable) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/')
}


/** Настройки плеера: перемотка и громкость в мини-плеере (по умолчанию обе выключены). */
object PlayerSettings {
    private var prefs: SharedPreferences? = null

    var miniSeek by mutableStateOf(false)
        private set

    var miniVolume by mutableStateOf(false)
        private set

    /** Цвет мини-плеера подстраивается под обложку играющего трека (включено по умолчанию). */
    var miniCoverColor by mutableStateOf(true)
        private set

    fun updateMiniCoverColor(on: Boolean) {
        miniCoverColor = on
        prefs?.edit()?.putBoolean("mini_cover_color", on)?.apply()
    }

    /** Высокое качество звука (lossless/hq, до 320 кбит/с). Выключено — экономный поток (nq, ~128 кбит/с). */
    var highQuality by mutableStateOf(true)
        private set

    /** Предзагрузка: следующие 3 трека очереди заранее получают ссылку и первые мегабайты звука. */
    var preload by mutableStateOf(true)
        private set

    fun updatePreload(on: Boolean) {
        preload = on
        prefs?.edit()?.putBoolean("preload_next", on)?.apply()
    }

    /** Микс треков: конец трека плавно переходит в начало следующего (как «Кроссфейд» в Spotify). */
    var crossfade by mutableStateOf(false)
        private set

    fun updateCrossfade(on: Boolean) {
        crossfade = on
        prefs?.edit()?.putBoolean("crossfade", on)?.apply()
    }

    /** Длительность микса, секунды (1–12). */
    var crossfadeSec by mutableStateOf(6)
        private set

    fun updateCrossfadeSec(sec: Int) {
        crossfadeSec = sec.coerceIn(1, 12)
        prefs?.edit()?.putInt("crossfade_sec", crossfadeSec)?.apply()
    }

    /** Ползунок громкости в полном плеере: показывать ли его вообще (настройки) и раскрыт ли он (значок слева от него). */
    var fullVolume by mutableStateOf(true)
        private set

    var fullVolumeOpen by mutableStateOf(true)
        private set

    fun updateFullVolume(on: Boolean) {
        fullVolume = on
        prefs?.edit()?.putBoolean("full_volume", on)?.apply()
    }

    fun updateFullVolumeOpen(on: Boolean) {
        fullVolumeOpen = on
        prefs?.edit()?.putBoolean("full_volume_open", on)?.apply()
    }

    /** Размытие строк текста песни: чем дальше строка от той, что поётся сейчас, тем сильнее она размыта (Android 12+). */
    var lyricsBlur by mutableStateOf(true)
        private set

    fun updateLyricsBlur(on: Boolean) {
        lyricsBlur = on
        prefs?.edit()?.putBoolean("lyrics_blur", on)?.apply()
    }

    /** То же размытие строк, но в тексте песни на весь экран (включается отдельно от размытия в плеере). */
    var lyricsBlurFull by mutableStateOf(true)
        private set

    fun updateLyricsBlurFull(on: Boolean) {
        lyricsBlurFull = on
        prefs?.edit()?.putBoolean("lyrics_blur_full", on)?.apply()
    }

    /** Размытая обложка трека на фоне полного плеера. */
    var playerBgBlur by mutableStateOf(true)
        private set

    fun updatePlayerBgBlur(on: Boolean) {
        playerBgBlur = on
        prefs?.edit()?.putBoolean("player_bg_blur", on)?.apply()
    }

    /** Отключённые источники текстов (id). Пусто — текст ищется во всех источниках. */
    var lyricsOff by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Включить/выключить источник текстов; хотя бы один из [all] всегда остаётся включённым. */
    fun setLyricsSource(id: String, on: Boolean, all: List<String>) {
        val next = if (on) lyricsOff - id else lyricsOff + id
        if (all.all { it in next }) return
        lyricsOff = next
        prefs?.edit()?.putStringSet("lyrics_off", next)?.apply()
    }

    /** Только один источник: остальные выключаются. */
    fun onlyLyricsSource(id: String, all: List<String>) {
        lyricsOff = all.filter { it != id }.toSet()
        prefs?.edit()?.putStringSet("lyrics_off", lyricsOff)?.apply()
    }

    fun resetLyricsSources() {
        lyricsOff = emptySet()
        prefs?.edit()?.remove("lyrics_off")?.apply()
    }

    /** Спрашивать подтверждение, когда текст выбран вручную через поиск. */
    var lyricsConfirm by mutableStateOf(true)
        private set

    fun updateLyricsConfirm(on: Boolean) {
        lyricsConfirm = on
        prefs?.edit()?.putBoolean("lyrics_confirm", on)?.apply()
    }

    /** Привязка выбранного вручную текста к треку (в окне подтверждения — галочка; без окна привязывается сразу). */
    var lyricsPin by mutableStateOf(true)
        private set

    fun updateLyricsPin(on: Boolean) {
        lyricsPin = on
        prefs?.edit()?.putBoolean("lyrics_pin", on)?.apply()
    }

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        prefs = p
        crossfade = p.getBoolean("crossfade", false)
        crossfadeSec = p.getInt("crossfade_sec", 6).coerceIn(1, 12)
        lyricsConfirm = p.getBoolean("lyrics_confirm", true)
        lyricsPin = p.getBoolean("lyrics_pin", true)
        lyricsOff = p.getStringSet("lyrics_off", emptySet())?.toSet().orEmpty()
        fullVolume = p.getBoolean("full_volume", true)
        fullVolumeOpen = p.getBoolean("full_volume_open", true)
        miniSeek = p.getBoolean("mini_seek", false)
        miniVolume = p.getBoolean("mini_volume", false)
        miniCoverColor = p.getBoolean("mini_cover_color", true)
        highQuality = p.getBoolean("high_quality", true)
        preload = p.getBoolean("preload_next", true)
        lyricsBlur = p.getBoolean("lyrics_blur", true)
        lyricsBlurFull = p.getBoolean("lyrics_blur_full", true)
        playerBgBlur = p.getBoolean("player_bg_blur", true)
    }

    fun updateHighQuality(on: Boolean) {
        highQuality = on
        prefs?.edit()?.putBoolean("high_quality", on)?.apply()
    }

    fun updateMiniSeek(on: Boolean) {
        miniSeek = on
        prefs?.edit()?.putBoolean("mini_seek", on)?.apply()
    }

    fun updateMiniVolume(on: Boolean) {
        miniVolume = on
        prefs?.edit()?.putBoolean("mini_volume", on)?.apply()
    }
}
