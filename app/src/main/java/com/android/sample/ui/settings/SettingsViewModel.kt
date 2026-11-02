// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
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
 * @property error Failure to report to the user, or null when there is none.
 */
data class SettingsUiState(
    val username: String? = null,
    val profilePictureUrl: String? = null,
    val isLoading: Boolean = false,
    val error: SettingsError? = null,
)

/**
 * Manages Settings screen state by observing the authenticated user and fetching their profile.
 *
 * Exposes profile information (resolving custom Firestore profile values with Google Auth fallback
 * defaults) and actions like signing out.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
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
        _uiState.update { it.copy(error = SettingsError.SIGN_OUT_FAILED) }
      }
    }
  }

  /** Clears any active error message. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }
}

/** Supplies an injected repository when Android creates the settings ViewModel. */
class SettingsViewModelFactory(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModelProvider.Factory {
  /**
   * Creates a SettingsViewModel and rejects unrelated ViewModel types rather than casting blindly.
   */
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == SettingsViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return SettingsViewModel(authRepository, profileRepository) as T
  }
}

/**
 * Returns the [SettingsViewModel] of the current ViewModel owner (Activity or navigation entry),
 * backed by the app's shared repository. The SettingsScreen uses this as their default.
 */
@Composable
fun settingsViewModel(): SettingsViewModel =
    viewModel(
        factory =
            SettingsViewModelFactory(
                authRepository = AuthRepositoryProvider.getRepository(LocalContext.current),
                profileRepository = ProfileRepositoryProvider.getRepository(),
            )
    )
