// Co-authored-by: GitHub Copilot
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.android.sample.model.event.EventType

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Google brand colours are fixed by Google's guidelines, not theme roles.
val GoogleButtonBorder = Color(0xFFDADCE0)
val GoogleButtonText = Color(0xFF3C4043)

// Event Type Colors for Light Theme
val EventTypeStudySession = Color(0xFFC09AF7)
val EventTypeCourse = Color(0xFF9BE7EF)
val EventTypeExerciseSession = Color(0xFF98E27E)
val EventTypeLab = Color(0xFFF2E36F)
val EventTypeProject = Color(0xFFF2A876)
val EventTypeExam = Color(0xFFF57975)
val EventTypeOther = Color(0xFFC7C7C7)

// Event Type Colors for Dark Theme
val EventTypeStudySessionDark = Color(0xFF5D3891)
val EventTypeCourseDark = Color(0xFF006875)
val EventTypeExerciseSessionDark = Color(0xFF2E6A1F)
val EventTypeLabDark = Color(0xFF6B6000)
val EventTypeProjectDark = Color(0xFF8B4210)
val EventTypeExamDark = Color(0xFF8C1D18)
val EventTypeOtherDark = Color(0xFF424242)

/** Returns a theme-adaptive background color for the given [EventType]. */
@Composable
fun eventColor(type: EventType, darkTheme: Boolean = isSystemInDarkTheme()): Color {
  return if (darkTheme) {
    when (type) {
      EventType.STUDY_SESSION -> EventTypeStudySessionDark
      EventType.COURSE -> EventTypeCourseDark
      EventType.EXERCISE_SESSION -> EventTypeExerciseSessionDark
      EventType.LAB -> EventTypeLabDark
      EventType.PROJECT -> EventTypeProjectDark
      EventType.EXAM -> EventTypeExamDark
      EventType.OTHER -> EventTypeOtherDark
    }
  } else {
    when (type) {
      EventType.STUDY_SESSION -> EventTypeStudySession
      EventType.COURSE -> EventTypeCourse
      EventType.EXERCISE_SESSION -> EventTypeExerciseSession
      EventType.LAB -> EventTypeLab
      EventType.PROJECT -> EventTypeProject
      EventType.EXAM -> EventTypeExam
      EventType.OTHER -> EventTypeOther
    }
  }
}

/** Extension property to conveniently obtain a theme-adapted [Color] for an [EventType]. */
val EventType.color: Color
  @Composable get() = eventColor(this)

/** Returns a theme-adaptive text/icon color for content displayed on top of an [eventColor]. */
@Composable
fun onEventColor(type: EventType, darkTheme: Boolean = isSystemInDarkTheme()): Color {
  return if (darkTheme) Color.White else Color(0xFF1D1B20)
}

/** Extension property to conveniently obtain a theme-adapted on-color for an [EventType]. */
val EventType.onColor: Color
  @Composable get() = onEventColor(this)
