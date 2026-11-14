// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Copilot
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.eventplanning

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import com.android.sample.ui.sharedComponents.EventDatePickerDialog
import com.android.sample.ui.sharedComponents.EventTimePickerDialog
import com.android.sample.ui.sharedComponents.LocationPickerDialog
import com.android.sample.ui.sharedComponents.PillActionButton
import com.android.sample.ui.sharedComponents.SecondaryMediumTopAppBar
import com.android.sample.ui.sharedComponents.SubjectPickerDialog
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime

private const val ERROR_MESSAGE_DURATION_MILLIS = 4_000L
private const val ERROR_MESSAGE_DURATION_NANOS = ERROR_MESSAGE_DURATION_MILLIS * 1_000_000

private suspend fun waitForErrorMessageDuration() {
  val expiresAtNanos = withFrameNanos { it } + ERROR_MESSAGE_DURATION_NANOS
  while (withFrameNanos { it } < expiresAtNanos) {}
}

/**
 * Stateful shared event-planning UI used by the Add and Edit destinations.
 *
 * Owns the built-in date, time, subject and location picker state, while allowing destinations to
 * provide optional external picker actions. The [isEditing] and [isLoading] flags control the
 * shared screen's mode-specific title, actions and form loading display.
 *
 * @param modifier Modifier applied to the shared screen.
 * @param uiState Current form values, loading/saving state and any validation or persistence error.
 * @param onBackClick Invoked when the top-bar back button is pressed.
 * @param onTypeChange Called when the event type changes.
 * @param onDateSelected Called with the selected event date.
 * @param onStartTimeSelected Called with the selected start time.
 * @param onEndTimeSelected Called with the selected end time.
 * @param onSubjectSelected Called with the selected subject.
 * @param onLocationChange Called with the location picked from the location picker dialog.
 * @param onLocationSearchQueryChange Called with the location picker's search text as the user
 *   types.
 * @param onDescriptionChange Called when the description field changes.
 * @param onAddTask Called when a non-blank task is submitted.
 * @param onToggleTask Called with the index of the task to toggle.
 * @param onDeleteTask Called with the index of the task to remove.
 * @param onSaveClick Invoked when Save is pressed.
 * @param onErrorDismiss Invoked when the transient error message expires.
 * @param onDeleteClick Optional delete action. The Delete button is shown only in edit mode when
 *   this callback is supplied.
 * @param isEditing Whether the shared screen is being used to edit an existing event.
 * @param isLoading Whether to show the loading indicator instead of the form.
 * @param onTypeClick Optional external event-type picker action; null uses the shared form's
 *   built-in type dropdown.
 * @param onPickDateClick Optional external date picker action; null uses the built-in date picker.
 * @param onPickStartTimeClick Optional external start-time picker action; null uses the built-in
 *   time picker.
 * @param onPickEndTimeClick Optional external end-time picker action; null uses the built-in time
 *   picker.
 * @param titleRes Optional custom string resource for the top-bar title; when null, the title is
 *   selected based on [isEditing] and [uiState.eventKind].
 * @param onPickSubjectClick Optional external subject picker action; null uses the built-in subject
 *   picker.
 */
