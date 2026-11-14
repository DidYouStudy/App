// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.profile.ProfileButtonTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** Unit tests for [MainTopAppBar] and [SecondaryMediumTopAppBar]. */
@RunWith(AndroidJUnit4::class)
class TopAppBarsTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun mainTopAppBarDisplaysTitleAndProfileButton() {
    composeTestRule.setContent {
      MainTopAppBar(
          title = "Test Title",
      )
    }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals("Test Title")
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun mainTopAppBarProfileButtonClickInvokesCallback() {
    var profileClicked = false
    composeTestRule.setContent {
      MainTopAppBar(
          title = "Dashboard",
          onProfileClick = { profileClicked = true },
      )
    }

    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    assertEquals(true, profileClicked)
  }

  @Test
  fun mainTopAppBarWithNavigationActionsNavigatesToSettings() {
    val navigationActions = mock<NavigationActions>()
    composeTestRule.setContent {
      MainTopAppBar(
          title = "Group Sessions",
          navigationActions = navigationActions,
      )
    }

    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
    verify(navigationActions).navigateTo(Screen.Settings)
  }

  @Test
  fun mainTopAppBarRendersExtraActionsBeforeProfileButton() {
    composeTestRule.setContent {
      MainTopAppBar(
          title = "Calendar",
          actions = { Text("Today", modifier = Modifier.testTag("customAction")) },
      )
    }

    composeTestRule.onNodeWithTag("customAction").assertIsDisplayed().assertTextEquals("Today")
    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).assertIsDisplayed()
  }

  @Test
  fun secondaryMediumTopAppBarDisplaysTitleAndBackButton() {
    var backClicked = false
    composeTestRule.setContent {
      SecondaryMediumTopAppBar(
          title = "Settings",
          onBackClick = { backClicked = true },
      )
    }

    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals("Settings")
    composeTestRule
        .onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON)
        .assertIsDisplayed()
        .performClick()
    assertEquals(true, backClicked)
  }

  @Test
  fun secondaryMediumTopAppBarHidesBackButtonWhenOnBackClickIsNull() {
    composeTestRule.setContent {
      SecondaryMediumTopAppBar(
          title = "Preferences",
          onBackClick = null,
      )
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertDoesNotExist()
  }

  @Test
  fun secondaryMediumTopAppBarDisablesBackButtonWhenConfigured() {
    composeTestRule.setContent {
      SecondaryMediumTopAppBar(
          title = "Edit Event",
          onBackClick = {},
          backButtonEnabled = false,
      )
    }

    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun secondaryMediumTopAppBarSupportsCustomTitleContent() {
    composeTestRule.setContent {
      SecondaryMediumTopAppBar(
          titleContent = { Text("Custom Title", modifier = Modifier.testTag("customTitle")) },
          onBackClick = {},
      )
    }

    composeTestRule
        .onNodeWithTag("customTitle")
        .assertIsDisplayed()
        .assertTextEquals("Custom Title")
    composeTestRule.onNodeWithTag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()
  }
}
