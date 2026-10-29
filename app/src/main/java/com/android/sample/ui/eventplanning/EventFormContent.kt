// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.android.sample.R
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import com.android.sample.ui.theme.Dimens
import java.time.format.DateTimeFormatter

/**
 * Renders the complete scrollable event planning form.
 *
 * This composable encapsulates the event input fields (type, date, start/end times,
 * subject, location, description, and task checklist) and error banners. It is designed
 * to be decoupled from screen chrome to enable reuse between Add and Edit event screens.
 *
 * @param uiState Current UI state containing form values, tasks, and validation errors.
 * @param onTypeClick Invoked when the user taps the event type selector pill.
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
 */
@Composable
fun EventFormContent(
    uiState: EventPlanningUiState,
    onTypeClick: () -> Unit,
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
) {
  val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  Column(
      modifier =
          modifier
              .verticalScroll(rememberScrollState())
              .padding(
                  horizontal = Dimens.formHorizontalPadding,
                  vertical = Dimens.formVerticalPadding,
              )
              .testTag(Tags.SCROLL_CONTAINER),
      verticalArrangement = Arrangement.spacedBy(Dimens.formSpacing),
  ) {
    // Type
    SectionLabel(
        text = stringResource(R.string.event_planning_type_label),
        testTag = Tags.TYPE_LABEL,
    )
    SelectorFieldPill(
        valueText =
            uiState.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
        placeholderText = stringResource(R.string.event_planning_type_placeholder),
        onClick = onTypeClick,
        testTag = Tags.TYPE_SELECTOR,
        hasDropdownArrow = true,
    )

    // Date
    SectionLabel(
        text = stringResource(R.string.event_planning_date_label),
        testTag = Tags.DATE_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.date?.format(dateFormatter),
        placeholderText = stringResource(R.string.event_planning_date_placeholder),
        onClick = onDateClick,
        testTag = Tags.DATE_SELECTOR,
    )

    // Start time
    SectionLabel(
        text = stringResource(R.string.event_planning_start_time_label),
        testTag = Tags.START_TIME_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.startTime?.format(timeFormatter),
        placeholderText = stringResource(R.string.event_planning_time_placeholder),
        onClick = onStartTimeClick,
        testTag = Tags.START_TIME_SELECTOR,
    )

    // End time
    SectionLabel(
        text = stringResource(R.string.event_planning_end_time_label),
        testTag = Tags.END_TIME_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.endTime?.format(timeFormatter),
        placeholderText = stringResource(R.string.event_planning_time_placeholder),
        onClick = onEndTimeClick,
        testTag = Tags.END_TIME_SELECTOR,
    )

    // Subject
    val subjectLabelText =
        if (uiState.isSubjectRequired) stringResource(R.string.event_planning_subject_label)
        else stringResource(R.string.event_planning_subject_optional_label)
    SectionLabel(text = subjectLabelText, testTag = Tags.SUBJECT_LABEL)
    SelectorFieldPill(
        valueText = uiState.subject,
        placeholderText = stringResource(R.string.event_planning_select_or_enter_placeholder),
        onClick = onSubjectClick,
        testTag = Tags.SUBJECT_SELECTOR,
        hasDropdownArrow = true,
    )

    // Location (optional)
    SectionLabel(
        text = stringResource(R.string.event_planning_location_optional_label),
        testTag = Tags.LOCATION_LABEL,
    )
    OutlinedInputField(
        value = uiState.location,
        onValueChange = onLocationChange,
        placeholder = stringResource(R.string.event_planning_select_or_enter_placeholder),
        testTag = Tags.LOCATION_INPUT,
    )

    // Description (optional)
    SectionLabel(
        text = stringResource(R.string.event_planning_description_optional_label),
        testTag = Tags.DESCRIPTION_LABEL,
    )
    OutlinedInputField(
        value = uiState.description,
        onValueChange = onDescriptionChange,
        placeholder = stringResource(R.string.event_planning_description_placeholder),
        testTag = Tags.DESCRIPTION_INPUT,
    )

    // Tasks (optional)
    SectionLabel(
        text = stringResource(R.string.event_planning_tasks_optional_label),
        testTag = Tags.TASKS_LABEL,
    )
    AddTaskInputField(
        onAddTask = onAddTask,
        testTag = Tags.ADD_TASK_BUTTON,
    )

    uiState.tasks.forEachIndexed { index, task ->
      TaskRowItem(
          task = task,
          onToggle = { onToggleTask(index) },
          onDelete = { onDeleteTask(index) },
          itemTag = "${Tags.TASK_ITEM_PREFIX}$index",
          checkboxTag = "${Tags.TASK_CHECKBOX_PREFIX}$index",
          deleteTag = "${Tags.TASK_DELETE_PREFIX}$index",
      )
    }

    // Display validation or save errors if present
    uiState.error?.let { err ->
      Text(
          text = err,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
          modifier =
              Modifier.padding(vertical = Dimens.errorVerticalPadding).testTag(Tags.ERROR_TEXT),
      )
    }
  }
}
