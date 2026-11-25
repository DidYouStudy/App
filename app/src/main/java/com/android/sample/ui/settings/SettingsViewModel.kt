// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.settings

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.connectivity.AndroidConnectivityObserver
import com.android.sample.model.connectivity.ConnectivityObserver
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import com.android.sample.model.profile.UsernamePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds UI state for the Settings screen.
 *
 * @property username Name to greet the user with, or null when none is known; the screen then shows
 *   the translated default name.
 * @property profilePictureUrl URL of the user's profile picture, if any.
 * @property isLoading Whether the profile is still loading.
 * @property error Failure to report to the user, or null when there is none.
 * @property usernameEdit State of the "Edit username" dialog, or null while it is closed.
 * @property isOffline Whether the device has no connection; the screen then shows the offline
 *   banner. Always false when the ViewModel has no connectivity observer.
 */
data class SettingsUiState(
    val username: String? = null,
    val profilePictureUrl: String? = null,
    val isLoading: Boolean = false,
    val error: SettingsError? = null,
    val usernameEdit: UsernameEditState? = null,
    val isOffline: Boolean = false,
)

/**
 * Manages Settings screen state by observing the authenticated user and fetching their profile.
 *
 * Exposes profile information (resolving custom Firestore profile values with Google Auth fallback
 * defaults) and actions like signing out. [connectivityObserver] drives [SettingsUiState.isOffline]
 * while [uiState] has a collector; with the default null nothing is observed and the screen never
 * shows the offline banner.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val connectivityObserver: ConnectivityObserver? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /** ID of the signed-in user, or null when nobody is signed in; target of username changes. */
  private var currentUserId: String? = null

  init {
    observeConnectivityWhileObserved()
    viewModelScope.launch {
      try {
        authRepository.currentUser.collect { userAccount ->
          // safely prevent null pointer exception and stops the loading state
          currentUserId = userAccount?.uid
          if (userAccount == null) {
            // Keep the connectivity indication: it does not depend on who is signed in.
            _uiState.value =
                SettingsUiState(isLoading = false, isOffline = _uiState.value.isOffline)
            return@collect
          }

          val profileResult = profileRepository.getProfile(userAccount.uid)
          val profile = profileResult.getOrNull()

          val username =
              profile?.username?.takeIf { it.isNotBlank() }
                  ?: userAccount.displayName?.takeIf { it.isNotBlank() }
                  ?: userAccount.email?.substringBefore("@")

          val photoUrl =
              profile?.profilePictureUrl?.takeIf { it.isNotBlank() } ?: userAccount.photoUrl

          _uiState.update {
            it.copy(
                username = username,
                profilePictureUrl = photoUrl,
                isLoading = false,
                error = null,
            )
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // Users see only the translated message; the cause goes to the log for debugging.
        Log.w(TAG, "Failed to load the user profile", exception)
        _uiState.update { it.copy(isLoading = false, error = SettingsError.LOAD_PROFILE_FAILED) }
      }
    }
  }

  /**
   * Tracks whether the device is offline, only while the UI state has an active observer.
   *
   * The observer emits the current connectivity as soon as collection starts, so returning to the
   * screen never shows an outdated banner. Nothing is collected when no [connectivityObserver] was
   * provided.
   */
  private fun observeConnectivityWhileObserved() {
    val observer = connectivityObserver ?: return
    viewModelScope.launch {
      _uiState.subscriptionCount
          .map { it > 0 }
          .distinctUntilChanged()
          .collectLatest { isObserved ->
            val connectivity = if (isObserved) observer.observeIsOnline() else emptyFlow()
            connectivity.collect { isOnline -> _uiState.update { it.copy(isOffline = !isOnline) } }
          }
    }
  }

  /** Initiates sign-out via the authentication repository and reports successful completion. */
  fun signOut(onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      try {
        authRepository.signOut()
        onSuccess()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        // Users see only the translated message; the cause goes to the log for debugging.
        Log.w(TAG, "Failed to sign out", exception)
        _uiState.update { it.copy(error = SettingsError.SIGN_OUT_FAILED) }
      }
    }
  }

  /** Opens the "Edit username" dialog pre-filled with the current username. */
  fun openUsernameEditor() {
    val state = _uiState.value
    if (currentUserId == null || state.isLoading) return
    _uiState.update { it.copy(usernameEdit = editState(state.username.orEmpty(), state.username)) }
  }

  /** Closes the "Edit username" dialog without saving, unless a save is in progress. */
  fun dismissUsernameEditor() {
    _uiState.update { state ->
      if (state.usernameEdit?.isSaving == true) state else state.copy(usernameEdit = null)
    }
  }

  /**
   * Updates the text typed in the dialog and re-validates it. Leading whitespace is dropped and the
   * text is capped at [UsernamePolicy.MAX_LENGTH], so spaces never use up the limit. Ignored while
   * a save is running.
   */
  fun onUsernameInputChange(input: String) {
    _uiState.update { state ->
      val edit = state.usernameEdit
      if (edit == null || edit.isSaving) state
      else
          state.copy(
              usernameEdit =
                  editState(input.trimStart().take(UsernamePolicy.MAX_LENGTH), state.username)
          )
    }
  }

  /**
   * Saves the typed username through [ProfileRepository.updateUsername].
   *
   * On success Settings shows the returned username and the dialog closes; on failure the dialog
   * stays open with the matching [UsernameError]. Does nothing when [UsernameEditState.canSave] is
   * false.
   */
  fun saveUsername() {
    val userId = currentUserId ?: return
    val edit = _uiState.value.usernameEdit ?: return
    if (!edit.canSave) return
    _uiState.update { it.copy(usernameEdit = edit.copy(isSaving = true, canSave = false)) }
    viewModelScope.launch {
      val result =
          try {
            profileRepository.updateUsername(userId, edit.input.trim())
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: Exception) {
            Result.failure(exception)
          }
      // The user may have signed out while the request was running; drop the stale result.
      if (currentUserId != userId) return@launch
      result
          .onSuccess { profile ->
            _uiState.update { it.copy(username = profile.username, usernameEdit = null) }
          }
          .onFailure { exception ->
            Log.w(TAG, "Failed to update the username", exception)
            val error =
                (exception as? ProfileException)?.error?.toUsernameError() ?: UsernameError.FAILURE
            _uiState.update {
              it.copy(
                  usernameEdit =
                      edit.copy(
                          isSaving = false,
                          error = error,
                          // A storage failure or a lost connection may be temporary, so the same
                          // name can be retried; the other errors need a different name.
                          canSave =
                              error == UsernameError.FAILURE || error == UsernameError.OFFLINE,
                      )
              )
            }
          }
    }
  }

  /** Builds the dialog state for [input]: local validation error and whether Save is enabled. */
  private fun editState(input: String, currentUsername: String?): UsernameEditState {
    val error = validateUsernameInput(input)
    val trimmed = input.trim()
    return UsernameEditState(
        input = input,
        error = error,
        canSave = error == null && trimmed.isNotEmpty() && trimmed != currentUsername,
    )
  }

  /** Clears any active error message. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  private companion object {
    const val TAG = "SettingsViewModel"
  }
}

/** Supplies an injected repository when Android creates the settings ViewModel. */
class SettingsViewModelFactory(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val connectivityObserver: ConnectivityObserver? = null,
) : ViewModelProvider.Factory {
  /**
   * Creates a SettingsViewModel and rejects unrelated ViewModel types rather than casting blindly.
   */
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == SettingsViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return SettingsViewModel(authRepository, profileRepository, connectivityObserver) as T
  }
}

/**
 * Returns the [SettingsViewModel] of the current ViewModel owner (Activity or navigation entry),
 * backed by the app's shared repository. The SettingsScreen uses this as their default.
 */
@Composable
fun settingsViewModel(): SettingsViewModel {
  val context = LocalContext.current
  val connectivityObserver = remember(context) { AndroidConnectivityObserver.create(context) }
  return viewModel(
      factory =
          SettingsViewModelFactory(
              authRepository = AuthRepositoryProvider.getRepository(context),
              profileRepository = ProfileRepositoryProvider.getRepository(),
              connectivityObserver = connectivityObserver,
          )
  )
}
