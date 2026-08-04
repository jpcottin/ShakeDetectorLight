package com.jpcottin.shakedetectortest.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** UI tests for [ShakeDetectorContent]. */
class ShakeDetectorScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun idleState_showsPromptAndAcceleration() {
    composeTestRule.setContent {
      ShakeDetectorContent(shakeLevel = ShakeLevel.NONE, acceleration = 9.81f)
    }

    composeTestRule.onNodeWithText("Shake your phone!").assertExists()
    composeTestRule.onNodeWithText("Acceleration: ${"%.2f".format(9.81f)}").assertExists()
  }

  @Test
  fun smallShake_showsSmallShakeMessage() {
    composeTestRule.setContent {
      ShakeDetectorContent(shakeLevel = ShakeLevel.SMALL, acceleration = 13.42f)
    }

    composeTestRule.onNodeWithText("Small Shake Detected!").assertExists()
  }

  @Test
  fun bigShake_showsBigShakeMessage() {
    composeTestRule.setContent {
      ShakeDetectorContent(shakeLevel = ShakeLevel.BIG, acceleration = 21.37f)
    }

    composeTestRule.onNodeWithText("Big Shake Detected!").assertExists()
  }
}
