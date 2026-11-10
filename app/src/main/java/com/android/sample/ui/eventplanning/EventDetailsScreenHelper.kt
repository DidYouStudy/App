// Co-authored-by: Claude Sonnet 5.5

package com.android.sample.ui.eventplanning

import com.android.sample.model.event.EventType
import com.android.sample.model.event.PersonalEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pattern for the date part of the date/time row, e.g. "Nov 2, 2026". */
internal const val EVENT_DATE_PATTERN = "MMM d, yyyy"

/** Pattern for the start and end times, 24 hour like the time picker, e.g. "10:15". */
internal const val EVENT_TIME_PATTERN = "HH:mm"

/** Display-ready data for the Event details screen, independent from the storage model. */
internal data class EventDetailsDisplay(
    val type: EventType,
    val title: String?,
    val dateLabel: String,
    val startTimeLabel: String,
    val endTimeLabel: String,
    val location: String?,
    val description: String?,
    val tasks: List<EventTaskDisplay>,
)

/** One checklist entry of [EventDetailsDisplay]. */
internal data class EventTaskDisplay(val text: String, val isCompleted: Boolean)

/**
 * Maps a [PersonalEvent] to its [EventDetailsDisplay], formatting times in [zone].
 *
 * NOTE: assumes [PersonalEvent] exposes `details` (type, subject, location, description, startsAt,
 * endsAt) like the group session model; adjust only here if the field names differ.
 */
internal fun PersonalEvent.toDisplay(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): EventDetailsDisplay {
  val dateFormatter = DateTimeFormatter.ofPattern(EVENT_DATE_PATTERN, locale).withZone(zone)
  val timeFormatter = DateTimeFormatter.ofPattern(EVENT_TIME_PATTERN, locale).withZone(zone)
  return EventDetailsDisplay(
      type = details.type,
      title = details.subject,
      dateLabel = dateFormatter.format(details.startsAt),
      startTimeLabel = timeFormatter.format(details.startsAt),
      endTimeLabel = timeFormatter.format(details.endsAt),
      location = details.location,
      description = details.description,
      tasks = tasks.map { EventTaskDisplay(it.text, it.isCompleted) },
  )
}
