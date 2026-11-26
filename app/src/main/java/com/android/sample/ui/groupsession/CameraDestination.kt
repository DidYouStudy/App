// Co-authored-by: Gemini <noreply@google.com>
package com.android.sample.ui.groupsession

import com.android.sample.model.location.Location

/**
 * Target destination for the group session map camera animation.
 *
 * @property location Geographic coordinates to focus on.
 * @property trigger Monotonically increasing sequence number ensuring repeated requests to the same
 *   coordinates (e.g. repeated recenter clicks) trigger a new camera animation.
 * @property resetZoom When true, animates the zoom back to `map_default_zoom` (e.g. when unfocusing
 *   a selected pin) instead of zooming in to `map_focus_zoom`.
 */
data class CameraDestination(
    val location: Location,
    val trigger: Int = 0,
    val resetZoom: Boolean = false,
)
