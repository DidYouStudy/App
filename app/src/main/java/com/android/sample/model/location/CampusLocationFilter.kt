// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

/**
 * Criteria for searching campus locations. Every criterion is optional, and a location must match
 * all the criteria that are set (logical AND). A filter with nothing set matches every location.
 *
 * The UI state of a search screen maps directly onto this class: the text in the input box, the
 * selected building and the selected room categories.
 *
 * @property query Text typed by the user. A location matches when its name contains the text,
 *   ignoring case and spaces, so "cm1121" finds "CM 1 121". `null` or blank means no text filter. A
 *   match can be partial, so the first search result is not always the exact room.
 * @property zone Building code, for example "CM". A location matches when its zone equals it,
 *   ignoring case and surrounding spaces. `null` or blank means any building.
 * @property types Room categories to keep. A location matches when its type is one of them. An
 *   empty set means any category, so to hide laboratories pass every type except
 *   [CampusLocationType.LAB]. [CampusLocationType.UNKNOWN] is only the fallback for unrecognized
 *   labels, not a real category: leave it out of pickers. Display names are the UI's job (string
 *   resources), not this class's.
 */
data class CampusLocationFilter(
    val query: String? = null,
    val zone: String? = null,
    val types: Set<CampusLocationType> = emptySet(),
)
