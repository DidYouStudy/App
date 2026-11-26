// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Copilot
// Co-authored-by: Gemini
// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: OpenAI Codex
package com.android.sample.ui.groupsession

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.ui.eventplanning.EventKind
import com.android.sample.ui.navigation.BottomNavigationMenu
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import com.android.sample.ui.sharedComponents.AddButton
import com.android.sample.ui.sharedComponents.EventTypeBadge
import com.android.sample.ui.sharedComponents.MainTopAppBar
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.color
import com.android.sample.ui.theme.label
import com.android.sample.ui.theme.onColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Mode for displaying group sessions: a list of cards or an interactive campus map. */
enum class GroupSessionViewMode {
  LIST,
  MAP,
}

object GroupSessionScreenTestTags {
  const val SCREEN = "groupSessionScreen"
  const val SEARCH = "groupSessionSearch"
  const val FILTER_BUTTON = "groupSessionFilterButton"
  const val ALL_SESSIONS_FILTER = "groupSessionAllFilter"
  const val MY_SESSIONS_FILTER = "groupSessionMyFilter"
  const val ACTIVE_TITLE = "groupSessionActiveTitle"
  const val LOADING = "groupSessionLoading"
  const val ERROR_SNACKBAR = "groupSessionErrorSnackbar"
  const val ERROR_DISMISS = "groupSessionErrorDismiss"
  const val SESSION_LIST = "groupSessionList"
  const val ADD_BUTTON = "groupSessionAddButton"
  const val SESSION_ITEM_PREFIX = "groupSessionItem_"
  const val VIEW_SWITCHER_PILL = "groupSessionViewSwitcherPill"
  const val LIST_VIEW_BUTTON = "groupSessionListViewButton"
  const val MAP_VIEW_BUTTON = "groupSessionMapViewButton"
  const val MAP_CONTENT = "groupSessionMapContent"
  const val LOCATION_PILL = "groupSessionLocationPill"
  const val PARTICIPANTS_PILL = "groupSessionParticipantsPill"
  const val CENTER_LOCATION_BUTTON = "groupSessionCenterLocationButton"
  const val OFFLINE_INDICATOR = "groupSessionOfflineIndicator"
  const val APPROXIMATE_LOCATION_DIALOG = "groupSessionApproximateLocationDialog"
  const val APPROXIMATE_LOCATION_CONFIRM = "groupSessionApproximateLocationConfirm"
  const val PRECISE_SETTINGS_DIALOG = "groupSessionPreciseSettingsDialog"
  const val PRECISE_SETTINGS_OPEN = "groupSessionPreciseSettingsOpen"
  const val PRECISE_SETTINGS_DISMISS = "groupSessionPreciseSettingsDismiss"

  fun sessionItem(index: Int) = "$SESSION_ITEM_PREFIX$index"

  fun joinButton(sessionId: String) = "groupSessionJoin_$sessionId"

  fun leaveButton(sessionId: String) = "groupSessionLeave_$sessionId"

