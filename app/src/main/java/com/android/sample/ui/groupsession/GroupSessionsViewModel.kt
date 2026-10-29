// Co-authored-by: GitHub Copilot
package com.android.sample.ui.groupsession

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.groupsession.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which sessions the list shows: all visible sessions or only those containing the current user.
 */
enum class GroupSessionFilter {
  /** Show every session that has not ended. */
  ALL,

  /** Show sessions where the current user is a member, including sessions they created. */
  JOINED,
}

/** A stable failure category for the screen to map to a localized message. */
enum class GroupSessionsError {
  /** The session list could not be observed. */
  LOAD_FAILED,

  /** The current user could not join the selected session. */
  JOIN_FAILED,

  /** The current user could not leave the selected session. */
  LEAVE_FAILED,
}

/** Returns this error unless it is LOAD_FAILED, which a new list or refresh resolves. */
private fun GroupSessionsError?.withoutLoadFailure() = takeUnless {
  it == GroupSessionsError.LOAD_FAILED
}

/** One row of the list, including the current user's membership and creator relationship. */
data class GroupSessionItem(
    val session: GroupSession,
    val isMember: Boolean,
    val isCreator: Boolean,
)

/**
 * State of the Group sessions screen; [sessions] is already searched and filtered.
 *
 * The query retains the user's typed text while matching ignores case and surrounding whitespace.
 */
data class GroupSessionsUiState(
    val isLoading: Boolean = true,
    val sessions: List<GroupSessionItem> = emptyList(),
    val query: String = "",
    val filter: GroupSessionFilter = GroupSessionFilter.ALL,
    val error: GroupSessionsError? = null,
)

/**
 * Observes public upcoming sessions for the signed-in user and exposes searched, filtered UI state.
 *
 * Sessions are sorted by the repository. Search checks subject, location, and description without
 * case sensitivity; joining and leaving use the current user's identity. A refreshed observation
 * uses the current clock instant, because an existing repository flow retains its original cutoff.
 * Successful list emissions and refreshes clear only [GroupSessionsError.LOAD_FAILED]; action
 * errors remain until cleared or the observed user ID changes. Action results from an earlier
 * observed user ID are ignored.
 */
