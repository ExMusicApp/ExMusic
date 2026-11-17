@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.ymusic.m3e.ui

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ymusic.m3e.R

private val FallbackLight = lightColorScheme(primary = Color(0xFF6B4EFF), secondary = Color(0xFFFF6B6B))
private val FallbackDark = darkColorScheme(primary = Color(0xFFC5BAFF), secondary = Color(0xFFFFB3B3))

private fun m3eDp(base: Int): Dp = base.dp

fun rounded(base: Int): Shape = ThemeSettings.iconShape.shape(m3eDp(base))

fun shapeInset(): Dp = when (ThemeSettings.iconShape) {
    IconShape.Circle, IconShape.Teardrop, IconShape.Pebble -> 14.dp
    IconShape.Hexagon -> 10.dp
    IconShape.Cut -> 6.dp
    IconShape.Squircle -> 4.dp
    else -> 0.dp
}

fun containerShape(base: Int): Shape = ThemeSettings.iconShape.containerShape(m3eDp(base))

fun textShape(base: Int = 8): Shape = androidx.compose.foundation.shape.RoundedCornerShape(base.dp)

fun animMs(ms: Int): Int = if (ThemeSettings.optimize) 1 else (ms / ThemeSettings.animSpeed).toInt().coerceAtLeast(1)

fun fxMs(ms: Int): Int = if (ThemeSettings.optimize) 1 else ms

fun <T> fxSpring(
    dampingRatio: Float = Spring.DampingRatioNoBouncy,
    stiffness: Float = Spring.StiffnessMedium,
): FiniteAnimationSpec<T> = if (ThemeSettings.optimize) snap() else spring(dampingRatio, stiffness)

fun scaledDp(base: Int): Dp = m3eDp(base)

fun cornerShape(r: Dp): Shape = ThemeSettings.iconShape.shape(r)

private fun expressiveShapes(sh: IconShape) = Shapes(
    extraSmall = sh.themeShape(8.dp),
    small = sh.themeShape(14.dp),
    medium = sh.themeShape(20.dp),
    large = sh.themeShape(28.dp),
    extraLarge = sh.themeShape(40.dp),
)

val GoogleSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_bold, FontWeight.Bold),
)

val GoogleSansCode = FontFamily(
    Font(R.font.google_sans_code_regular, FontWeight.Normal),
    Font(R.font.google_sans_code_medium, FontWeight.Medium),
)

private val BaseTypography = Typography()

private fun typographyWith(f: FontFamily): Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = f),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = f),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = f),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = f),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = f),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = f),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = f),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = f),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = f),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = f),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = f),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = f),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = f),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = f),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = f),
    displayLargeEmphasized = BaseTypography.displayLargeEmphasized.copy(fontFamily = f),
    displayMediumEmphasized = BaseTypography.displayMediumEmphasized.copy(fontFamily = f),
    displaySmallEmphasized = BaseTypography.displaySmallEmphasized.copy(fontFamily = f),
    headlineLargeEmphasized = BaseTypography.headlineLargeEmphasized.copy(fontFamily = f),
    headlineMediumEmphasized = BaseTypography.headlineMediumEmphasized.copy(fontFamily = f),
    headlineSmallEmphasized = BaseTypography.headlineSmallEmphasized.copy(fontFamily = f),
    titleLargeEmphasized = BaseTypography.titleLargeEmphasized.copy(fontFamily = f),
    titleMediumEmphasized = BaseTypography.titleMediumEmphasized.copy(fontFamily = f),
    titleSmallEmphasized = BaseTypography.titleSmallEmphasized.copy(fontFamily = f),
    bodyLargeEmphasized = BaseTypography.bodyLargeEmphasized.copy(fontFamily = f),
    bodyMediumEmphasized = BaseTypography.bodyMediumEmphasized.copy(fontFamily = f),
    bodySmallEmphasized = BaseTypography.bodySmallEmphasized.copy(fontFamily = f),
    labelLargeEmphasized = BaseTypography.labelLargeEmphasized.copy(fontFamily = f),
    labelMediumEmphasized = BaseTypography.labelMediumEmphasized.copy(fontFamily = f),
    labelSmallEmphasized = BaseTypography.labelSmallEmphasized.copy(fontFamily = f),
)

private val SystemTypography = typographyWith(FontFamily.Default)
private val GoogleSansTypography = typographyWith(GoogleSans)

@Composable
fun YMusicTheme(content: @Composable () -> Unit) {
    val accent = app.ymusic.m3e.data.PluginSystem.themeAccent ?: ThemeSettings.accent
    val dark = when (ThemeSettings.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val ctx = LocalContext.current
    val baseScheme = when {
        accent != null -> seedScheme(accent, dark)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> FallbackDark
        else -> FallbackLight
    }
    val slots = ThemeSettings.slotColors
    val tinted = if (slots.isEmpty()) baseScheme else baseScheme.withSlots(slots, dark)
    val scheme = if (dark && (ThemeSettings.amoled || app.ymusic.m3e.data.PluginSystem.themeAmoled)) tinted.toAmoled() else tinted
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    val customFont = ThemeSettings.customFont
    val customTypography = remember(customFont) { customFont?.let { typographyWith(it) } }
    val typography = when (ThemeSettings.fontMode) {
        FontMode.System -> SystemTypography
        FontMode.GoogleSans -> GoogleSansTypography
        FontMode.File -> customTypography ?: SystemTypography
    }
    val baseDensity = LocalDensity.current
    val uiScale = ThemeSettings.uiScale
    val density = remember(baseDensity, uiScale) { Density(baseDensity.density * uiScale, baseDensity.fontScale) }
    CompositionLocalProvider(LocalDensity provides density) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = remember(ThemeSettings.iconShape) { expressiveShapes(ThemeSettings.iconShape) },
            typography = typography,
            content = content,
        )
    }
}

