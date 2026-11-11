// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import androidx.compose.runtime.CompositionLocalProvider
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
import com.android.sample.model.location.RoomMarker
import com.android.sample.model.location.SessionMarkers
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests of the map view's pins-to-sessions flow, with the real Google map replaced by its stand-in.
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

  private fun item(id: String, subject: String) =
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
          isMember = false,
          isCreator = false,
      )

  private val aab = room("AAB 0 26")
  private val cm = room("CM 1 121")
  private val maths = item("s1", "Maths")
  private val physics = item("s2", "Physics")
  private val chemistry = item("s3", "Chemistry")
  private val rooms =
      listOf(RoomSessions(aab, listOf(maths, physics)), RoomSessions(cm, listOf(chemistry)))

  private fun setPreviewContent() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContentPreview()
      }
    }
  }

  private fun setContent(onJoin: (String) -> Unit = {}, initialSelectedRoom: String? = null) {
    composeTestRule.setContent {
      // The real Google map cannot load in a unit test; in inspection mode the map view shows a
      // stand-in with the same tap behaviour.
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContent(
            rooms = rooms,
            now = now,
            onJoin = onJoin,
            onLeave = {},
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
  fun preview_showsThePlaceholderMapWithAPinLinePerRoom() {
    setPreviewContent()

    composeTestRule.onNodeWithText(MAP_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithText(AAB_PIN).assertIsDisplayed()
    composeTestRule.onNodeWithText(CM_PIN).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun preview_tappingAPinLine_showsTheSampleSessionCardsOfThatRoom() {
    setPreviewContent()

    composeTestRule.onNodeWithText(AAB_PIN).performClick()

    composeTestRule.onNodeWithText("Linear Algebra").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Software Architecture"))
    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    composeTestRule.onNodeWithText("Thermodynamics").assertDoesNotExist()
  }

  @Test
  fun content_givesTheMapOnePinPerRoomWithItsSessionCount() {
    setContent()

    composeTestRule.onNodeWithText(AAB_PIN).assertIsDisplayed()
    composeTestRule.onNodeWithText(CM_PIN).assertIsDisplayed()
  }

  @Test
  fun tappingAPin_showsTheCardsOfThatRoomOnly() {
    setContent()

    composeTestRule.onNodeWithText(AAB_PIN).performClick()

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
  fun tappingAnotherPin_switchesToThatRoomsCards() {
    setContent()
    composeTestRule.onNodeWithText(AAB_PIN).performClick()

    composeTestRule.onNodeWithText(CM_PIN).performClick()

    composeTestRule.onNodeWithText("Chemistry").assertIsDisplayed()
    composeTestRule.onNodeWithText("Maths").assertDoesNotExist()
  }

  @Test
  fun tappingTheMapAwayFromPins_closesTheList() {
    setContent()
    composeTestRule.onNodeWithText(AAB_PIN).performClick()

    composeTestRule.onNodeWithText(MAP_TITLE).performClick()

    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun selectedRoom_survivesTheStateBeingSavedAndRestored() {
    val restoration = StateRestorationTester(composeTestRule)
    restoration.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContent(rooms = rooms, now = now, onJoin = {}, onLeave = {})
      }
    }
    composeTestRule.onNodeWithText(CM_PIN).performClick()

    restoration.emulateSavedInstanceStateRestore()

    composeTestRule.onNodeWithText("Chemistry").assertIsDisplayed()
  }

  @Test
  fun joinOnACard_reportsTheSessionId() {
    var joined: String? = null
    setContent(onJoin = { joined = it })
    composeTestRule.onNodeWithText(CM_PIN).performClick()

    composeTestRule.onNodeWithText(JOIN_LABEL).performClick()

    assertEquals("s3", joined)
  }

  @Test
  fun toRoomSessions_pairsMarkersWithTheirItems() {
    val markers =
        SessionMarkers(
            markers =
                listOf(
                    RoomMarker(cm, listOf(chemistry.session)),
                    RoomMarker(aab, listOf(maths.session, physics.session)),
                ),
            unplacedCount = 0,
        )

    val result = markers.toRoomSessions(listOf(maths, physics, chemistry))

    assertEquals(
        listOf(RoomSessions(cm, listOf(chemistry)), RoomSessions(aab, listOf(maths, physics))),
        result,
    )
  }

  @Test
  fun toRoomSessions_dropsMarkersWhoseSessionsAreNotInTheItems() {
    val markers =
        SessionMarkers(
            markers =
                listOf(
                    RoomMarker(aab, listOf(maths.session)),
                    RoomMarker(cm, listOf(chemistry.session)),
                ),
            unplacedCount = 0,
        )

    val result = markers.toRoomSessions(listOf(chemistry))

    assertEquals(listOf(RoomSessions(cm, listOf(chemistry))), result)
  }

  @Test
  fun roomSessions_withoutItems_isRejected() {
    assertTrue(runCatching { RoomSessions(aab, emptyList()) }.isFailure)
  }

  private companion object {
    const val MAP_TITLE = "Map (shown on a device only)"
    const val AAB_PIN = "AAB 0 26: 2 sessions"
    const val CM_PIN = "CM 1 121: 1 session"
    const val JOIN_LABEL = "Join"
  }
}
