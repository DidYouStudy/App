// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.groupsession

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.sample.R
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/** Holds the filter values displayed by the group-session filter sheet. */
data class GroupSessionFilters(
    val radiusMeters: Int? = null,
    val zones: Set<String> = emptySet(),
    val showLabs: Boolean = false,
)

/** Provides stable semantics tags for group-session filter controls. */
object GroupSessionFilterSheetTestTags {
  fun radiusChip(radiusMeters: Int?): String = "groupSessionRadius_${radiusMeters ?: "any"}"

  fun zoneChip(zone: String?): String = "groupSessionZone_${zone ?: "any"}"

  const val VIEW_MORE = "groupSessionViewMore"

  fun zoneOption(zone: String): String = "groupSessionZoneOption_$zone"

  const val LAB_SWITCH = "groupSessionLabSwitch"
  const val RESET_ALL = "groupSessionResetAll"
  const val SHOW_SESSIONS = "groupSessionShowSessions"
  const val PERMISSION_RATIONALE = "groupSessionPermissionRationale"
}

private data class FilterChoice(val labelRes: Int, val value: String?)

/** Displays filter controls and reports interactions to the owning screen. */
@Composable
fun GroupSessionFilterSheetContent(
    filters: GroupSessionFilters,
    availableZones: List<String>,
    matchingSessionCount: Int,
    hasLocationPermission: Boolean,
    onRadiusSelected: (Int?) -> Unit,
    onZonesSelected: (Set<String>) -> Unit,
    onShowLabsChanged: (Boolean) -> Unit,
    onLocationPermissionNeeded: (Int) -> Unit,
    onResetAll: () -> Unit,
    onShowSessions: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var showMoreZones by remember { mutableStateOf(false) }
  var showPermissionRationale by remember { mutableStateOf(false) }
  val zoneChoices =
      listOf(
          FilterChoice(R.string.group_session_filter_any, null),
          FilterChoice(R.string.group_session_filter_zone_co, "CO"),
          FilterChoice(R.string.group_session_filter_zone_cm, "CM"),
          FilterChoice(R.string.group_session_filter_zone_bc, "BC"),
      )
  val radiusChoices =
      listOf(
          FilterChoice(R.string.group_session_filter_any, null),
          FilterChoice(R.string.group_session_filter_radius_250, "250"),
          FilterChoice(R.string.group_session_filter_radius_500, "500"),
          FilterChoice(R.string.group_session_filter_radius_1000, "1000"),
      )

  Column(
      modifier =
          modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.large),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
          text = stringResource(R.string.group_session_filter_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
      )
      TextButton(
          modifier = Modifier.testTag(GroupSessionFilterSheetTestTags.RESET_ALL),
          onClick = {
            showPermissionRationale = false
            onResetAll()
          },
      ) {
        Text(stringResource(R.string.group_session_filter_reset_all))
      }
    }
    HorizontalDivider()

    FilterSectionLabel(R.string.group_session_filter_distance_title)
    FilterSegmentRow(
        choices = radiusChoices,
        selectedValue = filters.radiusMeters?.toString(),
        testTag = { GroupSessionFilterSheetTestTags.radiusChip(it?.toIntOrNull()) },
        onSelected = { value ->
          val radius = value?.toIntOrNull()
          if (radius != null && !hasLocationPermission) {
            showPermissionRationale = true
            onLocationPermissionNeeded(radius)
          } else {
            showPermissionRationale = false
            onRadiusSelected(radius)
          }
        },
    )
    if (showPermissionRationale) {
      Text(
          text = stringResource(R.string.group_session_filter_permission_rationale),
          modifier = Modifier.testTag(GroupSessionFilterSheetTestTags.PERMISSION_RATIONALE),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
      )
    }

    FilterSectionLabel(R.string.group_session_filter_zone_title)
    FilterSegmentRow(
        choices = zoneChoices,
        selectedValue = null,
        selectedValues = filters.zones,
        testTag = GroupSessionFilterSheetTestTags::zoneChip,
        onSelected = { zone ->
          if (zone == null) {
            onZonesSelected(emptySet())
          } else {
            val newZones = if (zone in filters.zones) filters.zones - zone else filters.zones + zone
            onZonesSelected(newZones)
          }
        },
    )
    Box(modifier = Modifier.fillMaxWidth()) {
      FilledTonalButton(
          modifier = Modifier.fillMaxWidth().testTag(GroupSessionFilterSheetTestTags.VIEW_MORE),
          onClick = { showMoreZones = true },
      ) {
        Text(stringResource(R.string.group_session_filter_view_more))
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
        )
      }
      DropdownMenu(
          expanded = showMoreZones,
          onDismissRequest = { showMoreZones = false },
      ) {
        availableZones.forEach { zone ->
          val isSelected = zone in filters.zones
          DropdownMenuItem(
              modifier =
                  Modifier.testTag(GroupSessionFilterSheetTestTags.zoneOption(zone)).semantics {
                    selected = isSelected
                  },
              text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                ) {
                  Box(
                      modifier =
                          Modifier.size(Dimens.iconSizeMedium)
                              .border(
                                  width = Dimens.borderThin,
                                  color =
                                      if (isSelected) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.outline,
                                  shape = CircleShape,
                              )
                              .padding(Spacing.extraSmall)
                              .clip(CircleShape)
                              .background(
                                  if (isSelected) MaterialTheme.colorScheme.primary
                                  else MaterialTheme.colorScheme.surface,
                              )
                  )
                  Text(zone)
                }
              },
              onClick = {
                onZonesSelected(if (isSelected) filters.zones - zone else filters.zones + zone)
              },
          )
        }
      }
    }

    FilterSectionLabel(R.string.group_session_filter_room_type_title)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(stringResource(R.string.group_session_filter_show_labs))
      Switch(
          modifier = Modifier.testTag(GroupSessionFilterSheetTestTags.LAB_SWITCH),
          checked = filters.showLabs,
          onCheckedChange = onShowLabsChanged,
      )
    }

    Button(
        modifier =
            Modifier.fillMaxWidth()
                .height(Dimens.preferencesWideButtonHeight)
                .testTag(GroupSessionFilterSheetTestTags.SHOW_SESSIONS),
        onClick = onShowSessions,
    ) {
      Text(
          pluralStringResource(
              R.plurals.group_session_filter_show_sessions,
              matchingSessionCount,
              matchingSessionCount,
          )
      )
    }
  }
}

