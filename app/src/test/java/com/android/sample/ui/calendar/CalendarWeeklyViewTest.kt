// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

// import androidx.compose.ui.test.onAllNodes
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the calendar's week navigation, day header, and empty weekly grid. */
@RunWith(AndroidJUnit4::class)
class CalendarWeeklyViewTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Gives the week navigation icons their string-resource content descriptions. */
  @Test
  fun weekNavigationButtonsHaveContentDescriptions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    composeTestRule.setContent { CalendarWeeklyView(today = LocalDate.of(2026, 10, 14)) }

    composeTestRule
        .onNodeWithContentDescription(
            context.getString(R.string.calendar_previous_week_description)
        )
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.calendar_next_week_description))
        .assertIsDisplayed()
  }

  /** Shows the Monday-to-Sunday range and moves backward and forward by one week. */
  @Test
  fun weekRangeControlsNavigateByOneWeek() {
    composeTestRule.setContent { CalendarWeeklyView(today = LocalDate.of(2026, 10, 14)) }

    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Oct 12–18, 2026")
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Oct 19–25, 2026")
    composeTestRule.onNodeWithTag(CalendarTestTags.PREVIOUS_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Oct 12–18, 2026")
  }

  /** Navigates across a year boundary and restores the original week after matching steps. */
  @Test
  fun weekNavigationCrossesYearBoundaryAndRestoresAfterRepeatedSteps() {
    composeTestRule.setContent { CalendarWeeklyView(today = LocalDate.of(2027, 1, 6)) }

    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Jan 4–10, 2027")
    composeTestRule.onNodeWithTag(CalendarTestTags.PREVIOUS_WEEK).performClick()
    composeTestRule
        .onNodeWithTag(CalendarTestTags.WEEK_RANGE)
        .assertTextEquals("Dec 28, 2026–Jan 3, 2027")
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Jan 4–10, 2027")
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Jan 11–17, 2027")
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Jan 18–24, 2027")
    composeTestRule.onNodeWithTag(CalendarTestTags.PREVIOUS_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Jan 11–17, 2027")
    composeTestRule.onNodeWithTag(CalendarTestTags.PREVIOUS_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Jan 4–10, 2027")
  }

  /** Shows all seven day labels and gives today's date its visual treatment. */
  @Test
  fun dayHeaderShowsSevenDatesAndHighlightsToday() {
    val today = LocalDate.of(2026, 10, 14)
    composeTestRule.setContent { CalendarWeeklyView(today = today) }

    composeTestRule.onNodeWithTag(CalendarTestTags.dayName(today)).assertTextEquals("W")
    composeTestRule.onNodeWithTag(CalendarTestTags.dayNumber(today)).assertTextEquals("14")
    composeTestRule
        .onNodeWithTag(CalendarTestTags.dayNumber(today.minusDays(1)))
        .assertTextEquals("13")
    (0L..6L).forEach { offset ->
      val date = LocalDate.of(2026, 10, 12).plusDays(offset)
      composeTestRule.onNodeWithTag(CalendarTestTags.dayName(date)).assertIsDisplayed()
      composeTestRule.onNodeWithTag(CalendarTestTags.dayNumber(date)).assertIsDisplayed()
    }
  }

  /** Shows today's date only while its week is displayed. */
  @Test
  fun todayDateIsVisibleOnlyInItsDisplayedWeek() {
    val today = LocalDate.of(2026, 10, 14)
    composeTestRule.setContent { CalendarWeeklyView(today = today) }

    composeTestRule.onNodeWithTag(CalendarTestTags.dayNumber(today)).assertTextEquals("14")
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.dayNumber(today)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CalendarTestTags.PREVIOUS_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.dayNumber(today)).assertTextEquals("14")
  }

  /** Shows the timeline with its initial scroll position at 8 AM. */
  @Test
  fun weeklyGridStartsAtEightInTheMorning() {
    composeTestRule.setContent { CalendarWeeklyView(today = LocalDate.of(2026, 10, 14)) }

    composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CalendarTestTags.hourLabel(8))
        .assertIsDisplayed()
        .assertTextEquals("8 AM")
    composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(7)).assertIsNotDisplayed()
  }

  /** Labels every hour in order on the 12-hour clock. */
  @Test
  fun weeklyGridShowsAllTwentyFourHourLabels() {
    composeTestRule.setContent {
      Box(Modifier.height(400.dp)) { CalendarTimeGrid(Modifier.fillMaxSize()) }
    }

    val expectedLabels =
        listOf("12 AM") + (1..11).map { "$it AM" } + listOf("12 PM") + (1..11).map { "$it PM" }
    expectedLabels.forEachIndexed { hour, label ->
      composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(hour)).assertTextEquals(label)
    }
    composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(8)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(7)).assertIsNotDisplayed()
  }

  /** Does not apply a delayed initial jump after a zero-height viewport becomes scrollable. */
  @Test
  fun gridDoesNotJumpWhenViewportGainsScrollRangeAfterInitialLayout() {
    val viewportHeight = mutableStateOf(0.dp)
    composeTestRule.setContent {
      Box(Modifier.height(viewportHeight.value)) { CalendarTimeGrid(Modifier.fillMaxSize()) }
    }

    composeTestRule.runOnIdle { viewportHeight.value = 400.dp }

    composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(0)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(CalendarTestTags.hourLabel(8)).assertIsDisplayed()
  }

  /** Formats valid weeks that cross month and year boundaries. */
  @Test
  fun weekRangeFormatsMonthAndYearBoundaries() {
    assertEquals(
        "Oct 12–18, 2026",
        formatWeekRange(CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 14))),
    )
    assertEquals(
        "Oct 26–Nov 1, 2026",
        formatWeekRange(CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 28))),
    )
    assertEquals(
        "Dec 28, 2026–Jan 3, 2027",
        formatWeekRange(CalendarDateHelper().weekDates(LocalDate.of(2026, 12, 30))),
    )
  }

  /** Rejects seven dates that do not form one ordered Monday-to-Sunday week. */
  @Test
  fun weekRangeRejectsInvalidWeekDates() {
    val monday = LocalDate.of(2026, 10, 12)

    assertThrows(IllegalArgumentException::class.java) {
      formatWeekRange((1L..7L).map(monday::plusDays))
    }
    assertThrows(IllegalArgumentException::class.java) {
      formatWeekRange((0L..5L).map(monday::plusDays) + monday.plusDays(7))
    }
    assertThrows(IllegalArgumentException::class.java) {
      formatWeekRange((0L..5L).map(monday::plusDays))
    }
    assertThrows(IllegalArgumentException::class.java) {
      val unorderedWeek =
          listOf(monday, monday.plusDays(2), monday.plusDays(1)) + (3L..6L).map(monday::plusDays)
      formatWeekRange(unorderedWeek)
    }
  }
}
