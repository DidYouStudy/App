// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventTextLimits
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Stores and observes study log recorded for personal sessions.
 *
 * A study log can be recorded for every event type. The caller (#77) and the user's preferences
 * determine which event types can be timed.
 */
interface StudyLogRepository {
  /**
   * Observes the study log record for one owner and event.
   *
   * Returns [StudyLogError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`.
   */
  fun observeLog(ownerId: String, eventId: String): Flow<Result<StudyLog?>>

  /**
   * Observes logs planned in the half-open interval [[from], [to]) for one owner.
   *
   * Intended for statistics over a day or a week.
   *
   * Returns [StudyLogError.INVALID_ARGUMENT] for an invalid owner ID or when [from] is not before
   * [to].
   */
  fun observeLogs(ownerId: String, from: Instant, to: Instant): Flow<Result<List<StudyLog>>>

  /**
   * Starts a new interval, creating the study log record if needed.
   *
   * If the app is killed while an interval runs, nothing closes it. The caller (#77) is responsible
   * for closing it on the next launch, for example at the planned end.
   *
   * Returns [StudyLogError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`, when the start
   * is before the previous end, or past [StudyLogLimits.MAX_INTERVALS]. Returns
   * [StudyLogError.ALREADY_STARTED] while an interval is running. A failed call changes nothing.
   */
  suspend fun startInterval(
      ownerId: String,
      target: StudyLogTarget,
      startedAt: Instant,
  ): Result<Unit>

  /**
   * Stops the currently running interval for an event.
   *
   * If the app was killed while the interval ran, the caller (#77) is responsible for closing it on
   * the next launch. [stoppedAt] may be in the past in that case, as long as it is after the
   * interval start.
   *
   * Returns [StudyLogError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`, or when
   * [stoppedAt] is not after the interval start. Returns [StudyLogError.NOT_STARTED] when nothing
   * runs. A failed call changes nothing.
   */
  suspend fun stopInterval(ownerId: String, eventId: String, stoppedAt: Instant): Result<Unit>

  /**
   * Saves or removes the comment for an event.
   *
   * Returns [StudyLogError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`, or when the
   * comment exceeds [StudyLogLimits.COMMENT]. A null or blank comment removes it. A failed call
   * changes nothing.
   */
  suspend fun saveComment(
      ownerId: String,
      target: StudyLogTarget,
      comment: String?,
  ): Result<Unit>

  /**
   * Replaces the task snapshot for an event, including task text and completion times.
   *
   * The caller (#77) takes the snapshot when the first interval starts, so the log always has the
   * task list, and again at each stop, so completedAt times are up to date.
   *
   * Returns [StudyLogError.INVALID_ARGUMENT] for invalid IDs, more than [StudyLogLimits.MAX_TASKS]
   * tasks, or task text exceeding [EventTextLimits.TASK]. An empty list on a missing log creates
   * nothing. A failed call changes nothing.
   */
  suspend fun saveTasks(
      ownerId: String,
      target: StudyLogTarget,
      tasks: List<EventTask>,
  ): Result<Unit>

  /**
   * Removes the study log record for an event.
   *
   * Returns [StudyLogError.INVALID_ARGUMENT] for a blank ID or an ID containing `/`. A missing
   * record is fine, and a failed call changes nothing.
   */
  suspend fun deleteLog(ownerId: String, eventId: String): Result<Unit>
}
