// Co-authored-by: Claude AI Agent
package com.android.sample.ui.sharedComponents

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.android.sample.R
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.profile.ProfileButton
import com.android.sample.ui.theme.Dimens

/**
 * Displays the standard top app bar for top-level screens.
 *
 * @param title Title text displayed in the top bar.
 * @param modifier Modifier applied to the [TopAppBar].
 * @param navigationActions Navigation actions used to navigate to the Settings screen.
 * @param onProfileClick Called when the profile button is clicked. Defaults to navigating to
 *   Settings if [navigationActions] is provided.
 * @param actions Extra action composables displayed before the profile button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationActions: NavigationActions? = null,
    onProfileClick: () -> Unit = { navigationActions?.navigateTo(Screen.Settings) },
    actions: @Composable RowScope.() -> Unit = {},
) {
  TopAppBar(
      title = {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE),
        )
      },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      actions = {
        actions()
        ProfileButton(
            onClick = onProfileClick,
            modifier = Modifier.padding(end = Dimens.profileButtonPadding),
        )
      },
      modifier = modifier,
  )
}

/**
 * Displays a medium top app bar with a back navigation button, surface colors, and headline title
 * styling or custom title content.
 *
 * @param modifier Modifier applied to the top app bar.
 * @param title Optional title text displayed in the bar with default headline styling.
 * @param onBackClick Called when the back button is clicked. If null, no back button is rendered.
 * @param backButtonEnabled Whether the back button is enabled for interaction.
 * @param backButtonDescription Content description for accessibility on the back button.
 * @param backButtonTestTag Test tag applied to the back icon button.
 * @param titleTestTag Test tag applied to the default title text.
 * @param actions Extra action composables displayed on the right.
 * @param titleContent Optional custom composable lambda for rendering the title content instead of
 *   [title].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryMediumTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBackClick: (() -> Unit)? = null,
    backButtonEnabled: Boolean = true,
    backButtonDescription: String = stringResource(id = R.string.button_go_back),
    backButtonTestTag: String = NavigationTestTags.GO_BACK_BUTTON,
    titleTestTag: String = NavigationTestTags.TOP_BAR_TITLE,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
) {
  val resolvedTitle: @Composable () -> Unit =
      titleContent
          ?: {
            if (title != null) {
              Text(
                  text = title,
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(titleTestTag),
              )
            }
          }

  MediumTopAppBar(
      title = resolvedTitle,
      navigationIcon = {
        if (onBackClick != null) {
          IconButton(
              onClick = onBackClick,
              enabled = backButtonEnabled,
              modifier = Modifier.testTag(backButtonTestTag),
          ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = backButtonDescription,
            )
          }
        }
      },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
          ),
      actions = actions,
      modifier = modifier,
  )
}
