// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth

/**
 * Application-level wiring. Screens obtain the contract here and inject it into their ViewModel.
 */
object AuthRepositoryProvider {
  @Volatile private var instance: AuthRepository? = null

  /**
   * Returns the shared repository, creating it on first use after Firebase's normal initialization.
   * Only the application context is used; Activities are never retained. Tests should inject fake
   * repositories directly into ViewModels instead of mutating this process-wide provider.
   */
  fun getRepository(context: Context): AuthRepository =
      instance
          ?: synchronized(this) {
            instance
                ?: run {
                  val helper =
                      GoogleSignInHelper(CredentialManager.create(context.applicationContext))
                  AuthRepositoryFirebase(FirebaseAuth.getInstance(), helper::clearCredentialState)
                      .also { instance = it }
                }
          }
}
