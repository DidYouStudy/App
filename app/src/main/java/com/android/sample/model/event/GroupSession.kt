// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: GitHub Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.event

import java.time.Instant

/**
 * A shared, public study session with membership managed separately from its schedule.
 *
 * @property id stable session identifier assigned by the caller before saving.
 * @property createdBy identifier of the user who created the session and must remain a member.
 * @property memberIds unique identifiers of members, including [createdBy], at most [MAX_MEMBERS].
 *   Callers should pass a stable list and not mutate it afterwards; it is exposed as read-only but
 *   not defensively copied.
 * @property details planned content shared with every [Event] kind.
 * @property createdAt creation time supplied by the caller.
 * @property updatedAt last-edit time, initially [createdAt].
 */
data class GroupSession(
    override val id: String,
    val createdBy: String,
    val memberIds: List<String>,
    override val details: EventDetails,
    override val createdAt: Instant,
    override val updatedAt: Instant = createdAt,
) : Event {
  init {
    require(id.isNotBlank()) { "Group session ID must not be blank" }
    require(createdBy.isNotBlank()) { "Creator ID must not be blank" }
    require(memberIds.all { it.isNotBlank() }) { "Member IDs must not be blank" }
    require(createdBy in memberIds) { "Creator must be a member" }
    require(memberIds.distinct().size == memberIds.size) { "Member IDs must be unique" }
    require(memberIds.size <= MAX_MEMBERS) { "Group session must not exceed $MAX_MEMBERS members" }
  }

  companion object {
    /** Maximum number of members allowed in a group session. */
    const val MAX_MEMBERS = 30
  }
}
