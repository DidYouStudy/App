// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.ui.calendar

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.ui.dashboard.DashboardContentTestTags
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        .onNodeWithContentDescription(context.getString(R.string.dashboard_add_description))
        .assertIsDisplayed()
        .assertHasClickAction()

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

  /** Tapping Event forwards exactly one event callback and closes the menu. */
  @Test
  fun eventMenuOptionInvokesEventCallbackAndClosesMenu() {
    var eventClicks = 0
    var groupSessionClicks = 0
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
          onAddEventClick = { eventClicks++ },
          onAddGroupSessionClick = { groupSessionClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      assertEquals(1, eventClicks)
      assertEquals(0, groupSessionClicks)
    }
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()
  }

  /** Starts collapsed with an accessible add control and no menu options. */
  @Test
  fun addMenuIsCollapsedAtStart() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertExists()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.dashboard_add_description))
        .assertHasClickAction()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()
  }

  /** Opens the add menu and checks the localized option labels. */
  @Test
  fun addMenuOpensWithLocalizedOptions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertExists()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.dashboard_fab_event))
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.dashboard_fab_group_session))
  }

  /** Tapping Group session invokes only its callback and closes the menu. */
  @Test
  fun groupSessionMenuOptionInvokesGroupCallbackAndClosesMenu() {
    var eventClicks = 0
    var groupSessionClicks = 0
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
          onAddEventClick = { eventClicks++ },
          onAddGroupSessionClick = { groupSessionClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      assertEquals(0, eventClicks)
      assertEquals(1, groupSessionClicks)
    }
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()
  }

  /** The second add-button tap closes the menu without invoking either callback. */
  @Test
  fun addButtonClosesMenuWithoutCallingCallbacks() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var eventClicks = 0
    var groupSessionClicks = 0
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
          onAddEventClick = { eventClicks++ },
          onAddGroupSessionClick = { groupSessionClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.cancel))
        .assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cancel)).performClick()
    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
      assertEquals(0, eventClicks)
      assertEquals(0, groupSessionClicks)
    }
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()
  }

  /** Uses the default event callback to navigate to AddEvent. */
  @Test
  fun eventMenuOptionNavigatesByDefault() {
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

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()

    verify(navigationActions).navigateTo(Screen.AddEvent)
  }

  /** Uses the default group-session callback to navigate to AddGroupSession. */
  @Test
  fun groupSessionMenuOptionNavigatesByDefault() {
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

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .performClick()

    verify(navigationActions).navigateTo(Screen.AddGroupSession)
  }

  /** Closes the Event menu option safely when navigation is unavailable. */
  @Test
  fun defaultEventOptionWithoutNavigationDoesNothing() {
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()
  }

  /** Keeps the add menu available while the calendar is in its error state. */
  @Test
  fun addMenuWorksInErrorState() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var eventClicks = 0
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
          onRetry = {},
          onAddEventClick = { eventClicks++ },
      )
    }

    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_load_events_error))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()

    composeTestRule.runOnIdle { assertEquals(1, eventClicks) }
  }

  /** Restores the hidden add menu when a failed load replaces the scrollable grid. */
  @Test
  fun addMenuReappearsWhenErrorReplacesScrolledGrid() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var state by mutableStateOf(calendarUiState(LocalDate.of(2026, 10, 28)))
    var eventClicks = 0
    composeTestRule.setContent {
      CalendarScreen(
          uiState = state,
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
          onAddEventClick = { eventClicks++ },
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertDoesNotExist()

    state =
        calendarUiState(
            LocalDate.of(2026, 10, 28),
            error = CalendarViewModelError.LOAD_EVENTS_FAILED,
        )
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_load_events_error))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertExists()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()
    composeTestRule.runOnIdle { assertEquals(1, eventClicks) }

    state = calendarUiState(LocalDate.of(2026, 10, 28))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertExists()
  }

  /** Keeps the add button visible after the initial automatic grid scroll. */
  @Test
  fun initialGridAutoScrollDoesNotHideAddButton() {
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertExists()
  }

  /** Hides the add button when the user scrolls the time grid down. */
  @Test
  fun addButtonHidesWhenGridScrollsDown() {
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertExists()
    val before =
        composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(12)).getUnclippedBoundsInRoot().top
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    val after =
        composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(12)).getUnclippedBoundsInRoot().top
    assertTrue(after < before)

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertDoesNotExist()
  }

  /** Restores the collapsed add button when the user scrolls the time grid up. */
  @Test
  fun addButtonShowsCollapsedWhenGridScrollsUp() {
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }

    composeTestRule.waitForIdle()
    val before =
        composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(12)).getUnclippedBoundsInRoot().top
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    val afterSwipeUp =
        composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(12)).getUnclippedBoundsInRoot().top
    assertTrue(afterSwipeUp < before)
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).performTouchInput { swipeDown() }
    composeTestRule.waitForIdle()
    val afterSwipeDown =
        composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(12)).getUnclippedBoundsInRoot().top
    assertTrue(afterSwipeDown > afterSwipeUp)

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertExists()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()
  }

  /** Collapses an expanded menu when scrolling hides and restores its add button. */
  @Test
  fun scrollingRestoresAddButtonWithMenuCollapsed() {
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28)),
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).performTouchInput { swipeUp() }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).performTouchInput { swipeDown() }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertExists()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()
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

  /** Renders the standard preview and confirms its calendar content is visible. */
  @Test
  fun calendarScreenPreviewShowsWeeklyCalendar() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { CalendarScreenPreview() }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.calendar_title))
    composeTestRule.onNodeWithTag(CalendarTestTags.TODAY_BUTTON).assertIsDisplayed()
  }

  /** Renders the error preview and confirms its message and retry action are visible. */
  @Test
  fun calendarScreenErrorPreviewShowsLoadErrorAndRetry() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { CalendarScreenErrorPreview() }

    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_load_events_error))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_retry_button))
        .assertIsDisplayed()
  }

  /** S1: a loading week shows the indicator over the grid, with its content description. */
  @Test
  fun weekLoadingShowsIndicatorOverGrid() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    setScreen(calendarUiState(LocalDate.of(2026, 10, 28), isLoading = true))

    composeTestRule
        .onNodeWithTag(CalendarTestTags.LOADING)
        .assertIsDisplayed()
        .assertContentDescriptionEquals(context.getString(R.string.calendar_loading_description))
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).assertIsDisplayed()
  }

  /** S2: a loaded week shows no loading indicator. */
  @Test
  fun loadedWeekHidesLoadingIndicator() {
    setScreen(calendarUiState(LocalDate.of(2026, 10, 28), isLoading = false))

    composeTestRule.onNodeWithTag(CalendarTestTags.LOADING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).assertIsDisplayed()
  }

  /** S4: while a retry runs, only the Retry button's own spinner shows. */
  @Test
  fun retryInProgressShowsOnlyButtonSpinner() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    setScreen(
        calendarUiState(
            LocalDate.of(2026, 10, 28),
            error = CalendarViewModelError.LOAD_EVENTS_FAILED,
            isLoading = true,
        )
    )

    composeTestRule.onNodeWithTag(CalendarTestTags.LOADING).assertDoesNotExist()
    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_load_events_error))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.calendar_retry_button))
        .assertDoesNotExist()
    // The Retry button's own spinner is the only progress indicator on screen.
    composeTestRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
        .assertIsDisplayed()
  }

  /** S5: the indicator returns when another week starts loading after one was loaded. */
  @Test
  fun indicatorReturnsWhenAnotherWeekLoads() {
    val firstWeek = calendarUiState(LocalDate.of(2026, 10, 28), isLoading = true)
    var uiState by mutableStateOf(firstWeek)
    composeTestRule.setContent {
      CalendarScreen(
          uiState = uiState,
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }
    composeTestRule.onNodeWithTag(CalendarTestTags.LOADING).assertIsDisplayed()

    uiState = firstWeek.copy(isLoading = false)
    composeTestRule.onNodeWithTag(CalendarTestTags.LOADING).assertDoesNotExist()

    uiState = calendarUiState(LocalDate.of(2026, 11, 4), isLoading = true)
    composeTestRule.onNodeWithTag(CalendarTestTags.LOADING).assertIsDisplayed()
  }

  /** S6: the week arrows stay clickable while a week loads. */
  @Test
  fun weekNavigationWorksWhileLoading() {
    var previousClicks = 0
    var nextClicks = 0
    composeTestRule.setContent {
      CalendarScreen(
          uiState = calendarUiState(LocalDate.of(2026, 10, 28), isLoading = true),
          onToday = {},
          onPreviousWeek = { previousClicks++ },
          onNextWeek = { nextClicks++ },
          onRetry = {},
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.PREVIOUS_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()

    composeTestRule.runOnIdle {
      assertEquals(1, previousClicks)
      assertEquals(1, nextClicks)
    }
  }

  /** S8: a swipe starting on the indicator still scrolls the grid and hides the add button. */
  @Test
  fun swipeOnLoadingIndicatorScrollsGrid() {
    setScreen(calendarUiState(LocalDate.of(2026, 10, 28), isLoading = true))
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertIsDisplayed()
    val spinner = composeTestRule.onNodeWithTag(CalendarTestTags.LOADING).getUnclippedBoundsInRoot()
    val grid = composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).getUnclippedBoundsInRoot()
    val labelTopBefore =
        composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(12)).getUnclippedBoundsInRoot().top

    // Start the swipe on the indicator's centre, in the grid's own coordinates.
    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).performTouchInput {
      val startY = ((spinner.top + spinner.bottom) / 2 - grid.top).toPx()
      swipeUp(startY = startY, endY = startY - 400f)
    }

    val labelTopAfter =
        composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(12)).getUnclippedBoundsInRoot().top
    assertTrue("grid did not scroll", labelTopAfter < labelTopBefore)
    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).assertDoesNotExist()
  }

  /** S7: the loading preview renders the loading indicator. */
  @Test
  fun calendarScreenLoadingPreviewShowsIndicator() {
    composeTestRule.setContent { CalendarScreenLoadingPreview() }

    composeTestRule.onNodeWithTag(CalendarTestTags.LOADING).assertIsDisplayed()
  }

  /** Renders the screen with no-op callbacks for state-only assertions. */
  private fun setScreen(uiState: CalendarUiState) {
    composeTestRule.setContent {
      CalendarScreen(
          uiState = uiState,
          onToday = {},
          onPreviousWeek = {},
          onNextWeek = {},
          onRetry = {},
      )
    }
  }

  /** Creates predictable weekly display state for screen tests. */
  private fun calendarUiState(
      today: LocalDate,
      error: CalendarViewModelError? = null,
      isLoading: Boolean = false,
  ): CalendarUiState {
    val weekDates = CalendarDateHelper().weekDates(today)
    return CalendarUiState(
        today = today,
        weekStart = weekDates.first(),
        weekDates = weekDates,
        isLoading = isLoading,
        error = error,
    )
  }
}
