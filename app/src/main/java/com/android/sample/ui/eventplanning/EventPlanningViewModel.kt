// Co-authored-by: GitHub Copilot
package com.android.sample.ui.eventplanning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form values and the result of the latest event-creation request. */
data class EventPlanningUiState(
    val type: EventType = EventType.STUDY_SESSION,
    val subject: String = "",
    val startsAt: Instant? = null,
    val endsAt: Instant? = null,
    val location: String = "",
    val description: String = "",
    val tasks: List<EventTask> = emptyList(),
    val isSaving: Boolean = false,
    val isEventAdded: Boolean = false,
    val error: String? = null,
)

/** Builds and saves events for the currently authenticated user through repository contracts. */
class EventPlanningViewModel(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(EventPlanningUiState())

  /** Read-only state for collecting the event form and save result. */
  val uiState: StateFlow<EventPlanningUiState> = _uiState.asStateFlow()

  private var currentUserId: String? = null

  init {
    viewModelScope.launch {
      authRepository.currentUser.collect { user -> currentUserId = user?.uid }
    }
  }

  /** Updates the event activity kind. */
  fun setType(type: EventType) = updateForm { copy(type = type) }

  /** Updates the event subject. */
  fun setSubject(subject: String) = updateForm { copy(subject = subject) }

  /** Updates the event start instant. */
  fun setStartsAt(startsAt: Instant?) = updateForm { copy(startsAt = startsAt) }

  /** Updates the event end instant. */
  fun setEndsAt(endsAt: Instant?) = updateForm { copy(endsAt = endsAt) }

  /** Updates the optional event location. */
  fun setLocation(location: String) = updateForm { copy(location = location) }

  /** Updates the optional event description. */
  fun setDescription(description: String) = updateForm { copy(description = description) }

  /** Replaces the event checklist. */
  fun setTasks(tasks: List<EventTask>) = updateForm { copy(tasks = tasks) }

  /** Clears a displayed save error. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  /**
   * Creates and saves the event represented by the current form.
   *
   * The authenticated UID is always used as the owner; callers cannot select another user's
   * repository partition. Missing or invalid form values and repository failures are exposed in
   * [uiState].
   */
  fun addEvent() {
    val state = _uiState.value
    if (state.isSaving) return

    val ownerId = currentUserId
    if (ownerId == null) {
      _uiState.update { it.copy(error = "No signed-in user", isEventAdded = false) }
      return
    }

    val startsAt = state.startsAt
    val endsAt = state.endsAt
    if (startsAt == null || endsAt == null) {
      _uiState.update {
        it.copy(error = "Event start and end times are required", isEventAdded = false)
      }
      return
    }

    val now = Instant.now()
    val event =
        try {
          Event(
              id = eventsRepository.newEventId(),
              ownerId = ownerId,
              type = state.type,
              subject = state.subject.trim().takeIf(String::isNotEmpty),
              startsAt = startsAt,
              endsAt = endsAt,
              createdAt = now,
              updatedAt = now,
              location = state.location.trim().takeIf(String::isNotEmpty),
              description = state.description.trim().takeIf(String::isNotEmpty),
              tasks = state.tasks,
          )
        } catch (exception: IllegalArgumentException) {
          _uiState.update { it.copy(error = exception.message, isEventAdded = false) }
          return
        }

    _uiState.update { it.copy(isSaving = true, isEventAdded = false, error = null) }
    viewModelScope.launch {
      try {
        eventsRepository
            .saveEvent(event)
            .onSuccess { _uiState.update { it.copy(isEventAdded = true) } }
            .onFailure { exception ->
              _uiState.update { it.copy(error = exception.message ?: "Failed to add event") }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(error = exception.message ?: "Failed to add event") }
      } finally {
        _uiState.update { it.copy(isSaving = false) }
      }
    }
  }

  /** Applies a form edit and clears the previous save result. */
  private fun updateForm(update: EventPlanningUiState.() -> EventPlanningUiState) {
    _uiState.update { it.update().copy(error = null, isEventAdded = false) }
  }
}