private fun ColorScheme.toAmoled(): ColorScheme {
    fun darker(c: Color, k: Float) = lerp(Color.Black, c, k)
    return copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = darker(surfaceContainerLow, 0.6f),
        surfaceContainer = darker(surfaceContainer, 0.68f),
        surfaceContainerHigh = darker(surfaceContainerHigh, 0.78f),
        surfaceContainerHighest = darker(surfaceContainerHighest, 0.88f),
    )
}

private fun toneOf(argb: Int, tone: Int, satScale: Float = 1f, minSat: Float = 0.30f): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return Color.hsl(hsv[0], (hsv[1] * satScale).coerceIn(minSat * satScale, 0.9f), tone / 100f)
}

private fun ColorScheme.withSlots(slots: Map<AccentSlot, Int>, dark: Boolean): ColorScheme {
    var cs = this
    slots[AccentSlot.Secondary]?.let { c ->
        fun t(d: Int, l: Int) = toneOf(c, if (dark) d else l)
        cs = cs.copy(
            secondary = t(80, 40), onSecondary = t(20, 100),
            secondaryContainer = t(30, 90), onSecondaryContainer = t(90, 10),
        )
    }
    slots[AccentSlot.Tertiary]?.let { c ->
        fun t(d: Int, l: Int) = toneOf(c, if (dark) d else l)
        cs = cs.copy(
            tertiary = t(80, 40), onTertiary = t(20, 100),
            tertiaryContainer = t(30, 90), onTertiaryContainer = t(90, 10),
        )
    }
    slots[AccentSlot.Surface]?.let { c ->
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        val sN = (hsv[1] * 0.30f).coerceIn(0.05f, 0.28f)
        val sV = (hsv[1] * 0.40f).coerceIn(0.07f, 0.34f)
        fun n(tone: Int) = Color.hsl(hsv[0], sN, tone / 100f)
        fun nv(tone: Int) = Color.hsl(hsv[0], sV, tone / 100f)
        cs = if (dark) cs.copy(
            background = n(6), onBackground = n(90), surface = n(6), onSurface = n(90),
            surfaceVariant = nv(30), onSurfaceVariant = nv(80),
            inverseSurface = n(90), inverseOnSurface = n(20),
            outline = nv(60), outlineVariant = nv(30),
            surfaceBright = n(24), surfaceDim = n(6),
            surfaceContainerLowest = n(4), surfaceContainerLow = n(10), surfaceContainer = n(12),
            surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
        ) else cs.copy(
            background = n(98), onBackground = n(10), surface = n(98), onSurface = n(10),
            surfaceVariant = nv(90), onSurfaceVariant = nv(30),
            inverseSurface = n(20), inverseOnSurface = n(95),
            outline = nv(50), outlineVariant = nv(80),
            surfaceBright = n(98), surfaceDim = n(87),
            surfaceContainerLowest = n(100), surfaceContainerLow = n(96), surfaceContainer = n(94),
            surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
        )
    }
    return cs
}

private fun seedScheme(seedArgb: Int, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seedArgb, hsv)
    val h = hsv[0]
    val s = hsv[1].coerceIn(0.40f, 0.85f)
    fun p(tone: Int) = Color.hsl(h, s, tone / 100f)
    fun sec(tone: Int) = Color.hsl(h, s * 0.35f, tone / 100f)
    fun ter(tone: Int) = Color.hsl((h + 60f) % 360f, s * 0.55f, tone / 100f)
    fun n(tone: Int) = Color.hsl(h, 0.08f, tone / 100f)
    fun nv(tone: Int) = Color.hsl(h, 0.12f, tone / 100f)
    return if (dark) darkColorScheme(
        primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
        inversePrimary = p(40),
        secondary = sec(80), onSecondary = sec(20), secondaryContainer = sec(30), onSecondaryContainer = sec(90),
        tertiary = ter(80), onTertiary = ter(20), tertiaryContainer = ter(30), onTertiaryContainer = ter(90),
        background = n(6), onBackground = n(90),
        surface = n(6), onSurface = n(90),
        surfaceVariant = nv(30), onSurfaceVariant = nv(80),
        surfaceTint = p(80),
        inverseSurface = n(90), inverseOnSurface = n(20),
        outline = nv(60), outlineVariant = nv(30),
        surfaceBright = n(24), surfaceDim = n(6),
        surfaceContainerLowest = n(4), surfaceContainerLow = n(10), surfaceContainer = n(12),
        surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
    ) else lightColorScheme(
        primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
        inversePrimary = p(80),
        secondary = sec(40), onSecondary = sec(100), secondaryContainer = sec(90), onSecondaryContainer = sec(10),
        tertiary = ter(40), onTertiary = ter(100), tertiaryContainer = ter(90), onTertiaryContainer = ter(10),
        background = n(98), onBackground = n(10),
        surface = n(98), onSurface = n(10),
        surfaceVariant = nv(90), onSurfaceVariant = nv(30),
        surfaceTint = p(40),
        inverseSurface = n(20), inverseOnSurface = n(95),
        outline = nv(50), outlineVariant = nv(80),
        surfaceBright = n(98), surfaceDim = n(87),
        surfaceContainerLowest = n(100), surfaceContainerLow = n(96), surfaceContainer = n(94),
        surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
    )
}
