// Co-authored-by: Copilot
// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini AI Agent
// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.android.sample.DidYouStudyApp
import com.android.sample.MainActivity
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.event.PersonalEvent
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import com.android.sample.testing.RepositoryOverridesRule
import com.android.sample.ui.auth.SignInScreenTestTags
import com.android.sample.ui.calendar.CalendarTestTags
import com.android.sample.ui.dashboard.DashboardContentTestTags
import com.android.sample.ui.eventplanning.EventDetailsScreenTestTags
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags
import com.android.sample.ui.groupsession.GroupSessionScreenTestTags
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.preferences.PreferencesScreenTestTags
import com.android.sample.ui.profile.ProfileButtonTestTags
import com.android.sample.ui.settings.SettingsScreenTestTags
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

// some tests inspired from NavigationB1Test.kt from the bootcamp repo
// TODO add firebase conditions for testing + new screens navigation tests
// TODO state management tests for the new screens
class NavigationTests {
  private val composeTestRule = createAndroidComposeRule<ComponentActivity>()
  private val userId = "navigation-test-user"
  private val events = InMemoryEventsRepository()

  // Fresh fakes for every test, installed before the app starts and removed after it ends.
  @get:Rule
  val rules: RuleChain =
      RuleChain.outerRule(signedInRepositories(userId, events)).around(composeTestRule)

  @Before
  fun setUp() {
    composeTestRule.setContent { DidYouStudyApp() }
  }

