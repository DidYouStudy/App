// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Gemini <noreply@google.com>
// Co-authored-by: OpenAI Codex
package com.android.sample.ui.groupsession

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.model.location.CampusLocation
import com.android.sample.model.location.CampusLocationFilter
import com.android.sample.model.location.CampusLocationRepository
import com.android.sample.model.location.CampusLocationType
import com.android.sample.model.location.Location
import com.android.sample.model.location.LocationProvider
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import com.android.sample.ui.navigation.NavigationTestTags
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class GroupSessionScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()

  // Base instant captured when test class is instantiated and shifted forward
  // so group session end times stay in the future regardless of test execution date.
  private val testInstant = Instant.now()

  private val repo =
      FakeGroupSessionsRepository(
          initialSessions =
              listOf(
                  GroupSession(
                      id = "s1",
                      createdBy = "user-1",
                      memberIds = listOf("user-1", "user-2"),
                      details =
                          EventDetails(
                              type = EventType.STUDY_SESSION,
                              subject = "Software Architecture",
                              location = "BC 01",
                              startsAt = testInstant.plus(Duration.ofHours(1)),
                              endsAt = testInstant.plus(Duration.ofHours(2)),
                          ),
                      createdAt = testInstant.minus(Duration.ofHours(1)),
                  ),
                  GroupSession(
                      id = "s2",
                      createdBy = "user-3",
                      memberIds = listOf("user-3"),
                      details =
                          EventDetails(
                              type = EventType.PROJECT,
                              subject = "Capstone planning",
                              startsAt = testInstant.plus(Duration.ofHours(3)),
                              endsAt = testInstant.plus(Duration.ofHours(4)),
                          ),
                      createdAt = testInstant.minus(Duration.ofHours(1)),
                  ),
                  GroupSession(
                      id = "s3",
                      createdBy = "user-3",
                      memberIds = listOf("user-3", "user-1"),
                      details =
                          EventDetails(
                              type = EventType.EXAM,
                              subject = "Exam revision",
                              startsAt = testInstant.plus(Duration.ofHours(5)),
                              endsAt = testInstant.plus(Duration.ofHours(6)),
                          ),
                      createdAt = testInstant.minus(Duration.ofHours(1)),
                  ),
                  GroupSession(
                      id = "s4",
                      createdBy = "user-4",
                      memberIds = listOf("user-4", "user-5", "user-6"),
                      details =
                          EventDetails(
                              type = EventType.OTHER,
                              subject = null,
                              description = null,
                              location = null,
                              startsAt = testInstant.plus(Duration.ofHours(7)),
                              endsAt = testInstant.plus(Duration.ofHours(8)),
                          ),
                      createdAt = testInstant.minus(Duration.ofHours(1)),
                  ),
              )
      )

  private val authRepository = FakeAuthRepository("user-1")
  private val profileRepository =
      FakeProfileRepository(
          mapOf(
              "user-1" to UserProfile(uid = "user-1", username = "Alice"),
              "user-3" to UserProfile(uid = "user-3", username = "Bob"),
          )
      )

  @Before
  fun resetRepo() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application)
        .denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    repo.reset()
    authRepository.currentUser.value = UserAccount("user-1", null, "Alice", null)
  }

  @Test
  fun screen_renders_title_and_filters_joined_sessions() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_active_sessions_title))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Software Architecture"))
    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Capstone planning"))
    composeTestRule.onNodeWithText("Capstone planning").assertIsDisplayed()

    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_filter_my))
        .performClick()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Software Architecture"))
    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    composeTestRule.onNodeWithText("Capstone planning").assertDoesNotExist()
  }

  /** The list pill opens the roster and the close button returns to the list. */
  @Test
  fun participants_pill_in_list_opens_and_closes_roster() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      viewModel.uiState.value.sessions.isNotEmpty()
    }

    composeTestRule
        .onAllNodesWithTag(GroupSessionScreenTestTags.PARTICIPANTS_PILL)[0]
        .assert(hasContentDescription("View 2 participants"))
        .performClick()

    composeTestRule.onNodeWithTag(GroupSessionParticipantsTestTags.SHEET).assertIsDisplayed()
    assertRosterContent()
    composeTestRule.onNodeWithTag(GroupSessionParticipantsTestTags.CLOSE).performClick()
    composeTestRule.onNodeWithTag(GroupSessionParticipantsTestTags.SHEET).assertDoesNotExist()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).assertIsDisplayed()
  }

  /** Both card paths present the same roster for the selected session. */
  @Test
  fun participants_pill_in_map_room_opens_roster() {
    val room =
        CampusLocation(
            name = "BC 01",
            zone = "BC",
            type = CampusLocationType.LECTURE_ROOM,
            floor = 0,
            location = Location(46.519, 6.565),
        )
    val viewModel =
        createViewModel(campusLocationRepository = FakeCampusLocationRepository(listOf(room)))
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.rooms.isNotEmpty() }

    // The room link takes the test to the same map cards shown after a room pin is selected.
    composeTestRule.onNodeWithText("BC 01").performClick()
    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.PARTICIPANTS_PILL)
        .performScrollTo()
        .assertIsDisplayed()
        .assert(hasContentDescription("View 2 participants"))
        .performClick()

    composeTestRule.onNodeWithTag(GroupSessionParticipantsTestTags.SHEET).assertIsDisplayed()
    assertRosterContent()
    composeTestRule.onNodeWithTag(GroupSessionParticipantsTestTags.CLOSE).performClick()
    composeTestRule.onNodeWithTag(GroupSessionParticipantsTestTags.SHEET).assertDoesNotExist()
    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertIsDisplayed()
  }

  /** Search matches names without case or outer whitespace and can restore the whole roster. */
  @Test
  fun participants_search_filters_the_open_roster() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }
    val card = hasTestTag(GroupSessionScreenTestTags.sessionItem(2))
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).performScrollToNode(card)
    composeTestRule
        .onNode(hasTestTag(GroupSessionScreenTestTags.PARTICIPANTS_PILL) and hasAnyAncestor(card))
        .performClick()

    expandParticipantsSheet()
    val sheet = hasAnyAncestor(hasTestTag(GroupSessionParticipantsTestTags.SHEET))
    val bob = composeTestRule.onNode(hasText("Bob") and sheet)
    val you = composeTestRule.onNode(hasText("You") and sheet)
    val search = composeTestRule.onNodeWithTag(GroupSessionParticipantsTestTags.SEARCH)
    bob.assertIsDisplayed()
    you.assertIsDisplayed()

    search.performTextReplacement("  bOB  ")
    bob.assertIsDisplayed()
    you.assertDoesNotExist()
    composeTestRule.onNode(hasText("Participants (2)") and sheet).assertIsDisplayed()

    search.performTextReplacement("you")
    you.assertIsDisplayed()
    bob.assertDoesNotExist()

    search.performTextReplacement("missing")
    composeTestRule
        .onNode(
            hasText(context.getString(R.string.group_session_participants_no_matches)) and sheet
        )
        .assertIsDisplayed()

    search.performTextReplacement("")
    bob.assertIsDisplayed()
    you.assertIsDisplayed()
  }

  /** Opens the expanded sheet so roster assertions do not depend on available screen height. */
  private fun expandParticipantsSheet() {
    composeTestRule
        .onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand), useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.Expand)
  }

  /** Verifies the session subject and the signed-in host’s label and avatar. */
  private fun assertRosterContent() {
    expandParticipantsSheet()
    composeTestRule.onAllNodesWithTag(GroupSessionParticipantsTestTags.AVATAR).assertCountEquals(2)
    composeTestRule
        .onNodeWithTag(GroupSessionParticipantsTestTags.SHEET)
        .assert(hasAnyDescendant(hasText("Software Architecture")))
        .assert(
            hasAnyDescendant(hasText(context.getString(R.string.group_session_participants_you)))
        )
        .assert(hasAnyDescendant(hasText("A")))
        .assert(
            hasAnyDescendant(hasText(context.getString(R.string.group_session_participants_host)))
        )
        .assert(
            hasAnyDescendant(
                hasText(context.getString(R.string.group_session_participants_unknown))
            )
        )
  }

  @Test
  fun add_group_session_button_is_displayed() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ADD_BUTTON).assertIsDisplayed()
  }

  @Test
  fun add_group_session_button_click_triggers_callback() {
    var addClicked = false
    val viewModel = createViewModel()
    composeTestRule.setContent {
      GroupSessionScreen(
          viewModel = viewModel,
          onAddSessionClick = { addClicked = true },
      )
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ADD_BUTTON).performClick()
    assertTrue(addClicked)
  }

  @Test
  fun default_add_session_click_navigates_to_add_group_session() {
    val navActions = org.mockito.kotlin.mock<com.android.sample.ui.navigation.NavigationActions>()
    val viewModel = createViewModel()
    composeTestRule.setContent {
      GroupSessionScreen(
          navigationActions = navActions,
          viewModel = viewModel,
      )
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ADD_BUTTON).performClick()
    org.mockito.kotlin
        .verify(navActions)
        .navigateTo(com.android.sample.ui.navigation.Screen.AddGroupSession)
  }

  @Test
  fun edit_group_session_button_click_triggers_callback() {
    var editedSessionId: String? = null
    val viewModel = createViewModel()
    composeTestRule.setContent {
      GroupSessionScreen(
          viewModel = viewModel,
          onEditSessionClick = { id -> editedSessionId = id },
      )
    }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasTestTag(GroupSessionScreenTestTags.sessionItem(0)))
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_action_edit))
        .performClick()

    assertEquals("s1", editedSessionId)
  }

  @Test
  fun default_edit_session_click_navigates_to_edit_group_session() {
    val navActions = org.mockito.kotlin.mock<com.android.sample.ui.navigation.NavigationActions>()
    val viewModel = createViewModel()
    composeTestRule.setContent {
      GroupSessionScreen(
          navigationActions = navActions,
          viewModel = viewModel,
      )
    }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasTestTag(GroupSessionScreenTestTags.sessionItem(0)))
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_action_edit))
        .performClick()

    org.mockito.kotlin
        .verify(navActions)
        .navigateTo(
            com.android.sample.ui.navigation.Screen.EditEvent(
                "s1",
                com.android.sample.ui.eventplanning.EventKind.GROUP_SESSION,
            )
        )
  }

  @Test
  fun host_sees_edit_action_without_join_or_leave_actions() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasTestTag(GroupSessionScreenTestTags.sessionItem(0)))
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.sessionItem(0))
        .assert(hasAnyDescendant(hasText(context.getString(R.string.group_session_host_self))))
        .assert(hasAnyDescendant(hasText(context.getString(R.string.group_session_action_edit))))
        .assert(!hasAnyDescendant(hasText(context.getString(R.string.group_session_action_join))))
        .assert(!hasAnyDescendant(hasText(context.getString(R.string.group_session_action_leave))))
  }

  @Test
  fun joined_filter_keeps_hosted_and_joined_sessions_only() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_filter_my))
        .performClick()

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Software Architecture"))
    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Exam revision"))
    composeTestRule.onNodeWithText("Exam revision").assertIsDisplayed()
    composeTestRule.onNodeWithText("Capstone planning").assertDoesNotExist()
  }

  @Test
  fun nullable_session_fields_use_readable_fallbacks() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasTestTag(GroupSessionScreenTestTags.sessionItem(3)))
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.sessionItem(3))
        .assert(
            hasAnyDescendant(hasText(context.getString(R.string.group_session_subject_fallback)))
        )
        .assert(
            hasAnyDescendant(
                hasText(context.getString(R.string.group_session_description_fallback))
            )
        )
        .assert(
            hasAnyDescendant(hasText(context.getString(R.string.group_session_location_fallback)))
        )
        .assert(hasAnyDescendant(hasContentDescription("View 3 participants")))
    // Verify the total also appears visually inside the button.
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.sessionItem(3), useUnmergedTree = true)
        .assert(hasAnyDescendant(hasText("3")))
  }

  @Test
  fun empty_repository_shows_empty_state() {
    val emptyRepository = FakeGroupSessionsRepository(emptyList())
    val viewModel = createViewModel(emptyRepository)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_empty))
        .assertIsDisplayed()
  }

  @Test
  fun loading_state_is_displayed_while_loading() {
    val neverEndingRepo =
        object : GroupSessionsRepository {
          override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
              flow {
                awaitCancellation()
              }

          override fun newSessionId(): String = "id"

          override suspend fun getSession(sessionId: String): Result<GroupSession> =
              Result.failure(RuntimeException())

          override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
              Result.failure(RuntimeException())

          override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
              Result.failure(RuntimeException())

          override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> =
              Result.failure(RuntimeException())

          override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> =
              Result.failure(RuntimeException())
        }
    val viewModel = createViewModel(neverEndingRepo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_loading))
        .assertIsDisplayed()
  }

  @Test
  fun search_and_join_leave_actions_work() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    val searchPlaceholder = context.getString(R.string.group_session_search_placeholder)
    val joinText = context.getString(R.string.group_session_action_join)
    val joinedText = context.getString(R.string.group_session_action_joined)
    val leaveText = context.getString(R.string.group_session_action_leave)

    composeTestRule.onNodeWithText(searchPlaceholder).performTextInput("capstone")
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Capstone planning"))
    composeTestRule.onNodeWithText("Capstone planning").assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText(joinText))
    composeTestRule.onNodeWithText(joinText).performClick()
    composeTestRule.waitUntil(timeoutMillis = 2_000) { repo.joinCalls == 1 }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText(joinedText))
    composeTestRule.onNodeWithText(joinedText).assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText(leaveText))
    composeTestRule.onNodeWithText(leaveText).performClick()
    composeTestRule.waitUntil(timeoutMillis = 2_000) { repo.leaveCalls == 1 }
  }

  /** Tagged Join and Leave buttons keep their labels and call the matching callback. */
  @Test
  fun cardButtons_showLabelsAndCallCallbacksWhenNotInProgress() {
    val joined = mutableListOf<String>()
    val left = mutableListOf<String>()
    setCardContent(
        items = listOf(screenItem("join"), screenItem("leave", isMember = true)),
        onJoin = joined::add,
        onLeave = left::add,
    )
    val sessionList = composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
    val joinText = context.getString(R.string.group_session_action_join)
    val leaveText = context.getString(R.string.group_session_action_leave)

    sessionList.performScrollToNode(hasText("Subject join"))
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.joinButton("join"))
        .assert(hasText(joinText))
        .performClick()
    assertEquals(listOf("join"), joined)

    sessionList.performScrollToNode(hasTestTag(GroupSessionScreenTestTags.leaveButton("leave")))
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.leaveButton("leave"))
        .assert(hasText(leaveText))
        .performClick()
    assertEquals(listOf("leave"), left)
  }

  /** A pending join shows progress without Join text and does not invoke onJoin when tapped. */
  @Test
  fun card_pendingJoinShowsProgressAndIgnoresTap() {
    val joined = mutableListOf<String>()
    setCardContent(
        items = listOf(screenItem("busy-join", isActionInProgress = true)),
        onJoin = joined::add,
    )
    val sessionList = composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
    sessionList.performScrollToNode(hasText("Subject busy-join"))

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.joinButton("busy-join"))
        .assert(
            hasAnyDescendant(hasTestTag(GroupSessionScreenTestTags.actionProgress("busy-join")))
        )
        .performTouchInput { click() }
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.actionProgress("busy-join"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_action_join))
        .assertDoesNotExist()
    assertEquals(emptyList<String>(), joined)
  }

  /** A pending leave shows progress without Leave text and does not invoke onLeave when tapped. */
  @Test
  fun card_pendingLeaveShowsProgressAndIgnoresTap() {
    val left = mutableListOf<String>()
    setCardContent(
        items = listOf(screenItem("busy-leave", isMember = true, isActionInProgress = true)),
        onLeave = left::add,
    )
    val sessionList = composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
    sessionList.performScrollToNode(hasText("Subject busy-leave"))

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.leaveButton("busy-leave"))
        .assert(
            hasAnyDescendant(hasTestTag(GroupSessionScreenTestTags.actionProgress("busy-leave")))
        )
        .performTouchInput { click() }
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.actionProgress("busy-leave"))
        .assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_action_leave))
        .assertDoesNotExist()
    assertEquals(emptyList<String>(), left)
  }

  /** A pending join button exposes its disabled state to accessibility services. */
  @Test
  fun card_pendingJoinIsDisabledForAccessibility() {
    setCardContent(items = listOf(screenItem("busy-join", isActionInProgress = true)))
    val sessionList = composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
    val joinButton = GroupSessionScreenTestTags.joinButton("busy-join")
    sessionList.performScrollToNode(hasTestTag(joinButton))

    composeTestRule.onNodeWithTag(joinButton).assertIsNotEnabled()
  }

  /** A pending leave button exposes its disabled state to accessibility services. */
  @Test
  fun card_pendingLeaveIsDisabledForAccessibility() {
    setCardContent(
        items = listOf(screenItem("busy-leave", isMember = true, isActionInProgress = true)),
    )
    val sessionList = composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
    val leaveButton = GroupSessionScreenTestTags.leaveButton("busy-leave")
    sessionList.performScrollToNode(hasTestTag(leaveButton))

    composeTestRule.onNodeWithTag(leaveButton).assertIsNotEnabled()
  }

  /** Idle Join and Leave buttons remain enabled. */
  @Test
  fun card_idleButtonsAreEnabled() {
    setCardContent(
        items = listOf(screenItem("idle-join"), screenItem("idle-leave", isMember = true))
    )
    val sessionList = composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
    val joinButton = GroupSessionScreenTestTags.joinButton("idle-join")
    sessionList.performScrollToNode(hasTestTag(joinButton))
    composeTestRule.onNodeWithTag(joinButton).assertIsEnabled()

    val leaveButton = GroupSessionScreenTestTags.leaveButton("idle-leave")
    sessionList.performScrollToNode(hasTestTag(leaveButton))
    composeTestRule.onNodeWithTag(leaveButton).assertIsEnabled()
  }

  /** The other card remains actionable while one card is showing leave progress. */
  @Test
  fun card_otherButtonStillWorksWhileOneCardIsInProgress() {
    val joined = mutableListOf<String>()
    setCardContent(
        items =
            listOf(
                screenItem("busy-leave", isMember = true, isActionInProgress = true),
                screenItem("available-join"),
            ),
        onJoin = joined::add,
    )
    val sessionList = composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
    sessionList.performScrollToNode(
        hasTestTag(GroupSessionScreenTestTags.joinButton("available-join")),
    )
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.joinButton("available-join"))
        .performClick()
    assertEquals(listOf("available-join"), joined)

    sessionList.performScrollToNode(hasText("Subject busy-leave"))
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.actionProgress("busy-leave"))
        .assertIsDisplayed()
  }

  /** Join failures expose only the existing Dismiss snackbar action, never Retry. */
  @Test
  fun joinFailureSnackbar_hasDismissAndNoRetry() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }
    repo.failJoin = true

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText(context.getString(R.string.group_session_action_join)))
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_action_join))
        .performClick()
    composeTestRule.waitUntil(timeoutMillis = 2_000) { repo.joinCalls == 1 }
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      composeTestRule
          .onAllNodesWithText(context.getString(R.string.group_session_error_join))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.ERROR_SNACKBAR)
        .assert(hasAnyDescendant(hasText(context.getString(R.string.group_session_error_dismiss))))
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ERROR_DISMISS).assertIsDisplayed()
    assertEquals(0, composeTestRule.onAllNodesWithText("Retry").fetchSemanticsNodes().size)
  }

  @Test
  fun failed_join_shows_dismissible_error_message() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    repo.failJoin = true
    val searchPlaceholder = context.getString(R.string.group_session_search_placeholder)
    val joinText = context.getString(R.string.group_session_action_join)
    val joinErrorText = context.getString(R.string.group_session_error_join)

    composeTestRule.onNodeWithText(searchPlaceholder).performTextInput("capstone")
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText(joinText))
    composeTestRule.onNodeWithText(joinText).performClick()
    composeTestRule.waitUntil(timeoutMillis = 2_000) { repo.joinCalls == 1 }
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      composeTestRule.onAllNodesWithText(joinErrorText).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.ERROR_SNACKBAR)
        .assert(hasAnyDescendant(hasText(joinErrorText)))
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ERROR_DISMISS).performClick()
    composeTestRule.onNodeWithText(joinErrorText).assertDoesNotExist()
  }

  @Test
  fun failed_leave_shows_dismissible_error_message() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    repo.failLeave = true
    val leaveText = context.getString(R.string.group_session_action_leave)
    val leaveErrorText = context.getString(R.string.group_session_error_leave)

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText(leaveText))
    composeTestRule.onNodeWithText(leaveText).performClick()
    composeTestRule.waitUntil(timeoutMillis = 2_000) { repo.leaveCalls == 1 }
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      composeTestRule.onAllNodesWithText(leaveErrorText).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.ERROR_SNACKBAR)
        .assert(hasAnyDescendant(hasText(leaveErrorText)))
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ERROR_DISMISS).performClick()
    composeTestRule.onNodeWithText(leaveErrorText).assertDoesNotExist()
  }

  @Test
  fun failed_load_shows_dismissible_error_message() {
    val failingRepo =
        object : GroupSessionsRepository {
          override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
              flowOf(Result.failure(RuntimeException("load failed")))

          override fun newSessionId(): String = "id"

          override suspend fun getSession(sessionId: String): Result<GroupSession> =
              Result.failure(RuntimeException())

          override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
              Result.failure(RuntimeException())

          override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
              Result.failure(RuntimeException())

          override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> =
              Result.failure(RuntimeException())

          override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> =
              Result.failure(RuntimeException())
        }
    val viewModel = createViewModel(failingRepo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    val loadErrorText = context.getString(R.string.group_session_error_load)
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      composeTestRule.onAllNodesWithText(loadErrorText).fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.ERROR_SNACKBAR)
        .assert(hasAnyDescendant(hasText(loadErrorText)))
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ERROR_DISMISS).performClick()
    composeTestRule.onNodeWithText(loadErrorText).assertDoesNotExist()
  }

  @Test
  fun session_status_label_formats_durations_correctly() {
    var startedLabel = ""
    var minLabel = ""
    var hourOneLabel = ""
    var hoursOtherLabel = ""
    var dayOneLabel = ""
    var daysOtherLabel = ""

    composeTestRule.setContent {
      val now = Instant.parse("2026-10-29T12:00:00Z")
      startedLabel = sessionStatusLabel(start = now.minus(Duration.ofMinutes(10)), now = now)
      minLabel = sessionStatusLabel(start = now.plus(Duration.ofMinutes(59)), now = now)
      hourOneLabel = sessionStatusLabel(start = now.plus(Duration.ofHours(1)), now = now)
      hoursOtherLabel = sessionStatusLabel(start = now.plus(Duration.ofHours(5)), now = now)
      dayOneLabel = sessionStatusLabel(start = now.plus(Duration.ofDays(1)), now = now)
      daysOtherLabel = sessionStatusLabel(start = now.plus(Duration.ofDays(3)), now = now)
    }

    assertEquals("Started", startedLabel)
    assertEquals("In 59 min", minLabel)
    assertEquals("In 1 hour", hourOneLabel)
    assertEquals("In 5 hours", hoursOtherLabel)
    assertEquals("In 1 day", dayOneLabel)
    assertEquals("In 3 days", daysOtherLabel)
  }

  @Test
  fun view_switcher_pill_is_displayed_with_list_and_map_options() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.VIEW_SWITCHER_PILL).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.LIST_VIEW_BUTTON).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).assertIsDisplayed()
  }

  @Test
  fun toggling_view_mode_switches_between_list_and_map() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    // Initially in list view mode
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertDoesNotExist()

    // Switch to map view mode
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).performClick()

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).assertDoesNotExist()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SEARCH).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.ALL_SESSIONS_FILTER)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MY_SESSIONS_FILTER).assertIsDisplayed()

    // Switch back to list view mode
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.LIST_VIEW_BUTTON).performClick()

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertDoesNotExist()
  }

  @Test
  fun location_badge_renders_room_name_only_when_distance_is_missing() {
    val viewModel = createViewModel()
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasTestTag(GroupSessionScreenTestTags.sessionItem(0)))
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.sessionItem(0))
        .assert(hasAnyDescendant(hasText("BC 01")))
  }

  @Test
  fun location_badge_renders_distance_when_coordinates_and_user_location_exist() {
    val campusLocation =
        CampusLocation(
            name = "BC 01",
            zone = "BC",
            type = CampusLocationType.LECTURE_ROOM,
            floor = 0,
            location = Location(46.519, 6.565),
        )
    val locationProvider = LocationProvider { flowOf(Location(46.519, 6.565)) }
    val campusLocationRepo = FakeCampusLocationRepository(listOf(campusLocation))
    val viewModel =
        createViewModel(
            locationProvider = locationProvider,
            campusLocationRepository = campusLocationRepo,
        )
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      viewModel.uiState.value.sessions.firstOrNull()?.proximity?.formattedDistance != null
    }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasTestTag(GroupSessionScreenTestTags.sessionItem(0)))
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.sessionItem(0))
        .assert(hasAnyDescendant(hasText("BC 01 • 0m")))
  }

  @Test
  fun card_location_pill_click_switches_to_map_mode_and_selects_session() {
    val campusLocation =
        CampusLocation(
            name = "BC 01",
            zone = "BC",
            type = CampusLocationType.LECTURE_ROOM,
            floor = 0,
            location = Location(46.519, 6.565),
        )
    val campusLocationRepo = FakeCampusLocationRepository(listOf(campusLocation))
    val viewModel = createViewModel(campusLocationRepository = campusLocationRepo)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.rooms.isNotEmpty() }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasTestTag(GroupSessionScreenTestTags.sessionItem(0)))
    composeTestRule.onNodeWithText("BC 01").performClick()

    // Switches to map mode
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).assertDoesNotExist()
    // Session sheet for BC 01 is opened on the map
    composeTestRule.onNodeWithTag(GroupSessionMapTestTags.SESSION_LIST).assertIsDisplayed()
    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    // View switcher is hidden while room session cards are shown, add button remains visible
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.VIEW_SWITCHER_PILL)
        .assertDoesNotExist()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ADD_BUTTON).assertIsDisplayed()
  }

  @Test
  fun initial_view_mode_map_renders_map_directly() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(
            viewModel = viewModel,
            initialViewMode = GroupSessionViewMode.MAP,
        )
      }
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).assertDoesNotExist()
  }

  @Test
  fun center_location_button_is_displayed_in_map_mode_and_not_in_list_mode() {
    val viewModel = createViewModel()
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    // List mode: button should not be present
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.CENTER_LOCATION_BUTTON)
        .assertDoesNotExist()

    // Switch to Map mode: button should be displayed on the filter row
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).performClick()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.CENTER_LOCATION_BUTTON)
        .assertIsDisplayed()
  }

  @Test
  fun switching_to_map_mode_requests_location_permission_when_not_granted() {
    var permissionRequested = false
    val registry =
        object : ActivityResultRegistry() {
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            permissionRequested = true
          }
        }
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val viewModel = createViewModel()
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalInspectionMode provides true,
          LocalActivityResultRegistryOwner provides owner,
      ) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }

    // Switch to map view mode
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertTrue(permissionRequested)
  }

  @Test
  fun switching_to_map_mode_does_not_request_permission_when_already_granted() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    var permissionRequested = false
    val registry =
        object : ActivityResultRegistry() {
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            permissionRequested = true
          }
        }
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val viewModel = createViewModel()
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalInspectionMode provides true,
          LocalActivityResultRegistryOwner provides owner,
      ) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }

    // Switch to map view mode
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertFalse(permissionRequested)
  }

  @Test
  fun switching_to_map_mode_when_permission_granted_refreshes_location() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    var collectionCount = 0
    val trackingProvider = LocationProvider {
      collectionCount++
      flowOf(Location(46.52, 6.56))
    }
    val viewModel = createViewModel(locationProvider = trackingProvider)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()
    val initialCount = collectionCount
    assertTrue(initialCount >= 1)

    // Switch to map view mode: permission is already granted, so it triggers refreshLocation()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertTrue(
        "refreshLocation should restart collection on LocationProvider",
        collectionCount > initialCount,
    )
  }

  @Test
  fun center_location_button_can_be_clicked_when_location_shared() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    val userLoc = Location(46.52, 6.56)
    val viewModel = createViewModel(locationProvider = LocationProvider { flowOf(userLoc) })
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel, initialViewMode = GroupSessionViewMode.MAP)
      }
    }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.CENTER_LOCATION_BUTTON)
        .assertIsDisplayed()
        .performClick()
  }

  @Test
  fun center_location_button_can_be_clicked_when_location_not_shared() {
    val viewModel = createViewModel(locationProvider = LocationProvider { flowOf(null) })
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel, initialViewMode = GroupSessionViewMode.MAP)
      }
    }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.CENTER_LOCATION_BUTTON)
        .assertIsDisplayed()
        .performClick()
  }

  @Test
  fun center_location_button_in_denied_twice_shows_settings_dialog() {
    var launchCount = 0
    val registry =
        object : ActivityResultRegistry() {
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            launchCount += 1
            dispatchResult(
                requestCode,
                mapOf(
                    Manifest.permission.ACCESS_FINE_LOCATION to false,
                    Manifest.permission.ACCESS_COARSE_LOCATION to false,
                ),
            )
          }
        }
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val viewModel = createViewModel(locationProvider = LocationProvider { flowOf(null) })
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalInspectionMode provides true,
          LocalActivityResultRegistryOwner provides owner,
      ) {
        GroupSessionScreen(viewModel = viewModel, initialViewMode = GroupSessionViewMode.MAP)
      }
    }
    composeTestRule.waitForIdle()

    // Refuse twice so the state machine reaches DENIED_TWICE, then dismiss the Settings dialog.
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.APPROXIMATE_LOCATION_CONFIRM)
        .performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_DISMISS)
        .performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, launchCount)

    // Clicking recenter in DENIED_TWICE shows the Settings dialog directly without re-prompting.
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.CENTER_LOCATION_BUTTON).performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, launchCount)
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_DIALOG)
        .assertIsDisplayed()
  }

  @Test
  fun open_settings_from_second_refusal_probes_once_on_resume_and_requests_fine_only_when_coarse_granted() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    val launchedArrays = mutableListOf<List<String>>()
    val registry =
        object : ActivityResultRegistry() {
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            launchedArrays.add((input as Array<*>).map { it as String })
            dispatchResult(
                requestCode,
                mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false),
            )
          }
        }
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val lifecycleOwner =
        object : LifecycleOwner {
          val lifecycleRegistry = LifecycleRegistry(this)
          override val lifecycle: Lifecycle = lifecycleRegistry
        }
    lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    val viewModel = createViewModel(locationProvider = LocationProvider { flowOf(null) })
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalInspectionMode provides true,
          LocalActivityResultRegistryOwner provides owner,
          LocalLifecycleOwner provides lifecycleOwner,
      ) {
        GroupSessionScreen(viewModel = viewModel, initialViewMode = GroupSessionViewMode.MAP)
      }
    }
    composeTestRule.waitForIdle()

    // Coarse already granted -> upgrade prompt asks only for ACCESS_FINE_LOCATION.
    assertEquals(listOf(listOf(Manifest.permission.ACCESS_FINE_LOCATION)), launchedArrays)

    // Refuse twice -> Settings dialog appears -> tap "Open Settings".
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.APPROXIMATE_LOCATION_CONFIRM)
        .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_OPEN).performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, launchedArrays.size)

    // Returning from Settings without fine granted launches one probe without re-opening dialog.
    composeTestRule.runOnUiThread {
      lifecycleOwner.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleOwner.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    composeTestRule.waitForIdle()
    assertEquals(3, launchedArrays.size)
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_DIALOG)
        .assertDoesNotExist()
  }

  @Test
  fun map_entry_does_not_auto_prompt_after_second_refusal_and_resets_on_settings_grant() {
    var launchCount = 0
    val registry =
        object : ActivityResultRegistry() {
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            launchCount += 1
            dispatchResult(
                requestCode,
                mapOf(
                    Manifest.permission.ACCESS_FINE_LOCATION to false,
                    Manifest.permission.ACCESS_COARSE_LOCATION to false,
                ),
            )
          }
        }
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val lifecycleOwner =
        object : LifecycleOwner {
          val lifecycleRegistry = LifecycleRegistry(this)
          override val lifecycle: Lifecycle = lifecycleRegistry
        }
    lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    var collectionCount = 0
    val viewModel =
        createViewModel(
            locationProvider =
                LocationProvider {
                  collectionCount++
                  flowOf(Location(46.52, 6.56))
                }
        )
    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalInspectionMode provides true,
          LocalActivityResultRegistryOwner provides owner,
          LocalLifecycleOwner provides lifecycleOwner,
      ) {
        GroupSessionScreen(viewModel = viewModel, initialViewMode = GroupSessionViewMode.MAP)
      }
    }
    composeTestRule.waitForIdle()
    assertEquals(1, launchCount)

    // First refusal shows explanation; confirming triggers the second system prompt.
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.APPROXIMATE_LOCATION_CONFIRM)
        .performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, launchCount)

    // Second refusal shows the Settings fallback dialog and persists the flag.
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_DISMISS)
        .performClick()
    composeTestRule.waitForIdle()

    // Switch back to LIST and return to MAP: should not auto-prompt again.
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.LIST_VIEW_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).performClick()
    composeTestRule.waitForIdle()
    assertEquals(2, launchCount)

    // Switch to LIST, grant precise location in Settings, and resume: should refresh location
    // and clear the persisted two-refusal flag.
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.LIST_VIEW_BUTTON).performClick()
    composeTestRule.waitForIdle()
    val collectionsBeforeResume = collectionCount
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    composeTestRule.runOnUiThread {
      lifecycleOwner.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleOwner.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    composeTestRule.waitForIdle()
    assertTrue(collectionCount > collectionsBeforeResume)

    // Switching to MAP now shows the user location marker without any popups.
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).performClick()
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.USER_LOCATION_MARKER, useUnmergedTree = true)
        .assertExists()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.APPROXIMATE_LOCATION_DIALOG)
        .assertDoesNotExist()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_DIALOG)
        .assertDoesNotExist()
    assertEquals(2, launchCount)
  }

  @Test
  fun onResume_refreshes_location_permission_when_revoked_and_granted() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    var collectionCount = 0
    val trackingProvider = LocationProvider {
      collectionCount++
      flowOf(null)
    }
    val lifecycleOwner =
        object : LifecycleOwner {
          val lifecycleRegistry = LifecycleRegistry(this)
          override val lifecycle: Lifecycle = lifecycleRegistry
        }
    lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    val viewModel = createViewModel(locationProvider = trackingProvider)

    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalInspectionMode provides true,
          LocalLifecycleOwner provides lifecycleOwner,
      ) {
        GroupSessionScreen(viewModel = viewModel, initialViewMode = GroupSessionViewMode.MAP)
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.USER_LOCATION_MARKER, useUnmergedTree = true)
        .assertExists()

    // Simulate revoking permission in system Settings and returning to the screen.
    shadowOf(application).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    composeTestRule.runOnUiThread {
      lifecycleOwner.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleOwner.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.USER_LOCATION_MARKER, useUnmergedTree = true)
        .assertDoesNotExist()

    // Simulate granting permission in system Settings and returning to the screen.
    val countBeforeGrant = collectionCount
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    composeTestRule.runOnUiThread {
      lifecycleOwner.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleOwner.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(GroupSessionMapTestTags.USER_LOCATION_MARKER, useUnmergedTree = true)
        .assertExists()
    assertTrue(collectionCount > countBeforeGrant)
  }

  private fun setCardContent(
      items: List<GroupSessionItem>,
      onJoin: (String) -> Unit = {},
      onLeave: (String) -> Unit = {},
  ) {
    composeTestRule.setContent {
      GroupSessionContent(
          state = GroupSessionsUiState(now = testInstant, isLoading = false, sessions = items),
          onJoin = onJoin,
          onLeave = onLeave,
      )
    }
  }

  private fun screenItem(
      id: String,
      isMember: Boolean = false,
      isActionInProgress: Boolean = false,
  ) =
      GroupSessionItem(
          session =
              GroupSession(
                  id = id,
                  createdBy = "host",
                  memberIds = if (isMember) listOf("host", "user-1") else listOf("host"),
                  details =
                      EventDetails(
                          type = EventType.STUDY_SESSION,
                          subject = "Subject $id",
                          startsAt = testInstant.plus(Duration.ofHours(1)),
                          endsAt = testInstant.plus(Duration.ofHours(2)),
                      ),
                  createdAt = testInstant.minus(Duration.ofHours(1)),
              ),
          isMember = isMember,
          isCreator = false,
          isActionInProgress = isActionInProgress,
      )

  private fun createViewModel(
      repository: GroupSessionsRepository = repo,
      locationProvider: LocationProvider = LocationProvider { flowOf(null) },
      campusLocationRepository: CampusLocationRepository? = null,
  ): GroupSessionsViewModel =
      GroupSessionsViewModel(
          authRepository = authRepository,
          repository = repository,
          profileRepository = profileRepository,
          locationProvider = locationProvider,
          campusLocationRepository = campusLocationRepository,
      )

  private class FakeCampusLocationRepository(
      private val locations: Result<List<CampusLocation>>,
  ) : CampusLocationRepository {
    constructor(locations: List<CampusLocation>) : this(Result.success(locations))

    override suspend fun getAllLocations(): Result<List<CampusLocation>> = locations

    override suspend fun search(filter: CampusLocationFilter): Result<List<CampusLocation>> =
        locations.map { entries ->
          entries.filter { location ->
            (filter.query.isNullOrBlank() ||
                location.name.contains(filter.query.trim(), ignoreCase = true)) &&
                (filter.zone.isNullOrBlank() ||
                    location.zone.equals(filter.zone.trim(), ignoreCase = true)) &&
                (filter.types.isEmpty() || location.type in filter.types)
          }
        }

    override suspend fun getZones(): Result<List<String>> = locations.map { entries ->
      entries.map { it.zone }.distinct().sorted()
    }
  }

  private class FakeProfileRepository(private val profiles: Map<String, UserProfile>) :
      ProfileRepository {
    override suspend fun getProfile(userId: String): Result<UserProfile?> =
        Result.success(profiles[userId])

    override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("Not needed in this test"))

    override suspend fun createProfile(userId: String): Result<UserProfile> =
        Result.success(UserProfile(uid = userId))

    override suspend fun updateProfilePictureUrl(userId: String, url: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Not needed in this test"))
  }

  private class FakeAuthRepository(override val currentUserId: String) : AuthRepository {
    override val currentUser = MutableStateFlow(UserAccount(currentUserId, null, "Alice", null))
    override val isAuthenticated: Boolean
      get() = true

    override suspend fun signInWithGoogle(idToken: String): UserAccount =
        UserAccount(currentUserId, null, "Alice", null)

    override suspend fun signOut() = Unit
  }

  private class FakeGroupSessionsRepository(
      private val initialSessions: List<GroupSession>,
      var failJoin: Boolean = false,
      var failLeave: Boolean = false,
  ) : GroupSessionsRepository {
    private var state = MutableStateFlow(initialSessions)
    var joinCalls: Int = 0
      private set

    var leaveCalls: Int = 0
      private set

    fun reset() {
      failJoin = false
      failLeave = false
      joinCalls = 0
      leaveCalls = 0
      state = MutableStateFlow(initialSessions)
    }

    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
        state.map { items ->
          Result.success(items.filter { it.details.endsAt.isAfter(endingAfter) })
        }

    override fun newSessionId(): String = "new-${System.nanoTime()}"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        Result.success(state.value.first { it.id == sessionId })

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> {
      joinCalls += 1
      if (failJoin) return Result.failure(RuntimeException("join failed"))
      state.value =
          state.value.map { session ->
            if (session.id == sessionId) {
              if (userId in session.memberIds) return Result.success(Unit)
              session.copy(memberIds = session.memberIds + userId)
            } else session
          }
      return Result.success(Unit)
    }

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> {
      leaveCalls += 1
      if (failLeave) return Result.failure(RuntimeException("leave failed"))
      state.value =
          state.value.map { session ->
            if (session.id == sessionId)
                session.copy(memberIds = session.memberIds.filter { it != userId })
            else session
          }
      return Result.success(Unit)
    }
  }
}
