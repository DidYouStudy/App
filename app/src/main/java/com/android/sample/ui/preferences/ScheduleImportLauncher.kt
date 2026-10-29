// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.ui.preferences

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.sample.R

/** MIME types offered by the picker; providers often label .ics files as generic binary or text. */
private val SCHEDULE_MIME_TYPES = arrayOf("text/calendar", "application/octet-stream", "text/plain")

/**
 * Returns an action that opens the system file picker and imports the chosen .ics schedule,
 * reporting the outcome with a toast. Pass it as `onImportClick` of the preferences screen.
 */
@Composable
fun rememberScheduleImportLauncher(
    viewModel: ScheduleImportViewModel = scheduleImportViewModel()
): () -> Unit {
  val context = LocalContext.current
  val resources = LocalResources.current
  val state by viewModel.state.collectAsStateWithLifecycle()

  val picker =
      rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importSchedule { context.contentResolver.openInputStream(uri) }
      }

  // Show the outcome once, then reset so it is not repeated on recomposition.
  LaunchedEffect(state) {
    val message =
        when (val s = state) {
          is ScheduleImportState.Success ->
              if (s.skipped == 0) {
                resources.getQuantityString(
                    R.plurals.preferences_import_schedule_success,
                    s.count,
                    s.count,
                )
              } else {
                // Some entries were unreadable: say so instead of reporting a clean import.
                resources.getQuantityString(
                    R.plurals.preferences_import_schedule_partial,
                    s.skipped,
                    s.skipped,
                )
              }
          ScheduleImportState.Failure ->
              resources.getString(R.string.preferences_import_schedule_failure)
          else -> null
        }
    if (message != null) {
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      viewModel.consumeResult()
    }
  }

  return { picker.launch(SCHEDULE_MIME_TYPES) }
}
