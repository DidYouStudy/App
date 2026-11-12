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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.location.CampusLocationRepositoryProvider
import com.android.sample.model.location.Location
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.google.maps.android.compose.CameraPositionState
import java.time.Instant
import kotlinx.coroutines.runBlocking

/** Test tags of the map content. */
object GroupSessionMapTestTags {
  /** The list of session cards shown at the bottom once a pin is tapped. */
  const val SESSION_LIST = "group_session_map_session_list"
}

/**
 * The map view of the group session screen: a pin per room, and, once a pin is tapped, the cards of
 * the sessions held in that room in a list at the bottom. Tapping the map away from a pin closes
 * the list.
 *
 * The map moves its own camera onto a tapped pin; this composable only remembers which room is
 * selected. When first shown, the camera moves to the campus centre.
 *
 * @param rooms Rooms that have sessions, each with its sessions' list items.
 * @param now Reference time of the cards' relative labels.
 * @param onJoin Called with the session ID when a card's join button is pressed.
 * @param onLeave Called with the session ID when a card's leave button is pressed.
 * @param onEdit Called with the session ID when the creator of a session presses its edit button.
 * @param initialSelectedRoom Name of the room selected when the map first appears, or null for
 *   none. Mainly for tests of the cards, which cannot tap a pin on the stand-in map.
 * @param cameraPositionState Camera of the map, passed on to [GroupSessionMapView] for a caller
 *   that needs to read or move it, such as instrumented tests; by default the map makes its own.
 */
@Composable
fun GroupSessionMapContent(
    rooms: List<RoomSessions>,
    now: Instant,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedRoom: String? = null,
    cameraPositionState: CameraPositionState? = null,
) {
  var selectedRoom by rememberSaveable { mutableStateOf(initialSelectedRoom) }
  val campusRooms = remember(rooms) { rooms.map { it.room } }
  val selected = rooms.firstOrNull { it.room.name == selectedRoom }
  val maxListHeightFraction = floatResource(R.dimen.map_session_list_max_height_fraction)
  val campusCenter =
      Location(
          floatResource(R.dimen.map_campus_center_latitude).toDouble(),
          floatResource(R.dimen.map_campus_center_longitude).toDouble(),
      )

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    GroupSessionMapView(
        rooms = campusRooms,
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
          onEdit = onEdit,
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
    onEdit: (String) -> Unit,
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
        GroupSessionCard(
            item = item,
            now = now,
            onJoin = onJoin,
            onLeave = onLeave,
            onEdit = onEdit,
        )
      }
    }
  }
}

/**
 * Preview of the map content with two sample rooms, each holding sessions. The rooms are the real
 * ones of the bundled campus data, so their names and coordinates are exact.
 *
 * @param cameraPositionState Camera of the map, for tests that need to read or move it.
 */
@Preview(showBackground = true)
@Composable
internal fun GroupSessionMapContentPreview(cameraPositionState: CameraPositionState? = null) {
  val now = Instant.parse("2030-01-01T09:00:00Z")
  val context = LocalContext.current
  // Effects do not run in a static preview, so the bundled rooms are read right here; the file is
  // small.
  val campusRooms = remember {
    runBlocking {
      CampusLocationRepositoryProvider.getRepository(context).getAllLocations().getOrThrow()
    }
        .associateBy { it.name }
  }

  fun room(name: String) = checkNotNull(campusRooms[name]) { "No campus room named $name" }

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

  DidYouStudyTheme {
    GroupSessionMapContent(
        rooms =
            listOf(
                RoomSessions(
                    room(PREVIEW_FIRST_ROOM),
                    listOf(
                        item(
                            "s1",
                            "Linear Algebra",
                            EventType.STUDY_SESSION,
                            PREVIEW_FIRST_ROOM,
                            1,
                        ),
                        item(
                            "s2",
                            "Software Architecture",
                            EventType.PROJECT,
                            PREVIEW_FIRST_ROOM,
                            3,
                        ),
                    ),
                ),
                RoomSessions(
                    room(PREVIEW_SECOND_ROOM),
                    listOf(
                        item(
                            "s3",
                            "Thermodynamics",
                            EventType.EXERCISE_SESSION,
                            PREVIEW_SECOND_ROOM,
                            2,
                        )
                    ),
                ),
            ),
        now = now,
        onJoin = {},
        onLeave = {},
        onEdit = {},
        cameraPositionState = cameraPositionState,
    )
  }
}

/** Names of the two campus rooms the preview shows sessions in. */
internal const val PREVIEW_FIRST_ROOM = "AAB 0 26"
internal const val PREVIEW_SECOND_ROOM = "CM 1 121"

private const val SECONDS_PER_HOUR = 3600L
