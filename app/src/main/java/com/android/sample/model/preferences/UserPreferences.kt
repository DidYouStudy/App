package com.android.sample.model.preferences

data class UserPreferences(
    val userId: String = "",
    val preferredStudyTimes: List<String> = emptyList(), // e.g., ["MORNING", "EVENING"]
    val preferredLocations: List<String> = emptyList(), // e.g., ["Library", "Home"]
    val sessionLengthMinutes: Int = 45,
    val breakLengthMinutes: Int = 10,
    val breakFrequencyMinutes: Int = 45,
)
