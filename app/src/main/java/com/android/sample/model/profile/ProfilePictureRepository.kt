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
   * Uploads a user's profile picture, replacing the previous one if there is any.
   *
   * @param userId stable authentication identifier; it names the stored picture.
   * @param jpegBytes the already compressed JPEG image.
   * @return [Result.success] containing the URL the picture can be loaded from; [Result.failure]
   *   containing a [ProfilePictureException].
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled. Cancellation
   *   is propagated rather than converted into a failed [Result].
   */
  suspend fun uploadProfilePicture(userId: String, jpegBytes: ByteArray): Result<String>
}
