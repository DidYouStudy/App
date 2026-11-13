// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun hasCompletedOnboarding_defaultsToNull() {
    assertNull(store.hasCompletedOnboarding("user-1"))
  }

  @Test
  fun setCompletedOnboarding_updatesValue() {
    store.setCompletedOnboarding("user-1", true)
    assertEquals(true, store.hasCompletedOnboarding("user-1"))

    store.setCompletedOnboarding("user-1", false)
    assertEquals(false, store.hasCompletedOnboarding("user-1"))

    store.clearOnboarding("user-1")
    assertNull(store.hasCompletedOnboarding("user-1"))
  }

  @Test
  fun blankUserId_returnsNullAndDoesNotCrash() {
    assertNull(store.hasCompletedOnboarding(""))
    assertNull(store.hasCompletedOnboarding("   "))
    store.setCompletedOnboarding("", true)
    assertNull(store.hasCompletedOnboarding(""))
    store.clearOnboarding("")
    store.clearOnboarding("   ")
  }

  @Test
  fun defaultClearOnboarding_doesNothingAndDoesNotThrow() {
    val defaultStore =
        object : OnboardingStore {
          override fun hasCompletedOnboarding(userId: String): Boolean? = null

          override fun setCompletedOnboarding(userId: String, completed: Boolean) {}
        }
    defaultStore.clearOnboarding("user-default")

    // Exercise DefaultImpls bridge method generated for binary compatibility
    val defaultImpls = runCatching {
      Class.forName("com.android.sample.model.preferences.OnboardingStore\$DefaultImpls")
    }
        .getOrNull()
    defaultImpls
        ?.getMethod("clearOnboarding", OnboardingStore::class.java, String::class.java)
        ?.invoke(null, defaultStore, "user-default")
  }

  @Test
  fun providerFallback_clearOnboardingRemovesUser() {
    val fallbackStore = OnboardingStoreProvider.getStore()
    fallbackStore.setCompletedOnboarding("user-fallback", true)
    assertEquals(true, fallbackStore.hasCompletedOnboarding("user-fallback"))

    fallbackStore.clearOnboarding("user-fallback")
    assertNull(fallbackStore.hasCompletedOnboarding("user-fallback"))
  }

  @Test
  fun provider_returnsConfiguredStore() {
    val defaultStore = OnboardingStoreProvider.getStore(context)
    assertNull(defaultStore.hasCompletedOnboarding("user-2"))

    val fakeStore =
        object : OnboardingStore {
          var completed: Boolean? = null

          override fun hasCompletedOnboarding(userId: String): Boolean? = completed

          override fun setCompletedOnboarding(userId: String, completed: Boolean) {
            this.completed = completed
          }
        }

    OnboardingStoreProvider.setStore(fakeStore)
    fakeStore.completed = true
    assertEquals(true, OnboardingStoreProvider.getStore(context).hasCompletedOnboarding("user-2"))
    assertEquals(true, OnboardingStoreProvider.getStore().hasCompletedOnboarding("user-2"))
  }
}
