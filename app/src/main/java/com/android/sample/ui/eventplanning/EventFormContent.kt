// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude AI Agent
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.eventplanning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.ui.sharedComponents.AddTaskInputField
import com.android.sample.ui.sharedComponents.EventTypeDropdownMenu
import com.android.sample.ui.sharedComponents.FormContentTestTags
import com.android.sample.ui.sharedComponents.OutlinedInputField
import com.android.sample.ui.sharedComponents.SectionLabel
import com.android.sample.ui.sharedComponents.SelectorFieldPill
import com.android.sample.ui.sharedComponents.TaskRowItem
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.label
import java.time.format.DateTimeFormatter

/**
 * Callbacks for managing tasks within personal events.
 *
 * Enforces at compile-time that all task-related actions must be supplied together.
 *
 * @property onAddTask Called with a non-blank task title submitted via the keyboard action.
 * @property onToggleTask Called with the index of the task to toggle completion.
 * @property onDeleteTask Called with the index of the task to delete.
 */
data class TaskActions(
    val onAddTask: (String) -> Unit,
    val onToggleTask: (Int) -> Unit,
    val onDeleteTask: (Int) -> Unit,
)

/**
 * Renders the complete scrollable form layout shared by personal events and group sessions.
 *
 * Takes [EventPlanningUiState] and user interaction callbacks. Conditionally displays the tasks
 * section when [EventPlanningUiState.showTasksSection] is true (for personal events), and marks
 * missing required fields with validation errors when [isValidationError] is true.
 *
 * @param modifier Modifier applied to the scrollable form container.
 * @param uiState Current field values, event kind, tasks and ViewModel error state.
 * @param isValidationError Whether required inputs should display validation error states.
 * @param onTypeChange Called when the event type changes.
 * @param onDateClick Invoked when the date selector is pressed.
 * @param onStartTimeClick Invoked when the start-time selector is pressed.
 * @param onEndTimeClick Invoked when the end-time selector is pressed.
 * @param onSubjectClick Invoked when the subject selector is pressed.
 * @param onLocationChange Called when the location field changes.
 * @param onDescriptionChange Called when the description field changes.
 * @param taskActions Callbacks for managing tasks in personal events; null for group sessions.
 * @param onTypeClick Optional external event-type picker action; when null, uses built-in dropdown.
 */
