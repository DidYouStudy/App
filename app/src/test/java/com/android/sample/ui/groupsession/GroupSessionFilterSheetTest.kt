// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.groupsession

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.android.sample.model.event.EventType
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
      filtersState: MutableState<GroupSessionFilters>? = null,
      matchingSessionCount: Int = 4,
      zones: List<String> = availableZones,
      hasLocationPermission: Boolean = true,
      onRadiusSelected: (Int?) -> Unit = {},
      onZonesSelected: (Set<String>) -> Unit = {},
      onShowLabsChanged: (Boolean) -> Unit = {},
      onSessionTypesSelected: (Set<EventType>) -> Unit = {},
      onStartingTimeSelected: (GroupSessionTimeFilter) -> Unit = {},
      onHideFullSessionsChange: (Boolean) -> Unit = {},
      onLocationPermissionNeeded: (Int) -> Unit = {},
      onResetAll: () -> Unit = {},
      onShowSessions: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      GroupSessionFilterSheetContent(
          filters = filtersState?.value ?: filters,
          availableZones = zones,
          matchingSessionCount = matchingSessionCount,
          hasLocationPermission = hasLocationPermission,
          onRadiusSelected = onRadiusSelected,
          onZonesSelected = onZonesSelected,
          onShowLabsChanged = onShowLabsChanged,
          onSessionTypesSelected = onSessionTypesSelected,
          onStartingTimeSelected = onStartingTimeSelected,
          onHideFullSessionsChange = onHideFullSessionsChange,
          onLocationPermissionNeeded = onLocationPermissionNeeded,
          onResetAll = onResetAll,
          onShowSessions = onShowSessions,
      )
    }
  }

  /** Returns a type chip's semantics node. */
  private fun typeChip(type: EventType) =
      composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.sessionTypeChip(type))

  /** Returns a starting-time chip's semantics node. */
  private fun startingTimeChip(filter: GroupSessionTimeFilter) =
      composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.startingTimeChip(filter))

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

  /** Shows the added rows, checks their order and labels, and verifies choice semantics. */
  @Test
  fun showsSessionTypeStartingTimeAndAvailabilityRows() {
    setSheet()

    listOf("SESSION TYPE", "STARTING TIME", "AVAILABILITY", "Hide full sessions").forEach {
      composeTestRule.onNodeWithText(it).assertIsDisplayed()
    }
    listOf(
            GroupSessionFilterSheetTestTags.sessionTypeChip(EventType.STUDY_SESSION) to "Study",
            GroupSessionFilterSheetTestTags.sessionTypeChip(EventType.COURSE) to "Course",
            GroupSessionFilterSheetTestTags.sessionTypeChip(EventType.EXERCISE_SESSION) to
                "Exercise",
            GroupSessionFilterSheetTestTags.sessionTypeChip(EventType.LAB) to "Lab",
            GroupSessionFilterSheetTestTags.sessionTypeChip(EventType.PROJECT) to "Project",
            GroupSessionFilterSheetTestTags.sessionTypeChip(EventType.EXAM) to "Exam",
            GroupSessionFilterSheetTestTags.sessionTypeChip(EventType.OTHER) to "Other",
        )
        .forEach { (tag, label) ->
          composeTestRule
              .onNodeWithTag(tag)
              .performScrollTo()
              .assertIsDisplayed()
              .assertTextEquals(label)
        }
    listOf(
            GroupSessionFilterSheetTestTags.startingTimeChip(GroupSessionTimeFilter.ANYTIME) to
                "Anytime",
            GroupSessionFilterSheetTestTags.startingTimeChip(
                GroupSessionTimeFilter.WITHIN_ONE_HOUR
            ) to "In under 1h",
            GroupSessionFilterSheetTestTags.startingTimeChip(GroupSessionTimeFilter.TODAY) to
                "Today",
            GroupSessionFilterSheetTestTags.startingTimeChip(GroupSessionTimeFilter.THIS_WEEK) to
                "This week",
        )
        .forEach { (tag, label) ->
          composeTestRule
              .onNodeWithTag(tag)
              .performScrollTo()
              .assertIsDisplayed()
              .assertTextEquals(label)
        }
    composeTestRule.onNodeWithText("Only show sessions with open spots").assertIsDisplayed()

    val sessionTypeTop =
        composeTestRule.onNodeWithText("SESSION TYPE").getUnclippedBoundsInRoot().top
    val startingTimeTop =
        composeTestRule.onNodeWithText("STARTING TIME").getUnclippedBoundsInRoot().top
    val distanceTop =
        composeTestRule.onNodeWithText("DISTANCE FROM ME").getUnclippedBoundsInRoot().top
    val zoneTop = composeTestRule.onNodeWithText("ZONE").getUnclippedBoundsInRoot().top
    val viewMoreTop =
        composeTestRule
            .onNodeWithTag(GroupSessionFilterSheetTestTags.VIEW_MORE)
            .getUnclippedBoundsInRoot()
            .top
    val roomTypeTop = composeTestRule.onNodeWithText("ROOM TYPE").getUnclippedBoundsInRoot().top
    val availabilityTop =
        composeTestRule.onNodeWithText("AVAILABILITY").getUnclippedBoundsInRoot().top
    val showSessionsTop =
        composeTestRule
            .onNodeWithTag(GroupSessionFilterSheetTestTags.SHOW_SESSIONS)
            .getUnclippedBoundsInRoot()
            .top

    assertTrue(sessionTypeTop < startingTimeTop)
    assertTrue(startingTimeTop < distanceTop)
    assertTrue(distanceTop < zoneTop)
    assertTrue(zoneTop < viewMoreTop)
    assertTrue(viewMoreTop < roomTypeTop)
    assertTrue(roomTypeTop < availabilityTop)
    assertTrue(availabilityTop < showSessionsTop)

    typeChip(EventType.STUDY_SESSION)
        .performScrollTo()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    composeTestRule
        .onNodeWithTag(
            GroupSessionFilterSheetTestTags.startingTimeChip(GroupSessionTimeFilter.ANYTIME)
        )
        .performScrollTo()
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
  }

  /** Adds and removes multiple types, reporting the complete selected set after every tap. */
  @Test
  fun sessionTypeChipsToggleMultipleSelectionsAndReportFullSets() {
    val reported = mutableListOf<Set<EventType>>()
    val filters = mutableStateOf(GroupSessionFilters())
    setSheet(
        filtersState = filters,
        onSessionTypesSelected = {
          reported.add(it)
          filters.value = filters.value.copy(sessionTypes = it)
        },
    )

    typeChip(EventType.STUDY_SESSION).performScrollTo().performClick()
    typeChip(EventType.STUDY_SESSION).assertIsSelected()
    typeChip(EventType.EXAM).performScrollTo().performClick()
    typeChip(EventType.STUDY_SESSION).assertIsSelected()
    typeChip(EventType.EXAM).assertIsSelected()
    typeChip(EventType.STUDY_SESSION).performScrollTo().performClick()
    typeChip(EventType.STUDY_SESSION).assertIsNotSelected()
    typeChip(EventType.EXAM).assertIsSelected()

    composeTestRule.runOnIdle {
      assertEquals(
          listOf(
              setOf(EventType.STUDY_SESSION),
              setOf(EventType.STUDY_SESSION, EventType.EXAM),
              setOf(EventType.EXAM),
          ),
          reported,
      )
    }
  }

  /** Renders the supplied multi-select types and leaves the other types unselected. */
  @Test
  fun sessionTypeSelectionReflectsFilters() {
    setSheet(filters = GroupSessionFilters(sessionTypes = setOf(EventType.COURSE, EventType.LAB)))

    typeChip(EventType.COURSE).performScrollTo().assertIsSelected()
    typeChip(EventType.LAB).performScrollTo().assertIsSelected()
    listOf(
            EventType.STUDY_SESSION,
            EventType.EXERCISE_SESSION,
            EventType.PROJECT,
            EventType.EXAM,
            EventType.OTHER,
        )
        .forEach { type -> typeChip(type).performScrollTo().assertIsNotSelected() }
  }

  /** Leaves all session types unselected when the filter set is empty. */
  @Test
  fun sessionTypeSelectionDefaultsToNone() {
    setSheet(filters = GroupSessionFilters())
    listOf(
            EventType.STUDY_SESSION,
            EventType.COURSE,
            EventType.EXERCISE_SESSION,
            EventType.LAB,
            EventType.PROJECT,
            EventType.EXAM,
            EventType.OTHER,
        )
        .forEach { type -> typeChip(type).performScrollTo().assertIsNotSelected() }
  }

  /** Scrolls to the final type chip, then back to Study and selects it. */
  @Test
  fun sessionTypeRowScrollsHorizontallyAndStudyRemainsSelectable() {
    val reported = mutableListOf<Set<EventType>>()
    val filters = mutableStateOf(GroupSessionFilters())
    setSheet(
        filtersState = filters,
        onSessionTypesSelected = {
          reported.add(it)
          filters.value = filters.value.copy(sessionTypes = it)
        },
    )

    typeChip(EventType.OTHER).assertIsNotDisplayed().performScrollTo().assertIsDisplayed()
    typeChip(EventType.STUDY_SESSION).performScrollTo().assertIsDisplayed().performClick()
    composeTestRule.runOnIdle { assertEquals(listOf(setOf(EventType.STUDY_SESSION)), reported) }
  }

  /** Tapping each time option reports its value and updates the exclusive selection. */
  @Test
  fun startingTimeChipsReportExclusiveSelections() {
    val reported = mutableListOf<GroupSessionTimeFilter>()
    val filters = mutableStateOf(GroupSessionFilters())
    setSheet(
        filtersState = filters,
        onStartingTimeSelected = {
          reported.add(it)
          filters.value = filters.value.copy(startingTime = it)
        },
    )

    startingTimeChip(GroupSessionTimeFilter.ANYTIME).performScrollTo().assertIsSelected()
    startingTimeChip(GroupSessionTimeFilter.TODAY).performScrollTo().performClick()
    startingTimeChip(GroupSessionTimeFilter.TODAY).assertIsSelected()
    startingTimeChip(GroupSessionTimeFilter.ANYTIME).assertIsNotSelected()
    listOf(
            GroupSessionTimeFilter.THIS_WEEK,
            GroupSessionTimeFilter.WITHIN_ONE_HOUR,
            GroupSessionTimeFilter.ANYTIME,
        )
        .forEach { filter ->
          startingTimeChip(filter).performScrollTo().performClick()
          startingTimeChip(filter).assertIsSelected()
        }
    composeTestRule.runOnIdle {
      assertEquals(
          listOf(
              GroupSessionTimeFilter.TODAY,
              GroupSessionTimeFilter.THIS_WEEK,
              GroupSessionTimeFilter.WITHIN_ONE_HOUR,
              GroupSessionTimeFilter.ANYTIME,
          ),
          reported,
      )
    }
  }

  /** Selects only the stored starting-time option. */
  @Test
  fun storedStartingTimeIsTheOnlySelectedChip() {
    setSheet(filters = GroupSessionFilters(startingTime = GroupSessionTimeFilter.THIS_WEEK))

    startingTimeChip(GroupSessionTimeFilter.THIS_WEEK).performScrollTo().assertIsSelected()
    listOf(
            GroupSessionTimeFilter.ANYTIME,
            GroupSessionTimeFilter.WITHIN_ONE_HOUR,
            GroupSessionTimeFilter.TODAY,
        )
        .forEach { filter -> startingTimeChip(filter).performScrollTo().assertIsNotSelected() }
  }

  /** Availability reflects the supplied value and reports both switch changes. */
  @Test
  fun hideFullSessionsSwitchStartsOffAndReportsChanges() {
    val reported = mutableListOf<Boolean>()
    val filters = mutableStateOf(GroupSessionFilters())
    setSheet(
        filtersState = filters,
        onHideFullSessionsChange = {
          reported.add(it)
          filters.value = filters.value.copy(hideFullSessions = it)
        },
    )

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.HIDE_FULL_SWITCH).assertIsOff()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.HIDE_FULL_SWITCH)
        .performClick()
        .assertIsOn()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.HIDE_FULL_SWITCH)
        .performClick()
        .assertIsOff()

    composeTestRule.runOnIdle { assertEquals(listOf(true, false), reported) }
  }

  /** Exposes the availability switch as a labeled switch in the semantics tree. */
  @Test
  fun availabilitySwitchIsAnnouncedWithItsLabel() {
    setSheet()

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.HIDE_FULL_SWITCH)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        .assert(hasText("Hide full sessions", substring = true))
        .assertIsOff()
  }

  /** Exposes the Lab switch as a labeled switch in the semantics tree. */
  @Test
  fun labSwitchIsAnnouncedWithItsLabel() {
    setSheet()

    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.LAB_SWITCH)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        .assert(hasText("Show “Lab” room type", substring = true))
        .assertIsOff()
  }

  /** Toggles availability by clicking its label and reports the enabled state. */
  @Test
  fun tappingTheAvailabilityLabelTogglesIt() {
    val reported = mutableListOf<Boolean>()
    val filters = mutableStateOf(GroupSessionFilters())
    setSheet(
        filtersState = filters,
        onHideFullSessionsChange = {
          reported.add(it)
          filters.value = filters.value.copy(hideFullSessions = it)
        },
    )

    composeTestRule.onNodeWithText("Hide full sessions").performScrollTo().performClick()
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.HIDE_FULL_SWITCH).assertIsOn()
    composeTestRule.runOnIdle { assertEquals(listOf(true), reported) }
  }

  /** Shows the stored availability value as enabled. */
  @Test
  fun storedHideFullSessionsShowsSwitchOn() {
    setSheet(filters = GroupSessionFilters(hideFullSessions = true))
    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.HIDE_FULL_SWITCH).assertIsOn()
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
          onSessionTypesSelected = {},
          onStartingTimeSelected = {},
          onHideFullSessionsChange = {},
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
          onSessionTypesSelected = {},
          onStartingTimeSelected = {},
          onHideFullSessionsChange = {},
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

  /** Matches the expanded zone list width to its View more button. */
  @Test
  fun zoneMenuIsAsWideAsViewMoreButton() {
    val zones = (1..20).map { "Z%02d".format(it) }
    setSheet(zones = zones)

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.VIEW_MORE).performClick()

    val buttonBounds =
        composeTestRule
            .onNodeWithTag(GroupSessionFilterSheetTestTags.VIEW_MORE)
            .getUnclippedBoundsInRoot()
    val optionBounds =
        composeTestRule
            .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("Z01"))
            .getUnclippedBoundsInRoot()
    val buttonWidth = buttonBounds.right - buttonBounds.left
    val optionWidth = optionBounds.right - optionBounds.left

    assertEquals(buttonWidth.value, optionWidth.value, 1f)
  }

  /** Keeps the expanded zone list scrollable instead of showing every option at once. */
  @Test
  fun zoneMenuScrollsInsteadOfFillingTheScreen() {
    val zones = (1..20).map { "Z%02d".format(it) }
    setSheet(zones = zones)

    composeTestRule.onNodeWithTag(GroupSessionFilterSheetTestTags.VIEW_MORE).performClick()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("Z01"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("Z08"))
        .assertIsNotDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionFilterSheetTestTags.zoneOption("Z20"))
        .assertIsNotDisplayed()
        .performScrollTo()
        .assertIsDisplayed()
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
          onSessionTypesSelected = {},
          onStartingTimeSelected = {},
          onHideFullSessionsChange = {},
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
          onSessionTypesSelected = {},
          onStartingTimeSelected = {},
          onHideFullSessionsChange = {},
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
