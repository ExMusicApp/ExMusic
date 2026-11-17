package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ymusic.m3e.R
import org.json.JSONObject

internal enum class MiniBlock(override val title: String, override val hint: String) : LayoutBlock {
    Cover("Обложка", "Обложка играющего трека"),
    Info("Название и исполнитель", "Текст трека; занимает всё свободное место"),
    Prev("Предыдущий трек", "Кнопка «назад» (по умолчанию скрыта)"),
    Play("Играть / пауза", "Главная кнопка (с кольцом перемотки в круговом стиле)"),
    Next("Следующий трек", "Кнопка «вперёд» (по умолчанию скрыта)"),
    Like("Нравится", "Лайк трека (по умолчанию скрыта)"),
}

internal enum class PlayerPart(override val title: String, override val hint: String) : LayoutBlock {
    Cover("Обложка / текст песни", "Занимает всё свободное место"),
    Info("Название и исполнители", "Название трека, исполнители, альбом"),
    Seek("Полоса перемотки и время", "Линейная полоса со временем (в круговом стиле кольцо — у кнопки play)"),
    Controls("Кнопки управления", "Предыдущий, играть / пауза, следующий"),
    Volume("Громкость", "Ползунок громкости (включается в Настройки → Плеер)"),
    Buttons("Нижний ряд кнопок", "Звук, ещё, повтор, текст, дизлайк, лайк — порядок на вкладке «Кнопки плеера»"),
}

internal fun androidx.compose.ui.text.TextStyle.scaled(k: Float): androidx.compose.ui.text.TextStyle =
    if (k == 1f || fontSize == androidx.compose.ui.unit.TextUnit.Unspecified) this else copy(fontSize = fontSize * k)

internal fun extraBlockIcon(b: Any): Int = when (b) {
    MiniBlock.Cover -> R.drawable.ms_music_note
    MiniBlock.Info -> R.drawable.ms_lyrics
    MiniBlock.Prev -> R.drawable.ms_skip_previous
    MiniBlock.Play -> R.drawable.ms_play_arrow
    MiniBlock.Next -> R.drawable.ms_skip_next
    MiniBlock.Like -> R.drawable.ms_favorite
    PlayerPart.Cover -> R.drawable.ms_music_note
    PlayerPart.Info -> R.drawable.ms_lyrics
    PlayerPart.Seek -> R.drawable.ms_schedule
    PlayerPart.Controls -> R.drawable.ms_play_arrow
    PlayerPart.Volume -> R.drawable.ms_volume_up
    PlayerPart.Buttons -> R.drawable.ms_more_vert
    else -> R.drawable.ms_music_note
}

internal object UiStyle {
    private var prefs: SharedPreferences? = null

    var dockSide by mutableIntStateOf(0)
        private set

    var dockBottom by mutableIntStateOf(10)
        private set

    var dockAlign by mutableIntStateOf(0)
        private set

    var dockSpacing by mutableIntStateOf(4)
        private set

    var dockCorner by mutableIntStateOf(32)
        private set

    var dockPadH by mutableIntStateOf(14)
        private set

    var dockPadV by mutableIntStateOf(12)
        private set

    var dockLabelScale by mutableFloatStateOf(1f)
        private set

    var dockDetachLast by mutableStateOf(false)
        private set

    var dockDetachGap by mutableIntStateOf(8)
        private set

    var miniZoom by mutableFloatStateOf(1f)
        private set

    var miniCorner by mutableIntStateOf(-1)
        private set

    var miniSide by mutableIntStateOf(0)
        private set

    var miniBottom by mutableIntStateOf(0)
        private set

