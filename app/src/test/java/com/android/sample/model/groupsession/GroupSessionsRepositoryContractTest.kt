// Co-authored-by: GitHub Copilot
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
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
 * Behavior every [GroupSessionsRepository] must provide; subclasses supply the repository to test.
 */
abstract class GroupSessionsRepositoryContractTest {
  /** Creates a fresh, empty repository for each contract test. */
  protected abstract fun createRepository(): GroupSessionsRepository

  private lateinit var repository: GroupSessionsRepository

  private val creator = "creator"
  private val other = "other"
  private val third = "third"
  private val created = Instant.parse("2026-10-18T12:00:00Z")
  private val monday = Instant.parse("2026-10-19T00:00:00Z")
  private val tuesday = Instant.parse("2026-10-20T00:00:00Z")

  /** Initializes a clean repository instance before each test. */
  @Before
  fun setUp() {
    repository = createRepository()
  }

  /** Builds a valid session at fixed hours after Monday midnight UTC. */
  private fun session(
      id: String,
      startHour: Long = 9,
      endHour: Long = 10,
      createdBy: String = creator,
      memberIds: List<String> = listOf(createdBy, other),
      subject: String? = "Analysis",
  ) =
      GroupSession(
          id = id,
          createdBy = createdBy,
          memberIds = memberIds,
          type = if (subject == null) EventType.OTHER else EventType.STUDY_SESSION,
          subject = subject,
          startsAt = monday.plusSeconds(startHour * 3600),
          endsAt = monday.plusSeconds(endHour * 3600),
          createdAt = created,
      )

  /** Reads the public list as of [endingAfter]. */
  private suspend fun current(endingAfter: Instant = monday) =
      repository.observeSessions(endingAfter).first().getOrThrow()

  /** Returns the stable category from a failed repository result. */
  private fun Result<*>.error() = (exceptionOrNull() as EventsException).error

  /** A saved session can be read back with every field unchanged. */
  @Test
  fun saveSession_createsAndGetReturnsSameSession() = runTest {
    val value = session("a")

    repository.saveSession(creator, value).getOrThrow()

    assertEquals(value, repository.getSession("a").getOrThrow())
  }

  /** Replacing updates session fields but preserves the original stored member list. */
  @Test
  fun saveSession_replacementKeepsStoredMembers() = runTest {
    val original = session("a")
    val replacement =
        original.copy(
            subject = "Physics",
            startsAt = monday.plusSeconds(11 * 3600),
            endsAt = monday.plusSeconds(12 * 3600),
            updatedAt = created.plusSeconds(300),
            location = "Library",
            description = "Review mechanics",
            memberIds = listOf(creator, third),
        )
    repository.saveSession(creator, original).getOrThrow()

    repository.saveSession(creator, replacement).getOrThrow()

    assertEquals(
        replacement.copy(memberIds = original.memberIds),
        repository.getSession("a").getOrThrow(),
    )
  }

  /** A caller cannot create a session for a different creator, and no session is stored. */
  @Test
  fun saveSession_createByDifferentUserIsDenied() = runTest {
    val value = session("a", createdBy = other, memberIds = listOf(other))

    assertEquals(EventsError.PERMISSION_DENIED, repository.saveSession(creator, value).error())
    assertEquals(EventsError.NOT_FOUND, repository.getSession("a").error())
  }

  /** A non-creator cannot update the session, and the stored value remains unchanged. */
  @Test
  fun saveSession_nonCreatorCannotReplace() = runTest {
    val original = session("a")
    repository.saveSession(creator, original).getOrThrow()

    assertEquals(
        EventsError.PERMISSION_DENIED,
        repository.saveSession(other, original.copy(subject = "Physics")).error(),
    )
    assertEquals(original, repository.getSession("a").getOrThrow())
  }

  /** An existing session cannot be transferred to another creator. */
  @Test
  fun saveSession_cannotChangeCreator() = runTest {
    val original = session("a")
    repository.saveSession(creator, original).getOrThrow()
    val transferred = original.copy(createdBy = other, memberIds = listOf(other))

    assertEquals(EventsError.PERMISSION_DENIED, repository.saveSession(other, transferred).error())
    assertEquals(original, repository.getSession("a").getOrThrow())
  }

  /** Looking up an unknown ID returns NOT_FOUND. */
  @Test
  fun getSession_missingIsNotFound() = runTest {
    assertEquals(EventsError.NOT_FOUND, repository.getSession("missing").error())
  }

  /** Every method accepting a user or session ID rejects blank values as INVALID_ARGUMENT. */
  @Test
  fun blankUserAndSessionIds_areInvalidArguments() = runTest {
    val value = session("a")
    val invalid = EventsError.INVALID_ARGUMENT

    assertEquals(invalid, repository.getSession(" ").error())
    assertEquals(invalid, repository.saveSession("", value).error())
    assertEquals(invalid, repository.deleteSession("", "a").error())
    assertEquals(invalid, repository.deleteSession(creator, "").error())
    assertEquals(invalid, repository.joinSession("", "a").error())
    assertEquals(invalid, repository.joinSession(creator, "").error())
    assertEquals(invalid, repository.leaveSession("", "a").error())
    assertEquals(invalid, repository.leaveSession(creator, "").error())
  }

