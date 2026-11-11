// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import com.android.sample.model.location.SessionMarkers
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.google.maps.android.compose.CameraPositionState
import java.time.Instant

/** Test tags of the map content. */
object GroupSessionMapTestTags {
  /** The list of session cards shown at the bottom once a pin is tapped. */
  const val SESSION_LIST = "group_session_map_session_list"
}

/**
 * The sessions held in one campus room, as the cards need them.
 *
 * @property room Room the sessions take place in.
 * @property items One list item per session, with the current user's membership; never empty.
 */
data class RoomSessions(val room: CampusLocation, val items: List<GroupSessionItem>) {
  init {
    require(items.isNotEmpty()) { "A room must have at least one session" }
  }
}

/**
 * Pairs each marker's room with the list items of its sessions, found by session ID in [items].
 * Markers keep their order, and a marker none of whose sessions is in [items] is dropped.
 */
fun SessionMarkers.toRoomSessions(items: List<GroupSessionItem>): List<RoomSessions> {
  val itemsById = items.associateBy { it.session.id }
  return markers.mapNotNull { marker ->
    val roomItems = marker.sessions.mapNotNull { itemsById[it.id] }
    if (roomItems.isEmpty()) null else RoomSessions(marker.room, roomItems)
  }
}

/**
 * The map view of the group session screen: a pin per room, and, once a pin is tapped, the cards of
 * the sessions held in that room in a list at the bottom. Tapping the map away from a pin closes
 * the list.
 *
 * The map moves its own camera onto a tapped pin; this composable only remembers which room is
 * selected.
 *
 * @param rooms Rooms that have sessions, each with its sessions' list items.
 * @param now Reference time of the cards' relative labels.
 * @param onJoin Called with the session ID when a card's join button is pressed.
 * @param onLeave Called with the session ID when a card's leave button is pressed.
 * @param initialSelectedRoom Name of the room selected when the map first appears, or null for
 *   none. Mainly for previews, which cannot tap a pin.
 * @param cameraPositionState Camera of the map, passed on to [GroupSessionMapView] for a caller
 *   that needs to read or move it, such as instrumented tests; by default the map makes its own.
 */
@Composable
fun GroupSessionMapContent(
    rooms: List<RoomSessions>,
    now: Instant,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedRoom: String? = null,
    cameraPositionState: CameraPositionState? = null,
) {
  var selectedRoom by rememberSaveable { mutableStateOf(initialSelectedRoom) }
  val markers =
      remember(rooms) { rooms.map { MapMarkerUi(it.room.name, it.room.location, it.items.size) } }
  val selected = rooms.firstOrNull { it.room.name == selectedRoom }
  val maxListHeightFraction = floatResource(R.dimen.map_session_list_max_height_fraction)
  val campusCenter =
      Location(
          floatResource(R.dimen.map_campus_center_latitude).toDouble(),
          floatResource(R.dimen.map_campus_center_longitude).toDouble(),
      )

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    GroupSessionMapView(
        markers = markers,
        cameraTarget = campusCenter,
        onMarkerClick = { selectedRoom = it },
        onMapClick = { selectedRoom = null },
        cameraPositionState = cameraPositionState,
    )
    if (selected != null) {
      SessionCardList(
          room = selected,
          now = now,
          onJoin = onJoin,
          onLeave = onLeave,
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .heightIn(max = maxHeight * maxListHeightFraction),
      )
    }
  }
}

/** Sheet at the bottom of the screen listing the cards of a room's sessions. */
@Composable
private fun SessionCardList(
    room: RoomSessions,
    now: Instant,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(topStart = Dimens.cornerLarge, topEnd = Dimens.cornerLarge),
      color = MaterialTheme.colorScheme.surface,
      shadowElevation = Spacing.medium,
  ) {
    LazyColumn(
        modifier = Modifier.testTag(GroupSessionMapTestTags.SESSION_LIST),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      items(items = room.items, key = { it.session.id }) { item ->
        GroupSessionCard(item = item, now = now, onJoin = onJoin, onLeave = onLeave)
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
internal fun GroupSessionMapContentPreview() {
  val now = Instant.parse("2030-01-01T09:00:00Z")

  fun item(id: String, subject: String, type: EventType, room: String, startsInHours: Long) =
      GroupSessionItem(
          session =
              GroupSession(
                  id = id,
                  createdBy = "creator",
                  memberIds = listOf("creator", "member"),
                  details =
                      EventDetails(
                          type = type,
                          subject = subject,
                          startsAt = now.plusSeconds(startsInHours * SECONDS_PER_HOUR),
                          endsAt = now.plusSeconds((startsInHours + 1) * SECONDS_PER_HOUR),
                          location = room,
                      ),
                  createdAt = now,
              ),
          isMember = false,
          isCreator = false,
      )

  fun room(name: String, latitude: Double, longitude: Double) =
      CampusLocation(
          name = name,
          zone = name.substringBefore(' '),
          type = CampusLocationType.LECTURE_ROOM,
          floor = 0,
          location = Location(latitude, longitude),
      )

  DidYouStudyTheme {
    GroupSessionMapContent(
        rooms =
            listOf(
                RoomSessions(
                    room("AAB 0 26", 46.5186, 6.5619),
                    listOf(
                        item("s1", "Linear Algebra", EventType.STUDY_SESSION, "AAB 0 26", 1),
                        item("s2", "Software Architecture", EventType.PROJECT, "AAB 0 26", 3),
                    ),
                ),
                RoomSessions(
                    room("CM 1 121", 46.5202, 6.5652),
                    listOf(item("s3", "Thermodynamics", EventType.EXERCISE_SESSION, "CM 1 121", 2)),
                ),
            ),
        now = now,
        onJoin = {},
        onLeave = {},
        initialSelectedRoom = null,
    )
  }
}

private const val SECONDS_PER_HOUR = 3600L
