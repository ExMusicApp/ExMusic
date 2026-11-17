package app.ymusic.m3e.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

fun colorHex(argb: Int): String = "%06X".format(argb and 0xFFFFFF)

@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int?,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    val start = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initial ?: 0xFF6B4EFF.toInt(), hsv)
        hsv
    }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var typed by remember { mutableStateOf<String?>(null) }
    val color = Color.hsv(hue.coerceIn(0f, 360f), sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { p ->
                                sat = (p.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (p.y / size.height).coerceIn(0f, 1f)
                                typed = null
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                typed = null
                            }
                        }
                        .drawBehind {
                            drawRect(Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f))
                            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                            val c = Offset(sat * size.width, (1f - value) * size.height)
                            drawCircle(Color.White, 11.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                            drawCircle(color, 8.dp.toPx(), c)
                        },
                )
                Box(
                    Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { p ->
                                hue = (p.x / size.width).coerceIn(0f, 1f) * 360f
                                typed = null
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                                typed = null
                            }
                        }
                        .drawBehind {
                            drawRect(
                                Brush.horizontalGradient(
                                    listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it.coerceAtMost(359.9f), 1f, 1f) },
                                ),
                            )
                            val x = hue.coerceIn(0f, 360f) / 360f * size.width
                            drawCircle(Color.White, 12.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
                            drawCircle(Color.hsv(hue.coerceIn(0f, 360f), 1f, 1f), 9.dp.toPx(), Offset(x, size.height / 2))
                        },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    OutlinedTextField(
                        value = typed ?: colorHex(color.toArgb()),
                        onValueChange = { v ->
                            val clean = v.removePrefix("#").filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(6).uppercase()
                            typed = clean
                            if (clean.length == 6) {
                                clean.toIntOrNull(16)?.let { rgb ->
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(0xFF000000.toInt() or rgb, hsv)
                                    hue = hsv[0]; sat = hsv[1]; value = hsv[2]
                                }
                            }
                        },
                        prefix = { Text("#") },
                        label = { Text("HEX") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color.toArgb()) }) { Text("Выбрать") } },
        dismissButton = {
            Row {
                if (onReset != null) TextButton(onClick = onReset) { Text("Сбросить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}
