// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Duration
import java.time.Instant

/**
 * A private, timed entry of a user's schedule, independent of Firebase and screen state.
 *
 * Events are shown by the calendar, the dashboard and the add-activity flow. Times are absolute
 * instants; the UI displays them in the device's current time zone. This model describes planned
 * time, not actual study duration, breaks, feedback, or group participation.
 *
 * @property id stable event identifier assigned by the caller before saving.
 * @property ownerId identifier of the user owning this private entry.
 * @property type activity kind selected in the add-event form; independent of its subject.
 * @property subject selected or custom subject label, such as a course name. Required and nonblank
 *   for every type except [EventType.OTHER], where it may be null.
 * @property startsAt planned start, inclusive.
 * @property endsAt planned end, exclusive; after [startsAt] and at most [MAX_DURATION] later.
 * @property createdAt creation time supplied by the caller; no clock is read by this model.
 * @property updatedAt last-edit time, initially [createdAt]; copying does not advance it
 *   automatically.
 * @property location optional nonblank place label; contains no geographic coordinates.
 * @property description optional free-form details.
 * @property tasks planned checklist for this event, in display order; allowed for every type.
 */
data class Event(
    val id: String,
    val ownerId: String,
    val type: EventType,
    val subject: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val location: String? = null,
    val description: String? = null,
    val tasks: List<EventTask> = emptyList(),
) {
  init {
    require(id.isNotBlank()) { "Event ID must not be blank" }
    require(ownerId.isNotBlank()) { "Owner ID must not be blank" }
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
