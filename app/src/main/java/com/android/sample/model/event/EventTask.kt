// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant

/**
 * One item of an [Event]'s planned checklist, such as "Exercises 1 to 3".
 *
 * A task is a value stored inside its event: it has no identifier and no schedule of its own.
 *
 * @property text nonblank description of what is planned.
 * @property completedAt when the user checked the task off, or null while it is still open.
 */
data class EventTask(val text: String, val completedAt: Instant? = null) {
  init {
    require(text.isNotBlank()) { "Task text must not be blank" }
  }

  /** Whether the user has checked this task off. */
  val isCompleted: Boolean
    get() = completedAt != null
}
