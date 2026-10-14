// Co-authored-by: GitHub Copilot
package com.android.sample.ui.app

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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
  private val dispatcher = UnconfinedTestDispatcher()
  private lateinit var authRepository: TestAuthRepository
  private lateinit var preferencesRepository: PreferencesRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    authRepository = TestAuthRepository()
    preferencesRepository = mock(PreferencesRepository::class.java)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun startsInSignInWhenThereIsNoAuthenticatedUser() = runTest {
    val viewModel = AppViewModel(authRepository, preferencesRepository)

    assertEquals(AppUiState.SignIn, viewModel.uiState.value)
  }

  @Test
  fun authenticatedUserWithoutPreferencesStartsFirstTimeSignIn() = runTest {
    val user = UserAccount("user-1", null, null, null)
    authRepository.currentUser.value = user
    `when`(preferencesRepository.getPreferences(user.uid)).thenReturn(Result.success(null))

    val viewModel = AppViewModel(authRepository, preferencesRepository)

    assertEquals(AppUiState.FirstTimeSignIn, viewModel.uiState.value)
  }

  @Test
  fun authenticatedUserWithPreferencesStartsDashboard() = runTest {
    val user = UserAccount("user-1", null, null, null)
    authRepository.currentUser.value = user
    `when`(preferencesRepository.getPreferences(user.uid))
        .thenReturn(Result.success(UserPreferences(userId = user.uid)))

    val viewModel = AppViewModel(authRepository, preferencesRepository)

    assertEquals(AppUiState.Dashboard, viewModel.uiState.value)
  }

  @Test
  fun signingOutChangesStateToLogOut() = runTest {
    val user = UserAccount("user-1", null, null, null)
    authRepository.currentUser.value = user
    `when`(preferencesRepository.getPreferences(user.uid)).thenReturn(Result.success(null))
    val viewModel = AppViewModel(authRepository, preferencesRepository)

    authRepository.currentUser.value = null

    assertEquals(AppUiState.LogOut, viewModel.uiState.value)
  }
}
