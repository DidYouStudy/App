// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** In-memory feedback repository used by tests until the Firestore implementation exists. */
class InMemorySessionFeedbackRepository : SessionFeedbackRepository {
  /** Observes a feedback record. */
  override fun observeFeedback(ownerId: String, eventId: String): Flow<Result<SessionFeedback?>> =
      throw NotImplementedError()

  /** Starts an interval. */
  override suspend fun startInterval(
      ownerId: String,
      target: FeedbackTarget,
      startedAt: Instant,
  ): Result<Unit> = throw NotImplementedError()

  /** Stops an interval. */
  override suspend fun stopInterval(
      ownerId: String,
      eventId: String,
      stoppedAt: Instant,
  ): Result<Unit> = throw NotImplementedError()

  /** Saves or removes a comment. */
  override suspend fun saveComment(
      ownerId: String,
      target: FeedbackTarget,
      comment: String?,
  ): Result<Unit> = throw NotImplementedError()

  /** Saves or removes a task count. */
  override suspend fun saveTasksCompleted(
      ownerId: String,
      target: FeedbackTarget,
      count: Int?,
  ): Result<Unit> = throw NotImplementedError()

  /** Deletes feedback. */
  override suspend fun deleteFeedback(ownerId: String, eventId: String): Result<Unit> =
      throw NotImplementedError()
}
