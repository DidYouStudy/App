// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.app

import com.android.sample.model.auth.UserAccount
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.ui.auth.TestAuthRepository
import com.android.sample.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

  @Test
  fun signingInEmitsPreferencesDestinationForFirstTimeUser() = runTest {
    val user = UserAccount("user-1", null, null, null)
    `when`(preferencesRepository.getPreferences(user.uid)).thenReturn(Result.success(null))
    val viewModel = AppViewModel(authRepository, preferencesRepository)
    val destinations = mutableListOf<Screen>()

    authRepository.currentUser.value = user
    viewModel.onSignedIn(destinations::add)

    assertEquals(listOf(Screen.Preferences), destinations)
  }

  @Test
  fun signingInTimeoutReturnsToAuthenticationWithErrorState() = runTest {
    val viewModel = AppViewModel(authRepository, preferencesRepository)
    val destinations = mutableListOf<Screen>()

    viewModel.onSignedIn(destinations::add)
    advanceTimeBy(10_001)
    runCurrent()

    assertEquals(
        AppUiState.Error("Timed out waiting for signed-in user"),
        viewModel.uiState.value,
    )
    assertEquals(listOf(Screen.Auth), destinations)
    assertEquals(true, viewModel.canNavigateTo(Screen.Auth))
    assertEquals(false, viewModel.canNavigateTo(Screen.Dashboard))
  }

  @Test
  fun savingPreferencesSetsDashboardState() = runTest {
    val viewModel = AppViewModel(authRepository, preferencesRepository)

    viewModel.onPreferencesSaved()

    assertEquals(AppUiState.Dashboard, viewModel.uiState.value)
  }

  @Test
  fun authenticationStateOnlyAllowsAuthenticationDestination() = runTest {
    val viewModel = AppViewModel(authRepository, preferencesRepository)

    assertEquals(true, viewModel.canNavigateTo(Screen.Auth))
    assertEquals(false, viewModel.canNavigateTo(Screen.Preferences))
    assertEquals(false, viewModel.canNavigateTo(Screen.Dashboard))
  }

  @Test
  fun firstTimeStateOnlyAllowsPreferencesDestination() = runTest {
    val user = UserAccount("user-1", null, null, null)
    authRepository.currentUser.value = user
    `when`(preferencesRepository.getPreferences(user.uid)).thenReturn(Result.success(null))
    val viewModel = AppViewModel(authRepository, preferencesRepository)

    assertEquals(true, viewModel.canNavigateTo(Screen.Preferences))
    assertEquals(false, viewModel.canNavigateTo(Screen.Auth))
    assertEquals(false, viewModel.canNavigateTo(Screen.Dashboard))
  }

  @Test
  fun returningUserOnlyAllowsAuthenticatedDestinations() = runTest {
    val user = UserAccount("user-1", null, null, null)
    authRepository.currentUser.value = user
    `when`(preferencesRepository.getPreferences(user.uid))
        .thenReturn(Result.success(UserPreferences(userId = user.uid)))
    val viewModel = AppViewModel(authRepository, preferencesRepository)

    assertEquals(true, viewModel.canNavigateTo(Screen.Dashboard))
    assertEquals(true, viewModel.canNavigateTo(Screen.Settings))
    assertEquals(false, viewModel.canNavigateTo(Screen.Auth))
  }

  /** A returning user must be able to reach Preferences from Settings to edit them (#124). */
  @Test
  fun returningUserCanNavigateToPreferencesToEditThem() = runTest {
    val viewModel = signedInViewModelWithPreferences()

    assertEquals(AppUiState.Dashboard, viewModel.uiState.value)
    assertEquals(true, viewModel.canNavigateTo(Screen.Preferences))
  }

  /** Saving edited preferences keeps the returning user in the Dashboard state. */
  @Test
  fun savingEditedPreferencesKeepsDashboardState() = runTest {
    val viewModel = signedInViewModelWithPreferences()

    viewModel.onPreferencesSaved()

    assertEquals(AppUiState.Dashboard, viewModel.uiState.value)
    assertEquals(true, viewModel.canNavigateTo(Screen.Settings))
    assertEquals(true, viewModel.canNavigateTo(Screen.Preferences))
    assertEquals(false, viewModel.canNavigateTo(Screen.Auth))
  }

  /** First-time onboarding is unchanged: saving moves to Dashboard and closes the flow. */
  @Test
  fun savingFirstTimePreferencesStillMovesToDashboard() = runTest {
    val user = UserAccount("user-1", null, null, null)
    authRepository.currentUser.value = user
    `when`(preferencesRepository.getPreferences(user.uid)).thenReturn(Result.success(null))
    val viewModel = AppViewModel(authRepository, preferencesRepository)

    viewModel.onPreferencesSaved()

    assertEquals(AppUiState.Dashboard, viewModel.uiState.value)
    assertEquals(true, viewModel.canNavigateTo(Screen.Dashboard))
  }

  /** Signing out while editing blocks Preferences again. */
  @Test
  fun signingOutWhileEditingBlocksPreferences() = runTest {
    val viewModel = signedInViewModelWithPreferences()

    authRepository.currentUser.value = null

    assertEquals(AppUiState.LogOut, viewModel.uiState.value)
    assertEquals(false, viewModel.canNavigateTo(Screen.Preferences))
    assertEquals(true, viewModel.canNavigateTo(Screen.Auth))
  }

  /** Builds a view model for a signed-in user who already has saved preferences. */
  private suspend fun signedInViewModelWithPreferences(): AppViewModel {
    val user = UserAccount("user-1", null, null, null)
    authRepository.currentUser.value = user
    `when`(preferencesRepository.getPreferences(user.uid))
        .thenReturn(Result.success(UserPreferences(userId = user.uid)))
    return AppViewModel(authRepository, preferencesRepository)
  }
}
