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
   * Returns true if the user with [userId] has completed the onboarding flow.
   *
   * @param userId The ID of the user to check.
   */
  fun hasCompletedOnboarding(userId: String): Boolean

  /**
   * Records whether the user with [userId] has completed the onboarding flow.
   *
   * @param userId The ID of the user to update.
   * @param completed Whether the onboarding flow is completed.
   */
  fun setCompletedOnboarding(userId: String, completed: Boolean)
}

/** SharedPreferences-backed implementation of [OnboardingStore]. */
class SharedPreferencesOnboardingStore(
    context: Context,
    prefsName: String = "onboarding_preferences",
) : OnboardingStore {
  private val sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  override fun hasCompletedOnboarding(userId: String): Boolean {
    return userId.isNotBlank() && sharedPreferences.getBoolean(key(userId), false)
  }

  override fun setCompletedOnboarding(userId: String, completed: Boolean) {
    if (userId.isBlank()) return
    sharedPreferences.edit { putBoolean(key(userId), completed) }
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
            private val completedUsers = mutableSetOf<String>()

            override fun hasCompletedOnboarding(userId: String): Boolean =
                completedUsers.contains(userId)

            override fun setCompletedOnboarding(userId: String, completed: Boolean) {
              if (completed) completedUsers.add(userId) else completedUsers.remove(userId)
            }
          }

  /** Replaces the shared store for tests. Passing null restores default behavior. */
  @VisibleForTesting
  @Synchronized
  fun setStore(store: OnboardingStore?) {
    instance = store
  }
}
