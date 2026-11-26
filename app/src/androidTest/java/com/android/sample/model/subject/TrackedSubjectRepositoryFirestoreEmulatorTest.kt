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
import org.junit.Assert.assertNull
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

  private suspend fun observed() =
      withTimeout(15000) { repository.observeDecisions(uid).first().getOrThrow() }

  private suspend fun serverDecisions() =
      TrackedSubjectFirestoreMapper.decode(doc().get(Source.SERVER).await().data)

  private fun Result<*>.error() = (exceptionOrNull() as TrackedSubjectException).error

  /** The priority is null until saved; afterwards it is observed and stored on the server. */
  @Test
  fun priorityIsNullUntilSavedThenRoundTrips() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()
    assertNull(observed().priorityOf("Analysis I"))

    repository.savePriority(uid, "Analysis I", 4).getOrThrow()
    sync()

    assertEquals(SubjectPriority(4), observed().priorityOf("Analysis I"))
    assertEquals(SubjectPriority(4), serverDecisions().priorityOf("Analysis I"))
  }

  /** The lowest and the highest levels are accepted and stored. */
  @Test
  fun boundaryLevelsAreAccepted() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()

    repository.savePriority(uid, "Analysis I", SubjectPriority.MIN).getOrThrow()
    sync()
    assertEquals(SubjectPriority(SubjectPriority.MIN), serverDecisions().priorityOf("Analysis I"))

    repository.savePriority(uid, "Analysis I", SubjectPriority.MAX).getOrThrow()
    sync()
    assertEquals(SubjectPriority(SubjectPriority.MAX), serverDecisions().priorityOf("Analysis I"))
  }

  /** Out-of-range levels fail with INVALID_ARGUMENT and the stored priority stays as it was. */
  @Test
  fun outOfRangeLevelsAreRejectedWithoutWriting() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()
    repository.savePriority(uid, "Analysis I", 3).getOrThrow()
    sync()

    for (level in listOf(0, -1, SubjectPriority.MAX + 1)) {
      assertEquals(
          TrackedSubjectError.INVALID_ARGUMENT,
          repository.savePriority(uid, "Analysis I", level).error(),
      )
    }
    sync()

    assertEquals(SubjectPriority(3), serverDecisions().priorityOf("Analysis I"))
  }

  /** Saving a priority keeps the tracked flag, and saving again replaces the earlier priority. */
  @Test
  fun savingReplacesPriorityAndKeepsTrackedFlag() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true, "Physics" to true)).getOrThrow()

    repository.savePriority(uid, "Analysis I", 2).getOrThrow()
    repository.savePriority(uid, "Analysis I", 5).getOrThrow()
    sync()

    val server = serverDecisions()
    assertEquals(SubjectPriority(5), server.priorityOf("Analysis I"))
    assertTrue(server.isTracked("Analysis I"))
    assertTrue(server.isTracked("Physics"))
    assertNull(server.priorityOf("Physics"))
    assertEquals(listOf("Analysis I", "Physics"), server.trackedSubjects)
  }

  /** Untracking and tracking again through saveDecisions keeps the stored priority. */
  @Test
  fun saveDecisionsKeepsStoredPriority() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()
    repository.savePriority(uid, "Analysis I", 4).getOrThrow()

    repository.saveDecisions(uid, mapOf("Analysis I" to false)).getOrThrow()
    sync()
    val untracked = serverDecisions()
    assertFalse(untracked.isTracked("Analysis I"))
    assertEquals(SubjectPriority(4), untracked.priorityOf("Analysis I"))

    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()
    sync()
    assertEquals(SubjectPriority(4), serverDecisions().priorityOf("Analysis I"))
  }

  /** A subject marked as not tracked fails with INVALID_ARGUMENT and nothing is written. */
  @Test
  fun untrackedSubjectFailsWithoutWriting() = runBlocking {
    repository.saveDecisions(uid, mapOf("Physics" to false)).getOrThrow()
    sync()

    val result = repository.savePriority(uid, "Physics", 3)
    sync()

    assertEquals(TrackedSubjectError.INVALID_ARGUMENT, result.error())
    assertNull(serverDecisions().priorityOf("Physics"))
  }

  /** A subject without any decision, such as a custom subject, fails and writes nothing. */
  @Test
  fun subjectWithoutDecisionFailsWithoutWriting() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()
    sync()

    val result = repository.savePriority(uid, "Custom subject", 3)
    sync()

    assertEquals(TrackedSubjectError.INVALID_ARGUMENT, result.error())
    assertEquals(
        setOf("Analysis I"),
        serverDecisions().decisions.values.map { it.spelling }.toSet(),
    )
  }

  /** Saving a priority for an owner other than the signed-in user is denied. */
  @Test
  fun otherOwnerIsDenied() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()

    val result = repository.savePriority("someone-else", "Analysis I", 3)

    assertEquals(TrackedSubjectError.PERMISSION_DENIED, result.error())
  }

  /** A priority saved offline is observed locally at once and synced when the network returns. */
  @Test
  fun offlineSavePriorityAndReconnectSync() = runBlocking {
    repository.saveDecisions(uid, mapOf("Analysis I" to true)).getOrThrow()
    sync()

    db.disableNetwork().await()
    withTimeout(5000) {
      repository.savePriority(uid, "Analysis I", 2).getOrThrow()
      repository.savePriority(uid, "Analysis I", 5).getOrThrow()
      val local = repository.observeDecisions(uid).first().getOrThrow()
      assertEquals(SubjectPriority(5), local.priorityOf("Analysis I"))
      assertTrue(local.isTracked("Analysis I"))
    }

    db.enableNetwork().await()
    sync()

    assertEquals(SubjectPriority(5), serverDecisions().priorityOf("Analysis I"))
  }
}
