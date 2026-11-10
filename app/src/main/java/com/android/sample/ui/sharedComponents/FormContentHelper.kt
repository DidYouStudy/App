// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.sample.R
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.ui.theme.AlphaTokens
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.label
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.launch

/** Renders a bold section title label for form fields. */
@Composable
fun SectionLabel(text: String, testTag: String) {
  Text(
      text = text,
      color = MaterialTheme.colorScheme.onBackground,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.testTag(testTag),
  )
}

/** Clickable pill styled identically to dropdowns and date/time pickers using theme colors. */
@Composable
fun SelectorFieldPill(
    valueText: String?,
    placeholderText: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    hasDropdownArrow: Boolean = false,
    isError: Boolean = false,
) {
  val shape = RoundedCornerShape(Dimens.cornerPill)
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(min = Dimens.pillMinHeight)
              .clip(shape)
              .background(MaterialTheme.colorScheme.secondaryContainer)
              .then(
                  if (isError) {
                    Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.error, shape)
                  } else {
                    Modifier
                  }
              )
              .clickable(onClick = onClick)
              .padding(
                  horizontal = Dimens.pillHorizontalPadding,
                  vertical = Dimens.pillVerticalPadding,
              )
              .testTag(testTag),
      contentAlignment = Alignment.CenterStart,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = if (!valueText.isNullOrBlank()) valueText else placeholderText,
          color =
              if (!valueText.isNullOrBlank()) MaterialTheme.colorScheme.onSecondaryContainer
              else
                  MaterialTheme.colorScheme.onSecondaryContainer.copy(
                      alpha = AlphaTokens.PLACEHOLDER
                  ),
          style = MaterialTheme.typography.bodyMedium,
      )
      if (hasDropdownArrow) {
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}

/** Expandable multi-line field with the primary stroke and secondary container fill. */
@Composable
fun OutlinedInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
) {
  var isFocused by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(Dimens.cornerPill)
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  val coroutineScope = rememberCoroutineScope()
  TextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = {
        Text(
            text = placeholder,
            color =
                MaterialTheme.colorScheme.onSecondaryContainer.copy(
                    alpha = AlphaTokens.PLACEHOLDER
                ),
        )
      },
      minLines = minLines,
      colors = formTextFieldColors(),
      shape = shape,
      modifier =
          modifier
              .fillMaxWidth()
              .bringIntoViewRequester(bringIntoViewRequester)
              .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                  coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                }
              }
              .then(
                  if (isFocused) {
                    Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
                  } else {
                    Modifier
                  }
              )
              .testTag(testTag),
  )
}

