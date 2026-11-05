// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the personal event's own fields and validation; shared content is covered by
 * [EventDetailsTest].
 */
class PersonalEventTest {
  private val start = Instant.parse("2026-10-19T23:30:00Z")
  private val end = Instant.parse("2026-10-20T00:30:00Z")
  private val created = Instant.parse("2026-10-18T12:00:00Z")
  private val details = EventDetails(EventType.STUDY_SESSION, "Analysis", start, end)

  /** Builds a valid entry, independent of machine time and time zone. */
  private fun event() =
      PersonalEvent(id = "event-1", ownerId = "user-1", details = details, createdAt = created)

  /** Absent optional data needs no placeholders, and the edit time defaults to creation. */
  @Test
  fun newEvent_defaultsTasksImportKeyAndEditTime() {
    val event = event()

    assertSame(details, event.details)
    assertEquals(created, event.updatedAt)
    assertTrue(event.tasks.isEmpty())
    assertNull(event.importKey)
  }

  /** A personal event is an [Event], so shared code can read its content without a cast. */
  @Test
  fun personalEvent_isAnEvent() {
    val event: Event = event()

    assertEquals("event-1", event.id)
    assertEquals("Analysis", event.details.subject)
    assertEquals(created, event.createdAt)
    assertEquals(created, event.updatedAt)
  }

  /** Hand-made events carry no import key, and a key, when present, must have text. */
  @Test
  fun importKey_isNullByDefaultAndMustNotBeBlank() {
    assertEquals("uid-1", event().copy(importKey = "uid-1").importKey)
    assertThrows(IllegalArgumentException::class.java) { event().copy(importKey = " ") }
  }

  /** Both construction and data-class copies reject missing identity. */
  @Test
  fun blankIdentity_isRejected() {
    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { event().copy(id = blank) }
      assertThrows(IllegalArgumentException::class.java) { event().copy(ownerId = blank) }
    }
  }

  /** Editing remains explicit: replacing the details never changes identity or timestamps. */
  @Test
  fun editingDetails_keepsIdentityAndTimestampsUntilCallerUpdatesThem() {
    val initial = event()
    val edited = initial.copy(details = details.copy(subject = "Physics"))

    assertEquals("Physics", edited.details.subject)
    assertEquals(initial.id, edited.id)
    assertEquals(initial.ownerId, edited.ownerId)
    assertEquals(initial.createdAt, edited.createdAt)
    assertEquals(initial.updatedAt, edited.updatedAt)
  }

  /** Tasks are not limited to study sessions and keep their order. */
  @Test
  fun tasks_areAllowedForEveryTypeInOrder() {
    val tasks = listOf(EventTask("Exercise 1"), EventTask("Exercise 2", completedAt = end))

    for (type in EventType.entries) {
      val subject = if (type == EventType.OTHER) null else "Analysis"
      val event =
          event().copy(details = details.copy(type = type, subject = subject), tasks = tasks)

      assertEquals(tasks, event.tasks)
    }
  }
}
