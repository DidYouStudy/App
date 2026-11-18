// Co-authored-by: OpenAI Codex
package com.android.sample.ui.groupsession

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the real Material sheet gesture and scrolling on Android. */
@RunWith(AndroidJUnit4::class)
class GroupSessionParticipantsSheetTest {
  @get:Rule val composeTestRule = createComposeRule()

  /** A short roster still has a distinct expanded position. */
  @Test
  fun swipeUpExpandsShortRoster() {
    showParticipants(count = 2)
    swipeUpToExpand()
    composeTestRule.onNodeWithText("Member 2").assertIsDisplayed()
  }

  /** Expanding a long roster leaves the list independently scrollable. */
  @Test
  fun swipeUpExpandsLongRosterAndListCanScroll() {
    showParticipants(count = 28)
    swipeUpToExpand()
    composeTestRule
        .onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag(GroupSessionParticipantsTestTags.SHEET))
        )
        .performScrollToNode(hasText("Member 28"))
    composeTestRule.onNodeWithText("Member 28").assertIsDisplayed()
  }

  /** Renders a deterministic roster without requiring application auth or Firestore. */
  private fun showParticipants(count: Int) {
    val state =
        GroupSessionParticipantsSheet(
            sessionSubject = "Physics",
            participantCount = count,
            rows =
                List(count) { index ->
                  GroupSessionParticipant(
                      userId = "id-${index + 1}",
                      username = "Member ${index + 1}",
                      isHost = index == 0,
                      isCurrentUser = false,
                  )
                },
            isLoading = false,
        )
    composeTestRule.setContent { GroupSessionParticipantsSheet(state = state, onDismiss = {}) }
  }

  /** Drags the standard handle and checks that the sheet actually moves to the upper anchor. */
  private fun swipeUpToExpand() {
    val sheet = composeTestRule.onNodeWithTag(GroupSessionParticipantsTestTags.SHEET)
    val partialTop = sheet.fetchSemanticsNode().boundsInRoot.top
    assertTrue("The sheet should start partway up the display", partialTop > 0f)

    composeTestRule
        .onNodeWithTag(GroupSessionParticipantsTestTags.DRAG_HANDLE, useUnmergedTree = true)
        .performTouchInput {
          swipe(start = center, end = Offset(center.x, center.y - partialTop * 0.9f))
        }

    assertTrue(
        "The swipe should move the sheet to the expanded position",
        sheet.fetchSemanticsNode().boundsInRoot.top < partialTop / 2,
    )
  }
}