  fun actionProgress(sessionId: String) = "groupSessionActionProgress_$sessionId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSessionScreen(
    navigationActions: NavigationActions? = null,
    viewModel: GroupSessionsViewModel = groupSessionsViewModel(),
    initialViewMode: GroupSessionViewMode = GroupSessionViewMode.LIST,
    onAddSessionClick: () -> Unit = { navigationActions?.navigateTo(Screen.AddGroupSession) },
    onEditSessionClick: (String) -> Unit = { sessionId ->
      navigationActions?.navigateTo(Screen.EditEvent(sessionId, EventKind.GROUP_SESSION))
    },
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var viewMode by rememberSaveable { mutableStateOf(initialViewMode) }
  var selectedRoom by rememberSaveable { mutableStateOf<String?>(null) }
  var showFilterSheet by rememberSaveable { mutableStateOf(false) }
  val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val coroutineScope = rememberCoroutineScope()
  val hasLocationPermission = uiState.hasPreciseLocationPermission
  val locationPermissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
          results ->
        val fineGranted = isLocationPermissionGranted(results) || hasFineLocationPermission(context)
        viewModel.onSystemPermissionResult(fineGranted)
      }
  val dispatchPendingPrompt = {
    if (viewModel.uiState.value.pendingPermissionPrompt) {
      viewModel.onPermissionPromptLaunched()
      launchLocationPermissionRequest(context, locationPermissionLauncher)
    }
  }

  PreciseLocationPermissionEffects(
      viewMode = viewMode,
      permissionDialog = uiState.permissionDialog,
      pendingPermissionPrompt = uiState.pendingPermissionPrompt,
      viewModel = viewModel,
      onDispatchPendingPrompt = dispatchPendingPrompt,
  )

  val campusCenter = defaultCampusCenter()
  var cameraDestination by remember { mutableStateOf<CameraDestination?>(null) }
  var cameraDestinationTrigger by remember { mutableIntStateOf(0) }

  val snackbarHostState = remember { SnackbarHostState() }
  val onCenterLocationClick: () -> Unit = {
    val userLoc = uiState.userLocation
    val target =
        if (hasLocationPermission && userLoc != null) {
          userLoc
        } else {
          if (!hasLocationPermission) {
            viewModel.onLocationFeatureRequested(hasFineLocationPermission(context))
            dispatchPendingPrompt()
          }
          campusCenter
        }
    selectedRoom = null
    cameraDestination = CameraDestination(target, ++cameraDestinationTrigger)
  }
  fun closeFilterSheet() {
    coroutineScope.launch {
      if (filterSheetState.isVisible) filterSheetState.hide()
      showFilterSheet = false
    }
  }
  GroupSessionErrorEffect(
      error = uiState.error,
      snackbarHostState = snackbarHostState,
      onClearError = viewModel::clearError,
  )
  // Join/leave progress is stored per session in GroupSessionsViewModel.
  Scaffold(
      modifier = Modifier.testTag(GroupSessionScreenTestTags.SCREEN),
      snackbarHost = {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.testTag(GroupSessionScreenTestTags.ERROR_SNACKBAR),
            snackbar = { data ->
              Snackbar(
                  action = {
                    TextButton(
                        onClick = { data.dismiss() },
                        modifier = Modifier.testTag(GroupSessionScreenTestTags.ERROR_DISMISS),
                    ) {
                      Text(stringResource(R.string.group_session_error_dismiss))
                    }
                  },
                  content = { Text(data.visuals.message) },
              )
            },
        )
      },
      topBar = {
        MainTopAppBar(
            title = stringResource(R.string.group_session_title),
            onProfileClick = { navigationActions?.navigateTo(Screen.Settings) },
        )
      },
      bottomBar = {
        BottomNavigationMenu(
            selectedTab = Tab.GroupSession,
            onTabSelected = { tab -> navigationActions?.navigateTo(tab.destination) },
            modifier = Modifier.testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU),
        )
      },
      floatingActionButton = {
        AddButton(
            onClick = onAddSessionClick,
            contentDescription = stringResource(R.string.group_session_add_description),
            modifier = Modifier.testTag(GroupSessionScreenTestTags.ADD_BUTTON),
            // Creating a session needs the server, so it is unavailable while offline.
            enabled = !uiState.isOffline,
        )
      },
      content = { pd ->
        GroupSessionContent(
            state = uiState,
            modifier = Modifier.padding(pd),
            viewMode = viewMode,
            onViewModeChange = { newMode ->
              if (newMode == GroupSessionViewMode.LIST) {
                cameraDestination = null
              }
              viewMode = newMode
            },
            selectedRoom = selectedRoom,
            onSelectedRoomChange = { room ->
              selectedRoom = room
              if (room != null) {
                cameraDestination = null
              }
            },
            onLocationClick = { item ->
              selectedRoom = item.proximity.campusLocation?.name ?: item.session.details.location
              cameraDestination = null
              viewMode = GroupSessionViewMode.MAP
            },
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onJoin = viewModel::join,
            onLeave = viewModel::leave,
            onEdit = onEditSessionClick,
            onOpenFilters = { showFilterSheet = true },
            onCenterLocationClick = onCenterLocationClick,
            cameraDestination = cameraDestination,
            hasLocationPermission = hasLocationPermission,
            onParticipantsClick = viewModel::openParticipants,
        )
      },
  )
  uiState.participantsSheet?.let { sheet ->
    GroupSessionParticipantsSheet(state = sheet, onDismiss = viewModel::dismissParticipants)
  }
  if (showFilterSheet) {
    GroupSessionFilterModal(
        filterSheetState = filterSheetState,
        uiState = uiState,
        hasLocationPermission = hasLocationPermission,
        viewModel = viewModel,
        onLocationPermissionNeeded = { radius ->
          viewModel.onLocationFeatureRequested(
              hasFineLocationPermission(context),
              pendingRadius = radius,
          )
          dispatchPendingPrompt()
        },
        onDismiss = ::closeFilterSheet,
    )
  }
}

/** Checks whether `ACCESS_FINE_LOCATION` is currently granted in the OS. */
private fun hasFineLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Launches the OS location permission prompt, requesting only `ACCESS_FINE_LOCATION` when coarse
 * location is already granted so Android 12+ shows the precise-location upgrade prompt.
 */
private fun launchLocationPermissionRequest(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
  val coarseAlreadyGranted =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
          PackageManager.PERMISSION_GRANTED
  val permissions =
      if (coarseAlreadyGranted) {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
      } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      }
  launcher.launch(permissions)
}

/**
 * Bridges Android permission prompts, `ON_RESUME` lifecycle events, and permission dialogs to
 * [GroupSessionsViewModel] without owning any permission policy state.
 */
