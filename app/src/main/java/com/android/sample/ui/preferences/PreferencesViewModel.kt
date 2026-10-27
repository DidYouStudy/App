// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.preferences

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PreferencesUiState {
  object Loading : PreferencesUiState

  data class Success(val preferences: UserPreferences) : PreferencesUiState

  data class Error(val message: String) : PreferencesUiState
}

class PreferencesViewModel(
    private val repository: PreferencesRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow<PreferencesUiState>(PreferencesUiState.Loading)
  val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow()

  private val _isSaving = MutableStateFlow(false)
  /** True while a save is in flight; the screen disables Save so it cannot be sent twice. */
  val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
  private var userId: String? = null

  init {
    viewModelScope.launch {
      authRepository.currentUser.collect { user ->
        userId = user?.uid
        if (user == null) {
          _uiState.value = PreferencesUiState.Error("No signed-in user")
        } else {
          loadPreferences()
        }
      }
    }
  }

  fun loadPreferences() {
    val currentUserId = userId ?: return
    viewModelScope.launch {
      _uiState.value = PreferencesUiState.Loading
      repository
          .getPreferences(currentUserId)
          .onSuccess { prefs ->
            _uiState.value =
                // Fallback to default preferences if none exist for the user
                PreferencesUiState.Success(prefs ?: UserPreferences(userId = currentUserId))
          }
          .onFailure { exception ->
            _uiState.value =
                PreferencesUiState.Error(exception.localizedMessage ?: "Failed to load preferences")
          }
    }
  }

  /**
   * Saves [updated] and calls [onSuccess] once it is persisted.
   *
   * Calls made while a previous save is still in flight are ignored, so rapid taps on Save write
   * once and navigate once.
   */
  fun updatePreferences(updated: UserPreferences, onSuccess: () -> Unit = {}) {
    if (_isSaving.value) return
    _isSaving.value = true
    viewModelScope.launch {
      try {
        repository
            .savePreferences(updated)
            .onSuccess {
              _uiState.value = PreferencesUiState.Success(updated)
              onSuccess()
            }
            .onFailure { exception ->
              _uiState.value =
                  PreferencesUiState.Error(
                      exception.localizedMessage ?: "Failed to save preferences"
                  )
            }
      } finally {
        // Re-enable Save even if the save was cancelled or threw.
        _isSaving.value = false
      }
    }
  }
}

/** Creates [PreferencesViewModel] instances with the application's repository contracts. */
class PreferencesViewModelFactory(
    private val repository: PreferencesRepository,
    private val authRepository: AuthRepository,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == PreferencesViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return PreferencesViewModel(repository, authRepository) as T
  }
}

/** Returns a preferences ViewModel backed by the application's repositories. */
@Composable
fun preferencesViewModel(): PreferencesViewModel =
    viewModel(
        factory =
            PreferencesViewModelFactory(
                PreferencesRepositoryProvider.getRepository(),
                AuthRepositoryProvider.getRepository(
                    androidx.compose.ui.platform.LocalContext.current
                ),
            ),
    )
