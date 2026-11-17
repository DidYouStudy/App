// Co-authored-by: Gemini AI Agent
// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
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
import com.android.sample.model.event.EventsRepository
import com.android.sample.model.event.EventsRepositoryProvider
import com.android.sample.model.event.GroupSession
import com.android.sample.model.event.PersonalEvent
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import com.android.sample.model.profile.findUsername
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One member of a group session shown on the Event Details screen.
 *
 * @property userId authentication ID of the member.
 * @property username the member's username, or null when the profile is missing, blank, or failed
 *   to load.
 * @property isHost true for the session's creator, who is always listed first.
 */
data class EventMember(val userId: String, val username: String?, val isHost: Boolean)

/**
 * UI state for the Event Details screen.
 *
 * @property event the [PersonalEvent] or [com.android.sample.model.event.GroupSession] loaded for
 *   display, or null if not yet loaded or on error.
 * @property members members of a loaded group session, host first then in stored order; empty for a
 *   personal event.
 * @property isLoading true while an event is being loaded from the repository.
 * @property isDeleting true while the event is being deleted.
 * @property isUpdatingTasks true while a checklist update is being saved.
 * @property isEventDeleted set to true when the event is successfully deleted, cleared by
 *   [EventDetailsViewModel.onEventDeletedHandled].
 * @property error failure category to display to the user, or null when there is none.
 */
data class EventDetailsUiState(
    val event: Event? = null,
    val members: List<EventMember> = emptyList(),
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val isEventDeleted: Boolean = false,
    val error: EventPlanningError? = null,
    val isUpdatingTasks: Boolean = false,
) {
  /** True while loading, deleting, or saving a checklist change. */
  val isBusy: Boolean
    get() = isLoading || isDeleting || isUpdatingTasks
}

/**
 * ViewModel backing the Event Details screen.
 *
 * Provides event loading, event deletion, and task checklist toggling through repository contracts
 * ([AuthRepository] and [EventsRepository]).
 *
 * @param authRepository source of the currently authenticated user.
 * @param eventsRepository repository storing personal events.
 * @param groupSessionsRepository repository storing shared group sessions.
 * @param profileRepository source of member usernames for group sessions.
 * @param clock provider for current time and time zone.
 */
class EventDetailsViewModel(
    private val authRepository: AuthRepository,
    private val eventsRepository: EventsRepository,
    private val groupSessionsRepository: GroupSessionsRepository,
    private val profileRepository: ProfileRepository,
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
   * Loads the event of [kind] with [eventId] for the signed-in user.
   *
   * A personal event is read from [EventsRepository]. A group session is read from
   * [GroupSessionsRepository] and published together with its [EventDetailsUiState.members].
   *
   * @param eventId ID of the event to load.
   * @param kind which repository the event lives in.
   * @param refresh Fetch the event again even if it is already displayed.
   */
  fun loadEvent(eventId: String, kind: EventKind = EventKind.PERSONAL, refresh: Boolean = false) {
    if (eventId.isBlank() || _uiState.value.isBusy) return
    val displayed = _uiState.value.event
    if (!refresh && displayed?.id == eventId && displayed.kind() == kind) return

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
        when (kind) {
          EventKind.PERSONAL -> loadPersonalEvent(ownerId, eventId)
          EventKind.GROUP_SESSION -> loadGroupSession(eventId)
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        publishLoadFailure(exception)
      }
    }
  }

  /** Reads a personal event of [ownerId] and publishes it with no members. */
  private suspend fun loadPersonalEvent(ownerId: String, eventId: String) {
    eventsRepository
        .getEvent(ownerId, eventId)
        .onSuccess { event ->
          _uiState.update {
            it.copy(event = event, members = emptyList(), isLoading = false, error = null)
          }
        }
        .onFailure { exception -> publishLoadFailure(exception) }
  }

  /** Reads a group session, resolves its member usernames, and publishes both in one update. */
  private suspend fun loadGroupSession(eventId: String) {
    groupSessionsRepository
        .getSession(eventId)
        .onSuccess { session ->
          val members = resolveMembers(session)
          _uiState.update {
            it.copy(event = session, members = members, isLoading = false, error = null)
          }
        }
        .onFailure { exception -> publishLoadFailure(exception) }
  }

  /** Looks up the members of [session] concurrently, host first, keeping that order. */
  private suspend fun resolveMembers(session: GroupSession): List<EventMember> = coroutineScope {
    session
        .orderedMemberIds()
        .map { id ->
          async {
            EventMember(id, profileRepository.findUsername(id), isHost = id == session.createdBy)
          }
        }
        .awaitAll()
  }

  /** Clears the displayed event and members and shows the error mapped from [exception]. */
  private fun publishLoadFailure(exception: Throwable) {
    _uiState.update {
      it.copy(
          event = null,
          members = emptyList(),
          isLoading = false,
          error = exception.toUserError(EventPlanningError.FAILED_TO_LOAD_EVENT),
      )
    }
  }

  /** The [EventKind] this event is loaded as. */
  private fun Event.kind(): EventKind =
      when (this) {
        is PersonalEvent -> EventKind.PERSONAL
        is GroupSession -> EventKind.GROUP_SESSION
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

    if (currentEvent !is PersonalEvent) return
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
    val state = _uiState.value
    if (state.isBusy) return
    val currentEvent = state.event as? PersonalEvent ?: return
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

  /** Shows the toggle immediately and rolls it back if the checklist patch fails. */
  private fun updateEventTasks(previousEvent: PersonalEvent, updatedEvent: PersonalEvent) {
    _uiState.update { it.copy(event = updatedEvent, error = null, isUpdatingTasks = true) }
    viewModelScope.launch {
      try {
        eventsRepository
            .updateEventTasks(
                updatedEvent.ownerId,
                updatedEvent.id,
                updatedEvent.tasks,
                updatedEvent.updatedAt,
            )
            .getOrThrow()
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        _uiState.update {
          it.copy(
              event = previousEvent,
              error = exception.toUserError(EventPlanningError.FAILED_TO_EDIT_EVENT),
          )
        }
      } finally {
        _uiState.update { it.copy(isUpdatingTasks = false) }
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
    private val groupSessionsRepository: GroupSessionsRepository,
    private val profileRepository: ProfileRepository,
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
    return EventDetailsViewModel(
        authRepository,
        eventsRepository,
        groupSessionsRepository,
        profileRepository,
        clock,
    )
        as T
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
                groupSessionsRepository = GroupSessionsRepositoryProvider.getRepository(),
                profileRepository = ProfileRepositoryProvider.getRepository(),
            )
    )
