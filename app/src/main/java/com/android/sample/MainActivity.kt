// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude AI Agent
// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini AI Agent
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.android.sample.model.auth.AuthRepositoryProvider
import com.android.sample.ui.app.AppUiState
import com.android.sample.ui.app.AppViewModel
import com.android.sample.ui.app.appViewModel
import com.android.sample.ui.auth.SignInRoute
import com.android.sample.ui.calendar.CalendarRoute
import com.android.sample.ui.dashboard.DashboardScreen
import com.android.sample.ui.eventplanning.AddEventScreen
import com.android.sample.ui.eventplanning.EditEventScreen
import com.android.sample.ui.eventplanning.EventDetailsScreen
import com.android.sample.ui.eventplanning.EventKind
import com.android.sample.ui.eventplanning.eventDetailsViewModel
import com.android.sample.ui.groupsession.GroupSessionScreen
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.preferences.PreferencesScreen
import com.android.sample.ui.preferences.rememberScheduleImportLauncher
import com.android.sample.ui.settings.SettingsScreen
import com.android.sample.ui.sharedComponents.rememberNotImplementedToast
import com.android.sample.ui.theme.DidYouStudyTheme

private const val EVENT_EDITED_RESULT = "eventEdited"

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
fun DidYouStudyApp(
    applicationViewModel: AppViewModel = appViewModel(),
) {
  val appState by applicationViewModel.uiState.collectAsStateWithLifecycle()

  var initialDestination by remember {
    mutableStateOf(applicationViewModel.resolveStartDestination(appState))
  }
  if (initialDestination == null) {
    initialDestination = applicationViewModel.resolveStartDestination(appState)
  }

  val start = initialDestination
  if (start == null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(
          modifier = Modifier.testTag(NavigationTestTags.LOADING_INDICATOR),
      )
    }
    return
  }

  val navController = rememberNavController()
  val navigationActions = NavigationActions(navController)

  LaunchedEffect(appState) {
    if (appState == AppUiState.LogOut && navigationActions.currentRoute() != Screen.Auth.route) {
      navigationActions.navigateToAndClear(Screen.Auth, Screen.Auth, clearAll = true)
    }
  }

  NavHost(
      navController = navController,
      startDestination = start.name,
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
        // A returning user opened this from Settings: edit mode with back navigation.
        // Onboarding has no previous back stack entry. Fixed for the lifetime of this entry.
        val isEditing = remember { navController.previousBackStackEntry != null }
        PreferencesScreen(
            onImportClick = rememberScheduleImportLauncher(),
            // Study-location import is not built yet.
            onAddLocationClick = rememberNotImplementedToast(),
            onSaved = {
              if (isEditing) {
                // Only handle the save while its Preferences entry is current. goBack() separately
                // protects Dashboard from being removed.
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
            onEventClick = {
              navigationActions.navigateIfAllowed(
                  Screen.AddEvent,
                  applicationViewModel::canNavigateTo,
              )
            },
            onGroupSessionClick = {
              navigationActions.navigateIfAllowed(
                  Screen.AddGroupSession,
                  applicationViewModel::canNavigateTo,
              )
            },
        )
      }
    }

    navigation(
        startDestination = Screen.Calendar.route,
        route = Screen.Calendar.name,
    ) {
      composable(Screen.Calendar.route) { entry ->
        CalendarRoute(
            navigationActions = navigationActions,
            onAddEventClick = {
              navigationActions.navigateIfAllowed(
                  Screen.AddEvent,
                  applicationViewModel::canNavigateTo,
              )
            },
            onEventClick = { eventId ->
              navController.runIfCurrentEntry(entry) {
                navigationActions.navigateIfAllowed(
                    Screen.EventDetails(eventId),
                    applicationViewModel::canNavigateTo,
                )
              }
            },
            onAddGroupSessionClick = {
              navigationActions.navigateIfAllowed(
                  Screen.AddGroupSession,
                  applicationViewModel::canNavigateTo,
              )
            },
        )
      }
    }

    navigation(
        startDestination = Screen.AddEvent.route,
        route = Screen.AddEvent.name,
    ) {
      val renderAddEvent: @Composable (EventKind, String) -> Unit = { initialKind, routePrefix ->
        AddEventScreen(
            initialKind = initialKind,
            onBackClick = {
              if (navigationActions.currentRoute().startsWith(routePrefix)) {
                navigationActions.goBack()
              }
            },
            onEventSaved = {
              if (navigationActions.currentRoute().startsWith(routePrefix)) {
                navigationActions.goBack()
              }
            },
        )
      }

      composable(Screen.AddEvent.route) {
        renderAddEvent(EventKind.PERSONAL, Screen.AddEvent.route)
      }

      composable(Screen.AddGroupSession.route) {
        renderAddEvent(EventKind.GROUP_SESSION, Screen.AddGroupSession.route)
      }

      editEventDestination(navController, navigationActions)
    }

    composable(
        Screen.EventDetails.ROUTE,
        arguments = listOf(navArgument(Screen.EVENT_ID_ARGUMENT) { type = NavType.StringType }),
    ) { entry ->
      val eventId = entry.arguments?.getString(Screen.EVENT_ID_ARGUMENT) ?: return@composable
      val viewModel = eventDetailsViewModel()
      val eventEdited by
          entry.savedStateHandle
              .getStateFlow(EVENT_EDITED_RESULT, false)
              .collectAsStateWithLifecycle()
      LaunchedEffect(eventEdited) {
        if (eventEdited) {
          viewModel.loadEvent(eventId, refresh = true)
          entry.savedStateHandle[EVENT_EDITED_RESULT] = false
        }
      }
      EventDetailsScreen(
          eventId = eventId,
          viewModel = viewModel,
          onNavigateBack = { navController.runIfCurrentEntry(entry, navigationActions::goBack) },
          onEdit = { selectedEventId ->
            navController.runIfCurrentEntry(entry) {
              navigationActions.navigateIfAllowed(
                  Screen.EditEvent(selectedEventId),
                  applicationViewModel::canNavigateTo,
              )
            }
          },
      )
    }

    navigation(
        startDestination = Screen.GroupSession.route,
        route = Screen.GroupSession.name,
    ) {
      composable(Screen.GroupSession.route) {
        GroupSessionScreen(
            navigationActions = navigationActions,
            onAddSessionClick = {
              navigationActions.navigateIfAllowed(
                  Screen.AddGroupSession,
                  applicationViewModel::canNavigateTo,
              )
            },
            onEditSessionClick = { sessionId ->
              navigationActions.navigateIfAllowed(
                  Screen.EditEvent(sessionId, EventKind.GROUP_SESSION),
                  applicationViewModel::canNavigateTo,
              )
            },
        )
      }
    }

    navigation(
        startDestination = Screen.Settings.route,
        route = Screen.Settings.name,
    ) {
      composable(Screen.Settings.route) {
        // Profile editing and friends management are not built yet.
        val notImplemented = rememberNotImplementedToast()
        SettingsScreen(
            onBackClick = navigationActions::goBack,
            onEditUsernameClick = notImplemented,
            onChangeProfilePictureClick = notImplemented,
            onModifyPreferencesClick = {
              navigationActions.navigateIfAllowed(
                  Screen.Preferences,
                  applicationViewModel::canNavigateTo,
              )
            },
            onManageFriendsClick = notImplemented,
        )
      }
    }
  }
}

