// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {

  @Test
  fun defaultUserPreferences_hasExpectedDefaults() {
    val preferences = UserPreferences()

    assertEquals("", preferences.userId)
    assertEquals(PreferenceDefaults.DEFAULT_TIMES, preferences.preferredStudyTimes)
    assertTrue(preferences.preferredLocations.isEmpty())
    assertEquals(PreferenceDefaults.DEFAULT_SESSION_MINUTES, preferences.sessionLengthMinutes)
    assertEquals(PreferenceDefaults.DEFAULT_BREAK_MINUTES, preferences.breakLengthMinutes)
    assertEquals(PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES, preferences.breakFrequencyMinutes)
  }

  @Test
  fun customUserPreferences_storesCorrectValues() {
    val times = setOf(TimeOfDay.EVENING, TimeOfDay.NIGHT)
    val locations = listOf("Library", "Home")
    val preferences =
        UserPreferences(
            userId = "user123",
            preferredStudyTimes = times,
            preferredLocations = locations,
            sessionLengthMinutes = 60,
            breakLengthMinutes = 15,
            breakFrequencyMinutes = 30,
        )

    assertEquals("user123", preferences.userId)
    assertEquals(times, preferences.preferredStudyTimes)
    assertEquals(locations, preferences.preferredLocations)
    assertEquals(60, preferences.sessionLengthMinutes)
    assertEquals(15, preferences.breakLengthMinutes)
    assertEquals(30, preferences.breakFrequencyMinutes)
  }

  @Test
  fun userPreferences_copyMethodWorksCorrectly() {
    val initial = UserPreferences(userId = "user1")
    val updated = initial.copy(sessionLengthMinutes = 120)

    assertEquals("user1", updated.userId)
    assertEquals(120, updated.sessionLengthMinutes)
    assertEquals(initial.breakLengthMinutes, updated.breakLengthMinutes)
  }

  @Test
  fun timeOfDayEnum_hasCorrectDisplayNames() {
    assertEquals("Morning", TimeOfDay.MORNING.displayName)
    assertEquals("Afternoon", TimeOfDay.AFTERNOON.displayName)
    assertEquals("Evening", TimeOfDay.EVENING.displayName)
    assertEquals("Night", TimeOfDay.NIGHT.displayName)
  }

  @Test
  fun preferenceDefaults_containsExpectedValues() {
    assertEquals(listOf(30, 60, 120, 180), PreferenceDefaults.SESSION_DURATIONS_MINUTES)
    assertEquals(listOf(5, 10, 15, 20), PreferenceDefaults.BREAK_DURATIONS_MINUTES)
    assertEquals(listOf(30, 45, 60), PreferenceDefaults.BREAK_FREQUENCIES_MINUTES)
    assertEquals(60, PreferenceDefaults.DEFAULT_SESSION_MINUTES)
    assertEquals(10, PreferenceDefaults.DEFAULT_BREAK_MINUTES)
    assertEquals(45, PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES)
    assertEquals(
        setOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON),
        PreferenceDefaults.DEFAULT_TIMES,
    )
  }
}
