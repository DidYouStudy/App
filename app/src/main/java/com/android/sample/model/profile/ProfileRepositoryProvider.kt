// Co-authored-by: Copilot
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.profile

import androidx.annotation.VisibleForTesting

/**
 * Application-level wiring for the profile repository.
 *
 * This provider is separate from [com.android.sample.model.auth.AuthRepositoryProvider], which
 * creates the Firebase repository responsible for authentication, while this provider creates the
 * Firestore repository responsible for persisted profile documents. Firestore uses its own SDK
 * initialization, so no Android context is required.
 */
object ProfileRepositoryProvider {
  private var instance: ProfileRepository? = null

  /**
   * Returns the process-shared Firestore profile repository, or the one set for tests.
   *
   * @return the cached [ProfileRepository] implementation.
   */
  @Synchronized
  fun getRepository(): ProfileRepository {
    instance?.let {
      return it
    }
    return ProfileRepositoryFirestore().also { instance = it }
  }

  /**
   * Replaces the shared repository, so tests that start the whole app can supply a fake or an
   * emulator-backed one without initializing Firestore. `null` removes the replacement: the next
   * [getRepository] call creates a fresh Firestore repository.
   */
  @VisibleForTesting
  @Synchronized
  fun setRepository(repository: ProfileRepository?) {
    instance = repository
  }
}
