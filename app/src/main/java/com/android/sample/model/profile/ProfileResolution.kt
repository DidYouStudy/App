// Co-authored-by: Github Copilot
package com.android.sample.model.profile

/** Describes how a profile became available during resolution. */
sealed interface ProfileResolution {
  /** The persisted profile returned by the resolution operation. */
  val profile: UserProfile

  /**
   * Indicates that the profile already existed before resolution started.
   *
   * @property profile existing persisted profile.
   */
  data class Existing(override val profile: UserProfile) : ProfileResolution

  /**
   * Indicates that resolution created the profile because no profile was found.
   *
   * @property profile newly created persisted profile.
   */
  data class Created(override val profile: UserProfile) : ProfileResolution
}
