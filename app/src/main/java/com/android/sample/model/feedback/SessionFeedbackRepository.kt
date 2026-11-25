// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Stores and observes feedback recorded for personal study sessions.
 *
 * Only personal events are recorded for now. Part 2 adds the Firestore implementation with
 * local-first writes.
 */
interface SessionFeedbackRepository {
  /**
   * Observes the feedback record for one owner and event.
   *
   * Returns [FeedbackError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`.
   */
  fun observeFeedback(ownerId: String, eventId: String): Flow<Result<SessionFeedback?>>

  /**
   * Starts a new interval, creating the feedback record if needed.
   *
   * Returns [FeedbackError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`, when the start
   * is before the previous end, or past [FeedbackLimits.MAX_INTERVALS]. Returns
   * [FeedbackError.ALREADY_STARTED] while an interval is running. A failed call changes nothing.
   */
  suspend fun startInterval(
      ownerId: String,
      target: FeedbackTarget,
      startedAt: Instant,
  ): Result<Unit>

  /**
   * Stops the currently running interval for an event.
   *
   * Returns [FeedbackError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`, or when
   * [stoppedAt] is not after the interval start. Returns [FeedbackError.NOT_STARTED] when nothing
   * runs. A failed call changes nothing.
   */
  suspend fun stopInterval(ownerId: String, eventId: String, stoppedAt: Instant): Result<Unit>

  /**
   * Saves or removes the comment for an event.
   *
   * Returns [FeedbackError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`, or when the
   * comment exceeds [FeedbackLimits.COMMENT]. A null or blank comment removes it. A failed call
   * changes nothing.
   */
  suspend fun saveComment(
      ownerId: String,
      target: FeedbackTarget,
      comment: String?,
  ): Result<Unit>

  /**
   * Saves or removes the completed-task count for an event.
   *
   * Returns [FeedbackError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`, or when the
   * count is negative. A null count removes it. A failed call changes nothing.
   */
  suspend fun saveTasksCompleted(
      ownerId: String,
      target: FeedbackTarget,
      count: Int?,
  ): Result<Unit>

  /**
   * Removes the feedback record for an event.
   *
   * Returns [FeedbackError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`. A missing
   * record is fine, and a failed call changes nothing.
   */
  suspend fun deleteFeedback(ownerId: String, eventId: String): Result<Unit>
}
