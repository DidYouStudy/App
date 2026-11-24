// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.groupsession

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.android.sample.R
import com.android.sample.model.event.EventType
import com.android.sample.ui.sharedComponents.SegmentedChoiceRow
import com.android.sample.ui.theme.DidYouStudyTheme
import com.android.sample.ui.theme.Dimens
import com.android.sample.ui.theme.Spacing

/** Holds the filter values displayed by the group-session filter sheet. */
data class GroupSessionFilters(
    val sessionTypes: Set<EventType> = emptySet(),
    val startingTime: GroupSessionTimeFilter = GroupSessionTimeFilter.ANYTIME,
    val hideFullSessions: Boolean = false,
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

  fun sessionTypeChip(type: EventType): String = "groupSessionType_${type.name}"

  fun startingTimeChip(filter: GroupSessionTimeFilter): String =
      "groupSessionStartingTime_${filter.name}"

  const val HIDE_FULL_SWITCH = "groupSessionHideFullSwitch"
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
    onSessionTypesSelected: (Set<EventType>) -> Unit,
    onStartingTimeSelected: (GroupSessionTimeFilter) -> Unit,
    onHideFullSessionsChange: (Boolean) -> Unit,
    onLocationPermissionNeeded: (Int) -> Unit,
    onResetAll: () -> Unit,
    onShowSessions: () -> Unit,
    modifier: Modifier = Modifier,
) {
  var showMoreZones by remember { mutableStateOf(false) }
  var showPermissionRationale by remember { mutableStateOf(false) }
  var viewMoreButtonWidthPx by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current
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
  val sessionTypeChoices =
      listOf(
          EventType.STUDY_SESSION to R.string.group_session_filter_type_study,
          EventType.COURSE to R.string.group_session_filter_type_course,
          EventType.EXERCISE_SESSION to R.string.group_session_filter_type_exercise,
          EventType.LAB to R.string.group_session_filter_type_lab,
          EventType.PROJECT to R.string.group_session_filter_type_project,
          EventType.EXAM to R.string.group_session_filter_type_exam,
          EventType.OTHER to R.string.group_session_filter_type_other,
      )
  val startingTimeChoices =
      listOf(
          GroupSessionTimeFilter.ANYTIME to R.string.group_session_filter_time_anytime,
          GroupSessionTimeFilter.WITHIN_ONE_HOUR to
              R.string.group_session_filter_time_within_one_hour,
          GroupSessionTimeFilter.TODAY to R.string.group_session_filter_time_today,
          GroupSessionTimeFilter.THIS_WEEK to R.string.group_session_filter_time_this_week,
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

    FilterSectionLabel(R.string.group_session_filter_session_type_title)
    SegmentedChoiceRow(
        items = sessionTypeChoices,
        isSelected = { (type, _) -> type in filters.sessionTypes },
        onClick = { (type, _) ->
          onSessionTypesSelected(
              if (type in filters.sessionTypes) filters.sessionTypes - type
              else filters.sessionTypes + type
          )
        },
        label = { (_, labelRes) -> stringResource(labelRes) },
        testTag = { (type, _) -> GroupSessionFilterSheetTestTags.sessionTypeChip(type) },
        role = Role.Checkbox,
        itemWidth = Dimens.groupSessionFilterTypeChipWidth,
    )

    FilterSectionLabel(R.string.group_session_filter_starting_time_title)
    SegmentedChoiceRow(
        items = startingTimeChoices,
        isSelected = { (filter, _) -> filter == filters.startingTime },
        onClick = { (filter, _) -> onStartingTimeSelected(filter) },
        label = { (_, labelRes) -> stringResource(labelRes) },
        testTag = { (filter, _) -> GroupSessionFilterSheetTestTags.startingTimeChip(filter) },
        role = Role.RadioButton,
    )

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
    if (showPermissionRationale && !hasLocationPermission) {
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
          modifier =
              Modifier.fillMaxWidth()
                  .onSizeChanged { viewMoreButtonWidthPx = it.width }
                  .testTag(GroupSessionFilterSheetTestTags.VIEW_MORE),
          onClick = { showMoreZones = true },
      ) {
        Text(stringResource(R.string.group_session_filter_view_more))
        androidx.compose.material3.Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
        )
      }
      if (showMoreZones) {
        val menuHeightPx =
            with(density) {
              val rowHeightPx = Dimens.groupSessionZoneMenuItemHeight.roundToPx()
              val dividerHeightPx = Dimens.borderThin.roundToPx()
              val contentHeightPx =
                  rowHeightPx * availableZones.size +
                      dividerHeightPx * (availableZones.size - 1).coerceAtLeast(0)
              minOf(contentHeightPx, Dimens.groupSessionZoneMenuMaxHeight.roundToPx())
            }
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(x = 0, y = -menuHeightPx),
            onDismissRequest = { showMoreZones = false },
            properties = PopupProperties(focusable = true),
        ) {
          Surface(
              modifier =
                  Modifier.width(with(density) { viewMoreButtonWidthPx.toDp() })
                      .heightIn(max = Dimens.groupSessionZoneMenuMaxHeight),
              shape = MaterialTheme.shapes.large,
              color = MaterialTheme.colorScheme.surfaceContainerLow,
              shadowElevation = Dimens.groupSessionZoneMenuShadowElevation,
          ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
              availableZones.forEachIndexed { index, zone ->
                val isSelected = zone in filters.zones
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(Dimens.groupSessionZoneMenuItemHeight)
                            .semantics { selected = isSelected }
                            .testTag(GroupSessionFilterSheetTestTags.zoneOption(zone))
                            .clickable {
                              onZonesSelected(
                                  if (isSelected) filters.zones - zone else filters.zones + zone
                              )
                            }
                            .padding(horizontal = Spacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(zone, style = MaterialTheme.typography.bodyLarge)
                  RadioButton(selected = isSelected, onClick = null)
                }
                if (index < availableZones.lastIndex) {
                  HorizontalDivider(
                      thickness = Dimens.borderThin,
                      color = MaterialTheme.colorScheme.outlineVariant,
                  )
                }
              }
            }
          }
        }
      }
    }

    FilterSectionLabel(R.string.group_session_filter_room_type_title)
    FilterSwitchRow(
        checked = filters.showLabs,
        onCheckedChange = onShowLabsChanged,
        labelRes = R.string.group_session_filter_show_labs,
        testTag = GroupSessionFilterSheetTestTags.LAB_SWITCH,
    )

    FilterSectionLabel(R.string.group_session_filter_availability_title)
    FilterSwitchRow(
        checked = filters.hideFullSessions,
        onCheckedChange = onHideFullSessionsChange,
        labelRes = R.string.group_session_filter_hide_full_sessions,
        testTag = GroupSessionFilterSheetTestTags.HIDE_FULL_SWITCH,
        subtitleRes = R.string.group_session_filter_hide_full_sessions_subtitle,
    )

    Button(
        modifier =
            Modifier.fillMaxWidth()
                .height(Dimens.filterActionButtonHeight)
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

/**
 * Displays one on/off filter as a label (with an optional subtitle) and a trailing switch.
 *
 * The whole row is a single toggleable node with [Role.Switch], so TalkBack reads the label and the
 * state together and tapping the label toggles the switch too.
 */
@Composable
private fun FilterSwitchRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    labelRes: Int,
    testTag: String,
    subtitleRes: Int? = null,
) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
              .testTag(testTag),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column {
      Text(stringResource(labelRes))
      if (subtitleRes != null) {
        Text(
            text = stringResource(subtitleRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    // The row handles the toggle, so the switch itself only shows the state.
    Switch(checked = checked, onCheckedChange = null)
  }
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
  val isMultiSelect = selectedValues != null
  val role = if (isMultiSelect) Role.Checkbox else Role.RadioButton
  SegmentedChoiceRow(
      items = choices,
      isSelected = { choice ->
        if (selectedValues == null) choice.value == selectedValue
        else if (choice.value == null) selectedValues.isEmpty() else choice.value in selectedValues
      },
      onClick = { choice -> onSelected(choice.value) },
      label = { choice -> stringResource(choice.labelRes) },
      testTag = { choice -> testTag(choice.value) },
      role = role,
  )
}

/** Provides a light-mode preview of the group-session filter sheet. */
@Preview(showBackground = true)
@Composable
internal fun GroupSessionFilterSheetLightPreview() {
  DidYouStudyTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
      GroupSessionFilterSheetContent(
          filters =
              GroupSessionFilters(
                  sessionTypes = setOf(EventType.STUDY_SESSION, EventType.COURSE),
                  hideFullSessions = true,
              ),
          availableZones = listOf("AI", "BC", "CM", "CO", "GC"),
          matchingSessionCount = 4,
          hasLocationPermission = true,
          onRadiusSelected = {},
          onZonesSelected = {},
          onShowLabsChanged = {},
          onSessionTypesSelected = {},
          onStartingTimeSelected = {},
          onHideFullSessionsChange = {},
          onLocationPermissionNeeded = {},
          onResetAll = {},
          onShowSessions = {},
      )
    }
  }
}

/** Provides a dark-mode preview of the group-session filter sheet. */
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun GroupSessionFilterSheetDarkPreview() {
  DidYouStudyTheme {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
      GroupSessionFilterSheetContent(
          filters =
              GroupSessionFilters(
                  sessionTypes = setOf(EventType.STUDY_SESSION, EventType.COURSE),
                  hideFullSessions = true,
              ),
          availableZones = listOf("AI", "BC", "CM", "CO", "GC"),
          matchingSessionCount = 4,
          hasLocationPermission = true,
          onRadiusSelected = {},
          onZonesSelected = {},
          onShowLabsChanged = {},
          onSessionTypesSelected = {},
          onStartingTimeSelected = {},
          onHideFullSessionsChange = {},
          onLocationPermissionNeeded = {},
          onResetAll = {},
          onShowSessions = {},
      )
    }
  }
}

/** Returns whether either requested location permission was granted. */
fun isLocationPermissionGranted(results: Map<String, Boolean>): Boolean =
    results[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
        results[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
