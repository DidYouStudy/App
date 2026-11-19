// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/** Test tags used to locate UI elements in the [OfflineBanner] component during automated tests. */
object OfflineBannerTestTags {
  const val OFFLINE_BANNER = "OfflineBanner"
  const val OFFLINE_BANNER_ICON = "OfflineBannerIcon"
  const val OFFLINE_BANNER_TEXT = "OfflineBannerText"
}

/**
 * Shared offline banner component that displays an offline icon and "You're offline" status message
 * using theme colors.
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
  Surface(
      color = MaterialTheme.colorScheme.errorContainer,
      contentColor = MaterialTheme.colorScheme.onErrorContainer,
      modifier = modifier.fillMaxWidth().testTag(OfflineBannerTestTags.OFFLINE_BANNER),
  ) {
    Row(
        modifier =
            Modifier.fillMaxWidth().padding(vertical = Spacing.small, horizontal = Spacing.large),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Default.WifiOff,
          contentDescription = null,
          modifier =
              Modifier.size(Dimens.iconSizeMedium)
                  .testTag(OfflineBannerTestTags.OFFLINE_BANNER_ICON),
      )
      Spacer(modifier = Modifier.width(Spacing.small))
      Text(
          text = stringResource(R.string.offline_banner_text),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.testTag(OfflineBannerTestTags.OFFLINE_BANNER_TEXT),
      )
    }
  }
}

@Preview(name = "Offline Banner Light", showBackground = true)
@Composable
fun OfflineBannerLightPreview() {
  DidYouStudyTheme(darkTheme = false) { OfflineBanner() }
}

@Preview(name = "Offline Banner Dark", showBackground = true)
@Composable
fun OfflineBannerDarkPreview() {
  DidYouStudyTheme(darkTheme = true) { OfflineBanner() }
}
