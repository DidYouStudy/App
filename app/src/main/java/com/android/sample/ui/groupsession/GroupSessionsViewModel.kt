// Co-authored-by: GitHub Copilot
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini <noreply@google.com>
// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: OpenAI Codex
package com.android.sample.ui.groupsession

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.model.connectivity.AndroidConnectivityObserver
import com.android.sample.model.connectivity.ConnectionLostException
import com.android.sample.model.connectivity.ConnectivityObserver
import com.android.sample.model.connectivity.runOnlineWrite
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.GroupSessionsRepositoryProvider
import com.android.sample.model.location.AndroidLocationProvider
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationRepository
import com.android.sample.model.location.CampusLocationRepositoryProvider
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.DistanceCalculator
import com.android.sample.model.location.Location
import com.android.sample.model.location.LocationProvider
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileRepositoryProvider
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
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

  /** An action that needs the server was attempted while the device is offline. */
  OFFLINE,

  /** The connection dropped, or the server did not answer, in the middle of a join or leave. */
  CONNECTION_LOST,
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

/**
 * One row of the list, including membership, creator relationship, and the known host username.
 *
 * [hostName] is null until a nonblank username has been loaded for another user's profile.
 * [hostPictureUrl] comes from the same profile; it is also null when that host has no picture.
 */
data class GroupSessionItem(
    val session: GroupSession,
    val isMember: Boolean,
    val isCreator: Boolean,
    val hostName: String? = null,
    val proximity: SessionWithProximity = SessionWithProximity(session, null, null, null),
    val isActionInProgress: Boolean = false,
    val hostPictureUrl: String? = null,
)

/** A session enriched with its resolved room and optional distance from the user. */
data class SessionWithProximity(
    val session: GroupSession,
    val campusLocation: CampusLocation?,
    val distanceMeters: Double?,
    val formattedDistance: String?,
)

/**
 * A visible roster row and the outcome of looking up its profile username.
 *
 * [profilePictureUrl] is null without a loaded username, or when the member has no picture.
 */
data class GroupSessionParticipant(
    val userId: String,
    val username: String?,
    val isHost: Boolean,
    val isCurrentUser: Boolean,
    val isUnavailable: Boolean = false,
    val profilePictureUrl: String? = null,
)

/** The open roster, including the full count while individual profiles are loading. */
data class GroupSessionParticipantsSheet(
    val sessionSubject: String?,
    val participantCount: Int,
    val rows: List<GroupSessionParticipant>,
    val isLoading: Boolean,
) {
  val hasError: Boolean
    get() = rows.any { it.isUnavailable }
}

/**
 * State of the Group sessions screen; [sessions] is already searched and filtered.
 *
 * The query retains the user's typed text while matching ignores case and surrounding whitespace.
 * [now] is the reference time every card uses for its relative labels, so they all agree. It has no
 * default value, so that every new state has to supply the current time. Laboratory rooms are
 * hidden by default and shown only when [showLabRoomType] is enabled. [isOffline] is true while the
 * device has no internet connection; [sessions] then holds the sessions already loaded and saved on
 * the device, and the screen shows an offline indication.
 */
data class GroupSessionsUiState(
    val now: Instant,
    val isLoading: Boolean = true,
    val sessions: List<GroupSessionItem> = emptyList(),
    val rooms: List<RoomSessions> = emptyList(),
    val query: String = "",
    val filter: GroupSessionFilter = GroupSessionFilter.ALL,
    val selectedSessionTypes: Set<EventType> = emptySet(),
    val startingTimeFilter: GroupSessionTimeFilter = GroupSessionTimeFilter.ANYTIME,
    val selectedRadiusMeters: Int? = null,
    val selectedZones: Set<String> = emptySet(),
    val availableZones: List<String> = emptyList(),
    val showLabRoomType: Boolean = false,
    val hideFullSessions: Boolean = false,
    val userLocation: Location? = null,
    val campusCenter: Location? = null,
    val error: GroupSessionsError? = null,
    val isOffline: Boolean = false,
    val participantsSheet: GroupSessionParticipantsSheet? = null,
)

