// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.sample.model.preferences.PreferencesRepository
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
    private val userId: String,
) : ViewModel() {

  private val _uiState = MutableStateFlow<PreferencesUiState>(PreferencesUiState.Loading)
  val uiState: StateFlow<PreferencesUiState> = _uiState.asStateFlow() // passed to View

  init {
    loadPreferences()
  }

  fun loadPreferences() {
    viewModelScope.launch {
      _uiState.value = PreferencesUiState.Loading
      repository
          .getPreferences(userId)
          .onSuccess { prefs ->
            _uiState.value =
                PreferencesUiState.Success(
                    prefs ?: UserPreferences(userId = userId) // Fallback to default values
                )
          }
          .onFailure { exception ->
            _uiState.value =
                PreferencesUiState.Error(exception.localizedMessage ?: "Failed to load preferences")
          }
    }
  }

  fun updatePreferences(updated: UserPreferences) {
    viewModelScope.launch {
      repository
          .savePreferences(updated)
          .onSuccess { _uiState.value = PreferencesUiState.Success(updated) }
          .onFailure { exception ->
            _uiState.value =
                PreferencesUiState.Error(exception.localizedMessage ?: "Failed to save preferences")
          }
    }
  }
}
