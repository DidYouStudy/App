// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

/** A signed-in user's identity, without exposing Firebase types or authentication tokens. */
data class UserAccount(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
)