@Composable
internal fun EventPlanningScreen(
    modifier: Modifier = Modifier,
    uiState: EventPlanningUiState,
    @StringRes titleRes: Int? = null,
    onBackClick: () -> Unit,
    onTypeChange: (EventType) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onStartTimeSelected: (LocalTime) -> Unit,
    onEndTimeSelected: (LocalTime) -> Unit,
    onSubjectSelected: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onLocationSearchQueryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onErrorDismiss: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    isEditing: Boolean = false,
    isLoading: Boolean = false,
    onTypeClick: (() -> Unit)? = null,
    onPickDateClick: (() -> Unit)? = null,
    onPickStartTimeClick: (() -> Unit)? = null,
    onPickEndTimeClick: (() -> Unit)? = null,
    onPickSubjectClick: (() -> Unit)? = null,
) {
  LaunchedEffect(uiState.error) {
    if (uiState.error != null) {
      waitForErrorMessageDuration()
      onErrorDismiss()
    }
  }

  var showDatePicker by rememberSaveable { mutableStateOf(false) }
  var showStartTimePicker by rememberSaveable { mutableStateOf(false) }
  var showEndTimePicker by rememberSaveable { mutableStateOf(false) }
  var showSubjectPicker by rememberSaveable { mutableStateOf(false) }
  var showLocationPicker by rememberSaveable { mutableStateOf(false) }

  EventPlanningScreenStateless(
      uiState = uiState,
      titleRes = titleRes,
      onBackClick = onBackClick,
      onTypeChange = onTypeChange,
      onTypeClick = onTypeClick,
      onDateClick = { onPickDateClick?.invoke() ?: run { showDatePicker = true } },
      onStartTimeClick = { onPickStartTimeClick?.invoke() ?: run { showStartTimePicker = true } },
      onEndTimeClick = { onPickEndTimeClick?.invoke() ?: run { showEndTimePicker = true } },
      onSubjectClick = { onPickSubjectClick?.invoke() ?: run { showSubjectPicker = true } },
      onLocationClick = { showLocationPicker = true },
      onLocationClear = { onLocationChange("") },
      onDescriptionChange = onDescriptionChange,
      onAddTask = onAddTask,
      onToggleTask = onToggleTask,
      onDeleteTask = onDeleteTask,
      onSaveClick = onSaveClick,
      onDeleteClick = onDeleteClick,
      isEditing = isEditing,
      isLoading = isLoading,
      modifier = modifier,
  )

  if (showDatePicker) {
    EventDatePickerDialog(
        initialDate = uiState.date,
        onDateSelected = { date ->
          onDateSelected(date)
          showDatePicker = false
        },
        onDismiss = { showDatePicker = false },
    )
  }

  if (showStartTimePicker) {
    EventTimePickerDialog(
        initialTime = uiState.startTime,
        title = stringResource(R.string.event_planning_start_time_label),
        onTimeSelected = { time ->
          onStartTimeSelected(time)
          showStartTimePicker = false
        },
        onDismiss = { showStartTimePicker = false },
    )
  }

  if (showEndTimePicker) {
    EventTimePickerDialog(
        initialTime = uiState.endTime,
        title = stringResource(R.string.event_planning_end_time_label),
        onTimeSelected = { time ->
          onEndTimeSelected(time)
          showEndTimePicker = false
        },
        onDismiss = { showEndTimePicker = false },
    )
  }

  if (showSubjectPicker) {
    SubjectPickerDialog(
        currentSelection = uiState.subject,
        allSubjects = uiState.recommendedSubjects,
        onDismiss = { showSubjectPicker = false },
        onConfirm = { subject ->
          onSubjectSelected(subject)
          showSubjectPicker = false
        },
    )
  }

  if (showLocationPicker) {
    LocationPickerDialog(
        searchResults = uiState.locationSearchResults,
        onDismiss = {
          showLocationPicker = false
          onLocationSearchQueryChange("")
        },
        onSearchQueryChange = onLocationSearchQueryChange,
        onConfirm = { location ->
          onLocationChange(location)
          showLocationPicker = false
          onLocationSearchQueryChange("")
        },
    )
  }
}

