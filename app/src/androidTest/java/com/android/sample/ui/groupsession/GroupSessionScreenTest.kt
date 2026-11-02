// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.groupsession

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.event.EventType
import com.android.sample.model.groupsession.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
                      type = EventType.STUDY_SESSION,
                      subject = "Software Architecture",
                      startsAt = testInstant.plus(Duration.ofHours(1)),
                      endsAt = testInstant.plus(Duration.ofHours(2)),
                      createdAt = testInstant.minus(Duration.ofHours(1)),
                  ),
                  GroupSession(
                      id = "s2",
                      createdBy = "user-3",
                      memberIds = listOf("user-3"),
                      type = EventType.PROJECT,
                      subject = "Capstone planning",
                      startsAt = testInstant.plus(Duration.ofHours(3)),
                      endsAt = testInstant.plus(Duration.ofHours(4)),
                      createdAt = testInstant.minus(Duration.ofHours(1)),
                  ),
                  GroupSession(
                      id = "s3",
                      createdBy = "user-3",
                      memberIds = listOf("user-3", "user-1"),
                      type = EventType.EXAM,
                      subject = "Exam revision",
                      startsAt = testInstant.plus(Duration.ofHours(5)),
                      endsAt = testInstant.plus(Duration.ofHours(6)),
                      createdAt = testInstant.minus(Duration.ofHours(1)),
                  ),
                  GroupSession(
                      id = "s4",
                      createdBy = "user-4",
                      memberIds = listOf("user-4", "user-5", "user-6"),
                      type = EventType.OTHER,
                      subject = null,
                      description = null,
                      location = null,
                      startsAt = testInstant.plus(Duration.ofHours(7)),
                      endsAt = testInstant.plus(Duration.ofHours(8)),
                      createdAt = testInstant.minus(Duration.ofHours(1)),
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
    val viewModel = GroupSessionsViewModel(authRepository, repo)
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
    val viewModel = GroupSessionsViewModel(authRepository, repo)
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
        .assert(hasAnyDescendant(hasText("+1")))
  }

  @Test
  fun empty_repository_shows_empty_state() {
    val emptyRepository = FakeGroupSessionsRepository(emptyList())
    val viewModel = GroupSessionsViewModel(authRepository, emptyRepository)
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
    val viewModel = GroupSessionsViewModel(authRepository, neverEndingRepo)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.LOADING).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_loading))
        .assertIsDisplayed()
  }

  @Test
  fun search_and_join_leave_actions_work() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
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

  @Test
  fun failed_join_shows_dismissible_error_message() {
    val viewModel = GroupSessionsViewModel(authRepository, repo)
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
    val viewModel = GroupSessionsViewModel(authRepository, repo)
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
    val viewModel = GroupSessionsViewModel(authRepository, failingRepo)
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

  private class FakeAuthRepository(private val currentUserId: String) : AuthRepository {
    override val currentUser = MutableStateFlow(UserAccount(currentUserId, null, "Alice", null))

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
