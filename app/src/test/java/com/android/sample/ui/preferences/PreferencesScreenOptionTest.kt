// Co-authored-by: Claude AI Agent
package com.android.sample.ui.preferences

import com.android.sample.model.preferences.PreferenceDefaults
import com.android.sample.model.preferences.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesScreenOptionTest {

    @Test
    fun studyTimeLabelsComeFromTheDomainEnum() {
        StudyTime.entries.forEach { assertEquals(it.timeOfDay.displayName, it.label) }
    }

    @Test
    fun studyTimeFromMapsEveryTimeOfDay() {
        TimeOfDay.entries.forEach { assertEquals(it, StudyTime.from(it).timeOfDay) }
        assertEquals(StudyTime.MORNING, StudyTime.from(TimeOfDay.MORNING))
        assertEquals(StudyTime.AFTERNOON, StudyTime.from(TimeOfDay.AFTERNOON))
        assertEquals(StudyTime.EVENING, StudyTime.from(TimeOfDay.EVENING))
        assertEquals(StudyTime.NIGHT, StudyTime.from(TimeOfDay.NIGHT))
    }

    @Test
    fun sessionLengthFromMinutesReturnsMatchingOption() {
        SessionLength.entries.forEach { assertEquals(it, SessionLength.fromMinutes(it.minutes)) }
    }

    @Test
    fun sessionLengthFromUnknownMinutesFallsBackToDefault() {
        val fallback = SessionLength.fromMinutes(7)
        assertEquals(PreferenceDefaults.DEFAULT_SESSION_MINUTES, fallback.minutes)
        assertEquals(SessionLength.ONE_HOUR, fallback)
    }

    @Test
    fun sessionLengthOptionsMatchDomainDurations() {
        assertEquals(
            PreferenceDefaults.SESSION_DURATIONS_MINUTES, SessionLength.entries.map { it.minutes })
    }

    @Test
    fun breakDurationFromMinutesReturnsMatchingOption() {
        BreakDuration.entries.forEach { assertEquals(it, BreakDuration.fromMinutes(it.minutes)) }
    }

    @Test
    fun breakDurationFromUnknownMinutesFallsBackToDefault() {
        val fallback = BreakDuration.fromMinutes(-1)
        assertEquals(PreferenceDefaults.DEFAULT_BREAK_MINUTES, fallback.minutes)
        assertEquals(BreakDuration.TEN_MIN, fallback)
    }

    @Test
    fun breakDurationOptionsMatchDomainDurations() {
        assertEquals(
            PreferenceDefaults.BREAK_DURATIONS_MINUTES, BreakDuration.entries.map { it.minutes })
    }

    @Test
    fun breakFrequencyFromMinutesReturnsMatchingOption() {
        BreakFrequency.entries.forEach { assertEquals(it, BreakFrequency.fromMinutes(it.minutes)) }
    }

    @Test
    fun breakFrequencyFromUnknownMinutesFallsBackToDefault() {
        val fallback = BreakFrequency.fromMinutes(1000)
        assertEquals(PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES, fallback.minutes)
        assertEquals(BreakFrequency.EVERY_45_MIN, fallback)
    }

    @Test
    fun breakFrequencyOptionsMatchDomainFrequencies() {
        assertEquals(
            PreferenceDefaults.BREAK_FREQUENCIES_MINUTES, BreakFrequency.entries.map { it.minutes })
    }

    @Test
    fun allOptionTestTagsAreUnique() {
        val tags: List<PreferenceOption> =
            StudyTime.entries +
                    SessionLength.entries +
                    BreakDuration.entries +
                    BreakFrequency.entries
        val tagStrings = tags.map { it.testTag }
        assertEquals(tagStrings.size, tagStrings.toSet().size)
        assertTrue(tagStrings.all { it.isNotBlank() })
    }
}