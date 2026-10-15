// Co-authored-by: Copilot
// Co-authored-by: Claude AI Agent
package com.android.sample.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.android.sample.DidYouStudyApp
import com.android.sample.MainActivity
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.ui.auth.SignInScreenTestTags
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.profile.ProfileButtonTestTags
import com.android.sample.ui.settings.SettingsScreenTestTags
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

// some tests inspired from NavigationB1Test.kt from the bootcamp repo
// TODO add firebase conditions for testing + new screens navigation tests
// TODO state management tests for the new screens
// TODO remake the last commented out tests so that they pass and doesn't use real fireStore
class NavigationTests {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setUp() {
    installFakeRepositories()
    composeTestRule.setContent { DidYouStudyApp() }
  }

  private fun installFakeRepositories() {
    val user = UserAccount("navigation-test-user", null, null, null)
    setProviderInstance(
        AuthRepositoryProvider,
        FakeAuthRepository(user),
    )
    setProviderInstance(
        PreferencesRepositoryProvider,
        FakePreferencesRepository(UserPreferences(userId = user.uid)),
    )
  }

  private fun setProviderInstance(provider: Any, repository: Any) {
    provider.javaClass.getDeclaredField("instance").apply {
      isAccessible = true
      set(provider, repository)
    }
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

  //  @Test
  //  fun profileButtonOpensSettingsAndBackButtonReturnsToDashboard() {
  //    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
  //    checkSettingsScreenIsDisplayed()
  //    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
  //    checkDashboardScreenIsDisplayed()
  //  }

  //  @Test
  //  fun profileButtonOpensSettingsFromCalendarAndSystemBackReturnsToCalendar() {
  //    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
  //    checkCalendarScreenIsDisplayed()
  //    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
  //    checkSettingsScreenIsDisplayed()
  //    pressBack(shouldFinish = false)
  //    checkCalendarScreenIsDisplayed()
  //  }
}

/** Verifies that the real activity hosts the Compose navigation graph. */
class MainActivityNavigationTests {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

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

  companion object {
    @JvmStatic
    @BeforeClass
    fun installFakeRepositories() {
      val user = UserAccount("main-activity-test-user", null, null, null)
      setProviderInstance(AuthRepositoryProvider, FakeAuthRepository(user))
      setProviderInstance(
          PreferencesRepositoryProvider,
          FakePreferencesRepository(UserPreferences(userId = user.uid)),
      )
    }

    @JvmStatic
    @AfterClass
    fun clearFakeRepositories() {
      setProviderInstance(AuthRepositoryProvider, null)
      setProviderInstance(PreferencesRepositoryProvider, null)
    }

    private fun setProviderInstance(provider: Any, repository: Any?) {
      provider.javaClass.getDeclaredField("instance").apply {
        isAccessible = true
        set(provider, repository)
      }
    }
  }
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
