package com.android.sample.model.preferences

enum class TimeOfDay(val displayName: String) {
    MORNING("Morning"),
    AFTERNOON("Afternoon"),
    EVENING("Evening"),
    NIGHT("Night")
}

object PreferenceDefaults {
    val SESSION_DURATIONS_MINUTES = listOf(25, 45, 60, 90) //can have multiple durations
    val BREAK_DURATIONS_MINUTES = listOf(5, 10, 15, 20)
    val BREAK_FREQUENCIES_MINUTES = listOf(30, 45, 60)

    const val DEFAULT_SESSION_MINUTES = 45
    const val DEFAULT_BREAK_MINUTES = 10
    const val DEFAULT_FREQUENCY_MINUTES = 45
    val DEFAULT_TIMES = setOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON)
}

data class UserPreferences(
    val userId: String = "",
    val preferredStudyTimes: Set<TimeOfDay> = PreferenceDefaults.DEFAULT_TIMES, //can have multiple times
    val preferredLocations: List<String> = emptyList(),
    val sessionLengthMinutes: Int = PreferenceDefaults.DEFAULT_SESSION_MINUTES,
    val breakLengthMinutes: Int = PreferenceDefaults.DEFAULT_BREAK_MINUTES,
    val breakFrequencyMinutes: Int = PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES
)
