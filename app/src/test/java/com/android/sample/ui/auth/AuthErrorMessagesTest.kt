// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import com.android.sample.R
import com.android.sample.model.auth.AuthError
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMessagesTest {
  @Test
  fun mapsEveryAuthErrorToItsExpectedMessage() {
    val expected =
        mapOf(
            AuthError.NETWORK to R.string.sign_in_error_network,
            AuthError.INVALID_CREDENTIAL to R.string.sign_in_error_invalid_credential,
            AuthError.USER_DISABLED to R.string.sign_in_error_user_disabled,
            AuthError.NO_CREDENTIAL to R.string.sign_in_error_no_credential,
            AuthError.UNKNOWN to R.string.sign_in_error_unknown,
            AuthError.CANCELLED to R.string.sign_in_error_unknown,
            AuthError.CREDENTIAL_STATE_CLEAR_FAILED to R.string.sign_in_error_unknown,
        )

    AuthError.entries.forEach { error ->
      assertEquals(expected.getValue(error), error.toMessageRes())
    }
  }
}
