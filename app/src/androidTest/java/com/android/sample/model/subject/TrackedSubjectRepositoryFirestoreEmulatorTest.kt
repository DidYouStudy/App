// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator tests for [TrackedSubjectRepositoryFirestore] testing persistence and offline sync. */
@RunWith(AndroidJUnit4::class)
class TrackedSubjectRepositoryFirestoreEmulatorTest {

  private lateinit var app: FirebaseApp
  private lateinit var db: FirebaseFirestore
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: TrackedSubjectRepositoryFirestore
  private lateinit var uid: String
  private val name = "tracked-subjects-${UUID.randomUUID()}"
  private val email = "$name@example.test"
  private val password = "EmulatorOnly123!"

  @Before
  fun setup() = runBlocking {
    initialize()
    uid = auth.createUserWithEmailAndPassword(email, password).await().user!!.uid
  }

  private fun initialize() {
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:trackedsubjects")
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            name,
        )
    auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    initializeFirestore()
  }

  private fun initializeFirestore() {
    db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 8080)
    repository = TrackedSubjectRepositoryFirestore(db, auth)
  }

  @After
  fun cleanup() = runBlocking {
    if (::db.isInitialized) {
      db.terminate().await()
      db.clearPersistence().await()
    }
    if (::auth.isInitialized) auth.signOut()
    if (::app.isInitialized) app.delete()
  }

  private suspend fun sync() = withTimeout(15000) { db.waitForPendingWrites().await() }

  private fun doc() =
      db.collection("users").document(uid).collection("meta").document("trackedSubjects")

  /** Verifies saving decisions persists to Firestore server and can be observed. */
  @Test
  fun saveDecisionsAndServerRoundTrip() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true, "Physics" to false)).getOrThrow()
    sync()

    val snapshot = doc().get(Source.SERVER).await()
    assertTrue(snapshot.exists())

    val current = withTimeout(15000) { repository.observeDecisions(uid).first().getOrThrow() }
    assertTrue(current.isTracked("Analysis I"))
    assertFalse(current.isTracked("Physics"))
  }

  /** Merging keeps the other subjects and overwrites one entry with the trimmed latest spelling. */
  @Test
  fun mergeKeepsOtherSubjectsAndUpdatesSpelling() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true, "Physics" to false)).getOrThrow()
    repository.saveDecisions(uid, mapOf(" physics " to true)).getOrThrow()
    sync()

    val server = TrackedSubjectFirestoreMapper.decode(doc().get(Source.SERVER).await().data)
    assertEquals(listOf("Analysis I", "physics"), server.trackedSubjects)
  }

  /** A rejected save (the same subject twice) writes nothing to the server. */
  @Test
  fun rejectedSaveWritesNothing() = runBlocking {
    repository.saveDecisions(uid, mapOf("Physics" to false)).getOrThrow()
    val rejected =
        repository.saveDecisions(uid, mapOf("Analysis I" to true, " analysis i " to false))
    assertTrue(rejected.isFailure)
    sync()

    val server = TrackedSubjectFirestoreMapper.decode(doc().get(Source.SERVER).await().data)
    assertEquals(setOf("Physics"), server.decisions.values.map { it.spelling }.toSet())
  }

  /** Verifies decisions saved offline are observed locally and synced when network is restored. */
  @Test
  fun offlineSaveDecisionsAndReconnectSync() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()
    sync()

    db.disableNetwork().await()
    withTimeout(5000) {
      repository.saveDecisions(uid, mapOf("Physics" to true)).getOrThrow()
      val local = repository.observeDecisions(uid).first().getOrThrow()
      assertTrue(local.isTracked("Analysis I"))
      assertTrue(local.isTracked("Physics"))
    }

    db.enableNetwork().await()
    sync()

    val serverSnapshot = doc().get(Source.SERVER).await()
    assertTrue(serverSnapshot.exists())
    val serverDecisions = TrackedSubjectFirestoreMapper.decode(serverSnapshot.data)
    assertTrue(serverDecisions.isTracked("Analysis I"))
    assertTrue(serverDecisions.isTracked("Physics"))
  }
}
