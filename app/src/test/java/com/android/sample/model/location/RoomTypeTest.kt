// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [RoomType], its English display labels and its data label parsing. */
class RoomTypeTest {

  @Test
  fun fromDataLabel_everyLabelInTheCampusData_returnsItsType() {
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

    expected.forEach { (dataLabel, type) ->
      assertEquals(dataLabel, type, RoomType.fromDataLabel(dataLabel))
    }
    assertEquals(RoomType.entries.toSet(), expected.values.toSet())
  }

  @Test
  fun fromDataLabel_ignoresCaseAndSurroundingSpaces() {
    assertEquals(RoomType.LAB, RoomType.fromDataLabel("labo"))
    assertEquals(RoomType.LECTURE_ROOM, RoomType.fromDataLabel("  Salle de cours "))
  }

  @Test
  fun fromDataLabel_unknownOrBlankLabel_returnsNull() {
    assertNull(RoomType.fromDataLabel("BUREAU"))
    assertNull(RoomType.fromDataLabel(""))
    assertNull(RoomType.fromDataLabel("   "))
  }

  @Test
  fun fromDataLabel_englishDisplayLabel_isNotADataLabel() {
    assertNull(RoomType.fromDataLabel("Laboratory"))
  }

  @Test
  fun dataLabel_roundTripsThroughFromDataLabel() {
    RoomType.entries.forEach { assertEquals(it, RoomType.fromDataLabel(it.dataLabel)) }
  }

  @Test
  fun label_everyTypeHasADistinctEnglishDisplayLabel() {
    val labels = RoomType.entries.map { it.label }
    assertEquals(labels.size, labels.toSet().size)
    assertTrue(labels.all { it.isNotBlank() && it.first().isUpperCase() })
  }
}
