// Co-authored-by: Claude AI Agent
package com.android.sample.ui.groupsession

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.model.event.EventType
import com.android.sample.ui.navigation.BottomNavigationMenu
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import com.android.sample.ui.profile.ProfileButton
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSessionScreen(
    // TODO add VM as parameter
    // TODO: add credentialManager as parameter
    // credentialManager: CredentialManager = CredentialManager.create(LocalContext.current),
    // TODO: add onSignedOut as parameter
    // onSignedOut: () -> Unit = {},
    navigationActions: NavigationActions? = null,
    viewModel: GroupSessionsViewModel = groupSessionsViewModel(),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  Scaffold(
      topBar = {
        TopAppBar(
            title = {
              Text("Group sessions", modifier = Modifier.testTag(NavigationTestTags.TOP_BAR_TITLE))
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

/** Renders the list from ViewModel state while keeping the screen easy to preview and test. */
@Composable
private fun GroupSessionContent(
    state: GroupSessionsUiState,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit = {},
    onFilterChange: (GroupSessionFilter) -> Unit = {},
    onJoin: (String) -> Unit = {},
    onLeave: (String) -> Unit = {},
) {
  Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
          IconButton(onClick = {}) {
            Icon(Icons.Outlined.Tune, contentDescription = "Filter sessions")
          }
        },
        placeholder = { Text("Search group sessions") },
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
          selected = state.filter == GroupSessionFilter.ALL,
          onClick = { onFilterChange(GroupSessionFilter.ALL) },
          label = { Text("All sessions") },
          leadingIcon =
              if (state.filter == GroupSessionFilter.ALL) {
                {
                  Icon(
                      Icons.Default.Check,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                  )
                }
              } else null,
          colors =
              FilterChipDefaults.filterChipColors(
                  selectedContainerColor =
                      androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
              ),
      )
      FilterChip(
          selected = state.filter == GroupSessionFilter.JOINED,
          onClick = { onFilterChange(GroupSessionFilter.JOINED) },
          label = { Text("My sessions") },
      )
    }
    Text(
        "Active sessions",
        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 10.dp),
    )
    when {
      state.isLoading -> Text("Loading sessions...")
      state.sessions.isEmpty() -> Text("No active sessions")
      else ->
          LazyColumn(
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxSize(),
          ) {
            items(state.sessions, key = { it.session.id }) { item ->
              GroupSessionCard(item, onJoin, onLeave)
            }
          }
    }
  }
}

/** Displays the session details and the membership action exposed by the ViewModel. */
@Composable
private fun GroupSessionCard(
    item: GroupSessionItem,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
) {
  val session = item.session
  val formatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(18.dp))
              .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow)
              .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
        text = (session.subject ?: "Other").take(1).uppercase(),
        modifier =
            Modifier.size(44.dp)
                .clip(CircleShape)
                .background(eventColor(session.type))
                .padding(top = 10.dp),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(session.subject ?: session.type.displayName(), fontWeight = FontWeight.Bold)
      Text(
          "${formatter.format(session.startsAt)} - ${formatter.format(session.endsAt)}" +
              (session.location?.let { "  •  $it" } ?: ""),
          style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Group, contentDescription = null, modifier = Modifier.size(15.dp))
        Text(
            " ${session.memberIds.size} participant${if (session.memberIds.size == 1) "" else "s"}",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
      }
    }
    if (!item.isCreator) {
      FilterChip(
          selected = item.isMember,
          onClick = { if (item.isMember) onLeave(session.id) else onJoin(session.id) },
          label = { Text(if (item.isMember) "Joined" else "Join") },
      )
    }
  }
}

/** Uses the event colors shown in the supplied design reference. */
private fun eventColor(type: EventType): Color =
    when (type) {
      EventType.STUDY_SESSION -> Color(0xFFC09AF7)
      EventType.COURSE -> Color(0xFF9BE7EF)
      EventType.EXERCISE_SESSION -> Color(0xFF98E27E)
      EventType.LAB -> Color(0xFFF2E36F)
      EventType.PROJECT -> Color(0xFFF2A876)
      EventType.EXAM -> Color(0xFFF57975)
      EventType.OTHER -> Color(0xFFC7C7C7)
    }

/** Provides a readable fallback when an OTHER session has no subject. */
private fun EventType.displayName(): String =
    name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Preview(showBackground = true)
@Composable
fun GroupSessionScreenPreview() {
  GroupSessionScreen()
}
