// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/**
 * Collects the settings UI state and connects screen actions to the [SettingsViewModel].
 *
 * Displays errors from the ViewModel in a snackbar and clears them after they are shown. Changing
 * the profile picture opens the system photo picker, restricted to images, and hands the chosen
 * image to the ViewModel.
 *
 * @param viewModel ViewModel supplying settings state and the sign-out and picture actions.
 * @param modifier Modifier applied to the settings screen.
 * @param onBackClick Called when the user taps the back button.
 * @param onModifyPreferencesClick Called when the user opens preference settings.
 * @param onManageFriendsClick Called when the user opens friend management.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = settingsViewModel(),
    onBackClick: () -> Unit = {},
    onModifyPreferencesClick: () -> Unit = {},
    onManageFriendsClick: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  // The photo picker needs no storage permission; it returns null when the user closes it.
  val pickPicture =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onPictureSelected(uri?.toString())
      }

  // Resolve the error text here rather than in the ViewModel, so it follows the device language.
  val errorMessage = uiState.error?.let { stringResource(it.toMessageRes()) }

  // Show errors from the ViewModel in a snackbar, then clear them.
  LaunchedEffect(errorMessage) {
    errorMessage?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearError()
    }
  }

  // Render the UI from the latest state and route user actions to their handlers.
  SettingsScreenStateless(
      modifier = modifier,
      username = uiState.username ?: stringResource(R.string.default_username),
      profilePictureUrl = uiState.profilePictureUrl,
      isLoading = uiState.isLoading,
      isUploadingPicture = uiState.isUploadingPicture,
      snackbarHostState = snackbarHostState,
      onBackClick = onBackClick,
      onEditUsernameClick = viewModel::openUsernameEditor,
      onChangeProfilePictureClick = {
        pickPicture.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
      },
      onModifyPreferencesClick = onModifyPreferencesClick,
      onManageFriendsClick = onManageFriendsClick,
      onLogoutClick = { viewModel.signOut() },
      isOffline = uiState.isOffline,
  )

  // The dialog is driven by the ViewModel, so its content survives recomposition and rotation.
  uiState.usernameEdit?.let { edit ->
    EditUsernameDialog(
        state = edit,
        onInputChange = viewModel::onUsernameInputChange,
        onSave = viewModel::saveUsername,
        onDismiss = viewModel::dismissUsernameEditor,
    )
  }
}

/**
 * Renders the Settings screen from explicit state and event callbacks.
 *
 * Keeping this composable stateless makes the UI independently previewable and testable.
 *
 * @param modifier Modifier applied to the screen.
 * @param username Name displayed in the profile card.
 * @param profilePictureUrl URL of the user's profile picture, if available.
 * @param isLoading Whether profile information is still loading.
 * @param isUploadingPicture Whether a new profile picture is being uploaded; the avatar then shows
 *   a progress indicator and the change button is disabled.
 * @param snackbarHostState State used to display screen snackbars.
 * @param onBackClick Called when the user taps the back button.
 * @param onEditUsernameClick Called when the user requests to edit their username.
 * @param onChangeProfilePictureClick Called when the user requests to change their profile picture.
 * @param onModifyPreferencesClick Called when the user opens preference settings.
 * @param onManageFriendsClick Called when the user opens friend management.
 * @param onLogoutClick Called when the user taps the logout button.
 * @param isOffline Whether to show the "You're offline" banner under the header.
 */
@Composable
fun SettingsScreenStateless(
    modifier: Modifier = Modifier,
    username: String = stringResource(R.string.default_username),
    profilePictureUrl: String? = null,
    isLoading: Boolean = true,
    isUploadingPicture: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBackClick: () -> Unit = {},
    onEditUsernameClick: () -> Unit = {},
    onChangeProfilePictureClick: () -> Unit = {},
    onModifyPreferencesClick: () -> Unit = {},
    onManageFriendsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    isOffline: Boolean = false,
) {
  Scaffold(
      modifier = modifier.fillMaxSize().testTag(SettingsScreenTestTags.SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      // The header and the footer handle the system insets themselves.
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = { SettingsHeader(onBackClick = onBackClick, isOffline = isOffline) },
      snackbarHost = {
        SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding())
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.settingsHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(Spacing.medium))

      // Shows the user's name and profile actions; displays a loading indicator while needed.
      ProfileCard(
          username = username,
          profilePictureUrl = profilePictureUrl,
          isLoading = isLoading,
          isUploadingPicture = isUploadingPicture,
          onEditUsernameClick = onEditUsernameClick,
          onChangeProfilePictureClick = onChangeProfilePictureClick,
      )

      Spacer(Modifier.height(Spacing.large))

      // Opens the study preference settings.
      SettingsPillButton(
          icon = Icons.Outlined.FavoriteBorder,
          text = stringResource(R.string.settings_preferences_button),
          onClick = onModifyPreferencesClick,
          testTag = SettingsScreenTestTags.PREFERENCES_BUTTON,
      )

      Spacer(Modifier.height(Spacing.large))

      // Opens the friends-management screen.
      SettingsPillButton(
          icon = Icons.Outlined.Groups,
          text = stringResource(R.string.settings_friends_button),
          onClick = onManageFriendsClick,
          testTag = SettingsScreenTestTags.FRIENDS_BUTTON,
      )
      Text(
          text = stringResource(R.string.not_implemented),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Pushes the logout button to the bottom
      Spacer(Modifier.weight(1f))

      // Signs the user out through the callback supplied by the stateful screen.
      LogoutButton(onClick = onLogoutClick)

      Spacer(Modifier.navigationBarsPadding().height(Spacing.extraExtraLarge))
    }
  }
}

/** Preview of the Settings screen with profile loading complete. */
@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
  MaterialTheme {
    SettingsScreenStateless(
        isLoading = false,
    )
  }
}
