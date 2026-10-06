// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed sessions. Inject the Firebase instance and credential cleanup function so that
 * construction has no hidden SDK initialization and tests can replace the external services.
 */
class AuthRepositoryFirebase(
    private val firebaseAuth: FirebaseAuth,
    private val clearCredentialState: suspend () -> Unit,
) : AuthRepository {
  override val currentUser: Flow<UserAccount?> = callbackFlow {
    // Firebase immediately calls a newly registered listener with its persisted session.
    val listener = FirebaseAuth.AuthStateListener { auth ->
      trySend(auth.currentUser?.toUserAccount())
    }
    firebaseAuth.addAuthStateListener(listener)
    awaitClose { firebaseAuth.removeAuthStateListener(listener) }
  }
      .buffer(Channel.CONFLATED)
      .distinctUntilChanged()

  /** Exchanges a Google ID token for a Firebase session. */
  override suspend fun signInWithGoogle(idToken: String): UserAccount {
    if (idToken.isBlank()) throw AuthException(AuthError.INVALID_CREDENTIAL)
    val result =
        try {
          firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        } catch (exception: CancellationException) {
          // If our coroutine was cancelled, ensureActive() rethrows so the caller stops quietly.
          // Otherwise Firebase cancelled its own task, which is a failure the UI must report.
          currentCoroutineContext().ensureActive()
          throw AuthException(AuthError.UNKNOWN, exception)
        } catch (exception: Exception) {
          throw AuthException(exception.toAuthError(), exception)
        }
    return result.user?.toUserAccount() ?: throw AuthException(AuthError.UNKNOWN)
  }

  /** Ends the Firebase session first, then clears the account preference in Credential Manager. */
  override suspend fun signOut() {
    firebaseAuth.signOut()
    try {
      clearCredentialState()
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      throw AuthException(AuthError.CREDENTIAL_STATE_CLEAR_FAILED, exception)
    }
  }

  private fun FirebaseUser.toUserAccount() =
      UserAccount(
          uid = uid,
          email = email,
          displayName = displayName,
          photoUrl = photoUrl?.toString(),
      )

  private fun Exception.toAuthError(): AuthError =
      when (this) {
        is FirebaseNetworkException -> AuthError.NETWORK
        is FirebaseAuthInvalidCredentialsException -> AuthError.INVALID_CREDENTIAL
        is FirebaseAuthInvalidUserException ->
            if (errorCode == "ERROR_USER_DISABLED") AuthError.USER_DISABLED
            else AuthError.INVALID_CREDENTIAL
        else -> AuthError.UNKNOWN
      }
}
