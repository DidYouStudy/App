// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.eventplanning

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import com.android.sample.ui.theme.AlphaTokens
import com.android.sample.ui.theme.Dimens

/**
 * Loads an existing event into the shared event form and saves edits only when Save is pressed.
 *
 * The ViewModel owns form validation and persistence; this screen handles loading, picker dialogs,
 * and the navigation callbacks.
 *
 * @param eventId ID of the event to load for editing.
 * @param modifier Modifier applied to the screen.
 * @param viewModel ViewModel backing the event editing workflow.
 * @param onBackClick Invoked when the top bar back button is pressed.
 * @param onEventSaved Invoked after the edited event has been persisted successfully.
 * @param onPickTypeClick Optional external event type selector.
 * @param onPickDateClick Optional external date picker.
 * @param onPickStartTimeClick Optional external start time picker.
 * @param onPickEndTimeClick Optional external end time picker.
 * @param onPickSubjectClick Optional external subject selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEventScreen(
    eventId: String,
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
  val focusManager = LocalFocusManager.current
  var showDatePicker by rememberSaveable { mutableStateOf(false) }
  var showStartTimePicker by rememberSaveable { mutableStateOf(false) }
  var showEndTimePicker by rememberSaveable { mutableStateOf(false) }
  var showSubjectPicker by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(viewModel, eventId) { viewModel.loadEvent(eventId) }

  LaunchedEffect(uiState.isEventEdited) {
    if (uiState.isEventEdited) {
      viewModel.onEventEditedHandled()
      onEventSaved()
    }
  }

  BackHandler {
    focusManager.clearFocus()
    onBackClick()
  }

  val isValidationError =
      uiState.error == EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR ||
          uiState.error == EventPlanningViewModel.INVALID_EVENT_ERROR

  Scaffold(
      topBar = {
        MediumTopAppBar(
            title = {
              Text(
                  text = "Edit event",
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
              Text(
                  text =
                      stringResource(
                          if (isValidationError) R.string.event_planning_error_required_fields
                          else R.string.event_planning_error_save_failed
                      ),
                  color = MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier =
                      Modifier.padding(bottom = Dimens.errorVerticalPadding)
                          .testTag(Tags.ERROR_TEXT),
              )
            }

            Button(
                onClick = viewModel::editEvent,
                enabled = uiState.isEditing && !uiState.isBusy,
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
                        .height(Dimens.saveButtonHeight)
                        .testTag(Tags.SAVE_BUTTON),
            ) {
              if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier =
                        Modifier.size(Dimens.saveButtonProgressSize)
                            .testTag(Tags.SAVE_BUTTON_PROGRESS),
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
      },
      modifier = modifier.fillMaxSize().imePadding().testTag(Tags.SCREEN_CONTAINER),
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (uiState.isLoading) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
      } else {
        EventFormContent(
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
            modifier = Modifier.fillMaxSize(),
        )
      }
    }
  }

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
