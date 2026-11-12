// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Local key-value store for user preferences persisted on the device. */
interface PreferencesLocalStore {
  /** Retrieves locally stored preferences for [userId], or null if none exist. */
  fun get(userId: String): UserPreferences?

  /** Persists [preferences] locally on the device. */
  fun save(preferences: UserPreferences)

  /** Returns true if preferences exist locally for [userId]. */
  fun has(userId: String): Boolean

  /** Clears locally stored preferences for [userId]. */
  fun clear(userId: String)
}

/**
 * SharedPreferences-backed implementation of [PreferencesLocalStore].
 *
 * Preferences are serialized to JSON strings keyed by user ID.
 */
class SharedPreferencesPreferencesLocalStore(
    context: Context,
    prefsName: String = "user_preferences",
) : PreferencesLocalStore {
  private val sharedPreferences: SharedPreferences =
      context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  override fun get(userId: String): UserPreferences? {
    if (userId.isBlank()) return null
    val jsonString = sharedPreferences.getString(key(userId), null) ?: return null
    return try {
      deserialize(jsonString)
    } catch (_: Exception) {
      null
    }
  }

  override fun save(preferences: UserPreferences) {
    if (preferences.userId.isBlank()) return
    val jsonString = serialize(preferences)
    sharedPreferences.edit().putString(key(preferences.userId), jsonString).apply()
  }

  override fun has(userId: String): Boolean {
    if (userId.isBlank()) return false
    return sharedPreferences.contains(key(userId))
  }

  override fun clear(userId: String) {
    if (userId.isBlank()) return
    sharedPreferences.edit().remove(key(userId)).apply()
  }

  private fun key(userId: String): String = "prefs_$userId"

  companion object {
    private const val KEY_USER_ID = "userId"
    private const val KEY_STUDY_TIMES = "preferredStudyTimes"
    private const val KEY_LOCATIONS = "preferredLocations"
    private const val KEY_SESSION_LENGTH = "sessionLengthMinutes"
    private const val KEY_BREAK_LENGTH = "breakLengthMinutes"
    private const val KEY_BREAK_FREQUENCY = "breakFrequencyMinutes"

    fun serialize(preferences: UserPreferences): String {
      val json = JSONObject()
      json.put(KEY_USER_ID, preferences.userId)

      val timesArray = JSONArray()
      preferences.preferredStudyTimes.forEach { timesArray.put(it.name) }
      json.put(KEY_STUDY_TIMES, timesArray)

      val locsArray = JSONArray()
      preferences.preferredLocations.forEach { locsArray.put(it) }
      json.put(KEY_LOCATIONS, locsArray)

      json.put(KEY_SESSION_LENGTH, preferences.sessionLengthMinutes)
      json.put(KEY_BREAK_LENGTH, preferences.breakLengthMinutes)
      json.put(KEY_BREAK_FREQUENCY, preferences.breakFrequencyMinutes)
      return json.toString()
    }

    fun deserialize(jsonString: String): UserPreferences {
      val json = JSONObject(jsonString)
      val userId = json.optString(KEY_USER_ID, "")

      val timesArray = json.optJSONArray(KEY_STUDY_TIMES)
      val times = mutableListOf<TimeOfDay>()
      if (timesArray != null) {
        for (i in 0 until timesArray.length()) {
          try {
            times.add(TimeOfDay.valueOf(timesArray.getString(i)))
          } catch (_: IllegalArgumentException) {}
        }
      }

      val locsArray = json.optJSONArray(KEY_LOCATIONS)
      val locations = mutableListOf<String>()
      if (locsArray != null) {
        for (i in 0 until locsArray.length()) {
          locations.add(locsArray.getString(i))
        }
      }

      return UserPreferences(
          userId = userId,
          preferredStudyTimes = if (times.isEmpty()) PreferenceDefaults.DEFAULT_TIMES else times,
          preferredLocations = locations,
          sessionLengthMinutes =
              json.optInt(KEY_SESSION_LENGTH, PreferenceDefaults.DEFAULT_SESSION_MINUTES),
          breakLengthMinutes =
              json.optInt(KEY_BREAK_LENGTH, PreferenceDefaults.DEFAULT_BREAK_MINUTES),
          breakFrequencyMinutes =
              json.optInt(KEY_BREAK_FREQUENCY, PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES),
      )
    }
  }
}