  private fun checkDashboardScreenIsDisplayed() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("dashboard", substring = true, ignoreCase = true)
  }

  private fun checkCalendarScreenIsDisplayed() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("calendar", substring = true, ignoreCase = true)
  }

  private fun checkGroupSessionScreenIsDisplayed() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("Group Sessions", substring = false, ignoreCase = true)
  }

  private fun checkSettingsScreenIsDisplayed() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("settings", substring = true, ignoreCase = true)
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertDoesNotExist()
  }

  private fun checkPreferencesScreenIsDisplayed() {
    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.PREFERENCE_SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()
  }

  private fun checkAddEventScreenIsDisplayed() {
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.SCREEN_CONTAINER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.TOP_BAR_TITLE).assertIsDisplayed()
  }

  private fun pressBack(shouldFinish: Boolean) {
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitUntil { composeTestRule.activity.isFinishing == shouldFinish }
    assertEquals(shouldFinish, composeTestRule.activity.isFinishing)
  }

  @Test
  fun testTagsAreCorrectlySet() {
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationIsDisplayedForDashboard() {
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationIsDisplayedForCalendar() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationIsDisplayedForGroupSession() {
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun tabsAreClickable() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.DASHBOARD_TAB)
        .assertIsDisplayed()
        .performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.CALENDAR_TAB)
        .assertIsDisplayed()
        .performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB)
        .assertIsDisplayed()
        .performClick()
  }

  @Test
  fun topBarTitleIsCorrectForDashboard() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains(value = "Dashboard")
  }

  @Test
  fun topBarTitleIsCorrectForCalendar() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains(value = "Calendar")
  }

  @Test
  // TODO change to SignIn when VM can be created as default parameter
  fun navigationStartsOnDashboardTab() {
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun canNavigateToCalendar() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
  }

  @Test
  fun canNavigateToGroupSession() {
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    checkGroupSessionScreenIsDisplayed()
  }

  @Test
  fun canNavigateToCalendarAndBackToDashboardUsingSystemBack() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    pressBack(shouldFinish = false)
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun canNavigateToGroupSessionAndBackToDashboardUsingSystemBack() {
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    checkGroupSessionScreenIsDisplayed()
    pressBack(shouldFinish = false)
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun canNavigateBetweenTabs() {
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).performClick()
    checkDashboardScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    checkGroupSessionScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).performClick()
    checkDashboardScreenIsDisplayed()
  }

  /**
   * This test verifies that the following navigation flow works as expected:
   * - Dashboard -> Calendar -> Calendar
   * - Press Back -> Dashboard (and not Calendar)
   */
  @Test
  fun navigateToCalendarFromCalendarAndPressBackGoesToDashboard() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    pressBack(shouldFinish = false)
    assertFalse(composeTestRule.activity.isFinishing)
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun pressingBackFromDashboardFinishesActivity() {
    checkDashboardScreenIsDisplayed()
    pressBack(shouldFinish = true)
  }

  @Test
  fun profileButtonOpensSettingsAndBackButtonReturnsToDashboard() {
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    checkSettingsScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun repeatedSettingsBackButtonPressesKeepDashboardVisible() {
    repeat(2) {
      composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
      checkSettingsScreenIsDisplayed()
      composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
      checkDashboardScreenIsDisplayed()
    }
  }

  @Test
  fun profileButtonOpensSettingsFromCalendarAndBackReturnsToCalendarThenDashboard() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    checkSettingsScreenIsDisplayed()
    pressBack(shouldFinish = false)
    checkCalendarScreenIsDisplayed()
    pressBack(shouldFinish = false)
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun settingsPreferencesBackNavigationReturnsToPreviousScreen() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    checkSettingsScreenIsDisplayed()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()
    checkPreferencesScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    checkSettingsScreenIsDisplayed()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()
    checkPreferencesScreenIsDisplayed()
    pressBack(shouldFinish = false)
    checkSettingsScreenIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    checkCalendarScreenIsDisplayed()
    pressBack(shouldFinish = false)
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun repeatedlyOpeningPreferencesAndPressingBackDoesNotBlockOrBlankTheApp() {
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    checkSettingsScreenIsDisplayed()

    repeat(5) {
      composeTestRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()
      checkPreferencesScreenIsDisplayed()
      composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
      checkSettingsScreenIsDisplayed()
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    checkDashboardScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun rapidBackArrowPressesFromSettingsKeepDashboardResponsive() {
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    checkSettingsScreenIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performTouchInput {
      repeat(5) { click(center) }
    }

    checkDashboardScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
  }

  @Test
  fun rapidBackArrowPressesFromPreferencesReturnToDashboardWithoutBlanking() {
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    checkSettingsScreenIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()
    checkPreferencesScreenIsDisplayed()

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performTouchInput {
      repeat(5) { click(center) }
    }

    checkDashboardScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
  }

  @Test
  fun canNavigateToAddEventFromDashboardFabMenu() {
    checkDashboardScreenIsDisplayed()

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()

    checkAddEventScreenIsDisplayed()
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performClick()
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun canNavigateToAddGroupSessionFromDashboardFabMenu() {
    checkDashboardScreenIsDisplayed()

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .performClick()

    checkAddEventScreenIsDisplayed()
    composeTestRule
        .onNodeWithTag(EventPlanningScreenTestTags.TOP_BAR_TITLE)
        .assertTextContains(
            composeTestRule.activity.getString(com.android.sample.R.string.group_session_add_title)
        )
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.ADD_TASK_BUTTON).assertDoesNotExist()

    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performClick()
    checkDashboardScreenIsDisplayed()
  }

  /** Verifies the Calendar FAB menu opens group-session creation and returns to Calendar. */
  @Test
  fun canNavigateToAddGroupSessionFromCalendarFabMenu() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .performClick()

    checkAddEventScreenIsDisplayed()
    composeTestRule
        .onNodeWithTag(EventPlanningScreenTestTags.TOP_BAR_TITLE)
        .assertTextContains(
            composeTestRule.activity.getString(com.android.sample.R.string.group_session_add_title)
        )
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.ADD_TASK_BUTTON).assertDoesNotExist()

    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performClick()
    checkCalendarScreenIsDisplayed()
  }

  @Test
  fun canNavigateToAddEventFromCalendarFabAndBackToCalendar() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()
    checkAddEventScreenIsDisplayed()

    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performClick()
    checkCalendarScreenIsDisplayed()
  }

  @Test
  fun rapidBackArrowPressesFromAddEventOpenedFromCalendarReturnToCalendarWithoutBlanking() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON).performClick()
    checkAddEventScreenIsDisplayed()

    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performTouchInput {
      repeat(5) { click(center) }
    }

    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  /** Repeated taps across the event flow create one Details entry and one Edit entry. */
  @Test
  fun rapidEventCardEditSaveAndBackTapsReturnToCalendar() {
    val cardTag = addCalendarEvent()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()

    tapRepeatedly(cardTag)
    tapRepeatedly(EventDetailsScreenTestTags.EDIT_BUTTON)
    tapRepeatedly(EventPlanningScreenTestTags.SAVE_BUTTON)
    tapRepeatedly(EventDetailsScreenTestTags.BACK_BUTTON)

    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(cardTag).assertIsDisplayed()
  }

  /** Repeated Delete taps from Details and Edit each return to the Calendar. */
  @Test
  fun rapidDeleteTapsFromEitherEventScreenReturnToCalendar() {
    val cardTag = addCalendarEvent()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    composeTestRule.onNodeWithTag(cardTag).performClick()
    tapRepeatedly(EventDetailsScreenTestTags.DELETE_BUTTON)

    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(cardTag).assertDoesNotExist()

    val secondCardTag = addCalendarEvent()
    composeTestRule.onNodeWithTag(secondCardTag).performClick()
    composeTestRule.onNodeWithTag(EventDetailsScreenTestTags.EDIT_BUTTON).performClick()
    tapRepeatedly(EventPlanningScreenTestTags.DELETE_BUTTON)

    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(secondCardTag).assertDoesNotExist()
  }

  /** Seeds a visible card for today and returns its Calendar tag. */
  private fun addCalendarEvent(): String {
    val today = LocalDate.now()
    val start = today.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant()
    val event =
        PersonalEvent(
            id = events.newEventId(),
            ownerId = userId,
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Navigation test event",
                    startsAt = start,
                    endsAt = start.plus(2, ChronoUnit.HOURS),
                ),
            createdAt = start,
        )
    runBlocking { events.saveEvent(event).getOrThrow() }
    return CalendarTestTags.eventCard(event.id, today.dayOfWeek.value - 1)
  }

  /** Sends a burst of touch events to the same control. */
  private fun tapRepeatedly(tag: String) {
    composeTestRule.onNodeWithTag(tag).performTouchInput { repeat(3) { click(center) } }
  }

  @Test
  fun canNavigateToAddGroupSessionFromGroupSessionFabAndBackToGroupSession() {
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    checkGroupSessionScreenIsDisplayed()

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ADD_BUTTON).performClick()
    checkAddEventScreenIsDisplayed()
    // Verify that the screen is configured for a group session (no tasks section)
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.ADD_TASK_BUTTON).assertDoesNotExist()

    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performClick()
    checkGroupSessionScreenIsDisplayed()
  }

  /** Each editor exit returns to the group session list when the editor opened from that list. */
  @Test
  fun groupSessionEditorReturnsToGroupSessionsAfterBackSaveAndDelete() {
    val now = java.time.Instant.now()
    val session =
        com.android.sample.model.event.GroupSession(
            id = "session-1",
            createdBy = "navigation-test-user",
            memberIds = listOf("navigation-test-user"),
            details =
                com.android.sample.model.event.EventDetails(
                    type = com.android.sample.model.event.EventType.STUDY_SESSION,
                    subject = "Networks",
                    startsAt = now.plus(java.time.Duration.ofHours(1)),
                    endsAt = now.plus(java.time.Duration.ofHours(2)),
                ),
            createdAt = now,
        )
    kotlinx.coroutines.runBlocking {
      com.android.sample.model.groupsession.GroupSessionsRepositoryProvider.getRepository()
          .saveSession(
              "navigation-test-user",
              session,
          )
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    checkGroupSessionScreenIsDisplayed()

    val cardMatcher = hasAnyDescendant(hasText(session.details.subject ?: ""))
    val editButtonMatcher =
        hasText(
            composeTestRule.activity.getString(
                com.android.sample.R.string.group_session_action_edit
            )
        ) and hasAnyAncestor(cardMatcher)
    openGroupSessionEditor(editButtonMatcher)
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performClick()
    checkGroupSessionScreenIsDisplayed()

    openGroupSessionEditor(editButtonMatcher)
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.SAVE_BUTTON).performClick()
    checkGroupSessionScreenIsDisplayed()

    openGroupSessionEditor(editButtonMatcher)
    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.DELETE_BUTTON).performClick()
    checkGroupSessionScreenIsDisplayed()
    composeTestRule.onNode(editButtonMatcher).assertDoesNotExist()
  }

  /** Opens the shared editor through the group's own card and verifies its kind. */
  private fun openGroupSessionEditor(editButtonMatcher: SemanticsMatcher) {
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(editButtonMatcher)
    composeTestRule.onNode(editButtonMatcher).performClick()

    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.SCREEN_CONTAINER).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(EventPlanningScreenTestTags.TOP_BAR_TITLE)
        .assertTextContains(
            composeTestRule.activity.getString(com.android.sample.R.string.group_session_edit_title)
        )
  }
}

