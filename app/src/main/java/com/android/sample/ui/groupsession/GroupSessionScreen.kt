// Co-authored-by: Claude AI Agent
// Co-authored-by: Copilot
// Co-authored-by: Gemini
package com.android.sample.ui.groupsession

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.ui.navigation.BottomNavigationMenu
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import com.android.sample.ui.profile.ProfileButton
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.color
import com.android.sample.ui.theme.onColor
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object GroupSessionScreenTestTags {
  const val SCREEN = "groupSessionScreen"
  const val SEARCH = "groupSessionSearch"
  const val ALL_SESSIONS_FILTER = "groupSessionAllFilter"
  const val MY_SESSIONS_FILTER = "groupSessionMyFilter"
  const val ACTIVE_TITLE = "groupSessionActiveTitle"
  const val LOADING = "groupSessionLoading"
  const val ERROR_SNACKBAR = "groupSessionErrorSnackbar"
  const val ERROR_DISMISS = "groupSessionErrorDismiss"
  const val SESSION_LIST = "groupSessionList"
  const val ADD_BUTTON = "groupSessionAddButton"
  const val SESSION_ITEM_PREFIX = "groupSessionItem_"

  fun sessionItem(index: Int) = "$SESSION_ITEM_PREFIX$index"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSessionScreen(
    navigationActions: NavigationActions? = null,
    viewModel: GroupSessionsViewModel = groupSessionsViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val loadErrorMessage = stringResource(R.string.group_session_error_load)
  val joinErrorMessage = stringResource(R.string.group_session_error_join)
  val leaveErrorMessage = stringResource(R.string.group_session_error_leave)
  LaunchedEffect(uiState.error) {
    uiState.error?.let { error ->
      val msg =
          when (error) {
            GroupSessionsError.LOAD_FAILED -> loadErrorMessage
            GroupSessionsError.JOIN_FAILED -> joinErrorMessage
            GroupSessionsError.LEAVE_FAILED -> leaveErrorMessage
          }
      snackbarHostState.showSnackbar(msg)
      viewModel.clearError()
    }
  }
  // TODO: modularize add button with other screens and refactor affected tests
  // TODO: Have the ViewModel expose a retry/loading state after join or leave failures.
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
        TopAppBar(
            title = {
              Text(
                  stringResource(R.string.group_session_title),
                  modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE),
              )
            },
            actions = {
              ProfileButton(onClick = { navigationActions?.navigateTo(Screen.Settings) })
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
      floatingActionButton = { GroupSessionAddButton(onClick = { /* TODO ADD GROUPSESSION */ }) },
      content = { pd ->
        GroupSessionContent(
            state = uiState,
            modifier = Modifier.padding(pd),
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onJoin = viewModel::join,
            onLeave = viewModel::leave,
        )
      },
  )
}

@Composable
private fun GroupSessionAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  FloatingActionButton(
      onClick = onClick,
      modifier = modifier.testTag(GroupSessionScreenTestTags.ADD_BUTTON),
  ) {
    Icon(
        Icons.Default.Add,
        contentDescription = stringResource(R.string.group_session_add_description),
    )
  }
}

