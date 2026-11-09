// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant

/**
 * Anything shown on a user's schedule: a [PersonalEvent] or a [GroupSession].
 *
 * Code that only needs the schedule content (calendar, dashboard, statistics) works on this type; a
 * `when` over it is exhaustive, so adding a kind flags every place that must handle it.
 *
 * @property id stable identifier assigned by the caller before saving.
 * @property details the planned content shared by every kind.
 * @property createdAt creation time supplied by the caller; no clock is read by the models.
 * @property updatedAt last-edit time, initially [createdAt]; copying does not advance it.
 */
sealed interface Event {
  val id: String
  val details: EventDetails
  val createdAt: Instant
  val updatedAt: Instant
}
