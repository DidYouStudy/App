// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.sample.R
import com.android.sample.model.location.Location
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of the real Google map in [GroupSessionMapView], on a device or emulator with Google Play
 * services and a Maps key: the camera moves and the marker taps. Without a valid key the map draws
 * no pins and moves no camera, so these tests fail.
 */
@RunWith(AndroidJUnit4::class)
class GroupSessionMapViewTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
  private val campusCenter = Location(46.5191, 6.5668)
  private val focusZoom = resourceFloat(R.dimen.map_focus_zoom)

  private val first = MapTestDriver.lectureRoom("AAB 0 26", Location(46.5207473, 6.5632687))
  private val second = MapTestDriver.lectureRoom("CM 1 121", Location(46.5205949, 6.5676836))

  // Halfway between the two pins, so both are near the middle of the screen at the start zoom, on
  // whatever size of screen the test runs.
  private val betweenThePins =
      MapTestDriver.midpoint(first.location.toLatLng(), second.location.toLatLng())

  private lateinit var camera: CameraPositionState
  private val map = MapTestDriver(composeTestRule) { camera }
  private var tappedRoom: String? = null
  private var mapClicks = 0

  private fun resourceFloat(id: Int): Float = ResourcesCompat.getFloat(resources, id)

  /**
   * Shows the map with the camera at [startAt], at a zoom between the two used. By default that is
   * far from the campus centre and from the pins, so the tests of camera moves have a move to make.
   */
  private fun showMap(cameraTarget: Location? = null, startAt: LatLng = FAR_AWAY) {
    composeTestRule.setContent {
      camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(startAt, START_ZOOM)
      }
      GroupSessionMapView(
          rooms = listOf(first, second),
          cameraTarget = cameraTarget,
          onMarkerClick = { tappedRoom = it },
          onMapClick = { mapClicks++ },
          cameraPositionState = camera,
      )
    }
    map.waitForMap()
  }

  private fun waitForCameraAt(target: Location, minZoom: Float, maxZoom: Float = minZoom) {
    composeTestRule.waitUntil(MapTestDriver.TIMEOUT_MS) {
      val position = camera.position
      !camera.isMoving &&
          abs(position.target.latitude - target.latitude) < POSITION_TOLERANCE &&
          abs(position.target.longitude - target.longitude) < POSITION_TOLERANCE &&
          position.zoom >= minZoom - ZOOM_TOLERANCE &&
          position.zoom <= maxZoom + ZOOM_TOLERANCE
    }
  }

  @Test
  fun startingTarget_movesTheCameraOntoItAtLeastToTheFocusZoom() {
    showMap(cameraTarget = campusCenter)

    waitForCameraAt(campusCenter, minZoom = focusZoom, maxZoom = 21f)
  }

  /**
   * Tapping a pin reports its room and moves the camera onto it, and the tap is not also reported
   * as a click on the map.
   */
  @Test
  fun tappingAPin_reportsItsRoomAndFocusesItWithoutAMapClick() {
    showMap(startAt = betweenThePins)

    map.tapPinUntil(first.location.toLatLng()) { tappedRoom != null }
    assertEquals(first.name, tappedRoom)
    waitForCameraAt(first.location, minZoom = focusZoom, maxZoom = 21f)
    // Taps that missed the pin while it was being drawn report their map clicks late.
    Thread.sleep(MapTestDriver.TAP_SPACING_MS)
    val clicksBefore = mapClicks
    tappedRoom = null

    map.tapPin(first.location.toLatLng())
    assertTrue(map.waitFor { tappedRoom != null })
    Thread.sleep(MapTestDriver.TAP_SPACING_MS)

    assertEquals(first.name, tappedRoom)
    assertEquals(clicksBefore, mapClicks)
  }

  @Test
  fun tappingTheMapAwayFromPins_reportsAMapClickOnly() {
    showMap()

    map.tapAt(LatLng(46.5170, 6.5600))

    composeTestRule.waitUntil(MapTestDriver.TIMEOUT_MS) { mapClicks == 1 }
    assertTrue(tappedRoom == null)
  }

  private companion object {
    const val START_ZOOM = 15f
    val FAR_AWAY = LatLng(46.5191, 6.5600)
    const val POSITION_TOLERANCE = 0.0002
    const val ZOOM_TOLERANCE = 0.05f
  }
}
