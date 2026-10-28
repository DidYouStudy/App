// Co-authored-by: GitHub Copilot
package com.android.sample.ui.groupsession

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventType
import com.android.sample.model.groupsession.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.ui.navigation.NavigationTestTags
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupSessionScreenTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val repo =
      FakeGroupSessionsRepository(
          initialSessions =
              listOf(
                  GroupSession(
                      id = "s1",
                      createdBy = "user-1",
                      memberIds = listOf("user-1", "user-2"),
                      type = EventType.STUDY_SESSION,
                      subject = "Software Architecture",
                      startsAt = Instant.parse("2026-10-30T09:00:00Z"),
                      endsAt = Instant.parse("2026-10-30T10:00:00Z"),
                      createdAt = Instant.parse("2026-10-29T10:00:00Z"),
                  ),
                  GroupSession(
                      id = "s2",
                      createdBy = "user-3",
                      memberIds = listOf("user-3"),
                      type = EventType.PROJECT,
                      subject = "Capstone planning",
                      startsAt = Instant.parse("2026-10-30T12:00:00Z"),
                      endsAt = Instant.parse("2026-10-30T13:00:00Z"),
                      createdAt = Instant.parse("2026-10-29T11:00:00Z"),
                  ),
                  GroupSession(
                      id = "s3",
                      createdBy = "user-3",
                      memberIds = listOf("user-3", "user-1"),
                      type = EventType.EXAM,
                      subject = "Exam revision",
                      startsAt = Instant.parse("2026-10-30T14:00:00Z"),
                      endsAt = Instant.parse("2026-10-30T15:00:00Z"),
                      createdAt = Instant.parse("2026-10-29T12:00:00Z"),
                  ),
                  GroupSession(
                      id = "s4",
                      createdBy = "user-4",
                      memberIds = listOf("user-4", "user-5", "user-6"),
                      type = EventType.OTHER,
                      subject = null,
                      description = null,
                      location = null,
                      startsAt = Instant.parse("2026-10-30T16:00:00Z"),
                      endsAt = Instant.parse("2026-10-30T17:00:00Z"),
                      createdAt = Instant.parse("2026-10-29T13:00:00Z"),
                  ),
              )
      )

  private val authRepository = FakeAuthRepository("user-1")

  @Before
  fun resetRepo() {
    repo.reset()
    authRepository.currentUser.value = UserAccount("user-1", null, "Alice", null)
  }

  @Test
  fun screen_renders_title_and_filters_joined_sessions() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(NavigationTestTags.TOP_BAR_TITLE).assertIsDisplayed()
    composeTestRule.onNodeWithText("Active sessions").assertIsDisplayed()
    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    composeTestRule.onNodeWithText("Capstone planning").assertIsDisplayed()

    composeTestRule.onNodeWithText("My sessions").performClick()
    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    composeTestRule.onNodeWithText("Capstone planning").assertDoesNotExist()
  }

  @Test
  fun add_group_session_button_is_displayed() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ADD_BUTTON).assertIsDisplayed()
  }

  @Test
  fun host_sees_edit_action_without_join_or_leave_actions() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.sessionItem(0))
        .assert(hasAnyDescendant(hasText("Your session (Host)")))
        .assert(hasAnyDescendant(hasText("Edit")))
        .assert(!hasAnyDescendant(hasText("Join")))
        .assert(!hasAnyDescendant(hasText("Leave")))
  }

  @Test
  fun joined_filter_keeps_hosted_and_joined_sessions_only() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule.onNodeWithText("My sessions").performClick()

    composeTestRule.onNodeWithText("Software Architecture").assertIsDisplayed()
    composeTestRule.onNodeWithText("Exam revision").assertIsDisplayed()
    composeTestRule.onNodeWithText("Capstone planning").assertDoesNotExist()
  }

  @Test
  fun nullable_session_fields_use_readable_fallbacks() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).performTouchInput {
      swipeUp()
      swipeUp()
    }
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.sessionItem(3))
        .assert(hasAnyDescendant(hasText("Other")))
        .assert(hasAnyDescendant(hasText("Group study session")))
        .assert(hasAnyDescendant(hasText("Location TBD")))
        .assert(hasAnyDescendant(hasText("+1")))
  }

  @Test
  fun empty_repository_shows_empty_state() {
    val emptyRepository = FakeGroupSessionsRepository(emptyList())
    val viewModel = GroupSessionsViewModel(authRepository, emptyRepository)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("No active sessions").assertIsDisplayed()
  }

  @Test
  fun search_and_join_leave_actions_work() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithText("Search group sessions").performTextInput("capstone")
    composeTestRule.onNodeWithText("Capstone planning").assertIsDisplayed()

    composeTestRule.onNodeWithText("Join").performClick()
    composeTestRule.waitUntil(timeoutMillis = 2_000) { repo.joinCalls == 1 }
    composeTestRule.onNodeWithText("Joined").assertIsDisplayed()

    composeTestRule.onNodeWithText("Leave").performClick()
    composeTestRule.waitUntil(timeoutMillis = 2_000) { repo.leaveCalls == 1 }
  }

  @Test
  fun failed_join_shows_dismissible_error_message() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 4 }

    repo.failJoin = true
    composeTestRule.onNodeWithText("Search group sessions").performTextInput("capstone")
    composeTestRule.onNodeWithText("Join").performClick()
    composeTestRule.waitUntil(timeoutMillis = 2_000) { repo.joinCalls == 1 }
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      composeTestRule
          .onAllNodesWithText("Could not join session. Please try again.")
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.ERROR_SNACKBAR)
        .assert(hasAnyDescendant(hasText("Could not join session. Please try again.")))
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.ERROR_DISMISS).performClick()
    composeTestRule.onNodeWithText("Could not join session. Please try again.").assertDoesNotExist()
  }

  private class FakeAuthRepository(private val currentUserId: String) : AuthRepository {
    override val currentUser = MutableStateFlow(UserAccount(currentUserId, null, "Alice", null))

    override suspend fun signInWithGoogle(idToken: String): UserAccount =
        UserAccount(currentUserId, null, "Alice", null)

    override suspend fun signOut() = Unit
  }

  private class FakeGroupSessionsRepository(
      private val initialSessions: List<GroupSession>,
      var failJoin: Boolean = false,
  ) : GroupSessionsRepository {
    private var state = MutableStateFlow(initialSessions)
    var joinCalls: Int = 0
      private set

    var leaveCalls: Int = 0
      private set

    fun reset() {
      failJoin = false
      joinCalls = 0
      leaveCalls = 0
      state = MutableStateFlow(initialSessions)
    }

    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
        state.map { items ->
          Result.success(items.filter { it.endsAt.isAfter(endingAfter) })
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
