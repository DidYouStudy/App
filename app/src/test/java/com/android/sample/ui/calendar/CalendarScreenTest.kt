// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** Covers the calendar screen's scaffold, displayed state, and action callbacks. */
@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Shows the calendar chrome and routes navigation clicks. */
  @Test
  fun screenShowsCalendarChromeAndHandlesNavigationClick() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val navigationActions = mock<NavigationActions>()
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
          navigationActions = navigationActions,
      )
    }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.calendar_title))
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Calendar)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(CalendarTestTags.TODAY_BUTTON)
        .assertIsDisplayed()
        .assertHasClickAction()
    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_today_label))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CalendarTestTags.ADD_BUTTON)
        .assertHasClickAction()
        .assertContentDescriptionEquals("Add calendar event")

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Dashboard)).performClick()
    verify(navigationActions).navigateTo(Screen.Dashboard)
  }

  /** Keeps the Today button visible and forwards its click to the route callback. */
  @Test
  fun todayButtonIsVisibleAndForwardsClick() {
    var todayClicks = 0
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = { todayClicks++ },
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.TODAY_BUTTON).assertIsDisplayed().performClick()

    composeTestRule.runOnIdle { assertEquals(1, todayClicks) }
  }

  /** Shows the error message and forwards the Retry button click. */
  @Test
  fun eventLoadFailureShowsRetryButton() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var retryClicks = 0
    composeTestRule.setContent {
      CalendarScreen(
          uiState =
              calendarUiState(
                  LocalDate.of(2026, 10, 28),
                  error = CalendarViewModelError.LOAD_EVENTS_FAILED,
              ),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = { retryClicks++ },
      )
    }

    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_load_events_error))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_retry_button))
        .assertIsDisplayed()
        .performClick()
    composeTestRule.runOnIdle { assertEquals(1, retryClicks) }
  }

  /** Creates predictable weekly display state for screen tests. */
  private fun calendarUiState(
      today: LocalDate,
      error: CalendarViewModelError? = null,
  ): CalendarUiState {
    val weekDates = CalendarDateHelper().weekDates(today)
    return CalendarUiState(
        today = today,
        weekStart = weekDates.first(),
        weekDates = weekDates,
        isLoading = false,
        error = error,
    )
  }
}
