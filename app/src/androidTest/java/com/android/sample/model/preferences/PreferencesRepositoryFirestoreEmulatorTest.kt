// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.preferences

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Auth/Firestore SDK tests for [PreferencesRepositoryFirestore]; they use the local emulators,
 * never production credentials or data. They check what the mock-based unit tests cannot: that the
 * local-write barrier works on the real SDK, and how the cache fallback behaves.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryFirestoreEmulatorTest {
  private lateinit var app: FirebaseApp
  private lateinit var db: FirebaseFirestore
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: PreferencesRepositoryFirestore
  private lateinit var uid: String
  private val name = "preferences-${UUID.randomUUID()}"
  private val email = "$name@example.test"
  private val password = "EmulatorOnly123!"

  /** Creates an isolated SDK cache and an authenticated emulator user for each test. */
  @Before
  fun setup() = runBlocking {
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:preferences")
                // A unique fake key isolates each client's Auth emulator registration.
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            name,
        )
    auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 8080)
    repository = PreferencesRepositoryFirestore(db)
    // The rules only let a user touch preferences/{uid}, so the document ID is the user's uid.
    uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
  }

  /** Releases listeners/cache files and removes only this test's local persistence. */
  @After
  fun cleanup() = runBlocking {
    if (::db.isInitialized) {
      db.terminate().await()
      db.clearPersistence().await()
    }
    if (::auth.isInitialized) auth.signOut()
    if (::app.isInitialized) app.delete()
  }

  /** Builds the tested user's preferences, overriding only the fields a test cares about. */
  private fun prefs(sessionLengthMinutes: Int = 60, breakLengthMinutes: Int = 10) =
      UserPreferences(
          userId = uid,
          sessionLengthMinutes = sessionLengthMinutes,
          breakLengthMinutes = breakLengthMinutes,
      )

  /** Waits for server confirmation only in tests, never in the repository's write API. */
  private suspend fun sync() = withTimeout(15000.milliseconds) { db.waitForPendingWrites().await() }

  /**
   * Saving offline completes locally without waiting for the server, the saved values can be read
   * back offline, and the queued write reaches the server after reconnecting.
   */
  @Test
  fun offlineSaveCompletesLocallyAndSyncs() = runBlocking {
    db.disableNetwork().await()

    val edited = prefs(sessionLengthMinutes = 120, breakLengthMinutes = 15)
    // A hang here (timeout) means the save or the read waited for the server.
    withTimeout(5000.milliseconds) {
      repository.savePreferences(edited).getOrThrow()
      assertEquals(edited, repository.getPreferences(uid).getOrThrow())
    }

    db.enableNetwork().await()
    sync()
    val onServer =
        db.collection("preferences")
            .document(uid)
            .get(Source.SERVER)
            .await()
            .toObject(UserPreferences::class.java)
    assertEquals(edited, onServer)
  }

  /**
   * A permission error on read is returned as a failure and is not hidden by the cached copy: the
   * cache fallback applies only to an unreachable server.
   */
  @Test
  fun permissionDeniedReadIsNotMaskedByCache() = runBlocking {
    repository.savePreferences(prefs()).getOrThrow()
    sync()
    // Signed out, the rules (isOwner) refuse the read although the document is cached.
    auth.signOut()

    val error = withTimeout(15000.milliseconds) { repository.getPreferences(uid).exceptionOrNull() }

    assertTrue("Expected a failure, got $error", error is FirebaseFirestoreException)
    assertEquals(
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        (error as FirebaseFirestoreException).code,
    )
  }
}