@Composable
fun EventFormContent(
    modifier: Modifier = Modifier,
    uiState: EventPlanningUiState,
    isValidationError: Boolean =
        uiState.error == EventPlanningError.EVENT_DATE_AND_TIMES_REQUIRED ||
            uiState.error == EventPlanningError.INVALID_EVENT,
    onTypeChange: (EventType) -> Unit,
    onDateClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSubjectClick: () -> Unit,
    onLocationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    taskActions: TaskActions? = null,
    onTypeClick: (() -> Unit)? = null,
) {
  val focusManager = LocalFocusManager.current
  val density = LocalDensity.current
  val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
  val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
  var showTypeMenu by rememberSaveable { mutableStateOf(false) }
  var pillWidth by remember { mutableStateOf(Dimens.noPadding) }

  val isDateMissing = isValidationError && uiState.date == null
  val isStartTimeMissing = isValidationError && uiState.startTime == null
  val isEndTimeMissing = isValidationError && uiState.endTime == null
  val isSubjectMissing = isValidationError && uiState.isSubjectRequired && uiState.subject.isBlank()

  Column(
      modifier =
          modifier
              .verticalScroll(rememberScrollState())
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
              ) {
                focusManager.clearFocus()
              }
              .padding(
                  horizontal = Dimens.formHorizontalPadding,
                  vertical = Dimens.formVerticalPadding,
              )
              .testTag(FormContentTestTags.SCROLL_CONTAINER),
      verticalArrangement = Arrangement.spacedBy(Dimens.formSpacing),
  ) {
    // Type
    SectionLabel(
        text = stringResource(R.string.event_planning_type_label),
        testTag = FormContentTestTags.TYPE_LABEL,
    )
    Box(
        modifier =
            Modifier.fillMaxWidth().onSizeChanged { size ->
              pillWidth = with(density) { size.width.toDp() }
            }
    ) {
      SelectorFieldPill(
          valueText = uiState.type.label(),
          placeholderText = stringResource(R.string.event_planning_type_placeholder),
          onClick = {
            focusManager.clearFocus()
            if (onTypeClick != null) {
              onTypeClick()
            } else {
              showTypeMenu = true
            }
          },
          testTag = FormContentTestTags.TYPE_SELECTOR,
          hasDropdownArrow = true,
      )

      EventTypeDropdownMenu(
          expanded = showTypeMenu,
          onDismissRequest = { showTypeMenu = false },
          onTypeSelected = { selectedType ->
            onTypeChange(selectedType)
            showTypeMenu = false
          },
          modifier = Modifier.width(pillWidth),
      )
    }

    // Date
    SectionLabel(
        text = stringResource(R.string.event_planning_date_label),
        testTag = FormContentTestTags.DATE_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.date?.format(dateFormatter),
        placeholderText = stringResource(R.string.event_planning_date_placeholder),
        onClick = {
          focusManager.clearFocus()
          onDateClick()
        },
        testTag = FormContentTestTags.DATE_SELECTOR,
        isError = isDateMissing,
    )

    // Start time
    SectionLabel(
        text = stringResource(R.string.event_planning_start_time_label),
        testTag = FormContentTestTags.START_TIME_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.startTime?.format(timeFormatter),
        placeholderText = stringResource(R.string.event_planning_time_placeholder),
        onClick = {
          focusManager.clearFocus()
          onStartTimeClick()
        },
        testTag = FormContentTestTags.START_TIME_SELECTOR,
        isError = isStartTimeMissing,
    )

    // End time
    SectionLabel(
        text = stringResource(R.string.event_planning_end_time_label),
        testTag = FormContentTestTags.END_TIME_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.endTime?.format(timeFormatter),
        placeholderText = stringResource(R.string.event_planning_time_placeholder),
        onClick = {
          focusManager.clearFocus()
          onEndTimeClick()
        },
        testTag = FormContentTestTags.END_TIME_SELECTOR,
        isError = isEndTimeMissing,
    )

    // Informational warning for overnight events
    val endsNextDay =
        uiState.startTime != null &&
            uiState.endTime != null &&
            uiState.endTime.isBefore(uiState.startTime)

    if (endsNextDay) {
      Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
              Modifier.padding(start = Dimens.cornerSmall, top = Spacing.extraSmall)
                  .testTag(FormContentTestTags.ENDS_NEXT_DAY_WARNING),
      ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.iconSizeSmall),
        )
        Spacer(modifier = Modifier.width(Spacing.extraSmall))
        Text(
            text = stringResource(R.string.event_planning_ends_next_day_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    // Subject
    val subjectLabelText =
        if (uiState.isSubjectRequired) stringResource(R.string.event_planning_subject_label)
        else stringResource(R.string.event_planning_subject_optional_label)
    SectionLabel(text = subjectLabelText, testTag = FormContentTestTags.SUBJECT_LABEL)
    SelectorFieldPill(
        valueText = uiState.subject,
        placeholderText = stringResource(R.string.event_planning_select_or_enter_placeholder),
        onClick = {
          focusManager.clearFocus()
          onSubjectClick()
        },
        testTag = FormContentTestTags.SUBJECT_SELECTOR,
        hasDropdownArrow = true,
        isError = isSubjectMissing,
    )

    // Location (optional)
    SectionLabel(
        text = stringResource(R.string.event_planning_location_optional_label),
        testTag = FormContentTestTags.LOCATION_LABEL,
    )
    OutlinedInputField(
        value = uiState.location,
        onValueChange = onLocationChange,
        placeholder = stringResource(R.string.event_planning_select_or_enter_placeholder),
        testTag = FormContentTestTags.LOCATION_INPUT,
    )

    // Description (optional)
    SectionLabel(
        text = stringResource(R.string.event_planning_description_optional_label),
        testTag = FormContentTestTags.DESCRIPTION_LABEL,
    )
    OutlinedInputField(
        value = uiState.description,
        onValueChange = onDescriptionChange,
        placeholder = stringResource(R.string.event_planning_description_placeholder),
        testTag = FormContentTestTags.DESCRIPTION_INPUT,
    )

    // Tasks (optional, personal events only)
    if (uiState.showTasksSection) {
      val taskActionsRequiredError =
          stringResource(R.string.event_planning_error_task_actions_required)
      requireNotNull(taskActions) { taskActionsRequiredError }
      SectionLabel(
          text = stringResource(R.string.event_planning_tasks_optional_label),
          testTag = FormContentTestTags.TASKS_LABEL,
      )
      AddTaskInputField(
          onAddTask = taskActions.onAddTask,
          testTag = FormContentTestTags.ADD_TASK_BUTTON,
      )

      uiState.tasks.forEachIndexed { index, task ->
        TaskRowItem(
            task = task,
            onToggle = {
              focusManager.clearFocus()
              taskActions.onToggleTask(index)
            },
            onDelete = {
              focusManager.clearFocus()
              taskActions.onDeleteTask(index)
            },
            itemTag = "${FormContentTestTags.TASK_ITEM_PREFIX}$index",
            checkboxTag = "${FormContentTestTags.TASK_CHECKBOX_PREFIX}$index",
            deleteTag = "${FormContentTestTags.TASK_DELETE_PREFIX}$index",
        )
      }
    }
  }
}
