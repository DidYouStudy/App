// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Github Copilot
package com.android.sample.ui.auth

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.sample.model.auth.AuthError
import com.android.sample.model.auth.AuthException
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.ProfileResolutionCoordinator
import com.android.sample.model.profile.UserProfile
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

/** State for the profile lookup that follows an explicit successful sign-in. */
data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = false,
    val error: ProfileError? = null,
)

/**
 * Errors the user has nothing to act on, so the screen never shows them:
 * - [AuthError.CANCELLED]: closing the account picker is the user's choice, not a failure.
 * - [AuthError.CREDENTIAL_STATE_CLEAR_FAILED]: Firebase has already signed the user out; only
 *   Credential Manager's remembered account was not cleared, and the next sign-in still shows the
 *   picker.
 */
private val silentErrors = setOf(AuthError.CANCELLED, AuthError.CREDENTIAL_STATE_CLEAR_FAILED)

/**
 * Manages authentication state through the repository contract, without Firebase or Activity APIs.
 */
class AuthViewModel(
    private val repository: AuthRepository,
    private val profileResolutionCoordinator: ProfileResolutionCoordinator,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow(AuthUiState())
  private val mutableProfileUiState = MutableStateFlow(ProfileUiState())
  private var profileResolutionJob: kotlinx.coroutines.Job? = null
  private var profileResolutionToken: Any = Any()
  private var profileResolutionUserId: String? = null
  private var pendingProfileUser: UserAccount? = null

  /** Read-only state for lifecycle-aware collection by the authentication screen. */
  val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

  /** Read-only state for profile resolution after an explicit sign-in. */
  val profileUiState: StateFlow<ProfileUiState> = mutableProfileUiState.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        repository.currentUser.collect { user ->
          val previousUser = mutableUiState.value.user
          var pendingUserToResolve: UserAccount? = null
          when {
            user == null -> {
              if (profileResolutionUserId != null) invalidateProfileResolution()
            }
            pendingProfileUser?.uid == user.uid -> {
              pendingUserToResolve = pendingProfileUser
              pendingProfileUser = null
            }
            previousUser?.uid != user.uid && user.uid != profileResolutionUserId -> {
              invalidateProfileResolution()
            }
          }
          mutableUiState.update { it.copy(user = user, isInitializing = false) }
          pendingUserToResolve?.let { startProfileResolution(it) }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        invalidateProfileResolution()
        // The session flow broke: end startup so the screen is not stuck, keep the last known
        // user, and report it rather than crashing the app.
        mutableUiState.update { it.copy(isInitializing = false, error = AuthError.UNKNOWN) }
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
    if (mutableUiState.value.isLoading) return
    invalidateProfileResolution()
    runOperation { repository.signInWithGoogle(getIdToken()) }
  }

  /**
   * Starts sign-out and provider cleanup from a main-thread UI action. The session flow reports
   * sign-out even if cleanup fails; operation completion only controls loading and error state.
   */
  @MainThread
  fun signOut() {
    if (mutableUiState.value.isLoading) return
    invalidateProfileResolution()
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
  private fun runOperation(operation: suspend () -> Any?) {
    // Ignore another sign-in/sign-out tap while the current request is still running.
    if (mutableUiState.value.isLoading) return
    mutableUiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        val signedInUser = operation()
        if (signedInUser is UserAccount) {
          when (mutableUiState.value.user?.uid) {
            null -> pendingProfileUser = signedInUser
            signedInUser.uid -> startProfileResolution(signedInUser)
            else -> invalidateProfileResolution()
          }
        }
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
        if (error !in silentErrors) mutableUiState.update { it.copy(error = error) }
      } finally {
        mutableUiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Starts the profile lookup/create operation for the currently authenticated user.
   *
   * A new identity token marks this attempt as the current one, and any previous job is cancelled.
   * The token and authenticated UID checks prevent an older completion from changing newer profile
   * state. Successful resolutions publish the contained profile; known failures publish their
   * stable [ProfileError]; operation-level cancellation publishes [ProfileError.CANCELLED] only
   * when this attempt is still current.
   *
   * @param user authenticated account whose profile should be resolved.
   * @throws CancellationException only when this coroutine is cancelled by its parent or replaced
   *   by a newer resolution attempt.
   */
  private fun startProfileResolution(user: UserAccount) {
    profileResolutionJob?.cancel()
    val token = Any()
    profileResolutionToken = token
    profileResolutionUserId = user.uid
    mutableProfileUiState.update { ProfileUiState(isLoading = true) }
    profileResolutionJob = viewModelScope.launch {
      try {
        val result = profileResolutionCoordinator.resolve(user)
        // TODO-use the result to know whether the profile was just created, then do the navigation
        if (profileResolutionToken !== token || mutableUiState.value.user?.uid != user.uid) {
          if (profileResolutionToken === token) {
            mutableProfileUiState.update { ProfileUiState() }
          }
          return@launch
        }
        mutableProfileUiState.update {
          result.fold(
              onSuccess = { ProfileUiState(profile = it.profile) },
              onFailure = {
                ProfileUiState(error = (it as? ProfileException)?.error ?: ProfileError.UNKNOWN)
              },
          )
        }
      } catch (exception: CancellationException) {
        currentCoroutineContext().ensureActive()
        if (profileResolutionToken === token) {
          mutableProfileUiState.update {
            ProfileUiState(error = ProfileError.CANCELLED, isLoading = false)
          }
        }
      } catch (exception: ProfileException) {
        if (profileResolutionToken === token) {
          mutableProfileUiState.update {
            ProfileUiState(error = exception.error, isLoading = false)
          }
        }
      } catch (exception: Exception) {
        if (profileResolutionToken === token) {
          mutableProfileUiState.update {
            ProfileUiState(error = ProfileError.UNKNOWN, isLoading = false)
          }
        }
      }
    }
  }

  /**
   * Cancels the active profile resolution and resets all profile-resolution state.
   *
   * Replacing the token invalidates callbacks from the cancelled job before the job is cancelled.
   * This ordering ensures an older coroutine cannot clear or overwrite state belonging to a newer
   * attempt. Pending sign-in users are discarded because their session is no longer confirmed.
   */
  private fun invalidateProfileResolution() {
    profileResolutionToken = Any()
    profileResolutionUserId = null
    pendingProfileUser = null
    profileResolutionJob?.cancel()
    profileResolutionJob = null
    mutableProfileUiState.update { ProfileUiState() }
  }
}
