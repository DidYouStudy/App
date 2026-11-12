// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import androidx.credentials.ClearCredentialStateRequest
import androidx.test.core.app.ApplicationProvider
import com.android.sample.model.auth.AuthRepositoryFirebase
import com.android.sample.model.event.EventsRepositoryFirestore
import com.android.sample.model.groupsession.GroupSessionsRepositoryFirestore
import com.android.sample.model.preferences.PreferencesRepositoryFirestore
import com.android.sample.model.preferences.TimeOfDay
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.profile.ProfileRepositoryFirestore
import com.android.sample.testing.FakeGoogleCredentialManager
import com.android.sample.testing.RepositoryOverridesRule
import com.android.sample.ui.auth.SignInScreenTestTags
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.preferences.PreferencesScreenTestTags as PrefTags
import com.android.sample.ui.preferences.PreferencesScreenTestsUtils.click
import com.android.sample.ui.profile.ProfileButtonTestTags
import com.android.sample.ui.settings.SettingsScreenTestTags
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * End-to-end tests driving the real [MainActivity] through the whole user journey (sign-in,
 * first-time preferences, dashboard tabs, settings, logout) against the real Firebase SDKs talking
 * to the local Firebase emulators (Auth on 9099, Firestore on 8080, reached from the Android
 * emulator at 10.0.2.2).
 *
 * Sign-in goes through the app's real flow: the Google button, the account picker (replaced by
 * [FakeGoogleCredentialManager], which returns a fake ID token), `AuthRepositoryFirebase` and the
 * Auth emulator. Preferences are written and read through the real Firestore SDK and verified
 * directly on the emulator server. Each test uses a uniquely named Firebase app and user, and
 * removes everything it created, so runs are repeatable and never touch production data.
 *
 * Requires: `firebase emulators:start --only auth,firestore` running on the host.
 */
class EndToEndTest {
  private val email = "e2e-${UUID.randomUUID()}@example.test"
  private val picker = FakeGoogleCredentialManager(email)

  // Configured before any SDK use: the emulator endpoints must be set on a fresh instance.
  private val app: FirebaseApp = initializeEmulatorApp("e2e")
  private val auth = FirebaseAuth.getInstance(app).apply { useEmulator(HOST, AUTH_PORT) }
  private val db = FirebaseFirestore.getInstance(app).apply { useEmulator(HOST, FIRESTORE_PORT) }
  private val composeRule = createAndroidComposeRule<MainActivity>()

  // Installs emulator-backed repositories and the fake picker before MainActivity launches. Every
  // repository the screens read is emulator-backed, so no test can fall back to production.
  @get:Rule
  val rules: RuleChain =
      RuleChain.outerRule(
              RepositoryOverridesRule(
                  auth =
                      AuthRepositoryFirebase(auth) {
                        picker.clearCredentialState(ClearCredentialStateRequest())
                      },
                  preferences = PreferencesRepositoryFirestore(db),
                  profile = ProfileRepositoryFirestore(db),
                  events = EventsRepositoryFirestore(db, auth),
                  groupSessions = GroupSessionsRepositoryFirestore(db, auth),
                  credentialManager = picker,
              )
          )
          .around(composeRule)

  /** Releases the SDK state and wipes the emulators' Auth accounts and Firestore documents. */
  @After
  fun cleanup() = runBlocking {
    auth.signOut()
    db.terminate().await()
    db.clearPersistence().await()
    app.delete()
    // Run every wipe even if an earlier one fails, then report all failures together.
    val failures =
        listOf(
                "http://$HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT_ID/accounts",
                "http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID/databases/(default)/documents",
            )
            .mapNotNull { url -> runCatching { clearEmulator(url) }.exceptionOrNull() }
    failures.firstOrNull()?.let { first ->
      failures.drop(1).forEach(first::addSuppressed)
      throw first
    }
  }

