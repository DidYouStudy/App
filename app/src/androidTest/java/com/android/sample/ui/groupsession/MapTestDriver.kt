// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import android.graphics.Point
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.android.sample.ui.theme.Dimens
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState

/**
 * Drives the real Google map from instrumented tests: waits for it, finds where places are on the
 * screen and taps them.
 *
 * The map is not part of the Compose tree, so pins cannot be found as nodes. They are tapped by
 * screen position, computed from the camera's projection.
 *
 * @param camera The camera of the map under test; read each time, so it may be set after this
 *   driver is created.
 */
class MapTestDriver(
    private val composeTestRule: ComposeTestRule,
    private val camera: () -> CameraPositionState,
) {

  /** Waits until the map is ready: its projection exists once it is. */
  fun waitForMap() {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      composeTestRule.runOnUiThread { camera().projection != null }
    }
  }

  /** Waits until the camera has not moved for a short while. */
  fun waitForCameraStill() {
    var stillSince: Long? = null
    composeTestRule.waitUntil(TIMEOUT_MS) {
      if (camera().isMoving) {
        stillSince = null
        false
      } else {
        val since = stillSince ?: System.currentTimeMillis().also { stillSince = it }
        System.currentTimeMillis() - since >= STILL_MS
      }
    }
  }

  /** Moves the camera at once, without an animation; the map only answers on the main thread. */
  fun moveCameraTo(target: LatLng, zoom: Float) {
    composeTestRule.runOnUiThread { camera().move(CameraUpdateFactory.newLatLngZoom(target, zoom)) }
  }

  /** Where [location] is on the screen; Google Maps answers only on the main thread. */
  fun screenLocation(location: LatLng): Point = composeTestRule.runOnUiThread {
    checkNotNull(camera().projection).toScreenLocation(location)
  }

  /** Taps the map at [location], for places away from any pin. */
  fun tapAt(location: LatLng) {
    val point = screenLocation(location)
    composeTestRule.onRoot().performTouchInput {
      click(Offset(point.x.toFloat(), point.y.toFloat()))
    }
  }

  /** Taps the pin at [location]: its tip is on the coordinates, so its body is above them. */
  fun tapPin(location: LatLng) {
    val tip = screenLocation(location)
    val halfPin = with(composeTestRule.density) { Dimens.mapMarkerHeight.toPx() } / 2
    composeTestRule.onRoot().performTouchInput { click(Offset(tip.x.toFloat(), tip.y - halfPin)) }
  }

  /**
   * Taps the pin at [location] until [isDone] holds.
   *
   * The pins are drawn a moment after the map, so the first taps land on the map. Each tap waits
   * for its result before the next one: a pin's report arrives a moment after the tap, and any
   * touch cancels the camera move the report starts. The wait also spaces the taps out, because
   * taps in quick succession count as a double tap, which zooms the map and moves the pin from
   * under the next tap.
   */
  fun tapPinUntil(location: LatLng, isDone: () -> Boolean) {
    composeTestRule.waitUntil(TIMEOUT_MS) {
      waitForCameraStill()
      tapPin(location)
      waitFor(isDone)
    }
  }

  /** Waits up to [TAP_SPACING_MS] for [isDone] to hold; returns whether it did. */
  fun waitFor(isDone: () -> Boolean): Boolean {
    val end = System.currentTimeMillis() + TAP_SPACING_MS
    while (System.currentTimeMillis() < end) {
      composeTestRule.waitForIdle()
      if (isDone()) return true
      Thread.sleep(POLL_MS)
    }
    return false
  }

  companion object {
    const val TIMEOUT_MS = 30_000L
    // Longer than the double-tap timeout, so that two taps are never read as one gesture.
    const val TAP_SPACING_MS = 800L
    private const val POLL_MS = 50L
    private const val STILL_MS = 300L
  }
}
