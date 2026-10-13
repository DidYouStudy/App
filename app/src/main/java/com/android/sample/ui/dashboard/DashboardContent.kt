// Co-authored-by: GitHub Copilot
package com.android.sample.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.SampleAppTheme
import com.android.sample.ui.theme.Spacing

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

  fun sessionItem(index: Int) = "dashboard_sessionItem_$index"
}

/** Displays today's planned sessions and study statistics. */
@Composable
fun DashboardContent(
    sessions: List<DashboardSession>,
    modifier: Modifier = Modifier,
    stats: DashboardStats = DashboardStats(),
) {
  val cardShape = RoundedCornerShape(Dimens.cornerLarge)
  val tileShape = RoundedCornerShape(Dimens.cornerMedium)
  val emptyValue = stringResource(R.string.dashboard_stat_empty_value)

  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.surface)
              .verticalScroll(rememberScrollState())
              .padding(horizontal = Spacing.large, vertical = Spacing.large)
              .padding(bottom = Dimens.fabClearance)
              .testTag(DashboardContentTestTags.CONTENT),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, cardShape)
                .border(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant, cardShape)
                .padding(Spacing.large)
                .testTag(DashboardContentTestTags.YOUR_DAY_CARD),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
          text = stringResource(R.string.dashboard_your_day),
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag(DashboardContentTestTags.YOUR_DAY_TITLE),
      )
      if (sessions.isEmpty()) {
        Text(
            text = stringResource(R.string.dashboard_no_sessions),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag(DashboardContentTestTags.EMPTY_DAY),
        )
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
          sessions.forEachIndexed { index, session ->
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, tileShape)
                        .border(
                            Dimens.borderThin,
                            MaterialTheme.colorScheme.outlineVariant,
                            tileShape,
                        )
                        .padding(Spacing.medium)
                        .testTag(DashboardContentTestTags.sessionItem(index)),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(
                  modifier = Modifier.width(Dimens.sessionTimeColumnWidth),
                  verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall / 2),
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
              Text(
                  text = session.subject,
                  modifier = Modifier.weight(1f),
                  fontSize = 16.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, cardShape)
                .border(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant, cardShape)
                .padding(Spacing.large)
                .testTag(DashboardContentTestTags.STATS_CARD),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Text(
          text = stringResource(R.string.dashboard_your_stats),
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.testTag(DashboardContentTestTags.STATS_TITLE),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        DashboardStatTile(
            label = stringResource(R.string.dashboard_stat_average_daily),
            value = stats.averageDailyStudyTime ?: emptyValue,
            modifier = Modifier.weight(1f),
            testTag = DashboardContentTestTags.STAT_AVERAGE_DAILY,
        )
        DashboardStatTile(
            label = stringResource(R.string.dashboard_stat_total_sessions),
            value = stats.totalSessions ?: emptyValue,
            modifier = Modifier.weight(1f),
            testTag = DashboardContentTestTags.STAT_TOTAL_SESSIONS,
        )
      }
      DashboardStatTile(
          label = stringResource(R.string.dashboard_stat_average_focus),
          value = stats.averageFocusDuration ?: emptyValue,
          modifier = Modifier.fillMaxWidth(),
          testTag = DashboardContentTestTags.STAT_AVERAGE_FOCUS,
      )
      Text(
          text = stringResource(R.string.dashboard_stats_placeholder),
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag(DashboardContentTestTags.STATS_HINT),
      )
    }
  }
}

@Composable
private fun DashboardStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    testTag: String,
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

@Preview(showBackground = true)
@Composable
private fun DashboardContentWithSessionsPreview() {
  SampleAppTheme {
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

@Preview(showBackground = true)
@Composable
private fun DashboardContentWithoutSessionsPreview() {
  SampleAppTheme { DashboardContent(sessions = emptyList()) }
}

@Preview(showBackground = true)
@Composable
private fun DashboardAddButtonPreview() {
  SampleAppTheme { DashboardAddButton(onClick = {}) }
}
