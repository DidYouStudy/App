// Co-authored-by: Claude AI Agent
package com.android.sample.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags

/*
   Actual stateful preference screen
*/
@Composable
fun PreferencesScreen(
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = viewModel(),
    onImportClick: () -> Unit,
    onAddLocationClick: () -> Unit,
    onSaved: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()

  when (val state = uiState) {
    // if the screen should be loading
    is PreferencesUiState.Loading ->
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }

    // if an error occurred
    is PreferencesUiState.Error ->
        Column(
            modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(state.message)
          Button(
              onClick = viewModel::loadPreferences,
          ) {
            Text("Retry")
          }
        }

    is PreferencesUiState.Success -> {
      var draft by remember(state.preferences) { mutableStateOf(state.preferences) }
      PreferenceScreenStateless(
          selectedStudyTimes = draft.preferredStudyTimes.map { StudyTime.from(it) }.toSet(),
          selectedSessionLength = SessionLength.fromMinutes(draft.sessionLengthMinutes),
          selectedBreakDuration = BreakDuration.fromMinutes(draft.breakLengthMinutes),
          selectedBreakFrequency = BreakFrequency.fromMinutes(draft.breakFrequencyMinutes),
          onImportClick = onImportClick,
          onAddLocationClick = onAddLocationClick,
          onStudyTimeToggle = { time ->
            val tod = time.timeOfDay
            draft =
                draft.copy(
                    preferredStudyTimes =
                        if (tod in draft.preferredStudyTimes) draft.preferredStudyTimes - tod
                        else draft.preferredStudyTimes + tod
                )
          },
          onSessionLengthSelect = { draft = draft.copy(sessionLengthMinutes = it.minutes) },
          onBreakDurationSelect = { draft = draft.copy(breakLengthMinutes = it.minutes) },
          onBreakFrequencySelect = { draft = draft.copy(breakFrequencyMinutes = it.minutes) },
          onSaveClick = {
            viewModel.updatePreferences(draft)
            onSaved()
          },
          modifier = modifier,
      )
    }
  }
}

/*
   Stateless preference screen used for the Success state and for previewing
*/
@Composable
fun PreferenceScreenStateless(
    modifier: Modifier = Modifier,
    selectedStudyTimes: Set<StudyTime>,
    selectedSessionLength: SessionLength,
    selectedBreakDuration: BreakDuration,
    selectedBreakFrequency: BreakFrequency,
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
      modifier =
          modifier
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
          modifier =
              Modifier.weight(1f)
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
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
    )
  }
}

/*
   Preview with the selected preferences being the ones from the mockup on Figma
*/

@Preview // (showBackground = true, showSystemUi = true)
@Composable
private fun PreferencesScreenPreview() {
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