  /** The creator can delete a session, after which lookup reports NOT_FOUND. */
  @Test
  fun deleteSession_creatorRemovesSession() = runTest {
    repository.saveSession(creator, session("a")).getOrThrow()

    repository.deleteSession(creator, "a").getOrThrow()

    assertEquals(EventsError.NOT_FOUND, repository.getSession("a").error())
  }

  /** Another user cannot delete a session and the stored session is preserved. */
  @Test
  fun deleteSession_nonCreatorIsDenied() = runTest {
    val original = session("a")
    repository.saveSession(creator, original).getOrThrow()

    assertEquals(EventsError.PERMISSION_DENIED, repository.deleteSession(other, "a").error())
    assertEquals(original, repository.getSession("a").getOrThrow())
  }

  /** Deleting a session that is already missing succeeds. */
  @Test
  fun deleteSession_missingSessionSucceeds() = runTest {
    repository.deleteSession(creator, "missing").getOrThrow()
  }

  /** Joining adds a member once, including when the same user joins repeatedly. */
  @Test
  fun joinSession_addsUserOnlyOnce() = runTest {
    repository.saveSession(creator, session("a")).getOrThrow()

    repository.joinSession(third, "a").getOrThrow()
    repository.joinSession(third, "a").getOrThrow()

    assertEquals(listOf(creator, other, third), repository.getSession("a").getOrThrow().memberIds)
  }

  /** A join against a missing session reports NOT_FOUND. */
  @Test
  fun joinSession_missingSessionIsNotFound() = runTest {
    assertEquals(EventsError.NOT_FOUND, repository.joinSession(third, "missing").error())
  }

  /** Leaving removes a member, while leaving as a non-member changes nothing. */
  @Test
  fun leaveSession_removesMemberAndIgnoresNonMember() = runTest {
    val original = session("a")
    repository.saveSession(creator, original).getOrThrow()

    repository.leaveSession(other, "a").getOrThrow()
    assertEquals(listOf(creator), repository.getSession("a").getOrThrow().memberIds)
    repository.leaveSession(third, "a").getOrThrow()
    assertEquals(listOf(creator), repository.getSession("a").getOrThrow().memberIds)
  }

  /** Leaving a missing session reports NOT_FOUND. */
  @Test
  fun leaveSession_missingSessionIsNotFound() = runTest {
    assertEquals(EventsError.NOT_FOUND, repository.leaveSession(other, "missing").error())
  }

  /** The creator cannot leave and remains in the stored membership. */
  @Test
  fun leaveSession_creatorIsRejectedAndRemainsMember() = runTest {
    repository.saveSession(creator, session("a")).getOrThrow()

    assertEquals(EventsError.INVALID_ARGUMENT, repository.leaveSession(creator, "a").error())
    assertTrue(repository.getSession("a").getOrThrow().memberIds.contains(creator))
  }

  /**
   * Any user can observe public sessions; ended entries are excluded and ties sort
   * deterministically.
   */
  @Test
  fun observeSessions_returnsPublicUnendedSessionsInStableOrder() = runTest {
    val tiedLateId = session("z", 9, 10)
    val tiedEarlyId = session("a", 9, 10)
    val laterEnd = session("b", 9, 11)
    val ongoing = session("ongoing", 8, 10)
    val future = session("future", 12, 13, createdBy = other, memberIds = listOf(other))
    val endingAtThreshold = session("ended", 8, 9)
    for (value in listOf(laterEnd, tiedLateId, endingAtThreshold, future, ongoing, tiedEarlyId)) {
      repository.saveSession(value.createdBy, value).getOrThrow()
    }

    assertEquals(
        listOf(ongoing, tiedEarlyId, tiedLateId, laterEnd, future),
        current(endingAfter = monday.plusSeconds(9 * 3600)),
    )
    assertEquals(
        listOf(endingAtThreshold, ongoing, tiedEarlyId, tiedLateId, laterEnd, future),
        current(),
    )
  }

  /** The live list emits after save, join, leave, and delete changes the visible sessions. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observeSessions_reEmitsAfterMembershipAndSessionChanges() = runTest {
    val original = session("a")
    val emissions = mutableListOf<List<GroupSession>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeSessions(monday).collect { emissions += it.getOrThrow() }
    }

    repository.saveSession(creator, original).getOrThrow()
    repository.joinSession(third, "a").getOrThrow()
    repository.leaveSession(other, "a").getOrThrow()
    repository.deleteSession(creator, "a").getOrThrow()

    val joined = original.copy(memberIds = listOf(creator, other, third))
    val left = original.copy(memberIds = listOf(creator, third))
    assertEquals(
        listOf(emptyList(), listOf(original), listOf(joined), listOf(left), emptyList()),
        emissions,
    )
  }

  /** Generated IDs are nonblank and distinct. */
  @Test
  fun newSessionId_isNonBlankAndUnique() {
    val ids = List(100) { repository.newSessionId() }

    assertTrue(ids.all { it.isNotBlank() })
    assertEquals(ids.size, ids.toSet().size)
    assertNotEquals(ids[0], ids[1])
  }
}
