// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.ui.theme.CardTitleTextStyle
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing
import com.android.sample.ui.theme.color
import com.android.sample.ui.theme.label
import com.android.sample.ui.theme.onColor

/**
 * One planned study slot shown in 'Your day' (display model with preformatted times, not a domain
 * model).
 */
data class DashboardSession(
    val startTime: String,
    val endTime: String,
    val subject: String? = null,
    val eventType: EventType = EventType.STUDY_SESSION,
    val location: String? = null,
    val id: String = "",
) {
  /** Formatted time range displayed as "start_time - end_time". */
  val timeRange: String
    get() = "$startTime - $endTime"
}

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
  const val FAB_MENU_EVENT_BUTTON = "dashboard_fabMenuEventButton"
  const val FAB_MENU_GROUP_SESSION_BUTTON = "dashboard_fabMenuGroupSessionButton"
  const val STAT_AVERAGE_DAILY = "dashboard_statAverageDaily"
  const val STAT_TOTAL_SESSIONS = "dashboard_statTotalSessions"
  const val STAT_AVERAGE_FOCUS = "dashboard_statAverageFocus"
  const val STATS_HINT = "dashboard_statsHint"
  const val LOADING_INDICATOR = "dashboard_loadingIndicator"
  const val ERROR_CONTAINER = "dashboard_errorContainer"
  const val ERROR_TEXT = "dashboard_errorText"
  const val RETRY_BUTTON = "dashboard_retryButton"

  /**
   * Tag of the session row at [index], one per row, so tests can target and order individual rows.
   */
  fun sessionItem(index: Int) = "dashboard_sessionItem_$index"

  /** Tag for the time range text within the session row at [index]. */
  fun sessionTime(index: Int) = "dashboard_sessionTime_$index"

  /** Tag for the optional location text within the session row at [index]. */
  fun sessionLocation(index: Int) = "dashboard_sessionLocation_$index"

  /** Tag for the subject text within the session row at [index]. */
  fun sessionSubject(index: Int) = "dashboard_sessionSubject_$index"

  /** Tag for the event type label within the session row at [index]. */
  fun sessionType(index: Int) = "dashboard_sessionType_$index"
}

/**
 * Displays today's planned sessions and study statistics.
 *
 * @param sessions Sessions planned for today, in display order.
 * @param modifier Modifier applied to the scrollable content column.
 * @param stats Study statistics shown in the stats card.
 * @param onEventCardClick Called when the user taps an event card.
 * @param onStatsCardClick Called when the user taps the stats card.
 */
@Composable
fun DashboardContent(
    sessions: List<DashboardSession>,
    modifier: Modifier = Modifier,
    stats: DashboardStats = DashboardStats(),
    onEventCardClick: (String) -> Unit = {},
    onStatsCardClick: () -> Unit = {},
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = Spacing.large, vertical = Spacing.large)
              // Keep the last scrollable content clear of the overlaid add button.
              .padding(bottom = Dimens.fabClearance)
              .testTag(DashboardContentTestTags.CONTENT),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    DashboardDayCard(sessions, onEventCardClick = onEventCardClick)
    DashboardStatsCard(stats, onClick = onStatsCardClick)
  }
}

/** Displays the planned sessions for the day. */
@Composable
private fun DashboardDayCard(
    sessions: List<DashboardSession>,
    modifier: Modifier = Modifier,
    onEventCardClick: (String) -> Unit = {},
) {
  DashboardCard(
      testTag = DashboardContentTestTags.YOUR_DAY_CARD,
      clickLabel = stringResource(R.string.dashboard_your_day),
      onClick = null,
      modifier = modifier,
  ) {
    Text(
        text = stringResource(R.string.dashboard_your_day),
        style = CardTitleTextStyle,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(DashboardContentTestTags.YOUR_DAY_TITLE),
    )
    // Empty day: a centred message instead of session rows.
    if (sessions.isEmpty()) {
      Text(
          text = stringResource(R.string.dashboard_no_sessions),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().testTag(DashboardContentTestTags.EMPTY_DAY),
      )
    } else {
      // One outlined row per session, in the order received.
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        sessions.forEachIndexed { index, session ->
          DashboardSessionRow(
              session = session,
              index = index,
              onClick = { onEventCardClick(session.id) },
          )
        }
      }
    }
  }
}

