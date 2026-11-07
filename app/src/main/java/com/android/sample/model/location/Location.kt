// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

/**
 * Represents geographic coordinates on the Earth's surface.
 *
 * @property latitude Geographic latitude in degrees, ranging from -90.0 to 90.0.
 * @property longitude Geographic longitude in degrees, ranging from -180.0 to 180.0.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
) {
  init {
    require(latitude in -90.0..90.0) { "Latitude out of range: $latitude" }
    require(longitude in -180.0..180.0) { "Longitude out of range: $longitude" }
  }
}
