package com.jpcottin.shakedetectortest.ui.main

import android.content.Context
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/** How long a detected shake stays on screen after the last shaking sensor reading. */
const val SHAKE_HOLD_MS = 1000L

/** Everything [ShakeDetectorContent] needs to render. */
data class ShakeUiState(
  val shakeLevel: ShakeLevel = ShakeLevel.NONE,
  val acceleration: Float = 0f,
  val isSensorAvailable: Boolean = true,
  /** Timestamp of the last non-[ShakeLevel.NONE] reading; internal bookkeeping. */
  internal val lastShakeAt: Long = Long.MIN_VALUE,
)

/**
 * Folds one accelerometer reading into the current state.
 *
 * A shake stays on screen for [SHAKE_HOLD_MS] after the last shaking reading, so
 * the label doesn't flicker between the peaks of a single shake. Pure and
 * clock-injected, so it is unit tested on the JVM without a device.
 */
internal fun ShakeUiState.reduce(acceleration: Float, now: Long): ShakeUiState =
  when (val level = classifyShake(acceleration)) {
    // A shake: show it and restart the hold window.
    ShakeLevel.NONE ->
      if (now - lastShakeAt < SHAKE_HOLD_MS) {
        // Below threshold but still inside the hold window: keep the label.
        copy(acceleration = acceleration)
      } else {
        // Hold expired: back to idle.
        copy(shakeLevel = ShakeLevel.NONE, acceleration = acceleration)
      }
    else -> copy(shakeLevel = level, acceleration = acceleration, lastShakeAt = now)
  }

/**
 * Turns the raw accelerometer stream into [ShakeUiState].
 *
 * The sensor is only subscribed while the UI is collecting (see
 * [SharingStarted.WhileSubscribed]), so backgrounding the app releases it.
 */
class ShakeDetectorViewModel(
  accelerometer: AccelerometerDataSource,
  private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {

  val uiState: StateFlow<ShakeUiState> =
    accelerometer.magnitude
      .runningFold(ShakeUiState(isSensorAvailable = accelerometer.isAvailable)) { state, reading ->
        state.reduce(acceleration = reading, now = elapsedRealtimeMs())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ShakeUiState(isSensorAvailable = accelerometer.isAvailable),
      )

  companion object {
    fun factory(context: Context) = viewModelFactory {
      initializer {
        val sensorManager =
          context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        ShakeDetectorViewModel(AccelerometerDataSource(sensorManager))
      }
    }
  }
}
