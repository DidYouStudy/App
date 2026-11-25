// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import com.android.sample.model.event.EventType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests computed properties on [SessionFeedback]. */
class SessionFeedbackTest {
  /** A closed interval's duration is measured in whole seconds. */
  @Test
  fun studiedSeconds_usesWholeSeconds() {
    val feedback =
        SessionFeedback(
            eventId = "event",
            eventType = EventType.STUDY_SESSION,
            subject = "Analysis I",
            intervals =
                listOf(
                    StudyInterval(
                        java.time.Instant.parse(
                            "2026-11-02T13:07:30Z"
                        ), // 14:07:30 Zurich local time
                        java.time.Instant.parse(
                            "2026-11-02T13:50:00Z"
                        ), // 14:50:00 Zurich local time
                    ),
                    StudyInterval(
                        java.time.Instant.parse(
                            "2026-11-02T14:00:00Z"
                        ), // 15:00:00 Zurich local time
                        java.time.Instant.parse(
                            "2026-11-02T14:12:10Z"
                        ), // 15:12:10 Zurich local time
                    ),
                ),
        )

    assertEquals(3280L, feedback.studiedSeconds)
  }

  /** Running state follows the final interval rather than any earlier interval. */
  @Test
  fun isRunning_isTrueOnlyForAnOpenLastInterval() {
    val closed =
        StudyInterval(
            java.time.Instant.parse("2026-11-02T13:07:30Z"), // 14:07:30 Zurich local time
            java.time.Instant.parse("2026-11-02T13:50:00Z"), // 14:50:00 Zurich local time
        )

    assertEquals(false, SessionFeedback("event", EventType.STUDY_SESSION, null).isRunning)
    assertEquals(
        false,
        SessionFeedback("event", EventType.STUDY_SESSION, null, listOf(closed)).isRunning,
    )
    assertEquals(
        true,
        SessionFeedback(
                "event",
                EventType.STUDY_SESSION,
                null,
                listOf(closed, StudyInterval(closed.endedAt!!)),
            )
            .isRunning,
    )
  }
}
