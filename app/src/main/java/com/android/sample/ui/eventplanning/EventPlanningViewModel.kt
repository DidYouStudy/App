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
 * instants with the clock's time zone when adding. The date is the day the event starts: an end
 * time earlier than the start time means the event ends the next day.
 *
 * @property recommendedSubjects subjects the user has used before, most recent first, for the
 *   suggestions.
 * @property isEditing whether the form was filled from an existing event by
 *   [EventPlanningViewModel.loadEvent].
 * @property isLoading whether an existing event is being loaded into the form.
 * @property isDeleting whether an event is being deleted.
 * @property isEventAdded set after a successful add until
 *   [EventPlanningViewModel.onEventAddedHandled] is called; the screen navigates away on it.
 * @property isEventEdited same for a successful edit, cleared by
 *   [EventPlanningViewModel.onEventEditedHandled].
 * @property isEventDeleted same for a successful delete, cleared by
 *   [EventPlanningViewModel.onEventDeletedHandled].
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
    val isDeleting: Boolean = false,
    val isEventAdded: Boolean = false,
    val isEventEdited: Boolean = false,
    val isEventDeleted: Boolean = false,
    val error: String? = null,
) {
  /** The subject is mandatory for every type except [EventType.OTHER] ("Subject (optional)"). */
  val isSubjectRequired: Boolean
    get() = type != EventType.OTHER

  /** Whether a request is running; the screen can disable its buttons meanwhile. */
  val isBusy: Boolean
    get() = isSaving || isLoading || isDeleting

  /** The same state without the previous error and add, edit or delete result. */
  internal fun withoutResults() =
      copy(error = null, isEventAdded = false, isEventEdited = false, isEventDeleted = false)
}

