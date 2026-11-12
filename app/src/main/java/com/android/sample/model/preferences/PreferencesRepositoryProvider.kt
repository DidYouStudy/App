// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.preferences

import androidx.annotation.VisibleForTesting
import com.google.firebase.firestore.FirebaseFirestore

/** Supplies the application's shared preferences repository; tests can replace it. */
object PreferencesRepositoryProvider {
  private var instance: PreferencesRepository? = null

  /** Returns the shared Firestore-backed preferences repository, or the one set for tests. */
  @Synchronized
  fun getRepository(): PreferencesRepository =
      instance
          ?: PreferencesRepositoryFirestore(FirebaseFirestore.getInstance()).also { instance = it }

  /**
   * Replaces the shared repository, so tests that start the whole app can supply a fake or an
   * emulator-backed one without initializing Firestore. `null` removes the replacement: the next
   * [getRepository] call creates a fresh Firestore repository.
   */
  @VisibleForTesting
  @Synchronized
  fun setRepository(repository: PreferencesRepository?) {
    instance = repository
  }
}
