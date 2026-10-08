// Co-authored-by: Claude AI Agent
package com.android.sample.ui.preferences

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PreferencesScreenStatelessTests {
    @get:Rule val composeTestRule = createComposeRule()

    private var importClicks = 0
    private var addLocationClicks = 0
    private var saveClicks = 0

    @Before
    fun setUp() {
        importClicks = 0
        addLocationClicks = 0
        saveClicks = 0
        composeTestRule.setContent {
            var times by remember { mutableStateOf(setOf(StudyTime.MORNING, StudyTime.AFTERNOON)) }
            var session by remember { mutableStateOf(SessionLength.ONE_HOUR) }
            var breakDuration by remember { mutableStateOf(BreakDuration.TEN_MIN) }
            var breakFrequency by remember { mutableStateOf(BreakFrequency.EVERY_45_MIN) }

            PreferenceScreenStateless(
                selectedStudyTimes = times,
                selectedSessionLength = session,
                selectedBreakDuration = breakDuration,
                selectedBreakFrequency = breakFrequency,
                onImportClick = { importClicks++ },
                onAddLocationClick = { addLocationClicks++ },
                onStudyTimeToggle = { time ->
                    times = if (time in times) times - time else times + time
                },
                onSessionLengthSelect = { session = it },
                onBreakDurationSelect = { breakDuration = it },
                onBreakFrequencySelect = { breakFrequency = it },
                onSaveClick = { saveClicks++ },
            )
        }
    }

    private fun scrollTo(tag: String) = composeTestRule.onNodeWithTag(tag).performScrollTo()

    private fun assertSelected(tag: String) {
        scrollTo(tag)
        composeTestRule.onNodeWithTag(tag).assertIsSelected()
    }

    private fun assertNotSelected(tag: String) {
        scrollTo(tag)
        composeTestRule.onNodeWithTag(tag).assertIsNotSelected()
    }

    private fun click(tag: String) {
        scrollTo(tag)
        composeTestRule.onNodeWithTag(tag).performClick()
    }

    @Test
    fun displayAllStaticComponents() {
        composeTestRule.onNodeWithTag(Tags.PREFERENCE_SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(Tags.PREFERENCE_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(Tags.PREFERENCE_SCROLL_CONTAINER).assertIsDisplayed()
        composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).assertIsDisplayed()
    }

    @Test
    fun displayAllSectionLabelsAndButtons() {
        val labelsAndTexts =
            listOf(
                Tags.IMPORT_SCHEDULE_LABEL,
                Tags.STUDY_LOCATIONS_LABEL,
                Tags.STUDY_TIME_LABEL,
                Tags.SESSION_LENGTH_LABEL,
                Tags.BREAK_DURATION_LABEL,
                Tags.BREAK_FREQUENCY_LABEL,
            )
        labelsAndTexts.forEach { tag ->
            scrollTo(tag)
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        scrollTo(Tags.IMPORT_SCHEDULE_BUTTON)
        composeTestRule.onNodeWithTag(Tags.IMPORT_SCHEDULE_BUTTON).assertIsDisplayed()
        scrollTo(Tags.ADD_STUDY_LOCATION_BUTTON)
        composeTestRule.onNodeWithTag(Tags.ADD_STUDY_LOCATION_BUTTON).assertIsDisplayed()
    }

    @Test
    fun displayAllPreferencePillsWithTheirLabels() {
        val allOptions: List<PreferenceOption> =
            StudyTime.entries +
                    SessionLength.entries +
                    BreakDuration.entries +
                    BreakFrequency.entries
        allOptions.forEach { option ->
            scrollTo(option.testTag)
            composeTestRule
                .onNodeWithTag(option.testTag)
                .assertIsDisplayed()
                .assertTextEquals(option.label)
        }
    }

    @Test
    fun initialSelectionMatchesProvidedPreferences() {
        // study times: morning + afternoon
        assertSelected(Tags.STUDY_TIME_MORNING)
        assertSelected(Tags.STUDY_TIME_AFTERNOON)
        assertNotSelected(Tags.STUDY_TIME_EVENING)
        assertNotSelected(Tags.STUDY_TIME_NIGHT)
        // session length: 1 hour
        assertSelected(Tags.SESSION_LENGTH_1_HOUR)
        assertNotSelected(Tags.SESSION_LENGTH_30_MIN)
        assertNotSelected(Tags.SESSION_LENGTH_2_HOURS)
        assertNotSelected(Tags.SESSION_LENGTH_3_HOURS)
        // break duration: 10 min
        assertSelected(Tags.BREAK_DURATION_10_MIN)
        assertNotSelected(Tags.BREAK_DURATION_5_MIN)
        assertNotSelected(Tags.BREAK_DURATION_15_MIN)
        assertNotSelected(Tags.BREAK_DURATION_20_MIN)
        // break frequency: 45 min
        assertSelected(Tags.BREAK_FREQUENCY_45_MIN)
        assertNotSelected(Tags.BREAK_FREQUENCY_30_MIN)
        assertNotSelected(Tags.BREAK_FREQUENCY_1_HOUR)
    }

    @Test
    fun importButtonTriggersCallback() {
        click(Tags.IMPORT_SCHEDULE_BUTTON)
        assert(importClicks == 1)
        assert(addLocationClicks == 0 && saveClicks == 0)
    }

    @Test
    fun addLocationButtonTriggersCallback() {
        click(Tags.ADD_STUDY_LOCATION_BUTTON)
        assert(addLocationClicks == 1)
        assert(importClicks == 0 && saveClicks == 0)
    }

    @Test
    fun saveButtonTriggersCallback() {
        composeTestRule.onNodeWithTag(Tags.SAVE_BUTTON).performClick()
        assert(saveClicks == 1)
        assert(importClicks == 0 && addLocationClicks == 0)
    }

    @Test
    fun clickingUnselectedStudyTimeSelectsIt() {
        click(Tags.STUDY_TIME_EVENING)
        assertSelected(Tags.STUDY_TIME_EVENING)
        // existing selections are untouched (multiple choice)
        assertSelected(Tags.STUDY_TIME_MORNING)
        assertSelected(Tags.STUDY_TIME_AFTERNOON)
        assertNotSelected(Tags.STUDY_TIME_NIGHT)
    }

    @Test
    fun clickingSelectedStudyTimeDeselectsIt() {
        click(Tags.STUDY_TIME_MORNING)
        assertNotSelected(Tags.STUDY_TIME_MORNING)
        assertSelected(Tags.STUDY_TIME_AFTERNOON)
    }

    @Test
    fun studyTimeCanBeToggledOnAndOffAgain() {
        click(Tags.STUDY_TIME_NIGHT)
        assertSelected(Tags.STUDY_TIME_NIGHT)
        click(Tags.STUDY_TIME_NIGHT)
        assertNotSelected(Tags.STUDY_TIME_NIGHT)
    }

    @Test
    fun allStudyTimesCanBeSelectedAndDeselected() {
        val selectedByDefault = setOf(Tags.STUDY_TIME_MORNING, Tags.STUDY_TIME_AFTERNOON)

        StudyTime.entries.forEach { if (it.testTag !in selectedByDefault) click(it.testTag) }
        StudyTime.entries.forEach { assertSelected(it.testTag) }
        StudyTime.entries.forEach { click(it.testTag) }
        StudyTime.entries.forEach { assertNotSelected(it.testTag) }
    }

    @Test
    fun selectingSessionLengthReplacesPreviousSelection() {
        SessionLength.entries.forEach { selected ->
            click(selected.testTag)
            SessionLength.entries.forEach { other ->
                if (other == selected) assertSelected(other.testTag) else assertNotSelected(other.testTag)
            }
        }
    }

    @Test
    fun selectingBreakDurationReplacesPreviousSelection() {
        BreakDuration.entries.forEach { selected ->
            click(selected.testTag)
            BreakDuration.entries.forEach { other ->
                if (other == selected) assertSelected(other.testTag) else assertNotSelected(other.testTag)
            }
        }
    }

    @Test
    fun selectingBreakFrequencyReplacesPreviousSelection() {
        BreakFrequency.entries.forEach { selected ->
            click(selected.testTag)
            BreakFrequency.entries.forEach { other ->
                if (other == selected) assertSelected(other.testTag) else assertNotSelected(other.testTag)
            }
        }
    }

    @Test
    fun clickingAlreadySelectedSingleChoiceKeepsItSelected() {
        click(Tags.SESSION_LENGTH_1_HOUR)
        assertSelected(Tags.SESSION_LENGTH_1_HOUR)
    }

    @Test
    fun sectionsAreIndependentOfEachOther() {
        click(Tags.SESSION_LENGTH_3_HOURS)
        assertSelected(Tags.SESSION_LENGTH_3_HOURS)
        assertSelected(Tags.BREAK_DURATION_10_MIN)
        assertSelected(Tags.BREAK_FREQUENCY_45_MIN)
        assertSelected(Tags.STUDY_TIME_MORNING)
    }
}