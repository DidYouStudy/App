// Co-authored-by: GitHub Copilot
package com.android.sample.ui.eventplanning

import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/** Shared fixtures and lifecycle setup for the event-planning ViewModel unit tests. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class EventPlanningViewModelTestBase {
  protected val testDispatcher = UnconfinedTestDispatcher()
  protected val currentUserFlow = MutableStateFlow<UserAccount?>(null)
  protected lateinit var authRepository: AuthRepository
  protected lateinit var eventsRepository: EventsRepository

  protected val zone = ZoneId.of("Europe/Berlin")
  protected val now = Instant.parse("2026-10-27T12:00:00Z")
  protected val clock = Clock.fixed(now, zone)
  protected val date = LocalDate.of(2026, 10, 27)
  protected val startTime = LocalTime.of(9, 0)
  protected val endTime = LocalTime.of(11, 0)
  protected val startsAt = Instant.parse("2026-10-27T08:00:00Z")
  protected val endsAt = Instant.parse("2026-10-27T10:00:00Z")
  protected val tasks = listOf(EventTask("Solve exercises 12, 13"))

  /** Installs the test dispatcher used by ViewModel's viewModelScope. */
  @Before
  fun setMainDispatcher() {
    Dispatchers.setMain(testDispatcher)
  }

  /** Restores the process main dispatcher after each test. */
  @After
  fun resetMainDispatcher() {
    Dispatchers.resetMain()
  }

  /** Creates an account with no optional profile fields. */
  protected fun account(uid: String) =
      UserAccount(uid = uid, email = null, displayName = null, photoUrl = null)

  /** Creates a ViewModel backed by the repositories configured by the test. */
  protected fun createViewModel() = EventPlanningViewModel(authRepository, eventsRepository, clock)

  /** Fills a valid study-session form using the shared Berlin date and time fixtures. */
  protected fun EventPlanningViewModel.fillValidForm(
      date: LocalDate = this@EventPlanningViewModelTestBase.date,
      start: LocalTime = this@EventPlanningViewModelTestBase.startTime,
      end: LocalTime = this@EventPlanningViewModelTestBase.endTime,
  ) {
    setType(EventType.STUDY_SESSION)
    setSubject("Algebra")
    setDate(date)
    setStartTime(start)
    setEndTime(end)
  }

  /** Returns the events passed to saveEvent after verifying the expected number of saves. */
  protected suspend fun savedEvents(count: Int = 1): List<Event> {
    val captor = argumentCaptor<Event>()
    verify(eventsRepository, times(count)).saveEvent(captor.capture())
    return captor.allValues
  }

  /** Verifies that no event was passed to saveEvent. */
  protected suspend fun assertNothingSaved() {
    verify(eventsRepository, never()).saveEvent(any())
  }
}
