// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.event.EventType
import com.android.sample.ui.theme.label
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI tests for the components shared by the group session and event details screens. */
@RunWith(AndroidJUnit4::class)
class EventSharedComponentsTest {
  @get:Rule val composeTestRule = createComposeRule()

  private companion object {
    const val BADGE_TAG = "badge"
    const val ROW_TAG = "row"
    const val CHECKBOX_TAG = "checkbox"
    const val TRAILING_TAG = "trailing"
  }

  // EventTypeBadge

  @Test
  fun badgeShowsTheLabelOfEveryEventType() {
    lateinit var labels: List<String>
    composeTestRule.setContent {
      labels = EventType.entries.map { it.label() }
      Column {
        EventType.entries.forEachIndexed { index, type ->
          EventTypeBadge(type = type, modifier = Modifier.testTag("$BADGE_TAG$index"))
        }
      }
    }

    labels.forEachIndexed { index, label ->
      composeTestRule.onNodeWithTag("$BADGE_TAG$index").assertIsDisplayed()
      composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }
  }

  // IconTextRow

  @Test
  fun iconTextRowShowsItsText() {
    composeTestRule.setContent {
      IconTextRow(
          icon = Icons.Default.AccessTime,
          text = "Nov 2, 2026 • 10:15 - 12:00",
          modifier = Modifier.testTag(ROW_TAG),
      )
    }

    composeTestRule.onNodeWithTag(ROW_TAG).assertIsDisplayed()
    composeTestRule.onNodeWithText("Nov 2, 2026 • 10:15 - 12:00").assertIsDisplayed()
  }

  @Test
  fun iconTextRowAppliesTheCallerModifierToExactlyOneNode() {
    composeTestRule.setContent {
      IconTextRow(
          icon = Icons.Default.AccessTime,
          text = "CM5",
          modifier = Modifier.testTag(ROW_TAG),
      )
    }

    assertEquals(1, composeTestRule.onAllNodesWithTag(ROW_TAG).fetchSemanticsNodes().size)
  }

  // TaskCheckRow

  private fun setRow(
      isCompleted: Boolean = false,
      checkboxTag: String? = CHECKBOX_TAG,
      onToggle: () -> Unit = {},
      trailing: (@Composable () -> Unit)? = null,
  ) {
    composeTestRule.setContent {
      TaskCheckRow(
          text = "Solve exercises 12, 13",
          isCompleted = isCompleted,
          onToggle = onToggle,
          modifier = Modifier.testTag(ROW_TAG),
          checkboxTag = checkboxTag,
          trailingContent = trailing,
      )
    }
  }

  @Test
  fun taskRowShowsItsText() {
    setRow()

    composeTestRule.onNodeWithTag(ROW_TAG).assertIsDisplayed()
    composeTestRule.onNodeWithText("Solve exercises 12, 13").assertIsDisplayed()
  }

  @Test
  fun uncheckedTaskShowsUncheckedBox() {
    setRow(isCompleted = false)

    composeTestRule.onNodeWithTag(CHECKBOX_TAG).assertIsOff()
  }

  @Test
  fun completedTaskShowsCheckedBox() {
    setRow(isCompleted = true)

    composeTestRule.onNodeWithTag(CHECKBOX_TAG).assertIsOn()
  }

  @Test
  fun clickingTheCheckboxInvokesToggleOnce() {
    var toggles = 0
    setRow(onToggle = { toggles++ })

    composeTestRule.onNodeWithTag(CHECKBOX_TAG).performClick()

    assertEquals(1, toggles)
  }

  @Test
  fun checkboxIsStillAvailableWithoutATag() {
    var toggles = 0
    setRow(checkboxTag = null, onToggle = { toggles++ })

    composeTestRule.onNode(isToggleable()).performClick()

    assertEquals(1, toggles)
  }

  @Test
  fun trailingContentIsShownWhenProvided() {
    setRow(trailing = { Text("delete", modifier = Modifier.testTag(TRAILING_TAG)) })

    composeTestRule.onNodeWithTag(TRAILING_TAG).assertIsDisplayed().assertTextEquals("delete")
  }

  @Test
  fun withoutTrailingContentNothingExtraIsShown() {
    setRow(trailing = null)

    composeTestRule.onNodeWithTag(TRAILING_TAG).assertDoesNotExist()
  }

  @Test
  fun clickingTrailingContentDoesNotToggleTheTask() {
    var toggles = 0
    var trailingClicks = 0
    setRow(
        onToggle = { toggles++ },
        trailing = {
          TextButton(
              onClick = { trailingClicks++ },
              modifier = Modifier.testTag(TRAILING_TAG),
          ) {
            Text("delete")
          }
        },
    )

    composeTestRule.onNodeWithTag(TRAILING_TAG).performClick()

    assertEquals(1, trailingClicks)
    assertEquals(0, toggles)
  }
}