/** Displays a compact uppercase label for one filter section. */
@Composable
private fun FilterSectionLabel(labelRes: Int) {
  Text(
      text = stringResource(labelRes),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** Displays a connected, equally sized row of selected filter choices. */
@Composable
private fun FilterSegmentRow(
    choices: List<FilterChoice>,
    selectedValue: String?,
    testTag: (String?) -> String,
    onSelected: (String?) -> Unit,
    selectedValues: Set<String>? = null,
) {
  Row(modifier = Modifier.fillMaxWidth()) {
    choices.forEachIndexed { index, choice ->
      val isSelected =
          if (selectedValues == null) choice.value == selectedValue
          else if (choice.value == null) selectedValues.isEmpty()
          else choice.value in selectedValues
      val shape =
          when (index) {
            0 ->
                RoundedCornerShape(
                    topStart = Dimens.cornerMedium,
                    bottomStart = Dimens.cornerMedium,
                )
            choices.lastIndex ->
                RoundedCornerShape(topEnd = Dimens.cornerMedium, bottomEnd = Dimens.cornerMedium)
            else -> RoundedCornerShape(0.dp)
          }
      OutlinedButton(
          modifier =
              Modifier.weight(1f)
                  .height(Dimens.calendarRetryButtonHeight)
                  .semantics { selected = isSelected }
                  .testTag(testTag(choice.value)),
          onClick = { onSelected(choice.value) },
          shape = shape,
          border =
              BorderStroke(
                  Dimens.borderThin,
                  if (isSelected) MaterialTheme.colorScheme.secondary
                  else MaterialTheme.colorScheme.outlineVariant,
              ),
          colors =
              ButtonDefaults.outlinedButtonColors(
                  containerColor =
                      if (isSelected) MaterialTheme.colorScheme.secondary
                      else MaterialTheme.colorScheme.secondaryContainer,
                  contentColor =
                      if (isSelected) MaterialTheme.colorScheme.onSecondary
                      else MaterialTheme.colorScheme.onSecondaryContainer,
              ),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.extraSmall),
      ) {
        Text(stringResource(choice.labelRes), maxLines = 1)
      }
    }
  }
}

/** Provides a light-mode preview of the group-session filter sheet. */
@Preview(showBackground = true)
@Composable
private fun GroupSessionFilterSheetLightPreview() {
  DidYouStudyTheme {
    GroupSessionFilterSheetContent(
        filters = GroupSessionFilters(),
        availableZones = listOf("AI", "BC", "CM", "CO", "GC"),
        matchingSessionCount = 4,
        hasLocationPermission = true,
        onRadiusSelected = {},
        onZonesSelected = {},
        onShowLabsChanged = {},
        onLocationPermissionNeeded = {},
        onResetAll = {},
        onShowSessions = {},
    )
  }
}

/** Provides a dark-mode preview of the group-session filter sheet. */
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupSessionFilterSheetDarkPreview() {
  DidYouStudyTheme {
    GroupSessionFilterSheetContent(
        filters = GroupSessionFilters(),
        availableZones = listOf("AI", "BC", "CM", "CO", "GC"),
        matchingSessionCount = 4,
        hasLocationPermission = true,
        onRadiusSelected = {},
        onZonesSelected = {},
        onShowLabsChanged = {},
        onLocationPermissionNeeded = {},
        onResetAll = {},
        onShowSessions = {},
    )
  }
}

/** Returns whether either requested location permission was granted. */
fun isLocationPermissionGranted(results: Map<String, Boolean>): Boolean =
    results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
