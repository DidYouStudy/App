// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.PersonalEvent
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the Event Details screen.
 *
 * @property event the [PersonalEvent] loaded for display, or null if not yet loaded or on error.
 * @property isLoading true while an event is being loaded from the repository.
 * @property isDeleting true while the event is being deleted.
 * @property isEventDeleted set to true when the event is successfully deleted, cleared by
 *   [EventDetailsViewModel.onEventDeletedHandled].
 * @property error failure category to display to the user, or null when there is none.
 */
data class EventDetailsUiState(
    val event: PersonalEvent? = null,
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val isEventDeleted: Boolean = false,
    val error: EventPlanningError? = null,
) {
  /** True while any asynchronous operation (loading or deleting) is in progress. */
  val isBusy: Boolean
    get() = isLoading || isDeleting
}

/**
 * ViewModel backing the Event Details screen.
 *
 * Provides event loading, event deletion, and task checklist toggling through repository contracts
 * ([AuthRepository] and [EventsRepository]).
 *
 * @param authRepository source of the currently authenticated user.
 * @param eventsRepository repository storing personal events.
 * @param clock provider for current time and time zone.
 */
class EventDetailsViewModel(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  private val _uiState = MutableStateFlow(EventDetailsUiState())
  /** Current state of the event details UI. */
  val uiState: StateFlow<EventDetailsUiState> = _uiState.asStateFlow()

  private var currentUserId: String? = null

  init {
    viewModelScope.launch {
      try {
        authRepository.currentUser.onEach { user -> currentUserId = user?.uid }.collect {}
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        currentUserId = null
        _uiState.update { it.copy(error = EventPlanningError.FAILED_TO_OBSERVE_USER) }
      }
    }
  }

  /**
   * Loads the [PersonalEvent] with [eventId] for the signed-in user.
   *
   * @param eventId ID of the event to load.
   */
  fun loadEvent(eventId: String) {
    if (eventId.isBlank() || _uiState.value.isLoading) return
    if (_uiState.value.event?.id == eventId) return

    _uiState.update { it.copy(isLoading = true, error = null) }
    viewModelScope.launch {
      try {
        val ownerId = authRepository.currentUser.first()?.uid
        if (ownerId == null) {
          _uiState.update {
            it.copy(isLoading = false, error = EventPlanningError.NO_SIGNED_IN_USER)
          }
          return@launch
        }
        eventsRepository
            .getEvent(ownerId, eventId)
            .onSuccess { event ->
              _uiState.update { it.copy(event = event, isLoading = false, error = null) }
            }
            .onFailure { exception ->
              _uiState.update {
                it.copy(
                    event = null,
                    isLoading = false,
                    error = exception.toUserError(EventPlanningError.FAILED_TO_LOAD_EVENT),
                )
              }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update {
          it.copy(
              event = null,
              isLoading = false,
              error = exception.toUserError(EventPlanningError.FAILED_TO_LOAD_EVENT),
          )
        }
      }
    }
  }

  /**
   * Deletes the currently loaded event for the signed-in user. On success, sets
   * [EventDetailsUiState.isEventDeleted] to true.
   */
  fun deleteEvent() {
    val currentEvent = _uiState.value.event
    if (currentEvent == null) {
      _uiState.update { it.copy(error = EventPlanningError.NO_EVENT_TO_EDIT) }
      return
    }

    if (_uiState.value.isBusy) return

    _uiState.update { it.copy(isDeleting = true, error = null) }
    viewModelScope.launch {
      try {
        val ownerId = authRepository.currentUser.first()?.uid
        if (ownerId == null) {
          _uiState.update {
            it.copy(isDeleting = false, error = EventPlanningError.NO_SIGNED_IN_USER)
          }
          return@launch
        }
        eventsRepository
            .deleteEvent(ownerId, currentEvent.id)
            .onSuccess {
              _uiState.update {
                it.copy(
                    event = null,
                    isDeleting = false,
                    isEventDeleted = true,
                    error = null,
                )
              }
            }
            .onFailure { exception ->
              _uiState.update {
                it.copy(
                    isDeleting = false,
                    error = exception.toUserError(EventPlanningError.FAILED_TO_DELETE_EVENT),
                )
              }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update {
          it.copy(
              isDeleting = false,
              error = exception.toUserError(EventPlanningError.FAILED_TO_DELETE_EVENT),
          )
        }
      }
    }
  }

  /** Acknowledges a successful delete so navigation back triggers only once. */
  fun onEventDeletedHandled() {
    _uiState.update { it.copy(isEventDeleted = false) }
  }

  /**
   * Toggles the completion status of the task at [index] for the currently loaded event and
   * persists the change to the repository.
   *
   * @param index index of the task to toggle in [PersonalEvent.tasks].
   */
  fun toggleTask(index: Int) {
    val currentEvent = _uiState.value.event ?: return
    if (index !in currentEvent.tasks.indices) return

    val now = clock.instant()
    val updatedTasks = toggleTaskItem(currentEvent.tasks, index, clock)
    val updatedEvent = currentEvent.copy(tasks = updatedTasks, updatedAt = now)
    updateEventTasks(previousEvent = currentEvent, updatedEvent = updatedEvent)
  }

  /** Clears any displayed error message. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  private fun updateEventTasks(previousEvent: PersonalEvent, updatedEvent: PersonalEvent) {
    _uiState.update { it.copy(event = updatedEvent, error = null) }
    viewModelScope.launch {
      try {
        eventsRepository.saveEvent(updatedEvent).onFailure { exception ->
          _uiState.update {
            it.copy(
                event = previousEvent,
                error = exception.toUserError(EventPlanningError.FAILED_TO_EDIT_EVENT),
            )
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update {
          it.copy(
              event = previousEvent,
              error = exception.toUserError(EventPlanningError.FAILED_TO_EDIT_EVENT),
          )
        }
      }
    }
  }

  private fun Throwable.toUserError(default: EventPlanningError): EventPlanningError =
      toEventPlanningError(default)
}

/** Supplies an injected repository when Android creates the [EventDetailsViewModel]. */
class EventDetailsViewModelFactory(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModelProvider.Factory {
  /**
   * Creates an EventDetailsViewModel and rejects unrelated ViewModel types rather than casting
   * blindly.
   */
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == EventDetailsViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return EventDetailsViewModel(authRepository, eventsRepository, clock) as T
  }
}

/**
 * Returns the [EventDetailsViewModel] of the current ViewModel owner, backed by the app's
 * repositories. To be used by the EventDetailsScreen composable.
 */
@Composable
fun eventDetailsViewModel(): EventDetailsViewModel =
    viewModel(
        factory =
            EventDetailsViewModelFactory(
                authRepository = AuthRepositoryProvider.getRepository(LocalContext.current),
                eventsRepository = EventsRepositoryProvider.getRepository(),
            )
    )
