// Co-authored-by: Claude AI Agent
package com.android.sample.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.groupsession.GroupSession
import com.android.sample.model.groupsession.GroupSessionsRepository
import com.android.sample.ui.calendar.CalendarScreen
import com.android.sample.ui.dashboard.DashboardScreen
import com.android.sample.ui.groupsession.GroupSessionScreen
import com.android.sample.ui.groupsession.GroupSessionsViewModel
import com.android.sample.ui.profile.ProfileButtonTestTags
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
class TopLevelScreensProfileButtonTest {
  @get:Rule val composeTestRule = createComposeRule()

  private val fakeAuthRepository =
      object : AuthRepository {
        override val currentUser = MutableStateFlow<UserAccount?>(null)

        override suspend fun signInWithGoogle(idToken: String) =
            UserAccount("test-user", null, "Test user", null)

        override suspend fun signOut() = Unit
      }

  private fun <T : ViewModel> assertProfileButtonOpensSettings(
      viewModel: T? = null,
      screen: @Composable (NavigationActions?, T?) -> Unit,
  ) {
    val navigationActions = mock<NavigationActions>()
    composeTestRule.setContent { screen(navigationActions, viewModel) }

    composeTestRule
        .onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON)
        .assertIsDisplayed()
        .performClick()

    verify(navigationActions).navigateTo(Screen.Settings)
    verifyNoMoreInteractions(navigationActions)
  }

  @Test
  fun dashboardProfileButtonOpensSettings() {
    assertProfileButtonOpensSettings<ViewModel> { navigationActions, _ ->
      DashboardScreen(navigationActions = navigationActions)
    }
  }

  @Test
  fun calendarProfileButtonOpensSettings() {
    assertProfileButtonOpensSettings<ViewModel> { navigationActions, _ ->
      CalendarScreen(navigationActions = navigationActions)
    }
  }

  @Test
  fun groupSessionProfileButtonOpensSettings() {
    val groupSessionsViewModel =
        GroupSessionsViewModel(fakeAuthRepository, EmptyGroupSessionsRepository())
    assertProfileButtonOpensSettings(groupSessionsViewModel) { navigationActions, viewModel ->
      GroupSessionScreen(
          navigationActions = navigationActions,
          viewModel = requireNotNull(viewModel),
      )
    }
  }

  @Test
  fun profileButtonWithoutNavigationActionsDoesNotCrash() {
    composeTestRule.setContent { DashboardScreen() }

    composeTestRule.onNodeWithTag(ProfileButtonTestTags.PROFILE_BUTTON).performClick()
  }

  private class EmptyGroupSessionsRepository : GroupSessionsRepository {
    override fun observeSessions(endingAfter: Instant): Flow<Result<List<GroupSession>>> =
        flowOf(Result.success(emptyList()))

    override fun newSessionId(): String = "test-session"

    override suspend fun getSession(sessionId: String): Result<GroupSession> =
        error("Not used by this test")

    override suspend fun saveSession(userId: String, session: GroupSession): Result<Unit> =
        error("Not used by this test")

    override suspend fun deleteSession(userId: String, sessionId: String): Result<Unit> =
        error("Not used by this test")

    override suspend fun joinSession(userId: String, sessionId: String): Result<Unit> =
        error("Not used by this test")

    override suspend fun leaveSession(userId: String, sessionId: String): Result<Unit> =
        error("Not used by this test")
  }
}
