// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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
    composeTestRule.setContent { GroupSessionScreen(viewModel = newViewModel()) }

    openFilterSheet()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).performClick()

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500))
        .assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).assertIsOn()

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.RESET_ALL).performClick()

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(null))
        .assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip(null)).assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).assertIsOff()

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.SHOW_SESSIONS).performClick()
    composeTestRule.onNodeWithText("Filter sessions").assertDoesNotExist()
  }

  /** Applies a granted fine-location result to the pending distance selection. */
  @Test
  fun fineLocationPermissionResultSelectsPendingRadius() {
    val registry =
        ImmediatePermissionResultRegistry(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
    setScreenWithRegistry(registry)

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
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(250))
        .assertIsSelected()
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
    setScreenWithRegistry(registry)

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
  }

  /** Preserves distance, zone, and Lab selections through saved-state restoration. */
  @Test
  fun filterSelectionsSurviveScreenRestoration() {
    grantFineLocationPermission()
    val restorationTester = StateRestorationTester(composeTestRule)
    restorationTester.setContent { GroupSessionScreen(viewModel = newViewModel()) }

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
  private fun setScreenWithRegistry(registry: ActivityResultRegistry) {
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        GroupSessionScreen(viewModel = newViewModel())
      }
    }
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
      GroupSessionsViewModel(FakeAuthRepository(), EmptySessionsRepository())

  private class FakeAuthRepository : AuthRepository {
    override val currentUser =
        MutableStateFlow<UserAccount?>(UserAccount("filter-user", null, "Filter User", null))

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
}
