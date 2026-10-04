package com.android.sample.model.preferences

interface PreferencesRepository {
  suspend fun getPreferences(userId: String): Result<UserPreferences?>

  suspend fun savePreferences(preferences: UserPreferences): Result<Unit>
}
