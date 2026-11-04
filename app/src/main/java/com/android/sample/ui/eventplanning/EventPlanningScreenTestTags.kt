// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude AI Agent
package com.android.sample.ui.eventplanning

import com.android.sample.ui.sharedComponents.FormComponentsTestTags

/** Test tags for event planning screen and its components. */
object EventPlanningScreenTestTags {
  const val SCREEN_CONTAINER = "event_planning_screen_container"
  const val SCROLL_CONTAINER = FormComponentsTestTags.SCROLL_CONTAINER
  const val TOP_APP_BAR = "event_planning_top_bar"
  const val BACK_BUTTON = "event_planning_back_button"
  const val TOP_BAR_TITLE = "event_planning_top_bar_title"

  // Section Labels
  const val SUBJECT_LABEL = FormComponentsTestTags.SUBJECT_LABEL

  // Interactive Elements / Selectors
  const val TYPE_SELECTOR = FormComponentsTestTags.TYPE_SELECTOR
  const val DATE_SELECTOR = FormComponentsTestTags.DATE_SELECTOR
  const val START_TIME_SELECTOR = FormComponentsTestTags.START_TIME_SELECTOR
  const val END_TIME_SELECTOR = FormComponentsTestTags.END_TIME_SELECTOR
  const val SUBJECT_SELECTOR = FormComponentsTestTags.SUBJECT_SELECTOR
  const val LOCATION_INPUT = FormComponentsTestTags.LOCATION_INPUT
  const val DESCRIPTION_INPUT = FormComponentsTestTags.DESCRIPTION_INPUT

  // Tasks
  const val ADD_TASK_BUTTON = FormComponentsTestTags.ADD_TASK_BUTTON

  // Actions & Feedback
  const val SAVE_BUTTON = "event_planning_save_button"
  const val SAVE_BUTTON_PROGRESS = "event_planning_save_button_progress"
  const val ERROR_TEXT = "event_planning_error_text"
}
