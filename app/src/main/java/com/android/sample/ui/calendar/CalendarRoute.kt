// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Gemini AI Agent
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

/** Collects calendar ViewModel state and passes it with user actions to [CalendarScreen]. */
@Composable
fun CalendarRoute(
    navigationActions: NavigationActions? = null,
    viewModel: CalendarViewModel = calendarViewModel(),
    onAddClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddEvent) },
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
      onAddClick = onAddClick,
  )
}
