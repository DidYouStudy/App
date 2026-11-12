// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.calendar

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.model.event.PersonalEvent
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

/** Tests selected-week navigation, event observation, authentication, and load failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")
  private val clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), zone)
  private lateinit var authRepository: TestAuthRepository
  private lateinit var eventsRepository: RecordingEventsRepository

  /** Installs the test dispatcher and fresh fake repositories for each test. */
  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = TestAuthRepository()
    eventsRepository = RecordingEventsRepository()
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
    val viewModel = CalendarViewModel(authRepository, eventsRepository, mutableClock)
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

    val viewModel = CalendarViewModel(failingAuthRepository, eventsRepository, clock)

    assertTrue(viewModel.uiState.value.events.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(CalendarViewModelError.LOAD_EVENTS_FAILED, viewModel.uiState.value.error)
  }

  /** Rejects unsupported ViewModel classes with the requested class name in the message. */
  @Test
  fun factoryRejectsUnsupportedViewModelClass() {
    val factory = CalendarViewModelFactory(authRepository, eventsRepository, clock)

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

  /** Creates a ViewModel with the test repositories and fixed clock. */
  private fun createViewModel() = CalendarViewModel(authRepository, eventsRepository, clock)

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
