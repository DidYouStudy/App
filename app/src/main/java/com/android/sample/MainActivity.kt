// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude AI Agent
package com.android.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.resources.C
import com.android.sample.ui.app.AppUiState
import com.android.sample.ui.app.appViewModel
import com.android.sample.ui.auth.SignInRoute
import com.android.sample.ui.calendar.CalendarScreen
import com.android.sample.ui.dashboard.DashboardScreen
import com.android.sample.ui.groupsession.GroupSessionScreen
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.preferences.PreferencesScreen
import com.android.sample.ui.settings.SettingsScreen
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
fun DidYouStudyApp() {
  val navController = rememberNavController()
  val navigationActions = NavigationActions(navController)
  val applicationViewModel = appViewModel()
  val appState = applicationViewModel.uiState.collectAsStateWithLifecycle().value
  val currentNavigationActions by rememberUpdatedState(navigationActions)

  LaunchedEffect(appState) {
    when (appState) {
      AppUiState.SignIn,
      AppUiState.LogOut ->
          if (currentNavigationActions.currentRoute() != Screen.Auth.route) {
            currentNavigationActions.navigateTo(Screen.Auth)
          }
      AppUiState.FirstTimeSignIn ->
          if (currentNavigationActions.currentRoute() != Screen.Preferences.route) {
            currentNavigationActions.navigateTo(Screen.Preferences)
          }
      AppUiState.Dashboard ->
          if (currentNavigationActions.currentRoute() != Screen.Dashboard.route) {
            currentNavigationActions.navigateTo(Screen.Dashboard)
          }
      AppUiState.Loading,
      is AppUiState.Error -> Unit
    }
  }

  NavHost(
      navController = navController,
      startDestination = Screen.Auth.name,
  ) {
    navigation(
        startDestination = Screen.Auth.route,
        route = Screen.Auth.name,
    ) {
      composable(Screen.Auth.route) {
        SignInRoute(
            onSignedIn = {},
            getIdToken = AuthRepositoryProvider::requestGoogleIdToken,
        )
      }
    }

    navigation(
        startDestination = Screen.Preferences.route,
        route = Screen.Preferences.name,
    ) {
      composable(Screen.Preferences.route) {
        PreferencesScreen(
            onImportClick = {},
            onAddLocationClick = {},
            onSaved = applicationViewModel::onPreferencesSaved,
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
        )
      }
    }

    navigation(
        startDestination = Screen.Settings.route,
        route = Screen.Settings.name,
    ) {
      composable(Screen.Settings.route) {
        SettingsScreen(
            navigationActions = navigationActions,
        )
      }
    }
  }
}
