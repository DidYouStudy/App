// Co-authored-by: OpenAI Codex
package com.android.sample.ui.groupsession

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.android.sample.R
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/** Compose selectors for the roster and its dismiss control. */
object GroupSessionParticipantsTestTags {
  const val SHEET = "groupSessionParticipantsSheet"
  const val CLOSE = "groupSessionParticipantsClose"
  const val AVATAR = "groupSessionParticipantAvatar"
  const val SEARCH = "groupSessionParticipantsSearch"
  const val DRAG_HANDLE = "groupSessionParticipantsDragHandle"
}

/** Presents the live roster above either group sessions view. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupSessionParticipantsSheet(
    state: GroupSessionParticipantsSheet,
    onDismiss: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  val search = query.trim()
  val youLabel = stringResource(R.string.group_session_participants_you)
  val matchingParticipants =
      state.rows.filter { participant ->
        search.isEmpty() ||
            participant.username?.contains(search, ignoreCase = true) == true ||
            (participant.isCurrentUser && youLabel.contains(search, ignoreCase = true))
      }
  ModalBottomSheet(
      modifier = Modifier.fillMaxHeight(),
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
      onDismissRequest = onDismiss,
      dragHandle = {
        BottomSheetDefaults.DragHandle(
            modifier = Modifier.testTag(GroupSessionParticipantsTestTags.DRAG_HANDLE)
        )
      },
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.large)
                .padding(bottom = Spacing.large)
                .testTag(GroupSessionParticipantsTestTags.SHEET),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
            text =
                stringResource(R.string.group_session_participants_title, state.participantCount),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(GroupSessionParticipantsTestTags.CLOSE),
        ) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.group_session_participants_close),
          )
        }
      }
      Text(
          text =
              state.sessionSubject?.takeIf { it.isNotBlank() }
                  ?: stringResource(R.string.group_session_subject_fallback),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
      )
      ParticipantSearchField(query = query, onQueryChange = { query = it })
      if (state.isLoading) {
        Text(stringResource(R.string.group_session_participants_loading))
      }
      if (state.hasError) {
        Text(
            text = stringResource(R.string.group_session_participants_error),
            color = MaterialTheme.colorScheme.error,
        )
      }
      if (search.isNotEmpty() && matchingParticipants.isEmpty() && !state.isLoading) {
        Text(stringResource(R.string.group_session_participants_no_matches))
      }
      LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
        items(matchingParticipants, key = { it.userId }) { participant ->
          HorizontalDivider()
          ParticipantRow(participant)
        }
      }
    }
  }
}

/** Filters the loaded roster as the user types. */
@Composable
private fun ParticipantSearchField(query: String, onQueryChange: (String) -> Unit) {
  val searchLabel = stringResource(R.string.group_session_participants_search)
  OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      modifier =
          Modifier.fillMaxWidth().testTag(GroupSessionParticipantsTestTags.SEARCH).semantics {
            contentDescription = searchLabel
          },
      singleLine = true,
      shape = RoundedCornerShape(Dimens.cornerFull),
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      placeholder = { Text(searchLabel) },
      colors =
          OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedBorderColor = Color.Transparent,
              focusedBorderColor = Color.Transparent,
          ),
  )
}

/** Renders a username or its fallback beside an avatar, with a badge for the host. */
@Composable
private fun ParticipantRow(participant: GroupSessionParticipant) {
  val username =
      participant.username
          ?: stringResource(
              if (participant.isUnavailable) R.string.group_session_participants_unavailable
              else R.string.group_session_participants_unknown
          )
  Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    ParticipantAvatar(participant)
    Text(
        text =
            if (participant.isCurrentUser) stringResource(R.string.group_session_participants_you)
            else username,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
    )
    if (participant.isHost) {
      Surface(
          shape = RoundedCornerShape(Dimens.cornerSmall),
          color = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ) {
        Text(
            text = stringResource(R.string.group_session_participants_host),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
        )
      }
    }
  }
}

/** Shows an initial from the stored username, with a stable color across roster updates. */
@Composable
private fun ParticipantAvatar(participant: GroupSessionParticipant) {
  val initial =
      participant.username
          ?.trimStart()
          ?.takeIf { it.isNotEmpty() }
          ?.let { name ->
            String(Character.toChars(Character.toUpperCase(Character.codePointAt(name, 0))))
          }
  val colors = MaterialTheme.colorScheme
  val (background, foreground) =
      when {
        initial == null -> colors.surfaceVariant to colors.onSurfaceVariant
        participant.isHost -> colors.primaryContainer to colors.onPrimaryContainer
        participant.userId.hashCode() % 2 == 0 ->
            colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
      }

  Box(
      modifier =
          Modifier.size(Dimens.avatarSizeMedium)
              .background(background, CircleShape)
              .testTag(GroupSessionParticipantsTestTags.AVATAR)
              .semantics { hideFromAccessibility() },
      contentAlignment = Alignment.Center,
  ) {
    if (initial == null) {
      Icon(
          imageVector = Icons.Outlined.Person,
          contentDescription = null,
          tint = foreground,
      )
    } else {
      Text(text = initial, color = foreground, style = MaterialTheme.typography.titleMedium)
    }
  }
}