class GroupSessionsViewModel(
    private val authRepository: AuthRepository,
    private val repository: GroupSessionsRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(GroupSessionsUiState())
  /** The current loading, list, search, filter, and categorized-error state. */
  val uiState: StateFlow<GroupSessionsUiState> = _uiState.asStateFlow()

  private val refreshVersion = MutableStateFlow(0)
  private var currentUserId: String? = null
  /** Distinguishes action requests made during different observed sign-in periods. */
  private var authGeneration = 0L
  private var observedSessions: List<GroupSession> = emptyList()

  init {
    viewModelScope.launch {
      combine(authRepository.currentUser, refreshVersion) { user, _ -> user }
          .collectLatest { user ->
            val userId = user?.uid
            val userChanged = userId != currentUserId
            if (userChanged) {
              currentUserId = userId
              authGeneration += 1
            }
            if (user == null) {
              observedSessions = emptyList()
              _uiState.value = GroupSessionsUiState(isLoading = false)
            } else {
              updateVisibleSessions()
              _uiState.update {
                it.copy(
                    isLoading = true,
                    error =
                        if (userChanged) {
                          null
                        } else {
                          it.error.withoutLoadFailure()
                        },
                )
              }
              repository.observeSessions(clock.instant()).collect { result ->
                result.fold(
                    onSuccess = { sessions ->
                      observedSessions = sessions
                      updateVisibleSessions()
                      _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = it.error.withoutLoadFailure(),
                        )
                      }
                    },
                    onFailure = {
                      _uiState.update {
                        it.copy(isLoading = false, error = GroupSessionsError.LOAD_FAILED)
                      }
                    },
                )
              }
            }
          }
    }
  }

  /** Updates the search text and recomputes the visible sessions. */
  fun onQueryChange(query: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(query = query) }
      updateVisibleSessions()
    }
  }

  /** Updates the list filter and recomputes the visible sessions. */
  fun onFilterChange(filter: GroupSessionFilter) {
    viewModelScope.launch {
      _uiState.update { it.copy(filter = filter) }
      updateVisibleSessions()
    }
  }

  /**
   * Joins [sessionId] as the signed-in user and reports failures as
   * [GroupSessionsError.JOIN_FAILED] only if the observed user ID has not changed while joining.
   * The error remains until [clearError] or a change in the observed user ID.
   */
  fun join(sessionId: String) {
    viewModelScope.launch {
      val userId = currentUserId ?: return@launch
      val requestGeneration = authGeneration
      if (
          repository.joinSession(userId, sessionId).isFailure && requestGeneration == authGeneration
      ) {
        _uiState.update { it.copy(error = GroupSessionsError.JOIN_FAILED) }
      }
    }
  }

  /**
   * Leaves [sessionId] as the signed-in user and reports failures as
   * [GroupSessionsError.LEAVE_FAILED] only if the observed user ID has not changed while leaving.
   * The error remains until [clearError] or a change in the observed user ID.
   */
  fun leave(sessionId: String) {
    viewModelScope.launch {
      val userId = currentUserId ?: return@launch
      val requestGeneration = authGeneration
      if (
          repository.leaveSession(userId, sessionId).isFailure &&
              requestGeneration == authGeneration
      ) {
        _uiState.update { it.copy(error = GroupSessionsError.LEAVE_FAILED) }
      }
    }
  }

  /**
   * Observes again from the current time, so sessions that ended meanwhile disappear.
   *
   * Without a refresh, the repository's flow continues using its original time cutoff. Refresh
   * clears only [GroupSessionsError.LOAD_FAILED], preserving action errors.
   */
  fun refresh() {
    viewModelScope.launch { refreshVersion.update { it + 1 } }
  }

  /** Clears the categorized error without changing the rest of the screen state. */
  fun clearError() {
    viewModelScope.launch { _uiState.update { it.copy(error = null) } }
  }

  private fun updateVisibleSessions() {
    val userId = currentUserId ?: return
    _uiState.update { state ->
      val query = state.query.trim()
      val matchingSessions =
          observedSessions
              .map { session ->
                GroupSessionItem(
                    session = session,
                    isMember = userId in session.memberIds,
                    isCreator = userId == session.createdBy,
                )
              }
              .filter { item ->
                (query.isEmpty() ||
                    listOfNotNull(
                            item.session.subject,
                            item.session.location,
                            item.session.description,
                        )
                        .any { it.contains(query, ignoreCase = true) }) &&
                    (state.filter != GroupSessionFilter.JOINED || item.isMember)
              }
      state.copy(sessions = matchingSessions)
    }
  }
}

/** Creates [GroupSessionsViewModel] instances using injected repository interfaces. */
class GroupSessionsViewModelFactory(
    private val authRepository: AuthRepository,
    private val repository: GroupSessionsRepository,
) : ViewModelProvider.Factory {
  /**
   * Creates the requested ViewModel and rejects unsupported classes with their class name.
   *
   * @throws IllegalArgumentException when [modelClass] is not [GroupSessionsViewModel].
   */
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass == GroupSessionsViewModel::class.java) {
      "Unsupported ViewModel: ${modelClass.name}"
    }
    @Suppress("UNCHECKED_CAST")
    return GroupSessionsViewModel(authRepository, repository) as T
  }
}

/**
 * Returns the screen's ViewModel backed by the app-wide shared repositories.
 *
 * The Compose ViewModel owner retains the same instance across recompositions.
 */
@Composable
fun groupSessionsViewModel(): GroupSessionsViewModel =
    viewModel(
        factory =
            GroupSessionsViewModelFactory(
                authRepository = AuthRepositoryProvider.getRepository(LocalContext.current),
                repository = GroupSessionsRepositoryProvider.getRepository(),
            )
    )
