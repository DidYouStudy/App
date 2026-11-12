// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import com.android.sample.model.location.SessionPlaceResolver
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [groupByRoom] and [RoomSessions]. */
class RoomSessionsTest {

  private val start = Instant.parse("2030-01-01T10:00:00Z")

  private fun room(name: String) =
      CampusLocation(
          name = name,
          zone = name.substringBefore(' '),
          type = CampusLocationType.LECTURE_ROOM,
          floor = 0,
          location = Location(46.52, 6.56),
      )

  private fun item(id: String, location: String?) =
      GroupSessionItem(
          session =
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
              ),
          isMember = false,
          isCreator = false,
      )

  private val aab = room("AAB 0 26")
  private val cm = room("CM 1 121")
  private val resolver = SessionPlaceResolver(listOf(aab, cm))

  @Test
  fun groupByRoom_noItems_returnsNothing() {
    assertTrue(groupByRoom(emptyList(), resolver).isEmpty())
  }

  @Test
  fun groupByRoom_oneItem_returnsItsRoom() {
    val i = item("s1", "AAB 0 26")

    assertEquals(listOf(RoomSessions(aab, listOf(i))), groupByRoom(listOf(i), resolver))
  }

  @Test
  fun groupByRoom_sameRoom_sharesOneEntry() {
    val i1 = item("s1", "AAB 0 26")
    val i2 = item("s2", "aab026")

    val result = groupByRoom(listOf(i1, i2), resolver)

    assertEquals(listOf(RoomSessions(aab, listOf(i1, i2))), result)
  }

  @Test
  fun groupByRoom_differentRooms_returnsOneEntryEach() {
    val i1 = item("s1", "AAB 0 26")
    val i2 = item("s2", "CM 1 121")

    val result = groupByRoom(listOf(i1, i2), resolver)

    assertEquals(listOf(aab, cm), result.map { it.room })
    assertEquals(listOf(listOf(i1), listOf(i2)), result.map { it.items })
  }

  @Test
  fun groupByRoom_entriesFollowFirstAppearanceOrder() {
    val i1 = item("s1", "CM 1 121")
    val i2 = item("s2", "AAB 0 26")
    val i3 = item("s3", "CM 1 121")

    val result = groupByRoom(listOf(i1, i2, i3), resolver)

    assertEquals(listOf(cm, aab), result.map { it.room })
    assertEquals(listOf(i1, i3), result[0].items)
  }

  @Test
  fun groupByRoom_noLocation_isLeftOut() {
    assertTrue(groupByRoom(listOf(item("s1", null)), resolver).isEmpty())
  }

  @Test
  fun groupByRoom_unknownPlace_isLeftOut() {
    assertTrue(groupByRoom(listOf(item("s1", "Rolex café")), resolver).isEmpty())
  }

  @Test
  fun groupByRoom_mixedItems_keepsOnlyThePlacedOnes() {
    val placed = item("s1", "CM 1 121")

    val result = groupByRoom(listOf(item("s0", null), placed, item("s2", "somewhere")), resolver)

    assertEquals(listOf(RoomSessions(cm, listOf(placed))), result)
  }

  @Test
  fun roomSessions_withoutItems_isRejected() {
    assertThrows(IllegalArgumentException::class.java) { RoomSessions(aab, emptyList()) }
  }
}
