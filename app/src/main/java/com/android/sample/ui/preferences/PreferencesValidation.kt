// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.preferences

import com.android.sample.model.preferences.UserPreferences

/**
 * Decides whether the Save button of the preferences screen is enabled.
 *
 * Saving requires at least one study time. In edit mode (opened from Settings) it also requires an
 * actual change, since saving the loaded values again does nothing. During onboarding the loaded
 * defaults are a valid choice, so they can be saved unchanged.
 *
 * @param draft Preferences currently selected on screen.
 * @param original Preferences the screen was loaded with.
 * @param isEditing True when editing existing preferences rather than onboarding.
 * @return True if Save should be enabled.
 */
internal fun canSavePreferences(
    draft: UserPreferences,
    original: UserPreferences,
    isEditing: Boolean,
): Boolean {
  if (draft.preferredStudyTimes.isEmpty()) return false
  return !isEditing || hasChanges(draft, original)
}

/**
 * Compares preferences ignoring the order of study times: deselecting then reselecting a time
 * appends it at the end, which is not a real change.
 */
private fun hasChanges(draft: UserPreferences, original: UserPreferences): Boolean =
    draft.preferredStudyTimes.toSet() != original.preferredStudyTimes.toSet() ||
        draft.copy(preferredStudyTimes = emptyList()) !=
            original.copy(preferredStudyTimes = emptyList())
