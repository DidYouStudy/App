package com.android.sample.ui.navigation

object NavigationTestTags {
  const val BOTTOM_NAVIGATION_MENU = "BottomNavigationMenu"
  const val GO_BACK_BUTTON = "GoBackButton"
  const val TOP_BAR_TITLE = "TopBarTitle"
  const val AUTH_TAB = "AuthTab"
  const val DASHBOARD_TAB = "DashboardTab"
  const val PREFERENCES_TAB = "PreferencesTab"
  const val CALENDAR_TAB = "CalendarTab"
  const val GROUP_SESSION_TAB = "GroupSessionTab"

  fun getTabTestTag(tab: Tab): String =
      when (tab) {
        is Tab.Dashboard -> DASHBOARD_TAB
        is Tab.Calendar -> CALENDAR_TAB
        is Tab.GroupSession -> GROUP_SESSION_TAB
      }
}
