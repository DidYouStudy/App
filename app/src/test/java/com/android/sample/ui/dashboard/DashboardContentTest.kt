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
  fun showsYourDayCardWithLocalizedTitle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardContent(sessions = emptyList()) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(DashboardContentTestTags.YOUR_DAY_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.YOUR_DAY_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_your_day))
  }

  @Test
  fun showsStatsCardWithLocalizedTitle() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardContent(sessions = emptyList()) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.STATS_CARD).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.STATS_TITLE)
        .assertTextEquals(context.getString(R.string.dashboard_your_stats))
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
  fun sessionItemsShowExactlyTheirTextsInInputAndOnScreenOrder() {
    val sessions =
        listOf(
            DashboardSession(startTime = "08:15", endTime = "09:45", subject = "Algebra"),
            DashboardSession(startTime = "13:00", endTime = "14:30", subject = "Chemistry"),
        )
    composeTestRule.setContent { DashboardContent(sessions = sessions) }

    composeTestRule.onNodeWithTag(DashboardContentTestTags.EMPTY_DAY).assertDoesNotExist()
    sessions.forEachIndexed { index, session ->
      val itemTag = DashboardContentTestTags.sessionItem(index)
      val itemNode = composeTestRule.onNodeWithTag(itemTag, useUnmergedTree = true)
      itemNode.assertIsDisplayed()
      itemNode.onChildren().assertCountEquals(3)
      listOf(session.startTime, session.endTime, session.subject).forEach { text ->
        composeTestRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasText(text, substring = false), useUnmergedTree = true)
            .assertCountEquals(1)
      }
      itemNode.assert(
          hasAnyDescendant(hasText(session.startTime, substring = false))
              .and(hasAnyDescendant(hasText(session.endTime, substring = false)))
              .and(hasAnyDescendant(hasText(session.subject, substring = false)))
      )
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
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun longSubjectWrapsAndRemainsFullyVisible() {
    val longSubject = "Advanced statistical mechanics and computational modelling workshop"
    composeTestRule.setContent {
      Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
        DashboardContent(
            sessions =
                listOf(
                    DashboardSession(
                        startTime = "10:00",
                        endTime = "12:00",
                        subject = longSubject,
                    ),
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
  fun lastOfEightSessionsCanBeScrolledIntoView() {
    val sessions =
        (1..8).map { index ->
          DashboardSession(
              startTime = "%02d:00".format(index),
              endTime = "%02d:00".format(index + 1),
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
  fun statsTilesShowTheirLocalizedLabels() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardContent(sessions = emptyList()) }

    listOf(
            DashboardContentTestTags.STAT_AVERAGE_DAILY to R.string.dashboard_stat_average_daily,
            DashboardContentTestTags.STAT_TOTAL_SESSIONS to R.string.dashboard_stat_total_sessions,
            DashboardContentTestTags.STAT_AVERAGE_FOCUS to R.string.dashboard_stat_average_focus,
        )
        .forEach { (tileTag, labelResource) ->
          composeTestRule.onNodeWithTag(tileTag).onChildren().assertCountEquals(2)
          composeTestRule
              .onNodeWithTag(tileTag)
              .assert(
                  hasAnyDescendant(hasText(context.getString(labelResource), substring = false))
              )
        }
  }

  @Test
  fun statsCardShowsLocalizedHint() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { DashboardContent(sessions = emptyList()) }

    composeTestRule
        .onNodeWithTag(DashboardContentTestTags.STATS_HINT)
        .assertTextEquals(context.getString(R.string.dashboard_stats_placeholder))
  }

  @Test
  fun defaultStatsShowLocalizedEmptyValueInEveryTile() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val emptyValue = context.getString(R.string.dashboard_stat_empty_value)
    composeTestRule.setContent {
      DashboardContent(sessions = emptyList(), stats = DashboardStats())
    }

    listOf(
            DashboardContentTestTags.STAT_AVERAGE_DAILY,
            DashboardContentTestTags.STAT_TOTAL_SESSIONS,
            DashboardContentTestTags.STAT_AVERAGE_FOCUS,
        )
        .forEach { tileTag ->
          composeTestRule.onNodeWithTag(tileTag).onChildren().assertCountEquals(2)
          composeTestRule
              .onNodeWithTag(tileTag)
              .assert(hasAnyDescendant(hasText(emptyValue, substring = false)))
        }
  }

  @Test
  fun populatedStatsShowOnlyTheirOwnValueInEachTile() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val emptyValue = context.getString(R.string.dashboard_stat_empty_value)
    val stats =
        DashboardStats(
            averageDailyStudyTime = "1h 20m",
            totalSessions = "12",
            averageFocusDuration = "35m",
        )
    val tileValues =
        listOf(
            DashboardContentTestTags.STAT_AVERAGE_DAILY to "1h 20m",
            DashboardContentTestTags.STAT_TOTAL_SESSIONS to "12",
            DashboardContentTestTags.STAT_AVERAGE_FOCUS to "35m",
        )
    composeTestRule.setContent { DashboardContent(sessions = emptyList(), stats = stats) }

    tileValues.forEach { (tileTag, value) ->
      composeTestRule.onNodeWithTag(tileTag).onChildren().assertCountEquals(2)
      composeTestRule
          .onNodeWithTag(tileTag)
          .assert(hasAnyDescendant(hasText(value, substring = false)))
    }
    composeTestRule
        .onNodeWithText(emptyValue, substring = false, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun sideBySideStatTilesHaveEqualHeightWhenLabelsWrap() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent {
      Box(Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
        DashboardContent(sessions = emptyList())
      }
    }

    val longLabelLayout = mutableListOf<TextLayoutResult>()
    composeTestRule
        .onNodeWithText(
            context.getString(R.string.dashboard_stat_average_daily),
            useUnmergedTree = true,
        )
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getTextLayoutResult ->
          assertTrue(getTextLayoutResult.invoke(longLabelLayout))
        }
    assertTrue(
        "The narrow layout must wrap the longer label",
        longLabelLayout.single().lineCount > 1,
    )

    val averageDailyBounds =
        composeTestRule
            .onNodeWithTag(DashboardContentTestTags.STAT_AVERAGE_DAILY)
            .getUnclippedBoundsInRoot()
    val totalSessionsBounds =
        composeTestRule
            .onNodeWithTag(DashboardContentTestTags.STAT_TOTAL_SESSIONS)
            .getUnclippedBoundsInRoot()
    assertEquals(
        "Side-by-side statistic tiles must have equal heights",
        averageDailyBounds.bottom - averageDailyBounds.top,
        totalSessionsBounds.bottom - totalSessionsBounds.top,
    )
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
