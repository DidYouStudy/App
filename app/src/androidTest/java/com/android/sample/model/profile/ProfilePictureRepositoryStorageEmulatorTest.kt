// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises [ProfilePictureRepositoryStorage] with the real SDK against Auth/Storage emulators. */
@RunWith(AndroidJUnit4::class)
class ProfilePictureRepositoryStorageEmulatorTest {
  private lateinit var app: FirebaseApp
  private lateinit var auth: FirebaseAuth
  private lateinit var storage: FirebaseStorage
  private lateinit var repository: ProfilePictureRepositoryStorage
  private lateinit var uid: String

  /** Signs a fresh user in and builds a repository on an isolated, emulator-backed app. */
  @Before
  fun setup() = runBlocking {
    app =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            FirebaseOptions.Builder()
                .setProjectId("demo-didyoustudy")
                .setApplicationId("1:123:android:picturerepo")
                .setApiKey("fake-${UUID.randomUUID()}")
                .setStorageBucket("demo-didyoustudy.appspot.com")
                .build(),
            "picture-repo-${UUID.randomUUID()}",
        )
    auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
    storage = FirebaseStorage.getInstance(app).apply { useEmulator("10.0.2.2", 9199) }
    uid = timed {
      auth
          .createUserWithEmailAndPassword(
              "picture-${UUID.randomUUID()}@example.test",
              "EmulatorOnly123!",
          )
          .await()
          .user!!
          .uid
    }
    // The real application context, so the Storage folder and content type come from strings.xml.
    repository =
        ProfilePictureRepositoryStorage(ApplicationProvider.getApplicationContext(), storage)
  }

  /** Releases the isolated Firebase app after each test. */
  @After
  fun cleanup() {
    auth.signOut()
    app.delete()
  }

  /** A first upload succeeds, returns a URL, and stores exactly the bytes in the user's slot A. */
  @Test
  fun upload_firstUploadStoresBytesInSlotAAndReturnsUrl() = runBlocking {
    val bytes = ByteArray(UPLOAD_SIZE_BYTES) { (it % 251).toByte() }

    val url = timed { repository.uploadProfilePicture(uid, bytes, null) }.getOrThrow()

    assertTrue(url.contains("${uid}_a.jpg"))
    assertArrayEquals(bytes, timed { storedBytes("a") })
  }

  /**
   * A second upload goes to the other slot and leaves the first picture untouched, so the profile's
   * current picture stays intact until the new URL is saved. A third upload reuses slot A.
   */
  @Test
  fun upload_alternatesSlotsAndNeverOverwritesTheCurrentPicture() = runBlocking {
    val firstBytes = byteArrayOf(1, 2, 3)
    val secondBytes = byteArrayOf(4, 5, 6)
    val thirdBytes = byteArrayOf(7, 8, 9)

    val first = timed { repository.uploadProfilePicture(uid, firstBytes, null) }.getOrThrow()
    val second = timed { repository.uploadProfilePicture(uid, secondBytes, first) }.getOrThrow()

    assertNotEquals(first, second)
    assertTrue(second.contains("${uid}_b.jpg"))
    assertArrayEquals(firstBytes, timed { storedBytes("a") })
    assertArrayEquals(secondBytes, timed { storedBytes("b") })

    val third = timed { repository.uploadProfilePicture(uid, thirdBytes, second) }.getOrThrow()

    assertTrue(third.contains("${uid}_a.jpg"))
    assertArrayEquals(secondBytes, timed { storedBytes("b") })
    assertArrayEquals(thirdBytes, timed { storedBytes("a") })
  }

  /** The rules refuse an upload to someone else's path, which surfaces as STORAGE_FAILURE. */
  @Test
  fun upload_forAnotherUser_failsWithStorageFailure() = runBlocking {
    val result = timed { repository.uploadProfilePicture("not-$uid", byteArrayOf(1, 2, 3), null) }

    val failure = result.exceptionOrNull() as ProfilePictureException
    assertEquals(ProfilePictureError.STORAGE_FAILURE, failure.error)
  }

  /** Reads back the bytes stored in the signed-in user's picture [slot] ("a" or "b"). */
  private suspend fun storedBytes(slot: String): ByteArray =
      storage.reference
          .child("profilePictures/${uid}_$slot.jpg")
          .getBytes(MAX_DOWNLOAD_BYTES)
          .await()

  /** Bounds a call so a stuck emulator fails the test instead of hanging it. */
  private suspend fun <T> timed(action: suspend () -> T): T =
      withTimeout(15_000.milliseconds) { action() }

  private companion object {
    /** Size of the image uploaded in the round-trip test. */
    const val UPLOAD_SIZE_BYTES = 2_048

    /** Download limit when reading the stored image back; larger than [UPLOAD_SIZE_BYTES]. */
    const val MAX_DOWNLOAD_BYTES = 4_096L
  }
}
