// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant

/**
 * A private, timed entry of a user's schedule, independent of Firebase and screen state.
 *
 * Personal events are shown by the calendar, the dashboard and the add-activity flow. This model
 * describes planned time, not actual study duration, breaks, feedback, or group participation.
 *
 * @property id stable event identifier assigned by the caller before saving.
 * @property ownerId identifier of the user owning this private entry.
 * @property details planned content shared with every [Event] kind.
 * @property createdAt creation time supplied by the caller; no clock is read by this model.
 * @property updatedAt last-edit time, initially [createdAt]; copying does not advance it
 *   automatically.
 * @property tasks planned checklist for this event, in display order; allowed for every type.
 *   Callers should pass a stable list and not mutate it after creating the event. The list is
 *   exposed as read-only but is not defensively copied.
 * @property importKey identifies the entry in the schedule file it was imported from (its `UID`),
 *   so a later import can update or remove it; null for events the user created by hand.
 */
data class PersonalEvent(
    override val id: String,
    val ownerId: String,
    override val details: EventDetails,
    override val createdAt: Instant,
    override val updatedAt: Instant = createdAt,
    val tasks: List<EventTask> = emptyList(),
    val importKey: String? = null,
) : Event {
  init {
    require(id.isNotBlank()) { "Event ID must not be blank" }
    require(ownerId.isNotBlank()) { "Owner ID must not be blank" }
    require(importKey == null || importKey.isNotBlank()) { "Import key must not be blank" }
  }
}
