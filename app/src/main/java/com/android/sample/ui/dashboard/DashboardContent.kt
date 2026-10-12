// Co-authored-by: GitHub Copilot
package com.android.sample.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.sample.R
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.DysOrange
import com.android.sample.ui.theme.SampleAppTheme
import com.android.sample.ui.theme.Spacing

/** One planned study slot shown in "Your Day" (display model, not a domain model). */
data class DashboardSession(val timeRange: String, val subject: String)

object DashboardContentTestTags {
  const val CONTENT = "dashboard_content"
  const val YOUR_DAY_CARD = "dashboard_yourDayCard"
  const val YOUR_DAY_TITLE = "dashboard_yourDayTitle"
  const val EMPTY_DAY = "dashboard_emptyDay"
  const val STATS_CARD = "dashboard_statsCard"
  const val STATS_TITLE = "dashboard_statsTitle"
  const val ADD_BUTTON = "dashboard_addButton"

  fun sessionItem(index: Int) = "dashboard_sessionItem_$index"
}

/** Displays today's planned sessions and a placeholder for future study statistics. */
@Composable
fun DashboardContent(sessions: List<DashboardSession>, modifier: Modifier = Modifier) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(Color.White)
              .verticalScroll(rememberScrollState())
              .padding(
                  start = Spacing.extraLarge,
                  top = Spacing.large,
                  end = Spacing.extraLarge,
                  bottom = Spacing.extraLarge,
              )
              .testTag(DashboardContentTestTags.CONTENT),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = Dimens.dashboardCardMinHeight)
                .border(Dimens.borderThin, Color.Black, RoundedCornerShape(Dimens.cornerMedium))
                .background(Color.White, RoundedCornerShape(Dimens.cornerMedium))
                .padding(Dimens.cardPadding)
                .testTag(DashboardContentTestTags.YOUR_DAY_CARD),
    ) {
      Text(
          text = stringResource(R.string.dashboard_your_day),
          fontSize = 20.sp,
          fontWeight = FontWeight.Medium,
          color = Color.Black,
          modifier = Modifier.testTag(DashboardContentTestTags.YOUR_DAY_TITLE),
      )
      Spacer(modifier = Modifier.height(Dimens.dashboardSectionSpacing))
      if (sessions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(Dimens.dashboardEmptyDayHeight),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              text = stringResource(R.string.dashboard_no_sessions),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              modifier = Modifier.testTag(DashboardContentTestTags.EMPTY_DAY),
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
          sessions.forEachIndexed { index, session ->
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = Dimens.dashboardSessionMinHeight)
                        .border(
                            Dimens.borderThin,
                            DysOrange,
                            RoundedCornerShape(Dimens.cornerSmall),
                        )
                        .padding(
                            horizontal = Dimens.dashboardSessionHorizontalPadding,
                            vertical = Spacing.small,
                        )
                        .testTag(DashboardContentTestTags.sessionItem(index)),
                verticalArrangement = Arrangement.Center,
            ) {
              Text(
                  text = session.timeRange,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Light,
                  color = DysOrange,
              )
              Text(
                  text = session.subject,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.Medium,
                  color = DysOrange,
              )
            }
          }
        }
      }
    }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = Dimens.dashboardCardMinHeight)
                .border(Dimens.borderThin, Color.Black, RoundedCornerShape(Dimens.cornerMedium))
                .background(Color.White, RoundedCornerShape(Dimens.cornerMedium))
                .padding(Dimens.cardPadding)
                .testTag(DashboardContentTestTags.STATS_CARD),
    ) {
      Text(
          text = stringResource(R.string.dashboard_your_stats),
          fontSize = 20.sp,
          fontWeight = FontWeight.Medium,
          color = Color.Black,
          modifier = Modifier.testTag(DashboardContentTestTags.STATS_TITLE),
      )
      Spacer(modifier = Modifier.height(Spacing.medium))
      Text(
          text = stringResource(R.string.dashboard_stats_placeholder),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Shows the outlined dashboard action and invokes the caller's add-session callback. */
@Composable
fun DashboardAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
      onClick = onClick,
      modifier = modifier.size(Dimens.addButtonSize).testTag(DashboardContentTestTags.ADD_BUTTON),
      shape = CircleShape,
      color = Color.White,
      border = BorderStroke(Dimens.borderThick, DysOrange),
      shadowElevation = 0.dp,
  ) {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = stringResource(R.string.dashboard_add_description),
        tint = DysOrange,
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
                DashboardSession("09:00 - 10:30", "Mathematics"),
                DashboardSession("14:00 - 15:30", "Physics"),
            )
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
