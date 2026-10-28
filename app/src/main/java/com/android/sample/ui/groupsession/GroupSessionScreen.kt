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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.model.event.EventType
import com.android.sample.ui.navigation.BottomNavigationMenu
import com.android.sample.ui.navigation.NavigationActions
import com.android.sample.ui.navigation.NavigationTestTags
import com.android.sample.ui.navigation.Screen
import com.android.sample.ui.navigation.Tab
import com.android.sample.ui.profile.ProfileButton
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
  Scaffold(
      modifier = Modifier.testTag(GroupSessionScreenTestTags.SCREEN),
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
        modifier = Modifier.fillMaxWidth().testTag(GroupSessionScreenTestTags.SEARCH),
        singleLine = true,
        shape = RoundedCornerShape(32.dp),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        trailingIcon = {
          IconButton(onClick = {}) {
            Icon(Icons.Outlined.Tune, contentDescription = "Filter sessions")
          }
        },
        placeholder = { Text("Search group sessions") },
        colors =
            androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
            ),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilterChip(
          modifier = Modifier.testTag(GroupSessionScreenTestTags.ALL_SESSIONS_FILTER),
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
          shape = RoundedCornerShape(12.dp),
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
          label = { Text("My sessions") },
          leadingIcon =
              if (state.filter == GroupSessionFilter.JOINED) {
                {
                  Icon(
                      Icons.Default.Check,
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                  )
                }
              } else null,
          shape = RoundedCornerShape(12.dp),
          border =
              FilterChipDefaults.filterChipBorder(
                  enabled = true,
                  selected = state.filter == GroupSessionFilter.JOINED,
              ),
      )
    }
    Text(
        "Active sessions",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier =
            Modifier.padding(vertical = 10.dp).testTag(GroupSessionScreenTestTags.ACTIVE_TITLE),
    )
    when {
      state.isLoading ->
          Text(
              "Loading sessions...",
              modifier = Modifier.testTag(GroupSessionScreenTestTags.LOADING),
          )
      state.sessions.isEmpty() -> Text("No active sessions")
      else ->
          LazyColumn(
              verticalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.fillMaxSize(),
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
  val actionLabel = if (shouldShowHost) "Edit" else if (item.isMember) "Joined" else "Join"

  Card(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(24.dp),
      colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
      // Top Row: Avatar + Title/Host + Status Badge
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.Top,
      ) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(eventColor(session.type)),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = (session.subject ?: "Other").take(1).uppercase(),
              fontWeight = FontWeight.Bold,
              fontSize = 22.sp,
              color = Color(0xFF201F22),
          )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = session.subject ?: session.type.displayName(),
              fontWeight = FontWeight.Bold,
              maxLines = 2, // Allows title to expand vertically before truncating
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(2.dp))
          Text(
              text =
                  if (shouldShowHost) "Your session (Host)" else "Hosted by ${session.createdBy}",
              style = MaterialTheme.typography.bodyMedium,
              color =
                  if (shouldShowHost) Color(0xFF6750A4)
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = if (shouldShowHost) FontWeight.Bold else FontWeight.Normal,
          )
        }

        Spacer(Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF3DCCB),
        ) {
          Text(
              text = statusLabel,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              color = Color(0xFF7C4B2A),
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
          )
        }
      }

      Spacer(Modifier.height(14.dp))

      // Type Badge
      Surface(
          shape = RoundedCornerShape(12.dp),
          color = eventColor(session.type),
      ) {
        Text(
            text = session.type.displayName(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
      }

      Spacer(Modifier.height(10.dp))

      // Description
      Text(
          text = session.description ?: "Group study session",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )

      Spacer(Modifier.height(14.dp))

      // Meta Info Pills: Location on top, Date/Time below (Full Width to prevent text clipping)
      Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        MetaInfoPill(
            modifier = Modifier.fillMaxWidth(),
            icon = {
              Icon(
                  Icons.Outlined.LocationOn,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
              )
            },
            text = session.location ?: "Location TBD",
        )
        MetaInfoPill(
            modifier = Modifier.fillMaxWidth(),
            icon = {
              Icon(
                  Icons.Default.AccessTime,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
              )
            },
            text = "$dateLabel · $startLabel – $endLabel",
        )
      }

      Spacer(Modifier.height(16.dp))

      // Thin Divider Line
      HorizontalDivider(color = Color(0xFFE7E0EC), thickness = 1.dp)

      Spacer(Modifier.height(14.dp))

      // Bottom Row: Compact Participants Pill + Action Buttons
      Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // Shortened Participants Pill (Avatars + Count + Chevron only)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF3EDF7),
            modifier = Modifier.height(40.dp),
        ) {
          Row(
              modifier = Modifier.padding(horizontal = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFD0BCFF)),
                contentAlignment = Alignment.Center,
            ) {
              Text("M", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF21005D))
            }
            Box(
                modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(0xFFCCC2DC)),
                contentAlignment = Alignment.Center,
            ) {
              Text("A", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF1D1B20))
            }
            val extraCount = session.memberIds.size - 2
            if (extraCount > 0) {
              Surface(
                  shape = CircleShape,
                  color = Color(0xFFE8DEF8),
                  modifier = Modifier.size(20.dp),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text("+$extraCount", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF49454F),
            )
          }
        }

        // Action Buttons (Host Edit vs Member Leave/Joined vs Join)
        if (shouldShowHost) {
          Button(
              onClick = {},
              shape = RoundedCornerShape(20.dp),
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = Color(0xFFE5E1E7),
                      contentColor = Color(0xFF2B2B2B),
                  ),
              border = BorderStroke(1.dp, Color(0xFF79747E)),
              modifier = Modifier.height(40.dp),
          ) {
            Text(actionLabel, fontWeight = FontWeight.Medium)
          }
        } else if (item.isMember) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onLeave(session.id) },
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFB3261E)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E)),
                modifier = Modifier.height(40.dp),
            ) {
              Text("Leave", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = {},
                enabled = false,
                shape = RoundedCornerShape(20.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        disabledContainerColor = Color(0xFF1D1B20).copy(alpha = 0.1f),
                        disabledContentColor = Color(0xFF1D1B20).copy(alpha = 0.45f),
                    ),
                modifier = Modifier.height(40.dp),
            ) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(4.dp))
              Text("Joined", fontWeight = FontWeight.Medium)
            }
          }
        } else {
          Button(
              onClick = { onJoin(session.id) },
              shape = RoundedCornerShape(20.dp),
              colors =
                  ButtonDefaults.buttonColors(
                      containerColor = Color(0xFF6750A4),
                      contentColor = Color.White,
                  ),
              modifier = Modifier.height(40.dp),
          ) {
            Text("Join", fontWeight = FontWeight.Medium)
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
              .border(1.dp, Color(0xFFD7D6D9), RoundedCornerShape(16.dp))
              .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    icon()
    Spacer(Modifier.width(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun sessionStatusLabel(start: Instant, now: Instant): String {
  val duration = Duration.between(now, start)
  return when {
    duration.isNegative || duration.isZero -> "Started"
    duration.toMinutes() < 60 -> "In ${duration.toMinutes()} min"
    duration.toHours() < 24 ->
        "In ${duration.toHours()} hour${if (duration.toHours() == 1L) "" else "s"}"
    else -> "In ${duration.toDays()} day${if (duration.toDays() == 1L) "" else "s"}"
  }
}

private fun sessionDateLabel(start: Instant, now: Instant): String {
  val zone = ZoneId.systemDefault()
  val localStart = start.atZone(zone).toLocalDate()
  val localNow = now.atZone(zone).toLocalDate()
  return if (localStart == localNow) "Today"
  else start.atZone(zone).format(DateTimeFormatter.ofPattern("EEE"))
}

private fun sessionTimeLabel(instant: Instant): String =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault()).format(instant)

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
