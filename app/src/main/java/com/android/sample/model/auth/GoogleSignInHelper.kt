// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import android.app.Activity
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * Opens Google's account picker and reads its ID token. Failures are reported as [AuthException],
 * like the repository's, so one error state covers the whole sign-in. No Activity is retained.
 *
 * Injecting CredentialManager lets tests replace the platform interaction.
 */
class GoogleSignInHelper(private val credentialManager: CredentialManager) {
  /**
   * Requests a token after an explicit tap on a Google sign-in button, including first-time users.
   *
   * [serverClientId] must be the Web OAuth client ID (normally R.string.default_web_client_id), not
   * the Android OAuth client ID. [activity] must be the foreground Activity presenting the picker.
   *
   * @throws AuthException with [AuthError.CANCELLED] if the user closes the picker,
   *   [AuthError.NO_CREDENTIAL] if the device has no Google account, or [AuthError.UNKNOWN] for any
   *   other picker or token failure (often a missing SHA-1 or wrong client ID).
   */
  suspend fun getIdToken(activity: Activity, serverClientId: String): String {
    require(serverClientId.isNotBlank()) { "The Web OAuth client ID must not be blank." }
    val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential =
        try {
          credentialManager.getCredential(activity, request).credential
        } catch (exception: GetCredentialCancellationException) {
          // The user closed the picker.
          throw AuthException(AuthError.CANCELLED, exception)
        } catch (exception: NoCredentialException) {
          // No Google account on the device.
          throw AuthException(AuthError.NO_CREDENTIAL, exception)
        } catch (exception: GetCredentialException) {
          // Setup problems (SHA-1, client ID, Play services) and other picker failures.
          throw AuthException(AuthError.UNKNOWN, exception)
        }
    if (
        credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      throw AuthException(AuthError.UNKNOWN)
    }
    try {
      return GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (exception: GoogleIdTokenParsingException) {
      throw AuthException(AuthError.UNKNOWN, exception)
    }
  }

  /** Clears the provider's active session preference, without deleting saved Google credentials. */
  suspend fun clearCredentialState() {
    credentialManager.clearCredentialState(ClearCredentialStateRequest())
  }
}
