// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationRepository
import com.android.sample.model.location.CampusLocationRepositoryProvider
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.DistanceCalculator
import com.android.sample.model.location.Location
import com.android.sample.model.location.LocationProvider
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

/** Start-time range used by the group-session filter sheet. */
enum class GroupSessionTimeFilter {
  /** Do not constrain the session start time. */
  ANYTIME,

  /** Keep sessions starting within the next hour. */
  WITHIN_ONE_HOUR,

  /** Keep sessions starting today in the device's local time zone. */
  TODAY,

  /** Keep sessions starting during the current Monday-to-Monday week. */
  THIS_WEEK,
}

/** A stable failure category for the screen to map to a localized message. */
enum class GroupSessionsError {
  /** The session list could not be observed. */
  LOAD_FAILED,

  /** Bundled campus locations could not be loaded for proximity filtering. */
  LOCATION_LOAD_FAILED,

  /** The current user could not join the selected session. */
  JOIN_FAILED,

  /** The current user could not leave the selected session. */
  LEAVE_FAILED,
}

/**
 * How often the reference time is refreshed while the screen is visible.
 *
 * Status badges change at most once a minute, so a badge is never more than 30 seconds late.
 */
private val NOW_REFRESH_INTERVAL = 30.seconds

/** Returns this error unless it is LOAD_FAILED, which a new list or refresh resolves. */
private fun GroupSessionsError?.withoutLoadFailure() = takeUnless {
  it == GroupSessionsError.LOAD_FAILED
}

/** One row of the list, including the current user's membership and creator relationship. */
data class GroupSessionItem(
    val session: GroupSession,
    val isMember: Boolean,
    val isCreator: Boolean,
    val proximity: SessionWithProximity = SessionWithProximity(session, null, null, null),
)

/** A session enriched with its resolved room and optional distance from the user. */
data class SessionWithProximity(
    val session: GroupSession,
    val campusLocation: CampusLocation?,
    val distanceMeters: Double?,
    val formattedDistance: String?,
)

/**
 * State of the Group sessions screen; [sessions] is already searched and filtered.
 *
 * The query retains the user's typed text while matching ignores case and surrounding whitespace.
 * [now] is the reference time every card uses for its relative labels, so they all agree. It has no
 * default value, so that every new state has to supply the current time.
 */
data class GroupSessionsUiState(
    val now: Instant,
    val isLoading: Boolean = true,
    val sessions: List<GroupSessionItem> = emptyList(),
    val query: String = "",
    val filter: GroupSessionFilter = GroupSessionFilter.ALL,
    val selectedSessionTypes: Set<EventType> = emptySet(),
    val startingTimeFilter: GroupSessionTimeFilter = GroupSessionTimeFilter.ANYTIME,
    val selectedRadiusMeters: Int? = null,
    val selectedZone: String? = null,
    val selectedZones: Set<String> = emptySet(),
    val showLabRoomType: Boolean = true,
    val hideFullSessions: Boolean = false,
    val userLocation: Location? = null,
    val error: GroupSessionsError? = null,
) {
  /** Alias that makes clear the list already reflects all active criteria. */
  val filteredSessions: List<GroupSessionItem>
    get() = sessions
}

/**
 * Observes public upcoming sessions for the signed-in user and exposes searched, filtered UI state.
 *
 * Sessions are sorted by the repository. Search checks subject, location, and description without
 * case sensitivity; joining and leaving use the current user's identity. Session type, start time,
 * proximity, zone, room type, and capacity criteria are applied together to that list. A refreshed
 * observation uses the current clock instant, because an existing repository flow retains its
 * original cutoff. Successful list emissions and refreshes clear only
 * [GroupSessionsError.LOAD_FAILED]; action errors remain until cleared or the observed user ID
 * changes. Action results from an earlier observed user ID are ignored. [GroupSessionsUiState.now]
 * is refreshed from [clock] while [uiState] has a collector, immediately when a collector returns,
 * and when the list is observed again.
 */
