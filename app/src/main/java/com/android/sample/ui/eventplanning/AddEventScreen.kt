// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType

/**
 * Stateful destination screen for creating and saving a new event.
 *
 * Collects state from [EventPlanningViewModel], handles the event-saved navigation side effect, and
 * delegates all rendering (form, pickers, Save button) to the shared [EventPlanningScreen].
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
  val focusManager = LocalFocusManager.current

  LaunchedEffect(uiState.isEventAdded) {
    if (uiState.isEventAdded) {
      viewModel.onEventAddedHandled()
      onEventSaved()
    }
  }

  // clear form if it was previously being edited
  LaunchedEffect(Unit) { if (uiState.isEditing) viewModel.resetForm() }

  BackHandler {
    focusManager.clearFocus()
    onBackClick()
  }

  EventPlanningScreen(
      uiState = uiState,
      onBackClick = onBackClick,
      onTypeChange = viewModel::setType,
      onDateSelected = viewModel::setDate,
      onStartTimeSelected = viewModel::setStartTime,
      onEndTimeSelected = viewModel::setEndTime,
      onSubjectSelected = viewModel::setSubject,
      onLocationChange = viewModel::setLocation,
      onDescriptionChange = viewModel::setDescription,
      onAddTask = viewModel::addTask,
      onToggleTask = viewModel::toggleTask,
      onDeleteTask = viewModel::removeTask,
      onSaveClick = viewModel::addEvent,
      isEditing = false,
      modifier = modifier,
      onTypeClick = onPickTypeClick,
      onPickDateClick = onPickDateClick,
      onPickStartTimeClick = onPickStartTimeClick,
      onPickEndTimeClick = onPickEndTimeClick,
      onPickSubjectClick = onPickSubjectClick,
  )
}

@Preview
@Composable
private fun AddEventScreenPreview() {
  MaterialTheme {
    EventPlanningScreenStateless(
        uiState =
            EventPlanningUiState(
                type = EventType.COURSE,
                subject = "Modèles stochastiques",
                location = "CM3",
                description = "Bring course pack and notes.",
                tasks = listOf(EventTask("Read Chapter 4"), EventTask("Do homework 2")),
            ),
        onBackClick = {},
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
