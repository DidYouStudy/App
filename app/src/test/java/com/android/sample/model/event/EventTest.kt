// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the event model's validation and optional scheduling data without Android or Firebase. */
class EventTest {
  private val start = Instant.parse("2026-10-19T23:30:00Z")
  private val end = Instant.parse("2026-10-20T00:30:00Z")
  private val created = Instant.parse("2026-10-18T12:00:00Z")

  /** Builds a valid entry crossing midnight, independent of machine time and time zone. */
  private fun event() =
      Event(
          id = "event-1",
          ownerId = "user-1",
          type = EventType.STUDY_SESSION,
          subject = "Analysis",
          startsAt = start,
          endsAt = end,
          createdAt = created,
      )

  /** Scheduling across midnight is valid, and absent optional data needs no placeholder strings. */
  @Test
  fun crossMidnightEvent_defaultsOptionalDetailsAndEditTime() {
    val event = event()

    assertEquals(start, event.startsAt)
    assertEquals(end, event.endsAt)
    assertEquals(created, event.updatedAt)
    assertNull(event.location)
    assertNull(event.description)
    assertTrue(event.tasks.isEmpty())
  }

  /** Selected or custom subject/place labels retain their text without a category lookup. */
  @Test
  fun event_retainsSubjectLocationDescriptionAndExplicitEditTime() {
    val edited = created.plusSeconds(60)
    val event =
        event()
            .copy(
                subject = "Analysis II",
                location = "Library",
                description = "Review integration exercises",
                updatedAt = edited,
            )

    assertEquals("Analysis II", event.subject)
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
      assertThrows(IllegalArgumentException::class.java) { event().copy(subject = blank) }
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

  /** Null means absent; a blank place label is invalid instead of acting as missing data. */
  @Test
  fun blankLocation_isRejected() {
    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { event().copy(location = blank) }
    }
  }

  /** Editing remains explicit: the model never generates IDs or reads a wall clock. */
  @Test
  fun editingSubject_keepsIdentityAndTimestampsUntilCallerUpdatesThem() {
    val initial = event()
    val edited = initial.copy(subject = "Physics")

    assertEquals(initial.id, edited.id)
    assertEquals(initial.ownerId, edited.ownerId)
    assertEquals(initial.createdAt, edited.createdAt)
    assertEquals(initial.updatedAt, edited.updatedAt)
  }

  /** A subject is independent of activity kind: its course and study block can share one label. */
  @Test
  fun sameSubject_canBeUsedForCourseAndStudySession() {
    val studySession = event()
    val course = studySession.copy(id = "course-1", type = EventType.COURSE)

    assertEquals(EventType.STUDY_SESSION, studySession.type)
    assertEquals(EventType.COURSE, course.type)
    assertEquals(studySession.subject, course.subject)
  }

  /** Only OTHER may omit its subject; an absent subject is null, never blank. */
  @Test
  fun subject_isOptionalOnlyForOther() {
    val other = event().copy(type = EventType.OTHER, subject = null)

    assertNull(other.subject)
    assertThrows(IllegalArgumentException::class.java) { event().copy(subject = null) }
    assertThrows(IllegalArgumentException::class.java) {
      event().copy(type = EventType.OTHER, subject = " ")
    }
  }

  /** Up to 24 hours is valid; anything longer is treated as an input mistake. */
  @Test
  fun duration_isLimitedToMaxDuration() {
    val longest = event().copy(endsAt = start.plus(Event.MAX_DURATION))

    assertEquals(start.plus(Event.MAX_DURATION), longest.endsAt)
    assertThrows(IllegalArgumentException::class.java) {
      event().copy(endsAt = start.plus(Event.MAX_DURATION).plusSeconds(1))
    }
  }

  /** Tasks are not limited to study sessions and keep their order. */
  @Test
  fun tasks_areAllowedForEveryTypeInOrder() {
    val tasks = listOf(EventTask("Exercise 1"), EventTask("Exercise 2", completedAt = end))

    for (type in EventType.entries) {
      val subject = if (type == EventType.OTHER) null else "Analysis"
      val event = event().copy(type = type, subject = subject, tasks = tasks)

      assertEquals(tasks, event.tasks)
    }
  }
}
