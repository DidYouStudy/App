// Co-authored-by: Claude AI Agent
package com.android.sample.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.ui.navigation.NavigationTestTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * UI tests for the Settings screen.
 *
 * The first group exercises the stateless UI with explicit state, the second group exercises the
 * stateful [SettingsScreen] wired to a real [SettingsViewModel] backed by mocked repositories.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private lateinit var authRepository: AuthRepository
  private lateinit var profileRepository: ProfileRepository

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Shows the stateless screen; every callback records its name in [events]. */
  private fun setStatelessContent(
      events: MutableList<String>,
      username: String = "Alice",
      isLoading: Boolean = false,
  ) {
    composeTestRule.setContent {
      SettingsScreenStateless(
          username = username,
          isLoading = isLoading,
          onBackClick = { events += "back" },
          onEditUsernameClick = { events += "editUsername" },
          onChangeProfilePictureClick = { events += "changeProfilePicture" },
          onModifyPreferencesClick = { events += "preferences" },
          onManageFriendsClick = { events += "friends" },
          onLogoutClick = { events += "logout" },
      )
    }
  }

  /** Shows the stateful screen; every navigation callback records its name in [events]. */
  private fun setStatefulContent(
      viewModel: SettingsViewModel,
      events: MutableList<String> = mutableListOf(),
  ) {
    composeTestRule.setContent {
      SettingsScreen(
          viewModel = viewModel,
          onBackClick = { events += "back" },
          onEditUsernameClick = { events += "editUsername" },
          onChangeProfilePictureClick = { events += "changeProfilePicture" },
          onModifyPreferencesClick = { events += "preferences" },
          onManageFriendsClick = { events += "friends" },
      )
    }
  }

  private fun createViewModel() = SettingsViewModel(authRepository, profileRepository)

  /** Makes the mocked repository's suspending [AuthRepository.signOut] throw [error]. */
  private fun makeSignOutFail(error: Throwable) {
    runBlocking { whenever(authRepository.signOut()).thenThrow(error) }
  }

  private fun waitForText(text: String) {
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun waitForTextToDisappear(text: String) {
    composeTestRule.waitUntil(timeoutMillis = 15_000) {
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }
  }

  @Before
  fun setUp() {
    authRepository = mock()
    profileRepository = mock()
    // No signed-in user: the ViewModel finishes loading right away.
    whenever(authRepository.currentUser).thenReturn(MutableStateFlow(null))
  }

  // ---------------------------------------------------------------------------
  // Stateless screen: what is displayed
  // ---------------------------------------------------------------------------

  @Test
  fun headerShowsSettingsTitleAndBackButton() {
    setStatelessContent(mutableListOf())

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.settings_header))
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()
  }

  @Test
  fun loadedStateShowsProfileCardWithDefaultAvatarAndGreeting() {
    setStatelessContent(mutableListOf(), username = "Alice")

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.PROFILE_CARD).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.AVATAR).assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.settings_profile_picture))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.GREETING)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.settings_greeting, "Alice"))
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOADING_INDICATOR).assertDoesNotExist()
  }

  @Test
  fun loadingStateShowsSpinnerInsteadOfGreetingAndKeepsActionsAvailable() {
    setStatelessContent(mutableListOf(), isLoading = true)

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.GREETING).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON).assertIsDisplayed()
  }

  @Test
  fun usesDefaultUsernameWhenNoneIsProvided() {
    composeTestRule.setContent { SettingsScreenStateless(isLoading = false) }

    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.GREETING)
        .assertTextEquals(
            context.getString(
                R.string.settings_greeting,
                context.getString(R.string.default_username),
            )
        )
  }

  @Test
  fun actionButtonsShowTheirLabels() {
    setStatelessContent(mutableListOf())

    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.settings_edit_username))
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.CHANGE_PROFILE_PICTURE_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.settings_change_profile_picture))
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.settings_preferences_button))
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.FRIENDS_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.settings_friends_button))
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.settings_logout_button))
  }

  // ---------------------------------------------------------------------------
  // Stateless screen: each button triggers exactly its own callback
  // ---------------------------------------------------------------------------

  @Test
  fun backButtonInvokesOnlyTheBackCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(listOf("back"), events)
  }

  @Test
  fun editUsernameInvokesOnlyItsCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_BUTTON).performClick()

    assertEquals(listOf("editUsername"), events)
  }

  @Test
  fun changeProfilePictureInvokesOnlyItsCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.CHANGE_PROFILE_PICTURE_BUTTON)
        .performClick()

    assertEquals(listOf("changeProfilePicture"), events)
  }

  @Test
  fun preferencesButtonInvokesOnlyItsCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()

    assertEquals(listOf("preferences"), events)
  }

  @Test
  fun friendsButtonInvokesOnlyItsCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.FRIENDS_BUTTON).performClick()

    assertEquals(listOf("friends"), events)
  }

  @Test
  fun logoutButtonInvokesOnlyTheLogoutCallback() {
    val events = mutableListOf<String>()
    setStatelessContent(events)

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON).performClick()

    assertEquals(listOf("logout"), events)
  }

  @Test
  fun actionsWithDefaultCallbacksDoNothingAndDoNotCrash() {
    composeTestRule.setContent { SettingsScreenStateless(isLoading = false) }

    listOf(
            NavigationTestTags.GO_BACK_BUTTON,
            SettingsScreenTestTags.EDIT_USERNAME_BUTTON,
            SettingsScreenTestTags.CHANGE_PROFILE_PICTURE_BUTTON,
            SettingsScreenTestTags.PREFERENCES_BUTTON,
            SettingsScreenTestTags.FRIENDS_BUTTON,
            SettingsScreenTestTags.LOGOUT_BUTTON,
        )
        .forEach { tag -> composeTestRule.onNodeWithTag(tag).performClick() }

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------
  // Stateful screen: wiring to the ViewModel
  // ---------------------------------------------------------------------------

  @Test
  fun statefulScreenForwardsEveryActionToItsCallback() {
    val events = mutableListOf<String>()
    setStatefulContent(createViewModel(), events)

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.EDIT_USERNAME_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(SettingsScreenTestTags.CHANGE_PROFILE_PICTURE_BUTTON)
        .performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.PREFERENCES_BUTTON).performClick()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.FRIENDS_BUTTON).performClick()

    assertEquals(
        listOf("back", "editUsername", "changeProfilePicture", "preferences", "friends"),
        events,
    )
  }

  @Test
  fun finishedLoadingHidesSpinnerAndShowsGreeting() {
    setStatefulContent(createViewModel())

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule
          .onAllNodesWithTag(SettingsScreenTestTags.LOADING_INDICATOR)
          .fetchSemanticsNodes()
          .isEmpty()
    }

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOADING_INDICATOR).assertDoesNotExist()
    composeTestRule.onNodeWithTag(SettingsScreenTestTags.GREETING).assertIsDisplayed()
  }

  @Test
  fun logoutButtonSignsOutThroughTheRepository() {
    setStatefulContent(createViewModel())

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON).performClick()
    composeTestRule.waitForIdle()

    verifyBlocking(authRepository) { signOut() }
  }

  @Test
  fun failedSignOutShowsTheErrorMessageInSnackbar() {
    makeSignOutFail(RuntimeException(SIGN_OUT_ERROR))
    setStatefulContent(createViewModel())

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON).performClick()

    waitForText(SIGN_OUT_ERROR)
    composeTestRule.onAllNodesWithText(SIGN_OUT_ERROR)[0].assertIsDisplayed()
  }

  @Test
  fun failedSignOutWithoutMessageShowsTheDefaultError() {
    makeSignOutFail(RuntimeException())
    setStatefulContent(createViewModel())

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON).performClick()

    waitForText(SettingsViewModel.FAILED_TO_SIGN_OUT_ERROR)
    composeTestRule
        .onAllNodesWithText(SettingsViewModel.FAILED_TO_SIGN_OUT_ERROR)[0]
        .assertIsDisplayed()
  }

  @Test
  fun errorIsClearedFromTheViewModelOnceTheSnackbarIsDismissed() {
    makeSignOutFail(RuntimeException(SIGN_OUT_ERROR))
    val viewModel = createViewModel()
    setStatefulContent(viewModel)

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.LOGOUT_BUTTON).performClick()
    waitForText(SIGN_OUT_ERROR)

    // The snackbar times out on its own, after which the screen asks the ViewModel to clear it.
    composeTestRule.waitUntil(timeoutMillis = 15_000) { viewModel.uiState.value.error == null }
    waitForTextToDisappear(SIGN_OUT_ERROR)

    assertNull(viewModel.uiState.value.error)
  }

  private companion object {
    const val SIGN_OUT_ERROR = "Sign out failed (test)"
  }
}
