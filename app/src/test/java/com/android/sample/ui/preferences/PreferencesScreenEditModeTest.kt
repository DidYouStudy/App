// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.preferences

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.ui.auth.TestAuthRepository
import com.android.sample.ui.navigation.NavigationTestTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

/** Checks the onboarding and edit-mode variants of [PreferencesScreen]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PreferencesScreenEditModeTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val userId = "user-1"
  private lateinit var repository: PreferencesRepository
  private lateinit var viewModel: PreferencesViewModel

  @Before
  fun setUp() = runTest {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    repository = mock(PreferencesRepository::class.java)
    val preferences = UserPreferences(userId = userId)
    `when`(repository.getPreferences(userId)).thenReturn(Result.success(preferences))
    `when`(repository.savePreferences(any())).thenReturn(Result.success(Unit))
    val authRepository = TestAuthRepository()
    authRepository.currentUser.value = UserAccount(userId, null, null, null)
    viewModel = PreferencesViewModel(repository, authRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun onboardingHasNoBackButtonAndShowsOnboardingTitle() {
    composeTestRule.setContent {
      PreferencesScreen(
          viewModel = viewModel,
          onImportClick = {},
          onAddLocationClick = {},
          onSaved = {},
      )
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(PreferencesScreenTestTags.PREFERENCE_TITLE)
        .assertTextEquals("Complete your profile")
  }

  @Test
  fun editModeShowsBackButtonAndEditTitle() {
    composeTestRule.setContent {
      PreferencesScreen(
          viewModel = viewModel,
          onImportClick = {},
          onAddLocationClick = {},
          onSaved = {},
          onBack = {},
      )
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(PreferencesScreenTestTags.PREFERENCE_TITLE)
        .assertTextEquals("Modify your preferences")
  }

  @Test
  fun backButtonInvokesOnBackWithoutSaving() {
    var backCalls = 0
    var savedCalls = 0
    composeTestRule.setContent {
      PreferencesScreen(
          viewModel = viewModel,
          onImportClick = {},
          onAddLocationClick = {},
          onSaved = { savedCalls++ },
          onBack = { backCalls++ },
      )
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(1, backCalls)
    assertEquals(0, savedCalls)
  }

  @Test
  fun savingInEditModeInvokesOnSaved() {
    var savedCalls = 0
    composeTestRule.setContent {
      PreferencesScreen(
          viewModel = viewModel,
          onImportClick = {},
          onAddLocationClick = {},
          onSaved = { savedCalls++ },
          onBack = {},
      )
    }

    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SESSION_LENGTH_2_HOURS).performClick()
    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SAVE_BUTTON).performClick()

    assertEquals(1, savedCalls)
  }

  @Test
  fun editModeDisablesSaveUntilSomethingChanges() {
    setEditModeContent()
    val save = composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SAVE_BUTTON)

    save.assertIsNotEnabled()
    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SESSION_LENGTH_2_HOURS).performClick()
    save.assertIsEnabled()
    // Going back to the loaded value is no change either.
    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SESSION_LENGTH_1_HOUR).performClick()
    save.assertIsNotEnabled()
  }

  @Test
  fun onboardingAllowsSavingTheDefaults() {
    composeTestRule.setContent {
      PreferencesScreen(
          viewModel = viewModel,
          onImportClick = {},
          onAddLocationClick = {},
          onSaved = {},
      )
    }

    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SAVE_BUTTON).assertIsEnabled()
  }

  @Test
  fun deselectingEveryStudyTimeDisablesSave() {
    composeTestRule.setContent {
      PreferencesScreen(
          viewModel = viewModel,
          onImportClick = {},
          onAddLocationClick = {},
          onSaved = {},
      )
    }

    // Defaults select morning and afternoon.
    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.STUDY_TIME_MORNING).performClick()
    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.STUDY_TIME_AFTERNOON).performClick()

    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SAVE_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun repeatedSaveTapsWhileSavingSaveAndNavigateOnce() {
    val pendingSave = CompletableDeferred<Result<Unit>>()
    var saveCalls = 0
    val slowRepository =
        object : PreferencesRepository {
          override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
              Result.success(UserPreferences(userId = userId))

          override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> {
            saveCalls++
            return pendingSave.await()
          }
        }
    val authRepository = TestAuthRepository()
    authRepository.currentUser.value = UserAccount(userId, null, null, null)
    viewModel = PreferencesViewModel(slowRepository, authRepository)
    var savedCalls = 0
    setEditModeContent(onSaved = { savedCalls++ })
    val save = composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SAVE_BUTTON)

    composeTestRule.onNodeWithTag(PreferencesScreenTestTags.SESSION_LENGTH_2_HOURS).performClick()
    save.performClick()
    // The save is still in flight: Save is disabled and further taps do nothing.
    save.assertIsNotEnabled()
    save.performClick()
    save.performClick()
    pendingSave.complete(Result.success(Unit))
    composeTestRule.waitForIdle()

    assertEquals(1, saveCalls)
    assertEquals(1, savedCalls)
  }

  /** Shows the screen in edit mode, as opened from Settings. */
  private fun setEditModeContent(onSaved: () -> Unit = {}) {
    composeTestRule.setContent {
      PreferencesScreen(
          viewModel = viewModel,
          onImportClick = {},
          onAddLocationClick = {},
          onSaved = onSaved,
          onBack = {},
      )
    }
  }
}
