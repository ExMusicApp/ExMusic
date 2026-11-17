package app.ymusic.m3e.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberTilt(enabled: Boolean): State<Float> {
    val ctx = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tilt = remember { mutableFloatStateOf(0f) }
    DisposableEffect(enabled, lifecycle) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = if (enabled) sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        if (sm == null || sensor == null) {
            tilt.floatValue = 0f
            return@DisposableEffect onDispose { }
        }
        var smooth = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.size < 2) return
                val rot = runCatching { view.display?.rotation }.getOrNull() ?: Surface.ROTATION_0
                val sx = when (rot) {
                    Surface.ROTATION_90 -> -e.values[1]
                    Surface.ROTATION_180 -> -e.values[0]
                    Surface.ROTATION_270 -> e.values[1]
                    else -> e.values[0]
                }
                val target = (-sx / 6f).coerceIn(-1f, 1f)
                smooth += (target - smooth) * 0.35f
                tilt.floatValue = smooth
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        var registered = false
        fun register() {
            if (!registered) registered = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        fun unregister() {
            if (registered) { sm.unregisterListener(listener); registered = false }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            unregister()
        }
    }
    return tilt
}
