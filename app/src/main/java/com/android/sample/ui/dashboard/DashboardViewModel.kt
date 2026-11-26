// Co-authored-by: Gemini <noreply@google.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.Event
import com.android.sample.model.event.GetUserScheduleUseCase
import com.android.sample.model.event.GroupSession
import com.android.sample.model.preferences.PreferenceDefaults
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.session.RingState
import com.android.sample.model.session.StudyRingCalculator
import com.android.sample.model.session.minuteTicks
import java.time.Clock
import java.time.Duration as JavaDuration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Categorized failures that the dashboard screen can map to localized messages. */
enum class DashboardViewModelError {
  /** The study session stream could not be observed. */
  LOAD_SESSIONS_FAILED,
}

/**
 * State holding today's study sessions, loading indicator, and any load error.
 *
 * @property currentSessions sessions covering the current minute with their ring states.
 */
data class DashboardUiState(
    val sessions: List<DashboardSession> = emptyList(),
    val currentSessions: List<CurrentSession> = emptyList(),
    val isLoading: Boolean = true,
    val error: DashboardViewModelError? = null,
    val midnightDelay: Duration = 0.toDuration(DurationUnit.SECONDS),
)

/**
 * A dashboard session currently covering the injected clock time and its ring state.
 *
 * @property session dashboard display data for the current session.
 * @property ring computed study or break ring state.
 */
data class CurrentSession(val session: DashboardSession, val ring: RingState)

/**
 * Owns dashboard data loading and observes the signed-in user's study sessions for today.
 *
 * Combines personal events and joined group sessions through [GetUserScheduleUseCase] for the
 * currently signed-in student and today's date.
 *
 * @property authRepository source for the current authenticated user.
 * @property getUserSchedule use case combining personal events and joined group sessions.
 * @property preferencesRepository source for break settings, read in parallel with the schedule;
 *   documented defaults are used until preferences arrive.
 * @property clock provider for current time and time zone calculations.
 * @property minuteTicks emits at each minute boundary; injected for tests.
 */
