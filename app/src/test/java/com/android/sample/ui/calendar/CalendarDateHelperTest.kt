// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests calendar week calculations and today detection. */
class CalendarDateHelperTest {

  /** Returns Monday through Sunday when the input date is a Monday. */
  @Test
  fun weekDatesFromMondayReturnsMondayThroughSunday() {
    val monday = LocalDate.of(2026, 10, 12)

    assertEquals(
        listOf(
            monday,
            monday.plusDays(1),
            monday.plusDays(2),
            monday.plusDays(3),
            monday.plusDays(4),
            monday.plusDays(5),
            monday.plusDays(6),
        ),
        CalendarDateHelper().weekDates(monday),
    )
  }

  /** Finds the same Monday-to-Sunday week when the input date is a Sunday. */
  @Test
  fun weekDatesFromSundayReturnsContainingWeek() {
    val sunday = LocalDate.of(2026, 10, 18)

    assertEquals(
        LocalDate.of(2026, 10, 12),
        CalendarDateHelper().weekDates(sunday).first(),
    )
    assertEquals(sunday, CalendarDateHelper().weekDates(sunday).last())
  }

  /** Returns the previous Monday-to-Sunday week for a date in the current week. */
  @Test
  fun previousWeekReturnsSevenDatesBeforeContainingWeek() {
    val helper = CalendarDateHelper()

    assertEquals(
        listOf(
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 7),
            LocalDate.of(2026, 10, 8),
            LocalDate.of(2026, 10, 9),
            LocalDate.of(2026, 10, 10),
            LocalDate.of(2026, 10, 11),
        ),
        helper.previousWeek(LocalDate.of(2026, 10, 14)),
    )
  }

  /** Returns the next Monday-to-Sunday week for a date in the current week. */
  @Test
  fun nextWeekReturnsSevenDatesAfterContainingWeek() {
    val helper = CalendarDateHelper()

    assertEquals(
        listOf(
            LocalDate.of(2026, 10, 19),
            LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 10, 21),
            LocalDate.of(2026, 10, 22),
            LocalDate.of(2026, 10, 23),
            LocalDate.of(2026, 10, 24),
            LocalDate.of(2026, 10, 25),
        ),
        helper.nextWeek(LocalDate.of(2026, 10, 14)),
    )
  }

  /** Handles navigation from the final week of one year into the next year. */
  @Test
  fun nextWeekCrossesYearBoundary() {
    val helper = CalendarDateHelper()

    assertEquals(
        LocalDate.of(2027, 1, 4),
        helper.nextWeek(LocalDate.of(2026, 12, 31)).first(),
    )
  }

  /** Detects today using the injected clock and its time zone. */
  @Test
  fun isTodayUsesInjectedClock() {
    val clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)
    val helper = CalendarDateHelper(clock)

    assertTrue(helper.isToday(LocalDate.of(2026, 10, 21)))
    assertFalse(helper.isToday(LocalDate.of(2026, 10, 22)))
  }
}
