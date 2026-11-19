// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import android.content.Context
import com.android.sample.R
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.net.URI
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
   * @param currentUrl the picture URL the profile references now, or null if it has none. It must
   *   come from the freshly loaded profile, not a cached copy: a stale URL makes the upload
   *   overwrite the live slot (see [ProfilePictureRepository.uploadProfilePicture]).
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
   * A Storage download URL embeds the object name, so it tells which slot it belongs to. The name
   * is parsed out of the URL (see [objectNameOf]) and compared with the slot A name as a whole. A
   * null URL or one for any other object, such as a picture of an earlier format, selects [SLOT_A];
   * the current file is then left alone all the same, because it is not one of the two slots.
   *
   * @param userId the user whose slots are considered.
   * @param currentUrl the picture URL the profile references now, or null if it has none.
   * @return [SLOT_B] when [currentUrl] points to slot A, otherwise [SLOT_A].
   */
  private fun freeSlot(userId: String, currentUrl: String?): String {
    val usesSlotA = currentUrl?.let(::objectNameOf) == "$folder/${userId}_$SLOT_A.jpg"
    return if (usesSlotA) SLOT_B else SLOT_A
  }

  /**
   * Extracts the decoded object name from a Storage download URL.
   *
   * A download URL looks like `https://<host>/v0/b/<bucket>/o/<escaped name>?alt=media&token=<t>`,
   * where the `/` of the name is escaped as `%2F`. Only the path is read, so a user id or file name
   * that appears in the host, bucket or query string is never mistaken for the object name.
   *
   * @param url the picture URL to parse.
   * @return the unescaped object name, for example `profilePictures/uid_a.jpg`, or null when [url]
   *   is not a well-formed Storage download URL.
   */
  private fun objectNameOf(url: String): String? {
    // java.net.URI is used rather than android.net.Uri so that this also runs in plain unit tests.
    val path = runCatching { URI(url).path }.getOrNull() ?: return null
    return OBJECT_PATH.matchEntire(path)?.groupValues?.get(1)
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

    /**
     * Decoded path of a download URL, `/v0/b/<bucket>/o/<object name>`; group 1 is the object name.
     * The bucket is a single path segment, so the first `/o/` after it starts the object name.
     */
    val OBJECT_PATH = Regex("^/v0/b/[^/]+/o/(.+)$")
  }
}
