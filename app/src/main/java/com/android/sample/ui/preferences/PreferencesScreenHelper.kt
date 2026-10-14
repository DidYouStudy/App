// Co-authored-by: Claude AI Agent
package com.android.sample.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.sample.R
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags

// ---------------------------------------------------------------------------
// Building blocks / Helper methods
// ---------------------------------------------------------------------------

/**
 * Displays the preferences screen title.
 *
 * @param modifier Modifier applied to the title.
 */
@Composable
internal fun PreferencesHeader(modifier: Modifier = Modifier) {
  Text(
      text = stringResource(id = R.string.preferences_header),
      style = MaterialTheme.typography.displaySmall,
      fontWeight = FontWeight.Bold,
      modifier =
          modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
              .statusBarsPadding()
              .padding(horizontal = 16.dp, vertical = 16.dp)
              .testTag(Tags.PREFERENCE_TITLE),
  )
}

/**
 * Displays a bold label for a preference section.
 *
 * @param text Text shown as the section label.
 * @param testTag Test tag applied to the label for UI tests.
 * @param modifier Modifier applied to the label.
 */
@Composable
internal fun SectionLabel(text: String, testTag: String, modifier: Modifier = Modifier) {
  Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      modifier = modifier.padding(top = 8.dp).testTag(testTag),
  )
}

/**
 * Displays a full-width pill-shaped button.
 *
 * @param text Text shown inside the button.
 * @param onClick Action invoked when the button is clicked.
 * @param testTag Test tag applied to the button for UI tests.
 * @param modifier Modifier applied to the button.
 */
@Composable
internal fun WidePillButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
  FilledTonalButton(
      onClick = onClick,
      shape = CircleShape,
      modifier = modifier.fillMaxWidth().height(56.dp).testTag(testTag),
  ) {
    Text(text)
  }
}

/**
 * Displays a row of selectable options that may be selected independently.
 *
 * @param options Options shown in the row.
 * @param selected Currently selected options.
 * @param onToggle Called with an option when it is toggled.
 * @param modifier Modifier applied to the row.
 */
@Composable
internal fun <T : PreferenceOption> MultiChoiceRow(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) =
    ChoiceRow(
        options = options,
        isSelected = { it in selected },
        onClick = onToggle,
        role = Role.Checkbox,
        modifier = modifier,
    )

/**
 * Displays a row of options where only one option may be selected.
 *
 * @param options Options shown in the row.
 * @param selected Currently selected option.
 * @param onSelect Called with an option when it is selected.
 * @param modifier Modifier applied to the row.
 */
@Composable
internal fun <T : PreferenceOption> SingleChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) =
    ChoiceRow(
        options = options,
        isSelected = { it == selected },
        onClick = onSelect,
        role = Role.RadioButton,
        modifier = modifier,
    )

/**
 * Returns the shape for an option segment based on its position and selection state.
 *
 * @param index Zero-based position of the segment in the row.
 * @param count Number of segments in the row.
 * @param selected Whether the segment is selected.
 * @return A circular shape for selected segments, or a shape with rounded outer corners otherwise.
 */
@Composable
internal fun segmentShape(index: Int, count: Int, selected: Boolean): Shape {
  if (selected) return CircleShape

  val full = CornerSize(50)
  val small = CornerSize(4.dp)
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
 * Displays selectable options with selection semantics and a role.
 *
 * @param options Options shown in the row.
 * @param isSelected Returns whether an option is currently selected.
 * @param onClick Called with an option when it is selected.
 * @param role Accessibility role assigned to each option.
 * @param modifier Modifier applied to the row.
 */
@Composable
internal fun <T : PreferenceOption> ChoiceRow(
    options: List<T>,
    isSelected: (T) -> Boolean,
    onClick: (T) -> Unit,
    role: Role,
    modifier: Modifier = Modifier,
) {
  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    options.forEachIndexed { index, option ->
      val selected = isSelected(option)
      Box(
          modifier =
              Modifier.weight(1f)
                  .height(40.dp)
                  .clip(segmentShape(index, options.size, selected))
                  .background(
                      if (selected) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.secondaryContainer
                  )
                  .selectable(
                      selected = selected,
                      role = role,
                      onClick = { onClick(option) },
                  )
                  .testTag(option.testTag),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = option.label,
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
