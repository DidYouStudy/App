// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

// import androidx.compose.ui.test.assertDoesNotExist
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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
    composeTestRule.setContent {
      CalendarWeeklyView(
          uiState = calendarState(LocalDate.of(2026, 10, 14)),
          onPreviousWeek = {},
          onNextWeek = {},
      )
    }

    composeTestRule
        .onNodeWithContentDescription(
            context.getString(R.string.calendar_previous_week_description)
        )
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.calendar_next_week_description))
        .assertIsDisplayed()
  }

  /** Forwards previous and next week clicks to their owner. */
  @Test
  fun weekRangeControlsForwardNavigationCallbacks() {
    var previousClicks = 0
    var nextClicks = 0
    composeTestRule.setContent {
      CalendarWeeklyView(
          uiState = calendarState(LocalDate.of(2026, 10, 14)),
          onPreviousWeek = { previousClicks++ },
          onNextWeek = { nextClicks++ },
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertTextEquals("Oct 12–18, 2026")
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.PREVIOUS_WEEK).performClick()
    composeTestRule.runOnIdle {
      assertEquals(1, previousClicks)
      assertEquals(1, nextClicks)
    }
  }

  /** Shows all seven day labels and gives today's date its visual treatment. */
  @Test
  fun dayHeaderShowsSevenDatesAndHighlightsToday() {
    val today = LocalDate.of(2026, 10, 14)
    composeTestRule.setContent {
      CalendarWeeklyView(uiState = calendarState(today), onPreviousWeek = {}, onNextWeek = {})
    }

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

  /** Renders dates from the supplied ViewModel state after its owner changes the week. */
  @Test
  fun displayedDatesFollowSuppliedState() {
    val today = LocalDate.of(2026, 10, 14)
    composeTestRule.setContent {
      val state = remember { mutableStateOf(calendarState(today)) }
      CalendarWeeklyView(
          uiState = state.value,
          onPreviousWeek = {},
          onNextWeek = {
            val weekStart = state.value.weekStart.plusWeeks(1)
            state.value =
                state.value.copy(
                    weekStart = weekStart,
                    weekDates = CalendarDateHelper().weekDates(weekStart),
                )
          },
      )
    }

    composeTestRule.onNodeWithTag(CalendarTestTags.dayNumber(today)).assertTextEquals("14")
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.dayNumber(today)).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CalendarTestTags.NEXT_WEEK).performClick()
    composeTestRule.onNodeWithTag(CalendarTestTags.dayNumber(today)).assertDoesNotExist()
  }

  /** Shows the timeline with its initial scroll position at 8 AM. */
  @Test
  fun weeklyGridStartsAtEightInTheMorning() {
    composeTestRule.setContent {
      CalendarWeeklyView(
          uiState = calendarState(LocalDate.of(2026, 10, 14)),
          onPreviousWeek = {},
          onNextWeek = {},
      )
    }

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

  /** Calculates one placement with the event's local start time and duration. */
  @Test
  fun eventPlacementUsesLocalStartTimeAndDuration() {
    val weekDates = CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 14))
    val event = eventOn(weekDates[2], LocalTime.of(9, 30), LocalTime.of(11, 0))

    val placement = calculateEventPlacements(event, weekDates, 10.dp).single()

    assertEquals(2, placement.dayIndex)
    assertEquals(95.dp, placement.top)
    assertEquals(15.dp, placement.height)
  }

  /** Splits an event crossing midnight into adjacent cards with the same event contents. */
  @Test
  fun eventPlacementSplitsAtMidnight() {
    val weekDates = CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 14))
    val event = eventOn(weekDates[2], LocalTime.of(23, 0), weekDates[3], LocalTime.of(1, 0))

    val placements = calculateEventPlacements(event, weekDates, 10.dp)

    assertEquals(2, placements.size)
    assertEquals(2, placements[0].dayIndex)
    assertEquals(230f, placements[0].top.value, 0.001f)
    assertEquals(10.dp, placements[0].height)
    assertEquals(3, placements[1].dayIndex)
    assertEquals(0.dp, placements[1].top)
    assertEquals(10.dp, placements[1].height)
    assertEquals(event, placements[0].event)
    assertEquals(event, placements[1].event)
  }

  /** Ignores placements outside the displayed week while retaining in-week event segments. */
  @Test
  fun eventPlacementFiltersDatesOutsideDisplayedWeek() {
    val weekDates = CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 14))
    val event =
        eventOn(
            weekDates.first().minusDays(1),
            LocalTime.of(23, 0),
            weekDates.first(),
            LocalTime.of(1, 0),
        )

    val placements = calculateEventPlacements(event, weekDates, 10.dp)

    assertEquals(1, placements.size)
    assertEquals(0, placements.single().dayIndex)
    assertEquals(10.dp, placements.single().height)
  }

  /** Produces no card for an event that does not intersect the displayed week. */
  @Test
  fun eventPlacementOmitsEventOutsideDisplayedWeek() {
    val weekDates = CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 14))
    val event =
        eventOn(
            weekDates.last().plusDays(1),
            LocalTime.of(9, 0),
            weekDates.last().plusDays(1),
            LocalTime.of(10, 0),
        )

    assertTrue(calculateEventPlacements(event, weekDates, 10.dp).isEmpty())
  }

  /** Does not create a zero-height segment when an event ends exactly at midnight. */
  @Test
  fun eventPlacementDoesNotCreateZeroLengthMidnightSegment() {
    val weekDates = CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 14))
    val event = eventOn(weekDates[2], LocalTime.of(23, 0), weekDates[3], LocalTime.MIDNIGHT)

    val placements = calculateEventPlacements(event, weekDates, 10.dp)

    assertEquals(1, placements.size)
    assertEquals(2, placements.single().dayIndex)
    assertEquals(10.dp, placements.single().height)
  }

  /** Renders event cards from the supplied events in the weekly time grid. */
  @Test
  fun timeGridRendersEventCardForAnInWeekEvent() {
    val weekDates = CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 14))
    val event = eventOn(weekDates[1], LocalTime.of(9, 0), LocalTime.of(10, 0))

    composeTestRule.setContent {
      Box(Modifier.height(400.dp)) {
        CalendarTimeGrid(
            modifier = Modifier.fillMaxSize(),
            events = listOf(event),
            weekDates = weekDates,
        )
      }
    }

    composeTestRule.onNodeWithText("Course").assertExists()
    composeTestRule.onNodeWithTag(CalendarTestTags.eventCard(event.id, 1)).assertIsDisplayed()
  }

  /** Applies the calculated time and day placement to the rendered event card bounds. */
  @Test
  fun timeGridPlacesCardsInExpectedDayColumnsAndTimeOrder() {
    val weekDates = CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 14))
    val morningEvent =
        eventOn(weekDates[1], LocalTime.of(9, 0), LocalTime.of(10, 0), id = "morning")
    val laterEvent = eventOn(weekDates[1], LocalTime.of(11, 0), LocalTime.of(12, 0), id = "later")
    val otherDayEvent =
        eventOn(weekDates[3], LocalTime.of(9, 0), LocalTime.of(10, 0), id = "other-day")

    composeTestRule.setContent {
      Box(Modifier.height(400.dp)) {
        CalendarTimeGrid(
            modifier = Modifier.fillMaxSize(),
            events = listOf(morningEvent, laterEvent, otherDayEvent),
            weekDates = weekDates,
        )
      }
    }

    val morningBounds =
        composeTestRule
            .onNodeWithTag(CalendarTestTags.eventCard("morning", 1))
            .getUnclippedBoundsInRoot()
    val laterBounds =
        composeTestRule
            .onNodeWithTag(CalendarTestTags.eventCard("later", 1))
            .getUnclippedBoundsInRoot()
    val otherDayBounds =
        composeTestRule
            .onNodeWithTag(CalendarTestTags.eventCard("other-day", 3))
            .getUnclippedBoundsInRoot()

    assertTrue(
        "Later event should be lower in the same day column",
        laterBounds.top > morningBounds.top,
    )
    assertTrue(
        "Equal-duration events should have equal heights",
        abs(
            ((morningBounds.bottom - morningBounds.top) - (laterBounds.bottom - laterBounds.top))
                .value
        ) < 0.5f,
    )
    assertTrue("A later day should be to the right", otherDayBounds.left > morningBounds.left)
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

  /** Creates display state for a supplied date and its containing week. */
  private fun calendarState(today: LocalDate): CalendarUiState {
    val dateHelper = CalendarDateHelper()
    val dates = dateHelper.weekDates(today)
    return CalendarUiState(
        today = today,
        weekStart = dates.first(),
        weekDates = dates,
        isLoading = false,
    )
  }

  private fun eventOn(
      date: LocalDate,
      start: LocalTime,
      end: LocalTime,
      id: String = "weekly-test-event",
  ): Event = eventOn(date, start, date, end, id)

  private fun eventOn(
      startDate: LocalDate,
      start: LocalTime,
      endDate: LocalDate,
      end: LocalTime,
      id: String = "weekly-test-event",
  ): Event {
    val zone = ZoneId.systemDefault()
    return Event(
        id = id,
        ownerId = "test-owner",
        type = EventType.COURSE,
        subject = "Weekly course",
        startsAt = startDate.atTime(start).atZone(zone).toInstant(),
        endsAt = endDate.atTime(end).atZone(zone).toInstant(),
        createdAt = Instant.parse("2026-10-01T12:00:00Z"),
    )
  }
}
