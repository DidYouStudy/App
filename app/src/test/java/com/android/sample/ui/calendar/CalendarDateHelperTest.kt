// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests calendar week calculations and navigation. */
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

  /** Finds the containing Monday-to-Sunday week for a date in the middle of the week. */
  @Test
  fun weekDatesFromMidweekReturnsContainingWeek() {
    val wednesday = LocalDate.of(2026, 10, 14)
    val dates = CalendarDateHelper().weekDates(wednesday)

    assertEquals(LocalDate.of(2026, 10, 12), dates.first())
    assertEquals(wednesday, dates[2])
    assertEquals(LocalDate.of(2026, 10, 18), dates.last())
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

  /** Navigates from the first week of a year to the final week of the previous year. */
  @Test
  fun previousWeekCrossesYearBoundary() {
    val helper = CalendarDateHelper()

    assertEquals(
        LocalDate.of(2026, 12, 28),
        helper.previousWeek(LocalDate.of(2027, 1, 6)).first(),
    )
    assertEquals(
        LocalDate.of(2027, 1, 3),
        helper.previousWeek(LocalDate.of(2027, 1, 6)).last(),
    )
  }
}
