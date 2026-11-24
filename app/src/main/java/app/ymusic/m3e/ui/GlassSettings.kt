package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * «Матовое стекло» (размытая прозрачность): полупрозрачные панели, за которыми экран размыт.
 * Работает для док-панели, мини-плеера и фона полного плеера. По умолчанию ВСЁ выключено.
 * У каждой панели своя сила размытия и своя плотность подложки.
 * Настоящее размытие — на Android 12+; на более старых подложка почти непрозрачная.
 * Хранится в SharedPreferences «glass».
 */
internal object GlassSettings {
    private var prefs: SharedPreferences? = null

    const val MAX_BLUR = 80

    // ---- Мини-плеер ----
    var miniGlass by mutableStateOf(false)
        private set
    var miniBlurDp by mutableIntStateOf(24)
        private set

    /** Плотность подложки мини-плеера: 1 — сплошная, меньше — прозрачнее. */
    var miniAlpha by mutableFloatStateOf(0.55f)
        private set

    // ---- Фон полного плеера ----
    var playerGlass by mutableStateOf(false)
        private set
    var playerBlurDp by mutableIntStateOf(40)
        private set
    var playerAlpha by mutableFloatStateOf(0.60f)
        private set

    // ---- Док-панель (включается прежним переключателем ThemeSettings.dockBlur) ----
    var dockBlurDp by mutableIntStateOf(28)
        private set

    /** Плотность подложки под доком; отрицательное значение — «как раньше» (0,40 у островка и 0,55 у панели). */
    var dockTint by mutableFloatStateOf(-1f)
        private set

    /** Плотность самой капсулы-островка: 1 — сплошная (по умолчанию), меньше — прозрачная капсула. */
    var dockCapsuleAlpha by mutableFloatStateOf(1f)
        private set

