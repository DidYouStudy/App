// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

import org.junit.Assert
import org.junit.Test

/** Unit tests for [CampusLocation]. */
class CampusLocationTest {

  @Test
  fun campusLocation_dataClass_holdsPropertiesCorrectly() {
    val loc = Location(latitude = 46.5206096, longitude = 6.5674949)
    val campusLoc =
        CampusLocation(
            name = "CM 1 1",
            zone = "CM",
            type = CampusLocationType.AUDITORIUM,
            floor = 1,
            location = loc,
        )

    Assert.assertEquals("CM 1 1", campusLoc.name)
    Assert.assertEquals("CM", campusLoc.zone)
    Assert.assertEquals(CampusLocationType.AUDITORIUM, campusLoc.type)
    Assert.assertEquals(1, campusLoc.floor)
    Assert.assertEquals(loc, campusLoc.location)
  }

  @Test
  fun campusLocation_nullFloor_holdsPropertiesCorrectly() {
    val loc = Location(latitude = 46.5209133, longitude = 6.5629821)
    val campusLoc =
        CampusLocation(
            name = "AAC 0 31",
            zone = "AAC",
            type = CampusLocationType.PRACTICAL_ROOM,
            floor = null,
            location = loc,
        )

    Assert.assertEquals("AAC 0 31", campusLoc.name)
    Assert.assertEquals("AAC", campusLoc.zone)
    Assert.assertEquals(CampusLocationType.PRACTICAL_ROOM, campusLoc.type)
    Assert.assertNull(campusLoc.floor)
    Assert.assertEquals(loc, campusLoc.location)
  }

  @Test
  fun campusLocation_copyAndEquality_behaveCorrectly() {
    val loc = Location(latitude = 46.5186, longitude = 6.5619)
    val loc1 =
        CampusLocation(
            name = "BC 01",
            zone = "BC Building",
            type = CampusLocationType.LECTURE_ROOM,
            floor = 1,
            location = loc,
        )
    val loc2 = loc1.copy(floor = 2)
    val loc3 =
        CampusLocation(
            name = "BC 01",
            zone = "BC Building",
            type = CampusLocationType.LECTURE_ROOM,
            floor = 1,
            location = loc,
        )

    Assert.assertEquals(loc1, loc3)
    Assert.assertEquals(loc1.hashCode(), loc3.hashCode())
    Assert.assertNotEquals(loc1, loc2)
    Assert.assertEquals(2, loc2.floor)
  }
}