/**
 * Applies the shared dashboard card surface and content spacing, and makes the whole card tappable
 * when [onClick] is provided.
 *
 * The tap target is a separate layer drawn over the content rather than a `clickable` on the
 * column: `clickable` merges every descendant into one accessibility node, which would make
 * TalkBack read the whole card (every session row) as a single button and hide the inner nodes.
 *
 * @param testTag Tag applied to the card container.
 * @param clickLabel Accessibility label announced for the card's tap target.
 * @param onClick Called when the user taps anywhere on the card, or null if individual elements
 *   handle their own clicks.
 */
@Composable
private fun DashboardCard(
    testTag: String,
    clickLabel: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.medium),
    content: @Composable ColumnScope.() -> Unit,
) {
  val cardShape = RoundedCornerShape(Dimens.cornerLarge)
  Box(modifier = modifier.fillMaxWidth().testTag(testTag)) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, cardShape)
                .border(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant, cardShape)
                .padding(Spacing.large),
        verticalArrangement = verticalArrangement,
        content = content,
    )
    if (onClick != null) {
      // Tap layer over the content; clipped so the ripple follows the rounded corners.
      Box(
          modifier =
              Modifier.matchParentSize().clip(cardShape).clickable(onClick = onClick).semantics {
                contentDescription = clickLabel
              },
      )
    }
  }
}

/** Shows one planned session with its time range, location, subject, and event type. */
@Composable
private fun DashboardSessionRow(
    session: DashboardSession,
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
  val tileShape = RoundedCornerShape(Dimens.cornerMedium)
  val backgroundColor = session.eventType.color
  val contentColor = session.eventType.onColor
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(tileShape)
              .clickable(onClick = onClick)
              .background(backgroundColor, tileShape)
              .padding(Spacing.medium)
              .testTag(DashboardContentTestTags.sessionItem(index)),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    // Left: time range displayed as start_time - end_time, and location with pin icon under the
    // times.
    Column(
        modifier = Modifier.width(Dimens.sessionTimeRangeColumnWidth),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraExtraSmall),
    ) {
      Text(
          text = session.timeRange,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Medium,
          color = contentColor,
          modifier = Modifier.testTag(DashboardContentTestTags.sessionTime(index)),
      )
      if (!session.location.isNullOrBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.extraExtraSmall),
        ) {
          Icon(
              imageVector = Icons.Outlined.LocationOn,
              contentDescription = null,
              tint = contentColor,
              modifier = Modifier.size(Dimens.iconSizeSmall),
          )
          Text(
              text = session.location,
              style = MaterialTheme.typography.bodySmall,
              color = contentColor,
              modifier = Modifier.testTag(DashboardContentTestTags.sessionLocation(index)),
          )
        }
      }
    }
    // Right: subject in bold and event type label, left-aligned in the remaining row space.
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Spacing.extraExtraSmall),
    ) {
      Text(
          text = session.subject ?: stringResource(R.string.event_type_other),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = contentColor,
          textAlign = TextAlign.Start,
          modifier = Modifier.testTag(DashboardContentTestTags.sessionSubject(index)),
      )
      Text(
          text = session.eventType.label(),
          style = MaterialTheme.typography.labelSmall,
          color = contentColor,
          textAlign = TextAlign.Start,
          modifier = Modifier.testTag(DashboardContentTestTags.sessionType(index)),
      )
    }
  }
}

/** Displays the dashboard's study statistics and placeholder hint. */
@Composable
private fun DashboardStatsCard(
    stats: DashboardStats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val emptyValue = stringResource(R.string.dashboard_stat_empty_value)
  DashboardCard(
      testTag = DashboardContentTestTags.STATS_CARD,
      clickLabel = stringResource(R.string.dashboard_your_stats),
      onClick = onClick,
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Text(
        text = stringResource(R.string.dashboard_your_stats),
        style = CardTitleTextStyle,
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
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(DashboardContentTestTags.STATS_HINT),
    )
    Text(
        text = stringResource(R.string.not_implemented),
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
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
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
