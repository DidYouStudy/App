// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.groupsession

import android.content.Context
import android.content.res.Resources
import androidx.annotation.DimenRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.res.ResourcesCompat
import com.android.sample.R
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.DistanceCalculator
import com.android.sample.model.location.Location
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.math.acos
import kotlin.math.cos
import kotlin.time.Duration
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
 * @param campusCenter Default campus center coordinates used to initialize the camera when
 *   [cameraPositionState] is not supplied; defaults to [defaultCampusCenter] from `map.xml`.
 * @param filterRadiusMeters Active distance filter radius in metres, or null when no distance
 *   filter is applied.
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
    campusCenter: Location? = null,
    filterRadiusMeters: Int? = null,
) {
  // Remember the last known location while permission is granted so transient null emissions
  // keep the circle, visible pins, and camera-fit effect stable without flickering.
  val lastKnownLocation = remember { arrayOfNulls<Location>(1) }
  val stableUserLocation =
      resolveStableLocation(lastKnownLocation[0], userLocation, hasLocationPermission)
  lastKnownLocation[0] = stableUserLocation
  val activeFilterArea = filterArea(stableUserLocation, filterRadiusMeters)

  // The real map cannot load in the IDE preview, so it shows a stand-in.
  if (LocalInspectionMode.current) {
    MapPlaceholder(
        onMapClick,
        modifier,
        stableUserLocation,
        hasLocationPermission,
        activeFilterArea,
    )
    return
  }

  val resolvedCampusCenter = (campusCenter ?: defaultCampusCenter()).toLatLng()
  val defaultZoom = floatResource(R.dimen.map_default_zoom)
  val focusZoom = floatResource(R.dimen.map_focus_zoom)
  val cameraAnimationMs = integerResource(R.integer.map_camera_animation_ms)
  val cameraPositionState =
      cameraPositionState
          ?: rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(resolvedCampusCenter, defaultZoom)
          }

  // Moves the camera onto a place, zooming in if the map is further out than the focus zoom.
  val focusOn: suspend (Location) -> Unit = { target ->
    val zoom = maxOf(cameraPositionState.position.zoom, focusZoom)
    try {
      cameraPositionState.animate(
          CameraUpdateFactory.newLatLngZoom(target.toLatLng(), zoom),
          cameraAnimationMs,
      )
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
      awaitMapProjection(cameraPositionState, projectionTimeoutMs, projectionPollIntervalMs)
      focusOn(destination.location)
    }
  }

  val visibleRooms =
      remember(rooms, activeFilterArea) { roomsWithinFilterArea(rooms, activeFilterArea) }
  val activeRadius = activeFilterArea?.radiusMeters
  val cameraPaddingPx =
      with(LocalDensity.current) { Dimens.mapFilterRadiusCameraPadding.roundToPx() }
  val latestFilterArea by rememberUpdatedState(activeFilterArea)
  val resources = LocalResources.current

  LaunchedEffect(activeRadius) {
    val area = latestFilterArea ?: return@LaunchedEffect
    awaitMapProjection(cameraPositionState, projectionTimeoutMs, projectionPollIntervalMs)
    cameraPositionState.animate(
        CameraUpdateFactory.newLatLngBounds(circleBounds(area, resources), cameraPaddingPx),
        cameraAnimationMs,
    )
  }

  // Disable on-screen zoom and default my-location buttons so they do not overlap with floating UI.
  val uiSettings = remember {
    MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
  }
  val mapProperties =
      remember(hasLocationPermission) { MapProperties(isMyLocationEnabled = hasLocationPermission) }
  val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)

  Box(modifier = modifier.fillMaxSize()) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = uiSettings,
        onMapClick = { onMapClick() },
    ) {
      activeFilterArea?.let { area -> FilterRadiusOverlay(area = area, onMapClick = onMapClick) }
      visibleRooms.forEach { room ->
        key(room.name) {
          val onPinClick =
              remember(room.name) {
                { _: Marker ->
                  currentOnMarkerClick(room.name)
                  // Consumed: the caller moves the camera via cameraDestination instead of Maps'
                  // default snap.
                  true
                }
              }
          MarkerComposable(
              room.name,
              state = rememberUpdatedMarkerState(position = room.location.toLatLng()),
              onClick = onPinClick,
          ) {
            MarkerPin()
          }
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

/** Waits up to [timeout] for the map's projection to become ready before animating the camera. */
private suspend fun awaitMapProjection(
    cameraPositionState: CameraPositionState,
    timeout: Duration,
    pollInterval: Duration,
) {
  withTimeoutOrNull(timeout) {
    while (cameraPositionState.projection == null) {
      delay(pollInterval)
    }
  }
}

/**
 * Draws the translucent distance-filter circle and its southern-edge pill label on the map. Drawn
 * before room pins so pins stay above it; tapping the label delegates to [onMapClick].
 */
@Composable
@GoogleMapComposable
private fun FilterRadiusOverlay(area: FilterArea, onMapClick: () -> Unit) {
  val bounds = circleBounds(area, LocalResources.current)
  val fillAlpha = floatResource(R.dimen.map_filter_radius_fill_alpha)
  val centerAnchor = floatResource(R.dimen.map_center_anchor)
  val strokeWidthPx = with(LocalDensity.current) { Dimens.mapFilterRadiusStrokeWidth.toPx() }
  val primaryColor = MaterialTheme.colorScheme.primary
  val fillColor = remember(primaryColor, fillAlpha) { primaryColor.copy(alpha = fillAlpha) }
  val currentOnMapClick by rememberUpdatedState(onMapClick)
  val onLabelClick = remember {
    { _: Marker ->
      currentOnMapClick()
      true
    }
  }

  Circle(
      center = area.center.toLatLng(),
      radius = area.radiusMeters.toDouble(),
      fillColor = fillColor,
      strokeColor = primaryColor,
      strokeWidth = strokeWidthPx,
      clickable = false,
  )
  MarkerComposable(
      "filter_radius_label",
      state =
          rememberUpdatedMarkerState(
              position = LatLng(bounds.southwest.latitude, area.center.longitude),
          ),
      anchor = Offset(centerAnchor, centerAnchor),
      onClick = onLabelClick,
  ) {
    FilterRadiusLabel()
  }
}

/** Pill label shown at the bottom edge of the active distance-filter circle. */
@Composable
private fun FilterRadiusLabel(modifier: Modifier = Modifier) {
  Surface(
      modifier = modifier,
      shape = RoundedCornerShape(Dimens.cornerLarge),
      color = MaterialTheme.colorScheme.surfaceContainerLowest,
      border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.primary),
  ) {
    Text(
        text = stringResource(R.string.group_session_filter_radius_label),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.large, vertical = Spacing.small),
    )
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
    filterArea: FilterArea? = null,
) {
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { onMapClick() }
              .padding(Spacing.large)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
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
      if (filterArea != null) {
        Text(
            text =
                stringResource(
                    R.string.group_session_filter_radius_placeholder,
                    filterArea.radiusMeters,
                ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(GroupSessionMapTestTags.FILTER_RADIUS_CIRCLE),
        )
        FilterRadiusLabel()
      }
    }
  }
}

/** Area covered by the active distance filter around the user's position. */
internal data class FilterArea(val center: Location, val radiusMeters: Int)

/**
 * Returns the last known [Location] while [hasLocationPermission] is granted so transient `null`
 * emissions do not drop the user's position; returns [current] directly when permission is revoked.
 */
internal fun resolveStableLocation(
    lastKnown: Location?,
    current: Location?,
    hasLocationPermission: Boolean,
): Location? = if (hasLocationPermission) current ?: lastKnown else current

/**
 * Returns the [FilterArea] to draw on the map, or null when no distance filter is active (either
 * [userLocation] or [radiusMeters] is missing, or [radiusMeters] is non-positive).
 */
internal fun filterArea(userLocation: Location?, radiusMeters: Int?): FilterArea? {
  if (userLocation == null || radiusMeters == null || radiusMeters <= 0) return null
  return FilterArea(center = userLocation, radiusMeters = radiusMeters)
}

/**
 * Filters [rooms] to those inside or on the boundary of [area], or returns [rooms] unchanged when
 * [area] is null. Keeps pins in sync with the drawn circle even across transient `null` locations
 * or two-step state updates.
 */
internal fun roomsWithinFilterArea(
    rooms: List<CampusLocation>,
    area: FilterArea?,
): List<CampusLocation> {
  if (area == null) return rooms
  return rooms.filter { room ->
    DistanceCalculator.distanceMeters(area.center, room.location) <= area.radiusMeters
  }
}

/**
 * Returns the geographic [LatLngBounds] enclosing [area], with longitude deltas scaled by the
 * cosine of the center latitude, using Earth radius and clamp constants from [resources].
 */
internal fun circleBounds(area: FilterArea, resources: Resources): LatLngBounds {
  val earthRadiusMeters =
      ResourcesCompat.getFloat(resources, R.dimen.map_earth_radius_meters).toDouble()
  val minCosLatitude = ResourcesCompat.getFloat(resources, R.dimen.map_min_cos_latitude).toDouble()
  val latDelta = Math.toDegrees(area.radiusMeters / earthRadiusMeters)
  val cosLat = cos(Math.toRadians(area.center.latitude)).coerceAtLeast(minCosLatitude)
  val lngDelta = Math.toDegrees(area.radiusMeters / (earthRadiusMeters * cosLat))
  val southWest = LatLng(area.center.latitude - latDelta, area.center.longitude - lngDelta)
  val northEast = LatLng(area.center.latitude + latDelta, area.center.longitude + lngDelta)
  return LatLngBounds(southWest, northEast)
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
 * Reads the default campus centre geographic coordinates from [context], defined in
 * `res/values/map.xml`.
 */
fun defaultCampusCenter(context: Context): Location = defaultCampusCenter(context.resources)

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
