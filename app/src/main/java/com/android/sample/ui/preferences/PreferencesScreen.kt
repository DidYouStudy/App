// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.preferences.PreferencesScreenTestTags as Tags

/*
   Actual stateful preference screen
*/
/**
 * Displays the preference screen, loading saved preferences and allowing the user to edit and save
 * them.
 *
 * @param modifier Modifier applied to the screen and its content.
 * @param viewModel ViewModel that loads and updates the user's preferences.
 * @param onImportClick Called when the user requests to import a schedule.
 * @param onAddLocationClick Called when the user requests to add study locations.
 * @param onSaved Called after the user saves the edited preferences.
 * @param onBack Called when the user taps the back button. When non-null the screen is in edit mode
 *   (opened from Settings): it shows a back button and the "Preferences" title instead of the
 *   onboarding title. When null (first-time onboarding) there is no way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    modifier: Modifier = Modifier,
    viewModel: PreferencesViewModel = preferencesViewModel(),
    onImportClick: () -> Unit,
    onAddLocationClick: () -> Unit,
    onSaved: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
  Scaffold(
      topBar = {
        MediumTopAppBar(
            title = {
              PreferencesHeader(
                  title =
                      stringResource(
                          id =
                              if (onBack == null) R.string.preferences_header
                              else R.string.preferences_edit_header
                      )
              )
            },
            // Edit mode only: returns to Settings without saving.
            navigationIcon = {
              if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
                ) {
                  Icon(
                      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                      contentDescription = stringResource(id = R.string.button_go_back),
                  )
                }
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
            modifier = modifier.testTag(NavigationTestTags.TOP_BAR_TITLE),
        )
      },
      modifier = modifier.fillMaxSize(),
      content = { pd ->
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

        when (val state = uiState) {
          // if the screen should be loading
          is PreferencesUiState.Loading ->
              Box(modifier.fillMaxSize().padding(pd), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
              }

          // if an error occurred
          is PreferencesUiState.Error ->
              Column(
                  modifier.fillMaxSize().padding(pd),
                  verticalArrangement = Arrangement.Center,
                  horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Text(state.message)
                Button(
                    onClick = viewModel::loadPreferences,
                ) {
                  Text(stringResource(id = R.string.preferences_retry_button_on_error))
                }
              }

          is PreferencesUiState.Success -> {
            var draft by remember(state.preferences) { mutableStateOf(state.preferences) }
            PreferenceScreenStateless(
                selectedStudyTimes = draft.preferredStudyTimes.map { StudyTime.from(it) },
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
                onSaveClick = { viewModel.updatePreferences(draft, onSaved) },
                saveEnabled = !isSaving,
                modifier = modifier.padding(pd),
            )
          }
        }
      },
  )
}

/*
   Stateless preference screen used for the Success state and for previewing
*/
/**
 * Displays the preference controls for an already-loaded preference state.
 *
 * @param modifier Modifier applied to the screen content.
 * @param selectedStudyTimes Currently selected study times.
 * @param selectedSessionLength Currently selected study session length.
 * @param selectedBreakDuration Currently selected break duration.
 * @param selectedBreakFrequency Currently selected break frequency.
 * @param onImportClick Called when the user requests to import a schedule.
 * @param onAddLocationClick Called when the user requests to add study locations.
 * @param onStudyTimeToggle Called when a study-time option is toggled.
 * @param onSessionLengthSelect Called when a session length is selected.
 * @param onBreakDurationSelect Called when a break duration is selected.
 * @param onBreakFrequencySelect Called when a break frequency is selected.
 * @param onSaveClick Called when the user saves the selected preferences.
 * @param saveEnabled Whether the Save button accepts taps (false while a save is in flight).
 */
@Composable
fun PreferenceScreenStateless(
    modifier: Modifier = Modifier,
    selectedStudyTimes: List<StudyTime>,
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
    saveEnabled: Boolean = true,
) {
  // outer box which contains the full screen content
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(Tags.PREFERENCE_SCREEN),
  ) {

    // inner container for the preference content
    Column(modifier = Modifier.fillMaxSize()) {

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
        SectionLabel(
            stringResource(id = R.string.preferences_import_schedule_label),
            Tags.IMPORT_SCHEDULE_LABEL,
        )
        WidePillButton(
            stringResource(id = R.string.preferences_import_schedule_button),
            onImportClick,
            Tags.IMPORT_SCHEDULE_BUTTON,
        )

        // add study locations section
        SectionLabel(
            stringResource(id = R.string.preferences_add_study_locations_label),
            Tags.STUDY_LOCATIONS_LABEL,
        )
        WidePillButton(
            stringResource(id = R.string.preferences_add_study_locations_button),
            onAddLocationClick,
            Tags.ADD_STUDY_LOCATION_BUTTON,
        )

        // time_of_day preference section
        SectionLabel(
            stringResource(id = R.string.preferences_study_time_label),
            Tags.STUDY_TIME_LABEL,
        )
        MultiChoiceRow(
            options = StudyTime.entries,
            selected = selectedStudyTimes.toSet(),
            onToggle = onStudyTimeToggle,
        )

        // study_length preference section
        SectionLabel(
            stringResource(id = R.string.preferences_session_length_label),
            Tags.SESSION_LENGTH_LABEL,
        )
        SingleChoiceRow(
            options = SessionLength.entries,
            selected = selectedSessionLength,
            onSelect = onSessionLengthSelect,
        )

        // break_duration preference section
        SectionLabel(
            stringResource(id = R.string.preferences_break_duration_label),
            Tags.BREAK_DURATION_LABEL,
        )
        SingleChoiceRow(
            options = BreakDuration.entries,
            selected = selectedBreakDuration,
            onSelect = onBreakDurationSelect,
        )

        // break_frequency preference section
        SectionLabel(
            stringResource(id = R.string.preferences_break_frequency_label),
            Tags.BREAK_FREQUENCY_LABEL,
        )
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
        enabled = saveEnabled,
    )
  }
}

/*
   Preview with the selected preferences being the ones from the mockup on Figma
*/

@Preview
@Composable
private fun PreferencesScreenPreview() {
  MaterialTheme {
    PreferenceScreenStateless(
        selectedStudyTimes = listOf(StudyTime.MORNING, StudyTime.AFTERNOON),
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
