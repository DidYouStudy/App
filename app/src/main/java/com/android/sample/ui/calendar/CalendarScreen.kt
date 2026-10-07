package com.android.sample.ui.calendar

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
import com.android.sample.ui.navigation.NavigationTestTags

object CalendarScreenTestTags {
  const val PREFERENCES_BUTTON = "preferencesButton"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    // TODO add VM as parameter
    credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    onSignedOut: () -> Unit = {},
) {
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text("Calendar", modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE))
            },
        )
      },
      content = { pd -> Text("Calendar screen", modifier = Modifier.padding(pd)) },
  )
}

@Preview(showBackground = true)
@Composable
fun CalendarScreenPreview() {
  CalendarScreen()
}
