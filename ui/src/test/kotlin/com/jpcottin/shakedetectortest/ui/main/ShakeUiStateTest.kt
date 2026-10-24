package com.jpcottin.shakedetectortest.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun `a new peak inside the hold window restarts the window`() {
    val first = idle.reduce(acceleration = 20f, now = 1_000L)
    val second = first.reduce(acceleration = 12f, now = 1_500L)

    // 1_400ms after the FIRST peak, but only 900ms after the second.
    val stillHeld = second.reduce(acceleration = 9.81f, now = 2_400L)
    // And 1_000ms after the second peak the hold is over.
    val released = second.reduce(acceleration = 9.81f, now = 2_500L)

    assertEquals(ShakeLevel.BIG, second.shakeLevel)
    assertEquals(ShakeLevel.BIG, stillHeld.shakeLevel)
    assertEquals(ShakeLevel.NONE, released.shakeLevel)
  }

  @Test
  fun `a weaker peak inside the hold window keeps the stronger label`() {
    val big = idle.reduce(acceleration = 20f, now = 1_000L)

    // The dip between two peaks of one vigorous shake, sampled as a small reading.
    val dip = big.reduce(acceleration = 14f, now = 1_200L)

    assertEquals(ShakeLevel.BIG, dip.shakeLevel)
    assertEquals(14f, dip.acceleration, 0.001f)
  }

  @Test
  fun `a weaker shake after the hold expired downgrades the label`() {
    val big = idle.reduce(acceleration = 20f, now = 1_000L)

    val small = big.reduce(acceleration = 14f, now = 1_000L + SHAKE_HOLD_MS)

    assertEquals(ShakeLevel.SMALL, small.shakeLevel)
  }

  @Test
  fun `a stronger peak upgrades the label`() {
    val small = idle.reduce(acceleration = 12f, now = 1_000L)

    val big = small.reduce(acceleration = 20f, now = 1_200L)

    assertEquals(ShakeLevel.BIG, big.shakeLevel)
    assertEquals(1, big.shakeCount)
  }

  @Test
  fun `a shake is counted once however many peaks it has`() {
    val first = idle.reduce(acceleration = 20f, now = 1_000L)
    val dip = first.reduce(acceleration = 9.81f, now = 1_200L)
    val secondPeak = dip.reduce(acceleration = 18f, now = 1_400L)
    val settling = secondPeak.reduce(acceleration = 9.81f, now = 1_600L)

    assertEquals(1, first.shakeCount)
    assertEquals(1, dip.shakeCount)
    assertEquals(1, secondPeak.shakeCount)
    assertEquals(1, settling.shakeCount)
  }

  @Test
  fun `a shake after the label let go counts again`() {
    val first = idle.reduce(acceleration = 20f, now = 1_000L)
    val released = first.reduce(acceleration = 9.81f, now = 1_000L + SHAKE_HOLD_MS)
    val second = released.reduce(acceleration = 20f, now = 2_100L)

    assertEquals(ShakeLevel.NONE, released.shakeLevel)
    assertEquals(2, second.shakeCount)
  }

  @Test
  fun `resting readings never count as a shake`() {
    val state = idle.reduce(acceleration = 9.81f, now = 1_000L).reduce(acceleration = 10f, now = 1_200L)

    assertEquals(0, state.shakeCount)
  }

  @Test
  fun `resting readings from a cold start stay idle`() {
    val state = idle.reduce(acceleration = 9.81f, now = 0L)

    assertEquals(ShakeLevel.NONE, state.shakeLevel)
    assertNull(state.lastShakeAt)
  }

  @Test
  fun `without a recorded shake there is no hold window`() {
    // A label with no timestamp behind it (never produced by reduce, but the
    // invariant the hold branch relies on) must reset instead of being held.
    val state = idle.copy(shakeLevel = ShakeLevel.BIG).reduce(acceleration = 9.81f, now = 0L)

    assertEquals(ShakeLevel.NONE, state.shakeLevel)
  }
}
