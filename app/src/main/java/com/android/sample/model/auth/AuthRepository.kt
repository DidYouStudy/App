// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

import kotlinx.coroutines.flow.Flow

/** Authentication contract consumed by ViewModels; implementations own the authentication SDK. */
interface AuthRepository {
  /**
   * Emits the persisted session when collected, then subsequent sign-in/sign-out changes. A null
   * value means signed out. Collection must release its listener when cancelled.
   */
  val currentUser: Flow<UserAccount?>

  /**
   * Exchanges a Google ID token for an authenticated session. This token comes from Credential
   * Manager, not from Firebase. Never store or log it. The returned account describes this
   * operation; [currentUser] is the source of truth for the current session.
   *
   * @throws AuthException if authentication fails or the token is blank.
   * @throws kotlinx.coroutines.CancellationException if the caller's coroutine is cancelled.
   */
  suspend fun signInWithGoogle(idToken: String): UserAccount

  /**
   * Signs out locally and clears Credential Manager's session preference, allowing account
   * selection on the next sign-in. This does not delete the account or revoke Google access.
   *
   * @throws AuthException if credential cleanup fails. Firebase is already signed out in that case;
   *   [currentUser] remains the source of truth for the session.
   */
  suspend fun signOut()
}
