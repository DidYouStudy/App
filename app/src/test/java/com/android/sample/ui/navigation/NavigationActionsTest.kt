// Co-authored-by: GitHub Copilot
package com.android.sample.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/** Verifies guarded navigation used by the authentication and onboarding flows. */
class NavigationActionsTest {
  @Test
  fun blockedDestinationDoesNotNavigateBackIntoAuthenticationFlow() {
    val actions = RecordingNavigationActions(Screen.Preferences.route)

    actions.navigateIfAllowed(Screen.Auth, { false })

    assertTrue(actions.navigatedTo.isEmpty())
    assertTrue(actions.clearedFlows.isEmpty())
  }

  @Test
  fun allowedDestinationClearsCompletedAuthenticationFlow() {
    val actions = RecordingNavigationActions(Screen.Auth.route)

    actions.navigateIfAllowed(Screen.Preferences, { true }, completedFlow = Screen.Auth)

    assertEquals(emptyList<Screen>(), actions.navigatedTo)
    assertEquals(listOf(Screen.Preferences to Screen.Auth), actions.clearedFlows)
  }

  @Test
  fun repeatedDestinationDoesNotCreateAnotherBackStackEntry() {
    val actions = RecordingNavigationActions(Screen.Dashboard.route)

    actions.navigateIfAllowed(Screen.Dashboard, { true })

    assertTrue(actions.navigatedTo.isEmpty())
    assertTrue(actions.clearedFlows.isEmpty())
  }

  private class RecordingNavigationActions(currentRoute: String) : NavigationActions(mock()) {
    private val route = currentRoute
    val navigatedTo = mutableListOf<Screen>()
    val clearedFlows = mutableListOf<Pair<Screen, Screen>>()

    override fun currentRoute(): String = route

    override fun navigateTo(screen: Screen) {
      navigatedTo += screen
    }

    override fun navigateToAndClear(
        screen: Screen,
        completedFlow: Screen,
        clearAll: Boolean,
    ) {
      clearedFlows += screen to completedFlow
    }
  }
}
