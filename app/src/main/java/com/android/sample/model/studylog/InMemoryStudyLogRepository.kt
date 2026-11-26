// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventTextLimits
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

  override fun observeLogs(
      ownerId: String,
      from: Instant,
      to: Instant,
  ): Flow<Result<List<StudyLog>>> {
    val invalidRange = invalidId(ownerId) || !from.isBefore(to)
    if (invalidRange) {
      return flowOf(Result.failure(StudyLogException(StudyLogError.INVALID_ARGUMENT)))
    }
    return data
        .map { records ->
          Result.success(
              records
                  .filter { (key, log) ->
                    key.ownerId == ownerId &&
                        !log.plannedStart.isBefore(from) &&
                        log.plannedStart.isBefore(to)
                  }
                  .values
                  .sortedWith(compareBy<StudyLog> { it.plannedStart }.thenBy { it.eventId })
          )
        }
        .distinctUntilChanged()
  }

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

  override suspend fun saveTasks(
      ownerId: String,
      target: StudyLogTarget,
      tasks: List<EventTask>,
  ): Result<Unit> {
    invalidIds(ownerId, target.eventId)?.let {
      return Result.failure(it)
    }
    if (
        tasks.size > StudyLogLimits.MAX_TASKS || tasks.any { it.text.length > EventTextLimits.TASK }
    ) {
      return Result.failure(StudyLogException(StudyLogError.INVALID_ARGUMENT))
    }
    return updateRecord(ownerId, target.eventId) { current ->
      if (current == null && tasks.isEmpty()) {
        Result.success(null)
      } else {
        Result.success((current ?: newStudyLog(target)).copy(tasks = tasks))
      }
    }
  }

  /** Deletes study log for a personal event. */
  override suspend fun deleteLog(ownerId: String, eventId: String): Result<Unit> =
      invalidIds(ownerId, eventId)?.let { Result.failure(it) }
          ?: updateRecord(ownerId, eventId) { Result.success(null) }

  // Validates document path components before any state access or mutation.
  private fun invalidIds(ownerId: String, eventId: String): StudyLogException? =
      if (invalidId(ownerId) || invalidId(eventId)) {
        StudyLogException(StudyLogError.INVALID_ARGUMENT)
      } else {
        null
      }

  private fun invalidId(id: String): Boolean = id.isBlank() || '/' in id

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
          eventId = target.eventId,
          eventType = target.eventType,
          subject = target.subject?.trim()?.takeUnless { it.isBlank() },
          plannedStart = target.plannedStart,
          plannedEnd = target.plannedEnd,
      )
}
