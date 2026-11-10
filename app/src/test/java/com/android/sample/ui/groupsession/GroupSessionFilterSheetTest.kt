// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.groupsession

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GroupSessionFilterSheetTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val availableZones = listOf("AI", "BC", "CM", "CO", "GC")

  /** Sets the composable content with the supplied filters and event callbacks. */
  private fun setSheet(
      filters: GroupSessionFilters = GroupSessionFilters(),
      matchingSessionCount: Int = 4,
      hasLocationPermission: Boolean = true,
      onRadiusSelected: (Int?) -> Unit = {},
      onZonesSelected: (Set<String>) -> Unit = {},
      onShowLabsChanged: (Boolean) -> Unit = {},
      onLocationPermissionNeeded: (Int) -> Unit = {},
      onResetAll: () -> Unit = {},
      onShowSessions: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      GroupSessionFilterSheetContent(
          filters = filters,
          availableZones = availableZones,
          matchingSessionCount = matchingSessionCount,
          hasLocationPermission = hasLocationPermission,
          onRadiusSelected = onRadiusSelected,
          onZonesSelected = onZonesSelected,
          onShowLabsChanged = onShowLabsChanged,
          onLocationPermissionNeeded = onLocationPermissionNeeded,
          onResetAll = onResetAll,
          onShowSessions = onShowSessions,
      )
    }
  }

  /** Shows every requested section, choice, and action on the sheet. */
  @Test
  fun showsFilterSectionsAndChoices() {
    setSheet()

    listOf(
            "Filter sessions",
            "Reset all",
            "DISTANCE FROM ME",
            "< 250m",
            "< 500m",
            "< 1km",
            "ZONE",
            "CO",
            "CM",
            "BC",
            "ROOM TYPE",
            "Show “Lab” room type",
            "Show 4 sessions",
        )
        .forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed() }
    composeTestRule.onAllNodesWithText("Any").assertCountEquals(2)
    composeTestRule.onNodeWithText("View more", substring = true).assertIsDisplayed()
  }

  /** Selects a stored radius and reports each of the available distance values. */
  @Test
  fun selectedDistanceChipReportsRadiusAndAnyReportsNull() {
    val selected = mutableListOf<Int?>()
    setSheet(filters = GroupSessionFilters(radiusMeters = 500), onRadiusSelected = selected::add)

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500))
        .assertIsSelected()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(250))
        .assertIsNotSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(250)).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(1000)).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(null)).performClick()

    composeTestRule.runOnIdle { assertEquals(listOf(500, 250, 1000, null), selected) }
  }

  /** Requests permission for the 250-meter radius without selecting it. */
  @Test
  fun distance250WithoutPermissionRequestsLocationAccess() {
    val selected = mutableListOf<Int?>()
    val permissionNeeded = mutableListOf<Int>()
    setSheet(
        hasLocationPermission = false,
        onRadiusSelected = selected::add,
        onLocationPermissionNeeded = permissionNeeded::add,
    )

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(250)).performClick()

    composeTestRule
        .onNodeWithText("Allow location access to filter by distance")
        .assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(listOf(250), permissionNeeded)
      assertEquals(emptyList<Int?>(), selected)
    }
  }

  /** Requests permission for the 500-meter radius without selecting it. */
  @Test
  fun distance500WithoutPermissionRequestsLocationAccess() {
    val selected = mutableListOf<Int?>()
    val permissionNeeded = mutableListOf<Int>()
    setSheet(
        hasLocationPermission = false,
        onRadiusSelected = selected::add,
        onLocationPermissionNeeded = permissionNeeded::add,
    )

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(500)).performClick()

    composeTestRule
        .onNodeWithText("Allow location access to filter by distance")
        .assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(listOf(500), permissionNeeded)
      assertEquals(emptyList<Int?>(), selected)
    }
  }

  /** Requests permission for the one-kilometer radius without selecting it. */
  @Test
  fun distance1000WithoutPermissionRequestsLocationAccess() {
    val selected = mutableListOf<Int?>()
    val permissionNeeded = mutableListOf<Int>()
    setSheet(
        hasLocationPermission = false,
        onRadiusSelected = selected::add,
        onLocationPermissionNeeded = permissionNeeded::add,
    )

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(1000)).performClick()

    composeTestRule
        .onNodeWithText("Allow location access to filter by distance")
        .assertIsDisplayed()
    composeTestRule.runOnIdle {
      assertEquals(listOf(1000), permissionNeeded)
      assertEquals(emptyList<Int?>(), selected)
    }
  }

  /** Selecting Any clears the radius even when location access is unavailable. */
  @Test
  fun anyDistanceReportsNullWithoutPermission() {
    val selected = mutableListOf<Int?>()
    val permissionNeeded = mutableListOf<Int>()
    setSheet(
        hasLocationPermission = false,
        onRadiusSelected = selected::add,
        onLocationPermissionNeeded = permissionNeeded::add,
    )

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.radiusChip(null)).performClick()

    composeTestRule.runOnIdle {
      assertEquals(listOf<Int?>(null), selected)
      assertEquals(emptyList<Int>(), permissionNeeded)
    }
  }

  /** Zone chips reflect the selected set and report additions, removals, and clearing. */
  @Test
  fun zoneChipsToggleAndAnyClearsSelection() {
    val selected = mutableListOf<Set<String>>()
    var filters by mutableStateOf(GroupSessionFilters(zones = setOf("CO")))
    composeTestRule.setContent {
      GroupSessionFilterSheetContent(
          filters = filters,
          availableZones = availableZones,
          matchingSessionCount = 4,
          hasLocationPermission = true,
          onRadiusSelected = {},
          onZonesSelected = {
            selected.add(it)
            filters = filters.copy(zones = it)
          },
          onShowLabsChanged = {},
          onLocationPermissionNeeded = {},
          onResetAll = {},
          onShowSessions = {},
      )
    }

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CO")).assertIsSelected()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip(null))
        .assertIsNotSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CM")).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("CO")).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip(null)).performClick()

    composeTestRule.runOnIdle {
      assertEquals(listOf(setOf("CO", "CM"), setOf("CM"), emptySet<String>()), selected)
    }
  }

  /** Any is selected when no zones are active and reports an empty set when tapped. */
  @Test
  fun anyZoneStartsSelectedAndReportsEmptySet() {
    val selected = mutableListOf<Set<String>>()
    setSheet(onZonesSelected = selected::add)

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip(null)).assertIsSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip(null)).performClick()

    composeTestRule.runOnIdle { assertEquals(listOf(emptySet<String>()), selected) }
  }

  /** View more lists all available zones and reports selection changes from the list. */
  @Test
  fun viewMoreListsZonesAndTogglesAnOption() {
    val selected = mutableListOf<Set<String>>()
    var filters by mutableStateOf(GroupSessionFilters(zones = setOf("CM")))
    composeTestRule.setContent {
      GroupSessionFilterSheetContent(
          filters = filters,
          availableZones = availableZones,
          matchingSessionCount = 4,
          hasLocationPermission = true,
          onRadiusSelected = {},
          onZonesSelected = {
            selected.add(it)
            filters = filters.copy(zones = it)
          },
          onShowLabsChanged = {},
          onLocationPermissionNeeded = {},
          onResetAll = {},
          onShowSessions = {},
      )
    }

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.VIEW_MORE).performClick()
    availableZones.forEach {
      composeTestRule
          .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption(it))
          .assertIsDisplayed()
    }
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("CM"))
        .assertIsSelected()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("AI"))
        .assertIsNotSelected()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("AI")).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("CM")).performClick()

    composeTestRule.runOnIdle { assertEquals(listOf(setOf("CM", "AI"), setOf("AI")), selected) }
  }

  /** A zone selected by chip stays selected after it is shown in the expanded list. */
  @Test
  fun chipSelectionIsSelectedInViewMoreList() {
    var filters by mutableStateOf(GroupSessionFilters())
    composeTestRule.setContent {
      GroupSessionFilterSheetContent(
          filters = filters,
          availableZones = availableZones,
          matchingSessionCount = 4,
          hasLocationPermission = true,
          onRadiusSelected = {},
          onZonesSelected = { filters = filters.copy(zones = it) },
          onShowLabsChanged = {},
          onLocationPermissionNeeded = {},
          onResetAll = {},
          onShowSessions = {},
      )
    }

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.zoneChip("BC")).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.VIEW_MORE).performClick()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("BC"))
        .assertIsSelected()
  }

  /** The Lab control starts off and reports the enabled state after toggling. */
  @Test
  fun labSwitchStartsOffAndReportsChanges() {
    val values = mutableListOf<Boolean>()
    var filters by mutableStateOf(GroupSessionFilters())
    composeTestRule.setContent {
      GroupSessionFilterSheetContent(
          filters = filters,
          availableZones = availableZones,
          matchingSessionCount = 4,
          hasLocationPermission = true,
          onRadiusSelected = {},
          onZonesSelected = {},
          onShowLabsChanged = {
            values.add(it)
            filters = filters.copy(showLabs = it)
          },
          onLocationPermissionNeeded = {},
          onResetAll = {},
          onShowSessions = {},
      )
    }

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).assertIsOff()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH).assertIsOn()

    composeTestRule.runOnIdle { assertEquals(listOf(true), values) }
  }

  /** Reset invokes its callback, and the singular/plural action reports session display. */
  @Test
  fun resetAndShowSessionsActionsReportCallbacksAndCounts() {
    val resetCalls = mutableListOf<Unit>()
    val showCalls = mutableListOf<Unit>()
    setSheet(
        matchingSessionCount = 4,
        onResetAll = { resetCalls.add(Unit) },
        onShowSessions = { showCalls.add(Unit) },
    )

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.RESET_ALL).performClick()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.SHOW_SESSIONS)
        .assertIsDisplayed()
        .performClick()
    composeTestRule.runOnIdle {
      assertEquals(listOf(Unit), resetCalls)
      assertEquals(listOf(Unit), showCalls)
    }
  }

  /** Uses singular wording when exactly one session matches. */
  @Test
  fun showsSingularSessionCount() {
    setSheet(matchingSessionCount = 1)

    composeTestRule.onNodeWithText("Show 1 session").assertIsDisplayed()
  }

  /** Returns true when either supported location permission is granted. */
  @Test
  fun locationPermissionIsGrantedForFineOrCoarse() {
    assertTrue(isLocationPermissionGranted(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true)))
    assertTrue(
        isLocationPermissionGranted(mapOf(Manifest.permission.ACCESS_COARSE_LOCATION to true))
    )
    assertTrue(
        isLocationPermissionGranted(
            mapOf(
                Manifest.permission.ACCESS_FINE_LOCATION to true,
                Manifest.permission.ACCESS_COARSE_LOCATION to true,
            )
        )
    )
  }

  /** Returns false when no location permission result is granted or present. */
  @Test
  fun locationPermissionIsNotGrantedWhenNeitherOrNoResults() {
    assertEquals(
        false,
        isLocationPermissionGranted(
            mapOf(
                Manifest.permission.ACCESS_FINE_LOCATION to false,
                Manifest.permission.ACCESS_COARSE_LOCATION to false,
            )
        ),
    )
    assertEquals(false, isLocationPermissionGranted(emptyMap()))
  }

  /** Requires both location permissions to appear in the merged app manifest. */
  @Test
  fun manifestRequestsFineAndCoarseLocationPermissions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packageInfo =
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val requestedPermissions = packageInfo.requestedPermissions?.toList().orEmpty()

    assertTrue(
        "android.permission.ACCESS_FINE_LOCATION",
        requestedPermissions.contains("android.permission.ACCESS_FINE_LOCATION"),
    )
    assertTrue(
        "android.permission.ACCESS_COARSE_LOCATION",
        requestedPermissions.contains("android.permission.ACCESS_COARSE_LOCATION"),
    )
  }
}
