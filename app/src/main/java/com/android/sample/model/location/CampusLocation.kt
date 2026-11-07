// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

/**
 * Represents a study location on campus with name, zone, type, floor, and geographic coordinates.
 *
 * @property name Specific room designation or location name.
 * @property zone Campus zone or building section identifier.
 * @property type Type or category of the location (e.g., "SALLE DE COURS", "AUDITOIRE", "LABO").
 * @property floor Floor level number if applicable, or null if unspecified.
 * @property location Geographic coordinates of the campus location.
 */
data class CampusLocation(
    val name: String,
    val zone: String,
    val type: String,
    val floor: Int?,
    val location: Location,
)
