// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.groupsession

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of [GroupSessionMapContent]'s selection and cards, with the real Google map replaced by its
 * stand-in. The stand-in has no pins, so a room is selected through `initialSelectedRoom`; tapping
 * the pins themselves is tested in the instrumented `GroupSessionMapContentRealMapTest`. It also
 * covers [dropletPath], the shape of those pins, which needs no map.
 */
@RunWith(AndroidJUnit4::class)
class GroupSessionMapContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val now = Instant.parse("2030-01-01T09:00:00Z")

  private fun room(name: String) =
      CampusLocation(
          name,
          name.substringBefore(' '),
          CampusLocationType.LECTURE_ROOM,
          0,
          Location(46.52, 6.56),
      )

  private fun item(
      id: String,
      subject: String,
      isCreator: Boolean = false,
      isActionInProgress: Boolean = false,
  ) =
      GroupSessionItem(
          session =
              GroupSession(
                  id = id,
                  createdBy = "creator",
                  memberIds = listOf("creator"),
                  details =
                      EventDetails(
                          type = EventType.STUDY_SESSION,
                          subject = subject,
                          startsAt = now.plusSeconds(3600),
                          endsAt = now.plusSeconds(7200),
                      ),
                  createdAt = now,
              ),
          isMember = false,
          isCreator = isCreator,
          isActionInProgress = isActionInProgress,
      )

  private val aab = room("AAB 0 26")
  private val cm = room("CM 1 121")
  private val bc = room("BC 410")
  private val maths = item("s1", "Maths")
  private val physics = item("s2", "Physics")
  private val chemistry = item("s3", "Chemistry")
  private val ownSession = item("s4", "Algebra", isCreator = true)
  private val rooms =
      listOf(
          RoomSessions(aab, listOf(maths, physics)),
          RoomSessions(cm, listOf(chemistry)),
          RoomSessions(bc, listOf(ownSession)),
      )

  private fun setContent(
      onJoin: (String) -> Unit = {},
      onEdit: (String) -> Unit = {},
      initialSelectedRoom: String? = null,
      userLocation: Location? = null,
      cameraDestination: CameraDestination? = null,
      hasLocationPermission: Boolean = false,
      filterRadiusMeters: Int? = null,
      roomsToShow: List<RoomSessions> = rooms,
  ) {
    composeTestRule.setContent {
      // The real Google map cannot load in a unit test; in inspection mode the map view shows a
      // stand-in with the same tap behaviour.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContent(
            rooms = roomsToShow,
            now = now,
            onJoin = onJoin,
            onLeave = {},
            onEdit = onEdit,
            initialSelectedRoom = initialSelectedRoom,
            userLocation = userLocation,
            cameraDestination = cameraDestination,
            hasLocationPermission = hasLocationPermission,
            filterRadiusMeters = filterRadiusMeters,
        )
      }
    }
  }

  @Test
  fun defaultCampusCenter_readsCoordinatesFromMapResources() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val expected =
        Location(
            ResourcesCompat.getFloat(context.resources, R.dimen.map_campus_center_latitude)
                .toDouble(),
            ResourcesCompat.getFloat(context.resources, R.dimen.map_campus_center_longitude)
                .toDouble(),
        )

    assertEquals(expected, defaultCampusCenter(context.resources))

    var composableCenter: Location? = null
    composeTestRule.setContent { composableCenter = defaultCampusCenter() }
    composeTestRule.waitForIdle()
    assertEquals(expected, composableCenter)
  }

  @Test
  fun cameraDestination_closesSelectedRoom() {
    var destination by mutableStateOf<CameraDestination?>(null)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContent(
            rooms = rooms,
            now = now,
            onJoin = {},
            onLeave = {},
            onEdit = {},
            initialSelectedRoom = "CM 1 121",
            cameraDestination = destination,
        )
      }
    }

    composeTestRule.onNodeWithText("Chemistry").assertIsDisplayed()
    destination = CameraDestination(Location(46.52, 6.56), trigger = 1)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun content_withLocationPermission_displaysUserLocationMarker() {
    setContent(hasLocationPermission = true)

    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.USER_LOCATION_MARKER, useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun content_withUserLocation_displaysUserLocationMarker() {
    setContent(userLocation = Location(46.519, 6.565))

    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.USER_LOCATION_MARKER, useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun content_withoutUserLocation_doesNotDisplayUserLocationMarker() {
    setContent(userLocation = null, hasLocationPermission = false)

    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.USER_LOCATION_MARKER, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun filterArea_returnsNullWhenLocationOrRadiusMissingOrNonPositive() {
    val location = Location(46.5191, 6.5668)
    assertNull(filterArea(null, 500))
    assertNull(filterArea(location, null))
    assertNull(filterArea(null, null))
    assertNull(filterArea(location, 0))
    assertNull(filterArea(location, -100))
  }

  @Test
  fun filterArea_returnsAreaWhenLocationAndPositiveRadiusAreSet() {
    val location = Location(46.5191, 6.5668)
    assertEquals(FilterArea(location, 500), filterArea(location, 500))
  }

  @Test
  fun circleBounds_for500mAtCampusCenter_hasExpectedSpanAndSymmetry() {
    val resources = ApplicationProvider.getApplicationContext<Context>().resources
    val center = Location(46.5191, 6.5668)
    val bounds = circleBounds(FilterArea(center, 500), resources)

    val latSpan = bounds.northeast.latitude - bounds.southwest.latitude
    val lngSpan = bounds.northeast.longitude - bounds.southwest.longitude

    // 1000 m diameter along a meridian is ~0.0090 degrees of latitude.
    assertEquals(0.0090, latSpan, 0.0002)
    assertTrue(lngSpan > latSpan)
    assertEquals(center.latitude, (bounds.northeast.latitude + bounds.southwest.latitude) / 2, 1e-6)
    assertEquals(
        center.longitude,
        (bounds.northeast.longitude + bounds.southwest.longitude) / 2,
        1e-6,
    )
  }

  @Test
  fun circleBounds_growsWithRadius() {
    val resources = ApplicationProvider.getApplicationContext<Context>().resources
    val center = Location(46.5191, 6.5668)
    val small = circleBounds(FilterArea(center, 250), resources)
    val large = circleBounds(FilterArea(center, 1000), resources)

    assertTrue(large.northeast.latitude > small.northeast.latitude)
    assertTrue(large.southwest.latitude < small.southwest.latitude)
    assertTrue(large.northeast.longitude > small.northeast.longitude)
    assertTrue(large.southwest.longitude < small.southwest.longitude)
  }

  @Test
  fun content_withLocationAndRadius_displaysFilterRadiusCircleAndLabel() {
    setContent(userLocation = Location(46.5191, 6.5668), filterRadiusMeters = 500)

    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.FILTER_RADIUS_CIRCLE, useUnmergedTree = true)
        .assertExists()
    composeTestRule.onNodeWithText("Pins in this area", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun content_withRadiusButNoLocation_doesNotDisplayFilterRadiusCircle() {
    setContent(userLocation = null, hasLocationPermission = true, filterRadiusMeters = 500)

    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.FILTER_RADIUS_CIRCLE, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun content_withLocationButNullRadius_doesNotDisplayFilterRadiusCircle() {
    setContent(userLocation = Location(46.5191, 6.5668), filterRadiusMeters = null)

    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.FILTER_RADIUS_CIRCLE, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun content_changingOrClearingRadius_updatesAndHidesFilterRadiusCircle() {
    var radius by mutableStateOf<Int?>(250)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContent(
            rooms = rooms,
            now = now,
            onJoin = {},
            onLeave = {},
            onEdit = {},
            userLocation = Location(46.5191, 6.5668),
            filterRadiusMeters = radius,
        )
      }
    }

    composeTestRule.onNodeWithText("Filter radius: 250 m", useUnmergedTree = true).assertExists()

    radius = 1000
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Filter radius: 1000 m", useUnmergedTree = true).assertExists()

    radius = null
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.FILTER_RADIUS_CIRCLE, useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun resolveStableLocation_retainsLastKnownOnTransientNullWhenPermitted() {
    val first = Location(46.5191, 6.5668)
    val moved = Location(46.5200, 6.5675)

    // Transient null keeps last known location when permission is granted.
    assertEquals(first, resolveStableLocation(first, null, hasLocationPermission = true))
    // New non-null location updates so the circle follows the user.
    assertEquals(moved, resolveStableLocation(first, moved, hasLocationPermission = true))
    // Never fabricates a location when none was known yet.
    assertNull(resolveStableLocation(null, null, hasLocationPermission = true))
  }

  @Test
  fun resolveStableLocation_usesCurrentDirectlyWhenPermissionNotGranted() {
    val first = Location(46.5191, 6.5668)

    assertNull(resolveStableLocation(first, null, hasLocationPermission = false))
    assertEquals(first, resolveStableLocation(null, first, hasLocationPermission = false))
  }

  @Test
  fun toLatLng_preservesLatitudeAndLongitude() {
    val location = Location(46.5191, 6.5668)
    val latLng = location.toLatLng()

    assertEquals(location.latitude, latLng.latitude, 0.0)
    assertEquals(location.longitude, latLng.longitude, 0.0)
  }

  @Test
  fun roomsWithinFilterArea_returnsAllRoomsWhenAreaIsNull() {
    assertEquals(listOf(aab, cm, bc), roomsWithinFilterArea(listOf(aab, cm, bc), null))
  }

  @Test
  fun roomsWithinFilterArea_excludesRoomsOutsideRadiusAndKeepsRoomsInside() {
    val center = Location(46.5191, 6.5668)
    val insideRoom =
        CampusLocation("IN 1", "IN", CampusLocationType.LECTURE_ROOM, 0, Location(46.5195, 6.5668))
    val outsideRoom =
        CampusLocation(
            "OUT 1",
            "OUT",
            CampusLocationType.LECTURE_ROOM,
            0,
            Location(46.5300, 6.5668),
        )

    val filtered = roomsWithinFilterArea(listOf(insideRoom, outsideRoom), FilterArea(center, 500))

    assertEquals(listOf(insideRoom), filtered)
  }

  @Test
  fun content_beforeAnyTap_showsNoList() {
    setContent()

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun content_withAnInitialRoom_showsItsCardsWithoutATap() {
    setContent(initialSelectedRoom = "CM 1 121")

    composeTestRule.onNodeWithText("Chemistry").assertIsDisplayed()
  }

  @Test
  fun content_withAnUnknownInitialRoom_showsNoList() {
    setContent(initialSelectedRoom = "Nowhere")

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun content_withAnInitialRoom_showsThatRoomsCardsOnly() {
    setContent(initialSelectedRoom = "AAB 0 26")

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertIsDisplayed()
    composeTestRule.onNodeWithText("Maths").assertIsDisplayed()
    // The list scrolls: the second card is reached by scrolling.
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Physics"))
    composeTestRule.onNodeWithText("Physics").assertIsDisplayed()
    composeTestRule.onNodeWithText("Chemistry").assertDoesNotExist()
  }

  @Test
  fun tappingTheMap_closesTheList() {
    setContent(initialSelectedRoom = "AAB 0 26")

    composeTestRule.onNodeWithText(MAP_TITLE).performClick()

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun selection_survivesTheStateBeingSavedAndRestored() {
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContent(
            rooms = rooms,
            now = now,
            onJoin = {},
            onLeave = {},
            onEdit = {},
            initialSelectedRoom = "CM 1 121",
        )
      }
    }
    // Closing the list changes the selection away from its initial value.
    composeTestRule.onNodeWithText(MAP_TITLE).performClick()

    restoration.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun joinOnACard_reportsTheSessionId() {
    var joined: String? = null
    setContent(onJoin = { joined = it }, initialSelectedRoom = "CM 1 121")

    composeTestRule.onNodeWithText(JOIN_LABEL).performClick()

    assertEquals("s3", joined)
  }

  /** A pending join in the selected room shows progress and ignores card taps. */
  @Test
  fun pendingJoinOnACard_showsProgressAndIgnoresTap() {
    var joined: String? = null
    val pendingItem = item("pending", "Pending", isActionInProgress = true)
    setContent(
        onJoin = { joined = it },
        initialSelectedRoom = cm.name,
        roomsToShow = listOf(RoomSessions(cm, listOf(pendingItem))),
    )
    val sessionList = composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST)
    sessionList.performScrollToNode(hasText("Pending"))

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.joinButton("pending"))
        .assert(
            hasAnyDescendant(
                hasTestTag(GroupSessionScreenTestTags.actionProgress("pending")),
            ),
        )
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.actionProgress("pending"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.joinButton("pending"))
        .performTouchInput { click() }

    assertEquals(null, joined)
  }

  @Test
  fun editOnTheCreatorsCard_reportsTheSessionId() {
    var edited: String? = null
    setContent(onEdit = { edited = it }, initialSelectedRoom = "BC 410")

    composeTestRule.onNodeWithText(EDIT_LABEL).performClick()

    assertEquals("s4", edited)
  }

  /** The bounds of the [dropletPath] of [size], with the turns in degrees. */
  private fun dropletBounds(size: Size): Rect = dropletPath(size, 90f, 360f).getBounds()

  private fun assertBounds(expected: Rect, actual: Rect, tolerance: Float) {
    assertEquals(expected.left, actual.left, tolerance)
    assertEquals(expected.top, actual.top, tolerance)
    assertEquals(expected.right, actual.right, tolerance)
    assertEquals(expected.bottom, actual.bottom, tolerance)
  }

  @Test
  fun dropletPath_ofTheMarkerSize_fillsItsWholeSize() {
    // The size of the pin on the map: 32 x 40, taller than it is wide.
    val bounds = dropletBounds(Size(32f, 40f))

    // The tip is the lowest point, so the shape ends exactly on the bottom edge.
    assertEquals(40f, bounds.bottom, DROPLET_EXACT)
    // Robolectric bounds an arc loosely (by about 1.7 here), so the other edges only need to be
    // near the size: a NaN or a collapsed shape would still be far off.
    assertBounds(Rect(0f, 0f, 32f, 40f), bounds, DROPLET_ARC_TOLERANCE)
  }

  @Test
  fun dropletPath_ofAShapeNotTallerThanWide_isJustTheCircle() {
    // Equal height and width, a height under the width, a height of half the width (a tip distance
    // of 0, which divides by 0 without the guard), and an empty size: none has room for a tip.
    listOf(Size(32f, 32f), Size(32f, 20f), Size(32f, 16f), Size.Zero).forEach { size ->
      assertBounds(Rect(0f, 0f, size.width, size.width), dropletBounds(size), DROPLET_EXACT)
    }
  }

  private companion object {
    const val MAP_TITLE = "Map (shown on a device only)"
    const val JOIN_LABEL = "Join"
    const val EDIT_LABEL = "Edit"
    const val DROPLET_EXACT = 0.01f
    const val DROPLET_ARC_TOLERANCE = 3f
  }
}
