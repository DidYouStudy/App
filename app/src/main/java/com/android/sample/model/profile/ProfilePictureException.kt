// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

/**
 * Domain exception returned inside a failed profile picture repository [Result].
 *
 * @param error stable category describing the upload failure.
 * @param cause underlying validation, Storage, or unexpected exception, when available.
 */
class ProfilePictureException(val error: ProfilePictureError, cause: Throwable? = null) :
    Exception("Profile picture operation failed: $error", cause)