/** Single task checklist entry displaying a checkbox, label, and delete action. */
@Composable
fun TaskRowItem(
    task: EventTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    itemTag: String,
    checkboxTag: String,
    deleteTag: String,
) {
  TaskCheckRow(
      text = task.text,
      isCompleted = task.isCompleted,
      onToggle = onToggle,
      modifier = Modifier.testTag(itemTag),
      checkboxTag = checkboxTag,
  ) {
    IconButton(onClick = onDelete, modifier = Modifier.testTag(deleteTag)) {
      Icon(
          imageVector = Icons.Outlined.Delete,
          contentDescription =
              stringResource(R.string.event_planning_delete_task_content_description),
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

/** Pill button that triggers adding a checklist item. */
@Composable
fun AddTaskInputField(
    onAddTask: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
  var text by rememberSaveable { mutableStateOf("") }
  var isFocused by remember { mutableStateOf(false) }
  val shape = RoundedCornerShape(Dimens.cornerPill)
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  val coroutineScope = rememberCoroutineScope()

  TextField(
      value = text,
      onValueChange = { text = it },
      placeholder = {
        Text(
            text = stringResource(R.string.event_planning_add_task_placeholder),
            color =
                MaterialTheme.colorScheme.onSecondaryContainer.copy(
                    alpha = AlphaTokens.PLACEHOLDER
                ),
        )
      },
      leadingIcon = {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions =
          KeyboardActions(
              onDone = {
                if (text.isNotBlank()) {
                  onAddTask(text)
                  text = ""
                }
              }
          ),
      colors = formTextFieldColors(),
      shape = shape,
      modifier =
          modifier
              .fillMaxWidth()
              .bringIntoViewRequester(bringIntoViewRequester)
              .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused) {
                  coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                }
              }
              .then(
                  if (isFocused) {
                    Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
                  } else {
                    Modifier
                  }
              )
              .testTag(testTag),
  )
}

/**
 * Full-width pill button that shows a progress indicator instead of its label while in progress.
 */
@Composable
fun PillActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isInProgress: Boolean,
    label: String,
    containerColor: Color,
    contentColor: Color,
    buttonTag: String,
    progressTag: String,
    icon: ImageVector? = null,
) {
  Button(
      onClick = onClick,
      enabled = enabled,
      colors =
          ButtonDefaults.buttonColors(
              containerColor = containerColor,
              contentColor = contentColor,
              disabledContainerColor = containerColor.copy(alpha = AlphaTokens.DISABLED),
              disabledContentColor = contentColor.copy(alpha = AlphaTokens.DISABLED),
          ),
      shape = RoundedCornerShape(Dimens.cornerPill),
      modifier = Modifier.fillMaxWidth().height(Dimens.saveButtonHeight).testTag(buttonTag),
  ) {
    if (isInProgress) {
      CircularProgressIndicator(
          modifier = Modifier.size(Dimens.saveButtonProgressSize).testTag(progressTag),
          color = contentColor,
          strokeWidth = Dimens.progressIndicatorStrokeWidth,
      )
    } else {
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
          Icon(imageVector = icon, contentDescription = null, tint = contentColor)
          Spacer(modifier = Modifier.width(Spacing.extraSmall))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
        )
      }
    }
  }
}

/** Material 3 Type dropdown menu matching the types from EventType. */
@Composable
fun EventTypeDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onTypeSelected: (EventType) -> Unit,
    modifier: Modifier = Modifier,
) {
  MaterialTheme(
      shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(Dimens.cornerLarge))
  ) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(Dimens.cornerLarge),
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
      EventType.entries.forEach { type ->
        DropdownMenuItem(
            text = {
              Text(
                  text = type.label(),
                  style = MaterialTheme.typography.bodyLarge,
              )
            },
            onClick = {
              onTypeSelected(type)
              onDismissRequest()
            },
        )
        if (type != EventType.entries.last()) {
          HorizontalDivider(
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.OUTLINE)
          )
        }
      }
    }
  }
}

