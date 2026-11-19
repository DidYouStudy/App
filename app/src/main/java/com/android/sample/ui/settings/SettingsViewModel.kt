// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.settings

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.profile.AndroidImageCompressor
import com.android.sample.model.profile.ImageCompressor
import com.android.sample.model.profile.ProfilePictureRepository
import com.android.sample.model.profile.ProfilePictureRepositoryProvider
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 */
data class SettingsUiState(
    val username: String? = null,
    val profilePictureUrl: String? = null,
    val isLoading: Boolean = false,
    val isUploadingPicture: Boolean = false,
    val error: SettingsError? = null,
)

/**
 * Manages Settings screen state by observing the authenticated user and fetching their profile.
 *
 * Exposes profile information (resolving custom Firestore profile values with Google Auth fallback
 * defaults) and actions like signing out and changing the profile picture.
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
) : ViewModel() {

  private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        authRepository.currentUser.collect { userAccount ->
          // safely prevent null pointer exception and stops the loading state
          if (userAccount == null) {
            _uiState.value = SettingsUiState(isLoading = false)
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
   * and the previous URL is kept.
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
      uploadPicture(userId, uri)
          .onSuccess { url ->
            _uiState.update {
              it.copy(profilePictureUrl = url, isUploadingPicture = false, error = null)
            }
          }
          .onFailure { exception ->
            // Users see only the translated message; the cause goes to the log for debugging.
            Log.w(TAG, "Failed to change the profile picture", exception)
            _uiState.update {
              it.copy(isUploadingPicture = false, error = SettingsError.UPLOAD_PICTURE_FAILED)
            }
          }
    }
  }

  /**
   * Compresses the image, uploads it and saves its URL in the profile, stopping at the first
   * failure.
   *
   * The picture the profile currently shows ([SettingsUiState.profilePictureUrl]) is passed to the
   * upload so that it is not overwritten: a new image goes to a different file, and the current URL
   * keeps working until the new one is saved. If the save fails, the profile therefore still points
   * to a picture that loads, and the user can simply pick again.
   *
   * @param userId authentication ID of the user whose picture is replaced.
   * @param uri location of the picked image.
   * @return the URL the new picture can be loaded from, or the failure of the step that failed.
   */
  private suspend fun uploadPicture(userId: String, uri: String): Result<String> {
    val jpegBytes =
        imageCompressor.compress(uri).getOrElse {
          return Result.failure(it)
        }
    val url =
        profilePictureRepository
            .uploadProfilePicture(userId, jpegBytes, _uiState.value.profilePictureUrl)
            .getOrElse {
              return Result.failure(it)
            }
    return profileRepository.updateProfilePictureUrl(userId, url).map { url }
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
  return viewModel(
      factory =
          SettingsViewModelFactory(
              authRepository = AuthRepositoryProvider.getRepository(context),
              profileRepository = ProfileRepositoryProvider.getRepository(),
              profilePictureRepository = ProfilePictureRepositoryProvider.getRepository(context),
              imageCompressor = AndroidImageCompressor(context),
          )
  )
}
