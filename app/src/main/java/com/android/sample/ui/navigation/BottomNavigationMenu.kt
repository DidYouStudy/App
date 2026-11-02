// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.navigation

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.android.sample.R

/**
 * A destination of the bottom navigation bar.
 *
 * @property labelRes String resource shown under the tab icon, resolved by the UI so it follows the
 *   device language.
 * @property icon Icon drawn for the tab.
 * @property destination Screen opened when the tab is selected.
 */
sealed class Tab(@StringRes val labelRes: Int, val icon: ImageVector, val destination: Screen) {
  object Dashboard : Tab(R.string.nav_tab_dashboard, Icons.Outlined.Timeline, Screen.Dashboard)

  object Calendar : Tab(R.string.nav_tab_calendar, Icons.Outlined.CalendarMonth, Screen.Calendar)

  object GroupSession :
      Tab(R.string.nav_tab_group_session, Icons.Outlined.Groups, Screen.GroupSession)
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
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
      content = {
        tabs.forEach { tab ->
          val selected = tab == selectedTab
          NavigationBarItem(
              icon = { Icon(tab.icon, contentDescription = null) },
              label = {
                Text(
                    text = stringResource(tab.labelRes),
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
