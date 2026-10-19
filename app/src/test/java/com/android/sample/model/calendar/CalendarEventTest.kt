// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.calendar

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests the calendar model's validation and optional scheduling data without Android or Firebase.
 */
class CalendarEventTest {
  private val start = Instant.parse("2026-10-19T23:30:00Z")
  private val end = Instant.parse("2026-10-20T00:30:00Z")
  private val created = Instant.parse("2026-10-18T12:00:00Z")

  /** Builds a valid entry crossing midnight, independent of machine time and time zone. */
  private fun event() =
      CalendarEvent(
          id = "event-1",
          ownerId = "user-1",
          title = "Revision",
          startsAt = start,
          endsAt = end,
          createdAt = created,
      )

  /** Scheduling across midnight is valid, and absent optional data needs no placeholder strings. */
  @Test
  fun crossMidnightEvent_allowsUncategorizedEntryAndDefaultsEditTime() {
    val event = event()

    assertEquals(start, event.startsAt)
    assertEquals(end, event.endsAt)
    assertEquals(created, event.updatedAt)
    assertNull(event.categoryId)
    assertNull(event.location)
    assertNull(event.description)
  }

  /**
   * Multiple tags are unnecessary: an event can reference one category alongside manual details.
   */
  @Test
  fun categorizedEvent_retainsLocationDescriptionAndExplicitEditTime() {
    val edited = created.plusSeconds(60)
    val event =
        event()
            .copy(
                categoryId = "analysis",
                location = "Library",
                description = "Review integration exercises",
                updatedAt = edited,
            )

    assertEquals("analysis", event.categoryId)
    assertEquals("Library", event.location)
    assertEquals("Review integration exercises", event.description)
    assertEquals(created, event.createdAt)
    assertEquals(edited, event.updatedAt)
  }

  /** Both construction and data-class copies reject missing identity or content. */
  @Test
  fun blankRequiredFields_areRejected() {
    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { event().copy(id = blank) }
      assertThrows(IllegalArgumentException::class.java) { event().copy(ownerId = blank) }
      assertThrows(IllegalArgumentException::class.java) { event().copy(title = blank) }
    }
  }

  /** A timed entry must occupy a positive duration, including when edited. */
  @Test
  fun zeroOrNegativeDuration_isRejected() {
    assertThrows(IllegalArgumentException::class.java) { event().copy(endsAt = start) }
    assertThrows(IllegalArgumentException::class.java) {
      event().copy(endsAt = start.minusSeconds(1))
    }
    assertThrows(IllegalArgumentException::class.java) { event().copy(startsAt = end) }
  }

  /**
   * Null means absent; blank labels and references are invalid instead of acting as missing data.
   */
  @Test
  fun blankCategoryOrLocation_isRejected() {
    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { event().copy(categoryId = blank) }
      assertThrows(IllegalArgumentException::class.java) { event().copy(location = blank) }
    }
  }

  /** Editing remains explicit: the model never generates IDs or reads a wall clock. */
  @Test
  fun editingTitle_keepsIdentityAndTimestampsUntilCallerUpdatesThem() {
    val initial = event()
    val edited = initial.copy(title = "Exam revision")

    assertEquals(initial.id, edited.id)
    assertEquals(initial.ownerId, edited.ownerId)
    assertEquals(initial.createdAt, edited.createdAt)
    assertEquals(initial.updatedAt, edited.updatedAt)
  }
}
