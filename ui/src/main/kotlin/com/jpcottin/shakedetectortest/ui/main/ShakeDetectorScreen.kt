package com.jpcottin.shakedetectortest.ui.main

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jpcottin.shakedetectortest.theme.Pink80
import com.jpcottin.shakedetectortest.theme.Purple80
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * Stateful screen: listens to the accelerometer, classifies shakes, vibrates on
 * shake level changes and resets the label one second after the last shake.
 */
@Composable
fun ShakeDetectorScreen(modifier: Modifier = Modifier) {
  var shakeLevel by remember { mutableStateOf(ShakeLevel.NONE) }
  var acceleration by remember { mutableFloatStateOf(0f) }
  val context = LocalContext.current

  AccelerometerEffect { acc ->
    acceleration = acc

    val newShakeLevel = classifyShake(acc)
    if (newShakeLevel != ShakeLevel.NONE && newShakeLevel != shakeLevel) {
      vibrate(context)
    }
    shakeLevel = newShakeLevel
  }

  ShakeDetectorContent(shakeLevel = shakeLevel, acceleration = acceleration, modifier = modifier)

  LaunchedEffect(shakeLevel) {
    if (shakeLevel != ShakeLevel.NONE) {
      delay(1000)
      shakeLevel = ShakeLevel.NONE
    }
  }
}

/** Stateless UI, driven directly by previews and UI tests. */
@Composable
internal fun ShakeDetectorContent(
  shakeLevel: ShakeLevel,
  acceleration: Float,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text =
        when (shakeLevel) {
          ShakeLevel.SMALL -> "Small Shake Detected!"
          ShakeLevel.BIG -> "Big Shake Detected!"
          ShakeLevel.NONE -> "Shake your phone!"
        },
      style =
        when (shakeLevel) {
          ShakeLevel.SMALL -> TextStyle(color = Pink80, fontSize = 32.sp)
          ShakeLevel.BIG -> TextStyle(color = Purple80, fontSize = 40.sp)
          ShakeLevel.NONE -> MaterialTheme.typography.headlineMedium
        },
    )
    Text(
      text = "Acceleration: ${"%.2f".format(acceleration)}",
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.padding(top = 16.dp),
    )
  }
}

@Composable
private fun AccelerometerEffect(onAccelerationChanged: (Float) -> Unit) {
  val context = LocalContext.current

  DisposableEffect(context) {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val sensorEventListener =
      object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
          event?.let {
            val x = it.values[0]
            val y = it.values[1]
            val z = it.values[2]
            onAccelerationChanged(sqrt(x * x + y * y + z * z))
          }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
      }

    sensorManager.registerListener(
      sensorEventListener,
      accelerometer,
      SensorManager.SENSOR_DELAY_NORMAL,
    )

    onDispose { sensorManager.unregisterListener(sensorEventListener) }
  }
}

private fun vibrate(context: Context) {
  val vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager =
        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
      vibratorManager.defaultVibrator
    } else {
      @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
  } else {
    @Suppress("DEPRECATION") vibrator.vibrate(150)
  }
}
