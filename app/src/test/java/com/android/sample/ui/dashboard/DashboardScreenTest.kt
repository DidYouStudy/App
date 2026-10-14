// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.dashboard

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.ui.navigation.NavigationTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun screenWiresLocalizedDashboardCardsEmptyStateAndNavigationBars() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardScreen(navigationActions = null, onAddClick = {}) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.YOUR_DAY_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.YOUR_DAY_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_your_day))
    composeTestRule.onNodeWithTag(DashboardContentTestTags.STATS_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.STATS_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_your_stats))
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

  @Test
  fun topBarShowsLocalizedBoldDashboardTitle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardScreen(navigationActions = null, onAddClick = {}) }

    val results = mutableListOf<TextLayoutResult>()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_title))
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }

    assertEquals(FontWeight.Bold, results.single().layoutInput.style.fontWeight)
  }
}
