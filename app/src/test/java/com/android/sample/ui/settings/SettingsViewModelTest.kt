// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.settings

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    viewModel.signOut()

    verify(authRepository).signOut()
  }

  @Test
  fun clearError_clearsErrorMessageInState() = runTest {
    val viewModel = SettingsViewModel(authRepository, profileRepository)

    viewModel.clearError()

    assertNull(viewModel.uiState.value.error)
  }
}
