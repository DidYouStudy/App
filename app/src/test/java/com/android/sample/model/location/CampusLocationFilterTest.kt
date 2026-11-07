// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the contract of [CampusLocationFilter]: what an unset filter means, and how a
 * screen can update it as its state changes.
 */
class CampusLocationFilterTest {

  @Test
  fun defaultFilter_hasNoCriteria() {
    val filter = CampusLocationFilter()

    assertNull(filter.query)
    assertNull(filter.zone)
    assertTrue(filter.types.isEmpty())
  }

  @Test
  fun equalFilters_areEqual_soUnchangedScreenStateCanBeDetected() {
    val a =
        CampusLocationFilter(
            query = "cm",
            zone = "CM",
            types = setOf(CampusLocationType.LIBRARY),
        )
    val b =
        CampusLocationFilter(
            query = "cm",
            zone = "CM",
            types = setOf(CampusLocationType.LIBRARY),
        )

    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
  }

  @Test
  fun copy_changesOnlyTheGivenCriterion() {
    val filter = CampusLocationFilter(query = "cm", types = setOf(CampusLocationType.LAB))

    val withZone = filter.copy(zone = "CM")

    assertEquals("cm", withZone.query)
    assertEquals("CM", withZone.zone)
    assertEquals(setOf(CampusLocationType.LAB), withZone.types)
    assertNotEquals(filter, withZone)
  }

  @Test
  fun types_everyRealTypeExceptLab_expressesHidingLaboratories() {
    val withoutLabs =
        CampusLocationFilter(
            types =
                CampusLocationType.entries.toSet() -
                    CampusLocationType.LAB -
                    CampusLocationType.UNKNOWN
        )

    assertTrue(CampusLocationType.LAB !in withoutLabs.types)
    assertTrue(CampusLocationType.LECTURE_ROOM in withoutLabs.types)
    // The 9 real categories minus LAB.
    assertEquals(8, withoutLabs.types.size)
  }
}
