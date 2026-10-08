// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import androidx.annotation.StringRes
import com.android.sample.R
import com.android.sample.model.auth.AuthError

@StringRes
fun AuthError.toMessageRes(): Int =
    when (this) {
      AuthError.NETWORK -> R.string.sign_in_error_network
      AuthError.INVALID_CREDENTIAL -> R.string.sign_in_error_invalid_credential
      AuthError.USER_DISABLED -> R.string.sign_in_error_user_disabled
      AuthError.NO_CREDENTIAL -> R.string.sign_in_error_no_credential
      AuthError.UNKNOWN -> R.string.sign_in_error_unknown
      // AuthViewModel filters these through silentErrors, so they never reach the screen.
      AuthError.CANCELLED,
      AuthError.CREDENTIAL_STATE_CLEAR_FAILED -> R.string.sign_in_error_unknown
    }
