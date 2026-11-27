// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Sonnet 5.5

package com.android.sample.ui.eventplanning

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.ui.sharedComponents.EventTypeBadge
import com.android.sample.ui.sharedComponents.IconTextRow
import com.android.sample.ui.sharedComponents.ParticipantRow
import com.android.sample.ui.sharedComponents.PillActionButton
import com.android.sample.ui.sharedComponents.SecondaryMediumTopAppBar
import com.android.sample.ui.sharedComponents.TaskCheckRow
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

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
  const val GROUP_SESSION_CHIP = "eventDetailsGroupSessionChip"
  const val PARTICIPANTS_LABEL = "eventDetailsParticipantsLabel"
  const val PARTICIPANT_AVATAR = "eventDetailsParticipantAvatar"
  const val PARTICIPANT_ROW_PREFIX = "eventDetailsParticipantRow_"
  const val TASK_CHECKBOX_PREFIX = "eventDetailsTaskCheckbox_"
  const val EDIT_BUTTON = "eventDetailsEditButton"
  const val DELETE_BUTTON = "eventDetailsDeleteButton"
  const val DELETE_PROGRESS = "eventDetailsDeleteProgress"
  const val SNACKBAR = "eventDetailsSnackbar"

  fun taskCheckbox(index: Int) = "$TASK_CHECKBOX_PREFIX$index"

  /** Test tag of the participant row at [index], host first. */
  fun participantRow(index: Int) = "$PARTICIPANT_ROW_PREFIX$index"
}

/**
 * Event details screen: loads [eventId], shows it, and lets the user toggle tasks, edit or delete.
 *
 * @param eventId ID of the event to display.
 * @param kind Kind of event to load.
 * @param onNavigateBack invoked on back press and after a successful delete.
 * @param onEdit invoked with the loaded event's ID and kind when the edit button is pressed.
 * @param viewModel Loads and manages the displayed event.
 */
@Composable
fun EventDetailsScreen(
    eventId: String,
    kind: EventKind = EventKind.PERSONAL,
    onNavigateBack: () -> Unit = {},
    onEdit: (String, EventKind) -> Unit = { _, _ -> },
    viewModel: EventDetailsViewModel = eventDetailsViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val errorMessage = uiState.error?.let { stringResource(it.stringResource) }

  LaunchedEffect(eventId, kind) { viewModel.loadEvent(eventId, kind) }

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

  val display =
      remember(uiState.event, uiState.members) {
        uiState.event?.toDisplay(members = uiState.members)
      }

  EventDetailsStateless(
      display = display,
      isLoading = uiState.isLoading,
      fallbackActions =
          if (kind == EventKind.PERSONAL) EventDetailsActionSet.EDIT_AND_DELETE
          else EventDetailsActionSet.NONE,
      isBusy = uiState.isBusy,
      isDeleting = uiState.isDeleting,
      snackbarHostState = snackbarHostState,
      onBack = onNavigateBack,
      onToggleTask = viewModel::toggleTask,
      onEdit = {
        val state = viewModel.uiState.value
        if (!state.isBusy) state.event?.let { event -> onEdit(event.id, event.kind()) }
      },
      onDelete = viewModel::deleteEvent,
  )
}

/**
 * Stateless Event details layout, driven entirely by its parameters.
 *
 * @param fallbackActions Actions to show disabled when there is no display data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventDetailsStateless(
    display: EventDetailsDisplay?,
    isLoading: Boolean = false,
    fallbackActions: EventDetailsActionSet = EventDetailsActionSet.EDIT_AND_DELETE,
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
        SecondaryMediumTopAppBar(
            title = stringResource(R.string.event_details_title),
            onBackClick = onBack,
            backButtonDescription = stringResource(R.string.event_details_back_description),
            backButtonTestTag = EventDetailsScreenTestTags.BACK_BUTTON,
            titleTestTag = EventDetailsScreenTestTags.TITLE,
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
          actions = display?.actions ?: fallbackActions,
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
      // FlowRow so the chip wraps under the badge on narrow screens or large font scales.
      FlowRow(
          horizontalArrangement = Arrangement.spacedBy(Spacing.small),
          verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
          itemVerticalAlignment = Alignment.CenterVertically,
      ) {
        EventTypeBadge(
            type = display.type,
            modifier = Modifier.testTag(EventDetailsScreenTestTags.TYPE_BADGE),
        )
        if (display.isGroupSession) GroupSessionChip()
      }
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

    if (display.isGroupSession) {
      EventDetailsParticipants(display.participants)
    } else if (display.tasks.isNotEmpty()) {
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

/** Identifies a group session beside its event type badge. */
@Composable
private fun GroupSessionChip() {
  Surface(
      shape = RoundedCornerShape(Dimens.cornerSmall),
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      modifier =
          Modifier.testTag(EventDetailsScreenTestTags.GROUP_SESSION_CHIP).semantics(
              mergeDescendants = true
          ) {},
  ) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.Groups,
          contentDescription = null,
          modifier = Modifier.size(Dimens.iconSizeSmall),
      )
      Text(
          text = stringResource(R.string.event_details_group_session),
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.labelLarge,
      )
    }
  }
}

/** Shows the participant count and rows in the order supplied by the view model. */
@Composable
private fun EventDetailsParticipants(participants: List<EventMember>) {
  LabeledSection(
      label = stringResource(R.string.group_session_participants_title, participants.size),
      labelTag = EventDetailsScreenTestTags.PARTICIPANTS_LABEL,
  ) {
    Column {
      participants.forEachIndexed { index, participant ->
        if (index > 0) HorizontalDivider()
        ParticipantRow(
            userId = participant.userId,
            username = participant.username,
            isHost = participant.isHost,
            isCurrentUser = participant.isCurrentUser,
            isUnavailable = participant.isUnavailable,
            avatarTestTag = EventDetailsScreenTestTags.PARTICIPANT_AVATAR,
            modifier = Modifier.testTag(EventDetailsScreenTestTags.participantRow(index)),
        )
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

/** Renders the action buttons offered for the displayed event. */
@Composable
private fun EventDetailsActions(
    actions: EventDetailsActionSet,
    enabled: Boolean,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
  if (actions == EventDetailsActionSet.NONE) return

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
    if (actions == EventDetailsActionSet.EDIT_AND_DELETE) {
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

/** Previews the group session details and shared roster rows in both themes. */
@Preview(
    name = "Group session light",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Group session dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
internal fun GroupSessionEventDetailsPreview() {
  DidYouStudyTheme(dynamicColor = false) {
    EventDetailsStateless(
        display =
            EventDetailsDisplay(
                type = EventType.EXERCISE_SESSION,
                title = "Algebra",
                dateLabel = "Nov 2, 2026",
                startTimeLabel = "10:15",
                endTimeLabel = "12:00",
                location = "CM5",
                description = "Exercise session focusing on the new concepts of week 8.",
                tasks = emptyList(),
                isGroupSession = true,
                participants =
                    listOf(
                        EventMember("host", "Sam", isHost = true, isCurrentUser = true),
                        EventMember("marcus", "Marcus Vance", isHost = false),
                        EventMember("alice", "Alice Laurent", isHost = false),
                    ),
                actions = EventDetailsActionSet.EDIT_ONLY,
            ),
    )
  }
}
