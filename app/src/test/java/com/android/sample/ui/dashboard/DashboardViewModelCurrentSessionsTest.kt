// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.dashboard

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
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
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.UserPreferences
import com.android.sample.model.session.RingMode
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests current-session projection, preference loading, and minute refreshes. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelCurrentSessionsTest {
  private val dispatcher = UnconfinedTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")
  private val start = Instant.parse("2026-10-28T13:00:00Z")
  private val end = Instant.parse("2026-10-28T14:00:00Z")
  private lateinit var clock: MutableClock
  private lateinit var authRepository: TestAuthRepository
  private lateinit var eventsRepository: EventsRepository
  private lateinit var groupSessionsRepository: GroupSessionsRepository
  private lateinit var preferencesRepository: RecordingPreferencesRepository
  private lateinit var ticks: MutableSharedFlow<Unit>
  private lateinit var schedule: GetUserScheduleUseCase

  /** Installs deterministic dispatchers and in-memory repositories. */
  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    clock = MutableClock(start.plusSeconds(30 * 60), zone)
    authRepository = TestAuthRepository()
    eventsRepository = InMemoryEventsRepository()
    groupSessionsRepository = InMemoryGroupSessionsRepository()
    preferencesRepository = RecordingPreferencesRepository()
    ticks = MutableSharedFlow()
    schedule = GetUserScheduleUseCase(eventsRepository, groupSessionsRepository, clock)
  }

  /** Restores the process dispatcher after each test. */
  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** No schedule entries produce an empty current-session list. */
  @Test
  fun noEventHasNoCurrentSessions() = runTest {
    signIn()
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value.currentSessions.isEmpty())
  }

  /** A personal study session exposes its computed ring. */
  @Test
  fun personalSessionExposesRing() = runTest {
    signIn()
    val event = personal("personal", start, end)
    eventsRepository.saveEvent(event)
    val viewModel = createViewModel()
    val current = viewModel.uiState.value.currentSessions.single()
    assertEquals("personal", current.session.id)
    assertEquals(RingMode.STUDYING, current.ring.mode)
    assertEquals(0.6667f, current.ring.progress, 1e-4f)
    assertEquals(java.time.Duration.ofMinutes(15), current.ring.segmentRemaining)
  }

  /** Session start is inclusive and session end is exclusive. */
  @Test
  fun boundariesDetermineCurrentness() = runTest {
    signIn()
    val event = personal("boundary", start, end)
    eventsRepository.saveEvent(event)
    clock.currentInstant = start
    val atStart = createViewModel()
    assertEquals(1, atStart.uiState.value.currentSessions.size)
    clock.currentInstant = end
    ticks.emit(Unit)
    assertTrue(atStart.uiState.value.currentSessions.isEmpty())
  }

  /** A session becomes current after a minute tick without retrying the schedule. */
  @Test
  fun tickRecomputesWhenSessionStarts() = runTest {
    signIn()
    val eventStart = end
    val eventEnd = end.plusSeconds(60 * 60)
    eventsRepository.saveEvent(personal("later", eventStart, eventEnd))
    clock.currentInstant = end.minusSeconds(60)
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value.currentSessions.isEmpty())
    clock.currentInstant = end
    ticks.emit(Unit)
    assertEquals(listOf("later"), viewModel.uiState.value.currentSessions.map { it.session.id })
  }

  /** A minute tick removes a session at its exclusive end. */
  @Test
  fun tickRecomputesWhenSessionEnds() = runTest {
    signIn()
    eventsRepository.saveEvent(personal("ending", start, end))
    val viewModel = createViewModel()
    assertEquals(1, viewModel.uiState.value.currentSessions.size)
    clock.currentInstant = end
    ticks.emit(Unit)
    assertTrue(viewModel.uiState.value.currentSessions.isEmpty())
  }

  /** Moving the injected clock alone does not refresh current sessions. */
  @Test
  fun clockMoveWithoutTickDoesNotRefresh() = runTest {
    signIn()
    eventsRepository.saveEvent(personal("waiting", end, end.plusSeconds(3600)))
    clock.currentInstant = end.minusSeconds(60)
    val viewModel = createViewModel()
    clock.currentInstant = end
    assertTrue(viewModel.uiState.value.currentSessions.isEmpty())
  }

  /** Overlapping personal and joined group sessions retain schedule order. */
  @Test
  fun overlappingSessionsBothAppearInScheduleOrder() = runTest {
    signIn()
    eventsRepository.saveEvent(personal("personal", start, end))
    val group = group("group", start.plusSeconds(30 * 60), end.plusSeconds(30 * 60))
    groupSessionsRepository.saveSession("alice", group)
    val viewModel = createViewModel()
    assertEquals(
        listOf("personal", "group"),
        viewModel.uiState.value.currentSessions.map { it.session.id },
    )
    assertEquals(RingMode.BREAK, viewModel.uiState.value.currentSessions[0].ring.mode)
    assertEquals(RingMode.STUDYING, viewModel.uiState.value.currentSessions[1].ring.mode)
    assertEquals(0.3333f, viewModel.uiState.value.currentSessions[1].ring.progress, 1e-4f)
  }

  /** Non-study event types are also projected into current sessions. */
  @Test
  fun courseEventIsIncluded() = runTest {
    signIn()
    val event = personal("course", start, end, EventType.COURSE)
    eventsRepository.saveEvent(event)
    val viewModel = createViewModel()
    assertEquals(
        EventType.COURSE,
        viewModel.uiState.value.currentSessions.single().session.eventType,
    )
  }

  /** Ring computation truncates the current instant to the minute. */
  @Test
  fun currentInstantIsTruncatedToMinute() = runTest {
    signIn()
    eventsRepository.saveEvent(personal("fraction", start, end))
    clock.currentInstant = start.plusSeconds(30 * 60 + 45)
    val viewModel = createViewModel()
    assertEquals(
        java.time.Duration.ofMinutes(15),
        viewModel.uiState.value.currentSessions.single().ring.segmentRemaining,
    )
  }

  /** Missing preferences use the documented defaults. */
  @Test
  fun nullPreferencesUseDefaults() = runTest {
    signIn()
    preferencesRepository.result = Result.success(null)
    eventsRepository.saveEvent(personal("default", start, end))
    clock.currentInstant = start.plusSeconds(45 * 60)
    val viewModel = createViewModel()
    assertEquals(RingMode.BREAK, viewModel.uiState.value.currentSessions.single().ring.mode)
  }

  /** Saved preferences determine the break schedule. */
  @Test
  fun savedPreferencesAreApplied() = runTest {
    signIn()
    preferencesRepository.result =
        Result.success(UserPreferences("alice", breakFrequencyMinutes = 30, breakLengthMinutes = 5))
    eventsRepository.saveEvent(personal("custom", start, end))
    val viewModel = createViewModel()
    assertEquals(RingMode.BREAK, viewModel.uiState.value.currentSessions.single().ring.mode)
  }

  /** Preference failures fall back without changing the dashboard error. */
  @Test
  fun preferenceFailureUsesDefaultsWithoutDashboardError() = runTest {
    signIn()
    preferencesRepository.result = Result.failure(IllegalStateException("offline"))
    eventsRepository.saveEvent(personal("fallback", start, end))
    clock.currentInstant = start.plusSeconds(45 * 60)
    val viewModel = createViewModel()
    assertEquals(RingMode.BREAK, viewModel.uiState.value.currentSessions.single().ring.mode)
    assertNull(viewModel.uiState.value.error)
  }

  /** Preferences load once, reload on retry, and reload for a changed user. */
  @Test
  fun preferencesReloadOnRetryAndUserChange() = runTest {
    signIn()
    val viewModel = createViewModel()
    repeat(3) { ticks.emit(Unit) }
    assertEquals(listOf("alice"), preferencesRepository.userIds)
    viewModel.retry()
    assertEquals(listOf("alice", "alice"), preferencesRepository.userIds)
    authRepository.currentUser.value = UserAccount("bob", null, null, null)
    assertEquals(listOf("alice", "alice", "bob"), preferencesRepository.userIds)
  }

  /** Signing out clears current sessions without reading preferences. */
  @Test
  fun signedOutHasNoCurrentSessionsAndDoesNotReadPreferences() = runTest {
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value.currentSessions.isEmpty())
    assertTrue(preferencesRepository.userIds.isEmpty())
  }

  /** Schedule failure clears current sessions and reports the existing load error. */
  @Test
  fun scheduleFailureClearsCurrentSessionsAndSetsError() = runTest {
    signIn()
    val failingSchedule =
        GetUserScheduleUseCase(
            eventsRepository = FailingEventsRepository(),
            groupSessionsRepository = groupSessionsRepository,
            clock = clock,
        )
    val viewModel = createViewModel(failingSchedule)
    assertTrue(viewModel.uiState.value.currentSessions.isEmpty())
    assertEquals(DashboardViewModelError.LOAD_SESSIONS_FAILED, viewModel.uiState.value.error)
  }

  /** A live schedule update adds a covering event without a minute tick. */
  @Test
  fun eventSavedLaterAppearsWithoutTick() = runTest {
    signIn()
    val viewModel = createViewModel()
    assertTrue(viewModel.uiState.value.currentSessions.isEmpty())
    eventsRepository.saveEvent(personal("later", start, end))
    assertEquals(listOf("later"), viewModel.uiState.value.currentSessions.map { it.session.id })
  }

  /** Updating the dashboard date does not stop minute refreshes. */
  @Test
  fun updateTodayDoesNotStopTicks() = runTest {
    signIn()
    eventsRepository.saveEvent(personal("tick", start, end))
    val viewModel = createViewModel()
    clock.currentInstant = end
    viewModel.updateToday()
    ticks.emit(Unit)
    assertTrue(viewModel.uiState.value.currentSessions.isEmpty())
  }

  /** Clearing a provider-owned ViewModel cancels its minute-tick subscription. */
  @Test
  fun tickerStopsWhenViewModelIsCleared() = runTest {
    signIn()
    val store = ViewModelStore()
    val factory =
        DashboardViewModelFactory(
            authRepository,
            schedule,
            preferencesRepository,
            clock,
            ticks,
        )
    ViewModelProvider(store, factory)[DashboardViewModel::class.java]
    assertEquals(1, ticks.subscriptionCount.value)
    store.clear()
    assertEquals(0, ticks.subscriptionCount.value)
  }

  /** The factory accepts the preferences repository dependency. */
  @Test
  fun factoryCreatesDashboardViewModelWithPreferencesRepository() {
    val factory =
        DashboardViewModelFactory(
            authRepository,
            schedule,
            preferencesRepository,
            clock,
            ticks,
        )
    assertTrue(factory.create(DashboardViewModel::class.java) is DashboardViewModel)
  }

  private fun createViewModel(
      scheduleUseCase: GetUserScheduleUseCase = schedule,
  ): DashboardViewModel =
      DashboardViewModel(
          authRepository,
          scheduleUseCase,
          preferencesRepository,
          clock,
          ticks,
      )

  private fun signIn() {
    authRepository.currentUser.value = UserAccount("alice", null, null, null)
  }

  private fun personal(
      id: String,
      startsAt: Instant,
      endsAt: Instant,
      type: EventType = EventType.STUDY_SESSION,
  ) =
      PersonalEvent(
          id,
          "alice",
          EventDetails(type, "Subject", startsAt, endsAt),
          startsAt,
      )

  private fun group(id: String, startsAt: Instant, endsAt: Instant) =
      GroupSession(
          id,
          "alice",
          listOf("alice"),
          EventDetails(EventType.STUDY_SESSION, "Group", startsAt, endsAt),
          startsAt,
      )

  /** A mutable clock copied from the calendar tests. */
  private class MutableClock(var currentInstant: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant
  }

  private class RecordingPreferencesRepository : PreferencesRepository {
    var result: Result<UserPreferences?> = Result.success(null)
    val userIds = mutableListOf<String>()

    override suspend fun getPreferences(userId: String): Result<UserPreferences?> {
      userIds += userId
      return result
    }

    override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> =
        Result.success(Unit)
  }

  private class FailingEventsRepository : EventsRepository by InMemoryEventsRepository() {
    override fun observeEvents(
        ownerId: String,
        from: Instant,
        to: Instant,
    ): Flow<Result<List<PersonalEvent>>> =
        kotlinx.coroutines.flow.flowOf(Result.failure(IllegalStateException("offline")))

    override suspend fun saveEvent(event: PersonalEvent): Result<Unit> = Result.success(Unit)
  }
}
