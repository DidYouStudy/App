// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.annotation.DimenRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.res.ResourcesCompat
import com.android.sample.R
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.Location
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.math.acos
import kotlinx.coroutines.launch

/**
 * Map of the campus with one pin per room.
 *
 * It is the only composable that draws Google Maps, so the rest of the screen stays free of map
 * types, apart from the optional camera passed through for tests, and can be tested without the
 * real map. It holds no selection: tapping a pin moves the camera onto it and reports the room to
 * the caller, which decides what to show.
 *
 * A button in the top-right corner moves the camera back to the campus centre at the default zoom.
 *
 * Needs a Maps API key in the app manifest; without one the map stays blank.
 *
 * @param rooms Rooms to draw a pin for, at their coordinates.
 * @param cameraTarget Place to move the camera to whenever it changes, for moves the caller starts;
 *   null keeps the camera where the user left it.
 * @param onMarkerClick Called with the room name when a pin is tapped, after the camera starts
 *   moving onto it.
 * @param onMapClick Called when the map is tapped away from any pin.
 * @param cameraPositionState Camera of the map, for a caller that needs to read or move it, such as
 *   instrumented tests; by default a camera on the campus centre at the default zoom.
 */
@Composable
fun GroupSessionMapView(
    rooms: List<CampusLocation>,
    cameraTarget: Location?,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit = {},
    cameraPositionState: CameraPositionState? = null,
) {
  // The real map cannot load in the IDE preview, so it shows a stand-in.
  if (LocalInspectionMode.current) {
    MapPlaceholder(onMapClick, modifier)
    return
  }

  val campusCenter =
      LatLng(
          floatResource(R.dimen.map_campus_center_latitude).toDouble(),
          floatResource(R.dimen.map_campus_center_longitude).toDouble(),
      )
  val defaultZoom = floatResource(R.dimen.map_default_zoom)
  val focusZoom = floatResource(R.dimen.map_focus_zoom)
  val cameraAnimationMs = integerResource(R.integer.map_camera_animation_ms)
  val cameraPositionState =
      cameraPositionState
          ?: rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(campusCenter, defaultZoom)
          }

  // Moves the camera onto a place, zooming in if the map is further out than the focus zoom.
  val focusOn: suspend (Location) -> Unit = { target ->
    val zoom = maxOf(cameraPositionState.position.zoom, focusZoom)
    cameraPositionState.animate(
        CameraUpdateFactory.newLatLngZoom(target.toLatLng(), zoom),
        cameraAnimationMs,
    )
  }
  // Moves the camera back to the campus centre at the default zoom.
  val recenter: suspend () -> Unit = {
    cameraPositionState.animate(
        CameraUpdateFactory.newLatLngZoom(campusCenter, defaultZoom),
        cameraAnimationMs,
    )
  }
  val scope = rememberCoroutineScope()

  LaunchedEffect(cameraTarget) { cameraTarget?.let { focusOn(it) } }

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        onMapClick = { onMapClick() },
    ) {
      rooms.forEach { room ->
        MarkerComposable(
            room.name,
            state = rememberUpdatedMarkerState(position = room.location.toLatLng()),
            onClick = {
              scope.launch { focusOn(room.location) }
              onMarkerClick(room.name)
              // Consumed: the camera move above replaces the map's default one.
              true
            },
        ) {
          MarkerPin()
        }
      }
    }
    FloatingActionButton(
        onClick = { scope.launch { recenter() } },
        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.large),
    ) {
      Icon(
          imageVector = Icons.Default.Explore,
          modifier = Modifier.size(Dimens.mapRecenterIconSize),
          contentDescription = stringResource(R.string.group_session_map_recenter),
      )
    }
  }
}

/**
 * Pin drawn for a room: a droplet in the primary colour with its tip down and a white dot in the
 * middle. Google Maps anchors a marker at its bottom centre, so the tip sits on the room's
 * coordinates.
 */
@Composable
private fun MarkerPin() {
  val width = Dimens.mapMarkerWidth
  val height = Dimens.mapMarkerHeight
  val dotDiameter = Dimens.mapMarkerDotDiameter
  val quarterTurn = integerResource(R.integer.map_quarter_turn_degrees).toFloat()
  val fullTurn = integerResource(R.integer.map_full_turn_degrees).toFloat()
  val color = MaterialTheme.colorScheme.primary
  Canvas(modifier = Modifier.size(width, height)) {
    drawPath(dropletPath(size, quarterTurn, fullTurn), color)
    drawCircle(
        Color.White,
        radius = dotDiameter.toPx() / 2,
        center = Offset(size.width / 2, size.width / 2),
    )
  }
}

/**
 * Outline of a droplet that fits [size]: a circle as wide as the shape, with two straight sides
 * running down, tangent to the circle, to a tip at the bottom centre.
 *
 * @param quarterTurn A quarter turn, in degrees.
 * @param fullTurn A full turn, in degrees.
 */
internal fun dropletPath(size: Size, quarterTurn: Float, fullTurn: Float): Path {
  val radius = size.width / 2
  val tipDistance = size.height - radius
  // Angle, at the circle's centre, between the downward direction and each tangent point.
  val tangentAngle = Math.toDegrees(acos(radius / tipDistance).toDouble()).toFloat()
  val circle = Rect(Offset.Zero, Size(size.width, size.width))
  return Path().apply {
    moveTo(radius, size.height)
    // Angles are clockwise from 3 o'clock; start at the left tangent point, go over the top.
    arcTo(circle, quarterTurn + tangentAngle, fullTurn - 2 * tangentAngle, false)
    close()
  }
}

/**
 * Stand-in for the map where the real one cannot load, in the IDE preview and in unit tests.
 * Tapping it reports a map click, like the real map.
 */
@Composable
private fun MapPlaceholder(onMapClick: () -> Unit, modifier: Modifier = Modifier) {
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { onMapClick() }
              .padding(Spacing.large)
  ) {
    Text(
        text = stringResource(R.string.group_session_map_preview_placeholder),
        style = MaterialTheme.typography.titleMedium,
    )
  }
}

/**
 * Reads a plain number declared as a float item in `res/values`, like `integerResource`. It reads
 * through [LocalResources], so it is read again if the configuration changes.
 */
@Composable
internal fun floatResource(@DimenRes id: Int): Float =
    ResourcesCompat.getFloat(LocalResources.current, id)

/** Google Maps positions use the same degrees as [Location]. */
internal fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)
