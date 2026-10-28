// Co-authored-by: GitHub Copilot
// Co-authored-by: Codex AI Agent
package com.android.sample.model.profile

import com.android.sample.model.auth.UserAccount
import kotlinx.coroutines.CancellationException

/**
 * Resolves the persisted profile for an already authenticated user.
 *
 * Authentication remains the responsibility of [com.android.sample.ui.auth.AuthViewModel] and
 * [com.android.sample.model.auth.AuthRepository]. This coordinator owns only the profile lookup and
 * conditional creation sequence.
 *
 * @param repository profile persistence used to look up or create the profile.
 */
class ProfileResolutionCoordinator(private val repository: ProfileRepository) {
  /**
   * Returns the existing profile or creates one when the user has no profile yet.
   *
   * Profile creation is attempted only after a successful lookup that returns `null`. Repository
   * failures are returned unchanged, directly thrown profile exceptions and coroutine cancellation
   * are propagated, and other implementation exceptions become failed results.
   *
   * The repository contract normally reports lookup and creation failures through [Result]. Its
   * implementation or a dependency may still throw while performing either operation: a thrown
   * [CancellationException] is rethrown so the caller's coroutine can stop, a thrown
   * [ProfileException] is rethrown unchanged, and any other [Exception] is wrapped in a failed
   * result with [ProfileError.UNKNOWN]. A cancellation exception stored in a failed [Result] is
   * also rethrown rather than returned as an ordinary repository failure.
   *
   * @param user authenticated account whose UID identifies the profile document.
   * @return a successful result containing the existing or newly created profile, or the original
   *   repository failure.
   */
  suspend fun resolve(user: UserAccount): Result<UserProfile> {
    if (user.uid.isBlank()) {
      return Result.failure(ProfileException(ProfileError.INVALID_USER_ID))
    }
    return try {
      // Look up first so an existing profile is reused and creation is only attempted when the
      // repository successfully confirms that no profile exists.
      val existingProfile =
          repository.getProfile(user.uid).rethrowCancellation().getOrElse {
            return Result.failure(it)
          }
      if (existingProfile != null) {
        return validateProfile(user, existingProfile)
      }

      // A null lookup result is the only path that creates a profile. The repository may find that
      // another operation created it in the meantime and return that existing profile instead.
      val profile =
          repository.createProfile(user.uid).rethrowCancellation().getOrElse {
            return Result.failure(it)
          }
      validateProfile(user, profile)
    } catch (exception: CancellationException) {
      // Repository calls can throw cancellation directly (rather than return it in Result); keep
      // coroutine cancellation observable instead of wrapping it as an unknown profile failure.
      throw exception
    } catch (exception: ProfileException) {
      // Preserve known profile errors thrown directly by a repository implementation.
      throw exception
    } catch (exception: Exception) {
      // Convert unexpected implementation failures to an unknown error.
      Result.failure(ProfileException(ProfileError.UNKNOWN, exception))
    }
  }

  /**
   * Verifies that a profile belongs to the authenticated user.
   *
   * Only the profile UID determines whether the result is safe to publish.
   *
   * @param user authenticated account whose UID must own the profile.
   * @param profile existing or newly created profile to validate.
   * @return the profile when its UID matches, or a failed result with
   *   [ProfileError.INVALID_PROFILE_DATA].
   */
  private fun validateProfile(
      user: UserAccount,
      profile: UserProfile,
  ): Result<UserProfile> =
      if (profile.uid == user.uid) {
        Result.success(profile)
      } else {
        Result.failure(ProfileException(ProfileError.INVALID_PROFILE_DATA))
      }

  /**
   * Rethrows cancellation stored in a failed repository result so it remains coroutine control
   * flow.
   */
  private fun <T> Result<T>.rethrowCancellation(): Result<T> {
    val failure = exceptionOrNull()
    if (failure is CancellationException) throw failure
    return this
  }
}
