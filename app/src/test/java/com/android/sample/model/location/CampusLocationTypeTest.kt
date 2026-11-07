// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

import org.junit.Assert
import org.junit.Test

/** Unit tests for [CampusLocationType]. */
class CampusLocationTypeTest {

  @Test
  fun fromString_knownNames_returnsExpectedType() {
    Assert.assertEquals(
        CampusLocationType.LECTURE_ROOM,
        CampusLocationType.fromString("LECTURE_ROOM"),
    )
    Assert.assertEquals(
        CampusLocationType.PRACTICAL_ROOM,
        CampusLocationType.fromString("PRACTICAL_ROOM"),
    )
    Assert.assertEquals(
        CampusLocationType.AUDITORIUM,
        CampusLocationType.fromString("AUDITORIUM"),
    )
    Assert.assertEquals(
        CampusLocationType.CONFERENCE_ROOM,
        CampusLocationType.fromString("CONFERENCE_ROOM"),
    )
    Assert.assertEquals(
        CampusLocationType.TUTORING_ROOM,
        CampusLocationType.fromString("TUTORING_ROOM"),
    )
    Assert.assertEquals(
        CampusLocationType.MULTIMEDIA_CONFERENCE_ROOM,
        CampusLocationType.fromString("MULTIMEDIA_CONFERENCE_ROOM"),
    )
    Assert.assertEquals(
        CampusLocationType.LIBRARY,
        CampusLocationType.fromString("LIBRARY"),
    )
    Assert.assertEquals(
        CampusLocationType.MEETING_ROOM,
        CampusLocationType.fromString("MEETING_ROOM"),
    )
    Assert.assertEquals(
        CampusLocationType.LAB,
        CampusLocationType.fromString("LAB"),
    )
  }

  @Test
  fun fromString_caseInsensitiveAndWhitespace_returnsExpectedType() {
    Assert.assertEquals(
        CampusLocationType.LECTURE_ROOM,
        CampusLocationType.fromString("  lecture_room "),
    )
    Assert.assertEquals(
        CampusLocationType.LAB,
        CampusLocationType.fromString("lab"),
    )
    Assert.assertEquals(
        CampusLocationType.LIBRARY,
        CampusLocationType.fromString("Library"),
    )
  }

  @Test
  fun fromString_unknownOrBlankLabels_returnsUnknown() {
    Assert.assertEquals(
        CampusLocationType.UNKNOWN,
        CampusLocationType.fromString("NON_EXISTENT_TYPE"),
    )
    Assert.assertEquals(CampusLocationType.UNKNOWN, CampusLocationType.fromString(""))
    Assert.assertEquals(CampusLocationType.UNKNOWN, CampusLocationType.fromString("   "))
    Assert.assertEquals(CampusLocationType.UNKNOWN, CampusLocationType.fromString(null))
  }
}
