// Co-authored-by: GitHub Copilot
package com.android.sample.ui.eventplanning

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.Event
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.EventsRepositoryProvider
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Form values, subject suggestions and the result of the latest add request.
 *
 * The date and times are the local values shown by the pickers; the ViewModel converts them to
 * instants with the clock's time zone when adding.
 *
 * @property recommendedSubjects subjects the user has used before, most recent first, for the
 *   suggestions.
 * @property isEventAdded set after a successful add until
 *   [EventPlanningViewModel.onEventAddedHandled] is called; the screen navigates away on it.
 */
data class EventPlanningUiState(
    val type: EventType = EventType.STUDY_SESSION,
    val subject: String = "",
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val location: String = "",
    val description: String = "",
    val tasks: List<EventTask> = emptyList(),
    val recommendedSubjects: List<String> = emptyList(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isEventAdded: Boolean = false,
    val error: String? = null,
) {
  /** The subject is mandatory for every type except [EventType.OTHER] ("Subject (optional)"). */
  val isSubjectRequired: Boolean
    get() = type != EventType.OTHER
}

/**
 * Builds and saves events for the currently authenticated user through repository contracts.
 *
 * @param clock source of the current time (task completion, creation and update times) and of the
 *   time zone used to turn the picked date and times into instants.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventPlanningViewModel(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(EventPlanningUiState())
  val uiState: StateFlow<EventPlanningUiState> = _uiState.asStateFlow()

  private var currentUserId: String? = null

  /**
   * ID of the event being added. Generated on the first attempt and reused by retries, so that a
   * retry replaces the event instead of duplicating it.
   */
  private var pendingEventId: String? = null

  init {
    viewModelScope.launch {
      try {
        authRepository.currentUser
            .onEach { user -> currentUserId = user?.uid }
            .flatMapLatest { user ->
              if (user == null) flowOf(Result.success(emptyList()))
              else eventsRepository.observeSubjects(user.uid)
            }
            // Suggestions are optional: if they cannot be read the list is simply empty and the
            // user can still type a subject.
            .collect { result ->
              _uiState.update { it.copy(recommendedSubjects = result.getOrDefault(emptyList())) }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        currentUserId = null
        _uiState.update {
          it.copy(
              error = exception.localizedMessage ?: FAILED_TO_OBSERVE_USER_ERROR,
              isEventAdded = false,
          )
        }
      }
    }
  }

  /** Updates the event activity kind. */
  fun setType(type: EventType) = updateForm { copy(type = type) }

  /** Updates the event subject. */
  fun setSubject(subject: String) = updateForm { copy(subject = subject) }

  /** Updates the event date. */
  fun setDate(date: LocalDate?) = updateForm { copy(date = date) }

  /** Updates the start time of day. */
  fun setStartTime(startTime: LocalTime?) = updateForm { copy(startTime = startTime) }

  /** Updates the end time of day. */
  fun setEndTime(endTime: LocalTime?) = updateForm { copy(endTime = endTime) }

  /** Updates the optional event location. */
  fun setLocation(location: String) = updateForm { copy(location = location) }

  /** Updates the optional event description. */
  fun setDescription(description: String) = updateForm { copy(description = description) }

  /** Replaces the event checklist. */
  fun setTasks(tasks: List<EventTask>) = updateForm { copy(tasks = tasks) }

  /** Appends an open task; blank text is ignored. */
  fun addTask(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    updateForm { copy(tasks = tasks + EventTask(trimmed)) }
  }

  /** Removes the task at [index]; an index outside the list is ignored. */
  fun removeTask(index: Int) = updateForm {
    if (index !in tasks.indices) this else copy(tasks = tasks.filterIndexed { i, _ -> i != index })
  }

  /** Checks off or reopens the task at [index]; an index outside the list is ignored. */
  fun toggleTask(index: Int) = updateForm {
    if (index !in tasks.indices) this
    else
        copy(
            tasks =
                tasks.mapIndexed { i, task ->
                  if (i != index) task
                  else task.copy(completedAt = if (task.isCompleted) null else clock.instant())
                }
        )
  }

  /** Clears a displayed save error. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  /**
   * Creates and saves the event represented by the current form.
   *
   * The authenticated UID is always used as the owner; callers cannot select another user's
   * repository partition. Missing or invalid form values and repository failures are exposed in
   * [uiState]. The event is assumed to start and end on the picked date, so an end time that is not
   * after the start time is rejected. On success the form is reset and
   * [EventPlanningUiState.isEventAdded] is set.
   */
  fun addEvent() {
    val state = _uiState.value
    if (state.isSaving) return

    val ownerId = currentUserId
    if (ownerId == null) {
      fail(NO_SIGNED_IN_USER_ERROR)
      return
    }

    val date = state.date
    val startTime = state.startTime
    val endTime = state.endTime
    if (date == null || startTime == null || endTime == null) {
      fail(EVENT_DATE_AND_TIMES_REQUIRED_ERROR)
      return
    }

    // uses new id for event if it is not a retry for a previous event
    val id = pendingEventId ?: eventsRepository.newEventId().also { pendingEventId = it }
    val now = clock.instant()
    val event =
        try {
          Event(
              id = id,
              ownerId = ownerId,
              type = state.type,
              subject = state.subject.trim().takeIf(String::isNotEmpty),
              startsAt = date.atTime(startTime).atZone(clock.zone).toInstant(),
              endsAt = date.atTime(endTime).atZone(clock.zone).toInstant(),
              createdAt = now,
              updatedAt = now,
              location = state.location.trim().takeIf(String::isNotEmpty),
              description = state.description.trim().takeIf(String::isNotEmpty),
              tasks = state.tasks,
          )
        } catch (exception: IllegalArgumentException) {
          fail(exception.message)
          return
        }

    _uiState.update { it.copy(isSaving = true, isEventAdded = false, error = null) }
    viewModelScope.launch {
      try {
        eventsRepository
            .saveEvent(event)
            .onSuccess {
              pendingEventId = null
              _uiState.update {
                EventPlanningUiState(
                    recommendedSubjects = it.recommendedSubjects,
                    isEventAdded = true,
                )
              }
            }
            .onFailure { exception ->
              _uiState.update {
                it.copy(error = exception.toUserMessage(FAILED_TO_ADD_EVENT_ERROR))
              }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(error = exception.toUserMessage(FAILED_TO_ADD_EVENT_ERROR)) }
      } finally {
        _uiState.update { it.copy(isSaving = false) }
      }
    }
  }

  /**
   * Acknowledges a successful add after the screen has navigated away, so that reopening the screen
   * does not navigate away again.
   */
  fun onEventAddedHandled() {
    _uiState.update { it.copy(isEventAdded = false) }
  }

  /** Discards the form, e.g. when the user leaves the screen without adding. */
  fun resetForm() {
    pendingEventId = null
    _uiState.update { EventPlanningUiState(recommendedSubjects = it.recommendedSubjects) }
  }

  private fun fail(message: String?) {
    _uiState.update { it.copy(error = message, isEventAdded = false) }
  }

  /** Applies a form edit and clears the previous save result. */
  private fun updateForm(update: EventPlanningUiState.() -> EventPlanningUiState) {
    _uiState.update { it.update().copy(error = null, isEventAdded = false) }
  }

  /** Turns a repository failure into text for the user rather than a technical message. */
  private fun Throwable.toUserMessage(default: String): String =
      if (this is EventsException) {
        when (error) {
          EventsError.INVALID_ARGUMENT -> INVALID_EVENT_ERROR
          EventsError.NOT_FOUND -> EVENT_NOT_FOUND_ERROR
          EventsError.PERMISSION_DENIED -> PERMISSION_DENIED_ERROR
          EventsError.INVALID_EVENT_DATA -> INVALID_EVENT_DATA_ERROR
          EventsError.STORAGE_FAILURE -> STORAGE_FAILURE_ERROR
          EventsError.UNKNOWN -> default
        }
      } else {
        message ?: default
      }

  companion object {
    const val FAILED_TO_OBSERVE_USER_ERROR = "Failed to observe signed-in user"
    const val NO_SIGNED_IN_USER_ERROR = "No signed-in user"
    const val EVENT_DATE_AND_TIMES_REQUIRED_ERROR =
        "Event date, start time and end time are required"
    const val FAILED_TO_ADD_EVENT_ERROR = "Failed to add event"
    const val INVALID_EVENT_ERROR = "The event is invalid"
    const val EVENT_NOT_FOUND_ERROR = "This event no longer exists"
    const val PERMISSION_DENIED_ERROR = "You are not allowed to change this event"
    const val INVALID_EVENT_DATA_ERROR = "The stored event is corrupted"
    const val STORAGE_FAILURE_ERROR = "Your events could not be stored on this device"
  }
}

/** Supplies an injected repository when Android creates the settings ViewModel. */
class EventPlanningViewModelFactory(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
) : ViewModelProvider.Factory {
  /**
   * Creates an EventsPlanningViewModel and rejects unrelated ViewModel types rather than casting
   * blindly.
   */
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == EventPlanningViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return EventPlanningViewModel(authRepository, eventsRepository) as T
  }
}

/**
 * Returns the [EventPlanningViewModel] of the current ViewModel owner (Activity or navigation
 * entry), backed by the app's in memory repository. The Add new ... and Edit ... screens use this
 * as their default.
 */
@Composable
fun eventsPlanningViewModel(): EventPlanningViewModel =
    viewModel(
        factory =
            EventPlanningViewModelFactory(
                authRepository = AuthRepositoryProvider.getRepository(LocalContext.current),
                eventsRepository = EventsRepositoryProvider.getRepository(),
            )
    )
