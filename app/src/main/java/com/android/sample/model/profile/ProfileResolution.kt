// Co-authored-by: Github Copilot
package com.android.sample.model.profile

sealed interface ProfileResolution {
  val profile: UserProfile

  data class Existing(override val profile: UserProfile) : ProfileResolution

  data class Created(override val profile: UserProfile) : ProfileResolution
}