@Composable
private fun GroupSessionContent(
    state: GroupSessionsUiState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onFilterChange: (GroupSessionFilter) -> Unit = {},
    onJoin: (String) -> Unit = {},
    onLeave: (String) -> Unit = {},
) {
  Column(modifier = modifier.fillMaxSize().padding(horizontal = Spacing.large)) {
    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().testTag(GroupSessionScreenTestTags.SEARCH),
        singleLine = true,
        shape = RoundedCornerShape(Dimens.cornerFull),
        leadingIcon = {
          Icon(
              Icons.Default.Search,
              contentDescription = stringResource(R.string.group_session_search_icon_description),
          )
        },
        trailingIcon = {
          IconButton(onClick = { /* TODO ADVANCED FILTERS */ }) {
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
    Spacer(Modifier.height(Spacing.small))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
      FilterChip(
          modifier = Modifier.testTag(GroupSessionScreenTestTags.ALL_SESSIONS_FILTER),
          selected = state.filter == GroupSessionFilter.ALL,
          onClick = { onFilterChange(GroupSessionFilter.ALL) },
          label = { Text(stringResource(R.string.group_session_filter_all)) },
          leadingIcon =
              if (state.filter == GroupSessionFilter.ALL) {
                {
                  Icon(
                      Icons.Default.Check,
                      contentDescription = null,
                      modifier = Modifier.size(Dimens.iconSizeSmall),
                  )
                }
              } else null,
          shape = RoundedCornerShape(Dimens.cornerMedium),
          border =
              FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = state.filter == GroupSessionFilter.ALL,
              ),
      )
      FilterChip(
          modifier = Modifier.testTag(GroupSessionScreenTestTags.MY_SESSIONS_FILTER),
          selected = state.filter == GroupSessionFilter.JOINED,
          onClick = { onFilterChange(GroupSessionFilter.JOINED) },
          label = { Text(stringResource(R.string.group_session_filter_my)) },
          leadingIcon =
              if (state.filter == GroupSessionFilter.JOINED) {
                {
                  Icon(
                      Icons.Default.Check,
                      contentDescription = null,
                      modifier = Modifier.size(Dimens.iconSizeSmall),
                  )
                }
              } else null,
          shape = RoundedCornerShape(Dimens.cornerMedium),
          border =
              FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = state.filter == GroupSessionFilter.JOINED,
              ),
      )
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
              contentPadding = PaddingValues(bottom = Dimens.fabClearance),
              modifier = Modifier.fillMaxSize().testTag(GroupSessionScreenTestTags.SESSION_LIST),
          ) {
            items(state.sessions.indices.toList(), key = { it }) { index ->
              GroupSessionCard(
                  item = state.sessions[index],
                  onJoin = onJoin,
                  onLeave = onLeave,
                  modifier = Modifier.testTag(GroupSessionScreenTestTags.sessionItem(index)),
              )
            }
          }
    }
  }
}

