// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import android.content.Context
import android.net.Uri
import com.android.sample.R
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import java.util.concurrent.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class ProfilePictureRepositoryStorageTest {
  private lateinit var storage: FirebaseStorage
  private lateinit var root: StorageReference
  private lateinit var picture: StorageReference
  private lateinit var pictureB: StorageReference
  private lateinit var repository: ProfilePictureRepositoryStorage
  private val bytes = byteArrayOf(1, 2, 3)

  @Before
  fun setUp() {
    storage = mock(FirebaseStorage::class.java)
    root = mock(StorageReference::class.java)
    picture = mock(StorageReference::class.java)
    pictureB = mock(StorageReference::class.java)
    `when`(storage.reference).thenReturn(root)
    `when`(root.child("profilePictures/user123_a.jpg")).thenReturn(picture)
    `when`(root.child("profilePictures/user123_b.jpg")).thenReturn(pictureB)
    val context = mock(Context::class.java)
    `when`(context.getString(R.string.profile_picture_storage_folder)).thenReturn("profilePictures")
    `when`(context.getString(R.string.profile_picture_content_type)).thenReturn("image/jpeg")
    repository = ProfilePictureRepositoryStorage(context, storage)
  }

  /** Builds an [UploadTask] that is already finished, so `await()` returns without waiting. */
  private fun finishedUpload(failure: Exception? = null): UploadTask {
    val task = mock(UploadTask::class.java)
    `when`(task.isComplete).thenReturn(true)
    `when`(task.isCanceled).thenReturn(false)
    `when`(task.exception).thenReturn(failure)
    if (failure == null) {
      `when`(task.result).thenReturn(mock(UploadTask.TaskSnapshot::class.java))
    }
    return task
  }

  /**
   * Makes slot A (or slot B when [target] is [pictureB]) return [task] from `putBytes`; [task] is
   * built beforehand.
   */
  private fun stubUpload(task: UploadTask, target: StorageReference = picture) {
    `when`(target.putBytes(any(), any())).thenReturn(task)
  }

  /** Makes slot A (or slot B when [target] is [pictureB]) return [task] from `downloadUrl`. */
  private fun stubUrl(task: Task<Uri>, target: StorageReference = picture) {
    `when`(target.downloadUrl).thenReturn(task)
  }

  private fun urlOf(value: String): Uri {
    val uri = mock(Uri::class.java)
    `when`(uri.toString()).thenReturn(value)
    return uri
  }

  // Uploads a JPEG to one of the user's own slots and returns the download URL.
  @Test
  fun uploadProfilePicture_uploadsJpegAndReturnsDownloadUrl() = runTest {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forResult(urlOf(storageUrl("user123_a.jpg"))))

    val result = repository.uploadProfilePicture("user123", bytes, null)

    assertEquals(storageUrl("user123_a.jpg"), result.getOrThrow())
    val metadata = argumentCaptor<StorageMetadata>()
    verify(picture).putBytes(any(), metadata.capture())
    assertEquals("image/jpeg", metadata.firstValue.contentType)
  }

  // Blank ids and ids that would create a sub-folder are rejected before touching Storage.
  @Test
  fun uploadProfilePicture_rejectsInvalidUserId() = runTest {
    listOf("", "   ", "a/b").forEach { userId ->
      val result = repository.uploadProfilePicture(userId, bytes, null)

      assertEquals(ProfilePictureError.INVALID_USER_ID, error(result).error)
    }
    verify(storage, never()).reference
  }

  // An empty image is rejected before touching Storage.
  @Test
  fun uploadProfilePicture_rejectsEmptyImage() = runTest {
    val result = repository.uploadProfilePicture("user123", ByteArray(0), null)

    assertEquals(ProfilePictureError.EMPTY_IMAGE, error(result).error)
    verify(storage, never()).reference
  }

  // A Storage failure during the upload becomes STORAGE_FAILURE and keeps its cause.
  @Test
  fun uploadProfilePicture_mapsStorageFailure() = runTest {
    val cause = mock(StorageException::class.java)
    stubUpload(finishedUpload(failure = cause))

    val result = repository.uploadProfilePicture("user123", bytes, null)

    assertEquals(ProfilePictureError.STORAGE_FAILURE, error(result).error)
    assertSame(cause, error(result).cause)
  }

  // A non-Storage failure, here while fetching the URL, becomes UNKNOWN.
  @Test
  fun uploadProfilePicture_mapsUnexpectedFailureToUnknown() = runTest {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forException(IllegalStateException("boom")))

    val result = repository.uploadProfilePicture("user123", bytes, null)

    assertEquals(ProfilePictureError.UNKNOWN, error(result).error)
  }

  // Cancellation is rethrown instead of being turned into a failed Result.
  @Test
  fun uploadProfilePicture_rethrowsCancellation() {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forException(CancellationException("cancelled")))

    assertThrows(CancellationException::class.java) {
      runBlocking { repository.uploadProfilePicture("user123", bytes, null) }
    }
  }

  // Cancelling the caller also cancels the running upload, which await() alone would not do.
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun uploadProfilePicture_cancelledWhileUploading_cancelsTheUploadTask() = runTest {
    // Never completes, so the repository stays suspended on it.
    val pendingUpload = mock(UploadTask::class.java)
    stubUpload(pendingUpload)
    val job = launch { repository.uploadProfilePicture("user123", bytes, null) }
    testScheduler.runCurrent()
    verify(pendingUpload, never()).cancel()

    job.cancel()
    testScheduler.runCurrent()

    verify(pendingUpload).cancel()
  }

  // Without a current picture, the first slot is used.
  @Test
  fun uploadProfilePicture_withoutCurrentUrl_usesSlotA() = runTest {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forResult(urlOf(storageUrl("user123_a.jpg"))))

    repository.uploadProfilePicture("user123", bytes, null)

    verify(picture).putBytes(any(), any())
    verify(pictureB, never()).putBytes(any(), any())
  }

  // A profile pointing to slot A gets its new picture in slot B, so slot A is left untouched.
  @Test
  fun uploadProfilePicture_currentUrlInSlotA_usesSlotB() = runTest {
    stubUpload(finishedUpload(), pictureB)
    stubUrl(Tasks.forResult(urlOf(storageUrl("user123_b.jpg"))), pictureB)

    val result = repository.uploadProfilePicture("user123", bytes, urlInSlot("a"))

    assertEquals(storageUrl("user123_b.jpg"), result.getOrThrow())
    verify(pictureB).putBytes(any(), any())
    verify(picture, never()).putBytes(any(), any())
  }

  // A profile pointing to slot B goes back to slot A, which holds the older picture.
  @Test
  fun uploadProfilePicture_currentUrlInSlotB_usesSlotA() = runTest {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forResult(urlOf(storageUrl("user123_a.jpg"))))

    repository.uploadProfilePicture("user123", bytes, urlInSlot("b"))

    verify(picture).putBytes(any(), any())
    verify(pictureB, never()).putBytes(any(), any())
  }

  // A URL that is not one of the user's slots, such as an older format, selects slot A.
  @Test
  fun uploadProfilePicture_unrecognizedCurrentUrl_usesSlotA() = runTest {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forResult(urlOf(storageUrl("user123_a.jpg"))))

    repository.uploadProfilePicture("user123", bytes, storageUrl("user123.jpg"))

    verify(picture).putBytes(any(), any())
    verify(pictureB, never()).putBytes(any(), any())
  }

  // The slot is read from the object name, so the emulator's http host and port change nothing.
  @Test
  fun uploadProfilePicture_emulatorUrlInSlotA_usesSlotB() = runTest {
    stubUpload(finishedUpload(), pictureB)
    stubUrl(Tasks.forResult(urlOf(storageUrl("user123_b.jpg"))), pictureB)

    repository.uploadProfilePicture(
        "user123",
        bytes,
        "http://10.0.2.2:9199/v0/b/demo-app.appspot.com/o/profilePictures%2Fuser123_a.jpg" +
            "?alt=media&token=t",
    )

    verify(pictureB).putBytes(any(), any())
    verify(picture, never()).putBytes(any(), any())
  }

  // Only the object name decides the slot: look-alike names in other URL parts or other objects do
  // not count as slot A, so slot A is chosen (and it is the free slot, as the URL is not slot A).
  @Test
  fun uploadProfilePicture_lookAlikeUrls_areNotSlotA() = runTest {
    val lookAlikes =
        listOf(
            // Same file name in another folder.
            storageUrl("otherFolder/user123_a.jpg"),
            // Another user whose id ends with this user's id.
            storageUrl("xuser123_a.jpg"),
            // The slot A name only appears in the query string.
            storageUrl("user123_b.jpg", token = "user123_a.jpg"),
            // The slot A name only appears in the bucket name.
            storageUrl("user123_b.jpg", bucket = "user123_a.jpg"),
            // Not a Storage download URL at all.
            "https://example.test/user123_a.jpg",
            "not a url",
        )
    stubUpload(finishedUpload())
    stubUrl(Tasks.forResult(urlOf(storageUrl("user123_a.jpg"))))

    lookAlikes.forEach { url -> repository.uploadProfilePicture("user123", bytes, url) }

    verify(picture, times(lookAlikes.size)).putBytes(any(), any())
    verify(pictureB, never()).putBytes(any(), any())
  }

  // The point of the slots: even when the upload fails after the bytes are stored (here while
  // fetching the URL), the slot the profile points to has not been written to.
  @Test
  fun uploadProfilePicture_failureAfterUpload_neverTouchesCurrentSlot() = runTest {
    stubUpload(finishedUpload(), pictureB)
    stubUrl(Tasks.forException(IllegalStateException("boom")), pictureB)

    val result = repository.uploadProfilePicture("user123", bytes, urlInSlot("a"))

    assertTrue(result.isFailure)
    verify(picture, never()).putBytes(any(), any())
  }

  /** A real-format download URL for the user's picture in the given [slot] ("a" or "b"). */
  private fun urlInSlot(slot: String) = storageUrl("user123_$slot.jpg")

  /**
   * A download URL in the format Firebase Storage returns:
   * `https://firebasestorage.googleapis.com/v0/b/<bucket>/o/<escaped name>?alt=media&token=<t>`,
   * where the `/` between the folder and the file name is escaped as `%2F`.
   *
   * @param file file name inside `profilePictures`, or a `folder/file` path to use another folder.
   */
  private fun storageUrl(
      file: String,
      bucket: String = "my-app.appspot.com",
      token: String = "0a1b2c3d-4e5f-6789-abcd-ef0123456789",
  ): String {
    val name = if (file.contains('/')) file else "profilePictures/$file"
    return "https://firebasestorage.googleapis.com/v0/b/$bucket/o/" +
        "${name.replace("/", "%2F")}?alt=media&token=$token"
  }

  /** Returns the [ProfilePictureException] of a failed [result], failing the test otherwise. */
  private fun error(result: Result<String>): ProfilePictureException {
    assertTrue(result.isFailure)
    return result.exceptionOrNull() as ProfilePictureException
  }
}