/**
 * Observes public upcoming sessions for the signed-in user and exposes searched, filtered UI state.
 *
 * Sessions are sorted by the repository. Search checks subject, location, and description without
 * case sensitivity; joining and leaving use the current user's identity. Session type, start time,
 * proximity, zone, room type, and capacity criteria are applied together to that list. A refreshed
 * observation uses the current clock instant, because an existing repository flow retains its
 * original cutoff. Successful list emissions and refreshes clear only
 * [GroupSessionsError.LOAD_FAILED]; action errors remain until cleared or the observed user ID
 * changes. Action results from an earlier observed user ID are ignored. Host usernames are loaded
 * asynchronously through [profileRepository], so profile lookups never delay the session list.
 * [GroupSessionsUiState.now] is refreshed from [clock] while [uiState] has a collector, immediately
 * when a collector returns, and when the list is observed again.
 *
 * Offline, the repository keeps serving the sessions it already loaded from its local cache, so the
 * list, search, and filters keep working without a connection. [connectivityObserver] drives
 * [GroupSessionsUiState.isOffline] while [uiState] has a collector, which is what the screen shows
 * and what group session actions check. Joining and leaving are online-only: while offline they
 * send nothing and report [GroupSessionsError.OFFLINE], and if the connection drops (or the server
 * does not answer in time) while one is running it is cancelled, its progress indicator is cleared
 * and [GroupSessionsError.CONNECTION_LOST] is reported. Both work again as soon as the connection
 * returns. [connectivityObserver] is null when connectivity is not tracked, in which case the state
 * never reports offline.
 */
