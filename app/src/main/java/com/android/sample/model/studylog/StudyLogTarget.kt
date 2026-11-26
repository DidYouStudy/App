// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import com.android.sample.model.event.EventType

/** Event metadata required when creating study log for an event. */
data class StudyLogTarget(
    /** The event receiving study log. */
    val eventId: String,
    /** The event type at the time the study log operation is requested. */
    val eventType: EventType,
    /** The event subject, if any. */
    val subject: String?,
    /** The planned start time frozen into a newly created study log. */
    val plannedStart: java.time.Instant,
    /** The planned end time frozen into a newly created study log. */
    val plannedEnd: java.time.Instant,
) {
  init {
    require(plannedEnd.isAfter(plannedStart)) { "Planned end must be after planned start" }
  }
}
