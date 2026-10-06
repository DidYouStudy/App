// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.preferences

import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
  private val userId = "testUser123"

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    repository = mock(PreferencesRepository::class.java)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun init_loadsPreferences_successWithExistingPreferences() = runTest {
    val existingPrefs = UserPreferences(userId = userId, sessionLengthMinutes = 60)
    `when`(repository.getPreferences(userId)).thenReturn(Result.success(existingPrefs))

    val viewModel = PreferencesViewModel(repository, userId)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Success)
    assertEquals(existingPrefs, (state as PreferencesUiState.Success).preferences)
  }

  @Test
  fun init_loadsPreferences_successWithNullPreferences_fallsBackToDefault() = runTest {
    `when`(repository.getPreferences(userId)).thenReturn(Result.success(null))

    val viewModel = PreferencesViewModel(repository, userId)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Success)
    val successState = state as PreferencesUiState.Success
    assertEquals(userId, successState.preferences.userId)
  }

  @Test
  fun init_loadsPreferences_failure_setsErrorState() = runTest {
    `when`(repository.getPreferences(userId))
        .thenReturn(Result.failure(RuntimeException("Network error")))

    val viewModel = PreferencesViewModel(repository, userId)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Error)
    assertEquals("Network error", (state as PreferencesUiState.Error).message)
  }

  @Test
  fun updatePreferences_success_updatesUiStateToSuccess() = runTest {
    val initialPrefs = UserPreferences(userId = userId)
    val updatedPrefs = initialPrefs.copy(sessionLengthMinutes = 90)

    `when`(repository.getPreferences(userId)).thenReturn(Result.success(initialPrefs))
    `when`(repository.savePreferences(updatedPrefs)).thenReturn(Result.success(Unit))

    val viewModel = PreferencesViewModel(repository, userId)

    viewModel.updatePreferences(updatedPrefs)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Success)
    assertEquals(updatedPrefs, (state as PreferencesUiState.Success).preferences)
    verify(repository).savePreferences(updatedPrefs)
  }

  @Test
  fun updatePreferences_failure_updatesUiStateToError() = runTest {
    val initialPrefs = UserPreferences(userId = userId)
    val updatedPrefs = initialPrefs.copy(sessionLengthMinutes = 90)

    `when`(repository.getPreferences(userId)).thenReturn(Result.success(initialPrefs))
    `when`(repository.savePreferences(updatedPrefs))
        .thenReturn(Result.failure(RuntimeException("Save failed")))

    val viewModel = PreferencesViewModel(repository, userId)

    viewModel.updatePreferences(updatedPrefs)

    val state = viewModel.uiState.value
    assertTrue(state is PreferencesUiState.Error)
    assertEquals("Save failed", (state as PreferencesUiState.Error).message)
  }
}
