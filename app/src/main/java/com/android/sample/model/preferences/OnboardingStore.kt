// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit

/**
 * Stores whether a user has completed the initial onboarding/preferences setup.
 *
 * This synchronous boolean check allows the application to pick the correct start destination
 * immediately on launch without waiting for asynchronous network requests.
 */
interface OnboardingStore {
  /**
   * Returns true if the user with [userId] has completed the onboarding flow, false if explicitly
   * not completed, or null if the onboarding completion status is unknown (missing local flag).
   *
   * @param userId The ID of the user to check.
   */
  fun hasCompletedOnboarding(userId: String): Boolean?

  /**
   * Records whether the user with [userId] has completed the onboarding flow.
   *
   * @param userId The ID of the user to update.
   * @param completed Whether the onboarding flow is completed.
   */
  fun setCompletedOnboarding(userId: String, completed: Boolean)

  /**
   * Clears any recorded onboarding status for [userId], resetting it to unknown (null).
   *
   * @param userId The ID of the user to clear.
   */
  fun clearOnboarding(userId: String) {
    // Default no-op for stores that do not persist state or require explicit clearing.
  }
}

/** SharedPreferences-backed implementation of [OnboardingStore]. */
class SharedPreferencesOnboardingStore(
    context: Context,
    prefsName: String = "onboarding_preferences",
) : OnboardingStore {
  private val sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  override fun hasCompletedOnboarding(userId: String): Boolean? {
    if (userId.isBlank()) return null
    return if (sharedPreferences.contains(key(userId))) {
      sharedPreferences.getBoolean(key(userId), false)
    } else {
      null
    }
  }

  override fun setCompletedOnboarding(userId: String, completed: Boolean) {
    if (userId.isBlank()) return
    sharedPreferences.edit { putBoolean(key(userId), completed) }
  }

  override fun clearOnboarding(userId: String) {
    if (userId.isBlank()) return
    sharedPreferences.edit { remove(key(userId)) }
  }

  private fun key(userId: String): String = "onboarding_completed_$userId"
}

/** Supplies the application's shared [OnboardingStore]; tests can replace it. */
object OnboardingStoreProvider {
  private var instance: OnboardingStore? = null

  /** Returns the shared onboarding store backed by SharedPreferences, or the one set for tests. */
  @Synchronized
  fun getStore(context: Context): OnboardingStore =
      instance
          ?: SharedPreferencesOnboardingStore(context.applicationContext).also { instance = it }

  /** Returns the test override if set, or an in-memory no-op fallback. */
  @Synchronized
  fun getStore(): OnboardingStore =
      instance
          ?: object : OnboardingStore {
            private val completedUsers = mutableMapOf<String, Boolean>()

            override fun hasCompletedOnboarding(userId: String): Boolean? = completedUsers[userId]

            override fun setCompletedOnboarding(userId: String, completed: Boolean) {
              completedUsers[userId] = completed
            }

            override fun clearOnboarding(userId: String) {
              completedUsers.remove(userId)
            }
          }

  /** Replaces the shared store for tests. Passing null restores default behavior. */
  @VisibleForTesting
  @Synchronized
  fun setStore(store: OnboardingStore?) {
    instance = store
  }
}
