// Co-authored-by: GitHub Copilot
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests group-session fields and validation without Android or Firebase. */
class GroupSessionTest {
  private val start = Instant.parse("2026-10-19T09:00:00Z")
  private val end = Instant.parse("2026-10-19T10:00:00Z")
  private val created = Instant.parse("2026-10-18T12:00:00Z")

  /** Builds a valid session with deterministic times and membership. */
  private fun session() =
      GroupSession(
          id = "session-1",
          createdBy = "creator",
          memberIds = listOf("creator", "other"),
          type = EventType.STUDY_SESSION,
          subject = "Analysis",
          startsAt = start,
          endsAt = end,
          createdAt = created,
      )

  /** A valid session retains every supplied field and defaults its edit time to creation. */
  @Test
  fun validSession_retainsAllFields() {
    val editedAt = created.plusSeconds(60)
    val value =
        session()
            .copy(
                subject = "Analysis II",
                startsAt = start,
                endsAt = end,
                updatedAt = editedAt,
                location = "Library",
                description = "Review integration exercises",
            )

    assertEquals("session-1", value.id)
    assertEquals("creator", value.createdBy)
    assertEquals(listOf("creator", "other"), value.memberIds)
    assertEquals(EventType.STUDY_SESSION, value.type)
    assertEquals("Analysis II", value.subject)
    assertEquals(start, value.startsAt)
    assertEquals(end, value.endsAt)
    assertEquals(created, value.createdAt)
    assertEquals(editedAt, value.updatedAt)
    assertEquals("Library", value.location)
    assertEquals("Review integration exercises", value.description)
    assertEquals(created, session().updatedAt)
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

  /** Subjects are nonblank for all types, and null is accepted only for OTHER. */
  @Test
  fun subject_isOptionalOnlyForOther() {
    assertNull(session().copy(type = EventType.OTHER, subject = null).subject)
    for (type in EventType.entries) {
      for (blank in listOf("", " \t\n")) {
        assertThrows(IllegalArgumentException::class.java) {
          session().copy(type = type, subject = blank)
        }
      }
      if (type != EventType.OTHER) {
        assertThrows(IllegalArgumentException::class.java) {
          session().copy(type = type, subject = null)
        }
      }
    }
  }

  /** Session time must be positive and cannot exceed the exact 24-hour limit. */
  @Test
  fun duration_isPositiveAndLimitedToMaxDuration() {
    assertThrows(IllegalArgumentException::class.java) { session().copy(endsAt = start) }
    assertThrows(IllegalArgumentException::class.java) {
      session().copy(endsAt = start.minusSeconds(1))
    }
    val longest = session().copy(endsAt = start.plus(GroupSession.MAX_DURATION))
    assertEquals(start.plus(GroupSession.MAX_DURATION), longest.endsAt)
    assertThrows(IllegalArgumentException::class.java) {
      session().copy(endsAt = start.plus(GroupSession.MAX_DURATION).plusSeconds(1))
    }
  }

  /** A location may be absent but cannot be blank. */
  @Test
  fun location_isOptionalButNotBlank() {
    assertNull(session().copy(location = null).location)
    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { session().copy(location = blank) }
    }
  }
}
