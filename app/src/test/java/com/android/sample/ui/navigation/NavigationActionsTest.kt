// Co-authored-by: GitHub Copilot
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/** Verifies guarded navigation used by the authentication and onboarding flows. */
class NavigationActionsTest {
  @Test
  fun repeatedBackAtDashboardDoesNotPopTheRootDestination() {
    val navController = mock(NavHostController::class.java)
    val dashboard = mock(NavDestination::class.java)
    `when`(dashboard.route).thenReturn(Screen.Dashboard.route)
    `when`(navController.currentDestination).thenReturn(dashboard)

    val actions = NavigationActions(navController)
    repeat(3) { actions.goBack() }

    verify(navController, never()).popBackStack()
  }

  @Test
  fun repeatedBackFromSettingsStopsOnceDashboardIsReached() {
    val navController = mock(NavHostController::class.java)
    val settings = mock(NavDestination::class.java)
    val dashboard = mock(NavDestination::class.java)
    `when`(settings.route).thenReturn(Screen.Settings.route)
    `when`(dashboard.route).thenReturn(Screen.Dashboard.route)
    `when`(navController.currentDestination).thenReturn(settings, dashboard)
    `when`(navController.popBackStack()).thenReturn(true)

    val actions = NavigationActions(navController)
    actions.goBack()
    actions.goBack()

    verify(navController, times(1)).popBackStack()
  }

  /** Recovers to Dashboard if popping the only non-root destination empties the controller. */
  @Test
  fun backFromLastDestinationNavigatesToDashboard() {
    val navController = mock(NavHostController::class.java)
    val settings = mock(NavDestination::class.java)
    `when`(settings.route).thenReturn(Screen.Settings.route)
    `when`(navController.currentDestination).thenReturn(settings, null)
    `when`(navController.popBackStack()).thenReturn(true)
    `when`(
            navController.navigate(
                org.mockito.kotlin.any<String>(),
                org.mockito.kotlin.any<androidx.navigation.NavOptionsBuilder.() -> Unit>(),
            )
        )
        .thenAnswer { invocation ->
          val builder = invocation.getArgument<androidx.navigation.NavOptionsBuilder.() -> Unit>(1)
          androidx.navigation.NavOptionsBuilder().apply(builder)
          null
        }

    NavigationActions(navController).goBack()

    assertTrue(
        org.mockito.Mockito.mockingDetails(navController).invocations.any {
          it.method.name == "navigate" && it.arguments.firstOrNull() == Screen.Dashboard.route
        }
    )
  }

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

  @Test
  fun screenRoutesAreConstructedCorrectly() {
    assertEquals("add_event?kind=PERSONAL", Screen.AddEvent.route)
    assertEquals("add_event?kind=PERSONAL", Screen.AddEvent().route)
    assertEquals(
        "add_event?kind=GROUP_SESSION",
        Screen.AddEvent(com.android.sample.ui.eventplanning.EventKind.GROUP_SESSION).route,
    )
    assertEquals("add_event?kind={kind}", Screen.AddEvent.ROUTE)
    assertEquals("edit_event/e1?kind=PERSONAL", Screen.EditEvent("e1").route)
    assertEquals(
        "edit_event/s1?kind=GROUP_SESSION",
        Screen.EditEvent("s1", com.android.sample.ui.eventplanning.EventKind.GROUP_SESSION).route,
    )
    assertEquals("edit_event/{eventId}?kind={kind}", Screen.EditEvent.ROUTE)
  }

  @Test
  fun navigateToTopLevelDestinationWhenAlreadyThereDoesNothing() {
    val navController = mock(NavHostController::class.java)
    val dest = mock(NavDestination::class.java)
    `when`(dest.route).thenReturn(Screen.Dashboard.route)
    `when`(navController.currentDestination).thenReturn(dest)

    val actions = NavigationActions(navController)
    actions.navigateTo(Screen.Dashboard)

    assertTrue(
        org.mockito.Mockito.mockingDetails(navController).invocations.none {
          it.method.name == "navigate"
        }
    )
  }

