// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

/** Categorization of campus study spaces and rooms. */
enum class CampusLocationType {
  LECTURE_ROOM,
  PRACTICAL_ROOM,
  AUDITORIUM,
  CONFERENCE_ROOM,
  TUTORING_ROOM,
  MULTIMEDIA_CONFERENCE_ROOM,
  LIBRARY,
  MEETING_ROOM,
  LAB,
  UNKNOWN;

  companion object {
    /**
     * Converts a string name to a [CampusLocationType].
     *
     * Matches case-insensitively after trimming. Returns [UNKNOWN] for null, blank, or unrecognized
     * names.
     */
    fun fromString(value: String?): CampusLocationType {
      if (value.isNullOrBlank()) return UNKNOWN
      val normalized = value.trim()
      return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: UNKNOWN
    }
  }
}
