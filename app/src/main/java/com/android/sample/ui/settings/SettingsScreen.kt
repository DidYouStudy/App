// Co-authored-by: Claude AI Agent
package com.android.sample.ui.settings

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R

/**
 * Collects the settings UI state and connects screen actions to the [SettingsViewModel].
 *
 * Displays errors from the ViewModel in a snackbar and clears them after they are shown.
 *
 * @param viewModel ViewModel supplying settings state and the sign-out action.
 * @param modifier Modifier applied to the settings screen.
 * @param onBackClick Called when the user taps the back button.
 * @param onEditUsernameClick Called when the user requests to edit their username.
 * @param onChangeProfilePictureClick Called when the user requests to change their profile picture.
 * @param onModifyPreferencesClick Called when the user opens preference settings.
 * @param onManageFriendsClick Called when the user opens friend management.
 * @param onLogoutSuccess Called after the user is successfully signed out.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = settingsViewModel(),
    onBackClick: () -> Unit = {},
    onEditUsernameClick: () -> Unit = {},
    onChangeProfilePictureClick: () -> Unit = {},
    onModifyPreferencesClick: () -> Unit = {},
    onManageFriendsClick: () -> Unit = {},
    onLogoutSuccess: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  // Show errors from the ViewModel in a snackbar, then clear them.
  LaunchedEffect(uiState.error) {
    uiState.error?.let { message ->
      snackbarHostState.showSnackbar(message)
      viewModel.clearError()
    }
  }

  // Render the UI from the latest state and route user actions to their handlers.
  SettingsScreenStateless(
      modifier = modifier,
      username = uiState.username,
      profilePictureUrl = uiState.profilePictureUrl,
      isLoading = uiState.isLoading,
      snackbarHostState = snackbarHostState,
      onBackClick = onBackClick,
      onEditUsernameClick = onEditUsernameClick,
      onChangeProfilePictureClick = onChangeProfilePictureClick,
      onModifyPreferencesClick = onModifyPreferencesClick,
      onManageFriendsClick = onManageFriendsClick,
      onLogoutClick = { viewModel.signOut(onLogoutSuccess) },
  )
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
 * @param snackbarHostState State used to display screen snackbars.
 * @param onBackClick Called when the user taps the back button.
 * @param onEditUsernameClick Called when the user requests to edit their username.
 * @param onChangeProfilePictureClick Called when the user requests to change their profile picture.
 * @param onModifyPreferencesClick Called when the user opens preference settings.
 * @param onManageFriendsClick Called when the user opens friend management.
 * @param onLogoutClick Called when the user taps the logout button.
 */
@Composable
fun SettingsScreenStateless(
    modifier: Modifier = Modifier,
    username: String = stringResource(R.string.default_username),
    profilePictureUrl: String? = null,
    isLoading: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBackClick: () -> Unit = {},
    onEditUsernameClick: () -> Unit = {},
    onChangeProfilePictureClick: () -> Unit = {},
    onModifyPreferencesClick: () -> Unit = {},
    onManageFriendsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
) {
  Scaffold(
      modifier = modifier.fillMaxSize().testTag(SettingsScreenTestTags.SCREEN),
      containerColor = MaterialTheme.colorScheme.background,
      // The header and the footer handle the system insets themselves.
      contentWindowInsets = WindowInsets(0, 0, 0, 0),
      topBar = { SettingsHeader(onBackClick = onBackClick) },
      snackbarHost = {
        SnackbarHost(snackbarHostState, modifier = Modifier.navigationBarsPadding())
      },
  ) { innerPadding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(12.dp))

      // Shows the user's name and profile actions; displays a loading indicator while needed.
      ProfileCard(
          username = username,
          profilePictureUrl = profilePictureUrl,
          isLoading = isLoading,
          onEditUsernameClick = onEditUsernameClick,
          onChangeProfilePictureClick = onChangeProfilePictureClick,
      )

      Spacer(Modifier.height(16.dp))

      // Opens the study preference settings.
      SettingsPillButton(
          icon = Icons.Outlined.FavoriteBorder,
          text = stringResource(R.string.settings_preferences_button),
          onClick = onModifyPreferencesClick,
          testTag = SettingsScreenTestTags.PREFERENCES_BUTTON,
      )

      Spacer(Modifier.height(16.dp))

      // Opens the friends-management screen.
      SettingsPillButton(
          icon = Icons.Outlined.Groups,
          text = stringResource(R.string.settings_friends_button),
          onClick = onManageFriendsClick,
          testTag = SettingsScreenTestTags.FRIENDS_BUTTON,
      )

      // Pushes the logout button to the bottom
      Spacer(Modifier.weight(1f))

      // Signs the user out through the callback supplied by the stateful screen.
      LogoutButton(onClick = onLogoutClick)

      Spacer(Modifier.navigationBarsPadding().height(24.dp))
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
