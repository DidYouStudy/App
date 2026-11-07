// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.location

/**
 * Category of a campus room.
 *
 * Each value has two labels:
 * - [label] is the English name meant for display. English is the default language for now, a
 *   translation layer (string resources) can map the enum later without changing this class.
 * - [dataLabel] is the raw French category written by EPFL in the bundled campus locations data.
 *   The data layer uses it with [fromDataLabel] to turn a raw label into a [RoomType].
 *
 * The set is closed: the bundled data only contains these categories.
 *
 * @property label English display name.
 * @property dataLabel Raw category label as written in the campus locations data.
 */
enum class RoomType(val label: String, val dataLabel: String) {
  /** Regular classroom. */
  LECTURE_ROOM("Lecture room", "SALLE DE COURS"),

  /** Room for practical work. */
  PRACTICAL_ROOM("Practical work room", "SALLE TP"),

  /** Large lecture hall. */
  AUDITORIUM("Auditorium", "AUDITOIRE"),

  /** Conference room. */
  CONFERENCE("Conference room", "CONFERENCES"),

  /** Room used for tutoring. */
  TUTORING("Tutoring room", "TUTORAT"),

  /** Conference room with multimedia equipment. */
  MULTIMEDIA_CONFERENCE("Multimedia conference room", "CONF MULTIMEDIA"),

  /** Library space. */
  LIBRARY("Library", "BIBLIOTHEQUE"),

  /** Meeting room. */
  MEETING("Meeting room", "REUNION"),

  /** Laboratory. Mostly research and teaching labs, not necessarily open to students. */
  LAB("Laboratory", "LABO");

  companion object {
    /**
     * Returns the [RoomType] whose [dataLabel] equals [dataLabel], ignoring case and surrounding
     * spaces, or `null` when no category has that label.
     */
    fun fromDataLabel(dataLabel: String): RoomType? {
      val wanted = dataLabel.trim()
      return entries.firstOrNull { it.dataLabel.equals(wanted, ignoreCase = true) }
    }
  }
}