/** Registers the editor shared by personal event details and the group session list. */
private fun NavGraphBuilder.editEventDestination(
    navController: NavHostController,
    navigationActions: NavigationActions,
) {
  composable(
      route = Screen.EditEvent.ROUTE,
      arguments =
          listOf(
              navArgument(Screen.EVENT_ID_ARGUMENT) { type = NavType.StringType },
              navArgument("kind") {
                type = NavType.StringType
                defaultValue = EventKind.PERSONAL.name
              },
          ),
  ) { entry ->
    val eventId = entry.arguments?.getString(Screen.EVENT_ID_ARGUMENT).orEmpty()
    if (eventId.isBlank()) {
      LaunchedEffect(Unit) { navController.runIfCurrentEntry(entry, navigationActions::goBack) }
      return@composable
    }
    val kind =
        entry.arguments?.getString("kind")?.let {
          runCatching { EventKind.valueOf(it) }.getOrNull()
        } ?: EventKind.PERSONAL
    EditEventScreen(
        eventId = eventId,
        eventKind = kind,
        onBackClick = { navController.runIfCurrentEntry(entry, navigationActions::goBack) },
        onEventSaved = {
          navController.runIfCurrentEntry(entry) {
            navController
                .previousEventDetails(eventId, kind)
                ?.savedStateHandle
                ?.set(EVENT_EDITED_RESULT, true)
            navigationActions.goBack()
          }
        },
        onEventDelete = {
          navController.runIfCurrentEntry(entry) {
            if (navController.previousEventDetails(eventId, kind) != null) {
              navigationActions.goBackPast(Screen.EventDetails(eventId))
            } else {
              navigationActions.goBack()
            }
          }
        },
    )
  }
}

/** Finds the matching personal event details entry that opened the editor. */
private fun NavHostController.previousEventDetails(
    eventId: String,
    kind: EventKind,
): NavBackStackEntry? {
  if (kind != EventKind.PERSONAL) return null
  return previousBackStackEntry?.takeIf {
    it.destination.route == Screen.EventDetails.ROUTE &&
        it.arguments?.getString(Screen.EVENT_ID_ARGUMENT) == eventId
  }
}

/** Runs a navigation callback only while its originating back-stack entry is current. */
private fun NavHostController.runIfCurrentEntry(entry: NavBackStackEntry, action: () -> Unit) {
  if (currentBackStackEntry == entry) action()
}
