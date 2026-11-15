package com.jpcottin.shakedetectortest.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * [ShakeDetectorScreen] buzzes once per shake: not for every peak of one shake, and not again
 * when the screen is recreated with the shake still on it. The sensor is a fake the test feeds,
 * the clock a variable, and the buzz a counter in place of the vibrator.
 */
class ShakeDetectorScreenBuzzTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val readings = MutableSharedFlow<Float>(extraBufferCapacity = 64)
  private val accelerometer =
    object : Accelerometer {
      override val isAvailable = true
      override val magnitude: Flow<Float> = readings
    }

  @Volatile private var now = 0L
  private val viewModel = ShakeDetectorViewModel(accelerometer) { now }
  @Volatile private var buzzes = 0

  /** One accelerometer reading at [at] ms on the injected clock, folded into the screen. */
  private fun read(acceleration: Float, at: Long) {
    now = at
    check(readings.tryEmit(acceleration))
    composeTestRule.waitForIdle()
  }

  @Test
  fun oneBuzzPerShake_noneForALaterPeakOrARecreation() {
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent { ShakeDetectorScreen(viewModel = viewModel, onShake = { buzzes++ }) }
    composeTestRule.waitUntil(5_000) { readings.subscriptionCount.value > 0 }
    assertEquals(0, buzzes)

    read(20f, at = 1_000) // a big shake
    composeTestRule.waitUntil(5_000) { buzzes == 1 }

    read(13f, at = 1_400) // a weaker peak of the same shake
    read(9.8f, at = 1_800) // at rest, still inside the hold window
    assertEquals(1, buzzes)

    restoration.emulateSavedInstanceStateRestore() // a rotation with the shake still on screen
    composeTestRule.waitForIdle()
    assertEquals(1, buzzes)

    read(9.8f, at = 3_000) // the hold window is over
    read(20f, at = 3_100) // a new shake
    composeTestRule.waitUntil(5_000) { buzzes == 2 }
  }
}
