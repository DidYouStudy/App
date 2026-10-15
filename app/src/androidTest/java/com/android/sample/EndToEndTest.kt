// Co-authored-by: Claude Sonnet 5.5
package com.android.sample

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.preferences.TimeOfDay
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import com.android.sample.model.profile.ProfileResolution
import com.android.sample.model.profile.UserProfile
import com.android.sample.ui.auth.SignInScreenTestTags
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.preferences.PreferencesScreenTestTags as PrefTags
import com.android.sample.ui.preferences.PreferencesScreenTestsUtils.click
import com.android.sample.ui.profile.ProfileButtonTestTags
import com.android.sample.ui.settings.SettingsScreenTestTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain

/**
 * End-to-end tests driving the real [MainActivity] and navigation graph through the whole user
 * journey: sign-in, first-time preferences, dashboard tabs, settings and logout.
 *
 * Only the repositories are faked (no Firebase, Firestore or Google account picker is needed), so
 * the real AppViewModel, navigation, ViewModels and screens are exercised together. Sign-in is
 * simulated by emitting a user from [FakeSessionAuthRepository], which is what the app reacts to
 * after the real Google flow completes.
 */
class EndToEndTest {
  private val environment = FakeEnvironment()
  private val composeRule = createAndroidComposeRule<MainActivity>()

  // The fakes must be installed before MainActivity is launched by the compose rule.
  @get:Rule val rules: RuleChain = RuleChain.outerRule(environment).around(composeRule)

  /** Waits until a node with [tag] exists, so tests tolerate asynchronous navigation. */
  private fun waitForTag(tag: String) {
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  /** Asserts the top bar title of the current top-level screen contains [title]. */
  private fun assertTopBarTitle(title: String) {
    waitForTag(NavigationTestTags.TOP_BAR_TITLE)
    composeRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains(title, substring = true, ignoreCase = true)
  }

  /** Waits for the dashboard to be shown after sign-in or preferences saving. */
  private fun assertOnDashboard() {
    waitForTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
    assertTopBarTitle("Dashboard")
  }

  /** Opens settings through the profile button and checks the settings screen is displayed. */
  private fun openSettings() {
    composeRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    waitForTag(SettingsScreenTestTags.SCREEN)
    composeRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
  }

  /** Logs out from the settings screen and waits for the sign-in screen. */
  private fun logOutFromSettings() {
    composeRule.onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON).performClick()
    waitForTag(SignInScreenTestTags.SCREEN)
    composeRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertIsDisplayed()
    composeRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun firstTimeUser_completesPreferences_usesApp_andLogsOut() {
    // Signed out at launch: the sign-in screen is shown.
    waitForTag(SignInScreenTestTags.SCREEN)
    composeRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsDisplayed()

    // Signing in as a user without preferences leads to the preferences screen.
    environment.auth.signInAs(USER)
    waitForTag(PrefTags.PREFERENCE_SCREEN)
    composeRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertDoesNotExist()

    // Edit the draft and save: preferences are persisted and the dashboard opens.
    composeRule.click(PrefTags.STUDY_TIME_EVENING)
    composeRule.click(PrefTags.SESSION_LENGTH_2_HOURS)
    composeRule.click(PrefTags.BREAK_DURATION_15_MIN)
    composeRule.click(PrefTags.BREAK_FREQUENCY_1_HOUR)
    composeRule.onNodeWithTag(PrefTags.SAVE_BUTTON).performClick()
    assertOnDashboard()
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_SCREEN).assertDoesNotExist()
    assertEquals(
        UserPreferences(
            userId = USER.uid,
            preferredStudyTimes = listOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON, TimeOfDay.EVENING),
            sessionLengthMinutes = 120,
            breakLengthMinutes = 15,
            breakFrequencyMinutes = 60,
        ),
        environment.preferences.saved[USER.uid],
    )

