// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Unit tests for [GetUserScheduleUseCase]. */
class GetUserScheduleUseCaseTest {

  private val studentId = "student-123"
  private val otherUserId = "user-456"

  private val start1 = Instant.parse("2026-10-10T09:00:00Z")
  private val end1 = Instant.parse("2026-10-10T10:00:00Z")

  private val start2 = Instant.parse("2026-10-10T10:00:00Z")
  private val end2 = Instant.parse("2026-10-10T11:00:00Z")

  private val start3 = Instant.parse("2026-10-10T11:00:00Z")
  private val end3 = Instant.parse("2026-10-10T12:00:00Z")

  private val from = Instant.parse("2026-10-10T00:00:00Z")
  private val to = Instant.parse("2026-10-11T00:00:00Z")

  private val utcClock = Clock.fixed(start1, ZoneId.of("UTC"))

  private lateinit var eventsRepository: InMemoryEventsRepository
  private lateinit var groupSessionsRepository: InMemoryGroupSessionsRepository
  private lateinit var useCase: GetUserScheduleUseCase

  @Before
  fun setUp() {
    eventsRepository = InMemoryEventsRepository()
    groupSessionsRepository = InMemoryGroupSessionsRepository()
    useCase = GetUserScheduleUseCase(eventsRepository, groupSessionsRepository, utcClock)
  }

  @Test
  fun signedOutStudent_returnsEmptyListSuccess() = runTest {
    val nullResult = useCase(null, from, to).first()
    assertTrue(nullResult.isSuccess)
    assertTrue(nullResult.getOrThrow().isEmpty())

    val blankResult = useCase("   ", from, to).first()
    assertTrue(blankResult.isSuccess)
    assertTrue(blankResult.getOrThrow().isEmpty())
  }

  @Test
  fun invalidTimeRange_returnsInvalidArgumentError() = runTest {
    val result = useCase(studentId, to, from).first()
    assertTrue(result.isFailure)

    val exception = result.exceptionOrNull() as? EventsException
    assertEquals(EventsError.INVALID_ARGUMENT, exception?.error)
  }

  @Test
  fun combinesPersonalEventsAndJoinedGroupSessions_sortedByStartTime() = runTest {
    val personalEvent =
        PersonalEvent(
            id = "personal-1",
            ownerId = studentId,
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Math",
                    startsAt = start2,
                    endsAt = end2,
                ),
            createdAt = start2,
        )