    /** Матовое стекло островка: капсула полупрозрачная, за ней размытый экран. */
    var islandGlass by mutableStateOf(false)
        private set
    var islandBlurDp by mutableIntStateOf(24)
        private set
    var islandAlpha by mutableFloatStateOf(0.55f)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("glass", Context.MODE_PRIVATE)
        prefs = p
        miniGlass = p.getBoolean("mini_glass", false)
        miniBlurDp = p.getInt("mini_blur", 24).coerceIn(1, MAX_BLUR)
        miniAlpha = p.getFloat("mini_alpha", 0.55f).coerceIn(0.1f, 1f)
        playerGlass = p.getBoolean("player_glass", false)
        playerBlurDp = p.getInt("player_blur", 40).coerceIn(1, MAX_BLUR)
        playerAlpha = p.getFloat("player_alpha", 0.60f).coerceIn(0.1f, 1f)
        dockBlurDp = p.getInt("dock_blur_dp", 28).coerceIn(1, MAX_BLUR)
        dockTint = p.getFloat("dock_tint", -1f).let { if (it < 0f) -1f else it.coerceIn(0f, 1f) }
        dockCapsuleAlpha = p.getFloat("dock_capsule_alpha", 1f).coerceIn(0.2f, 1f)
        islandGlass = p.getBoolean("island_glass", false)
        islandBlurDp = p.getInt("island_blur", 24).coerceIn(1, MAX_BLUR)
        islandAlpha = p.getFloat("island_alpha", 0.55f).coerceIn(0.1f, 1f)
    }

    /** Нужно ли записывать экран в слой для размытия (мини-плеер или островок со стеклом). */
    val anyBackdropNeeded: Boolean
        get() = !ThemeSettings.optimize && (miniGlass || (islandGlass && ThemeSettings.dockIsland))

    fun dockTintAlpha(island: Boolean): Float = if (dockTint >= 0f) dockTint else if (island) 0.40f else 0.55f

    /** На Android 8–11 размытия слоя нет, поэтому прозрачность ограничена: панель остаётся читаемой. */
    fun solidAlpha(a: Float): Float = if (Build.VERSION.SDK_INT >= 31) a else maxOf(a, 0.92f)

    private fun ed(block: SharedPreferences.Editor.() -> Unit) { prefs?.edit()?.apply(block)?.apply() }

    fun updateMiniGlass(on: Boolean) { miniGlass = on; ed { putBoolean("mini_glass", on) } }
    fun updateMiniBlur(dp: Int) { miniBlurDp = dp.coerceIn(1, MAX_BLUR); ed { putInt("mini_blur", miniBlurDp) } }
    fun updateMiniAlpha(v: Float) { miniAlpha = v.coerceIn(0.1f, 1f); ed { putFloat("mini_alpha", miniAlpha) } }
    fun updatePlayerGlass(on: Boolean) { playerGlass = on; ed { putBoolean("player_glass", on) } }
    fun updatePlayerBlur(dp: Int) { playerBlurDp = dp.coerceIn(1, MAX_BLUR); ed { putInt("player_blur", playerBlurDp) } }
    fun updatePlayerAlpha(v: Float) { playerAlpha = v.coerceIn(0.1f, 1f); ed { putFloat("player_alpha", playerAlpha) } }
    fun updateDockBlurDp(dp: Int) { dockBlurDp = dp.coerceIn(1, MAX_BLUR); ed { putInt("dock_blur_dp", dockBlurDp) } }
    fun updateIslandGlass(on: Boolean) { islandGlass = on; ed { putBoolean("island_glass", on) } }
    fun updateIslandBlur(dp: Int) { islandBlurDp = dp.coerceIn(1, MAX_BLUR); ed { putInt("island_blur", islandBlurDp) } }
    fun updateIslandAlpha(v: Float) { islandAlpha = v.coerceIn(0.1f, 1f); ed { putFloat("island_alpha", islandAlpha) } }
    fun updateDockTint(v: Float) { dockTint = v.coerceIn(0f, 1f); ed { putFloat("dock_tint", dockTint) } }
    fun updateDockCapsuleAlpha(v: Float) { dockCapsuleAlpha = v.coerceIn(0.2f, 1f); ed { putFloat("dock_capsule_alpha", dockCapsuleAlpha) } }

    /** Всё стекло в исходное состояние: прозрачности выключены, размытие дока выключено, силы размытия — по умолчанию. */
    fun resetAll() {
        prefs?.edit()?.clear()?.apply()
        prefs?.let { init2(it) }
        ThemeSettings.updateDockBlur(false)
    }

    private fun init2(p: SharedPreferences) {
        miniGlass = p.getBoolean("mini_glass", false)
        miniBlurDp = p.getInt("mini_blur", 24)
        miniAlpha = p.getFloat("mini_alpha", 0.55f)
        playerGlass = p.getBoolean("player_glass", false)
        playerBlurDp = p.getInt("player_blur", 40)
        playerAlpha = p.getFloat("player_alpha", 0.60f)
        dockBlurDp = p.getInt("dock_blur_dp", 28)
        dockTint = p.getFloat("dock_tint", -1f)
        dockCapsuleAlpha = p.getFloat("dock_capsule_alpha", 1f)
        islandGlass = p.getBoolean("island_glass", false)
        islandBlurDp = p.getInt("island_blur", 24)
        islandAlpha = p.getFloat("island_alpha", 0.55f)
    }

    // ---- Выгрузка / загрузка в составе «Копии раскладки» ----

    fun exportInto(o: JSONObject) {
        o.put("glass_dock_on", ThemeSettings.dockBlur)
        o.put("glass_dock_blur", dockBlurDp)
        o.put("glass_dock_tint", dockTint.toDouble())
        o.put("glass_dock_capsule", dockCapsuleAlpha.toDouble())
        o.put("glass_island_on", islandGlass)
        o.put("glass_island_blur", islandBlurDp)
        o.put("glass_island_alpha", islandAlpha.toDouble())
        o.put("glass_mini_on", miniGlass)
        o.put("glass_mini_blur", miniBlurDp)
        o.put("glass_mini_alpha", miniAlpha.toDouble())
        o.put("glass_player_on", playerGlass)
        o.put("glass_player_blur", playerBlurDp)
        o.put("glass_player_alpha", playerAlpha.toDouble())
    }

    fun importFrom(o: JSONObject) {
        if (o.keys().asSequence().none { it.startsWith("glass_") }) return
        ThemeSettings.updateDockBlur(o.optBoolean("glass_dock_on", false))
        updateDockBlurDp(o.optInt("glass_dock_blur", 28))
        val t = o.optDouble("glass_dock_tint", -1.0).toFloat()
        if (t < 0f) { dockTint = -1f; ed { putFloat("dock_tint", -1f) } } else updateDockTint(t)
        updateDockCapsuleAlpha(o.optDouble("glass_dock_capsule", 1.0).toFloat())
        updateIslandGlass(o.optBoolean("glass_island_on", false))
        updateIslandBlur(o.optInt("glass_island_blur", 24))
        updateIslandAlpha(o.optDouble("glass_island_alpha", 0.55).toFloat())
        updateMiniGlass(o.optBoolean("glass_mini_on", false))
        updateMiniBlur(o.optInt("glass_mini_blur", 24))
        updateMiniAlpha(o.optDouble("glass_mini_alpha", 0.55).toFloat())
        updatePlayerGlass(o.optBoolean("glass_player_on", false))
        updatePlayerBlur(o.optInt("glass_player_blur", 40))
        updatePlayerAlpha(o.optDouble("glass_player_alpha", 0.60).toFloat())
    }
}

