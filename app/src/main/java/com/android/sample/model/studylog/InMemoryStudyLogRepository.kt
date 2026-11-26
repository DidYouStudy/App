// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory study log repository used by tests until the Firestore implementation exists. */
class InMemoryStudyLogRepository : StudyLogRepository {
  private data class StudyLogKey(val ownerId: String, val eventId: String)

  private val data = MutableStateFlow<Map<StudyLogKey, StudyLog>>(emptyMap())

  /** Observes a study log record for a personal event. */
  override fun observeLog(ownerId: String, eventId: String): Flow<Result<StudyLog?>> =
      invalidIds(ownerId, eventId)?.let { flowOf(Result.failure(it)) }
          ?: data
              .map { records -> Result.success(records[StudyLogKey(ownerId, eventId)]) }
              .distinctUntilChanged()

  /** Starts an interval for a personal event. */
  override suspend fun startInterval(
      ownerId: String,
      target: StudyLogTarget,
      startedAt: Instant,
  ): Result<Unit> {
    invalidIds(ownerId, target.eventId)?.let {
      return Result.failure(it)
    }
    return updateRecord(ownerId, target.eventId) { current ->
      when {
        current?.isRunning == true ->
            Result.failure(StudyLogException(StudyLogError.ALREADY_STARTED))
        current != null && current.intervals.size >= StudyLogLimits.MAX_INTERVALS ->
            Result.failure(StudyLogException(StudyLogError.INVALID_ARGUMENT))
        current != null &&
            current.intervals.lastOrNull()?.endedAt?.let { startedAt.isBefore(it) } == true ->
            Result.failure(StudyLogException(StudyLogError.INVALID_ARGUMENT))
        else ->
            Result.success(
                (current ?: newStudyLog(target)).copy(
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
            Result.failure(StudyLogException(StudyLogError.NOT_STARTED))
        !stoppedAt.isAfter(open.startedAt) ->
            Result.failure(StudyLogException(StudyLogError.INVALID_ARGUMENT))
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
      target: StudyLogTarget,
      comment: String?,
  ): Result<Unit> {
    invalidIds(ownerId, target.eventId)?.let {
      return Result.failure(it)
    }
    val normalized = comment?.trim()?.takeUnless { it.isBlank() }
    if (normalized != null && normalized.length > StudyLogLimits.COMMENT) {
      return Result.failure(StudyLogException(StudyLogError.INVALID_ARGUMENT))
    }
    return updateRecord(ownerId, target.eventId) { current ->
      if (current == null && normalized == null) {
        // Keep an empty delete atomic with the record lookup.
        Result.success(null)
      } else {
        Result.success((current ?: newStudyLog(target)).copy(comment = normalized))
      }
    }
  }

  /** Saves or removes the completed-task count for a personal event. */
  override suspend fun saveTasksCompleted(
      ownerId: String,
      target: StudyLogTarget,
      count: Int?,
  ): Result<Unit> {
    invalidIds(ownerId, target.eventId)?.let {
      return Result.failure(it)
    }
    if (count != null && count < 0) {
      return Result.failure(StudyLogException(StudyLogError.INVALID_ARGUMENT))
    }
    return updateRecord(ownerId, target.eventId) { current ->
      if (current == null && count == null) {
        // Do not create a record for an empty save.
        Result.success(null)
      } else {
        Result.success((current ?: newStudyLog(target)).copy(tasksCompleted = count))
      }
    }
  }

  /** Deletes study log for a personal event. */
  override suspend fun deleteLog(ownerId: String, eventId: String): Result<Unit> =
      invalidIds(ownerId, eventId)?.let { Result.failure(it) }
          ?: updateRecord(ownerId, eventId) { Result.success(null) }

  // Validates document path components before any state access or mutation.
  private fun invalidIds(ownerId: String, eventId: String): StudyLogException? =
      if (ownerId.isBlank() || eventId.isBlank() || '/' in ownerId || '/' in eventId) {
        StudyLogException(StudyLogError.INVALID_ARGUMENT)
      } else {
        null
      }

  // Applies one checked mutation atomically, retaining the current record on failure.
  private fun updateRecord(
      ownerId: String,
      eventId: String,
      transform: (StudyLog?) -> Result<StudyLog?>,
  ): Result<Unit> {
    var failure: StudyLogException? = null
    data.update { records ->
      // MutableStateFlow may retry this lambda after a concurrent state change.
      failure = null
      val key = StudyLogKey(ownerId, eventId)
      val result = transform(records[key])
      result.exceptionOrNull()?.let {
        failure = it as StudyLogException
        return@update records
      }
      val updated = result.getOrNull()
      if (updated == null) records - key else records + (key to updated)
    }
    return failure?.let { Result.failure(it) } ?: Result.success(Unit)
  }

  private fun newStudyLog(target: StudyLogTarget): StudyLog =
      StudyLog(
          target.eventId,
          target.eventType,
          target.subject?.trim()?.takeUnless { it.isBlank() },
      )
}
