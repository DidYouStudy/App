// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Verifies the group-session Firestore wire format without an emulator. */
class GroupSessionFirestoreMapperTest {
  private val time = Instant.parse("2026-10-20T12:00:00.123456Z")

  /** Encodes a complete session and a minimal OTHER session without losing model values. */
  @Test
  fun roundTripsCompleteAndMinimalSessions() {
    val complete =
        GroupSession(
            id = "complete",
            createdBy = "creator",
            memberIds = listOf("creator", "member"),
            type = EventType.COURSE,
            subject = "Analysis",
            startsAt = time,
            endsAt = time.plusSeconds(3600),
            createdAt = time.minusSeconds(60),
            updatedAt = time,
            location = "BC 410",
            description = "Review exercises",
        )
    val minimal =
        GroupSession(
            id = "minimal",
            createdBy = "creator",
            memberIds = listOf("creator"),
            type = EventType.OTHER,
            subject = null,
            startsAt = time,
            endsAt = time.plusSeconds(3600),
            createdAt = time,
        )

    listOf(complete, minimal).forEach { expected ->
      assertEquals(
          expected,
          GroupSessionFirestoreMapper.decode(
              expected.id,
              GroupSessionFirestoreMapper.encode(expected),
          ),
      )
    }
  }

  /** Encoded fields match the schema exactly, and the ID remains the document ID. */
  @Test
  fun encodesExactlyTheDocumentFields() {
    val data = GroupSessionFirestoreMapper.encode(minimalSession())

    assertEquals(
        setOf(
            "createdBy",
            "memberIds",
            "type",
            "subject",
            "startsAt",
            "endsAt",
            "createdAt",
            "updatedAt",
            "location",
            "description",
        ),
        data.keys,
    )
    assertFalse(data.containsKey("id"))
  }

  /** Encoded timestamps match Firestore's microsecond precision. */
  @Test
  fun normalizesSubMicrosecondPrecision() {
    val precise = time.plusNanos(789)
    val input =
        minimalSession()
            .copy(
                startsAt = precise,
                endsAt = precise.plusSeconds(3600),
                createdAt = precise,
                updatedAt = precise,
            )
    val expected =
        input.copy(
            startsAt = time,
            endsAt = time.plusSeconds(3600),
            createdAt = time,
            updatedAt = time,
        )

    assertEquals(
        expected,
        GroupSessionFirestoreMapper.decode(
            input.id,
            GroupSessionFirestoreMapper.encode(input),
        ),
    )
  }

  /** Both absent and explicit-null optional fields decode as null. */
  @Test
  fun missingAndNullOptionalsDecodeAsNull() {
    val base = (validData() + ("type" to EventType.OTHER.name)).toMutableMap()
    listOf("subject", "location", "description").forEach(base::remove)
    assertNull(
        GroupSessionFirestoreMapper.decode("session", base).location,
    )
    assertNull(GroupSessionFirestoreMapper.decode("session", base).description)
    assertNull(GroupSessionFirestoreMapper.decode("session", base).subject)

    val nulls =
        validData().toMutableMap().apply {
          put("type", EventType.OTHER.name)
          put("subject", null)
          put("location", null)
          put("description", null)
        }
    val decoded = GroupSessionFirestoreMapper.decode("session", nulls)
    assertNull(decoded.subject)
    assertNull(decoded.location)
    assertNull(decoded.description)
  }

  /** Every malformed document fails with the stable invalid-data category. */
  @Test
  fun rejectsMalformedDocumentsAsInvalidEventData() {
    val valid = validData()
    val requiredFields =
        listOf("createdBy", "memberIds", "type", "startsAt", "endsAt", "createdAt", "updatedAt")
    val malformed =
        requiredFields.map { valid - it } +
            listOf(
                valid + ("startsAt" to "yesterday"),
                valid + ("type" to "UNKNOWN"),
                valid + ("type" to 42),
                valid + ("subject" to 42),
                valid + ("subject" to " "),
                valid + ("subject" to null),
                valid + ("createdBy" to 42),
                valid + ("memberIds" to "creator"),
                valid + ("memberIds" to listOf("creator", 42)),
                valid + ("memberIds" to listOf("member")),
                valid + ("memberIds" to listOf("creator", "creator")),
                valid + ("endsAt" to valid["startsAt"]),
                valid + ("endsAt" to timestamp(time.plusSeconds(24 * 3600 + 1))),
                valid + ("location" to 42),
                valid + ("location" to " "),
                valid + ("description" to 42),
            )

    malformed.forEach { data ->
      val error =
          assertThrows(EventsException::class.java) {
            GroupSessionFirestoreMapper.decode("session", data)
          }
      assertEquals(EventsError.INVALID_EVENT_DATA, error.error)
    }
  }

  /** Supplies a minimal valid session for encoding assertions. */
  private fun minimalSession() =
      GroupSession(
          id = "session",
          createdBy = "creator",
          memberIds = listOf("creator"),
          type = EventType.OTHER,
          subject = null,
          startsAt = time,
          endsAt = time.plusSeconds(3600),
          createdAt = time,
      )

  /** Supplies valid raw Firestore fields independently of the mapper under test. */
  private fun validData(): Map<String, Any?> =
      mapOf(
          "createdBy" to "creator",
          "memberIds" to listOf("creator"),
          "type" to EventType.COURSE.name,
          "subject" to "Analysis",
          "startsAt" to timestamp(time),
          "endsAt" to timestamp(time.plusSeconds(3600)),
          "createdAt" to timestamp(time),
          "updatedAt" to timestamp(time),
          "location" to "BC 410",
          "description" to "Review exercises",
      )

  /** Converts UTC instants to Firestore timestamps. */
  private fun timestamp(value: Instant) = Timestamp(value.epochSecond, value.nano)
}
