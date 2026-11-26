// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.groupsession

import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.location.InMemoryPreciseLocationStateStore
import com.android.sample.model.location.Location
import com.android.sample.model.location.LocationProvider
import com.android.sample.model.location.PreciseLocationState
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

/** Plain JVM unit tests for precise-location permission policy in [GroupSessionsViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSessionsViewModelPermissionTest {
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private fun createViewModel(
      store: InMemoryPreciseLocationStateStore = InMemoryPreciseLocationStateStore(),
      onLocationCollected: () -> Unit = {},
  ): GroupSessionsViewModel =
      GroupSessionsViewModel(
          authRepository = TestAuthRepository(),
          repository = InMemoryGroupSessionsRepository(),
          profileRepository = StubProfileRepository(),
          clock = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC),
          locationProvider =
              LocationProvider {
                onLocationCollected()
                flowOf(Location(46.52, 6.56))
              },
          preciseLocationStateStore = store,
      )

  /** Initial UI state reflects the persisted store, and store reset returns to INIT. */
  @Test
  fun initialStateReadsPersistedStoreAndResetClearsIt() {
    val store = InMemoryPreciseLocationStateStore(PreciseLocationState.RATIONALE_PENDING)
    val viewModel = createViewModel(store = store)

    assertEquals(
        PreciseLocationState.RATIONALE_PENDING,
        viewModel.uiState.value.preciseLocationState,
    )
    assertFalse(viewModel.uiState.value.hasPreciseLocationPermission)
    assertEquals(ActivePermissionDialog.NONE, viewModel.uiState.value.permissionDialog)
    assertFalse(viewModel.uiState.value.pendingPermissionPrompt)

    store.reset()
    assertEquals(PreciseLocationState.INIT, store.getState())
  }

  /** Syncing with granted OS permission transitions to GRANTED and dismisses dialogs. */
  @Test
  fun syncPreciseLocationPermissionGrantsAndDismissesActiveDialog() {
    val store = InMemoryPreciseLocationStateStore(PreciseLocationState.RATIONALE_PENDING)
    val viewModel = createViewModel(store = store)

    viewModel.onMapEntered(hasFineLocation = false)
    assertEquals(ActivePermissionDialog.EXPLANATION, viewModel.uiState.value.permissionDialog)

    viewModel.syncPreciseLocationPermission(hasFineLocation = true)
    assertEquals(PreciseLocationState.GRANTED, viewModel.uiState.value.preciseLocationState)
    assertTrue(viewModel.uiState.value.hasPreciseLocationPermission)
    assertEquals(ActivePermissionDialog.NONE, viewModel.uiState.value.permissionDialog)
    assertEquals(PreciseLocationState.GRANTED, store.getState())
  }

  /** Syncing with revoked OS permission resets GRANTED back to INIT. */
  @Test
  fun syncPreciseLocationPermissionResetsWhenRevoked() {
    val store = InMemoryPreciseLocationStateStore(PreciseLocationState.GRANTED)
    val viewModel = createViewModel(store = store)

    viewModel.syncPreciseLocationPermission(hasFineLocation = false)
    assertEquals(PreciseLocationState.INIT, viewModel.uiState.value.preciseLocationState)
    assertFalse(viewModel.uiState.value.hasPreciseLocationPermission)
    assertEquals(PreciseLocationState.INIT, store.getState())
  }

  /**
   * Passive map entry prompts in INIT and SECOND_PROMPT_PENDING, explains in RATIONALE_PENDING, and
   * is silent in DENIED_TWICE.
   */
  @Test
  fun onMapEnteredAppliesStateMachineForEachState() = runTest {
    val initVm = createViewModel(InMemoryPreciseLocationStateStore(PreciseLocationState.INIT))
    initVm.onMapEntered(hasFineLocation = false)
    assertTrue(initVm.uiState.value.pendingPermissionPrompt)
    initVm.onPermissionPromptLaunched()
    assertFalse(initVm.uiState.value.pendingPermissionPrompt)

    val rationaleVm =
        createViewModel(InMemoryPreciseLocationStateStore(PreciseLocationState.RATIONALE_PENDING))
    rationaleVm.onMapEntered(hasFineLocation = false)
    assertEquals(ActivePermissionDialog.EXPLANATION, rationaleVm.uiState.value.permissionDialog)
    assertFalse(rationaleVm.uiState.value.pendingPermissionPrompt)

    val secondVm =
        createViewModel(
            InMemoryPreciseLocationStateStore(PreciseLocationState.SECOND_PROMPT_PENDING)
        )
    secondVm.onMapEntered(hasFineLocation = false)
    assertTrue(secondVm.uiState.value.pendingPermissionPrompt)

    val deniedVm =
        createViewModel(InMemoryPreciseLocationStateStore(PreciseLocationState.DENIED_TWICE))
    deniedVm.onMapEntered(hasFineLocation = false)
    assertEquals(ActivePermissionDialog.NONE, deniedVm.uiState.value.permissionDialog)
    assertFalse(deniedVm.uiState.value.pendingPermissionPrompt)
  }

  /** Passive map entry with fine permission granted refreshes location without prompting. */
  @Test
  fun onMapEnteredWithPermissionRefreshesLocationSilently() = runTest {
    var locationCollections = 0
    val viewModel = createViewModel(onLocationCollected = { locationCollections++ })
    val collector = launch { viewModel.uiState.collect {} }
    runCurrent()
    val before = locationCollections

    viewModel.onMapEntered(hasFineLocation = true)
    runCurrent()

    assertTrue(viewModel.uiState.value.hasPreciseLocationPermission)
    assertFalse(viewModel.uiState.value.pendingPermissionPrompt)
    assertEquals(ActivePermissionDialog.NONE, viewModel.uiState.value.permissionDialog)
    assertTrue(locationCollections > before)
    collector.cancel()
  }

  /**
   * Two consecutive refusals progress through explanation to Settings dialog and persist
   * DENIED_TWICE.
   */
  @Test
  fun twoRefusalsProgressThroughExplanationToSettingsDialog() {
    val store = InMemoryPreciseLocationStateStore()
    val viewModel = createViewModel(store = store)

    viewModel.onMapEntered(hasFineLocation = false)
    assertTrue(viewModel.uiState.value.pendingPermissionPrompt)
    viewModel.onPermissionPromptLaunched()

    viewModel.onSystemPermissionResult(isFineGranted = false)
    assertEquals(
        PreciseLocationState.RATIONALE_PENDING,
        viewModel.uiState.value.preciseLocationState,
    )
    assertEquals(ActivePermissionDialog.EXPLANATION, viewModel.uiState.value.permissionDialog)
    assertEquals(PreciseLocationState.RATIONALE_PENDING, store.getState())

    viewModel.onExplanationDismissed()
    assertEquals(
        PreciseLocationState.SECOND_PROMPT_PENDING,
        viewModel.uiState.value.preciseLocationState,
    )
    assertEquals(ActivePermissionDialog.NONE, viewModel.uiState.value.permissionDialog)
    assertTrue(viewModel.uiState.value.pendingPermissionPrompt)
    viewModel.onPermissionPromptLaunched()

    viewModel.onSystemPermissionResult(isFineGranted = false)
    assertEquals(PreciseLocationState.DENIED_TWICE, viewModel.uiState.value.preciseLocationState)
    assertEquals(ActivePermissionDialog.SETTINGS, viewModel.uiState.value.permissionDialog)
    assertEquals(PreciseLocationState.DENIED_TWICE, store.getState())

    viewModel.onSettingsDialogDismissed()
    assertEquals(ActivePermissionDialog.NONE, viewModel.uiState.value.permissionDialog)
  }

  /**
   * Granting permission after requesting a distance filter applies the pending radius and refreshes
   * location.
   */
  @Test
  fun featureRequestWithPendingRadiusAppliesRadiusOnGrant() = runTest {
    var locationCollections = 0
    val viewModel = createViewModel(onLocationCollected = { locationCollections++ })
    val collector = launch { viewModel.uiState.collect {} }
    runCurrent()
    val before = locationCollections

    viewModel.onLocationFeatureRequested(hasFineLocation = false, pendingRadius = 500)
    assertTrue(viewModel.uiState.value.pendingPermissionPrompt)
    assertNull(viewModel.uiState.value.selectedRadiusMeters)
    viewModel.onPermissionPromptLaunched()

    viewModel.onSystemPermissionResult(isFineGranted = true)
    runCurrent()

    assertTrue(viewModel.uiState.value.hasPreciseLocationPermission)
    assertEquals(500, viewModel.uiState.value.selectedRadiusMeters)
    assertTrue(locationCollections > before)
    collector.cancel()
  }

  /** Pending radius survives the first refusal and is applied if the second prompt is granted. */
  @Test
  fun featureRequestWithPendingRadiusAppliesAfterSecondPromptGrant() = runTest {
    val viewModel = createViewModel()
    val collector = launch { viewModel.uiState.collect {} }
    runCurrent()

    viewModel.onLocationFeatureRequested(hasFineLocation = false, pendingRadius = 250)
    viewModel.onPermissionPromptLaunched()
    viewModel.onSystemPermissionResult(isFineGranted = false)
    assertEquals(ActivePermissionDialog.EXPLANATION, viewModel.uiState.value.permissionDialog)
    assertNull(viewModel.uiState.value.selectedRadiusMeters)

    viewModel.onExplanationDismissed()
    viewModel.onPermissionPromptLaunched()
    viewModel.onSystemPermissionResult(isFineGranted = true)
    runCurrent()

    assertEquals(250, viewModel.uiState.value.selectedRadiusMeters)
    collector.cancel()
  }

  /** Reaching DENIED_TWICE clears any pending radius and opens the Settings dialog. */
  @Test
  fun featureRequestClearsPendingRadiusWhenDeniedTwice() = runTest {
    val store = InMemoryPreciseLocationStateStore(PreciseLocationState.SECOND_PROMPT_PENDING)
    val viewModel = createViewModel(store = store)
    val collector = launch { viewModel.uiState.collect {} }
    runCurrent()

    viewModel.onLocationFeatureRequested(hasFineLocation = false, pendingRadius = 1000)
    viewModel.onPermissionPromptLaunched()
    viewModel.onSystemPermissionResult(isFineGranted = false)
    runCurrent()

    assertEquals(PreciseLocationState.DENIED_TWICE, viewModel.uiState.value.preciseLocationState)
    assertEquals(ActivePermissionDialog.SETTINGS, viewModel.uiState.value.permissionDialog)
    assertNull(viewModel.uiState.value.selectedRadiusMeters)

    // Subsequent feature request in DENIED_TWICE opens Settings directly without prompting.
    viewModel.onSettingsDialogDismissed()
    viewModel.onLocationFeatureRequested(hasFineLocation = false, pendingRadius = 500)
    assertEquals(ActivePermissionDialog.SETTINGS, viewModel.uiState.value.permissionDialog)
    assertFalse(viewModel.uiState.value.pendingPermissionPrompt)
    assertNull(viewModel.uiState.value.selectedRadiusMeters)
    collector.cancel()
  }

  /**
   * Feature request when OS permission is already granted syncs state and applies radius
   * immediately.
   */
  @Test
  fun featureRequestWhenAlreadyGrantedAppliesRadiusImmediately() = runTest {
    var locationCollections = 0
    val viewModel = createViewModel(onLocationCollected = { locationCollections++ })
    val collector = launch { viewModel.uiState.collect {} }
    runCurrent()
    val before = locationCollections

    viewModel.onLocationFeatureRequested(hasFineLocation = true, pendingRadius = 500)
    runCurrent()

    assertTrue(viewModel.uiState.value.hasPreciseLocationPermission)
    assertFalse(viewModel.uiState.value.pendingPermissionPrompt)
    assertEquals(500, viewModel.uiState.value.selectedRadiusMeters)
    assertTrue(locationCollections > before)
    collector.cancel()
  }

  /**
   * Returning from Settings without grant launches a single silent probe that does not re-open
   * Settings on denial.
   */
  @Test
  fun returningFromSettingsWithoutGrantProbesOnceAndStaysSilentOnDenial() {
    val store = InMemoryPreciseLocationStateStore(PreciseLocationState.DENIED_TWICE)
    val viewModel = createViewModel(store = store)

    viewModel.onLocationFeatureRequested(hasFineLocation = false)
    assertEquals(ActivePermissionDialog.SETTINGS, viewModel.uiState.value.permissionDialog)

    viewModel.onSettingsOpened()
    assertEquals(ActivePermissionDialog.NONE, viewModel.uiState.value.permissionDialog)

    viewModel.onResumed(hasFineLocation = false)
    assertTrue(viewModel.uiState.value.pendingPermissionPrompt)
    viewModel.onPermissionPromptLaunched()

    viewModel.onSystemPermissionResult(isFineGranted = false)
    assertEquals(PreciseLocationState.DENIED_TWICE, viewModel.uiState.value.preciseLocationState)
    assertEquals(ActivePermissionDialog.NONE, viewModel.uiState.value.permissionDialog)
  }

  /**
   * Returning from Settings with fine permission granted transitions to GRANTED and refreshes
   * location.
   */
  @Test
  fun returningFromSettingsWithGrantRefreshesLocation() = runTest {
    var locationCollections = 0
    val store = InMemoryPreciseLocationStateStore(PreciseLocationState.DENIED_TWICE)
    val viewModel = createViewModel(store = store, onLocationCollected = { locationCollections++ })
    val collector = launch { viewModel.uiState.collect {} }
    runCurrent()
    val before = locationCollections

    viewModel.onSettingsOpened()
    viewModel.onResumed(hasFineLocation = true)
    runCurrent()

    assertEquals(PreciseLocationState.GRANTED, viewModel.uiState.value.preciseLocationState)
    assertFalse(viewModel.uiState.value.pendingPermissionPrompt)
    assertEquals(ActivePermissionDialog.NONE, viewModel.uiState.value.permissionDialog)
    assertTrue(locationCollections > before)
    collector.cancel()
  }

  /** Normal resume syncs live permission changes (grant and revocation) and refreshes on grant. */
  @Test
  fun onResumedWithoutPendingSettingsSyncsGrantAndRevocation() = runTest {
    var locationCollections = 0
    val store = InMemoryPreciseLocationStateStore(PreciseLocationState.DENIED_TWICE)
    val viewModel = createViewModel(store = store, onLocationCollected = { locationCollections++ })
    val collector = launch { viewModel.uiState.collect {} }
    runCurrent()
    val before = locationCollections

    viewModel.onResumed(hasFineLocation = true)
    runCurrent()
    assertEquals(PreciseLocationState.GRANTED, viewModel.uiState.value.preciseLocationState)
    assertTrue(locationCollections > before)

    viewModel.onResumed(hasFineLocation = false)
    assertEquals(PreciseLocationState.INIT, viewModel.uiState.value.preciseLocationState)
    assertEquals(PreciseLocationState.INIT, store.getState())
    collector.cancel()
  }

  /** Factory wires the injected [PreciseLocationStateStore] into [GroupSessionsViewModel]. */
  @Test
  fun factoryInjectsPreciseLocationStateStore() {
    val store = InMemoryPreciseLocationStateStore(PreciseLocationState.DENIED_TWICE)
    val factory =
        GroupSessionsViewModelFactory(
            authRepository = TestAuthRepository(),
            repository = InMemoryGroupSessionsRepository(),
            profileRepository = StubProfileRepository(),
            locationProvider = LocationProvider { flowOf(null) },
            preciseLocationStateStore = store,
        )

    val created = factory.create(GroupSessionsViewModel::class.java)
    assertEquals(PreciseLocationState.DENIED_TWICE, created.uiState.value.preciseLocationState)
  }

  /** Minimal [ProfileRepository] stub for JVM permission tests. */
  private class StubProfileRepository : ProfileRepository {
    override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(null)

    override suspend fun createProfile(userId: String): Result<UserProfile> =
        Result.success(UserProfile(userId, ""))

    override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> =
        Result.success(UserProfile(userId, username))

    override suspend fun updateProfilePictureUrl(userId: String, url: String): Result<Unit> =
        Result.success(Unit)
  }
}
