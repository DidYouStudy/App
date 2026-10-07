// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firebase-backed sessions. Inject the Firebase instance and credential cleanup function so that
 * construction has no hidden SDK initialization and tests can replace the external services.
 */
class AuthRepositoryFirebase(
    private val firebaseAuth: FirebaseAuth,
    private val clearCredentialState: suspend () -> Unit,
) : AuthRepository {
  // Runs sign-in and sign-out one at a time; see the AuthRepository documentation.
  private val operationMutex = Mutex()

  /**
   * Turns Firebase's callback-based AuthStateListener into a flow. Nothing runs until someone
   * collects; each collector registers its own listener.
   */
  override val currentUser: Flow<UserAccount?> = callbackFlow {
    // Firebase calls a newly registered listener immediately with the session restored from disk,
    // then again after every sign-in and sign-out.
    val listener = FirebaseAuth.AuthStateListener { auth ->
      trySend(auth.currentUser?.toUserAccount())
    }
    firebaseAuth.addAuthStateListener(listener)
    // Runs when collection stops (e.g. the ViewModel is cleared), so the listener never leaks.
    awaitClose { firebaseAuth.removeAuthStateListener(listener) }
  }
      // Only the latest session matters: a slow collector skips intermediate values.
      .buffer(Channel.CONFLATED)
      // Firebase may notify twice for the same account; emit only real changes.
      .distinctUntilChanged()

  /** Exchanges a Google ID token for a Firebase session. */
  override suspend fun signInWithGoogle(idToken: String): UserAccount {
    if (idToken.isBlank()) throw AuthException(AuthError.INVALID_CREDENTIAL)
    val result = operationMutex.withLock {
      // Why NonCancellable: once a sign-in request is sent, Firebase cannot stop it. If the caller
      // were cancelled here (e.g. the user leaves the screen) and we released the lock at once, a
      // sign-out could run before that request completes, and the late sign-in would then sign
      // the user back in. So we wait for Firebase's answer while still holding the lock, even if
      // the caller was cancelled.
      val outcome =
          withContext(NonCancellable) {
            runCatching {
              firebaseAuth
                  .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                  .await()
            }
          }
      // If the caller was cancelled meanwhile, stop quietly now that Firebase is done.
      currentCoroutineContext().ensureActive()
      // A CancellationException here means Firebase cancelled its own task: report it as UNKNOWN.
      outcome.getOrElse { exception ->
        val error = if (exception is Exception) exception.toAuthError() else AuthError.UNKNOWN
        throw AuthException(error, exception)
      }
    }
    return result.user?.toUserAccount() ?: throw AuthException(AuthError.UNKNOWN)
  }

  /** Ends the Firebase session first, then clears the account preference in Credential Manager. */
  override suspend fun signOut() = operationMutex.withLock {
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

  /** Maps Firebase sign-in failures to [AuthError]; see each value for what it means. */
  private fun Exception.toAuthError(): AuthError =
      when (this) {
        // Offline or timed out.
        is FirebaseNetworkException -> AuthError.NETWORK
        // Malformed or expired token, or one issued for another project.
        is FirebaseAuthInvalidCredentialsException -> AuthError.INVALID_CREDENTIAL
        // The account was disabled or deleted in the Firebase console.
        is FirebaseAuthInvalidUserException ->
            if (errorCode == "ERROR_USER_DISABLED") AuthError.USER_DISABLED
            else AuthError.INVALID_CREDENTIAL
        // Rate limiting, account collisions, console configuration errors, unexpected failures.
        else -> AuthError.UNKNOWN
      }
}
