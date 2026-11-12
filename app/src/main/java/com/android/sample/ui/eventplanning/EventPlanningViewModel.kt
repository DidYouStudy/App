// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini <noreply@google.com>
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
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.GroupSession
import com.android.sample.model.event.PersonalEvent
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationFilter
import com.android.sample.model.location.CampusLocationRepository
import com.android.sample.model.location.CampusLocationRepositoryProvider
import com.android.sample.model.preferences.PreferenceDefaults
import com.android.sample.model.preferences.PreferencesRepository
import com.android.sample.model.preferences.PreferencesRepositoryProvider
import com.android.sample.model.preferences.UserPreferences
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Categorizes whether an event being planned is private to the user or a collaborative group
 * session.
 */
enum class EventKind {
  /** A private event belonging to the authenticated user. */
  PERSONAL,

  /** A public or shared study session with multiple members. */
  GROUP_SESSION,
}

/**
 * Form values, subject suggestions and the result of the latest add request.
 *
 * The date and times are the local values shown by the pickers; the ViewModel converts them to
 * instants with the clock's time zone when adding. The date is the day the event starts: an end
 * time earlier than the start time means the event ends the next day.
 *
 * @property eventKind whether the event is personal or a group session.
 * @property recommendedSubjects subjects the user has used before, most recent first, for the
 *   suggestions.
 * @property locationSearchResults campus locations currently matching the location picker's search
 *   text.
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
    val eventKind: EventKind = EventKind.PERSONAL,
    val type: EventType = EventType.STUDY_SESSION,
    val subject: String = "",
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val location: String = "",
    val description: String = "",
    val tasks: List<EventTask> = emptyList(),
    val recommendedSubjects: List<String> = emptyList(),
    val locationSearchResults: List<CampusLocation> = emptyList(),
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val isEventAdded: Boolean = false,
    val isEventEdited: Boolean = false,
    val isEventDeleted: Boolean = false,
    val error: EventPlanningError? = null,
) {
  /** Whether the task checklist section should be shown in the form. */
  val showTasksSection: Boolean
    get() = eventKind == EventKind.PERSONAL

  /** The subject is mandatory for every type except [EventType.OTHER] ("Subject (optional)"). */
  val isSubjectRequired: Boolean
    get() = type != EventType.OTHER

  /** Whether a request is running; the screen can disable its buttons meanwhile. */
  val isBusy: Boolean
    get() = isSaving || isLoading || isDeleting

  /** Keeps Edit open until a write fails or its success callback is delivered. */
  val canLeaveEdit: Boolean
    get() = !isSaving && !isDeleting && !isEventEdited && !isEventDeleted

  /** The same state without the previous error and add, edit or delete result. */
  internal fun withoutResults() =
      copy(error = null, isEventAdded = false, isEventEdited = false, isEventDeleted = false)
}

