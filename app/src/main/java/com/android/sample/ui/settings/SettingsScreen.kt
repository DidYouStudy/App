// Co-authored-by: Claude AI Agent
package com.android.sample.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags

/** Placeholder for the Settings screen, reached from the profile button of the top app bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    // TODO add VM as parameter
    navigationActions: NavigationActions? = null,
) {
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text("Settings", modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE))
            },
            navigationIcon = {
              IconButton(
                  onClick = { navigationActions?.goBack() },
                  modifier = Modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
              ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
              }
            },
        )
      },
      content = { pd -> Text("Settings screen", modifier = Modifier.padding(pd)) },
  )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
  SettingsScreen()
}
