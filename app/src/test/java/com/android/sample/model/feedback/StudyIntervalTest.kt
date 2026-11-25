// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import com.android.sample.model.event.EventType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests validation and duration behavior of [StudyInterval]. */
class StudyIntervalTest {
  /** An ended interval must end after it starts, while an open interval is valid. */
  @Test
  fun endedAt_mustBeAfterStartedAt() {
    val start = Instant.parse("2026-11-02T13:07:30Z") // 14:07:30 Zurich local time

    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      StudyInterval(start, Instant.parse("2026-11-02T13:07:30Z"))
    }
    org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
      StudyInterval(start, Instant.parse("2026-11-02T13:07:29Z"))
    }
    assertEquals(null, StudyInterval(start).endedAt)
  }

  /** Closed intervals contribute whole seconds, while an open interval contributes nothing. */
  @Test
  fun studiedSeconds_sumsClosedIntervals() {
    val first =
        StudyInterval(
            Instant.parse("2026-11-02T13:07:30Z"), // 14:07:30 Zurich local time
            Instant.parse("2026-11-02T13:50:00Z"), // 14:50:00 Zurich local time
        )
    val second =
        StudyInterval(
            Instant.parse("2026-11-02T14:00:00Z"), // 15:00:00 Zurich local time
            Instant.parse("2026-11-02T14:12:10Z"), // 15:12:10 Zurich local time
        )
    val open = StudyInterval(Instant.parse("2026-11-02T15:00:00Z")) // 16:00:00 Zurich local time

    assertEquals(
        3280L,
        SessionFeedback("event", EventType.STUDY_SESSION, null, listOf(first, second))
            .studiedSeconds,
    )
    assertEquals(
        3880L,
        SessionFeedback(
                "event",
                EventType.STUDY_SESSION,
                null,
                listOf(
                    StudyInterval(
                        Instant.parse("2026-11-02T13:07:30Z"), // 14:07:30 Zurich local time
                        Instant.parse("2026-11-02T14:12:10Z"), // 15:12:10 Zurich local time
                    )
                ),
            )
            .studiedSeconds,
    )
    assertEquals(
        3280L,
        SessionFeedback("event", EventType.STUDY_SESSION, null, listOf(first, second, open))
            .studiedSeconds,
    )
    assertEquals(0L, SessionFeedback("event", EventType.STUDY_SESSION, null).studiedSeconds)
  }

  /** A feedback record is running exactly when its last interval is open. */
  @Test
  fun isRunning_dependsOnLastInterval() {
    val start = Instant.parse("2026-11-02T13:07:30Z") // 14:07:30 Zurich local time
    val end = Instant.parse("2026-11-02T13:50:00Z") // 14:50:00 Zurich local time

    assertEquals(false, SessionFeedback("event", EventType.STUDY_SESSION, null).isRunning)
    assertEquals(
        true,
        SessionFeedback("event", EventType.STUDY_SESSION, null, listOf(StudyInterval(start)))
            .isRunning,
    )
    assertEquals(
        false,
        SessionFeedback("event", EventType.STUDY_SESSION, null, listOf(StudyInterval(start, end)))
            .isRunning,
    )
    assertEquals(
        true,
        SessionFeedback(
                "event",
                EventType.STUDY_SESSION,
                null,
                listOf(
                    StudyInterval(start, end),
                    StudyInterval(
                        Instant.parse("2026-11-02T14:00:00Z")
                    ), // 15:00:00 Zurich local time
                ),
            )
            .isRunning,
    )
  }
}
