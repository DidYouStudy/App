package com.android.sample.ui.navigation

import androidx.navigation.NavHostController

sealed class Screen(
    val route: String,
    val name: String,
    val isTopLevelDestination: Boolean = false,
) {
  object Auth : Screen(route = "auth", name = "Authentication")

  object Dashboard : Screen(route = "dashboard", name = "Dashboard", isTopLevelDestination = true)

  object Preferences : Screen(route = "preferences", name = "Preferences")

  object Calendar : Screen(route = "calendar", name = "Calendar", isTopLevelDestination = true)

  object GroupSession :
      Screen(route = "group_session", name = "GroupSession", isTopLevelDestination = true)
}

open class NavigationActions(
    private val navController: NavHostController,
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
    navController.navigate(screen.route) {
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

  /** Navigate back to the previous screen. */
  open fun goBack() {
    navController.popBackStack()
  }

  /**
   * Get the current route of the navigation controller.
   *
   * @return The current route
   */
  open fun currentRoute(): String {
    return navController.currentDestination?.route ?: ""
  }
}
