// Co-authored-by: Claude AI Agent
package com.android.sample.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun showsSettingsTitleAndNoBottomNavigation() {
    composeTestRule.setContent { SettingsScreen() }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals("Settings")
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertDoesNotExist()
  }

  @Test
  fun backButtonGoesBack() {
    val navigationActions = mock<NavigationActions>()
    composeTestRule.setContent { SettingsScreen() }

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    verify(navigationActions).goBack()
  }

  @Test
  fun backButtonWithoutNavigationActionsDoesNotCrash() {
    composeTestRule.setContent { SettingsScreen() }

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).performClick()
  }
}