@Composable
private fun PreciseLocationPermissionEffects(
    viewMode: GroupSessionViewMode,
    permissionDialog: ActivePermissionDialog,
    pendingPermissionPrompt: Boolean,
    viewModel: GroupSessionsViewModel,
    onDispatchPendingPrompt: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  LaunchedEffect(Unit) {
    viewModel.syncPreciseLocationPermission(hasFineLocationPermission(context))
  }

  LaunchedEffect(viewMode) {
    if (viewMode == GroupSessionViewMode.MAP) {
      viewModel.onMapEntered(hasFineLocationPermission(context))
      onDispatchPendingPrompt()
    }
  }

  LaunchedEffect(pendingPermissionPrompt) {
    if (pendingPermissionPrompt) {
      onDispatchPendingPrompt()
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed(hasFineLocationPermission(context))
        onDispatchPendingPrompt()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  PreciseLocationDialogs(
      activeDialog = permissionDialog,
      onExplanationDismissed = {
        viewModel.onExplanationDismissed()
        onDispatchPendingPrompt()
      },
      onSettingsOpened = {
        viewModel.onSettingsOpened()
        openAppSettings(context)
      },
      onSettingsDismissed = viewModel::onSettingsDialogDismissed,
  )
}

/** Renders the active in-app permission dialog (`EXPLANATION` or `SETTINGS`) when needed. */
@Composable
private fun PreciseLocationDialogs(
    activeDialog: ActivePermissionDialog,
    onExplanationDismissed: () -> Unit,
    onSettingsOpened: () -> Unit,
    onSettingsDismissed: () -> Unit,
) {
  when (activeDialog) {
    ActivePermissionDialog.EXPLANATION -> {
      AlertDialog(
          onDismissRequest = onExplanationDismissed,
          title = { Text(stringResource(R.string.group_session_precise_location_title)) },
          text = { Text(stringResource(R.string.group_session_precise_location_message)) },
          confirmButton = {
            TextButton(
                onClick = onExplanationDismissed,
                modifier =
                    Modifier.testTag(GroupSessionScreenTestTags.APPROXIMATE_LOCATION_CONFIRM),
            ) {
              Text(stringResource(R.string.group_session_precise_location_confirm))
            }
          },
          modifier = Modifier.testTag(GroupSessionScreenTestTags.APPROXIMATE_LOCATION_DIALOG),
      )
    }
    ActivePermissionDialog.SETTINGS -> {
      AlertDialog(
          onDismissRequest = onSettingsDismissed,
          title = { Text(stringResource(R.string.group_session_precise_settings_title)) },
          text = { Text(stringResource(R.string.group_session_precise_settings_message)) },
          confirmButton = {
            TextButton(
                onClick = onSettingsOpened,
                modifier = Modifier.testTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_OPEN),
            ) {
              Text(stringResource(R.string.group_session_precise_settings_open))
            }
          },
          dismissButton = {
            TextButton(
                onClick = onSettingsDismissed,
                modifier = Modifier.testTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_DISMISS),
            ) {
              Text(stringResource(R.string.group_session_precise_settings_dismiss))
            }
          },
          modifier = Modifier.testTag(GroupSessionScreenTestTags.PRECISE_SETTINGS_DIALOG),
      )
    }
    ActivePermissionDialog.NONE -> Unit
  }
}

/** Opens the system App Info Settings screen so the user can grant precise location manually. */
private fun openAppSettings(context: Context) {
  val intent =
      Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.fromParts("package", context.packageName, null),
      )
  try {
    context.startActivity(intent)
  } catch (_: ActivityNotFoundException) {
    // Ignore in environments without a Settings activity.
  }
}

/** Shows a snackbar when [error] is non-null and clears it once dismissed. */
@Composable
private fun GroupSessionErrorEffect(
    error: GroupSessionsError?,
    snackbarHostState: SnackbarHostState,
    onClearError: () -> Unit,
) {
  val loadErrorMessage = stringResource(R.string.group_session_error_load)
  val locationLoadErrorMessage = stringResource(R.string.group_session_error_location_load)
  val joinErrorMessage = stringResource(R.string.group_session_error_join)
  val leaveErrorMessage = stringResource(R.string.group_session_error_leave)
  val offlineErrorMessage = stringResource(R.string.group_session_error_offline)
  val connectionLostErrorMessage = stringResource(R.string.group_session_error_connection_lost)
  LaunchedEffect(error) {
    error?.let { activeError ->
      val msg =
          when (activeError) {
            GroupSessionsError.LOAD_FAILED -> loadErrorMessage
            GroupSessionsError.LOCATION_LOAD_FAILED -> locationLoadErrorMessage
            GroupSessionsError.JOIN_FAILED -> joinErrorMessage
            GroupSessionsError.LEAVE_FAILED -> leaveErrorMessage
            GroupSessionsError.OFFLINE -> offlineErrorMessage
            GroupSessionsError.CONNECTION_LOST -> connectionLostErrorMessage
          }
      snackbarHostState.showSnackbar(msg)
      onClearError()
    }
  }
}

