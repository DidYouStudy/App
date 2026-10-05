// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Opens Google's account picker and reads its ID token. Call from an Activity or screen coroutine,
 * then pass the returned token to the ViewModel. No Activity is retained by this helper.
 *
 * Injecting CredentialManager lets tests replace the platform interaction.
 */
class GoogleSignInHelper(private val credentialManager: CredentialManager) {
  /**
   * Requests a token after an explicit tap on a Google sign-in button, including first-time users.
   *
   * [serverClientId] must be the Web OAuth client ID (normally R.string.default_web_client_id), not
   * the Android OAuth client ID. [activity] must be the foreground Activity presenting the picker.
   * Credential Manager errors and token parsing errors propagate to the caller. Treat
   * GetCredentialCancellationException as dismissal of the picker, not a failed Firebase sign-in.
   */
  suspend fun getIdToken(activity: Activity, serverClientId: String): String {
    require(serverClientId.isNotBlank()) { "The Web OAuth client ID must not be blank." }
    val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = credentialManager.getCredential(activity, request).credential
    check(
        credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      "Credential Manager returned an unsupported credential type."
    }
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
  }

  /** Clears the provider's active session preference, without deleting saved Google credentials. */
  suspend fun clearCredentialState() {
    credentialManager.clearCredentialState(ClearCredentialStateRequest())
  }
}
