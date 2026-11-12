// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.sample.R
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.location.CampusLocationRepositoryProvider
import com.android.sample.model.location.Location
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of [GroupSessionMapContent] with the real Google map, on a device or emulator with Google
 * Play services and a Maps key: tapping a real pin opens the cards of its room. The unit tests of
 * the content use a stand-in for the map, so this covers what they cannot, the pins themselves.
 * Without a valid key the map draws no pins, so these tests fail.
 *
 * A pin is not a node of the Compose tree, so a test finds it by tapping where its room is. A tap
 * that opens the room's cards therefore shows that the pin is drawn there and can be tapped. That
 * is also how [GroupSessionMapContentPreview] is tested, with the real rooms of the campus data.
 */
@RunWith(AndroidJUnit4::class)
class GroupSessionMapContentRealMapTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
  private val now = Instant.parse("2030-01-01T09:00:00Z")

  // Two rooms close enough to both be on screen at the start zoom.
  private val aab = MapTestDriver.lectureRoom("AAB 0 26", Location(46.5191, 6.5660))
  private val cm = MapTestDriver.lectureRoom("CM 1 121", Location(46.5191, 6.5676))
  private val rooms =
      listOf(
          RoomSessions(aab, listOf(item("s1", "Maths"), item("s2", "Physics"))),
          RoomSessions(cm, listOf(item("s3", "Chemistry"))),
      )

  private lateinit var camera: CameraPositionState
  private val map = MapTestDriver(composeTestRule) { camera }

  private fun item(id: String, subject: String) =
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
          isCreator = false,
      )

  private fun showContent() {
    composeTestRule.setContent {
      camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(46.5191, 6.5668), START_ZOOM)
      }
      GroupSessionMapContent(
          rooms = rooms,
          now = now,
          onJoin = {},
          onLeave = {},
          onEdit = {},
          cameraPositionState = camera,
      )
    }
    map.waitForMap()
  }

  // The rooms of the preview, as the campus data places them.
  private val previewRooms: Map<String, LatLng> by lazy {
    runBlocking {
      CampusLocationRepositoryProvider.getRepository(targetContext).getAllLocations().getOrThrow()
    }
        .associate { it.name to it.location.toLatLng() }
  }

  private fun previewRoom(name: String) = checkNotNull(previewRooms[name])

  private fun betweenThePreviewRooms() =
      MapTestDriver.midpoint(previewRoom(PREVIEW_FIRST_ROOM), previewRoom(PREVIEW_SECOND_ROOM))

  /** Shows the preview, with the camera on both of its rooms. */
  private fun showPreview() {
    val resources = targetContext.resources
    val campusCenter =
        LatLng(
            ResourcesCompat.getFloat(resources, R.dimen.map_campus_center_latitude).toDouble(),
            ResourcesCompat.getFloat(resources, R.dimen.map_campus_center_longitude).toDouble(),
        )
    composeTestRule.setContent {
      // Starting on the campus centre, where the content first sends the camera, so that it makes
      // no move of its own later.
      camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(campusCenter, PREVIEW_ZOOM)
      }
      GroupSessionMapContentPreview(cameraPositionState = camera)
    }
    map.waitForMap()
    showBothPreviewRooms()
  }

  /**
   * Puts both rooms of the preview on the screen, whatever its size. Tapping a pin zooms in on it,
   * which can push the other room off the screen, so tests do this again before tapping another.
   */
  private fun showBothPreviewRooms() {
    map.waitForCameraStill()
    map.moveCameraTo(betweenThePreviewRooms(), PREVIEW_ZOOM)
  }

  private fun listIsShown() =
      composeTestRule
          .onAllNodesWithTag(GroupSessionMapTestTags.SESSION_LIST)
          .fetchSemanticsNodes()
          .isNotEmpty()

  private fun isShown(text: String) =
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  /** Tapping a pin opens the cards of its room only; tapping another pin switches to its cards. */
  @Test
  fun tappingPins_showsTheCardsOfTheTappedRoomOnly() {
    showContent()
    assert(!listIsShown()) // no list before any tap

    map.tapPinUntil(aab.location.toLatLng()) { listIsShown() }
    composeTestRule.onNodeWithText("Maths").assertIsDisplayed()
    assert(!isShown("Chemistry"))

    map.tapPinUntil(cm.location.toLatLng()) { isShown("Chemistry") }
    assert(!isShown("Maths"))
  }

  @Test
  fun tappingTheMapAwayFromPins_closesTheList() {
    showContent()
    map.tapPinUntil(aab.location.toLatLng()) { listIsShown() }
    map.waitForCameraStill()

    // North of the pin, in the half of the screen the list does not cover.
    map.tapAt(LatLng(46.5200, 6.5660))

    composeTestRule.waitUntil(MapTestDriver.TIMEOUT_MS) { !listIsShown() }
  }

  /** The preview shows no cards at first, then the session cards of whichever room is tapped. */
  @Test
  fun preview_tappingEachRoomsPin_showsItsSessionCards() {
    showPreview()
    assert(!listIsShown()) // no cards before any tap

    map.tapPinUntil(previewRoom(PREVIEW_FIRST_ROOM)) { listIsShown() }
    composeTestRule.onNodeWithText("Linear Algebra").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Software Architecture"))
    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    assert(!isShown("Thermodynamics"))

    // The first tap zoomed in, so bring the other room back on the screen.
    showBothPreviewRooms()
    map.tapPinUntil(previewRoom(PREVIEW_SECOND_ROOM)) { isShown("Thermodynamics") }
    assert(!isShown("Linear Algebra"))
    assert(!isShown("Software Architecture"))
  }

  private companion object {
    // Test geometry, not the app's zoom: close enough in to tell the test rooms apart, which are
    // about 150 dp apart at this zoom, so both are on screen even on a small one.
    const val START_ZOOM = 17f

    // Test geometry, not the app's zoom: far enough in to tell the preview's pins apart, far enough
    // out to hold both on a small screen.
    const val PREVIEW_ZOOM = 16f
  }
}
