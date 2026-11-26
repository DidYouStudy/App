// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.groupsession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.android.sample.R
import com.android.sample.ui.sharedComponents.ParticipantRow
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
          ParticipantRow(
              userId = participant.userId,
              username = participant.username,
              isHost = participant.isHost,
              isCurrentUser = participant.isCurrentUser,
              isUnavailable = participant.isUnavailable,
              avatarTestTag = GroupSessionParticipantsTestTags.AVATAR,
              pictureUrl = participant.profilePictureUrl,
          )
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
