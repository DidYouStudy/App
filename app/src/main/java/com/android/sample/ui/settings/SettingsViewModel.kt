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
import com.android.sample.model.connectivity.runOnlineWrite
import com.android.sample.model.profile.AndroidImageCompressor
import com.android.sample.model.profile.ImageCompressor
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.ProfilePictureRepository
import com.android.sample.model.profile.ProfilePictureRepositoryProvider
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
 * @property isUploadingPicture Whether a newly picked profile picture is being prepared, uploaded
 *   and saved.
 * @property error Failure to report to the user, or null when there is none.
 * @property usernameEdit State of the "Edit username" dialog, or null while it is closed.
 * @property isOffline Whether the device has no connection; the screen then shows the offline
 *   banner. Always false when the ViewModel has no connectivity observer.
 */
data class SettingsUiState(
    val username: String? = null,
    val profilePictureUrl: String? = null,
    val isLoading: Boolean = false,
    val isUploadingPicture: Boolean = false,
    val error: SettingsError? = null,
    val usernameEdit: UsernameEditState? = null,
    val isOffline: Boolean = false,
)

/**
 * Manages Settings screen state by observing the authenticated user and fetching their profile.
 *
 * Exposes profile information (resolving custom Firestore profile values with Google Auth fallback
 * defaults) and actions like signing out and changing the profile picture. [connectivityObserver]
 * drives [SettingsUiState.isOffline] while [uiState] has a collector; with the default null nothing
 * is observed and the screen never shows the offline banner.
 *
 * @param authRepository source of the signed-in user.
 * @param profileRepository reads the profile and saves the new picture URL in it.
 * @param profilePictureRepository stores the picture and returns the URL it can be loaded from.
 * @param imageCompressor turns a picked image into a small JPEG before the upload.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val profilePictureRepository: ProfilePictureRepository,
    private val imageCompressor: ImageCompressor,
    private val connectivityObserver: ConnectivityObserver? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  /** ID of the signed-in user, or null when nobody is signed in; target of username changes. */
  private var currentUserId: String? = null

  /**
   * ID of the account whose profile the state shows, or null before the first one or at sign-out.
   */
  private var observedUserId: String? = null

  init {
    observeConnectivityWhileObserved()
    viewModelScope.launch {
      try {
        authRepository.currentUser.collect { userAccount ->
          // safely prevent null pointer exception and stops the loading state
          currentUserId = userAccount?.uid
          if (userAccount == null) {
            observedUserId = null
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

          // Another account replacing the shown one does not inherit its picture upload: that
          // upload's result is discarded when it arrives (see onPictureSelected), so the indicator
          // must not wait for it. The first account seen is not a replacement: a pick made before
          // the profile loaded is still its own.
          val previousUserId = observedUserId
          val accountReplaced = previousUserId != null && previousUserId != userAccount.uid
          observedUserId = userAccount.uid

          _uiState.update {
            it.copy(
                username = username,
                profilePictureUrl = photoUrl,
                isLoading = false,
                isUploadingPicture = it.isUploadingPicture && !accountReplaced,
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

  /**
   * Replaces the signed-in user's profile picture with the image at [uri].
   *
   * The image is compressed, uploaded, and its URL saved in the profile, in that order; the state's
   * [SettingsUiState.profilePictureUrl] changes only once all three steps have succeeded. Does
   * nothing when [uri] is null (the user closed the picker without choosing), or while a previous
   * picture is still uploading. Any failure is reported as [SettingsError.UPLOAD_PICTURE_FAILED]
   * and the previous URL is kept. The same happens when the device goes offline, or the change
   * takes longer than the time limit of [runOnlineWrite].
   *
   * If the signed-in account changes during the upload (sign-out, or another account signing in),
   * the result belongs to the previous user and is not written to the state, which by then shows
   * someone else. The picture is still saved in the previous user's profile.
   *
   * @param uri location of the picked image, or null when nothing was picked.
   */
  fun onPictureSelected(uri: String?) {
    if (uri == null || _uiState.value.isUploadingPicture) return
    val userId = authRepository.currentUserId
    if (userId == null) {
      _uiState.update { it.copy(error = SettingsError.UPLOAD_PICTURE_FAILED) }
      return
    }
    _uiState.update { it.copy(isUploadingPicture = true) }
    viewModelScope.launch {
      // Bounded, so an offline or stalled upload cannot keep the spinner running.
      val result = runOnlineWrite(connectivityObserver) { uploadPicture(userId, uri) }
      // Users see only the translated message; the cause goes to the log for debugging.
      result.exceptionOrNull()?.let { Log.w(TAG, "Failed to change the profile picture", it) }
      if (authRepository.currentUserId != userId) return@launch
      _uiState.update { state ->
        result.fold(
            onSuccess = { url ->
              state.copy(profilePictureUrl = url, isUploadingPicture = false, error = null)
            },
            onFailure = {
              state.copy(isUploadingPicture = false, error = SettingsError.UPLOAD_PICTURE_FAILED)
            },
        )
      }
    }
  }

  /**
   * Compresses the image, uploads it and saves its URL in the profile, stopping at the first
   * failure.
   *
   * The profile is read just before the upload, and its picture URL is passed on so that the
   * current picture is not overwritten: a new image goes to a different file, and the current URL
   * keeps working until the new one is saved. The URL on screen is not used, because it can be out
   * of date or not loaded yet. If the read fails, nothing is uploaded.
   *
   * @param userId authentication ID of the user whose picture is replaced.
   * @param uri location of the picked image.
   * @return the URL the new picture can be loaded from, or the failure of the step that failed,
   *   including an exception a step threw instead of returning it.
   * @throws CancellationException when the coroutine is cancelled, also by the time limit.
   */
  private suspend fun uploadPicture(userId: String, uri: String): Result<String> {
    return try {
      val jpegBytes =
          imageCompressor.compress(uri).getOrElse {
            return Result.failure(it)
          }
      // By userId, not from the screen, which may show another account by now.
      val profile =
          profileRepository.getProfile(userId).getOrElse {
            return Result.failure(it)
          }
      val currentUrl = profile?.profilePictureUrl
      val url =
          profilePictureRepository.uploadProfilePicture(userId, jpegBytes, currentUrl).getOrElse {
            return Result.failure(it)
          }
      profileRepository.updateProfilePictureUrl(userId, url).map { url }
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      // The steps should return their failures; one that throws must not crash the app.
      Result.failure(exception)
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
    private val profilePictureRepository: ProfilePictureRepository,
    private val imageCompressor: ImageCompressor,
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
    return SettingsViewModel(
        authRepository,
        profileRepository,
        profilePictureRepository,
        imageCompressor,
        connectivityObserver,
    )
        as T
  }
}

/**
 * Returns the [SettingsViewModel] of the current ViewModel owner (Activity or navigation entry),
 * backed by the app's shared repository. The SettingsScreen uses this as their default.
 */
@Composable
fun settingsViewModel(): SettingsViewModel {
  // The application context lives as long as the process, so the ViewModel never holds an Activity.
  val context = LocalContext.current.applicationContext
  val connectivityObserver = remember(context) { AndroidConnectivityObserver.create(context) }
  return viewModel(
      factory =
          SettingsViewModelFactory(
              authRepository = AuthRepositoryProvider.getRepository(context),
              profileRepository = ProfileRepositoryProvider.getRepository(),
              profilePictureRepository = ProfilePictureRepositoryProvider.getRepository(context),
              connectivityObserver = connectivityObserver,
              imageCompressor = AndroidImageCompressor(context),
          )
  )
}
