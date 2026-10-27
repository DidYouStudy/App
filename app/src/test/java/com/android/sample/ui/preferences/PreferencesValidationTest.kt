// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.preferences

import com.android.sample.model.preferences.TimeOfDay
import com.android.sample.model.preferences.UserPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks when the preferences Save button is enabled, in onboarding and in edit mode. */
class PreferencesValidationTest {
  private val original =
      UserPreferences(
          userId = "user-1",
          preferredStudyTimes = listOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON),
      )

  @Test
  fun onboardingAllowsSavingUnchangedDefaults() {
    assertTrue(canSavePreferences(original, original, isEditing = false))
  }

  @Test
  fun onboardingRejectsNoStudyTime() {
    val draft = original.copy(preferredStudyTimes = emptyList())

    assertFalse(canSavePreferences(draft, original, isEditing = false))
  }

  @Test
  fun editingRejectsUnchangedPreferences() {
    assertFalse(canSavePreferences(original, original, isEditing = true))
  }

  @Test
  fun editingAllowsChangedSingleChoiceValue() {
    assertTrue(canSavePreferences(original.copy(sessionLengthMinutes = 120), original, true))
    assertTrue(canSavePreferences(original.copy(breakLengthMinutes = 20), original, true))
    assertTrue(canSavePreferences(original.copy(breakFrequencyMinutes = 30), original, true))
  }

  @Test
  fun editingAllowsChangedStudyTimes() {
    val draft = original.copy(preferredStudyTimes = listOf(TimeOfDay.MORNING))

    assertTrue(canSavePreferences(draft, original, isEditing = true))
  }

  @Test
  fun editingTreatsReorderedStudyTimesAsUnchanged() {
    val draft = original.copy(preferredStudyTimes = listOf(TimeOfDay.AFTERNOON, TimeOfDay.MORNING))

    assertFalse(canSavePreferences(draft, original, isEditing = true))
  }

  @Test
  fun editingRejectsNoStudyTimeEvenIfOtherwiseChanged() {
    val draft = original.copy(preferredStudyTimes = emptyList(), sessionLengthMinutes = 120)

    assertFalse(canSavePreferences(draft, original, isEditing = true))
  }
}
