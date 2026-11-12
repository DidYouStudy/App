// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
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
   * Only the application context is used; Activities are never retained. ViewModel tests should
   * inject fakes into the ViewModel directly; tests that start the whole app use [setRepository].
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
   * Replaces the shared repository, so tests that start the whole app can supply a fake or an
   * emulator-backed one without initializing Firebase or Credential Manager. `null` removes the
   * replacement: the next [getRepository] call creates a fresh Firebase repository.
   */
  @VisibleForTesting
  @Synchronized
  fun setRepository(repository: AuthRepository?) {
    instance = repository
  }

  /**
   * Replaces the Credential Manager behind the account picker, so tests can return a fake Google ID
   * token (for example to sign in against the Firebase Auth emulator). `null` restores the platform
   * Credential Manager on next use.
   *
   * [requestGoogleIdToken] uses the new manager immediately. A Firebase repository that
   * [getRepository] already created keeps clearing credentials through the previous one, so set the
   * manager before the repository is first created to keep sign-in and sign-out on one manager.
   */
  @VisibleForTesting
  @Synchronized
  fun setCredentialManager(credentialManager: CredentialManager?) {
    helper = credentialManager?.let(::GoogleSignInHelper)
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
