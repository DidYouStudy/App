// Co-authored-by: GitHub Copilot
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventType
import java.time.Duration
import java.time.Instant

/**
 * A shared, public study session with membership managed separately from its schedule.
 *
 * @property id stable session identifier assigned by the caller before saving.
 * @property createdBy identifier of the user who created the session and must remain a member.
 * @property memberIds unique identifiers of members, including [createdBy].
 * @property type activity kind, independent of its subject.
 * @property subject selected or custom subject label; required except for [EventType.OTHER].
 * @property startsAt planned start, inclusive.
 * @property endsAt planned end, exclusive; after [startsAt] and at most [MAX_DURATION] later.
 * @property createdAt creation time supplied by the caller.
 * @property updatedAt last-edit time, initially [createdAt].
 * @property location optional nonblank place label.
 * @property description optional free-form details.
 */
data class GroupSession(
    val id: String,
    val createdBy: String,
    val memberIds: List<String>,
    val type: EventType,
    val subject: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val location: String? = null,
    val description: String? = null,
) {
  init {
    TODO("Not implemented")
  }

  companion object {
    /** Longest allowed session duration; catches scheduling mistakes such as a wrong end date. */
    val MAX_DURATION: Duration = Duration.ofHours(24)
  }
}
