// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.eventplanning

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.sample.model.event.EventType
import com.android.sample.ui.sharedComponents.FormContent

/**
 * Renders the complete scrollable event planning form for personal events.
 *
 * Delegated to [FormContent] in `sharedComponents` with tasks section enabled.
 *
 * @param uiState Current UI state containing form values, tasks, and validation errors.
 * @param onTypeChange Callback when event type is selected.
 * @param onDateClick Invoked when the user taps the date selector pill.
 * @param onStartTimeClick Invoked when the user taps the start time selector pill.
 * @param onEndTimeClick Invoked when the user taps the end time selector pill.
 * @param onSubjectClick Invoked when the user taps the subject selector pill.
 * @param onLocationChange Callback when the text in the location field changes.
 * @param onDescriptionChange Callback when the text in the description field changes.
 * @param onAddTask Callback invoked with the non-blank task title when submitted via IME Done.
 * @param onToggleTask Callback invoked with the index of the task to toggle completion.
 * @param onDeleteTask Callback invoked with the index of the task to delete.
 * @param modifier Modifier applied to the outer scrollable container.
 * @param onTypeClick Invoked when the user taps the event type selector pill.
 */
@Composable
fun EventFormContent(
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
    modifier: Modifier = Modifier,
    onTypeClick: (() -> Unit)? = null,
) {
  val isValidationError =
      uiState.error == EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR ||
          uiState.error == EventPlanningViewModel.INVALID_EVENT_ERROR

  val isDateMissing = isValidationError && uiState.date == null
  val isStartTimeMissing = isValidationError && uiState.startTime == null
  val isEndTimeMissing = isValidationError && uiState.endTime == null
  val isSubjectMissing = isValidationError && uiState.isSubjectRequired && uiState.subject.isBlank()

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
      onLocationChange = onLocationChange,
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
}
