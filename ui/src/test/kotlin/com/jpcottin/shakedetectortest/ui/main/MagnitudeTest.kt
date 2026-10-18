package com.jpcottin.shakedetectortest.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

/** The accelerometer reading is reduced to one number before classification. */
class MagnitudeTest {

  @Test
  fun `a three-four-zero vector has length five`() {
    assertEquals(5f, magnitude(3f, 4f, 0f), 0.0001f)
  }

  @Test
  fun `gravity alone on any axis reads as gravity`() {
    assertEquals(9.81f, magnitude(0f, 0f, 9.81f), 0.0001f)
    assertEquals(9.81f, magnitude(0f, -9.81f, 0f), 0.0001f)
  }

  @Test
  fun `sign of the components does not matter`() {
    assertEquals(magnitude(1f, 2f, 2f), magnitude(-1f, -2f, -2f), 0.0001f)
  }

  @Test
  fun `a device at rest stays below the shake thresholds`() {
    assertEquals(ShakeLevel.NONE, classifyShake(magnitude(0.1f, 0.2f, 9.8f)))
  }
}
