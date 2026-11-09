// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.eventplanning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.sample.model.event.EventType
import com.android.sample.ui.sharedComponents.FormContent
import com.android.sample.ui.sharedComponents.LocationPickerDialog

/**
 * Renders the complete scrollable event-planning form for personal events.
 *
 * Adapts [FormContent] from `sharedComponents` to [EventPlanningUiState] and enables the task
 * section. When [isValidationError] is true, missing required date, times and subject are marked
 * invalid; by default, this is derived from the ViewModel's required-field and invalid-event
 * errors.
 *
 * Also owns the [LocationPickerDialog]'s open/closed state: tapping the location field opens it,
 * and its confirmed selection is reported through [onLocationChange].
 *
 * @param modifier Modifier applied to the scrollable form container.
 * @param uiState Current field values, event tasks and ViewModel error state.
 * @param isValidationError Whether required inputs should display validation error states.
 * @param onTypeChange Called when the event type changes.
 * @param onDateClick Invoked when the date selector is pressed.
 * @param onStartTimeClick Invoked when the start-time selector is pressed.
 * @param onEndTimeClick Invoked when the end-time selector is pressed.
 * @param onSubjectClick Invoked when the subject selector is pressed.
 * @param onLocationChange Called with the location picked from [LocationPickerDialog].
 * @param onLocationSearchQueryChange Called with the location picker's search text as the user
 *   types, so the ViewModel can resolve matches via the campus location repository.
 * @param onDescriptionChange Called when the description field changes.
 * @param onAddTask Called with a non-blank task title submitted via the keyboard action.
 * @param onToggleTask Called with the index of the task to toggle completion.
 * @param onDeleteTask Called with the index of the task to delete.
 * @param onTypeClick Optional external event-type picker action; when null, [FormContent] uses its
 *   built-in type dropdown.
 */
@Composable
fun EventFormContent(
    modifier: Modifier = Modifier,
    uiState: EventPlanningUiState,
    isValidationError: Boolean =
        uiState.error == EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR ||
            uiState.error == EventPlanningViewModel.INVALID_EVENT_ERROR,
    onTypeChange: (EventType) -> Unit,
    onDateClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSubjectClick: () -> Unit,
    onLocationChange: (String) -> Unit,
    onLocationSearchQueryChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onTypeClick: (() -> Unit)? = null,
) {

  val isDateMissing = isValidationError && uiState.date == null
  val isStartTimeMissing = isValidationError && uiState.startTime == null
  val isEndTimeMissing = isValidationError && uiState.endTime == null
  val isSubjectMissing = isValidationError && uiState.isSubjectRequired && uiState.subject.isBlank()
  var showLocationPicker by rememberSaveable { mutableStateOf(false) }

  FormContent(
      type = uiState.type,
      subject = uiState.subject,
      date = uiState.date,
      startTime = uiState.startTime,
      endTime = uiState.endTime,
      location = uiState.location,
      description = uiState.description,
      isSubjectRequired = uiState.isSubjectRequired,
      onTypeChange = onTypeChange,
      onDateClick = onDateClick,
      onStartTimeClick = onStartTimeClick,
      onEndTimeClick = onEndTimeClick,
      onSubjectClick = onSubjectClick,
      onLocationClick = { showLocationPicker = true },
      onDescriptionChange = onDescriptionChange,
      modifier = modifier,
      tasks = uiState.tasks,
      showTasksSection = true,
      isDateMissing = isDateMissing,
      isStartTimeMissing = isStartTimeMissing,
      isEndTimeMissing = isEndTimeMissing,
      isSubjectMissing = isSubjectMissing,
      onAddTask = onAddTask,
      onToggleTask = onToggleTask,
      onDeleteTask = onDeleteTask,
      onTypeClick = onTypeClick,
  )

  if (showLocationPicker) {
    LocationPickerDialog(
        searchResults = uiState.locationSearchResults,
        onDismiss = { showLocationPicker = false },
        onSearchQueryChange = onLocationSearchQueryChange,
        onConfirm = { location ->
          onLocationChange(location)
          showLocationPicker = false
        },
    )
  }
}
