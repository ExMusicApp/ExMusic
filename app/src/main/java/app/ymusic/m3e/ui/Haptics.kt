package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Сила виброотклика: множитель к амплитуде каждого «щелчка». «Средняя» — значение по умолчанию. */
enum class HapticStrength(val title: String, val scale: Float) {
    Light("Слабая", 0.55f),
    Medium("Средняя", 1f),
    Strong("Сильная", 1.65f),
}

/** Вид «щелчка»: лёгкий тик (ползунок, мелкие кнопки), обычный клик, плотный отклик (плей/пауза, «Моя волна»). */
enum class HapticKind(val durationMs: Long, val amplitude: Int) {
    Tick(7, 50),
    Click(12, 90),
    Heavy(22, 150),
}

/**
 * Виброотклик приложения. Можно выключить целиком и выбрать силу (Настройки → Вибрация).
 * Вызывается из любого места: [tick], [click], [heavy], [double] или [perform].
 */
object Haptics {
    private var prefs: SharedPreferences? = null
    private var vibrator: Vibrator? = null
    private var lastAt = 0L

    // --- Сила нажатия ---
    // «Обычное» нажатие каждого человека/экрана разное, поэтому давление сравнивается со скользящим средним по
    // последним касаниям: заметно сильнее среднего → сильный отклик, слабее → слабый, около среднего → средний.
    // Кроме того, очень быстрый «тычок» (меньше ~70 мс) даёт совсем слабую вибрацию.
    private var baseline = -1f
    private var peak = 0f
    private var downAt = 0L
    private var touchAt = 0L
    private var forceFactor = 1f

    // --- Реальное состояние касания окна приложения (см. onTouch / stillHeld) ---
    @Volatile private var fingerDown = false
    @Volatile private var lastDownAt = 0L

    /**
     * Продолжается ли нажатие, начавшееся в [pressStartedAt]. Если оно началось в окне приложения (касание принято
     * в [onTouch] незадолго до этого), а палец уже отпущен, — нет: тики не должны идти за «залипшим» нажатием.
     * Для нажатий вне окна приложения (диалоги, шторки) судить не по чему — считаем, что палец прижат.
     */
    internal fun stillHeld(pressStartedAt: Long): Boolean {
        val startedInWindow = pressStartedAt - lastDownAt in 0L..HELD_WINDOW_MS
        return !startedInWindow || fingerDown
    }

    /** Давно ли палец прижат (мс) — по касаниям окна приложения; для нажатий вне окна — 0 (считается от самого нажатия). */
    internal fun heldFor(pressStartedAt: Long): Long {
        val startedInWindow = pressStartedAt - lastDownAt in 0L..HELD_WINDOW_MS
        return if (startedInWindow) SystemClock.uptimeMillis() - lastDownAt else 0L
    }

    private const val HELD_WINDOW_MS = 1200L

    /** Включён ли виброотклик вообще. */
    var enabled by mutableStateOf(true)
        private set

    /** Выбранная сила. */
    var strength by mutableStateOf(HapticStrength.Medium)
        private set

    /** Учитывать силу и скорость нажатия: сильно жмёшь — сильнее вибрация, быстро тапнул — слабее. */
    var pressureSensitive by mutableStateOf(true)
        private set

    /** Тики при удержании кнопки (нарастающие). Выключено — только отклик в момент нажатия. */
    var holdTicks by mutableStateOf(true)
        private set

    /** Через сколько миллисекунд удержания начинаются тики. Больше — обычные нажатия и «тапы» никогда не дают тиков. */
    var holdDelayMs by mutableStateOf(DEFAULT_HOLD_DELAY_MS)
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        val p = app.getSharedPreferences("haptics", Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", true)
        strength = HapticStrength.entries.firstOrNull { it.name == p.getString("strength", null) } ?: HapticStrength.Medium
        pressureSensitive = p.getBoolean("pressure", true)
        holdTicks = p.getBoolean("hold_ticks", true)
        holdDelayMs = p.getInt("hold_delay_ms", DEFAULT_HOLD_DELAY_MS).coerceIn(MIN_HOLD_DELAY_MS, MAX_HOLD_DELAY_MS)
        vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull()?.takeIf { it.hasVibrator() }
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }

    fun updateStrength(s: HapticStrength) {
        strength = s
        prefs?.edit()?.putString("strength", s.name)?.apply()
    }

