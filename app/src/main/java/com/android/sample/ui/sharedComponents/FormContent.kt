// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

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
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Generic scrollable form layout that can be shared between events and group sessions. */
@Composable
fun FormContent(
    type: EventType,
    subject: String,
    date: LocalDate?,
    startTime: LocalTime?,
    endTime: LocalTime?,
    location: String,
    description: String,
    isSubjectRequired: Boolean,
    onTypeChange: (EventType) -> Unit,
    onDateClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSubjectClick: () -> Unit,
    onLocationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tasks: List<EventTask> = emptyList(),
    showTasksSection: Boolean = true,
    isDateMissing: Boolean = false,
    isStartTimeMissing: Boolean = false,
    isEndTimeMissing: Boolean = false,
    isSubjectMissing: Boolean = false,
    onAddTask: ((String) -> Unit)? = null,
    onToggleTask: ((Int) -> Unit)? = null,
    onDeleteTask: ((Int) -> Unit)? = null,
    onTypeClick: (() -> Unit)? = null,
) {
  val focusManager = LocalFocusManager.current
  val density = LocalDensity.current
  val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
  val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
  var showTypeMenu by rememberSaveable { mutableStateOf(false) }
  var pillWidth by remember { mutableStateOf(Dimens.noPadding) }

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
          valueText = type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
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
        valueText = date?.format(dateFormatter),
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
        valueText = startTime?.format(timeFormatter),
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
        valueText = endTime?.format(timeFormatter),
        placeholderText = stringResource(R.string.event_planning_time_placeholder),
        onClick = {
          focusManager.clearFocus()
          onEndTimeClick()
        },
        testTag = FormContentTestTags.END_TIME_SELECTOR,
        isError = isEndTimeMissing,
    )

    // Informational warning for overnight events
    val endsNextDay = startTime != null && endTime != null && endTime.isBefore(startTime)

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
        if (isSubjectRequired) stringResource(R.string.event_planning_subject_label)
        else stringResource(R.string.event_planning_subject_optional_label)
    SectionLabel(text = subjectLabelText, testTag = FormContentTestTags.SUBJECT_LABEL)
    SelectorFieldPill(
        valueText = subject,
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
        value = location,
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
        value = description,
        onValueChange = onDescriptionChange,
        placeholder = stringResource(R.string.event_planning_description_placeholder),
        testTag = FormContentTestTags.DESCRIPTION_INPUT,
    )

    // Tasks (optional)
    if (showTasksSection && onAddTask != null) {
      SectionLabel(
          text = stringResource(R.string.event_planning_tasks_optional_label),
          testTag = FormContentTestTags.TASKS_LABEL,
      )
      AddTaskInputField(
          onAddTask = onAddTask,
          testTag = FormContentTestTags.ADD_TASK_BUTTON,
      )

      tasks.forEachIndexed { index, task ->
        TaskRowItem(
            task = task,
            onToggle = {
              focusManager.clearFocus()
              onToggleTask?.invoke(index)
            },
            onDelete = {
              focusManager.clearFocus()
              onDeleteTask?.invoke(index)
            },
            itemTag = "${FormContentTestTags.TASK_ITEM_PREFIX}$index",
            checkboxTag = "${FormContentTestTags.TASK_CHECKBOX_PREFIX}$index",
            deleteTag = "${FormContentTestTags.TASK_DELETE_PREFIX}$index",
        )
      }
    }
  }
}
