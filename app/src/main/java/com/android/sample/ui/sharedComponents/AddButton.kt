// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude AI Agent
// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.sharedComponents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.android.sample.R
import com.android.sample.ui.dashboard.DashboardContentTestTags
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/** Displays a reusable add-action floating action button. */
@Composable
fun AddButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
  FloatingActionButton(
      onClick = onClick,
      modifier = modifier,
  ) {
    Icon(
        imageVector = Icons.Default.Add,
        contentDescription = contentDescription,
    )
  }
}

/** Displays a speed dial option pill item (icon + text label). */
@Composable
fun SpeedDialFabOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
  Surface(
      onClick = onClick,
      shape = RoundedCornerShape(Dimens.cornerPill),
      color = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      shadowElevation = Dimens.borderMedium,
      modifier = modifier.testTag(testTag),
  ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
            Modifier.padding(
                horizontal = Dimens.pillHorizontalPadding,
                vertical = Dimens.pillVerticalPadding,
            ),
    ) {
      Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(Dimens.avatarSizeSmall),
      )
      Spacer(modifier = Modifier.width(Spacing.small))
      Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/**
 * Displays an expandable add menu offering Event and Group session actions.
 *
 * Used by the Dashboard and Calendar.
 */
@Composable
fun AddFabMenu(
    onEventClick: () -> Unit,
    onGroupSessionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var isExpanded by rememberSaveable { mutableStateOf(false) }

  Column(
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      modifier = modifier,
  ) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
      Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        SpeedDialFabOption(
            icon = Icons.Outlined.Event,
            label = stringResource(R.string.dashboard_fab_event),
            onClick = {
              isExpanded = false
              onEventClick()
            },
            testTag = DashboardContentTestTags.FAB_MENU_EVENT_BUTTON,
        )
        SpeedDialFabOption(
            icon = Icons.Outlined.Groups,
            label = stringResource(R.string.dashboard_fab_group_session),
            onClick = {
              isExpanded = false
              onGroupSessionClick()
            },
            testTag = DashboardContentTestTags.FAB_MENU_GROUP_SESSION_BUTTON,
        )
      }
    }

    FloatingActionButton(
        onClick = { isExpanded = !isExpanded },
        containerColor =
            if (isExpanded) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primaryContainer,
        contentColor =
            if (isExpanded) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onPrimaryContainer,
        shape = if (isExpanded) CircleShape else FloatingActionButtonDefaults.shape,
    ) {
      Icon(
          imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
          contentDescription =
              if (isExpanded) stringResource(R.string.cancel)
              else stringResource(R.string.dashboard_add_description),
      )
    }
  }
}

/** Previews the add-action floating action button. */
@Preview(showBackground = true)
@Composable
fun AddButtonPreview() {
  DidYouStudyTheme {
    AddButton(
        onClick = {},
        contentDescription = stringResource(R.string.dashboard_add_description),
    )
  }
}

/** Previews the expanded add menu. */
@Preview(showBackground = true)
@Composable
fun AddFabMenuPreview() {
  DidYouStudyTheme {
    AddFabMenu(
        onEventClick = {},
        onGroupSessionClick = {},
    )
  }
}
