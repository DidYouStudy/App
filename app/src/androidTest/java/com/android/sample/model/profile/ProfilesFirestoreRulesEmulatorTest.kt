// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.profile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies profile and preference privacy rules against Auth and Firestore emulators. */
@RunWith(AndroidJUnit4::class)
class ProfilesFirestoreRulesEmulatorTest {
  private data class Client(
      val app: FirebaseApp,
      val auth: FirebaseAuth,
      val db: FirebaseFirestore,
      var uid: String,
  )

  private val clients = mutableListOf<Client>()
  private lateinit var owner: Client
  private lateinit var reader: Client
  private lateinit var signedOut: Client

  /** Creates distinct signed-in clients and a never-signed-in client for each test. */
  @Before
  fun setup() = runBlocking {
    clients.clear()
    owner = newClient("owner")
    reader = newClient("reader")
    signedOut = newSignedOutClient("signed-out")
  }

  /** Releases each isolated Firebase client after its rule assertions. */
  @After
  fun cleanup() = runBlocking {
    clients.forEach { client ->
      timed {
        client.db.terminate().await()
        client.db.clearPersistence().await()
      }
      client.auth.signOut()
      client.app.delete()
    }
    clients.clear()
  }

  /** An owner can create and read a profile document under their authentication UID. */
  @Test
  fun ownerCanWriteAndReadOwnProfile() = runBlocking {
    val expected = profileData(owner.uid, "Owner")

    set(profileDocument(owner, owner.uid), expected)

    assertEquals(expected, readData(owner, owner.uid))
  }

  /** A different signed-in user can get a profile document when they know its ID. */
  @Test
  fun anotherSignedInUserCanGetProfileById() = runBlocking {
    val expected = profileData(owner.uid, "Owner")
    set(profileDocument(owner, owner.uid), expected)

    assertEquals(expected, readData(reader, owner.uid))
  }

  /** ProfileRepositoryFirestore maps a permitted cross-user document to its username. */
  @Test
  fun profileRepositoryReadsAnotherUsersUsername() = runBlocking {
    set(profileDocument(owner, owner.uid), profileData(owner.uid, "Owner"))
    val result = ProfileRepositoryFirestore(reader.db).getProfile(owner.uid)

    assertEquals("Owner", result.getOrThrow()?.username)
  }

  /** A signed-in user cannot list the profiles collection. */
  @Test
  fun profilesCollectionQueryIsDenied() = runBlocking {
    set(profileDocument(owner, owner.uid), profileData(owner.uid, "Owner"))

    denied { timed { reader.db.collection("profiles").get(Source.SERVER).await() } }
  }

  /** A signed-out client cannot read an existing profile document. */
  @Test
  fun signedOutUserCannotGetProfile() = runBlocking {
    set(profileDocument(owner, owner.uid), profileData(owner.uid, "Owner"))

    denied { timed { profileDocument(signedOut, owner.uid).get(Source.SERVER).await() } }
  }

  /** A non-owner cannot replace, update or delete an existing profile document. */
  @Test
  fun anotherUserCannotWriteOrDeleteProfile() = runBlocking {
    val original = profileData(owner.uid, "Owner")
    set(profileDocument(owner, owner.uid), original)

    denied {
      timed { profileDocument(reader, owner.uid).set(profileData(owner.uid, "Changed")).await() }
    }
    denied { timed { profileDocument(reader, owner.uid).update("username", "Changed").await() } }
    denied { timed { profileDocument(reader, owner.uid).delete().await() } }

    assertEquals(original, readData(owner, owner.uid))
  }

  /** A different signed-in user cannot read the owner's preferences document. */
  @Test
  fun anotherUserCannotReadPreferences() = runBlocking {
    val expected = mapOf("userId" to owner.uid, "privateNote" to "Owner's preferences")
    timed { preferencesDocument(owner).set(expected).await() }

    denied { timed { preferencesDocument(reader).get(Source.SERVER).await() } }
  }

  private suspend fun newClient(label: String): Client {
    val client = newSignedOutClient(label)
    client.uid = signUp(client.auth, client.app.name)
    return client
  }

  private fun newSignedOutClient(label: String): Client {
    val name = "profiles-rules-$label-${UUID.randomUUID()}"
    val app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:profiles")
                .setApiKey("fake-${UUID.randomUUID()}")
                .build(),
            name,
        )
    val auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    val db = FirebaseFirestore.getInstance(app)
    db.useEmulator("10.0.2.2", 8080)
    return Client(app, auth, db, uid = "").also(clients::add)
  }

  private suspend fun signUp(auth: FirebaseAuth, label: String): String = timed {
    auth
        .createUserWithEmailAndPassword(
            "$label-${UUID.randomUUID()}@example.test",
            "EmulatorOnly123!",
        )
        .await()
        .user!!
        .uid
  }

  private fun profileData(uid: String, username: String): Map<String, Any?> =
      mapOf("uid" to uid, "username" to username)

  private fun profileDocument(client: Client, uid: String): DocumentReference =
      client.db.collection("profiles").document(uid)

  private fun preferencesDocument(client: Client): DocumentReference =
      client.db.collection("preferences").document(owner.uid)

  private suspend fun readData(client: Client, uid: String): Map<String, Any?> = timed {
    profileDocument(client, uid).get(Source.SERVER).await().data!!
  }

  private suspend fun set(ref: DocumentReference, data: Map<String, Any?>) {
    timed { ref.set(data).await() }
  }

  private suspend fun denied(action: suspend () -> Unit) {
    try {
      timed { action() }
      fail("Expected server permission denial")
    } catch (error: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, error.code)
    }
  }

  private suspend fun <T> timed(action: suspend () -> T): T = withTimeout(15_000) { action() }
}
