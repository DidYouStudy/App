// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Sonnet 5.5

package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.android.sample.model.event.EventType
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.color
import com.android.sample.ui.theme.label
import com.android.sample.ui.theme.onColor

/**
 * Colored badge showing the label of an [EventType]. Shared by the group session cards and the
 * event details screen.
 */
@Composable
fun EventTypeBadge(
    type: EventType,
    modifier: Modifier = Modifier,
) {
  Surface(
      shape = RoundedCornerShape(Dimens.cornerSmall),
      color = type.color,
      modifier = modifier,
  ) {
    Text(
        text = type.label(),
        modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
        color = type.onColor,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
    )
  }
}

/** Leading icon followed by a single line of text, used for date/time and location rows. */
@Composable
fun IconTextRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
  Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(Dimens.iconSizeMedium),
    )
    Spacer(Modifier.width(Spacing.small))
    Text(text = text, style = textStyle)
  }
}

/**
 * Read-only-styled checklist row (checkbox + text) with the same look as [TaskRowItem] but without
 * the delete action.
 */
@Composable
fun TaskCheckRow(
    text: String,
    isCompleted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    checkboxTag: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(Dimens.cornerMedium))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(
                  horizontal = Dimens.taskRowHorizontalPadding,
                  vertical = Dimens.taskRowVerticalPadding,
              ),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
      Checkbox(
          checked = isCompleted,
          enabled = enabled,
          onCheckedChange = { onToggle() },
          colors =
              CheckboxDefaults.colors(
                  checkedColor = MaterialTheme.colorScheme.primary,
                  uncheckedColor = MaterialTheme.colorScheme.onSecondaryContainer,
                  checkmarkColor = MaterialTheme.colorScheme.onPrimary,
              ),
          modifier = if (checkboxTag != null) Modifier.testTag(checkboxTag) else Modifier,
      )
      Text(
          text = text,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(start = Dimens.taskTextStartPadding),
      )
    }
    trailingContent?.invoke()
  }
}
