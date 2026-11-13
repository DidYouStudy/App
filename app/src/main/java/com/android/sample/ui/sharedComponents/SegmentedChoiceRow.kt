// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/**
 * Returns the shape for a segment in a [SegmentedChoiceRow] based on its position index, total item
 * count, and whether it is currently selected.
 *
 * @param index Zero-based position of the segment in the row.
 * @param count Total number of segments in the row.
 * @param selected Whether the segment is selected.
 * @return A circular shape if selected; otherwise a shape with rounded outer corners.
 */
@Composable
fun segmentShape(index: Int, count: Int, selected: Boolean): Shape {
  if (selected) return CircleShape

  val full = CornerSize(50)
  val small = CornerSize(Dimens.cornerExtraSmall)
  val isFirst = index == 0
  val isLast = index == count - 1

  return RoundedCornerShape(
      topStart = if (isFirst) full else small,
      bottomStart = if (isFirst) full else small,
      topEnd = if (isLast) full else small,
      bottomEnd = if (isLast) full else small,
  )
}

/**
 * Displays a segmented row of selectable options with Material 3 styling and selection semantics.
 *
 * @param T The type of option items.
 * @param items List of option items to display in the row.
 * @param isSelected Returns whether an item is currently selected.
 * @param onClick Called when an item is tapped.
 * @param label Provides the text label for an item.
 * @param testTag Provides the test tag for an item.
 * @param role Accessibility role assigned to each option (e.g. [Role.RadioButton] or
 *   [Role.Checkbox]).
 * @param modifier Modifier applied to the row.
 * @param height Height of each segment choice.
 */
@Composable
fun <T> SegmentedChoiceRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    onClick: (T) -> Unit,
    label: @Composable (T) -> String,
    testTag: (T) -> String,
    role: Role,
    modifier: Modifier = Modifier,
    height: Dp = Dimens.preferencesChoiceHeight,
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(Spacing.extraExtraSmall),
  ) {
    items.forEachIndexed { index, item ->
      val selected = isSelected(item)
      Box(
          modifier =
              Modifier.weight(1f)
                  .height(height)
                  .clip(segmentShape(index, items.size, selected))
                  .background(
                      if (selected) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.secondaryContainer
                  )
                  .selectable(
                      selected = selected,
                      role = role,
                      onClick = { onClick(item) },
                  )
                  .testTag(testTag(item)),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = label(item),
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            color =
                if (selected) MaterialTheme.colorScheme.onSecondary
                else MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}