/** Modal bottom sheet hosting the advanced group-session filter controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSessionFilterModal(
    filterSheetState: androidx.compose.material3.SheetState,
    uiState: GroupSessionsUiState,
    hasLocationPermission: Boolean,
    viewModel: GroupSessionsViewModel,
    onLocationPermissionNeeded: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
  ModalBottomSheet(
      sheetState = filterSheetState,
      onDismissRequest = onDismiss,
  ) {
    GroupSessionFilterSheetContent(
        filters =
            GroupSessionFilters(
                sessionTypes = uiState.selectedSessionTypes,
                startingTime = uiState.startingTimeFilter,
                hideFullSessions = uiState.hideFullSessions,
                radiusMeters = uiState.selectedRadiusMeters,
                zones = uiState.selectedZones,
                showLabs = uiState.showLabRoomType,
            ),
        availableZones = uiState.availableZones,
        matchingSessionCount = uiState.sessions.size,
        hasLocationPermission = hasLocationPermission,
        onRadiusSelected = viewModel::onRadiusFilterSelected,
        onZonesSelected = viewModel::onZonesFilterSelected,
        onShowLabsChanged = viewModel::onShowLabRoomTypeChange,
        onSessionTypesSelected = viewModel::onSessionTypesSelected,
        onStartingTimeSelected = viewModel::onStartingTimeFilterSelected,
        onHideFullSessionsChange = viewModel::onHideFullSessionsChange,
        onLocationPermissionNeeded = onLocationPermissionNeeded,
        onResetAll = viewModel::resetAdvancedFilters,
        onShowSessions = onDismiss,
    )
  }
}

@Composable
internal fun GroupSessionContent(
    state: GroupSessionsUiState,
    modifier: Modifier = Modifier,
    viewMode: GroupSessionViewMode = GroupSessionViewMode.LIST,
    onViewModeChange: (GroupSessionViewMode) -> Unit = {},
    selectedRoom: String? = null,
    onSelectedRoomChange: (String?) -> Unit = {},
    onLocationClick: (GroupSessionItem) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onFilterChange: (GroupSessionFilter) -> Unit = {},
    onJoin: (String) -> Unit = {},
    onLeave: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    onOpenFilters: () -> Unit = {},
    onCenterLocationClick: (() -> Unit)? = null,
    cameraDestination: CameraDestination? = null,
    hasLocationPermission: Boolean = false,
    onParticipantsClick: (String) -> Unit = {},
) {
  when (viewMode) {
    GroupSessionViewMode.LIST -> {
      Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.fillMaxSize().padding(horizontal = Spacing.large, vertical = Spacing.small)
        ) {
          GroupSessionSearchBar(
              query = state.query,
              onQueryChange = onQueryChange,
              onFilterClick = onOpenFilters,
          )
          Spacer(Modifier.height(Spacing.small))
          GroupSessionFilterRow(
              filter = state.filter,
              onFilterChange = onFilterChange,
          )
          if (state.isOffline) {
            Spacer(Modifier.height(Spacing.small))
            GroupSessionOfflineIndicator()
          }
          Text(
              stringResource(R.string.group_session_active_sessions_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              modifier =
                  Modifier.padding(vertical = Spacing.mediumSmall)
                      .testTag(GroupSessionScreenTestTags.ACTIVE_TITLE),
          )
          when {
            state.isLoading ->
                Text(
                    stringResource(R.string.group_session_loading),
                    modifier = Modifier.testTag(GroupSessionScreenTestTags.LOADING),
                )
            state.sessions.isEmpty() -> Text(stringResource(R.string.group_session_empty))
            else ->
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium),
                    contentPadding = PaddingValues(bottom = Spacing.small),
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(bottom = Dimens.groupSessionViewSwitcherClearance)
                            .testTag(GroupSessionScreenTestTags.SESSION_LIST),
                ) {
                  itemsIndexed(
                      items = state.sessions,
                      key = { _, item -> item.session.id },
                  ) { index, item ->
                    GroupSessionCard(
                        item = item,
                        now = state.now,
                        onJoin = onJoin,
                        onLeave = onLeave,
                        onEdit = onEdit,
                        onLocationClick = onLocationClick,
                        onParticipantsClick = { onParticipantsClick(item.session.id) },
                        actionsEnabled = !state.isOffline,
                        modifier = Modifier.testTag(GroupSessionScreenTestTags.sessionItem(index)),
                    )
                  }
                }
          }
        }
        GroupSessionViewSwitcher(
            viewMode = viewMode,
            onViewModeChange = onViewModeChange,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.large),
        )
      }
    }
    GroupSessionViewMode.MAP -> {
      Box(modifier = modifier.fillMaxSize().testTag(GroupSessionScreenTestTags.MAP_CONTENT)) {
        GroupSessionMapContent(
            rooms = state.rooms,
            now = state.now,
            onJoin = onJoin,
            onLeave = onLeave,
            onEdit = onEdit,
            selectedRoom = selectedRoom,
            onSelectedRoomChange = onSelectedRoomChange,
            onLocationClick = onLocationClick,
            userLocation = state.userLocation,
            cameraDestination = cameraDestination,
            hasLocationPermission = hasLocationPermission,
            onParticipantsClick = onParticipantsClick,
            actionsEnabled = !state.isOffline,
        )
        Column(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.large, vertical = Spacing.small),
        ) {
          GroupSessionSearchBar(
              query = state.query,
              onQueryChange = onQueryChange,
              onFilterClick = onOpenFilters,
          )
          Spacer(Modifier.height(Spacing.small))
          GroupSessionFilterRow(
              filter = state.filter,
              onFilterChange = onFilterChange,
              onCenterLocationClick = onCenterLocationClick,
          )
          if (state.isOffline) {
            Spacer(Modifier.height(Spacing.small))
            GroupSessionOfflineIndicator()
          }
        }
        if (selectedRoom == null) {
          GroupSessionViewSwitcher(
              viewMode = viewMode,
              onViewModeChange = onViewModeChange,
              modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.large),
          )
        }
      }
    }
  }
}

/** Filter chips row (All sessions / My sessions) used across both list and map views. */
@Composable
internal fun GroupSessionFilterRow(
    filter: GroupSessionFilter,
    onFilterChange: (GroupSessionFilter) -> Unit,
    modifier: Modifier = Modifier,
    onCenterLocationClick: (() -> Unit)? = null,
) {
  val chipColors =
      FilterChipDefaults.filterChipColors(
          containerColor = MaterialTheme.colorScheme.surface,
          selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
      )

  Row(
      modifier = modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      FilterChip(
          modifier = Modifier.testTag(GroupSessionScreenTestTags.ALL_SESSIONS_FILTER),
          selected = filter == GroupSessionFilter.ALL,
          onClick = { onFilterChange(GroupSessionFilter.ALL) },
          label = { Text(stringResource(R.string.group_session_filter_all)) },
          leadingIcon =
              if (filter == GroupSessionFilter.ALL) {
                {
                  Icon(
                      Icons.Default.Check,
                      contentDescription = null,
                      modifier = Modifier.size(Dimens.iconSizeSmall),
                  )
                }
              } else null,
          shape = RoundedCornerShape(Dimens.cornerMedium),
          colors = chipColors,
          border =
              FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = filter == GroupSessionFilter.ALL,
              ),
      )
      FilterChip(
          modifier = Modifier.testTag(GroupSessionScreenTestTags.MY_SESSIONS_FILTER),
          selected = filter == GroupSessionFilter.JOINED,
          onClick = { onFilterChange(GroupSessionFilter.JOINED) },
          label = { Text(stringResource(R.string.group_session_filter_my)) },
          leadingIcon =
              if (filter == GroupSessionFilter.JOINED) {
                {
                  Icon(
                      Icons.Default.Check,
                      contentDescription = null,
                      modifier = Modifier.size(Dimens.iconSizeSmall),
                  )
                }
              } else null,
          shape = RoundedCornerShape(Dimens.cornerMedium),
          colors = chipColors,
          border =
              FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = filter == GroupSessionFilter.JOINED,
              ),
      )
    }
    if (onCenterLocationClick != null) {
      FilledIconButton(
          onClick = onCenterLocationClick,
          modifier = Modifier.testTag(GroupSessionScreenTestTags.CENTER_LOCATION_BUTTON),
          shape = FloatingActionButtonDefaults.shape,
          colors =
              IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.surface,
                  contentColor = MaterialTheme.colorScheme.primary,
              ),
      ) {
        Icon(
            Icons.Default.MyLocation,
            contentDescription = stringResource(R.string.group_session_center_location_description),
        )
      }
    }
  }
}

