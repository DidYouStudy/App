// Co-authored-by: Gemini AI Agent
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InMemoryEventsRepositorySaveEventsTest {

  private lateinit var repository: InMemoryEventsRepository
  private val ownerId1 = "user_alpha"
  private val ownerId2 = "user_beta"

  private val baseInstant = Instant.parse("2026-10-15T10:00:00Z")

  @Before
  fun setUp() {
    repository = InMemoryEventsRepository()
  }

  private fun createSampleEvent(
      id: String,
      ownerId: String,
      subject: String,
      offsetHours: Long = 0,
      durationMinutes: Long = 60,
  ): Event {
    val start = baseInstant.plus(Duration.ofHours(offsetHours))
    val end = start.plus(Duration.ofMinutes(durationMinutes))
    return Event(
        id = id,
        ownerId = ownerId,
        type = EventType.COURSE,
        subject = subject,
        startsAt = start,
        endsAt = end,
        createdAt = baseInstant,
        updatedAt = baseInstant,
    )
  }

  @Test
  fun saveEventsEmptyListReturnsSuccessAndDoesNotMutateState() = runBlocking {
    val result = repository.saveEvents(emptyList())

    assertTrue(result.isSuccess)
    val observed =
        repository
            .observeEvents(
                ownerId1,
                baseInstant.minus(Duration.ofDays(1)),
                baseInstant.plus(Duration.ofDays(1)),
            )
            .first()

    assertTrue(observed.getOrThrow().isEmpty())
  }

  @Test
  fun saveEventsSavesAllEventsForSingleUserAndOverwritesDuplicates() = runBlocking {
    val event1 = createSampleEvent("event_1", ownerId1, "Analysis I", offsetHours = 0)
    val event2 = createSampleEvent("event_2", ownerId1, "Algebra", offsetHours = 2)

    val batchResult = repository.saveEvents(listOf(event1, event2))
    assertTrue(batchResult.isSuccess)

    // Verify both events are present and queryable
    val fetched1 = repository.getEvent(ownerId1, "event_1").getOrThrow()
    val fetched2 = repository.getEvent(ownerId1, "event_2").getOrThrow()
    assertEquals("Analysis I", fetched1.subject)
    assertEquals("Algebra", fetched2.subject)

    // Overwrite event_1 with updated subject via saveEvents
    val updatedEvent1 = event1.copy(subject = "Analysis I - Advanced")
    repository.saveEvents(listOf(updatedEvent1))

    val refetched1 = repository.getEvent(ownerId1, "event_1").getOrThrow()
    assertEquals("Analysis I - Advanced", refetched1.subject)

    // Verify list size remains 2, not 3
    val allEvents =
        repository
            .observeEvents(
                ownerId1,
                baseInstant.minus(Duration.ofDays(1)),
                baseInstant.plus(Duration.ofDays(1)),
            )
            .first()
            .getOrThrow()
    assertEquals(2, allEvents.size)
  }

  @Test
  fun saveEventsAggregatesAndDeduplicatesSubjectsCaseInsensitively() = runBlocking {
    val event1 =
        createSampleEvent("e1", ownerId1, "Physics", offsetHours = 0)
            .copy(updatedAt = baseInstant.plusSeconds(10))
    val event2 =
        createSampleEvent("e2", ownerId1, "PHYSICS", offsetHours = 2)
            .copy(updatedAt = baseInstant.plusSeconds(30)) // Latest spelling and timestamp
    val event3 =
        createSampleEvent("e3", ownerId1, "Computer Science", offsetHours = 4)
            .copy(updatedAt = baseInstant.plusSeconds(20))

    repository.saveEvents(listOf(event1, event2, event3))

    val subjects = repository.observeSubjects(ownerId1).first().getOrThrow()

    // Deduplication check: "Physics" and "PHYSICS" collapsed into 1 subject
    assertEquals(2, subjects.size)

    // Order check: Sorted descending by usedAt (PHYSICS at +30s comes before Computer Science at
    // +20s)
    assertEquals("PHYSICS", subjects[0])
    assertEquals("Computer Science", subjects[1])
  }

  /** Mixed-owner batches are rejected before either user's data is changed. */
  @Test
  fun saveEventsRejectsMixedOwnersWithoutWrites() = runBlocking {
    val user1Event = createSampleEvent("user1_ev", ownerId1, "User1 Course")
    val user2Event = createSampleEvent("user2_ev", ownerId2, "User2 Course")

    val result = repository.saveEvents(listOf(user1Event, user2Event))

    assertEquals(EventsError.INVALID_ARGUMENT, (result.exceptionOrNull() as EventsException).error)
    assertEquals(
        EventsError.NOT_FOUND,
        (repository.getEvent(ownerId1, user1Event.id).exceptionOrNull() as EventsException).error,
    )
    assertEquals(
        EventsError.NOT_FOUND,
        (repository.getEvent(ownerId2, user2Event.id).exceptionOrNull() as EventsException).error,
    )
    assertTrue(repository.observeSubjects(ownerId1).first().getOrThrow().isEmpty())
    assertTrue(repository.observeSubjects(ownerId2).first().getOrThrow().isEmpty())
  }

  /** Separate batches remain isolated between owners. */
  @Test
  fun saveEventsIsolatesDataBetweenMultipleUsers() = runBlocking {
    val user1Event = createSampleEvent("user1_ev", ownerId1, "User1 Course")
    val user2Event = createSampleEvent("user2_ev", ownerId2, "User2 Course")

    repository.saveEvents(listOf(user1Event)).getOrThrow()
    repository.saveEvents(listOf(user2Event)).getOrThrow()

    // User 1 sees only their event and subjects
    val user1Events =
        repository
            .observeEvents(
                ownerId1,
                baseInstant.minus(Duration.ofDays(1)),
                baseInstant.plus(Duration.ofDays(1)),
            )
            .first()
            .getOrThrow()
    assertEquals(1, user1Events.size)
    assertEquals("user1_ev", user1Events.first().id)

    val user1Subjects = repository.observeSubjects(ownerId1).first().getOrThrow()
    assertEquals(listOf("User1 Course"), user1Subjects)

    // User 2 sees only their event and subjects
    val user2Events =
        repository
            .observeEvents(
                ownerId2,
                baseInstant.minus(Duration.ofDays(1)),
                baseInstant.plus(Duration.ofDays(1)),
            )
            .first()
            .getOrThrow()
    assertEquals(1, user2Events.size)
    assertEquals("user2_ev", user2Events.first().id)
  }
}
