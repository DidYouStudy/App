// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.ui.navigation.NavigationActions

/** Collects calendar ViewModel state and passes it with user actions to [CalendarScreen]. */
@Composable
fun CalendarRoute(
    navigationActions: NavigationActions? = null,
    viewModel: CalendarViewModel = calendarViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  CalendarScreen(
      uiState = uiState,
      navigationActions = navigationActions,
      onToday = viewModel::navigateToToday,
      onPreviousWeek = viewModel::previousWeek,
      onNextWeek = viewModel::nextWeek,
      onRetry = viewModel::retryEvents,
  )
}
