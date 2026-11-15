// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.preferences

/** Repository interface for managing user preferences persistence. */
interface PreferencesRepository {

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
   * Writes are local-first: success means the preferences are stored locally and queued for sync,
   * not that the server has acknowledged them. This lets saving work while offline. A later
   * server-side rejection of the queued write is not reported through this call.
   *
   * @param preferences The [UserPreferences] instance to save. [UserPreferences.userId] must not be
   *   blank.
   * @return A [Result] containing [Unit] on success or [Result.failure] on error.
   */
  suspend fun savePreferences(preferences: UserPreferences): Result<Unit>
}
