package app.ymusic.m3e.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp

/**
 * Планшетный режим. «Авто» — широкая раскладка включается, когда окно шире 600 dp (планшеты, складные устройства,
 * телефоны в альбомной ориентации). «Только планшеты» — только на устройствах с короткой стороной от 600 dp
 * (на телефонах остаётся обычная раскладка и в альбомной ориентации). «Выкл» — широкой раскладки нет совсем.
 */
internal enum class TabletMode(val title: String) {
    Auto("Авто"),
    TabletsOnly("Только планшеты"),
    Off("Выкл"),
}

/** Где на планшете стоит островок. */
internal enum class IslandPos(val title: String) {
    Left("Слева"),
    Right("Справа"),
    Top("Сверху"),
    Bottom("Снизу"),
}

/** Настройка планшетного режима (SharedPreferences «tablet»). Настройки → Внешний вид → Планшеты. */
internal object TabletSettings {
    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf(TabletMode.Auto)
        private set

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("tablet", Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { TabletMode.valueOf(p.getString("mode", null) ?: "Auto") }.getOrDefault(TabletMode.Auto)
        sideRail = p.getBoolean("side_rail", true)
        inPortrait = p.getBoolean("in_portrait", true)
        islandPos = runCatching { IslandPos.valueOf(p.getString("island_pos", null) ?: "Left") }.getOrDefault(IslandPos.Left)
    }

    /** Навигация на планшете: true — боковая панель слева, false — нижний док / островок поверх контента. */
    var sideRail by mutableStateOf(true)
        private set

    fun updateSideRail(on: Boolean) {
        sideRail = on
        prefs?.edit()?.putBoolean("side_rail", on)?.apply()
    }

    /** Планшетная раскладка и в вертикальной ориентации (по умолчанию включено). */
    var inPortrait by mutableStateOf(true)
        private set

    fun updateInPortrait(on: Boolean) {
        inPortrait = on
        prefs?.edit()?.putBoolean("in_portrait", on)?.apply()
    }

    var islandPos by mutableStateOf(IslandPos.Left)
        private set

    fun updateIslandPos(p: IslandPos) {
        islandPos = p
        prefs?.edit()?.putString("island_pos", p.name)?.apply()
    }

    fun update(m: TabletMode) {
        mode = m
        prefs?.edit()?.putString("mode", m.name)?.apply()
    }

    /** Нужна ли широкая раскладка (боковая панель, две колонки) при такой конфигурации окна. */
    fun isWide(cfg: Configuration): Boolean = if (!inPortrait && cfg.orientation == Configuration.ORIENTATION_PORTRAIT) false else when (mode) {
        TabletMode.Off -> false
        TabletMode.Auto -> cfg.screenWidthDp >= 600
        TabletMode.TabletsOnly -> cfg.screenWidthDp >= 600 && cfg.smallestScreenWidthDp >= 600
    }
}

/** Широкая (планшетная) раскладка включена. Задаётся один раз в AppRoot. */
internal val LocalWide = compositionLocalOf { false }

/** Окно низкое (телефон в альбомной ориентации): боковая панель и шапки делаются компактнее. */
internal val LocalCompactHeight = compositionLocalOf { false }

/** Мы внутри левой колонки двухпанельного экрана: шапка альбома / плейлиста рисуется вертикально, с крупной обложкой. */
internal val LocalInSplitPane = compositionLocalOf { false }

@Composable
internal fun rememberIsWide(): Boolean {
    val cfg = LocalConfiguration.current
    return TabletSettings.isWide(cfg)
}

@Composable
internal fun rememberIsCompactHeight(): Boolean = LocalConfiguration.current.screenHeightDp < 480

/** Сетка альбомов: карточки не растягиваются на пол-экрана, а на широком экране помещается столько, сколько влезает. */
internal val AlbumGridCells: GridCells = GridCells.Adaptive(148.dp)

/** Ширина левой колонки двухпанельного экрана от ширины области. */
internal fun splitLeftWidth(total: Dp): Dp = (total * 0.38f).coerceIn(300.dp, 440.dp)

/**
 * Две колонки: слева кнопки и шапка, справа список. Каждая колонка прокручивается сама.
 * [leftWidth] по умолчанию считается от ширины области.
 */
@Composable
internal fun TwoPane(
    modifier: Modifier = Modifier,
    leftWidth: Dp? = null,
    left: @Composable BoxScope.() -> Unit,
    right: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val lw = leftWidth ?: splitLeftWidth(maxWidth)
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(lw).fillMaxHeight(), content = left)
            Box(Modifier.weight(1f).fillMaxHeight(), content = right)
        }
    }
}
