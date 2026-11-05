// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.preferences

import com.android.sample.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks that every preferences error is shown with its own translated message. */
class PreferencesErrorTest {

  /** Each error maps to the message that describes it. */
  @Test
  fun eachErrorMapsToItsMessage() {
    assertEquals(
        R.string.preferences_error_no_signed_in_user,
        PreferencesError.NO_SIGNED_IN_USER.toMessageRes(),
    )
    assertEquals(
        R.string.preferences_error_load_failed,
        PreferencesError.LOAD_FAILED.toMessageRes(),
    )
    assertEquals(
        R.string.preferences_error_save_failed,
        PreferencesError.SAVE_FAILED.toMessageRes(),
    )
  }

  /** No two errors share a message, so the user can tell a load failure from a save failure. */
  @Test
  fun errorsHaveDistinctMessages() {
    val messages = PreferencesError.entries.map { it.toMessageRes() }

    assertEquals(messages.size, messages.toSet().size)
  }
}
