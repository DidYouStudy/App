// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import com.android.sample.model.location.Location
import com.android.sample.model.location.RoomMarker

/**
 * A marker as the map view needs it: a place, a name to report back when tapped, and the number of
 * sessions in the room. It holds no session data, so the map view stays independent of the group
 * session model.
 *
 * @property roomName Name of the room, also the marker's identity in click callbacks.
 * @property location Where the marker is drawn.
 * @property count Number of sessions in the room. It is not drawn on the pin; the screen shows it
 *   when the marker is opened.
 */
data class MapMarkerUi(val roomName: String, val location: Location, val count: Int)

/** Converts a room marker to what the map view draws. */
fun RoomMarker.toMapMarker(): MapMarkerUi =
    MapMarkerUi(roomName = room.name, location = room.location, count = count)
