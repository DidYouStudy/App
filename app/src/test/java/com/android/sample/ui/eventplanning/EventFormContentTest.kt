// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
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
  fun formContentRendersPopulatedStateAndInteractions() {
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
            date = LocalDate.of(2026, 10, 27),
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(11, 0),
            subject = "Algebra",
            location = "CM3",
            description = "Bring pack",
            tasks = listOf(EventTask("Task 1")),
            error = "Validation error",
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
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

    // Scroll container
    composeTestRule.onNodeWithTag(Tags.SCROLL_CONTAINER).assertIsDisplayed()

    // Type
    composeTestRule
        .onNodeWithTag(Tags.TYPE_LABEL)
        .assertTextEquals(context.getString(R.string.event_planning_type_label))
    composeTestRule.onNodeWithTag(Tags.TYPE_SELECTOR).performClick()
    composeTestRule.onNodeWithText("Course").assertIsDisplayed()
    assertTrue(typeClicked)

    // Date
    composeTestRule
        .onNodeWithTag(Tags.DATE_LABEL)
        .assertTextEquals(context.getString(R.string.event_planning_date_label))
    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).performClick()
    composeTestRule.onNodeWithText("2026-10-27").assertIsDisplayed()
    assertTrue(dateClicked)

    // Start time
    composeTestRule
        .onNodeWithTag(Tags.START_TIME_LABEL)
        .assertTextEquals(context.getString(R.string.event_planning_start_time_label))
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText("09:00").assertIsDisplayed()
    assertTrue(startTimeClicked)

    // End time
    composeTestRule
        .onNodeWithTag(Tags.END_TIME_LABEL)
        .assertTextEquals(context.getString(R.string.event_planning_end_time_label))
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText("11:00").assertIsDisplayed()
    assertTrue(endTimeClicked)

    // Subject
    composeTestRule
        .onNodeWithTag(Tags.SUBJECT_LABEL)
        .assertTextEquals(context.getString(R.string.event_planning_subject_label))
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).performClick()
    composeTestRule.onNodeWithText("Algebra").assertIsDisplayed()
    assertTrue(subjectClicked)

    // Location
    composeTestRule
        .onNodeWithTag(Tags.LOCATION_LABEL)
        .assertTextEquals(context.getString(R.string.event_planning_location_optional_label))
    composeTestRule.onNodeWithTag(Tags.LOCATION_INPUT).performTextInput(" New")
    assertTrue(locationInput.contains("New"))

    // Description
    composeTestRule
        .onNodeWithTag(Tags.DESCRIPTION_LABEL)
        .assertTextEquals(context.getString(R.string.event_planning_description_optional_label))
    composeTestRule.onNodeWithTag(Tags.DESCRIPTION_INPUT).performTextInput(" New")
    assertTrue(descriptionInput.contains("New"))

    // Tasks
    composeTestRule
        .onNodeWithTag(Tags.TASKS_LABEL)
        .performScrollTo()
        .assertTextEquals(context.getString(R.string.event_planning_tasks_optional_label))
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).performScrollTo().performTextInput("Task 2")
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).performImeAction()
    assertEquals("Task 2", addedTask)

    // Task items
    composeTestRule.onNodeWithTag("${Tags.TASK_ITEM_PREFIX}0").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("${Tags.TASK_CHECKBOX_PREFIX}0").performScrollTo().performClick()
    assertEquals(0, toggledTaskIndex)

    composeTestRule.onNodeWithTag("${Tags.TASK_DELETE_PREFIX}0").performScrollTo().performClick()
    assertEquals(0, deletedTaskIndex)

    // Error text
    composeTestRule
        .onNodeWithTag(Tags.ERROR_TEXT)
        .performScrollTo()
        .assertIsDisplayed()
        .assertTextEquals("Validation error")
  }

  @Test
  fun formContentRendersEmptyStateAndOptionalSubjectLabel() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uiState =
        EventPlanningUiState(
            type = EventType.OTHER,
            date = null,
            startTime = null,
            endTime = null,
            subject = "",
            error = null,
            tasks = emptyList(),
        )

    composeTestRule.setContent {
      EventFormContent(
          uiState = uiState,
          onTypeClick = {},
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
        .onNodeWithTag(Tags.SUBJECT_LABEL)
        .assertTextEquals(context.getString(R.string.event_planning_subject_optional_label))
    composeTestRule.onNodeWithTag(Tags.ERROR_TEXT).assertDoesNotExist()
    composeTestRule.onNodeWithTag("${Tags.TASK_ITEM_PREFIX}0").assertDoesNotExist()
  }
}
