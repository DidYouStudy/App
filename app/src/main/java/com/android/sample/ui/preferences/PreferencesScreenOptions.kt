// Co-authored-by: Claude AI Agent
package com.android.sample.ui.preferences

import com.android.sample.model.preferences.PreferenceDefaults
import com.android.sample.model.preferences.TimeOfDay
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags

/*
   Interface for representing all preferences
*/

interface PreferenceOption {
  val label: String
  val testTag: String
}

/*
   Enum Classes for representing the available preferences
*/

enum class StudyTime(
    val timeOfDay: TimeOfDay,
    override val testTag: String,
) : PreferenceOption {
  MORNING(TimeOfDay.MORNING, Tags.STUDY_TIME_MORNING),
  AFTERNOON(TimeOfDay.AFTERNOON, Tags.STUDY_TIME_AFTERNOON),
  EVENING(TimeOfDay.EVENING, Tags.STUDY_TIME_EVENING),
  NIGHT(TimeOfDay.NIGHT, Tags.STUDY_TIME_NIGHT);

  // Reuse the label from your domain enum instead of duplicating it
  override val label: String
    get() = timeOfDay.displayName

  companion object {
    /**
     * Returns the study-time option associated with the given time of day.
     *
     * @param timeOfDay Domain time-of-day value to look up.
     * @return The matching study-time option.
     */
    fun from(timeOfDay: TimeOfDay): StudyTime = entries.first { it.timeOfDay == timeOfDay }
  }
}

enum class SessionLength(
    val minutes: Int,
    override val label: String,
    override val testTag: String,
) : PreferenceOption {
  THIRTY_MIN(30, "30 min", Tags.SESSION_LENGTH_30_MIN),
  ONE_HOUR(60, "1 hour", Tags.SESSION_LENGTH_1_HOUR),
  TWO_HOURS(120, "2 hours", Tags.SESSION_LENGTH_2_HOURS),
  THREE_HOURS(180, "3 hours", Tags.SESSION_LENGTH_3_HOURS);

  companion object {
    /**
     * Returns the session-length option for the given number of minutes.
     *
     * If the value does not match an option, returns the configured default session length.
     *
     * @param minutes Session length in minutes.
     * @return The matching option or the configured default.
     */
    fun fromMinutes(minutes: Int): SessionLength =
        entries.firstOrNull { it.minutes == minutes }
            ?: entries.first { it.minutes == PreferenceDefaults.DEFAULT_SESSION_MINUTES }
  }
}

enum class BreakDuration(
    val minutes: Int,
    override val label: String,
    override val testTag: String,
) : PreferenceOption {
  FIVE_MIN(5, "5 minutes", Tags.BREAK_DURATION_5_MIN),
  TEN_MIN(10, "10 minutes", Tags.BREAK_DURATION_10_MIN),
  FIFTEEN_MIN(15, "15 minutes", Tags.BREAK_DURATION_15_MIN),
  TWENTY_MIN(20, "20 minutes", Tags.BREAK_DURATION_20_MIN);

  companion object {
    /**
     * Returns the break-duration option for the given number of minutes.
     *
     * If the value does not match an option, returns the configured default break duration.
     *
     * @param minutes Break duration in minutes.
     * @return The matching option or the configured default.
     */
    fun fromMinutes(minutes: Int): BreakDuration =
        entries.firstOrNull { it.minutes == minutes }
            ?: entries.first { it.minutes == PreferenceDefaults.DEFAULT_BREAK_MINUTES }
  }
}

enum class BreakFrequency(
    val minutes: Int,
    override val label: String,
    override val testTag: String,
) : PreferenceOption {
  EVERY_30_MIN(30, "Every 30 min", Tags.BREAK_FREQUENCY_30_MIN),
  EVERY_45_MIN(45, "Every 45 min", Tags.BREAK_FREQUENCY_45_MIN),
  EVERY_HOUR(60, "Every hour", Tags.BREAK_FREQUENCY_1_HOUR);

  companion object {
    /**
     * Returns the break-frequency option for the given interval in minutes.
     *
     * If the value does not match an option, returns the configured default break frequency.
     *
     * @param minutes Break interval in minutes.
     * @return The matching option or the configured default.
     */
    fun fromMinutes(minutes: Int): BreakFrequency =
        entries.firstOrNull { it.minutes == minutes }
            ?: entries.first { it.minutes == PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES }
  }
}