/** Общее состояние размытия, выставляемое корнем приложения (см. AppRoot): панели берут из него размытый экран. */
internal object GlassHost {
    var backdrop: DockBackdrop? = null

}

/**
 * Матовое стекло полного плеера: приложение под шторкой размывается на месте (RenderEffect на его собственном слое).
 * Сила размытия пропорциональна тому, насколько шторка выехала, поэтому при выезде и сворачивании плеера размытие плавно
 * нарастает и спадает. В покое (плеер закрыт) эффекта нет совсем — слой не создаётся и ничего не стоит.
 * Нужен Android 12+; на более старых модификатор ничего не делает (там подложка плеера почти непрозрачная).
 */
internal fun Modifier.playerGlassBackdrop(enabled: Boolean, progress: () -> Float): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < 31) return this
    return this.graphicsLayer {
        val k = progress().coerceIn(0f, 1f)
        val r = GlassSettings.playerBlurDp.dp.toPx() * k
        renderEffect = if (r >= 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
    }
}

/** Лёгкая «молочная» вуаль и светлая кромка поверх размытого приложения: стекло выглядит матовым, а не просто мутным. */
internal fun Modifier.glassFrost(enabled: Boolean, shape: Shape): Modifier {
    if (!enabled) return this
    return this.drawBehind {
        val path = when (val out = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Generic -> out.path
            is Outline.Rounded -> Path().apply { addRoundRect(out.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(out.rect) }
        }
        clipPath(path) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.13f),
                    1f to Color.White.copy(alpha = 0.05f),
                ),
            )
        }
        drawPath(path, Color.White.copy(alpha = 0.16f), style = Stroke(width = 1.dp.toPx()))
    }
}

/** Запоминает положение элемента в окне — нужно [glassBehind], чтобы подставить под него нужный кусок экрана. */
internal fun Modifier.glassOriginProbe(state: MutableState<Offset>): Modifier =
    this.onGloballyPositioned { state.value = it.positionInRoot() }

/**
 * Размытый экран позади элемента (матовое стекло). Рисуется ПОЗАДИ содержимого, обрезается по [shape]; цвет подложки
 * элемент должен задать сам — полупрозрачным. [origin] — положение элемента в окне (см. [glassOriginProbe]).
 * Если [enabled] = false, нет подложки-источника или Android старше 12 — модификатор ничего не добавляет.
 * [source] — откуда брать экран (по умолчанию — область экрана без дока). [base] — сплошной цвет под размытием: без него
 * прозрачные места слоя пропускают сквозь стекло резкий интерфейс (видно кнопки и текст позади плеера).
 */
@Composable
internal fun Modifier.glassBehind(
    enabled: Boolean,
    blurDp: Int,
    shape: Shape,
    source: DockBackdrop? = null,
    base: Color = Color.Unspecified,
    origin: () -> Offset,
): Modifier {
    val b = source ?: GlassHost.backdrop
    val layer = rememberGraphicsLayer()
    val applied = remember { floatArrayOf(-1f) }
    val radiusPx = with(LocalDensity.current) { blurDp.coerceAtLeast(1).dp.toPx() }
    if (!enabled || b == null || Build.VERSION.SDK_INT < 31) return this
    return this.drawBehind {
        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        if (w <= 0 || h <= 0) return@drawBehind
        if (applied[0] != radiusPx) {
            layer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            applied[0] = radiusPx
        }
        val o = origin()
        // Слой чуть больше элемента: размытие на краях подмешивает соседний экран, а не растягивает край.
        val m = (radiusPx * 2f).roundToInt()
        layer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = IntSize(w + 2 * m, h + 2 * m),
        ) {
            translate(b.contentOrigin.x - o.x + m, b.contentOrigin.y - o.y + m) { drawLayer(b.content) }
        }
        val path = when (val out = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Generic -> out.path
            is Outline.Rounded -> Path().apply { addRoundRect(out.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(out.rect) }
        }
        clipPath(path) {
            if (base != Color.Unspecified) drawRect(base)
            translate(-m.toFloat(), -m.toFloat()) { drawLayer(layer) }
            // Матовость: лёгкая светлая вуаль с мягким градиентом поверх размытия — стекло становится «молочным», а не просто мутным.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.13f),
                    1f to Color.White.copy(alpha = 0.05f),
                ),
            )
        }
        // Тонкая светлая кромка, как у матового стекла.
        drawPath(path, Color.White.copy(alpha = 0.16f), style = Stroke(width = 1.dp.toPx()))
    }
}

// ---------- Настройки на экране ----------

