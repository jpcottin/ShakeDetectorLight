package com.jpcottin.shakedetectortest.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for the shake-hold reducer behind [ShakeDetectorViewModel]. */
class ShakeUiStateTest {

  private val idle = ShakeUiState()

  @Test
  fun `reading above big threshold becomes a big shake`() {
    val state = idle.reduce(acceleration = 20f, now = 1_000L)

    assertEquals(ShakeLevel.BIG, state.shakeLevel)
    assertEquals(20f, state.acceleration, 0.001f)
  }

  @Test
  fun `acceleration is always the latest reading`() {
    val state = idle.reduce(acceleration = 9.81f, now = 1_000L)

    assertEquals(9.81f, state.acceleration, 0.001f)
  }

  @Test
  fun `shake label is held while the device settles`() {
    val shaken = idle.reduce(acceleration = 20f, now = 1_000L)

    // Back to gravity 300ms later, well inside the 1s hold window.
    val settling = shaken.reduce(acceleration = 9.81f, now = 1_300L)

    assertEquals(ShakeLevel.BIG, settling.shakeLevel)
    assertEquals(9.81f, settling.acceleration, 0.001f)
  }

  @Test
  fun `shake label resets once the hold window expires`() {
    val shaken = idle.reduce(acceleration = 20f, now = 1_000L)

    val settled = shaken.reduce(acceleration = 9.81f, now = 1_000L + SHAKE_HOLD_MS)

    assertEquals(ShakeLevel.NONE, settled.shakeLevel)
  }

  @Test
  fun `a new shake inside the hold window restarts the window`() {
    val first = idle.reduce(acceleration = 20f, now = 1_000L)
    val second = first.reduce(acceleration = 12f, now = 1_500L)

    // 1_400ms after the FIRST shake, but only 900ms after the second.
    val stillHeld = second.reduce(acceleration = 9.81f, now = 2_400L)

    assertEquals(ShakeLevel.SMALL, second.shakeLevel)
    assertEquals(ShakeLevel.SMALL, stillHeld.shakeLevel)
  }

  @Test
  fun `resting readings from a cold start stay idle`() {
    val state = idle.reduce(acceleration = 9.81f, now = 0L)

    assertEquals(ShakeLevel.NONE, state.shakeLevel)
  }
}