    val createdGroupSession =
        GroupSession(
            id = "group-created",
            createdBy = studentId,
            memberIds = listOf(studentId),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Physics",
                    startsAt = start1,
                    endsAt = end1,
                ),
            createdAt = start1,
        )

    val joinedGroupSession =
        GroupSession(
            id = "group-joined",
            createdBy = otherUserId,
            memberIds = listOf(otherUserId, studentId),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Chemistry",
                    startsAt = start3,
                    endsAt = end3,
                ),
            createdAt = start3,
        )

    val unjoinedGroupSession =
        GroupSession(
            id = "group-unjoined",
            createdBy = otherUserId,
            memberIds = listOf(otherUserId),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Biology",
                    startsAt = start1,
                    endsAt = end1,
                ),
            createdAt = start1,
        )

    eventsRepository.saveEvent(personalEvent)
    groupSessionsRepository.saveSession(studentId, createdGroupSession)
    groupSessionsRepository.saveSession(otherUserId, joinedGroupSession)
    groupSessionsRepository.saveSession(otherUserId, unjoinedGroupSession)

    val result = useCase(studentId, from, to).first()
    assertTrue(result.isSuccess)

    val schedule = result.getOrThrow()
    assertEquals(3, schedule.size)

    assertEquals("group-created", schedule[0].id)
    assertTrue(schedule[0] is GroupSession)

    assertEquals("personal-1", schedule[1].id)
    assertTrue(schedule[1] is PersonalEvent)

    assertEquals("group-joined", schedule[2].id)
    assertTrue(schedule[2] is GroupSession)
  }

  @Test
  fun updatesWhenPersonalEventsChange() = runTest {
    val personalEvent =
        PersonalEvent(
            id = "personal-1",
            ownerId = studentId,
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Math",
                    startsAt = start1,
                    endsAt = end1,
                ),
            createdAt = start1,
        )

    var schedule = useCase(studentId, from, to).first().getOrThrow()
    assertTrue(schedule.isEmpty())

    eventsRepository.saveEvent(personalEvent)

    schedule = useCase(studentId, from, to).first().getOrThrow()
    assertEquals(1, schedule.size)
    assertEquals("personal-1", schedule[0].id)

    eventsRepository.deleteEvent(studentId, "personal-1")

    schedule = useCase(studentId, from, to).first().getOrThrow()
    assertTrue(schedule.isEmpty())
  }

  @Test
  fun updatesWhenGroupSessionJoinedOrLeft() = runTest {
    val session =
        GroupSession(
            id = "group-1",
            createdBy = otherUserId,
            memberIds = listOf(otherUserId),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Physics",
                    startsAt = start1,
                    endsAt = end1,
                ),
            createdAt = start1,
        )
    groupSessionsRepository.saveSession(otherUserId, session)

    var schedule = useCase(studentId, from, to).first().getOrThrow()
    assertTrue(schedule.isEmpty())

    groupSessionsRepository.joinSession(studentId, "group-1")

    schedule = useCase(studentId, from, to).first().getOrThrow()
    assertEquals(1, schedule.size)
    assertEquals("group-1", schedule[0].id)

    groupSessionsRepository.leaveSession(studentId, "group-1")

    schedule = useCase(studentId, from, to).first().getOrThrow()
    assertTrue(schedule.isEmpty())
  }

  @Test
  fun reportsErrorFromEventsRepository() = runTest {
    val failingEventsRepo =
        object : EventsRepository by eventsRepository {
          override fun observeEvents(
              ownerId: String,
              from: Instant,
              to: Instant,
          ): Flow<Result<List<PersonalEvent>>> =
              flowOf(Result.failure(EventsException(EventsError.NOT_FOUND)))
        }

    val failingUseCase =
        GetUserScheduleUseCase(failingEventsRepo, groupSessionsRepository, utcClock)
    val result = failingUseCase(studentId, from, to).first()

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as? EventsException
    assertEquals(EventsError.NOT_FOUND, exception?.error)
  }

  @Test
  fun reportsErrorFromGroupSessionsRepository() = runTest {
    val failingSessionsRepo =
        object : GroupSessionsRepository by groupSessionsRepository {
          override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
              flowOf(Result.failure(EventsException(EventsError.NOT_FOUND)))
        }

    val failingUseCase = GetUserScheduleUseCase(eventsRepository, failingSessionsRepo, utcClock)
    val result = failingUseCase(studentId, from, to).first()

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as? EventsException
    assertEquals(EventsError.NOT_FOUND, exception?.error)
  }

  @Test
  fun reportsErrorImmediatelyWhenPersonalEventsFailsWhileGroupSessionsIsPending() = runTest {
    val failingEventsRepo =
        object : EventsRepository by eventsRepository {
          override fun observeEvents(
              ownerId: String,
              from: Instant,
              to: Instant,
          ): Flow<Result<List<PersonalEvent>>> =
              flowOf(Result.failure(EventsException(EventsError.PERMISSION_DENIED)))
        }

    val pendingSessionsRepo =
        object : GroupSessionsRepository by groupSessionsRepository {
          override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
              emptyFlow()
        }

    val testUseCase = GetUserScheduleUseCase(failingEventsRepo, pendingSessionsRepo, utcClock)
    val result = testUseCase(studentId, from, to).first()

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as? EventsException
    assertEquals(EventsError.PERMISSION_DENIED, exception?.error)
  }

  @Test
  fun reportsErrorImmediatelyWhenGroupSessionsFailsWhilePersonalEventsIsPending() = runTest {
    val pendingEventsRepo =
        object : EventsRepository by eventsRepository {
          override fun observeEvents(
              ownerId: String,
              from: Instant,
              to: Instant,
          ): Flow<Result<List<PersonalEvent>>> = emptyFlow()
        }

    val failingSessionsRepo =
        object : GroupSessionsRepository by groupSessionsRepository {
          override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
              flowOf(Result.failure(EventsException(EventsError.NOT_FOUND)))
        }

    val testUseCase = GetUserScheduleUseCase(pendingEventsRepo, failingSessionsRepo, utcClock)
    val result = testUseCase(studentId, from, to).first()

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as? EventsException
    assertEquals(EventsError.NOT_FOUND, exception?.error)
  }

  @Test
  fun eventsWithSameStartTimes_sortedByEndTimeAndThenId() = runTest {
    val eventLonger =
        PersonalEvent(
            id = "b-longer",
            ownerId = studentId,
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Longer",
                    startsAt = start1,
                    endsAt = end2,
                ),
            createdAt = start1,
        )

    val eventShorter =
        PersonalEvent(
            id = "a-shorter",
            ownerId = studentId,
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Shorter",
                    startsAt = start1,
                    endsAt = end1,
                ),
            createdAt = start1,
        )

    val eventZ =
        PersonalEvent(
            id = "z-id",
            ownerId = studentId,
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Z Subject",
                    startsAt = start1,
                    endsAt = end1,
                ),
            createdAt = start1,
        )

    val eventA =
        PersonalEvent(
            id = "a-id",
            ownerId = studentId,
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "A Subject",
                    startsAt = start1,
                    endsAt = end1,
                ),
            createdAt = start1,
        )

    eventsRepository.saveEvents(listOf(eventLonger, eventZ, eventA, eventShorter))

    val schedule = useCase(studentId, from, to).first().getOrThrow()
    assertEquals(4, schedule.size)

    assertEquals("a-id", schedule[0].id)
    assertEquals("a-shorter", schedule[1].id)
    assertEquals("z-id", schedule[2].id)
    assertEquals("b-longer", schedule[3].id)
  }

  @Test
  fun joinedGroupSessions_timeBoundariesAndCrossMidnightHandling() = runTest {
    val endingAtFromSession =
        GroupSession(
            id = "session-ending-at-from",
            createdBy = studentId,
            memberIds = listOf(studentId),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Ending At From",
                    startsAt = Instant.parse("2026-10-09T23:00:00Z"),
                    endsAt = from,
                ),
            createdAt = Instant.parse("2026-10-09T23:00:00Z"),
        )

    val startingAtToSession =
        GroupSession(
            id = "session-starting-at-to",
            createdBy = studentId,
            memberIds = listOf(studentId),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Starting At To",
                    startsAt = to,
                    endsAt = Instant.parse("2026-10-11T01:00:00Z"),
                ),
            createdAt = to,
        )

    val crossMidnightSession =
        GroupSession(
            id = "session-cross-midnight",
            createdBy = studentId,
            memberIds = listOf(studentId),
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Cross Midnight",
                    startsAt = Instant.parse("2026-10-10T23:00:00Z"),
                    endsAt = Instant.parse("2026-10-11T01:00:00Z"),
                ),
            createdAt = Instant.parse("2026-10-10T23:00:00Z"),
        )

    groupSessionsRepository.saveSession(studentId, endingAtFromSession)
    groupSessionsRepository.saveSession(studentId, startingAtToSession)
    groupSessionsRepository.saveSession(studentId, crossMidnightSession)

    val schedule = useCase(studentId, from, to).first().getOrThrow()

    assertEquals(1, schedule.size)
    assertEquals("session-cross-midnight", schedule[0].id)
  }

  @Test
  fun worksWithInjectedClockInDifferentTimeZones() = runTest {
    val tokyoZone = ZoneId.of("Asia/Tokyo")
    val tokyoClock = Clock.fixed(start1, tokyoZone)
    val tokyoUseCase = GetUserScheduleUseCase(eventsRepository, groupSessionsRepository, tokyoClock)

    val date = LocalDate.of(2026, 10, 10)
    val result = tokyoUseCase(studentId, date).first()

    assertTrue(result.isSuccess)
  }
}
