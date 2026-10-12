// Co-authored-by: Claude AI Agent
package com.android.sample.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.ui.calendar.CalendarScreen
import com.android.sample.ui.dashboard.DashboardScreen
import com.android.sample.ui.groupsession.GroupSessionScreen
import com.android.sample.ui.profile.ProfileButtonTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
class TopLevelScreensProfileButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  private fun assertProfileButtonOpensSettings(screen: @Composable (NavigationActions?) -> Unit) {
    val navigationActions = mock<NavigationActions>()
    composeTestRule.setContent { screen(navigationActions) }

    composeTestRule
        .onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON)
        .assertIsDisplayed()
        .performClick()

    verify(navigationActions).navigateTo(Screen.Settings)
    verifyNoMoreInteractions(navigationActions)
  }

  @Test
  fun dashboardProfileButtonOpensSettings() {
    assertProfileButtonOpensSettings { DashboardScreen(navigationActions = it) }
  }

  @Test
  fun calendarProfileButtonOpensSettings() {
    assertProfileButtonOpensSettings { CalendarScreen(navigationActions = it) }
  }

  @Test
  fun groupSessionProfileButtonOpensSettings() {
    assertProfileButtonOpensSettings { GroupSessionScreen(navigationActions = it) }
  }

  @Test
  fun profileButtonWithoutNavigationActionsDoesNotCrash() {
    composeTestRule.setContent { DashboardScreen() }

    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
  }
}
