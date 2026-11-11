// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession.map

import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import com.android.sample.model.location.RoomMarker
import com.android.sample.ui.groupsession.MapMarkerUi
import com.android.sample.ui.groupsession.toMapMarker
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for the conversion of room markers to what the map view draws. */
class MapMarkerUiTest {

  private val aab =
      MapMarkerUi(roomName = "AAB 0 26", location = Location(46.518, 6.563), count = 3)

  @Test
  fun toMapMarker_usesRoomNameLocationAndSessionCount() {
    val start = Instant.parse("2030-01-01T10:00:00Z")

    fun session(id: String) =
        GroupSession(
            id = id,
            createdBy = "creator",
            memberIds = listOf("creator"),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Maths",
                    startsAt = start,
                    endsAt = start.plusSeconds(3600),
                ),
            createdAt = start,
        )

    val room =
        CampusLocation(
            "AAB 0 26",
            "AAB",
            CampusLocationType.LECTURE_ROOM,
            0,
            Location(46.518, 6.563),
        )

    val marker = RoomMarker(room, listOf(session("s1"), session("s2"), session("s3"))).toMapMarker()

    assertEquals(aab, marker)
  }
}
