// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.groupsession

import android.content.res.Resources
import androidx.annotation.DimenRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
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
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.math.acos
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Map of the campus with one pin per room.
 *
 * It is the only composable that draws Google Maps, so the rest of the screen stays free of map
 * types, apart from the optional camera passed through for tests, and can be tested without the
 * real map. It holds no selection: tapping a pin reports the room to the caller, which decides what
 * to show and drives all camera moves through [cameraDestination].
 *
 * Needs a Maps API key in the app manifest; without one the map stays blank.
 *
 * @param rooms Rooms to draw a pin for, at their coordinates.
 * @param onMarkerClick Called with the room name when a pin is tapped.
 * @param onMapClick Called when the map is tapped away from any pin.
 * @param cameraDestination Destination to move the camera onto whenever it changes; null keeps the
 *   camera where the user left it.
 * @param cameraPositionState Camera of the map, for a caller that needs to read or move it, such as
 *   instrumented tests; by default a camera on [campusCenter] at the default zoom.
 * @param userLocation Current device location of the user, or null if not available or shared.
 * @param hasLocationPermission Whether location permission is granted, enabling the native Google
 *   Maps blue dot for user location.
 * @param campusCenter Default campus centre coordinates used to initialize the camera when
 *   [cameraPositionState] is not supplied; defaults to [defaultCampusCenter] from `map.xml`.
 */
@Composable
fun GroupSessionMapView(
    rooms: List<CampusLocation>,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMapClick: () -> Unit = {},
    cameraDestination: CameraDestination? = null,
    cameraPositionState: CameraPositionState? = null,
    userLocation: Location? = null,
    hasLocationPermission: Boolean = false,
    campusCenter: Location = defaultCampusCenter(),
) {
  // The real map cannot load in the IDE preview, so it shows a stand-in.
  if (LocalInspectionMode.current) {
    MapPlaceholder(onMapClick, modifier, userLocation, hasLocationPermission)
    return
  }

  val defaultZoom = floatResource(R.dimen.map_default_zoom)
  val focusZoom = floatResource(R.dimen.map_focus_zoom)
  val cameraAnimationMs = integerResource(R.integer.map_camera_animation_ms)
  val cameraPositionState =
      cameraPositionState
          ?: rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(campusCenter.toLatLng(), defaultZoom)
          }

  // Moves the camera onto a destination: either resets to default zoom on unfocus, or zooms in on
  // the target if the map is further out than focus zoom.
  val focusOn: suspend (CameraDestination) -> Unit = { destination ->
    val update =
        if (destination.resetZoom) {
          CameraUpdateFactory.zoomTo(defaultZoom)
        } else {
          val zoom = maxOf(cameraPositionState.position.zoom, focusZoom)
          CameraUpdateFactory.newLatLngZoom(destination.location.toLatLng(), zoom)
        }
    try {
      cameraPositionState.animate(update, cameraAnimationMs)
    } catch (_: Exception) {
      // In unit tests or environments without initialized Maps services, ignore camera animation
      // failure.
    }
  }
  val projectionTimeoutMs = integerResource(R.integer.map_projection_timeout_ms).milliseconds
  val projectionPollIntervalMs =
      integerResource(R.integer.map_projection_poll_interval_ms).milliseconds

  LaunchedEffect(cameraDestination) {
    cameraDestination?.let { destination ->
      withTimeoutOrNull(projectionTimeoutMs) {
        while (cameraPositionState.projection == null) {
          delay(projectionPollIntervalMs)
        }
      }
      focusOn(destination)
    }
  }

  // Disable on-screen zoom and default my-location buttons so they do not overlap with floating UI.
  val uiSettings = remember {
    MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
  }
  val mapProperties =
      remember(hasLocationPermission) { MapProperties(isMyLocationEnabled = hasLocationPermission) }

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        onMapClick = { onMapClick() },
    ) {
      rooms.forEach { room ->
        MarkerComposable(
            room.name,
            state = rememberUpdatedMarkerState(position = room.location.toLatLng()),
            onClick = {
              onMarkerClick(room.name)
              // Consumed: the caller moves the camera via cameraDestination instead of Maps'
              // default snap.
              true
            },
        ) {
          MarkerPin()
        }
      }
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
    // The dot sits at the centre of the droplet's circle, which [dropletPath] draws as wide as the
    // shape, at the top: its centre is half a width from the left and from the top. If the circle
    // in [dropletPath] changes, move this dot with it.
    drawCircle(
        Color.White,
        radius = dotDiameter.toPx() / 2,
        center = Offset(size.width / 2, size.width / 2),
    )
  }
}

/**
 * Outline of a droplet that fits [size]: a circle as wide as the shape, with two straight sides
 * running down, tangent to the circle, to a tip at the bottom centre. A shape that is not taller
 * than it is wide has no room for a tip, so it is drawn as just the circle.
 *
 * @param quarterTurn A quarter turn, in degrees.
 * @param fullTurn A full turn, in degrees.
 */
internal fun dropletPath(size: Size, quarterTurn: Float, fullTurn: Float): Path {
  val circle = Rect(Offset.Zero, Size(size.width, size.width))
  // A tangent from the tip to the circle only exists when the tip is outside it, that is when the
  // shape is taller than it is wide. Otherwise there is no tip: draw the circle. This also keeps
  // the `acos` below from getting an argument over 1, or from a division by 0.
  if (size.height <= size.width) return Path().apply { addOval(circle) }
  val radius = size.width / 2
  val tipDistance = size.height - radius
  // Angle, at the circle's centre, between the downward direction and each tangent point.
  val tangentAngle = Math.toDegrees(acos(radius / tipDistance).toDouble()).toFloat()
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
private fun MapPlaceholder(
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
    userLocation: Location? = null,
    hasLocationPermission: Boolean = false,
) {
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { onMapClick() }
              .padding(Spacing.large)
  ) {
    Column {
      Text(
          text = stringResource(R.string.group_session_map_preview_placeholder),
          style = MaterialTheme.typography.titleMedium,
      )
      if (hasLocationPermission || userLocation != null) {
        Text(
            text = stringResource(R.string.group_session_user_location_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(GroupSessionMapTestTags.USER_LOCATION_MARKER),
        )
      }
    }
  }
}

/**
 * Reads a plain number declared as a float item in `res/values`, like `integerResource`. It reads
 * through [LocalResources], so it is read again if the configuration changes.
 */
@Composable
internal fun floatResource(@DimenRes id: Int): Float =
    ResourcesCompat.getFloat(LocalResources.current, id)

/**
 * Reads the default campus centre geographic coordinates from Android [resources], defined in
 * `res/values/map.xml`.
 */
fun defaultCampusCenter(resources: Resources): Location =
    Location(
        ResourcesCompat.getFloat(resources, R.dimen.map_campus_center_latitude).toDouble(),
        ResourcesCompat.getFloat(resources, R.dimen.map_campus_center_longitude).toDouble(),
    )

/**
 * Reads the default campus centre geographic coordinates in Compose from `res/values/map.xml`,
 * reacting to configuration changes via [LocalResources].
 */
@Composable
fun defaultCampusCenter(): Location =
    Location(
        floatResource(R.dimen.map_campus_center_latitude).toDouble(),
        floatResource(R.dimen.map_campus_center_longitude).toDouble(),
    )

/** Google Maps positions use the same degrees as [Location]. */
internal fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)