    var playerSide by mutableIntStateOf(20)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("ui_style", Context.MODE_PRIVATE)
        prefs = p
        load(p)
    }

    private fun load(p: SharedPreferences) {
        dockSide = p.getInt("dock_side", 0).coerceIn(0, 48)
        dockBottom = p.getInt("dock_bottom", 10).coerceIn(0, 64)
        dockAlign = p.getInt("dock_align", 0).coerceIn(0, 2)
        dockSpacing = p.getInt("dock_spacing", 4).coerceIn(0, 16)
        dockCorner = p.getInt("dock_corner", 32).coerceIn(8, 40)
        dockPadH = p.getInt("dock_pad_h", 14).coerceIn(6, 28)
        dockPadV = p.getInt("dock_pad_v", 12).coerceIn(4, 22)
        dockLabelScale = p.getFloat("dock_label_scale", 1f).coerceIn(0.7f, 1.4f)
        dockDetachLast = p.getBoolean("dock_detach", false)
        dockDetachGap = p.getInt("dock_detach_gap", 8).coerceIn(0, 32)
        miniZoom = p.getFloat("mini_zoom", 1f).coerceIn(0.7f, 1.4f)
        miniCorner = p.getInt("mini_corner", -1).coerceIn(-1, 40)
        miniSide = p.getInt("mini_side", 0).coerceIn(0, 32)
        miniBottom = p.getInt("mini_bottom", 0).coerceIn(0, 32)
        playerSide = p.getInt("player_side", 20).coerceIn(0, 48)
    }

    private fun putInt(key: String, v: Int) { prefs?.edit()?.putInt(key, v)?.apply() }
    private fun putFloat(key: String, v: Float) { prefs?.edit()?.putFloat(key, v)?.apply() }

    fun updateDockSide(v: Int) { dockSide = v.coerceIn(0, 48); putInt("dock_side", dockSide) }
    fun updateDockBottom(v: Int) { dockBottom = v.coerceIn(0, 64); putInt("dock_bottom", dockBottom) }
    fun updateDockAlign(v: Int) { dockAlign = v.coerceIn(0, 2); putInt("dock_align", dockAlign) }
    fun updateDockSpacing(v: Int) { dockSpacing = v.coerceIn(0, 16); putInt("dock_spacing", dockSpacing) }
    fun updateDockCorner(v: Int) { dockCorner = v.coerceIn(8, 40); putInt("dock_corner", dockCorner) }
    fun updateDockPadH(v: Int) { dockPadH = v.coerceIn(6, 28); putInt("dock_pad_h", dockPadH) }
    fun updateDockPadV(v: Int) { dockPadV = v.coerceIn(4, 22); putInt("dock_pad_v", dockPadV) }
    fun updateDockLabelScale(v: Float) { dockLabelScale = v.coerceIn(0.7f, 1.4f); putFloat("dock_label_scale", dockLabelScale) }
    fun updateDockDetachLast(on: Boolean) { dockDetachLast = on; prefs?.edit()?.putBoolean("dock_detach", on)?.apply() }
    fun updateDockDetachGap(v: Int) { dockDetachGap = v.coerceIn(0, 32); putInt("dock_detach_gap", dockDetachGap) }
    fun updateMiniZoom(v: Float) { miniZoom = v.coerceIn(0.7f, 1.4f); putFloat("mini_zoom", miniZoom) }
    fun updateMiniCorner(v: Int) { miniCorner = v.coerceIn(-1, 40); putInt("mini_corner", miniCorner) }
    fun updateMiniSide(v: Int) { miniSide = v.coerceIn(0, 32); putInt("mini_side", miniSide) }
    fun updateMiniBottom(v: Int) { miniBottom = v.coerceIn(0, 32); putInt("mini_bottom", miniBottom) }
    fun updatePlayerSide(v: Int) { playerSide = v.coerceIn(0, 48); putInt("player_side", playerSide) }

    fun resetDock() {
        val e = prefs?.edit() ?: return
        listOf("dock_side", "dock_bottom", "dock_align", "dock_spacing", "dock_corner", "dock_pad_h", "dock_pad_v",
            "dock_label_scale", "dock_detach", "dock_detach_gap").forEach { e.remove(it) }
        e.apply()
        prefs?.let { load(it) }
    }

    fun resetMini() {
        val e = prefs?.edit() ?: return
        listOf("mini_zoom", "mini_corner", "mini_side", "mini_bottom").forEach { e.remove(it) }
        e.apply()
        prefs?.let { load(it) }
    }

    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        prefs?.let { load(it) }
    }

    fun exportInto(o: JSONObject) {
        prefs?.all?.forEach { (k, v) -> o.put("style_$k", v) }
    }

    fun importFrom(o: JSONObject) {
        val e = prefs?.edit() ?: return
        e.clear()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (!k.startsWith("style_")) continue
            val name = k.removePrefix("style_")
            val v = o.get(k)
            if (name in FLOAT_KEYS && v is Number) { e.putFloat(name, v.toFloat()); continue }
            when (v) {
                is Boolean -> e.putBoolean(name, v)
                is Int -> e.putInt(name, v)
                is Double -> e.putFloat(name, v.toFloat())
                is Number -> e.putFloat(name, v.toFloat())
                is String -> e.putString(name, v)
            }
        }
        e.apply()
        prefs?.let { load(it) }
    }

    private val FLOAT_KEYS = setOf("dock_label_scale", "mini_zoom")
}
