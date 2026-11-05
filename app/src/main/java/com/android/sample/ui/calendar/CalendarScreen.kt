// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.model.event.PersonalEvent
import com.android.sample.ui.navigation.BottomNavigationMenu
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import com.android.sample.ui.profile.ProfileButton
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.Typography
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Renders the calendar scaffold from the supplied state and action callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onToday: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onRetry: () -> Unit,
    navigationActions: NavigationActions? = null,
    onAddClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddEvent) },
) {
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.calendar_title),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE),
              )
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            actions = {
              Surface(
                  onClick = onToday,
                  modifier = Modifier.testTag(CalendarTestTags.TODAY_BUTTON),
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
              ) {
                Text(
                    text = stringResource(R.string.calendar_today_label),
                    modifier =
                        Modifier.padding(
                            horizontal = Dimens.calendarTodayChipHorizontalPadding,
                            vertical = Dimens.calendarTodayChipVerticalPadding,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
              }
              Spacer(Modifier.width(Spacing.small))
              ProfileButton(onClick = { navigationActions?.navigateTo(Screen.Settings) })
            },
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = Tab.Calendar,
            onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) },
            modifier = Modifier.testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      floatingActionButton = {
        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier.testTag(CalendarTestTags.ADD_BUTTON),
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = "Add calendar event")
        }
      },
      content = { paddingValues ->
        if (uiState.error != null) {
          Box(
              modifier = Modifier.padding(paddingValues).fillMaxSize(),
              contentAlignment = Alignment.Center,
          ) {
            val errorMessage =
                when (uiState.error) {
                  CalendarViewModelError.LOAD_EVENTS_FAILED ->
                      stringResource(R.string.calendar_load_events_error)
                }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(text = errorMessage)
              Spacer(modifier = Modifier.height(Spacing.small))
              Button(
                  onClick = onRetry,
                  modifier =
                      Modifier.size(
                          width = Dimens.calendarRetryButtonWidth,
                          height = Dimens.calendarRetryButtonHeight,
                      ),
                  enabled = !uiState.isLoading,
                  colors =
                      ButtonDefaults.buttonColors(
                          containerColor = MaterialTheme.colorScheme.primary,
                          contentColor = MaterialTheme.colorScheme.onPrimary,
                      ),
              ) {
                if (uiState.isLoading) {
                  CircularProgressIndicator(
                      modifier = Modifier.size(Dimens.calendarLoadingSignSize),
                      color = MaterialTheme.colorScheme.onPrimary,
                  )
                } else {
                  Text(
                      text = stringResource(R.string.calendar_retry_button),
                      fontSize = Typography.bodyLarge.fontSize,
                  )
                }
              }
            }
          }
        } else {
          CalendarWeeklyView(
              uiState = uiState,
              onPreviousWeek = onPreviousWeek,
              onNextWeek = onNextWeek,
              modifier = Modifier.padding(paddingValues),
          )
        }
      },
  )
}

/** Renders a preview of the calendar screen with its weekly content. */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun CalendarScreenPreview() {
  DidYouStudyTheme {
    CalendarScreen(
        uiState = previewUiState(),
        onToday = {},
        onPreviousWeek = {},
        onNextWeek = {},
        onRetry = {},
    )
  }
}

/**
 * Renders a preview of the calendar screen's event-loading error state. Allows users to reattempt
 * loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun CalendarScreenErrorPreview() {
  DidYouStudyTheme {
    CalendarScreen(
        uiState =
            previewUiState(error = CalendarViewModelError.LOAD_EVENTS_FAILED, isLoading = false),
        onToday = {},
        onPreviousWeek = {},
        onNextWeek = {},
        onRetry = {},
    )
  }
}

/**
 * Renders a preview of the calendar screen's event-loading error state, when a reattempt is in
 * progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun CalendarScreenErrorReloadPreview() {
  DidYouStudyTheme {
    CalendarScreen(
        uiState =
            previewUiState(error = CalendarViewModelError.LOAD_EVENTS_FAILED, isLoading = true),
        onToday = {},
        onPreviousWeek = {},
        onNextWeek = {},
        onRetry = {},
    )
  }
}

/** Creates deterministic date state for the calendar screen previews. */
private fun previewUiState(
    error: CalendarViewModelError? = null,
    isLoading: Boolean = false,
): CalendarUiState {
  val today = LocalDate.of(2026, 10, 28)
  val dates = CalendarDateHelper().weekDates(today)
  val previewEvents =
      listOf(
          previewEvent(
              "preview-study",
              EventType.STUDY_SESSION,
              "Veryverylongsubjectnameetc.etc.",
              "2026-10-26T00:00",
              "2026-10-26T01:00",
          ),
          previewEvent(
              "preview-course",
              EventType.COURSE,
              "Physics",
              "2026-10-27T19:00",
              "2026-10-28T01:00",
          ),
          previewEvent(
              "preview-lab",
              EventType.LAB,
              "Chemistry",
              "2026-10-28T02:30",
              "2026-10-28T03:00",
          ),
          previewEvent(
              "preview-exam",
              EventType.EXAM,
              "History",
              "2026-10-30T21:00",
              "2026-10-31T10:00",
          ),
      )
  return CalendarUiState(
      today = today,
      weekStart = dates.first(),
      weekDates = dates,
      events = previewEvents,
      isLoading = isLoading,
      error = error,
  )
}

/** Creates a valid event used by the calendar screen previews. */
private fun previewEvent(
    id: String,
    type: EventType,
    subject: String,
    startsAt: String,
    endsAt: String,
): PersonalEvent {
  val createdAt = Instant.parse("2026-10-20T12:00:00Z")
  val previewZone = ZoneId.systemDefault()
  return PersonalEvent(
      id = id,
      ownerId = "preview-owner",
      type = type,
      subject = subject,
      startsAt = LocalDateTime.parse(startsAt).atZone(previewZone).toInstant(),
      endsAt = LocalDateTime.parse(endsAt).atZone(previewZone).toInstant(),
      createdAt = createdAt,
  )
}
