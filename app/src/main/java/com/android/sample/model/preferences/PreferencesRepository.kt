// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

/** Repository interface for managing user preferences persistence. */
interface PreferencesRepository {

  /**
   * Checks synchronously whether preferences are stored locally on the device for the given user.
   *
   * @param userId The ID of the user to check.
   * @return `true` if preferences exist locally on the device, `false` otherwise.
   */
  fun hasPreferences(userId: String): Boolean

  /**
   * Fetches the preferences for a given user.
   *
   * @param userId The ID of the user whose preferences are requested. Must not be blank.
   * @return A [Result] containing [UserPreferences] if found, `null` if no preferences document
   *   exists for the user, or a [Result.failure] if an error occurs.
   */
  suspend fun getPreferences(userId: String): Result<UserPreferences?>

  /**
   * Persists the provided user preferences.
   *
   * @param preferences The [UserPreferences] instance to save. [UserPreferences.userId] must not be
   *   blank.
   * @return A [Result] containing [Unit] on success or [Result.failure] on error.
   */
  suspend fun savePreferences(preferences: UserPreferences): Result<Unit>
}