/** Search bar used across both list and map modes of the group sessions screen. */
@Composable
internal fun GroupSessionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onFilterClick: () -> Unit = {},
) {
  OutlinedTextField(
      value = query,
      onValueChange = onQueryChange,
      modifier = modifier.fillMaxWidth().testTag(GroupSessionScreenTestTags.SEARCH),
      singleLine = true,
      shape = RoundedCornerShape(Dimens.cornerFull),
      leadingIcon = {
        Icon(
            Icons.Default.Search,
            contentDescription = stringResource(R.string.group_session_search_icon_description),
        )
      },
      trailingIcon = {
        IconButton(
            modifier = Modifier.testTag(GroupSessionScreenTestTags.FILTER_BUTTON),
            onClick = onFilterClick,
        ) {
          Icon(
              Icons.Outlined.Tune,
              contentDescription = stringResource(R.string.group_session_filter_icon_description),
          )
        }
      },
      placeholder = { Text(stringResource(R.string.group_session_search_placeholder)) },
      colors =
          OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedBorderColor = Color.Transparent,
              focusedBorderColor = Color.Transparent,
          ),
  )
}

/**
 * Banner telling the student the device is offline, so the sessions shown are the ones already
 * loaded and saved on the device.
 *
 * Shown in both the list and the map view while [GroupSessionsUiState.isOffline] is true, and
 * removed as soon as the connection returns.
 */
