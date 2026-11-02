// Co-authored-by: Claude AI Agent
package com.android.sample.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.ui.calendar.CalendarDateHelper
import com.android.sample.ui.calendar.CalendarScreen
import com.android.sample.ui.calendar.CalendarUiState
import com.android.sample.ui.dashboard.DashboardScreen
import com.android.sample.ui.groupsession.GroupSessionScreen
import com.android.sample.ui.profile.ProfileButtonTestTags
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
class TopLevelScreensProfileButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Shows a screen with optional ViewModel state and checks its profile navigation action. */
  private fun <T : ViewModel> assertProfileButtonOpensSettings(
      viewModel: T? = null,
      screen: @Composable (NavigationActions?, T?) -> Unit,
  ) {
    val navigationActions = mock<NavigationActions>()
    composeTestRule.setContent { screen(navigationActions, viewModel) }

    composeTestRule
        .onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON)
        .assertIsDisplayed()
        .performClick()

    verify(navigationActions).navigateTo(Screen.Settings)
    verifyNoMoreInteractions(navigationActions)
  }

  @Test
  fun dashboardProfileButtonOpensSettings() {
    assertProfileButtonOpensSettings<ViewModel> { navigationActions, _ ->
      DashboardScreen(navigationActions = navigationActions)
    }
  }

  @Test
  fun calendarProfileButtonOpensSettings() {
    assertProfileButtonOpensSettings<ViewModel> { navigationActions, _ ->
      val today = LocalDate.now()
      val weekDates = CalendarDateHelper().weekDates(today)
      CalendarScreen(
          uiState =
              CalendarUiState(
                  today = today,
                  weekStart = weekDates.first(),
                  weekDates = weekDates,
              ),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
          navigationActions = navigationActions,
      )
    }
  }

  @Test
  fun groupSessionProfileButtonOpensSettings() {
    assertProfileButtonOpensSettings<ViewModel> { navigationActions, _ ->
      GroupSessionScreen(navigationActions = navigationActions)
    }
  }

  @Test
  fun profileButtonWithoutNavigationActionsDoesNotCrash() {
    composeTestRule.setContent { DashboardScreen() }

    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
  }
}
