// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.testing

import android.app.PendingIntent
import android.content.Context
import android.os.CancellationSignal
import android.util.Base64
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PrepareGetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.concurrent.Executor
import org.json.JSONObject

/**
 * Stands in for the platform account picker: [getCredential] immediately returns a fake Google ID
 * token for [email] instead of showing UI, so the app's real sign-in flow can run in tests.
 *
 * The token is the unsigned JWT form (alg "none") that the Firebase Auth emulator accepts for
 * `google.com` sign-in (it never validates a signature), and that production Firebase would reject.
 * It does not exercise the real Google account picker.
 *
 * @param email address of the fake Google account; each distinct [subject] is a distinct user.
 * @param subject Google account id (`sub`), which the emulator turns into a new user once.
 */
class FakeGoogleCredentialManager(
    private val email: String,
    private val subject: String = "google-$email",
) : CredentialManager {

  /** Number of times the app asked for a credential, so tests can assert the picker was used. */
  var getCredentialCalls = 0
    private set

  /** Number of times the app cleared its credential state (sign-out). */
  var clearCalls = 0
    private set

  /** Returns the fake Google credential straight away, without any UI. */
  override suspend fun getCredential(
      context: Context,
      request: GetCredentialRequest,
  ): GetCredentialResponse {
    getCredentialCalls++
    return GetCredentialResponse(
        CustomCredential(
            GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            GoogleIdTokenCredential.Builder().setId(email).setIdToken(idToken()).build().data,
        )
    )
  }

  /** Records the sign-out cleanup; there is no real credential state to clear. */
  override suspend fun clearCredentialState(request: ClearCredentialStateRequest) {
    clearCalls++
  }

  /** Builds the unsigned JSON ID token understood by the Firebase Auth emulator. */
  /** The fake Google ID token, also usable to sign in directly through the SDK. */
  fun idToken(): String {
    val header = JSONObject().put("alg", "none").put("typ", "JWT")
    val payload =
        JSONObject()
            .put("iss", "https://accounts.google.com")
            .put("sub", subject)
            .put("email", email)
            .put("email_verified", true)
            .put("name", "End To End")
    return listOf(header, payload).joinToString(".", postfix = ".signature") { part ->
      Base64.encodeToString(
          part.toString().toByteArray(),
          Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
      )
    }
  }

  // The callback-based variants are never used by the app, so they are deliberately unsupported.
  override fun getCredentialAsync(
      context: Context,
      request: GetCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
  ) = throw UnsupportedOperationException()

  override fun getCredentialAsync(
      context: Context,
      pendingGetCredentialHandle: PrepareGetCredentialResponse.PendingGetCredentialHandle,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<GetCredentialResponse, GetCredentialException>,
  ) = throw UnsupportedOperationException()

  override fun prepareGetCredentialAsync(
      request: GetCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<PrepareGetCredentialResponse, GetCredentialException>,
  ) = throw UnsupportedOperationException()

  override fun createCredentialAsync(
      context: Context,
      request: CreateCredentialRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
  ) = throw UnsupportedOperationException()

  override fun clearCredentialStateAsync(
      request: ClearCredentialStateRequest,
      cancellationSignal: CancellationSignal?,
      executor: Executor,
      callback: CredentialManagerCallback<Void?, ClearCredentialException>,
  ) = throw UnsupportedOperationException()

  override fun createSettingsPendingIntent(): PendingIntent = throw UnsupportedOperationException()
}