class GroupSessionsViewModel(
    private val authRepository: AuthRepository,
    private val repository: GroupSessionsRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val locationProvider: LocationProvider = LocationProvider { flowOf(null) },
    private val campusLocationRepository: CampusLocationRepository? = null,
) : ViewModel() {
  private val _uiState = MutableStateFlow(GroupSessionsUiState(now = clock.instant()))
  /** The current loading, list, search, filter, and categorized-error state. */
  val uiState: StateFlow<GroupSessionsUiState> = _uiState.asStateFlow()

  private val refreshVersion = MutableStateFlow(0)
  private var currentUserId: String? = null
  /** Distinguishes action requests made during different observed sign-in periods. */
  private var authGeneration = 0L
  private var observedSessions: List<GroupSession> = emptyList()
  private var campusLocationsByName: Map<String, CampusLocation> = emptyMap()

  init {
    refreshNowWhileObserved()
    observeLocationWhileObserved()
    loadCampusLocations()
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
              _uiState.value = GroupSessionsUiState(now = clock.instant(), isLoading = false)
            } else {
              val observedFrom = clock.instant()
              updateVisibleSessions()
              _uiState.update {
                it.copy(
                    now = observedFrom,
                    isLoading = true,
                    error =
                        if (userChanged) {
                          null
                        } else {
                          it.error.withoutLoadFailure()
                        },
                )
              }
              // Keep the list cutoff and badge reference time aligned.
              repository.observeSessions(observedFrom).collect { result ->
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

  /** Selects a session type subset; an empty set means all session types. */
  fun onSessionTypesSelected(types: Set<EventType>) {
    viewModelScope.launch {
      _uiState.update { it.copy(selectedSessionTypes = types) }
      updateVisibleSessions()
    }
  }

  /** Selects the start-time range used to filter sessions. */
  fun onStartingTimeFilterSelected(filter: GroupSessionTimeFilter) {
    viewModelScope.launch {
      _uiState.update { it.copy(startingTimeFilter = filter) }
      updateVisibleSessions()
    }
  }

  /** Selects a positive radius in meters, or `null` to show any distance. */
  fun onRadiusFilterSelected(radius: Int?) {
    require(radius == null || radius > 0) { "Radius must be positive or null" }
    viewModelScope.launch {
      _uiState.update { it.copy(selectedRadiusMeters = radius) }
      updateVisibleSessions()
    }
  }

  /** Selects one campus zone, or `null` to show sessions from every zone. */
  fun onZoneFilterSelected(zone: String?) {
    onZonesFilterSelected(zone?.takeIf { it.isNotBlank() }?.let(::setOf).orEmpty())
  }

  /** Selects multiple campus zones; an empty set means every zone. */
  fun onZonesFilterSelected(zones: Set<String>) {
    val normalizedZones = zones.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet()
    viewModelScope.launch {
      _uiState.update {
        it.copy(selectedZone = normalizedZones.singleOrNull(), selectedZones = normalizedZones)
      }
      updateVisibleSessions()
    }
  }

  /** Controls whether rooms classified as laboratories can appear in the results. */
  fun onShowLabRoomTypeChange(show: Boolean) {
    viewModelScope.launch {
      _uiState.update { it.copy(showLabRoomType = show) }
      updateVisibleSessions()
    }
  }

  /** Hides sessions that have reached the maximum member count when enabled. */
  fun onHideFullSessionsChange(hide: Boolean) {
    viewModelScope.launch {
      _uiState.update { it.copy(hideFullSessions = hide) }
      updateVisibleSessions()
    }
  }

  /** Restores the advanced filter criteria without changing search or membership filters. */
  fun resetAdvancedFilters() {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
            selectedSessionTypes = emptySet(),
            startingTimeFilter = GroupSessionTimeFilter.ANYTIME,
            selectedRadiusMeters = null,
            selectedZone = null,
            selectedZones = emptySet(),
            showLabRoomType = true,
            hideFullSessions = false,
        )
      }
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

  /**
   * Keeps the reference time current only while the screen collects [uiState].
   *
   * The loop stops with the last collector, so a hidden screen does no work, and restarts with an
   * immediate update, so returning to the screen never shows an outdated time.
   */
  private fun refreshNowWhileObserved() {
    viewModelScope.launch {
      _uiState.subscriptionCount
          .map { it > 0 }
          .distinctUntilChanged()
          .collectLatest { isObserved ->
            while (isObserved) {
              _uiState.update { it.copy(now = clock.instant()) }
              updateVisibleSessions()
              delay(NOW_REFRESH_INTERVAL)
            }
          }
    }
  }

  /** Collects device location only while the UI state has an active observer. */
  private fun observeLocationWhileObserved() {
    viewModelScope.launch {
      _uiState.subscriptionCount
          .map { it > 0 }
          .distinctUntilChanged()
          .collectLatest { isObserved ->
            val locations =
                if (isObserved) locationProvider.observeCurrentLocation() else emptyFlow()
            locations.collect { location ->
              _uiState.update { it.copy(userLocation = location) }
              updateVisibleSessions()
            }
          }
    }
  }

  /** Loads campus room metadata once so later GPS updates never re-fetch session data. */
  private fun loadCampusLocations() {
    val locationRepository = campusLocationRepository ?: return
    viewModelScope.launch {
      locationRepository
          .getAllLocations()
          .fold(
              onSuccess = { locations ->
                campusLocationsByName = locations.associateBy { normalizeRoomName(it.name) }
                updateVisibleSessions()
              },
              onFailure = {
                _uiState.update { it.copy(error = GroupSessionsError.LOCATION_LOAD_FAILED) }
              },
          )
    }
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
                    proximity = session.proximity(state.userLocation),
                )
              }
              .filter { item ->
                (query.isEmpty() ||
                    listOfNotNull(
                            item.session.details.subject,
                            item.session.details.location,
                            item.session.details.description,
                        )
                        .any { it.contains(query, ignoreCase = true) }) &&
                    (state.filter != GroupSessionFilter.JOINED || item.isMember) &&
                    (state.selectedSessionTypes.isEmpty() ||
                        item.session.details.type in state.selectedSessionTypes) &&
                    matchesStartingTime(item.session.details.startsAt, state) &&
                    (item.proximity.distanceMeters == null ||
                        state.userLocation == null ||
                        state.selectedRadiusMeters == null ||
                        item.proximity.distanceMeters <= state.selectedRadiusMeters) &&
                    (state.selectedZones.isEmpty() ||
                        item.proximity.campusLocation?.zone?.let { zone ->
                          state.selectedZones.any { it.trim().equals(zone, ignoreCase = true) }
                        } == true) &&
                    (state.showLabRoomType ||
                        item.proximity.campusLocation?.type != CampusLocationType.LAB) &&
                    (!state.hideFullSessions ||
                        item.session.memberIds.size < GroupSession.MAX_MEMBERS)
              }
      state.copy(sessions = matchingSessions)
    }
  }

  private fun GroupSession.proximity(userLocation: Location?): SessionWithProximity {
    val campusLocation = details.location?.let(::normalizeRoomName)?.let(campusLocationsByName::get)
    val distanceMeters =
        if (userLocation == null || campusLocation == null) {
          null
        } else {
          DistanceCalculator.distanceMeters(userLocation, campusLocation.location)
        }
    return SessionWithProximity(
        session = this,
        campusLocation = campusLocation,
        distanceMeters = distanceMeters,
        formattedDistance = distanceMeters?.let(::formatDistance),
    )
  }

  /** Matches a session start against the chosen local-time range. */
  private fun matchesStartingTime(start: Instant, state: GroupSessionsUiState): Boolean {
    val zone = ZoneId.systemDefault()
    val startDate = start.atZone(zone).toLocalDate()
    val today = state.now.atZone(zone).toLocalDate()
    return when (state.startingTimeFilter) {
      GroupSessionTimeFilter.ANYTIME -> true
      GroupSessionTimeFilter.WITHIN_ONE_HOUR ->
          !start.isBefore(state.now) && !start.isAfter(state.now.plusSeconds(60 * 60))
      GroupSessionTimeFilter.TODAY -> startDate == today
      GroupSessionTimeFilter.THIS_WEEK -> {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        startDate >= weekStart && startDate < weekStart.plusDays(7)
      }
    }
  }

  /** Normalizes room labels so spacing and capitalization do not block location resolution. */
  private fun normalizeRoomName(name: String): String =
      name.filterNot(Char::isWhitespace).lowercase(Locale.ROOT)

  /** Formats nearby distances in meters and longer distances to one decimal kilometer. */
  private fun formatDistance(distanceMeters: Double): String =
      if (distanceMeters < 1000) {
        "${distanceMeters.toInt()}m"
      } else {
        String.format(Locale.ROOT, "%.1fkm", distanceMeters / 1000)
      }
}

/** Creates [GroupSessionsViewModel] instances using injected repository interfaces. */
class GroupSessionsViewModelFactory(
    private val authRepository: AuthRepository,
    private val repository: GroupSessionsRepository,
    private val locationProvider: LocationProvider = LocationProvider { flowOf(null) },
    private val campusLocationRepository: CampusLocationRepository? = null,
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
    return GroupSessionsViewModel(
        authRepository,
        repository,
        locationProvider = locationProvider,
        campusLocationRepository = campusLocationRepository,
    )
        as T
  }
}

/**
 * Returns the screen's ViewModel backed by the app-wide shared repositories.
 *
 * The Compose ViewModel owner retains the same instance across recompositions.
 */
@Composable
fun groupSessionsViewModel(): GroupSessionsViewModel {
  val context = LocalContext.current
  return viewModel(
      factory =
          GroupSessionsViewModelFactory(
              authRepository = AuthRepositoryProvider.getRepository(context),
              repository = GroupSessionsRepositoryProvider.getRepository(),
              campusLocationRepository = CampusLocationRepositoryProvider.getRepository(context),
          )
  )
}
