package com.jpcottin.shakedetectortest

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jpcottin.shakedetectortest.ui.main.ShakeDetectorScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    // Scopes ViewModels to their NavEntry, so each entry gets its own instance
    // and it is cleared when the entry leaves the back stack. Listing any
    // decorator replaces the defaults, so the saveable-state one is repeated.
    entryDecorators =
      listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider =
      entryProvider {
        entry<Main> {
          ShakeDetectorScreen(modifier = Modifier.safeDrawingPadding().padding(16.dp))
        }
      },
  )
}
