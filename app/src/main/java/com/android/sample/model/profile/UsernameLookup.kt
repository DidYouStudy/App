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
