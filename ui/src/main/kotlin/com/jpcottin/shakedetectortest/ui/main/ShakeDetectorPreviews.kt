package com.jpcottin.shakedetectortest.ui.main

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.jpcottin.shakedetectortest.theme.ShakeDetectorLightTheme

@Preview(showBackground = true, name = "Idle")
@Composable
fun ShakeDetectorIdlePreview() {
  ShakeDetectorLightTheme {
    ShakeDetectorContent(shakeLevel = ShakeLevel.NONE, acceleration = 9.81f)
  }
}

@Preview(showBackground = true, name = "Small shake")
@Composable
fun ShakeDetectorSmallShakePreview() {
  ShakeDetectorLightTheme {
    ShakeDetectorContent(shakeLevel = ShakeLevel.SMALL, acceleration = 13.42f)
  }
}

@Preview(showBackground = true, name = "Big shake")
@Composable
fun ShakeDetectorBigShakePreview() {
  ShakeDetectorLightTheme {
    ShakeDetectorContent(shakeLevel = ShakeLevel.BIG, acceleration = 21.37f)
  }
}

@Preview(showBackground = true, name = "Big shake - dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ShakeDetectorBigShakeDarkPreview() {
  ShakeDetectorLightTheme {
    ShakeDetectorContent(shakeLevel = ShakeLevel.BIG, acceleration = 21.37f)
  }
}
