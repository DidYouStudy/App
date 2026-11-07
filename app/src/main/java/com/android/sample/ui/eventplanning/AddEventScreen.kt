// Co-authored-by: Gemini AI Agent
// Co-authored-by: Gemini <noreply@google.com>
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
 * Collects form state from [EventPlanningViewModel], handles successful-save acknowledgement and
 * navigation, and delegates form and picker rendering to [EventPlanningScreen]. The event is
 * persisted only when the user presses Save.
 *
 * @param modifier Modifier applied to the root screen layout.
 * @param initialKind Category of event to create (defaults to [EventKind.PERSONAL]).
 * @param viewModel ViewModel backing the event creation workflow; defaults to the screen's
 *   repository-backed ViewModel.
 * @param onBackClick Invoked by the top-bar back button or system back action.
 * @param onEventSaved Invoked after a successful save has been acknowledged by the ViewModel.
 * @param onPickTypeClick Optional external event-type picker action; when null, the screen uses its
 *   built-in type selector.
 * @param onPickDateClick Optional external date picker action; when null, the screen uses its
 *   built-in date picker.
 * @param onPickStartTimeClick Optional external start-time picker action; when null, the screen
 *   uses its built-in time picker.
 * @param onPickEndTimeClick Optional external end-time picker action; when null, the screen uses
 *   its built-in time picker.
 * @param onPickSubjectClick Optional external subject picker action; when null, the screen uses its
 *   built-in subject picker.
 */
@Composable
fun AddEventScreen(
    modifier: Modifier = Modifier,
    initialKind: EventKind = EventKind.PERSONAL,
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

  LaunchedEffect(initialKind) { viewModel.setEventKind(initialKind) }

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
internal fun AddEventScreenPreview() {
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
