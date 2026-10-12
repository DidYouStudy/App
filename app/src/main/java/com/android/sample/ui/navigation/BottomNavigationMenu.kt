package com.android.sample.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight

sealed class Tab(val name: String, val icon: ImageVector, val destination: Screen) {
  object Dashboard : Tab("Dashboard", Icons.Outlined.Timeline, Screen.Dashboard)

  object Calendar : Tab("Calendar", Icons.Outlined.CalendarMonth, Screen.Calendar)

  object GroupSession : Tab("GroupSession", Icons.Outlined.Groups, Screen.GroupSession)
}

private val tabs =
    listOf(
        Tab.Dashboard,
        Tab.Calendar,
        Tab.GroupSession,
    )

@Composable
fun BottomNavigationMenu(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
  NavigationBar(
      modifier = modifier.fillMaxWidth(),
      containerColor = MaterialTheme.colorScheme.surface,
      content = {
        tabs.forEach { tab ->
          val selected = tab == selectedTab
          NavigationBarItem(
              icon = { Icon(tab.icon, contentDescription = null) },
              label = {
                Text(
                    text = tab.name,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
              },
              selected = selected,
              onClick = { onTabSelected(tab) },
              modifier = Modifier.testTag(NavigationTestTags.getTabTestTag(tab)),
          )
        }
      },
  )
}
