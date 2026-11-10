// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [groupSessionsByRoom] and [RoomMarker]. */
class SessionMarkersTest {

  private val start = Instant.parse("2030-01-01T10:00:00Z")

  private fun room(name: String) =
      CampusLocation(
          name = name,
          zone = name.substringBefore(' '),
          type = CampusLocationType.LECTURE_ROOM,
          floor = 0,
          location = Location(46.52, 6.56),
      )

  private fun session(id: String, location: String?) =
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
                  location = location,
              ),
          createdAt = start,
      )

  private val aab = room("AAB 0 26")
  private val cm = room("CM 1 121")
  private val resolver = SessionPlaceResolver(listOf(aab, cm))

  @Test
  fun groupSessionsByRoom_noSessions_returnsNothing() {
    val result = groupSessionsByRoom(emptyList(), resolver)

    assertTrue(result.markers.isEmpty())
    assertEquals(0, result.unplacedCount)
  }

  @Test
  fun groupSessionsByRoom_oneSession_returnsOneMarker() {
    val s = session("s1", "AAB 0 26")

    val result = groupSessionsByRoom(listOf(s), resolver)

    assertEquals(listOf(RoomMarker(aab, listOf(s))), result.markers)
    assertEquals(0, result.unplacedCount)
  }

  @Test
  fun groupSessionsByRoom_sameRoom_sharesOneMarkerWithCount() {
    val s1 = session("s1", "AAB 0 26")
    val s2 = session("s2", "aab026")

    val result = groupSessionsByRoom(listOf(s1, s2), resolver)

    assertEquals(1, result.markers.size)
    assertEquals(aab, result.markers[0].room)
    assertEquals(listOf(s1, s2), result.markers[0].sessions)
    assertEquals(2, result.markers[0].count)
  }

  @Test
  fun groupSessionsByRoom_differentRooms_returnsOneMarkerEach() {
    val s1 = session("s1", "AAB 0 26")
    val s2 = session("s2", "CM 1 121")

    val result = groupSessionsByRoom(listOf(s1, s2), resolver)

    assertEquals(listOf(aab, cm), result.markers.map { it.room })
    assertEquals(listOf(1, 1), result.markers.map { it.count })
  }

  @Test
  fun groupSessionsByRoom_markersFollowFirstAppearanceOrder() {
    val s1 = session("s1", "CM 1 121")
    val s2 = session("s2", "AAB 0 26")
    val s3 = session("s3", "CM 1 121")

    val result = groupSessionsByRoom(listOf(s1, s2, s3), resolver)

    assertEquals(listOf(cm, aab), result.markers.map { it.room })
    assertEquals(listOf(s1, s3), result.markers[0].sessions)
  }

  @Test
  fun groupSessionsByRoom_noLocation_isCountedAsUnplaced() {
    val result = groupSessionsByRoom(listOf(session("s1", null)), resolver)

    assertTrue(result.markers.isEmpty())
    assertEquals(1, result.unplacedCount)
  }

  @Test
  fun groupSessionsByRoom_unknownPlace_isCountedAsUnplaced() {
    val result = groupSessionsByRoom(listOf(session("s1", "Rolex café")), resolver)

    assertTrue(result.markers.isEmpty())
    assertEquals(1, result.unplacedCount)
  }

  @Test
  fun groupSessionsByRoom_mixedSessions_splitsPlacedAndUnplaced() {
    val placed = session("s1", "CM 1 121")

    val result =
        groupSessionsByRoom(
            listOf(session("s0", null), placed, session("s2", "somewhere")),
            resolver,
        )

    assertEquals(listOf(RoomMarker(cm, listOf(placed))), result.markers)
    assertEquals(2, result.unplacedCount)
  }

  @Test
  fun roomMarker_withoutSessions_isRejected() {
    assertThrows(IllegalArgumentException::class.java) { RoomMarker(aab, emptyList()) }
  }
}
