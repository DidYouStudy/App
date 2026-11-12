// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

import org.junit.Assert
import org.junit.Test

/** Unit tests for [DistanceCalculator] and domain location abstractions. */
class DistanceCalculatorTest {

  private val oneDegreeMeters = Math.PI * EARTH_RADIUS_METERS / 180.0
  private val halfCircumference = Math.PI * EARTH_RADIUS_METERS

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
  fun distanceMeters_epflBcToRolexLearningCenter_returnsExpectedDistance() {
    val bcBuilding = Location(latitude = 46.5186, longitude = 6.5619)
    val rolexCenter = Location(latitude = 46.5180, longitude = 6.5681)

    // Haversine formula with R = 6,371,000 m yields ~479.07 m
    val expectedDistanceMeters = 479.07
    val distance = DistanceCalculator.distanceMeters(bcBuilding, rolexCenter)
    Assert.assertEquals(expectedDistanceMeters, distance, 0.5)
  }

  @Test
  fun distanceMeters_equatorPoints_returnsExpectedDistance() {
    val pointA = Location(latitude = 0.0, longitude = 0.0)
    val pointB = Location(latitude = 0.0, longitude = 1.0)

    val distance = DistanceCalculator.distanceMeters(pointA, pointB)
    Assert.assertEquals(oneDegreeMeters, distance, 1.0)
  }

  @Test
  fun distanceMeters_poles_returnsExpectedDistance() {
    val northPole = Location(latitude = 90.0, longitude = 0.0)
    val southPole = Location(latitude = -90.0, longitude = 0.0)

    val distance = DistanceCalculator.distanceMeters(northPole, southPole)
    Assert.assertEquals(halfCircumference, distance, 1.0)
  }

  @Test
  fun distanceMeters_antipodalPoints_handlesWithoutNaN() {
    val pointA = Location(latitude = 0.0, longitude = 0.0)
    val pointB = Location(latitude = 0.0, longitude = 180.0)

    val distance = DistanceCalculator.distanceMeters(pointA, pointB)
    Assert.assertFalse(distance.isNaN())
    Assert.assertEquals(halfCircumference, distance, 1.0)
  }

  @Test
  fun distanceMeters_antimeridianCrossing_returnsCorrectDistance() {
    val pointEast = Location(latitude = 0.0, longitude = 179.9)
    val pointWest = Location(latitude = 0.0, longitude = -179.9)

    val distance = DistanceCalculator.distanceMeters(pointEast, pointWest)
    Assert.assertFalse(distance.isNaN())
    Assert.assertEquals(0.2 * oneDegreeMeters, distance, 1.0)
  }

  @Test
  fun distanceMeters_isSymmetric_returnsSameDistanceBothWays() {
    val pointA = Location(latitude = 46.5186, longitude = 6.5619)
    val pointB = Location(latitude = 46.5180, longitude = 6.5681)

    val ab = DistanceCalculator.distanceMeters(pointA, pointB)
    val ba = DistanceCalculator.distanceMeters(pointB, pointA)

    Assert.assertEquals(ab, ba, 1e-5)
  }

  @Test
  fun distanceMeters_shortDistance_returnsExpectedDistance() {
    val pointA = Location(latitude = 0.0, longitude = 0.0)
    val pointB = Location(latitude = 0.0, longitude = 0.0001)

    val distance = DistanceCalculator.distanceMeters(pointA, pointB)
    val expected = 0.0001 * oneDegreeMeters
    Assert.assertEquals(expected, distance, 0.1)
  }

  @Test
  fun location_boundaryCoordinates_areAccepted() {
    val northPole = Location(latitude = 90.0, longitude = 0.0)
    val southPole = Location(latitude = -90.0, longitude = 0.0)
    val antimeridianEast = Location(latitude = 0.0, longitude = 180.0)
    val antimeridianWest = Location(latitude = 0.0, longitude = -180.0)

    Assert.assertEquals(90.0, northPole.latitude, 1e-5)
    Assert.assertEquals(-90.0, southPole.latitude, 1e-5)
    Assert.assertEquals(180.0, antimeridianEast.longitude, 1e-5)
    Assert.assertEquals(-180.0, antimeridianWest.longitude, 1e-5)
  }

  @Test(expected = IllegalArgumentException::class)
  fun location_latitudeExceedsMax_throwsIllegalArgumentException() {
    Location(latitude = 90.0001, longitude = 0.0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun location_latitudeBelowMin_throwsIllegalArgumentException() {
    Location(latitude = -90.0001, longitude = 0.0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun location_longitudeExceedsMax_throwsIllegalArgumentException() {
    Location(latitude = 0.0, longitude = 180.0001)
  }

  @Test(expected = IllegalArgumentException::class)
  fun location_longitudeBelowMin_throwsIllegalArgumentException() {
    Location(latitude = 0.0, longitude = -180.0001)
  }

  @Test(expected = IllegalArgumentException::class)
  fun location_latitudeNaN_throwsIllegalArgumentException() {
    Location(latitude = Double.NaN, longitude = 0.0)
  }

  @Test(expected = IllegalArgumentException::class)
  fun location_longitudeNaN_throwsIllegalArgumentException() {
    Location(latitude = 0.0, longitude = Double.NaN)
  }

  companion object {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
  }
}
