// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.sample.R
import com.android.sample.ui.navigation.BottomNavigationMenu
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import com.android.sample.ui.sharedComponents.AddFabMenu
import com.android.sample.ui.sharedComponents.MainTopAppBar
import com.android.sample.ui.sharedComponents.rememberNotImplementedToast
import com.android.sample.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Stateful dashboard entry point. Observes [DashboardViewModel] state and updates today's sessions
 * at midnight.
 *
 * @param navigationActions optional navigation controller to handle route changes.
 * @param viewModel ViewModel providing the study sessions state.
 * @param onEventClick invoked when selecting the Event option in the floating add menu.
 * @param onGroupSessionClick invoked when selecting the Group Session option in the floating add
 *   menu.
 * @param onEventCardClick invoked when selecting an event card.
 */
@Composable
fun DashboardScreen(
    navigationActions: NavigationActions? = null,
    viewModel: DashboardViewModel = dashboardViewModel(),
    onEventClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddEvent) },
    onGroupSessionClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddGroupSession) },
    onEventCardClick: (String) -> Unit = { eventId ->
      navigationActions?.navigateTo(Screen.EventDetails(eventId))
    },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current

  // Refresh today's date and sessions at midnight when resumed
  DisposableEffect(lifecycleOwner, viewModel) {
    val refreshJob =
        lifecycleOwner.lifecycleScope.launch {
          lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
              delay(uiState.midnightDelay)
              viewModel.updateToday()
            }
          }
        }
    onDispose { refreshJob.cancel() }
  }

  DashboardScreen(
      uiState = uiState,
      onRetry = viewModel::retry,
      navigationActions = navigationActions,
      onEventClick = onEventClick,
      onGroupSessionClick = onGroupSessionClick,
      onEventCardClick = onEventCardClick,
  )
}

/**
 * Stateless dashboard screen. Displays study sessions with loading, error, and empty states.
 *
 * @param uiState current dashboard state including sessions, loading flag, and any error.
 * @param onRetry invoked when the retry button is pressed in the error state.
 * @param navigationActions optional navigation controller to handle route changes.
 * @param onEventClick invoked when selecting the Event option in the floating add menu.
 * @param onGroupSessionClick invoked when selecting the Group Session option in the floating add
 *   menu.
 * @param onEventCardClick invoked when selecting an event card.
 */
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRetry: () -> Unit = {},
    navigationActions: NavigationActions? = null,
    onEventClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddEvent) },
    onGroupSessionClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddGroupSession) },
    onEventCardClick: (String) -> Unit = { eventId ->
      navigationActions?.navigateTo(Screen.EventDetails(eventId))
    },
) {
  Scaffold(
      topBar = {
        MainTopAppBar(
            title = stringResource(R.string.dashboard_title),
            onProfileClick = { navigationActions?.navigateTo(Screen.Settings) },
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = Tab.Dashboard,
            onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) },
            modifier = Modifier.testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      floatingActionButton = {
        AddFabMenu(
            onEventClick = onEventClick,
            onGroupSessionClick = onGroupSessionClick,
            modifier = Modifier.testTag(DashboardContentTestTags.ADD_BUTTON),
        )
      },
      content = { pd ->
        when {
          uiState.isLoading -> {
            Box(
                modifier =
                    Modifier.padding(pd)
                        .fillMaxSize()
                        .testTag(DashboardContentTestTags.LOADING_INDICATOR),
                contentAlignment = Alignment.Center,
            ) {
              CircularProgressIndicator()
            }
          }
          uiState.error != null -> {
            Column(
                modifier =
                    Modifier.padding(pd)
                        .fillMaxSize()
                        .testTag(DashboardContentTestTags.ERROR_CONTAINER),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                  text = stringResource(R.string.dashboard_load_sessions_error),
                  style = MaterialTheme.typography.bodyLarge,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.testTag(DashboardContentTestTags.ERROR_TEXT),
              )
              Spacer(modifier = Modifier.height(Spacing.medium))
              Button(
                  onClick = onRetry,
                  modifier = Modifier.testTag(DashboardContentTestTags.RETRY_BUTTON),
              ) {
                Text(text = stringResource(R.string.dashboard_retry_button))
              }
            }
          }
          else -> {
            val notImplemented = rememberNotImplementedToast()
            DashboardContent(
                sessions = uiState.sessions,
                modifier = Modifier.padding(pd),
                onEventCardClick = onEventCardClick,
                onStatsCardClick = notImplemented,
            )
          }
        }
      },
  )
}

@Preview(showBackground = true)
@Composable
fun DashBoardScreenPreview() {
  DashboardScreen(
      uiState =
          DashboardUiState(
              sessions =
                  listOf(
                      DashboardSession("09:00", "10:30", "Mathematics"),
                      DashboardSession("14:00", "15:30", "Physics"),
                  ),
              isLoading = false,
          )
  )
}
