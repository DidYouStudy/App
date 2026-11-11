// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of [GroupSessionMapContent] with the real Google map, on a device or emulator with Google
 * Play services: tapping a real pin opens the cards of its room. The unit tests of the content use
 * a stand-in for the map, so this covers what they cannot, the pins themselves. The map tiles are
 * not needed, so the tests pass without a Maps key or a network.
 */
@RunWith(AndroidJUnit4::class)
class GroupSessionMapContentRealMapTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val now = Instant.parse("2030-01-01T09:00:00Z")

  // Two rooms close enough to both be on screen at the start zoom.
  private val aab = room("AAB 0 26", Location(46.5191, 6.5660))
  private val cm = room("CM 1 121", Location(46.5191, 6.5676))
  private val rooms =
      listOf(
          RoomSessions(aab, listOf(item("s1", "Maths"), item("s2", "Physics"))),
          RoomSessions(cm, listOf(item("s3", "Chemistry"))),
      )

  private lateinit var camera: CameraPositionState
  private val map = MapTestDriver(composeTestRule) { camera }

  private fun room(name: String, location: Location) =
      CampusLocation(name, name.substringBefore(' '), CampusLocationType.LECTURE_ROOM, 0, location)

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
          cameraPositionState = camera,
      )
    }
    map.waitForMap()
  }

  private fun listIsShown() =
      composeTestRule
          .onAllNodesWithTag(GroupSessionMapTestTags.SESSION_LIST)
          .fetchSemanticsNodes()
          .isNotEmpty()

  private fun isShown(text: String) =
      composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun beforeAnyTap_noListIsShown() {
    showContent()

    assert(!listIsShown())
  }

  @Test
  fun tappingAPin_showsTheCardsOfThatRoomOnly() {
    showContent()

    map.tapPinUntil(aab.location.toLatLng()) { listIsShown() }

    composeTestRule.onNodeWithText("Maths").assertIsDisplayed()
    assert(!isShown("Chemistry"))
  }

  @Test
  fun tappingAnotherPin_switchesToThatRoomsCards() {
    showContent()
    map.tapPinUntil(aab.location.toLatLng()) { listIsShown() }

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

  private companion object {
    const val START_ZOOM = 17f
  }
}
