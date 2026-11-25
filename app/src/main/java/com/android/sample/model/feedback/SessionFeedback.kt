// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import com.android.sample.model.event.EventType

/** Feedback recorded for one student's personal study session. */
data class SessionFeedback(
    /** The event to which this feedback belongs. */
    val eventId: String,
    /** The event type stored when feedback was first created. */
    val eventType: EventType,
    /** The normalized subject stored when feedback was first created. */
    val subject: String?,
    /** The student's study intervals. */
    val intervals: List<StudyInterval> = emptyList(),
    /** The number of completed tasks, or null when not recorded. */
    val tasksCompleted: Int? = null,
    /** The optional normalized comment. */
    val comment: String? = null,
) {
  /** Returns the total number of whole seconds in closed intervals. */
  val studiedSeconds: Long
    get() = throw NotImplementedError()

  /** Returns whether the last interval is still open. */
  val isRunning: Boolean
    get() = throw NotImplementedError()
}