@Composable
internal fun GroupSessionOfflineIndicator(modifier: Modifier = Modifier) {
  Surface(
      modifier = modifier.fillMaxWidth().testTag(GroupSessionScreenTestTags.OFFLINE_INDICATOR),
      shape = RoundedCornerShape(Dimens.cornerMedium),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.Outlined.CloudOff,
          contentDescription = null,
          modifier = Modifier.size(Dimens.iconSizeMedium),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.width(Spacing.small))
      Text(
          text = stringResource(R.string.group_session_offline_indicator),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Floating segmented pill control toggling between list and map views of group sessions. */
@Composable
internal fun GroupSessionViewSwitcher(
    viewMode: GroupSessionViewMode,
    onViewModeChange: (GroupSessionViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier = modifier.testTag(GroupSessionScreenTestTags.VIEW_SWITCHER_PILL),
      shape = RoundedCornerShape(Dimens.cornerFull),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shadowElevation = Spacing.extraSmall,
  ) {
    Row(
        modifier = Modifier.padding(Spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      ViewSwitcherOption(
          label = stringResource(R.string.group_session_view_mode_list),
          icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
          selected = viewMode == GroupSessionViewMode.LIST,
          onClick = { onViewModeChange(GroupSessionViewMode.LIST) },
          modifier = Modifier.testTag(GroupSessionScreenTestTags.LIST_VIEW_BUTTON),
      )
      ViewSwitcherOption(
          label = stringResource(R.string.group_session_view_mode_map),
          icon = Icons.Outlined.Map,
          selected = viewMode == GroupSessionViewMode.MAP,
          onClick = { onViewModeChange(GroupSessionViewMode.MAP) },
          modifier = Modifier.testTag(GroupSessionScreenTestTags.MAP_VIEW_BUTTON),
      )
    }
  }
}

@Composable
private fun ViewSwitcherOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val selectedBgColor = MaterialTheme.colorScheme.secondaryContainer
  val unselectedBgColor = Color.Transparent
  val contentColor =
      if (selected) MaterialTheme.colorScheme.onSecondaryContainer
      else MaterialTheme.colorScheme.onSurfaceVariant

  Row(
      modifier =
          modifier
              .clip(RoundedCornerShape(Dimens.cornerFull))
              .background(if (selected) selectedBgColor else unselectedBgColor)
              .clickable(onClick = onClick)
              .padding(horizontal = Spacing.large, vertical = Spacing.small),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
  ) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(Dimens.iconSizeMedium),
        tint = contentColor,
    )
    Spacer(modifier = Modifier.width(Spacing.small))
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = contentColor,
    )
  }
}

/**
 * One session of the list or of a map room.
 *
 * @param actionsEnabled whether the join, leave and edit buttons can be pressed; false while the
 *   device is offline, because those actions need the server.
 */
@Composable
internal fun GroupSessionCard(
    item: GroupSessionItem,
    now: Instant,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    onParticipantsClick: () -> Unit = {},
    onLocationClick: (GroupSessionItem) -> Unit = {},
    actionsEnabled: Boolean = true,
) {
  val session = item.session
  val statusLabel = sessionStatusLabel(session.details.startsAt, now)
  val startLabel = sessionTimeLabel(session.details.startsAt)
  val endLabel = sessionTimeLabel(session.details.endsAt)
  val dateLabel = sessionDateLabel(session.details.startsAt, now)
  val shouldShowHost = item.isCreator

  val subjectFallback = stringResource(R.string.group_session_subject_fallback)

  Card(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(Dimens.cornerExtraLarge),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = Dimens.groupSessionCardElevation),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(Dimens.cardPadding)) {
      // Top Row: Avatar + Title/Host + Status Badge
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Top,
      ) {
        Box(
            modifier =
                Modifier.size(Dimens.avatarSizeLarge)
                    .clip(CircleShape)
                    .background(session.details.type.color),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = (session.details.subject ?: subjectFallback).take(1).uppercase(),
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleLarge,
              color = session.details.type.onColor,
          )
        }

        Spacer(Modifier.width(Spacing.mediumLarge))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = session.details.subject ?: session.details.type.label(),
              fontWeight = FontWeight.Bold,
              maxLines = 2, // Allows title to expand vertically before truncating
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(Spacing.extraExtraSmall))
          Text(
              text =
                  if (shouldShowHost) stringResource(R.string.group_session_host_self)
                  else
                      item.hostName?.let { stringResource(R.string.group_session_hosted_by, it) }
                          ?: stringResource(R.string.group_session_host_unknown),
              style = MaterialTheme.typography.bodyMedium,
              color =
                  if (shouldShowHost) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = if (shouldShowHost) FontWeight.Bold else FontWeight.Normal,
          )
        }

        Spacer(Modifier.width(Spacing.small))

        Surface(
            shape = RoundedCornerShape(Dimens.cornerLarge),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
          Text(
              text = statusLabel,
              modifier =
                  Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
          )
        }
      }

      Spacer(Modifier.height(Spacing.mediumLarge))

      // Type Badge
      EventTypeBadge(type = session.details.type)

      Spacer(Modifier.height(Spacing.mediumSmall))

      // Description
      Text(
          text =
              session.details.description
                  ?: stringResource(R.string.group_session_description_fallback),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(Modifier.height(Spacing.mediumLarge))

      // Meta Info Pills: Location on top, Date/Time below (Full Width to prevent text clipping)
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        val locationText =
            session.details.location?.let { loc ->
              item.proximity.formattedDistance?.let { dist ->
                stringResource(R.string.group_session_location_with_distance, loc, dist)
              } ?: loc
            } ?: stringResource(R.string.group_session_location_fallback)

        MetaInfoPill(
            modifier = Modifier.fillMaxWidth().testTag(GroupSessionScreenTestTags.LOCATION_PILL),
            icon = {
              Icon(
                  Icons.Outlined.LocationOn,
                  contentDescription = null,
                  modifier = Modifier.size(Dimens.iconSizeSmall),
              )
            },
            text = locationText,
            onClick =
                if (session.details.location != null) {
                  { onLocationClick(item) }
                } else null,
        )
        MetaInfoPill(
            modifier = Modifier.fillMaxWidth(),
            icon = {
              Icon(
                  Icons.Default.AccessTime,
                  contentDescription = null,
                  modifier = Modifier.size(Dimens.iconSizeSmall),
              )
            },
            text =
                stringResource(
                    R.string.group_session_date_time_range,
                    dateLabel,
                    startLabel,
                    endLabel,
                ),
        )
      }

      Spacer(Modifier.height(Spacing.large))

      // Thin Divider Line
      HorizontalDivider(
          color = MaterialTheme.colorScheme.outlineVariant,
          thickness = Dimens.borderThin,
      )

      Spacer(Modifier.height(Spacing.mediumLarge))

      // Bottom Row: Compact Participants Pill + Action Buttons
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        val participantCount = session.memberIds.size
        val participantsDescription =
            pluralStringResource(
                R.plurals.group_session_view_participants,
                participantCount,
                participantCount,
            )
        Surface(
            onClick = onParticipantsClick,
            shape = RoundedCornerShape(Dimens.cornerLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier =
                Modifier.height(Dimens.actionButtonHeight)
                    .testTag(GroupSessionScreenTestTags.PARTICIPANTS_PILL)
                    .semantics {
                      contentDescription = participantsDescription
                      role = Role.Button
                    },
        ) {
          Row(
              modifier = Modifier.padding(horizontal = Spacing.mediumSmall).clearAndSetSemantics {},
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
          ) {
            Icon(
                Icons.Outlined.Groups,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeMedium),
            )
            Text(
                stringResource(R.string.group_session_participant_count, participantCount),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.width(Spacing.extraExtraSmall))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        GroupSessionCardActions(item, onJoin, onLeave, onEdit, actionsEnabled)
      }
    }
  }
}

