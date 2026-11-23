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
  ): RingState = throw NotImplementedError()
}
