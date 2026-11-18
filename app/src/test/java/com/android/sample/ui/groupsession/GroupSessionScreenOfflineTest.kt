// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.groupsession

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.R
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.connectivity.FakeConnectivityObserver
import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
import com.android.sample.model.groupsession.InMemoryGroupSessionsRepository
import com.android.sample.model.location.LocationProvider
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.UserProfile
import com.android.sample.ui.auth.TestAuthRepository
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric tests for the offline indication of the Group sessions screen.
 *
 * Most tests render the stateless [GroupSessionContent] with a hand-made [GroupSessionsUiState], so
 * they check exactly what the screen does with [GroupSessionsUiState.isOffline]. A few tests render
 * the whole [GroupSessionScreen] on a real ViewModel fed by a [FakeConnectivityObserver], to check
 * that the indication follows connectivity from end to end and that cached sessions stay usable.
 */
@RunWith(AndroidJUnit4::class)
class GroupSessionScreenOfflineTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val offlineMessage = context.getString(R.string.group_session_offline_indicator)

  // Captured once so session times stay in the future whatever the date the tests run on.
  private val now = Instant.now()

  // ---------------------------------------------------------------------------------------------
  // The indicator composable
  // ---------------------------------------------------------------------------------------------

  @Test
  fun indicatorShowsTheOfflineMessage() {
    composeTestRule.setContent { GroupSessionOfflineIndicator() }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithText(offlineMessage).assertIsDisplayed()
  }

  @Test
  fun offlineMessageTellsTheStudentTheSessionsAreSaved() {
    assertEquals("You are offline. Showing saved sessions.", offlineMessage)
  }

  // ---------------------------------------------------------------------------------------------
  // Offline previews
  // ---------------------------------------------------------------------------------------------

  /** The list preview renders the offline indicator and empty state. */
  @Test
  fun listOfflinePreviewShowsTheOfflineIndicator() {
    composeTestRule.setContent { GroupSessionContentOfflinePreview() }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithText(offlineMessage).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_empty))
        .assertIsDisplayed()
  }

  /** The map preview renders the offline indicator in inspection mode. */
  @Test
  fun mapOfflinePreviewShowsTheOfflineIndicator() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionMapContentOfflinePreview()
      }
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithText(offlineMessage).assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------------------------
  // List view
  // ---------------------------------------------------------------------------------------------

  @Test
  fun listViewShowsTheIndicatorWhileOffline() {
    composeTestRule.setContent { GroupSessionContent(state = state(isOffline = true)) }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithText(offlineMessage).assertIsDisplayed()
  }

  @Test
  fun listViewHidesTheIndicatorWhileOnline() {
    composeTestRule.setContent { GroupSessionContent(state = state(isOffline = false)) }

    assertIndicatorIsAbsent()
  }

  @Test
  fun listViewIndicatorFollowsTheOfflineState() {
    assertIndicatorFollowsTheState(GroupSessionViewMode.LIST)
  }

  @Test
  fun listViewShowsCachedSessionsTogetherWithTheIndicator() {
    composeTestRule.setContent {
      GroupSessionContent(state = state(isOffline = true, sessions = cachedItems()))
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Cached algebra"))
    composeTestRule.onNodeWithText("Cached algebra").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Cached biology"))
    composeTestRule.onNodeWithText("Cached biology").assertIsDisplayed()
  }

  @Test
  fun listViewShowsTheIndicatorWhileTheCachedListIsStillLoading() {
    composeTestRule.setContent {
      GroupSessionContent(state = state(isOffline = true, isLoading = true))
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun listViewShowsTheIndicatorTogetherWithTheEmptyState() {
    composeTestRule.setContent { GroupSessionContent(state = state(isOffline = true)) }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
    composeTestRule
        .onNodeWithText(context.getString(R.string.group_session_empty))
        .assertIsDisplayed()
  }

  /** The indicator is a notice between the filters and the list, not a replacement for either. */
  @Test
  fun listViewPlacesTheIndicatorBetweenTheFiltersAndTheList() {
    composeTestRule.setContent {
      GroupSessionContent(state = state(isOffline = true, sessions = cachedItems()))
    }

    val filters =
        composeTestRule
            .onNodeWithTag(GroupSessionScreenTestTags.ALL_SESSIONS_FILTER)
            .getUnclippedBoundsInRoot()
    val indicator =
        composeTestRule
            .onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR)
            .getUnclippedBoundsInRoot()
    val list =
        composeTestRule
            .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
            .getUnclippedBoundsInRoot()

    assertTrue(indicator.top >= filters.bottom)
    assertTrue(list.top >= indicator.bottom)
  }

  // ---------------------------------------------------------------------------------------------
  // Search and filters stay usable offline
  // ---------------------------------------------------------------------------------------------

  @Test
  fun searchFiltersAndViewSwitcherStayVisibleOffline() {
    composeTestRule.setContent {
      GroupSessionContent(state = state(isOffline = true, sessions = cachedItems()))
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SEARCH).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.FILTER_BUTTON).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.ALL_SESSIONS_FILTER)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MY_SESSIONS_FILTER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.VIEW_SWITCHER_PILL).assertIsDisplayed()
  }

  @Test
  fun typingInTheSearchFieldStillReportsTheQueryOffline() {
    var query: String? = null
    composeTestRule.setContent {
      GroupSessionContent(state = state(isOffline = true), onQueryChange = { query = it })
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SEARCH).performTextInput("alg")

    assertEquals("alg", query)
  }

  @Test
  fun theMySessionsChipStillReportsTheFilterOffline() {
    var filter: GroupSessionFilter? = null
    composeTestRule.setContent {
      GroupSessionContent(state = state(isOffline = true), onFilterChange = { filter = it })
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MY_SESSIONS_FILTER).performClick()

    assertEquals(GroupSessionFilter.JOINED, filter)
  }

  @Test
  fun theFilterButtonStillOpensTheFilterSheetOffline() {
    var opened = false
    composeTestRule.setContent {
      GroupSessionContent(state = state(isOffline = true), onOpenFilters = { opened = true })
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.FILTER_BUTTON).performClick()

    assertTrue(opened)
  }

  // ---------------------------------------------------------------------------------------------
  // Map view
  // ---------------------------------------------------------------------------------------------

  @Test
  fun mapViewShowsTheIndicatorWhileOffline() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionContent(state = state(isOffline = true), viewMode = GroupSessionViewMode.MAP)
      }
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
    composeTestRule.onNodeWithText(offlineMessage).assertIsDisplayed()
  }

  @Test
  fun mapViewHidesTheIndicatorWhileOnline() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionContent(state = state(isOffline = false), viewMode = GroupSessionViewMode.MAP)
      }
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertIsDisplayed()
    assertIndicatorIsAbsent()
  }

  @Test
  fun mapViewIndicatorFollowsTheOfflineState() {
    assertIndicatorFollowsTheState(GroupSessionViewMode.MAP)
  }

  @Test
  fun mapViewKeepsSearchAndFiltersNextToTheIndicator() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionContent(state = state(isOffline = true), viewMode = GroupSessionViewMode.MAP)
      }
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SEARCH).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.ALL_SESSIONS_FILTER)
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MY_SESSIONS_FILTER).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------------------------
  // The whole screen on a real ViewModel
  // ---------------------------------------------------------------------------------------------

  /** The acceptance case: shown while offline, gone when the connection returns, and back again. */
  @Test
  fun screenShowsTheIndicatorOnlyWhileTheDeviceIsOffline() {
    val connectivity = FakeConnectivityObserver(initialOnline = false)
    val viewModel = createViewModel(connectivity)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      viewModel.uiState.value.isOffline && viewModel.uiState.value.sessions.size == 2
    }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()

    connectivity.isOnline.value = true
    composeTestRule.waitUntil(timeoutMillis = 2_000) { !viewModel.uiState.value.isOffline }
    assertIndicatorIsAbsent()

    connectivity.isOnline.value = false
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.isOffline }
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun screenKeepsTheCachedSessionsVisibleWhenTheConnectionIsLost() {
    val connectivity = FakeConnectivityObserver(initialOnline = true)
    val viewModel = createViewModel(connectivity)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 2 }
    assertIndicatorIsAbsent()

    connectivity.isOnline.value = false
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.isOffline }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Cached algebra"))
    composeTestRule.onNodeWithText("Cached algebra").assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Cached biology"))
    composeTestRule.onNodeWithText("Cached biology").assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun screenSearchesTheCachedSessionsWhileOffline() {
    val connectivity = FakeConnectivityObserver(initialOnline = false)
    val viewModel = createViewModel(connectivity)
    composeTestRule.setContent { GroupSessionScreen(viewModel = viewModel) }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 2 }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SEARCH).performTextInput("alg")
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.sessions.size == 1 }

    composeTestRule
        .onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST)
        .performScrollToNode(hasText("Cached algebra"))
    composeTestRule.onNodeWithText("Cached algebra").assertIsDisplayed()
    composeTestRule.onNodeWithText("Cached biology").assertDoesNotExist()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun screenKeepsTheIndicatorWhenSwitchingBetweenListAndMapOffline() {
    val connectivity = FakeConnectivityObserver(initialOnline = false)
    val viewModel = createViewModel(connectivity)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 2_000) {
      viewModel.uiState.value.isOffline && viewModel.uiState.value.sessions.size == 2
    }
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.LIST_VIEW_BUTTON).performClick()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.SESSION_LIST).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
  }

  @Test
  fun screenOpenedInMapModeShowsTheIndicatorOffline() {
    val connectivity = FakeConnectivityObserver(initialOnline = false)
    val viewModel = createViewModel(connectivity)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionScreen(viewModel = viewModel, initialViewMode = GroupSessionViewMode.MAP)
      }
    }
    composeTestRule.waitUntil(timeoutMillis = 2_000) { viewModel.uiState.value.isOffline }

    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.MAP_CONTENT).assertIsDisplayed()
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  /** Checks that the indicator appears, disappears and appears again as the state changes. */
  private fun assertIndicatorFollowsTheState(viewMode: GroupSessionViewMode) {
    var isOffline by mutableStateOf(false)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        GroupSessionContent(state = state(isOffline = isOffline), viewMode = viewMode)
      }
    }
    assertIndicatorIsAbsent()

    composeTestRule.runOnIdle { isOffline = true }
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()

    composeTestRule.runOnIdle { isOffline = false }
    assertIndicatorIsAbsent()

    composeTestRule.runOnIdle { isOffline = true }
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertIsDisplayed()
  }

  /** Checks that neither the indicator nor its message is on screen. */
  private fun assertIndicatorIsAbsent() {
    composeTestRule.onNodeWithTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR).assertDoesNotExist()
    composeTestRule.onNodeWithText(offlineMessage).assertDoesNotExist()
  }

  /** Builds a screen state whose only interesting parts are the offline flag and the sessions. */
  private fun state(
      isOffline: Boolean,
      sessions: List<GroupSessionItem> = emptyList(),
      isLoading: Boolean = false,
  ) =
      GroupSessionsUiState(
          now = now,
          isLoading = isLoading,
          sessions = sessions,
          isOffline = isOffline,
      )

  /** Two cached sessions, as the list would show them after being loaded online. */
  private fun cachedItems(): List<GroupSessionItem> =
      cachedSessions().map { GroupSessionItem(it, isMember = false, isCreator = false) }

  private fun cachedSessions(): List<GroupSession> =
      listOf(
          session("algebra", "Cached algebra", startsInHours = 1),
          session("biology", "Cached biology", startsInHours = 3),
      )

  private fun session(id: String, subject: String, startsInHours: Long) =
      GroupSession(
          id = id,
          createdBy = HOST,
          memberIds = listOf(HOST),
          details =
              EventDetails(
                  type = EventType.COURSE,
                  subject = subject,
                  startsAt = now.plus(Duration.ofHours(startsInHours)),
                  endsAt = now.plus(Duration.ofHours(startsInHours + 1)),
              ),
          createdAt = now,
      )

  /** Builds a ViewModel for a signed-in student whose repository already holds the sessions. */
  private fun createViewModel(connectivity: FakeConnectivityObserver): GroupSessionsViewModel {
    val authRepository = TestAuthRepository()
    authRepository.currentUser.value = UserAccount(STUDENT, null, "Student", null)
    val repository = InMemoryGroupSessionsRepository()
    runBlocking { cachedSessions().forEach { repository.saveSession(it.createdBy, it) } }
    return GroupSessionsViewModel(
        authRepository = authRepository,
        repository = repository,
        profileRepository = NoProfilesRepository(),
        locationProvider = LocationProvider { flowOf(null) },
        connectivityObserver = connectivity,
    )
  }

  /** Profile lookups find nobody; host names are not what these tests are about. */
  private class NoProfilesRepository : ProfileRepository {
    override suspend fun getProfile(userId: String): Result<UserProfile?> = Result.success(null)

    override suspend fun createProfile(userId: String): Result<UserProfile> =
        Result.success(UserProfile(uid = userId))

    /** Username updates are outside the scope of these offline screen tests. */
    override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> =
        Result.failure(UnsupportedOperationException("Not needed in this test"))
  }

  private companion object {
    const val HOST = "host"
    const val STUDENT = "student"
  }
}
