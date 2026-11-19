// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.UserProfile
import com.android.sample.model.profile.UsernamePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * UI tests for the "Edit username" dialog content, wired to a real [SettingsViewModel] with a
 * [FakeProfileRepository], so that opening, errors, disabled Save and closing are all covered.
 */
@RunWith(AndroidJUnit4::class)
class EditUsernameDialogTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var repository: FakeProfileRepository

  @Before
  fun setUp() {
    repository = FakeProfileRepository(UserProfile(uid = "u1", username = "alice"))
    val authRepository: AuthRepository = mock()
    whenever(authRepository.currentUser)
        .thenReturn(
            MutableStateFlow(
                UserAccount(uid = "u1", email = "a@b.c", displayName = "A", photoUrl = null)
            )
        )
    // Created once: building it inside setContent would make a new ViewModel on every
    // recomposition.
    val viewModel = SettingsViewModel(authRepository, repository)
    composeTestRule.setContent {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      // Same wiring as SettingsScreen, but the dialog content is shown inline: a text field inside
      // a dialog window never becomes idle under Robolectric.
      Box {
        SettingsScreenStateless(
            username = uiState.username.orEmpty(),
            isLoading = uiState.isLoading,
            onEditUsernameClick = viewModel::openUsernameEditor,
        )
        uiState.usernameEdit?.let { edit ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EditUsernameDialogContent(
                state = edit,
                onInputChange = viewModel::onUsernameInputChange,
                onSave = viewModel::saveUsername,
                onDismiss = viewModel::dismissUsernameEditor,
            )
          }
        }
      }
    }
  }

  private fun openDialog() {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SettingsScreenTestTags.GREETING)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_BUTTON).performClick()
  }

  private fun type(text: String) {
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_FIELD).performTextClearance()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_FIELD).performTextInput(text)
  }

  private fun assertError(error: UsernameError) {
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(error.toMessageRes()))
  }

  @Test
  fun tappingEditUsername_opensDialogPrefilledAndSaveDisabled() {
    openDialog()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_DIALOG).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_FIELD)
        .assertTextContains("alice")
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON)
        .assertIsNotEnabled()
  }

  @Test
  fun cancel_closesTheDialog() {
    openDialog()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_CANCEL_BUTTON).performClick()

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_DIALOG).assertDoesNotExist()
  }

  @Test
  fun validInput_enablesSave() {
    openDialog()
    type("bob")

    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON)
        .assertIsEnabled()
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_ERROR, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun emptyInput_disablesSave() {
    openDialog()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_FIELD).performTextClearance()

    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON)
        .assertIsNotEnabled()
  }

  @Test
  fun tooShortInput_showsErrorAndDisablesSave() {
    openDialog()
    type("ab")

    assertError(UsernameError.TOO_SHORT)
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON)
        .assertIsNotEnabled()
  }

  @Test
  fun invalidCharacters_showErrorAndDisableSave() {
    openDialog()
    type("bad name!")

    assertError(UsernameError.INVALID_CHARACTERS)
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON)
        .assertIsNotEnabled()
  }

  @Test
  fun field_limitsInputToMaxLength() {
    openDialog()
    type("a".repeat(UsernamePolicy.MAX_LENGTH + 5))

    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_FIELD)
        .assertTextContains("a".repeat(UsernamePolicy.MAX_LENGTH))
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_ERROR, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun takenName_showsTakenErrorAndKeepsDialogOpen() {
    repository.onUpdate = { Result.failure(ProfileException(ProfileError.USERNAME_TAKEN)) }
    openDialog()
    type("bob")
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON).performClick()

    composeTestRule.waitForIdle()
    assertError(UsernameError.TAKEN)
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_DIALOG).assertIsDisplayed()
  }

  @Test
  fun storageFailure_showsFailureError() {
    repository.onUpdate = { Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE)) }
    openDialog()
    type("bob")
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON).performClick()

    composeTestRule.waitForIdle()
    assertError(UsernameError.FAILURE)
  }

  @Test
  fun successfulSave_closesDialogAndShowsReturnedUsername() {
    openDialog()
    type("bob")
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON).performClick()

    composeTestRule.waitForIdle()
    assertEquals(listOf("bob"), repository.requestedUsernames)
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_DIALOG).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.GREETING)
        .assertTextContains("bob", substring = true)
  }
}
