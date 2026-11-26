// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.android.sample.R
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/**
 * Renders a participant's avatar and display name, with a badge for the host.
 *
 * @param pictureUrl URL of the profile picture; without one the avatar shows the initial.
 */
@Composable
fun ParticipantRow(
    userId: String,
    username: String?,
    isHost: Boolean,
    isCurrentUser: Boolean,
    isUnavailable: Boolean,
    avatarTestTag: String,
    modifier: Modifier = Modifier,
    pictureUrl: String? = null,
) {
  val displayName =
      when {
        isCurrentUser -> stringResource(R.string.group_session_participants_you)
        username != null -> username
        isUnavailable -> stringResource(R.string.group_session_participants_unavailable)
        else -> stringResource(R.string.group_session_participants_unknown)
      }
  Row(
      modifier = modifier.fillMaxWidth().padding(vertical = Spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    UserAvatar(
        userId = userId,
        username = username,
        pictureUrl = pictureUrl,
        modifier = Modifier.testTag(avatarTestTag),
        isHost = isHost,
    )
    Text(
        text = displayName,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f),
    )
    if (isHost) {
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
