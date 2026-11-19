// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

/**
 * Provides profile picture storage without exposing Cloud Storage to ViewModels or screens.
 *
 * It only stores the image bytes. Saving the returned URL in the user's [UserProfile] is the job of
 * [ProfileRepository]. Implementations return [Result] so callers can handle failures explicitly
 * without depending on a particular storage SDK.
 */
fun interface ProfilePictureRepository {
  /**
   * Uploads a user's profile picture as a new image, without touching the one [currentUrl] points
   * to.
   *
   * Replacing a stored image invalidates its old URL, so overwriting the picture the profile still
   * references could leave the profile with a dead URL if saving the new one then fails. The new
   * image therefore goes to a different file than the current one, and the old URL keeps working
   * until the caller has saved the returned URL in the profile.
   *
   * @param userId stable authentication identifier; it names the stored picture.
   * @param jpegBytes the already compressed JPEG image.
   * @param currentUrl the picture URL the profile references now, or null if it has none. The
   *   upload avoids the file this URL points to.
   * @return [Result.success] containing the URL the picture can be loaded from; [Result.failure]
   *   containing a [ProfilePictureException].
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled. Cancellation
   *   is propagated rather than converted into a failed [Result].
   */
  suspend fun uploadProfilePicture(
      userId: String,
      jpegBytes: ByteArray,
      currentUrl: String?,
  ): Result<String>
}