/**
 * Renders the stateless shared event-planning layout for Add or Edit mode.
 *
 * Displays the top bar, form or loading indicator, and pinned Save action. In edit mode, it also
 * displays the optional Delete action. Form state and all user actions are supplied by the caller.
 *
 * @param modifier Modifier applied to the screen scaffold.
 * @param uiState Current form values, operation state and any validation or persistence error.
 * @param onBackClick Invoked when the top-bar back button is pressed.
 * @param onTypeChange Called when the event type changes.
 * @param onDateClick Invoked when the date selector is pressed.
 * @param onStartTimeClick Invoked when the start-time selector is pressed.
 * @param onEndTimeClick Invoked when the end-time selector is pressed.
 * @param onSubjectClick Invoked when the subject selector is pressed.
 * @param onLocationClick Invoked when the location selector is pressed.
 * @param onDescriptionChange Called when the description field changes.
 * @param onAddTask Called when a non-blank task is submitted.
 * @param onToggleTask Called with the index of the task to toggle.
 * @param onDeleteTask Called with the index of the task to remove.
 * @param onSaveClick Invoked when Save is pressed.
 * @param onDeleteClick Optional delete action; when null, no Delete button is shown.
 * @param isEditing Whether to render Edit mode; defaults to Add mode.
 * @param isLoading Whether to show a loading indicator instead of the form.
 * @param titleRes Optional custom string resource for the top-bar title; when null, the title is
 *   selected based on [isEditing] and [uiState.eventKind].
 * @param onTypeClick Optional event-type picker action passed to the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPlanningScreenStateless(
    modifier: Modifier = Modifier,
    uiState: EventPlanningUiState,
    @StringRes titleRes: Int? = null,
    onBackClick: () -> Unit,
    onTypeChange: (EventType) -> Unit,
    onDateClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSubjectClick: () -> Unit,
    onLocationClick: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    isEditing: Boolean = false,
    isLoading: Boolean = false,
    onTypeClick: (() -> Unit)? = null,
    onLocationClear: (() -> Unit)? = null,
) {
  val isValidationError = uiState.error?.isInputValidationError == true

  val resolvedTitleRes =
      titleRes
          ?: when (uiState.eventKind) {
            EventKind.GROUP_SESSION ->
                if (isEditing) R.string.group_session_edit_title
                else R.string.group_session_add_title
            EventKind.PERSONAL ->
                if (isEditing) R.string.event_planning_edit_title
                else R.string.event_planning_add_title
          }

  Scaffold(
      topBar = {
        SecondaryMediumTopAppBar(
            title = stringResource(resolvedTitleRes),
            onBackClick = onBackClick,
            backButtonEnabled = !isEditing || uiState.canLeaveEdit,
            backButtonTestTag = Tags.BACK_BUTTON,
            titleTestTag = Tags.TOP_BAR_TITLE,
            modifier = Modifier.testTag(Tags.TOP_APP_BAR),
        )
      },
      bottomBar = {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        ) {
          Column(
              modifier =
                  Modifier.fillMaxWidth()
                      .padding(
                          horizontal = Dimens.saveButtonHorizontalPadding,
                          vertical = Dimens.saveButtonVerticalPadding,
                      ),
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            uiState.error?.let {
              Text(
                  text = stringResource(it.stringResource),
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier =
                      Modifier.padding(bottom = Dimens.errorVerticalPadding)
                          .testTag(Tags.ERROR_TEXT),
              )
            }

            // changed the old save button to a shared one between save and delete and refactored it
            // to the shared folder
            PillActionButton(
                onClick = onSaveClick,
                enabled =
                    if (isEditing) uiState.isEditing && !uiState.isBusy else !uiState.isSaving,
                isInProgress = uiState.isSaving,
                label = stringResource(R.string.event_planning_save_button),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                buttonTag = Tags.SAVE_BUTTON,
                progressTag = Tags.SAVE_BUTTON_PROGRESS,
            )

            // Edit mode only: delete the event that is currently being edited.
            if (isEditing && onDeleteClick != null) {
              Spacer(modifier = Modifier.height(Spacing.extraSmall))
              PillActionButton(
                  onClick = onDeleteClick,
                  enabled = uiState.isEditing && !uiState.isBusy,
                  isInProgress = uiState.isDeleting,
                  label = stringResource(R.string.event_planning_delete_button),
                  containerColor = MaterialTheme.colorScheme.error,
                  contentColor = MaterialTheme.colorScheme.onError,
                  buttonTag = Tags.DELETE_BUTTON,
                  progressTag = Tags.DELETE_BUTTON_PROGRESS,
                  icon = Icons.Outlined.Delete,
              )
            }
          }
        }
      },
      modifier = modifier.fillMaxSize().imePadding().testTag(Tags.SCREEN_CONTAINER),
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
      } else {
        EventFormContent(
            uiState = uiState,
            isValidationError = isValidationError,
            onTypeChange = onTypeChange,
            onDateClick = onDateClick,
            onStartTimeClick = onStartTimeClick,
            onEndTimeClick = onEndTimeClick,
            onSubjectClick = onSubjectClick,
            onLocationClick = onLocationClick,
            onLocationClear = onLocationClear,
            onDescriptionChange = onDescriptionChange,
            taskActions = TaskActions(onAddTask, onToggleTask, onDeleteTask),
            onTypeClick = onTypeClick,
            modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}
