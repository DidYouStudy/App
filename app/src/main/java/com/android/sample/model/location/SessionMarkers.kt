// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import com.android.sample.model.event.GroupSession

/**
 * The group sessions held in one campus room, shown as a single map marker.
 *
 * @property room Room the marker is placed at.
 * @property sessions Sessions held in [room], in the order they were given; never empty.
 */
data class RoomMarker(val room: CampusLocation, val sessions: List<GroupSession>) {
  init {
    require(sessions.isNotEmpty()) { "A marker must have at least one session" }
  }

  /** Number of sessions held in [room]. */
  val count: Int
    get() = sessions.size
}

/**
 * Map content built from a list of sessions.
 *
 * @property markers One marker per room that has at least one session.
 * @property unplacedCount Number of sessions with no known room, which cannot be shown on the map.
 */
data class SessionMarkers(val markers: List<RoomMarker>, val unplacedCount: Int)

/**
 * Groups [sessions] by the campus room they take place in.
 *
 * A session's room is found with [resolver] from its location text. Sessions in the same room share
 * one marker, and sessions without a location or in an unknown place are only counted in
 * [SessionMarkers.unplacedCount]. Markers keep the order in which their room first appears in
 * [sessions], so the result is stable between calls.
 */
fun groupSessionsByRoom(
    sessions: List<GroupSession>,
    resolver: SessionPlaceResolver,
): SessionMarkers {
  val byRoom = LinkedHashMap<CampusLocation, MutableList<GroupSession>>()
  var unplaced = 0
  for (session in sessions) {
    val room = resolver.resolve(session.details.location)
    if (room == null) unplaced++ else byRoom.getOrPut(room) { mutableListOf() }.add(session)
  }
  return SessionMarkers(
      markers = byRoom.map { (room, grouped) -> RoomMarker(room, grouped) },
      unplacedCount = unplaced,
  )
}
