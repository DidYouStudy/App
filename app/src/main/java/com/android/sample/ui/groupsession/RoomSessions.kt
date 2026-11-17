// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.groupsession

import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
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

    // fake CampusLocation object used to filter noLoc sessions
    val noLoc =
        CampusLocation(
            "",
            "",
            CampusLocationType.CONFERENCE_ROOM,
            0,
            Location(0.0, 0.0),
        )

    return items
        .groupBy { item -> resolver.resolve(item.session.details.location) ?: noLoc }
        .filter { (key, list) -> key != noLoc }
        .map { (room, grouped) -> RoomSessions(room, grouped.toList()) }
}
