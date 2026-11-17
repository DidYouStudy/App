// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.sharedComponents

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit/UI tests for [OfflineBanner]. */
@RunWith(AndroidJUnit4::class)
class OfflineBannerTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun offlineBannerDisplaysIconAndLocalizedText() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val expectedText = context.getString(R.string.offline_banner_text)

    composeTestRule.setContent { OfflineBanner() }

    composeTestRule.onNodeWithTag(OfflineBannerTestTags.OFFLINE_BANNER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(OfflineBannerTestTags.OFFLINE_BANNER_ICON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(OfflineBannerTestTags.OFFLINE_BANNER_TEXT).assertIsDisplayed()
    composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
  }

  @Test
  fun offlineBannerPreviewsRenderWithoutErrors() {
    composeTestRule.setContent {
      OfflineBannerLightPreview()
      OfflineBannerDarkPreview()
    }
    composeTestRule.onAllNodesWithTag(OfflineBannerTestTags.OFFLINE_BANNER).assertCountEquals(2)
  }
}
