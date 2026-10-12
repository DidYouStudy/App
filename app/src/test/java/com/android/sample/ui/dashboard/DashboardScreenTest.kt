// Co-authored-by: GitHub Copilot
package com.android.sample.ui.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.ui.navigation.NavigationTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun screenShowsDashboardContentEmptyStateAddButtonAndNavigationBars() {
    composeTestRule.setContent { DashboardScreen(navigationActions = null, onAddClick = {}) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.EMPTY_DAY).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun screenAddButtonInvokesCallbackExactlyOnce() {
    var callbackCount = 0
    composeTestRule.setContent {
      DashboardScreen(navigationActions = null, onAddClick = { callbackCount++ })
    }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()

    assertEquals(1, callbackCount)
  }
}