class GroupSessionsViewModel(
    private val authRepository: AuthRepository,
    private val repository: GroupSessionsRepository,
    private val profileRepository: ProfileRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val locationProvider: LocationProvider,
    private val campusLocationRepository: CampusLocationRepository? = null,
    private val connectivityObserver: ConnectivityObserver? = null,
    private val campusCenter: Location,
) : ViewModel() {
  private val _uiState =
      MutableStateFlow(
          GroupSessionsUiState(
              now = clock.instant(),
              campusCenter = campusCenter,
          )
      )
  /** The current loading, list, search, filter, and categorized-error state. */
  val uiState: StateFlow<GroupSessionsUiState> = _uiState.asStateFlow()

  private val refreshVersion = MutableStateFlow(0)
  private val locationRefreshTrigger = MutableStateFlow(0)
  private var currentUserId: String? = null
  /** Distinguishes action requests made during different observed sign-in periods. */
  private var authGeneration = 0L
  private var observedSessions: List<GroupSession> = emptyList()
  private var campusLocationsByName: Map<String, CampusLocation> = emptyMap()
  /**
   * Profiles that have a username, kept until a refresh or a change of signed-in user. Opening a
   * roster reads its members again.
   */
  private val profilesByUserId = mutableMapOf<String, CachedProfile>()
  private val usernameLookupsInProgress = mutableSetOf<String>()
  /** Session IDs whose join or leave request is currently running. */
  private val actionRequestsInProgress = mutableSetOf<String>()
  private var participantsSessionId: String? = null
  private val participantNamesWithoutUsername = mutableMapOf<String, ParticipantNameStatus>()

  /** What the screen shows of a member; [profilePictureUrl] is null rather than blank. */
  private data class CachedProfile(val username: String, val profilePictureUrl: String?)

  private enum class ParticipantNameStatus {
    UNKNOWN,
    UNAVAILABLE,
  }

  init {
    refreshNowWhileObserved()
    observeLocationWhileObserved()
    observeConnectivityWhileObserved()
    loadCampusLocations()
    viewModelScope.launch {
      combine(authRepository.currentUser, refreshVersion) { user, _ -> user }
          .collectLatest { user ->
            val userId = user?.uid
            val userChanged = userId != currentUserId
            if (userChanged) {
              currentUserId = userId
              authGeneration += 1
              profilesByUserId.clear()
              usernameLookupsInProgress.clear()
              actionRequestsInProgress.clear()
              clearParticipants()
            }
            if (user == null) {
              observedSessions = emptyList()
              _uiState.value =
                  GroupSessionsUiState(
                      now = clock.instant(),
                      isLoading = false,
                      availableZones = _uiState.value.availableZones,
                      // Preserve user location if authRepository emits null after a location
                      // already emitted.
                      userLocation = _uiState.value.userLocation,
                      campusCenter = campusCenter,
                      // Connectivity belongs to the device, not to the signed-in user.
                      isOffline = _uiState.value.isOffline,
                  )
            } else {
              val observedFrom = clock.instant()
              updateVisibleSessions()
              _uiState.update {
                it.copy(
                    now = observedFrom,
                    isLoading = true,
                    error =
                        if (userChanged) {
                          it.error.takeIf { error ->
                            error == GroupSessionsError.LOCATION_LOAD_FAILED
                          }
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
                      lookupHostNames(sessions)
                      loadParticipantNames()
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
      _uiState.update { it.copy(selectedZones = normalizedZones) }
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

  /**
   * Resets advanced criteria, including hiding labs, without changing search or membership filters.
   */
  fun resetAdvancedFilters() {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
            selectedSessionTypes = emptySet(),
            startingTimeFilter = GroupSessionTimeFilter.ANYTIME,
            selectedRadiusMeters = null,
            selectedZones = emptySet(),
            showLabRoomType = false,
            hideFullSessions = false,
        )
      }
      updateVisibleSessions()
    }
  }

  /** Starts a join request for [sessionId]; online-only, see [runSessionAction]. */
  fun join(sessionId: String) {
    runSessionAction(sessionId, isJoin = true)
  }

  /** Starts a leave request for [sessionId]; online-only, see [runSessionAction]. */
  fun leave(sessionId: String) {
    runSessionAction(sessionId, isJoin = false)
  }

  /**
   * Runs one join or leave request, publishing its progress and ignoring duplicate taps for the
   * same session until the repository result arrives.
   *
   * Writes are online-only: while [GroupSessionsUiState.isOffline] nothing is sent and
   * [GroupSessionsError.OFFLINE] is reported. The request runs through [runOnlineWrite], so it
   * cannot hang: a connection loss or timeout ends it with [GroupSessionsError.CONNECTION_LOST],
   * any other failure with [GroupSessionsError.JOIN_FAILED] or [GroupSessionsError.LEAVE_FAILED],
   * and in every case the session's progress indicator is cleared and the list is refreshed.
   */
  private fun runSessionAction(sessionId: String, isJoin: Boolean) {
    viewModelScope.launch {
      val userId = currentUserId ?: return@launch
      if (_uiState.value.isOffline) {
        _uiState.update { it.copy(error = GroupSessionsError.OFFLINE) }
        return@launch
      }
      if (!actionRequestsInProgress.add(sessionId)) return@launch
      updateVisibleSessions()

      val requestGeneration = authGeneration
      val result =
          runOnlineWrite(connectivityObserver) {
            if (isJoin) repository.joinSession(userId, sessionId)
            else repository.leaveSession(userId, sessionId)
          }
      if (requestGeneration != authGeneration) return@launch

      actionRequestsInProgress.remove(sessionId)
      updateVisibleSessions()
      result.exceptionOrNull()?.let { failure ->
        _uiState.update {
          it.copy(
              error =
                  when {
                    failure is ConnectionLostException -> GroupSessionsError.CONNECTION_LOST
                    isJoin -> GroupSessionsError.JOIN_FAILED
                    else -> GroupSessionsError.LEAVE_FAILED
                  },
          )
        }
        refresh()
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
    viewModelScope.launch {
      profilesByUserId.clear()
      refreshVersion.update { it + 1 }
    }
  }

  /**
   * Re-evaluates location collection from [locationProvider], for example after runtime location
   * permissions have been granted.
   */
  fun refreshLocation() {
    viewModelScope.launch { locationRefreshTrigger.update { it + 1 } }
  }

  /**
   * Opens the roster for a session in the latest repository snapshot.
   *
   * Members already read are read again, so a changed picture or username shows up. What was read
   * before stays on screen meanwhile.
   */
  fun openParticipants(sessionId: String) {
    viewModelScope.launch {
      val session = observedSessions.firstOrNull { it.id == sessionId } ?: return@launch
      participantsSessionId = sessionId
      participantNamesWithoutUsername.clear()
      // Cached or not. Loading then publishes the rows without starting a second read.
      session.memberIds.forEach(::fetchProfile)
      loadParticipantNames()
    }
  }

  /** Closes the roster and discards its missing or failed profile results. */
  fun dismissParticipants() {
    viewModelScope.launch { clearParticipants() }
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
              if (_uiState.value.startingTimeFilter != GroupSessionTimeFilter.ANYTIME) {
                updateVisibleSessions()
              }
              delay(NOW_REFRESH_INTERVAL)
            }
          }
    }
  }

  /** Collects device location only while the UI state has an active observer. */
  private fun observeLocationWhileObserved() {
    viewModelScope.launch {
      combine(
              _uiState.subscriptionCount.map { it > 0 }.distinctUntilChanged(),
              locationRefreshTrigger,
          ) { isObserved, _ ->
            isObserved
          }
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

  /**
   * Tracks whether the device is offline, only while the UI state has an active observer.
   *
   * The observer emits the current connectivity as soon as collection starts, so returning to the
   * screen never shows an outdated indication, and the indication disappears when the connection
   * returns. Nothing is collected when no [connectivityObserver] was provided.
   */
  private fun observeConnectivityWhileObserved() {
    val observer = connectivityObserver ?: return
    viewModelScope.launch {
      _uiState.subscriptionCount
          .map { it > 0 }
          .distinctUntilChanged()
          .collectLatest { isObserved ->
            val connectivity = if (isObserved) observer.observeIsOnline() else emptyFlow()
            connectivity.collect { isOnline -> _uiState.update { it.copy(isOffline = !isOnline) } }
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
                _uiState.update {
                  it.copy(
                      availableZones =
                          locations
                              .map { location -> location.zone.trim() }
                              .filter(String::isNotEmpty)
                              .distinct()
                              .sorted(),
                  )
                }
                updateVisibleSessions()
              },
              onFailure = {
                _uiState.update {
                  it.copy(
                      availableZones = emptyList(),
                      error = GroupSessionsError.LOCATION_LOAD_FAILED,
                  )
                }
              },
          )
    }
  }

  /** Prefetches other hosts for the session cards without waiting for their profiles. */
  private fun lookupHostNames(sessions: List<GroupSession>) {
    lookupUsernames(sessions.map { it.createdBy }.distinct().filter { it != currentUserId })
  }

  /** Loads newly visible participants and reuses any usernames or requests shared with cards. */
  private fun loadParticipantNames() {
    if (participantsSessionId == null) return
    val session = selectedParticipantsSession()
    if (session == null) {
      clearParticipants()
      return
    }
    // A member who rejoins can retry an unavailable or missing profile.
    participantNamesWithoutUsername.keys.retainAll(session.memberIds.toSet())
    lookupUsernames(session.memberIds.filterNot { it in participantNamesWithoutUsername })
    updateParticipantsSheet()
  }

  /** Schedules one profile read per uncached user with no lookup already in progress. */
  private fun lookupUsernames(userIds: List<String>) {
    if (currentUserId == null) return
    userIds.filterNot { it in profilesByUserId }.forEach(::fetchProfile)
  }

  /** Reads one profile, cached or not, unless a read for that user is already running. */
  private fun fetchProfile(userId: String) {
    if (!usernameLookupsInProgress.add(userId)) return
    val requestGeneration = authGeneration
    viewModelScope.launch { resolveUsername(userId, requestGeneration) }
  }

  /** Applies one profile result and releases its lookup slot for the same sign-in period. */
  private suspend fun resolveUsername(userId: String, requestGeneration: Long) {
    try {
      val result = profileRepository.getProfile(userId)
      if (requestGeneration != authGeneration) return

      val profile = result.getOrNull()
      val username = profile?.username?.takeIf { it.isNotBlank() }
      if (username != null) {
        val pictureUrl = profile.profilePictureUrl?.takeIf { it.isNotBlank() }
        profilesByUserId[userId] = CachedProfile(username, pictureUrl)
        updateVisibleSessions()
        return
      }

      // A failed read says nothing new, so an earlier result stays. A successful one without a
      // username replaces it.
      if (result.isFailure && userId in profilesByUserId) return
      if (profilesByUserId.remove(userId) != null) updateVisibleSessions()

      if (selectedParticipantsSession()?.memberIds?.contains(userId) != true) return
      participantNamesWithoutUsername[userId] =
          if (result.isFailure) ParticipantNameStatus.UNAVAILABLE else ParticipantNameStatus.UNKNOWN
    } finally {
      if (requestGeneration == authGeneration) {
        usernameLookupsInProgress.remove(userId)
        updateParticipantsSheet()
      }
    }
  }

  /**
   * Resolves the selection against unfiltered sessions so joins and leaves reach the open sheet.
   */
  private fun selectedParticipantsSession(): GroupSession? = participantsSessionId?.let { id ->
    observedSessions.firstOrNull { it.id == id }
  }

  /** Publishes resolved rows in membership order with the host pinned to the top. */
  private fun updateParticipantsSheet() {
    val session = selectedParticipantsSession() ?: return
    val orderedIds = session.orderedMemberIds()
    val rows = orderedIds.mapNotNull { userId ->
      val profile = profilesByUserId[userId]
      val username = profile?.username
      val status = participantNamesWithoutUsername[userId]
      if (username == null && status == null) return@mapNotNull null
      GroupSessionParticipant(
          userId = userId,
          username = username,
          isHost = userId == session.createdBy,
          isCurrentUser = userId == currentUserId,
          isUnavailable = username == null && status == ParticipantNameStatus.UNAVAILABLE,
          profilePictureUrl = profile?.profilePictureUrl,
      )
    }
    _uiState.update {
      it.copy(
          participantsSheet =
              GroupSessionParticipantsSheet(
                  sessionSubject = session.details.subject,
                  participantCount = orderedIds.size,
                  rows = rows,
                  isLoading = rows.size < orderedIds.size,
              )
      )
    }
  }

  /** Removes the active selection and any results scoped to that sheet. */
  private fun clearParticipants() {
    participantsSessionId = null
    participantNamesWithoutUsername.clear()
    _uiState.update { it.copy(participantsSheet = null) }
  }

  private fun updateVisibleSessions() {
    val userId = currentUserId ?: return
    _uiState.update { state ->
      val query = state.query.trim()
      val matchingSessions =
          observedSessions
              .map { session ->
                // The user's own card names no host, so it carries no host picture either.
                val host =
                    profilesByUserId[session.createdBy].takeUnless { session.createdBy == userId }
                GroupSessionItem(
                    session = session,
                    isMember = userId in session.memberIds,
                    isCreator = userId == session.createdBy,
                    proximity = session.proximity(state.userLocation),
                    hostName = host?.username,
                    hostPictureUrl = host?.profilePictureUrl,
                    isActionInProgress = session.id in actionRequestsInProgress,
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
      val rooms =
          matchingSessions
              .groupBy { it.proximity.campusLocation }
              .mapNotNull { (room, items) -> room?.let { RoomSessions(it, items) } }
      state.copy(sessions = matchingSessions, rooms = rooms)
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
    val zone = clock.zone
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
    private val profileRepository: ProfileRepository,
    private val locationProvider: LocationProvider,
    private val campusLocationRepository: CampusLocationRepository? = null,
    private val connectivityObserver: ConnectivityObserver? = null,
    private val campusCenter: Location,
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
        profileRepository = profileRepository,
        locationProvider = locationProvider,
        campusLocationRepository = campusLocationRepository,
        connectivityObserver = connectivityObserver,
        campusCenter = campusCenter,
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
  val locationProvider = remember(context) { AndroidLocationProvider.create(context) }
  val connectivityObserver = remember(context) { AndroidConnectivityObserver.create(context) }
  val campusCenter = defaultCampusCenter()
  return viewModel(
      factory =
          GroupSessionsViewModelFactory(
              authRepository = AuthRepositoryProvider.getRepository(context),
              repository = GroupSessionsRepositoryProvider.getRepository(),
              campusLocationRepository = CampusLocationRepositoryProvider.getRepository(context),
              profileRepository = ProfileRepositoryProvider.getRepository(),
              locationProvider = locationProvider,
              connectivityObserver = connectivityObserver,
              campusCenter = campusCenter,
          )
  )
}
