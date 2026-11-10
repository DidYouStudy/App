// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventFormContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun eventFormContentAdaptsUiStateToFormContent() {
    var typeClicked = false
    val uiState =
        EventPlanningUiState(
            type = EventType.COURSE,
            date = LocalDate.of(2026, 10, 27),
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(11, 0),
            subject = "Algebra",
            location = "CM3",
            description = "Bring pack",
            tasks = listOf(EventTask("Task 1")),
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = {},
          onTypeClick = { typeClicked = true },
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule.onNodeWithTag(Tags.SCROLL_CONTAINER).assertIsDisplayed()
    composeTestRule.onNodeWithText("Algebra").assertIsDisplayed()
    composeTestRule.onNodeWithText("CM3").assertIsDisplayed()
    composeTestRule.onNodeWithText("Bring pack").assertIsDisplayed()

    composeTestRule.onNodeWithTag(Tags.TYPE_SELECTOR).performClick()
    assertEquals(true, typeClicked)
  }

  /**
   * Clicking the clear icon fires only [EventFormContent]'s onLocationClear, not onLocationClick
   * (the nested clickable must not also trigger the pill's own click handler).
   */
  @Test
  fun eventFormContentClearButtonTriggersOnlyOnLocationClear() {
    var locationCleared = false
    var locationClicked = false
    val uiState = EventPlanningUiState(type = EventType.COURSE, location = "CM3")

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = { locationClicked = true },
          onLocationClear = { locationCleared = true },
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule
        .onNodeWithTag(
            com.android.sample.ui.sharedComponents.FormContentTestTags.LOCATION_CLEAR_BUTTON,
            useUnmergedTree = true,
        )
        .performClick()

    assertEquals(true, locationCleared)
    assertEquals(false, locationClicked)
  }

  @Test
  fun eventFormContentMapsOptionalSubjectLabelForTypeOther() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uiState =
        EventPlanningUiState(
            type = EventType.OTHER,
            subject = "",
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule.onNodeWithText("Other").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(Tags.SUBJECT_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_subject_optional_label))
  }

  @Test
  fun eventFormContentComputesMissingFieldFlagsForValidationErrors() {
    val uiState =
        EventPlanningUiState(
            type = EventType.COURSE,
            date = null,
            startTime = null,
            endTime = null,
            subject = "",
            error = EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR,
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).assertIsDisplayed()
  }

  @Test
  fun eventFormContentDoesNotComputeMissingFieldFlagsForNonValidationErrors() {
    val uiState =
        EventPlanningUiState(
            type = EventType.COURSE,
            date = null,
            startTime = null,
            endTime = null,
            subject = "",
            error = EventPlanningViewModel.FAILED_TO_ADD_EVENT_ERROR,
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).assertIsDisplayed()
  }
}
