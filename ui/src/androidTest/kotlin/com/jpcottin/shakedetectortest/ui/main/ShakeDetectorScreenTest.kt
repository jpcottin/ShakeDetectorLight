package com.jpcottin.shakedetectortest.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.jpcottin.shakedetectortest.R
import org.junit.Rule
import org.junit.Test

/** UI tests for [ShakeDetectorContent]. */
class ShakeDetectorScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

  @Test
  fun idleState_showsPromptAndAcceleration() {
    composeTestRule.setContent {
      ShakeDetectorContent(ShakeUiState(shakeLevel = ShakeLevel.NONE, acceleration = 9.81f))
    }

    composeTestRule.onNodeWithText(resources.getString(R.string.shake_prompt)).assertExists()
    composeTestRule
      .onNodeWithText(resources.getString(R.string.acceleration_label, 9.81f))
      .assertExists()
  }

  @Test
  fun smallShake_showsSmallShakeMessage() {
    composeTestRule.setContent {
      ShakeDetectorContent(ShakeUiState(shakeLevel = ShakeLevel.SMALL, acceleration = 13.42f))
    }

    composeTestRule.onNodeWithText(resources.getString(R.string.shake_small)).assertExists()
  }

  @Test
  fun bigShake_showsBigShakeMessage() {
    composeTestRule.setContent {
      ShakeDetectorContent(ShakeUiState(shakeLevel = ShakeLevel.BIG, acceleration = 21.37f))
    }

    composeTestRule.onNodeWithText(resources.getString(R.string.shake_big)).assertExists()
  }

  @Test
  fun noAccelerometer_showsUnavailableMessageInsteadOfReading() {
    composeTestRule.setContent {
      ShakeDetectorContent(ShakeUiState(shakeLevel = ShakeLevel.NONE, isSensorAvailable = false))
    }

    composeTestRule.onNodeWithText(resources.getString(R.string.sensor_unavailable)).assertExists()
    composeTestRule
      .onNodeWithText(resources.getString(R.string.acceleration_label, 0f))
      .assertDoesNotExist()
  }
}
