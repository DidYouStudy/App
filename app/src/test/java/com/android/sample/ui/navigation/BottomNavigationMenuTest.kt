// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.navigation

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the bottom navigation bar: localized tab labels, selection and tab clicks. */
@RunWith(AndroidJUnit4::class)
class BottomNavigationMenuTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  /** Each tab shows the label from its string resource, not a hardcoded name. */
  @Test
  fun tabsShowLabelsFromStringResources() {
    composeTestRule.setContent {
      BottomNavigationMenu(selectedTab = Tab.Dashboard, onTabSelected = {})
    }

    val expectedLabels =
        mapOf(
            Tab.Dashboard to R.string.nav_tab_dashboard,
            Tab.Calendar to R.string.nav_tab_calendar,
            Tab.GroupSession to R.string.nav_tab_group_session,
        )
    expectedLabels.forEach { (tab, labelRes) ->
      composeTestRule
          .onNodeWithTag(NavigationTestTags.getTabTestTag(tab))
          .assert(hasText(context.getString(labelRes)))
    }
  }

  /** Only the tab passed as selected is marked selected. */
  @Test
  fun onlySelectedTabIsMarkedSelected() {
    composeTestRule.setContent {
      BottomNavigationMenu(selectedTab = Tab.Calendar, onTabSelected = {})
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.CALENDAR_TAB).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.DASHBOARD_TAB).assertIsNotSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).assertIsNotSelected()
  }

  /** Tapping a tab reports that tab to the caller. */
  @Test
  fun clickingTabReportsSelectedTab() {
    val selected = mutableListOf<Tab>()
    composeTestRule.setContent {
      BottomNavigationMenu(selectedTab = Tab.Dashboard, onTabSelected = { selected += it })
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.GROUP_SESSION_TAB).performClick()

    assertEquals(listOf<Tab>(Tab.GroupSession), selected)
  }
}
