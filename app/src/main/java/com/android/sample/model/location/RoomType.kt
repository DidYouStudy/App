// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

/**
 * Category of a campus room.
 *
 * Each value keeps the [label] used by EPFL in the bundled campus locations data, so the data layer
 * can turn a raw label into a [RoomType] with [fromLabel]. The set is closed: the bundled data only
 * contains these categories.
 *
 * @property label The raw category label as written in the campus locations data.
 */
enum class RoomType(val label: String) {
  /** Regular classroom ("SALLE DE COURS"). */
  LECTURE_ROOM("SALLE DE COURS"),

  /** Room for practical work ("SALLE TP"). */
  PRACTICAL_ROOM("SALLE TP"),

  /** Large lecture hall ("AUDITOIRE"). */
  AUDITORIUM("AUDITOIRE"),

  /** Conference room ("CONFERENCES"). */
  CONFERENCE("CONFERENCES"),

  /** Room used for tutoring ("TUTORAT"). */
  TUTORING("TUTORAT"),

  /** Conference room with multimedia equipment ("CONF MULTIMEDIA"). */
  MULTIMEDIA_CONFERENCE("CONF MULTIMEDIA"),

  /** Library space ("BIBLIOTHEQUE"). */
  LIBRARY("BIBLIOTHEQUE"),

  /** Meeting room ("REUNION"). */
  MEETING("REUNION"),

  /** Laboratory ("LABO"). Mostly research and teaching labs, not necessarily open to students. */
  LAB("LABO");

  companion object {
    /**
     * Returns the [RoomType] whose [label] equals [label], ignoring case and surrounding spaces, or
     * `null` when no category has that label.
     */
    fun fromLabel(label: String): RoomType? {
      val wanted = label.trim()
      return entries.firstOrNull { it.label.equals(wanted, ignoreCase = true) }
    }
  }
}
