// Co-authored-by: Copilot
package com.android.sample.ui.eventplanning

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import com.android.sample.ui.sharedComponents.EventDatePickerDialog
import com.android.sample.ui.sharedComponents.EventTimePickerDialog
import com.android.sample.ui.sharedComponents.PillActionButton
import com.android.sample.ui.sharedComponents.SubjectPickerDialog
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime

/** Renders the shared event form and owns its standard date, time and subject pickers. */
@Composable
internal fun EventPlanningScreen(
    modifier: Modifier = Modifier,
    uiState: EventPlanningUiState,
    onBackClick: () -> Unit,
    onTypeChange: (EventType) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onStartTimeSelected: (LocalTime) -> Unit,
    onEndTimeSelected: (LocalTime) -> Unit,
    onSubjectSelected: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    isEditing: Boolean = false,
    isLoading: Boolean = false,
    onTypeClick: (() -> Unit)? = null,
    onPickDateClick: (() -> Unit)? = null,
    onPickStartTimeClick: (() -> Unit)? = null,
    onPickEndTimeClick: (() -> Unit)? = null,
    onPickSubjectClick: (() -> Unit)? = null,
) {
  var showDatePicker by rememberSaveable { mutableStateOf(false) }
  var showStartTimePicker by rememberSaveable { mutableStateOf(false) }
  var showEndTimePicker by rememberSaveable { mutableStateOf(false) }
  var showSubjectPicker by rememberSaveable { mutableStateOf(false) }

  EventPlanningScreenStateless(
      uiState = uiState,
      onBackClick = onBackClick,
      onTypeChange = onTypeChange,
      onTypeClick = onTypeClick,
      onDateClick = { onPickDateClick?.invoke() ?: run { showDatePicker = true } },
      onStartTimeClick = { onPickStartTimeClick?.invoke() ?: run { showStartTimePicker = true } },
      onEndTimeClick = { onPickEndTimeClick?.invoke() ?: run { showEndTimePicker = true } },
      onSubjectClick = { onPickSubjectClick?.invoke() ?: run { showSubjectPicker = true } },
      onLocationChange = onLocationChange,
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
}

/**
 * Stateless event planning screen: top bar, form content and the pinned Save (and Delete) buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPlanningScreenStateless(
    modifier: Modifier = Modifier,
    uiState: EventPlanningUiState,
    onBackClick: () -> Unit,
    onTypeChange: (EventType) -> Unit,
    onDateClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSubjectClick: () -> Unit,
    onLocationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    isEditing: Boolean = false,
    isLoading: Boolean = false,
    onTypeClick: (() -> Unit)? = null,
) {
  val isValidationError: Boolean =
      uiState.error == EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR ||
          uiState.error == EventPlanningViewModel.INVALID_EVENT_ERROR

  Scaffold(
      topBar = {
        MediumTopAppBar(
            title = {
              Text(
                  text =
                      stringResource(
                          if (isEditing) R.string.event_planning_edit_title
                          else R.string.event_planning_add_title
                      ),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(Tags.TOP_BAR_TITLE),
              )
            },
            navigationIcon = {
              IconButton(onClick = onBackClick, modifier = Modifier.testTag(Tags.BACK_BUTTON)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.button_go_back),
                )
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
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
              // Map the error directly based on ViewModel error state
              val errorMessageRes =
                  if (isValidationError) {
                    R.string.event_planning_error_required_fields
                  } else {
                    R.string.event_planning_error_save_failed
                  }

              Text(
                  text = stringResource(errorMessageRes),
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
            onLocationChange = onLocationChange,
            onDescriptionChange = onDescriptionChange,
            onAddTask = onAddTask,
            onToggleTask = onToggleTask,
            onDeleteTask = onDeleteTask,
            onTypeClick = onTypeClick,
            modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }
}
