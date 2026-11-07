// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

import org.junit.Assert
import org.junit.Test

/** Unit tests for [DistanceCalculator] and domain location abstractions. */
class DistanceCalculatorTest {

  @Test
  fun distanceMeters_identicalCoordinates_returnsZero() {
    val location = Location(latitude = 46.5186, longitude = 6.5619)
    val distance = DistanceCalculator.distanceMeters(location, location)
    Assert.assertEquals(0.0, distance, 1e-5)
  }

  @Test
  fun haversine_identicalCoordinates_returnsZero() {
    val distance = DistanceCalculator.haversine(46.5186, 6.5619, 46.5186, 6.5619)
    Assert.assertEquals(0.0, distance, 1e-5)
  }

  @Test
  fun distanceMeters_epflBcToRolexLearningCenter_returnsApprox478Meters() {
    val bcBuilding = Location(latitude = 46.5186, longitude = 6.5619)
    val rolexCenter = Location(latitude = 46.5180, longitude = 6.5681)

    val distance = DistanceCalculator.distanceMeters(bcBuilding, rolexCenter)
    Assert.assertEquals(478.0, distance, 5.0)
  }

  @Test
  fun distanceMeters_equatorPoints_returnsExpectedDistance() {
    val pointA = Location(latitude = 0.0, longitude = 0.0)
    val pointB = Location(latitude = 0.0, longitude = 1.0)

    val distance = DistanceCalculator.distanceMeters(pointA, pointB)
    // 1 degree longitude at equator is approximately 111,195 meters
    Assert.assertEquals(111195.0, distance, 500.0)
  }

  @Test
  fun distanceMeters_poles_returnsExpectedDistance() {
    val northPole = Location(latitude = 90.0, longitude = 0.0)
    val southPole = Location(latitude = -90.0, longitude = 0.0)

    val distance = DistanceCalculator.distanceMeters(northPole, southPole)
    // Half of Earth's circumference (~20,015,087 meters)
    Assert.assertEquals(20015087.0, distance, 1000.0)
  }

  @Test
  fun distanceMeters_antipodalPoints_handlesWithoutNaN() {
    val pointA = Location(latitude = 0.0, longitude = 0.0)
    val pointB = Location(latitude = 0.0, longitude = 180.0)

    val distance = DistanceCalculator.distanceMeters(pointA, pointB)
    Assert.assertFalse(distance.isNaN())
    Assert.assertEquals(20015087.0, distance, 1000.0)
  }

  @Test
  fun distanceMeters_antimeridianCrossing_returnsCorrectDistance() {
    val pointEast = Location(latitude = 0.0, longitude = 179.9)
    val pointWest = Location(latitude = 0.0, longitude = -179.9)

    val distance = DistanceCalculator.distanceMeters(pointEast, pointWest)
    // 0.2 degrees across the antimeridian at the equator (~22,239 meters)
    Assert.assertFalse(distance.isNaN())
    Assert.assertEquals(22239.0, distance, 100.0)
  }

  @Test
  fun campusLocation_dataClass_holdsPropertiesCorrectly() {
    val loc = Location(latitude = 46.5186, longitude = 6.5619)
    val campusLoc =
        CampusLocation(
            name = "BC 01",
            zone = "BC Building",
            type = "Study Room",
            floor = 1,
            location = loc,
        )

    Assert.assertEquals("BC 01", campusLoc.name)
    Assert.assertEquals("BC Building", campusLoc.zone)
    Assert.assertEquals("Study Room", campusLoc.type)
    Assert.assertEquals(1, campusLoc.floor)
    Assert.assertEquals(loc, campusLoc.location)
  }

  @Test
  fun campusLocation_nullFloor_holdsPropertiesCorrectly() {
    val loc = Location(latitude = 46.5186, longitude = 6.5619)
    val campusLoc =
        CampusLocation(
            name = "Rolex Center",
            zone = "RLC",
            type = "Library",
            floor = null,
            location = loc,
        )

    Assert.assertEquals("Rolex Center", campusLoc.name)
    Assert.assertEquals("RLC", campusLoc.zone)
    Assert.assertEquals("Library", campusLoc.type)
    Assert.assertNull(campusLoc.floor)
    Assert.assertEquals(loc, campusLoc.location)
  }
}
