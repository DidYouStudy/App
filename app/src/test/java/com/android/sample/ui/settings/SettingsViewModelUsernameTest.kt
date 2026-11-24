// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.settings

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import com.android.sample.model.profile.UsernamePolicy
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Fake [ProfileRepository] whose [updateUsername] outcome is chosen by each test. */
class FakeProfileRepository(private val profile: UserProfile?) : ProfileRepository {
  /** Usernames received by [updateUsername], in call order. */
  val requestedUsernames = mutableListOf<String>()

  /** Produces the result of the next [updateUsername] call; may suspend or throw. */
  var onUpdate: suspend (String) -> Result<UserProfile> = { name ->
    Result.success(UserProfile(uid = "u1", username = name))
  }

  override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(profile)

  override suspend fun createProfile(userId: String): Result<UserProfile> =
      Result.success(profile ?: UserProfile(uid = userId))

  override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> {
    requestedUsernames += username
    return onUpdate(username)
  }

  override suspend fun updateProfilePictureUrl(userId: String, url: String): Result<Unit> =
      Result.failure(UnsupportedOperationException("Not needed in this test"))
}

/** Unit tests for the "Edit username" logic of [SettingsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelUsernameTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val currentUser = MutableStateFlow<UserAccount?>(null)
  private lateinit var authRepository: AuthRepository
  private lateinit var repository: FakeProfileRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = mock()
    whenever(authRepository.currentUser).thenReturn(currentUser)
    repository = FakeProfileRepository(UserProfile(uid = "u1", username = "alice"))
    currentUser.value = UserAccount(uid = "u1", email = "a@b.c", displayName = "A", photoUrl = null)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun viewModel() = SettingsViewModel(authRepository, repository)

  /** Opens the dialog and types [input]. */
  private fun SettingsViewModel.type(input: String) {
    openUsernameEditor()
    onUsernameInputChange(input)
  }

  private fun SettingsViewModel.edit() = uiState.value.usernameEdit!!

  // --- Opening and closing -----------------------------------------------------------------

  @Test
  fun open_prefillsCurrentUsername_withSaveDisabled() = runTest {
    val vm = viewModel()
    vm.openUsernameEditor()

    assertEquals("alice", vm.edit().input)
    assertNull(vm.edit().error)
    assertFalse(vm.edit().canSave)
  }

  @Test
  fun open_doesNothing_whenNobodyIsSignedIn() = runTest {
    currentUser.value = null
    val vm = viewModel()
    vm.openUsernameEditor()

    assertNull(vm.uiState.value.usernameEdit)
  }

  @Test
  fun dismiss_closesTheDialog() = runTest {
    val vm = viewModel()
    vm.openUsernameEditor()
    vm.dismissUsernameEditor()

    assertNull(vm.uiState.value.usernameEdit)
  }

  @Test
  fun signingOut_closesTheDialog() = runTest {
    val vm = viewModel()
    vm.openUsernameEditor()
    currentUser.value = null

    assertNull(vm.uiState.value.usernameEdit)
  }

  // --- Input rules -------------------------------------------------------------------------

  @Test
  fun validDifferentInput_enablesSave() = runTest {
    val vm = viewModel()
    vm.type("bob_2")

    assertNull(vm.edit().error)
    assertTrue(vm.edit().canSave)
  }

  @Test
  fun caseOnlyChange_isTreatedAsAChange() = runTest {
    val vm = viewModel()
    vm.type("Alice")

    assertTrue(vm.edit().canSave)
  }

  @Test
  fun emptyInput_disablesSaveWithoutError() = runTest {
    val vm = viewModel()
    vm.type("")

    assertNull(vm.edit().error)
    assertFalse(vm.edit().canSave)
  }

  @Test
  fun unchangedInput_disablesSave() = runTest {
    val vm = viewModel()
    vm.type("alice")

    assertFalse(vm.edit().canSave)
  }

  @Test
  fun tooShortInput_showsErrorAndDisablesSave() = runTest {
    val vm = viewModel()
    vm.type("ab")

    assertEquals(UsernameError.TOO_SHORT, vm.edit().error)
    assertFalse(vm.edit().canSave)
  }

  @Test
  fun invalidCharacters_showErrorAndDisableSave() = runTest {
    val vm = viewModel()
    vm.type("bad name!")

    assertEquals(UsernameError.INVALID_CHARACTERS, vm.edit().error)
    assertFalse(vm.edit().canSave)
  }

  @Test
  fun input_isCappedAtMaxLength() = runTest {
    val vm = viewModel()
    vm.type("a".repeat(UsernamePolicy.MAX_LENGTH + 5))

    assertEquals(UsernamePolicy.MAX_LENGTH, vm.edit().input.length)
    assertNull(vm.edit().error)
    assertTrue(vm.edit().canSave)
  }

  @Test
  fun leadingWhitespace_isDroppedAndDoesNotUseUpTheLimit() = runTest {
    val vm = viewModel()
    vm.type("     " + "a".repeat(UsernamePolicy.MAX_LENGTH))

    assertEquals("a".repeat(UsernamePolicy.MAX_LENGTH), vm.edit().input)
    assertNull(vm.edit().error)
  }

  @Test
  fun inputChange_ignored_whenDialogIsClosed() = runTest {
    val vm = viewModel()
    vm.onUsernameInputChange("bob")

    assertNull(vm.uiState.value.usernameEdit)
  }

  // --- Saving ------------------------------------------------------------------------------

  @Test
  fun save_success_showsReturnedUsername_andClosesDialog() = runTest {
    repository.onUpdate = { Result.success(UserProfile(uid = "u1", username = "Bob")) }
    val vm = viewModel()
    vm.type("  Bob  ")
    vm.saveUsername()

    assertEquals(listOf("Bob"), repository.requestedUsernames)
    assertEquals("Bob", vm.uiState.value.username)
    assertNull(vm.uiState.value.usernameEdit)
  }

  @Test
  fun save_whenNotAllowed_doesNotCallRepository() = runTest {
    val vm = viewModel()
    vm.type("alice") // unchanged
    vm.saveUsername()
    vm.type("ab") // invalid
    vm.saveUsername()

    assertTrue(repository.requestedUsernames.isEmpty())
  }

  @Test
  fun save_taken_keepsDialogOpenWithTakenError() = runTest {
    repository.onUpdate = { Result.failure(ProfileException(ProfileError.USERNAME_TAKEN)) }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    assertEquals(UsernameError.TAKEN, vm.edit().error)
    assertFalse(vm.edit().isSaving)
    assertFalse(vm.edit().canSave)
    assertEquals("alice", vm.uiState.value.username)
  }

  @Test
  fun save_invalidUsernameFromRepository_showsInvalidCharactersError() = runTest {
    repository.onUpdate = { Result.failure(ProfileException(ProfileError.INVALID_USERNAME)) }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    assertEquals(UsernameError.INVALID_CHARACTERS, vm.edit().error)
  }

  @Test
  fun save_storageFailure_showsFailureError_andAllowsRetry() = runTest {
    repository.onUpdate = { Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE)) }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    assertEquals(UsernameError.FAILURE, vm.edit().error)
    // A storage failure may be temporary: the same name can be saved again without editing it.
    assertTrue(vm.edit().canSave)
    vm.saveUsername()
    assertEquals(listOf("bob", "bob"), repository.requestedUsernames)

    // Typing again clears the error and re-enables Save.
    vm.onUsernameInputChange("bobby")
    assertNull(vm.edit().error)
    assertTrue(vm.edit().canSave)
  }

  @Test
  fun save_offline_showsOfflineError_keepsDialogOpen_andAllowsRetryOfSameName() = runTest {
    repository.onUpdate = { Result.failure(ProfileException(ProfileError.OFFLINE)) }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    assertEquals(UsernameError.OFFLINE, vm.edit().error)
    assertFalse(vm.edit().isSaving)
    assertEquals("bob", vm.edit().input)
    assertEquals("alice", vm.uiState.value.username)
    // The same name can be retried once the connection is back, and then succeeds.
    assertTrue(vm.edit().canSave)
    repository.onUpdate = { Result.success(UserProfile(uid = "u1", username = it)) }
    vm.saveUsername()
    assertEquals(listOf("bob", "bob"), repository.requestedUsernames)
    assertNull(vm.uiState.value.usernameEdit)
    assertEquals("bob", vm.uiState.value.username)
  }

  @Test
  fun save_missingProfile_showsProfileUnavailable_withoutRetry() = runTest {
    repository.onUpdate = { Result.failure(ProfileException(ProfileError.INVALID_PROFILE_DATA)) }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    assertEquals(UsernameError.PROFILE_UNAVAILABLE, vm.edit().error)
    assertFalse(vm.edit().canSave)
  }

  @Test
  fun save_resultArrivingAfterSignOut_isIgnored() = runTest {
    val gate = CompletableDeferred<Result<UserProfile>>()
    repository.onUpdate = { gate.await() }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()
    currentUser.value = null

    gate.complete(Result.success(UserProfile(uid = "u1", username = "bob")))

    assertNull(vm.uiState.value.username)
    assertNull(vm.uiState.value.usernameEdit)
  }

  @Test
  fun save_unexpectedException_showsFailureError() = runTest {
    repository.onUpdate = { throw IllegalStateException("boom") }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    assertEquals(UsernameError.FAILURE, vm.edit().error)
  }

  @Test
  fun save_nonProfileFailure_showsFailureError() = runTest {
    repository.onUpdate = { Result.failure(RuntimeException("network")) }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    assertEquals(UsernameError.FAILURE, vm.edit().error)
  }

  @Test
  fun whileSaving_saveIsDisabled_andInputAndDismissAreIgnored() = runTest {
    val gate = CompletableDeferred<Result<UserProfile>>()
    repository.onUpdate = { gate.await() }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    assertTrue(vm.edit().isSaving)
    assertFalse(vm.edit().canSave)

    vm.saveUsername()
    vm.onUsernameInputChange("other")
    vm.dismissUsernameEditor()
    assertEquals(listOf("bob"), repository.requestedUsernames)
    assertEquals("bob", vm.edit().input)
    assertNotNull(vm.uiState.value.usernameEdit)

    gate.complete(Result.success(UserProfile(uid = "u1", username = "bob")))
    assertNull(vm.uiState.value.usernameEdit)
    assertEquals("bob", vm.uiState.value.username)
  }

  @Test
  fun save_whenDialogIsClosed_doesNothing() = runTest {
    val vm = viewModel()
    vm.saveUsername()

    assertTrue(repository.requestedUsernames.isEmpty())
  }

  @Test
  fun save_whenSignedOutAfterOpening_doesNothing() = runTest {
    val vm = viewModel()
    vm.type("bob")
    currentUser.value = null
    vm.saveUsername()

    assertTrue(repository.requestedUsernames.isEmpty())
  }

  @Test
  fun save_cancelled_doesNotReportAnError() = runTest {
    repository.onUpdate = { throw kotlinx.coroutines.CancellationException("cancelled") }
    val vm = viewModel()
    vm.type("bob")
    vm.saveUsername()

    // Cancellation is propagated, not turned into a failure shown to the user.
    assertNull(vm.edit().error)
  }

  @Test
  fun open_whileProfileIsLoading_doesNothing() = runTest {
    val vm = SettingsViewModel(authRepository, repository)
    currentUser.value = null
    // A signed-out ViewModel finishes loading but has no user to edit.
    vm.openUsernameEditor()

    assertNull(vm.uiState.value.usernameEdit)
  }

  // --- Pure helpers ------------------------------------------------------------------------

  @Test
  fun validateUsernameInput_coversEveryRule() {
    assertNull(validateUsernameInput(""))
    assertNull(validateUsernameInput("   "))
    assertNull(validateUsernameInput("abc"))
    assertNull(validateUsernameInput("a_b.c-d9"))
    assertNull(validateUsernameInput("a".repeat(UsernamePolicy.MAX_LENGTH)))
    assertEquals(UsernameError.TOO_SHORT, validateUsernameInput("ab"))
    assertEquals(
        UsernameError.TOO_LONG,
        validateUsernameInput("a".repeat(UsernamePolicy.MAX_LENGTH + 1)),
    )
    assertEquals(UsernameError.INVALID_CHARACTERS, validateUsernameInput("_abc"))
    assertEquals(UsernameError.INVALID_CHARACTERS, validateUsernameInput("ab c"))
    assertEquals(UsernameError.INVALID_CHARACTERS, validateUsernameInput("é-abc"))
    assertEquals(UsernameError.INVALID_CHARACTERS, validateUsernameInput("ab!"))
  }

  @Test
  fun toUsernameError_mapsRepositoryCategories() {
    assertEquals(UsernameError.TAKEN, ProfileError.USERNAME_TAKEN.toUsernameError())
    assertEquals(UsernameError.INVALID_CHARACTERS, ProfileError.INVALID_USERNAME.toUsernameError())
    assertEquals(
        UsernameError.PROFILE_UNAVAILABLE,
        ProfileError.INVALID_PROFILE_DATA.toUsernameError(),
    )
    assertEquals(UsernameError.OFFLINE, ProfileError.OFFLINE.toUsernameError())
    assertEquals(UsernameError.FAILURE, ProfileError.FIRESTORE_FAILURE.toUsernameError())
    assertEquals(UsernameError.FAILURE, ProfileError.UNKNOWN.toUsernameError())
  }

  @Test
  fun everyUsernameError_hasAMessage() {
    val messages = UsernameError.entries.map { it.toMessageRes() }
    assertEquals(UsernameError.entries.size, messages.toSet().size)
  }
}
