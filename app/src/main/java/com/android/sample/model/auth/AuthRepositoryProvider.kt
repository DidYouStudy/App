// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import com.android.sample.R
import com.google.firebase.auth.FirebaseAuth

/**
 * Application-level wiring. Screens obtain the contract here and inject it into their ViewModel.
 *
 * Sign-in and sign-out share one [GoogleSignInHelper], so the account picker and the credential
 * cleanup on sign-out go through the same Credential Manager.
 */
object AuthRepositoryProvider {
  private var helper: GoogleSignInHelper? = null
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
    return AuthRepositoryFirebase(
            FirebaseAuth.getInstance(),
            getHelper(context)::clearCredentialState,
        )
        .also { instance = it }
  }

  /**
   * Opens Google's account picker on [activity] and returns the ID token, through the shared
   * helper. [activity] is used for this call only and is never retained.
   *
   * Not synchronized: the picker can stay open for seconds and must not block [getRepository].
   *
   * @throws AuthException as described by [GoogleSignInHelper.getIdToken].
   */
  suspend fun requestGoogleIdToken(activity: Activity): String =
      getHelper(activity).getIdToken(activity, activity.getString(R.string.default_web_client_id))

  /**
   * Returns the shared helper, creating it on first use. Synchronized because
   * [requestGoogleIdToken] calls it without holding the provider's lock.
   */
  @Synchronized
  private fun getHelper(context: Context): GoogleSignInHelper {
    helper?.let {
      return it
    }
    return GoogleSignInHelper(CredentialManager.create(context.applicationContext)).also {
      helper = it
    }
  }
}
