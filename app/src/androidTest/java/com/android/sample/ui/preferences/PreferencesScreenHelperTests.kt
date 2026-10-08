// Co-authored-by: Claude AI Agent
package com.android.sample.ui.preferences

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags
import org.junit.Rule
import org.junit.Test

class PreferencesScreenHelperTests {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun headerDisplaysTitle() {
    composeTestRule.setContent { MaterialTheme { PreferenceHeader() } }
    composeTestRule.onNodeWithTag(Tags.PREFERENCE_TITLE).assertIsDisplayed()
  }

  @Test
  fun sectionLabelDisplaysTextWithGivenTag() {
    composeTestRule.setContent {
      MaterialTheme { SectionLabel(text = "Some label", testTag = "customLabelTag") }
    }
    composeTestRule.onNodeWithTag("customLabelTag").assertIsDisplayed()
  }

  @Test
  fun widePillButtonDisplaysTextAndHandlesClicks() {
    var clicks = 0
    composeTestRule.setContent {
      MaterialTheme { WidePillButton(text = "Press me", onClick = { clicks++ }, testTag = "pill") }
    }
    composeTestRule.onNodeWithTag("pill").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pill").performClick()
    composeTestRule.onNodeWithTag("pill").performClick()
    assert(clicks == 2)
  }

  @Test
  fun multiChoiceRowShowsAllOptionsAndMarksSelectedOnes() {
    composeTestRule.setContent {
      MaterialTheme {
        MultiChoiceRow(
            options = StudyTime.entries,
            selected = setOf(StudyTime.EVENING, StudyTime.NIGHT),
            onToggle = {},
        )
      }
    }
    StudyTime.entries.forEach {
      composeTestRule.onNodeWithTag(it.testTag).assertIsDisplayed().assertTextEquals(it.label)
    }
    composeTestRule.onNodeWithTag(Tags.STUDY_TIME_MORNING).assertIsNotSelected()
    composeTestRule.onNodeWithTag(Tags.STUDY_TIME_AFTERNOON).assertIsNotSelected()
    composeTestRule.onNodeWithTag(Tags.STUDY_TIME_EVENING).assertIsSelected()
    composeTestRule.onNodeWithTag(Tags.STUDY_TIME_NIGHT).assertIsSelected()
  }

  @Test
  fun singleChoiceRowMarksOnlyTheSelectedOption() {
    composeTestRule.setContent {
      MaterialTheme {
        SingleChoiceRow(
            options = BreakDuration.entries,
            selected = BreakDuration.FIFTEEN_MIN,
            onSelect = {},
        )
      }
    }
    BreakDuration.entries.forEach {
      composeTestRule.onNodeWithTag(it.testTag).assertIsDisplayed().assertTextEquals(it.label)
      if (it == BreakDuration.FIFTEEN_MIN)
          composeTestRule.onNodeWithTag(it.testTag).assertIsSelected()
      else composeTestRule.onNodeWithTag(it.testTag).assertIsNotSelected()
    }
  }

  @Test
  fun choiceRowHandlesSingleOption() {
    var clicked: SessionLength? = null
    composeTestRule.setContent {
      MaterialTheme {
        ChoiceRow(
            options = listOf(SessionLength.ONE_HOUR),
            isSelected = { false },
            onClick = { clicked = it },
            role = Role.RadioButton,
        )
      }
    }
    composeTestRule.onNodeWithTag(Tags.SESSION_LENGTH_1_HOUR).assertIsDisplayed().performClick()
    assert(clicked == SessionLength.ONE_HOUR)
  }
}
