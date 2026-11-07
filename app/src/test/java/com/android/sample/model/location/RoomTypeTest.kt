// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [RoomType] and its label parsing. */
class RoomTypeTest {

  @Test
  fun fromLabel_everyLabelInTheCampusData_returnsItsType() {
    val expected =
        mapOf(
            "SALLE DE COURS" to RoomType.LECTURE_ROOM,
            "SALLE TP" to RoomType.PRACTICAL_ROOM,
            "AUDITOIRE" to RoomType.AUDITORIUM,
            "CONFERENCES" to RoomType.CONFERENCE,
            "TUTORAT" to RoomType.TUTORING,
            "CONF MULTIMEDIA" to RoomType.MULTIMEDIA_CONFERENCE,
            "BIBLIOTHEQUE" to RoomType.LIBRARY,
            "REUNION" to RoomType.MEETING,
            "LABO" to RoomType.LAB,
        )

    expected.forEach { (label, type) -> assertEquals(label, type, RoomType.fromLabel(label)) }
    assertEquals(RoomType.entries.toSet(), expected.values.toSet())
  }

  @Test
  fun fromLabel_ignoresCaseAndSurroundingSpaces() {
    assertEquals(RoomType.LAB, RoomType.fromLabel("labo"))
    assertEquals(RoomType.LECTURE_ROOM, RoomType.fromLabel("  Salle de cours "))
  }

  @Test
  fun fromLabel_unknownOrBlankLabel_returnsNull() {
    assertNull(RoomType.fromLabel("BUREAU"))
    assertNull(RoomType.fromLabel(""))
    assertNull(RoomType.fromLabel("   "))
  }

  @Test
  fun label_roundTripsThroughFromLabel() {
    RoomType.entries.forEach { assertEquals(it, RoomType.fromLabel(it.label)) }
  }
}
