// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests the schedule content shared by every event kind, without Android or Firebase. */
class EventDetailsTest {
  private val start = Instant.parse("2026-10-19T23:30:00Z")
  private val end = Instant.parse("2026-10-20T00:30:00Z")

  /** Builds valid details crossing midnight, independent of machine time and time zone. */
  private fun details() =
      EventDetails(
          type = EventType.STUDY_SESSION,
          subject = "Analysis",
          startsAt = start,
          endsAt = end,
      )

  /** Scheduling across midnight is valid, and absent optional data needs no placeholder strings. */
  @Test
  fun crossMidnightDetails_defaultOptionalFields() {
    val details = details()

    assertEquals(start, details.startsAt)
    assertEquals(end, details.endsAt)
    assertNull(details.location)
    assertNull(details.description)
  }

  /** Selected or custom labels retain their text without a category lookup. */
  @Test
  fun details_retainSubjectLocationAndDescription() {
    val details =
        details()
            .copy(
                subject = "Analysis II",
                location = "Library",
                description = "Review integration exercises",
            )

    assertEquals("Analysis II", details.subject)
    assertEquals("Library", details.location)
    assertEquals("Review integration exercises", details.description)
  }

  /** Equality reflects the planned content only, so a form can detect unsaved changes. */
  @Test
  fun equality_dependsOnlyOnContent() {
    assertEquals(details(), details())
    assertNotEquals(details(), details().copy(subject = "Physics"))
  }

  /** A subject is independent of activity kind: a course and a study block can share one label. */
  @Test
  fun sameSubject_canBeUsedForCourseAndStudySession() {
    val course = details().copy(type = EventType.COURSE)

    assertEquals(EventType.COURSE, course.type)
    assertEquals(details().subject, course.subject)
  }

  /** Subjects are nonblank for all types, and null is accepted only for OTHER. */
  @Test
  fun subject_isOptionalOnlyForOther() {
    assertNull(details().copy(type = EventType.OTHER, subject = null).subject)
    for (type in EventType.entries) {
      for (blank in listOf("", " \t\n")) {
        assertThrows(IllegalArgumentException::class.java) {
          details().copy(type = type, subject = blank)
        }
      }
      if (type != EventType.OTHER) {
        assertThrows(IllegalArgumentException::class.java) {
          details().copy(type = type, subject = null)
        }
      }
    }
  }

  /** A timed entry must occupy a positive duration. */
  @Test
  fun zeroOrNegativeDuration_isRejected() {
    assertThrows(IllegalArgumentException::class.java) { details().copy(endsAt = start) }
    assertThrows(IllegalArgumentException::class.java) {
      details().copy(endsAt = start.minusSeconds(1))
    }
    assertThrows(IllegalArgumentException::class.java) { details().copy(startsAt = end) }
  }

  /** Up to 24 hours is valid; anything longer is treated as an input mistake. */
  @Test
  fun duration_isLimitedToMaxDuration() {
    val longest = details().copy(endsAt = start.plus(EventDetails.MAX_DURATION))

    assertEquals(start.plus(EventDetails.MAX_DURATION), longest.endsAt)
    assertThrows(IllegalArgumentException::class.java) {
      details().copy(endsAt = start.plus(EventDetails.MAX_DURATION).plusSeconds(1))
    }
  }

  /**
   * The limit is exact: a single nanosecond over is rejected, a single nanosecond under is valid.
   */
  @Test
  fun duration_limitIsCheckedBelowOneSecond() {
    val limit = start.plus(EventDetails.MAX_DURATION)

    assertEquals(limit.minusNanos(1), details().copy(endsAt = limit.minusNanos(1)).endsAt)
    for (excess in listOf(1L, 1_000L, 999_999_999L)) {
      assertThrows(IllegalArgumentException::class.java) {
        details().copy(endsAt = limit.plusNanos(excess))
      }
    }
  }

  /** Null means absent; a blank place label is invalid instead of acting as missing data. */
  @Test
  fun blankLocation_isRejected() {
    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { details().copy(location = blank) }
    }
  }
}
