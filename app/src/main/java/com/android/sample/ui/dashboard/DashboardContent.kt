// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.android.sample.R
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/**
 * One planned study slot shown in 'Your day' (display model with preformatted times, not a domain
 * model).
 */
data class DashboardSession(
    val startTime: String,
    val endTime: String,
    val subject: String,
)

/** Placeholder-friendly stats; null values are shown as an em dash. */
data class DashboardStats(
    val averageDailyStudyTime: String? = null,
    val totalSessions: String? = null,
    val averageFocusDuration: String? = null,
)

/** Test tags for the dashboard content, shared by the UI and its tests. */
object DashboardContentTestTags {
  const val CONTENT = "dashboard_content"
  const val YOUR_DAY_CARD = "dashboard_yourDayCard"
  const val YOUR_DAY_TITLE = "dashboard_yourDayTitle"
  const val EMPTY_DAY = "dashboard_emptyDay"
  const val STATS_CARD = "dashboard_statsCard"
  const val STATS_TITLE = "dashboard_statsTitle"
  const val ADD_BUTTON = "dashboard_addButton"
  const val STAT_AVERAGE_DAILY = "dashboard_statAverageDaily"
  const val STAT_TOTAL_SESSIONS = "dashboard_statTotalSessions"
  const val STAT_AVERAGE_FOCUS = "dashboard_statAverageFocus"
  const val STATS_HINT = "dashboard_statsHint"

  /**
   * Tag of the session row at [index], one per row, so tests can target and order individual rows.
   */
  fun sessionItem(index: Int) = "dashboard_sessionItem_$index"
}

/** Displays today's planned sessions and study statistics. */
@Composable
fun DashboardContent(
    sessions: List<DashboardSession>,
    modifier: Modifier = Modifier,
    stats: DashboardStats = DashboardStats(),
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surface)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = Spacing.large, vertical = Spacing.large)
              // Keep the last scrollable content clear of the overlaid add button.
              .padding(bottom = Dimens.fabClearance)
              .testTag(DashboardContentTestTags.CONTENT),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    DashboardDayCard(sessions)
    DashboardStatsCard(stats)
  }
}

/** Displays the planned sessions for the day. */
@Composable
private fun DashboardDayCard(sessions: List<DashboardSession>, modifier: Modifier = Modifier) {
  DashboardCard(
      testTag = DashboardContentTestTags.YOUR_DAY_CARD,
      modifier = modifier,
  ) {
    Text(
        text = stringResource(R.string.dashboard_your_day),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(DashboardContentTestTags.YOUR_DAY_TITLE),
    )
    // Empty day: a centred message instead of session rows.
    if (sessions.isEmpty()) {
      Text(
          text = stringResource(R.string.dashboard_no_sessions),
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().testTag(DashboardContentTestTags.EMPTY_DAY),
      )
      Text(
          text = "(Not implemented)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      // One outlined row per session, in the order received.
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        sessions.forEachIndexed { index, session -> DashboardSessionRow(session, index) }
      }
    }
  }
}

/** Applies the shared dashboard card surface and content spacing. */
@Composable
private fun DashboardCard(
    testTag: String,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.medium),
    content: @Composable ColumnScope.() -> Unit,
) {
  val cardShape = RoundedCornerShape(Dimens.cornerLarge)
  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceContainerLowest, cardShape)
              .border(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant, cardShape)
              .padding(Spacing.large)
              .testTag(testTag),
      verticalArrangement = verticalArrangement,
      content = content,
  )
}

