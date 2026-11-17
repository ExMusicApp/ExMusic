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

enum class FontMode(val title: String) {
    System("Системный"),
    GoogleSans("Google Sans"),
    File("Из файла"),
}

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

enum class AccentSlot(val key: String, val title: String, val hint: String) {
    Secondary("secondary", "Дополнительный", "Фильтры, чипы, второстепенные кнопки"),
    Tertiary("tertiary", "Контрастный", "Бейджи, выделения, свечение и мелкие акценты"),
    Surface("surface", "Фон и поверхности", "Оттенок фона, карточек и панелей"),
}

object ThemeSettings {
    private const val FONT_FILE_NAME = "custom_font.ttf"
    private const val MAX_FONT_BYTES = 30L * 1024 * 1024

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    var mode by mutableStateOf(ThemeMode.System)
        private set

    var accent by mutableStateOf<Int?>(null)
        private set

    var slotColors by mutableStateOf<Map<AccentSlot, Int>>(emptyMap())
        private set

    var customColors by mutableStateOf<List<Int>>(emptyList())
        private set

    var fontMode by mutableStateOf(FontMode.GoogleSans)
        private set

    var seekStyle by mutableStateOf(SeekBarStyle.Wavy)
        private set

    var amoled by mutableStateOf(false)
        private set

    var dockIsland by mutableStateOf(true)
        private set

    var dockAutoHide by mutableStateOf(false)
        private set

    var dockBlur by mutableStateOf(false)
        private set

    var pinWave by mutableStateOf(true)
        private set

    var glowPlayer by mutableStateOf(true)
        private set

    var uiScale by mutableStateOf(1f)
        private set

    var glowWave by mutableStateOf(true)
        private set

    var glowPlaylists by mutableStateOf(true)
        private set

    var waveTilt by mutableStateOf(true)
        private set

    var iconShape by mutableStateOf(IconShape.Default)
        private set

    var avatarShape by mutableStateOf(AvatarShape.Cookie12)
        private set

    var searchAutoKeyboard by mutableStateOf(false)
        private set

    var animSpeed by mutableStateOf(1f)
        private set

    var optimize by mutableStateOf(false)
        private set

    var animCheck by mutableStateOf(true)
        private set

    var animLoading by mutableStateOf(true)
        private set

    var animRemove by mutableStateOf(true)
        private set

    var animTabScroll by mutableStateOf(true)
        private set

    var tabAnimStyle by mutableStateOf(0)
        private set

    var motionBlur by mutableStateOf(true)
        private set

    var motionBlurStrength by mutableStateOf(0.5f)
        private set

    var animPageSwap by mutableStateOf(true)
        private set

    var animRecognition by mutableStateOf(true)
        private set

    var customFont by mutableStateOf<FontFamily?>(null)
        private set

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
        glowPlayer = p.getBoolean("glow_player", true)
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

    fun updateSlot(slot: AccentSlot, argb: Int?) {
        slotColors = if (argb == null) slotColors - slot else slotColors + (slot to argb)
        prefs?.edit()?.apply { if (argb == null) remove("slot_" + slot.key) else putInt("slot_" + slot.key, argb) }?.apply()
    }

    fun resetSlots() {
        slotColors = emptyMap()
        prefs?.edit()?.apply { AccentSlot.entries.forEach { remove("slot_" + it.key) } }?.apply()
    }

    fun addCustomColor(argb: Int) {
        customColors = (listOf(argb) + customColors.filter { it != argb }).take(12)
        prefs?.edit()?.putString("custom_colors", customColors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })?.apply()
    }

    fun removeCustomColor(argb: Int) {
        customColors = customColors.filter { it != argb }
        prefs?.edit()?.putString("custom_colors", customColors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() })?.apply()
    }

    fun updateUiScale(v: Float) {
        uiScale = ((v * 20f).roundToInt() / 20f).coerceIn(MIN_UI_SCALE, MAX_UI_SCALE)
        prefs?.edit()?.putFloat("ui_scale", uiScale)?.apply()
    }

    fun updateAnimSpeed(v: Float) {
        animSpeed = ((v * 20f).roundToInt() / 20f).coerceIn(MIN_ANIM_SPEED, MAX_ANIM_SPEED)
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

    fun updatePinWave(on: Boolean) {
        pinWave = on
        prefs?.edit()?.putBoolean("pin_wave", on)?.apply()
    }

    fun updateGlowPlayer(on: Boolean) {
        glowPlayer = on
        prefs?.edit()?.putBoolean("glow_player", on)?.apply()
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

object PlayerSettings {
    private var prefs: SharedPreferences? = null

    var miniSeek by mutableStateOf(false)
        private set

    var miniVolume by mutableStateOf(false)
        private set

    var highQuality by mutableStateOf(true)
        private set

    var preload by mutableStateOf(true)
        private set

    fun updatePreload(on: Boolean) {
        preload = on
        prefs?.edit()?.putBoolean("preload_next", on)?.apply()
    }

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

    var lyricsBlur by mutableStateOf(true)
        private set

    fun updateLyricsBlur(on: Boolean) {
        lyricsBlur = on
        prefs?.edit()?.putBoolean("lyrics_blur", on)?.apply()
    }

    var lyricsBlurFull by mutableStateOf(true)
        private set

    fun updateLyricsBlurFull(on: Boolean) {
        lyricsBlurFull = on
        prefs?.edit()?.putBoolean("lyrics_blur_full", on)?.apply()
    }

    var playerBgBlur by mutableStateOf(true)
        private set

    fun updatePlayerBgBlur(on: Boolean) {
        playerBgBlur = on
        prefs?.edit()?.putBoolean("player_bg_blur", on)?.apply()
    }

    var lyricsOff by mutableStateOf<Set<String>>(emptySet())
        private set

    fun setLyricsSource(id: String, on: Boolean, all: List<String>) {
        val next = if (on) lyricsOff - id else lyricsOff + id
        if (all.all { it in next }) return
        lyricsOff = next
        prefs?.edit()?.putStringSet("lyrics_off", next)?.apply()
    }

    fun onlyLyricsSource(id: String, all: List<String>) {
        lyricsOff = all.filter { it != id }.toSet()
        prefs?.edit()?.putStringSet("lyrics_off", lyricsOff)?.apply()
    }

    fun resetLyricsSources() {
        lyricsOff = emptySet()
        prefs?.edit()?.remove("lyrics_off")?.apply()
    }

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        prefs = p
        lyricsOff = p.getStringSet("lyrics_off", emptySet())?.toSet().orEmpty()
        fullVolume = p.getBoolean("full_volume", true)
        fullVolumeOpen = p.getBoolean("full_volume_open", true)
        miniSeek = p.getBoolean("mini_seek", false)
        miniVolume = p.getBoolean("mini_volume", false)
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
