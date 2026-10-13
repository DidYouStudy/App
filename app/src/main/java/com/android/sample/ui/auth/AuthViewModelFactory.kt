// Co-authored-by: Codex AI Agent
// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.profile.ProfileResolutionCoordinator

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
