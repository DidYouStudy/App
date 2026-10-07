package com.android.sample.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.android.sample.DidYouStudyApp
import com.android.sample.ui.navigation.NavigationTestTags
import junit.framework.TestCase.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// some tests inspired from NavigationB1Test.kt from the bootcamp repo
// TODO add firebase conditions for testing + new screens navigation tests
// TODO state management tests for the new screens
class NavigationTests {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun checkDashboardScreenIsDisplayed() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("dashboard", substring = true, ignoreCase = true)
  }

  private fun checkCalendarScreenIsDisplayed() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("calendar", substring = true, ignoreCase = true)
  }

  private fun checkGroupSessionScreenIsDisplayed() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains("Group Sessions", substring = false, ignoreCase = true)
  }

  private fun pressBack(shouldFinish: Boolean) {
    composeTestRule.activityRule.scenario.onActivity { activity ->
      activity.onBackPressedDispatcher.onBackPressed()
    }
    composeTestRule.waitUntil { composeTestRule.activity.isFinishing == shouldFinish }
    assertEquals(shouldFinish, composeTestRule.activity.isFinishing)
  }

  @Before
  fun setUp() {
    composeTestRule.setContent { DidYouStudyApp() }
  }

  @Test
  fun testTagsAreCorrectlySet() {
    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationIsDisplayedForDashboard() {
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationIsDisplayedForCalendar() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun bottomNavigationIsDisplayedForGroupSession() {
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }

  @Test
  fun tabsAreClickable() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.DASHBOARD_TAB)
        .assertIsDisplayed()
        .performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.CALENDAR_TAB)
        .assertIsDisplayed()
        .performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB)
        .assertIsDisplayed()
        .performClick()
  }

  @Test
  fun topBarTitleIsCorrectForDashboard() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains(value = "Dashboard")
  }

  @Test
  fun topBarTitleIsCorrectForCalendar() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextContains(value = "Calendar")
  }

  @Test
  fun navigationStartsOnDashboardTab() {
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun canNavigateToCalendar() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
  }

  @Test
  fun canNavigateToCalendarAndBackToDashboardUsingSystemBack() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    pressBack(shouldFinish = false)
    checkDashboardScreenIsDisplayed()
  }

  @Test
  fun canNavigateBetweenTabs() {
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).performClick()
    checkDashboardScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()
    checkGroupSessionScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).performClick()
    checkDashboardScreenIsDisplayed()
  }

  /**
   * This test verifies that the following navigation flow works as expected:
   * - Dashboard -> Calendar -> Calendar
   * - Press Back -> Dashboard (and not Calendar)
   */
  @Test
  fun navigateToCalendarFromCalendarAndPressBackGoesToDashboard() {
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).performClick()
    checkCalendarScreenIsDisplayed()
    pressBack(shouldFinish = false)
    assertFalse(composeTestRule.activity.isFinishing)
    checkDashboardScreenIsDisplayed()
  }
}