/**
 * Builds, adds, edits and deletes events for the currently authenticated user through repository
 * contracts.
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

  /**
   * The event loaded by [loadEvent]; [editEvent] replaces it and keeps its ID and creation time.
   */
  private var editingEvent: Event? = null

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
          it.withoutResults()
              .copy(error = exception.localizedMessage ?: FAILED_TO_OBSERVE_USER_ERROR)
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
   * Fills the form from one of the signed-in user's existing events, for the edit screen. Call
   * [editEvent] afterwards to save the changes.
   */
  fun loadEvent(eventId: String) {
    val ownerId = currentUserId
    if (ownerId == null) {
      fail(NO_SIGNED_IN_USER_ERROR)
      return
    }
    _uiState.update { it.withoutResults().copy(isLoading = true) }
    viewModelScope.launch {
      try {
        eventsRepository
            .getEvent(ownerId, eventId)
            .onSuccess { event -> populateForm(event) }
            .onFailure { exception ->
              _uiState.update {
                it.copy(error = exception.toUserMessage(FAILED_TO_LOAD_EVENT_ERROR))
              }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(error = exception.toUserMessage(FAILED_TO_LOAD_EVENT_ERROR)) }
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Creates and adds a new event from the current form, always under a new ID, even if an event was
   * loaded (which makes "duplicate" possible).
   *
   * The authenticated UID is always used as the owner; callers cannot select another user's
   * repository partition. Missing or invalid form values and repository failures are exposed in
   * [uiState]. The event starts on the picked date; an end time earlier than the start time means
   * it ends the next day, while an end time equal to the start time is rejected. On success the
   * form is reset and [EventPlanningUiState.isEventAdded] is set.
   */
  fun addEvent() = save(editing = null)

  /**
   * Replaces the event loaded by [loadEvent] with the current form. The event keeps its ID and
   * creation time; its update time becomes now. Times, including the next-day rule, and failures
   * behave as in [addEvent]; on success the form is reset and [EventPlanningUiState.isEventEdited]
   * is set.
   */
  fun editEvent() {
    val event = editingEvent
    if (event == null) {
      fail(NO_EVENT_TO_EDIT_ERROR)
      return
    }
    save(editing = event)
  }

  /**
   * Deletes the signed-in user's event [eventId]; deleting an event that does not exist also
   * succeeds. On success the form is reset and [EventPlanningUiState.isEventDeleted] is set.
   */
  fun deleteEvent(eventId: String) {
    if (_uiState.value.isBusy) return

    val ownerId = currentUserId
    if (ownerId == null) {
      fail(NO_SIGNED_IN_USER_ERROR)
      return
    }

    _uiState.update { it.withoutResults().copy(isDeleting = true) }
    viewModelScope.launch {
      try {
        eventsRepository
            .deleteEvent(ownerId, eventId)
            .onSuccess {
              pendingEventId = null
              editingEvent = null
              _uiState.update {
                EventPlanningUiState(
                    recommendedSubjects = it.recommendedSubjects,
                    isEventDeleted = true,
                )
              }
            }
            .onFailure { exception ->
              _uiState.update {
                it.copy(error = exception.toUserMessage(FAILED_TO_DELETE_EVENT_ERROR))
              }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(error = exception.toUserMessage(FAILED_TO_DELETE_EVENT_ERROR)) }
      } finally {
        _uiState.update { it.copy(isDeleting = false) }
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

  /** Acknowledges a successful edit after the screen has navigated away. */
  fun onEventEditedHandled() {
    _uiState.update { it.copy(isEventEdited = false) }
  }

  /** Acknowledges a successful delete after the screen has navigated away. */
  fun onEventDeletedHandled() {
    _uiState.update { it.copy(isEventDeleted = false) }
  }

  /** Discards the form, e.g. when the user leaves the screen without adding. */
  fun resetForm() {
    pendingEventId = null
    editingEvent = null
    _uiState.update { EventPlanningUiState(recommendedSubjects = it.recommendedSubjects) }
  }

  /** Adds the form as a new event, or replaces [editing] with it. */
  private fun save(editing: Event?) {
    val state = _uiState.value
    if (state.isBusy) return

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

    // An end time before the start time can only mean the next day (e.g. 22:00 to 01:00).
    val endDate = if (endTime.isBefore(startTime)) date.plusDays(1) else date

    val now = clock.instant()
    val event =
        try {
          val subject = state.subject.trim().takeIf(String::isNotEmpty)
          val startsAt = date.atTime(startTime).atZone(clock.zone).toInstant()
          val endsAt = endDate.atTime(endTime).atZone(clock.zone).toInstant()
          val location = state.location.trim().takeIf(String::isNotEmpty)
          val description = state.description.trim().takeIf(String::isNotEmpty)
          if (editing == null) {
            val id = pendingEventId ?: eventsRepository.newEventId().also { pendingEventId = it }
            Event(
                id = id,
                ownerId = ownerId,
                type = state.type,
                subject = subject,
                startsAt = startsAt,
                endsAt = endsAt,
                createdAt = now,
                updatedAt = now,
                location = location,
                description = description,
                tasks = state.tasks,
            )
          } else {
            editing.copy(
                ownerId = ownerId,
                type = state.type,
                subject = subject,
                startsAt = startsAt,
                endsAt = endsAt,
                updatedAt = now,
                location = location,
                description = description,
                tasks = state.tasks,
            )
          }
        } catch (exception: IllegalArgumentException) {
          fail(exception.message)
          return
        }

    val defaultError =
        if (editing == null) FAILED_TO_ADD_EVENT_ERROR else FAILED_TO_EDIT_EVENT_ERROR
    _uiState.update { it.withoutResults().copy(isSaving = true) }
    viewModelScope.launch {
      try {
        eventsRepository
            .saveEvent(event)
            .onSuccess {
              pendingEventId = null
              editingEvent = null
              _uiState.update {
                EventPlanningUiState(
                    recommendedSubjects = it.recommendedSubjects,
                    isEventAdded = editing == null,
                    isEventEdited = editing != null,
                )
              }
            }
            .onFailure { exception ->
              _uiState.update { it.copy(error = exception.toUserMessage(defaultError)) }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(error = exception.toUserMessage(defaultError)) }
      } finally {
        _uiState.update { it.copy(isSaving = false) }
      }
    }
  }

  /** Fills the form from [event] and remembers it for [editEvent]. */
  private fun populateForm(event: Event) {
    val start = event.startsAt.atZone(clock.zone)
    val end = event.endsAt.atZone(clock.zone)
    editingEvent = event
    _uiState.update {
      it.copy(
          type = event.type,
          subject = event.subject.orEmpty(),
          date = start.toLocalDate(),
          startTime = start.toLocalTime(),
          endTime = end.toLocalTime(),
          location = event.location.orEmpty(),
          description = event.description.orEmpty(),
          tasks = event.tasks,
          isEditing = true,
      )
    }
  }

  private fun fail(message: String?) {
    _uiState.update { it.copy(error = message, isEventAdded = false) }
  }

  /** Applies a form edit and clears the previous save result. */
  private fun updateForm(update: EventPlanningUiState.() -> EventPlanningUiState) {
    _uiState.update { it.update().withoutResults() }
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
    const val NO_EVENT_TO_EDIT_ERROR = "No event loaded to edit"
    const val FAILED_TO_ADD_EVENT_ERROR = "Failed to add event"
    const val FAILED_TO_EDIT_EVENT_ERROR = "Failed to edit event"
    const val FAILED_TO_DELETE_EVENT_ERROR = "Failed to delete event"
    const val FAILED_TO_LOAD_EVENT_ERROR = "Failed to load event"
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
