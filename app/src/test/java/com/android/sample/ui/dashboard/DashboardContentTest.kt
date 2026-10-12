// Co-authored-by: GitHub Copilot
package com.android.sample.ui.dashboard

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
class DashboardContentTest {
  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun alwaysShowsDashboardCardsAndLocalizedStats() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardContent(sessions = emptyList()) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.YOUR_DAY_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.YOUR_DAY_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_your_day))
    composeTestRule.onNodeWithTag(DashboardContentTestTags.STATS_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.STATS_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_your_stats))
    composeTestRule
        .onNodeWithText(context.getString(R.string.dashboard_stats_placeholder))
        .assertIsDisplayed()
  }

  @Test
  fun emptySessionsShowLocalizedEmptyDayWithoutSessionItems() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardContent(sessions = emptyList()) }

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.EMPTY_DAY)
        .assertIsDisplayed()
        .assertTextEquals(context.getString(R.string.dashboard_no_sessions))
    composeTestRule.onNodeWithTag(DashboardContentTestTags.sessionItem(0)).assertDoesNotExist()
  }

  @Test
  fun sessionsAppearInInputOrderWithOnlyTheirOwnTexts() {
    val sessions =
        listOf(
            DashboardSession(timeRange = "08:15 - 09:45", subject = "Algebra"),
            DashboardSession(timeRange = "13:00 - 14:30", subject = "Chemistry"),
        )
    composeTestRule.setContent { DashboardContent(sessions = sessions) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.EMPTY_DAY).assertDoesNotExist()
    sessions.forEachIndexed { index, session ->
      val itemNode =
          composeTestRule.onNodeWithTag(
              DashboardContentTestTags.sessionItem(index),
              useUnmergedTree = true,
          )
      itemNode.assertIsDisplayed()
      itemNode.assert(
          hasAnyDescendant(hasText(session.timeRange, substring = false))
              .and(hasAnyDescendant(hasText(session.subject, substring = false)))
      )
      itemNode.onChildren().assertCountEquals(2)
      if (index > 0) {
        val previousTop =
            composeTestRule
                .onNodeWithTag(
                    DashboardContentTestTags.sessionItem(index - 1),
                    useUnmergedTree = true,
                )
                .getUnclippedBoundsInRoot()
                .top
        val currentTop = itemNode.getUnclippedBoundsInRoot().top
        assertTrue(
            "Session item ${index - 1} must appear above session item $index",
            previousTop < currentTop,
        )
      }
    }
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.sessionItem(sessions.size))
        .assertDoesNotExist()
  }

  @Test
  // NATIVE graphics are needed for Robolectric to measure text layout correctly.
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun longSubjectIsDisplayedInFull() {
    val longSubject = "Advanced statistical mechanics and computational modelling workshop"
    composeTestRule.setContent {
      Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
        DashboardContent(
            sessions =
                listOf(
                    DashboardSession(timeRange = "10:00 - 12:00", subject = longSubject),
                ),
        )
      }
    }

    var textLayoutResult: TextLayoutResult? = null
    composeTestRule
        .onNodeWithText(longSubject, useUnmergedTree = true)
        .assertIsDisplayed()
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getTextLayoutResult ->
          val results = mutableListOf<TextLayoutResult>()
          assertTrue(getTextLayoutResult.invoke(results))
          textLayoutResult = results.single()
        }

    val layoutResult = checkNotNull(textLayoutResult)
    assertFalse(layoutResult.hasVisualOverflow)
    assertTrue(layoutResult.lineCount > 1)
  }

  @Test
  fun lastOfManySessionsCanBeScrolledIntoView() {
    val sessions =
        (1..8).map { index ->
          DashboardSession(
              timeRange = "%02d:00 - %02d:00".format(index, index + 1),
              subject = "Subject $index",
          )
        }
    val lastSession = sessions.last()
    val lastSessionTag = DashboardContentTestTags.sessionItem(sessions.lastIndex)
    composeTestRule.setContent { DashboardContent(sessions = sessions) }

    composeTestRule.onNodeWithTag(lastSessionTag).performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText(lastSession.subject).assertIsDisplayed()
  }

  @Test
  fun addButtonHasAccessibleDescriptionAndClickAction() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardAddButton(onClick = {}) }

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.ADD_BUTTON)
        .assertIsDisplayed()
        .assertHasClickAction()
        .assertContentDescriptionEquals(context.getString(R.string.dashboard_add_description))
  }

  @Test
  fun eachAddButtonClickInvokesCallbackExactlyOnce() {
    var callbackCount = 0
    composeTestRule.setContent { DashboardAddButton(onClick = { callbackCount++ }) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    assertEquals(1, callbackCount)
    composeTestRule.onNodeWithTag(DashboardContentTestTags.ADD_BUTTON).performClick()
    assertEquals(2, callbackCount)
  }
}