/** Ползунок с подписью. Значение — в долях (0..1) или в единицах [range]. */
@Composable
internal fun StyleSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range, steps = steps)
    }
}

private fun pct(v: Float): Int = (v * 100f).roundToInt()
private fun step5(v: Float): Float = (v * 20f).roundToInt() / 20f

/**
 * «Матовое стекло»: размытая прозрачность дока, мини-плеера и фона полного плеера. Всё выключено по умолчанию.
 * Общий вид для «Настройки → Внешний вид» и вкладки «Стекло» редактора интерфейса.
 */
@Composable
internal fun GlassSettingsSection() {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Размытая прозрачность: панель становится полупрозрачной, а экран за ней — размытым, как матовое стекло. " +
                "Каждая панель включается отдельно, и у каждой своя сила размытия и плотность. По умолчанию всё выключено. " +
                "Настоящее размытие — на Android 12 и новее; на старых панель остаётся почти непрозрачной. " +
                "Общая «Оптимизация» отключает стекло целиком.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Док-панель
        Text("Док-панель", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
        GlowSwitchRow(
            "Размытие под док-панелью",
            "Экран уходит под панель и размывается за ней",
            ThemeSettings.dockBlur,
        ) { ThemeSettings.updateDockBlur(it) }
        StyleSlider("Сила размытия: ${GlassSettings.dockBlurDp} dp", GlassSettings.dockBlurDp.toFloat(), 1f..GlassSettings.MAX_BLUR.toFloat()) {
            GlassSettings.updateDockBlurDp(it.roundToInt())
        }
        val tintShown = GlassSettings.dockTintAlpha(ThemeSettings.dockIsland)
        StyleSlider("Плотность подложки: ${pct(tintShown)}%", tintShown, 0f..1f, 19) { GlassSettings.updateDockTint(step5(it)) }
        StyleSlider(
            "Прозрачность капсулы-островка: ${pct(GlassSettings.dockCapsuleAlpha)}% непрозрачности",
            GlassSettings.dockCapsuleAlpha, 0.2f..1f, 15,
        ) { GlassSettings.updateDockCapsuleAlpha(step5(it)) }
        GlowSwitchRow(
            "Матовое стекло островка",
            "Капсула островка полупрозрачная, за ней размытый экран (работает в режиме «Островок»; на планшетах островок тоже плывёт поверх контента)",
            GlassSettings.islandGlass,
        ) { GlassSettings.updateIslandGlass(it) }
        StyleSlider("Сила размытия островка: ${GlassSettings.islandBlurDp} dp", GlassSettings.islandBlurDp.toFloat(), 1f..GlassSettings.MAX_BLUR.toFloat()) {
            GlassSettings.updateIslandBlur(it.roundToInt())
        }
        StyleSlider("Плотность стекла островка: ${pct(GlassSettings.islandAlpha)}%", GlassSettings.islandAlpha, 0.1f..1f, 17) {
            GlassSettings.updateIslandAlpha(step5(it))
        }

        // Мини-плеер
        Text("Мини-плеер", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
        GlowSwitchRow(
            "Матовое стекло мини-плеера",
            "Мини-плеер полупрозрачный, за ним размытый экран",
            GlassSettings.miniGlass,
        ) { GlassSettings.updateMiniGlass(it) }
        StyleSlider("Сила размытия: ${GlassSettings.miniBlurDp} dp", GlassSettings.miniBlurDp.toFloat(), 1f..GlassSettings.MAX_BLUR.toFloat()) {
            GlassSettings.updateMiniBlur(it.roundToInt())
        }
        StyleSlider("Плотность подложки: ${pct(GlassSettings.miniAlpha)}%", GlassSettings.miniAlpha, 0.1f..1f, 17) {
            GlassSettings.updateMiniAlpha(step5(it))
        }

        // Полный плеер
        Text("Фон плеера", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp))
        GlowSwitchRow(
            "Матовое стекло фона плеера",
            "Фон полного плеера полупрозрачный, за ним размытое приложение",
            GlassSettings.playerGlass,
        ) { GlassSettings.updatePlayerGlass(it) }
        StyleSlider("Сила размытия: ${GlassSettings.playerBlurDp} dp", GlassSettings.playerBlurDp.toFloat(), 1f..GlassSettings.MAX_BLUR.toFloat()) {
            GlassSettings.updatePlayerBlur(it.roundToInt())
        }
        StyleSlider("Плотность подложки: ${pct(GlassSettings.playerAlpha)}%", GlassSettings.playerAlpha, 0.1f..1f, 17) {
            GlassSettings.updatePlayerAlpha(step5(it))
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { GlassSettings.resetAll() }) { Text("Выключить стекло и вернуть силу размытия по умолчанию") }
    }
}
