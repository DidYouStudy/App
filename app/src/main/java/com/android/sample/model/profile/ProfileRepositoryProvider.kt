// Co-authored-by: Copilot
package com.android.sample.model.profile

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
   * Returns the process-shared Firestore profile repository.
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
}
