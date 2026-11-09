// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

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
import com.android.sample.ui.sharedComponents.FormContentTestTags as Tags
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun formContentRendersPopulatedStateAndInteractions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var typeClicked = false
    var dateClicked = false
    var startTimeClicked = false
    var endTimeClicked = false
    var subjectClicked = false
    var locationClicked = false
    var descriptionInput = ""
    var addedTask = ""
    var toggledTaskIndex = -1
    var deletedTaskIndex = -1

    composeTestRule.setContent {
      FormContent(
          type = EventType.COURSE,
          subject = "Algebra",
          date = LocalDate.of(2026, 10, 27),
          startTime = LocalTime.of(9, 0),
          endTime = LocalTime.of(11, 0),
          location = "CM3",
          description = "Bring pack",
          isSubjectRequired = true,
          onTypeChange = {},
          onTypeClick = { typeClicked = true },
          onDateClick = { dateClicked = true },
          onStartTimeClick = { startTimeClicked = true },
          onEndTimeClick = { endTimeClicked = true },
          onSubjectClick = { subjectClicked = true },
          onLocationClick = { locationClicked = true },
          onDescriptionChange = { descriptionInput = it },
          onAddTask = { addedTask = it },
          onToggleTask = { toggledTaskIndex = it },
          onDeleteTask = { deletedTaskIndex = it },
          tasks = listOf(EventTask("Task 1")),
          showTasksSection = true,
      )
    }

    composeTestRule.onNodeWithTag(Tags.SCROLL_CONTAINER).assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(Tags.TYPE_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_type_label))
    composeTestRule.onNodeWithTag(Tags.TYPE_SELECTOR).performClick()
    composeTestRule.onNodeWithText("Course").assertIsDisplayed()
    assertTrue(typeClicked)

    composeTestRule
        .onNodeWithTag(Tags.DATE_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_date_label))
    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).performClick()
    composeTestRule.onNodeWithText("27/10/2026").assertIsDisplayed()
    assertTrue(dateClicked)

    composeTestRule
        .onNodeWithTag(Tags.START_TIME_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_start_time_label))
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).performClick()
    composeTestRule.onNodeWithText("09:00").assertIsDisplayed()
    assertTrue(startTimeClicked)

    composeTestRule
        .onNodeWithTag(Tags.END_TIME_LABEL, useUnmergedTree = true)
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
        .onNodeWithTag(Tags.LOCATION_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_location_optional_label))
    composeTestRule.onNodeWithTag(Tags.LOCATION_SELECTOR).assertTextEquals("CM3").performClick()
    assertTrue(locationClicked)

    composeTestRule
        .onNodeWithTag(Tags.DESCRIPTION_LABEL, useUnmergedTree = true)
        .assertTextEquals(context.getString(R.string.event_planning_description_optional_label))
    composeTestRule.onNodeWithTag(Tags.DESCRIPTION_INPUT).performTextInput(" New")
    assertTrue(descriptionInput.contains("New"))

    composeTestRule
        .onNodeWithTag(Tags.TASKS_LABEL, useUnmergedTree = true)
        .performScrollTo()
        .assertTextEquals(context.getString(R.string.event_planning_tasks_optional_label))
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).performScrollTo().performTextInput("Task 2")
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).performImeAction()
    assertEquals("Task 2", addedTask)

    composeTestRule
        .onNodeWithTag("${Tags.TASK_ITEM_PREFIX}0", useUnmergedTree = true)
        .performScrollTo()
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag("${Tags.TASK_CHECKBOX_PREFIX}0", useUnmergedTree = true)
        .performScrollTo()
        .performClick()
    assertEquals(0, toggledTaskIndex)

    composeTestRule
        .onNodeWithTag("${Tags.TASK_DELETE_PREFIX}0", useUnmergedTree = true)
        .performScrollTo()
        .performClick()
    assertEquals(0, deletedTaskIndex)
  }

  @Test
  fun formContentRendersWithoutTasksWhenShowTasksSectionFalse() {
    composeTestRule.setContent {
      FormContent(
          type = EventType.STUDY_SESSION,
          subject = "Physics",
          date = LocalDate.of(2025, 5, 10),
          startTime = LocalTime.of(10, 0),
          endTime = LocalTime.of(12, 0),
          location = "BC Building",
          description = "Group prep",
          isSubjectRequired = true,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          showTasksSection = false,
      )
    }

    composeTestRule.onNodeWithText("Physics").assertIsDisplayed()
    composeTestRule.onNodeWithText("BC Building").assertIsDisplayed()
    composeTestRule.onNodeWithText("Group prep").assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.TASKS_LABEL).assertDoesNotExist()
    composeTestRule.onNodeWithTag(Tags.ADD_TASK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun formContentRendersEndsNextDayWarningWhenEndTimeBeforeStartTime() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent {
      FormContent(
          type = EventType.STUDY_SESSION,
          subject = "Night Study",
          date = LocalDate.of(2025, 5, 10),
          startTime = LocalTime.of(23, 0),
          endTime = LocalTime.of(2, 0),
          location = "",
          description = "",
          isSubjectRequired = true,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
      )
    }

    composeTestRule
        .onNodeWithTag(Tags.ENDS_NEXT_DAY_WARNING, useUnmergedTree = true)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.event_planning_ends_next_day_warning))
        .assertIsDisplayed()
  }

  @Test
  fun formContentOpensTypeDropdownWhenOnTypeClickIsNull() {
    var typeChanged: EventType? = null

    composeTestRule.setContent {
      FormContent(
          type = EventType.OTHER,
          subject = "",
          date = null,
          startTime = null,
          endTime = null,
          location = "",
          description = "",
          isSubjectRequired = false,
          onTypeChange = { typeChanged = it },
          onTypeClick = null,
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
      )
    }

    composeTestRule.onNodeWithTag(Tags.TYPE_SELECTOR).performClick()
    composeTestRule.onNodeWithText("Course").assertIsDisplayed().performClick()
    assertEquals(EventType.COURSE, typeChanged)
  }

  @Test
  fun formContentHighlightsMissingFieldsWhenErrorFlagsSet() {
    composeTestRule.setContent {
      FormContent(
          type = EventType.COURSE,
          subject = "",
          date = null,
          startTime = null,
          endTime = null,
          location = "",
          description = "",
          isSubjectRequired = true,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
          isDateMissing = true,
          isStartTimeMissing = true,
          isEndTimeMissing = true,
          isSubjectMissing = true,
      )
    }

    composeTestRule.onNodeWithTag(Tags.DATE_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.START_TIME_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.END_TIME_SELECTOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(Tags.SUBJECT_SELECTOR).assertIsDisplayed()
  }

  @Test
  fun formContentClickingFormBackgroundClearsFocus() {
    composeTestRule.setContent {
      FormContent(
          type = EventType.COURSE,
          subject = "",
          date = null,
          startTime = null,
          endTime = null,
          location = "",
          description = "",
          isSubjectRequired = true,
          onTypeChange = {},
          onDateClick = {},
          onStartTimeClick = {},
          onEndTimeClick = {},
          onSubjectClick = {},
          onLocationClick = {},
          onDescriptionChange = {},
      )
    }

    composeTestRule.onNodeWithTag(Tags.SCROLL_CONTAINER).performClick()
  }
}