/** Shows one planned session with its times and subject. */
@Composable
private fun DashboardSessionRow(
    session: DashboardSession,
    index: Int,
    modifier: Modifier = Modifier,
) {
  val tileShape = RoundedCornerShape(Dimens.cornerMedium)
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceContainer, tileShape)
              .border(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant, tileShape)
              .padding(Spacing.medium)
              .testTag(DashboardContentTestTags.sessionItem(index)),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // Left: fixed-width time column, start time in bold above the end time.
    Column(
        modifier = Modifier.width(Dimens.sessionTimeColumnWidth),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraExtraSmall),
    ) {
      Text(
          text = session.startTime,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
          text = session.endTime,
          fontSize = 14.sp,
          fontWeight = FontWeight.Normal,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    // Right: the subject takes the remaining width and wraps if it is long.
    Text(
        text = session.subject,
        modifier = Modifier.weight(1f),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** Displays the dashboard's study statistics and placeholder hint. */
@Composable
private fun DashboardStatsCard(stats: DashboardStats, modifier: Modifier = Modifier) {
  val emptyValue = stringResource(R.string.dashboard_stat_empty_value)
  DashboardCard(
      testTag = DashboardContentTestTags.STATS_CARD,
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Text(
        text = stringResource(R.string.dashboard_your_stats),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(DashboardContentTestTags.STATS_TITLE),
    )
    // Top: two tiles side by side; intrinsic height keeps them equally tall.
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      DashboardStatTile(
          label = stringResource(R.string.dashboard_stat_average_daily),
          value = stats.averageDailyStudyTime ?: emptyValue,
          testTag = DashboardContentTestTags.STAT_AVERAGE_DAILY,
          modifier = Modifier.weight(1f).fillMaxHeight(),
      )
      DashboardStatTile(
          label = stringResource(R.string.dashboard_stat_total_sessions),
          value = stats.totalSessions ?: emptyValue,
          testTag = DashboardContentTestTags.STAT_TOTAL_SESSIONS,
          modifier = Modifier.weight(1f).fillMaxHeight(),
      )
    }
    // Below: one full-width tile.
    DashboardStatTile(
        label = stringResource(R.string.dashboard_stat_average_focus),
        value = stats.averageFocusDuration ?: emptyValue,
        testTag = DashboardContentTestTags.STAT_AVERAGE_FOCUS,
        modifier = Modifier.fillMaxWidth(),
    )
    // Hint explaining why the values are still empty (no session data yet).
    Text(
        text = stringResource(R.string.dashboard_stats_placeholder),
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(DashboardContentTestTags.STATS_HINT),
    )
    Text(
        text = "(Not implemented)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Displays one dashboard statistic. */
@Composable
private fun DashboardStatTile(
    label: String,
    value: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .background(
                  MaterialTheme.colorScheme.surfaceContainer,
                  RoundedCornerShape(Dimens.cornerMedium),
              )
              .border(
                  Dimens.borderThin,
                  MaterialTheme.colorScheme.outlineVariant,
                  RoundedCornerShape(Dimens.cornerMedium),
              )
              .padding(Spacing.medium)
              .testTag(testTag),
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    // What the statistic is, then its value (or an em dash when missing).
    Text(
        text = label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/** Shows the standard dashboard action and invokes the caller's add-session callback. */
@Composable
fun DashboardAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  FloatingActionButton(
      onClick = onClick,
      modifier = modifier.testTag(DashboardContentTestTags.ADD_BUTTON),
  ) {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = stringResource(R.string.dashboard_add_description),
    )
  }
}

/** Previews the dashboard populated with two planned sessions. */
@Preview(showBackground = true)
@Composable
private fun DashboardContentWithSessionsPreview() {
  DidYouStudyTheme {
    DashboardContent(
        sessions =
            listOf(
                DashboardSession("09:00", "10:30", "Mathematics"),
                DashboardSession("14:00", "15:30", "Physics"),
            ),
        stats = DashboardStats(),
    )
  }
}

/** Previews the dashboard with no planned sessions. */
@Preview(showBackground = true)
@Composable
private fun DashboardContentWithoutSessionsPreview() {
  DidYouStudyTheme { DashboardContent(sessions = emptyList()) }
}

/** Previews the dashboard add-session button. */
@Preview(showBackground = true)
@Composable
private fun DashboardAddButtonPreview() {
  DidYouStudyTheme { DashboardAddButton(onClick = {}) }
}
