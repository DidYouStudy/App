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

  /** Blank IDs and empty ranges are rejected before reaching the storage. */
  @Test
  fun invalidArguments_areRejected() = runTest {
    val invalid = EventsError.INVALID_ARGUMENT
    assertEquals(invalid, repository.observeEvents(" ", monday, tuesday).first().error())
    assertEquals(invalid, repository.observeEvents(owner, tuesday, tuesday).first().error())
    assertEquals(invalid, repository.observeEvents(owner, tuesday, monday).first().error())
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
