// Co-authored-by: Copilot
// Co-authored-by: Claude Code
package com.android.sample.model.profile

/**
 * Provides profile persistence without exposing Firestore to ViewModels or screens.
 *
 * Implementations return [Result] so callers can handle validation and storage failures explicitly
 * without depending on a particular persistence SDK.
 */
interface ProfileRepository {
  /**
   * Looks up a profile by its authentication user ID.
   *
   * @param userId stable authentication identifier used as the Firestore document ID.
   * @return [Result.success] containing the profile, or `null` when no profile exists;
   *   [Result.failure] containing a [ProfileException].
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled. Cancellation
   *   is propagated rather than converted into a failed [Result].
   */
  suspend fun getProfile(userId: String): Result<UserProfile?>

  /**
   * Creates a profile for an authentication user.
   *
   * Implementations must preserve a profile that already exists rather than replacing its data.
   *
   * @param userId stable authentication identifier used as the Firestore document ID.
   * @return [Result.success] containing the existing or newly created profile; [Result.failure]
   *   containing a [ProfileException].
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled. Cancellation
   *   is propagated rather than converted into a failed [Result].
   */
  suspend fun createProfile(userId: String): Result<UserProfile>

  /**
   * Changes the username of an existing profile, keeping usernames unique.
   *
   * Uniqueness is case-insensitive and enforced atomically. Choosing the name the user already owns
   * succeeds without other changes, and the previous name becomes available to others.
   *
   * @param userId stable authentication identifier used as the Firestore document ID.
   * @param username requested username; must satisfy [UsernamePolicy.validate].
   * @return [Result.success] containing the updated profile; [Result.failure] containing a
   *   [ProfileException] whose error is [ProfileError.INVALID_USER_ID],
   *   [ProfileError.INVALID_USERNAME], [ProfileError.USERNAME_TAKEN], or a storage failure
   *   category.
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled. Cancellation
   *   is propagated rather than converted into a failed [Result].
   */
  suspend fun updateUsername(userId: String, username: String): Result<UserProfile>

  /**
   * Saves the URL of a user's profile picture in their existing profile, leaving every other field
   * untouched.
   *
   * The profile must already exist: it is created at sign-in through [createProfile]. Uploading the
   * image itself is the job of [ProfilePictureRepository].
   *
   * @param userId stable authentication identifier used as the Firestore document ID.
   * @param url URL the picture can be loaded from, as returned by
   *   [ProfilePictureRepository.uploadProfilePicture].
   * @return [Result.success] once the URL is saved; [Result.failure] containing a
   *   [ProfileException], including when the profile does not exist.
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled. Cancellation
   *   is propagated rather than converted into a failed [Result].
   */
  suspend fun updateProfilePictureUrl(userId: String, url: String): Result<Unit>
}
