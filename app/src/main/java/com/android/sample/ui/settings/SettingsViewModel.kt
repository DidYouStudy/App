// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Holds UI state for the Settings screen. */
data class SettingsUiState(
    val username: String = "",
    val profilePictureUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
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
                  ?: DEFAULT_USERNAME

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
        _uiState.update {
          it.copy(
              isLoading = false,
              error = exception.localizedMessage ?: FAILED_TO_LOAD_PROFILE_ERROR,
          )
        }
      }
    }
  }

  /** Initiates sign-out via the authentication repository. */
  fun signOut() {
    viewModelScope.launch {
      try {
        authRepository.signOut()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(error = exception.localizedMessage ?: FAILED_TO_SIGN_OUT_ERROR) }
      }
    }
  }

  /** Clears any active error message. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  companion object {
    const val DEFAULT_USERNAME = "User"
    const val FAILED_TO_LOAD_PROFILE_ERROR = "Failed to load user profile"
    const val FAILED_TO_SIGN_OUT_ERROR = "Failed to sign out"
  }
}
