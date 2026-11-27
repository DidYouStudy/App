// Co-authored-by: Gemini 3.6 Flash
// Co-authored-by: OpenAI Codex
package com.android.sample.model.subject

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator tests for Firestore security rules under `users/{userId}/meta/trackedSubjects`. */
@RunWith(AndroidJUnit4::class)
class TrackedSubjectRulesEmulatorTest {
  /** Keeps each Firestore connection tied to its own Auth instance. */
  private data class Client(val app: FirebaseApp, val auth: FirebaseAuth, val db: FirebaseFirestore)

  private val clients = mutableListOf<Client>()
  private lateinit var owner: Client
  private lateinit var ownerUid: String

  @Before
  fun setup() = runBlocking {
    owner = newClient()
    ownerUid =
        owner.auth
            .createUserWithEmailAndPassword("${owner.app.name}@example.test", "EmulatorOnly123!")
            .await()
            .user!!
            .uid
  }

  /** Creates a fresh emulator client that starts signed out. */
  private fun newClient(): Client {
    val name = "tracked-rules-${UUID.randomUUID()}"
    val app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:trackedrules")
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            name,
        )
    val auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    val db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 8080)
    return Client(app, auth, db).also(clients::add)
  }

  @After
  fun cleanup() = runBlocking {
    clients.forEach { client ->
      client.db.terminate().await()
      client.db.clearPersistence().await()
      client.auth.signOut()
      client.app.delete()
    }
  }

  private fun doc(client: Client = owner) =
      client.db
          .collection("users")
          .document(ownerUid)
          .collection("meta")
          .document("trackedSubjects")

  private fun validPayload() = TrackedSubjectFirestoreMapper.encode(mapOf("Analysis I" to true))

  /** Verifies document owner can write and read valid trackedSubjects payload. */
  @Test
  fun ownerCanReadAndWriteValidData() = runBlocking {
    doc().set(validPayload()).await()
    assertTrue(doc().get(Source.SERVER).await().exists())
  }

  /** Verifies rules reject invalid document schemas (subjects not a map or extra keys). */
  @Test
  fun rejectsInvalidDataSchema() = runBlocking {
    denied { doc().set(mapOf("subjects" to "not-a-map")).await() }
    denied { doc().set(mapOf("subjects" to emptyMap<String, Any>(), "extraKey" to true)).await() }
  }

  /** Verifies signed-out clients and non-owner users are denied access. */
  @Test
  fun deniesSignedOutAndOtherUsers() = runBlocking {
    doc().set(validPayload()).await()

    val signedOut = newClient()
    denied { doc(signedOut).get(Source.SERVER).await() }
    denied { doc(signedOut).set(validPayload()).await() }

    val otherUser = newClient()
    otherUser.auth.signInAnonymously().await()
    denied { doc(otherUser).get(Source.SERVER).await() }
    denied { doc(otherUser).set(validPayload()).await() }
  }

  private fun priorityPayload(level: Int) =
      TrackedSubjectFirestoreMapper.encodePriority("Analysis I", SubjectPriority(level))

  /** The owner can merge priorities from the lowest to the highest level into an entry. */
  @Test
  fun ownerCanWritePriorityLevels() = runBlocking {
    doc().set(validPayload()).await()

    for (level in SubjectPriority.RANGE) {
      doc().set(priorityPayload(level), SetOptions.merge()).await()
      val stored = TrackedSubjectFirestoreMapper.decode(doc().get(Source.SERVER).await().data)
      assertEquals(SubjectPriority(level), stored.priorityOf("Analysis I"))
      assertTrue(stored.isTracked("Analysis I"))
    }
  }

  /** Signed-out clients and other users cannot write or change a priority. */
  @Test
  fun deniesPriorityWritesForSignedOutAndOtherUsers() = runBlocking {
    doc().set(validPayload()).await()
    doc().set(priorityPayload(3), SetOptions.merge()).await()

    val signedOut = newClient()
    denied { doc(signedOut).set(priorityPayload(5), SetOptions.merge()).await() }

    val otherUser = newClient()
    otherUser.auth.signInAnonymously().await()
    denied { doc(otherUser).set(priorityPayload(5), SetOptions.merge()).await() }
    denied { doc(otherUser).get(Source.SERVER).await() }
  }

  /** A document that carries a priority still has to follow the top-level schema. */
  @Test
  fun rejectsPriorityPayloadWithExtraTopLevelKey() = runBlocking {
    doc().set(validPayload()).await()

    denied { doc().set(priorityPayload(3) + ("extraKey" to true), SetOptions.merge()).await() }
  }

  private suspend fun denied(action: suspend () -> Any?) {
    try {
      withTimeout(15000) { action() }
      fail("Expected server permission denial")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