/**
 * Renders the edit, leave, joined, or join actions for a group session card.
 *
 * When [actionsEnabled] is false (offline) the edit, leave and join buttons are disabled; the
 * offline banner explains why.
 */
@Composable
private fun GroupSessionCardActions(
    item: GroupSessionItem,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
    onEdit: (String) -> Unit,
    actionsEnabled: Boolean,
) {
  val sessionId = item.session.id
  val actionEditLabel = stringResource(R.string.group_session_action_edit)
  val actionJoinedLabel = stringResource(R.string.group_session_action_joined)
  val actionJoinLabel = stringResource(R.string.group_session_action_join)
  val actionLeaveLabel = stringResource(R.string.group_session_action_leave)
  val actionJoiningDescription = stringResource(R.string.group_session_action_joining)
  val actionLeavingDescription = stringResource(R.string.group_session_action_leaving)

  // A running request keeps its button enabled for its colors; semantics communicates the busy
  // state to TalkBack. Offline, the buttons are really disabled.
  if (item.isCreator) {
    Button(
        onClick = { onEdit(sessionId) },
        enabled = actionsEnabled,
        shape = RoundedCornerShape(Dimens.cornerLarge),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = Spacing.extraSmall),
        modifier = Modifier.height(Dimens.actionButtonHeight).width(Dimens.actionButtonWidth),
    ) {
      Text(actionEditLabel, fontWeight = FontWeight.Medium)
    }
  } else if (item.isMember) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      OutlinedButton(
          onClick = { if (!item.isActionInProgress) onLeave(sessionId) },
          enabled = actionsEnabled,
          shape = RoundedCornerShape(Dimens.cornerLarge),
          border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.error),
          colors =
              ButtonDefaults.outlinedButtonColors(
                  contentColor = MaterialTheme.colorScheme.error,
              ),
          contentPadding = PaddingValues(horizontal = Spacing.extraSmall),
          modifier =
              Modifier.height(Dimens.actionButtonHeight)
                  .width(Dimens.actionButtonWidth)
                  .testTag(GroupSessionScreenTestTags.leaveButton(sessionId))
                  .semantics { if (item.isActionInProgress) disabled() },
      ) {
        ActionButtonContent(
            isInProgress = item.isActionInProgress,
            label = actionLeaveLabel,
            progressDescription = actionLeavingDescription,
            progressTag = GroupSessionScreenTestTags.actionProgress(sessionId),
        )
      }
      Button(
          onClick = {},
          enabled = false,
          shape = RoundedCornerShape(Dimens.cornerLarge),
          contentPadding = PaddingValues(horizontal = Spacing.extraSmall),
          modifier = Modifier.height(Dimens.actionButtonHeight).width(Dimens.actionButtonWidth),
      ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(Dimens.iconSizeSmall),
        )
        Spacer(Modifier.width(Spacing.extraExtraSmall))
        Text(actionJoinedLabel, fontWeight = FontWeight.Medium)
      }
    }
  } else {
    Button(
        onClick = { if (!item.isActionInProgress) onJoin(sessionId) },
        enabled = actionsEnabled,
        shape = RoundedCornerShape(Dimens.cornerLarge),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        contentPadding = PaddingValues(horizontal = Spacing.extraSmall),
        modifier =
            Modifier.height(Dimens.actionButtonHeight)
                .width(Dimens.actionButtonWidth)
                .testTag(GroupSessionScreenTestTags.joinButton(sessionId))
                .semantics { if (item.isActionInProgress) disabled() },
    ) {
      ActionButtonContent(
          isInProgress = item.isActionInProgress,
          label = actionJoinLabel,
          progressDescription = actionJoiningDescription,
          progressTag = GroupSessionScreenTestTags.actionProgress(sessionId),
      )
    }
  }
}

