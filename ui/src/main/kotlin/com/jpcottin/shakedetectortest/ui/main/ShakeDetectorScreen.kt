package com.jpcottin.shakedetectortest.ui.main

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jpcottin.shakedetectortest.R

/**
 * Stateful screen: observes [ShakeDetectorViewModel] and vibrates once per detected
 * shake (when [ShakeUiState.shakeCount] grows), not when the screen is merely
 * recreated with a shake still on it.
 */
@Composable
fun ShakeDetectorScreen(
  modifier: Modifier = Modifier,
  viewModel: ShakeDetectorViewModel =
    viewModel(factory = ShakeDetectorViewModel.factory(LocalContext.current)),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  // The count already acknowledged by a buzz; saved across recreation so a rotation inside the
  // hold window does not buzz again for the same shake.
  var buzzedFor by rememberSaveable { mutableIntStateOf(uiState.shakeCount) }
  LaunchedEffect(uiState.shakeCount) {
    if (uiState.shakeCount > buzzedFor) {
      vibrate(context)
    }
    buzzedFor = uiState.shakeCount
  }

  ShakeDetectorContent(uiState = uiState, modifier = modifier)
}

/** Stateless UI, driven directly by previews and UI tests. */
@Composable
internal fun ShakeDetectorContent(uiState: ShakeUiState, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize(),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text =
        stringResource(
          when (uiState.shakeLevel) {
            ShakeLevel.SMALL -> R.string.shake_small
            ShakeLevel.BIG -> R.string.shake_big
            ShakeLevel.NONE -> R.string.shake_prompt
          }
        ),
      color =
        when (uiState.shakeLevel) {
          ShakeLevel.SMALL -> MaterialTheme.colorScheme.tertiary
          ShakeLevel.BIG -> MaterialTheme.colorScheme.primary
          ShakeLevel.NONE -> MaterialTheme.colorScheme.onBackground
        },
      style =
        when (uiState.shakeLevel) {
          ShakeLevel.SMALL -> MaterialTheme.typography.headlineLarge
          ShakeLevel.BIG -> MaterialTheme.typography.displaySmall
          ShakeLevel.NONE -> MaterialTheme.typography.headlineMedium
        },
      textAlign = TextAlign.Center,
      // Announce shake changes to TalkBack users, who can't see the colour change.
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )

    if (uiState.isSensorAvailable) {
      Text(
        text = stringResource(R.string.acceleration_label, uiState.acceleration),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 16.dp),
      )
    } else {
      Text(
        text = stringResource(R.string.sensor_unavailable),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 16.dp),
      )
    }
  }
}

private fun vibrate(context: Context) {
  val vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vibratorManager =
        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
      vibratorManager.defaultVibrator
    } else {
      @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
  } else {
    @Suppress("DEPRECATION") vibrator.vibrate(150)
  }
}
