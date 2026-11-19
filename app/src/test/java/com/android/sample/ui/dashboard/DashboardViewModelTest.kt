// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.dashboard

import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.GetUserScheduleUseCase
import com.android.sample.model.event.GroupSession
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.event.PersonalEvent
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Unit tests for [DashboardViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")
  private val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), zone)
  private lateinit var authRepository: TestAuthRepository
  private lateinit var eventsRepository: EventsRepository
  private lateinit var groupSessionsRepository: GroupSessionsRepository
  private lateinit var getUserScheduleUseCase: GetUserScheduleUseCase

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = TestAuthRepository()
    eventsRepository = InMemoryEventsRepository()
    groupSessionsRepository = InMemoryGroupSessionsRepository()
    getUserScheduleUseCase =
        GetUserScheduleUseCase(
            eventsRepository = eventsRepository,
            groupSessionsRepository = groupSessionsRepository,
            clock = clock,
        )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(
      scheduleUseCase: GetUserScheduleUseCase = getUserScheduleUseCase,
      clockToUse: Clock = clock,
  ): DashboardViewModel =
      DashboardViewModel(
          authRepository = authRepository,
          getUserSchedule = scheduleUseCase,
          clock = clockToUse,
      )

  @Test
  fun signedOutUserHasEmptySessionsAndNotLoading() = runTest {
    authRepository.currentUser.value = null
    val viewModel = createViewModel()

    val state = viewModel.uiState.value
    assertTrue(state.sessions.isEmpty())
    assertTrue(state.events.isEmpty())
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun signedInUserLoadsTodayEventsAndMapsToDashboardSessions() = runTest {
    authRepository.currentUser.value = UserAccount("alice", "alice@epfl.ch", "Alice", null)

    val start1 = Instant.parse("2026-10-28T08:00:00Z") // 09:00 local in Zurich (UTC+1)
    val end1 = Instant.parse("2026-10-28T09:30:00Z") // 10:30 local in Zurich
    val personalEvent =
        PersonalEvent(
            id = "evt1",
            ownerId = "alice",
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Calculus",
                    startsAt = start1,
                    endsAt = end1,
                    location = "BC 01",
                ),
            createdAt = start1,
        )
    eventsRepository.saveEvent(personalEvent)

    val start2 = Instant.parse("2026-10-28T13:00:00Z") // 14:00 local in Zurich
    val end2 = Instant.parse("2026-10-28T14:30:00Z") // 15:30 local in Zurich
    val groupSession =
        GroupSession(
            id = "grp1",
            createdBy = "alice",
            memberIds = listOf("alice"),
            details =
                EventDetails(
                    type = EventType.EXERCISE_SESSION,
                    subject = "Physics",
                    startsAt = start2,
                    endsAt = end2,
                ),
            createdAt = start2,
        )
    groupSessionsRepository.saveSession("alice", groupSession)

    val viewModel = createViewModel()

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertNull(state.error)
    assertEquals(2, state.sessions.size)
    assertEquals(listOf(personalEvent, groupSession), state.events)

    assertEquals("09:00", state.sessions[0].startTime)
    assertEquals("10:30", state.sessions[0].endTime)
    assertEquals("09:00 - 10:30", state.sessions[0].timeRange)
    assertEquals("Calculus", state.sessions[0].subject)
    assertEquals("BC 01", state.sessions[0].location)
    assertEquals(EventType.STUDY_SESSION, state.sessions[0].eventType)

    assertEquals("14:00", state.sessions[1].startTime)
    assertEquals("15:30", state.sessions[1].endTime)
    assertEquals("14:00 - 15:30", state.sessions[1].timeRange)
    assertEquals("Physics", state.sessions[1].subject)
    assertNull(state.sessions[1].location)
    assertEquals(EventType.EXERCISE_SESSION, state.sessions[1].eventType)
  }

  @Test
  fun eventWithNullSubjectFallsBackToOther() = runTest {
    authRepository.currentUser.value = UserAccount("alice", "alice@epfl.ch", "Alice", null)

    val start = Instant.parse("2026-10-28T09:00:00Z")
    val end = Instant.parse("2026-10-28T10:00:00Z")
    val event =
        PersonalEvent(
            id = "evt_other",
            ownerId = "alice",
            details =
                EventDetails(
                    type = EventType.OTHER,
                    subject = null,
                    startsAt = start,
                    endsAt = end,
                ),
            createdAt = start,
        )
    eventsRepository.saveEvent(event)

    val viewModel = createViewModel()

    val session = viewModel.uiState.value.sessions.single()
    assertEquals("Other", session.subject)
  }

  @Test
  fun eventsOutsideTodayAreExcluded() = runTest {
    authRepository.currentUser.value = UserAccount("alice", "alice@epfl.ch", "Alice", null)

    val yesterday =
        PersonalEvent(
            id = "evt_yesterday",
            ownerId = "alice",
            details =
                EventDetails(
                    type = EventType.COURSE,
                    subject = "Yesterday Event",
                    startsAt = Instant.parse("2026-10-27T08:00:00Z"),
                    endsAt = Instant.parse("2026-10-27T09:00:00Z"),
                ),
            createdAt = Instant.parse("2026-10-27T08:00:00Z"),
        )
    eventsRepository.saveEvent(yesterday)

    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value.sessions.isEmpty())
  }

  @Test
  fun loadFailureSetsErrorState() = runTest {
    authRepository.currentUser.value = UserAccount("alice", "alice@epfl.ch", "Alice", null)

    val failingScheduleUseCase = mock<GetUserScheduleUseCase>()
    whenever(failingScheduleUseCase.invoke(any<String>(), any<LocalDate>()))
        .thenReturn(flowOf(Result.failure(RuntimeException("Firestore error"))))

    val viewModel = createViewModel(scheduleUseCase = failingScheduleUseCase)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(DashboardViewModelError.LOAD_SESSIONS_FAILED, state.error)
    assertTrue(state.sessions.isEmpty())
    assertTrue(state.events.isEmpty())
  }

  @Test
  fun exceptionInScheduleStreamSetsErrorState() = runTest {
    authRepository.currentUser.value = UserAccount("alice", "alice@epfl.ch", "Alice", null)

    val throwingScheduleUseCase = mock<GetUserScheduleUseCase>()
    whenever(throwingScheduleUseCase.invoke(any<String>(), any<LocalDate>()))
        .thenReturn(flow { throw RuntimeException("Stream crash") })

    val viewModel = createViewModel(scheduleUseCase = throwingScheduleUseCase)

    val state = viewModel.uiState.value
    assertFalse(state.isLoading)
    assertEquals(DashboardViewModelError.LOAD_SESSIONS_FAILED, state.error)
  }

  @Test
  fun retryRetriesObservationAfterFailure() = runTest {
    authRepository.currentUser.value = UserAccount("alice", "alice@epfl.ch", "Alice", null)

    var returnFailure = true
    val scheduleUseCase = mock<GetUserScheduleUseCase>()
    whenever(scheduleUseCase.invoke(any<String>(), any<LocalDate>())).thenAnswer {
      if (returnFailure) {
        flowOf(Result.failure<List<PersonalEvent>>(RuntimeException("Temporary error")))
      } else {
        flowOf(
            Result.success(
                listOf(
                    PersonalEvent(
                        id = "evt1",
                        ownerId = "alice",
                        details =
                            EventDetails(
                                type = EventType.STUDY_SESSION,
                                subject = "Calculus",
                                startsAt = Instant.parse("2026-10-28T08:00:00Z"),
                                endsAt = Instant.parse("2026-10-28T09:00:00Z"),
                            ),
                        createdAt = Instant.parse("2026-10-28T08:00:00Z"),
                    )
                )
            )
        )
      }
    }

    val viewModel = createViewModel(scheduleUseCase = scheduleUseCase)
    assertEquals(DashboardViewModelError.LOAD_SESSIONS_FAILED, viewModel.uiState.value.error)

    returnFailure = false
    viewModel.retry()

    assertNull(viewModel.uiState.value.error)
    assertEquals(1, viewModel.uiState.value.sessions.size)
    assertEquals("Calculus", viewModel.uiState.value.sessions.first().subject)
  }

  @Test
  fun userSignOutClearsSessions() = runTest {
    authRepository.currentUser.value = UserAccount("alice", "alice@epfl.ch", "Alice", null)
    val event =
        PersonalEvent(
            id = "evt1",
            ownerId = "alice",
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Calculus",
                    startsAt = Instant.parse("2026-10-28T08:00:00Z"),
                    endsAt = Instant.parse("2026-10-28T09:00:00Z"),
                ),
            createdAt = Instant.parse("2026-10-28T08:00:00Z"),
        )
    eventsRepository.saveEvent(event)

    val viewModel = createViewModel()
    assertEquals(1, viewModel.uiState.value.sessions.size)

    authRepository.signOut()

    val state = viewModel.uiState.value
    assertTrue(state.sessions.isEmpty())
    assertFalse(state.isLoading)
    assertNull(state.error)
  }

  @Test
  fun realTimeUpdatesReflectedInDashboard() = runTest {
    authRepository.currentUser.value = UserAccount("alice", "alice@epfl.ch", "Alice", null)
    val viewModel = createViewModel()

    assertTrue(viewModel.uiState.value.sessions.isEmpty())

    val newEvent =
        PersonalEvent(
            id = "evt1",
            ownerId = "alice",
            details =
                EventDetails(
                    type = EventType.STUDY_SESSION,
                    subject = "Algebra",
                    startsAt = Instant.parse("2026-10-28T08:00:00Z"),
                    endsAt = Instant.parse("2026-10-28T09:00:00Z"),
                ),
            createdAt = Instant.parse("2026-10-28T08:00:00Z"),
        )
    eventsRepository.saveEvent(newEvent)

    assertEquals(1, viewModel.uiState.value.sessions.size)
    assertEquals("Algebra", viewModel.uiState.value.sessions.first().subject)
  }

  @Test
  fun midnightDelayCalculatesTimeUntilNextMidnight() {
    val viewModel = createViewModel()
    val delay = viewModel.calculateMidnightDelay()
    assertEquals(Duration.ofHours(11).seconds, delay.inWholeSeconds)
  }

  @Test
  fun updateTodayReturnsCurrentDate() {
    val viewModel = createViewModel()
    val today = viewModel.updateToday()
    assertEquals(LocalDate.of(2026, 10, 28), today)
  }

  @Test
  fun factoryCreatesDashboardViewModel() {
    val factory = DashboardViewModelFactory(authRepository, getUserScheduleUseCase, clock)
    val vm = factory.create(DashboardViewModel::class.java)
    assertTrue(vm is DashboardViewModel)

    assertThrows(IllegalArgumentException::class.java) {
      factory.create(String::class.java as Any as Class<DashboardViewModel>)
    }
  }
}
