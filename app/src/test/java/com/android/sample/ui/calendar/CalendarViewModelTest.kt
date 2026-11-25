// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.calendar

import com.android.sample.model.auth.AuthRepository
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

/** Tests selected-week navigation, event observation, authentication, and load failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")
  private val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), zone)
  private lateinit var authRepository: TestAuthRepository
  private lateinit var eventsRepository: RecordingEventsRepository
  private lateinit var groupSessionsRepository: GroupSessionsRepository

  /** Installs the test dispatcher and fresh fake repositories for each test. */
  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = TestAuthRepository()
    eventsRepository = RecordingEventsRepository()
    groupSessionsRepository = InMemoryGroupSessionsRepository()
  }

  /** Restores the process main dispatcher after each test. */
  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** Loads the signed-in owner's events for the current Monday-to-Monday local-time range. */
  @Test
  fun signedInUserLoadsCurrentWeekEventsAndLocalDateBounds() = runTest {
    authRepository.currentUser.value = account("alice")
    val event = event("algebra", "alice", LocalDate.of(2026, 10, 28))
    eventsRepository.saveEvent(event)

    val viewModel = createViewModel()

    assertEquals(LocalDate.of(2026, 10, 26), viewModel.uiState.value.weekStart)
    assertEquals(LocalDate.of(2026, 10, 28), viewModel.uiState.value.today)
    assertEquals(listOf(event), viewModel.uiState.value.events)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    assertEquals("alice", eventsRepository.queries.single().ownerId)
    assertEquals(Instant.parse("2026-10-25T23:00:00Z"), eventsRepository.queries.single().from)
    assertEquals(Instant.parse("2026-11-01T23:00:00Z"), eventsRepository.queries.single().to)
  }

  /** Week navigation updates the selected dates and requests events for each new week. */
  @Test
  fun previousAndNextWeekUpdateStateAndEventQuery() = runTest {
    authRepository.currentUser.value = account("alice")
    val viewModel = createViewModel()

    viewModel.previousWeek()

    assertEquals(LocalDate.of(2026, 10, 19), viewModel.uiState.value.weekStart)
    assertEquals(
        CalendarDateHelper().weekDates(LocalDate.of(2026, 10, 19)),
        viewModel.uiState.value.weekDates,
    )
    assertEquals(Instant.parse("2026-10-18T22:00:00Z"), eventsRepository.queries.last().from)

    viewModel.nextWeek()

    assertEquals(LocalDate.of(2026, 10, 26), viewModel.uiState.value.weekStart)
    assertEquals(Instant.parse("2026-10-25T23:00:00Z"), eventsRepository.queries.last().from)
    assertEquals(listOf("alice", "alice", "alice"), eventsRepository.queries.map { it.ownerId })
  }

  /** A week change clears the previous week's events and shows loading until the new week loads. */
  @Test
  fun weekChangeShowsLoadingUntilNewWeekLoads() = runTest {
    authRepository.currentUser.value = account("alice")
    eventsRepository.saveEvent(event("algebra", "alice", LocalDate.of(2026, 10, 28))).getOrThrow()
    val nextWeekSessions = MutableStateFlow<Result<List<GroupSession>>?>(null)
    groupSessionsRepository =
        mock<GroupSessionsRepository>().also {
          whenever(it.observeSessions(any()))
              .thenReturn(flowOf(Result.success(emptyList())), nextWeekSessions.filterNotNull())
        }
    val viewModel = createViewModel()
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(1, viewModel.uiState.value.events.size)

    viewModel.nextWeek()

    assertTrue(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.events.isEmpty())
    assertNull(viewModel.uiState.value.error)

    nextWeekSessions.value = Result.success(emptyList())

    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  /** Today navigation selects the week containing today from the injected clock. */
  @Test
  fun navigateToTodayUsesInjectedClock() = runTest {
    authRepository.currentUser.value = account("alice")
    val viewModel = createViewModel()
    viewModel.nextWeek()

    viewModel.navigateToToday()

    assertEquals(LocalDate.of(2026, 10, 26), viewModel.uiState.value.weekStart)
    assertEquals(LocalDate.of(2026, 10, 28), viewModel.uiState.value.today)
    assertEquals(Instant.parse("2026-10-25T23:00:00Z"), eventsRepository.queries.last().from)
  }

  /** Updating today after midnight preserves a future week and does not reload its events. */
  @Test
  fun updateTodayAfterMidnightKeepsSelectedWeekWithoutReloadingEvents() = runTest {
    authRepository.currentUser.value = account("alice")
    val mutableClock = MutableClock(Instant.parse("2026-10-28T22:59:00Z"), zone)
    val viewModel = CalendarViewModel(authRepository, scheduleUseCase(), mutableClock)
    assertEquals(LocalDate.of(2026, 10, 28), viewModel.uiState.value.today)

    repeat(3) { viewModel.nextWeek() }

    val selectedWeek = LocalDate.of(2026, 11, 16)
    assertEquals(selectedWeek, viewModel.uiState.value.weekStart)
    val queriesBeforeDateUpdate = eventsRepository.queries.toList()

    mutableClock.currentInstant = Instant.parse("2026-10-28T23:01:00Z")
    viewModel.updateToday()

    assertEquals(LocalDate.of(2026, 10, 29), LocalDate.now(mutableClock))
    assertEquals(LocalDate.of(2026, 10, 29), viewModel.uiState.value.today)
    assertEquals(selectedWeek, viewModel.uiState.value.weekStart)
    assertEquals(queriesBeforeDateUpdate, eventsRepository.queries)
  }

  /** Previous and next week navigation preserve correct dates across a year boundary. */
  @Test
  fun weekNavigationCrossesYearBoundary() = runTest {
    authRepository.currentUser.value = account("alice")
    val viewModel = createViewModel()

    repeat(10) { viewModel.nextWeek() }
    assertEquals(LocalDate.of(2027, 1, 4), viewModel.uiState.value.weekStart)

    viewModel.previousWeek()
    assertEquals(LocalDate.of(2026, 12, 28), viewModel.uiState.value.weekStart)
    assertEquals(
        CalendarDateHelper().weekDates(LocalDate.of(2026, 12, 28)),
        viewModel.uiState.value.weekDates,
    )

    viewModel.nextWeek()
    viewModel.nextWeek()
    assertEquals(LocalDate.of(2027, 1, 11), viewModel.uiState.value.weekStart)
    assertEquals(
        CalendarDateHelper().weekDates(LocalDate.of(2027, 1, 11)),
        viewModel.uiState.value.weekDates,
    )
  }

  /** Signing out clears the previous user's events; signing in observes only the new owner. */
  @Test
  fun authenticationChangesReplaceTheObservedOwnerAndEvents() = runTest {
    val aliceEvent = event("alice-event", "alice", LocalDate.of(2026, 10, 28))
    val bobEvent = event("bob-event", "bob", LocalDate.of(2026, 10, 28))
    eventsRepository.saveEvent(aliceEvent)
    eventsRepository.saveEvent(bobEvent)
    authRepository.currentUser.value = account("alice")
    val viewModel = createViewModel()

    assertEquals(listOf(aliceEvent), viewModel.uiState.value.events)
    authRepository.currentUser.value = null
    assertTrue(viewModel.uiState.value.events.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)
    authRepository.currentUser.value = account("bob")
    assertEquals(listOf(bobEvent), viewModel.uiState.value.events)
    assertEquals(listOf("alice", "bob"), eventsRepository.queries.map { it.ownerId })
  }

  /** Converts a repository failure into a stable calendar load error. */
  @Test
  fun repositoryFailureSetsLoadErrorAndStopsLoading() = runTest {
    authRepository.currentUser.value = account("alice")
    eventsRepository.failure = IllegalStateException("offline")

    val viewModel = createViewModel()

    assertTrue(viewModel.uiState.value.events.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)
  }

  /**
   * Converts an exception thrown while collecting the repository event stream into a load error.
   */
  @Test
  fun repositoryObservationExceptionSetsLoadError() = runTest {
    authRepository.currentUser.value = account("alice")
    eventsRepository.throwOnObserve = true

    val viewModel = createViewModel()

    assertTrue(viewModel.uiState.value.events.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)
  }

  /** Converts a failure from the authentication session stream into a load error. */
  @Test
  fun authenticationStreamExceptionSetsLoadError() = runTest {
    val failingAuthRepository = FailingAuthRepository()

    val viewModel = CalendarViewModel(failingAuthRepository, scheduleUseCase(), clock)

    assertTrue(viewModel.uiState.value.events.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)
  }

  /** Rejects unsupported ViewModel classes with the requested class name in the message. */
  @Test
  fun factoryRejectsUnsupportedViewModelClass() {
    val factory = CalendarViewModelFactory(authRepository, scheduleUseCase(), clock)

    val exception =
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(androidx.lifecycle.ViewModel::class.java)
        }

    assertEquals(
        "Unsupported ViewModel: androidx.lifecycle.ViewModel",
        exception.message,
    )
  }

  /** Retry reobserves the same owner and week, then clears the error when events load. */
  @Test
  fun retryAfterFailureReloadsEventsForCurrentWeek() = runTest {
    authRepository.currentUser.value = account("alice")
    val event = event("algebra", "alice", LocalDate.of(2026, 10, 28))
    eventsRepository.saveEvent(event)
    eventsRepository.failure = IllegalStateException("offline")
    val viewModel = createViewModel()

    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)
    val initialQuery = eventsRepository.queries.single()

    eventsRepository.failure = null
    viewModel.retryEvents()

    assertEquals(listOf(event), viewModel.uiState.value.events)
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    assertEquals(2, eventsRepository.queries.size)
    assertEquals(initialQuery, eventsRepository.queries.last())
  }

  /** Waits for both schedule sources and retries the same week if the session source fails. */
  @Test
  fun pendingGroupSourceCanFailAndRecoverOnRetry() = runTest {
    authRepository.currentUser.value = account("alice")
    val personalEvent = event("algebra", "alice", LocalDate.of(2026, 10, 28))
    eventsRepository.saveEvent(personalEvent).getOrThrow()
    val groupResults = MutableStateFlow<Result<List<GroupSession>>?>(null)
    groupSessionsRepository =
        mock<GroupSessionsRepository>().also {
          whenever(it.observeSessions(any()))
              .thenReturn(groupResults.filterNotNull(), flowOf(Result.success(emptyList())))
        }

    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.events.isEmpty())
    assertNull(viewModel.uiState.value.error)

    groupResults.value = Result.failure(IllegalStateException("Group sessions unavailable"))
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)
    val initialQuery = eventsRepository.queries.single()

    viewModel.retryEvents()
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf(personalEvent), viewModel.uiState.value.events)
    assertEquals(listOf(initialQuery, initialQuery), eventsRepository.queries)
  }

  /** R3: a retry on its own keeps the error visible while the same week reloads. */
  @Test
  fun retryAloneKeepsErrorVisibleWhileReloading() = runTest {
    authRepository.currentUser.value = account("alice")
    val retrySessions = MutableStateFlow<Result<List<GroupSession>>?>(null)
    groupSessionsRepository =
        mock<GroupSessionsRepository>().also {
          whenever(it.observeSessions(any()))
              .thenReturn(
                  flowOf(Result.failure(IllegalStateException("Group sessions unavailable"))),
                  retrySessions.filterNotNull(),
              )
        }
    val viewModel = createViewModel()
    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)

    viewModel.retryEvents()

    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)
  }

  /** R1: a retry that lands in the same update as a week change loads the new week fresh. */
  @Test
  fun retryTogetherWithWeekChangeLoadsNewWeekFresh() = runTest {
    // A standard dispatcher lets the retry and the week change reach the ViewModel together.
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    authRepository.currentUser.value = account("alice")
    val nextWeekSessions = MutableStateFlow<Result<List<GroupSession>>?>(null)
    groupSessionsRepository =
        mock<GroupSessionsRepository>().also {
          whenever(it.observeSessions(any()))
              .thenReturn(
                  flowOf(Result.failure(IllegalStateException("Group sessions unavailable"))),
                  nextWeekSessions.filterNotNull(),
              )
        }
    val viewModel = createViewModel()
    advanceUntilIdle()
    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)

    viewModel.retryEvents()
    viewModel.nextWeek()
    advanceUntilIdle()

    assertEquals(LocalDate.of(2026, 11, 2), viewModel.uiState.value.weekStart)
    assertTrue(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.events.isEmpty())
  }

  /** R2: a retry that lands in the same update as an account change drops the old user's events. */
  @Test
  fun retryTogetherWithAccountChangeClearsPreviousUserEvents() = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    authRepository.currentUser.value = account("alice")
    eventsRepository.saveEvent(event("algebra", "alice", LocalDate.of(2026, 10, 28))).getOrThrow()
    val bobSessions = MutableStateFlow<Result<List<GroupSession>>?>(null)
    groupSessionsRepository =
        mock<GroupSessionsRepository>().also {
          whenever(it.observeSessions(any()))
              .thenReturn(flowOf(Result.success(emptyList())), bobSessions.filterNotNull())
        }
    val viewModel = createViewModel()
    advanceUntilIdle()
    assertEquals(1, viewModel.uiState.value.events.size)

    viewModel.retryEvents()
    authRepository.currentUser.value = account("bob")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.events.isEmpty())
    assertNull(viewModel.uiState.value.error)
  }

  /** Creates the shared schedule use case over deterministic test repositories. */
  private fun scheduleUseCase() = GetUserScheduleUseCase(eventsRepository, groupSessionsRepository)

  /** Creates a ViewModel with the test schedule and fixed clock. */
  private fun createViewModel() = CalendarViewModel(authRepository, scheduleUseCase(), clock)

  /** Creates a minimal authenticated account for observation tests. */
  private fun account(uid: String) = UserAccount(uid, null, null, null)

  /** Creates a one-hour course event on [date] in the test time zone. */
  private fun event(id: String, ownerId: String, date: LocalDate): PersonalEvent {
    val startsAt = date.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()
    val endsAt = date.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant()
    return PersonalEvent(
        id = id,
        ownerId = ownerId,
        details =
            EventDetails(
                type = EventType.COURSE,
                subject = "Algebra",
                startsAt = startsAt,
                endsAt = endsAt,
            ),
        createdAt = startsAt,
    )
  }

  /** A test clock whose instant can be advanced across midnight. */
  private class MutableClock(var currentInstant: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant
  }

  /** Records each range request while delegating event storage and observation to memory. */
  private class RecordingEventsRepository(
      private val delegate: InMemoryEventsRepository = InMemoryEventsRepository()
  ) : EventsRepository by delegate {
    data class Query(val ownerId: String, val from: Instant, val to: Instant)

    val queries = mutableListOf<Query>()
    var failure: Throwable? = null
    var throwOnObserve: Boolean = false

    override fun observeEvents(
        ownerId: String,
        from: Instant,
        to: Instant,
    ): Flow<Result<List<PersonalEvent>>> {
      queries += Query(ownerId, from, to)
      if (throwOnObserve) return flow { throw IllegalStateException("observation failed") }
      val observationFailure = failure
      return if (observationFailure == null) {
        delegate.observeEvents(ownerId, from, to)
      } else {
        flowOf(Result.failure(observationFailure))
      }
    }
  }

  /** Supplies a session flow that fails immediately to exercise the outer observation handler. */
  private class FailingAuthRepository : AuthRepository {
    override val isAuthenticated: Boolean
      get() = false

    override val currentUserId: String?
      get() = null

    override val currentUser: Flow<UserAccount?> = flow {
      throw IllegalStateException("auth failed")
    }

    override suspend fun signInWithGoogle(idToken: String): UserAccount =
        error("Sign-in is not used in this test")

    override suspend fun signOut() = Unit
  }
}
