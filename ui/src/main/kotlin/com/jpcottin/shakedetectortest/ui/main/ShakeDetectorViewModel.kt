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
import kotlinx.coroutines.flow.stateIn

/** How long a detected shake stays on screen after the last shaking sensor reading. */
const val SHAKE_HOLD_MS = 1000L

/** Everything [ShakeDetectorContent] needs to render. */
data class ShakeUiState(
  val shakeLevel: ShakeLevel = ShakeLevel.NONE,
  val acceleration: Float = 0f,
  val isSensorAvailable: Boolean = true,
  /**
   * How many shakes have been detected since the sensor was first read: one per shake, however
   * many peaks it has. The screen vibrates when this grows, never on a redraw.
   */
  val shakeCount: Int = 0,
  /** Timestamp of the last non-[ShakeLevel.NONE] reading, null until the first shake; internal bookkeeping. */
  internal val lastShakeAt: Long? = null,
)

/**
 * Folds one accelerometer reading into the current state.
 *
 * A shake stays on screen for [SHAKE_HOLD_MS] after the last shaking reading, so
 * the label doesn't flicker between the peaks of a single shake: a weaker peak
 * inside that window keeps the stronger label (and extends the window), a
 * stronger one upgrades it, and only the first peak after an idle label counts
 * as a new shake. Pure and clock-injected, so it is unit tested on the JVM
 * without a device.
 */
internal fun ShakeUiState.reduce(acceleration: Float, now: Long): ShakeUiState {
  val level = classifyShake(acceleration)
  val inHold = lastShakeAt != null && now - lastShakeAt < SHAKE_HOLD_MS
  return when {
    // Below threshold but still inside the hold window: keep the label.
    level == ShakeLevel.NONE && inHold -> copy(acceleration = acceleration)
    // Hold expired (or never shaken): back to idle.
    level == ShakeLevel.NONE -> copy(shakeLevel = ShakeLevel.NONE, acceleration = acceleration)
    // A weaker peak of the shake being shown: keep its label, restart the hold window.
    inHold && level < shakeLevel -> copy(acceleration = acceleration, lastShakeAt = now)
    // A shake: show it (upgrading a weaker label) and restart the hold window.
    else ->
      copy(
        shakeLevel = level,
        acceleration = acceleration,
        lastShakeAt = now,
        shakeCount = if (shakeLevel == ShakeLevel.NONE) shakeCount + 1 else shakeCount,
      )
  }
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

  // Kept outside the flow so the state (and the shake count) survives the sensor being released
  // and re-subscribed: a fold inside the flow would restart from the idle seed on every return.
  private var latest = ShakeUiState(isSensorAvailable = accelerometer.isAvailable)

  val uiState: StateFlow<ShakeUiState> =
    accelerometer.magnitude
      .map { reading -> latest.reduce(acceleration = reading, now = elapsedRealtimeMs()).also { latest = it } }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = latest,
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
