// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.DidYouStudyApp
import com.android.sample.R
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import com.android.sample.ui.auth.TestAuthRepository
import com.android.sample.ui.dashboard.DashboardContentTestTags
import com.android.sample.ui.preferences.PreferencesScreenTestTags
import com.android.sample.ui.profile.ProfileButtonTestTags
import com.android.sample.ui.settings.SettingsScreenTestTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Checks, through the production navigation graph, that every placeholder action wired in
 * `DidYouStudyApp` shows the "not implemented" toast instead of doing nothing.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class NotImplementedToastNavigationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val userId = "not-implemented-user"
  private val expectedMessage =
      ApplicationProvider.getApplicationContext<Context>().getString(R.string.toast_not_implemented)

  /** Starts a signed-in, onboarded session so the app opens on the Dashboard. */
  @Before
  fun openDashboard() {
    val auth =
        TestAuthRepository().apply { currentUser.value = UserAccount(userId, null, null, null) }
    val preferences = mock<PreferencesRepository>()
    runBlocking {
      whenever(preferences.getPreferences(userId))
          .thenReturn(Result.success(UserPreferences(userId)))
    }
    AuthRepositoryProvider.setRepository(auth)
    PreferencesRepositoryProvider.setRepository(preferences)
    ProfileRepositoryProvider.setRepository(mock<ProfileRepository>())
    EventsRepositoryProvider.setRepository(InMemoryEventsRepository())
    GroupSessionsRepositoryProvider.setRepository(InMemoryGroupSessionsRepository())

    composeRule.setContent { DidYouStudyApp() }
    composeRule.waitUntil(timeoutMillis = 5_000) { exists(DashboardContentTestTags.YOUR_DAY_CARD) }
  }

  /** Clears the provider overrides so each test starts with a fresh signed-in session. */
  @After
  fun clearProviders() {
    AuthRepositoryProvider.setRepository(null)
    PreferencesRepositoryProvider.setRepository(null)
    ProfileRepositoryProvider.setRepository(null)
    EventsRepositoryProvider.setRepository(null)
    GroupSessionsRepositoryProvider.setRepository(null)
  }

  /** Both Dashboard cards show the toast. */
  @Test
  fun dashboardCardsShowNotImplementedToast() {
    tapAndExpectToast(DashboardContentTestTags.YOUR_DAY_CARD, expectedCount = 1)
    tapAndExpectToast(DashboardContentTestTags.STATS_CARD, expectedCount = 2)
  }

  /** Username, profile picture and friends actions in Settings show the toast. */
  @Test
  fun settingsPlaceholderActionsShowNotImplementedToast() {
    openSettings()

    tapAndExpectToast(SettingsScreenTestTags.EDIT_USERNAME_BUTTON, expectedCount = 1)
    tapAndExpectToast(SettingsScreenTestTags.CHANGE_PROFILE_PICTURE_BUTTON, expectedCount = 2)
    tapAndExpectToast(SettingsScreenTestTags.FRIENDS_BUTTON, expectedCount = 3)
  }

  /** Adding study locations in Preferences (opened from Settings) shows the toast. */
  @Test
  fun preferencesAddLocationShowsNotImplementedToast() {
    openSettings()
    composeRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      exists(PreferencesScreenTestTags.ADD_STUDY_LOCATION_BUTTON)
    }

    // Preferences is a long form, so bring the button on screen first.
    composeRule.onNodeWithTag(PreferencesScreenTestTags.ADD_STUDY_LOCATION_BUTTON).performScrollTo()
    tapAndExpectToast(PreferencesScreenTestTags.ADD_STUDY_LOCATION_BUTTON, expectedCount = 1)
  }

  /** Opens Settings from the Dashboard profile button. */
  private fun openSettings() {
    composeRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) { exists(SettingsScreenTestTags.SCREEN) }
  }

  /** Taps [tag], then checks that a new toast with the expected text was shown. */
  private fun tapAndExpectToast(tag: String, expectedCount: Int) {
    composeRule.onNodeWithTag(tag).performClick()
    composeRule.waitForIdle()

    assertEquals(expectedCount, ShadowToast.shownToastCount())
    assertEquals(expectedMessage, ShadowToast.getTextOfLatestToast())
  }

  /** Whether a node with [tag] is currently in the semantics tree. */
  private fun exists(tag: String): Boolean =
      composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}
