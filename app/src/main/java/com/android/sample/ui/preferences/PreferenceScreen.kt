package com.android.sample.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags

object PreferenceScreenTestTags {
  const val PREFERENCES_BUTTON = "preferencesButton"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferenceScreen(
    // TODO add VM as parameter
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignedOut: () -> Unit = {},
    navigationActions: NavigationActions? = null,
) {
  // TODO WHEN DOING TOP NAVIGATION
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text(
                  "Preferences screen",
                  modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE),
              )
            },
        )
      },
      modifier = Modifier.fillMaxSize(),
      content = { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) { Text("Preferences screen") }
      },
  )
}

@Preview(showBackground = true)
@Composable
fun PreferenceScreenPreview() {
  PreferenceScreen()
}
