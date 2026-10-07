// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.auth

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.sample.model.auth.AuthError
import com.android.sample.model.auth.AuthException
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen state. Session identity and request progress are separate: a session notification must not
 * erase a pending request or its error. [isInitializing] distinguishes startup from signed out.
 */
data class AuthUiState(
    val user: UserAccount? = null,
    val isInitializing: Boolean = true,
    val isLoading: Boolean = false,
    val error: AuthError? = null,
)

/**
 * Manages authentication state through the repository contract, without Firebase or Activity APIs.
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
  private val mutableUiState = MutableStateFlow(AuthUiState())

  /** Read-only state for lifecycle-aware collection by the authentication screen. */
  val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

  init {
    viewModelScope.launch {
      repository.currentUser.collect { user ->
        mutableUiState.update { it.copy(user = user, isInitializing = false) }
      }
    }
  }

  /**
   * Starts Google sign-in from a main-thread UI action. [getIdToken] opens the account picker
   * (normally GoogleSignInHelper.getIdToken), so picker failures share this screen's loading and
   * error state. Repeated actions are ignored while busy; session identity is updated only by
   * [AuthRepository.currentUser].
   */
  @MainThread
  fun signInWithGoogle(getIdToken: suspend () -> String) {
    runOperation { repository.signInWithGoogle(getIdToken()) }
  }

  /**
   * Starts sign-out and provider cleanup from a main-thread UI action. The session flow reports
   * sign-out even if cleanup fails; operation completion only controls loading and error state.
   */
  @MainThread
  fun signOut() {
    runOperation { repository.signOut() }
  }

  /** Dismisses an error after the screen has displayed it, without changing the current session. */
  @MainThread
  fun clearError() {
    mutableUiState.update { it.copy(error = null) }
  }

  /**
   * Runs an authentication action with the screen's shared request handling: ignore another tap
   * while busy, show loading, clear the old error, report failures, and always stop loading. The
   * action is suspendable, so it runs in viewModelScope rather than blocking the UI thread.
   */
  private fun runOperation(operation: suspend () -> Unit) {
    // Ignore another sign-in/sign-out tap while the current request is still running.
    if (mutableUiState.value.isLoading) return
    mutableUiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        operation()
      } catch (exception: CancellationException) {
        // A CancellationException can mean either that this coroutine was cancelled
        // (e.g. the ViewModel was cleared) or that the operation itself was cancelled
        // while the coroutine is still active (e.g. by an SDK task).
        // ensureActive() rethrows only in the first case; otherwise, report the
        // operation-level cancellation as an error.
        currentCoroutineContext().ensureActive()
        mutableUiState.update { it.copy(error = AuthError.UNKNOWN) }
      } catch (exception: Exception) {
        val error = (exception as? AuthException)?.error ?: AuthError.UNKNOWN
        // Closing the account picker is the user's choice, not a failure to report.
        if (error != AuthError.CANCELLED) mutableUiState.update { it.copy(error = error) }
      } finally {
        mutableUiState.update { it.copy(isLoading = false) }
      }
    }
  }
}
