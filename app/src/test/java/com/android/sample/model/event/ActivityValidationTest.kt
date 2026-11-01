// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import com.android.sample.model.groupsession.GroupSession
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests the checks shared by [Event] and [GroupSession] without Android or Firebase. */
class ActivityValidationTest {
  private val start = Instant.parse("2026-10-19T09:00:00Z")
  private val end = Instant.parse("2026-10-19T10:00:00Z")

  /** Calls the shared check with valid defaults, overriding only what a test varies. */
  private fun validate(
      activityName: String = "Event",
      type: EventType = EventType.STUDY_SESSION,
      subject: String? = "Analysis",
      startsAt: Instant = start,
      endsAt: Instant = end,
      location: String? = null,
  ) = requireValidActivity(activityName, type, subject, startsAt, endsAt, location)

  /** Returns the message of the [IllegalArgumentException] thrown by [block]. */
  private fun failureOf(block: () -> Unit): String? =
      assertThrows(IllegalArgumentException::class.java) { block() }.message

  /** A fully valid activity passes, with or without a location. */
  @Test
  fun validActivity_passes() {
    validate()
    validate(location = "Library")
  }

  /** A null subject is accepted only for OTHER. */
  @Test
  fun subject_isOptionalOnlyForOther() {
    validate(type = EventType.OTHER, subject = null)
    assertEquals(
        "Subject must not be blank, and may only be absent for OTHER",
        failureOf { validate(subject = null) },
    )
  }

  /** A blank subject is rejected, even for OTHER. */
  @Test
  fun blankSubject_isRejected() {
    assertThrows(IllegalArgumentException::class.java) { validate(subject = " ") }
    assertThrows(IllegalArgumentException::class.java) {
      validate(type = EventType.OTHER, subject = "")
    }
  }

  /** The end must be strictly after the start. */
  @Test
  fun end_mustBeAfterStart() {
    assertEquals("End must be after start", failureOf { validate(endsAt = start) })
    assertEquals("End must be after start", failureOf { validate(endsAt = start.minusSeconds(1)) })
  }

  /** Exactly the maximum duration passes; one second more fails and names the model. */
  @Test
  fun duration_isCappedAtMaxDuration() {
    validate(endsAt = start.plus(ACTIVITY_MAX_DURATION))
    val tooLong = start.plus(ACTIVITY_MAX_DURATION).plusSeconds(1)
    assertEquals(
        "Group session must not last longer than ${Duration.ofHours(24)}",
        failureOf { validate(activityName = "Group session", endsAt = tooLong) },
    )
  }

  /** A blank location is rejected; an absent one is fine. */
  @Test
  fun location_mustBeNonBlankWhenPresent() {
    validate(location = null)
    assertEquals("Location must not be blank", failureOf { validate(location = "  ") })
  }

  /** Each model passes its own name, so the duration error says which model failed. */
  @Test
  fun models_nameThemselvesInDurationError() {
    val tooLong = start.plus(ACTIVITY_MAX_DURATION).plusSeconds(1)
    assertEquals(
        "Event must not last longer than $ACTIVITY_MAX_DURATION",
        failureOf {
          Event(
              id = "event",
              ownerId = "user",
              type = EventType.STUDY_SESSION,
              subject = "Analysis",
              startsAt = start,
              endsAt = tooLong,
              createdAt = start,
          )
        },
    )
    assertEquals(
        "Group session must not last longer than $ACTIVITY_MAX_DURATION",
        failureOf {
          GroupSession(
              id = "session",
              createdBy = "user",
              memberIds = listOf("user"),
              type = EventType.STUDY_SESSION,
              subject = "Analysis",
              startsAt = start,
              endsAt = tooLong,
              createdAt = start,
          )
        },
    )
  }

  /** Event and GroupSession expose the one shared maximum duration. */
  @Test
  fun models_shareTheSingleMaxDuration() {
    assertEquals(ACTIVITY_MAX_DURATION, Event.MAX_DURATION)
    assertEquals(ACTIVITY_MAX_DURATION, GroupSession.MAX_DURATION)
    assertEquals(Duration.ofHours(24), ACTIVITY_MAX_DURATION)
  }
}