/** Material 3 DatePickerDialog configured for text input mode. */
@Composable
fun EventDatePickerDialog(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
  val initialMillis =
      initialDate?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli()
          ?: System.currentTimeMillis()

  val datePickerState =
      rememberDatePickerState(
          initialSelectedDateMillis = initialMillis,
          initialDisplayMode = DisplayMode.Input,
      )

  DatePickerDialog(
      onDismissRequest = onDismiss,
      confirmButton = {
        TextButton(
            onClick = {
              datePickerState.selectedDateMillis?.let { millis ->
                val selectedDate =
                    Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                onDateSelected(selectedDate)
              }
              onDismiss()
            }
        ) {
          Text(stringResource(R.string.confirm))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  ) {
    DatePicker(state = datePickerState)
  }
}

/** Material 3 TimePickerDialog using the dial clock picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventTimePickerDialog(
    initialTime: LocalTime?,
    title: String,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
  val defaultTime = initialTime ?: LocalTime.now()
  val timePickerState =
      rememberTimePickerState(
          initialHour = defaultTime.hour,
          initialMinute = defaultTime.minute,
          is24Hour = true,
      )

  AlertDialog(
      onDismissRequest = onDismiss,
      confirmButton = {
        TextButton(
            onClick = {
              onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
              onDismiss()
            }
        ) {
          Text(stringResource(R.string.confirm))
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
      title = { Text(text = title, style = MaterialTheme.typography.labelLarge) },
      text = { Column(modifier = Modifier.fillMaxWidth()) { TimePicker(state = timePickerState) } },
  )
}

/** Subject Picker Dialog content with integrated search and custom new subject entry. */
@Composable
fun SubjectPickerDialogContent(
    currentSelection: String,
    allSubjects: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }

  val filteredSubjects =
      remember(searchQuery, allSubjects) {
        if (searchQuery.isBlank()) allSubjects
        else allSubjects.filter { it.contains(searchQuery, ignoreCase = true) }
      }

  val isNewSubject =
      searchQuery.isNotBlank() &&
          allSubjects.none { subject -> subject.equals(searchQuery.trim(), ignoreCase = true) }

  Surface(
      shape = RoundedCornerShape(Dimens.dialogCornerRadius),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.formHorizontalPadding),
  ) {
    Column(modifier = Modifier.padding(Dimens.formHorizontalPadding)) {
      Text(
          text = stringResource(R.string.event_planning_select_subject_title),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(modifier = Modifier.height(Dimens.cornerLarge))

      Column(modifier = Modifier.fillMaxWidth().heightIn(max = Dimens.subjectPickerMaxHeight)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
              Text(stringResource(R.string.event_planning_subject_search_placeholder))
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Dimens.cornerMedium))

        Surface(
            shape = RoundedCornerShape(Dimens.cornerLarge),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
          LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (isNewSubject) {
              item {
                ListItem(
                    headlineContent = {
                      Text(
                          text = "+ Use \"${searchQuery.trim()}\"",
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.primary,
                      )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().clickable { onConfirm(searchQuery.trim()) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              }
            }

            itemsIndexed(filteredSubjects) { index, subject ->
              ListItem(
                  headlineContent = { Text(subject) },
                  colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                  modifier = Modifier.fillMaxWidth().clickable { onConfirm(subject) },
              )
              if (index < filteredSubjects.lastIndex) {
                HorizontalDivider(
                    color =
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = AlphaTokens.OUTLINE)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(Dimens.formHorizontalPadding))

      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
      ) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        Spacer(modifier = Modifier.width(Dimens.cornerSmall))
        TextButton(
            onClick = {
              val choice =
                  if (searchQuery.isNotBlank()) searchQuery.trim() else currentSelection.trim()
              if (choice.isNotBlank()) onConfirm(choice) else onDismiss()
            }
        ) {
          Text(stringResource(R.string.confirm))
        }
      }
    }
  }
}

/** Subject Picker Dialog wrapper. */
@Composable
fun SubjectPickerDialog(
    currentSelection: String,
    allSubjects: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
  Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    SubjectPickerDialogContent(
        currentSelection = currentSelection,
        allSubjects = allSubjects,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
  }
}

@Composable
private fun formTextFieldColors(): TextFieldColors =
    TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
        unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

/** Test tags for reusable form components. */
object FormContentTestTags {
  const val SCROLL_CONTAINER = "form_content_scroll_container"

  // Section Labels
  const val TYPE_LABEL = "form_content_type_label"
  const val DATE_LABEL = "form_content_date_label"
  const val START_TIME_LABEL = "form_content_start_time_label"
  const val END_TIME_LABEL = "form_content_end_time_label"
  const val SUBJECT_LABEL = "form_content_subject_label"
  const val LOCATION_LABEL = "form_content_location_label"
  const val DESCRIPTION_LABEL = "form_content_description_label"
  const val TASKS_LABEL = "form_content_tasks_label"

  // Interactive Elements / Selectors
  const val TYPE_SELECTOR = "form_content_type_selector"
  const val DATE_SELECTOR = "form_content_date_selector"
  const val START_TIME_SELECTOR = "form_content_start_time_selector"
  const val END_TIME_SELECTOR = "form_content_end_time_selector"
  const val SUBJECT_SELECTOR = "form_content_subject_selector"
  const val LOCATION_INPUT = "form_content_location_input"
  const val DESCRIPTION_INPUT = "form_content_description_input"

  // Tasks
  const val ADD_TASK_BUTTON = "form_content_add_task_button"
  const val TASK_ITEM_PREFIX = "form_content_task_item_"
  const val TASK_CHECKBOX_PREFIX = "form_content_task_checkbox_"
  const val TASK_DELETE_PREFIX = "form_content_task_delete_"

  // Actions & Feedback
  const val ENDS_NEXT_DAY_WARNING = "form_content_ends_next_day_warning"
}
