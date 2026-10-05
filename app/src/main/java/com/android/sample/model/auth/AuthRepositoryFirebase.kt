// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Firebase-backed sessions. Inject the Firebase instance and credential cleanup function so that
 * construction has no hidden SDK initialization and tests can replace the external services.
 */
class AuthRepositoryFirebase(
    private val firebaseAuth: FirebaseAuth,
    private val clearCredentialState: suspend () -> Unit,
) : AuthRepository {
  private val operationMutex = Mutex()

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

  /**
   * Exchanges a Google token for a Firebase session. If the caller cancels, later operations still
   * wait for the started Firebase task to finish, so an earlier sign-in cannot undo a later
   * sign-out.
   */
  override suspend fun signInWithGoogle(idToken: String): UserAccount {
    operationMutex.lock()
    var task: Task<AuthResult>? = null
    try {
      if (idToken.isBlank()) throw AuthException(AuthError.INVALID_CREDENTIAL)
      val credential = GoogleAuthProvider.getCredential(idToken, null)
      task = firebaseAuth.signInWithCredential(credential)
      val result = task.await()
      return result.user?.toUserAccount() ?: throw AuthException(AuthError.UNKNOWN)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: AuthException) {
      throw exception
    } catch (exception: Exception) {
      throw AuthException(exception.toAuthError(), exception)
    } finally {
      val startedTask = task
      if (startedTask != null && !startedTask.isComplete) {
        // Firebase cannot cancel the task. Transfer lock release to its completion callback so the
        // caller can cancel promptly without allowing another operation to overtake this sign-in.
        startedTask.addOnCompleteListener(Executor { it.run() }) { operationMutex.unlock() }
      } else {
        operationMutex.unlock()
      }
    }
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