/**
 * Builds, adds, edits and deletes events for the currently authenticated user through repository
 * contracts.
 *
 * @param campusLocationRepository source of the campus locations shown by the location picker.
 * @param clock source of the current time (task completion, creation and update times) and of the
 *   time zone used to turn the picked date and times into instants.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventPlanningViewModel(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val campusLocationRepository: CampusLocationRepository,
    private val groupSessionsRepository: GroupSessionsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val preferencesRepository: PreferencesRepository =
        object : PreferencesRepository {
          override suspend fun getPreferences(userId: String): Result<UserPreferences?> =
              Result.success(null)

          override suspend fun savePreferences(preferences: UserPreferences): Result<Unit> =
              Result.success(Unit)
        },
) : ViewModel() {
  private val _uiState = MutableStateFlow(EventPlanningUiState())
  val uiState: StateFlow<EventPlanningUiState> = _uiState.asStateFlow()

  private var currentUserId: String? = null
  private var sessionLengthMinutes: Int = PreferenceDefaults.DEFAULT_SESSION_MINUTES

  /**
   * ID of the event being added. Generated on the first attempt and reused by retries, so that a
   * retry replaces the event instead of duplicating it.
   */
  private var pendingEventId: String? = null

  /**
   * The event loaded by [loadEvent]; [editEvent] replaces it and keeps its ID and creation time.
   */
  private var editingEvent: Event? = null

  /**
   * The latest [searchLocations] request; cancelled on every new call so a slow, stale response
   * cannot overwrite a newer one's results.
   */
  private var locationSearchJob: Job? = null

  init {
    viewModelScope.launch {
      try {
        authRepository.currentUser
            .onEach { user ->
              currentUserId = user?.uid
              if (user != null) {
                preferencesRepository.getPreferences(user.uid).onSuccess { prefs ->
                  if (prefs != null) {
                    sessionLengthMinutes = prefs.sessionLengthMinutes
                  }
                }
              }
            }
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
          it.withoutResults().copy(error = EventPlanningError.FAILED_TO_OBSERVE_USER)
        }
      }
    }
  }

  /**
   * Resolves campus locations matching [query] via [campusLocationRepository] and exposes them as
   * [EventPlanningUiState.locationSearchResults] for the location picker. A blank query clears the
   * results directly, without calling the repository: the picker starts empty, not pre-loaded with
   * every location. Like [recommendedSubjects][EventPlanningUiState.recommendedSubjects], a lookup
   * failure is not shown as an error: the results are simply left empty.
   */
  fun searchLocations(query: String) {
    locationSearchJob?.cancel()
    if (query.isBlank()) {
      _uiState.update { it.copy(locationSearchResults = emptyList()) }
      return
    }
    locationSearchJob = viewModelScope.launch {
      val result = campusLocationRepository.search(CampusLocationFilter(query = query))
      _uiState.update { it.copy(locationSearchResults = result.getOrDefault(emptyList())) }
    }
  }

  /** Updates the category of the event being planned (personal vs group session). */
  fun setEventKind(kind: EventKind) {
    if (_uiState.value.eventKind == kind && !_uiState.value.isEditing) return
    resetForm(kind)
  }

  /** Updates the event activity kind. */
  fun setType(type: EventType) = updateForm { copy(type = type) }

  /** Updates the event subject. */
  fun setSubject(subject: String) = updateForm { copy(subject = subject) }

  /** Updates the event date. */
  fun setDate(date: LocalDate?) = updateForm { copy(date = date) }

  /**
   * Updates the start time of day and automatically sets the end time to start time plus
   * sessionLengthMinutes from user preferences.
   */
  fun setStartTime(startTime: LocalTime?) {
    updateForm {
      val endTime = startTime?.plusMinutes(sessionLengthMinutes.toLong()) ?: this.endTime
      copy(startTime = startTime, endTime = endTime)
    }
  }

  /** Updates the end time of day. */
  fun setEndTime(endTime: LocalTime?) = updateForm { copy(endTime = endTime) }

  /** Updates the optional event location. */
  fun setLocation(location: String) = updateForm { copy(location = location) }

  /** Updates the optional event description. */
  fun setDescription(description: String) = updateForm { copy(description = description) }

  /** Replaces the event checklist. Forbidden for group sessions. */
  fun setTasks(tasks: List<EventTask>) {
    requirePersonalEvent()
    updateForm { copy(tasks = tasks) }
  }

  /** Appends an open task; blank text is ignored. Forbidden for group sessions. */
  fun addTask(text: String) {
    requirePersonalEvent()
    if (text.trim().isEmpty()) return
    updateForm { copy(tasks = addTaskItem(tasks, text)) }
  }

  /**
   * Removes the task at [index]; an index outside the list is ignored. Forbidden for group
   * sessions.
   */
  fun removeTask(index: Int) {
    requirePersonalEvent()
    if (index !in _uiState.value.tasks.indices) return
    updateForm { copy(tasks = removeTaskItem(tasks, index)) }
  }

  /**
   * Checks off or reopens the task at [index]; an index outside the list is ignored. Forbidden for
   * group sessions.
   */
  fun toggleTask(index: Int) {
    requirePersonalEvent()
    if (index !in _uiState.value.tasks.indices) return
    updateForm { copy(tasks = toggleTaskItem(tasks, index, clock)) }
  }

  /** Clears a displayed save error. */
  fun clearError() {
    _uiState.update { it.copy(error = null) }
  }

  /**
   * Fills the form from one of the signed-in user's existing events, for the edit screen. Call
   * [editEvent] afterwards to save the changes.
   */
  fun loadEvent(eventId: String, kind: EventKind = EventKind.PERSONAL) {
    if (eventId.isBlank()) return
    // Already loaded (e.g. after rotation) or loading right now: keep the user's edits.
    if (editingEvent?.id == eventId || _uiState.value.isLoading) return

    pendingEventId = null
    editingEvent = null
    _uiState.update {
      EventPlanningUiState(
          eventKind = kind,
          isLoading = true,
          recommendedSubjects = it.recommendedSubjects,
      )
    }
    viewModelScope.launch {
      try {
        // Suspends until the auth flow has delivered its first value.
        val ownerId = authRepository.currentUser.first()?.uid
        if (ownerId == null) {
          fail(EventPlanningError.NO_SIGNED_IN_USER)
          return@launch
        }
        val result: Result<Event> =
            when (kind) {
              EventKind.PERSONAL -> eventsRepository.getEvent(ownerId, eventId)
              EventKind.GROUP_SESSION -> groupSessionsRepository.getSession(eventId)
            }
        result
            .onSuccess { event -> populateForm(event) }
            .onFailure { exception ->
              _uiState.update {
                it.copy(
                    error = exception.toUserError(EventPlanningError.FAILED_TO_LOAD_EVENT),
                    isEditing = false,
                )
              }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update {
          it.copy(
              error = exception.toUserError(EventPlanningError.FAILED_TO_LOAD_EVENT),
              isEditing = false,
          )
        }
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
  fun addEvent() = save(isEditing = false)

  /**
   * Replaces the event loaded by [loadEvent] with the current form. The event keeps its ID and
   * creation time; its update time becomes now. Times, including the next-day rule, and failures
   * behave as in [addEvent]; on success the form is reset and [EventPlanningUiState.isEventEdited]
   * is set.
   */
  fun editEvent() {
    if (editingEvent == null) {
      fail(EventPlanningError.NO_EVENT_TO_EDIT)
      return
    }
    save(isEditing = true)
  }

  /**
   * Deletes the signed-in user's event [eventId]; deleting an event that does not exist also
   * succeeds. On success the form is reset and [EventPlanningUiState.isEventDeleted] is set.
   */
  fun deleteEvent(eventId: String) {
    if (_uiState.value.isBusy) return

    val userId = currentUserId
    if (userId == null) {
      fail(EventPlanningError.NO_SIGNED_IN_USER)
      return
    }

    val kind = _uiState.value.eventKind
    _uiState.update { it.withoutResults().copy(isDeleting = true) }
    viewModelScope.launch {
      try {
        val result =
            when (kind) {
              EventKind.PERSONAL -> eventsRepository.deleteEvent(userId, eventId)
              EventKind.GROUP_SESSION -> groupSessionsRepository.deleteSession(userId, eventId)
            }
        result
            .onSuccess {
              pendingEventId = null
              editingEvent = null
              _uiState.update {
                EventPlanningUiState(
                    eventKind = it.eventKind,
                    recommendedSubjects = it.recommendedSubjects,
                    isEventDeleted = true,
                )
              }
            }
            .onFailure { exception ->
              _uiState.update {
                it.copy(error = exception.toUserError(EventPlanningError.FAILED_TO_DELETE_EVENT))
              }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update {
          it.copy(error = exception.toUserError(EventPlanningError.FAILED_TO_DELETE_EVENT))
        }
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

  /** Discards the form, e.g. when the user leaves the screen without adding or changes kind. */
  fun resetForm(kind: EventKind? = null) {
    pendingEventId = null
    editingEvent = null
    _uiState.update {
      EventPlanningUiState(
          eventKind = kind ?: it.eventKind,
          recommendedSubjects = it.recommendedSubjects,
      )
    }
  }

  /** Validates form inputs and builds [EventDetails], or sets the error and returns null. */
  private fun validateAndBuildDetails(original: EventDetails?): EventDetails? {
    val state = _uiState.value
    val date = state.date
    val startTime = state.startTime
    val endTime = state.endTime
    if (date == null || startTime == null || endTime == null) {
      fail(EventPlanningError.EVENT_DATE_AND_TIMES_REQUIRED)
      return null
    }

    val (startsAt, endsAt) = calculateStartAndEndInstants(date, startTime, endTime, clock, original)

    return try {
      EventDetails(
          type = state.type,
          subject = state.subject.trim().takeIf(String::isNotEmpty),
          startsAt = startsAt,
          endsAt = endsAt,
          location = state.location.trim().takeIf(String::isNotEmpty),
          description = state.description.trim().takeIf(String::isNotEmpty),
      )
    } catch (_: IllegalArgumentException) {
      fail(EventPlanningError.INVALID_EVENT)
      null
    }
  }

  /**
   * Saves the current form as a new event (when [isEditing] is false) or an update to
   * [editingEvent].
   */
  private fun save(isEditing: Boolean) {
    val state = _uiState.value
    if (state.isBusy) return

    val userId = currentUserId
    if (userId == null) {
      fail(EventPlanningError.NO_SIGNED_IN_USER)
      return
    }

    val details = validateAndBuildDetails(editingEvent?.details?.takeIf { isEditing }) ?: return
    val now = clock.instant()
    val defaultError =
        if (isEditing) EventPlanningError.FAILED_TO_EDIT_EVENT
        else EventPlanningError.FAILED_TO_ADD_EVENT

    _uiState.update { it.withoutResults().copy(isSaving = true) }
    viewModelScope.launch {
      try {
        val result =
            when (state.eventKind) {
              EventKind.PERSONAL -> savePersonalEvent(userId, details, state.tasks, now, isEditing)
              EventKind.GROUP_SESSION -> saveGroupSession(userId, details, now, isEditing)
            }
        result
            .onSuccess {
              pendingEventId = null
              editingEvent = null
              _uiState.update {
                EventPlanningUiState(
                    eventKind = it.eventKind,
                    recommendedSubjects = it.recommendedSubjects,
                    isEventAdded = !isEditing,
                    isEventEdited = isEditing,
                )
              }
            }
            .onFailure { exception ->
              _uiState.update { it.copy(error = exception.toUserError(defaultError)) }
            }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update { it.copy(error = exception.toUserError(defaultError)) }
      } finally {
        _uiState.update { it.copy(isSaving = false) }
      }
    }
  }

  /** Saves a [PersonalEvent], preserving its ID and creation timestamp when [isEditing]. */
  private suspend fun savePersonalEvent(
      userId: String,
      details: EventDetails,
      tasks: List<EventTask>,
      now: Instant,
      isEditing: Boolean,
  ): Result<Unit> {
    val editing = if (isEditing) editingEvent as? PersonalEvent else null
    val event =
        if (editing == null) {
          val id = pendingEventId ?: eventsRepository.newEventId().also { pendingEventId = it }
          PersonalEvent(
              id = id,
              ownerId = userId,
              details = details,
              createdAt = now,
              updatedAt = now,
              tasks = tasks,
          )
        } else {
          editing.copy(
              ownerId = userId,
              details = details,
              updatedAt = now,
              tasks = tasks,
          )
        }
    return eventsRepository.saveEvent(event)
  }

  /** Saves a [GroupSession], preserving its ID and creation timestamp when [isEditing]. */
  private suspend fun saveGroupSession(
      userId: String,
      details: EventDetails,
      now: Instant,
      isEditing: Boolean,
  ): Result<Unit> {
    val editing = if (isEditing) editingEvent as? GroupSession else null
    val session =
        if (editing == null) {
          val id =
              pendingEventId ?: groupSessionsRepository.newSessionId().also { pendingEventId = it }
          GroupSession(
              id = id,
              createdBy = userId,
              memberIds = listOf(userId),
              details = details,
              createdAt = now,
              updatedAt = now,
          )
        } else {
          editing.copy(details = details, updatedAt = now)
        }
    return groupSessionsRepository.saveSession(userId, session)
  }

  /** Fills the form from [event] and remembers it for [editEvent]. */
  private fun populateForm(event: Event) {
    val start = event.details.startsAt.atZone(clock.zone)
    val end = event.details.endsAt.atZone(clock.zone)
    editingEvent = event
    _uiState.update {
      it.copy(
          eventKind =
              when (event) {
                is PersonalEvent -> EventKind.PERSONAL
                is GroupSession -> EventKind.GROUP_SESSION
              },
          type = event.details.type,
          subject = event.details.subject.orEmpty(),
          date = start.toLocalDate(),
          startTime = start.toLocalTime(),
          endTime = end.toLocalTime(),
          location = event.details.location.orEmpty(),
          description = event.details.description.orEmpty(),
          tasks = (event as? PersonalEvent)?.tasks.orEmpty(),
          isEditing = true,
      )
    }
  }

  private fun fail(error: EventPlanningError) {
    _uiState.update { it.withoutResults().copy(error = error) }
  }

  /** Ensures tasks are only modified when planning a personal event. */
  private fun requirePersonalEvent() {
    // Debugging error for developer misuse, not a user-facing error; safe to keep as a Kotlin
    // string literal rather than in strings.xml.
    require(_uiState.value.showTasksSection) { "Tasks are only supported for personal events" }
  }

  /** Applies a form edit and clears the previous save result. */
  private fun updateForm(update: EventPlanningUiState.() -> EventPlanningUiState) {
    _uiState.update { it.update().withoutResults() }
  }

  /** Turns a repository failure into a stable [EventPlanningError] category for the UI. */
  private fun Throwable.toUserError(default: EventPlanningError): EventPlanningError =
      toEventPlanningError(default)
}

/** Supplies an injected repository when Android creates the settings ViewModel. */
class EventPlanningViewModelFactory(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val campusLocationRepository: CampusLocationRepository,
    private val groupSessionsRepository: GroupSessionsRepository,
    private val preferencesRepository: PreferencesRepository =
        PreferencesRepositoryProvider.getRepository(),
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
    return EventPlanningViewModel(
        authRepository,
        eventsRepository,
        campusLocationRepository,
        groupSessionsRepository,
        Clock.systemDefaultZone(),
        preferencesRepository,
    )
        as T
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
                campusLocationRepository =
                    CampusLocationRepositoryProvider.getRepository(LocalContext.current),
                groupSessionsRepository = GroupSessionsRepositoryProvider.getRepository(),
                preferencesRepository = PreferencesRepositoryProvider.getRepository(),
            )
    )
