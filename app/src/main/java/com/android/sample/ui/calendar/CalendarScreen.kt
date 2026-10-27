// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.ui.navigation.BottomNavigationMenu
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import com.android.sample.ui.profile.ProfileButton
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import java.time.LocalDate

/** Shows the calendar weekly view inside the app's standard navigation scaffold. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    navigationActions: NavigationActions? = null,
) {
  val today = remember { LocalDate.now() }
  var showTodayChip by remember { mutableStateOf(true) }

  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.calendar_title),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE),
              )
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            actions = {
              if (showTodayChip) {
                Surface(
                    modifier = Modifier.testTag(CalendarTestTags.TODAY_CHIP),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                  Text(
                      text = stringResource(R.string.calendar_today_label),
                      modifier =
                          Modifier.padding(
                              horizontal = Dimens.calenderTodayChipHorizontalPadding,
                              vertical = Dimens.calendarTodayChipVerticalPadding,
                          ),
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.Bold,
                  )
                }

                Spacer(Modifier.width(Spacing.small))
              }
              ProfileButton(onClick = { navigationActions?.navigateTo(Screen.Settings) })
            },
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = Tab.Calendar,
            onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) },
            modifier = Modifier.testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      floatingActionButton = {
        FloatingActionButton(
            onClick = {},
            modifier = Modifier.testTag(CalendarTestTags.ADD_BUTTON),
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = "Add calendar event")
        }
      },
      content = { paddingValues ->
        CalendarWeeklyView(
            today = today,
            onWeekContainsToday = { showTodayChip = it },
            modifier = Modifier.padding(paddingValues),
        )
      },
  )
}

/** Renders a preview of the calendar screen. */
@Preview(showBackground = true)
@Composable
fun CalendarScreenPreview() {
  DidYouStudyTheme { CalendarScreen() }
}
