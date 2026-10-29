// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.ui.eventplanning.EventPlanningScreenTestTags as Tags
import com.android.sample.ui.theme.Dimens

/**
 * Stateful destination screen for creating and saving a new event.
 *
 * Collects state from [EventPlanningViewModel], handles the event-saved navigation side effect, and
 * embeds [AddEventScreenStateless] inside a Scaffold.
 *
 * @param modifier Modifier applied to the root screen layout.
 * @param viewModel ViewModel backing the event creation workflow.
 * @param onBackClick Invoked when the top app bar navigation icon is pressed.
 * @param onEventSaved Invoked once the event has been successfully persisted.
 * @param onPickTypeClick Intent to trigger the type selection modal.
 * @param onPickDateClick Intent to trigger the date picker dialog.
 * @param onPickStartTimeClick Intent to trigger the start time picker dialog.
 * @param onPickEndTimeClick Intent to trigger the end time picker dialog.
 * @param onPickSubjectClick Intent to trigger the subject search modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    modifier: Modifier = Modifier,
    viewModel: EventPlanningViewModel = eventsPlanningViewModel(),
    onBackClick: () -> Unit,
    onEventSaved: () -> Unit,
    onPickTypeClick: () -> Unit = {},
    onPickDateClick: () -> Unit = {},
    onPickStartTimeClick: () -> Unit = {},
    onPickEndTimeClick: () -> Unit = {},
    onPickSubjectClick: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(uiState.isEventAdded) {
    if (uiState.isEventAdded) {
      viewModel.onEventAddedHandled()
      onEventSaved()
    }
  }

  Scaffold(
      topBar = {
        MediumTopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.event_planning_add_title),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(Tags.TOP_BAR_TITLE),
              )
            },
            navigationIcon = {
              IconButton(onClick = onBackClick, modifier = Modifier.testTag(Tags.BACK_BUTTON)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.button_go_back),
                )
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
            modifier = Modifier.testTag(Tags.TOP_APP_BAR),
        )
      },
      modifier = modifier.fillMaxSize(),
  ) { innerPadding ->
    AddEventScreenStateless(
        uiState = uiState,
        onTypeClick = onPickTypeClick,
        onDateClick = onPickDateClick,
        onStartTimeClick = onPickStartTimeClick,
        onEndTimeClick = onPickEndTimeClick,
        onSubjectClick = onPickSubjectClick,
        onLocationChange = viewModel::setLocation,
        onDescriptionChange = viewModel::setDescription,
        onAddTask = viewModel::addTask,
        onToggleTask = viewModel::toggleTask,
        onDeleteTask = viewModel::removeTask,
        onSaveClick = viewModel::addEvent,
        modifier = Modifier.padding(innerPadding),
    )
  }
}

/** Stateless AddEventScreen displaying form content and the pinned Save button. */
@Composable
fun AddEventScreenStateless(
    uiState: EventPlanningUiState,
    onTypeClick: () -> Unit,
    onDateClick: () -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    onSubjectClick: () -> Unit,
    onLocationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (Int) -> Unit,
    onDeleteTask: (Int) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(Tags.SCREEN_CONTAINER),
  ) {
    // 1. Scrollable form takes all remaining vertical space ABOVE the button
    EventFormContent(
        uiState = uiState,
        onTypeClick = onTypeClick,
        onDateClick = onDateClick,
        onStartTimeClick = onStartTimeClick,
        onEndTimeClick = onEndTimeClick,
        onSubjectClick = onSubjectClick,
        onLocationChange = onLocationChange,
        onDescriptionChange = onDescriptionChange,
        onAddTask = onAddTask,
        onToggleTask = onToggleTask,
        onDeleteTask = onDeleteTask,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    )

    // 2. Button container sits physically below the form, never overlapping it
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
      Button(
          onClick = onSaveClick,
          enabled = !uiState.isSaving,
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                  disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                  disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
              ),
          shape = RoundedCornerShape(Dimens.cornerPill),
          modifier =
              Modifier.fillMaxWidth()
                  .padding(
                      horizontal = Dimens.saveButtonHorizontalPadding,
                      vertical = Dimens.saveButtonVerticalPadding,
                  )
                  .height(Dimens.saveButtonHeight)
                  .testTag(Tags.SAVE_BUTTON),
      ) {
        if (uiState.isSaving) {
          CircularProgressIndicator(
              modifier =
                  Modifier.size(Dimens.saveButtonProgressSize).testTag(Tags.SAVE_BUTTON_PROGRESS),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = Dimens.progressIndicatorStrokeWidth,
          )
        } else {
          Text(
              text = stringResource(R.string.event_planning_save_button),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onPrimary,
          )
        }
      }
    }
  }
}

@Preview
@Composable
private fun AddEventScreenPreview() {
  MaterialTheme {
    AddEventScreenStateless(
        uiState =
            EventPlanningUiState(
                type = EventType.COURSE,
                subject = "Modèles stochastiques",
                location = "CM3",
                description = "Bring course pack and notes.",
                tasks = listOf(EventTask("Read Chapter 4"), EventTask("Do homework 2")),
            ),
        onTypeClick = {},
        onDateClick = {},
        onStartTimeClick = {},
        onEndTimeClick = {},
        onSubjectClick = {},
        onLocationChange = {},
        onDescriptionChange = {},
        onAddTask = {},
        onToggleTask = {},
        onDeleteTask = {},
        onSaveClick = {},
    )
  }
}
