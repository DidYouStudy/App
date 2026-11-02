// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.EventsRepositoryProvider
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DAYS_PER_WEEK = 7L

/** Categorized failures that the calendar screen can map to localized messages. */
enum class CalendarViewModelError {
  /** The event stream could not be observed. */
  LOAD_EVENTS_FAILED,
}

/** The current date, displayed week, its live event data, and any loading error. */
data class CalendarUiState(
    val today: LocalDate,
    val weekStart: LocalDate,
    val weekDates: List<LocalDate>,
    val events: List<Event> = emptyList(),
    val isLoading: Boolean = true,
    val error: CalendarViewModelError? = null,
)

/** Owns calendar date selection and observes the signed-in user's events for the displayed week. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val dateHelper = CalendarDateHelper()
  // Start with the week containing today; subsequent navigation stays in this ViewModel's state.
  private val currentWeekStart =
      MutableStateFlow(dateHelper.weekDates(LocalDate.now(clock)).first())
  private val retryRequest = MutableStateFlow(0L)

  private val _uiState =
      MutableStateFlow(
          CalendarUiState(
              today = LocalDate.now(clock),
              weekStart = currentWeekStart.value,
              weekDates = dateHelper.weekDates(currentWeekStart.value),
          )
      )

  /** The selected week, loading state, observed events, and any load error. */
  val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

  /** Selects the Monday-to-Sunday week before the currently displayed one. */
  fun previousWeek() {
    currentWeekStart.value = dateHelper.previousWeek(currentWeekStart.value).first()
  }

  /** Selects the Monday-to-Sunday week after the currently displayed one. */
  fun nextWeek() {
    currentWeekStart.value = dateHelper.nextWeek(currentWeekStart.value).first()
  }

  /** Selects the week containing today according to the ViewModel's injected clock. */
  fun navigateToToday() {
    val today = LocalDate.now(clock)
    val todayWeekStart = dateHelper.weekDates(today).first()
    _uiState.update { it.copy(today = today) }
    currentWeekStart.value = todayWeekStart
  }

  /** Retries observing events for the current user and selected week. */
  fun retryEvents() {
    retryRequest.update { it + 1 }
  }

  init {
    viewModelScope.launch {
      var lastHandledRetryRequest = retryRequest.value
      try {
        combine(
                authRepository.currentUser,
                currentWeekStart,
                retryRequest,
            ) { user, weekStart, retryCount ->
              Triple(user, weekStart, retryCount)
            }
            .collectLatest { (user, weekStart, retryCount) ->
              val isRetry = retryCount != lastHandledRetryRequest
              lastHandledRetryRequest = retryCount
              observeWeek(user?.uid, weekStart, isRetry)
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        showLoadError()
      }
    }
  }

  /** Resets week state or preserves the error during retry, then observes events for that week. */
  private suspend fun observeWeek(
      ownerId: String?,
      weekStart: LocalDate,
      isRetry: Boolean,
  ) {
    if (isRetry) {
      // Keep the error visible while the retry runs; success clears it in [updateEvents].
      _uiState.update { it.copy(isLoading = ownerId != null) }
    } else {
      // Clear stale events whenever the user or selected week changes.
      _uiState.value =
          CalendarUiState(
              today = LocalDate.now(clock),
              weekStart = weekStart,
              weekDates = dateHelper.weekDates(weekStart),
              isLoading = ownerId != null,
          )
    }

    if (ownerId == null) return

    // A new week or user cancels the prior observation through collectLatest.
    try {
      getWeekEvents(ownerId, weekStart).collect(::updateEvents)
    } catch (exception: CancellationException) {
      throw exception
    } catch (exception: Exception) {
      showLoadError()
    }
  }

  /** Builds the local Monday-to-Monday time range and requests that week's event stream. */
  private fun getWeekEvents(ownerId: String, weekStart: LocalDate): Flow<Result<List<Event>>> {
    val from = weekStart.atStartOfDay(clock.zone).toInstant()
    val to = weekStart.plusDays(DAYS_PER_WEEK).atStartOfDay(clock.zone).toInstant()
    return eventsRepository.observeEvents(ownerId, from, to)
  }

  /** Updates the screen from one repository result, clearing events when the query fails. */
  private fun updateEvents(result: Result<List<Event>>) {
    result.fold(
        onSuccess = { events ->
          _uiState.update { it.copy(events = events, isLoading = false, error = null) }
        },
        onFailure = { showLoadError() },
    )
  }

  /** Clears stale event data and exposes the error state for the calendar screen. */
  private fun showLoadError() {
    _uiState.update {
      it.copy(
          events = emptyList(),
          isLoading = false,
          error = CalendarViewModelError.LOAD_EVENTS_FAILED,
      )
    }
  }
}

/** Creates [CalendarViewModel] instances with the app's repository contracts. */
class CalendarViewModelFactory(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == CalendarViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return CalendarViewModel(
        authRepository = authRepository,
        eventsRepository = eventsRepository,
        clock = clock,
    )
        as T
  }
}

/** Returns the calendar ViewModel backed by the app's shared authentication and event contracts. */
@Composable
fun calendarViewModel(): CalendarViewModel =
    viewModel(
        factory =
            CalendarViewModelFactory(
                authRepository = AuthRepositoryProvider.getRepository(LocalContext.current),
                eventsRepository = EventsRepositoryProvider.getRepository(),
            )
    )
