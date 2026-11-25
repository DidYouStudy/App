// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Stores and observes feedback recorded for personal study sessions. */
interface SessionFeedbackRepository {
  /** Observes the feedback record for one owner and event. */
  fun observeFeedback(ownerId: String, eventId: String): Flow<Result<SessionFeedback?>>

  /** Starts a new interval, creating the feedback record if needed. */
  suspend fun startInterval(
      ownerId: String,
      target: FeedbackTarget,
      startedAt: Instant,
  ): Result<Unit>

  /** Stops the currently running interval for an event. */
  suspend fun stopInterval(ownerId: String, eventId: String, stoppedAt: Instant): Result<Unit>

  /** Saves or removes the comment for an event. */
  suspend fun saveComment(
      ownerId: String,
      target: FeedbackTarget,
      comment: String?,
  ): Result<Unit>

  /** Saves or removes the completed-task count for an event. */
  suspend fun saveTasksCompleted(
      ownerId: String,
      target: FeedbackTarget,
      count: Int?,
  ): Result<Unit>

  /** Removes the feedback record for an event. */
  suspend fun deleteFeedback(ownerId: String, eventId: String): Result<Unit>
}
