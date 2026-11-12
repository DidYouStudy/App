// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.ui.eventplanning

import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for helper functions in [EventViewModelHelper]. */
class EventViewModelHelperTest {

  private val zone = ZoneId.of("Europe/Berlin")
  private val now = Instant.parse("2026-11-02T10:00:00Z")
  private val clock = Clock.fixed(now, zone)

  @Test
  fun toEventPlanningError_mapsEventsExceptionToExpectedErrors() {
    val mappings =
        listOf(
            EventsError.INVALID_ARGUMENT to EventPlanningError.INVALID_EVENT,
            EventsError.NOT_FOUND to EventPlanningError.EVENT_NOT_FOUND,
            EventsError.PERMISSION_DENIED to EventPlanningError.PERMISSION_DENIED,
            EventsError.INVALID_EVENT_DATA to EventPlanningError.INVALID_EVENT_DATA,
            EventsError.STORAGE_FAILURE to EventPlanningError.STORAGE_FAILURE,
            EventsError.UNKNOWN to EventPlanningError.FAILED_TO_ADD_EVENT,
        )

    for ((error, expected) in mappings) {
      val exception = EventsException(error)
      assertEquals(
          expected,
          exception.toEventPlanningError(EventPlanningError.FAILED_TO_ADD_EVENT),
      )
    }
  }

  @Test
  fun toEventPlanningError_mapsGenericExceptionToDefaultError() {
    val exception = RuntimeException("Unknown")
    assertEquals(
        EventPlanningError.FAILED_TO_LOAD_EVENT,
        exception.toEventPlanningError(EventPlanningError.FAILED_TO_LOAD_EVENT),
    )
  }

  @Test
  fun toggleTaskItem_togglesTaskCompletionState() {
    val tasks = listOf(EventTask("Task 1"), EventTask("Task 2"))

    val toggled = toggleTaskItem(tasks, 0, clock)
    assertTrue(toggled[0].isCompleted)
    assertEquals(now, toggled[0].completedAt)
    assertFalse(toggled[1].isCompleted)

    val toggledBack = toggleTaskItem(toggled, 0, clock)
    assertFalse(toggledBack[0].isCompleted)
    assertNull(toggledBack[0].completedAt)
  }

  @Test
  fun toggleTaskItem_invalidIndex_returnsOriginalList() {
    val tasks = listOf(EventTask("Task 1"))
    assertEquals(tasks, toggleTaskItem(tasks, -1, clock))
    assertEquals(tasks, toggleTaskItem(tasks, 5, clock))
  }

  @Test
  fun removeTaskItem_removesTaskAtIndex() {
    val tasks = listOf(EventTask("Task 1"), EventTask("Task 2"))
    val result = removeTaskItem(tasks, 0)

    assertEquals(1, result.size)
    assertEquals("Task 2", result[0].text)
  }

  @Test
  fun removeTaskItem_invalidIndex_returnsOriginalList() {
    val tasks = listOf(EventTask("Task 1"))
    assertEquals(tasks, removeTaskItem(tasks, -1))
    assertEquals(tasks, removeTaskItem(tasks, 10))
  }

  @Test
  fun addTaskItem_appendsTaskWhenNonBlank() {
    val tasks = listOf(EventTask("Task 1"))
    val result = addTaskItem(tasks, "  Task 2  ")

    assertEquals(2, result.size)
    assertEquals("Task 2", result[1].text)
  }

  @Test
  fun addTaskItem_ignoresBlankText() {
    val tasks = listOf(EventTask("Task 1"))
    assertEquals(tasks, addTaskItem(tasks, "   "))
  }

  @Test
  fun calculateStartAndEndInstants_sameDayAndNextDay() {
    val date = LocalDate.of(2026, 11, 2)
    val startTime = LocalTime.of(10, 0)
    val endTimeSameDay = LocalTime.of(12, 0)

    val (start1, end1) = calculateStartAndEndInstants(date, startTime, endTimeSameDay, clock)
    assertEquals(Instant.parse("2026-11-02T09:00:00Z"), start1)
    assertEquals(Instant.parse("2026-11-02T11:00:00Z"), end1)

    val endTimeNextDay = LocalTime.of(2, 0)
    val (start2, end2) = calculateStartAndEndInstants(date, startTime, endTimeNextDay, clock)
    assertEquals(Instant.parse("2026-11-02T09:00:00Z"), start2)
    assertEquals(Instant.parse("2026-11-03T01:00:00Z"), end2)
  }

  /** Keeps a 24-hour event's stored end while the schedule is unchanged; a new date recomputes. */
  @Test
  fun calculateStartAndEndInstants_keepsOriginalOnlyWhileScheduleIsUnchanged() {
    val original =
        EventDetails(
            type = EventType.STUDY_SESSION,
            subject = "Algebra",
            startsAt = Instant.parse("2026-11-02T09:00:00Z"),
            endsAt = Instant.parse("2026-11-03T09:00:00Z"),
        )
    val date = LocalDate.of(2026, 11, 2)
    val time = LocalTime.of(10, 0)

    val unchanged = calculateStartAndEndInstants(date, time, time, clock, original)
    val (movedStart, _) =
        calculateStartAndEndInstants(date.plusDays(1), time, time, clock, original)

    assertEquals(original.startsAt to original.endsAt, unchanged)
    assertEquals(Instant.parse("2026-11-03T09:00:00Z"), movedStart)
  }
}
