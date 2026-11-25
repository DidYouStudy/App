// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.settings

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.connectivity.ConnectivityObserver
import com.android.sample.model.connectivity.ONLINE_WRITE_TIMEOUT
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
import kotlinx.coroutines.awaitCancellation
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
import org.mockito.kotlin.anyOrNull
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
    verify(profilePictureRepository, Mockito.never())
        .uploadProfilePicture(any(), any(), anyOrNull())

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

  /**
   * If saving the URL fails, an error is reported and the state keeps the previous URL. That URL
   * still works, because the upload was told not to replace the file it points to.
   */
  @Test
  fun onPictureSelected_saveFailure_keepsThePreviousPictureUrl() = runTest {
    stubPictureSteps(
        currentUrl = OLD_PICTURE_URL,
        saved = Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE)),
    )
    `when`(profileRepository.getProfile(USER_ID)).thenReturn(Result.success(null))
    currentUserFlow.value =
        UserAccount(uid = USER_ID, email = null, displayName = null, photoUrl = OLD_PICTURE_URL)
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    // The save is attempted once: there is no retry.
    verify(profileRepository, Mockito.times(1)).updateProfilePictureUrl(USER_ID, PICTURE_URL)
    assertEquals(SettingsError.UPLOAD_PICTURE_FAILED, viewModel.uiState.value.error)
    assertEquals(OLD_PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
  }

  /** The upload is told which picture the profile stores now, so that it does not replace it. */
  @Test
  fun onPictureSelected_passesTheStoredPictureUrlToTheUpload() = runTest {
    `when`(profileRepository.getProfile(USER_ID))
        .thenReturn(
            Result.success(
                UserProfile(uid = USER_ID, username = "Name", profilePictureUrl = OLD_PICTURE_URL)
            )
        )
    currentUserFlow.value =
        UserAccount(uid = USER_ID, email = null, displayName = null, photoUrl = null)
    stubPictureSteps(currentUrl = OLD_PICTURE_URL)
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    verify(profilePictureRepository).uploadProfilePicture(USER_ID, JPEG_BYTES, OLD_PICTURE_URL)
    assertEquals(PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
  }

  /** The picture that replaced the first one is the "current" one for the next change. */
  @Test
  fun onPictureSelected_secondChange_passesTheFirstNewUrlAsCurrent() = runTest {
    stubPictureSteps()
    val viewModel = createViewModel()
    viewModel.onPictureSelected(PICKED_URI)
    `when`(profilePictureRepository.uploadProfilePicture(USER_ID, JPEG_BYTES, PICTURE_URL))
        .thenReturn(Result.success(SECOND_PICTURE_URL))
    `when`(profileRepository.updateProfilePictureUrl(USER_ID, SECOND_PICTURE_URL))
        .thenReturn(Result.success(Unit))

    viewModel.onPictureSelected(PICKED_URI)

    verify(profilePictureRepository).uploadProfilePicture(USER_ID, JPEG_BYTES, PICTURE_URL)
    assertEquals(SECOND_PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
  }

  /**
   * If another account signs in during an upload, the result is not written to the state, which now
   * shows that account. The picture is still saved for the user who picked it, with that user's own
   * current picture, and the new account is not left waiting for the upload.
   */
  @Test
  fun onPictureSelected_accountSwitchedDuringUpload_discardsTheResult() = runTest {
    val compression = CompletableDeferred<Result<ByteArray>>()
    val viewModel = startPendingPictureChange(compression)

    switchToOtherAccount()
    compression.complete(Result.success(JPEG_BYTES))

    val state = viewModel.uiState.value
    assertEquals(OTHER_PICTURE_URL, state.profilePictureUrl)
    assertEquals("Other", state.username)
    assertNull(state.error)
    assertFalse(state.isUploadingPicture)
    // The upload got the picture of the user who picked, not of the account shown by then.
    verify(profilePictureRepository).uploadProfilePicture(USER_ID, JPEG_BYTES, OLD_PICTURE_URL)
    verify(profileRepository).updateProfilePictureUrl(USER_ID, PICTURE_URL)
    verify(profileRepository, Mockito.never()).updateProfilePictureUrl(OTHER_USER_ID, PICTURE_URL)
  }

  /** A sign-out during an upload also discards the result: nobody is shown a picture. */
  @Test
  fun onPictureSelected_signedOutDuringUpload_discardsTheResult() = runTest {
    val compression = CompletableDeferred<Result<ByteArray>>()
    val viewModel = startPendingPictureChange(compression)

    `when`(authRepository.currentUserId).thenReturn(null)
    currentUserFlow.value = null
    compression.complete(Result.success(JPEG_BYTES))

    assertEquals(SettingsUiState(isLoading = false), viewModel.uiState.value)
    verify(profileRepository).updateProfilePictureUrl(USER_ID, PICTURE_URL)
  }

  /** A failure that arrives after an account switch is not reported to the new account. */
  @Test
  fun onPictureSelected_failureAfterAccountSwitch_showsNoErrorToTheNewAccount() = runTest {
    val compression = CompletableDeferred<Result<ByteArray>>()
    val viewModel = startPendingPictureChange(compression)

    switchToOtherAccount()
    compression.complete(Result.failure(ProfilePictureException(ProfilePictureError.INVALID_IMAGE)))

    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isUploadingPicture)
    assertEquals(OTHER_PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
  }

  /**
   * The same user being emitted again, for example after a profile update, keeps the upload shown.
   */
  @Test
  fun sameUserEmittedDuringUpload_keepsTheUploadIndicator() = runTest {
    val compression = CompletableDeferred<Result<ByteArray>>()
    val viewModel = startPendingPictureChange(compression)

    currentUserFlow.value =
        UserAccount(uid = USER_ID, email = null, displayName = "Renamed", photoUrl = null)

    assertTrue(viewModel.uiState.value.isUploadingPicture)
    compression.complete(Result.success(JPEG_BYTES))
    assertFalse(viewModel.uiState.value.isUploadingPicture)
    assertEquals(PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
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

  /** A step that throws instead of returning a failure is reported like any other failure. */
  @Test
  fun onPictureSelected_stepThrows_reportsTheError() = runTest {
    stubPictureSteps()
    whenever(profilePictureRepository.uploadProfilePicture(USER_ID, JPEG_BYTES, null))
        .doSuspendableAnswer { throw IllegalStateException("boom") }
    val viewModel = createViewModel()

    viewModel.onPictureSelected(PICKED_URI)

    assertFalse(viewModel.uiState.value.isUploadingPicture)
    assertEquals(SettingsError.UPLOAD_PICTURE_FAILED, viewModel.uiState.value.error)
    assertNull(viewModel.uiState.value.profilePictureUrl)
  }

  /** An upload that never answers is given up after the timeout and no new picture is shown. */
  @Test
  fun onPictureSelected_stalledUpload_failsAfterTheTimeout() = runTest {
    stubPictureSteps()
    whenever(profilePictureRepository.uploadProfilePicture(USER_ID, JPEG_BYTES, null))
        .doSuspendableAnswer { awaitCancellation() }
    val viewModel = createViewModel()
    viewModel.onPictureSelected(PICKED_URI)

    testScheduler.advanceTimeBy(ONLINE_WRITE_TIMEOUT.inWholeMilliseconds - 1)
    assertTrue(viewModel.uiState.value.isUploadingPicture)
    testScheduler.advanceTimeBy(2)

    assertFalse(viewModel.uiState.value.isUploadingPicture)
    assertEquals(SettingsError.UPLOAD_PICTURE_FAILED, viewModel.uiState.value.error)
    assertNull(viewModel.uiState.value.profilePictureUrl)
  }

  /** Losing the connection ends the change at once, without waiting for the timeout. */
  @Test
  fun onPictureSelected_goingOffline_failsWithoutWaitingForTheTimeout() = runTest {
    val isOnline = MutableStateFlow(true)
    val viewModel =
        startPendingPictureChange(CompletableDeferred(), ConnectivityObserver { isOnline })

    isOnline.value = false

    assertFalse(viewModel.uiState.value.isUploadingPicture)
    assertEquals(SettingsError.UPLOAD_PICTURE_FAILED, viewModel.uiState.value.error)
    assertEquals(OLD_PICTURE_URL, viewModel.uiState.value.profilePictureUrl)
  }

  /** Creates the ViewModel with this test's mocked repositories and compressor. */
  private fun createViewModel(connectivityObserver: ConnectivityObserver? = null) =
      SettingsViewModel(
          authRepository,
          profileRepository,
          profilePictureRepository,
          imageCompressor,
          connectivityObserver,
      )

  /**
   * Shows [USER_ID] with a stored picture and starts a picture change for it whose compression
   * stays pending until [compression] is completed, so the test decides what happens meanwhile.
   *
   * @param compression completed by the test to let the pending compression finish.
   * @param connectivityObserver connectivity seen by the ViewModel, if any.
   * @return the ViewModel, with the upload indicator on.
   */
  private suspend fun startPendingPictureChange(
      compression: CompletableDeferred<Result<ByteArray>>,
      connectivityObserver: ConnectivityObserver? = null,
  ): SettingsViewModel {
    `when`(profileRepository.getProfile(USER_ID))
        .thenReturn(
            Result.success(
                UserProfile(uid = USER_ID, username = "Name", profilePictureUrl = OLD_PICTURE_URL)
            )
        )
    currentUserFlow.value =
        UserAccount(uid = USER_ID, email = null, displayName = null, photoUrl = null)
    stubPictureSteps(currentUrl = OLD_PICTURE_URL)
    whenever(imageCompressor.compress(PICKED_URI)).doSuspendableAnswer { compression.await() }
    val viewModel = createViewModel(connectivityObserver)
    viewModel.onPictureSelected(PICKED_URI)
    assertTrue(viewModel.uiState.value.isUploadingPicture)
    return viewModel
  }

  /** Signs [OTHER_USER_ID], who has a picture of their own, in place of [USER_ID]. */
  private suspend fun switchToOtherAccount() {
    `when`(authRepository.currentUserId).thenReturn(OTHER_USER_ID)
    `when`(profileRepository.getProfile(OTHER_USER_ID))
        .thenReturn(
            Result.success(
                UserProfile(
                    uid = OTHER_USER_ID,
                    username = "Other",
                    profilePictureUrl = OTHER_PICTURE_URL,
                )
            )
        )
    currentUserFlow.value =
        UserAccount(uid = OTHER_USER_ID, email = null, displayName = null, photoUrl = null)
  }

  /**
   * Makes each step of the picture change return the given result; all succeed by default.
   *
   * @param currentUrl the picture URL the ViewModel is expected to pass to the upload: the one the
   *   profile shows when the picture is changed, null when it has none.
   */
  private suspend fun stubPictureSteps(
      compressed: Result<ByteArray> = Result.success(JPEG_BYTES),
      uploaded: Result<String> = Result.success(PICTURE_URL),
      saved: Result<Unit> = Result.success(Unit),
      currentUrl: String? = null,
  ) {
    `when`(imageCompressor.compress(PICKED_URI)).thenReturn(compressed)
    `when`(profilePictureRepository.uploadProfilePicture(USER_ID, JPEG_BYTES, currentUrl))
        .thenReturn(uploaded)
    `when`(profileRepository.updateProfilePictureUrl(USER_ID, PICTURE_URL)).thenReturn(saved)
  }

  private companion object {
    const val USER_ID = "user123"
    const val OTHER_USER_ID = "other456"
    const val OTHER_PICTURE_URL = "https://example.test/other.jpg"
    const val PICKED_URI = "content://media/picker/1"
    const val PICTURE_URL = "https://example.test/avatar.jpg"
    const val OLD_PICTURE_URL = "https://example.test/old.jpg"
    const val SECOND_PICTURE_URL = "https://example.test/avatar2.jpg"
    val JPEG_BYTES = byteArrayOf(1, 2, 3)
  }
}
