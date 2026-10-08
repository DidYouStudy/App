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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags

// ---------------------------------------------------------------------------
// Building blocks / Helper methods
// ---------------------------------------------------------------------------

/*
    Builds the title of the screen
 */
@Composable
internal fun PreferenceHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Complete your profile",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(Tags.PREFERENCE_TITLE),
        )
    }
}

/*
    Builds the label containers for the preference sections
 */
@Composable
internal fun SectionLabel(text: String, testTag: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .padding(top = 8.dp)
            .testTag(testTag),
    )
}

/*
    Builds the wide buttons used for import, add and save
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
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag),
    ) {
        Text(text)
    }
}

/*
    Builds the row of preference options for time_of_day for multiple choice
 */
@Composable
internal fun <T : PreferenceOption> MultiChoiceRow(
    options: List<T>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) = ChoiceRow(
    options = options,
    isSelected = { it in selected },
    onClick = onToggle,
    role = Role.Checkbox,
    modifier = modifier,
)

/*
    Builds the row of preference options for study_length, break_duration and break_frequency for
    single choice
 */
@Composable
internal fun <T : PreferenceOption> SingleChoiceRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) = ChoiceRow(
    options = options,
    isSelected = { it == selected },
    onClick = onSelect,
    role = Role.RadioButton,
    modifier = modifier,
)

/*
    Formats the shape of the pill (preferences) buttons
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

/*
    Builds a row of pill buttons / preference options for the given role
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
                modifier = Modifier
                    .weight(1f)
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
                    color = if (selected) MaterialTheme.colorScheme.onSecondary
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