    // Walk through every bottom navigation tab and come back.
    composeRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    assertTopBarTitle("Calendar")
    composeRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    assertTopBarTitle("Group Sessions")
    composeRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).performClick()
    assertOnDashboard()

    // Settings opens over the dashboard and the back button returns to it.
    openSettings()
    composeRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertDoesNotExist()
    composeRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    assertOnDashboard()

    // Logging out signs the user out and returns to the sign-in screen.
    openSettings()
    logOutFromSettings()
    assertNull(environment.auth.currentUserValue)
  }

  @Test
  fun returningUser_skipsPreferences_andCanSignInAgainAfterLogout() {
    environment.preferences.saved[USER.uid] = UserPreferences(userId = USER.uid)
    environment.auth.signInAs(USER)

    // A restored session with saved preferences goes straight to the dashboard.
    assertOnDashboard()
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_SCREEN).assertDoesNotExist()

    // After logging out, signing in again still skips the preferences screen.
    openSettings()
    logOutFromSettings()
    environment.auth.signInAs(USER)
    assertOnDashboard()
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_SCREEN).assertDoesNotExist()
    composeRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun firstTimeUser_backFromDashboardLeavesApp_insteadOfReturningToPreferences() {
    environment.auth.signInAs(USER)
    waitForTag(PrefTags.PREFERENCE_SCREEN)
    composeRule.onNodeWithTag(PrefTags.SAVE_BUTTON).performClick()
    assertOnDashboard()

    // The completed preferences flow was removed from the back stack, so back exits the app.
    composeRule.activityRule.scenario.onActivity { activity ->
      activity.onBackPressedDispatcher.onBackPressed()
    }
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { composeRule.activity.isFinishing }
    assertEquals(true, composeRule.activity.isFinishing)
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
    val USER = UserAccount("end-to-end-user", "End To End", "e2e@example.com", null)
  }
}

/**
 * Test rule that swaps the app's process-wide repository providers for in-memory fakes before the
 * activity starts, and restores the real ones afterwards.
 */
private class FakeEnvironment : ExternalResource() {
  val auth = FakeSessionAuthRepository()
  val preferences = InMemoryPreferencesRepository()
  private val profiles = EmptyProfileRepository()

  override fun before() {
    setProviderInstance(AuthRepositoryProvider, auth)
    setProviderInstance(PreferencesRepositoryProvider, preferences)
    setProviderInstance(ProfileRepositoryProvider, profiles)
  }

  override fun after() {
    setProviderInstance(AuthRepositoryProvider, null)
    setProviderInstance(PreferencesRepositoryProvider, null)
    setProviderInstance(ProfileRepositoryProvider, null)
  }

  /** The providers expose no injection point, so their private `instance` field is set directly. */
  private fun setProviderInstance(provider: Any, repository: Any?) {
    provider.javaClass.getDeclaredField("instance").apply {
      isAccessible = true
      set(provider, repository)
    }
  }
}

/** Auth fake whose session starts signed out and changes only through [signInAs]/[signOut]. */
private class FakeSessionAuthRepository : AuthRepository {
  private val session = MutableStateFlow<UserAccount?>(null)
  override val currentUser: StateFlow<UserAccount?> = session

  /** Current session value, for assertions. */
  val currentUserValue: UserAccount?
    get() = session.value

  /** Simulates a completed Google sign-in (or a restored session). */
  fun signInAs(user: UserAccount) {
    session.value = user
  }

  override suspend fun signInWithGoogle(idToken: String): UserAccount =
      checkNotNull(session.value) { "Use signInAs() to simulate signing in" }

  override suspend fun signOut() {
    session.value = null
  }
}

/** Preferences fake backed by a map, so saved values can be asserted. */
private class InMemoryPreferencesRepository : PreferencesRepository {
  val saved = mutableMapOf<String, UserPreferences>()

  override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
      Result.success(saved[userId])

  override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> {
    saved[preferences.userId] = preferences
    return Result.success(Unit)
  }
}

/** Profile fake with no stored profile, so the settings screen falls back to the auth account. */
private class EmptyProfileRepository : ProfileRepository {
  override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(null)

  override suspend fun createProfile(userId: String): Result<ProfileResolution> =
      Result.failure(UnsupportedOperationException("Profiles are not created in this test"))
}