class DashboardViewModel(
    private val authRepository: AuthRepository,
    private val getUserSchedule: GetUserScheduleUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val minuteTicks: kotlinx.coroutines.flow.Flow<Unit> = minuteTicks(clock),
) : ViewModel() {
  private val currentDate = MutableStateFlow(LocalDate.now(clock))
  private val retryRequest = MutableStateFlow(0L)
  private var latestEvents: List<Event> = emptyList()
  private var breakFrequency = defaultBreakSettings().first
  private var breakLength = defaultBreakSettings().second
  private var observedStudentId: String? = null

  private val _uiState =
      MutableStateFlow(
          DashboardUiState(
              midnightDelay = calculateMidnightDelay(),
          )
      )

  /** The observed sessions, loading state, and any error for the dashboard. */
  val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

  /** Retries observing study sessions for today. */
  fun retry() {
    retryRequest.update { it + 1 }
  }

  /** Updates today's date if midnight has passed. */
  fun updateToday(): LocalDate {
    val today = LocalDate.now(clock)
    currentDate.update { today }
    return today
  }

  /** Calculates the duration until the next midnight according to [clock]. */
  fun calculateMidnightDelay(): Duration {
    val now = LocalDateTime.now(clock)
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
    return java.time.Duration.between(now, nextMidnight).toKotlinDuration()
  }

  init {
    viewModelScope.launch {
      var lastHandledRetry = retryRequest.value
      try {
        combine(
                authRepository.currentUser,
                currentDate,
                retryRequest,
            ) { user, date, retryCount ->
              Triple(user, date, retryCount)
            }
            .collectLatest { (user, date, retryCount) ->
              val isRetry = retryCount != lastHandledRetry
              lastHandledRetry = retryCount
              observeDate(user?.uid, date, isRetry)
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (_: Exception) {
        showLoadError()
      }
    }
    viewModelScope.launch { minuteTicks.collect { updateCurrentSessions() } }
  }

  /** Resets state or preserves error state during retry, then observes today's sessions. */
  private suspend fun observeDate(
      studentId: String?,
      date: LocalDate,
      isRetry: Boolean,
  ) {
    if (studentId.isNullOrBlank()) {
      clearEvents()
      observedStudentId = null
      _uiState.value =
          DashboardUiState(
              sessions = emptyList(),
              currentSessions = emptyList(),
              isLoading = false,
              error = null,
              midnightDelay = calculateMidnightDelay(),
          )
      return
    }

    if (studentId != observedStudentId) {
      clearEvents()
      observedStudentId = studentId
      resetBreakSettings()
    }
    if (isRetry) {
      _uiState.update { it.copy(isLoading = true) }
    } else {
      _uiState.value =
          DashboardUiState(
              sessions = emptyList(),
              currentSessions = emptyList(),
              isLoading = true,
              error = null,
              midnightDelay = calculateMidnightDelay(),
          )
    }

    try {
      coroutineScope {
        launch {
          loadPreferences(studentId)
          updateCurrentSessions()
        }
        getUserSchedule(studentId, date).collect(::updateSessions)
      }
    } catch (exception: CancellationException) {
      throw exception
    } catch (_: Exception) {
      showLoadError()
    }
  }

  /** Returns the default break settings used before preferences are available. */
  private fun defaultBreakSettings(): Pair<JavaDuration, JavaDuration> =
      JavaDuration.ofMinutes(PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES.toLong()) to
          JavaDuration.ofMinutes(PreferenceDefaults.DEFAULT_BREAK_MINUTES.toLong())

  /** Resets break settings when observing a different user. */
  private fun resetBreakSettings() {
    val (frequency, length) = defaultBreakSettings()
    breakFrequency = frequency
    breakLength = length
  }

  /** Loads break settings, falling back silently to the documented defaults. */
  private suspend fun loadPreferences(studentId: String) {
    val preferences =
        try {
          preferencesRepository.getPreferences(studentId).getOrNull()
        } catch (exception: CancellationException) {
          throw exception
        } catch (_: Exception) {
          null
        }
    breakFrequency =
        JavaDuration.ofMinutes(
            (preferences?.breakFrequencyMinutes ?: PreferenceDefaults.DEFAULT_FREQUENCY_MINUTES)
                .toLong()
        )
    breakLength =
        JavaDuration.ofMinutes(
            (preferences?.breakLengthMinutes ?: PreferenceDefaults.DEFAULT_BREAK_MINUTES).toLong()
        )
  }

  /** Converts incoming domain [Event] items to display-ready [DashboardSession] models. */
  private fun updateSessions(result: Result<List<Event>>) {
    result.fold(
        onSuccess = { events ->
          latestEvents = events
          val sessions = events.map(::toDashboardSession)
          _uiState.update {
            it.copy(
                sessions = sessions,
                currentSessions = currentSessions(events),
                isLoading = false,
                error = null,
                midnightDelay = calculateMidnightDelay(),
            )
          }
        },
        onFailure = { showLoadError() },
    )
  }

  /** Converts one domain event to the dashboard display model. */
  private fun toDashboardSession(event: Event): DashboardSession {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(clock.zone)
    return DashboardSession(
        id = event.id,
        startTime = timeFormatter.format(event.details.startsAt),
        endTime = timeFormatter.format(event.details.endsAt),
        subject = event.details.subject,
        eventType = event.details.type,
        location = event.details.location,
        // Group sessions stay non-clickable until Event Details supports them (#325/#326).
        isClickable = event !is GroupSession,
    )
  }

  /** Recomputes the current-session projection from the retained event list. */
  private fun updateCurrentSessions() {
    _uiState.update { it.copy(currentSessions = currentSessions(latestEvents)) }
  }

  /** Maps events covering the current minute to sessions with their ring state. */
  private fun currentSessions(events: List<Event>): List<CurrentSession> {
    val now = clock.instant().truncatedTo(ChronoUnit.MINUTES)
    return events
        .filter { event ->
          !now.isBefore(event.details.startsAt) && now.isBefore(event.details.endsAt)
        }
        .map { event ->
          CurrentSession(
              session = toDashboardSession(event),
              ring =
                  StudyRingCalculator.compute(
                      event.details.startsAt,
                      event.details.endsAt,
                      now,
                      breakFrequency,
                      breakLength,
                  ),
          )
        }
  }

  /** Drops retained events so ticks cannot resurrect data from an old user or failed load. */
  private fun clearEvents() {
    latestEvents = emptyList()
  }

  /** Clears stale sessions and sets the error state. */
  private fun showLoadError() {
    clearEvents()
    _uiState.update {
      it.copy(
          sessions = emptyList(),
          currentSessions = emptyList(),
          isLoading = false,
          error = DashboardViewModelError.LOAD_SESSIONS_FAILED,
      )
    }
  }
}

/**
 * Factory creating [DashboardViewModel] with injected dependencies.
 *
 * @property authRepository source for the current authenticated user.
 * @property getUserSchedule use case combining personal events and joined group sessions.
 * @property preferencesRepository source for break settings, read in parallel with the schedule;
 *   documented defaults are used until preferences arrive.
 * @property clock provider for current time and time zone calculations.
 * @property minuteTicks emits at each minute boundary; injected for tests.
 */
class DashboardViewModelFactory(
    private val authRepository: AuthRepository,
    private val getUserSchedule: GetUserScheduleUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val minuteTicks: kotlinx.coroutines.flow.Flow<Unit> = minuteTicks(clock),
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == DashboardViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return DashboardViewModel(
        authRepository = authRepository,
        getUserSchedule = getUserSchedule,
        preferencesRepository = preferencesRepository,
        clock = clock,
        minuteTicks = minuteTicks,
    )
        as T
  }
}

/** Returns the dashboard ViewModel backed by the app's shared repository wiring. */
@Composable
fun dashboardViewModel(): DashboardViewModel =
    viewModel(
        factory =
            DashboardViewModelFactory(
                authRepository = AuthRepositoryProvider.getRepository(LocalContext.current),
                getUserSchedule = GetUserScheduleUseCase(),
                preferencesRepository = PreferencesRepositoryProvider.getRepository(),
            )
    )
