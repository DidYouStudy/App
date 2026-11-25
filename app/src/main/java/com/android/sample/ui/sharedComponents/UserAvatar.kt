// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage

/**
 * Shows a user's profile picture cropped to a circle, or [fallback] when there is no picture to
 * show.
 *
 * The picture has no content description: callers that need one set it on their own node.
 *
 * @param pictureUrl URL of the picture. When null or blank, no image is requested.
 * @param modifier must give the avatar its size; the picture and the fallback fill that same area.
 * @param fallback shown without a picture, while it loads and when it cannot be loaded.
 */
@Composable
fun UserAvatar(
    pictureUrl: String?,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
  Box(modifier = modifier.clip(CircleShape), contentAlignment = Alignment.Center) {
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
