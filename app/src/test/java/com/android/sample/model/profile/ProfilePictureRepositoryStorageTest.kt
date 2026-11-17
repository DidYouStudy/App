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
import org.mockito.kotlin.verify

class ProfilePictureRepositoryStorageTest {
  private lateinit var storage: FirebaseStorage
  private lateinit var root: StorageReference
  private lateinit var picture: StorageReference
  private lateinit var repository: ProfilePictureRepositoryStorage
  private val bytes = byteArrayOf(1, 2, 3)

  @Before
  fun setUp() {
    storage = mock(FirebaseStorage::class.java)
    root = mock(StorageReference::class.java)
    picture = mock(StorageReference::class.java)
    `when`(storage.reference).thenReturn(root)
    `when`(root.child("profilePictures/user123.jpg")).thenReturn(picture)
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

  /** Makes the picture reference return [task] from `putBytes`; [task] is built beforehand. */
  private fun stubUpload(task: UploadTask) {
    `when`(picture.putBytes(any(), any())).thenReturn(task)
  }

  /** Makes the picture reference return [task] from `downloadUrl`. */
  private fun stubUrl(task: Task<Uri>) {
    `when`(picture.downloadUrl).thenReturn(task)
  }

  private fun urlOf(value: String): Uri {
    val uri = mock(Uri::class.java)
    `when`(uri.toString()).thenReturn(value)
    return uri
  }

  // Uploads a JPEG to the user's own path and returns the download URL.
  @Test
  fun uploadProfilePicture_uploadsJpegAndReturnsDownloadUrl() = runTest {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forResult(urlOf("https://example.test/a.jpg")))

    val result = repository.uploadProfilePicture("user123", bytes)

    assertEquals("https://example.test/a.jpg", result.getOrThrow())
    val metadata = argumentCaptor<StorageMetadata>()
    verify(picture).putBytes(any(), metadata.capture())
    assertEquals("image/jpeg", metadata.firstValue.contentType)
  }

  // Blank ids and ids that would create a sub-folder are rejected before touching Storage.
  @Test
  fun uploadProfilePicture_rejectsInvalidUserId() = runTest {
    listOf("", "   ", "a/b").forEach { userId ->
      val result = repository.uploadProfilePicture(userId, bytes)

      assertEquals(ProfilePictureError.INVALID_USER_ID, error(result).error)
    }
    verify(storage, never()).reference
  }

  // An empty image is rejected before touching Storage.
  @Test
  fun uploadProfilePicture_rejectsEmptyImage() = runTest {
    val result = repository.uploadProfilePicture("user123", ByteArray(0))

    assertEquals(ProfilePictureError.EMPTY_IMAGE, error(result).error)
    verify(storage, never()).reference
  }

  // A Storage failure during the upload becomes STORAGE_FAILURE and keeps its cause.
  @Test
  fun uploadProfilePicture_mapsStorageFailure() = runTest {
    val cause = mock(StorageException::class.java)
    stubUpload(finishedUpload(failure = cause))

    val result = repository.uploadProfilePicture("user123", bytes)

    assertEquals(ProfilePictureError.STORAGE_FAILURE, error(result).error)
    assertSame(cause, error(result).cause)
  }

  // A non-Storage failure, here while fetching the URL, becomes UNKNOWN.
  @Test
  fun uploadProfilePicture_mapsUnexpectedFailureToUnknown() = runTest {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forException(IllegalStateException("boom")))

    val result = repository.uploadProfilePicture("user123", bytes)

    assertEquals(ProfilePictureError.UNKNOWN, error(result).error)
  }

  // Cancellation is rethrown instead of being turned into a failed Result.
  @Test
  fun uploadProfilePicture_rethrowsCancellation() {
    stubUpload(finishedUpload())
    stubUrl(Tasks.forException(CancellationException("cancelled")))

    assertThrows(CancellationException::class.java) {
      runBlocking { repository.uploadProfilePicture("user123", bytes) }
    }
  }

  /** Returns the [ProfilePictureException] of a failed [result], failing the test otherwise. */
  private fun error(result: Result<String>): ProfilePictureException {
    assertTrue(result.isFailure)
    return result.exceptionOrNull() as ProfilePictureException
  }
}
