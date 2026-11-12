// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToLog
import androidx.credentials.ClearCredentialStateRequest
import androidx.test.core.app.ApplicationProvider
import com.android.sample.model.auth.AuthRepositoryFirebase
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepositoryFirestore
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepositoryFirestore
import com.android.sample.model.preferences.PreferencesRepositoryFirestore
import com.android.sample.model.preferences.TimeOfDay
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.profile.ProfileRepositoryFirestore
import com.android.sample.testing.FakeGoogleCredentialManager
import com.android.sample.testing.RepositoryOverridesRule
import com.android.sample.ui.auth.SignInScreenTestTags
import com.android.sample.ui.calendar.CalendarTestTags
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as EventTags
import com.android.sample.ui.groupsession.GroupSessionScreenTestTags
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
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

  /**
   * Releases the SDK state and wipes the emulators' Auth accounts and Firestore documents.
   *
   * The explicit `Unit` return type matters: JUnit rejects `@After` methods that are not void.
   */
  @After
  fun cleanup(): Unit =
      runBlocking<Unit> {
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

  /** Returns the app's string resource [id], so tests follow the UI texts instead of literals. */
  private fun string(@StringRes id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

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
    assertTopBarTitle(string(R.string.dashboard_title))
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
   * Runs [block] with the uid and a signed-in Firestore client of the Google account behind
   * [signInPicker], on a separate Firebase app so the app under test keeps its own session. The
   * client and its local cache are released afterwards.
   */
  private fun <T> withSeedClient(
      signInPicker: FakeGoogleCredentialManager,
      block: suspend (uid: String, db: FirebaseFirestore, auth: FirebaseAuth) -> T,
  ): T = runBlocking {
    val seedApp = initializeEmulatorApp("seed")
    val seedAuth = FirebaseAuth.getInstance(seedApp).apply { useEmulator(HOST, AUTH_PORT) }
    val seedDb = FirebaseFirestore.getInstance(seedApp).apply { useEmulator(HOST, FIRESTORE_PORT) }
    try {
      val account = AuthRepositoryFirebase(seedAuth) {}.signInWithGoogle(signInPicker.idToken())
      block(account.uid, seedDb, seedAuth)
    } finally {
      seedAuth.signOut()
      seedDb.terminate().await()
      seedDb.clearPersistence().await()
      seedApp.delete()
    }
  }

  /**
   * Creates the Google account's Firebase user and stores [preferences] for it, through a separate
   * Firebase app so the app under test keeps showing the sign-in screen. Returns the stored
   * preferences (with the user's uid), which the emulator reuses when the same Google account signs
   * in through the UI.
   */
  private fun seedReturningUser(preferences: (uid: String) -> UserPreferences): UserPreferences =
      withSeedClient(picker) { uid, seedDb, _ ->
        val stored = preferences(uid)
        PreferencesRepositoryFirestore(seedDb).savePreferences(stored).getOrThrow()
        stored
      }

  /**
   * Creates a public group session hosted by another Google account (not the app user), starting in
   * one hour. Returns the stored session.
   */
  private fun seedHostedGroupSession(): GroupSession =
      withSeedClient(FakeGoogleCredentialManager("host-${UUID.randomUUID()}@example.test")) {
          hostUid,
          seedDb,
          seedAuth ->
        val repository = GroupSessionsRepositoryFirestore(seedDb, seedAuth)
        val start = Instant.now().plus(Duration.ofHours(1))
        val session =
            GroupSession(
                id = repository.newSessionId(),
                createdBy = hostUid,
                memberIds = listOf(hostUid),
                details =
                    EventDetails(
                        type = EventType.STUDY_SESSION,
                        subject = "E2E Algebra",
                        startsAt = start,
                        endsAt = start.plus(Duration.ofHours(1)),
                    ),
                createdAt = Instant.now(),
            )
        repository.saveSession(hostUid, session).getOrThrow()
        session
      }

  /** Reads the member IDs of group session [sessionId] from the emulator server. */
  private fun storedMemberIds(sessionId: String): List<String> = runBlocking {
    val snapshot = db.collection("groupSessions").document(sessionId).get(Source.SERVER).await()
    (snapshot.get("memberIds") as List<*>).map { it as String }
  }

  /** Reads the documents of the user's personal events from the emulator server. */
  private fun storedEvents(uid: String) = runBlocking {
    db.collection("users").document(uid).collection("events").get(Source.SERVER).await().documents
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
    assertTopBarTitle(string(R.string.calendar_title))
    composeRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    assertTopBarTitle(string(R.string.group_session_title))
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
    composeRule
        .onNodeWithTag(PrefTags.PREFERENCE_TITLE)
        .assertTextEquals(string(R.string.preferences_edit_header))
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

  @Test
  fun returningUser_createsEventFromCalendar_andItIsStoredInFirestore() {
    val uid = seedReturningUser { UserPreferences(userId = it) }.userId
    signInThroughUi()
    assertOnDashboard()
    assertTrue("No event stored yet", storedEvents(uid).isEmpty())

    // The calendar's add button opens the event form.
    composeRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    assertTopBarTitle(string(R.string.calendar_title))
    composeRule.onNodeWithTag(CalendarTestTags.ADD_BUTTON).performClick()
    waitForTag(EventTags.SCREEN_CONTAINER)

    // "Other" needs no subject. Date and start time keep the pickers' defaults (today, now).
    composeRule.onNodeWithTag(EventTags.TYPE_SELECTOR).performClick()
    composeRule.onNodeWithText(string(R.string.event_type_other)).performClick()
    composeRule.onNodeWithTag(EventTags.DATE_SELECTOR).performClick()
    composeRule.onNodeWithText(string(R.string.confirm)).performClick()
    composeRule.onNodeWithTag(EventTags.START_TIME_SELECTOR).performClick()
    composeRule.onNodeWithContentDescription("8 hours").performClick()
    composeRule.onNodeWithText(string(R.string.confirm)).performClick()
    // The end time must differ from the start: tap the next hour on the picker's clock face
    // (an end before the start is accepted too, it ends the next day).
    composeRule.onNodeWithTag(EventTags.END_TIME_SELECTOR).performClick()
    val nextHour = (LocalTime.now().hour + 1) % 24
    composeRule.onNodeWithContentDescription("$nextHour hours").performClick()
    composeRule.onNodeWithText(string(R.string.confirm)).performClick()
    // The location is picked from the campus locations: search, then tap the matching row.
    // Scroll first: on a small screen the selector is below the fold and a tap would be lost.
    composeRule.onNodeWithTag(EventTags.LOCATION_SELECTOR).performScrollTo().performClick()
    // Wait for the picker dialog, then type in its only text field (not found by placeholder).
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
      composeRule
          .onAllNodesWithText(string(R.string.location_picker_dialog_title))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput(LOCATION)
    // Results arrive asynchronously, and the search field also shows the typed text: wait for the
    // row.
    val resultRow = hasText(LOCATION) and hasClickAction() and !hasSetTextAction()
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
      composeRule.onAllNodes(resultRow).fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNode(resultRow).performClick()
    composeRule.onNodeWithTag(EventTags.SAVE_BUTTON).performClick()

    // Saving returns to the calendar and the event reaches the user's Firestore collection.
    waitForTag(CalendarTestTags.ADD_BUTTON)
    composeRule.onNodeWithTag(EventTags.SCREEN_CONTAINER).assertDoesNotExist()
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) { storedEvents(uid).isNotEmpty() }
    val event = storedEvents(uid).single()
    assertEquals("OTHER", event.getString("type"))
    assertEquals(LOCATION, event.getString("location"))
  }

  @Test
  fun returningUser_joinsAndLeavesGroupSession_syncedToFirestore() {
    // Another user hosts the session, so the app user is not a member yet and can join it.
    val session = seedHostedGroupSession()
    val hostId = session.createdBy
    val uid = seedReturningUser { UserPreferences(userId = it) }.userId
    signInThroughUi()
    assertOnDashboard()

    composeRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    assertTopBarTitle(string(R.string.group_session_title))
    waitForTag(GroupSessionScreenTestTags.sessionItem(0))
    composeRule.onNodeWithText("E2E Algebra").assertIsDisplayed()
    assertEquals(listOf(hostId), storedMemberIds(session.id))

    // Joining adds the user to the session in Firestore and the card now offers Leave.
    composeRule.onNodeWithText(string(R.string.group_session_action_join)).performClick()
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
      composeRule
          .onAllNodesWithText(string(R.string.group_session_action_leave))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    assertEquals(listOf(hostId, uid), storedMemberIds(session.id))

    // Leaving removes the user again and the card goes back to Join.
    composeRule.onNodeWithText(string(R.string.group_session_action_leave)).performClick()
    composeRule.waitUntil(timeoutMillis = TIMEOUT_MILLIS) {
      composeRule
          .onAllNodesWithText(string(R.string.group_session_action_join))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    assertEquals(listOf(hostId), storedMemberIds(session.id))
  }

  @Test
  fun signedInUser_staysSignedInAndOnScreen_afterActivityRecreation() {
    val uid = seedReturningUser { UserPreferences(userId = it) }.userId
    signInThroughUi()
    assertOnDashboard()
    composeRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    assertTopBarTitle(string(R.string.calendar_title))

    // A configuration change recreates the activity: no sign-in screen, same session and screen.
    composeRule.activityRule.scenario.recreate()
    waitForTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU)
    composeRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertDoesNotExist()
    assertEquals(uid, auth.currentUser?.uid)
    assertTopBarTitle(string(R.string.calendar_title))
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

    /** A room of the bundled campus locations (`campus_locations.json`), used by the event test. */
    const val LOCATION = "AAB 0 26"

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
