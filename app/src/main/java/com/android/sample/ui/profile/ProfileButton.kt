// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.profile

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens

object ProfileButtonTestTags {
  const val PROFILE_BUTTON = "profileButton"
}

/**
 * Circular avatar button shown at the end of the top app bar that opens the user's profile.
 *
 * @param onClick called when the user taps the button.
 * @param modifier modifier applied to the button.
 */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  FilledIconButton(
      onClick = onClick,
      modifier =
          modifier.size(Dimens.profileButtonSize).testTag(ProfileButtonTestTags.PROFILE_BUTTON),
      shape = CircleShape,
      colors =
          IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
  ) {
    Icon(
        imageVector = Icons.Outlined.Person,
        contentDescription = stringResource(R.string.profile_button_description),
        modifier = Modifier.size(Dimens.profileButtonIconSize),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun ProfileButtonPreview() {
  DidYouStudyTheme(dynamicColor = false) { ProfileButton(onClick = {}) }
}
