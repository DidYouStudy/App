// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import com.android.sample.model.event.EventType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests computed properties on [StudyLog]. */
class StudyLogTest {
  /** A closed interval's duration is measured in whole seconds. */
  @Test
  fun studiedSeconds_usesWholeSeconds() {
    val studyLog =
        StudyLog(
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

    assertEquals(3280L, studyLog.studiedSeconds)
  }

  /** Each interval is floored separately before durations are summed. */
  @Test
  fun studiedSeconds_floorsEachIntervalSeparately() {
    val shortIntervals =
        listOf(
            StudyInterval(
                java.time.Instant.parse(
                    "2026-11-02T13:07:30.600Z"
                ), // 14:07:30.600 Zurich local time
                java.time.Instant.parse(
                    "2026-11-02T13:07:31.400Z"
                ), // 14:07:31.400 Zurich local time
            ),
            StudyInterval(
                java.time.Instant.parse(
                    "2026-11-02T13:08:00.600Z"
                ), // 14:08:00.600 Zurich local time
                java.time.Instant.parse(
                    "2026-11-02T13:08:01.400Z"
                ), // 14:08:01.400 Zurich local time
            ),
        )
    val almostTwoSeconds =
        listOf(
            StudyInterval(
                java.time.Instant.parse(
                    "2026-11-02T13:07:30.000Z"
                ), // 14:07:30.000 Zurich local time
                java.time.Instant.parse(
                    "2026-11-02T13:07:31.900Z"
                ), // 14:07:31.900 Zurich local time
            )
        )

    assertEquals(
        0L,
        StudyLog("event", EventType.STUDY_SESSION, null, shortIntervals).studiedSeconds,
    )
    assertEquals(
        1L,
        StudyLog("event", EventType.STUDY_SESSION, null, almostTwoSeconds).studiedSeconds,
    )
  }

  /** Running state follows the final interval rather than any earlier interval. */
  @Test
  fun isRunning_isTrueOnlyForAnOpenLastInterval() {
    val closed =
        StudyInterval(
            java.time.Instant.parse("2026-11-02T13:07:30Z"), // 14:07:30 Zurich local time
            java.time.Instant.parse("2026-11-02T13:50:00Z"), // 14:50:00 Zurich local time
        )

    assertEquals(false, StudyLog("event", EventType.STUDY_SESSION, null).isRunning)
    assertEquals(
        false,
        StudyLog("event", EventType.STUDY_SESSION, null, listOf(closed)).isRunning,
    )
    assertEquals(
        true,
        StudyLog(
                "event",
                EventType.STUDY_SESSION,
                null,
                listOf(closed, StudyInterval(closed.endedAt!!)),
            )
            .isRunning,
    )
  }
}
