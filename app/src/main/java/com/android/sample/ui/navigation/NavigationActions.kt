package com.android.sample.ui.navigation

import androidx.navigation.NavHostController

sealed class Screen(
    val route: String,
    val name: String,
    val isTopLevelDestination: Boolean = false,
) {
  object Auth : Screen("auth", "Authentication")

  object Dashboard : Screen("dashboard", "Dashboard", true)

  object Preferences : Screen("preferences", "Preferences")

  object Calendar : Screen("calendar", "Calendar", true)

  object GroupSession : Screen("group_session", "GroupSession", true)
}

open class NavigationActions(private val navController: NavHostController) {
  open fun navigateTo(screen: Screen) {
    if (screen.isTopLevelDestination && currentRoute() == screen.route) return
    navController.navigate(screen.route) {
      if (screen.isTopLevelDestination) {
        launchSingleTop = true
        popUpTo(screen.route) {
          inclusive = true
          saveState = false
        }
      }
      if (screen !is Screen.Auth) restoreState = true
    }
  }

  open fun goBack() {
    navController.popBackStack()
  }

  open fun currentRoute(): String = navController.currentDestination?.route ?: ""
}
