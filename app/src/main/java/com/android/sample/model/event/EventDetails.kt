// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Duration
import java.time.Instant

/**
 * The schedule content shared by every [Event] kind: what the user fills in the form.
 *
 * Record metadata (ID, creation and edit times) and kind-specific data (owner, tasks, members) live
 * on the [Event] implementations, so two details are equal exactly when the planned content is the
 * same. Times are absolute instants; the UI displays them in the device's current time zone.
 *
 * @property type activity kind; independent of its subject.
 * @property subject selected or custom subject label, such as a course name. Required and nonblank
 *   for every type except [EventType.OTHER], where it may be null.
 * @property startsAt planned start, inclusive.
 * @property endsAt planned end, exclusive; after [startsAt] and at most [MAX_DURATION] later.
 * @property location optional nonblank place label; contains no geographic coordinates.
 * @property description optional free-form details.
 */
data class EventDetails(
    val type: EventType,
    val subject: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val location: String? = null,
    val description: String? = null,
) {
  init {
    require(subject?.isNotBlank() ?: (type == EventType.OTHER)) {
      "Subject must not be blank, and may only be absent for OTHER"
    }
    require(endsAt.isAfter(startsAt)) { "End must be after start" }
    require(Duration.between(startsAt, endsAt) <= MAX_DURATION) {
      "Event must not last longer than $MAX_DURATION"
    }
    require(location == null || location.isNotBlank()) { "Location must not be blank" }
  }

  companion object {
    /** Longest allowed event; catches input mistakes such as a wrong end date. */
    val MAX_DURATION: Duration = Duration.ofHours(24)
  }
}
