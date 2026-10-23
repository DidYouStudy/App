// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Calculates the dates and today status used by the calendar's weekly UI. */
class CalendarDateHelper(
    private val clock: Clock = Clock.systemDefaultZone(),
) {
  /**
   * Checks whether [date] is today according to this helper's clock. Avoids calling now()
   * repeatedly. -check whether this is actually needed
   */
  fun isToday(date: LocalDate): Boolean = date == LocalDate.now(clock)

  /** Returns the Monday-through-Sunday dates for the week containing [date]. */
  fun weekDates(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).map(monday::plusDays)
  }

  /** Returns the Monday-through-Sunday dates of the week before the one containing [date]. */
  fun previousWeek(date: LocalDate): List<LocalDate> = weekDates(date.minusWeeks(1))

  /** Returns the Monday-through-Sunday dates of the week after the one containing [date]. */
  fun nextWeek(date: LocalDate): List<LocalDate> = weekDates(date.plusWeeks(1))
}
