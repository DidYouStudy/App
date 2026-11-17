// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import com.android.sample.model.auth.UserAccount
import com.android.sample.model.connectivity.ConnectivityObserver
import com.android.sample.model.connectivity.FakeConnectivityObserver
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
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
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the offline behavior of [GroupSessionsViewModel]: the [GroupSessionsUiState]
 * offline flag, and the guarantee that sessions already loaded stay visible, searchable and
 * filterable without a connection.
 *
 * The repository double stands in for Firestore with its on-device cache: offline it keeps serving
 * the sessions it already has and never reports an error.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSessionsViewModelOfflineTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val now = Instant.parse("2025-03-01T12:00:00Z")
  private lateinit var authRepository: TestAuthRepository
  private lateinit var connectivity: FakeConnectivityObserver
  private lateinit var cache: CachedSessionsRepository

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    authRepository = TestAuthRepository()
    connectivity = FakeConnectivityObserver()
    cache = CachedSessionsRepository(cachedSessions())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  // ---------------------------------------------------------------------------------------------
  // The offline flag
  // ---------------------------------------------------------------------------------------------

  /** A new state is online until the ViewModel learns otherwise. */
  @Test
  fun uiStateIsNotOfflineByDefault() {
    assertFalse(GroupSessionsUiState(now = now).isOffline)
  }

  /** Copying the state for another change keeps the offline flag. */
  @Test
  fun copyingTheStateKeepsTheOfflineFlag() {
    val offline = GroupSessionsUiState(now = now, isOffline = true)

    assertTrue(offline.copy(query = "bio").isOffline)
  }

  @Test
  fun reportsOnlineWhileTheObserverReportsAConnection() = runTest {
    val viewModel = openScreen()

    assertFalse(viewModel.uiState.value.isOffline)
  }

  @Test
  fun reportsOfflineWhenTheScreenOpensWithoutAConnection() = runTest {
    connectivity.isOnline.value = false

    val viewModel = openScreen()

    assertTrue(viewModel.uiState.value.isOffline)
  }

  /** The offline indication appears as soon as the connection is lost. */
  @Test
  fun reportsOfflineWhenTheConnectionIsLost() = runTest {
    val viewModel = openScreen()

    connectivity.isOnline.value = false
    runCurrent()

    assertTrue(viewModel.uiState.value.isOffline)
  }

  /** The offline indication disappears as soon as the connection returns. */
  @Test
  fun stopsReportingOfflineWhenTheConnectionReturns() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    connectivity.isOnline.value = true
    runCurrent()

    assertFalse(viewModel.uiState.value.isOffline)
  }

  @Test
  fun followsRepeatedChangesOfConnectivity() = runTest {
    val updates = MutableSharedFlow<Boolean>()
    val viewModel = openScreen(connectivityObserver = ConnectivityObserver { updates })

    updates.emit(false)
    assertTrue(viewModel.uiState.value.isOffline)
    updates.emit(true)
    assertFalse(viewModel.uiState.value.isOffline)
    updates.emit(false)
    updates.emit(false)
    assertTrue(viewModel.uiState.value.isOffline)
    updates.emit(true)
    assertFalse(viewModel.uiState.value.isOffline)
  }

  /** Without an observer the app cannot know, so it must never claim to be offline. */
  @Test
  fun neverReportsOfflineWithoutAConnectivityObserver() = runTest {
    connectivity.isOnline.value = false

    val viewModel = openScreen(connectivityObserver = null)

    assertFalse(viewModel.uiState.value.isOffline)
  }

  /** An observer that has nothing to say leaves the state at its online default. */
  @Test
  fun staysOnlineWhenTheObserverEmitsNothing() = runTest {
    val viewModel = openScreen(connectivityObserver = ConnectivityObserver { emptyFlow() })

    assertFalse(viewModel.uiState.value.isOffline)
  }

  /** A flow that completes, as the Android observer does without permission, keeps its value. */
  @Test
  fun keepsTheLastReportedOfflineStateWhenTheObserverCompletes() = runTest {
    val viewModel = openScreen(connectivityObserver = ConnectivityObserver { flowOf(false) })

    assertTrue(viewModel.uiState.value.isOffline)
  }

  @Test
  fun keepsTheLastReportedOnlineStateWhenTheObserverCompletes() = runTest {
    val viewModel = openScreen(connectivityObserver = ConnectivityObserver { flowOf(true) })

    assertFalse(viewModel.uiState.value.isOffline)
  }

  // ---------------------------------------------------------------------------------------------
  // Collecting only while the screen is visible
  // ---------------------------------------------------------------------------------------------

  @Test
  fun doesNotCollectConnectivityBeforeTheStateIsObserved() = runTest {
    authRepository.currentUser.value = account(ALICE)
    createViewModel()
    runCurrent()

    assertEquals(0, connectivity.activeCollectors)
  }

  @Test
  fun collectsConnectivityWhileTheStateIsObserved() = runTest {
    openScreen()

    assertEquals(1, connectivity.activeCollectors)
  }

  @Test
  fun releasesTheConnectivityCollectionWhenTheLastObserverLeaves() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()
    val observer = observe(viewModel)
    runCurrent()

    observer.cancel()
    runCurrent()

    assertEquals(0, connectivity.activeCollectors)
  }

  /** Two collectors (for example a rotation overlap) share one connectivity collection. */
  @Test
  fun keepsCollectingWhileAnotherObserverRemains() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()
    val first = observe(viewModel)
    observe(viewModel)
    runCurrent()

    first.cancel()
    runCurrent()
    connectivity.isOnline.value = false
    runCurrent()

    assertTrue(viewModel.uiState.value.isOffline)
    assertEquals(1, connectivity.activeCollectors)
  }

  /** A change that happens while the screen is hidden is applied when the screen comes back. */
  @Test
  fun appliesAConnectivityChangeMadeWhileHiddenWhenTheScreenReturns() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()
    val firstObserver = observe(viewModel)
    runCurrent()
    firstObserver.cancel()
    runCurrent()

    connectivity.isOnline.value = false
    runCurrent()
    assertFalse(viewModel.uiState.value.isOffline)

    observe(viewModel)
    runCurrent()

    assertTrue(viewModel.uiState.value.isOffline)
  }

  /** The reverse case: the indication must not stay stuck after the connection came back. */
  @Test
  fun clearsAStaleOfflineStateWhenTheScreenReturnsOnline() = runTest {
    connectivity.isOnline.value = false
    authRepository.currentUser.value = account(ALICE)
    val viewModel = createViewModel()
    val firstObserver = observe(viewModel)
    runCurrent()
    firstObserver.cancel()
    runCurrent()

    connectivity.isOnline.value = true
    runCurrent()
    assertTrue(viewModel.uiState.value.isOffline)

    observe(viewModel)
    runCurrent()

    assertFalse(viewModel.uiState.value.isOffline)
  }

  // ---------------------------------------------------------------------------------------------
  // The offline flag across sign-in changes
  // ---------------------------------------------------------------------------------------------

  /** Connectivity belongs to the device, so signing out must not reset it. */
  @Test
  fun signingOutKeepsTheOfflineState() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    authRepository.currentUser.value = null
    runCurrent()

    assertTrue(viewModel.uiState.value.isOffline)
    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(emptyList<String>(), visibleIds(viewModel))
  }

  @Test
  fun signingOutKeepsTheOnlineState() = runTest {
    val viewModel = openScreen()

    authRepository.currentUser.value = null
    runCurrent()

    assertFalse(viewModel.uiState.value.isOffline)
  }

  @Test
  fun switchingUsersKeepsTheOfflineState() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    authRepository.currentUser.value = account(BOB)
    runCurrent()

    assertTrue(viewModel.uiState.value.isOffline)
  }

  @Test
  fun signingInAgainAfterSignOutStillFollowsConnectivity() = runTest {
    val viewModel = openScreen()
    authRepository.currentUser.value = null
    runCurrent()

    authRepository.currentUser.value = account(ALICE)
    connectivity.isOnline.value = false
    runCurrent()

    assertTrue(viewModel.uiState.value.isOffline)
  }

  // ---------------------------------------------------------------------------------------------
  // Sessions already loaded stay visible
  // ---------------------------------------------------------------------------------------------

  @Test
  fun showsTheCachedSessionsWhenTheScreenOpensOffline() = runTest {
    connectivity.isOnline.value = false

    val viewModel = openScreen()

    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  /** The acceptance case: sessions loaded online stay on screen when the connection drops. */
  @Test
  fun keepsTheLoadedSessionsWhenTheConnectionIsLost() = runTest {
    val viewModel = openScreen()
    val before = viewModel.uiState.value.sessions

    connectivity.isOnline.value = false
    runCurrent()

    assertEquals(before, viewModel.uiState.value.sessions)
    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun keepsTheLoadedSessionsWhenTheConnectionReturns() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    connectivity.isOnline.value = true
    runCurrent()

    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
    assertFalse(viewModel.uiState.value.isOffline)
  }

  /** Going offline must not restart the repository listener, which would drop the cached list. */
  @Test
  fun changesOfConnectivityDoNotObserveTheRepositoryAgain() = runTest {
    openScreen()

    connectivity.isOnline.value = false
    runCurrent()
    connectivity.isOnline.value = true
    runCurrent()

    assertEquals(1, cache.observationCount)
  }

  /** Reading the cache is read-only: observing offline never asks the repository to write. */
  @Test
  fun showingCachedSessionsOfflineNeverWrites() = runTest {
    connectivity.isOnline.value = false
    openScreen()

    assertEquals(0, cache.writeCalls)
  }

  @Test
  fun waitsForTheCachedListWhileOfflineAndThenShowsIt() = runTest {
    connectivity.isOnline.value = false
    val emissions = MutableSharedFlow<Result<List<GroupSession>>>()
    val viewModel = openScreen(repository = FlowGroupSessionsRepository(emissions))

    assertTrue(viewModel.uiState.value.isOffline)
    assertTrue(viewModel.uiState.value.isLoading)

    emissions.emit(Result.success(cachedSessions()))

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
    assertTrue(viewModel.uiState.value.isOffline)
  }

  @Test
  fun showsAnEmptyCacheAsAnEmptyListWithoutAnError() = runTest {
    connectivity.isOnline.value = false
    cache.snapshots.value = Result.success(emptyList())

    val viewModel = openScreen()

    assertEquals(emptyList<String>(), visibleIds(viewModel))
    assertFalse(viewModel.uiState.value.isLoading)
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.isOffline)
  }

  /** The local cache can change offline (a write waiting to sync), and the list follows it. */
  @Test
  fun showsANewCacheSnapshotDeliveredWhileOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()
    val extra = session("extra", ALICE, subject = "Physics", startsAt = now.plusSeconds(240))

    cache.snapshots.value = Result.success(cachedSessions() + extra)

    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA, "extra"), visibleIds(viewModel))
    assertTrue(viewModel.uiState.value.isOffline)
  }

  /** A failed load must not blank the list that is already on screen. */
  @Test
  fun keepsTheCachedSessionsWhenALoadFailsOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    cache.snapshots.value = Result.failure(EventsException(EventsError.STORAGE_FAILURE))

    assertEquals(GroupSessionsError.LOAD_FAILED, viewModel.uiState.value.error)
    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
    assertTrue(viewModel.uiState.value.isOffline)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  @Test
  fun recoversFromALoadFailureOnceTheConnectionAndListReturn() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()
    cache.snapshots.value = Result.failure(EventsException(EventsError.STORAGE_FAILURE))

    connectivity.isOnline.value = true
    cache.snapshots.value = Result.success(cachedSessions())
    runCurrent()

    assertNull(viewModel.uiState.value.error)
    assertFalse(viewModel.uiState.value.isOffline)
    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
  }

  @Test
  fun refreshingWhileOfflineKeepsTheCachedSessionsAndTheOfflineState() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    viewModel.refresh()
    runCurrent()

    assertEquals(2, cache.observationCount)
    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
    assertTrue(viewModel.uiState.value.isOffline)
    assertFalse(viewModel.uiState.value.isLoading)
  }

  // ---------------------------------------------------------------------------------------------
  // Search and filters keep working on the cached list
  // ---------------------------------------------------------------------------------------------

  @Test
  fun searchesTheCachedListBySubjectOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    viewModel.onQueryChange("bio")
    runCurrent()

    assertEquals(listOf(BIOLOGY), visibleIds(viewModel))
    assertTrue(viewModel.uiState.value.isOffline)
  }

  @Test
  fun searchesTheCachedListByLocationOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    viewModel.onQueryChange("CM 1")
    runCurrent()

    assertEquals(listOf(CHEMISTRY), visibleIds(viewModel))
  }

  @Test
  fun searchesTheCachedListByDescriptionOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    viewModel.onQueryChange("membranes")
    runCurrent()

    assertEquals(listOf(BIOLOGY), visibleIds(viewModel))
  }

  @Test
  fun searchOfflineIgnoresCaseAndSurroundingWhitespace() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    viewModel.onQueryChange("  CELLS  ")
    runCurrent()

    assertEquals(listOf(BIOLOGY), visibleIds(viewModel))
    assertEquals("  CELLS  ", viewModel.uiState.value.query)
  }

  /** No match is a normal empty result, not an error and not a reason to leave offline mode. */
  @Test
  fun searchWithoutAMatchOfflineGivesAnEmptyListWithoutAnError() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    viewModel.onQueryChange("zzz")
    runCurrent()

    assertEquals(emptyList<String>(), visibleIds(viewModel))
    assertNull(viewModel.uiState.value.error)
    assertTrue(viewModel.uiState.value.isOffline)
  }

  @Test
  fun clearingTheSearchOfflineBringsTheWholeCachedListBack() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()
    viewModel.onQueryChange("bio")
    runCurrent()

    viewModel.onQueryChange("")
    runCurrent()

    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
  }

  @Test
  fun joinedFilterKeepsMySessionsOfTheCachedListOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    runCurrent()

    assertEquals(listOf(CHEMISTRY, ALGEBRA), visibleIds(viewModel))
  }

  @Test
  fun sessionTypeFilterAppliesToTheCachedListOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen()

    viewModel.onSessionTypesSelected(setOf(EventType.EXAM))
    runCurrent()

    assertEquals(listOf(CHEMISTRY), visibleIds(viewModel))
  }

  @Test
  fun zoneFilterAppliesToTheCachedListOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(campusLocations = campusLocations())

    viewModel.onZonesFilterSelected(setOf("BC"))
    runCurrent()

    assertEquals(listOf(BIOLOGY), visibleIds(viewModel))
    assertTrue(viewModel.uiState.value.isOffline)
  }

  @Test
  fun severalZonesCombineOfflineAndHideSessionsWithoutAKnownRoom() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(campusLocations = campusLocations())

    viewModel.onZonesFilterSelected(setOf("BC", "CM"))
    runCurrent()

    assertEquals(listOf(BIOLOGY, CHEMISTRY), visibleIds(viewModel))
  }

  @Test
  fun zoneFilterOfflineIgnoresCaseAndSurroundingWhitespace() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(campusLocations = campusLocations())

    viewModel.onZoneFilterSelected(" bc ")
    runCurrent()

    assertEquals(listOf(BIOLOGY), visibleIds(viewModel))
  }

  @Test
  fun anUnknownZoneOfflineLeavesNoSessions() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(campusLocations = campusLocations())

    viewModel.onZoneFilterSelected("XX")
    runCurrent()

    assertEquals(emptyList<String>(), visibleIds(viewModel))
    assertNull(viewModel.uiState.value.error)
  }

  @Test
  fun clearingTheZoneFilterOfflineBringsTheWholeCachedListBack() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(campusLocations = campusLocations())
    viewModel.onZoneFilterSelected("BC")
    runCurrent()

    viewModel.onZoneFilterSelected(null)
    runCurrent()

    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
  }

  /** Zones come from bundled data, so they are available without a connection. */
  @Test
  fun availableZonesAreStillOfferedOffline() = runTest {
    connectivity.isOnline.value = false

    val viewModel = openScreen(campusLocations = campusLocations())

    assertEquals(listOf("BC", "CM"), viewModel.uiState.value.availableZones)
  }

  /** The distance is computed on the device, so the radius works on the cached list. */
  @Test
  fun radiusFilterAppliesToTheCachedListOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(userLocation = USER, campusLocations = campusLocations())

    viewModel.onRadiusFilterSelected(500)
    runCurrent()

    // The far room is dropped; the session without a known room cannot be measured, so it stays.
    assertEquals(listOf(BIOLOGY, ALGEBRA), visibleIds(viewModel))
    assertTrue(viewModel.uiState.value.isOffline)
  }

  @Test
  fun aWideRadiusOfflineKeepsEverySession() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(userLocation = USER, campusLocations = campusLocations())

    viewModel.onRadiusFilterSelected(5_000)
    runCurrent()

    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
  }

  @Test
  fun radiusFilterOfflineDoesNothingWithoutAUserLocation() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(userLocation = null, campusLocations = campusLocations())

    viewModel.onRadiusFilterSelected(500)
    runCurrent()

    assertEquals(listOf(BIOLOGY, CHEMISTRY, ALGEBRA), visibleIds(viewModel))
  }

  @Test
  fun cachedSessionsShowTheirDistanceOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(userLocation = USER, campusLocations = campusLocations())

    val distances = viewModel.uiState.value.sessions.map { it.proximity.formattedDistance }

    assertEquals("0m", distances[0])
    assertTrue(distances[1]!!.endsWith("km"))
    assertNull(distances[2])
  }

  @Test
  fun searchAndZoneFilterCombineOnTheCachedListOffline() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(campusLocations = campusLocations())

    viewModel.onZonesFilterSelected(setOf("BC", "CM"))
    viewModel.onQueryChange("chem")
    runCurrent()

    assertEquals(listOf(CHEMISTRY), visibleIds(viewModel))
  }

  @Test
  fun resettingAdvancedFiltersOfflineRestoresTheCachedListAndKeepsTheSearch() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(userLocation = USER, campusLocations = campusLocations())
    viewModel.onZonesFilterSelected(setOf("BC"))
    viewModel.onRadiusFilterSelected(500)
    viewModel.onQueryChange("a")
    runCurrent()

    viewModel.resetAdvancedFilters()
    runCurrent()

    assertEquals("a", viewModel.uiState.value.query)
    assertEquals(emptySet<String>(), viewModel.uiState.value.selectedZones)
    assertNull(viewModel.uiState.value.selectedRadiusMeters)
    assertTrue(viewModel.uiState.value.isOffline)
  }

  /** Filters chosen online keep applying after the connection is lost. */
  @Test
  fun filtersChosenOnlineStillApplyAfterGoingOffline() = runTest {
    val viewModel = openScreen(campusLocations = campusLocations())
    viewModel.onZonesFilterSelected(setOf("BC"))
    viewModel.onQueryChange("bio")
    runCurrent()

    connectivity.isOnline.value = false
    runCurrent()

    assertEquals(listOf(BIOLOGY), visibleIds(viewModel))
    assertEquals("bio", viewModel.uiState.value.query)
    assertEquals(setOf("BC"), viewModel.uiState.value.selectedZones)
  }

  /** Filters chosen offline are not lost when the connection comes back. */
  @Test
  fun filtersChosenOfflineSurviveTheConnectionReturning() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(campusLocations = campusLocations())
    viewModel.onZonesFilterSelected(setOf("CM"))
    viewModel.onQueryChange("chem")
    runCurrent()

    connectivity.isOnline.value = true
    runCurrent()

    assertEquals(listOf(CHEMISTRY), visibleIds(viewModel))
    assertEquals("chem", viewModel.uiState.value.query)
    assertFalse(viewModel.uiState.value.isOffline)
  }

  /** Every state update triggered by a filter must carry the offline flag along. */
  @Test
  fun changingFiltersNeverResetsTheOfflineFlag() = runTest {
    connectivity.isOnline.value = false
    val viewModel = openScreen(userLocation = USER, campusLocations = campusLocations())

    viewModel.onQueryChange("a")
    assertTrue(viewModel.uiState.value.isOffline)
    viewModel.onFilterChange(GroupSessionFilter.JOINED)
    assertTrue(viewModel.uiState.value.isOffline)
    viewModel.onSessionTypesSelected(setOf(EventType.EXAM))
    assertTrue(viewModel.uiState.value.isOffline)
    viewModel.onStartingTimeFilterSelected(GroupSessionTimeFilter.TODAY)
    assertTrue(viewModel.uiState.value.isOffline)
    viewModel.onRadiusFilterSelected(500)
    assertTrue(viewModel.uiState.value.isOffline)
    viewModel.onZonesFilterSelected(setOf("BC"))
    assertTrue(viewModel.uiState.value.isOffline)
    viewModel.onShowLabRoomTypeChange(true)
    assertTrue(viewModel.uiState.value.isOffline)
    viewModel.onHideFullSessionsChange(true)
    assertTrue(viewModel.uiState.value.isOffline)
    viewModel.clearError()
    assertTrue(viewModel.uiState.value.isOffline)
  }

  // ---------------------------------------------------------------------------------------------
  // The factory passes the observer through
  // ---------------------------------------------------------------------------------------------

  @Test
  fun factoryBuildsAViewModelThatTracksTheGivenObserver() = runTest {
    authRepository.currentUser.value = account(ALICE)
    connectivity.isOnline.value = false
    val factory =
        GroupSessionsViewModelFactory(
            authRepository = authRepository,
            repository = cache,
            profileRepository = FakeProfileRepository(),
            locationProvider = FakeLocationProvider(),
            connectivityObserver = connectivity,
        )

    val viewModel = factory.create(GroupSessionsViewModel::class.java)
    observe(viewModel)
    runCurrent()

    assertTrue(viewModel.uiState.value.isOffline)
    connectivity.isOnline.value = true
    runCurrent()
    assertFalse(viewModel.uiState.value.isOffline)
  }

  @Test
  fun factoryWithoutAnObserverBuildsAViewModelThatNeverReportsOffline() = runTest {
    authRepository.currentUser.value = account(ALICE)
    val factory =
        GroupSessionsViewModelFactory(
            authRepository = authRepository,
            repository = cache,
            profileRepository = FakeProfileRepository(),
            locationProvider = FakeLocationProvider(),
        )

    val viewModel = factory.create(GroupSessionsViewModel::class.java)
    observe(viewModel)
    runCurrent()

    assertFalse(viewModel.uiState.value.isOffline)
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  /** Collects the state like a visible screen does, until the test ends or the job is cancelled. */
  private fun TestScope.observe(viewModel: GroupSessionsViewModel): Job =
      backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }

  /**
   * Opens the screen like a user would: creates the ViewModel for a signed-in Alice, starts
   * collecting its state, and lets everything settle. Set [connectivity] first to start offline.
   */
  private fun TestScope.openScreen(
      repository: GroupSessionsRepository = cache,
      userLocation: Location? = null,
      campusLocations: List<CampusLocation>? = null,
      connectivityObserver: ConnectivityObserver? = connectivity,
  ): GroupSessionsViewModel {
    authRepository.currentUser.value = account(ALICE)
    val viewModel =
        createViewModel(
            repository = repository,
            locationProvider = FakeLocationProvider(userLocation),
            campusLocationRepository = campusLocations?.let { FakeCampusLocationRepository(it) },
            connectivityObserver = connectivityObserver,
        )
    observe(viewModel)
    runCurrent()
    return viewModel
  }

  private fun createViewModel(
      repository: GroupSessionsRepository = cache,
      locationProvider: LocationProvider = FakeLocationProvider(),
      campusLocationRepository: CampusLocationRepository? = null,
      connectivityObserver: ConnectivityObserver? = connectivity,
  ) =
      GroupSessionsViewModel(
          authRepository = authRepository,
          repository = repository,
          profileRepository = FakeProfileRepository(),
          clock = Clock.fixed(now, ZoneOffset.UTC),
          locationProvider = locationProvider,
          campusLocationRepository = campusLocationRepository,
          connectivityObserver = connectivityObserver,
      )

  private fun visibleIds(viewModel: GroupSessionsViewModel): List<String> =
      viewModel.uiState.value.sessions.map { it.session.id }

  private fun account(uid: String) = UserAccount(uid, "$uid@example.com", uid, null)

  /**
   * The sessions the device already holds, in start order: a biology lecture in BC, a chemistry
   * exam in CM that Alice joined, and an algebra session Alice hosts that has no known room.
   */
  private fun cachedSessions(): List<GroupSession> =
      listOf(
          session(
              BIOLOGY,
              BOB,
              subject = "Biology",
              location = "BC 01",
              description = "Cells and membranes",
              startsAt = now.plusSeconds(60),
          ),
          session(
              CHEMISTRY,
              BOB,
              members = listOf(BOB, ALICE),
              subject = "Chemistry",
              location = "CM 1 100",
              description = "Exam revision",
              type = EventType.EXAM,
              startsAt = now.plusSeconds(120),
          ),
          session(
              ALGEBRA,
              ALICE,
              subject = "Algebra",
              type = EventType.STUDY_SESSION,
              startsAt = now.plusSeconds(180),
          ),
      )

  /** BC 01 is where the user stands, CM 1 100 is about 1.5 km away. */
  private fun campusLocations(): List<CampusLocation> =
      listOf(
          campusLocation("BC 01", zone = "BC", location = USER),
          campusLocation("CM 1 100", zone = "CM", location = FAR_FROM_USER),
      )

  private fun session(
      id: String,
      creator: String,
      members: List<String> = listOf(creator),
      subject: String? = "Biology",
      location: String? = null,
      description: String? = null,
      type: EventType = EventType.COURSE,
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

  private fun campusLocation(name: String, zone: String, location: Location) =
      CampusLocation(
          name = name,
          zone = zone,
          type = CampusLocationType.LECTURE_ROOM,
          floor = null,
          location = location,
      )

  /**
   * Repository double that behaves like Firestore with its on-device cache while offline: it keeps
   * serving the list it already has, through [snapshots], and fails every write.
   *
   * [observationCount] and [writeCalls] let tests check that the ViewModel observes once and never
   * writes while merely showing sessions.
   */
  private class CachedSessionsRepository(initial: List<GroupSession>) : GroupSessionsRepository {
    val snapshots = MutableStateFlow<Result<List<GroupSession>>>(Result.success(initial))
    var observationCount = 0
      private set

    var writeCalls = 0
      private set

    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> {
      observationCount += 1
      return snapshots
    }

    override fun newSessionId(): String = "unused"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        Result.failure(UnsupportedOperationException("Not used in this test"))

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        offlineWrite()

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        offlineWrite()

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> =
        offlineWrite()

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> =
        offlineWrite()

    /** Counts the write and fails it, as a repository without a connection would. */
    private fun offlineWrite(): Result<Unit> {
      writeCalls += 1
      return Result.failure(EventsException(EventsError.STORAGE_FAILURE))
    }
  }

  /** Repository double whose emissions the test controls one by one. */
  private class FlowGroupSessionsRepository(
      private val sessionsFlow: Flow<Result<List<GroupSession>>>
  ) : GroupSessionsRepository {
    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
        sessionsFlow

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

  /** Campus rooms bundled with the app, so they are available offline. */
  private class FakeCampusLocationRepository(private val campusRooms: List<CampusLocation>) :
      CampusLocationRepository {
    override suspend fun getAllLocations(): Result<List<CampusLocation>> =
        Result.success(campusRooms)

    override suspend fun search(filter: CampusLocationFilter): Result<List<CampusLocation>> =
        Result.success(campusRooms)

    override suspend fun getZones(): Result<List<String>> =
        Result.success(campusRooms.map { it.zone }.distinct().sorted())
  }

  /** Profile lookups find nobody; host names are not what these tests are about. */
  private class FakeProfileRepository : ProfileRepository {
    override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(null)

    override suspend fun createProfile(userId: String): Result<UserProfile> =
        Result.success(UserProfile(uid = userId))
  }

  private companion object {
    const val ALICE = "alice"
    const val BOB = "bob"

    // Session IDs of the cached fixture, in start order.
    const val BIOLOGY = "biology"
    const val CHEMISTRY = "chemistry"
    const val ALGEBRA = "algebra"

    val USER = Location(latitude = 46.5186, longitude = 6.5619)

    /** About 1.5 km north of [USER]. */
    val FAR_FROM_USER = Location(latitude = 46.5320, longitude = 6.5619)
  }
}
