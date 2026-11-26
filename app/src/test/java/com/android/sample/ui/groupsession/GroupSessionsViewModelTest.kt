// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini <noreply@google.com>
// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.groupsession

import androidx.lifecycle.viewModelScope
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.connectivity.ConnectionLostException
import com.android.sample.model.connectivity.ConnectivityObserver
import com.android.sample.model.connectivity.FakeConnectivityObserver
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationFilter
import com.android.sample.model.location.CampusLocationRepository
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.FakeLocationProvider
import com.android.sample.model.location.Location
import com.android.sample.model.location.LocationProvider
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupSessionsViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val now = Instant.parse("2025-03-01T12:00:00Z")
  private lateinit var authRepository: TestAuthRepository
  private lateinit var repository: InMemoryGroupSessionsRepository
  private lateinit var profileRepository: FakeProfileRepository
  private val testCampusCenter = Location(0.0, 0.0)

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = TestAuthRepository()
    repository = InMemoryGroupSessionsRepository()
    profileRepository = FakeProfileRepository()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  /** A signed-out user gets an empty, non-loading state without an error. */
  @Test
  fun signedOut_startsWithEmptyNonLoadingState() = runTest {
    val viewModel = createViewModel()

    assertEquals(now, viewModel.uiState.value.now)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(emptyList<GroupSessionItem>(), viewModel.uiState.value.sessions)
    assertNull(viewModel.uiState.value.error)
  }

  /** Exposes trimmed, distinct, non-blank campus zones in alphabetical order. */
  @Test
  fun availableZonesAreDistinctTrimmedAndSorted() = runTest {
    val viewModel =
        createViewModel(
            campusLocationRepository =
                FakeCampusLocationRepository(
                    listOf(
                        campusLocation("CM 1", zone = "CM"),
                        campusLocation("BC 2", zone = "BC"),
                        campusLocation("CM 3", zone = "CM"),
                        campusLocation("CO 4", zone = " CO "),
                        campusLocation("X 5", zone = ""),
                    ),
                ),
        )
    runCurrent()

    assertEquals(listOf("BC", "CM", "CO"), viewModel.uiState.value.availableZones)
  }

  /** Leaves available zones empty when no campus-location repository is configured. */
  @Test
  fun availableZonesAreEmptyWithoutCampusLocationRepository() = runTest {
    val viewModel = createViewModel()
    runCurrent()

    assertEquals(emptyList<String>(), viewModel.uiState.value.availableZones)
  }

  /** Leaves available zones empty and reports the location error when loading fails. */
  @Test
  fun availableZonesAreEmptyWhenCampusLocationLoadFails() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel =
        createViewModel(
            campusLocationRepository =
                FakeCampusLocationRepository(Result.failure(IllegalStateException("unavailable"))),
        )
    runCurrent()

    assertEquals(emptyList<String>(), viewModel.uiState.value.availableZones)
    assertEquals(GroupSessionsError.LOCATION_LOAD_FAILED, viewModel.uiState.value.error)
  }

  /** Hides LAB-room sessions by default and shows them only after enabling lab rooms. */
  @Test
  fun labRoomSessionsAreHiddenUntilLabRoomsAreEnabled() = runTest {
    authRepository.currentUser.value = account(ALICE)
    repository.saveSession(
        ALICE,
        session("study-lab", ALICE, location = "Lab 1"),
    )
    val viewModel =
        createViewModel(
            campusLocationRepository =
                FakeCampusLocationRepository(
                    listOf(campusLocation("Lab 1", type = CampusLocationType.LAB)),
                ),
        )
    runCurrent()

    assertFalse(viewModel.uiState.value.showLabRoomType)
    assertEquals(emptyList<String>(), visibleIds(viewModel))

    viewModel.onShowLabRoomTypeChange(true)
    runCurrent()

    assertEquals(listOf("study-lab"), visibleIds(viewModel))
  }

  /** Resets lab visibility to off after it has been enabled. */
  @Test
  fun resetAdvancedFiltersDisablesLabRooms() = runTest {
    val viewModel = createViewModel()
    viewModel.onShowLabRoomTypeChange(true)
    runCurrent()

    viewModel.resetAdvancedFilters()
    runCurrent()

    assertFalse(viewModel.uiState.value.showLabRoomType)
  }

  /** Loading remains active until the repository produces its first list. */
  @Test
  fun signedIn_waitsForFirstListBeforeClearingLoading() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))

    assertTrue(viewModel.uiState.value.isLoading)
    emissions.emit(Result.success(listOf(session("first", ALICE))))

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf("first"), viewModel.uiState.value.sessions.map { it.session.id })
  }

  /** Signing in after sign-out restores loading until the new session list arrives. */
  @Test
  fun signingInAgainShowsLoadingUntilTheNewListArrives() = runTest {
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))

    assertFalse(viewModel.uiState.value.isLoading)
    authRepository.currentUser.value = account(ALICE)
    assertTrue(viewModel.uiState.value.isLoading)

    val session = session("after-sign-in", ALICE)
    emissions.emit(Result.success(listOf(session)))

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(session), viewModel.uiState.value.sessions.map { it.session })
  }

  /** The list excludes ended sessions, retains repository order, and marks both relationships. */
  @Test
  fun list_filtersByCurrentTimeAndMarksMembershipAndCreator() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val own = session("own", ALICE, listOf(ALICE), startsAt = now.plusSeconds(60))
    val joined = session("joined", BOB, listOf(BOB, ALICE), startsAt = now.plusSeconds(120))
    val notJoined = session("not-joined", BOB, listOf(BOB), startsAt = now.plusSeconds(180))
    val ended =
        session(
            "ended",
            BOB,
            listOf(BOB),
            startsAt = now.minusSeconds(60),
            endsAt = now,
        )
    listOf(own, joined, notJoined, ended).forEach { repository.saveSession(it.createdBy, it) }

    val viewModel = createViewModel()

    assertEquals(
        listOf(
            GroupSessionItem(own, isMember = true, isCreator = true),
            GroupSessionItem(joined, isMember = true, isCreator = false),
            GroupSessionItem(notJoined, isMember = false, isCreator = false),
        ),
        viewModel.uiState.value.sessions,
    )
  }

  /** Repository saves and membership updates appear without calling a ViewModel refresh method. */
  @Test
  fun repositoryChangesUpdateLiveListAndMembership() = runTest {
    authRepository.currentUser.value = account(BOB)
    val existing = session("existing", ALICE, listOf(ALICE))
    repository.saveSession(ALICE, existing)
    val viewModel = createViewModel()

    assertEquals(false, viewModel.uiState.value.sessions.single().isMember)
    repository.joinSession(BOB, existing.id)
    assertTrue(viewModel.uiState.value.sessions.single().isMember)

    val added = session("added", BOB, listOf(BOB), startsAt = now.plusSeconds(120))
    repository.saveSession(BOB, added)
    assertEquals(
        listOf("existing", "added"),
        viewModel.uiState.value.sessions.map { it.session.id },
    )
  }

  /**
   * Search checks subject, location, and description, trims only for matching, and tolerates null
   * subjects.
   */
  @Test
  fun searchMatchesAllFieldsCaseInsensitivelyAndHandlesNullSubject() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val physics =
        session(
            "physics",
            ALICE,
            subject = "Physics",
            location = "Library",
            description = "Exam review",
        )
    val other =
        session(
            "other",
            ALICE,
            type = EventType.OTHER,
            subject = null,
            location = "Quiet Hall",
            description = "Group planning",
            startsAt = now.plusSeconds(120),
        )
    repository.saveSession(ALICE, physics)
    repository.saveSession(ALICE, other)
    val viewModel = createViewModel()

    viewModel.onQueryChange("  pHySiCs  ")
    assertEquals("  pHySiCs  ", viewModel.uiState.value.query)
    assertEquals(listOf("physics"), visibleIds(viewModel))

    viewModel.onQueryChange("LIBRARY")
    assertEquals(listOf("physics"), visibleIds(viewModel))
    viewModel.onQueryChange(" REVIEW ")
    assertEquals(listOf("physics"), visibleIds(viewModel))
    viewModel.onQueryChange("quiet hall")
    assertEquals(listOf("other"), visibleIds(viewModel))
    viewModel.onQueryChange("PLANNING")
    assertEquals(listOf("other"), visibleIds(viewModel))
    viewModel.onQueryChange("   ")
    assertEquals(listOf("physics", "other"), visibleIds(viewModel))
  }

  /** JOINED includes the creator, combines with search, and ALL restores non-member matches. */
  @Test
  fun joinedFilterCombinesWithSearchAndAllRestoresMatchingSessions() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val own = session("own-math", ALICE, subject = "Math")
    val joined =
        session(
            "joined-biology",
            BOB,
            listOf(BOB, ALICE),
            subject = "Biology",
            startsAt = now.plusSeconds(120),
        )
    val notJoined =
        session(
            "not-joined-biology",
            BOB,
            listOf(BOB),
            subject = "Biology",
            startsAt = now.plusSeconds(180),
        )
    listOf(own, joined, notJoined).forEach { repository.saveSession(it.createdBy, it) }
    val viewModel = createViewModel()

    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    assertEquals(listOf("own-math", "joined-biology"), visibleIds(viewModel))
    viewModel.onQueryChange("biology")
    assertEquals(listOf("joined-biology"), visibleIds(viewModel))
    viewModel.onFilterChange(GroupSessionFilter.ALL)
    assertEquals(listOf("joined-biology", "not-joined-biology"), visibleIds(viewModel))
    viewModel.onQueryChange("")
    assertEquals(listOf("own-math", "joined-biology", "not-joined-biology"), visibleIds(viewModel))
  }

  /** Visible sessions with known campus rooms are grouped into [RoomSessions]. */
  @Test
  fun resolvedRooms_groupsVisibleSessionsIntoRooms() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val s1 = session("s1", ALICE, location = "CM 1")
    val s2 = session("s2", BOB, location = "cm 1")
    val s3 = session("s3", BOB, location = "BC 2")
    val s4 = session("s4", BOB, location = "Unknown room")
    val campusLocations = listOf(campusLocation("CM 1"), campusLocation("BC 2"))
    val viewModel =
        createViewModel(
            sessionsRepository =
                FlowGroupSessionsRepository(flowOf(Result.success(listOf(s1, s2, s3, s4)))),
            campusLocationRepository = FakeCampusLocationRepository(campusLocations),
        )
    observe(viewModel)

    val rooms = viewModel.uiState.value.rooms
    assertEquals(2, rooms.size)
    assertEquals("CM 1", rooms[0].room.name)
    assertEquals(listOf("s1", "s2"), rooms[0].items.map { it.session.id })
    assertEquals("BC 2", rooms[1].room.name)
    assertEquals(listOf("s3"), rooms[1].items.map { it.session.id })
  }

  /** Without a GPS fix, selecting a radius keeps sessions and exposes no distance. */
  @Test
  fun radiusFilter_isBypassedWhenLocationIsUnavailable() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionWithRoom = session("room", ALICE, location = "CM 1")
    val sessionWithUnknownRoom = session("unknown-room", BOB, location = "Missing room")
    val sessionsRepository =
        FlowGroupSessionsRepository(
            flowOf(Result.success(listOf(sessionWithRoom, sessionWithUnknownRoom)))
        )
    val viewModel =
        createViewModel(
            sessionsRepository = sessionsRepository,
            locationProvider = LocationProvider { flowOf(null) },
            campusLocationRepository = FakeCampusLocationRepository(listOf(campusLocation("CM 1"))),
        )
    observe(viewModel)

    viewModel.onRadiusFilterSelected(250)

    assertNull(viewModel.uiState.value.userLocation)
    assertEquals(listOf("room", "unknown-room"), visibleIds(viewModel))
    assertTrue(viewModel.uiState.value.sessions.all { it.proximity.distanceMeters == null })
    assertTrue(viewModel.uiState.value.sessions.all { it.proximity.formattedDistance == null })
  }

  /** Resolved rooms provide raw and formatted distance, and the radius removes distant sessions. */
  @Test
  fun radiusFilter_attachesDistanceAndExcludesRoomsOutsideThreshold() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val close = session("close", ALICE, location = "CM 1")
    val far = session("far", BOB, location = "BC 2", startsAt = now.plusSeconds(120))
    val beyondKilometer =
        session("kilometer", BOB, location = "BC 3", startsAt = now.plusSeconds(180))
    val unknownRoom =
        session("unknown-room", BOB, location = "Unknown", startsAt = now.plusSeconds(240))
    val campusLocations =
        listOf(
            campusLocation("CM 1", longitude = 0.001),
            campusLocation("BC 2", longitude = 0.005),
            campusLocation("BC 3", longitude = 0.02),
        )
    val viewModel =
        createViewModel(
            sessionsRepository =
                FlowGroupSessionsRepository(
                    flowOf(Result.success(listOf(close, far, beyondKilometer, unknownRoom)))
                ),
            locationProvider = LocationProvider { flowOf(Location(0.0, 0.0)) },
            campusLocationRepository = FakeCampusLocationRepository(campusLocations),
        )
    observe(viewModel)

    val closeItem = viewModel.uiState.value.sessions.first { it.session.id == "close" }
    val distantItem = viewModel.uiState.value.sessions.first { it.session.id == "kilometer" }
    assertEquals(close, closeItem.proximity.session)
    assertEquals("CM 1", closeItem.proximity.campusLocation?.name)
    assertEquals(111.2, closeItem.proximity.distanceMeters!!, 1.0)
    assertEquals("111m", closeItem.proximity.formattedDistance)
    assertEquals("2.2km", distantItem.proximity.formattedDistance)
    val unresolvedItem = viewModel.uiState.value.sessions.first { it.session.id == "unknown-room" }
    assertNull(unresolvedItem.proximity.campusLocation)
    assertNull(unresolvedItem.proximity.distanceMeters)

    viewModel.onRadiusFilterSelected(250)

    assertEquals(listOf("close", "unknown-room"), visibleIds(viewModel))
  }

  /**
   * New GPS values recalculate distances and radius membership without observing sessions again.
   */
  @Test
  fun locationUpdates_recalculateProximityWithoutRefetchingSessions() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val nearRoom = session("near", ALICE, location = "CM 1")
    val farRoom = session("far", BOB, location = "BC 2", startsAt = now.plusSeconds(120))
    val sessionsRepository =
        FlowGroupSessionsRepository(
            flowOf(Result.success(listOf(nearRoom, farRoom))),
        )
    val userLocation = MutableStateFlow<Location?>(Location(0.0, 0.0))
    val viewModel =
        createViewModel(
            sessionsRepository = sessionsRepository,
            locationProvider = LocationProvider { userLocation },
            campusLocationRepository =
                FakeCampusLocationRepository(
                    listOf(
                        campusLocation("CM 1", longitude = 0.001),
                        campusLocation("BC 2", longitude = 0.005),
                    ),
                ),
        )
    val collector = observe(viewModel)
    viewModel.onRadiusFilterSelected(250)
    val observationCount = sessionsRepository.observationCount
    assertEquals(listOf("near"), visibleIds(viewModel))

    userLocation.value = Location(0.0, 0.004)

    assertEquals(listOf("far"), visibleIds(viewModel))
    assertEquals(111.2, viewModel.uiState.value.sessions.single().proximity.distanceMeters!!, 1.0)
    assertEquals(observationCount, sessionsRepository.observationCount)
    collector.cancel()
  }

  /** Zone and radius criteria are applied together, and clearing the zone retains the radius. */
  @Test
  fun zoneFilter_combinesWithRadiusAndCanBeClearedIndependently() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val cm = session("cm", ALICE, location = "CM 1")
    val bc = session("bc", BOB, location = "BC 2", startsAt = now.plusSeconds(120))
    val sessionsRepository = FlowGroupSessionsRepository(flowOf(Result.success(listOf(cm, bc))))
    val viewModel =
        createViewModel(
            sessionsRepository = sessionsRepository,
            locationProvider = LocationProvider { flowOf(Location(0.0, 0.0)) },
            campusLocationRepository =
                FakeCampusLocationRepository(
                    listOf(
                        campusLocation("CM 1", zone = "CM", longitude = 0.001),
                        campusLocation("BC 2", zone = "BC", longitude = 0.005),
                    ),
                ),
        )
    observe(viewModel)
    viewModel.onRadiusFilterSelected(600)
    viewModel.onZoneFilterSelected(" cm ")

    assertEquals(setOf("cm"), viewModel.uiState.value.selectedZones)
    assertEquals(listOf("cm"), visibleIds(viewModel))

    viewModel.onZoneFilterSelected(null)

    assertTrue(viewModel.uiState.value.selectedZones.isEmpty())
    assertEquals(listOf("cm", "bc"), visibleIds(viewModel))
  }

  /** Type, time, room type, and capacity criteria narrow results and reset together. */
  @Test
  fun advancedFilters_applyTogetherAndResetWithoutClearingSearchOrMembership() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val studyLab =
        session(
            "study-lab",
            ALICE,
            subject = "Study",
            type = EventType.STUDY_SESSION,
            location = "Lab 1",
        )
    val exercise =
        session(
            "exercise",
            ALICE,
            subject = "Study",
            type = EventType.EXERCISE_SESSION,
            startsAt = now.plusSeconds(120),
        )
    val lateStudy =
        session(
            "late-study",
            ALICE,
            subject = "Study",
            type = EventType.STUDY_SESSION,
            startsAt = now.plusSeconds(2 * 60 * 60),
        )
    val fullStudy =
        session(
            "full-study",
            ALICE,
            members = listOf(ALICE) + (1 until GroupSession.MAX_MEMBERS).map { "member-$it" },
            subject = "Study",
            type = EventType.STUDY_SESSION,
        )
    val availableStudy =
        session("available-study", ALICE, subject = "Study", type = EventType.STUDY_SESSION)
    listOf(studyLab, exercise, lateStudy, fullStudy, availableStudy).forEach {
      repository.saveSession(it.createdBy, it)
    }
    val viewModel =
        createViewModel(
            campusLocationRepository =
                FakeCampusLocationRepository(
                    listOf(campusLocation("Lab 1", type = CampusLocationType.LAB)),
                ),
        )

    viewModel.onQueryChange("study")
    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    viewModel.onSessionTypesSelected(setOf(EventType.STUDY_SESSION))
    assertEquals(
        listOf("available-study", "full-study", "late-study"),
        visibleIds(viewModel),
    )

    viewModel.onStartingTimeFilterSelected(GroupSessionTimeFilter.WITHIN_ONE_HOUR)
    viewModel.onShowLabRoomTypeChange(false)
    viewModel.onHideFullSessionsChange(true)

    assertEquals(listOf("available-study"), visibleIds(viewModel))

    viewModel.resetAdvancedFilters()

    assertEquals("study", viewModel.uiState.value.query)
    assertEquals(GroupSessionFilter.JOINED, viewModel.uiState.value.filter)
    assertEquals(
        listOf("available-study", "full-study", "exercise", "late-study"),
        visibleIds(viewModel),
    )
  }

  /** Today and this-week ranges use local calendar boundaries, including Monday week starts. */
  @Test
  fun startingTimeFilters_useTodayAndMondayWeekBoundaries() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val zone = ZoneId.systemDefault()
    val localNow = now.atZone(zone)
    val today = localNow.toLocalDate()
    val todaySession = session("today", ALICE, startsAt = now.plusSeconds(600))
    val tomorrowStart = localNow.plusDays(1).toInstant()
    val tomorrowSession = session("tomorrow", ALICE, startsAt = tomorrowStart)
    val nextMonday =
        today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(10, 0).atZone(zone).toInstant()
    val nextWeekSession = session("next-week", ALICE, startsAt = nextMonday)
    listOf(todaySession, tomorrowSession, nextWeekSession).forEach {
      repository.saveSession(it.createdBy, it)
    }
    val viewModel = createViewModel()

    viewModel.onStartingTimeFilterSelected(GroupSessionTimeFilter.TODAY)

    assertEquals(listOf("today"), visibleIds(viewModel))

    viewModel.onStartingTimeFilterSelected(GroupSessionTimeFilter.THIS_WEEK)

    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val expectedThisWeek =
        if (tomorrowStart.atZone(zone).toLocalDate().isBefore(weekStart.plusDays(7))) {
          listOf("today", "tomorrow")
        } else {
          listOf("today")
        }
    assertEquals(expectedThisWeek, visibleIds(viewModel))
    assertTrue(nextWeekSession.details.startsAt.isAfter(now))
  }

  /** A campus-location read failure is exposed instead of silently appearing as no room matches. */
  @Test
  fun campusLocationLoadFailure_isReported() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel =
        createViewModel(
            campusLocationRepository =
                FakeCampusLocationRepository(Result.failure(IllegalStateException("unavailable"))),
        )
    runCurrent()

    assertEquals(GroupSessionsError.LOCATION_LOAD_FAILED, viewModel.uiState.value.error)
  }

  /** Join and leave actions mutate repository membership and the corresponding visible item. */
  @Test
  fun joinAndLeaveUpdateMembershipThroughRepository() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val target = session("target", BOB, listOf(BOB))
    repository.saveSession(BOB, target)
    val viewModel = createViewModel()

    viewModel.join(target.id)
    assertTrue(viewModel.uiState.value.sessions.single().isMember)

    viewModel.leave(target.id)
    assertFalse(viewModel.uiState.value.sessions.single().isMember)
  }

  @Test
  fun joinOffline_doesNotCallRepository() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val connectivity = FakeConnectivityObserver(initialOnline = false)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("target", BOB, listOf(BOB))))
    val viewModel =
        createViewModel(
            sessionsRepository = sessionsRepository,
            connectivityObserver = connectivity,
        )
    observe(viewModel)
    runCurrent()

    viewModel.join("target")

    assertEquals(GroupSessionsError.OFFLINE, viewModel.uiState.value.error)
    assertEquals(0, sessionsRepository.joinCalls)
  }

  @Test
  fun leaveOffline_doesNotCallRepository() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val connectivity = FakeConnectivityObserver(initialOnline = false)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(
            listOf(session("target", BOB, listOf(BOB, ALICE))),
        )
    val viewModel =
        createViewModel(
            sessionsRepository = sessionsRepository,
            connectivityObserver = connectivity,
        )
    observe(viewModel)
    runCurrent()

    viewModel.leave("target")

    assertEquals(GroupSessionsError.OFFLINE, viewModel.uiState.value.error)
    assertEquals(0, sessionsRepository.leaveCalls)
  }

  @Test
  fun joinConnectionLostReportsConnectionLostAndClearsProgress() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB))))
    val connectivity = FakeConnectivityObserver(initialOnline = true)
    val viewModel =
        createViewModel(
            sessionsRepository = sessionsRepository,
            connectivityObserver = connectivity,
        )
    observe(viewModel)
    runCurrent()

    viewModel.join("a")
    runCurrent()
    sessionsRepository.joinResult.complete(Result.failure(ConnectionLostException()))
    runCurrent()

    assertFalse(viewModel.uiState.value.sessions.single().isActionInProgress)
    assertEquals(GroupSessionsError.CONNECTION_LOST, viewModel.uiState.value.error)
  }

  /** Marks only the requested session while joining and clears progress after success. */
  @Test
  fun join_marksOnlyTargetInProgressUntilSuccess() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(
            listOf(session("a", BOB, listOf(BOB)), session("b", BOB, listOf(BOB))),
        )
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.join("a")
    runCurrent()
    assertTrue(sessionsRepository.joinStarted.isCompleted)
    assertTrue(viewModel.uiState.value.sessions.single { it.session.id == "a" }.isActionInProgress)
    assertFalse(viewModel.uiState.value.sessions.single { it.session.id == "b" }.isActionInProgress)

    sessionsRepository.joinResult.complete(Result.success(Unit))
    runCurrent()

    assertFalse(viewModel.uiState.value.sessions.single { it.session.id == "a" }.isActionInProgress)
  }

  /** Marks only the requested member session while leaving and clears progress after success. */
  @Test
  fun leave_marksOnlyTargetInProgressUntilSuccess() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(
            listOf(
                session("a", BOB, listOf(BOB, ALICE)),
                session("b", BOB, listOf(BOB, ALICE)),
            ),
        )
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.leave("a")
    runCurrent()
    assertTrue(sessionsRepository.leaveStarted.isCompleted)
    assertTrue(viewModel.uiState.value.sessions.single { it.session.id == "a" }.isActionInProgress)
    assertFalse(viewModel.uiState.value.sessions.single { it.session.id == "b" }.isActionInProgress)

    sessionsRepository.leaveResult.complete(Result.success(Unit))
    runCurrent()

    assertFalse(viewModel.uiState.value.sessions.single { it.session.id == "a" }.isActionInProgress)
  }

  /** Clears join progress and reports JOIN_FAILED after a failed request. */
  @Test
  fun join_clearsProgressAndReportsFailure() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.join("a")
    runCurrent()
    assertTrue(viewModel.uiState.value.sessions.single().isActionInProgress)
    sessionsRepository.joinResult.complete(Result.failure(IllegalStateException()))
    runCurrent()
    assertFalse(viewModel.uiState.value.sessions.single().isActionInProgress)
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
    sessionsRepository.refreshedSessions.complete(Result.success(listOf(session("a", BOB))))
    runCurrent()
  }

  /** Clears leave progress and reports LEAVE_FAILED after a failed request. */
  @Test
  fun leave_clearsProgressAndReportsFailure() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB, ALICE))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.leave("a")
    runCurrent()
    assertTrue(viewModel.uiState.value.sessions.single().isActionInProgress)
    sessionsRepository.leaveResult.complete(Result.failure(IllegalStateException()))
    runCurrent()
    assertFalse(viewModel.uiState.value.sessions.single().isActionInProgress)
    assertEquals(GroupSessionsError.LEAVE_FAILED, viewModel.uiState.value.error)
    sessionsRepository.refreshedSessions.complete(
        Result.success(listOf(session("a", BOB, listOf(BOB, ALICE)))),
    )
    runCurrent()
  }

  /** Ignores a second join tap while the first request is pending. */
  @Test
  fun secondJoinWhilePending_callsRepositoryOnce() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.join("a")
    runCurrent()
    viewModel.join("a")
    runCurrent()

    assertEquals(1, sessionsRepository.joinCalls)
    sessionsRepository.joinResult.complete(Result.success(Unit))
    runCurrent()
  }

  /** Ignores a second leave tap while the first request is pending. */
  @Test
  fun secondLeaveWhilePending_callsRepositoryOnce() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB, ALICE))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.leave("a")
    runCurrent()
    viewModel.leave("a")
    runCurrent()

    assertEquals(1, sessionsRepository.leaveCalls)
    sessionsRepository.leaveResult.complete(Result.success(Unit))
    runCurrent()
  }

  /** Ignores leave while a join for the same session is pending. */
  @Test
  fun leaveWhileJoinPending_callsOnlyJoin() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB, ALICE))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.join("a")
    runCurrent()
    viewModel.leave("a")
    runCurrent()

    assertEquals(1, sessionsRepository.joinCalls)
    assertEquals(0, sessionsRepository.leaveCalls)
    sessionsRepository.joinResult.complete(Result.success(Unit))
    runCurrent()
  }

  /** Re-observes after join failure and keeps loading until the refreshed list arrives. */
  @Test
  fun failedJoin_reobservesUntilRefreshedListArrives() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()
    assertEquals(1, sessionsRepository.observationCount)

    viewModel.join("a")
    runCurrent()
    sessionsRepository.joinResult.complete(Result.failure(IllegalStateException()))
    runCurrent()

    assertEquals(2, sessionsRepository.observationCount)
    assertTrue(viewModel.uiState.value.isLoading)

    sessionsRepository.refreshedSessions.complete(Result.success(listOf(session("a", BOB))))
    runCurrent()

    assertFalse(viewModel.uiState.value.isLoading)
  }

  /** A successful join leaves the existing session observation intact. */
  @Test
  fun successfulJoin_doesNotReobserveSessions() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.join("a")
    runCurrent()
    sessionsRepository.joinResult.complete(Result.success(Unit))
    runCurrent()

    assertEquals(1, sessionsRepository.observationCount)
  }

  /** Re-observes after leave failure and keeps loading until the refreshed list arrives. */
  @Test
  fun failedLeave_reobservesUntilRefreshedListArrives() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB, ALICE))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()
    assertEquals(1, sessionsRepository.observationCount)

    viewModel.leave("a")
    runCurrent()
    sessionsRepository.leaveResult.complete(Result.failure(IllegalStateException()))
    runCurrent()

    assertEquals(2, sessionsRepository.observationCount)
    assertTrue(viewModel.uiState.value.isLoading)

    sessionsRepository.refreshedSessions.complete(
        Result.success(listOf(session("a", BOB, listOf(BOB, ALICE)))),
    )
    runCurrent()

    assertFalse(viewModel.uiState.value.isLoading)
  }

  /** A successful leave leaves the existing session observation intact. */
  @Test
  fun successfulLeave_doesNotReobserveSessions() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB, ALICE))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()

    viewModel.leave("a")
    runCurrent()
    sessionsRepository.leaveResult.complete(Result.success(Unit))
    runCurrent()

    assertEquals(1, sessionsRepository.observationCount)
  }

  /** A pending join failure after sign-out leaves no error or progress state. */
  @Test
  fun pendingJoinFailureAfterSignOut_doesNotChangeState() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()
    viewModel.join("a")
    runCurrent()

    authRepository.currentUser.value = null
    runCurrent()
    sessionsRepository.joinResult.complete(Result.failure(IllegalStateException()))
    runCurrent()

    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.sessions.isEmpty())
    assertTrue(viewModel.uiState.value.sessions.none { it.isActionInProgress })
  }

  /** A pending join failure after switching users leaves no error or progress state. */
  @Test
  fun pendingJoinFailureAfterUserChange_doesNotChangeState() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        PendingActionGroupSessionsRepository(listOf(session("a", BOB, listOf(BOB))))
    val viewModel = createViewModel(sessionsRepository)
    runCurrent()
    viewModel.join("a")
    runCurrent()

    authRepository.currentUser.value = account(BOB)
    runCurrent()
    sessionsRepository.joinResult.complete(Result.failure(IllegalStateException()))
    runCurrent()

    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.sessions.none { it.isActionInProgress })
    sessionsRepository.refreshedSessions.complete(Result.success(listOf(session("a", BOB))))
    runCurrent()
  }

  /**
   * Missing-session joins and creator leaves produce their stable categories and can be cleared.
   */
  @Test
  fun joinAndLeaveFailuresExposeCategoriesAndClearErrorResetsIt() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val own = session("own", ALICE)
    repository.saveSession(ALICE, own)
    val viewModel = createViewModel()

    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
    viewModel.clearError()
    assertNull(viewModel.uiState.value.error)

    viewModel.leave(own.id)
    assertEquals(GroupSessionsError.LEAVE_FAILED, viewModel.uiState.value.error)
  }

  /** A successful unrelated list update does not clear a join failure. */
  @Test
  fun joinFailure_survivesUnrelatedSessionUpdate() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()

    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)

    repository.saveSession(BOB, session("unrelated", BOB))

    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
  }

  /** A successful unrelated list update does not clear a leave failure. */
  @Test
  fun leaveFailure_survivesUnrelatedSessionUpdate() = runTest {
    authRepository.currentUser.value = account(ALICE)
    repository.saveSession(ALICE, session("own", ALICE))
    val viewModel = createViewModel()

    viewModel.leave("own")
    assertEquals(GroupSessionsError.LEAVE_FAILED, viewModel.uiState.value.error)

    repository.saveSession(BOB, session("unrelated", BOB))

    assertEquals(GroupSessionsError.LEAVE_FAILED, viewModel.uiState.value.error)
  }

  /** Refreshing the list does not clear a join failure. */
  @Test
  fun refresh_preservesJoinFailure() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()

    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)

    viewModel.refresh()

    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
  }

  /** A delayed join failure is ignored after switching directly to another user. */
  @Test
  fun joinFailureFromPreviousAccount_isIgnored() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val delayedRepository = DelayedGroupSessionsRepository()
    val viewModel = createViewModel(delayedRepository)

    viewModel.join("target")
    assertTrue(delayedRepository.joinStarted.isCompleted)
    authRepository.currentUser.value = account(BOB)
    delayedRepository.joinResult.complete(Result.failure(IllegalStateException()))

    assertNull(viewModel.uiState.value.error)
  }

  /** A delayed leave failure does not alter the signed-out state. */
  @Test
  fun leaveFailureAfterSignOut_isIgnored() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val delayedRepository = DelayedGroupSessionsRepository()
    val viewModel = createViewModel(delayedRepository)

    viewModel.leave("target")
    assertTrue(delayedRepository.leaveStarted.isCompleted)
    authRepository.currentUser.value = null
    delayedRepository.leaveResult.complete(Result.failure(IllegalStateException()))

    assertTrue(viewModel.uiState.value.sessions.isEmpty())
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  /** Signing out and back in as the same user invalidates an earlier join result. */
  @Test
  fun joinFailureAfterSigningOutAndBackIn_isIgnored() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val delayedRepository = DelayedGroupSessionsRepository()
    val viewModel = createViewModel(delayedRepository)

    viewModel.join("target")
    assertTrue(delayedRepository.joinStarted.isCompleted)
    authRepository.currentUser.value = null
    authRepository.currentUser.value = account(ALICE)
    delayedRepository.joinResult.complete(Result.failure(IllegalStateException()))

    assertNull(viewModel.uiState.value.error)
  }

  /** A later successful list emission clears a preceding load failure and displays the list. */
  @Test
  fun successfulEmission_clearsLoadFailure() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    emissions.emit(Result.failure(EventsException(EventsError.STORAGE_FAILURE)))
    assertEquals(GroupSessionsError.LOAD_FAILED, viewModel.uiState.value.error)
    val shown = session("shown", ALICE)

    emissions.emit(Result.success(listOf(shown)))

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf(shown), viewModel.uiState.value.sessions.map { it.session })
  }

  /** A refresh does not invalidate a still-pending join failure for the same user. */
  @Test
  fun joinFailure_survivesRefreshWhilePending() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val delayedRepository = DelayedGroupSessionsRepository()
    val viewModel = createViewModel(delayedRepository)

    viewModel.join("target")
    assertTrue(delayedRepository.joinStarted.isCompleted)
    viewModel.refresh()
    delayedRepository.joinResult.complete(Result.failure(IllegalStateException()))

    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
  }

  /** Switching users clears an action error belonging to the previous account. */
  @Test
  fun switchingUsers_clearsActionError() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()
    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)

    authRepository.currentUser.value = account(BOB)

    assertNull(viewModel.uiState.value.error)
  }

  /** A failed session flow stops loading and exposes LOAD_FAILED without leaking exception text. */
  @Test
  fun failedSessionFlowStopsLoadingAndExposesLoadCategory() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val failed =
        flowOf(Result.failure<List<GroupSession>>(EventsException(EventsError.STORAGE_FAILURE)))
    val viewModel = createViewModel(FlowGroupSessionsRepository(failed))

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(GroupSessionsError.LOAD_FAILED, viewModel.uiState.value.error)
  }

  /** A failure after a successful load keeps the last list and exposes LOAD_FAILED. */
  @Test
  fun loadFailureAfterSuccessKeepsLastListAndShowsError() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val shown = session("shown", ALICE)
    emissions.emit(Result.success(listOf(shown)))

    emissions.emit(Result.failure(EventsException(EventsError.STORAGE_FAILURE)))

    assertEquals(listOf(shown), viewModel.uiState.value.sessions.map { it.session })
    assertEquals(GroupSessionsError.LOAD_FAILED, viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  /**
   * Signing out clears sessions, and a different user's flags are recomputed from their identity.
   */
  @Test
  fun authenticationChangesClearAndRecomputeUserRelationships() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val aliceOwn = session("alice-own", ALICE, listOf(ALICE, BOB))
    val bobOwn = session("bob-own", BOB, listOf(BOB), startsAt = now.plusSeconds(120))
    repository.saveSession(ALICE, aliceOwn)
    repository.saveSession(BOB, bobOwn)
    val viewModel = createViewModel()

    assertEquals(
        listOf(true to true, false to false),
        viewModel.uiState.value.sessions.map { it.isMember to it.isCreator },
    )
    authRepository.currentUser.value = null
    assertTrue(viewModel.uiState.value.sessions.isEmpty())
    authRepository.currentUser.value = account(BOB)
    assertEquals(
        listOf(true to false, true to true),
        viewModel.uiState.value.sessions.map { it.isMember to it.isCreator },
    )
  }

  /** Join and leave while signed out are safe no-ops and do not alter the repository. */
  @Test
  fun signedOutJoinAndLeaveDoNotChangeRepositoryOrCrash() = runTest {
    val own = session("own", ALICE)
    repository.saveSession(ALICE, own)
    val viewModel = createViewModel()

    viewModel.join(own.id)
    viewModel.leave(own.id)

    assertEquals(Result.success(own), repository.getSession(own.id))
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.sessions.isEmpty())
  }

  /**
   * Existing flows retain their initial cutoff; refresh observes again using the advanced clock.
   */
  @Test
  fun refreshRemovesSessionThatEndedSinceObservationStarted() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val mutableClock = MutableTestClock(now)
    val expiring =
        session(
            "expiring",
            ALICE,
            startsAt = now.plusSeconds(60),
            endsAt = now.plusSeconds(120),
        )
    repository.saveSession(ALICE, expiring)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            repository,
            profileRepository,
            mutableClock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )

    assertEquals(listOf("expiring"), visibleIds(viewModel))
    mutableClock.currentInstant = expiring.details.endsAt
    assertEquals(listOf("expiring"), visibleIds(viewModel))

    viewModel.refresh()

    assertTrue(viewModel.uiState.value.sessions.isEmpty())
  }

  /** Refresh updates the reference time even when no screen collects the state. */
  @Test
  fun now_refreshUpdatesTimeWithoutUiStateCollector() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            FlowGroupSessionsRepository(flowOf(Result.success(emptyList()))),
            profileRepository,
            clock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )
    clock.currentInstant = Instant.parse("2025-03-01T12:00:25Z")

    viewModel.refresh()
    runCurrent()

    assertEquals(Instant.parse("2025-03-01T12:00:25Z"), viewModel.uiState.value.now)
  }

  /** Signing in updates the reference time without waiting for a visible screen or tick. */
  @Test
  fun now_signInUpdatesTimeWithoutUiStateCollector() = runTest {
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            FlowGroupSessionsRepository(flowOf(Result.success(emptyList()))),
            profileRepository,
            clock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )
    clock.currentInstant = Instant.parse("2025-03-01T12:00:25Z")

    authRepository.currentUser.value = account(ALICE)
    runCurrent()

    assertEquals(Instant.parse("2025-03-01T12:00:25Z"), viewModel.uiState.value.now)
  }

  /** The refreshed list cutoff and UI reference time come from the same clock read. */
  @Test
  fun now_refreshUsesSameInstantAsRepositoryCutoff() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val sessionsRepository =
        FlowGroupSessionsRepository(flowOf(Result.success(emptyList<GroupSession>())))
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            sessionsRepository,
            profileRepository,
            AdvancingTestClock(now),
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )

    viewModel.refresh()
    runCurrent()

    assertEquals(sessionsRepository.endingAfters.last(), viewModel.uiState.value.now)
  }

  /** The first signed-in state carries the injected time before the repository emits. */
  @Test
  fun now_startsFromTheInjectedClock() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            FlowGroupSessionsRepository(emissions),
            profileRepository,
            MutableTestClock(now),
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )

    assertEquals(now, viewModel.uiState.value.now)
    assertTrue(viewModel.uiState.value.isLoading)
  }

  /** The time changes after exactly 30 seconds of being observed, and not one moment earlier. */
  @Test
  fun now_refreshesEveryThirtySecondsWhileObserved() = runTest {
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            repository,
            profileRepository,
            clock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )
    observe(viewModel)
    val later = now.plusSeconds(30)
    clock.currentInstant = later

    advanceTimeBy(29_999)
    runCurrent()
    assertEquals(now, viewModel.uiState.value.now)

    advanceTimeBy(1)
    runCurrent()
    assertEquals(later, viewModel.uiState.value.now)
  }

  /** A time tick updates the clock without clearing an existing join failure. */
  @Test
  fun now_refreshPreservesJoinFailure() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val own = session("own", ALICE)
    repository.saveSession(ALICE, own)
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            repository,
            profileRepository,
            clock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )
    viewModel.join("missing")
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
    observe(viewModel)
    clock.currentInstant = Instant.parse("2025-03-01T12:00:30Z")

    advanceTimeBy(30_000)
    runCurrent()

    assertEquals(Instant.parse("2025-03-01T12:00:30Z"), viewModel.uiState.value.now)
    assertEquals(GroupSessionsError.JOIN_FAILED, viewModel.uiState.value.error)
  }

  /** A time tick preserves loading while the signed-in repository has not emitted. */
  @Test
  fun now_refreshPreservesLoadingBeforeFirstEmission() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            FlowGroupSessionsRepository(emissions),
            profileRepository,
            clock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )
    observe(viewModel)
    clock.currentInstant = Instant.parse("2025-03-01T12:00:30Z")

    advanceTimeBy(30_000)
    runCurrent()

    assertEquals(Instant.parse("2025-03-01T12:00:30Z"), viewModel.uiState.value.now)
    assertTrue(viewModel.uiState.value.isLoading)
  }

  /** A hidden screen does no work, and coming back shows the right time without waiting. */
  @Test
  fun now_pausesWithoutCollectorAndUpdatesAsSoonAsOneReturns() = runTest {
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            repository,
            profileRepository,
            clock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )
    val hiddenAt = now.plusSeconds(600)
    observe(viewModel).cancel()
    clock.currentInstant = hiddenAt

    advanceTimeBy(60_000)
    runCurrent()
    val timeWithoutCollector = viewModel.uiState.value.now

    val collector = observe(viewModel)
    val timeAfterReturn = viewModel.uiState.value.now
    collector.cancel()
    // Cancel ViewModel work before assertions so its collectors cannot mutate state afterward.
    viewModel.viewModelScope.cancel()

    assertEquals(now, timeWithoutCollector)
    assertEquals(hiddenAt, timeAfterReturn)
  }

  /** The sign-out reset rebuilds the whole state, which must not bring back an old time. */
  @Test
  fun now_staysCurrentAfterSignOut() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            repository,
            profileRepository,
            clock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )
    val signedOutAt = now.plusSeconds(600)
    clock.currentInstant = signedOutAt

    authRepository.currentUser.value = null

    assertEquals(signedOutAt, viewModel.uiState.value.now)
  }

  /** A refresh only changes the time: the list, search text and filter stay as they were. */
  @Test
  fun now_refreshLeavesTheRestOfTheStateUntouched() = runTest {
    authRepository.currentUser.value = account(ALICE)
    repository.saveSession(ALICE, session("own", ALICE))
    val clock = MutableTestClock(now)
    val viewModel =
        GroupSessionsViewModel(
            authRepository,
            repository,
            profileRepository,
            clock,
            locationProvider = FakeLocationProvider(),
            campusCenter = testCampusCenter,
        )
    viewModel.onQueryChange("bio")
    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    val before = viewModel.uiState.value
    observe(viewModel)
    val later = now.plusSeconds(30)
    clock.currentInstant = later

    advanceTimeBy(30_000)
    runCurrent()

    assertEquals(before.copy(now = later), viewModel.uiState.value)
  }

  /** A host lookup does not delay the list, then supplies the resolved username. */
  @Test
  fun hostNameLookupDoesNotBlockListAndPublishesNameWhenFinished() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val pending = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, pending)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val hosted = session("hosted", BOB)

    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()

    assertEquals(listOf(BOB), profileRepository.getProfileUserIds)
    assertEquals(listOf(hosted.id), visibleIds(viewModel))
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.sessions.single().hostName)

    pending.complete(Result.success(UserProfile(uid = BOB, username = "Bob")))
    runCurrent()

    assertEquals("Bob", viewModel.uiState.value.sessions.single().hostName)
  }

  /**
   * Missing, blank and failed profiles stay null without list errors and retry on new emissions.
   */
  @Test
  fun unusableHostProfilesStayNullAndAreRetriedOnNextListUpdate() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue("blank-host", Result.success(UserProfile("blank-host", "   ")))
    profileRepository.enqueue(
        "failed-host",
        Result.failure<UserProfile?>(IllegalStateException("profile lookup failed")),
    )
    profileRepository.enqueue("missing-host", Result.success(null))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val sessions =
        listOf(
            session("blank", "blank-host"),
            session("same-blank-host", "blank-host"),
            session("failed", "failed-host"),
            session("missing", "missing-host"),
        )

    emissions.emit(Result.success(sessions))
    runCurrent()

    assertEquals(sessions.map { it.id }, visibleIds(viewModel))
    assertEquals(
        setOf("blank-host", "failed-host", "missing-host"),
        profileRepository.getProfileUserIds.toSet(),
    )
    assertEquals(1, profileRepository.getProfileUserIds.count { it == "blank-host" })
    assertTrue(viewModel.uiState.value.sessions.all { it.hostName == null })
    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isLoading)

    emissions.emit(Result.success(sessions))
    runCurrent()

    assertEquals(2, profileRepository.getProfileUserIds.count { it == "blank-host" })
    assertEquals(2, profileRepository.getProfileUserIds.count { it == "failed-host" })
    assertEquals(2, profileRepository.getProfileUserIds.count { it == "missing-host" })
    assertNull(viewModel.uiState.value.error)
  }

  /** Host lookups deduplicate, skip the signed-in user and survive later list and view updates. */
  @Test
  fun hostNamesAreCachedAcrossHostsFiltersTicksAndListUpdates() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val pendingBob = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, pendingBob)
    profileRepository.enqueue("carol", Result.success(UserProfile("carol", "Carol")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val clock = MutableTestClock(now)
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions), clock = clock)
    val own = session("own", ALICE, subject = "Art")
    val bobFirst = session("bob-first", BOB, listOf(BOB, ALICE), subject = "Biology")
    val bobSecond = session("bob-second", BOB, subject = "Chemistry")
    val carol = session("carol", "carol", listOf("carol", ALICE), subject = "Art")

    emissions.emit(Result.success(listOf(own, bobFirst, bobSecond, carol)))
    runCurrent()

    assertEquals(1, profileRepository.getProfileUserIds.count { it == BOB })
    assertEquals(1, profileRepository.getProfileUserIds.count { it == "carol" })
    assertFalse(profileRepository.getProfileUserIds.contains(ALICE))
    emissions.emit(Result.success(listOf(own, bobFirst, bobSecond, carol)))
    runCurrent()
    assertEquals(1, profileRepository.getProfileUserIds.count { it == BOB })

    pendingBob.complete(Result.success(UserProfile(uid = BOB, username = "Bob")))
    runCurrent()
    assertEquals(
        "Bob",
        viewModel.uiState.value.sessions.first { it.session.id == "bob-first" }.hostName,
    )

    viewModel.onQueryChange("biology")
    runCurrent()
    assertEquals(listOf("bob-first"), visibleIds(viewModel))
    assertEquals("Bob", viewModel.uiState.value.sessions.single().hostName)
    viewModel.onQueryChange("")
    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    runCurrent()
    assertEquals(listOf(null, "Bob", "Carol"), viewModel.uiState.value.sessions.map { it.hostName })

    val collector = observe(viewModel)
    clock.currentInstant = now.plusSeconds(30)
    advanceTimeBy(30_000)
    runCurrent()
    assertEquals(now.plusSeconds(30), viewModel.uiState.value.now)
    assertEquals(listOf(null, "Bob", "Carol"), viewModel.uiState.value.sessions.map { it.hostName })
    collector.cancel()

    val bobLater = session("bob-later", BOB, listOf(BOB, ALICE), subject = "Physics")
    emissions.emit(Result.success(listOf(own, bobFirst, bobSecond, carol, bobLater)))
    runCurrent()
    assertEquals(1, profileRepository.getProfileUserIds.count { it == BOB })
    assertEquals(
        "Bob",
        viewModel.uiState.value.sessions.first { it.session.id == "bob-later" }.hostName,
    )
  }

  /** A lookup held for one account does not block the next account's lookup for that host. */
  @Test
  fun pendingHostLookupDoesNotBlockLookupAfterUserChange() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val pending = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, pending)
    profileRepository.enqueue(BOB, Result.success(UserProfile(uid = BOB, username = "Bob")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val hosted = session("hosted", BOB)

    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()
    assertEquals(1, profileRepository.getProfileUserIds.count { it == BOB })
    assertFalse(pending.isCompleted)

    authRepository.currentUser.value = account("carol")
    runCurrent()
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()

    assertEquals(2, profileRepository.getProfileUserIds.count { it == BOB })
    assertEquals(
        "Bob",
        viewModel.uiState.value.sessions.first { it.session.id == hosted.id }.hostName,
    )

    pending.complete(Result.success(UserProfile(uid = BOB, username = "Stale Bob")))
    runCurrent()
    assertEquals(
        "Bob",
        viewModel.uiState.value.sessions.first { it.session.id == hosted.id }.hostName,
    )
  }

  /** Refresh repeats profile reads so a changed username replaces the cached value. */
  @Test
  fun refreshReadsHostProfileAgainAndShowsChangedUsername() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(
        BOB,
        Result.success(UserProfile(uid = BOB, username = "Bob")),
    )
    profileRepository.enqueue(BOB, Result.success(UserProfile(uid = BOB, username = "Robert")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val hosted = session("hosted", BOB)
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()
    assertEquals("Bob", viewModel.uiState.value.sessions.single().hostName)

    viewModel.refresh()
    runCurrent()
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()

    assertEquals("Robert", viewModel.uiState.value.sessions.single().hostName)
  }

  /** A held lookup finishing after sign-out cannot restore or mutate the signed-out list. */
  @Test
  fun lateHostLookupAfterSignOutDoesNotChangeStateOrCrash() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val pending = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, pending)
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val hosted = session("hosted", BOB)
    val signedOutState =
        GroupSessionsUiState(now = now, isLoading = false, campusCenter = testCampusCenter)
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()
    assertEquals(listOf(BOB), profileRepository.getProfileUserIds)
    assertFalse(pending.isCompleted)
    assertEquals(listOf(hosted.id), visibleIds(viewModel))
    assertNull(viewModel.uiState.value.sessions.single().hostName)

    authRepository.currentUser.value = null
    runCurrent()
    assertEquals(signedOutState, viewModel.uiState.value)

    pending.complete(Result.success(UserProfile(uid = BOB, username = "Bob")))
    runCurrent()

    assertEquals(signedOutState, viewModel.uiState.value)
  }

  /** When currentUser becomes null, userLocation is preserved in uiState. */
  @Test
  fun currentUserBecomesNull_preservesUserLocation() = runTest {
    val location = Location(46.52, 6.56)
    val customProvider = LocationProvider { flowOf(location) }
    val viewModel = createViewModel(locationProvider = customProvider)
    val collector = observe(viewModel)
    runCurrent()

    assertEquals(location, viewModel.uiState.value.userLocation)

    authRepository.currentUser.value = null
    runCurrent()

    assertEquals(location, viewModel.uiState.value.userLocation)
    collector.cancel()
  }

  /** Calling refreshLocation restarts observing the location provider. */
  @Test
  fun refreshLocation_restartsCollectionOnLocationProvider() = runTest {
    var collectionCount = 0
    val customProvider = LocationProvider {
      collectionCount++
      flowOf(Location(46.52, 6.56))
    }
    val viewModel = createViewModel(locationProvider = customProvider)
    observe(viewModel)
    runCurrent()
    val initialCollections = collectionCount
    assertTrue(initialCollections >= 1)

    viewModel.refreshLocation()
    runCurrent()

    assertTrue(collectionCount > initialCollections)
  }

  /** The roster shares the pending host read and loads only the selected session's members. */
  @Test
  fun participantsShowHostFirstAndDistinguishUnknownFromFailedReads() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val host = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, host)
    profileRepository.enqueue(ALICE, Result.success(UserProfile(ALICE, "Alice")))
    profileRepository.enqueue("blank", Result.success(UserProfile("blank", "  ")))
    profileRepository.enqueue("failed", Result.failure(IllegalStateException("Unavailable")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val session = session("roster", BOB, listOf(ALICE, BOB, "blank", "missing", "failed"))
    emissions.emit(Result.success(listOf(session)))
    runCurrent()
    assertEquals(listOf(BOB), profileRepository.getProfileUserIds)

    viewModel.openParticipants(session.id)
    runCurrent()
    assertTrue(requireNotNull(viewModel.uiState.value.participantsSheet).isLoading)
    assertEquals(1, profileRepository.getProfileUserIds.count { it == BOB })

    host.complete(Result.success(UserProfile(BOB, "Bob")))
    runCurrent()
    val sheet = requireNotNull(viewModel.uiState.value.participantsSheet)
    assertFalse(sheet.isLoading)
    assertTrue(sheet.hasError)
    assertEquals(listOf(BOB, ALICE, "blank", "missing", "failed"), sheet.rows.map { it.userId })
    assertEquals(listOf("Bob", "Alice", null, null, null), sheet.rows.map { it.username })
    assertTrue(sheet.rows.first().isHost)
    assertEquals("Biology", sheet.sessionSubject)
    assertEquals(listOf(ALICE), sheet.rows.filter { it.isCurrentUser }.map { it.userId })
    assertEquals(listOf("failed"), sheet.rows.filter { it.isUnavailable }.map { it.userId })
    assertEquals("Bob", viewModel.uiState.value.sessions.single().hostName)
  }

  /** New membership snapshots drive an open roster and ignore late reads for departed members. */
  @Test
  fun participantsFollowMembershipAndCloseWhenSessionDisappears() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(BOB, Result.success(UserProfile(BOB, "Bob")))
    val departing = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold("departing", departing)
    profileRepository.enqueue("joining", Result.success(UserProfile("joining", "Joining")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val session = session("roster", BOB, listOf(BOB, "departing"))
    emissions.emit(Result.success(listOf(session)))
    runCurrent()
    viewModel.openParticipants(session.id)
    runCurrent()
    assertTrue(requireNotNull(viewModel.uiState.value.participantsSheet).isLoading)

    emissions.emit(Result.success(listOf(session.copy(memberIds = listOf(BOB, "joining")))))
    runCurrent()
    departing.complete(Result.failure(IllegalStateException("Late error")))
    runCurrent()
    val sheet = requireNotNull(viewModel.uiState.value.participantsSheet)
    assertFalse(sheet.isLoading)
    assertFalse(sheet.hasError)
    assertEquals(listOf("Bob", "Joining"), sheet.rows.map { it.username })
    // Once for the card and once on opening the roster; membership changes add no read.
    assertEquals(2, profileRepository.getProfileUserIds.count { it == BOB })

    emissions.emit(Result.success(emptyList()))
    runCurrent()
    assertNull(viewModel.uiState.value.participantsSheet)
  }

  /** A member who rejoins can recover from a failed profile read in the same open roster. */
  @Test
  fun participantsRetryFailedProfileWhenMemberRejoins() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(BOB, Result.success(UserProfile(BOB, "Bob")))
    profileRepository.enqueue("member", Result.failure(IllegalStateException("Unavailable")))
    profileRepository.enqueue("member", Result.success(UserProfile("member", "Member")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val session = session("roster", BOB, listOf(BOB, "member"))
    emissions.emit(Result.success(listOf(session)))
    runCurrent()
    viewModel.openParticipants(session.id)
    runCurrent()
    assertTrue(requireNotNull(viewModel.uiState.value.participantsSheet).hasError)

    emissions.emit(Result.success(listOf(session.copy(memberIds = listOf(BOB)))))
    runCurrent()
    assertFalse(requireNotNull(viewModel.uiState.value.participantsSheet).hasError)
    emissions.emit(Result.success(listOf(session)))
    runCurrent()

    val sheet = requireNotNull(viewModel.uiState.value.participantsSheet)
    assertEquals(2, profileRepository.getProfileUserIds.count { it == "member" })
    assertEquals(listOf("Bob", "Member"), sheet.rows.map { it.username })
    assertFalse(sheet.hasError)
  }

  /** Switching or dismissing the roster prevents late results from changing the selected sheet. */
  @Test
  fun lateParticipantReadCannotReplaceAnotherRosterOrReopenIt() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val departing = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold("departing", departing)
    profileRepository.enqueue(BOB, Result.success(UserProfile(BOB, "Bob")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val first = session("first", BOB, listOf(BOB, "departing"))
    val second = session("second", BOB)
    emissions.emit(Result.success(listOf(first, second)))
    runCurrent()
    viewModel.openParticipants(first.id)
    runCurrent()
    viewModel.openParticipants(second.id)
    runCurrent()

    departing.complete(Result.success(UserProfile("departing", "Departing")))
    runCurrent()
    assertEquals(
        listOf("Bob"),
        viewModel.uiState.value.participantsSheet?.rows?.map { it.username },
    )

    viewModel.dismissParticipants()
    runCurrent()
    assertNull(viewModel.uiState.value.participantsSheet)
  }

  /**
   * Participant rows and the host line get the URL from the profile read for the username. There is
   * no URL when it is missing or blank, or when the member has no username.
   */
  @Test
  fun pictureUrlsReachParticipantRowsAndHostLine() = runTest {
    authRepository.currentUser.value = account(ALICE)
    listOf(
            UserProfile(uid = BOB, username = "Bob", profilePictureUrl = "bob.jpg"),
            UserProfile(uid = ALICE, username = "Alice", profilePictureUrl = "alice.jpg"),
            UserProfile(uid = "none", username = "None"),
            UserProfile(uid = "blank", username = "Blank", profilePictureUrl = "  "),
            UserProfile(uid = "nameless", username = " ", profilePictureUrl = "nameless.jpg"),
        )
        .forEach { profileRepository.enqueue(it.uid, Result.success(it)) }
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val members = listOf(BOB, ALICE, "none", "blank", "nameless")
    val session = session("roster", BOB, members)
    emissions.emit(Result.success(listOf(session)))
    runCurrent()
    viewModel.openParticipants(session.id)
    runCurrent()

    val rows = requireNotNull(viewModel.uiState.value.participantsSheet).rows
    assertEquals(
        mapOf(
            BOB to "bob.jpg",
            ALICE to "alice.jpg",
            "none" to null,
            "blank" to null,
            "nameless" to null,
        ),
        rows.associate { it.userId to it.profilePictureUrl },
    )
    assertEquals("bob.jpg", viewModel.uiState.value.sessions.single().hostPictureUrl)
    // The host, already read for the card, is read once more on opening the roster.
    assertEquals((members + BOB).sorted(), profileRepository.getProfileUserIds.sorted())
  }

  /** Opening a roster reads every member again, so changed pictures show on rows and card. */
  @Test
  fun openingTheRosterReadsEveryMemberAgain() = runTest {
    authRepository.currentUser.value = account(ALICE)
    listOf(BOB to "bob", ALICE to "alice").forEach { (uid, name) ->
      listOf("old", "new").forEach { version ->
        profileRepository.enqueue(
            uid,
            Result.success(UserProfile(uid, name, "$name-$version.jpg")),
        )
      }
    }
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val session = session("roster", BOB, listOf(BOB, ALICE))
    emissions.emit(Result.success(listOf(session)))
    runCurrent()
    // The host was read for the card. This first opening reads Bob again and Alice once.
    viewModel.openParticipants(session.id)
    runCurrent()
    viewModel.dismissParticipants()
    runCurrent()

    viewModel.openParticipants(session.id)
    runCurrent()

    val rows = requireNotNull(viewModel.uiState.value.participantsSheet).rows
    assertEquals(listOf("bob-new.jpg", "alice-new.jpg"), rows.map { it.profilePictureUrl })
    assertEquals("bob-new.jpg", viewModel.uiState.value.sessions.single().hostPictureUrl)
    assertEquals(3, profileRepository.getProfileUserIds.count { it == BOB })
    assertEquals(2, profileRepository.getProfileUserIds.count { it == ALICE })
  }

  /** The earlier profile stays on screen while it is read again, and if that read fails. */
  @Test
  fun rosterKeepsTheEarlierProfileWhileReadingAgainAndWhenThatFails() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(BOB, Result.success(UserProfile(BOB, "Bob", "bob.jpg")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val session = session("roster", BOB)
    emissions.emit(Result.success(listOf(session)))
    runCurrent()
    val secondRead = CompletableDeferred<Result<UserProfile?>>()
    profileRepository.hold(BOB, secondRead)

    viewModel.openParticipants(session.id)
    runCurrent()
    val whileReading = requireNotNull(viewModel.uiState.value.participantsSheet)
    assertFalse(whileReading.isLoading)
    assertEquals("bob.jpg", whileReading.rows.single().profilePictureUrl)

    secondRead.complete(Result.failure(IllegalStateException("Unavailable")))
    runCurrent()
    val afterFailure = requireNotNull(viewModel.uiState.value.participantsSheet)
    assertFalse(afterFailure.hasError)
    assertEquals("Bob", afterFailure.rows.single().username)
    assertEquals("bob.jpg", afterFailure.rows.single().profilePictureUrl)
    assertEquals("bob.jpg", viewModel.uiState.value.sessions.single().hostPictureUrl)
  }

  /** A profile that no longer has a username replaces the earlier one. */
  @Test
  fun rosterDropsTheEarlierProfileWhenThereIsNoUsernameAnyMore() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(BOB, Result.success(UserProfile(BOB, "Bob", "bob.jpg")))
    profileRepository.enqueue(BOB, Result.success(null))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val session = session("roster", BOB)
    emissions.emit(Result.success(listOf(session)))
    runCurrent()

    viewModel.openParticipants(session.id)
    runCurrent()

    val row = requireNotNull(viewModel.uiState.value.participantsSheet).rows.single()
    assertNull(row.username)
    assertNull(row.profilePictureUrl)
    assertNull(viewModel.uiState.value.sessions.single().hostName)
    assertNull(viewModel.uiState.value.sessions.single().hostPictureUrl)
  }

  /** Coming back to the screen reads other hosts again; the first observation adds no read. */
  @Test
  fun returningToTheScreenReadsOtherHostsAgain() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(BOB, Result.success(UserProfile(BOB, "Bob", "old.jpg")))
    profileRepository.enqueue(BOB, Result.success(UserProfile(BOB, "Bob", "new.jpg")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    emissions.emit(Result.success(listOf(session("hosted", BOB), session("own", ALICE))))
    runCurrent()
    val firstVisit = observe(viewModel)
    runCurrent()
    assertEquals(listOf(BOB), profileRepository.getProfileUserIds)

    firstVisit.cancel()
    runCurrent()
    observe(viewModel)
    runCurrent()

    assertEquals(listOf(BOB, BOB), profileRepository.getProfileUserIds)
    val hosted = viewModel.uiState.value.sessions.first { it.session.id == "hosted" }
    assertEquals("new.jpg", hosted.hostPictureUrl)
  }

  /** A session you created has no host picture URL, even once your own profile is cached. */
  @Test
  fun ownSessionCardHasNoHostPictureUrl() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(
        ALICE,
        Result.success(UserProfile(uid = ALICE, username = "Alice", profilePictureUrl = "a.jpg")),
    )
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val own = session("own", ALICE)
    emissions.emit(Result.success(listOf(own)))
    runCurrent()
    viewModel.openParticipants(own.id)
    runCurrent()
    // A new snapshot rebuilds the cards after the profile has been cached.
    emissions.emit(Result.success(listOf(own)))
    runCurrent()

    val row = requireNotNull(viewModel.uiState.value.participantsSheet).rows.single()
    assertEquals("a.jpg", row.profilePictureUrl)
    assertNull(viewModel.uiState.value.sessions.single().hostPictureUrl)
  }

  /** Refresh reads the host profile again, so a removed picture is not shown from the cache. */
  @Test
  fun refreshDropsAPictureUrlTheHostRemoved() = runTest {
    authRepository.currentUser.value = account(ALICE)
    profileRepository.enqueue(
        BOB,
        Result.success(UserProfile(uid = BOB, username = "Bob", profilePictureUrl = "bob.jpg")),
    )
    profileRepository.enqueue(BOB, Result.success(UserProfile(uid = BOB, username = "Bob")))
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = createViewModel(FlowGroupSessionsRepository(emissions))
    val hosted = session("hosted", BOB)
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()
    assertEquals("bob.jpg", viewModel.uiState.value.sessions.single().hostPictureUrl)

    viewModel.refresh()
    runCurrent()
    emissions.emit(Result.success(listOf(hosted)))
    runCurrent()

    assertEquals("Bob", viewModel.uiState.value.sessions.single().hostName)
    assertNull(viewModel.uiState.value.sessions.single().hostPictureUrl)
  }

  /** Collects the state like a visible screen does, until the test ends or the job is cancelled. */
  private fun TestScope.observe(viewModel: GroupSessionsViewModel) =
      backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

  private fun createViewModel(
      sessionsRepository: GroupSessionsRepository = repository,
      locationProvider: LocationProvider = FakeLocationProvider(),
      campusLocationRepository: CampusLocationRepository? = null,
      profiles: FakeProfileRepository = profileRepository,
      clock: Clock = MutableTestClock(now),
      campusCenter: Location = testCampusCenter,
      connectivityObserver: ConnectivityObserver? = null,
  ): GroupSessionsViewModel =
      GroupSessionsViewModel(
          authRepository = authRepository,
          repository = sessionsRepository,
          clock = clock,
          locationProvider = locationProvider,
          campusLocationRepository = campusLocationRepository,
          profileRepository = profiles,
          campusCenter = campusCenter,
          connectivityObserver = connectivityObserver,
      )

  private fun visibleIds(viewModel: GroupSessionsViewModel): List<String> =
      viewModel.uiState.value.sessions.map { it.session.id }

  private fun account(uid: String) = UserAccount(uid, "$uid@example.com", uid, null)

  private fun session(
      id: String,
      creator: String,
      members: List<String> = listOf(creator),
      subject: String? = "Biology",
      location: String? = null,
      description: String? = null,
      type: EventType = if (subject == null) EventType.OTHER else EventType.COURSE,
      startsAt: Instant = now.plusSeconds(60),
      endsAt: Instant = startsAt.plusSeconds(600),
  ) =
      GroupSession(
          id = id,
          createdBy = creator,
          memberIds = members,
          details =
              EventDetails(
                  type = type,
                  subject = subject,
                  startsAt = startsAt,
                  endsAt = endsAt,
                  location = location,
                  description = description,
              ),
          createdAt = now,
      )

  private fun campusLocation(
      name: String,
      zone: String = "CM",
      type: CampusLocationType = CampusLocationType.LECTURE_ROOM,
      latitude: Double = 0.0,
      longitude: Double = 0.0,
  ) =
      CampusLocation(
          name = name,
          zone = zone,
          type = type,
          floor = null,
          location = Location(latitude, longitude),
      )

  private class MutableTestClock(var currentInstant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock =
        require(zone == ZoneOffset.UTC) { "Test clock only supports UTC" }.let { this }

    override fun instant(): Instant = currentInstant
  }

  private class AdvancingTestClock(private val firstInstant: Instant) : Clock() {
    private var reads = 0L

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock =
        require(zone == ZoneOffset.UTC) { "Test clock only supports UTC" }.let { this }

    override fun instant(): Instant = firstInstant.plusSeconds(reads++)
  }

  private class FlowGroupSessionsRepository(
      private val sessionsFlow: Flow<Result<List<GroupSession>>>
  ) : GroupSessionsRepository {
    val endingAfters = mutableListOf<Instant>()
    var observationCount = 0
      private set

    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> {
      endingAfters.add(endingAfter)
      observationCount += 1
      return sessionsFlow
    }

    override fun newSessionId(): String = "unused"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)
  }

  /** Campus-location repository fake for success and failure cases in the ViewModel tests. */
  private class FakeCampusLocationRepository(
      private val locations: Result<List<CampusLocation>>,
  ) : CampusLocationRepository {
    constructor(locations: List<CampusLocation>) : this(Result.success(locations))

    override suspend fun getAllLocations(): Result<List<CampusLocation>> = locations

    override suspend fun search(filter: CampusLocationFilter): Result<List<CampusLocation>> =
        locations.map { entries ->
          entries.filter { location ->
            (filter.query.isNullOrBlank() ||
                location.name.contains(filter.query.trim(), ignoreCase = true)) &&
                (filter.zone.isNullOrBlank() ||
                    location.zone.equals(filter.zone.trim(), ignoreCase = true)) &&
                (filter.types.isEmpty() || location.type in filter.types)
          }
        }

    override suspend fun getZones(): Result<List<String>> = locations.map { entries ->
      entries.map { it.zone }.distinct().sorted()
    }
  }

  /** Repository fake that lets tests complete join and leave calls after an auth change. */
  private class DelayedGroupSessionsRepository : GroupSessionsRepository {
    private val sessions = MutableStateFlow<Result<List<GroupSession>>>(Result.success(emptyList()))
    val joinStarted = CompletableDeferred<Unit>()
    val leaveStarted = CompletableDeferred<Unit>()
    val joinResult = CompletableDeferred<Result<Unit>>()
    val leaveResult = CompletableDeferred<Result<Unit>>()

    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> = sessions

    override fun newSessionId(): String = "unused"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> {
      joinStarted.complete(Unit)
      return joinResult.await()
    }

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> {
      leaveStarted.complete(Unit)
      return leaveResult.await()
    }
  }

  /** Repository fake for holding actions while exposing and counting session observations. */
  private class PendingActionGroupSessionsRepository(
      private val initialSessions: List<GroupSession>,
  ) : GroupSessionsRepository {
    val refreshedSessions = CompletableDeferred<Result<List<GroupSession>>>()
    val joinStarted = CompletableDeferred<Unit>()
    val leaveStarted = CompletableDeferred<Unit>()
    val joinResult = CompletableDeferred<Result<Unit>>()
    val leaveResult = CompletableDeferred<Result<Unit>>()
    var observationCount = 0
      private set

    var joinCalls = 0
      private set

    var leaveCalls = 0
      private set

    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> = flow {
      observationCount += 1
      emit(
          if (observationCount == 1) {
            Result.success(initialSessions)
          } else {
            refreshedSessions.await()
          },
      )
    }

    override fun newSessionId(): String = "unused"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> {
      joinCalls += 1
      joinStarted.complete(Unit)
      return joinResult.await()
    }

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> {
      leaveCalls += 1
      leaveStarted.complete(Unit)
      return leaveResult.await()
    }
  }

  private class FakeProfileRepository : ProfileRepository {
    val getProfileUserIds = mutableListOf<String>()
    private val outcomes = mutableMapOf<String, MutableList<Result<UserProfile?>>>()
    private val heldLookups = mutableMapOf<String, CompletableDeferred<Result<UserProfile?>>>()

    fun enqueue(userId: String, result: Result<UserProfile?>) {
      outcomes.getOrPut(userId) { mutableListOf() }.add(result)
    }

    fun hold(userId: String, result: CompletableDeferred<Result<UserProfile?>>) {
      heldLookups[userId] = result
    }

    override suspend fun getProfile(userId: String): Result<UserProfile?> {
      getProfileUserIds += userId
      heldLookups.remove(userId)?.let { pending ->
        return withContext(NonCancellable) { pending.await() }
      }
      val configured = outcomes[userId]
      // The last answer repeats, as a stored profile does when it is read again.
      return when {
        configured.isNullOrEmpty() -> Result.success(null)
        configured.size > 1 -> configured.removeAt(0)
        else -> configured.single()
      }
    }

    override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("Not needed in this test"))

    override suspend fun createProfile(userId: String): Result<UserProfile> =
        Result.success(UserProfile(uid = userId))

    override suspend fun updateProfilePictureUrl(userId: String, url: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not needed in this test"))
  }

  private companion object {
    const val ALICE = "alice"
    const val BOB = "bob"
  }
}
