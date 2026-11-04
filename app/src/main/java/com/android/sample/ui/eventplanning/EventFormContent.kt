// Co-authored-by: Gemini AI Agent
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
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import java.time.format.DateTimeFormatter

/**
 * Renders the complete scrollable event planning form.
 *
 * This composable encapsulates the event input fields (type, date, start/end times, subject,
 * location, description, and task checklist) and error banners. It is designed to be decoupled from
 * screen chrome to enable reuse between Add and Edit event screens.
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
  val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
  val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  var showTypeMenu by rememberSaveable { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val density = LocalDensity.current
  var pillWidth by remember { mutableStateOf(Dimens.noPadding) }

  /** Per-component errors: */
  val isValidationError =
      uiState.error == EventPlanningViewModel.EVENT_DATE_AND_TIMES_REQUIRED_ERROR ||
          uiState.error == EventPlanningViewModel.INVALID_EVENT_ERROR

  val isDateMissing = isValidationError && uiState.date == null
  val isStartTimeMissing = isValidationError && uiState.startTime == null
  val isEndTimeMissing = isValidationError && uiState.endTime == null
  val isSubjectMissing = isValidationError && uiState.isSubjectRequired && uiState.subject.isBlank()

  Column(
      modifier =
          modifier
              .verticalScroll(rememberScrollState())
              // Clear focus when tapping blank space in the scrollable form without a ripple
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
              .testTag(Tags.SCROLL_CONTAINER),
      verticalArrangement = Arrangement.spacedBy(Dimens.formSpacing),
  ) {
    // Type
    SectionLabel(
        text = stringResource(R.string.event_planning_type_label),
        testTag = Tags.TYPE_LABEL,
    )
    Box(
        modifier =
            Modifier.fillMaxWidth().onSizeChanged { size ->
              pillWidth = with(density) { size.width.toDp() }
            }
    ) {
      SelectorFieldPill(
          valueText =
              uiState.type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
          placeholderText = stringResource(R.string.event_planning_type_placeholder),
          onClick = {
            focusManager.clearFocus()
            if (onTypeClick != null) {
              onTypeClick()
            } else {
              showTypeMenu = true
            }
          },
          testTag = Tags.TYPE_SELECTOR,
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
        testTag = Tags.DATE_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.date?.format(dateFormatter),
        placeholderText = stringResource(R.string.event_planning_date_placeholder),
        onClick = {
          focusManager.clearFocus()
          onDateClick()
        },
        testTag = Tags.DATE_SELECTOR,
        isError = isDateMissing,
    )

    // Start time
    SectionLabel(
        text = stringResource(R.string.event_planning_start_time_label),
        testTag = Tags.START_TIME_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.startTime?.format(timeFormatter),
        placeholderText = stringResource(R.string.event_planning_time_placeholder),
        onClick = {
          focusManager.clearFocus()
          onStartTimeClick()
        },
        testTag = Tags.START_TIME_SELECTOR,
        isError = isStartTimeMissing,
    )

    // End time
    SectionLabel(
        text = stringResource(R.string.event_planning_end_time_label),
        testTag = Tags.END_TIME_LABEL,
    )
    SelectorFieldPill(
        valueText = uiState.endTime?.format(timeFormatter),
        placeholderText = stringResource(R.string.event_planning_time_placeholder),
        onClick = {
          focusManager.clearFocus()
          onEndTimeClick()
        },
        testTag = Tags.END_TIME_SELECTOR,
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
                  .testTag(Tags.ENDS_NEXT_DAY_WARNING),
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
    SectionLabel(text = subjectLabelText, testTag = Tags.SUBJECT_LABEL)
    SelectorFieldPill(
        valueText = uiState.subject,
        placeholderText = stringResource(R.string.event_planning_select_or_enter_placeholder),
        onClick = {
          focusManager.clearFocus()
          onSubjectClick()
        },
        testTag = Tags.SUBJECT_SELECTOR,
        hasDropdownArrow = true,
        isError = isSubjectMissing,
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
          onToggle = {
            focusManager.clearFocus()
            onToggleTask(index)
          },
          onDelete = {
            focusManager.clearFocus()
            onDeleteTask(index)
          },
          itemTag = "${Tags.TASK_ITEM_PREFIX}$index",
          checkboxTag = "${Tags.TASK_CHECKBOX_PREFIX}$index",
          deleteTag = "${Tags.TASK_DELETE_PREFIX}$index",
      )
    }
  }
}
