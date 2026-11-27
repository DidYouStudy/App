// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Copilot
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>

package com.android.sample.ui.eventplanning

import com.android.sample.model.event.Event
import com.android.sample.model.event.EventType
import com.android.sample.model.event.GroupSession
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
    val isGroupSession: Boolean = false,
    val participants: List<EventMember> = emptyList(),
    val actions: EventDetailsActionSet = EventDetailsActionSet.EDIT_AND_DELETE,
)

/** Which action buttons the Event details screen offers for the displayed event. */
internal enum class EventDetailsActionSet {
  /** A personal event: Edit and Delete. */
  EDIT_AND_DELETE,
  /** A group session hosted by the signed-in user: Edit only (Delete lives in the editor). */
  EDIT_ONLY,
  /** A group session the signed-in user joined: no actions. */
  NONE,
}

/** One checklist entry of [EventDetailsDisplay]. */
internal data class EventTaskDisplay(val text: String, val isCompleted: Boolean)

/**
 * Maps an [Event] to its [EventDetailsDisplay], formatting times in [zone].
 *
 * Personal events supply the checklist. Group sessions display [members] in their existing order.
 */
internal fun Event.toDisplay(
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
    members: List<EventMember> = emptyList(),
): EventDetailsDisplay {
  val dateFormatter = DateTimeFormatter.ofPattern(EVENT_DATE_PATTERN, locale).withZone(zone)
  val timeFormatter = DateTimeFormatter.ofPattern(EVENT_TIME_PATTERN, locale).withZone(zone)
  val isGroupSession = this is GroupSession
  return EventDetailsDisplay(
      type = details.type,
      title = details.subject?.takeIf { it.isNotBlank() },
      dateLabel = dateFormatter.format(details.startsAt),
      startTimeLabel = timeFormatter.format(details.startsAt),
      endTimeLabel = timeFormatter.format(details.endsAt),
      location = details.location?.takeIf { it.isNotBlank() },
      description = details.description?.takeIf { it.isNotBlank() },
      tasks =
          (this as? PersonalEvent)?.tasks.orEmpty().map {
            EventTaskDisplay(it.text, it.isCompleted)
          },
      isGroupSession = isGroupSession,
      participants = if (isGroupSession) members else emptyList(),
      actions =
          when {
            !isGroupSession -> EventDetailsActionSet.EDIT_AND_DELETE
            members.any { it.isHost && it.isCurrentUser } -> EventDetailsActionSet.EDIT_ONLY
            else -> EventDetailsActionSet.NONE
          },
  )
}

/** The [EventKind] this event is loaded and navigated as. */
internal fun Event.kind(): EventKind =
    when (this) {
      is PersonalEvent -> EventKind.PERSONAL
      is GroupSession -> EventKind.GROUP_SESSION
    }
