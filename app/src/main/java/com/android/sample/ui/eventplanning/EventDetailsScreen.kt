// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Sonnet 5.5

package com.android.sample.ui.eventplanning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.ui.sharedComponents.EventTypeBadge
import com.android.sample.ui.sharedComponents.IconTextRow
import com.android.sample.ui.sharedComponents.PillActionButton
import com.android.sample.ui.sharedComponents.TaskCheckRow
import com.android.sample.ui.theme.Dimens

/** Test tags for the Event details screen. */
object EventDetailsScreenTestTags {
  const val SCREEN = "eventDetailsScreen"
  const val TITLE = "eventDetailsTitle"
  const val BACK_BUTTON = "eventDetailsBackButton"
  const val LOADING = "eventDetailsLoading"
  const val NOT_FOUND = "eventDetailsNotFound"
  const val TYPE_BADGE = "eventDetailsTypeBadge"
  const val SUBJECT = "eventDetailsSubject"
  const val DATE_TIME = "eventDetailsDateTime"
  const val LOCATION = "eventDetailsLocation"
  const val DESCRIPTION_LABEL = "eventDetailsDescriptionLabel"
  const val DESCRIPTION = "eventDetailsDescription"
  const val TASKS_LABEL = "eventDetailsTasksLabel"
  const val TASK_CHECKBOX_PREFIX = "eventDetailsTaskCheckbox_"
  const val EDIT_BUTTON = "eventDetailsEditButton"
  const val DELETE_BUTTON = "eventDetailsDeleteButton"
  const val DELETE_PROGRESS = "eventDetailsDeleteProgress"
  const val SNACKBAR = "eventDetailsSnackbar"

  fun taskCheckbox(index: Int) = "$TASK_CHECKBOX_PREFIX$index"
}

/**
 * Event details screen: loads [eventId], shows it, and lets the user toggle tasks, edit or delete.
 *
 * @param eventId ID of the event to display.
 * @param onNavigateBack invoked on back press and after a successful delete.
 * @param onEdit invoked with the event ID when the edit button is pressed.
 */
@Composable
fun EventDetailsScreen(
    eventId: String,
    onNavigateBack: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    viewModel: EventDetailsViewModel = eventDetailsViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val errorMessage = uiState.error?.let { stringResource(it.stringResource) }

  LaunchedEffect(eventId) { viewModel.loadEvent(eventId) }

  LaunchedEffect(uiState.isEventDeleted) {
    if (uiState.isEventDeleted) {
      viewModel.onEventDeletedHandled()
      onNavigateBack()
    }
  }

  LaunchedEffect(errorMessage) {
    errorMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearError()
    }
  }

  val display = remember(uiState.event) { uiState.event?.toDisplay() }

  EventDetailsStateless(
      display = display,
      isLoading = uiState.isLoading,
      isBusy = uiState.isBusy,
      isDeleting = uiState.isDeleting,
      snackbarHostState = snackbarHostState,
      onBack = onNavigateBack,
      onToggleTask = viewModel::toggleTask,
      onEdit = {
        val state = viewModel.uiState.value
        if (!state.isBusy) state.event?.let { onEdit(it.id) }
      },
      onDelete = viewModel::deleteEvent,
  )
}