  @Test
  fun navigateToDestinationsCallsNavControllerNavigate() {
    val navController = mock(NavHostController::class.java)
    val dest = mock(NavDestination::class.java)
    `when`(dest.route).thenReturn(Screen.Settings.route)
    `when`(navController.currentDestination).thenReturn(dest)
    `when`(
            navController.navigate(
                org.mockito.kotlin.any<String>(),
                org.mockito.kotlin.any<androidx.navigation.NavOptionsBuilder.() -> Unit>(),
            )
        )
        .thenAnswer { invocation ->
          val builder = invocation.getArgument<androidx.navigation.NavOptionsBuilder.() -> Unit>(1)
          androidx.navigation.NavOptionsBuilder().apply(builder)
          null
        }

    val actions = NavigationActions(navController)
    // Top-level destination
    actions.navigateTo(Screen.Dashboard)
    // Non-top-level destination
    actions.navigateTo(Screen.Auth)
    actions.navigateTo(Screen.AddEvent())

    assertTrue(
        org.mockito.Mockito.mockingDetails(navController).invocations.any {
          it.method.name == "navigate" && it.arguments.firstOrNull() == Screen.Dashboard.route
        }
    )
  }

  @Test
  fun navigateToAndClearCallsNavController() {
    val navController = mock(NavHostController::class.java)
    `when`(navController.popBackStack()).thenReturn(true, false)
    `when`(
            navController.navigate(
                org.mockito.kotlin.any<String>(),
                org.mockito.kotlin.any<androidx.navigation.NavOptionsBuilder.() -> Unit>(),
            )
        )
        .thenAnswer { invocation ->
          val builder = invocation.getArgument<androidx.navigation.NavOptionsBuilder.() -> Unit>(1)
          androidx.navigation.NavOptionsBuilder().apply(builder)
          null
        }

    val actions = NavigationActions(navController)
    actions.navigateToAndClear(Screen.Dashboard, Screen.Auth, clearAll = true)
    actions.navigateToAndClear(Screen.Dashboard, Screen.Auth, clearAll = false)

    assertTrue(
        org.mockito.Mockito.mockingDetails(navController).invocations.any {
          it.method.name == "navigate" && it.arguments.firstOrNull() == Screen.Dashboard.route
        }
    )
  }

  @Test
  fun navigateIfAllowedWithoutCompletedFlowNavigatesNormally() {
    val navController = mock(NavHostController::class.java)
    val dest = mock(NavDestination::class.java)
    `when`(dest.route).thenReturn(Screen.Auth.route)
    `when`(navController.currentDestination).thenReturn(dest)

    val actions = NavigationActions(navController)
    actions.navigateIfAllowed(Screen.Dashboard, { true })

    assertTrue(
        org.mockito.Mockito.mockingDetails(navController).invocations.any {
          it.method.name == "navigate" && it.arguments.firstOrNull() == Screen.Dashboard.route
        }
    )
  }

  @Test
  fun editEventEncodesSpecialCharactersInRoute() {
    val complexId = "session/123?param=val#section test"
    val editEvent =
        Screen.EditEvent(complexId, com.android.sample.ui.eventplanning.EventKind.GROUP_SESSION)
    assertEquals(
        "edit_event/session%2F123%3Fparam%3Dval%23section%20test?kind=GROUP_SESSION",
        editEvent.route,
    )
    val decoded = Screen.EditEvent.decodePathSegment("session%2F123%3Fparam%3Dval%23section%20test")
    assertEquals(complexId, decoded)
  }

  @Test
  fun navigationActionsWithNullNavControllerDoesNotCrash() {
    val actions = NavigationActions()
    assertEquals("", actions.currentRoute())
    // Should be safe no-ops without throwing NullPointerException
    actions.navigateTo(Screen.Dashboard)
    actions.navigateToAndClear(Screen.Dashboard, Screen.Auth)
    actions.goBack()
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
