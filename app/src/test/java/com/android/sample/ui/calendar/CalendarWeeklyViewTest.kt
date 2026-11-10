// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

// import androidx.compose.ui.test.assertDoesNotExist
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.PersonalEvent
import com.android.sample.ui.theme.Dimens
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

  /** Uses compact week and day headers when the weekly calendar is in landscape. */
  @Test
  fun landscapeCalendarUsesCompactHeaders() {
    composeTestRule.setContent {
      Box(Modifier.width(800.dp).height(300.dp)) {
        CalendarWeeklyView(
            uiState = calendarState(LocalDate.of(2026, 10, 14)),
            onPreviousWeek = {},
            onNextWeek = {},
        )
      }
    }

    val weekHeader =
        composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_HEADER).getUnclippedBoundsInRoot()
    val dayHeader =
        composeTestRule.onNodeWithTag(CalendarTestTags.DAY_HEADER).getUnclippedBoundsInRoot()

    assertEquals(Dimens.calendarWeekSwitchHeaderHeightLandscape, weekHeader.bottom - weekHeader.top)
    assertEquals(Dimens.calendarDayHeaderHeightLandscape, dayHeader.bottom - dayHeader.top)
    composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_RANGE).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(CalendarTestTags.dayNumber(LocalDate.of(2026, 10, 14)))
        .assertIsDisplayed()
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

  /** Keeps the regular header heights when the weekly calendar is in portrait. */
  @Test
  fun portraitCalendarUsesRegularHeaders() {
    composeTestRule.setContent {
      Box(Modifier.width(360.dp).height(800.dp)) {
        CalendarWeeklyView(
            uiState = calendarState(LocalDate.of(2026, 10, 14)),
            onPreviousWeek = {},
            onNextWeek = {},
        )
      }
    }

    val weekHeader =
        composeTestRule.onNodeWithTag(CalendarTestTags.WEEK_HEADER).getUnclippedBoundsInRoot()
    val dayHeader =
        composeTestRule.onNodeWithTag(CalendarTestTags.DAY_HEADER).getUnclippedBoundsInRoot()

    assertEquals(Dimens.calendarWeekSwitchHeaderHeight, weekHeader.bottom - weekHeader.top)
    assertEquals(Dimens.calendarDayHeaderHeight, dayHeader.bottom - dayHeader.top)
  }

  /** Keeps the hour axis visible and rows readable in the full rotated calendar screen. */
  @Test
  fun landscapeCalendarScreenShowsReadableHourAxis() {
    composeTestRule.setContent {
      Box(Modifier.width(800.dp).height(360.dp)) {
        CalendarWeeklyView(
            uiState = calendarState(LocalDate.of(2026, 10, 14)),
            onPreviousWeek = {},
            onNextWeek = {},
        )
      }
    }

    val grid = composeTestRule.onNodeWithTag(CalendarTestTags.TIME_GRID).getUnclippedBoundsInRoot()
    val eightAm =
        composeTestRule
            .onNodeWithTag(CalendarTestTags.hourLabel(8))
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
    val nineAm =
        composeTestRule
            .onNodeWithTag(CalendarTestTags.hourLabel(9))
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

    assertTrue("The 8 AM label should not be clipped by the headers", eightAm.top >= grid.top)
    assertTrue(
        "Hour rows should remain readable",
        nineAm.top - eightAm.top >= Dimens.calendarMinHourRowHeight,
    )
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

  /** Positions a Zurich event by wall-clock time on the 2026 autumn clock-change day. */
  @Test
  fun eventPlacementUsesLocalClockOnZurichAutumnClockChange() {
    val zone = ZoneId.of("Europe/Zurich")
    val date = LocalDate.of(2026, 10, 25)
    val weekDates = CalendarDateHelper().weekDates(date)
    val event =
        PersonalEvent(
            id = "zurich-clock-change",
            ownerId = "test-owner",
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Zurich event",
                    startsAt = date.atTime(9, 0).atZone(zone).toInstant(),
                    endsAt = date.atTime(10, 0).atZone(zone).toInstant(),
                ),
            createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        )

    val placement = calculateEventPlacements(event, weekDates, 10.dp, zone).single()

    assertEquals(6, placement.dayIndex)
    assertEquals(90.dp, placement.top)
    assertEquals(10.dp, placement.height)
  }

  /** A half-day event is 13 real hours but must fill exactly the 12 rows of its column. */
  @Test
  fun fullDayEventOnAutumnClockChangeFillsColumn() {
    val zone = ZoneId.of("Europe/Zurich")
    val autumnDay = LocalDate.of(2026, 10, 25)
    val weekDates = CalendarDateHelper().weekDates(autumnDay)
    val event =
        PersonalEvent(
            id = "half-day",
            ownerId = "test-owner",
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Zurich event",
                    startsAt = autumnDay.atStartOfDay(zone).toInstant(),
                    endsAt = autumnDay.atTime(12, 0).atZone(zone).toInstant(),
                ),
            createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        )

    val placement = calculateEventPlacements(event, weekDates, 10.dp, zone).single()

    assertEquals(6, placement.dayIndex)
    assertEquals(0.dp, placement.top)
    assertEquals(120.dp, placement.height)
  }

  /** Documents current behavior: 01:30 CEST -> 03:30 CET is 3 real hours but is drawn as 2. */
  @Test
  fun eventSpanningAutumnClockChangeIsDrawnByWallClockDuration() {
    val zone = ZoneId.of("Europe/Zurich")
    val autumnDay = LocalDate.of(2026, 10, 25)
    val weekDates = CalendarDateHelper().weekDates(autumnDay)
    val event =
        PersonalEvent(
            id = "two-hours",
            ownerId = "test-owner",
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Zurich event",
                    startsAt = autumnDay.atTime(1, 30).atZone(zone).toInstant(),
                    endsAt = autumnDay.atTime(3, 30).atZone(zone).toInstant(),
                ),
            createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        )

    val placement = calculateEventPlacements(event, weekDates, 10.dp, zone).single()

    assertEquals(15.dp, placement.top)
    assertEquals(20.dp, placement.height)
  }

  /** 02:30 CEST (first pass) -> 02:15 CET (second pass) is 45 real minutes. */
  @Test
  fun eventInsideRepeatedHourNeverHasNegativeHeight() {
    val zone = ZoneId.of("Europe/Zurich")
    val autumnDay = LocalDate.of(2026, 10, 25)
    val weekDates = CalendarDateHelper().weekDates(autumnDay)
    val event =
        PersonalEvent(
            id = "45_mins",
            ownerId = "test-owner",
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Zurich event",
                    startsAt =
                        autumnDay
                            .atTime(2, 30)
                            .atZone(zone)
                            .withEarlierOffsetAtOverlap()
                            .toInstant(),
                    endsAt =
                        autumnDay.atTime(2, 15).atZone(zone).withLaterOffsetAtOverlap().toInstant(),
                ),
            createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        )

    val placement = calculateEventPlacements(event, weekDates, 10.dp, zone).single()

    assertEquals(25.dp, placement.top)
    assert(placement.height == 0.dp)
  }

  /**
   * 02:30 doesn't exist on 29 Mar; java.time moves it to 03:30 CEST, so it lands on the 3 AM row.
   */
  @Test
  fun eventInSkippedSpringHourIsShiftedForward() {
    val zone = ZoneId.of("Europe/Zurich")
    val springDay = LocalDate.of(2026, 3, 29)
    val weekDates = CalendarDateHelper().weekDates(springDay)
    val start = springDay.atTime(2, 30).atZone(zone) // resolves to 03:30 CEST
    val event =
        PersonalEvent(
            id = "non-existent",
            ownerId = "test-owner",
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Zurich event",
                    startsAt = start.toInstant(),
                    endsAt = start.plusHours(1).toInstant(),
                ),
            createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        )

    val placement = calculateEventPlacements(event, weekDates, 10.dp, zone).single()

    assertEquals(35.dp, placement.top)
    assertEquals(10.dp, placement.height)
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
  ): PersonalEvent = eventOn(date, start, date, end, id)

  private fun eventOn(
      startDate: LocalDate,
      start: LocalTime,
      endDate: LocalDate,
      end: LocalTime,
      id: String = "weekly-test-event",
  ): PersonalEvent {
    val zone = ZoneId.systemDefault()
    return PersonalEvent(
        id = id,
        ownerId = "test-owner",
        details =
            EventDetails(
                type = EventType.COURSE,
                subject = "Weekly course",
                startsAt = startDate.atTime(start).atZone(zone).toInstant(),
                endsAt = endDate.atTime(end).atZone(zone).toInstant(),
            ),
        createdAt = Instant.parse("2026-10-01T12:00:00Z"),
    )
  }
}
