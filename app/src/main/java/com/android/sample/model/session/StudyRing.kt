// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.session

import java.time.Duration
import java.time.Instant

/** Identifies whether the current ring segment is study time or break time. */
enum class RingMode {
  STUDYING,
  BREAK,
}

/** Describes the current segment and overall session progress. */
data class RingState(
    val mode: RingMode,
    val progress: Float,
    val segmentRemaining: Duration,
    val sessionRemaining: Duration,
    val isOvertime: Boolean,
)

/** Computes the ring state for a timed study session. */
object StudyRingCalculator {
  /** Computes the segment containing [now], or overtime after [end]. */
  fun compute(
      start: Instant,
      end: Instant,
      now: Instant,
      breakFrequency: Duration,
      breakLength: Duration,
  ): RingState {
    require(end.isAfter(start))
    require(!breakFrequency.isZero && !breakFrequency.isNegative)
    require(!breakLength.isZero && !breakLength.isNegative)
    require(!now.isBefore(start))

    if (!now.isBefore(end)) {
      return RingState(RingMode.STUDYING, 1f, Duration.ZERO, Duration.ZERO, isOvertime = true)
    }

    var segmentStart = start
    var mode = RingMode.STUDYING
    var segmentLength = breakFrequency
    while (true) {
      val segmentEnd = minOf(segmentStart.plus(segmentLength), end)
      if (now.isBefore(segmentEnd)) {
        val elapsed = Duration.between(segmentStart, now)
        val actualSegmentLength = Duration.between(segmentStart, segmentEnd)
        return RingState(
            mode = mode,
            progress = elapsed.toMillis().toFloat() / actualSegmentLength.toMillis().toFloat(),
            segmentRemaining = Duration.between(now, segmentEnd),
            sessionRemaining = Duration.between(now, end),
            isOvertime = false,
        )
      }
      segmentStart = segmentEnd
      if (mode == RingMode.STUDYING) {
        mode = RingMode.BREAK
        segmentLength = breakLength
      } else {
        mode = RingMode.STUDYING
        segmentLength = breakFrequency
      }
    }
  }
}
