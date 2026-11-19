// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.settings

import androidx.annotation.StringRes
import com.android.sample.R
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.UsernamePolicy

/**
 * Reasons the "Edit username" dialog can refuse or fail to save a name.
 *
 * Like [SettingsError], the ViewModel exposes these values instead of text; the UI resolves the
 * message in the current language with [toMessageRes].
 */
enum class UsernameError {
  /** Shorter than [UsernamePolicy.MIN_LENGTH]; detected before sending. */
  TOO_SHORT,

  /** Longer than [UsernamePolicy.MAX_LENGTH]; detected before sending. */
  TOO_LONG,

  /** Contains a character [UsernamePolicy] does not allow, or starts with a symbol. */
  INVALID_CHARACTERS,

  /** Another user already owns the name ([ProfileError.USERNAME_TAKEN]). */
  TAKEN,

  /** The save failed because of the network or another storage problem; retrying may work. */
  FAILURE,

  /** The user's profile document is missing or unreadable ([ProfileError.INVALID_PROFILE_DATA]). */
  PROFILE_UNAVAILABLE,
}

/** Returns the string resource shown under the username field for this error. */
@StringRes
fun UsernameError.toMessageRes(): Int =
    when (this) {
      UsernameError.TOO_SHORT -> R.string.settings_username_error_too_short
      UsernameError.TOO_LONG -> R.string.settings_username_error_too_long
      UsernameError.INVALID_CHARACTERS -> R.string.settings_username_error_invalid_characters
      UsernameError.TAKEN -> R.string.settings_username_error_taken
      UsernameError.FAILURE -> R.string.settings_username_error_failure
      UsernameError.PROFILE_UNAVAILABLE -> R.string.settings_username_error_profile_unavailable
    }

/**
 * State of the open "Edit username" dialog.
 *
 * @property input Text currently typed in the field.
 * @property error Reason the current input cannot be saved, or null when there is none.
 * @property isSaving Whether a save request is in flight.
 * @property canSave Whether the Save button is enabled: the input is non-empty, valid, different
 *   from the current username, and no save is running.
 */
data class UsernameEditState(
    val input: String,
    val error: UsernameError? = null,
    val isSaving: Boolean = false,
    val canSave: Boolean = false,
)

/**
 * Checks a typed username against [UsernamePolicy] before it is sent to the repository.
 *
 * Surrounding whitespace is ignored, as in [UsernamePolicy.validate]. An empty input yields no
 * error (the field simply keeps Save disabled).
 *
 * @param raw text typed by the user.
 * @return the first problem found, or null when the input is empty or valid.
 */
fun validateUsernameInput(raw: String): UsernameError? {
  if (raw.isBlank()) return null
  return when (UsernamePolicy.issue(raw)) {
    UsernamePolicy.Issue.INVALID_CHARACTERS -> UsernameError.INVALID_CHARACTERS
    UsernamePolicy.Issue.TOO_SHORT -> UsernameError.TOO_SHORT
    UsernamePolicy.Issue.TOO_LONG -> UsernameError.TOO_LONG
    null -> null
  }
}

/** Maps a repository failure category to the error shown in the dialog. */
fun ProfileError.toUsernameError(): UsernameError =
    when (this) {
      ProfileError.USERNAME_TAKEN -> UsernameError.TAKEN
      ProfileError.INVALID_USERNAME -> UsernameError.INVALID_CHARACTERS
      ProfileError.INVALID_PROFILE_DATA -> UsernameError.PROFILE_UNAVAILABLE
      else -> UsernameError.FAILURE
    }
