// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.preferences

import androidx.annotation.StringRes
import com.android.sample.R

/**
 * Failures the preferences screen can report.
 *
 * The ViewModel exposes these values instead of text, so it stays free of Android resources and the
 * UI resolves the message in the current language with [toMessageRes].
 */
enum class PreferencesError {
  /** No user is signed in, so there are no preferences to load or save. */
  NO_SIGNED_IN_USER,

  /** Reading the user's preferences failed. */
  LOAD_FAILED,

  /** Writing the user's preferences failed. */
  SAVE_FAILED,
}

/** Returns the string resource shown to the user for this error. */
@StringRes
fun PreferencesError.toMessageRes(): Int =
    when (this) {
      PreferencesError.NO_SIGNED_IN_USER -> R.string.preferences_error_no_signed_in_user
      PreferencesError.LOAD_FAILED -> R.string.preferences_error_load_failed
      PreferencesError.SAVE_FAILED -> R.string.preferences_error_save_failed
    }
