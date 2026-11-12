// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.google.firebase.firestore.FirebaseFirestore

/** Supplies the application's shared preferences repository; tests can replace it. */
object PreferencesRepositoryProvider {
  private var instance: PreferencesRepository? = null

  /**
   * Returns the shared preferences repository. If [context] is provided, returns an offline-first
   * synced repository backed by local storage and Firestore.
   */
  @Synchronized
  fun getRepository(context: Context? = null): PreferencesRepository {
    instance?.let {
      return it
    }
    val firestoreRepo = PreferencesRepositoryFirestore(FirebaseFirestore.getInstance())
    val repository =
        if (context != null) {
          PreferencesRepositorySync(
              SharedPreferencesPreferencesLocalStore(context.applicationContext),
              firestoreRepo,
          )
        } else {
          firestoreRepo
        }
    return repository.also { instance = it }
  }

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
