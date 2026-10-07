// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow

class TestAuthRepository : AuthRepository {
  override val currentUser = MutableStateFlow<UserAccount?>(null)
  val tokens = mutableListOf<String>()
  var signInAction: suspend (String) -> UserAccount = {
    UserAccount("test-user", "test@example.com", "Test User", null)
  }

  override suspend fun signInWithGoogle(idToken: String): UserAccount {
    tokens += idToken
    return signInAction(idToken)
  }

  override suspend fun signOut() {
    currentUser.value = null
  }
}
