// Co-authored-by: Claude AI Agent
package com.android.sample.ui.groupsession

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.event.EventType
import com.android.sample.model.groupsession.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form values and state of the latest group session save request. */
data class AddEditGroupSessionUiState(
    val type: EventType = EventType.STUDY_SESSION,
    val subject: String = "",
    val date: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val location: String = "",
    val description: String = "",
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSessionSaved: Boolean = false,
    val error: String? = null,
) {
  /** The subject is required for every type except [EventType.OTHER]. */
  val isSubjectRequired: Boolean
    get() = type != EventType.OTHER

  /** Whether a save or load request is in progress. */
  val isBusy: Boolean
    get() = isSaving || isLoading
}

/**
 * ViewModel backing the group session add and edit workflow.
 *
 * Interacts strictly with repository interfaces ([AuthRepository] and [GroupSessionsRepository]).
 */
class AddEditGroupSessionViewModel(
    private val authRepository: AuthRepository,
    private val groupSessionsRepository: GroupSessionsRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

  companion object {
    const val SESSION_DATE_AND_TIMES_REQUIRED_ERROR =
        "Session date, start time, and end time are required"
    const val INVALID_SESSION_ERROR = "Invalid session details"
    const val FAILED_TO_SAVE_SESSION_ERROR = "Failed to save group session"
  }

  private val _uiState = MutableStateFlow(AddEditGroupSessionUiState())
  val uiState: StateFlow<AddEditGroupSessionUiState> = _uiState.asStateFlow()

  private var loadedSession: GroupSession? = null
  private var loadJob: Job? = null

  /** Loads an existing group session into the form state for editing. */
  fun loadSession(sessionId: String) {
    if (sessionId.isBlank()) return
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      loadedSession = null
      _uiState.update {
        it.copy(
            isLoading = true,
            isEditing = false,
            type = EventType.STUDY_SESSION,
            subject = "",
            date = null,
            startTime = null,
            endTime = null,
            location = "",
            description = "",
            error = null,
        )
      }
      val result = groupSessionsRepository.getSession(sessionId)
      result.fold(
          onSuccess = { session ->
            loadedSession = session
            val zoneId = clock.zone
            val startZdt = session.startsAt.atZone(zoneId)
            val endZdt = session.endsAt.atZone(zoneId)

            _uiState.update {
              it.copy(
                  isLoading = false,
                  isEditing = true,
                  type = session.type,
                  subject = session.subject ?: "",
                  date = startZdt.toLocalDate(),
                  startTime = startZdt.toLocalTime(),
                  endTime = endZdt.toLocalTime(),
                  location = session.location ?: "",
                  description = session.description ?: "",
                  error = null,
              )
            }
          },
          onFailure = {
            loadedSession = null
            _uiState.update {
              it.copy(
                  isLoading = false,
                  isEditing = false,
                  type = EventType.STUDY_SESSION,
                  error = FAILED_TO_SAVE_SESSION_ERROR,
              )
            }
          },
      )
    }
  }

  fun setType(type: EventType) {
    _uiState.update { it.copy(type = type, error = null) }
  }

  fun setSubject(subject: String) {
    _uiState.update { it.copy(subject = subject, error = null) }
  }

  fun setDate(date: LocalDate) {
    _uiState.update { it.copy(date = date, error = null) }
  }

  fun setStartTime(time: LocalTime) {
    _uiState.update { it.copy(startTime = time, error = null) }
  }

  fun setEndTime(time: LocalTime) {
    _uiState.update { it.copy(endTime = time, error = null) }
  }

  fun setLocation(location: String) {
    _uiState.update { it.copy(location = location, error = null) }
  }

  fun setDescription(description: String) {
    _uiState.update { it.copy(description = description, error = null) }
  }

  fun onSessionSavedHandled() {
    _uiState.update { it.copy(isSessionSaved = false) }
  }

  /** Validates the current form inputs and creates or updates the group session. */
  fun saveSession() {
    val state = _uiState.value

    if (state.isBusy) return

    if (state.date == null || state.startTime == null || state.endTime == null) {
      _uiState.update { it.copy(error = SESSION_DATE_AND_TIMES_REQUIRED_ERROR) }
      return
    }

    val trimmedSubject = state.subject.trim()
    if (state.isSubjectRequired && trimmedSubject.isBlank()) {
      _uiState.update { it.copy(error = INVALID_SESSION_ERROR) }
      return
    }

    val zoneId = clock.zone
    val startInstant = state.date.atTime(state.startTime).atZone(zoneId).toInstant()
    var endZdt = state.date.atTime(state.endTime).atZone(zoneId)
    if (state.endTime.isBefore(state.startTime)) {
      endZdt = endZdt.plusDays(1)
    }
    val endInstant = endZdt.toInstant()

    if (
        !endInstant.isAfter(startInstant) ||
            Duration.between(startInstant, endInstant) > GroupSession.MAX_DURATION
    ) {
      _uiState.update { it.copy(error = INVALID_SESSION_ERROR) }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, error = null) }

      val currentUser = authRepository.currentUser.firstOrNull()
      val userId = currentUser?.uid

      if (userId == null) {
        _uiState.update { it.copy(isSaving = false, error = FAILED_TO_SAVE_SESSION_ERROR) }
        return@launch
      }

      val now = clock.instant()
      val location = state.location.trim().ifBlank { null }
      val description = state.description.trim().ifBlank { null }
      val subject = trimmedSubject.ifBlank { null }

      val sessionToSave =
          if (state.isEditing && loadedSession != null) {
            val existing = loadedSession!!
            existing.copy(
                type = state.type,
                subject = subject,
                startsAt = startInstant,
                endsAt = endInstant,
                location = location,
                description = description,
                updatedAt = now,
            )
          } else {
            val id = groupSessionsRepository.newSessionId()
            GroupSession(
                id = id,
                createdBy = userId,
                memberIds = listOf(userId),
                type = state.type,
                subject = subject,
                startsAt = startInstant,
                endsAt = endInstant,
                createdAt = now,
                updatedAt = now,
                location = location,
                description = description,
            )
          }

      val result = groupSessionsRepository.saveSession(userId, sessionToSave)
      result.fold(
          onSuccess = {
            _uiState.update { it.copy(isSaving = false, isSessionSaved = true, error = null) }
          },
          onFailure = {
            _uiState.update { it.copy(isSaving = false, error = FAILED_TO_SAVE_SESSION_ERROR) }
          },
      )
    }
  }
}

/** Factory for creating [AddEditGroupSessionViewModel] instances with injected repositories. */
class AddEditGroupSessionViewModelFactory(
    private val authRepository: AuthRepository,
    private val groupSessionsRepository: GroupSessionsRepository,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == AddEditGroupSessionViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return AddEditGroupSessionViewModel(authRepository, groupSessionsRepository) as T
  }
}

/** Returns a ViewModel retained across recompositions for the add/edit session flow. */
@Composable
fun addEditGroupSessionViewModel(): AddEditGroupSessionViewModel =
    viewModel(
        factory =
            AddEditGroupSessionViewModelFactory(
                authRepository = AuthRepositoryProvider.getRepository(LocalContext.current),
                groupSessionsRepository = GroupSessionsRepositoryProvider.getRepository(),
            )
    )
