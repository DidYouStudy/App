// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Behavior every [EventsRepository] must have. Subclasses provide the implementation under test, so
 * the Firestore repository (#132) can run the same checks as the in-memory one.
 */
abstract class EventsRepositoryContractTest {
  /** Creates an empty repository for one test. */
  protected abstract fun createRepository(): EventsRepository

  private lateinit var repository: EventsRepository

  private val owner = "user-1"
  private val otherOwner = "user-2"
  private val created = Instant.parse("2026-10-18T12:00:00Z")
  private val monday = Instant.parse("2026-10-19T00:00:00Z")
  private val tuesday = Instant.parse("2026-10-20T00:00:00Z")
  private val wednesday = Instant.parse("2026-10-21T00:00:00Z")

  @Before
  fun setUp() {
    repository = createRepository()
  }

  /** Builds a valid event; times are hours after Monday midnight UTC. */
  private fun event(id: String, startHour: Long, endHour: Long, ownerId: String = owner) =
      Event(
          id = id,
          ownerId = ownerId,
          type = EventType.STUDY_SESSION,
          subject = "Analysis",
          startsAt = monday.plusSeconds(startHour * 3600),
          endsAt = monday.plusSeconds(endHour * 3600),
          createdAt = created,
      )

  /** Reads the current list once. */
  private suspend fun current(from: Instant = monday, to: Instant = wednesday) =
      repository.observeEvents(owner, from, to).first().getOrThrow()

  /** Builds a valid event with [subject], last saved [minute] minutes after creation. */
  private fun withSubject(id: String, subject: String?, minute: Long, ownerId: String = owner) =
      event(id, 9, 10, ownerId)
          .copy(
              type = if (subject == null) EventType.OTHER else EventType.COURSE,
              subject = subject,
              updatedAt = created.plusSeconds(minute * 60),
          )

  /** Reads the owner's subjects once. */
  private suspend fun subjects(ownerId: String = owner) =
      repository.observeSubjects(ownerId).first().getOrThrow()

  /** Returns the error category of a failed result. */
  private fun Result<*>.error() = (exceptionOrNull() as EventsException).error

  /** A saved event can be read back, and saving the same ID again replaces it. */
  @Test
  fun saveEvent_createsThenReplaces() = runTest {
    val original = event("a", 9, 10)
    val edited = original.copy(subject = "Physics")

    repository.saveEvent(original).getOrThrow()
    assertEquals(original, repository.getEvent(owner, "a").getOrThrow())

    repository.saveEvent(edited).getOrThrow()
    assertEquals(edited, repository.getEvent(owner, "a").getOrThrow())
    assertEquals(listOf(edited), current())
  }

  /** Deleting removes the event; deleting again or a missing event still succeeds. */
  @Test
  fun getImportedEvents_returnsOnlyTheOwnersImportedEventsOfAnyDate() = runTest {
    val imported = event("a", 9, 10).copy(importKey = "uid-a")
    val farAway =
        event("b", 9, 10)
            .copy(
                startsAt = monday.plusSeconds(400 * 86_400L),
                endsAt = monday.plusSeconds(400 * 86_400L + 3600),
                importKey = "uid-b",
            )
    repository.saveEvents(
        listOf(
            imported,
            farAway,
            event("manual", 11, 12),
            event("other", 9, 10, otherOwner).copy(importKey = "uid-x"),
        )
    )

    val result = repository.getImportedEvents(owner).getOrThrow()

    assertEquals(listOf(imported, farAway), result)
  }

  @Test
  fun getImportedEvents_sortsByStartThenEndThenId() = runTest {
    val late = event("a", 9, 12).copy(importKey = "k1")
    val shortSameStart = event("b", 9, 10).copy(importKey = "k2")
    val tieB = event("d", 9, 10).copy(importKey = "k3")
    val earlier = event("z", 8, 9).copy(importKey = "k4")
    repository.saveEvents(listOf(late, tieB, shortSameStart, earlier))

    val ids = repository.getImportedEvents(owner).getOrThrow().map { it.id }

    assertEquals(listOf("z", "b", "d", "a"), ids)
  }

  @Test
  fun getImportedEvents_blankOwner_isInvalidArgument() = runTest {
    val error = repository.getImportedEvents(" ").exceptionOrNull() as EventsException

    assertEquals(EventsError.INVALID_ARGUMENT, error.error)
  }

  @Test
  fun deleteEvent_removesAndIsIdempotent() = runTest {
    repository.saveEvent(event("a", 9, 10)).getOrThrow()

    repository.deleteEvent(owner, "a").getOrThrow()
    repository.deleteEvent(owner, "a").getOrThrow()
    repository.deleteEvent("user-without-events", "a").getOrThrow()

    assertEquals(EventsError.NOT_FOUND, repository.getEvent(owner, "a").error())
    assertTrue(current().isEmpty())
  }

  /** A missing event is reported as NOT_FOUND, not as a generic failure. */
  @Test
  fun getEvent_missing_isNotFound() = runTest {
    assertEquals(EventsError.NOT_FOUND, repository.getEvent(owner, "missing").error())
  }

  /** Events come sorted by start, then end, then ID, whatever the save order. */
  @Test
  fun observeEvents_sortsByStartThenEndThenId() = runTest {
    val late = event("late", 14, 15)
    val shortEarly = event("b", 9, 10)
    val longEarly = event("a", 9, 11)
    val sameAsShort = event("a2", 9, 10)
    for (e in listOf(late, longEarly, shortEarly, sameAsShort)) repository.saveEvent(e).getOrThrow()

    assertEquals(listOf(sameAsShort, shortEarly, longEarly, late), current())
  }

  /**
   * Range is [from, to): touching events are excluded, cross-midnight events appear on both days.
   */
  @Test
  fun observeEvents_returnsOnlyOverlappingEvents() = runTest {
    val endsAtFrom = event("endsAtFrom", 22, 24) // Monday 22:00 to Tuesday 00:00
    val crossesMidnight = event("crosses", 23, 25) // Monday 23:00 to Tuesday 01:00
    val startsAtTo = event("startsAtTo", 48, 49) // Wednesday 00:00 to 01:00
    for (e in listOf(endsAtFrom, crossesMidnight, startsAtTo)) repository.saveEvent(e).getOrThrow()

    assertEquals(listOf(crossesMidnight), current(from = tuesday, to = wednesday))
    assertEquals(listOf(endsAtFrom, crossesMidnight), current(from = monday, to = tuesday))
  }

  /** One user never sees, reads or deletes another user's events. */
  @Test
  fun events_areIsolatedPerOwner() = runTest {
    val mine = event("same-id", 9, 10)
    val theirs = event("same-id", 11, 12, ownerId = otherOwner)
    repository.saveEvent(mine).getOrThrow()
    repository.saveEvent(theirs).getOrThrow()

    repository.deleteEvent(otherOwner, "same-id").getOrThrow()

    assertEquals(listOf(mine), current())
    assertEquals(mine, repository.getEvent(owner, "same-id").getOrThrow())
    assertEquals(EventsError.NOT_FOUND, repository.getEvent(otherOwner, "same-id").error())
  }

  /** The flow emits the current list, then again after each save and delete. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observeEvents_reEmitsOnEveryChange() = runTest {
    val event = event("a", 9, 10)
    val emissions = mutableListOf<List<Event>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeEvents(owner, monday, wednesday).collect { emissions += it.getOrThrow() }
    }

    repository.saveEvent(event).getOrThrow()
    repository.deleteEvent(owner, "a").getOrThrow()

    assertEquals(listOf(emptyList(), listOf(event), emptyList()), emissions)
  }

  /** Subjects come most recently used first; ties are sorted by name. */
  @Test
  fun observeSubjects_ordersByLastUseThenAlphabetically() = runTest {
    repository.saveEvent(withSubject("a", "Analysis", minute = 1)).getOrThrow()
    repository.saveEvent(withSubject("b", "Physics", minute = 3)).getOrThrow()
    repository.saveEvent(withSubject("c", "Chemistry", minute = 1)).getOrThrow()

    assertEquals(listOf("Physics", "Analysis", "Chemistry"), subjects())
  }

  /** Spelling variants count as one subject, shown with the spelling of the latest save. */
  @Test
  fun observeSubjects_mergesCaseAndSpaceVariants() = runTest {
    repository.saveEvent(withSubject("a", "analysis", minute = 1)).getOrThrow()
    repository.saveEvent(withSubject("b", " Analysis ", minute = 2)).getOrThrow()

    assertEquals(listOf("Analysis"), subjects())
  }

  /** Updating an event to have no subject leaves the existing subject suggestions unchanged. */
  @Test
  fun observeSubjects_keepsExistingSubjectsWhenEventIsUpdatedWithoutSubject() = runTest {
    val original = withSubject("a", "Analysis", minute = 1)
    repository.saveEvent(original).getOrThrow()
    repository.saveEvent(withSubject("b", "Physics", minute = 2)).getOrThrow()

    val updated =
        original.copy(
            type = EventType.OTHER,
            subject = null,
            updatedAt = created.plusSeconds(180),
        )
    repository.saveEvent(updated).getOrThrow()

    assertEquals(listOf("Physics", "Analysis"), subjects())
  }

  /** Suggestions outlive their events, and events without a subject add nothing. */
  @Test
  fun observeSubjects_keepsDeletedAndIgnoresMissingSubjects() = runTest {
    repository.saveEvent(withSubject("a", "Analysis", minute = 1)).getOrThrow()
    repository.saveEvent(withSubject("b", null, minute = 2)).getOrThrow()

    repository.deleteEvent(owner, "a").getOrThrow()

    assertEquals(listOf("Analysis"), subjects())
  }

  /** One user never sees another user's subjects. */
  @Test
  fun observeSubjects_isIsolatedPerOwner() = runTest {
    repository.saveEvent(withSubject("a", "Analysis", minute = 1)).getOrThrow()
    repository.saveEvent(withSubject("b", "Physics", minute = 1, ownerId = otherOwner)).getOrThrow()

    assertEquals(listOf("Analysis"), subjects())
    assertEquals(listOf("Physics"), subjects(otherOwner))
  }

  /** The flow emits the current list, then again when a new subject is used. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observeSubjects_reEmitsOnNewSubject() = runTest {
    val emissions = mutableListOf<List<String>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeSubjects(owner).collect { emissions += it.getOrThrow() }
    }

    repository.saveEvent(withSubject("a", "Analysis", minute = 1)).getOrThrow()

    assertEquals(listOf(emptyList(), listOf("Analysis")), emissions)
  }

  /** Blank IDs and empty ranges are rejected before reaching the storage. */
  @Test
  fun invalidArguments_areRejected() = runTest {
    val invalid = EventsError.INVALID_ARGUMENT
    assertEquals(invalid, repository.observeEvents(" ", monday, tuesday).first().error())
    assertEquals(invalid, repository.observeEvents(owner, tuesday, tuesday).first().error())
    assertEquals(invalid, repository.observeEvents(owner, tuesday, monday).first().error())
    assertEquals(invalid, repository.observeSubjects(" ").first().error())
    assertEquals(invalid, repository.getEvent(owner, " ").error())
    assertEquals(invalid, repository.getEvent("", "a").error())
    assertEquals(invalid, repository.deleteEvent(owner, "").error())
    assertEquals(invalid, repository.deleteEvent(" ", "a").error())
  }

  /** New IDs are nonblank and unique. */
  @Test
  fun newEventId_isUniqueAndNonBlank() {
    val ids = List(100) { repository.newEventId() }

    assertTrue(ids.all { it.isNotBlank() })
    assertEquals(ids.size, ids.toSet().size)
    assertNotEquals(ids[0], ids[1])
  }
}
