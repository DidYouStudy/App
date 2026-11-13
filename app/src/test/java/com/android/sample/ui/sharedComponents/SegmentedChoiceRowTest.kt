// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [SegmentedChoiceRow] and [segmentShape]. */
@RunWith(AndroidJUnit4::class)
class SegmentedChoiceRowTest {
  @get:Rule val composeTestRule = createComposeRule()

  private data class TestOption(val id: String, val label: String)

  private val testOptions =
      listOf(
          TestOption("1", "Option 1"),
          TestOption("2", "Option 2"),
          TestOption("3", "Option 3"),
      )

  @Test
  fun segmentedChoiceRowDisplaysAllItemsAndLabels() {
    composeTestRule.setContent {
      MaterialTheme {
        SegmentedChoiceRow(
            items = testOptions,
            isSelected = { it.id == "1" },
            onClick = {},
            label = { it.label },
            testTag = { "tag_${it.id}" },
            role = Role.RadioButton,
        )
      }
    }

    testOptions.forEach { option ->
      composeTestRule
          .onNodeWithTag("tag_${option.id}")
          .assertIsDisplayed()
          .assertTextEquals(option.label)
    }
  }

  @Test
  fun segmentedChoiceRowMarksSelectedOptionCorrectly() {
    composeTestRule.setContent {
      MaterialTheme {
        SegmentedChoiceRow(
            items = testOptions,
            isSelected = { it.id == "2" },
            onClick = {},
            label = { it.label },
            testTag = { "tag_${it.id}" },
            role = Role.RadioButton,
        )
      }
    }

    composeTestRule.onNodeWithTag("tag_1").assertIsNotSelected()
    composeTestRule.onNodeWithTag("tag_2").assertIsSelected()
    composeTestRule.onNodeWithTag("tag_3").assertIsNotSelected()
  }

  @Test
  fun segmentedChoiceRowTriggersOnClickWhenTapped() {
    var selectedId by mutableStateOf("1")

    composeTestRule.setContent {
      MaterialTheme {
        SegmentedChoiceRow(
            items = testOptions,
            isSelected = { it.id == selectedId },
            onClick = { selectedId = it.id },
            label = { it.label },
            testTag = { "tag_${it.id}" },
            role = Role.Checkbox,
        )
      }
    }

    composeTestRule.onNodeWithTag("tag_1").assertIsSelected()
    composeTestRule.onNodeWithTag("tag_2").performClick()
    composeTestRule.runOnIdle { assertEquals("2", selectedId) }
    composeTestRule.onNodeWithTag("tag_2").assertIsSelected()
    composeTestRule.onNodeWithTag("tag_1").assertIsNotSelected()
  }

  @Test
  fun segmentShapeReturnsCircleShapeWhenSelected() {
    composeTestRule.setContent {
      val shape = segmentShape(index = 0, count = 3, selected = true)
      assertEquals(CircleShape, shape)
    }
  }
}
