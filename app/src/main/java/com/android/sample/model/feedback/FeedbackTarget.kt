// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import com.android.sample.model.event.EventType

/** Event metadata required when creating feedback for an event. */
data class FeedbackTarget(
    /** The event receiving feedback. */
    val eventId: String,
    /** The event type at the time the feedback operation is requested. */
    val eventType: EventType,
    /** The event subject, if any. */
    val subject: String?,
)
