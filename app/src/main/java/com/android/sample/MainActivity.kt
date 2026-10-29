// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5
package com.android.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.ui.app.AppUiState
import com.android.sample.ui.app.appViewModel
import com.android.sample.ui.auth.SignInRoute
import com.android.sample.ui.calendar.CalendarScreen
import com.android.sample.ui.dashboard.DashboardScreen
import com.android.sample.ui.groupsession.GroupSessionScreen
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.preferences.PreferencesScreen
import com.android.sample.ui.preferences.rememberScheduleImportLauncher
import com.android.sample.ui.settings.SettingsScreen
import com.android.sample.ui.theme.DidYouStudyTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      DidYouStudyTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize(),
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
  val appState by applicationViewModel.uiState.collectAsStateWithLifecycle()
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
            onSignedIn = {
              applicationViewModel.onSignedIn { destination ->
                navigationActions.navigateIfAllowed(
                    destination,
                    applicationViewModel::canNavigateTo,
                    completedFlow = Screen.Auth,
                )
              }
            },
            getIdToken = AuthRepositoryProvider::requestGoogleIdToken,
        )
      }
    }

    navigation(
        startDestination = Screen.Preferences.route,
        route = Screen.Preferences.name,
    ) {
      composable(Screen.Preferences.route) {
        // A returning user (Dashboard state) opened this from Settings: edit mode, not onboarding.
        // Fixed for the lifetime of this entry so the saving transition doesn't flip the UI.
        val isEditing = remember { appState == AppUiState.Dashboard }
        PreferencesScreen(
            onImportClick = rememberScheduleImportLauncher(),
            onAddLocationClick = {},
            onSaved = {
              if (isEditing) {
                // Already in Dashboard state: just return to Settings instead of stacking the
                // Dashboard on top of it. The route check keeps a late callback from popping past
                // Settings and emptying the back stack.
                if (navigationActions.currentRoute() == Screen.Preferences.route) {
                  navigationActions.goBack()
                }
              } else {
                applicationViewModel.onPreferencesSaved()
                navigationActions.navigateIfAllowed(
                    Screen.Dashboard,
                    applicationViewModel::canNavigateTo,
                    completedFlow = Screen.Preferences,
                )
              }
            },
            onBack = if (isEditing) navigationActions::goBack else null,
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
            onBackClick = navigationActions::goBack,
            onEditUsernameClick = { /* TODO */ },
            onChangeProfilePictureClick = { /* TODO */ },
            onModifyPreferencesClick = {
              navigationActions.navigateIfAllowed(
                  Screen.Preferences,
                  applicationViewModel::canNavigateTo,
              )
            },
            onManageFriendsClick = { /* TODO */ },
            onLogoutSuccess = {
              navigationActions.navigateToAndClear(Screen.Auth, Screen.Auth, clearAll = true)
            },
        )
      }
    }
  }
}
