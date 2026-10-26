// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

/** Wire-format regression tests independent of an emulator or Android UI. */
class EventFirestoreMapperTest {
  private val time = Instant.parse("2026-10-20T12:00:00.123456Z")
  private val event =
      Event(
          "id",
          "owner",
          EventType.COURSE,
          "Analysis",
          time,
          time.plusSeconds(3600),
          time,
          location = "BC 410",
          description = "Details",
          tasks = listOf(EventTask("First"), EventTask("Second", time)),
      )

  /** Every activity type and every event field survives mapping. */
  @Test
  fun roundTrip() {
    EventType.entries.forEach { type ->
      val expected = event.copy(type = type)
      assertEquals(
          expected,
          EventFirestoreMapper.decode("id", "owner", EventFirestoreMapper.encode(expected)),
      )
    }
  }

  /** Local timestamps match server microseconds; exclusive query bounds round upward. */
  @Test
  fun normalizesSubMicrosecondPrecision() {
    val precise = time.plusNanos(789)
    val input =
        event.copy(
            startsAt = precise,
            endsAt = precise.plusSeconds(3600),
            createdAt = precise,
            updatedAt = precise,
            tasks = listOf(EventTask("Task", precise)),
        )
    val expected =
        input.copy(
            startsAt = time,
            endsAt = time.plusSeconds(3600),
            createdAt = time,
            updatedAt = time,
            tasks = listOf(EventTask("Task", time)),
        )
    assertEquals(
        expected,
        EventFirestoreMapper.decode("id", "owner", EventFirestoreMapper.encode(input)),
    )
    assertEquals(
        EventFirestoreMapper.timestamp(time.plusNanos(1000)),
        EventFirestoreMapper.exclusiveUpperBound(precise),
    )
    assertEquals(
        EventFirestoreMapper.timestamp(time),
        EventFirestoreMapper.exclusiveUpperBound(time),
    )
  }

  /** Absent optional fields follow the model defaults. */
  @Test
  fun missingOptionals() {
    val data = EventFirestoreMapper.encode(event.copy(type = EventType.OTHER)).toMutableMap()
    listOf("subject", "location", "description", "tasks").forEach { data.remove(it) }
    assertEquals(
        event.copy(
            type = EventType.OTHER,
            subject = null,
            location = null,
            description = null,
            tasks = emptyList(),
        ),
        EventFirestoreMapper.decode("id", "owner", data),
    )
  }

  /** Bad schema, owner mismatches and invalid nested tasks never become partial events. */
  @Test
  fun malformedRecords() {
    val valid = EventFirestoreMapper.encode(event)
    val mutations =
        listOf(
            "ownerId" to "someone-else",
            "type" to "UNKNOWN",
            "subject" to " ",
            "startsAt" to "yesterday",
            "endsAt" to valid["startsAt"],
            "createdAt" to null,
            "updatedAt" to 5,
            "location" to false,
            "description" to 5,
            "tasks" to null,
            "tasks" to listOf(mapOf("text" to " ")),
            "tasks" to listOf(mapOf("text" to "Task", "completedAt" to true)),
        )
    mutations.forEach { (key, value) ->
      val error =
          assertThrows(EventsException::class.java) {
            EventFirestoreMapper.decode("id", "owner", valid + (key to value))
          }
      assertEquals("Invalid $key", EventsError.INVALID_EVENT_DATA, error.error)
    }
    assertThrows(EventsException::class.java) {
      EventFirestoreMapper.decode("id", "owner", valid - "subject")
    }
  }

  /** Subject field names safely encode punctuation and Unicode and merge case/space variants. */
  @Test
  fun subjectsNormalizeAndSort() {
    assertEquals(
        SubjectCatalogFirestoreMapper.key(" Analysis "),
        SubjectCatalogFirestoreMapper.key("analysis"),
    )
    assertNotEquals(
        SubjectCatalogFirestoreMapper.key("a.b"),
        SubjectCatalogFirestoreMapper.key("a/b"),
    )
    val a =
        SubjectCatalogFirestoreMapper.encode(event.copy(subject = " Analysis "))!!["subjects"]
            as Map<*, *>
    val b =
        SubjectCatalogFirestoreMapper.encode(
            event.copy(subject = "Physics", updatedAt = time.plusSeconds(1))
        )!!["subjects"]
            as Map<*, *>
    val c =
        SubjectCatalogFirestoreMapper.encode(event.copy(subject = "Chemistry"))!!["subjects"]
            as Map<*, *>
    assertEquals(
        listOf("Physics", "Analysis", "Chemistry"),
        SubjectCatalogFirestoreMapper.decode(mapOf("subjects" to (a + b + c))),
    )
    assertNull(
        SubjectCatalogFirestoreMapper.encode(event.copy(type = EventType.OTHER, subject = null))
    )
    assertEquals(emptyList<String>(), SubjectCatalogFirestoreMapper.decode(null))
    assertThrows(EventsException::class.java) {
      SubjectCatalogFirestoreMapper.decode(mapOf("subjects" to mapOf("bad" to "entry")))
    }
  }

  /** A malformed subject invalidates the catalog instead of exposing partial suggestions. */
  @Test
  fun rejectsInvalidSubjectSpellingsAndKeys() {
    val usedAt = EventFirestoreMapper.timestamp(time)
    val valid = SubjectCatalogFirestoreMapper.encode(event)!!["subjects"] as Map<*, *>
    val invalidEntries =
        listOf(
            SubjectCatalogFirestoreMapper.key("") to "",
            SubjectCatalogFirestoreMapper.key(" ") to " ",
            SubjectCatalogFirestoreMapper.key("Physics") to " Physics ",
            SubjectCatalogFirestoreMapper.key("Chemistry") to "Physics",
        )
    invalidEntries.forEach { (key, spelling) ->
      val data =
          mapOf("subjects" to (valid + (key to mapOf("spelling" to spelling, "usedAt" to usedAt))))
      val error =
          assertThrows(EventsException::class.java) { SubjectCatalogFirestoreMapper.decode(data) }
      assertEquals(
          "Invalid spelling '$spelling' with key '$key'",
          EventsError.INVALID_EVENT_DATA,
          error.error,
      )
    }
  }
}
