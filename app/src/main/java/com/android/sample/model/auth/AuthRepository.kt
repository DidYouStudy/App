// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

import kotlinx.coroutines.flow.Flow

/** Authentication contract consumed by ViewModels; implementations own the authentication SDK. */
interface AuthRepository {
  /**
   * The signed-in account, or null when nobody is signed in. This is the single source of truth for
   * "who is signed in": the ViewModel observes it instead of keeping its own copy, and uses it for
   * example to choose between the login screen and the rest of the app.
   *
   * What it emits:
   * - As soon as collection starts: the session restored from the device, so a user who signed in
   *   earlier is still signed in after restarting the app (null if nobody is).
   * - Afterwards: a new value on every sign-in or sign-out, including ones the screen did not
   *   start, for example when Firebase ends the session because the account was deleted or
   *   disabled.
   *
   * Consecutive identical values are skipped. Each collector gets its own listener, which is
   * removed when collection stops, so nothing leaks when the ViewModel is cleared.
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
