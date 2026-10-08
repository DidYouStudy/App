// Co-authored-by: Copilot
package com.android.sample.model.profile

import android.content.Context

/**
 * Application-level wiring for the profile repository.
 *
 * This provider is separate from [com.android.sample.model.auth.AuthRepositoryProvider], which
 * creates the Firebase repository responsible for authentication, while this provider creates the
 * Firestore repository responsible for persisted profile documents. The context parameter is kept
 * so callers can use the same provider shape as the authentication repository; Firestore uses its
 * own SDK initialization here, so the context is intentionally not read or retained.
 */
object ProfileRepositoryProvider {
  private var instance: ProfileRepository? = null

  /**
   * Returns the process-shared Firestore profile repository.
   *
   * @param context context accepted for provider API consistency; it does not affect this
   *   repository's construction and is not retained.
   * @return the cached [ProfileRepository] implementation.
   */
  @Synchronized
  fun getRepository(@Suppress("UNUSED_PARAMETER") context: Context): ProfileRepository {
    instance?.let {
      return it
    }
    return ProfileRepositoryFirestore().also { instance = it }
  }
}
