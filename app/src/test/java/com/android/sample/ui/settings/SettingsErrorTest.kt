// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.settings

import com.android.sample.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks that every Settings error is shown with its own translated message. */
class SettingsErrorTest {

  /** Each error maps to the message that describes it. */
  @Test
  fun eachErrorMapsToItsMessage() {
    assertEquals(
        R.string.settings_error_failed_to_load_profile,
        SettingsError.LOAD_PROFILE_FAILED.toMessageRes(),
    )
    assertEquals(
        R.string.settings_error_failed_to_sign_out,
        SettingsError.SIGN_OUT_FAILED.toMessageRes(),
    )
  }

  /** No two errors share a message, so the user can tell a load failure from a sign-out failure. */
  @Test
  fun errorsHaveDistinctMessages() {
    val messages = SettingsError.entries.map { it.toMessageRes() }

    assertEquals(messages.size, messages.toSet().size)
  }
}
