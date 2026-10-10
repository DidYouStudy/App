// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider

/** Supplies an injected repository when Android creates the authentication ViewModel. */
class AuthViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
  /** Creates an AuthViewModel and rejects unrelated ViewModel types rather than casting blindly. */
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == AuthViewModel::class.java) { "Unsupported ViewModel: ${modelClass.name}" }
    @Suppress("UNCHECKED_CAST")
    return AuthViewModel(repository) as T
  }
}

/**
 * Returns the [AuthViewModel] of the current ViewModel owner (Activity or navigation entry), backed
 * by the app's shared repository. Screens that sign in or sign out use this as their default.
 */
@Composable
fun authViewModel(): AuthViewModel =
    viewModel(
        factory = AuthViewModelFactory(AuthRepositoryProvider.getRepository(LocalContext.current))
    )
