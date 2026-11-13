// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.ui.groupsession

import android.Manifest
import android.app.Application
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationFilter
import com.android.sample.model.location.CampusLocationRepository
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import com.android.sample.model.location.LocationProvider
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Covers the group-session filter previews, screen wiring, and state restoration. */
@RunWith(RobolectricTestRunner::class)
class GroupSessionScreenFiltersTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** Renders the light filter preview and checks its title. */
  @Test
  fun lightPreviewDisplaysFilterTitle() {
    composeTestRule.setContent { GroupSessionFilterSheetLightPreview() }

    composeTestRule.onNodeWithText("Filter sessions").assertIsDisplayed()
  }

  /** Renders the dark filter preview and checks its title. */
  @Test
  fun darkPreviewDisplaysFilterTitle() {
    composeTestRule.setContent { GroupSessionFilterSheetDarkPreview() }

    composeTestRule.onNodeWithText("Filter sessions").assertIsDisplayed()
  }

  /** Verifies granted-permission selections, reset state, and sheet dismissal. */
  @Test
  fun grantedPermissionFiltersCanBeSelectedResetAndDismissed() {
    grantFineLocationPermission()
    val viewModel = newViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).performClick()
    composeTestRule.waitForIdle()

    assertEquals(500, viewModel.uiState.value.selectedRadiusMeters)
    assertEquals(setOf("CM"), viewModel.uiState.value.selectedZones)
    assertEquals(true, viewModel.uiState.value.showLabRoomType)

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500))
        .assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).assertIsOn()

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.RESET_ALL).performClick()
    composeTestRule.waitForIdle()

    assertEquals(null, viewModel.uiState.value.selectedRadiusMeters)
    assertEquals(emptySet<String>(), viewModel.uiState.value.selectedZones)
    assertEquals(false, viewModel.uiState.value.showLabRoomType)

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(null))
        .assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip(null)).assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).assertIsOff()

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.SHOW_SESSIONS).performClick()
    composeTestRule.onNodeWithText("Filter sessions").assertDoesNotExist()
  }

  /** Loads a non-fixed campus zone from the ViewModel and applies its selection. */
  @Test
  fun zoneListComesFromViewModel() {
    val viewModel = newViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.VIEW_MORE).performClick()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("AI"))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("AI")).performClick()
    composeTestRule.waitForIdle()

    assertEquals(setOf("AI"), viewModel.uiState.value.selectedZones)
  }

  /** Retains a ViewModel-selected radius when the filter sheet is closed and reopened. */
  @Test
  fun selectedViewModelRadiusRemainsVisibleAfterReopeningSheet() {
    grantFineLocationPermission()
    val viewModel = newViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()
    composeTestRule.waitForIdle()
    assertEquals(500, viewModel.uiState.value.selectedRadiusMeters)

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.SHOW_SESSIONS).performClick()
    composeTestRule.onNodeWithText("Filter sessions").assertDoesNotExist()
    openFilterSheet()

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500))
        .assertIsSelected()
    assertEquals(500, viewModel.uiState.value.selectedRadiusMeters)
  }

  /** Applies a selected zone to the ViewModel rather than keeping it only in the sheet. */
  @Test
  fun selectingZoneUpdatesViewModel() {
    val viewModel = newViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).performClick()
    composeTestRule.waitForIdle()

    assertEquals(setOf("CM"), viewModel.uiState.value.selectedZones)
  }

  /** Applies a lab-switch change to the ViewModel rather than keeping it only in the sheet. */
  @Test
  fun enablingLabRoomsUpdatesViewModel() {
    val viewModel = newViewModel()
    viewModel.onShowLabRoomTypeChange(false)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).performClick()
    composeTestRule.waitForIdle()

    assertEquals(true, viewModel.uiState.value.showLabRoomType)
  }

  /** Resets radius, zone, and lab selections through the ViewModel. */
  @Test
  fun resetAllResetsViewModelFilters() {
    grantFineLocationPermission()
    val viewModel = newViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.RESET_ALL).performClick()
    composeTestRule.waitForIdle()

    assertEquals(null, viewModel.uiState.value.selectedRadiusMeters)
    assertEquals(emptySet<String>(), viewModel.uiState.value.selectedZones)
    assertEquals(false, viewModel.uiState.value.showLabRoomType)
  }

  /** Applies a granted fine-location result to the pending distance selection. */
  @Test
  fun fineLocationPermissionResultSelectsPendingRadius() {
    val registry =
        ImmediatePermissionResultRegistry(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
    val viewModel = setScreenWithRegistry(registry)

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()

    assertEquals(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        registry.launchedPermissions,
    )
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500))
        .assertIsSelected()
    assertEquals(500, viewModel.uiState.value.selectedRadiusMeters)
  }

  /** Hides the permission rationale after a granted result selects the pending radius. */
  @Test
  fun permissionMessageDisappearsOnceGranted() {
    val registry = DeferredPermissionResultRegistry()
    val viewModel = setScreenWithRegistry(registry)

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.PERMISSION_RATIONALE)
        .assertIsDisplayed()

    grantFineLocationPermission()
    registry.dispatchGrantedResult()
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.PERMISSION_RATIONALE)
        .assertDoesNotExist()
    assertEquals(500, viewModel.uiState.value.selectedRadiusMeters)
  }

  /** Leaves Any selected when the location permission request is denied. */
  @Test
  fun deniedLocationPermissionKeepsAnyRadiusSelected() {
    val registry =
        ImmediatePermissionResultRegistry(
            mapOf(
                Manifest.permission.ACCESS_FINE_LOCATION to false,
                Manifest.permission.ACCESS_COARSE_LOCATION to false,
            )
        )
    val viewModel = setScreenWithRegistry(registry)

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(250)).performClick()

    assertEquals(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
        registry.launchedPermissions,
    )
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(null))
        .assertIsSelected()
    assertEquals(null, viewModel.uiState.value.selectedRadiusMeters)
  }

  /** Preserves distance, zone, and Lab selections through saved-state restoration. */
  @Test
  fun filterSelectionsSurviveScreenRestoration() {
    grantFineLocationPermission()
    val restorationTester = StateRestorationTester(composeTestRule)
    val viewModel = newViewModel()
    restorationTester.setContent { GroupSessionScreen(viewModel = viewModel) }

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).performClick()

    restorationTester.emulateSavedInstanceStateRestore()
    composeTestRule.waitForIdle()
    if (composeTestRule.onAllNodesWithText("Filter sessions").fetchSemanticsNodes().isEmpty()) {
      openFilterSheet()
    }

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500))
        .assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).assertIsOn()
  }

  /** Provides a GroupSessionScreen with an immediate fake activity-result registry. */
  private fun setScreenWithRegistry(registry: ActivityResultRegistry): GroupSessionsViewModel {
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val viewModel = newViewModel()
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }
    return viewModel
  }

  /** Opens the sheet through the search bar's filter button. */
  private fun openFilterSheet() {
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.FILTER_BUTTON).performClick()
    composeTestRule.onNodeWithText("Filter sessions").assertIsDisplayed()
  }

  /** Grants fine location permission to the Robolectric application. */
  private fun grantFineLocationPermission() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
  }

  /** Creates a screen ViewModel backed only by local fakes. */
  private fun newViewModel() =
      GroupSessionsViewModel(
          FakeAuthRepository(),
          EmptySessionsRepository(),
          profileRepository =
              object : ProfileRepository {
                override suspend fun getProfile(userId: String) = Result.success<UserProfile?>(null)

                override suspend fun createProfile(userId: String) =
                    Result.failure<UserProfile>(UnsupportedOperationException())
              },
          locationProvider = LocationProvider { flowOf(null) },
          campusLocationRepository =
              FakeCampusLocationRepository(
                  listOf(
                      campusLocation("CM 1", "CM"),
                      campusLocation("BC 2", "BC"),
                      campusLocation("CO 4", "CO"),
                      campusLocation("AI 1", "AI"),
                  ),
              ),
      )

  private fun campusLocation(name: String, zone: String) =
      CampusLocation(
          name = name,
          zone = zone,
          type = CampusLocationType.LECTURE_ROOM,
          floor = null,
          location = Location(0.0, 0.0),
      )

  private class FakeAuthRepository : AuthRepository {
    override val currentUser =
        MutableStateFlow<UserAccount?>(UserAccount("filter-user", null, "Filter User", null))
    override val isAuthenticated: Boolean
      get() = true

    override val currentUserId: String?
      get() = "filter-user"

    override suspend fun signInWithGoogle(idToken: String) =
        UserAccount("filter-user", null, "Filter User", null)

    override suspend fun signOut() = Unit
  }

  private class EmptySessionsRepository : GroupSessionsRepository {
    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
        flowOf(Result.success(emptyList()))

    override fun newSessionId(): String = "filter-test-session"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        error("Not used by this test")

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        error("Not used by this test")

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        error("Not used by this test")

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> =
        error("Not used by this test")

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> =
        error("Not used by this test")
  }

  private class FakeCampusLocationRepository(
      private val locations: List<CampusLocation>,
  ) : CampusLocationRepository {
    override suspend fun getAllLocations(): Result<List<CampusLocation>> = Result.success(locations)

    override suspend fun search(filter: CampusLocationFilter): Result<List<CampusLocation>> =
        Result.success(
            locations.filter { location ->
              (filter.query.isNullOrBlank() ||
                  location.name.contains(filter.query.trim(), ignoreCase = true)) &&
                  (filter.zone.isNullOrBlank() ||
                      location.zone.equals(filter.zone.trim(), ignoreCase = true)) &&
                  (filter.types.isEmpty() || location.type in filter.types)
            },
        )

    override suspend fun getZones(): Result<List<String>> =
        Result.success(locations.map { it.zone }.distinct().sorted())
  }

  private class ImmediatePermissionResultRegistry(
      private val permissionResult: Map<String, Boolean>
  ) : ActivityResultRegistry() {
    var launchedPermissions: List<String>? = null
      private set

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      launchedPermissions = (input as Array<*>).map { it as String }
      dispatchResult(requestCode, permissionResult)
    }
  }

  private class DeferredPermissionResultRegistry : ActivityResultRegistry() {
    private var requestCode: Int? = null

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      this.requestCode = requestCode
    }

    fun dispatchGrantedResult() {
      val permissionRequestCode = checkNotNull(requestCode)
      dispatchResult(
          permissionRequestCode,
          mapOf(
              Manifest.permission.ACCESS_FINE_LOCATION to true,
              Manifest.permission.ACCESS_COARSE_LOCATION to false,
          ),
      )
    }
  }
}
