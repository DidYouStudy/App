// Co-authored-by: Claude Opus 5.5
package com.android.sample

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.ui.navigation.NavigationTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Robolectric smoke test that launches the real [MainActivity] and its navigation graph. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  /** The app starts on the Dashboard, with its top bar and bottom navigation visible. */
  @Test
  fun launchesOnDashboard() {
    composeTestRule
        .onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE)
        .assertIsDisplayed()
        .assertTextEquals("Dashboard")
    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
  }
}
