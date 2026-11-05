// Co-authored-by: Copilot
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
import java.time.LocalDate
import java.time.LocalTime

/**
 * Loads an existing event into the shared event form and persists edits only when Save is pressed.
 *
 * The ViewModel owns form validation and persistence. This screen coordinates loading, successful
 * save/delete acknowledgements and navigation; [EventPlanningScreen] renders the shared form and
 * built-in pickers. Leaving the screen discards the current form state.
 *
 * @param eventId ID of the event to load for editing.
 * @param modifier Modifier applied to the screen.
 * @param viewModel ViewModel backing the event editing workflow; defaults to the screen's
 *   repository-backed ViewModel.
 * @param onBackClick Invoked by the top-bar back button or system back action; unsaved edits are
 *   discarded.
 * @param onEventSaved Invoked after edited event persistence succeeds and the ViewModel result is
 *   acknowledged.
 * @param onEventDelete Invoked after deletion succeeds and the ViewModel result is acknowledged.
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
fun EditEventScreen(
    eventId: String,
    modifier: Modifier = Modifier,
    viewModel: EventPlanningViewModel = eventsPlanningViewModel(),
    onBackClick: () -> Unit,
    onEventSaved: () -> Unit,
    onEventDelete: () -> Unit,
    onPickTypeClick: (() -> Unit)? = null,
    onPickDateClick: (() -> Unit)? = null,
    onPickStartTimeClick: (() -> Unit)? = null,
    onPickEndTimeClick: (() -> Unit)? = null,
    onPickSubjectClick: (() -> Unit)? = null,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val focusManager = LocalFocusManager.current

  LaunchedEffect(viewModel, eventId) { viewModel.loadEvent(eventId) }

  LaunchedEffect(uiState.isEventEdited) {
    if (uiState.isEventEdited) {
      viewModel.onEventEditedHandled()
      onEventSaved()
    }
  }

  LaunchedEffect(uiState.isEventDeleted) {
    if (uiState.isEventDeleted) {
      viewModel.onEventDeletedHandled()
      onEventDelete()
    }
  }

  // clears the form when exiting
  val handleBack: () -> Unit = {
    focusManager.clearFocus()
    viewModel.resetForm()
    onBackClick()
  }

  BackHandler(onBack = handleBack)

  EventPlanningScreen(
      uiState = uiState,
      onBackClick = handleBack,
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
      onSaveClick = viewModel::editEvent,
      onDeleteClick = { viewModel.deleteEvent(eventId) },
      isEditing = true,
      isLoading = uiState.isLoading,
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
private fun EditEventScreenPreview() {
  MaterialTheme {
    EventPlanningScreenStateless(
        uiState =
            EventPlanningUiState(
                type = EventType.STUDY_SESSION,
                subject = "ModStoch",
                date = LocalDate.of(2026, 10, 24),
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(17, 0),
                location = "1018, Lausanne",
                description = "REVISE FOR MIDTERM!!!",
                tasks = listOf(EventTask("Read Chapter 4"), EventTask("Do homework 2")),
                isEditing = true,
            ),
        onBackClick = {},
        onTypeChange = {},
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
        onDeleteClick = {},
        isEditing = true,
    )
  }
}
