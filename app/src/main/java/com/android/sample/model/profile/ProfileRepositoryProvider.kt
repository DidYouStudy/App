// Co-authored-by: Copilot
package com.android.sample.model.profile

import android.content.Context

/**
 * Application-level wiring for the profile repository.
 *
 * This provider is separate from [com.android.sample.model.auth.AuthRepositoryProvider]:
 * [com.android.sample.model.auth.AuthRepositoryProvider] creates the Firebase repository
 * responsible for authentication, while this provider creates the Firestore repository responsible
 * for persisted profile documents.
 */
object ProfileRepositoryProvider {
  private var instance: ProfileRepository? = null

  /**
   * Returns the process-shared Firestore profile repository.
   *
   * @param context application context accepted for provider consistency; Firestore itself is
   *   obtained through its SDK singleton.
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
