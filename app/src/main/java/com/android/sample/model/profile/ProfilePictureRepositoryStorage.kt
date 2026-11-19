// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import android.content.Context
import com.android.sample.R
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Cloud Storage implementation of [ProfilePictureRepository].
 *
 * Each user has two picture slots, `profilePictures/{userId}_a.jpg` and
 * `profilePictures/{userId}_b.jpg`. A new picture always goes to the slot the profile does not
 * currently point to, so the current picture and its URL stay valid until the caller has saved the
 * new URL. The next upload then reuses the older slot, so at most two files exist per user and
 * nothing needs deleting. Firebase access stays in this model-layer class so ViewModels depend only
 * on [ProfilePictureRepository].
 *
 * The Storage folder and the content type must match `storage.rules`, so they are read once from
 * non-translatable string resources ([R.string.profile_picture_storage_folder] and
 * [R.string.profile_picture_content_type]) instead of being hard-coded here.
 *
 * @param context used only to read those two resources; it is not retained.
 * @param storage Cloud Storage client used for uploads and URL lookups.
 */
class ProfilePictureRepositoryStorage(
    context: Context,
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : ProfilePictureRepository {
  private val folder = context.getString(R.string.profile_picture_storage_folder)
  private val contentType = context.getString(R.string.profile_picture_content_type)

  /**
   * Uploads [jpegBytes] to the user's slot that [currentUrl] does not point to and returns its
   * download URL.
   *
   * @param userId stable authentication identifier; must not be blank or contain `/`.
   * @param jpegBytes the already compressed JPEG image; must not be empty.
   * @param currentUrl the picture URL the profile references now, or null if it has none.
   * @return [Result.success] containing the download URL; [Result.failure] containing
   *   [ProfilePictureException] with a stable [ProfilePictureError] category.
   * @throws CancellationException when the coroutine is cancelled.
   */
  override suspend fun uploadProfilePicture(
      userId: String,
      jpegBytes: ByteArray,
      currentUrl: String?,
  ): Result<String> {
    // A "/" would turn the file name into a sub-folder, which the Storage rules refuse anyway.
    if (userId.isBlank() || userId.contains('/')) {
      return Result.failure(ProfilePictureException(ProfilePictureError.INVALID_USER_ID))
    }
    if (jpegBytes.isEmpty()) {
      return Result.failure(ProfilePictureException(ProfilePictureError.EMPTY_IMAGE))
    }
    return execute {
      val reference =
          storage.reference.child("$folder/${userId}_${freeSlot(userId, currentUrl)}.jpg")
      // The Storage rules only accept image/jpeg, so the type is always declared explicitly.
      val metadata = StorageMetadata.Builder().setContentType(contentType).build()
      reference.putBytes(jpegBytes, metadata).await()
      reference.downloadUrl.await().toString()
    }
  }

  /**
   * Chooses the slot a new picture is uploaded to: the one [currentUrl] does not point to.
   *
   * A Storage download URL contains the file name, so it tells which slot it belongs to. A null URL
   * or one for any other file, such as a picture of an earlier format, selects [SLOT_A]; the
   * current file is then left alone all the same, because it is not one of the two slots.
   *
   * @param userId the user whose slots are considered.
   * @param currentUrl the picture URL the profile references now, or null if it has none.
   * @return [SLOT_B] when [currentUrl] points to slot A, otherwise [SLOT_A].
   */
  private fun freeSlot(userId: String, currentUrl: String?): String {
    val usesSlotA = currentUrl?.contains("${userId}_$SLOT_A.jpg") == true
    return if (usesSlotA) SLOT_B else SLOT_A
  }

  /**
   * Executes a Storage operation and maps failures to profile picture domain errors.
   *
   * Successful values are wrapped in [Result.success]. Coroutine cancellation is rethrown so
   * callers retain structured-concurrency behavior. Storage failures become
   * [ProfilePictureError.STORAGE_FAILURE]; other exceptions become [ProfilePictureError.UNKNOWN].
   *
   * @param operation suspendable Storage operation to execute.
   * @return successful operation value or a [ProfilePictureException]-backed failure.
   * @throws CancellationException when the coroutine is cancelled.
   */
  private suspend fun <T> execute(operation: suspend () -> T): Result<T> {
    return try {
      Result.success(operation())
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: StorageException) {
      Result.failure(ProfilePictureException(ProfilePictureError.STORAGE_FAILURE, exception))
    } catch (exception: Exception) {
      Result.failure(ProfilePictureException(ProfilePictureError.UNKNOWN, exception))
    }
  }

  private companion object {
    /** Suffix of the first picture slot; also the slot used when the profile has no picture. */
    const val SLOT_A = "a"

    /** Suffix of the second picture slot. */
    const val SLOT_B = "b"
  }
}