  /** Waits until a node with [tag] exists; on timeout logs the UI tree (tag `E2EDUMP`). */
  private fun waitForTag(tag: String) {
    try {
      composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
      }
    } catch (e: ComposeTimeoutException) {
      composeRule.onRoot().printToLog("E2EDUMP")
      throw e
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

  /** Taps the Google button: fake picker -> Firebase Auth emulator, as in production. */
  private fun signInThroughUi() {
    waitForTag(SignInScreenTestTags.SCREEN)
    composeRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).performClick()
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

  /**
   * Creates the Google account's Firebase user and stores [preferences] for it, through a separate
   * Firebase app so the app under test keeps showing the sign-in screen. Returns the stored
   * preferences (with the user's uid), which the emulator reuses when the same Google account signs
   * in through the UI.
   */
  private fun seedReturningUser(preferences: (uid: String) -> UserPreferences): UserPreferences =
      runBlocking {
        val seedApp = initializeEmulatorApp("seed")
        val seedAuth = FirebaseAuth.getInstance(seedApp).apply { useEmulator(HOST, AUTH_PORT) }
        val seedDb =
            FirebaseFirestore.getInstance(seedApp).apply { useEmulator(HOST, FIRESTORE_PORT) }
        try {
          val account = AuthRepositoryFirebase(seedAuth) {}.signInWithGoogle(picker.idToken())
          val stored = preferences(account.uid)
          PreferencesRepositoryFirestore(seedDb).savePreferences(stored).getOrThrow()
          stored
        } finally {
          seedAuth.signOut()
          seedDb.terminate().await()
          seedDb.clearPersistence().await()
          seedApp.delete()
        }
      }

  /** Reads the preferences document from the emulator server, bypassing the local cache. */
  private fun storedPreferences(uid: String): UserPreferences? = runBlocking {
    db.collection("preferences")
        .document(uid)
        .get(Source.SERVER)
        .await()
        .toObject(UserPreferences::class.java)
  }

  @Test
  fun firstTimeUser_completesPreferences_usesApp_andLogsOut() {
    // Signed out at launch: the sign-in screen is shown and the Auth emulator has no session.
    waitForTag(SignInScreenTestTags.SCREEN)
    composeRule.onNodeWithTag(SignInScreenTestTags.GOOGLE_BUTTON).assertIsDisplayed()
    assertNull(auth.currentUser)

    // Signing in as a user without preferences leads to the preferences screen.
    signInThroughUi()
    waitForTag(PrefTags.PREFERENCE_SCREEN)
    composeRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertDoesNotExist()
    assertEquals(1, picker.getCredentialCalls)
    val user = auth.currentUser
    assertNotNull("The Auth emulator created a session", user)
    assertEquals(email, user!!.email)
    val uid = user.uid
    assertNull("Nothing stored yet for a new user", storedPreferences(uid))

    // Edit the draft and save: preferences reach the Firestore emulator and the dashboard opens.
    composeRule.click(PrefTags.STUDY_TIME_EVENING)
    composeRule.click(PrefTags.SESSION_LENGTH_2_HOURS)
    composeRule.click(PrefTags.BREAK_DURATION_15_MIN)
    composeRule.click(PrefTags.BREAK_FREQUENCY_1_HOUR)
    composeRule.onNodeWithTag(PrefTags.SAVE_BUTTON).performClick()
    assertOnDashboard()
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_SCREEN).assertDoesNotExist()
    assertEquals(
        UserPreferences(
            userId = uid,
            preferredStudyTimes = listOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON, TimeOfDay.EVENING),
            sessionLengthMinutes = 120,
            breakLengthMinutes = 15,
            breakFrequencyMinutes = 60,
        ),
        storedPreferences(uid),
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

    // Logging out ends the Firebase session and clears the picker state.
    openSettings()
    logOutFromSettings()
    assertNull(auth.currentUser)
    assertEquals(1, picker.clearCalls)
  }

  @Test
  fun returningUser_skipsPreferences_andCanSignInAgainAfterLogout() {
    val uid = seedReturningUser { UserPreferences(userId = it) }.userId

    // A returning user with preferences stored in Firestore goes straight to the dashboard.
    signInThroughUi()
    assertOnDashboard()
    assertEquals(uid, auth.currentUser?.uid)
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_SCREEN).assertDoesNotExist()

    // After logging out, signing in again still skips the preferences screen.
    openSettings()
    logOutFromSettings()
    signInThroughUi()
    assertOnDashboard()
    assertEquals(uid, auth.currentUser?.uid)
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_SCREEN).assertDoesNotExist()
    composeRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertDoesNotExist()
  }

  @Test
  fun returningUser_opensPreferencesFromSettings_andBackReturnsWithoutSaving() {
    val original = seedReturningUser { UserPreferences(userId = it) }
    signInThroughUi()
    assertOnDashboard()

    // The Settings button opens Preferences in edit mode: edit title and a back button.
    openSettings()
    composeRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()
    waitForTag(PrefTags.PREFERENCE_SCREEN)
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_TITLE).assertTextEquals("Modify your preferences")
    composeRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()

    // Changing a value then going back returns to Settings and persists nothing.
    composeRule.click(PrefTags.SESSION_LENGTH_2_HOURS)
    composeRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    waitForTag(SettingsScreenTestTags.SCREEN)
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_SCREEN).assertDoesNotExist()
    assertEquals(original, storedPreferences(original.userId))
  }

  @Test
  fun returningUser_savesEditedPreferences_andReturnsToSettings() {
    val uid = seedReturningUser { UserPreferences(userId = it) }.userId
    signInThroughUi()
    assertOnDashboard()

    openSettings()
    composeRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()
    waitForTag(PrefTags.PREFERENCE_SCREEN)
    composeRule.click(PrefTags.SESSION_LENGTH_2_HOURS)
    composeRule.onNodeWithTag(PrefTags.SAVE_BUTTON).performClick()

    // Saving persists the edit in Firestore and lands back on Settings, not on the dashboard.
    waitForTag(SettingsScreenTestTags.SCREEN)
    composeRule.onNodeWithTag(PrefTags.PREFERENCE_SCREEN).assertDoesNotExist()
    assertEquals(120, storedPreferences(uid)?.sessionLengthMinutes)

    // Back from Settings reaches the dashboard: Preferences was not left on the back stack.
    composeRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    assertOnDashboard()
  }

  @Test
  fun firstTimeUser_backFromDashboardLeavesApp_insteadOfReturningToPreferences() {
    signInThroughUi()
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

  /**
   * Sends an HTTP DELETE to an emulator REST endpoint that wipes its state.
   *
   * Fails with an [IllegalStateException] naming the URL and status when the emulator does not
   * answer with a 2xx, so a failed cleanup cannot leak state into later tests.
   */
  private fun clearEmulator(url: String) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "DELETE"
      val status = connection.responseCode
      check(status in 200..299) { "Emulator cleanup failed: DELETE $url returned HTTP $status" }
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
    const val PROJECT_ID = "demo-didyoustudy"
    const val TIMEOUT_MILLIS = 30_000L

    /** Creates a uniquely named app on a demo project, so it can never address production. */
    fun initializeEmulatorApp(prefix: String): FirebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApplicationId("1:123:android:e2e")
                // A unique fake key keeps Auth's per-key emulator registry isolated per app.
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            "$prefix-${UUID.randomUUID()}",
        )
  }
}
