// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth

/**
 * Application-level wiring. Screens obtain the contract here and inject it into their ViewModel.
 */
object AuthRepositoryProvider {
  private var instance: AuthRepository? = null

  /**
   * Returns the shared repository, creating it on first use after Firebase's normal initialization.
   * Only the application context is used; Activities are never retained. Tests should inject fake
   * repositories directly into ViewModels instead of mutating this process-wide provider.
   */
  @Synchronized
  fun getRepository(context: Context): AuthRepository {
    instance?.let {
      return it
    }
    val helper = GoogleSignInHelper(CredentialManager.create(context.applicationContext))
    return AuthRepositoryFirebase(FirebaseAuth.getInstance(), helper::clearCredentialState).also {
      instance = it
    }
  }
}
