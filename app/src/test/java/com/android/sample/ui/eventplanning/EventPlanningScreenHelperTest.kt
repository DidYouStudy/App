// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.event.EventTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventPlanningScreenHelperTest {

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

    composeTestRule
        .onNodeWithContentDescription(
            context.getString(R.string.event_planning_delete_task_content_description)
        )
        .performClick()
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
}
