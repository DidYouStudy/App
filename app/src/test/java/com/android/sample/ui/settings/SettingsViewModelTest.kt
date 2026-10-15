// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.settings

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var authRepository: AuthRepository
  private lateinit var profileRepository: ProfileRepository
  private val currentUserFlow = MutableStateFlow<UserAccount?>(null)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = mock(AuthRepository::class.java)
    profileRepository = mock(ProfileRepository::class.java)
    `when`(authRepository.currentUser).thenReturn(currentUserFlow)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun init_handlesNullUser_stopsLoadingAndNeverQueriesProfileRepository() = runTest {
    currentUserFlow.value = null

    val viewModel = SettingsViewModel(authRepository, profileRepository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    verify(profileRepository, Mockito.never()).getProfile(any())
  }

  @Test
  fun currentUserEmitsNull_resetsUiStateToDefault() = runTest {
    val userAccount =
        UserAccount(
            uid = "user123",
            email = "test@example.com",
            displayName = "Google Name",
            photoUrl = "http://google.photo",
        )
    `when`(profileRepository.getProfile("user123")).thenReturn(Result.success(null))

    currentUserFlow.value = userAccount
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    assertEquals("Google Name", viewModel.uiState.value.username)

    // Simulate sign-out or session termination
    currentUserFlow.value = null

    val resetState = viewModel.uiState.value
    assertFalse(resetState.isLoading)
    assertEquals("", resetState.username)
    assertNull(resetState.profilePictureUrl)
    assertNull(resetState.error)
  }

  @Test
  fun init_loadsUserProfile_fromFirestore_whenCustomValuesExist() = runTest {
    val userAccount =
        UserAccount(
            uid = "user123",
            email = "test@example.com",
            displayName = "Google Name",
            photoUrl = "http://google.photo",
        )
    val customProfile =
        UserProfile(
            uid = "user123",
            username = "CustomHandle",
            profilePictureUrl = "http://custom.photo",
        )
    `when`(profileRepository.getProfile("user123")).thenReturn(Result.success(customProfile))

    currentUserFlow.value = userAccount
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("CustomHandle", state.username)
    assertEquals("http://custom.photo", state.profilePictureUrl)
  }

  @Test
  fun init_fallsBackToGoogleAuth_whenFirestoreValuesAreBlank() = runTest {
    val userAccount =
        UserAccount(
            uid = "user123",
            email = "test@example.com",
            displayName = "Google Name",
            photoUrl = "http://google.photo",
        )
    val emptyProfile = UserProfile(uid = "user123", username = "", profilePictureUrl = null)
    `when`(profileRepository.getProfile("user123")).thenReturn(Result.success(emptyProfile))

    currentUserFlow.value = userAccount
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("Google Name", state.username)
    assertEquals("http://google.photo", state.profilePictureUrl)
  }

  @Test
  fun init_fallsBackToEmailHandle_whenDisplayNameAndCustomUsernameAreNullOrBlank() = runTest {
    val userAccount =
        UserAccount(
            uid = "user123",
            email = "john.doe@example.com",
            displayName = null,
            photoUrl = null,
        )
    `when`(profileRepository.getProfile("user123")).thenReturn(Result.success(null))

    currentUserFlow.value = userAccount
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("john.doe", state.username)
    assertNull(state.profilePictureUrl)
  }

  @Test
  fun init_fallsBackToDefaultUser_whenNoNameOrEmailAvailable() = runTest {
    val userAccount =
        UserAccount(uid = "user123", email = null, displayName = null, photoUrl = null)
    `when`(profileRepository.getProfile("user123")).thenReturn(Result.success(null))

    currentUserFlow.value = userAccount
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("User", state.username)
    assertNull(state.profilePictureUrl)
  }

  @Test
  fun signOut_invokesAuthRepositorySignOut() = runTest {
    val viewModel = SettingsViewModel(authRepository, profileRepository)
    var signOutSucceeded = false

    viewModel.signOut { signOutSucceeded = true }

    verify(authRepository).signOut()
    assertEquals(true, signOutSucceeded)
  }

  @Test
  fun clearError_clearsErrorMessageInState() = runTest {
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    viewModel.clearError()

    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun init_handlesExceptionInCurrentUserFlow_updatesErrorInState() = runTest {
    `when`(authRepository.currentUser).thenReturn(flow { throw RuntimeException("Flow error") })

    val viewModel = SettingsViewModel(authRepository, profileRepository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("Flow error", state.error)
  }

  @Test
  fun init_handlesExceptionWithNullMessage_usesDefaultErrorMessage() = runTest {
    val exception = object : RuntimeException(null as String?) {}
    `when`(authRepository.currentUser).thenReturn(flow { throw exception })

    val viewModel = SettingsViewModel(authRepository, profileRepository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("Failed to load user profile", state.error)
  }

  @Test
  fun init_handlesProfileRepositoryFailure_fallsBackToGoogleAuth() = runTest {
    val userAccount =
        UserAccount(
            uid = "user123",
            email = "test@example.com",
            displayName = "Google Name",
            photoUrl = null,
        )
    `when`(profileRepository.getProfile("user123"))
        .thenReturn(Result.failure(RuntimeException("Firestore error")))

    currentUserFlow.value = userAccount
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("Google Name", state.username)
  }

  @Test
  fun signOut_failure_updatesErrorInState() = runTest {
    `when`(authRepository.signOut()).thenThrow(RuntimeException("Sign out failed"))
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    viewModel.signOut()

    val state = viewModel.uiState.value
    assertEquals("Sign out failed", state.error)
  }

  @Test
  fun signOut_failureWithNullMessage_usesDefaultErrorMessage() = runTest {
    val exception = object : RuntimeException(null as String?) {}
    `when`(authRepository.signOut()).thenThrow(exception)
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    viewModel.signOut()

    val state = viewModel.uiState.value
    assertEquals("Failed to sign out", state.error)
  }
}
