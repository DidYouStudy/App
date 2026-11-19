// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import kotlinx.coroutines.CancellationException

/**
 * Returns the username to show for [userId], or null when it cannot be shown.
 *
 * Null covers a missing profile, a blank username, and a failed lookup (a [Result.failure] or a
 * thrown exception), so one bad profile never fails a whole screen. Cancellation is propagated.
 * Shared by every ViewModel that shows other users by name.
 */
suspend fun ProfileRepository.findUsername(userId: String): String? =
    try {
      getProfile(userId).getOrNull()?.username?.takeIf { it.isNotBlank() }
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      null
    }

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
suspend fun ProfileRepository.lookupUsername(userId: String): UsernameLookup = TODO("stub")
