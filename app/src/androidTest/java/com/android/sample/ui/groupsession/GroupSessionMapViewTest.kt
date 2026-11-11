// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
 * services: the camera moves and the marker taps. The map tiles are not needed, so they pass
 * without a Maps key or a network.
 */
@RunWith(AndroidJUnit4::class)
class GroupSessionMapViewTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
  private val campusCenter = Location(46.5191, 6.5668)
  private val defaultZoom = resourceFloat(R.dimen.map_default_zoom)
  private val focusZoom = resourceFloat(R.dimen.map_focus_zoom)

  // Two rooms close enough to both be on screen at the start zoom.
  private val first = MapMarkerUi("AAB 0 26", Location(46.5191, 6.5660), count = 3)
  private val second = MapMarkerUi("CM 1 121", Location(46.5191, 6.5676), count = 1)

  private lateinit var camera: CameraPositionState
  private val map = MapTestDriver(composeTestRule) { camera }
  private var tappedRoom: String? = null
  private var mapClicks = 0

  private fun resourceFloat(id: Int): Float = ResourcesCompat.getFloat(resources, id)

  /** Shows the map with the camera far from the campus centre, at a zoom between the two used. */
  private fun showMap(cameraTarget: Location? = null) {
    composeTestRule.setContent {
      camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(46.5191, 6.5600), START_ZOOM)
      }
      GroupSessionMapView(
          markers = listOf(first, second),
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

  @Test
  fun recenterButton_returnsToTheCampusCentreAtTheDefaultZoom() {
    showMap()

    composeTestRule
        .onNodeWithContentDescription(resources.getString(R.string.group_session_map_recenter))
        .performClick()

    waitForCameraAt(campusCenter, minZoom = defaultZoom)
  }

  @Test
  fun tappingAPin_reportsItsRoom() {
    showMap()

    map.tapPinUntil(second.location.toLatLng()) { tappedRoom != null }

    assertEquals("CM 1 121", tappedRoom)
  }

  @Test
  fun tappingAPin_movesTheCameraOntoItAtLeastToTheFocusZoom() {
    showMap()

    map.tapPinUntil(second.location.toLatLng()) { tappedRoom != null }

    waitForCameraAt(second.location, minZoom = focusZoom, maxZoom = 21f)
  }

  @Test
  fun tappingAPin_doesNotReportAMapClick() {
    showMap()
    map.tapPinUntil(first.location.toLatLng()) { tappedRoom != null }
    waitForCameraAt(first.location, minZoom = focusZoom, maxZoom = 21f)
    // Taps that missed the pin while it was being drawn report their map clicks late.
    Thread.sleep(MapTestDriver.TAP_SPACING_MS)
    val clicksBefore = mapClicks
    tappedRoom = null

    map.tapPin(first.location.toLatLng())
    assertTrue(map.waitFor { tappedRoom != null })
    Thread.sleep(MapTestDriver.TAP_SPACING_MS)

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
    const val POSITION_TOLERANCE = 0.0002
    const val ZOOM_TOLERANCE = 0.05f
  }
}
