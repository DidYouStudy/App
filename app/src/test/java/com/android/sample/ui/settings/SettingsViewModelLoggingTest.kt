// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.settings

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.profile.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.shadows.ShadowLog

/**
 * Checks that a Settings failure keeps its cause for debugging: users only see the translated
 * message, so the exception must reach the log instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsViewModelLoggingTest {
  private val authRepository = mock(AuthRepository::class.java)
  private val profileRepository = mock(ProfileRepository::class.java)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    ShadowLog.clear()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A failure while observing the user or loading the profile logs its exception. */
  @Test
  fun loadFailureLogsTheCause() = runTest {
    val cause = RuntimeException("Flow error")
    `when`(authRepository.currentUser).thenReturn(flow { throw cause })

    SettingsViewModel(authRepository, profileRepository)

    assertWarningLogged(cause)
  }

  /** A sign-out failure logs its exception. */
  @Test
  fun signOutFailureLogsTheCause() = runTest {
    val cause = RuntimeException("Sign out failed")
    `when`(authRepository.currentUser).thenReturn(MutableStateFlow(null))
    `when`(authRepository.signOut()).thenThrow(cause)

    SettingsViewModel(authRepository, profileRepository).signOut()

    assertWarningLogged(cause)
  }

  /** Asserts that exactly one log entry carries [cause], and that it is a warning. */
  private fun assertWarningLogged(cause: Throwable) {
    val entries = ShadowLog.getLogs().filter { it.throwable === cause }
    assertEquals(1, entries.size)
    assertEquals(Log.WARN, entries.single().type)
  }
}
