// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.calendar

import java.time.Instant

/**
 * A private, timed calendar entry, independent of Firebase and screen state.
 *
 * Times are absolute instants; the UI displays them in the device's current time zone. This model
 * describes planned time, not actual study duration, breaks, feedback, or group participation.
 *
 * @property id stable event identifier assigned by the caller before saving.
 * @property ownerId identifier of the user owning this private entry.
 * @property type activity kind selected in the calendar form; independent of its subject.
 * @property subject nonblank selected or custom subject label, such as a course name.
 * @property startsAt planned start, inclusive.
 * @property endsAt planned end, exclusive; must be after [startsAt].
 * @property createdAt creation time supplied by the caller; no clock is read by this model.
 * @property updatedAt last-edit time, initially [createdAt]; copying does not advance it
 *   automatically.
 * @property location optional nonblank place label; contains no geographic coordinates.
 * @property description optional free-form details.
 */
data class CalendarEvent(
    val id: String,
    val ownerId: String,
    val type: EventType,
    val subject: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val location: String? = null,
    val description: String? = null,
) {
  init {
    require(id.isNotBlank()) { "Event ID must not be blank" }
    require(ownerId.isNotBlank()) { "Owner ID must not be blank" }
    require(subject.isNotBlank()) { "Subject must not be blank" }
    require(endsAt.isAfter(startsAt)) { "End must be after start" }
    require(location == null || location.isNotBlank()) { "Location must not be blank" }
  }
}
