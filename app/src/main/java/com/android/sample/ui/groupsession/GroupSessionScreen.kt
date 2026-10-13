package com.android.sample.ui.groupsession

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.ui.navigation.BottomNavigationMenu
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Tab

object GroupSessionScreenTestTags {
  const val PREFERENCES_BUTTON = "preferencesButton"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSessionScreen(
    // TODO add VM as parameter
    // TODO: add credentialManager as parameter
    // credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    // TODO: add onSignedOut as parameter
    // onSignedOut: () -> Unit = {},
    navigationActions: NavigationActions? = null,
) {
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text("Group sessions", modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE))
            },
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = Tab.GroupSession,
            onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) },
            modifier = Modifier.testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      content = { pd -> Text("Group sessions screen", modifier = Modifier.padding(pd)) },
  )
}

@Preview(showBackground = true)
@Composable
fun GroupSessionScreenPreview() {
  GroupSessionScreen()
}
