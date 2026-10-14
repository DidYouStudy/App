// Co-authored-by: GitHub Copilot
package com.android.sample.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AppUiState {
  data object Loading : AppUiState

  data object SignIn : AppUiState

  data object LogOut : AppUiState

  data object FirstTimeSignIn : AppUiState

  data object Dashboard : AppUiState

  data class Error(val message: String) : AppUiState
}

/** Owns app-wide authentication state and the first authenticated destination. */
class AppViewModel(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
  private val mutableUiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
  val uiState: StateFlow<AppUiState> = mutableUiState.asStateFlow()
  private var hadSignedInUser = false

  init {
    viewModelScope.launch {
      authRepository.currentUser.collect { user ->
        if (user == null) {
          mutableUiState.value = if (hadSignedInUser) AppUiState.LogOut else AppUiState.SignIn
        } else {
          hadSignedInUser = true
          loadDestination(user.uid)
        }
      }
    }
  }

  /** Moves to the dashboard after PreferencesViewModel confirms a successful save. */
  fun onPreferencesSaved() {
    mutableUiState.value = AppUiState.Dashboard
  }

  /** Re-evaluates the destination after SignInRoute reports a successful sign-in. */
  fun onSignedIn() {
    mutableUiState.value = AppUiState.Loading
    viewModelScope.launch {
      val user = authRepository.currentUser.first { it != null } ?: return@launch
      loadDestination(user.uid)
    }
  }

  /** Allows only destinations valid for the current app authentication state. */
  fun canNavigateTo(screen: Screen): Boolean =
      when (val state = mutableUiState.value) {
        AppUiState.Loading,
        is AppUiState.Error -> false
        AppUiState.SignIn,
        AppUiState.LogOut -> screen == Screen.Auth
        AppUiState.FirstTimeSignIn -> screen == Screen.Preferences
        AppUiState.Dashboard -> screen != Screen.Auth && screen != Screen.Preferences
      }

  private suspend fun loadDestination(userId: String) {
    preferencesRepository
        .getPreferences(userId)
        .onSuccess { preferences ->
          mutableUiState.value =
              if (preferences == null) AppUiState.FirstTimeSignIn else AppUiState.Dashboard
        }
        .onFailure { exception ->
          mutableUiState.value =
              AppUiState.Error(exception.localizedMessage ?: "Failed to load preferences")
        }
  }
}

/** Creates [AppViewModel] instances with repository contracts. */
class AppViewModelFactory(
    private val authRepository: AuthRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == AppViewModel::class.java) { "Unsupported ViewModel: ${modelClass.name}" }
    @Suppress("UNCHECKED_CAST")
    return AppViewModel(authRepository, preferencesRepository) as T
  }
}

/** Returns the application ViewModel backed by the application's repositories. */
@Composable
fun appViewModel(): AppViewModel =
    viewModel(
        factory =
            AppViewModelFactory(
                AuthRepositoryProvider.getRepository(LocalContext.current),
                PreferencesRepositoryProvider.getRepository(),
            )
    )
