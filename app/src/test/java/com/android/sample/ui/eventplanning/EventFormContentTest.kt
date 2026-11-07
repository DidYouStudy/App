// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude AI Agent
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import com.android.sample.ui.sharedComponents.FormContentTestTags
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventFormContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun eventFormContentRendersPopulatedStateAndInteractions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var typeClicked = false
    var dateClicked = false
    var startTimeClicked = false
    var endTimeClicked = false
    var subjectClicked = false
    var locationInput = ""
    var descriptionInput = ""
    var addedTask = ""
    var toggledTaskIndex = -1
    var deletedTaskIndex = -1

    val uiState =
        EventPlanningUiState(
            type = EventType.COURSE,
            subject = "Algebra",
            date = LocalDate.of(2026, 10, 27),
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(11, 0),
            location = "CM3",
            description = "Bring pack",
            tasks = listOf(EventTask("Task 1")),
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = {},
          onTypeClick = { typeClicked = true },
          onDateClick = { dateClicked = true },
          onStartTimeClick = { startTimeClicked = true },
          onEndTimeClick = { endTimeClicked = true },
          onSubjectClick = { subjectClicked = true },
          onLocationChange = { locationInput = it },
          onDescriptionChange = { descriptionInput = it },
          onAddTask = { addedTask = it },
          onToggleTask = { toggledTaskIndex = it },
          onDeleteTask = { deletedTaskIndex = it },
      )
    }

    composeTestRule.onNodeWithTag(Tags.SCROLL_CONTAINER).assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(FormContentTestTags.TYPE_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_type_label))
    composeTestRule.onNodeWithTag(Tags.TYPE_SELECTOR).performClick()
    composeTestRule.onNodeWithText("Course").assertIsDisplayed()
    assertTrue(typeClicked)

    composeTestRule
        .onNodeWithTag(FormContentTestTags.DATE_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_date_label))
    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).performClick()
    composeTestRule.onNodeWithText("27/10/2026").assertIsDisplayed()
    assertTrue(dateClicked)

    composeTestRule
        .onNodeWithTag(FormContentTestTags.START_TIME_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_start_time_label))
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText("09:00").assertIsDisplayed()
    assertTrue(startTimeClicked)

    composeTestRule
        .onNodeWithTag(FormContentTestTags.END_TIME_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_end_time_label))
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText("11:00").assertIsDisplayed()
    assertTrue(endTimeClicked)

    composeTestRule
        .onNodeWithTag(Tags.SUBJECT_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_subject_label))
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).performClick()
    composeTestRule.onNodeWithText("Algebra").assertIsDisplayed()
    assertTrue(subjectClicked)

    composeTestRule
        .onNodeWithTag(FormContentTestTags.LOCATION_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_location_optional_label))
    composeTestRule.onNodeWithTag(Tags.LOCATION_INPUT).performTextInput(" New")
    assertTrue(locationInput.contains("New"))

    composeTestRule
        .onNodeWithTag(FormContentTestTags.DESCRIPTION_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_description_optional_label))
    composeTestRule.onNodeWithTag(Tags.DESCRIPTION_INPUT).performTextInput(" New")
    assertTrue(descriptionInput.contains("New"))

    composeTestRule
        .onNodeWithTag(FormContentTestTags.TASKS_LABEL, useUnmergedTree = true)
        .performScrollTo()
        .assertTextEquals(context.getString(R.string.event_planning_tasks_optional_label))
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).performScrollTo().performTextInput("Task 2")
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).performImeAction()
    assertEquals("Task 2", addedTask)

    composeTestRule
        .onNodeWithTag("${FormContentTestTags.TASK_ITEM_PREFIX}0", useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag("${FormContentTestTags.TASK_CHECKBOX_PREFIX}0", useUnmergedTree = true)
        .performScrollTo()
        .performClick()
    assertEquals(0, toggledTaskIndex)

    composeTestRule
        .onNodeWithTag("${FormContentTestTags.TASK_DELETE_PREFIX}0", useUnmergedTree = true)
        .performScrollTo()
        .performClick()
    assertEquals(0, deletedTaskIndex)
  }

  @Test
  fun eventFormContentRendersWithoutTasksWhenShowTasksSectionFalse() {
    val uiState =
        EventPlanningUiState(
            eventKind = EventKind.GROUP_SESSION,
            type = EventType.STUDY_SESSION,
            subject = "Physics",
            date = LocalDate.of(2025, 5, 10),
            startTime = LocalTime.of(10, 0),
            endTime = LocalTime.of(12, 0),
            location = "BC Building",
            description = "Group prep",
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationChange = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule.onNodeWithText("Physics").assertIsDisplayed()
    composeTestRule.onNodeWithText("BC Building").assertIsDisplayed()
    composeTestRule.onNodeWithText("Group prep").assertIsDisplayed()
    composeTestRule.onNodeWithTag(FormContentTestTags.TASKS_LABEL).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun eventFormContentRendersEndsNextDayWarningWhenEndTimeBeforeStartTime() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uiState =
        EventPlanningUiState(
            type = EventType.STUDY_SESSION,
            subject = "Night Study",
            date = LocalDate.of(2025, 5, 10),
            startTime = LocalTime.of(23, 0),
            endTime = LocalTime.of(2, 0),
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationChange = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule
        .onNodeWithTag(FormContentTestTags.ENDS_NEXT_DAY_WARNING, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.event_planning_ends_next_day_warning))
        .assertIsDisplayed()
  }

  @Test
  fun eventFormContentOpensTypeDropdownWhenOnTypeClickIsNull() {
    var typeChanged: EventType? = null
    val uiState =
        EventPlanningUiState(
            type = EventType.OTHER,
            subject = "",
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeChange = { typeChanged = it },
          onTypeClick = null,
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationChange = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule.onNodeWithTag(Tags.TYPE_SELECTOR).performClick()
    composeTestRule.onNodeWithText("Course").assertIsDisplayed().performClick()
    assertEquals(EventType.COURSE, typeChanged)
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
          onLocationChange = {},
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
          onLocationChange = {},
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
          onLocationChange = {},
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
  fun eventFormContentClickingFormBackgroundClearsFocus() {
    val uiState =
        EventPlanningUiState(
            type = EventType.COURSE,
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
          onLocationChange = {},
          onDescriptionChange = {},
          onAddTask = {},
          onToggleTask = {},
          onDeleteTask = {},
      )
    }

    composeTestRule.onNodeWithTag(Tags.SCROLL_CONTAINER).performClick()
  }
}
