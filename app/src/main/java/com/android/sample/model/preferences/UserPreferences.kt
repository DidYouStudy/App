// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

/**
 * Represents the preferred time of day for study sessions.
 *
 * @property displayName Human-readable label for UI display.
 */
enum class TimeOfDay(val displayName: String) {
  MORNING("Morning"),
  AFTERNOON("Afternoon"),
  EVENING("Evening"),
  NIGHT("Night"),
}

/** Default options and fallback values for user preferences. */
object PreferenceDefaults {
  val SESSION_DURATIONS_MINUTES = listOf(30, 60, 120, 180) // can have multiple durations
  val BREAK_DURATIONS_MINUTES = listOf(5, 10, 15, 20)
  val BREAK_FREQUENCIES_MINUTES = listOf(30, 45, 60)

  const val DEFAULT_SESSION_MINUTES = 60
  const val DEFAULT_BREAK_MINUTES = 10
  const val DEFAULT_FREQUENCY_MINUTES = 45
  val DEFAULT_TIMES = listOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON)
}

/**
 * Data class holding a user's study environment and session length preferences.
 *
 * @property userId The unique ID of the user owning these preferences.
 * @property preferredStudyTimes List of preferred times of day for studying.
 * @property preferredLocations List of preferred physical locations (e.g., "Library", "Home").
 * @property sessionLengthMinutes Target length of a single study session in minutes.
 * @property breakLengthMinutes Duration of break periods in minutes.
 * @property breakFrequencyMinutes Frequency of breaks during study sessions in minutes.
 */
data class UserPreferences(
    val userId: String = "",
    val preferredStudyTimes: List<TimeOfDay> =
        PreferenceDefaults.DEFAULT_TIMES, // can have multiple times
    val preferredLocations: List<String> = emptyList(),
    val sessionLengthMinutes: Int = PreferenceDefaults.DEFAULT_SESSION_MINUTES,
    val breakLengthMinutes: Int = PreferenceDefaults.DEFAULT_BREAK_MINUTES,
    val breakFrequencyMinutes: Int = PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES,
)
