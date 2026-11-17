// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

/**
 * Stable categories for failures while uploading a profile picture.
 *
 * The category lets ViewModels and tests handle upload failures without depending on Firebase
 * exception classes or provider-specific message text.
 */
enum class ProfilePictureError {
  /** The repository received a blank user ID, or one that cannot be used as a file name. */
  INVALID_USER_ID,

  /** The image to upload has no bytes. */
  EMPTY_IMAGE,

  /** The picked image cannot be opened or decoded, so it cannot be prepared for upload. */
  INVALID_IMAGE,

  /** Cloud Storage rejected, failed, or could not complete the upload or the URL lookup. */
  STORAGE_FAILURE,

  /** The operation failed for an unexpected reason. */
  UNKNOWN,
}
