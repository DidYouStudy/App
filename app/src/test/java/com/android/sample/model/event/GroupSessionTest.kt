// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests group-session fields and membership validation without Android or Firebase; shared content
 * is covered by [EventDetailsTest].
 */
class GroupSessionTest {
  private val start = Instant.parse("2026-10-19T09:00:00Z")
  private val end = Instant.parse("2026-10-19T10:00:00Z")
  private val created = Instant.parse("2026-10-18T12:00:00Z")
  private val details = EventDetails(EventType.STUDY_SESSION, "Analysis", start, end)

  /** Builds a valid session with deterministic times and membership. */
  private fun session() =
      GroupSession(
          id = "session-1",
          createdBy = "creator",
          memberIds = listOf("creator", "other"),
          details = details,
          createdAt = created,
      )

  /** A valid session retains every supplied field and defaults its edit time to creation. */
  @Test
  fun validSession_retainsAllFields() {
    val editedAt = created.plusSeconds(60)
    val value = session().copy(updatedAt = editedAt)

    assertEquals("session-1", value.id)
    assertEquals("creator", value.createdBy)
    assertEquals(listOf("creator", "other"), value.memberIds)
    assertSame(details, value.details)
    assertEquals(created, value.createdAt)
    assertEquals(editedAt, value.updatedAt)
    assertEquals(created, session().updatedAt)
  }

  /** A group session is an [Event], so shared code can read its content without a cast. */
  @Test
  fun groupSession_isAnEvent() {
    val event: Event = session()

    assertEquals("session-1", event.id)
    assertEquals("Analysis", event.details.subject)
    assertEquals(created, event.createdAt)
    assertEquals(created, event.updatedAt)
  }

  /** Identity and required membership labels reject blank or incomplete values. */
  @Test
  fun blankIdentityAndInvalidMembership_areRejected() {
    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { session().copy(id = blank) }
      assertThrows(IllegalArgumentException::class.java) { session().copy(createdBy = blank) }
      assertThrows(IllegalArgumentException::class.java) {
        session().copy(memberIds = listOf("other"))
      }
      assertThrows(IllegalArgumentException::class.java) {
        session().copy(memberIds = listOf("creator", blank))
      }
    }
    assertThrows(IllegalArgumentException::class.java) {
      session().copy(memberIds = listOf("creator", "creator"))
    }
  }
}
