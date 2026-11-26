// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import com.android.sample.R
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/** Renders a participant's avatar and display name, with a badge for the host. */
@Composable
fun ParticipantRow(
    userId: String,
    username: String?,
    isHost: Boolean,
    isCurrentUser: Boolean,
    isUnavailable: Boolean,
    avatarTestTag: String,
    modifier: Modifier = Modifier,
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
    ParticipantAvatar(userId, username, isHost, avatarTestTag)
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

/** Shows an initial from the stored username, with a stable color across roster updates. */
@Composable
private fun ParticipantAvatar(
    userId: String,
    username: String?,
    isHost: Boolean,
    avatarTestTag: String,
) {
  val initial =
      username
          ?.trimStart()
          ?.takeIf { it.isNotEmpty() }
          ?.let { name ->
            String(Character.toChars(Character.toUpperCase(Character.codePointAt(name, 0))))
          }
  val colors = MaterialTheme.colorScheme
  val (background, foreground) =
      when {
        initial == null -> colors.surfaceVariant to colors.onSurfaceVariant
        isHost -> colors.primaryContainer to colors.onPrimaryContainer
        userId.hashCode() % 2 == 0 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
      }

  Box(
      modifier =
          Modifier.size(Dimens.avatarSizeMedium)
              .background(background, CircleShape)
              .testTag(avatarTestTag)
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
