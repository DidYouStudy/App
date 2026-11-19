// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: Copilot
package com.android.sample.ui.eventplanning

import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the mapping from [com.android.sample.model.event.PersonalEvent] to its display. */
class EventDetailsScreenHelperTest {
  private val berlin = ZoneId.of("Europe/Berlin")
  private val utc = ZoneId.of("UTC")

  @Test
  fun patternsMatchTheDesign() {
    assertEquals("MMM d, yyyy", EVENT_DATE_PATTERN)
    assertEquals("HH:mm", EVENT_TIME_PATTERN)
  }

  @Test
  fun mapsEveryPlainFieldOfTheEvent() {
    val type = EventType.OTHER
    val event =
        samplePersonalEvent(
            type = type,
            subject = "Algebra",
            location = "CM5",
            description = "Week 8",
        )

    val display = event.toDisplay(zone = berlin, locale = Locale.US)

    assertEquals(type, display.type)
    assertEquals("Algebra", display.title)
    assertEquals("CM5", display.location)
    assertEquals("Week 8", display.description)
  }

  @Test
  fun formatsDateAndTimesLikeTheFigmaExample() {
    val display = samplePersonalEvent().toDisplay(zone = berlin, locale = Locale.US)

    assertEquals("Nov 2, 2026", display.dateLabel)
    assertEquals("10:15", display.startTimeLabel)
    assertEquals("12:00", display.endTimeLabel)
  }

  @Test
  fun formatsTimesInTheRequestedZone() {
    val display = samplePersonalEvent().toDisplay(zone = utc, locale = Locale.US)

    assertEquals("09:15", display.startTimeLabel)
    assertEquals("11:00", display.endTimeLabel)
  }

  @Test
  fun dateFollowsTheZoneAcrossMidnight() {
    val event =
        samplePersonalEvent(
            startsAt = Instant.parse("2026-11-02T23:30:00Z"),
            endsAt = Instant.parse("2026-11-03T00:30:00Z"),
        )

    assertEquals("Nov 2, 2026", event.toDisplay(zone = utc, locale = Locale.US).dateLabel)
    assertEquals("Nov 3, 2026", event.toDisplay(zone = berlin, locale = Locale.US).dateLabel)
  }

  @Test
  fun keepsMissingOptionalFieldsAsNull() {
    val display =
        samplePersonalEvent(
                type = EventType.OTHER,
                subject = null,
                location = null,
                description = null,
            )
            .toDisplay(zone = berlin, locale = Locale.US)

    assertNull(display.title)
    assertNull(display.location)
    assertNull(display.description)
  }

  @Test
  fun whitespaceOnlyDescriptionIsDisplayedAsMissing() {
    val display =
        samplePersonalEvent(description = " \t\n").toDisplay(zone = berlin, locale = Locale.US)

    assertNull(display.description)
  }

  @Test
  fun mapsTasksInOrderWithTheirCompletion() {
    val event =
        samplePersonalEvent(
            tasks =
                listOf(
                    EventTask(text = "First", completedAt = null),
                    EventTask(text = "Second", completedAt = Instant.parse("2026-11-01T10:00:00Z")),
                )
        )

    val tasks = event.toDisplay(zone = berlin, locale = Locale.US).tasks

    assertEquals(listOf(EventTaskDisplay("First", false), EventTaskDisplay("Second", true)), tasks)
  }

  @Test
  fun eventWithoutTasksHasAnEmptyTaskList() {
    val display = samplePersonalEvent(tasks = emptyList()).toDisplay(zone = berlin)

    assertTrue(display.tasks.isEmpty())
  }

  @Test
  fun defaultArgumentsDoNotCrash() {
    val display = samplePersonalEvent().toDisplay()

    assertEquals("Algebra", display.title)
  }

  /** A group session maps its shared details and has no checklist. */
  @Test
  fun groupSession_mapsDetailsAndHasEmptyTasks() {
    val session = sampleGroupSession(subject = "Algebra", location = "CM5", description = "Week 8")

    val display = session.toDisplay(zone = berlin, locale = Locale.US)

    assertEquals(session.details.type, display.type)
    assertEquals("Algebra", display.title)
    assertEquals("CM5", display.location)
    assertEquals("Week 8", display.description)
    assertEquals("Nov 2, 2026", display.dateLabel)
    assertEquals("10:15", display.startTimeLabel)
    assertEquals("12:00", display.endTimeLabel)
    assertTrue(display.tasks.isEmpty())
  }

  /** Each event type maps to the kind used for navigation and loading. */
  @Test
  fun kind_mapsEachEventType() {
    assertEquals(EventKind.PERSONAL, samplePersonalEvent().kind())
    assertEquals(EventKind.GROUP_SESSION, sampleGroupSession().kind())
  }

  /** A group session is flagged, lists its members in the given order, and has no tasks. */
  @Test
  fun toDisplay_groupSession_hasParticipantsAndNoTasks() {
    val members =
        listOf(
            EventMember("host", "Hosty", isHost = true, isCurrentUser = true),
            EventMember("bob", null, isHost = false, isUnavailable = true),
        )

    val display = sampleGroupSession().toDisplay(members = members)

    assertTrue(display.isGroupSession)
    assertEquals(members, display.participants)
    assertTrue(display.tasks.isEmpty())
  }

  /** A personal event is not a group session and has no participants. */
  @Test
  fun toDisplay_personalEvent_hasNoParticipants() {
    val display = samplePersonalEvent().toDisplay()

    assertEquals(false, display.isGroupSession)
    assertTrue(display.participants.isEmpty())
  }

  /** A personal event offers Edit and Delete. */
  @Test
  fun toDisplay_personalEvent_offersEditAndDelete() {
    assertEquals(EventDetailsActionSet.EDIT_AND_DELETE, samplePersonalEvent().toDisplay().actions)
  }

  /** The signed-in host of a group session may only edit it from Details. */
  @Test
  fun toDisplay_groupSessionHostedByCurrentUser_offersEditOnly() {
    val members =
        listOf(
            EventMember("me", "Me", isHost = true, isCurrentUser = true),
            EventMember("bob", "Bob", isHost = false),
        )

    val display = sampleGroupSession().toDisplay(members = members)

    assertEquals(EventDetailsActionSet.EDIT_ONLY, display.actions)
  }

  /** A member who is not the host gets no actions. */
  @Test
  fun toDisplay_groupSessionJoinedByCurrentUser_offersNoActions() {
    val members =
        listOf(
            EventMember("host", "Hosty", isHost = true),
            EventMember("me", "Me", isHost = false, isCurrentUser = true),
        )

    val display = sampleGroupSession().toDisplay(members = members)

    assertEquals(EventDetailsActionSet.NONE, display.actions)
  }
}
