// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the checklist items stored inside an event. */
class EventTaskTest {

  /** A new task is open; checking it off records when it was completed. */
  @Test
  fun task_isOpenUntilCompletedAtIsSet() {
    val doneAt = Instant.parse("2026-10-20T00:00:00Z")
    val open = EventTask("Read chapter 3")
    val done = open.copy(completedAt = doneAt)

    assertFalse(open.isCompleted)
    assertTrue(done.isCompleted)
    assertEquals(doneAt, done.completedAt)
  }

  /** A task must say what is planned. */
  @Test
  fun blankText_isRejected() {
    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { EventTask(blank) }
    }
  }
}
