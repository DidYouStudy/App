// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.ProfileException
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Signed-in auth fake for the Settings dialog tests. */
private class SignedInAuthRepository : AuthRepository {
  override val currentUser: Flow<UserAccount?> =
      MutableStateFlow(UserAccount("u1", null, null, null))
  override val isAuthenticated: Boolean
    get() = true

  override val currentUserId: String
    get() = "u1"

  override suspend fun signInWithGoogle(idToken: String): UserAccount = throw NotImplementedError()

  override suspend fun signOut() {}
}

/** Profile fake whose [updateUsername] result is chosen by the test. */
private class UsernameProfileRepository : ProfileRepository {
  var result: Result<UserProfile>? = null

  override suspend fun getProfile(userId: String): Result<UserProfile?> =
      Result.success(UserProfile(uid = userId, username = "alice"))

  override suspend fun createProfile(userId: String): Result<UserProfile> =
      Result.success(UserProfile(uid = userId, username = "alice"))

  override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> =
      result ?: Result.success(UserProfile(uid = userId, username = username))
}

/**
 * Instrumented tests of the real "Edit username" dialog window, opened from the stateful
 * [SettingsScreen]. Unit tests cover the dialog content only, because a text field in a dialog
 * window never becomes idle under Robolectric.
 */
class EditUsernameDialogInstrumentedTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val profileRepository = UsernameProfileRepository()

  @Before
  fun setUp() {
    // Created once: building it inside setContent would make a new ViewModel on each recomposition.
    val viewModel = SettingsViewModel(SignedInAuthRepository(), profileRepository)
    composeTestRule.setContent { SettingsScreen(viewModel = viewModel) }
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

  @Test
  fun editUsername_opensDialogPrefilledWithSaveDisabled() {
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
  fun invalidInput_showsErrorAndKeepsSaveDisabled() {
    openDialog()
    type("ab")

    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_ERROR, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON)
        .assertIsNotEnabled()
  }

  @Test
  fun takenName_showsErrorAndKeepsDialogOpen() {
    profileRepository.result = Result.failure(ProfileException(ProfileError.USERNAME_TAKEN))
    openDialog()
    type("bob")
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON)
        .assertIsEnabled()
        .performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SettingsScreenTestTags.EDIT_USERNAME_ERROR, useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_DIALOG).assertIsDisplayed()
  }

  @Test
  fun successfulSave_closesDialogAndShowsNewName() {
    openDialog()
    type("bob")
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_SAVE_BUTTON).performClick()

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SettingsScreenTestTags.EDIT_USERNAME_DIALOG)
          .fetchSemanticsNodes()
          .isEmpty()
    }
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.GREETING).assertTextContains("bob", true)
  }
}
