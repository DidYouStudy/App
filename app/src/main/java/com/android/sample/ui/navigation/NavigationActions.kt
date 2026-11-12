// Co-authored-by: Claude AI Agent
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.navigation

import androidx.navigation.NavHostController
import com.android.sample.ui.eventplanning.EventKind

sealed class Screen(
    val route: String,
    val name: String,
    val isTopLevelDestination: Boolean = false,
) {
  object Auth : Screen(route = "auth", name = "Authentication")

  object Dashboard : Screen(route = "dashboard", name = "Dashboard", isTopLevelDestination = true)

  object Preferences : Screen(route = "preferences", name = "Preferences")

  object Settings : Screen(route = "settings", name = "Settings")

  object Calendar : Screen(route = "calendar", name = "Calendar", isTopLevelDestination = true)

  object GroupSession :
      Screen(route = "group_session", name = "GroupSession", isTopLevelDestination = true)

  object AddEvent : Screen(route = "add_event", name = "AddEvent")

  object AddGroupSession : Screen(route = "add_group_session", name = "AddGroupSession")

  data class EditEvent(val eventId: String, val kind: EventKind = EventKind.PERSONAL) :
      Screen(route = "edit_event/$eventId?kind=${kind.name}", name = "EditEvent") {
    companion object {
      const val ROUTE = "edit_event/{eventId}?kind={kind}"
    }
  }
}

open class NavigationActions(
    private val navController: NavHostController? = null,
) {
  /**
   * Navigate to the specified screen.
   *
   * @param screen The screen to navigate to
   */
  open fun navigateTo(screen: Screen) {
    if (screen.isTopLevelDestination && currentRoute() == screen.route) {
      // If the user is already on the top-level destination, do nothing
      return
    }

    // Forward to another tab: fresh screen state (no restore)
    navController?.navigate(screen.route) {
      if (screen.isTopLevelDestination) {
        // If the destination is already on top of stack, don’t push another copy to stack
        launchSingleTop = true
        // Before adding the new destination,
        // pop stack entries until the given screen is on top (according to options).
        // Useful for tab-style navigation to avoid deep stacks.
        popUpTo(screen.route) {
          // Pops destinations until the start destination is on top of the stack,
          // start destination included (<=> clears stack)
          inclusive = true
          // Pops destinations without saving their UI state for future restore
          saveState = false
        }
      }

      if (screen !is Screen.Auth) {
        // Restore state when re selecting a previously selected item
        restoreState = true
      }
    }
  }

  /** Navigates forward and removes the completed flow from the back stack. */
  open fun navigateToAndClear(
      screen: Screen,
      completedFlow: Screen,
      clearAll: Boolean = false,
  ) {
    if (clearAll) {
      while (navController?.popBackStack() == true) {}
      navController?.navigate(screen.route) { launchSingleTop = true }
      return
    }

    navController?.navigate(screen.route) {
      popUpTo(completedFlow.name) {
        inclusive = true
        saveState = false
      }
      launchSingleTop = true
    }
  }

  /** Performs a navigation only when the destination is allowed by the current app state. */
  open fun navigateIfAllowed(
      screen: Screen,
      isAllowed: (Screen) -> Boolean,
      completedFlow: Screen? = null,
  ) {
    if (!isAllowed(screen) || currentRoute() == screen.route) return
    if (completedFlow == null) navigateTo(screen) else navigateToAndClear(screen, completedFlow)
  }

  /**
   * Navigate back without allowing an explicit back action to remove the dashboard.
   *
   * System Back remains owned by Navigation, so it can still finish the activity from the
   * dashboard. If a non-dashboard destination is the only remaining entry, recover to Dashboard.
   */
  open fun goBack() {
    if (currentRoute() == Screen.Dashboard.route) return
    navController?.popBackStack()
    if (currentRoute().isEmpty()) {
      navController?.navigate(Screen.Dashboard.route) { launchSingleTop = true }
    }
  }

  /**
   * Get the current route of the navigation controller.
   *
   * @return The current route
   */
  open fun currentRoute(): String {
    return navController?.currentDestination?.route ?: ""
  }
}
