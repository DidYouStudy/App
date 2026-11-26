// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.dashboard

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.ui.auth.TestAuthRepository
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    AuthRepositoryProvider.setRepository(TestAuthRepository())
    EventsRepositoryProvider.setRepository(InMemoryEventsRepository())
    GroupSessionsRepositoryProvider.setRepository(InMemoryGroupSessionsRepository())
    PreferencesRepositoryProvider.setRepository(FakePreferencesRepository())
  }

  @After
  fun tearDown() {
    AuthRepositoryProvider.setRepository(null)
    EventsRepositoryProvider.setRepository(null)
    GroupSessionsRepositoryProvider.setRepository(null)
    PreferencesRepositoryProvider.setRepository(null)
  }

  @Test
  fun screenWiresLocalizedDashboardCardsEmptyStateAndNavigationBars() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardScreen(navigationActions = null, onEventClick = {}) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.YOUR_DAY_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.YOUR_DAY_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_your_day))
    composeTestRule.onNodeWithTag(DashboardContentTestTags.STATS_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.STATS_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_your_stats))
    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.EMPTY_DAY).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun screenAddButtonInvokesCallbackExactlyOnce() {
    var callbackCount = 0
    composeTestRule.setContent {
      DashboardScreen(navigationActions = null, onEventClick = { callbackCount++ })
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()

    assertEquals(1, callbackCount)
  }

  @Test
  fun dashboardFabMenuTogglesAndInvokesEventAndGroupSessionCallbacks() {
    var eventClicks = 0
    var groupSessionClicks = 0

    composeTestRule.setContent {
      DashboardScreen(
          navigationActions = null,
          onEventClick = { eventClicks++ },
          onGroupSessionClick = { groupSessionClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertIsDisplayed()
        .performClick()
    assertEquals(1, eventClicks)

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertIsDisplayed()
        .performClick()
    assertEquals(1, groupSessionClicks)
  }

  @Test
  fun dashboardFabMenuOptionsDisplayLocalizedLabels() {
    composeTestRule.setContent { DashboardScreen(navigationActions = null, onEventClick = {}) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertIsDisplayed()
  }

  @Test
  fun topBarShowsLocalizedBoldDashboardTitle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardScreen(navigationActions = null, onEventClick = {}) }

    val results = mutableListOf<TextLayoutResult>()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_title))
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }

    assertEquals(FontWeight.Bold, results.single().layoutInput.style.fontWeight)
  }

  @Test
  fun defaultEventClickNavigatesToAddEvent() {
    val navActions = mock(NavigationActions::class.java)
    composeTestRule.setContent { DashboardScreen(navigationActions = navActions) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()

    verify(navActions).navigateTo(Screen.AddEvent)
  }

  @Test
  fun defaultGroupSessionClickNavigatesToAddGroupSession() {
    val navActions = mock(NavigationActions::class.java)
    composeTestRule.setContent { DashboardScreen(navigationActions = navActions) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .performClick()

    verify(navActions).navigateTo(Screen.AddGroupSession)
  }

  @Test
  fun fabMenuClickWithNullNavigationActionsDoesNotCrash() {
    composeTestRule.setContent { DashboardScreen(navigationActions = null) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .performClick()
  }

  @Test
  fun loadingStateDisplaysLoadingIndicator() {
    composeTestRule.setContent {
      DashboardScreen(
          uiState = DashboardUiState(isLoading = true),
          navigationActions = null,
      )
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.CONTENT).assertDoesNotExist()
  }

  @Test
  fun errorStateDisplaysLocalizedErrorMessageAndRetryInvokesCallback() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var retryCount = 0

    composeTestRule.setContent {
      DashboardScreen(
          uiState =
              DashboardUiState(
                  isLoading = false,
                  error = DashboardViewModelError.LOAD_SESSIONS_FAILED,
              ),
          onRetry = { retryCount++ },
          navigationActions = null,
      )
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ERROR_CONTAINER).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.ERROR_TEXT)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.dashboard_load_sessions_error))
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.RETRY_BUTTON)
        .assertIsDisplayed()
        .performClick()

    assertEquals(1, retryCount)
  }

  @Test
  fun populatedStateDisplaysDashboardSessions() {
    val sessions =
        listOf(
            DashboardSession(
                startTime = "09:00",
                endTime = "10:30",
                subject = "Calculus",
                eventType = EventType.STUDY_SESSION,
                location = "BC 01",
            ),
            DashboardSession(
                startTime = "14:00",
                endTime = "15:30",
                subject = "Physics",
                eventType = EventType.EXERCISE_SESSION,
            ),
        )

    composeTestRule.setContent {
      DashboardScreen(
          uiState = DashboardUiState(sessions = sessions, isLoading = false),
          navigationActions = null,
      )
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.sessionItem(0)).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.sessionTime(0), useUnmergedTree = true)
        .assertTextEquals("09:00 - 10:30")
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.sessionLocation(0), useUnmergedTree = true)
        .assertTextEquals("BC 01")
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.sessionSubject(0), useUnmergedTree = true)
        .assertTextEquals("Calculus")
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.sessionType(0), useUnmergedTree = true)
        .assertIsDisplayed()

    composeTestRule.onNodeWithTag(DashboardContentTestTags.sessionItem(1)).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.sessionTime(1), useUnmergedTree = true)
        .assertTextEquals("14:00 - 15:30")
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.sessionLocation(1), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun sessionClickInvokesCallbackWithSessionId() {
    val sessions =
        listOf(
            DashboardSession(
                id = "session-1",
                startTime = "09:00",
                endTime = "10:30",
                subject = "Calculus",
                eventType = EventType.STUDY_SESSION,
            ),
        )
    var clickedId: String? = null
    composeTestRule.setContent {
      DashboardScreen(
          uiState = DashboardUiState(sessions = sessions, isLoading = false),
          navigationActions = null,
          onEventCardClick = { clickedId = it },
      )
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.sessionItem(0)).performClick()
    assertEquals("session-1", clickedId)
  }

  @Test
  fun defaultSessionClickNavigatesToEventDetails() {
    val navActions = mock(NavigationActions::class.java)
    val sessions =
        listOf(
            DashboardSession(
                id = "session-42",
                startTime = "09:00",
                endTime = "10:30",
                subject = "Calculus",
                eventType = EventType.STUDY_SESSION,
            ),
        )
    composeTestRule.setContent {
      DashboardScreen(
          uiState = DashboardUiState(sessions = sessions, isLoading = false),
          navigationActions = navActions,
      )
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.sessionItem(0)).performClick()
    verify(navActions)
        .navigateTo(org.mockito.kotlin.argThat { route == Screen.EventDetails("session-42").route })
  }

  @Test
  fun sessionClickWithNullNavigationActionsDoesNotCrash() {
    val sessions =
        listOf(
            DashboardSession(
                id = "session-42",
                startTime = "09:00",
                endTime = "10:30",
                subject = "Calculus",
                eventType = EventType.STUDY_SESSION,
            ),
        )
    composeTestRule.setContent {
      DashboardScreen(
          uiState = DashboardUiState(sessions = sessions, isLoading = false),
          navigationActions = null,
      )
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.sessionItem(0)).performClick()
  }

  @Test
  fun dashboardScreenPreviewRendersWithoutCrash() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashBoardScreenPreview() }
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_title))
  }

  private class FakePreferencesRepository : PreferencesRepository {
    override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
        Result.success(null)

    override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> =
        Result.success(Unit)
  }
}
