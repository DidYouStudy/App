// Co-authored-by: Copilot
// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini AI Agent
package com.android.sample.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.android.sample.DidYouStudyApp
import com.android.sample.MainActivity
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import com.android.sample.testing.RepositoryOverridesRule
import com.android.sample.ui.auth.SignInScreenTestTags
import com.android.sample.ui.calendar.CalendarTestTags
import com.android.sample.ui.dashboard.DashboardContentTestTags
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.preferences.PreferencesScreenTestTags
import com.android.sample.ui.profile.ProfileButtonTestTags
import com.android.sample.ui.settings.SettingsScreenTestTags
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

  // Fresh fakes for every test, installed before the app starts and removed after it ends.
  @get:Rule
  val rules: RuleChain =
      RuleChain.outerRule(signedInRepositories("navigation-test-user")).around(composeTestRule)

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
  }

  @Test
  fun canNavigateToAddEventFromCalendarFabAndBackToCalendar() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    checkAddEventScreenIsDisplayed()

    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performClick()
    checkCalendarScreenIsDisplayed()
  }

  @Test
  fun rapidBackArrowPressesFromAddEventOpenedFromCalendarReturnToCalendarWithoutBlanking() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()

    composeTestRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    checkAddEventScreenIsDisplayed()

    composeTestRule.onNodeWithTag(EventPlanningScreenTestTags.BACK_BUTTON).performTouchInput {
      repeat(5) { click(center) }
    }

    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
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
private fun signedInRepositories(uid: String): RepositoryOverridesRule {
  val user = UserAccount(uid, null, null, null)
  return RepositoryOverridesRule(
      auth = FakeAuthRepository(user),
      preferences = FakePreferencesRepository(UserPreferences(userId = user.uid)),
      profile = FakeProfileRepository(),
      events = InMemoryEventsRepository(),
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
