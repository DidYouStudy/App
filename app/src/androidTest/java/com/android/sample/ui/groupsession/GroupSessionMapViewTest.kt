// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.sample.R
import com.android.sample.model.location.Location
import com.android.sample.ui.theme.Dimens
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
    // The projection exists once the map is ready.
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.runOnUiThread { camera.projection != null }
    }
  }

  private fun waitForCameraAt(target: Location, minZoom: Float, maxZoom: Float = minZoom) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      val position = camera.position
      !camera.isMoving &&
          abs(position.target.latitude - target.latitude) < POSITION_TOLERANCE &&
          abs(position.target.longitude - target.longitude) < POSITION_TOLERANCE &&
          position.zoom >= minZoom - ZOOM_TOLERANCE &&
          position.zoom <= maxZoom + ZOOM_TOLERANCE
    }
  }

  /** Where [location] is on the screen; Google Maps answers only on the main thread. */
  private fun screenLocation(location: LatLng) = composeTestRule.runOnUiThread {
    checkNotNull(camera.projection).toScreenLocation(location)
  }

  /** Taps on the pin of [marker]: its tip is on the coordinates, so the body is above them. */
  private fun tapPin(marker: MapMarkerUi) {
    val tip = screenLocation(marker.location.toLatLng())
    val halfPin = with(composeTestRule.density) { Dimens.mapMarkerHeight.toPx() } / 2
    composeTestRule.onRoot().performTouchInput { click(Offset(tip.x.toFloat(), tip.y - halfPin)) }
  }

  /**
   * The pins are drawn a moment after the map, so the first taps land on the map. Each tap waits
   * for the pin's report before the next one: the report arrives a moment after the tap, and any
   * touch cancels the camera move the report starts. The wait also spaces the taps out, because
   * taps in quick succession count as a double tap, which zooms the map and moves the pin from
   * under the next tap.
   */
  private fun tapPinUntilReported(marker: MapMarkerUi) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.waitUntil(TIMEOUT_MS) { !camera.isMoving }
      tapPin(marker)
      waitForPinReport()
    }
  }

  /** Waits up to [TAP_SPACING_MS] for a pin tap to be reported; returns whether it was. */
  private fun waitForPinReport(): Boolean {
    val end = System.currentTimeMillis() + TAP_SPACING_MS
    while (System.currentTimeMillis() < end) {
      composeTestRule.waitForIdle()
      if (tappedRoom != null) return true
      Thread.sleep(POLL_MS)
    }
    return false
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

    tapPinUntilReported(second)

    assertEquals("CM 1 121", tappedRoom)
  }

  @Test
  fun tappingAPin_movesTheCameraOntoItAtLeastToTheFocusZoom() {
    showMap()

    tapPinUntilReported(second)

    waitForCameraAt(second.location, minZoom = focusZoom, maxZoom = 21f)
  }

  @Test
  fun tappingAPin_doesNotReportAMapClick() {
    showMap()
    tapPinUntilReported(first)
    waitForCameraAt(first.location, minZoom = focusZoom, maxZoom = 21f)
    // Taps that missed the pin while it was being drawn report their map clicks late.
    Thread.sleep(TAP_SPACING_MS)
    val clicksBefore = mapClicks
    tappedRoom = null

    tapPin(first)
    assertTrue(waitForPinReport())
    Thread.sleep(TAP_SPACING_MS)

    assertEquals(clicksBefore, mapClicks)
  }

  @Test
  fun tappingTheMapAwayFromPins_reportsAMapClickOnly() {
    showMap()
    val away = screenLocation(LatLng(46.5170, 6.5600))

    composeTestRule.onRoot().performTouchInput { click(Offset(away.x.toFloat(), away.y.toFloat())) }

    composeTestRule.waitUntil(TIMEOUT_MS) { mapClicks == 1 }
    assertTrue(tappedRoom == null)
  }

  private companion object {
    const val START_ZOOM = 15f
    const val TIMEOUT_MS = 30_000L
    // Longer than the double-tap timeout, so that two taps are never read as one gesture.
    const val TAP_SPACING_MS = 800L
    const val POLL_MS = 50L
    const val POSITION_TOLERANCE = 0.0002
    const val ZOOM_TOLERANCE = 0.05f
  }
}
