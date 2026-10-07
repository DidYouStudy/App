package com.android.sample

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.credentials.CredentialManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.android.sample.resources.C
import com.android.sample.ui.authentication.SignInScreen
import com.android.sample.ui.calendar.CalendarScreen
import com.android.sample.ui.dashboard.DashboardScreen
import com.android.sample.ui.groupsession.GroupSessionScreen
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.theme.SampleAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleAppTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          DidYouStudyApp()
        }
      }
    }
  }
}

@Composable
fun DidYouStudyApp(
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
) {
  val navController = rememberNavController()
  val navigationActions = NavigationActions(navController)
  val isSignedIn = true // TODO REMEMBER FIREBASE AUTH

  val startDestination = if (isSignedIn) Screen.Dashboard.route else Screen.Auth.name

  NavHost(
      navController = navController,
      startDestination = startDestination,
  ) {
    navigation(
        startDestination = Screen.Auth.route,
        route = Screen.Auth.name,
    ) {
      composable(Screen.Auth.route) {
        SignInScreen(
            credentialManager = credentialManager,
            onSignedIn = { navigationActions.navigateTo(Screen.Dashboard) },
        )
      }
    }

    navigation(
        startDestination = Screen.Dashboard.route,
        route = Screen.Dashboard.name,
    ) {
      composable(Screen.Dashboard.route) {
        DashboardScreen(
            navigationActions = navigationActions,
            credentialManager = credentialManager,
        )
      }
    }

    navigation(
        startDestination = Screen.Calendar.route,
        route = Screen.Calendar.name,
    ) {
      composable(Screen.Calendar.route) {
        CalendarScreen(
            navigationActions = navigationActions,
            credentialManager = credentialManager,
        )
      }
    }

    navigation(
        startDestination = Screen.GroupSession.route,
        route = Screen.GroupSession.name,
    ) {
      composable(Screen.GroupSession.route) {
        GroupSessionScreen(
            navigationActions = navigationActions,
            credentialManager = credentialManager,
        )
      }
    }
  }
}
