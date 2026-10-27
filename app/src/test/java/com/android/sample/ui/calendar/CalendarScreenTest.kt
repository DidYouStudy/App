// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** Covers the calendar screen shell, selected navigation item, and add affordance. */
@RunWith(AndroidJUnit4::class)
class CalendarScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Shows the title, selects Calendar, exposes the add action, and routes tab clicks. */
  @Test
  fun screenShowsCalendarChromeAndHandlesNavigationClick() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val navigationActions = mock<NavigationActions>()
    composeTestRule.setContent { CalendarScreen(navigationActions) }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.calendar_title))
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Calendar)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(CalendarTestTags.ADD_BUTTON)
        .assertHasClickAction()
        .assertContentDescriptionEquals("Add calendar event")

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Dashboard)).performClick()
    verify(navigationActions).navigateTo(Screen.Dashboard)
  }
}
