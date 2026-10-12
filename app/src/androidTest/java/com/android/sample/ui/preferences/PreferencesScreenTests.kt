// Co-authored-by: Claude AI Agent
package com.android.sample.ui.preferences

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.TimeOfDay
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test

private class FakePreferencesRepository(
    var getResult: Result<UserPreferences?> = Result.success(null),
    var saveResult: Result<Unit> = Result.success(Unit),
    var getGate: CompletableDeferred<Unit>? = null,
) : PreferencesRepository {
  var getCalls = 0
  @Volatile var saved: UserPreferences? = null

  override suspend fun getPreferences(userId: String): Result<UserPreferences?> {
    getCalls++
    getGate?.await()
    return getResult
  }

  override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> {
    saved = preferences
    return saveResult
  }
}

class PreferencesScreenTests {
  @get:Rule val composeTestRule = createComposeRule()

  private val userId = "user-1"
  private var importClicks = 0
  private var addLocationClicks = 0
  private var savedCallbacks = 0

  private fun launch(repository: FakePreferencesRepository) {
    importClicks = 0
    addLocationClicks = 0
    savedCallbacks = 0
    val viewModel = PreferencesViewModel(repository, userId)
    composeTestRule.setContent {
      PreferencesScreen(
          viewModel = viewModel,
          onImportClick = { importClicks++ },
          onAddLocationClick = { addLocationClicks++ },
          onSaved = { savedCallbacks++ },
      )
    }
  }

  private fun waitForSuccess() {
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithTag(Tags.PREFERENCE_SCREEN).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun click(tag: String) {
    composeTestRule
        .onNodeWithTag(tag)
        .performScrollTo()
        .performSemanticsAction(SemanticsActions.OnClick)
  }

  private fun assertSelected(tag: String) {
    composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsSelected()
  }

  private fun waitForSave(repository: FakePreferencesRepository): UserPreferences {
    composeTestRule.waitUntil(5_000) { repository.saved != null }
    return repository.saved!!
  }

  @Test
  fun loadingStateShowsNoPreferenceContent() {
    val repository = FakePreferencesRepository(getGate = CompletableDeferred())
    launch(repository)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(Tags.PREFERENCE_SCREEN).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).assertDoesNotExist()
  }

  @Test
  fun successStateWithNoStoredPreferencesShowsDefaults() {
    launch(FakePreferencesRepository(getResult = Result.success(null)))
    waitForSuccess()

    composeTestRule.onNodeWithTag(Tags.PREFERENCE_TITLE).assertIsDisplayed()
    assertSelected(Tags.STUDY_TIME_MORNING)
    assertSelected(Tags.STUDY_TIME_AFTERNOON)
    assertSelected(Tags.SESSION_LENGTH_1_HOUR)
    assertSelected(Tags.BREAK_DURATION_10_MIN)
    assertSelected(Tags.BREAK_FREQUENCY_45_MIN)
  }

  @Test
  fun successStateShowsStoredPreferences() {
    val stored =
        UserPreferences(
            userId = userId,
            preferredStudyTimes = setOf(TimeOfDay.NIGHT),
            sessionLengthMinutes = 180,
            breakLengthMinutes = 20,
            breakFrequencyMinutes = 30,
        )
    launch(FakePreferencesRepository(getResult = Result.success(stored)))
    waitForSuccess()

    assertSelected(Tags.STUDY_TIME_NIGHT)
    assertSelected(Tags.SESSION_LENGTH_3_HOURS)
    assertSelected(Tags.BREAK_DURATION_20_MIN)
    assertSelected(Tags.BREAK_FREQUENCY_30_MIN)
  }

  @Test
  fun unknownStoredValuesFallBackToDefaultPills() {
    val stored =
        UserPreferences(
            userId = userId,
            sessionLengthMinutes = 999,
            breakLengthMinutes = 999,
            breakFrequencyMinutes = 999,
        )
    launch(FakePreferencesRepository(getResult = Result.success(stored)))
    waitForSuccess()

    assertSelected(Tags.SESSION_LENGTH_1_HOUR)
    assertSelected(Tags.BREAK_DURATION_10_MIN)
    assertSelected(Tags.BREAK_FREQUENCY_45_MIN)
  }

