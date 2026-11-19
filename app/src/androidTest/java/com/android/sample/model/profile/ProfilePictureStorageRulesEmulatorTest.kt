// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies `storage.rules` for profile pictures against the Auth and Storage emulators. */
@RunWith(AndroidJUnit4::class)
class ProfilePictureStorageRulesEmulatorTest {
  /** One isolated Firebase app with its own Auth session and Storage client. */
  private data class Client(
      val app: FirebaseApp,
      val auth: FirebaseAuth,
      val storage: FirebaseStorage,
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

  /** Releases each isolated Firebase client after its assertions. */
  @After
  fun cleanup() {
    clients.forEach { client ->
      client.auth.signOut()
      client.app.delete()
    }
    clients.clear()
  }

  /** The owner can upload to each of their two slots, read them back, and replace a slot. */
  @Test
  fun ownerCanUploadReadAndReplaceOwnPicture() = runBlocking {
    val first = bytes(size = 2_048)
    val replacement = bytes(size = 3_072)

    upload(pictureRef(owner, owner.uid, "a"), first)
    assertArrayEquals(first, download(pictureRef(owner, owner.uid, "a")))

    upload(pictureRef(owner, owner.uid, "b"), replacement)
    assertArrayEquals(replacement, download(pictureRef(owner, owner.uid, "b")))

    upload(pictureRef(owner, owner.uid, "a"), replacement)
    assertArrayEquals(replacement, download(pictureRef(owner, owner.uid, "a")))
  }

  /** Any signed-in user can read someone's picture, but a signed-out client cannot. */
  @Test
  fun readRequiresSignIn() = runBlocking {
    val stored = bytes(size = 2_048)
    upload(pictureRef(owner, owner.uid), stored)

    assertArrayEquals(stored, download(pictureRef(reader, owner.uid)))
    denied { download(pictureRef(signedOut, owner.uid)) }
  }

  /** Neither another signed-in user nor a signed-out client can write to the owner's slots. */
  @Test
  fun onlyOwnerCanWriteToOwnPath() = runBlocking {
    listOf("a", "b").forEach { slot ->
      denied { upload(pictureRef(reader, owner.uid, slot), bytes(size = 1_024)) }
      denied { upload(pictureRef(signedOut, owner.uid, slot), bytes(size = 1_024)) }
    }
  }

  /** Only the two slot names are allowed: the old `{uid}.jpg` and any other slot are refused. */
  @Test
  fun onlyTheTwoSlotNamesAreAllowed() = runBlocking {
    listOf("${owner.uid}.jpg", "${owner.uid}_c.jpg", "${owner.uid}_a.jpg.bak").forEach { name ->
      denied { upload(owner.storage.reference.child("profilePictures/$name"), bytes(size = 1_024)) }
    }
  }

  /** An upload whose content type is not `image/jpeg` is rejected, even for the owner. */
  @Test
  fun nonJpegUploadIsDenied() = runBlocking {
    denied { upload(pictureRef(owner, owner.uid), bytes(size = 1_024), contentType = "image/png") }
  }

  /** The rule requires strictly less than 1 MB: one byte under is accepted, exactly 1 MB is not. */
  @Test
  fun uploadMustBeUnderOneMegabyte() = runBlocking {
    upload(pictureRef(owner, owner.uid), bytes(size = ONE_MEGABYTE - 1))

    denied { upload(pictureRef(owner, owner.uid), bytes(size = ONE_MEGABYTE)) }
  }

  /** Signs a new user up in the Auth emulator and wraps it in an isolated client. */
  private suspend fun newClient(label: String): Client {
    val client = newSignedOutClient(label)
    client.uid = signUp(client.auth, client.app.name)
    return client
  }

  /** Creates an isolated Firebase app wired to the Auth and Storage emulators, not signed in. */
  private fun newSignedOutClient(label: String): Client {
    val name = "picture-rules-$label-${UUID.randomUUID()}"
    val app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:pictures")
                .setApiKey("fake-${UUID.randomUUID()}")
                .setStorageBucket("demo-didyoustudy.appspot.com")
                .build(),
            name,
        )
    val auth = FirebaseAuth.getInstance(app)
    auth.useEmulator("10.0.2.2", 9099)
    val storage = FirebaseStorage.getInstance(app)
    storage.useEmulator("10.0.2.2", 9199)
    return Client(app, auth, storage, uid = "").also(clients::add)
  }

  /** Creates a unique email/password user in the Auth emulator and returns its uid. */
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

  /** Returns the reference of `profilePictures/{uid}_{slot}.jpg` as seen by [client]. */
  private fun pictureRef(client: Client, uid: String, slot: String = "a"): StorageReference =
      client.storage.reference.child("profilePictures/${uid}_$slot.jpg")

  /** Builds [size] arbitrary bytes; the rules look at metadata and size, not at the content. */
  private fun bytes(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

  /** Uploads [bytes] to [ref], declaring [contentType] in the object metadata. */
  private suspend fun upload(
      ref: StorageReference,
      bytes: ByteArray,
      contentType: String = "image/jpeg",
  ) {
    val metadata = StorageMetadata.Builder().setContentType(contentType).build()
    timed { ref.putBytes(bytes, metadata).await() }
  }

  /** Downloads the object at [ref]; objects here never exceed [ONE_MEGABYTE] bytes. */
  private suspend fun download(ref: StorageReference): ByteArray = timed {
    ref.getBytes(ONE_MEGABYTE.toLong()).await()
  }

  /** Expects [action] to be refused by the rules (HTTP 401 or 403 from the emulator). */
  private suspend fun denied(action: suspend () -> Unit) {
    try {
      action()
      fail("Expected the Storage rules to deny the operation")
    } catch (error: StorageException) {
      assertTrue(
          "Expected a 401/403 denial but got HTTP ${error.httpResultCode}: ${error.message}",
          error.httpResultCode == 401 || error.httpResultCode == 403,
      )
    }
  }

  /** Bounds a call so a stuck emulator fails the test instead of hanging it. */
  private suspend fun <T> timed(action: suspend () -> T): T = withTimeout(15_000) { action() }

  private companion object {
    /** The rule's exclusive upper bound on an upload: 1 MB. */
    const val ONE_MEGABYTE = 1024 * 1024
  }
}
