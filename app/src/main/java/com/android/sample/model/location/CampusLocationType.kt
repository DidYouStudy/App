// Co-authored-by: Gemini AI Agent
package com.android.sample.model.location

/**
 * Categorization of campus study spaces and rooms.
 *
 * @property rawLabel Original raw string label corresponding to the campus location type.
 */
enum class CampusLocationType(val rawLabel: String) {
  SALLE_DE_COURS("SALLE DE COURS"),
  SALLE_TP("SALLE TP"),
  AUDITOIRE("AUDITOIRE"),
  CONFERENCES("CONFERENCES"),
  TUTORAT("TUTORAT"),
  CONF_MULTIMEDIA("CONF MULTIMEDIA"),
  BIBLIOTHEQUE("BIBLIOTHEQUE"),
  REUNION("REUNION"),
  LABO("LABO"),
  UNKNOWN("");

  companion object {
    /**
     * Converts a raw string label to a [CampusLocationType].
     *
     * Matches case-insensitively after trimming. Returns [UNKNOWN] for null, blank, or unrecognized
     * labels.
     */
    fun fromString(value: String?): CampusLocationType {
      if (value.isNullOrBlank()) return UNKNOWN
      val normalized = value.trim()
      return entries.firstOrNull {
        it.rawLabel.equals(normalized, ignoreCase = true) ||
            it.name.equals(normalized, ignoreCase = true)
      } ?: UNKNOWN
    }
  }
}