/** Stateless Event details layout, driven entirely by its parameters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailsStateless(
    display: EventDetailsDisplay?,
    isLoading: Boolean = false,
    isBusy: Boolean = false,
    isDeleting: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit = {},
    onToggleTask: (Int) -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
  Scaffold(
      modifier = Modifier.testTag(EventDetailsScreenTestTags.SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      snackbarHost = {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.testTag(EventDetailsScreenTestTags.SNACKBAR),
        )
      },
      topBar = {
        MediumTopAppBar(
            title = {
              Text(
                  text = stringResource(R.string.event_details_title),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(EventDetailsScreenTestTags.TITLE),
              )
            },
            navigationIcon = {
              IconButton(
                  onClick = onBack,
                  modifier = Modifier.testTag(EventDetailsScreenTestTags.BACK_BUTTON),
              ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.event_details_back_description),
                )
              }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
        )
      },
  ) { padding ->
    Column(
        modifier =
            Modifier.padding(padding)
                .fillMaxSize()
                .padding(horizontal = Dimens.screenHorizontalPadding),
    ) {
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
          display != null ->
              EventDetailsBody(
                  display = display,
                  tasksEnabled = !isBusy,
                  onToggleTask = onToggleTask,
              )
          isLoading ->
              CircularProgressIndicator(
                  modifier =
                      Modifier.align(Alignment.Center).testTag(EventDetailsScreenTestTags.LOADING),
              )
          else ->
              Text(
                  text = stringResource(R.string.event_details_not_found),
                  modifier =
                      Modifier.align(Alignment.Center)
                          .testTag(EventDetailsScreenTestTags.NOT_FOUND),
              )
        }
      }
      EventDetailsActions(
          enabled = display != null && !isBusy,
          isDeleting = isDeleting,
          onEdit = onEdit,
          onDelete = onDelete,
      )
    }
  }
}

@Composable
private fun EventDetailsBody(
    display: EventDetailsDisplay,
    tasksEnabled: Boolean,
    onToggleTask: (Int) -> Unit,
) {
  Column(
      modifier =
          Modifier.fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(top = Dimens.contentTopSpacing),
      verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.metaRowSpacing)) {
      EventTypeBadge(
          type = display.type,
          modifier = Modifier.testTag(EventDetailsScreenTestTags.TYPE_BADGE),
      )
      Spacer(Modifier.height(Dimens.titleToMetaSpacing))
      Text(
          text = display.title ?: stringResource(R.string.event_details_subject_fallback),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag(EventDetailsScreenTestTags.SUBJECT),
      )
      IconTextRow(
          icon = Icons.Default.AccessTime,
          text =
              stringResource(
                  R.string.event_details_date_time_range,
                  display.dateLabel,
                  display.startTimeLabel,
                  display.endTimeLabel,
              ),
          modifier = Modifier.testTag(EventDetailsScreenTestTags.DATE_TIME),
      )
      IconTextRow(
          icon = Icons.Outlined.LocationOn,
          text = display.location ?: stringResource(R.string.event_details_location_fallback),
          modifier = Modifier.testTag(EventDetailsScreenTestTags.LOCATION),
      )
    }

    LabeledSection(
        label = stringResource(R.string.event_details_description_label),
        labelTag = EventDetailsScreenTestTags.DESCRIPTION_LABEL,
    ) {
      Text(
          text = display.description ?: stringResource(R.string.event_details_description_fallback),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(EventDetailsScreenTestTags.DESCRIPTION),
      )
    }

    if (display.tasks.isNotEmpty()) {
      LabeledSection(
          label = stringResource(R.string.event_details_tasks_label),
          labelTag = EventDetailsScreenTestTags.TASKS_LABEL,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.taskListSpacing)) {
          display.tasks.forEachIndexed { index, task ->
            TaskCheckRow(
                text = task.text,
                isCompleted = task.isCompleted,
                onToggle = { onToggleTask(index) },
                checkboxTag = EventDetailsScreenTestTags.taskCheckbox(index),
                enabled = tasksEnabled,
            )
          }
        }
      }
    }
  }
}

/** Bold section label followed by its [content]. */
@Composable
private fun LabeledSection(
    label: String,
    labelTag: String,
    content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Dimens.sectionLabelToContentSpacing)) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(labelTag),
    )
    content()
  }
}

@Composable
private fun EventDetailsActions(
    enabled: Boolean,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
  Column(
      modifier =
          Modifier.padding(
              top = Dimens.actionsTopPadding,
              bottom = Dimens.actionsBottomPadding,
          ),
      verticalArrangement = Arrangement.spacedBy(Dimens.actionButtonSpacing),
  ) {
    PillActionButton(
        onClick = onEdit,
        enabled = enabled,
        isInProgress = false,
        label = stringResource(R.string.event_details_edit),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        buttonTag = EventDetailsScreenTestTags.EDIT_BUTTON,
        progressTag = EventDetailsScreenTestTags.EDIT_BUTTON + "Progress",
        icon = Icons.Default.Edit,
    )
    PillActionButton(
        onClick = onDelete,
        enabled = enabled,
        isInProgress = isDeleting,
        label = stringResource(R.string.event_details_delete),
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        buttonTag = EventDetailsScreenTestTags.DELETE_BUTTON,
        progressTag = EventDetailsScreenTestTags.DELETE_PROGRESS,
        icon = Icons.Outlined.Delete,
    )
  }
}

@Preview(showBackground = true)
@Composable
fun EventDetailsPreview() {
  MaterialTheme {
    EventDetailsStateless(
        display =
            EventDetailsDisplay(
                type = EventType.entries.first(),
                title = "Algebra",
                dateLabel = "Nov 2, 2026",
                startTimeLabel = "10:15",
                endTimeLabel = "12:00",
                location = "CM5",
                description = "Exercise session focusing on the new concepts of week 8.",
                tasks =
                    listOf(
                        EventTaskDisplay("Solve exercises 12, 13", false),
                        EventTaskDisplay("Read chapter 3 of textbook", false),
                    ),
            ),
    )
  }
}