@Composable
private fun GroupSessionCard(
    item: GroupSessionItem,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
  val session = item.session
  val now = Instant.now()
  val statusLabel = sessionStatusLabel(session.startsAt, now)
  val startLabel = sessionTimeLabel(session.startsAt)
  val endLabel = sessionTimeLabel(session.endsAt)
  val dateLabel = sessionDateLabel(session.startsAt, now)
  val shouldShowHost = item.isCreator

  val subjectFallback = stringResource(R.string.group_session_subject_fallback)
  val actionEditLabel = stringResource(R.string.group_session_action_edit)
  val actionJoinedLabel = stringResource(R.string.group_session_action_joined)
  val actionJoinLabel = stringResource(R.string.group_session_action_join)
  val actionLeaveLabel = stringResource(R.string.group_session_action_leave)
  val actionLabel =
      if (shouldShowHost) actionEditLabel
      else if (item.isMember) actionJoinedLabel else actionJoinLabel

  Card(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(Dimens.cornerExtraLarge),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
                    .background(session.type.color),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = (session.subject ?: subjectFallback).take(1).uppercase(),
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.titleLarge,
              color = session.type.onColor,
          )
        }

        Spacer(Modifier.width(Spacing.mediumLarge))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = session.subject ?: session.type.displayName(),
              fontWeight = FontWeight.Bold,
              maxLines = 2, // Allows title to expand vertically before truncating
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(Spacing.extraExtraSmall))
          Text(
              text =
                  if (shouldShowHost) stringResource(R.string.group_session_host_self)
                  else stringResource(R.string.group_session_hosted_by, session.createdBy),
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
      Surface(
          shape = RoundedCornerShape(Dimens.cornerMedium),
          color = session.type.color,
      ) {
        Text(
            text = session.type.displayName(),
            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall),
            color = session.type.onColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
        )
      }

      Spacer(Modifier.height(Spacing.mediumSmall))

      // Description
      Text(
          text = session.description ?: stringResource(R.string.group_session_description_fallback),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(Modifier.height(Spacing.mediumLarge))

      // Meta Info Pills: Location on top, Date/Time below (Full Width to prevent text clipping)
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(Spacing.small),
      ) {
        MetaInfoPill(
            modifier = Modifier.fillMaxWidth(),
            icon = {
              Icon(
                  Icons.Outlined.LocationOn,
                  contentDescription = null,
                  modifier = Modifier.size(Dimens.iconSizeSmall),
              )
            },
            text = session.location ?: stringResource(R.string.group_session_location_fallback),
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
            text = "$dateLabel · $startLabel – $endLabel",
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
        // Shortened Participants Pill (Avatars + Count + Chevron only)
        // TODO: make participants pill dynamically show first member pictures
        // TODO: make pill clickable to show all group members
        Surface(
            shape = RoundedCornerShape(Dimens.cornerLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(Dimens.actionButtonHeight),
        ) {
          Row(
              modifier = Modifier.padding(horizontal = Spacing.mediumSmall),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
          ) {
            Box(
                modifier =
                    Modifier.size(Dimens.avatarSizeSmall)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  "M",
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Box(
                modifier =
                    Modifier.size(Dimens.avatarSizeSmall)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
              Text(
                  "A",
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            val extraCount = session.memberIds.size - 2
            if (extraCount > 0) {
              Surface(
                  shape = CircleShape,
                  color = MaterialTheme.colorScheme.tertiaryContainer,
                  modifier = Modifier.size(Dimens.avatarSizeSmall),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                      "+$extraCount",
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
                }
              }
            }
            Spacer(Modifier.width(Spacing.extraExtraSmall))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(Dimens.iconSizeSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        // Action Buttons (Host Edit vs Member Leave/Joined vs Join)
        if (shouldShowHost) {
          Button(
              onClick = { /* TODO EDIT GROUP SESSION */ },
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
            Text(actionLabel, fontWeight = FontWeight.Medium)
          }
        } else if (item.isMember) {
          Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
            OutlinedButton(
                onClick = { onLeave(session.id) },
                shape = RoundedCornerShape(Dimens.cornerLarge),
                border = BorderStroke(Dimens.borderThin, MaterialTheme.colorScheme.error),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                contentPadding = PaddingValues(horizontal = Spacing.extraSmall),
                modifier =
                    Modifier.height(Dimens.actionButtonHeight).width(Dimens.actionButtonWidth),
            ) {
              Text(actionLeaveLabel, fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = {},
                enabled = false,
                shape = RoundedCornerShape(Dimens.cornerLarge),
                contentPadding = PaddingValues(horizontal = Spacing.extraSmall),
                modifier =
                    Modifier.height(Dimens.actionButtonHeight).width(Dimens.actionButtonWidth),
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
              onClick = { onJoin(session.id) },
              shape = RoundedCornerShape(Dimens.cornerLarge),
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = MaterialTheme.colorScheme.primary,
                      contentColor = MaterialTheme.colorScheme.onPrimary,
                  ),
              contentPadding = PaddingValues(horizontal = Spacing.extraSmall),
              modifier = Modifier.height(Dimens.actionButtonHeight).width(Dimens.actionButtonWidth),
          ) {
            Text(actionJoinLabel, fontWeight = FontWeight.Medium)
          }
        }
      }
    }
  }
}

@Composable
private fun MetaInfoPill(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: String,
) {
  Row(
      modifier =
          modifier
              .border(
                  Dimens.borderThin,
                  MaterialTheme.colorScheme.outlineVariant,
                  RoundedCornerShape(Dimens.cornerLarge),
              )
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

@Composable
private fun sessionStatusLabel(start: Instant, now: Instant): String {
  val duration = Duration.between(now, start)
  return when {
    duration.isNegative || duration.isZero -> stringResource(R.string.group_session_status_started)
    duration.toMinutes() < 60 ->
        stringResource(R.string.group_session_status_in_minutes, duration.toMinutes())
    duration.toHours() < 24 -> {
      val hours = duration.toHours()
      if (hours == 1L) stringResource(R.string.group_session_status_in_hours_one, hours)
      else stringResource(R.string.group_session_status_in_hours_other, hours)
    }
    else -> {
      val days = duration.toDays()
      if (days == 1L) stringResource(R.string.group_session_status_in_days_one, days)
      else stringResource(R.string.group_session_status_in_days_other, days)
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

private fun EventType.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Preview(showBackground = true)
@Composable
fun GroupSessionScreenPreview() {
  GroupSessionScreen()
}