  @Test
  fun errorStateShowsMessageAndRetryButton() {
    launch(FakePreferencesRepository(getResult = Result.failure(Exception("boom"))))

    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("boom").assertIsDisplayed()
    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.PREFERENCE_SCREEN).assertDoesNotExist()
  }

  @Test
  fun errorStateWithoutMessageShowsFallbackText() {
    launch(FakePreferencesRepository(getResult = Result.failure(Exception())))

    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Failed to load preferences").assertIsDisplayed()
  }

  @Test
  fun retryReloadsPreferencesAndShowsSuccessState() {
    val repository =
        FakePreferencesRepository(getResult = Result.failure(Exception("network down")))
    launch(repository)
    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
    }

    repository.getResult = Result.success(UserPreferences(userId = userId))
    composeTestRule.onNodeWithText("Retry").performClick()
    waitForSuccess()

    assert(repository.getCalls == 2)
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun importAndAddLocationButtonsForwardTheirCallbacks() {
    launch(FakePreferencesRepository())
    waitForSuccess()

    click(Tags.IMPORT_SCHEDULE_BUTTON)
    assert(importClicks == 1 && addLocationClicks == 0)
    click(Tags.ADD_STUDY_LOCATION_BUTTON)
    assert(importClicks == 1 && addLocationClicks == 1)
  }

  @Test
  fun savingWithoutChangesPersistsTheLoadedPreferences() {
    val repository = FakePreferencesRepository()
    launch(repository)
    waitForSuccess()

    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()

    val saved = waitForSave(repository)
    assert(saved == UserPreferences(userId = userId))
    assert(savedCallbacks == 1)
  }

  @Test
  fun savingPersistsAllSelectedPills() {
    val repository = FakePreferencesRepository()
    launch(repository)
    waitForSuccess()

    click(Tags.STUDY_TIME_MORNING) // deselect
    click(Tags.STUDY_TIME_NIGHT) // select
    click(Tags.SESSION_LENGTH_2_HOURS)
    click(Tags.BREAK_DURATION_15_MIN)
    click(Tags.BREAK_FREQUENCY_1_HOUR)
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()

    val saved = waitForSave(repository)
    assert(saved.userId == userId)
    assert(saved.preferredStudyTimes == setOf(TimeOfDay.AFTERNOON, TimeOfDay.NIGHT))
    assert(saved.sessionLengthMinutes == 120)
    assert(saved.breakLengthMinutes == 15)
    assert(saved.breakFrequencyMinutes == 60)
    assert(savedCallbacks == 1)
  }

  @Test
  fun togglingAStudyTimeTwiceRestoresTheOriginalSet() {
    val repository = FakePreferencesRepository()
    launch(repository)
    waitForSuccess()

    click(Tags.STUDY_TIME_EVENING)
    click(Tags.STUDY_TIME_EVENING)
    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()

    val saved = waitForSave(repository)
    assert(saved.preferredStudyTimes == setOf(TimeOfDay.MORNING, TimeOfDay.AFTERNOON))
  }

  @Test
  fun everySingleChoiceOptionIsPersistedWhenSelected() {
    val repository = FakePreferencesRepository()
    launch(repository)
    waitForSuccess()

    fun saveAndGet(): UserPreferences {
      repository.saved = null
      composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()
      return waitForSave(repository)
    }

    SessionLength.entries.forEach { option ->
      click(option.testTag)
      assert(saveAndGet().sessionLengthMinutes == option.minutes)
    }
    BreakDuration.entries.forEach { option ->
      click(option.testTag)
      assert(saveAndGet().breakLengthMinutes == option.minutes)
    }
    BreakFrequency.entries.forEach { option ->
      click(option.testTag)
      assert(saveAndGet().breakFrequencyMinutes == option.minutes)
    }
  }

  @Test
  fun saveFailureShowsErrorStateWithMessage() {
    val repository = FakePreferencesRepository(saveResult = Result.failure(Exception("disk full")))
    launch(repository)
    waitForSuccess()

    composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()

    composeTestRule.waitUntil(5_000) {
      composeTestRule.onAllNodesWithText("disk full").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
  }
}
