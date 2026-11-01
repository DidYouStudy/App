// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import com.android.sample.ui.theme.AlphaTokens
import com.android.sample.ui.theme.Dimens

/**
 * Stateful destination screen for creating and saving a new event.
 *
 * Collects state from [EventPlanningViewModel], handles the event-saved navigation side effect, and
 * embeds [AddEventScreenStateless] inside a Scaffold.
 *
 * @param modifier Modifier applied to the root screen layout.
 * @param viewModel ViewModel backing the event creation workflow.
 * @param onBackClick Invoked when the top app bar navigation icon is pressed.
 * @param onEventSaved Invoked once the event has been successfully persisted.
 * @param onPickTypeClick Intent to trigger the type selection modal.
 * @param onPickDateClick Intent to trigger the date picker dialog.
 * @param onPickStartTimeClick Intent to trigger the start time picker dialog.
 * @param onPickEndTimeClick Intent to trigger the end time picker dialog.
 * @param onPickSubjectClick Intent to trigger the subject search modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    modifier: Modifier = Modifier,
    viewModel: EventPlanningViewModel = eventsPlanningViewModel(),
    onBackClick: () -> Unit,
    onEventSaved: () -> Unit,
    onPickTypeClick: (() -> Unit)? = null,
    onPickDateClick: (() -> Unit)? = null,
    onPickStartTimeClick: (() -> Unit)? = null,
    onPickEndTimeClick: (() -> Unit)? = null,
    onPickSubjectClick: (() -> Unit)? = null,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  // State-saveable dialog visibility flags so open dialogs survive configuration changes
  var showTypeMenu by rememberSaveable { mutableStateOf(false) }
  var showDatePicker by rememberSaveable { mutableStateOf(false) }
  var showStartTimePicker by rememberSaveable { mutableStateOf(false) }
  var showEndTimePicker by rememberSaveable { mutableStateOf(false) }
  var showSubjectPicker by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(uiState.isEventAdded) {
    if (uiState.isEventAdded) {
      viewModel.onEventAddedHandled()
      onEventSaved()
    }
  }

  Scaffold(
      topBar = {
        MediumTopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.event_planning_add_title),
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
      modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      AddEventScreenStateless(
          uiState = uiState,
          onTypeChange = viewModel::setType,
          onTypeClick = onPickTypeClick,
          onDateClick = { onPickDateClick?.invoke() ?: run { showDatePicker = true } },
          onStartTimeClick = {
            onPickStartTimeClick?.invoke() ?: run { showStartTimePicker = true }
          },
          onEndTimeClick = { onPickEndTimeClick?.invoke() ?: run { showEndTimePicker = true } },
          onSubjectClick = { onPickSubjectClick?.invoke() ?: run { showSubjectPicker = true } },
          onLocationChange = viewModel::setLocation,
          onDescriptionChange = viewModel::setDescription,
          onAddTask = viewModel::addTask,
          onToggleTask = viewModel::toggleTask,
          onDeleteTask = viewModel::removeTask,
          onSaveClick = viewModel::addEvent,
      )

      // Date Picker Dialog
      if (showDatePicker) {
        EventDatePickerDialog(
            initialDate = uiState.date,
            onDateSelected = { date ->
              viewModel.setDate(date)
              showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
      }

      // Start Time Picker Dialog
      if (showStartTimePicker) {
        EventTimePickerDialog(
            initialTime = uiState.startTime,
            title = stringResource(R.string.event_planning_start_time_label),
            onTimeSelected = { time ->
              viewModel.setStartTime(time)
              showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false },
        )
      }

      // End Time Picker Dialog
      if (showEndTimePicker) {
        EventTimePickerDialog(
            initialTime = uiState.endTime,
            title = stringResource(R.string.event_planning_end_time_label),
            onTimeSelected = { time ->
              viewModel.setEndTime(time)
              showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false },
        )
      }

      // Subject Picker Dialog
      if (showSubjectPicker) {
        SubjectPickerDialog(
            currentSelection = uiState.subject,
            allSubjects = uiState.recommendedSubjects,
            onDismiss = { showSubjectPicker = false },
            onConfirm = { subject ->
              viewModel.setSubject(subject)
              showSubjectPicker = false
            },
        )
      }
    }
  }
}

/** Stateless AddEventScreen displaying form content and the pinned Save button. */
@Composable
fun AddEventScreenStateless(
    uiState: EventPlanningUiState,
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
    modifier: Modifier = Modifier,
    onTypeClick: (() -> Unit)? = null,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(Tags.SCREEN_CONTAINER),
  ) {
    // 1. Scrollable form takes all remaining vertical space ABOVE the button
    EventFormContent(
        uiState = uiState,
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
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )

    // 2. Button container sits physically below the form, never overlapping it
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Button(
          onClick = onSaveClick,
          enabled = !uiState.isSaving,
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                  disabledContainerColor =
                      MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.DISABLED),
                  disabledContentColor =
                      MaterialTheme.colorScheme.onPrimary.copy(alpha = AlphaTokens.DISABLED),
              ),
          shape = RoundedCornerShape(Dimens.cornerPill),
          modifier =
              Modifier.fillMaxWidth()
                  .padding(
                      horizontal = Dimens.saveButtonHorizontalPadding,
                      vertical = Dimens.saveButtonVerticalPadding,
                  )
                  .height(Dimens.saveButtonHeight)
                  .testTag(Tags.SAVE_BUTTON),
      ) {
        if (uiState.isSaving) {
          CircularProgressIndicator(
              modifier =
                  Modifier.size(Dimens.saveButtonProgressSize).testTag(Tags.SAVE_BUTTON_PROGRESS),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = Dimens.progressIndicatorStrokeWidth,
          )
        } else {
          Text(
              text = stringResource(R.string.event_planning_save_button),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
    }
  }
}

@Preview
@Composable
private fun AddEventScreenPreview() {
  MaterialTheme {
    AddEventScreenStateless(
        uiState =
            EventPlanningUiState(
                type = EventType.COURSE,
                subject = "Modèles stochastiques",
                location = "CM3",
                description = "Bring course pack and notes.",
                tasks = listOf(EventTask("Read Chapter 4"), EventTask("Do homework 2")),
            ),
        onTypeClick = {},
        onDateClick = {},
        onStartTimeClick = {},
        onEndTimeClick = {},
        onSubjectClick = {},
        onLocationChange = {},
        onDescriptionChange = {},
        onAddTask = {},
        onToggleTask = {},
        onDeleteTask = {},
        onSaveClick = {},
        onTypeChange = {},
        modifier = Modifier,
    )
  }
}
