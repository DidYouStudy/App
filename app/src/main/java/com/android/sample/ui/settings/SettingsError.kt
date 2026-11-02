// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.settings

import androidx.annotation.StringRes
import com.android.sample.R

/**
 * Failures the Settings screen can report.
 *
 * The ViewModel exposes these values instead of text, so it stays free of Android resources and the
 * UI resolves the message in the current language with [toMessageRes].
 */
enum class SettingsError {
  /** Observing the signed-in user or reading their profile failed. */
  LOAD_PROFILE_FAILED,

  /** Signing the user out failed. */
  SIGN_OUT_FAILED,
}

/** Returns the string resource shown to the user for this error. */
@StringRes
fun SettingsError.toMessageRes(): Int =
    when (this) {
      SettingsError.LOAD_PROFILE_FAILED -> R.string.settings_error_failed_to_load_profile
      SettingsError.SIGN_OUT_FAILED -> R.string.settings_error_failed_to_sign_out
    }