    fun updatePressureSensitive(on: Boolean) {
        pressureSensitive = on
        prefs?.edit()?.putBoolean("pressure", on)?.apply()
    }

    fun updateHoldTicks(on: Boolean) {
        holdTicks = on
        prefs?.edit()?.putBoolean("hold_ticks", on)?.apply()
    }

    fun updateHoldDelay(ms: Int) {
        holdDelayMs = ms.coerceIn(MIN_HOLD_DELAY_MS, MAX_HOLD_DELAY_MS)
        prefs?.edit()?.putInt("hold_delay_ms", holdDelayMs)?.apply()
    }

    /** Сюда передаются все касания окна (Activity.dispatchTouchEvent): отсюда берётся давление и длительность нажатия. */
    fun onTouch(e: MotionEvent) {
        // Прижат ли палец на самом деле: по этому признаку обрываются тики, если у кнопки «залипло» состояние нажатия
        // (например, главный поток был занят загрузкой, и отпускание обработалось позже). Нужно всегда, даже если
        // чувствительность к силе нажатия выключена.
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { fingerDown = true; lastDownAt = SystemClock.uptimeMillis() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> fingerDown = false
        }
        if (!pressureSensitive) return
        val p = e.pressure
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                peak = p
                downAt = e.eventTime
                touchAt = SystemClock.uptimeMillis()
                forceFactor = relativeForce(p)
            }
            MotionEvent.ACTION_MOVE -> {
                if (p > peak) peak = p
                touchAt = SystemClock.uptimeMillis()
                forceFactor = relativeForce(p)
            }
            MotionEvent.ACTION_UP -> {
                if (p > peak) peak = p
                touchAt = SystemClock.uptimeMillis()
                val ms = e.eventTime - downAt
                // Быстрый тычок: до 70 мс — самый слабый отклик, к 110 мс — обычный.
                val speed = when {
                    ms < 70 -> 0.6f
                    ms < 110 -> 0.6f + (ms - 70) / 40f * 0.4f
                    else -> 1f
                }
                forceFactor = relativeForce(peak) * speed
                if (peak > 0f) baseline = if (baseline <= 0f) peak else baseline * 0.92f + peak * 0.08f
            }
        }
    }

    /** Давление относительно «обычного» → множитель силы: 1.0 — обычное нажатие, ~0.35 — очень лёгкое, до ~1.8 — сильное. */
    private fun relativeForce(p: Float): Float {
        if (p <= 0f) return 1f
        val r = if (baseline > 0f) p / baseline else 1f
        return if (r < 1f) 0.35f + (r.coerceAtLeast(0.6f) - 0.6f) / 0.4f * 0.65f
        else 1f + (r.coerceAtMost(1.5f) - 1f) / 0.5f * 0.8f
    }

    /** Множитель текущего касания; если касание давно закончилось (или шло в другом окне, например в диалоге) — 1. */
    private fun force(): Float =
        if (!pressureSensitive || SystemClock.uptimeMillis() - touchAt > 600L) 1f else forceFactor

    /** Лёгкий отклик в момент касания кнопки (ещё до отпускания). */
    fun pressDown() = perform(HapticKind.Tick)

    /**
     * Тик при удержании: с каждым шагом [step] (1, 2, 3…) сильнее и чуть длиннее, пока не достигнет максимума.
     * Давление пальца здесь не учитывается — сила нарастает только от времени удержания.
     */
    fun holdTick(step: Int) {
        if (!enabled) return
        val v = vibrator ?: return
        val k = step.coerceIn(1, HOLD_MAX_STEP)
        // Раньше тик был 8 мс с амплитудой ~47 из 255 — на большинстве моторов такой импульс почти не ощущается,
        // особенно под маленькой кнопкой, где палец прижат не всей подушечкой. Теперь база плотнее (как у «Click»).
        val effect = runCatching {
            if (v.hasAmplitudeControl()) {
                val a = (HOLD_BASE_AMPLITUDE * strength.scale * (0.7f + 0.3f * k)).toInt().coerceIn(HOLD_MIN_AMPLITUDE, 255)
                VibrationEffect.createOneShot(HOLD_BASE_DURATION_MS + k, a)
            } else {
                VibrationEffect.createOneShot(((HOLD_BASE_DURATION_MS + k) * strength.scale * 1.5f).toLong().coerceAtLeast(8L), VibrationEffect.DEFAULT_AMPLITUDE)
            }
        }.getOrNull() ?: return
        lastAt = SystemClock.uptimeMillis()
        runCatching { v.vibrate(effect) }
    }

    private const val HOLD_MAX_STEP = 10
    private const val HOLD_BASE_AMPLITUDE = 90
    private const val HOLD_MIN_AMPLITUDE = 55
    private const val HOLD_BASE_DURATION_MS = 12L

    const val DEFAULT_HOLD_DELAY_MS = 450
    const val MIN_HOLD_DELAY_MS = 200
    const val MAX_HOLD_DELAY_MS = 1200

    fun tick() = perform(HapticKind.Tick)
    fun click() = perform(HapticKind.Click)
    fun heavy() = perform(HapticKind.Heavy)

    /** Короткая серия из двух импульсов — для двойного нажатия. */
    fun double() {
        if (!enabled) return
        val v = vibrator ?: return
        val k = HapticKind.Click
        val effect = runCatching {
            if (v.hasAmplitudeControl()) {
                val a = amp(k)
                VibrationEffect.createWaveform(longArrayOf(0, k.durationMs, 45, k.durationMs), intArrayOf(0, a, 0, a), -1)
            } else {
                val d = dur(k)
                VibrationEffect.createWaveform(longArrayOf(0, d, 45, d), -1)
            }
        }.getOrNull() ?: return
        lastAt = SystemClock.uptimeMillis()
        runCatching { v.vibrate(effect) }
    }

    fun perform(kind: HapticKind) {
        if (!enabled) return
        val v = vibrator ?: return
        // Не складываем подряд идущие импульсы (например, нажатие + клик) в одну «кашу».
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < 22L) return
        lastAt = now
        val effect = runCatching {
            if (v.hasAmplitudeControl()) VibrationEffect.createOneShot(kind.durationMs, amp(kind))
            else VibrationEffect.createOneShot(dur(kind), VibrationEffect.DEFAULT_AMPLITUDE)
        }.getOrNull() ?: return
        runCatching { v.vibrate(effect) }
    }

    private fun amp(k: HapticKind): Int = (k.amplitude * strength.scale * force()).toInt().coerceIn(1, 255)

    // Без управления амплитудой силу задаём длиной импульса.
    private fun dur(k: HapticKind): Long = (k.durationMs * strength.scale * force() * 1.5f).toLong().coerceAtLeast(5L)
}

