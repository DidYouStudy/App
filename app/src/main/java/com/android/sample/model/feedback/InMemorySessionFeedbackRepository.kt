// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory feedback repository used by tests until the Firestore implementation exists. */
class InMemorySessionFeedbackRepository : SessionFeedbackRepository {
  private data class FeedbackKey(val ownerId: String, val eventId: String)

  private val data = MutableStateFlow<Map<FeedbackKey, SessionFeedback>>(emptyMap())

  /** Observes a feedback record for a personal event. */
  override fun observeFeedback(ownerId: String, eventId: String): Flow<Result<SessionFeedback?>> =
      invalidIds(ownerId, eventId)?.let { flowOf(Result.failure(it)) }
          ?: data
              .map { records -> Result.success(records[FeedbackKey(ownerId, eventId)]) }
              .distinctUntilChanged()

  /** Starts an interval for a personal event. */
  override suspend fun startInterval(
      ownerId: String,
      target: FeedbackTarget,
      startedAt: Instant,
  ): Result<Unit> {
    invalidIds(ownerId, target.eventId)?.let {
      return Result.failure(it)
    }
    return updateRecord(ownerId, target.eventId) { current ->
      when {
        current?.isRunning == true ->
            Result.failure(FeedbackException(FeedbackError.ALREADY_STARTED))
        current != null && current.intervals.size >= FeedbackLimits.MAX_INTERVALS ->
            Result.failure(FeedbackException(FeedbackError.INVALID_ARGUMENT))
        current != null &&
            current.intervals.lastOrNull()?.endedAt?.let { startedAt.isBefore(it) } == true ->
            Result.failure(FeedbackException(FeedbackError.INVALID_ARGUMENT))
        else ->
            Result.success(
                (current ?: newFeedback(target)).copy(
                    intervals = (current?.intervals ?: emptyList()) + StudyInterval(startedAt)
                )
            )
      }
    }
  }

  /** Stops the running interval for a personal event. */
  override suspend fun stopInterval(
      ownerId: String,
      eventId: String,
      stoppedAt: Instant,
  ): Result<Unit> {
    invalidIds(ownerId, eventId)?.let {
      return Result.failure(it)
    }
    return updateRecord(ownerId, eventId) { current ->
      val open = current?.intervals?.lastOrNull()
      when {
        open == null || current.isRunning != true ->
            Result.failure(FeedbackException(FeedbackError.NOT_STARTED))
        !stoppedAt.isAfter(open.startedAt) ->
            Result.failure(FeedbackException(FeedbackError.INVALID_ARGUMENT))
        else ->
            Result.success(
                current.copy(
                    intervals =
                        current.intervals.dropLast(1) + StudyInterval(open.startedAt, stoppedAt)
                )
            )
      }
    }
  }

  /** Saves or removes the comment for a personal event. */
  override suspend fun saveComment(
      ownerId: String,
      target: FeedbackTarget,
      comment: String?,
  ): Result<Unit> {
    invalidIds(ownerId, target.eventId)?.let {
      return Result.failure(it)
    }
    val normalized = comment?.trim()?.takeUnless { it.isBlank() }
    if (normalized != null && normalized.length > FeedbackLimits.COMMENT) {
      return Result.failure(FeedbackException(FeedbackError.INVALID_ARGUMENT))
    }
    if (normalized == null && !data.value.containsKey(FeedbackKey(ownerId, target.eventId))) {
      return Result.success(Unit)
    }
    return updateRecord(ownerId, target.eventId) { current ->
      Result.success((current ?: newFeedback(target)).copy(comment = normalized))
    }
  }

  /** Saves or removes the completed-task count for a personal event. */
  override suspend fun saveTasksCompleted(
      ownerId: String,
      target: FeedbackTarget,
      count: Int?,
  ): Result<Unit> {
    invalidIds(ownerId, target.eventId)?.let {
      return Result.failure(it)
    }
    if (count != null && count < 0) {
      return Result.failure(FeedbackException(FeedbackError.INVALID_ARGUMENT))
    }
    return updateRecord(ownerId, target.eventId) { current ->
      Result.success((current ?: newFeedback(target)).copy(tasksCompleted = count))
    }
  }

  /** Deletes feedback for a personal event. */
  override suspend fun deleteFeedback(ownerId: String, eventId: String): Result<Unit> =
      invalidIds(ownerId, eventId)?.let { Result.failure(it) }
          ?: updateRecord(ownerId, eventId) { Result.success(null) }

  // Validates document path components before any state access or mutation.
  private fun invalidIds(ownerId: String, eventId: String): FeedbackException? =
      if (ownerId.isBlank() || eventId.isBlank() || '/' in ownerId || '/' in eventId) {
        FeedbackException(FeedbackError.INVALID_ARGUMENT)
      } else {
        null
      }

  // Applies one checked mutation atomically, retaining the current record on failure.
  private fun updateRecord(
      ownerId: String,
      eventId: String,
      transform: (SessionFeedback?) -> Result<SessionFeedback?>,
  ): Result<Unit> {
    var failure: FeedbackException? = null
    data.update { records ->
      // MutableStateFlow may retry this lambda after a concurrent state change.
      failure = null
      val key = FeedbackKey(ownerId, eventId)
      val result = transform(records[key])
      result.exceptionOrNull()?.let {
        failure = it as FeedbackException
        return@update records
      }
      val updated = result.getOrNull()
      if (updated == null) records - key else records + (key to updated)
    }
    return failure?.let { Result.failure(it) } ?: Result.success(Unit)
  }

  private fun newFeedback(target: FeedbackTarget): SessionFeedback =
      SessionFeedback(
          target.eventId,
          target.eventType,
          target.subject?.trim()?.takeUnless { it.isBlank() },
      )
}
