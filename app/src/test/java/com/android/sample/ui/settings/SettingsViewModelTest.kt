// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.settings

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ImageCompressor
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.ProfilePictureError
import com.android.sample.model.profile.ProfilePictureException
import com.android.sample.model.profile.ProfilePictureRepository
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var authRepository: AuthRepository
  private lateinit var profileRepository: ProfileRepository
  private lateinit var profilePictureRepository: ProfilePictureRepository
  private lateinit var imageCompressor: ImageCompressor
  private val currentUserFlow = MutableStateFlow<UserAccount?>(null)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = mock(AuthRepository::class.java)
    profileRepository = mock(ProfileRepository::class.java)
    profilePictureRepository = mock(ProfilePictureRepository::class.java)
    imageCompressor = mock(ImageCompressor::class.java)
    `when`(authRepository.currentUserId).thenReturn(USER_ID)
    `when`(authRepository.currentUser).thenReturn(currentUserFlow)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun init_handlesNullUser_stopsLoadingAndNeverQueriesProfileRepository() = runTest {
    currentUserFlow.value = null

    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

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
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    assertEquals("Google Name", viewModel.uiState.value.username)

    // Simulate sign-out or session termination
    currentUserFlow.value = null

    val resetState = viewModel.uiState.value
    assertFalse(resetState.isLoading)
    assertNull(resetState.username)
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
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

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
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

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
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("john.doe", state.username)
    assertNull(state.profilePictureUrl)
  }

  @Test
  fun init_leavesUsernameNull_whenNoNameOrEmailAvailable() = runTest {
    val userAccount =
        UserAccount(uid = "user123", email = null, displayName = null, photoUrl = null)
    `when`(profileRepository.getProfile("user123")).thenReturn(Result.success(null))

    currentUserFlow.value = userAccount
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    // The screen, not the ViewModel, supplies the translated default name.
    assertNull(state.username)
    assertNull(state.profilePictureUrl)
  }

  @Test
  fun signOut_invokesAuthRepositorySignOut() = runTest {
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )
    var signOutSucceeded = false

    viewModel.signOut { signOutSucceeded = true }

    verify(authRepository).signOut()
    assertEquals(true, signOutSucceeded)
  }

  @Test
  fun clearError_clearsErrorMessageInState() = runTest {
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    viewModel.clearError()

    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun init_handlesExceptionInCurrentUserFlow_updatesErrorInState() = runTest {
    `when`(authRepository.currentUser).thenReturn(flow { throw RuntimeException("Flow error") })

    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    // The raw exception text is not exposed; the UI shows the translated message.
    assertEquals(SettingsError.LOAD_PROFILE_FAILED, state.error)
  }

  @Test
  fun init_handlesExceptionWithNullMessage_setsLoadProfileFailedError() = runTest {
    val exception = object : RuntimeException(null as String?) {}
    `when`(authRepository.currentUser).thenReturn(flow { throw exception })

    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(SettingsError.LOAD_PROFILE_FAILED, state.error)
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
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals("Google Name", state.username)
  }

  @Test
  fun signOut_failure_updatesErrorInState() = runTest {
    `when`(authRepository.signOut()).thenThrow(RuntimeException("Sign out failed"))
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    viewModel.signOut()

    val state = viewModel.uiState.value
    assertEquals(SettingsError.SIGN_OUT_FAILED, state.error)
  }

  @Test
  fun signOut_failureWithNullMessage_setsSignOutFailedError() = runTest {
    val exception = object : RuntimeException(null as String?) {}
    `when`(authRepository.signOut()).thenThrow(exception)
    val viewModel =
        SettingsViewModel(
            authRepository,
            profileRepository,
            profilePictureRepository,
            imageCompressor,
        )

    viewModel.signOut()

    val state = viewModel.uiState.value
    assertEquals(SettingsError.SIGN_OUT_FAILED, state.error)
  }

  // ---------------------------------------------------------------------------
  // Changing the profile picture
  // ---------------------------------------------------------------------------

  /** A picked image is compressed, uploaded and its URL saved; only then does the state change. */
  @Test
  fun onPictureSelected_compressesUploadsAndSavesUrl_thenUpdatesThePictureUrl() = runTest {
    stubPictureSteps()
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    val state = viewModel.uiState.value
    assertEquals(PICTURE_URL, state.profilePictureUrl)
    assertFalse(state.isUploadingPicture)
    assertNull(state.error)
    verify(profileRepository).updateProfilePictureUrl(USER_ID, PICTURE_URL)
  }

  /** A compression failure reports an error without uploading, and the next success clears it. */
  @Test
  fun onPictureSelected_compressionFailure_skipsUpload_andErrorClearsOnNextSuccess() = runTest {
    stubPictureSteps(
        compressed = Result.failure(ProfilePictureException(ProfilePictureError.INVALID_IMAGE))
    )
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    assertEquals(SettingsError.UPLOAD_PICTURE_FAILED, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isUploadingPicture)
    verify(profilePictureRepository, Mockito.never()).uploadProfilePicture(any(), any())

    stubPictureSteps()
    viewModel.onPictureSelected(PICKED_URI)

    assertNull(viewModel.uiState.value.error)
    assertEquals(PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
  }

  /** An upload failure reports an error and never saves a URL in the profile. */
  @Test
  fun onPictureSelected_uploadFailure_skipsSavingTheUrl() = runTest {
    stubPictureSteps(
        uploaded = Result.failure(ProfilePictureException(ProfilePictureError.STORAGE_FAILURE))
    )
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    assertEquals(SettingsError.UPLOAD_PICTURE_FAILED, viewModel.uiState.value.error)
    verify(profileRepository, Mockito.never()).updateProfilePictureUrl(any(), any())
  }

  /** A failed save is retried once, so a short-lived failure does not leave a dead URL behind. */
  @Test
  fun onPictureSelected_saveFailsOnce_retriesAndShowsTheNewPicture() = runTest {
    stubPictureSteps()
    `when`(profileRepository.updateProfilePictureUrl(USER_ID, PICTURE_URL))
        .thenReturn(Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE)))
        .thenReturn(Result.success(Unit))
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    verify(profileRepository, Mockito.times(2)).updateProfilePictureUrl(USER_ID, PICTURE_URL)
    assertNull(viewModel.uiState.value.error)
    assertEquals(PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
  }

  /** If the save fails twice, an error is reported and the state keeps the previous URL. */
  @Test
  fun onPictureSelected_saveFailsTwice_reportsErrorAndKeepsThePreviousPictureUrl() = runTest {
    stubPictureSteps(saved = Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE)))
    `when`(profileRepository.getProfile(USER_ID)).thenReturn(Result.success(null))
    currentUserFlow.value =
        UserAccount(uid = USER_ID, email = null, displayName = null, photoUrl = OLD_PICTURE_URL)
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    // One retry only: the save is attempted twice, not forever.
    verify(profileRepository, Mockito.times(2)).updateProfilePictureUrl(USER_ID, PICTURE_URL)
    assertEquals(SettingsError.UPLOAD_PICTURE_FAILED, viewModel.uiState.value.error)
    assertEquals(OLD_PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
  }

  /** A closed picker (null) does nothing, and a pick made during an upload is ignored. */
  @Test
  fun onPictureSelected_ignoresNullUri_andPicksMadeWhileUploading() = runTest {
    val compression = CompletableDeferred<Result<ByteArray>>()
    whenever(imageCompressor.compress(PICKED_URI)).doSuspendableAnswer { compression.await() }
    val viewModel = createViewModel()

    viewModel.onPictureSelected(null)
    verify(imageCompressor, Mockito.never()).compress(any())

    viewModel.onPictureSelected(PICKED_URI)
    assertTrue(viewModel.uiState.value.isUploadingPicture)
    viewModel.onPictureSelected(PICKED_URI)
    compression.complete(Result.failure(ProfilePictureException(ProfilePictureError.INVALID_IMAGE)))

    verify(imageCompressor, Mockito.times(1)).compress(PICKED_URI)
    assertFalse(viewModel.uiState.value.isUploadingPicture)
  }

  /** Without a signed-in user there is no profile to update, so an error is reported. */
  @Test
  fun onPictureSelected_reportsError_whenNoUserIsSignedIn() = runTest {
    `when`(authRepository.currentUserId).thenReturn(null)
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    assertEquals(SettingsError.UPLOAD_PICTURE_FAILED, viewModel.uiState.value.error)
    verify(imageCompressor, Mockito.never()).compress(any())
  }

  /** Creates the ViewModel with this test's mocked repositories and compressor. */
  private fun createViewModel() =
      SettingsViewModel(
          authRepository,
          profileRepository,
          profilePictureRepository,
          imageCompressor,
      )

  /** Makes each step of the picture change return the given result; all succeed by default. */
  private suspend fun stubPictureSteps(
      compressed: Result<ByteArray> = Result.success(JPEG_BYTES),
      uploaded: Result<String> = Result.success(PICTURE_URL),
      saved: Result<Unit> = Result.success(Unit),
  ) {
    `when`(imageCompressor.compress(PICKED_URI)).thenReturn(compressed)
    `when`(profilePictureRepository.uploadProfilePicture(USER_ID, JPEG_BYTES)).thenReturn(uploaded)
    `when`(profileRepository.updateProfilePictureUrl(USER_ID, PICTURE_URL)).thenReturn(saved)
  }

  private companion object {
    const val USER_ID = "user123"
    const val PICKED_URI = "content://media/picker/1"
    const val PICTURE_URL = "https://example.test/avatar.jpg"
    const val OLD_PICTURE_URL = "https://example.test/old.jpg"
    val JPEG_BYTES = byteArrayOf(1, 2, 3)
  }
}
