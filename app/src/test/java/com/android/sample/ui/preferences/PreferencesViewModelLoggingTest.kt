// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.preferences

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.ui.auth.TestAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Checks that a preferences failure keeps its cause for debugging: users only see the translated
 * message, so the exception must reach the log instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PreferencesViewModelLoggingTest {
  private val userId = "user-1"
  private val repository = mock(PreferencesRepository::class.java)

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    ShadowLog.clear()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A load failure logs its exception as a warning. */
  @Test
  fun loadFailureLogsTheCause() = runTest {
    val cause = RuntimeException("Failed to get document because the client is offline.")
    `when`(repository.getPreferences(userId)).thenReturn(Result.failure(cause))

    signedInViewModel()

    assertWarningLogged(cause)
  }

  /** A save failure logs its exception as a warning. */
  @Test
  fun saveFailureLogsTheCause() = runTest {
    val prefs = UserPreferences(userId = userId)
    val cause = RuntimeException("disk full")
    `when`(repository.getPreferences(userId)).thenReturn(Result.success(prefs))
    `when`(repository.savePreferences(prefs)).thenReturn(Result.failure(cause))

    signedInViewModel().updatePreferences(prefs)

    assertWarningLogged(cause)
  }

  /** Creates the ViewModel with a signed-in user, which triggers the initial load. */
  private fun signedInViewModel(): PreferencesViewModel {
    val authRepository = TestAuthRepository()
    authRepository.currentUser.value = UserAccount(userId, null, null, null)
    return PreferencesViewModel(repository, authRepository)
  }

  /** Asserts that exactly one log entry carries [cause], and that it is a warning. */
  private fun assertWarningLogged(cause: Throwable) {
    val entries = ShadowLog.getLogs().filter { it.throwable === cause }
    assertEquals(1, entries.size)
    assertEquals(Log.WARN, entries.single().type)
  }
}
