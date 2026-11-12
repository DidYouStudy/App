// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingStoreTest {
  private lateinit var context: Context
  private lateinit var store: SharedPreferencesOnboardingStore

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    store = SharedPreferencesOnboardingStore(context, prefsName = "test_onboarding_prefs")
  }

  @After
  fun tearDown() {
    OnboardingStoreProvider.setStore(null)
  }

  @Test
  fun hasCompletedOnboarding_defaultsToFalse() {
    assertFalse(store.hasCompletedOnboarding("user-1"))
  }

  @Test
  fun setCompletedOnboarding_updatesValue() {
    store.setCompletedOnboarding("user-1", true)
    assertTrue(store.hasCompletedOnboarding("user-1"))

    store.setCompletedOnboarding("user-1", false)
    assertFalse(store.hasCompletedOnboarding("user-1"))
  }

  @Test
  fun blankUserId_returnsFalseAndDoesNotCrash() {
    assertFalse(store.hasCompletedOnboarding(""))
    assertFalse(store.hasCompletedOnboarding("   "))
    store.setCompletedOnboarding("", true)
    assertFalse(store.hasCompletedOnboarding(""))
  }

  @Test
  fun provider_returnsConfiguredStore() {
    val defaultStore = OnboardingStoreProvider.getStore(context)
    assertFalse(defaultStore.hasCompletedOnboarding("user-2"))

    val fakeStore =
        object : OnboardingStore {
          var completed = false

          override fun hasCompletedOnboarding(userId: String): Boolean = completed

          override fun setCompletedOnboarding(userId: String, completed: Boolean) {
            this.completed = completed
          }
        }

    OnboardingStoreProvider.setStore(fakeStore)
    fakeStore.completed = true
    assertTrue(OnboardingStoreProvider.getStore(context).hasCompletedOnboarding("user-2"))
    assertTrue(OnboardingStoreProvider.getStore().hasCompletedOnboarding("user-2"))
  }
}
