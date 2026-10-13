// Co-authored-by: Copilot
package com.android.sample.model.profile

/**
 * Domain exception returned inside a failed profile repository [Result].
 *
 * @param error stable category describing the profile failure.
 * @param cause underlying validation, Firestore, conversion, or unexpected exception, when
 *   available.
 */
class ProfileException(val error: ProfileError, cause: Throwable? = null) :
    Exception("Profile operation failed: $error", cause)
