// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Collects calendar ViewModel state and passes it with user actions to [CalendarScreen].
 *
 * @param onAddEventClick invoked when the Event menu option is selected.
 * @param onAddGroupSessionClick invoked when the Group Session menu option is selected; by default
 *   navigates to [Screen.AddGroupSession] when [navigationActions] is available.
 */
@Composable
fun CalendarRoute(
    navigationActions: NavigationActions? = null,
    viewModel: CalendarViewModel = calendarViewModel(),
    onAddEventClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddEvent) },
    onAddGroupSessionClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddGroupSession) },
    onEventClick: (String) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current

  // Wait until midnight to update today's date
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

  CalendarScreen(
      uiState = uiState,
      navigationActions = navigationActions,
      onToday = viewModel::navigateToToday,
      onPreviousWeek = viewModel::previousWeek,
      onNextWeek = viewModel::nextWeek,
      onRetry = viewModel::retryEvents,
      onAddEventClick = onAddEventClick,
      onAddGroupSessionClick = onAddGroupSessionClick,
      onEventClick = onEventClick,
  )
}
