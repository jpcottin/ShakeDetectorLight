package com.jpcottin.shakedetectortest.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeLevelTest {

  @Test
  fun `resting gravity is not a shake`() {
    assertEquals(ShakeLevel.NONE, classifyShake(9.81f))
  }

  @Test
  fun `acceleration above small threshold is a small shake`() {
    assertEquals(ShakeLevel.SMALL, classifyShake(12f))
  }

  @Test
  fun `acceleration above big threshold is a big shake`() {
    assertEquals(ShakeLevel.BIG, classifyShake(20f))
  }

  @Test
  fun `thresholds are exclusive bounds`() {
    assertEquals(ShakeLevel.NONE, classifyShake(SMALL_SHAKE_THRESHOLD))
    assertEquals(ShakeLevel.SMALL, classifyShake(BIG_SHAKE_THRESHOLD))
  }
}
