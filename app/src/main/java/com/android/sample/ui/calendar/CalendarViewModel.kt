// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
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

private const val WEEK_START_KEY = "calendar_week_start"
private const val DAYS_PER_WEEK = 7L

/** Categorized failures that the calendar screen can map to localized messages. */
enum class CalendarViewModelError {
  /** The event stream could not be observed. */
  LOAD_EVENTS_FAILED,
}

/** The selected calendar week and its live event data. */
data class CalendarUiState(
    val today: LocalDate,
    val weekStart: LocalDate,
    val events: List<Event> = emptyList(),
    val isLoading: Boolean = true,
    val error: CalendarViewModelError? = null,
)

/** Observes the signed-in user's events for the currently selected calendar week. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val savedStateHandle: SavedStateHandle,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val dateHelper = CalendarDateHelper()
  // Restore the previously selected week when available; otherwise start with this week's Monday.
  private val selectedWeekStart =
      MutableStateFlow(
          savedStateHandle.get<String>(WEEK_START_KEY)?.let(LocalDate::parse)
              ?: dateHelper.weekDates(LocalDate.now(clock)).first()
      )

  private val _uiState =
      MutableStateFlow(
          CalendarUiState(
              today = LocalDate.now(clock),
              weekStart = selectedWeekStart.value,
          )
      )

  /** The selected week, loading state, observed events, and any load error. */
  val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        combine(authRepository.currentUser, selectedWeekStart) { user, weekStart ->
              user to weekStart
            }
            .collectLatest { (user, weekStart) -> observeWeek(user?.uid, weekStart) }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        showLoadError()
      }
    }
  }

  /** Resets the visible week state, then observes its events when a user is signed in. */
  private suspend fun observeWeek(ownerId: String?, weekStart: LocalDate) {
    // Persist the active week and clear stale events when the user or week changes.
    savedStateHandle[WEEK_START_KEY] = weekStart.toString()
    _uiState.value =
        CalendarUiState(
            today = LocalDate.now(clock),
            weekStart = weekStart,
            isLoading = ownerId != null,
        )

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

/** Creates [CalendarViewModel] instances with the app's repository contracts and saved state. */
class CalendarViewModelFactory(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(
      modelClass: Class<T>,
      extras: CreationExtras,
  ): T {
    require(modelClass == CalendarViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return CalendarViewModel(
        authRepository = authRepository,
        eventsRepository = eventsRepository,
        savedStateHandle = extras.createSavedStateHandle(),
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
