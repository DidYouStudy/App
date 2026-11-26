// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.eventplanning

import com.android.sample.ui.sharedComponents.FormContentTestTags

/** Test tags for event planning screen and its components. */
object EventPlanningScreenTestTags {
  const val SCREEN_CONTAINER = "event_planning_screen_container"
  const val SCROLL_CONTAINER = FormContentTestTags.SCROLL_CONTAINER
  const val TOP_APP_BAR = "event_planning_top_bar"
  const val BACK_BUTTON = "event_planning_back_button"
  const val TOP_BAR_TITLE = "event_planning_top_bar_title"

  // Section Labels
  const val SUBJECT_LABEL = FormContentTestTags.SUBJECT_LABEL

  // Interactive Elements / Selectors
  const val TYPE_SELECTOR = FormContentTestTags.TYPE_SELECTOR
  const val DATE_SELECTOR = FormContentTestTags.DATE_SELECTOR
  const val START_TIME_SELECTOR = FormContentTestTags.START_TIME_SELECTOR
  const val END_TIME_SELECTOR = FormContentTestTags.END_TIME_SELECTOR
  const val SUBJECT_SELECTOR = FormContentTestTags.SUBJECT_SELECTOR
  const val LOCATION_SELECTOR = FormContentTestTags.LOCATION_SELECTOR
  const val LOCATION_CLEAR_BUTTON = FormContentTestTags.LOCATION_CLEAR_BUTTON
  const val DESCRIPTION_INPUT = FormContentTestTags.DESCRIPTION_INPUT

  // Tasks
  const val ADD_TASK_BUTTON = FormContentTestTags.ADD_TASK_BUTTON

  // Actions & Feedback
  const val SAVE_BUTTON = "event_planning_save_button"
  const val SAVE_BUTTON_PROGRESS = "event_planning_save_button_progress"
  const val DELETE_BUTTON = "event_planning_delete_button"
  const val DELETE_BUTTON_PROGRESS = "event_planning_delete_button_progress"
  const val ERROR_TEXT = "event_planning_error_text"

  /**
   * Message shown while a group session cannot be saved or deleted because the device is offline.
   */
  const val OFFLINE_MESSAGE = "event_planning_offline_message"
}