/** Verifies that the real activity hosts the Compose navigation graph. */
class MainActivityNavigationTests {
  private val composeTestRule = createAndroidComposeRule<MainActivity>()

  // Installed per test, before MainActivity launches, so one test's logout cannot leak into the
  // next test's signed-in session.
  @get:Rule
  val rules: RuleChain =
      RuleChain.outerRule(signedInRepositories("main-activity-test-user")).around(composeTestRule)

  @Test
  fun activityStartsOnDashboardForAnAuthenticatedUser() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("Dashboard")
  }

  @Test
  fun loggingOutFromSettingsSignsOutAndNavigatesToSignIn() {
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = 20_000) {
      composeTestRule
          .onAllNodesWithTag(SignInScreenTestTags.SCREEN)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertDoesNotExist()

    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitUntil(timeoutMillis = 20_000) { composeTestRule.activity.isFinishing }
    assertEquals(true, composeTestRule.activity.isFinishing)
  }
}

/**
 * Fakes for a signed-in user [uid] who already saved preferences, so the app opens on the
 * dashboard. No repository falls back to the real Firebase project.
 */
private fun signedInRepositories(
    uid: String,
    events: EventsRepository = InMemoryEventsRepository(),
): RepositoryOverridesRule {
  val user = UserAccount(uid, null, null, null)
  return RepositoryOverridesRule(
      auth = FakeAuthRepository(user),
      preferences = FakePreferencesRepository(UserPreferences(userId = user.uid)),
      profile = FakeProfileRepository(),
      events = events,
      groupSessions = InMemoryGroupSessionsRepository(),
  )
}

private class FakeAuthRepository(private val user: UserAccount) : AuthRepository {
  private val currentUserState = MutableStateFlow<UserAccount?>(user)
  override val currentUser: StateFlow<UserAccount?> = currentUserState

  override suspend fun signInWithGoogle(idToken: String): UserAccount = user

  override suspend fun signOut() {
    currentUserState.value = null
  }
}

private class FakePreferencesRepository(
    private val preferences: UserPreferences,
) : PreferencesRepository {
  override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
      Result.success(preferences.takeIf { it.userId == userId })

  override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> =
      Result.success(Unit)
}

private class FakeProfileRepository : ProfileRepository {
  override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(null)

  override suspend fun createProfile(userId: String): Result<UserProfile> =
      Result.failure(UnsupportedOperationException("Profiles are not created in navigation tests"))
}
