// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthError
import com.android.sample.model.auth.AuthException
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.ProfileRepositoryProvider
import com.android.sample.model.profile.ProfileResolutionCoordinator
import com.android.sample.model.profile.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
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
  private val mutableAuthUiState = MutableStateFlow(AuthUiState())
  private val mutableProfileUiState = MutableStateFlow(ProfileUiState())
  private var activeProfileResolutionJob: Job? = null
  private var activeProfileResolutionUserId: String? = null
  private var pendingProfileUser: UserAccount? = null

  /** Read-only state for lifecycle-aware collection by the authentication screen. */
  val authUiState: StateFlow<AuthUiState> = mutableAuthUiState.asStateFlow()

  /** Read-only state for profile resolution after an explicit sign-in. */
  val profileUiState: StateFlow<ProfileUiState> = mutableProfileUiState.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        repository.currentUser.collect { user ->
          val previousUser = mutableAuthUiState.value.user
          var pendingUserToResolve: UserAccount? = null
          when {
            // Case 1: A null session invalidates an active lookup; repeated null emissions need no
            // action.
            user == null -> {
              if (activeProfileResolutionJob != null) invalidateProfileResolution()
            }
            // Case 2: Sign-in can finish before currentUser emits; defer lookup until it emits this
            // UID.
            pendingProfileUser?.uid == user.uid -> {
              pendingUserToResolve = pendingProfileUser
              pendingProfileUser = null
            }
            // Case 3: A different UID invalidates old work. Ignore the current/resolving UID;
            previousUser?.uid != user.uid && user.uid != activeProfileResolutionUserId -> {
              invalidateProfileResolution()
            }
          }
          mutableAuthUiState.update { it.copy(user = user, isInitializing = false) }
          pendingUserToResolve?.let { startProfileResolution(it) }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        invalidateProfileResolution()
        // The session flow broke: end startup so the screen is not stuck, keep the last known
        // user, and report it rather than crashing the app.
        mutableAuthUiState.update { it.copy(isInitializing = false, error = AuthError.UNKNOWN) }
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
    runOperation(
        operation = { repository.signInWithGoogle(getIdToken()) },
        onFailure = ::invalidateProfileResolution,
    )
  }

  /**
   * Starts sign-out and provider cleanup from a main-thread UI action. The session flow reports
   * sign-out even if cleanup fails; operation completion only controls loading and error state.
   */
  @MainThread
  fun signOut() {
    if (mutableAuthUiState.value.isLoading) return
    invalidateProfileResolution()
    runOperation(operation = { repository.signOut() })
  }

  /** Dismisses an error after the screen has displayed it, without changing the current session. */
  @MainThread
  fun clearError() {
    mutableAuthUiState.update { it.copy(error = null) }
  }

  /**
   * Runs an authentication action with the screen's shared request handling: ignore another tap
   * while busy, show loading, clear the old error, report failures, and always stop loading. The
   * action is suspendable, so it runs in viewModelScope rather than blocking the UI thread.
   */
  private fun runOperation(
      operation: suspend () -> Any?,
      onFailure: () -> Unit = {},
  ) {
    // Ignore another sign-in/sign-out tap while the current request is still running.
    if (mutableAuthUiState.value.isLoading) return
    mutableAuthUiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        val signedInUser = operation()
        if (signedInUser is UserAccount) {
          // The session flow may already have started this user's lookup; avoid starting it twice.
          if (
              activeProfileResolutionUserId == signedInUser.uid &&
                  mutableProfileUiState.value.isLoading
          ) {
            return@launch
          }
          when (mutableAuthUiState.value.user?.uid) {
            null -> {
              // Defer lookup until currentUser confirms the newly signed-in session.
              invalidateProfileResolution()
              pendingProfileUser = signedInUser
            }
            signedInUser.uid -> {
              // The session already matches, so resolve this user's profile now.
              invalidateProfileResolution()
              startProfileResolution(signedInUser)
            }
            // A different session arrived while sign-in was running; discard this stale result.
            else -> return@launch
          }
        }
      } catch (exception: CancellationException) {
        // A CancellationException can mean either that this coroutine was cancelled
        // (e.g. the ViewModel was cleared) or that the operation itself was cancelled
        // while the coroutine is still active (e.g. by an SDK task).
        // ensureActive() rethrows only in the first case; otherwise, report the
        // operation-level cancellation as an error.
        currentCoroutineContext().ensureActive()
        onFailure()
        mutableAuthUiState.update { it.copy(error = AuthError.UNKNOWN) }
      } catch (exception: Exception) {
        val error = (exception as? AuthException)?.error ?: AuthError.UNKNOWN
        if (error !in silentErrors) mutableAuthUiState.update { it.copy(error = error) }
      } finally {
        mutableAuthUiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Starts the profile lookup/create operation for the currently authenticated user.
   *
   * A new job becomes current before it starts, and any previous job is cancelled. Comparing the
   * job identity prevents an older completion from changing newer profile state. Successful
   * resolutions publish the contained profile; known failures publish their stable [ProfileError];
   * operation-level cancellation publishes [ProfileError.CANCELLED] only while this job remains
   * current.
   *
   * @param user authenticated account whose profile should be resolved.
   * @throws CancellationException only when this coroutine is cancelled by its parent or replaced
   *   by a newer resolution attempt.
   */
  private fun startProfileResolution(user: UserAccount) {
    activeProfileResolutionJob?.cancel()
    mutableProfileUiState.update { ProfileUiState(isLoading = true) }
    lateinit var resolutionJob: Job
    val job =
        // LAZY lets us record this job as current before its completion can update the UI.
        viewModelScope.launch(start = CoroutineStart.LAZY) {
          try {
            // The coordinator looks up the profile and creates it only when no profile exists.
            val result = profileResolutionCoordinator.resolve(user)
            // TODO-use the result to know whether the profile was just created, then do the
            // navigation
            if (
                activeProfileResolutionJob !== resolutionJob ||
                    mutableAuthUiState.value.user?.uid != user.uid
            ) {
              // Ignore stale results; if this job is still active, clear its loading state and
              // data.
              if (activeProfileResolutionJob === resolutionJob) {
                mutableProfileUiState.update { ProfileUiState() }
              }
              return@launch
            }
            // Publish either the resolved profile or its typed failure for the current job only.
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
            if (activeProfileResolutionJob === resolutionJob) {
              mutableProfileUiState.update {
                ProfileUiState(error = ProfileError.CANCELLED, isLoading = false)
              }
            }
          } catch (exception: ProfileException) {
            if (activeProfileResolutionJob === resolutionJob) {
              mutableProfileUiState.update {
                ProfileUiState(error = exception.error, isLoading = false)
              }
            }
          } catch (exception: Exception) {
            if (activeProfileResolutionJob === resolutionJob) {
              mutableProfileUiState.update {
                ProfileUiState(error = ProfileError.UNKNOWN, isLoading = false)
              }
            }
          }
        }
    resolutionJob = job
    activeProfileResolutionJob = job
    activeProfileResolutionUserId = user.uid
    job.start()
  }

  /**
   * Cancels the active profile resolution and resets all profile-resolution state.
   *
   * Clearing the active job invalidates callbacks from it before cancellation. This ensures an
   * older coroutine cannot clear or overwrite state belonging to a newer attempt. Pending sign-in
   * users are discarded because their session is no longer confirmed.
   */
  private fun invalidateProfileResolution() {
    val job = activeProfileResolutionJob
    activeProfileResolutionJob = null
    activeProfileResolutionUserId = null
    pendingProfileUser = null
    job?.cancel()
    mutableProfileUiState.update { ProfileUiState() }
  }
}

/** Supplies an injected repository when Android creates the authentication ViewModel. */
class AuthViewModelFactory(
    private val repository: AuthRepository,
    private val profileResolutionCoordinator: ProfileResolutionCoordinator,
) : ViewModelProvider.Factory {
  /** Creates an AuthViewModel and rejects unrelated ViewModel types rather than casting blindly. */
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == AuthViewModel::class.java) { "Unsupported ViewModel: ${modelClass.name}" }
    @Suppress("UNCHECKED_CAST")
    return AuthViewModel(repository, profileResolutionCoordinator) as T
  }
}

/**
 * Returns the [AuthViewModel] of the current ViewModel owner (Activity or navigation entry), backed
 * by the app's shared repository. Screens that need authentication can use this as their default.
 */
@Composable
fun authViewModel(
    profileResolutionCoordinator: ProfileResolutionCoordinator =
        ProfileResolutionCoordinator(ProfileRepositoryProvider.getRepository())
): AuthViewModel =
    viewModel(
        factory =
            AuthViewModelFactory(
                AuthRepositoryProvider.getRepository(LocalContext.current),
                profileResolutionCoordinator,
            )
    )
