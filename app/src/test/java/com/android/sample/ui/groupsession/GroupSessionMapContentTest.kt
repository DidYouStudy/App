// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of [GroupSessionMapContent]'s selection and cards, with the real Google map replaced by its
 * stand-in. The stand-in has no pins, so a room is selected through `initialSelectedRoom`; tapping
 * the pins themselves is tested in the instrumented `GroupSessionMapContentRealMapTest`. It also
 * covers [dropletPath], the shape of those pins, which needs no map.
 */
@RunWith(AndroidJUnit4::class)
class GroupSessionMapContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val now = Instant.parse("2030-01-01T09:00:00Z")

  private fun room(name: String) =
      CampusLocation(
          name,
          name.substringBefore(' '),
          CampusLocationType.LECTURE_ROOM,
          0,
          Location(46.52, 6.56),
      )

  private fun item(
      id: String,
      subject: String,
      isCreator: Boolean = false,
      isMember: Boolean = false,
  ) =
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
          isMember = isMember,
          isCreator = isCreator,
      )

  private val aab = room("AAB 0 26")
  private val cm = room("CM 1 121")
  private val bc = room("BC 410")
  private val inm = room("INM 202")
  private val maths = item("s1", "Maths")
  private val physics = item("s2", "Physics")
  private val chemistry = item("s3", "Chemistry")
  private val ownSession = item("s4", "Algebra", isCreator = true)
  // A session the current user has joined but did not create: its card offers Leave.
  private val joinedSession = item("s5", "Biology", isMember = true)
  private val rooms =
      listOf(
          RoomSessions(aab, listOf(maths, physics)),
          RoomSessions(cm, listOf(chemistry)),
          RoomSessions(bc, listOf(ownSession)),
          RoomSessions(inm, listOf(joinedSession)),
      )

  private fun setContent(
      onJoin: (String) -> Unit = {},
      onLeave: (String) -> Unit = {},
      onEdit: (String) -> Unit = {},
      initialSelectedRoom: String? = null,
  ) {
    composeTestRule.setContent {
      // The real Google map cannot load in a unit test; in inspection mode the map view shows a
      // stand-in with the same tap behaviour.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContent(
            rooms = rooms,
            now = now,
            onJoin = onJoin,
            onLeave = onLeave,
            onEdit = onEdit,
            initialSelectedRoom = initialSelectedRoom,
        )
      }
    }
  }

  @Test
  fun content_beforeAnyTap_showsNoList() {
    setContent()

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun content_withAnInitialRoom_showsItsCardsWithoutATap() {
    setContent(initialSelectedRoom = "CM 1 121")

    composeTestRule.onNodeWithText("Chemistry").assertIsDisplayed()
  }

  @Test
  fun content_withAnUnknownInitialRoom_showsNoList() {
    setContent(initialSelectedRoom = "Nowhere")

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun content_withAnInitialRoom_showsThatRoomsCardsOnly() {
    setContent(initialSelectedRoom = "AAB 0 26")

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertIsDisplayed()
    composeTestRule.onNodeWithText("Maths").assertIsDisplayed()
    // The list scrolls: the second card is reached by scrolling.
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Physics"))
    composeTestRule.onNodeWithText("Physics").assertIsDisplayed()
    composeTestRule.onNodeWithText("Chemistry").assertDoesNotExist()
  }

  @Test
  fun tappingTheMap_closesTheList() {
    setContent(initialSelectedRoom = "AAB 0 26")

    composeTestRule.onNodeWithText(MAP_TITLE).performClick()

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun selection_survivesTheStateBeingSavedAndRestored() {
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContent(
            rooms = rooms,
            now = now,
            onJoin = {},
            onLeave = {},
            onEdit = {},
            initialSelectedRoom = "CM 1 121",
        )
      }
    }
    // Closing the list changes the selection away from its initial value.
    composeTestRule.onNodeWithText(MAP_TITLE).performClick()

    restoration.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun joinOnACard_reportsTheSessionId() {
    var joined: String? = null
    setContent(onJoin = { joined = it }, initialSelectedRoom = "CM 1 121")

    composeTestRule.onNodeWithText(JOIN_LABEL).performClick()

    assertEquals("s3", joined)
  }

  /** Leave on a joined session's card reports that session's ID, like Join and Edit do. */
  @Test
  fun leaveOnAJoinedCard_reportsTheSessionId() {
    var left: String? = null
    setContent(onLeave = { left = it }, initialSelectedRoom = "INM 202")

    composeTestRule.onNodeWithText(LEAVE_LABEL).performClick()

    assertEquals("s5", left)
  }

  @Test
  fun editOnTheCreatorsCard_reportsTheSessionId() {
    var edited: String? = null
    setContent(onEdit = { edited = it }, initialSelectedRoom = "BC 410")

    composeTestRule.onNodeWithText(EDIT_LABEL).performClick()

    assertEquals("s4", edited)
  }

  /** The bounds of the [dropletPath] of [size], with the turns in degrees. */
  private fun dropletBounds(size: Size): Rect = dropletPath(size, 90f, 360f).getBounds()

  private fun assertBounds(expected: Rect, actual: Rect, tolerance: Float) {
    assertEquals(expected.left, actual.left, tolerance)
    assertEquals(expected.top, actual.top, tolerance)
    assertEquals(expected.right, actual.right, tolerance)
    assertEquals(expected.bottom, actual.bottom, tolerance)
  }

  @Test
  fun dropletPath_ofTheMarkerSize_fillsItsWholeSize() {
    // The size of the pin on the map: 32 x 40, taller than it is wide.
    val bounds = dropletBounds(Size(32f, 40f))

    // The tip is the lowest point, so the shape ends exactly on the bottom edge.
    assertEquals(40f, bounds.bottom, DROPLET_EXACT)
    // Robolectric bounds an arc loosely (by about 1.7 here), so the other edges only need to be
    // near the size: a NaN or a collapsed shape would still be far off.
    assertBounds(Rect(0f, 0f, 32f, 40f), bounds, DROPLET_ARC_TOLERANCE)
  }

  @Test
  fun dropletPath_ofAShapeNotTallerThanWide_isJustTheCircle() {
    // Equal height and width, a height under the width, a height of half the width (a tip distance
    // of 0, which divides by 0 without the guard), and an empty size: none has room for a tip.
    listOf(Size(32f, 32f), Size(32f, 20f), Size(32f, 16f), Size.Zero).forEach { size ->
      assertBounds(Rect(0f, 0f, size.width, size.width), dropletBounds(size), DROPLET_EXACT)
    }
  }

  private companion object {
    const val MAP_TITLE = "Map (shown on a device only)"
    const val JOIN_LABEL = "Join"
    const val LEAVE_LABEL = "Leave"
    const val EDIT_LABEL = "Edit"
    const val DROPLET_EXACT = 0.01f
    const val DROPLET_ARC_TOLERANCE = 3f
  }
}