/** Оборачивает обработчик нажатия: сначала виброотклик (если [kind] не null), затем само действие. */
internal fun withHaptic(kind: HapticKind?, onClick: () -> Unit): () -> Unit =
    if (kind == null) onClick else { { Haptics.perform(kind); onClick() } }

/**
 * Вибро при удержании: в момент нажатия — лёгкий отклик ([downTick]), затем после небольшой паузы идут тики,
 * каждый сильнее предыдущего и чаще (до предела), пока палец не отпущен.
 */
@Composable
internal fun HoldHaptics(pressed: Boolean, downTick: Boolean = true) {
    // В плеере тики при удержании выключены (LocalHoldHaptics = false): остаётся только отклик в момент нажатия.
    val holdOn = LocalHoldHaptics.current
    LaunchedEffect(pressed, holdOn) {
        if (!pressed) return@LaunchedEffect
        val startedAt = SystemClock.uptimeMillis()
        if (downTick) Haptics.pressDown()
        if (!Haptics.holdTicks || !holdOn) return@LaunchedEffect
        // Ждём, пока палец действительно продержится [holdDelayMs] — считая от самого касания, а не от момента,
        // когда Compose заметил нажатие: после «подвисания» интерфейса короткий тап иначе выглядел бы долгим.
        val delayMs = Haptics.holdDelayMs.toLong()
        delay((delayMs - Haptics.heldFor(startedAt)).coerceAtLeast(0L))
        var step = 1
        // Тики идут, только пока палец реально прижат: быстрый тап (в том числе «Моя волна», которая сразу уходит в
        // загрузку и отключает кнопку) тиков не даёт, даже если состояние нажатия у кнопки не успело сброситься.
        while (Haptics.stillHeld(startedAt)) {
            Haptics.holdTick(step)
            delay((130L - step * 8L).coerceAtLeast(60L))
            step++
        }
    }
}
