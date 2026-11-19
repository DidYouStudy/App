// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Reusable [ProfileRepository] fake with per-user configurable lookups.
 *
 * Every [getProfile] call is recorded in [getProfileUserIds]. A user resolves, in order, to a held
 * lookup ([hold]), a thrown exception ([throwFor]), a queued result ([enqueue]), or a missing
 * profile (`Result.success(null)`).
 */
internal class FakeProfileRepository : ProfileRepository {
  /** User IDs passed to [getProfile], in call order. */
  val getProfileUserIds = mutableListOf<String>()

  private val outcomes = mutableMapOf<String, MutableList<Result<UserProfile?>>>()
  private val heldLookups = mutableMapOf<String, CompletableDeferred<Result<UserProfile?>>>()
  private val thrown = mutableMapOf<String, Throwable>()

  /** Queues [result] as the next outcome of [getProfile] for [userId]. */
  fun enqueue(userId: String, result: Result<UserProfile?>) {
    outcomes.getOrPut(userId) { mutableListOf() }.add(result)
  }

  /** Gives [userId] a profile with [username]. */
  fun setUsername(userId: String, username: String) {
    enqueue(userId, Result.success(UserProfile(uid = userId, username = username)))
  }

  /** Makes the next lookup of [userId] suspend until [result] completes. */
  fun hold(userId: String, result: CompletableDeferred<Result<UserProfile?>>) {
    heldLookups[userId] = result
  }

  /** Makes every lookup of [userId] throw [exception]. */
  fun throwFor(userId: String, exception: Throwable) {
    thrown[userId] = exception
  }

  override suspend fun getProfile(userId: String): Result<UserProfile?> {
    getProfileUserIds += userId
    heldLookups.remove(userId)?.let { pending ->
      return withContext(NonCancellable) { pending.await() }
    }
    thrown[userId]?.let { throw it }
    val configured = outcomes[userId]
    return if (configured.isNullOrEmpty()) Result.success(null) else configured.removeAt(0)
  }

  override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> =
      Result.failure(UnsupportedOperationException("Not needed in this test"))

  override suspend fun updateProfilePictureUrl(userId: String, url: String): Result<Unit> =
      Result.failure(UnsupportedOperationException("Not needed in this test"))

  override suspend fun createProfile(userId: String): Result<UserProfile> =
      Result.success(UserProfile(uid = userId))
}
