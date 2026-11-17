package app.ymusic.m3e.player

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

data class EqPreset(val id: String, val title: String, val gains: List<Float>?)

object EqPresets {
    val Flat = EqPreset("flat", "Обычный", listOf(0f, 0f, 0f, 0f, 0f))
    val Bass = EqPreset("bass", "Усиление басов", listOf(6f, 4f, 1f, 0f, 0f))
    val Treble = EqPreset("treble", "Усиление высоких", listOf(0f, 0f, 0f, 3f, 6f))
    val Rock = EqPreset("rock", "Рок", listOf(5f, 3f, -1f, 3f, 5f))
    val Pop = EqPreset("pop", "Поп", listOf(-1f, 2f, 4f, 2f, -1f))
    val Jazz = EqPreset("jazz", "Джаз", listOf(3f, 1f, -1f, 2f, 4f))
    val Classical = EqPreset("classical", "Классика", listOf(3f, 2f, 0f, 2f, 3f))
    val Vocal = EqPreset("vocal", "Вокал", listOf(-2f, 0f, 3f, 3f, 1f))
    val Electronic = EqPreset("electronic", "Электроника", listOf(5f, 3f, 0f, 2f, 5f))
    val HipHop = EqPreset("hiphop", "Хип-хоп", listOf(5f, 4f, 0f, 1f, 3f))
    val Custom = EqPreset("custom", "Свой", null)

    val all = listOf(Flat, Bass, Treble, Rock, Pop, Jazz, Classical, Vocal, Electronic, HipHop, Custom)

    fun byId(id: String?): EqPreset = all.firstOrNull { it.id == id } ?: Flat
}

data class EqState(
    val enabled: Boolean = false,
    val presetId: String = EqPresets.Flat.id,
    val bandsDb: List<Float> = List(5) { 0f },
    val freqsHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val minDb: Float = -15f,
    val maxDb: Float = 15f,
)

class EqualizerController(context: Context, private val player: ExoPlayer) {
    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    private var effect: Equalizer? = null

    private val _state = MutableStateFlow(load())
    val state: StateFlow<EqState> = _state

    init {
        attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = attach(audioSessionId)
        })
    }

    fun setEnabled(on: Boolean) {
        _state.update { it.copy(enabled = on) }
        save(); apply()
    }

    fun setPreset(id: String) {
        val preset = EqPresets.byId(id)
        val gains = preset.gains
        _state.update { s ->
            if (gains == null) s.copy(presetId = preset.id)
            else s.copy(
                presetId = preset.id,
                bandsDb = resample(gains, s.freqsHz.size).map { it.coerceIn(s.minDb, s.maxDb) },
            )
        }
        save(); apply()
    }

    fun setBand(index: Int, db: Float) {
        _state.update { s ->
            if (index !in s.bandsDb.indices) s
            else s.copy(
                presetId = EqPresets.Custom.id,
                bandsDb = s.bandsDb.toMutableList().also { it[index] = db.coerceIn(s.minDb, s.maxDb) },
            )
        }
        save(); apply()
    }

    private fun attach(sessionId: Int) {
        runCatching { effect?.release() }
        effect = null
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
            effect = runCatching { Equalizer(0, sessionId) }.getOrNull()
        }
        val e = effect
        if (e != null) {
            runCatching {
                val n = e.numberOfBands.toInt()
                val range = e.bandLevelRange
                val freqs = List(n) { e.getCenterFreq(it.toShort()) / 1000 }
                val min = range[0] / 100f
                val max = range[1] / 100f
                _state.update { s ->
                    s.copy(
                        freqsHz = freqs,
                        minDb = min,
                        maxDb = max,
                        bandsDb = resample(s.bandsDb, n).map { it.coerceIn(min, max) },
                    )
                }
            }
        }
        apply()
    }

    private fun apply() {
        val e = effect ?: return
        val s = _state.value
        runCatching {
            val range = e.bandLevelRange
            val count = e.numberOfBands.toInt()
            s.bandsDb.forEachIndexed { i, db ->
                if (i < count) {
                    val mb = (db * 100f).roundToInt().coerceIn(range[0].toInt(), range[1].toInt())
                    e.setBandLevel(i.toShort(), mb.toShort())
                }
            }
            e.setEnabled(s.enabled)
        }
    }

    private fun load(): EqState {
        val preset = EqPresets.byId(prefs.getString("preset", null))
        val saved = prefs.getString("bands", null)
            ?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.isNotEmpty() }
        val bands = saved ?: resample(preset.gains ?: List(5) { 0f }, 5)
        return EqState(
            enabled = prefs.getBoolean("enabled", false),
            presetId = preset.id,
            bandsDb = bands,
            freqsHz = EqState().freqsHz.let { f -> if (bands.size == f.size) f else List(bands.size) { 0 } },
        )
    }

    private fun save() {
        val s = _state.value
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putString("preset", s.presetId)
            .putString("bands", s.bandsDb.joinToString(","))
            .apply()
    }

    private fun resample(src: List<Float>, n: Int): List<Float> {
        if (n <= 0) return emptyList()
        if (src.isEmpty()) return List(n) { 0f }
        if (src.size == n) return src
        if (src.size == 1) return List(n) { src[0] }
        return List(n) { i ->
            val pos = if (n == 1) 0f else i * (src.size - 1).toFloat() / (n - 1)
            val lo = pos.toInt().coerceIn(0, src.size - 1)
            val hi = (lo + 1).coerceAtMost(src.size - 1)
            val t = pos - lo
            src[lo] * (1 - t) + src[hi] * t
        }
    }
}
