// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.ui.eventplanning

import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.PersonalEvent
import java.time.Instant

/** 10:15 in Europe/Berlin (UTC+1 in November). */
internal val SAMPLE_START: Instant = Instant.parse("2026-11-02T09:15:00Z")

/** 12:00 in Europe/Berlin. */
internal val SAMPLE_END: Instant = Instant.parse("2026-11-02T11:00:00Z")

/** ID of the event built by [samplePersonalEvent]; tests stub and look it up by this value. */
internal const val SAMPLE_EVENT_ID = "event-1"

/** Owner of the event built by [samplePersonalEvent]; tests use it as the signed-in user ID. */
internal const val SAMPLE_OWNER_ID = "user-1"

private val SAMPLE_CREATED_AT: Instant = Instant.parse("2026-10-20T09:00:00Z")

/** Display model matching the Figma example. */
internal fun sampleDisplay(
    type: EventType = EventType.COURSE,
    title: String? = "Algebra",
    dateLabel: String = "Nov 2, 2026",
    startTimeLabel: String = "10:15",
    endTimeLabel: String = "12:00",
    location: String? = "CM5",
    description: String? = "Exercise session focusing on the new concepts of week 8.",
    tasks: List<EventTaskDisplay> =
        listOf(
            EventTaskDisplay("Solve exercises 12, 13", false),
            EventTaskDisplay("Read chapter 3 of textbook", false),
        ),
) =
    EventDetailsDisplay(
        type = type,
        title = title,
        dateLabel = dateLabel,
        startTimeLabel = startTimeLabel,
        endTimeLabel = endTimeLabel,
        location = location,
        description = description,
        tasks = tasks,
    )

/** Builds a [PersonalEvent] for tests; every test class goes through this single factory. */
internal fun samplePersonalEvent(
    id: String = SAMPLE_EVENT_ID,
    type: EventType = EventType.COURSE,
    subject: String? = "Algebra",
    location: String? = "CM5",
    description: String? = "Exercise session focusing on the new concepts of week 8.",
    startsAt: Instant = SAMPLE_START,
    endsAt: Instant = SAMPLE_END,
    tasks: List<EventTask> =
        listOf(EventTask("Solve exercises 12, 13"), EventTask("Read chapter 3 of textbook")),
) =
    PersonalEvent(
        id = id,
        ownerId = SAMPLE_OWNER_ID,
        details =
            EventDetails(
                type = type,
                subject = subject,
                startsAt = startsAt,
                endsAt = endsAt,
                location = location,
                description = description,
            ),
        createdAt = SAMPLE_CREATED_AT,
        updatedAt = SAMPLE_CREATED_AT,
        tasks = tasks,
    )