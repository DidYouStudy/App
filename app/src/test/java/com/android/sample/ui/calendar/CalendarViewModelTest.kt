// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import androidx.lifecycle.SavedStateHandle
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.InMemoryEventsRepository
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests selected-week event observation, saved state, authentication, and load failures. */
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
    assertEquals(Instant.parse("2026-10-25T22:00:00Z"), eventsRepository.queries.single().from)
    assertEquals(Instant.parse("2026-11-01T23:00:00Z"), eventsRepository.queries.single().to)
  }

  /** The saved week is restored and the observation restarts for that week. */
  @Test
  fun restoresSelectedWeekFromSavedState() = runTest {
    authRepository.currentUser.value = account("alice")
    val savedStateHandle = SavedStateHandle(mapOf("calendar_week_start" to "2026-11-02"))

    val viewModel = createViewModel(savedStateHandle)

    assertEquals(LocalDate.of(2026, 11, 2), viewModel.uiState.value.weekStart)
    assertEquals(Instant.parse("2026-11-01T23:00:00Z"), eventsRepository.queries.single().from)
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

  /** Creates a ViewModel using the supplied saved-state handle and fixed clock. */
  private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
      CalendarViewModel(authRepository, eventsRepository, savedStateHandle, clock)

  /** Creates a minimal authenticated account for observation tests. */
  private fun account(uid: String) = UserAccount(uid, null, null, null)

  /** Creates a one-hour course event on [date] in the test time zone. */
  private fun event(id: String, ownerId: String, date: LocalDate): Event {
    val startsAt = date.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant()
    val endsAt = date.atTime(LocalTime.of(10, 0)).atZone(zone).toInstant()
    return Event(
        id = id,
        ownerId = ownerId,
        type = EventType.COURSE,
        subject = "Algebra",
        startsAt = startsAt,
        endsAt = endsAt,
        createdAt = startsAt,
    )
  }

  /** Records each range request while delegating event storage and observation to memory. */
  private class RecordingEventsRepository(
      private val delegate: InMemoryEventsRepository = InMemoryEventsRepository()
  ) : EventsRepository by delegate {
    data class Query(val ownerId: String, val from: Instant, val to: Instant)

    val queries = mutableListOf<Query>()
    var failure: Throwable? = null

    override fun observeEvents(
        ownerId: String,
        from: Instant,
        to: Instant,
    ): Flow<Result<List<Event>>> {
      queries += Query(ownerId, from, to)
      val observationFailure = failure
      return if (observationFailure == null) {
        delegate.observeEvents(ownerId, from, to)
      } else {
        flowOf(Result.failure(observationFailure))
      }
    }
  }
}
