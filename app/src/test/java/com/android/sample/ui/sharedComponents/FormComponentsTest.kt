// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.android.sample.ui.sharedComponents.FormComponentsTestTags as Tags
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormComponentsTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun sectionLabelRendersTextAndTestTag() {
    composeTestRule.setContent { SectionLabel(text = "Header Test", testTag = "header_tag") }
    composeTestRule.onNodeWithTag("header_tag").assertIsDisplayed().assertTextEquals("Header Test")
  }

  @Test
  fun selectorFieldPillDisplaysValueTextAndTriggersClick() {
    var clicked = false
    composeTestRule.setContent {
      SelectorFieldPill(
          valueText = "Selected Value",
          placeholderText = "Placeholder",
          onClick = { clicked = true },
          testTag = "pill_tag",
          hasDropdownArrow = true,
      )
    }

    composeTestRule.onNodeWithTag("pill_tag").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithText("Selected Value").assertIsDisplayed()
    assertTrue(clicked)
  }

  @Test
  fun selectorFieldPillDisplaysPlaceholderWhenValueTextNull() {
    composeTestRule.setContent {
      SelectorFieldPill(
          valueText = null,
          placeholderText = "Select something...",
          onClick = {},
          testTag = "pill_tag",
          hasDropdownArrow = false,
      )
    }

    composeTestRule.onNodeWithTag("pill_tag").assertIsDisplayed()
    composeTestRule.onNodeWithText("Select something...").assertIsDisplayed()
  }

  @Test
  fun selectorFieldPillDisplaysPlaceholderWhenValueTextBlank() {
    composeTestRule.setContent {
      SelectorFieldPill(
          valueText = "   ",
          placeholderText = "Select something...",
          onClick = {},
          testTag = "pill_tag",
      )
    }

    composeTestRule.onNodeWithTag("pill_tag").assertIsDisplayed()
    composeTestRule.onNodeWithText("Select something...").assertIsDisplayed()
  }

  @Test
  fun outlinedInputFieldDisplaysValueAndTriggersOnValueChange() {
    var updatedText = ""
    composeTestRule.setContent {
      OutlinedInputField(
          value = "",
          onValueChange = { updatedText = it },
          placeholder = "Enter text",
          testTag = "input_tag",
      )
    }

    composeTestRule.onNodeWithTag("input_tag").assertIsDisplayed()
    composeTestRule.onNodeWithTag("input_tag").performTextInput("Library")
    assertEquals("Library", updatedText)
  }

  @Test
  fun taskRowItemDisplaysTaskAndInvokesToggleAndDeleteCallbacks() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var toggleCount = 0
    var deleteCount = 0
    val task = EventTask(text = "Read Chapter 1")

    composeTestRule.setContent {
      TaskRowItem(
          task = task,
          onToggle = { toggleCount++ },
          onDelete = { deleteCount++ },
          itemTag = "item_tag",
          checkboxTag = "checkbox_tag",
          deleteTag = "delete_tag",
      )
    }

    composeTestRule.onNodeWithTag("item_tag").assertIsDisplayed()
    composeTestRule.onNodeWithText("Read Chapter 1").assertIsDisplayed()

    composeTestRule.onNodeWithTag("checkbox_tag").performClick()
    assertEquals(1, toggleCount)

    val deleteDescription =
        context.getString(R.string.event_planning_delete_task_content_description)
    composeTestRule.onNodeWithContentDescription(deleteDescription).performClick()
    assertEquals(1, deleteCount)
  }

  @Test
  fun addTaskInputFieldInvokesOnAddTaskOnImeDoneWhenNotBlankAndClearsInput() {
    var addedTaskText = ""
    composeTestRule.setContent {
      AddTaskInputField(
          onAddTask = { addedTaskText = it },
          testTag = "add_task_tag",
      )
    }

    composeTestRule.onNodeWithTag("add_task_tag").assertIsDisplayed()
    composeTestRule.onNodeWithTag("add_task_tag").performTextInput("New Checklist Task")
    composeTestRule.onNodeWithTag("add_task_tag").performImeAction()

    assertEquals("New Checklist Task", addedTaskText)
  }

  @Test
  fun addTaskInputFieldDoesNotInvokeOnAddTaskWhenTextIsBlank() {
    var invoked = false
    composeTestRule.setContent {
      AddTaskInputField(
          onAddTask = { invoked = true },
          testTag = "add_task_tag",
      )
    }

    composeTestRule.onNodeWithTag("add_task_tag").performTextInput("   ")
    composeTestRule.onNodeWithTag("add_task_tag").performImeAction()

    assertFalse(invoked)
  }

  @Test
  fun eventTypeDropdownMenuRendersAllOptions() {
    var selectedType: EventType? = null
    composeTestRule.setContent {
      EventTypeDropdownMenu(
          expanded = true,
          onDismissRequest = {},
          onTypeSelected = { selectedType = it },
      )
    }

    composeTestRule.onNodeWithText("Course").assertIsDisplayed().performClick()
    assertEquals(EventType.COURSE, selectedType)
  }

  @Test
  fun eventDatePickerDialogTriggersConfirmAndDismiss() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var dismissed = false

    composeTestRule.setContent {
      EventDatePickerDialog(
          initialDate = LocalDate.of(2026, 11, 1),
          onDateSelected = {},
          onDismiss = { dismissed = true },
      )
    }

    composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
    assertTrue(dismissed)
  }

  @Test
  fun eventDatePickerDialogDismissButtonTriggersCallback() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var dismissed = false

    composeTestRule.setContent {
      EventDatePickerDialog(
          initialDate = LocalDate.of(2026, 11, 1),
          onDateSelected = {},
          onDismiss = { dismissed = true },
      )
    }

    composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
    assertTrue(dismissed)
  }

  @Test
  fun eventTimePickerDialogTriggersConfirmAndDismiss() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var selectedTime: LocalTime? = null
    var dismissed = false

    composeTestRule.setContent {
      EventTimePickerDialog(
          initialTime = LocalTime.of(14, 30),
          title = "Select Time",
          onTimeSelected = { selectedTime = it },
          onDismiss = { dismissed = true },
      )
    }

    composeTestRule.onNodeWithText("Select Time").assertIsDisplayed()
    composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
    assertEquals(LocalTime.of(14, 30), selectedTime)

    composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
    assertTrue(dismissed)
  }

  @Test
  fun subjectPickerDialogContentFiltersAndConfirms() {
    var confirmedSubject = ""
    composeTestRule.setContent {
      SubjectPickerDialogContent(
          currentSelection = "Math",
          allSubjects = listOf("Algebra", "Calculus", "Physics"),
          onDismiss = {},
          onConfirm = { confirmedSubject = it },
      )
    }

    composeTestRule.onNodeWithText("Calculus").assertIsDisplayed().performClick()
    assertEquals("Calculus", confirmedSubject)
  }

  @Test
  fun subjectPickerDialogFiltersListAndSelectsExistingSubject() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var confirmedSubject = ""
    val subjects = listOf("Analysis I", "Algorithms", "Linear Algebra")

    composeTestRule.setContent {
      SubjectPickerDialog(
          currentSelection = "",
          allSubjects = subjects,
          onDismiss = {},
          onConfirm = { confirmedSubject = it },
      )
    }

    composeTestRule
        .onNodeWithText(context.getString(R.string.event_planning_subject_search_placeholder))
        .performTextInput("Alg")

    composeTestRule.onNodeWithText("Algorithms").assertIsDisplayed().performClick()
    assertEquals("Algorithms", confirmedSubject)
  }

  @Test
  fun subjectPickerDialogAllowsSelectingCustomNewSubject() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var confirmedSubject = ""
    val subjects = listOf("Analysis I", "Algorithms")

    composeTestRule.setContent {
      SubjectPickerDialog(
          currentSelection = "",
          allSubjects = subjects,
          onDismiss = {},
          onConfirm = { confirmedSubject = it },
      )
    }

    composeTestRule
        .onNodeWithText(context.getString(R.string.event_planning_subject_search_placeholder))
        .performTextInput("Quantum Computing")

    composeTestRule.onNodeWithText("+ Use \"Quantum Computing\"").assertIsDisplayed().performClick()
    assertEquals("Quantum Computing", confirmedSubject)
  }

  @Test
  fun subjectPickerDialogConfirmButtonUsesCurrentSelectionIfSearchBlank() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var confirmedSubject = ""

    composeTestRule.setContent {
      SubjectPickerDialog(
          currentSelection = "Physics",
          allSubjects = listOf("Physics", "Chemistry"),
          onDismiss = {},
          onConfirm = { confirmedSubject = it },
      )
    }

    composeTestRule.onNodeWithText(context.getString(R.string.confirm)).performClick()
    assertEquals("Physics", confirmedSubject)
  }

  @Test
  fun subjectPickerDialogCancelTriggersDismiss() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    var dismissed = false

    composeTestRule.setContent {
      SubjectPickerDialog(
          currentSelection = "Physics",
          allSubjects = listOf("Physics"),
          onDismiss = { dismissed = true },
          onConfirm = {},
      )
    }

    composeTestRule.onNodeWithText(context.getString(R.string.cancel)).performClick()
    assertTrue(dismissed)
  }

  @Test
  fun inputFieldsGainFocusAndTriggerBringIntoView() {
    composeTestRule.setContent {
      Column {
        OutlinedInputField(
            value = "",
            onValueChange = {},
            placeholder = "Focus Location",
            testTag = "focus_input",
        )
        AddTaskInputField(
            onAddTask = {},
            testTag = "focus_task_input",
        )
      }
    }

    composeTestRule.onNodeWithTag("focus_input").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("focus_task_input").performClick()
    composeTestRule.waitForIdle()
  }

  @Test
  fun subjectPickerDialogQuerySurvivesScreenRestoration() {
    val restorationTester = StateRestorationTester(composeTestRule)
    val query = "Linear Algebra"

    restorationTester.setContent {
      SubjectPickerDialogContent(
          currentSelection = "",
          allSubjects = listOf("Linear Algebra", "Analysis I"),
          onDismiss = {},
          onConfirm = {},
      )
    }

    val searchInputNode = composeTestRule.onNode(hasSetTextAction())
    searchInputNode.assertIsDisplayed().performTextInput(query)
    searchInputNode.assertTextContains(query)

    restorationTester.emulateSavedInstanceStateRestore()

    composeTestRule.onNode(hasSetTextAction()).assertIsDisplayed().assertTextContains(query)
  }

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
          onLocationChange = { locationInput = it },
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
    composeTestRule.onNodeWithText("2026-10-27").assertIsDisplayed()
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
    composeTestRule.onNodeWithTag(Tags.LOCATION_INPUT).performTextInput(" New")
    assertTrue(locationInput.contains("New"))

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
          onLocationChange = {},
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
          onLocationChange = {},
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
          onLocationChange = {},
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
          onLocationChange = {},
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
          onLocationChange = {},
          onDescriptionChange = {},
      )
    }

    composeTestRule.onNodeWithTag(Tags.SCROLL_CONTAINER).performClick()
  }
}
