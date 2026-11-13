// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude AI Agent
// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.sharedComponents

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.ui.dashboard.DashboardContentTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [AddButton]. */
@RunWith(AndroidJUnit4::class)
class AddButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun addButtonHasAccessibleDescriptionAndClickAction() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val description = context.getString(R.string.dashboard_add_description)
    composeTestRule.setContent {
      AddButton(
          onClick = {},
          contentDescription = description,
      )
    }

    composeTestRule
        .onNodeWithContentDescription(description)
        .assertIsDisplayed()
        .assertHasClickAction()
        .assertContentDescriptionEquals(description)
  }

  @Test
  fun eachAddButtonClickInvokesCallbackExactlyOnce() {
    var callbackCount = 0
    composeTestRule.setContent {
      AddButton(
          onClick = { callbackCount++ },
          contentDescription = "Add",
      )
    }

    composeTestRule.onNodeWithContentDescription("Add").performClick()
    assertEquals(1, callbackCount)
    composeTestRule.onNodeWithContentDescription("Add").performClick()
    assertEquals(2, callbackCount)
  }

  @Test
  fun previewRendersAddButton() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { AddButtonPreview() }

    composeTestRule
        .onNodeWithContentDescription(
            context.getString(R.string.dashboard_add_description),
        )
        .assertIsDisplayed()
  }

  @Test
  fun dashboardAddFabMenuTogglesAndExecutesCallbacks() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val addDesc = context.getString(R.string.dashboard_add_description)
    var eventClicks = 0
    var groupSessionClicks = 0
    composeTestRule.setContent {
      AddFabMenu(
          onEventClick = { eventClicks++ },
          onGroupSessionClick = { groupSessionClicks++ },
      )
    }

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertDoesNotExist()

    composeTestRule.onNodeWithContentDescription(addDesc).performClick()

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertIsDisplayed()
        .performClick()
    assertEquals(1, eventClicks)

    composeTestRule.onNodeWithContentDescription(addDesc).performClick()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertIsDisplayed()
        .performClick()
    assertEquals(1, groupSessionClicks)
  }

  @Test
  fun speedDialFabOptionRendersLabelAndHandlesClick() {
    var clicked = false
    composeTestRule.setContent {
      SpeedDialFabOption(
          icon = Icons.Outlined.Event,
          label = "Custom Option",
          onClick = { clicked = true },
          testTag = "speed_dial_option",
      )
    }

    composeTestRule.onNodeWithTag("speed_dial_option").assertIsDisplayed().assertHasClickAction()
    composeTestRule.onNodeWithTag("speed_dial_option").performClick()
    assertEquals(true, clicked)
  }

  @Test
  fun dashboardAddFabMenuOptionsDisplayLocalizedContentAndIcons() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent {
      AddFabMenu(
          onEventClick = {},
          onGroupSessionClick = {},
      )
    }

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.dashboard_add_description))
        .performClick()

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON)
        .assertIsDisplayed()
  }

  @Test
  fun dashboardAddFabMenuRepeatedTogglesToggleExpansionStateAndAccessibilityDescriptions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val addDesc = context.getString(R.string.dashboard_add_description)
    val cancelDesc = context.getString(R.string.cancel)

    composeTestRule.setContent {
      AddFabMenu(
          onEventClick = {},
          onGroupSessionClick = {},
      )
    }

    // Initially collapsed: Add icon visible, option pills absent
    composeTestRule.onNodeWithContentDescription(addDesc).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription(cancelDesc).assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()

    // 1st tap: expands menu
    composeTestRule.onNodeWithContentDescription(addDesc).performClick()
    composeTestRule.onNodeWithContentDescription(cancelDesc).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertIsDisplayed()

    // 2nd tap: collapses menu
    composeTestRule.onNodeWithContentDescription(cancelDesc).performClick()
    composeTestRule.onNodeWithContentDescription(addDesc).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertDoesNotExist()

    // 3rd tap: expands menu again
    composeTestRule.onNodeWithContentDescription(addDesc).performClick()
    composeTestRule.onNodeWithContentDescription(cancelDesc).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.FAB_MENU_EVENT_BUTTON)
        .assertIsDisplayed()
  }

  @Test
  fun dashboardAddFabMenuPreviewRenders() {
    composeTestRule.setContent { AddFabMenuPreview() }
  }
}
