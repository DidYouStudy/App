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
   * @param user authenticated account whose UID identifies the profile document.
   * @return a successful result containing the existing or newly created profile, or the original
   *   repository failure.
   */
  suspend fun resolve(user: UserAccount): Result<ProfileResolution> {
    if (user.uid.isBlank()) {
      return Result.failure(ProfileException(ProfileError.INVALID_USER_ID))
    }
    return try {
      val existingProfile =
          repository
              .getProfile(user.uid)
              .fold(
                  onSuccess = { it },
                  onFailure = {
                    if (it is CancellationException) throw it
                    return Result.failure(it)
                  },
              )
      if (existingProfile != null) {
        return validateProfile(user, ProfileResolution.Existing(existingProfile))
      }

      val resolution =
          repository
              .createProfile(user.uid)
              .fold(
                  onSuccess = { it },
                  onFailure = {
                    if (it is CancellationException) throw it
                    return Result.failure(it)
                  },
              )
      validateProfile(user, resolution)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: ProfileException) {
      throw exception
    } catch (exception: Exception) {
      Result.failure(ProfileException(ProfileError.UNKNOWN, exception))
    }
  }

  /**
   * Verifies that a resolution belongs to the authenticated user.
   *
   * The repository may return an existing profile from a concurrent create operation, so both
   * [ProfileResolution.Existing] and [ProfileResolution.Created] are valid outcomes. Only the
   * contained profile UID determines whether the result is safe to publish.
   *
   * @param user authenticated account whose UID must own the profile.
   * @param resolution existing or newly created profile resolution to validate.
   * @return the original resolution when its profile UID matches, or a failed result with
   *   [ProfileError.INVALID_PROFILE_DATA].
   */
  private fun validateProfile(
      user: UserAccount,
      resolution: ProfileResolution?,
  ): Result<ProfileResolution> =
      if (resolution == null) {
        Result.failure(ProfileException(ProfileError.INVALID_PROFILE_DATA))
      } else if (resolution.profile.uid == user.uid) {
        Result.success(resolution)
      } else {
        Result.failure(ProfileException(ProfileError.INVALID_PROFILE_DATA))
      }
}
