package com.jpcottin.shakedetectortest.ui.main

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Exposes the device accelerometer as a cold [Flow] of magnitudes, in m/s².
 *
 * The listener is registered when the flow is collected and unregistered as soon
 * as collection stops, so nothing keeps the sensor alive while the app is in the
 * background.
 */
class AccelerometerDataSource(private val sensorManager: SensorManager) {

  /** False on devices with no accelerometer, where [magnitude] never emits. */
  val isAvailable: Boolean
    get() = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

  val magnitude: Flow<Float>
    get() {
      val accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return emptyFlow()

      return callbackFlow {
        val listener =
          object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
              val values = event?.values ?: return
              trySend(magnitude(values[0], values[1], values[2]))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
          }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose { sensorManager.unregisterListener(listener) }
      }
    }
}

/** Length of the acceleration vector, in m/s²: about 9.81 for a device at rest. */
internal fun magnitude(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)
