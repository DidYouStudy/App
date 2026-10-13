// Co-authored-by: Claude AI Agent
package com.android.sample.ui.profile

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun isDisplayedAsClickableCircleOfDesignSize() {
    composeTestRule.setContent { ProfileButton(onClick = {}) }

    composeTestRule
        .onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON)
        .assertIsDisplayed()
        .assertHasClickAction()
        .assertWidthIsEqualTo(ProfileButtonSize)
        .assertHeightIsEqualTo(ProfileButtonSize)
  }

  @Test
  fun iconHasProfileContentDescription() {
    val description =
        ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.profile_button_description)
    composeTestRule.setContent { ProfileButton(onClick = {}) }

    composeTestRule
        .onNodeWithContentDescription(description)
        .assertIsDisplayed()
        .assertContentDescriptionEquals(description)
  }

  @Test
  fun clickInvokesOnClickOnce() {
    var clicks = 0
    composeTestRule.setContent { ProfileButton(onClick = { clicks++ }) }

    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()

    assertEquals(1, clicks)
  }
}
