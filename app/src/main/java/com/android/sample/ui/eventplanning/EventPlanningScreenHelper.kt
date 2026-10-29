// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.android.sample.R
import com.android.sample.model.event.EventTask
import com.android.sample.ui.theme.Dimens

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
) {
  val shape = RoundedCornerShape(Dimens.cornerPill)
  Box(
      modifier =
          modifier
              .fillMaxWidth()
              .heightIn(min = Dimens.pillMinHeight)
              .clip(shape)
              .background(MaterialTheme.colorScheme.secondaryContainer)
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
              else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
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
  val shape = RoundedCornerShape(Dimens.cornerPill)
  TextField(
      value = value,
      onValueChange = onValueChange,
      placeholder = {
        Text(
            text = placeholder,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
      },
      minLines = minLines,
      colors =
          TextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
              unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
              disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
              focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
              unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
              cursorColor = MaterialTheme.colorScheme.primary,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
          ),
      shape = shape,
      modifier =
          modifier
              .fillMaxWidth()
              .border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
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
  val shape = RoundedCornerShape(Dimens.cornerLarge)
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(shape)
              .background(MaterialTheme.colorScheme.secondaryContainer)
              .padding(
                  horizontal = Dimens.taskRowHorizontalPadding,
                  vertical = Dimens.taskRowVerticalPadding,
              )
              .testTag(itemTag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
    ) {
      Checkbox(
          checked = task.isCompleted,
          onCheckedChange = { onToggle() },
          colors =
              CheckboxDefaults.colors(
                  checkedColor = MaterialTheme.colorScheme.primary,
                  uncheckedColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  checkmarkColor = MaterialTheme.colorScheme.onPrimary,
              ),
          modifier = Modifier.testTag(checkboxTag),
      )
      Text(
          text = task.text,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(start = Dimens.taskTextStartPadding),
      )
    }
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
  var text by remember { mutableStateOf("") }
  val shape = RoundedCornerShape(Dimens.cornerPill)

  TextField(
      value = text,
      onValueChange = { text = it },
      placeholder = {
        Text(
            text = stringResource(R.string.event_planning_add_task_placeholder),
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
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
      colors =
          TextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
              unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
              disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
              focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
              unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
              cursorColor = MaterialTheme.colorScheme.primary,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
          ),
      shape = shape,
      modifier =
          modifier
              .fillMaxWidth()
              .border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
              .testTag(testTag),
  )
}
