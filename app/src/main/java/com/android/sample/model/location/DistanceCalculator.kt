package com.android.sample.model.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure Kotlin utility for computing geographic distances without Android SDK dependencies. */
object DistanceCalculator {
  private const val EARTH_RADIUS_METERS = 6_371_000.0

  /**
   * Returns distance in meters between two coordinates specified by latitude and longitude using
   * the Haversine formula.
   *
   * @param lat1 Latitude of first point in degrees.
   * @param lon1 Longitude of first point in degrees.
   * @param lat2 Latitude of second point in degrees.
   * @param lon2 Longitude of second point in degrees.
   * @return Distance in meters.
   */
  fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val rLat1 = Math.toRadians(lat1)
    val rLat2 = Math.toRadians(lat2)

    val a = sin(dLat / 2.0).pow(2.0) + cos(rLat1) * cos(rLat2) * sin(dLon / 2.0).pow(2.0)
    val clampedA = a.coerceIn(0.0, 1.0)
    val c = 2.0 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
    return EARTH_RADIUS_METERS * c
  }

  /**
   * Returns distance in meters between two [Location] coordinates using the Haversine formula.
   *
   * @param from Origin location.
   * @param to Destination location.
   * @return Distance in meters.
   */
  fun distanceMeters(from: Location, to: Location): Double =
      haversine(from.latitude, from.longitude, to.latitude, to.longitude)
}
