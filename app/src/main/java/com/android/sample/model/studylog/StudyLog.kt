// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import java.time.Instant

/** study log recorded for one student's personal session. */
data class StudyLog(
    /** The event to which this study log belongs. */
    val eventId: String,
    /** The event type stored when study log was first created. */
    val eventType: EventType,
    /** The normalized subject stored when study log was first created. */
    val subject: String?,
    /** The planned start time stored when study log was first created. */
    val plannedStart: Instant,
    /** The planned end time stored when study log was first created. */
    val plannedEnd: Instant,
    /**
     * The student's focus intervals. Each gap between intervals is a break. An open interval is not
     * counted in [studiedSeconds].
     */
    val intervals: List<StudyInterval> = emptyList(),
    /** The task snapshot stored for this study log. */
    val tasks: List<EventTask> = emptyList(),
    /** The optional normalized comment. */
    val comment: String? = null,
) {
  /** Returns the total number of whole seconds in closed intervals. */
  val studiedSeconds: Long
    get() = intervals.sumOf { interval ->
      interval.endedAt?.let { endedAt ->
        java.time.Duration.between(interval.startedAt, endedAt).seconds
      } ?: 0L
    }

  /** Returns whether the last interval is still open. */
  val isRunning: Boolean
    get() = intervals.lastOrNull()?.endedAt == null && intervals.isNotEmpty()
}
