// Co-authored-by: GitHub Copilot
package com.android.sample.model.preferences

import com.google.firebase.firestore.FirebaseFirestore

/** Supplies the application's shared preferences repository. */
object PreferencesRepositoryProvider {
  private var instance: PreferencesRepository? = null

  /** Returns the shared Firestore-backed preferences repository. */
  @Synchronized
  fun getRepository(): PreferencesRepository =
      instance
          ?: PreferencesRepositoryFirestore(FirebaseFirestore.getInstance()).also { instance = it }
}