/** Renders an action button's progress indicator or label. */
@Composable
private fun ActionButtonContent(
    isInProgress: Boolean,
    label: String,
    progressDescription: String,
    progressTag: String,
) {
  if (isInProgress) {
    CircularProgressIndicator(
        modifier =
            Modifier.size(Dimens.groupSessionActionProgressSize)
                .semantics { contentDescription = progressDescription }
                .testTag(progressTag),
        color = LocalContentColor.current,
    )
  } else {
    Text(label, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun MetaInfoPill(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: String,
    onClick: (() -> Unit)? = null,
) {
  val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
  Row(
      modifier =
          modifier
              .border(
                  Dimens.borderThin,
                  MaterialTheme.colorScheme.outlineVariant,
                  RoundedCornerShape(Dimens.cornerLarge),
              )
              .clip(RoundedCornerShape(Dimens.cornerLarge))
              .then(clickModifier)
              .padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    icon()
    Spacer(Modifier.width(Spacing.small))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Returns a short label telling how long until a session starts ("Started", "In 5 min", "In 1
 * hour", "In 3 days"). The plural form is picked by the current locale's plural rules, so
 * translations can provide every form their language needs.
 */
@Composable
internal fun sessionStatusLabel(start: Instant, now: Instant): String {
  val duration = Duration.between(now, start)
  return when {
    duration.isNegative || duration.isZero -> stringResource(R.string.group_session_status_started)
    duration.toMinutes() < 60 -> {
      val minutes = duration.toMinutes().toInt()
      pluralStringResource(R.plurals.group_session_status_in_minutes, minutes, minutes)
    }
    duration.toHours() < 24 -> {
      val hours = duration.toHours().toInt()
      pluralStringResource(R.plurals.group_session_status_in_hours, hours, hours)
    }
    else -> {
      val days = duration.toDays().toInt()
      pluralStringResource(R.plurals.group_session_status_in_days, days, days)
    }
  }
}

@Composable
private fun sessionDateLabel(start: Instant, now: Instant): String {
  val zone = ZoneId.systemDefault()
  val localStart = start.atZone(zone).toLocalDate()
  val localNow = now.atZone(zone).toLocalDate()
  return if (localStart == localNow) stringResource(R.string.group_session_date_today)
  else start.atZone(zone).format(DateTimeFormatter.ofPattern("EEE"))
}

private fun sessionTimeLabel(instant: Instant): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(instant)

@Preview(showBackground = true)
@Composable
fun GroupSessionScreenPreview() {
  GroupSessionScreen()
}

@Preview(showBackground = true)
@Composable
fun GroupSessionContentOfflinePreview() {
  val state =
      GroupSessionsUiState(
          now = Instant.now(),
          isLoading = false,
          sessions = emptyList(),
          rooms = emptyList(),
          query = "",
          filter = GroupSessionFilter.ALL,
          selectedSessionTypes = emptySet(),
          startingTimeFilter = GroupSessionTimeFilter.ANYTIME,
          selectedRadiusMeters = null,
          selectedZones = emptySet(),
          availableZones = emptyList(),
          showLabRoomType = false,
          hideFullSessions = false,
          userLocation = null,
          error = null,
          isOffline = true,
      )
  GroupSessionContent(state = state)
}

@Preview(showBackground = true)
@Composable
fun GroupSessionMapContentOfflinePreview() {
  val state =
      GroupSessionsUiState(
          now = Instant.now(),
          isLoading = false,
          sessions = emptyList(),
          rooms = emptyList(),
          query = "",
          filter = GroupSessionFilter.ALL,
          selectedSessionTypes = emptySet(),
          startingTimeFilter = GroupSessionTimeFilter.ANYTIME,
          selectedRadiusMeters = null,
          selectedZones = emptySet(),
          availableZones = emptyList(),
          showLabRoomType = false,
          hideFullSessions = false,
          userLocation = null,
          error = null,
          isOffline = true,
      )
  GroupSessionContent(
      state = state,
      viewMode = GroupSessionViewMode.MAP,
  )
}
