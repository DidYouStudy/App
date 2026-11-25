// Co-authored-by: Copilot
// Co-authored-by: Claude Code
package com.android.sample.model.profile

/**
 * Stable categories for failures while reading or creating a persisted profile.
 *
 * The category lets ViewModels and tests handle profile failures without depending on Firebase
 * exception classes or provider-specific message text.
 */
enum class ProfileError {
  /** The repository received an invalid authentication user ID. */
  INVALID_USER_ID,

  /** Firestore rejected, failed, or could not complete the profile operation. */
  FIRESTORE_FAILURE,

  /** An existing Firestore document could not be converted to [UserProfile]. */
  INVALID_PROFILE_DATA,

  /** The requested username does not follow [UsernamePolicy]. */
  INVALID_USERNAME,

  /** Another user already owns the requested username (compared case-insensitively). */
  USERNAME_TAKEN,

  /**
   * The server could not be reached ([com.google.firebase.firestore.FirebaseFirestoreException.Code
   * .UNAVAILABLE]), typically because the device is offline. Retrying once connected may work.
   */
  OFFLINE,

  /** The operation failed for an unexpected reason. */
  UNKNOWN,

  /** The profile operation was cancelled while its coroutine remained active. */
  CANCELLED,
}
