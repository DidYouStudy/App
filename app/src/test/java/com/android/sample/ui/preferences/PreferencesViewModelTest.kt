// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.preferences

import androidx.lifecycle.ViewModel
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.ui.auth.TestAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var repository: PreferencesRepository
  private lateinit var authRepository: TestAuthRepository
  private val userId = "testUser123"

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = mock(PreferencesRepository::class.java)
    authRepository = TestAuthRepository()
    authRepository.currentUser.value = UserAccount(userId, null, null, null)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun init_loadsPreferences_successWithExistingPreferences() = runTest {
    val existingPrefs = UserPreferences(userId = userId, sessionLengthMinutes = 60)
    `when`(repository.getPreferences(userId)).thenReturn(Result.success(existingPrefs))

    val viewModel = PreferencesViewModel(repository, authRepository)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Success)
    assertEquals(existingPrefs, (state as PreferencesUiState.Success).preferences)
  }

  @Test
  fun init_loadsPreferences_successWithNullPreferences_fallsBackToDefault() = runTest {
    `when`(repository.getPreferences(userId)).thenReturn(Result.success(null))

    val viewModel = PreferencesViewModel(repository, authRepository)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Success)
    val successState = state as PreferencesUiState.Success
    assertEquals(userId, successState.preferences.userId)
  }

  @Test
  fun init_loadsPreferences_failure_setsErrorState() = runTest {
    `when`(repository.getPreferences(userId))
        .thenReturn(Result.failure(RuntimeException("Network error")))

    val viewModel = PreferencesViewModel(repository, authRepository)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Error)
    assertEquals("Network error", (state as PreferencesUiState.Error).message)
  }

  @Test
  fun init_withoutSignedInUser_setsErrorState() = runTest {
    authRepository.currentUser.value = null

    val viewModel = PreferencesViewModel(repository, authRepository)

    assertEquals(PreferencesUiState.Error("No signed-in user"), viewModel.uiState.value)
  }

  @Test
  fun init_loadsPreferences_failureWithoutMessage_usesFallback() = runTest {
    val exception = RuntimeException(null as String?)
    `when`(repository.getPreferences(userId)).thenReturn(Result.failure(exception))

    val viewModel = PreferencesViewModel(repository, authRepository)

    assertEquals(
        PreferencesUiState.Error("Failed to load preferences"),
        viewModel.uiState.value,
    )
  }

  @Test
  fun updatePreferences_success_updatesUiStateToSuccess() = runTest {
    val initialPrefs = UserPreferences(userId = userId)
    val updatedPrefs = initialPrefs.copy(sessionLengthMinutes = 90)

    `when`(repository.getPreferences(userId)).thenReturn(Result.success(initialPrefs))
    `when`(repository.savePreferences(updatedPrefs)).thenReturn(Result.success(Unit))

    val viewModel = PreferencesViewModel(repository, authRepository)

    viewModel.updatePreferences(updatedPrefs)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Success)
    assertEquals(updatedPrefs, (state as PreferencesUiState.Success).preferences)
    verify(repository).savePreferences(updatedPrefs)
  }

  @Test
  fun updatePreferences_success_invokesCallback() = runTest {
    val updatedPrefs = UserPreferences(userId = userId)
    `when`(repository.getPreferences(userId)).thenReturn(Result.success(null))
    `when`(repository.savePreferences(updatedPrefs)).thenReturn(Result.success(Unit))
    var callbackCalls = 0
    val viewModel = PreferencesViewModel(repository, authRepository)

    viewModel.updatePreferences(updatedPrefs) { callbackCalls++ }

    assertEquals(1, callbackCalls)
  }

  @Test
  fun updatePreferences_failure_updatesUiStateToError() = runTest {
    val initialPrefs = UserPreferences(userId = userId)
    val updatedPrefs = initialPrefs.copy(sessionLengthMinutes = 90)

    `when`(repository.getPreferences(userId)).thenReturn(Result.success(initialPrefs))
    `when`(repository.savePreferences(updatedPrefs))
        .thenReturn(Result.failure(RuntimeException("Save failed")))

    val viewModel = PreferencesViewModel(repository, authRepository)

    viewModel.updatePreferences(updatedPrefs)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Error)
    assertEquals("Save failed", (state as PreferencesUiState.Error).message)
  }

  @Test
  fun updatePreferences_whileSaveInFlight_ignoresRepeatedCalls() = runTest {
    val prefs = UserPreferences(userId = userId)
    val repository = SuspendingSaveRepository(prefs)
    var callbackCalls = 0
    val viewModel = PreferencesViewModel(repository, authRepository)

    // Rapid taps on Save: only the first one may reach the repository.
    viewModel.updatePreferences(prefs) { callbackCalls++ }
    viewModel.updatePreferences(prefs) { callbackCalls++ }
    viewModel.updatePreferences(prefs) { callbackCalls++ }
    assertTrue(viewModel.isSaving.value)

    repository.pendingSave.complete(Result.success(Unit))

    assertEquals(1, repository.saveCalls)
    assertEquals(1, callbackCalls)
    assertFalse(viewModel.isSaving.value)
  }

  @Test
  fun updatePreferences_isSavingIsFalseBeforeAnySave() = runTest {
    `when`(repository.getPreferences(userId)).thenReturn(Result.success(null))

    val viewModel = PreferencesViewModel(repository, authRepository)

    assertFalse(viewModel.isSaving.value)
  }

  @Test
  fun updatePreferences_afterFailure_allowsRetry() = runTest {
    val prefs = UserPreferences(userId = userId)
    val repository = SuspendingSaveRepository(prefs)
    val viewModel = PreferencesViewModel(repository, authRepository)

    viewModel.updatePreferences(prefs)
    repository.pendingSave.complete(Result.failure(RuntimeException("Save failed")))
    assertFalse(viewModel.isSaving.value)

    // A failed save must not leave the screen stuck: a new attempt reaches the repository.
    repository.pendingSave = CompletableDeferred()
    viewModel.updatePreferences(prefs)

    assertEquals(2, repository.saveCalls)
  }

  /** Repository whose save stays in flight until [pendingSave] is completed by the test. */
  private class SuspendingSaveRepository(private val loaded: UserPreferences) :
      PreferencesRepository {
    var pendingSave = CompletableDeferred<Result<Unit>>()
    var saveCalls = 0

    override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
        Result.success(loaded)

    override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> {
      saveCalls++
      return pendingSave.await()
    }
  }

  @Test
  fun factory_createsPreferencesViewModel_andRejectsOtherTypes() {
    val factory = PreferencesViewModelFactory(repository, authRepository)

    assertTrue(factory.create(PreferencesViewModel::class.java) is PreferencesViewModel)
    assertThrows(IllegalArgumentException::class.java) {
      factory.create(OtherViewModel::class.java)
    }
  }

  private class OtherViewModel : ViewModel()
}
