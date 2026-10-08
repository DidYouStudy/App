// Co-authored-by: Claude AI Agent
package com.android.sample.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags

// ---------------------------------------------------------------------------
// Interface for representing all preferences
// ---------------------------------------------------------------------------

interface PreferenceOption {
    val label: String
    val testTag: String
}

// ---------------------------------------------------------------------------
// Enum Classes for representing the available preferences
// ---------------------------------------------------------------------------

enum class StudyTime(override val label: String, override val testTag: String) : PreferenceOption {
    MORNING("Morning", Tags.STUDY_TIME_MORNING),
    AFTERNOON("Afternoon", Tags.STUDY_TIME_AFTERNOON),
    EVENING("Evening", Tags.STUDY_TIME_EVENING),
    NIGHT("Night", Tags.STUDY_TIME_NIGHT),
}

enum class SessionLength(override val label: String, override val testTag: String) : PreferenceOption {
    THIRTY_MIN("30 min", Tags.SESSION_LENGTH_30_MIN),
    ONE_HOUR("1 hour", Tags.SESSION_LENGTH_1_HOUR),
    TWO_HOURS("2 hours", Tags.SESSION_LENGTH_2_HOURS),
    THREE_HOURS("3 hours", Tags.SESSION_LENGTH_3_HOURS),
}

enum class BreakDuration(override val label: String, override val testTag: String) : PreferenceOption {
    FIVE_MIN("5 minutes", Tags.BREAK_DURATION_5_MIN),
    TEN_MIN("10 minutes", Tags.BREAK_DURATION_10_MIN),
    FIFTEEN_MIN("15 minutes", Tags.BREAK_DURATION_15_MIN),
    TWENTY_MIN("20 minutes", Tags.BREAK_DURATION_20_MIN),
}

enum class BreakFrequency(override val label: String, override val testTag: String) : PreferenceOption {
    EVERY_30_MIN("Every 30 min", Tags.BREAK_FREQUENCY_30_MIN),
    EVERY_45_MIN("Every 45 min", Tags.BREAK_FREQUENCY_45_MIN),
    EVERY_HOUR("Every hour", Tags.BREAK_FREQUENCY_1_HOUR),
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

/*
    Actual stateful preference screen
 */
@Composable
fun PreferenceScreen() {}

/*
    Stateless preference screen used for the Success state and for previewing
 */
@Composable
fun PreferenceScreenStateless(
    selectedStudyTimes: Set<StudyTime>,
    selectedSessionLength: SessionLength,
    selectedBreakDuration: BreakDuration,
    selectedBreakFrequency: BreakFrequency,
    modifier: Modifier = Modifier,
    onImportClick: () -> Unit,
    onAddLocationClick: () -> Unit,
    onStudyTimeToggle: (StudyTime) -> Unit,
    onSessionLengthSelect: (SessionLength) -> Unit,
    onBreakDurationSelect: (BreakDuration) -> Unit,
    onBreakFrequencySelect: (BreakFrequency) -> Unit,
    onSaveClick: () -> Unit,
) {
    // outer box which contains the full screen content
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(Tags.PREFERENCE_SCREEN),
    ) {

        // inner container for the preference content
        Column(modifier = Modifier.fillMaxSize()) {

            // helper method which builds the title of the screen
            PreferenceHeader()

            // component which contains the scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(Tags.PREFERENCE_SCROLL_CONTAINER),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // import schedule section
                SectionLabel("Import course schedule", Tags.IMPORT_SCHEDULE_LABEL)
                WidePillButton("Import...", onImportClick, Tags.IMPORT_SCHEDULE_BUTTON)

                // add study locations section
                SectionLabel("Add your study locations", Tags.STUDY_LOCATIONS_LABEL)
                WidePillButton("Add...", onAddLocationClick, Tags.ADD_STUDY_LOCATION_BUTTON)

                // time_of_day preference section
                SectionLabel("When do you prefer to study?", Tags.STUDY_TIME_LABEL)
                MultiChoiceRow(
                    options = StudyTime.entries,
                    selected = selectedStudyTimes,
                    onToggle = onStudyTimeToggle,
                )

                // study_length preference section
                SectionLabel("How long do you want your study sessions to be?", Tags.SESSION_LENGTH_LABEL)
                SingleChoiceRow(
                    options = SessionLength.entries,
                    selected = selectedSessionLength,
                    onSelect = onSessionLengthSelect,
                )

                // break_duration preference section
                SectionLabel("How much break time do you prefer?", Tags.BREAK_DURATION_LABEL)
                SingleChoiceRow(
                    options = BreakDuration.entries,
                    selected = selectedBreakDuration,
                    onSelect = onBreakDurationSelect,
                )

                // break_frequency preference section
                SectionLabel("How often would you like to take breaks?", Tags.BREAK_FREQUENCY_LABEL)
                SingleChoiceRow(
                    options = BreakFrequency.entries,
                    selected = selectedBreakFrequency,
                    onSelect = onBreakFrequencySelect,
                )

                // Keeps the last row clear of the pinned Save button
                Spacer(Modifier.height(88.dp))
            }
        }

        // Save button
        WidePillButton(
            text = "Save",
            onClick = onSaveClick,
            testTag = Tags.SAVE_BUTTON,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Building blocks / Helper methods
// ---------------------------------------------------------------------------

/*
    Builds the title of the screen
 */
@Composable
private fun PreferenceHeader(modifier: Modifier = Modifier) {
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
private fun SectionLabel(text: String, testTag: String, modifier: Modifier = Modifier) {
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
private fun WidePillButton(
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
private fun <T : PreferenceOption> MultiChoiceRow(
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
private fun <T : PreferenceOption> SingleChoiceRow(
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
private fun segmentShape(index: Int, count: Int, selected: Boolean): Shape {
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
private fun <T : PreferenceOption> ChoiceRow(
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

// ---------------------------------------------------------------------------
// Preview with the selected preferences being the ones from the mockup on Figma
// ---------------------------------------------------------------------------

@Preview//(showBackground = true, showSystemUi = true)
@Composable
private fun PreferenceScreenPreview() {
    MaterialTheme {
        PreferenceScreenStateless(
            selectedStudyTimes = setOf(StudyTime.MORNING, StudyTime.AFTERNOON),
            selectedSessionLength = SessionLength.ONE_HOUR,
            selectedBreakDuration = BreakDuration.TEN_MIN,
            selectedBreakFrequency = BreakFrequency.EVERY_45_MIN,
            onImportClick = {},
            onAddLocationClick = {},
            onStudyTimeToggle = {},
            onSessionLengthSelect = {},
            onBreakDurationSelect = {},
            onBreakFrequencySelect = {},
            onSaveClick = {},
        )
    }
}
