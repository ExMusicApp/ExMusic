package app.ymusic.m3e.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

enum class IconShape(val title: String) {
    Default("Material 3"),
    Circle("Круг"),
    RoundedSquare("Скруглённый квадрат"),
    Squircle("Суперэллипс"),
    Teardrop("Капля"),
    Pebble("Камешек"),
    Hexagon("Шестиугольник"),
    Cut("Срезанный квадрат"),
    Square("Квадрат"),
}

enum class AvatarShape(val title: String) {
    Cookie12("Печенье"),
    Cookie9("Печенье мягкое"),
    Cookie6("Печенье крупное"),
    Circle("Круг"),
    Clover("Клевер"),
    Sunny("Солнце"),
    Flower("Цветок"),
    Puffy("Пухлая"),
    SoftBurst("Взрыв"),
    Rounded("Скруглённый квадрат"),
}

object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val a = size.width / 2f
        val b = size.height / 2f
        val n = 4.5
        val path = Path()
        val steps = 144
        for (i in 0..steps) {
            val t = 2.0 * Math.PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = a + a * sign(c) * abs(c).pow(2.0 / n)
            val y = b + b * sign(s) * abs(s).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

object HexagonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val inset = minOf(w, h) * 0.25f
        val path = Path().apply {
            moveTo(inset, 0f)
            lineTo(w - inset, 0f)
            lineTo(w, h / 2f)
            lineTo(w - inset, h)
            lineTo(inset, h)
            lineTo(0f, h / 2f)
            close()
        }
        return Outline.Generic(path)
    }
}

fun IconShape.shape(defaultDp: Dp): Shape = when (this) {
    IconShape.Default -> RoundedCornerShape(defaultDp)
    IconShape.Circle -> RoundedCornerShape(50)
    IconShape.RoundedSquare -> RoundedCornerShape(24)
    IconShape.Squircle -> SquircleShape
    IconShape.Teardrop -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 10, bottomStartPercent = 50)
    IconShape.Pebble -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 36, bottomEndPercent = 50, bottomStartPercent = 28)
    IconShape.Hexagon -> HexagonShape
    IconShape.Cut -> CutCornerShape(26)
    IconShape.Square -> RectangleShape
}

fun IconShape.containerShape(dp: Dp): Shape = when (this) {
    IconShape.Default, IconShape.Circle, IconShape.RoundedSquare, IconShape.Squircle -> RoundedCornerShape(dp)
    IconShape.Teardrop -> RoundedCornerShape(topStart = dp, topEnd = dp, bottomEnd = dp / 4f, bottomStart = dp)
    IconShape.Pebble -> RoundedCornerShape(topStart = dp, topEnd = dp * 0.72f, bottomEnd = dp, bottomStart = dp * 0.56f)
    IconShape.Hexagon, IconShape.Cut -> CutCornerShape(dp * 0.6f)
    IconShape.Square -> RectangleShape
}

fun IconShape.themeShape(dp: Dp): CornerBasedShape = when (this) {
    IconShape.Default -> RoundedCornerShape(dp)
    IconShape.Circle -> if (dp >= 20.dp) RoundedCornerShape(dp) else RoundedCornerShape(20.dp)
    IconShape.RoundedSquare, IconShape.Squircle -> RoundedCornerShape(if (dp >= 20.dp) dp else dp * 1.5f)
    IconShape.Teardrop -> RoundedCornerShape(topStart = dp, topEnd = dp, bottomEnd = dp / 4f, bottomStart = dp)
    IconShape.Pebble -> RoundedCornerShape(topStart = dp, topEnd = dp * 0.72f, bottomEnd = dp, bottomStart = dp * 0.56f)
    IconShape.Hexagon, IconShape.Cut -> CutCornerShape(dp * 0.6f)
    IconShape.Square -> RoundedCornerShape(0.dp)
}
