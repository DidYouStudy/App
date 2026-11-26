// Co-authored-by: OpenAI Codex
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import coil3.compose.SubcomposeAsyncImage
import com.android.sample.ui.theme.Dimens

/**
 * Shows a user's profile picture cropped to a circle. Without one, while it loads and if it cannot
 * be loaded, shows the initial of [username] with a color that stays stable across updates, or a
 * person icon without a username. Hidden from screen readers, because callers write the name next
 * to it.
 *
 * @param userId picks the initial's color for users other than the host.
 * @param username name the initial is taken from, if known.
 * @param pictureUrl URL of the profile picture. When null or blank, no image is requested.
 * @param isHost gives the initial the host's color.
 * @param size diameter of the avatar; below the default, the initial uses a smaller text style.
 */
@Composable
fun UserAvatar(
    userId: String,
    username: String?,
    pictureUrl: String?,
    modifier: Modifier = Modifier,
    isHost: Boolean = false,
    size: Dp = Dimens.avatarSizeMedium,
) {
  Box(
      modifier = modifier.size(size).clip(CircleShape).semantics { hideFromAccessibility() },
      contentAlignment = Alignment.Center,
  ) {
    val fallback: @Composable () -> Unit = { AvatarInitial(userId, username, isHost, size) }
    if (pictureUrl.isNullOrBlank()) {
      fallback()
    } else {
      SubcomposeAsyncImage(
          model = pictureUrl,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
          loading = { fallback() },
          error = { fallback() },
      )
    }
  }
}

/** Fills the avatar with the user's initial, or a person icon, on a colored background. */
@Composable
private fun AvatarInitial(userId: String, username: String?, isHost: Boolean, size: Dp) {
  // A whole code point, so a character outside the basic plane is not cut in half.
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
  val initialStyle =
      if (size < Dimens.avatarSizeMedium) MaterialTheme.typography.labelSmall
      else MaterialTheme.typography.titleMedium

  Box(
      modifier = Modifier.fillMaxSize().background(background),
      contentAlignment = Alignment.Center,
  ) {
    if (initial == null) {
      Icon(
          imageVector = Icons.Outlined.Person,
          contentDescription = null,
          tint = foreground,
      )
    } else {
      Text(text = initial, color = foreground, style = initialStyle)
    }
  }
}
