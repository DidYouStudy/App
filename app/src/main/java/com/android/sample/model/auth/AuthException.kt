// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

/** Stable failure categories that a screen can translate into its own localized messages. */
enum class AuthError {
  INVALID_CREDENTIAL,
  NETWORK,
  USER_DISABLED,
  CREDENTIAL_STATE_CLEAR_FAILED,
  UNKNOWN,
}

/**
 * An authentication failure. The SDK cause is retained for diagnostics, not for display to users.
 */
class AuthException(val error: AuthError, cause: Throwable? = null) :
    Exception("Authentication failed: $error", cause)
