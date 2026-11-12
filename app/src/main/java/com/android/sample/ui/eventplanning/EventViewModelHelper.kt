// Co-authored-by: Copilot
// Co-authored-by: Gemini AI Agent
package com.android.sample.ui.eventplanning

import com.android.sample.R
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Stable failure categories for event planning and details errors. */
enum class EventPlanningError {
  FAILED_TO_OBSERVE_USER,
  NO_SIGNED_IN_USER,
  REQUIRED_FIELDS_MISSING,
  INVALID_TIME_RANGE,
  EVENT_IN_PAST,
  EVENT_TOO_LONG,
  NO_EVENT_TO_EDIT,
  FAILED_TO_ADD_EVENT,
  FAILED_TO_EDIT_EVENT,
  FAILED_TO_DELETE_EVENT,
  FAILED_TO_LOAD_EVENT,
  INVALID_EVENT,
  EVENT_NOT_FOUND,
  PERMISSION_DENIED,
  INVALID_EVENT_DATA,
  STORAGE_FAILURE;

  /** Whether this error came from validating fields entered in the event form. */
  val isInputValidationError: Boolean
    get() =
        this in
            setOf(
                REQUIRED_FIELDS_MISSING,
                INVALID_TIME_RANGE,
                EVENT_IN_PAST,
                EVENT_TOO_LONG,
                INVALID_EVENT,
            )
}

/** Maps an [EventPlanningError] to its localized string resource. */
val EventPlanningError.stringResource: Int
  get() =
      when (this) {
        EventPlanningError.FAILED_TO_OBSERVE_USER ->
            R.string.event_planning_error_failed_to_observe_user
        EventPlanningError.NO_SIGNED_IN_USER -> R.string.event_planning_error_no_signed_in_user
        EventPlanningError.REQUIRED_FIELDS_MISSING -> R.string.event_planning_error_required_fields
        EventPlanningError.INVALID_TIME_RANGE -> R.string.event_planning_error_invalid_time_range
        EventPlanningError.EVENT_IN_PAST -> R.string.event_planning_error_event_in_past
        EventPlanningError.EVENT_TOO_LONG -> R.string.event_planning_error_event_too_long
        EventPlanningError.NO_EVENT_TO_EDIT -> R.string.event_planning_error_no_event_to_edit
        EventPlanningError.FAILED_TO_ADD_EVENT -> R.string.event_planning_error_failed_to_add_event
        EventPlanningError.FAILED_TO_EDIT_EVENT ->
            R.string.event_planning_error_failed_to_edit_event
        EventPlanningError.FAILED_TO_DELETE_EVENT ->
            R.string.event_planning_error_failed_to_delete_event
        EventPlanningError.FAILED_TO_LOAD_EVENT ->
            R.string.event_planning_error_failed_to_load_event
        EventPlanningError.INVALID_EVENT -> R.string.event_planning_error_invalid_event
        EventPlanningError.EVENT_NOT_FOUND -> R.string.event_planning_error_event_not_found
        EventPlanningError.PERMISSION_DENIED -> R.string.event_planning_error_permission_denied
        EventPlanningError.INVALID_EVENT_DATA -> R.string.event_planning_error_invalid_event_data
        EventPlanningError.STORAGE_FAILURE -> R.string.event_planning_error_storage_failure
      }

/** Turns a repository failure into a stable [EventPlanningError] category for the UI. */
fun Throwable.toEventPlanningError(default: EventPlanningError): EventPlanningError =
    if (this is EventsException) {
      when (error) {
        EventsError.INVALID_ARGUMENT -> EventPlanningError.INVALID_EVENT
        EventsError.NOT_FOUND -> EventPlanningError.EVENT_NOT_FOUND
        EventsError.PERMISSION_DENIED -> EventPlanningError.PERMISSION_DENIED
        EventsError.INVALID_EVENT_DATA -> EventPlanningError.INVALID_EVENT_DATA
        EventsError.STORAGE_FAILURE -> EventPlanningError.STORAGE_FAILURE
        EventsError.UNKNOWN -> default
      }
    } else {
      default
    }

/**
 * Checks off or reopens the task at [index] in [tasks]. An index outside the list bounds returns
 * [tasks] unchanged.
 */
fun toggleTaskItem(tasks: List<EventTask>, index: Int, clock: Clock): List<EventTask> {
  if (index !in tasks.indices) return tasks
  val now = clock.instant()
  return tasks.mapIndexed { i, task ->
    if (i == index) task.copy(completedAt = if (task.isCompleted) null else now) else task
  }
}

/**
 * Removes the task at [index] from [tasks]. An index outside the list bounds returns [tasks]
 * unchanged.
 */
fun removeTaskItem(tasks: List<EventTask>, index: Int): List<EventTask> {
  if (index !in tasks.indices) return tasks
  return tasks.filterIndexed { i, _ -> i != index }
}

/** Appends an open task with [text] to [tasks]. Blank or empty text returns [tasks] unchanged. */
fun addTaskItem(tasks: List<EventTask>, text: String): List<EventTask> {
  val trimmed = text.trim()
  if (trimmed.isEmpty()) return tasks
  return tasks + EventTask(trimmed)
}

/**
 * Converts local [date], [startTime], and [endTime] into start and end [Instant]s with clock time
 * zone support, handling next-day midnight crossing when end time is before start time.
 */
fun calculateStartAndEndInstants(
    date: LocalDate,
    startTime: LocalTime,
    endTime: LocalTime,
    clock: Clock,
): Pair<Instant, Instant> {
  val endDate = if (endTime.isBefore(startTime)) date.plusDays(1) else date
  val startsAt = date.atTime(startTime).atZone(clock.zone).toInstant()
  val endsAt = endDate.atTime(endTime).atZone(clock.zone).toInstant()
  return Pair(startsAt, endsAt)
}
