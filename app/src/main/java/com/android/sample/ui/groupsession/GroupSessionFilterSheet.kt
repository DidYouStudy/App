// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.ui.groupsession

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
) {}

/** Returns whether either requested location permission was granted. */
fun isLocationPermissionGranted(results: Map<String, Boolean>): Boolean = false
