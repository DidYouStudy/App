// Co-authored-by: Claude Ai Agent
package com.android.sample.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.sample.R
import com.android.sample.ui.navigation.NavigationTestTags

// ---------------------------------------------------------------------------
// Building blocks / Helper methods
// ---------------------------------------------------------------------------

/** Test tags for the UI components of the Settings screen. */
object SettingsScreenTestTags {
  // Screen container
  const val SCREEN = "settings_screen"

  // Profile card
  const val PROFILE_CARD = "settings_profile_card"
  const val AVATAR = "settings_avatar"
  const val GREETING = "settings_greeting"
  const val LOADING_INDICATOR = "settings_loading_indicator"
  const val EDIT_USERNAME_BUTTON = "settings_edit_username_button"
  const val CHANGE_PROFILE_PICTURE_BUTTON = "settings_change_profile_picture_button"

  // Navigation buttons
  const val PREFERENCES_BUTTON = "settings_preferences_button"
  const val FRIENDS_BUTTON = "settings_friends_button"

  // Log out
  const val LOGOUT_BUTTON = "settings_logout_button"
}

/**
 * Displays the Settings app bar and invokes the supplied back action.
 *
 * @param onBackClick Called when the navigation button is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsHeader(onBackClick: () -> Unit) {
  MediumTopAppBar(
      title = {
        Text(
            text = stringResource(id = R.string.settings_header),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE),
        )
      },
      navigationIcon = {
        // Returns to the previous screen.
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
        ) {
          Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(id = R.string.button_go_back),
          )
        }
      },
      colors =
          TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer,
              scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
              titleContentColor = MaterialTheme.colorScheme.onSurface,
              actionIconContentColor = Color.Unspecified,
          ),
  )
}

/**
 * Displays the user's profile summary and profile-related actions.
 *
 * @param username Name shown in the greeting.
 * @param profilePictureUrl URL of the user's profile picture, if available; currently not rendered.
 * @param isLoading Whether the profile information is loading.
 * @param onEditUsernameClick Called when the username edit action is tapped.
 * @param onChangeProfilePictureClick Called when the profile-picture action is tapped.
 */
@Composable
internal fun ProfileCard(
    username: String,
    profilePictureUrl: String?,
    isLoading: Boolean,
    onEditUsernameClick: () -> Unit,
    onChangeProfilePictureClick: () -> Unit,
) {
  val avatarSize = 76.dp

  Box(modifier = Modifier.fillMaxWidth()) {
    // The card starts at the vertical middle of the avatar
    OutlinedCard(
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = avatarSize / 2)
                .testTag(SettingsScreenTestTags.PROFILE_CARD),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors =
            CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(
          modifier = Modifier.fillMaxWidth().padding(top = avatarSize / 2 + 4.dp, bottom = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (isLoading) {
          // loading circle
          CircularProgressIndicator(
              modifier = Modifier.size(24.dp).testTag(SettingsScreenTestTags.LOADING_INDICATOR),
              strokeWidth = 2.dp,
          )
        } else {
          // "Hello, user!" text
          Text(
              text = stringResource(R.string.settings_greeting, username),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag(SettingsScreenTestTags.GREETING),
          )
        }
        Spacer(Modifier.height(4.dp))
        // Opens the flow for changing the displayed username.
        SmallEditButton(
            text = stringResource(R.string.settings_edit_username),
            onClick = onEditUsernameClick,
            testTag = SettingsScreenTestTags.EDIT_USERNAME_BUTTON,
        )
        Text(
            text = "(Not implemented)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Opens the flow for choosing a profile picture.
        SmallEditButton(
            text = stringResource(R.string.settings_change_profile_picture),
            onClick = onChangeProfilePictureClick,
            testTag = SettingsScreenTestTags.CHANGE_PROFILE_PICTURE_BUTTON,
        )
        Text(
            text = "(Not implemented)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Displays the generic avatar placeholder. The profile picture URL is not rendered yet.
    Box(
        modifier =
            Modifier.size(avatarSize)
                .align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .testTag(SettingsScreenTestTags.AVATAR),
        contentAlignment = Alignment.Center,
    ) {
      DefaultAvatarIcon()

      // currently, the actual profile picture of the user is not displayed
      // the following code should take care of that but it is dependent on the coil library
      // which is not yet imported into the build gradle file: the artifact has not yet
      // been verified
      //            if (profilePictureUrl.isNullOrBlank()) {
      //                DefaultAvatarIcon()
      //            } else {
      //                SubcomposeAsyncImage(
      //                    model = profilePictureUrl,
      //                    contentDescription = stringResource(R.string.settings_profile_picture),
      //                    modifier = Modifier.fillMaxSize().clip(CircleShape),
      //                    contentScale = ContentScale.Crop,
      //                    // Fall back to the default icon while loading and if the image fails to
      // load
      //                    loading = { DefaultAvatarIcon() },
      //                    error = { DefaultAvatarIcon() },
      //                )
      //            }
    }
  }
}

/** Displays the generic person icon used as the avatar placeholder. */
@Composable
internal fun DefaultAvatarIcon() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Icon(
        imageVector = Icons.Outlined.Person,
        contentDescription = stringResource(R.string.settings_profile_picture),
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(52.dp),
    )
  }
}

/**
 * Displays a compact text action with an edit icon.
 *
 * @param text Text describing the action.
 * @param onClick Action invoked when tapped.
 * @param testTag Test tag applied for UI tests.
 */
@Composable
internal fun SmallEditButton(text: String, onClick: () -> Unit, testTag: String) {
  val color = MaterialTheme.colorScheme.primary
  Row(
      modifier =
          Modifier.testTag(testTag)
              .clickable(role = Role.Button, onClick = onClick)
              .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        imageVector = Icons.Outlined.Edit,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(text = text, style = MaterialTheme.typography.labelMedium, color = color)
  }
}

/**
 * Displays a full-width pill button with a leading icon.
 *
 * @param icon Icon representing the destination or action.
 * @param text Button label.
 * @param onClick Action invoked when tapped.
 * @param testTag Test tag applied for UI tests.
 */
@Composable
internal fun SettingsPillButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    testTag: String,
) {
  Button(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp).height(50.dp).testTag(testTag),
      shape = RoundedCornerShape(50),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          ),
      elevation = null,
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(12.dp))
      Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
  }
}

/**
 * Displays the full-width logout button.
 *
 * @param onClick Action invoked to sign the user out.
 */
@Composable
internal fun LogoutButton(onClick: () -> Unit) {
  Button(
      onClick = onClick,
      modifier =
          Modifier.fillMaxWidth()
              .padding(horizontal = 7.dp)
              .height(52.dp)
              .testTag(SettingsScreenTestTags.LOGOUT_BUTTON),
      shape = RoundedCornerShape(50),
      colors =
          ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
          ),
  ) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.Logout,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(
        text = stringResource(R.string.settings_logout_button),
        style = MaterialTheme.typography.labelLarge,
    )
  }
}
