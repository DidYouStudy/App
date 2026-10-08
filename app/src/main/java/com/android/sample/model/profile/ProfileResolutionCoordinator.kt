// Co-authored-by: Copilot
package com.android.sample.model.profile

import com.android.sample.model.auth.UserAccount

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
   * failures are returned unchanged, and coroutine cancellation is propagated by the repository.
   *
   * @param user authenticated account whose UID identifies the profile document.
   * @return a successful result containing the existing or newly created profile, or the original
   *   repository failure.
   */
  suspend fun resolve(user: UserAccount): Result<UserProfile> {
    return repository
        .getProfile(user.uid)
        .fold(
            onSuccess = { existingProfile ->
              existingProfile?.let { Result.success(it) } ?: repository.createProfile(user.uid)
            },
            onFailure = { Result.failure(it) },
        )
  }
}
