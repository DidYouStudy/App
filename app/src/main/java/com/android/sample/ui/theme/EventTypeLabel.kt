// Co-authored-by: Codex AI Agent
package com.android.sample.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.sample.R
import com.android.sample.model.event.EventType

/** Returns the string resource for this event type's localized display label. */
@StringRes
fun EventType.labelRes(): Int =
    when (this) {
      EventType.STUDY_SESSION -> R.string.event_type_study_session
      EventType.COURSE -> R.string.event_type_course
      EventType.EXERCISE_SESSION -> R.string.event_type_exercise_session
      EventType.LAB -> R.string.event_type_lab
      EventType.PROJECT -> R.string.event_type_project
      EventType.EXAM -> R.string.event_type_exam
      EventType.OTHER -> R.string.event_type_other
    }

/** Resolves this event type's localized display label for Compose UI. */
@Composable fun EventType.label(): String = stringResource(labelRes())
