// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesLocalStoreTest {
  private lateinit var context: Context
  private lateinit var store: SharedPreferencesPreferencesLocalStore

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    store = SharedPreferencesPreferencesLocalStore(context, "test_preferences")
    context.getSharedPreferences("test_preferences", Context.MODE_PRIVATE).edit().clear().commit()
  }

  @Test
  fun hasReturnsFalseWhenNoPreferencesStored() {
    assertFalse(store.has("user123"))
    assertFalse(store.has(""))
  }

  @Test
  fun getReturnsNullWhenNoPreferencesStored() {
    assertNull(store.get("user123"))
    assertNull(store.get(""))
  }

  @Test
  fun saveAndRetrievePreferences() {
    val prefs =
        UserPreferences(
            userId = "user123",
            preferredStudyTimes = listOf(TimeOfDay.MORNING, TimeOfDay.NIGHT),
            preferredLocations = listOf("BC Building", "Library"),
            sessionLengthMinutes = 90,
            breakLengthMinutes = 15,
            breakFrequencyMinutes = 45,
        )

    store.save(prefs)

    assertTrue(store.has("user123"))
    val retrieved = store.get("user123")
    assertNotNull(retrieved)
    assertEquals(prefs, retrieved)
  }

  @Test
  fun clearRemovesStoredPreferences() {
    val prefs = UserPreferences(userId = "user123")
    store.save(prefs)
    assertTrue(store.has("user123"))

    store.clear("user123")
    assertFalse(store.has("user123"))
    assertNull(store.get("user123"))
  }

  @Test
  fun serializeAndDeserializeRoundTrip() {
    val original =
        UserPreferences(
            userId = "userABC",
            preferredStudyTimes = listOf(TimeOfDay.AFTERNOON),
            preferredLocations = listOf("Home"),
            sessionLengthMinutes = 120,
            breakLengthMinutes = 20,
            breakFrequencyMinutes = 60,
        )

    val json = SharedPreferencesPreferencesLocalStore.serialize(original)
    val restored = SharedPreferencesPreferencesLocalStore.deserialize(json)

    assertEquals(original, restored)
  }
}
