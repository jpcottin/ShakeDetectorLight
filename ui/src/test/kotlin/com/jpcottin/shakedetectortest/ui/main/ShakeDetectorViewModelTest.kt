package com.jpcottin.shakedetectortest.ui.main

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ShakeDetectorViewModel] on the JVM: the sensor is a fake [Accelerometer]
 * the test feeds, and the clock is a variable, so the hold window and the 5-second
 * unsubscribe timeout both run on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShakeDetectorViewModelTest {

  private val dispatcher = StandardTestDispatcher()

  /** What the clock reads; well past the hold window of any earlier shake when moved. */
  private var now = 1_000L

  @Before
  fun useTestMainDispatcher() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun restoreMainDispatcher() {
    Dispatchers.resetMain()
  }

  /** A sensor the test drives, counting how often it is subscribed and released. */
  private class FakeAccelerometer(override val isAvailable: Boolean = true) : Accelerometer {
    val readings = MutableSharedFlow<Float>()
    var subscriptions = 0
    var activeSubscriptions = 0

    override val magnitude: Flow<Float> =
      readings
        .onStart {
          subscriptions++
          activeSubscriptions++
        }
        .onCompletion { activeSubscriptions-- }
  }

  @Test
  fun `a reading above the threshold becomes a shake`() =
    runTest(dispatcher) {
      val sensor = FakeAccelerometer()
      val viewModel = ShakeDetectorViewModel(sensor) { now }
      val collector = launch { viewModel.uiState.collect() }
      runCurrent()

      sensor.readings.emit(20f)
      runCurrent()

      val state = viewModel.uiState.value
      assertEquals(ShakeLevel.BIG, state.shakeLevel)
      assertEquals(20f, state.acceleration, 0.001f)
      assertEquals(1, state.shakeCount)
      assertTrue(state.isSensorAvailable)
      collector.cancel()
    }

  @Test
  fun `the shake count survives the sensor being released and subscribed again`() =
    runTest(dispatcher) {
      val sensor = FakeAccelerometer()
      val viewModel = ShakeDetectorViewModel(sensor) { now }

      val firstVisit = launch { viewModel.uiState.collect() }
      runCurrent()
      sensor.readings.emit(20f)
      runCurrent()
      assertEquals(1, viewModel.uiState.value.shakeCount)

      // The screen goes away: after the stop timeout the sensor is released.
      firstVisit.cancel()
      advanceTimeBy(5_001)
      runCurrent()
      assertEquals(0, sensor.activeSubscriptions)

      // It comes back, long after the hold window: the sensor is subscribed anew, the
      // label is idle again, and the count is where it was, not back at zero.
      now += 60_000L
      val secondVisit = launch { viewModel.uiState.collect() }
      runCurrent()
      sensor.readings.emit(9.81f)
      runCurrent()

      val state = viewModel.uiState.value
      assertEquals(2, sensor.subscriptions)
      assertEquals(1, sensor.activeSubscriptions)
      assertEquals(ShakeLevel.NONE, state.shakeLevel)
      assertEquals(9.81f, state.acceleration, 0.001f)
      assertEquals(1, state.shakeCount)
      secondVisit.cancel()
    }

  @Test
  fun `a failed registration reads as a missing sensor`() =
    runTest(dispatcher) {
      val sensor =
        object : Accelerometer {
          override val isAvailable = true
          override val magnitude: Flow<Float> = flow {
            throw IllegalStateException("accelerometer listener registration failed")
          }
        }
      val viewModel = ShakeDetectorViewModel(sensor) { now }
      assertTrue(viewModel.uiState.value.isSensorAvailable)

      val collector = launch { viewModel.uiState.collect() }
      runCurrent()

      assertFalse(viewModel.uiState.value.isSensorAvailable)
      collector.cancel()
    }

  @Test
  fun `a device without an accelerometer starts without one`() =
    runTest(dispatcher) {
      val viewModel = ShakeDetectorViewModel(FakeAccelerometer(isAvailable = false)) { now }

      assertFalse(viewModel.uiState.value.isSensorAvailable)
    }
}
