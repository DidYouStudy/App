// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.session

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests the pure study-ring segment calculation. */
class StudyRingCalculatorTest {
  private val frequency = Duration.ofMinutes(45)
  private val length = Duration.ofMinutes(10)
  private val start = Instant.parse("2026-10-28T13:00:00Z") // 14:00 in Zurich.
  private val end = Instant.parse("2026-10-28T14:00:00Z") // 15:00 in Zurich.
  private val zone = ZoneId.of("Europe/Zurich")

  /** The first study segment starts at zero progress. */
  @Test
  fun atSessionStartIsStudying() {
    assertState(instant("14:00"), RingMode.STUDYING, 0f, 45, 60)
  }

  /** Study progress advances through the first segment. */
  @Test
  fun halfwayThroughStudySegment() {
    assertState(instant("14:30"), RingMode.STUDYING, 0.6667f, 15, 30)
  }

  /** A break begins at the configured frequency. */
  @Test
  fun atBreakStartIsBreak() {
    assertState(instant("14:45"), RingMode.BREAK, 0f, 10, 15)
  }

  /** Break progress is measured within the break segment. */
  @Test
  fun halfwayThroughBreak() {
    assertState(instant("14:50"), RingMode.BREAK, 0.5f, 5, 10)
  }

  /** Study resumes after a break. */
  @Test
  fun studyResumesAfterBreak() {
    assertState(instant("14:55"), RingMode.STUDYING, 0f, 5, 5)
  }

  /** The last study segment reaches eighty percent at 14:59. */
  @Test
  fun nearSessionEndHasExpectedProgress() {
    assertState(instant("14:59"), RingMode.STUDYING, 0.8f, 1, 1)
  }

  /** At and after the end the session is overtime. */
  @Test
  fun atAndAfterEndAreOvertime() {
    assertState(instant("15:00"), RingMode.STUDYING, 1f, 0, 0, overtime = true)
    assertState(instant("15:30"), RingMode.STUDYING, 1f, 0, 0, overtime = true)
  }

  /** Times before the session are rejected. */
  @Test
  fun beforeStartIsRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      StudyRingCalculator.compute(start, end, instant("13:59"), frequency, length)
    }
  }

  /** A session shorter than a study period never schedules a break. */
  @Test
  fun shortSessionHasNoBreak() {
    val shortEnd = instant("14:30")
    assertState(
        instant("14:29"),
        RingMode.STUDYING,
        29f / 30f,
        1,
        1,
        sessionEnd = shortEnd,
    )
  }

  /** A break starting exactly at the end is not scheduled. */
  @Test
  fun breakAtEndIsNotScheduled() {
    val shortEnd = instant("14:45")
    assertState(
        instant("14:44"),
        RingMode.STUDYING,
        0.9778f,
        1,
        1,
        sessionEnd = shortEnd,
    )
  }

  /** A break extending beyond the session is cut at the session end. */
  @Test
  fun breakIsCutAtSessionEnd() {
    val cutEnd = instant("14:50")
    assertState(
        instant("14:45"),
        RingMode.BREAK,
        0f,
        5,
        5,
        sessionEnd = cutEnd,
    )
    assertState(
        instant("14:47:30"),
        RingMode.BREAK,
        0.5f,
        2,
        2,
        sessionEnd = cutEnd,
    )
  }

  /** A second cycle schedules another break and then study time. */
  @Test
  fun secondCycleIsComputed() {
    val longEnd = instant("16:00")
    assertState(
        instant("15:45"),
        RingMode.BREAK,
        0.5f,
        5,
        15,
        sessionEnd = longEnd,
    )
    assertState(
        instant("15:50"),
        RingMode.STUDYING,
        0f,
        10,
        10,
        sessionEnd = longEnd,
    )
  }

  /** Custom settings change the segment boundaries. */
  @Test
  fun customSettingsScheduleBreak() {
    val state =
        StudyRingCalculator.compute(
            start,
            end,
            instant("14:30"),
            Duration.ofMinutes(30),
            Duration.ofMinutes(5),
        )
    assertEquals(RingMode.BREAK, state.mode)
    assertEquals(0f, state.progress, 1e-4f)
  }

  /** Non-positive settings and invalid intervals are rejected. */
  @Test
  fun invalidInputsAreRejected() {
    assertThrows(IllegalArgumentException::class.java) {
      StudyRingCalculator.compute(start, end, start, Duration.ZERO, length)
    }
    assertThrows(IllegalArgumentException::class.java) {
      StudyRingCalculator.compute(start, end, start, Duration.ofMinutes(-1), length)
    }
    assertThrows(IllegalArgumentException::class.java) {
      StudyRingCalculator.compute(start, end, start, frequency, Duration.ZERO)
    }
    assertThrows(IllegalArgumentException::class.java) {
      StudyRingCalculator.compute(start, end, start, frequency, Duration.ofMinutes(-1))
    }
    assertThrows(IllegalArgumentException::class.java) {
      StudyRingCalculator.compute(start, start, start, frequency, length)
    }
    assertThrows(IllegalArgumentException::class.java) {
      StudyRingCalculator.compute(start, start.minusSeconds(1), start, frequency, length)
    }
  }

  private fun assertState(
      now: Instant,
      mode: RingMode,
      progress: Float,
      segmentMinutes: Long,
      sessionMinutes: Long,
      sessionEnd: Instant = end,
      overtime: Boolean = false,
  ) {
    val state =
        StudyRingCalculator.compute(
            start,
            sessionEnd,
            now,
            frequency,
            length,
        )
    assertEquals(mode, state.mode)
    assertEquals(progress, state.progress, 1e-4f)
    assertEquals(Duration.ofMinutes(segmentMinutes), state.segmentRemaining)
    assertEquals(Duration.ofMinutes(sessionMinutes), state.sessionRemaining)
    assertEquals(overtime, state.isOvertime)
  }

  private fun instant(localTime: String): Instant =
      LocalDateTime.parse("2026-10-28T$localTime").atZone(zone).toInstant()
}
