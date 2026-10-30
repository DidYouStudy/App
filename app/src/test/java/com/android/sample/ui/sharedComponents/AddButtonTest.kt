// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [AddButton]. */
@RunWith(AndroidJUnit4::class)
class AddButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun addButtonHasAccessibleDescriptionAndClickAction() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val description = context.getString(R.string.dashboard_add_description)
    composeTestRule.setContent {
      AddButton(
          onClick = {},
          contentDescription = description,
      )
    }

    composeTestRule
        .onNodeWithContentDescription(description)
        .assertIsDisplayed()
        .assertHasClickAction()
        .assertContentDescriptionEquals(description)
  }

  @Test
  fun eachAddButtonClickInvokesCallbackExactlyOnce() {
    var callbackCount = 0
    composeTestRule.setContent {
      AddButton(
          onClick = { callbackCount++ },
          contentDescription = "Add",
      )
    }

    composeTestRule.onNodeWithContentDescription("Add").performClick()
    assertEquals(1, callbackCount)
    composeTestRule.onNodeWithContentDescription("Add").performClick()
    assertEquals(2, callbackCount)
  }

  @Test
  fun previewRendersAddButton() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { AddButtonPreview() }

    composeTestRule
        .onNodeWithContentDescription(
            context.getString(R.string.dashboard_add_description),
        )
        .assertIsDisplayed()
  }
}
