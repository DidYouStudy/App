// Co-authored-by: Copilot
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
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled.
   */
  suspend fun getProfile(userId: String): Result<UserProfile?>

  /**
   * Creates a profile for an authentication user.
   *
   * Implementations must preserve a profile that already exists rather than replacing its data.
   *
   * @param userId stable authentication identifier used as the Firestore document ID.
   * @return [Result.success] containing the created or existing profile; [Result.failure]
   *   containing a [ProfileException].
   * @throws kotlinx.coroutines.CancellationException when the coroutine is cancelled.
   */
  suspend fun createProfile(userId: String): Result<UserProfile>
}
