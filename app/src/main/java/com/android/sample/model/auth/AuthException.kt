// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

/**
 * Why an authentication action failed. Screens map each value to their own localized message; the
 * original SDK exception stays in [AuthException.cause] for logs and is never shown to users.
 *
 * Failures come from these operations:
 * - Sign-in: Firebase exchanges a Google ID token for a Firebase session
 *   ([AuthRepository.signInWithGoogle]).
 * - Sign-out: ends that session, then clears Credential Manager's state ([AuthRepository.signOut]).
 *
 * Coroutine cancellation (the user left the screen) is not an error and never becomes an
 * [AuthError]: it propagates as a `CancellationException` and nothing is shown.
 */
enum class AuthError {
  /**
   * Firebase rejected the Google ID token.
   *
   * Comes from: `FirebaseAuthInvalidCredentialsException` (the token is malformed, expired, or was
   * issued for another Firebase project, typically because of a wrong Web client ID),
   * `FirebaseAuthInvalidUserException` other than a disabled account (the account was deleted), or
   * a blank token passed to the repository.
   *
   * Expect: rare for real users; signing in again with a fresh token usually works. If it happens
   * every time, check the Web client ID and `google-services.json`.
   */
  INVALID_CREDENTIAL,

  /**
   * Firebase could not be reached: no connection, or the request timed out
   * (`FirebaseNetworkException`).
   *
   * Expect: common on phones. Ask the user to check their connection and try again.
   */
  NETWORK,

  /**
   * The account was disabled in the Firebase console (`FirebaseAuthInvalidUserException` with error
   * code `ERROR_USER_DISABLED`).
   *
   * Expect: retrying will not help. Tell the user that this account cannot sign in.
   */
  USER_DISABLED,

  /**
   * Sign-out ended the Firebase session, but clearing Credential Manager's state then failed.
   *
   * Expect: the user is signed out anyway ([AuthRepository.currentUser] emits null). Credential
   * Manager may only remember the previously chosen account. A mild warning, or nothing, is enough.
   */
  CREDENTIAL_STATE_CLEAR_FAILED,

  /**
   * Anything not covered above. Comes from: Firebase cancelling its own sign-in task, a successful
   * sign-in that returns no user, `FirebaseTooManyRequestsException` (rate limited),
   * `FirebaseAuthUserCollisionException` (the email already uses another sign-in method), a generic
   * `FirebaseAuthException` such as `ERROR_OPERATION_NOT_ALLOWED` (Google sign-in is not enabled in
   * the Firebase console), or any unexpected exception.
   *
   * Expect: show a generic "something went wrong, try again" message and log the cause. Repeated
   * UNKNOWN errors during development usually mean a configuration problem.
   */
  UNKNOWN,
}

/**
 * An authentication failure, described by [error]. The SDK exception that caused it (if any) is
 * kept as [cause] for diagnostics, not for display to users.
 */
class AuthException(val error: AuthError, cause: Throwable? = null) :
    Exception("Authentication failed: $error", cause)
