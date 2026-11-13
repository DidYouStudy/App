// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.groupsession

import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.SessionPlaceResolver

/**
 * The group sessions held in one campus room, as the map view's cards need them.
 *
 * @property room Room the sessions take place in.
 * @property items One list item per session, with the current user's membership; never empty.
 */
data class RoomSessions(val room: CampusLocation, val items: List<GroupSessionItem>) {
  init {
    require(items.isNotEmpty()) { "A room must have at least one session" }
  }
}

/**
 * Groups [items] by the campus room their session takes place in, found with [resolver] from the
 * session's location text.
 *
 * Sessions in the same room share one entry. Sessions with no location, or in a place that is not a
 * known room, cannot be shown on the map and are left out. Entries keep the order in which their
 * room first appears in [items], so the result is stable between calls.
 */
fun groupByRoom(items: List<GroupSessionItem>, resolver: SessionPlaceResolver): List<RoomSessions> {
  val byRoom = LinkedHashMap<CampusLocation, MutableList<GroupSessionItem>>()
  for (item in items) {
    val room = resolver.resolve(item.session.details.location) ?: continue
    byRoom.getOrPut(room) { mutableListOf() }.add(item)
  }
  return byRoom.map { (room, grouped) -> RoomSessions(room, grouped.toList()) }
}
