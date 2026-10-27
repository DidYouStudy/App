// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.groupsession

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Stores shared, public group sessions behind a repository boundary.
 *
 * Any signed-in user can observe and join sessions. Only the creator can save changes or delete;
 * creator identity cannot change, and the creator cannot leave. Existing-session saves retain the
 * stored members and creation time, so membership changes only through join and leave. The
 * repository never reads a clock: callers supply timestamps. Failures are [Result.failure] values
 * containing [com.android.sample.model.event.EventsException], and cancellation is propagated.
 */
interface GroupSessionsRepository {
  /**
   * Observes every session whose end is after [endingAfter], sorted by start, end, then ID.
   *
   * A session that ends exactly at [endingAfter] is excluded. The flow emits its current list and
   * emits again when a save, join, leave, or delete changes the list. [endingAfter] is fixed for
   * the flow's lifetime: a session is not removed when its end time passes, so callers that need
   * that observe again with a later instant or filter the list themselves.
   */
  fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>>

  /** Returns a new nonblank, unique identifier to assign before saving a session. */
  fun newSessionId(): String

  /**
   * Returns the session with [sessionId], or NOT_FOUND if it does not exist.
   *
   * A blank [sessionId] fails with INVALID_ARGUMENT.
   */
  suspend fun getSession(sessionId: String): Result<GroupSession>

  /**
   * Creates a session or updates the existing session with the same ID.
   *
   * Creation requires [userId] to equal [GroupSession.createdBy]. Updates require [userId] to be
   * that stored session's creator and cannot change its creator. Updating never changes the stored
   * members or [GroupSession.createdAt]; incoming values for them are ignored. Blank user IDs fail
   * with INVALID_ARGUMENT, and permission violations fail with PERMISSION_DENIED.
   *
   * Timestamps are the caller's responsibility: set [GroupSession.updatedAt] to the edit time
   * before saving an update; the repository stores it as given and never reads a clock. Joining and
   * leaving do not change [GroupSession.updatedAt].
   */
  suspend fun saveSession(userId: String, session: GroupSession): Result<Unit>

  /**
   * Deletes the session identified by [sessionId] when [userId] is its creator.
   *
   * Deleting a missing session succeeds. Blank arguments fail with INVALID_ARGUMENT, and deleting
   * another user's session fails with PERMISSION_DENIED.
   */
  suspend fun deleteSession(userId: String, sessionId: String): Result<Unit>

  /**
   * Adds [userId] to the session's membership once; joining an already joined session succeeds.
   *
   * Blank arguments fail with INVALID_ARGUMENT and a missing session fails with NOT_FOUND.
   */
  suspend fun joinSession(userId: String, sessionId: String): Result<Unit>

  /**
   * Removes [userId] from membership; leaving when not a member succeeds without a change.
   *
   * The creator cannot leave and receives INVALID_ARGUMENT. Blank arguments fail with
   * INVALID_ARGUMENT, and a missing session fails with NOT_FOUND.
   */
  suspend fun leaveSession(userId: String, sessionId: String): Result<Unit>
}
