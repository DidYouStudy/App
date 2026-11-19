// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.profile

import kotlinx.coroutines.CancellationException

/** The outcome of looking up a user's username for display. */
sealed interface UsernameLookup {
  /** The profile exists and has a non-blank [username]. */
  data class Found(val username: String) : UsernameLookup

  /** No profile, or a blank username: shown as an unknown user. */
  data object Missing : UsernameLookup

  /** The lookup failed (a [Result.failure] or a thrown exception): shown as unavailable. */
  data object Unavailable : UsernameLookup
}

/**
 * Looks up the username of [userId] and tells a missing name apart from a failed lookup, so one bad
 * profile never fails a whole screen. Cancellation is propagated.
 */
suspend fun ProfileRepository.lookupUsername(userId: String): UsernameLookup =
    try {
      getProfile(userId)
          .fold(
              onSuccess = { profile ->
                val username = profile?.username?.takeIf { it.isNotBlank() }
                if (username == null) UsernameLookup.Missing else UsernameLookup.Found(username)
              },
              onFailure = { UsernameLookup.Unavailable },
          )
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      UsernameLookup.Unavailable
    }
