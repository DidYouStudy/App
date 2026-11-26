// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import com.android.sample.model.event.EventTask
import com.android.sample.model.event.EventTextLimits
import com.android.sample.model.event.EventType
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Behavior every [StudyLogRepository] implementation must satisfy. */
abstract class StudyLogRepositoryContractTest {
  protected abstract fun createRepository(): StudyLogRepository

  private lateinit var repository: StudyLogRepository
  private val owner = "owner-1"
  private val otherOwner = "owner-2"
  private val event = "event-1"
  private val otherEvent = "event-2"
  private val plannedStart = Instant.parse("2026-11-02T13:00:00Z") // 14:00 Zurich local time
  private val plannedEnd = Instant.parse("2026-11-02T14:00:00Z") // 15:00 Zurich local time
  private val target =
      StudyLogTarget(event, EventType.STUDY_SESSION, " Analysis I ", plannedStart, plannedEnd)
  private val start = Instant.parse("2026-11-02T13:07:30Z") // 14:07:30 Zurich local time
  private val end = Instant.parse("2026-11-02T13:50:00Z") // 14:50:00 Zurich local time
  private val resume = Instant.parse("2026-11-02T14:00:00Z") // 15:00:00 Zurich local time
  private val resumeEnd = Instant.parse("2026-11-02T14:12:10Z") // 15:12:10 Zurich local time

  @Before
  fun setUp() {
    repository = createRepository()
  }

  private fun Result<*>.error() = (exceptionOrNull() as StudyLogException).error

  private suspend fun current(ownerId: String = owner, eventId: String = event): StudyLog? =
      repository.observeLog(ownerId, eventId).first().getOrThrow()

  private fun assertError(result: Result<*>, error: StudyLogError) {
    assertEquals(error, result.error())
  }

  /** Observing an absent record emits null and each relevant mutation emits a new record. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observeLog_emitsInitialAndRelevantChangesOnly() = runTest {
    val own = mutableListOf<StudyLog?>()
    val otherEventValues = mutableListOf<StudyLog?>()
    val otherOwnerValues = mutableListOf<StudyLog?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeLog(owner, event).collect { own += it.getOrThrow() }
    }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeLog(owner, otherEvent).collect { otherEventValues += it.getOrThrow() }
    }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeLog(otherOwner, event).collect { otherOwnerValues += it.getOrThrow() }
    }

    assertEquals(listOf(null), own)
    assertEquals(listOf(null), otherEventValues)
    assertEquals(listOf(null), otherOwnerValues)
    repository.startInterval(owner, target, start).getOrThrow()
    assertEquals(2, own.size)
    assertEquals(listOf(null), otherEventValues)
    assertEquals(listOf(null), otherOwnerValues)
    repository.saveComment(owner, target, "note").getOrThrow()
    assertEquals(3, own.size)
    assertEquals("note", own.last()?.comment)
  }

  /**
   * Period observation uses planned-start boundaries, owner isolation, ordering, and re-emission.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observeLogs_filtersSortsAndReemitsRelevantChanges() = runTest {
    val firstTarget =
        StudyLogTarget(
            "first",
            EventType.STUDY_SESSION,
            "First",
            Instant.parse("2026-11-23T09:00:00Z"),
            Instant.parse("2026-11-23T10:00:00Z"),
        )
    val secondTarget =
        StudyLogTarget(
            "second",
            EventType.COURSE,
            "Second",
            Instant.parse("2026-11-25T13:00:00Z"),
            Instant.parse("2026-11-25T14:00:00Z"),
        )
    val thirdTarget =
        StudyLogTarget(
            "third",
            EventType.EXAM,
            "Third",
            Instant.parse("2026-11-30T08:00:00Z"),
            Instant.parse("2026-11-30T09:00:00Z"),
        )
    val bTarget =
        StudyLogTarget(
            "b",
            EventType.STUDY_SESSION,
            "B",
            Instant.parse("2026-11-24T13:00:00Z"),
            Instant.parse("2026-11-24T14:00:00Z"),
        )
    val aTarget =
        StudyLogTarget(
            "a",
            EventType.STUDY_SESSION,
            "A",
            Instant.parse("2026-11-24T13:00:00Z"),
            Instant.parse("2026-11-24T14:00:00Z"),
        )
    repository.startInterval(owner, secondTarget, start).getOrThrow()
    repository.startInterval(owner, thirdTarget, start).getOrThrow()
    repository.startInterval(owner, firstTarget, start).getOrThrow()
    repository.startInterval(owner, bTarget, start).getOrThrow()
    repository.startInterval(owner, aTarget, start).getOrThrow()
    repository.startInterval(otherOwner, firstTarget, start).getOrThrow()

    val values = mutableListOf<List<StudyLog>>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository
          .observeLogs(
              owner,
              Instant.parse("2026-11-23T00:00:00Z"),
              Instant.parse("2026-11-30T00:00:00Z"),
          )
          .collect { values += it.getOrThrow() }
    }

    assertEquals(
        listOf(
            StudyLog(
                "first",
                EventType.STUDY_SESSION,
                "First",
                Instant.parse("2026-11-23T09:00:00Z"),
                Instant.parse("2026-11-23T10:00:00Z"),
                listOf(StudyInterval(start)),
            ),
            StudyLog(
                "a",
                EventType.STUDY_SESSION,
                "A",
                Instant.parse("2026-11-24T13:00:00Z"),
                Instant.parse("2026-11-24T14:00:00Z"),
                listOf(StudyInterval(start)),
            ),
            StudyLog(
                "b",
                EventType.STUDY_SESSION,
                "B",
                Instant.parse("2026-11-24T13:00:00Z"),
                Instant.parse("2026-11-24T14:00:00Z"),
                listOf(StudyInterval(start)),
            ),
            StudyLog(
                "second",
                EventType.COURSE,
                "Second",
                Instant.parse("2026-11-25T13:00:00Z"),
                Instant.parse("2026-11-25T14:00:00Z"),
                listOf(StudyInterval(start)),
            ),
        ),
        values.single(),
    )
    assertEquals(
        listOf(
            StudyLog(
                "first",
                EventType.STUDY_SESSION,
                "First",
                Instant.parse("2026-11-23T09:00:00Z"),
                Instant.parse("2026-11-23T10:00:00Z"),
                listOf(StudyInterval(start)),
            ),
            StudyLog(
                "a",
                EventType.STUDY_SESSION,
                "A",
                Instant.parse("2026-11-24T13:00:00Z"),
                Instant.parse("2026-11-24T14:00:00Z"),
                listOf(StudyInterval(start)),
            ),
            StudyLog(
                "b",
                EventType.STUDY_SESSION,
                "B",
                Instant.parse("2026-11-24T13:00:00Z"),
                Instant.parse("2026-11-24T14:00:00Z"),
                listOf(StudyInterval(start)),
            ),
            StudyLog(
                "second",
                EventType.COURSE,
                "Second",
                Instant.parse("2026-11-25T13:00:00Z"),
                Instant.parse("2026-11-25T14:00:00Z"),
                listOf(StudyInterval(start)),
            ),
        ),
        repository
            .observeLogs(
                owner,
                Instant.parse("2026-11-23T00:00:00Z"),
                Instant.parse("2026-11-30T08:00:00Z"),
            )
            .first()
            .getOrThrow(),
    )
    assertEquals(
        listOf(
            StudyLog(
                "third",
                EventType.EXAM,
                "Third",
                Instant.parse("2026-11-30T08:00:00Z"),
                Instant.parse("2026-11-30T09:00:00Z"),
                listOf(StudyInterval(start)),
            )
        ),
        repository
            .observeLogs(
                owner,
                Instant.parse("2026-11-30T08:00:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"),
            )
            .first()
            .getOrThrow(),
    )
    repository.saveComment(otherOwner, firstTarget, "other owner")
    assertEquals(1, values.size)
    repository.saveComment(owner, firstTarget, "changed").getOrThrow()
    assertEquals(2, values.size)
    assertEquals("changed", values.last().first().comment)
  }

  /**
   * Period observation excludes the upper boundary, includes the lower boundary, and validates
   * input.
   */
  @Test
  fun observeLogs_handlesBoundariesAndInvalidRanges() = runTest {
    assertError(
        repository
            .observeLogs(
                owner,
                Instant.parse("2026-11-30T00:00:00Z"),
                Instant.parse("2026-11-30T00:00:00Z"),
            )
            .first(),
        StudyLogError.INVALID_ARGUMENT,
    )
    assertError(
        repository
            .observeLogs(
                "owner/id",
                Instant.parse("2026-11-23T00:00:00Z"),
                Instant.parse("2026-11-30T00:00:00Z"),
            )
            .first(),
        StudyLogError.INVALID_ARGUMENT,
    )
  }

  /** Starting a missing record stores normalized target metadata and one open interval. */
  @Test
  fun startInterval_createsRecordAndKeepsMetadataOnLaterStarts() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    assertEquals(
        StudyLog(
            event,
            EventType.STUDY_SESSION,
            "Analysis I",
            plannedStart,
            plannedEnd,
            listOf(StudyInterval(start)),
        ),
        current(),
    )
    repository.stopInterval(owner, event, end).getOrThrow()
    repository
        .startInterval(
            owner,
            StudyLogTarget(
                event,
                EventType.COURSE,
                " Edited ",
                Instant.parse("2026-11-03T13:00:00Z"),
                Instant.parse("2026-11-03T14:00:00Z"),
            ),
            end,
        )
        .getOrThrow()
    assertEquals(EventType.STUDY_SESSION, current()?.eventType)
    assertEquals("Analysis I", current()?.subject)
    assertEquals(plannedStart, current()?.plannedStart)
    assertEquals(plannedEnd, current()?.plannedEnd)
    assertEquals(listOf(StudyInterval(start, end), StudyInterval(end)), current()?.intervals)
  }

  /** Starting before the planned time is allowed and keeps the frozen plan unchanged. */
  @Test
  fun startInterval_acceptsStartBeforePlannedStart() = runTest {
    val startedBeforePlan =
        Instant.parse("2026-11-02T12:50:00Z") // 13:50 Zurich local time, ten minutes early
    repository.startInterval(owner, target, startedBeforePlan).getOrThrow()

    assertEquals(
        StudyLog(
            event,
            EventType.STUDY_SESSION,
            "Analysis I",
            Instant.parse("2026-11-02T13:00:00Z"),
            Instant.parse("2026-11-02T14:00:00Z"),
            listOf(StudyInterval(startedBeforePlan)),
        ),
        current(),
    )
  }

  /** A target rejects a planned end that is not after its planned start. */
  @Test
  fun studyLogTarget_rejectsNonIncreasingPlannedTimes() {
    assertThrows(IllegalArgumentException::class.java) {
      StudyLogTarget(event, EventType.STUDY_SESSION, null, plannedStart, plannedStart)
    }
    assertThrows(IllegalArgumentException::class.java) {
      StudyLogTarget(event, EventType.STUDY_SESSION, null, plannedEnd, plannedStart)
    }
  }

  /** A blank subject is normalized to null when a record is first created. */
  @Test
  fun startInterval_storesBlankSubjectAsNull() = runTest {
    repository
        .startInterval(
            owner,
            StudyLogTarget(
                event,
                EventType.STUDY_SESSION,
                "   ",
                plannedStart,
                plannedEnd,
            ),
            start,
        )
        .getOrThrow()

    assertNull(current()?.subject)
  }

  /** Invalid starts reject duplicate, out-of-order, and over-limit intervals without mutation. */
  @Test
  fun startInterval_rejectsInvalidIntervalsWithoutChangingRecord() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    val beforeRunning = current()
    assertError(repository.startInterval(owner, target, resume), StudyLogError.ALREADY_STARTED)
    assertEquals(beforeRunning, current())
    repository.stopInterval(owner, event, end).getOrThrow()
    val beforeOutOfOrder = current()
    assertError(repository.startInterval(owner, target, start), StudyLogError.INVALID_ARGUMENT)
    assertEquals(beforeOutOfOrder, current())
    repository.startInterval(owner, target, end).getOrThrow()
    repository.stopInterval(owner, event, resumeEnd).getOrThrow()
    repeat(48) {
      val intervalStart = resumeEnd.plusSeconds((it + 1).toLong())
      val intervalEnd = intervalStart.plusSeconds(1)
      repository.startInterval(owner, target, intervalStart).getOrThrow()
      repository.stopInterval(owner, event, intervalEnd).getOrThrow()
    }
    val beforeLimit = current()
    assertError(
        repository.startInterval(owner, target, resumeEnd.plusSeconds(100)),
        StudyLogError.INVALID_ARGUMENT,
    )
    assertEquals(beforeLimit, current())
  }

  /** Stopping closes the open interval and rejects missing, closed, and invalid stops. */
  @Test
  fun stopInterval_closesAndRejectsInvalidStopsWithoutChangingRecord() = runTest {
    assertError(repository.stopInterval(owner, event, end), StudyLogError.NOT_STARTED)
    assertNull(current())
    repository.startInterval(owner, target, start).getOrThrow()
    val beforeInvalid = current()
    assertError(repository.stopInterval(owner, event, start), StudyLogError.INVALID_ARGUMENT)
    assertEquals(beforeInvalid, current())
    repository.stopInterval(owner, event, end).getOrThrow()
    assertEquals(listOf(StudyInterval(start, end)), current()?.intervals)
    val beforeClosed = current()
    assertError(repository.stopInterval(owner, event, resumeEnd), StudyLogError.NOT_STARTED)
    assertEquals(beforeClosed, current())
  }

  /** Pausing and resuming preserves both intervals and sums their durations. */
  @Test
  fun startStopStartStop_recordsTwoIntervals() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    repository.stopInterval(owner, event, end).getOrThrow()
    repository.startInterval(owner, target, resume).getOrThrow()
    repository.stopInterval(owner, event, resumeEnd).getOrThrow()

    assertEquals(
        listOf(StudyInterval(start, end), StudyInterval(resume, resumeEnd)),
        current()?.intervals,
    )
    assertEquals(3280L, current()?.studiedSeconds)
  }

  /** Comments are normalized, replace prior text, and obey the UTF-16 length limit. */
  @Test
  fun saveComment_normalizesReplacesAndEnforcesLength() = runTest {
    repository.saveComment(owner, target, "  first  ").getOrThrow()
    assertEquals("first", current()?.comment)
    repository.saveComment(owner, target, " second ").getOrThrow()
    assertEquals("second", current()?.comment)
    repository.saveComment(owner, target, null).getOrThrow()
    assertNull(current()?.comment)
    repository.saveComment(owner, target, "   ").getOrThrow()
    assertNull(current()?.comment)

    val accented = "é".repeat(StudyLogLimits.COMMENT)
    repository.saveComment(owner, target, accented).getOrThrow()
    assertEquals(accented, current()?.comment)
    val emojiText = "😀".repeat(StudyLogLimits.COMMENT / 2)
    repository.saveComment(owner, target, emojiText).getOrThrow()
    assertEquals(StudyLogLimits.COMMENT, emojiText.length)
    val tooLong = emojiText + "😀"
    assertEquals(StudyLogLimits.COMMENT + 2, tooLong.length)
    assertError(repository.saveComment(owner, target, tooLong), StudyLogError.INVALID_ARGUMENT)
    assertEquals(
        StudyLog(
            event,
            EventType.STUDY_SESSION,
            "Analysis I",
            plannedStart,
            plannedEnd,
            comment = emojiText,
        ),
        current(),
    )
  }

  /** A 501-character ASCII comment is rejected without changing the existing record. */
  @Test
  fun saveComment_rejectsOneCharacterOverLimitWithoutChangingRecord() = runTest {
    repository.saveComment(owner, target, "existing").getOrThrow()
    val before = current()

    assertError(
        repository.saveComment(owner, target, "a".repeat(StudyLogLimits.COMMENT + 1)),
        StudyLogError.INVALID_ARGUMENT,
    )
    assertEquals(before, current())
  }

  /** Comments can create a record, and removing one from a missing record creates nothing. */
  @Test
  fun saveComment_handlesMissingRecordAndRemoval() = runTest {
    repository.saveComment(owner, target, null).getOrThrow()
    assertNull(current())
    repository.saveComment(owner, target, " note ").getOrThrow()
    assertEquals("note", current()?.comment)
  }

  /**
   * Comment changes preserve interval and task snapshots, and interval changes preserve comments.
   */
  @Test
  fun saveComment_andIntervals_preserveOtherFields() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    val tasks = listOf(EventTask("Read chapter", end), EventTask("Solve exercises"))
    repository.saveTasks(owner, target, tasks).getOrThrow()
    repository.saveComment(owner, target, "note").getOrThrow()
    repository.stopInterval(owner, event, end).getOrThrow()
    assertEquals("note", current()?.comment)
    assertEquals(tasks, current()?.tasks)
    assertEquals(listOf(StudyInterval(start, end)), current()?.intervals)
  }

  /** Saving tasks replaces the complete snapshot and preserves unrelated fields. */
  @Test
  fun saveTasks_replacesSnapshotAndPreservesOtherFields() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    repository.saveComment(owner, target, "note").getOrThrow()
    val first = listOf(EventTask("Read chapter", end), EventTask("Solve exercises"))
    repository.saveTasks(owner, target, first).getOrThrow()
    assertEquals(first, current()?.tasks)
    val second = listOf(EventTask("Review notes", resumeEnd))
    repository.saveTasks(owner, target, second).getOrThrow()
    assertEquals(second, current()?.tasks)
    assertEquals("note", current()?.comment)
    assertEquals(listOf(StudyInterval(start)), current()?.intervals)
  }

  /** Exactly the maximum number of tasks is accepted and stored. */
  @Test
  fun saveTasks_acceptsMaximumTaskCount() = runTest {
    val tasks = List(StudyLogLimits.MAX_TASKS) { EventTask("Task $it") }
    repository.saveTasks(owner, target, tasks).getOrThrow()
    assertEquals(tasks, current()?.tasks)
  }

  /** Exactly the task text length limit is accepted and stored. */
  @Test
  fun saveTasks_acceptsMaximumTaskTextLength() = runTest {
    val task = EventTask("a".repeat(EventTextLimits.TASK))
    repository.saveTasks(owner, target, listOf(task)).getOrThrow()
    assertEquals(listOf(task), current()?.tasks)
  }

  /** An empty task save on a missing event does not create a study log. */
  @Test
  fun saveTasks_emptyOnMissingRecordCreatesNothing() = runTest {
    repository.saveTasks(owner, target, emptyList()).getOrThrow()
    assertNull(current())
  }

  /** Oversized task snapshots reject atomically by count and text length. */
  @Test
  fun saveTasks_rejectsOversizedInputWithoutChangingRecord() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    repository.saveComment(owner, target, "note").getOrThrow()
    val before = current()
    val tooMany = List(51) { EventTask("Task $it") }
    assertError(repository.saveTasks(owner, target, tooMany), StudyLogError.INVALID_ARGUMENT)
    assertEquals(before, current())
    val tooLong = listOf(EventTask("a".repeat(201)))
    assertError(repository.saveTasks(owner, target, tooLong), StudyLogError.INVALID_ARGUMENT)
    assertEquals(before, current())
  }

  /** Comment and task writes retain the planned times frozen in an existing log. */
  @Test
  fun writes_keepExistingPlannedTimes() = runTest {
    repository.saveComment(owner, target, "initial").getOrThrow()
    val changedTarget =
        StudyLogTarget(
            event,
            EventType.COURSE,
            "Edited",
            Instant.parse("2026-12-01T13:00:00Z"),
            Instant.parse("2026-12-01T14:00:00Z"),
        )
    repository.saveComment(owner, changedTarget, "updated").getOrThrow()
    assertEquals(plannedStart, current()?.plannedStart)
    assertEquals(plannedEnd, current()?.plannedEnd)
    repository.saveTasks(owner, changedTarget, listOf(EventTask("Task"))).getOrThrow()
    assertEquals(plannedStart, current()?.plannedStart)
    assertEquals(plannedEnd, current()?.plannedEnd)
  }

  /** Deleting an existing or missing record succeeds, and an existing observer sees null. */
  @Test
  fun deleteLog_removesExistingAndAcceptsMissing() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    repository.deleteLog(owner, event).getOrThrow()
    assertNull(current())
    repository.deleteLog(owner, event).getOrThrow()
    assertNull(current())
  }

  /** Blank or slash-containing paths fail consistently in every repository operation. */
  @Test
  fun operations_rejectInvalidOwnerAndEventIds() = runTest {
    assertError(repository.observeLog(" ", event).first(), StudyLogError.INVALID_ARGUMENT)
    assertError(
        repository.observeLog(owner, "event/id").first(),
        StudyLogError.INVALID_ARGUMENT,
    )
    assertError(repository.startInterval(" ", target, start), StudyLogError.INVALID_ARGUMENT)
    assertError(
        repository.startInterval(
            owner,
            StudyLogTarget(
                "event/id",
                EventType.STUDY_SESSION,
                null,
                plannedStart,
                plannedEnd,
            ),
            start,
        ),
        StudyLogError.INVALID_ARGUMENT,
    )
    assertError(repository.stopInterval("owner/id", event, end), StudyLogError.INVALID_ARGUMENT)
    assertError(repository.stopInterval(owner, " ", end), StudyLogError.INVALID_ARGUMENT)
    assertError(repository.saveComment("owner/id", target, "x"), StudyLogError.INVALID_ARGUMENT)
    assertError(
        repository.saveComment(
            owner,
            StudyLogTarget(
                "event/id",
                EventType.STUDY_SESSION,
                null,
                plannedStart,
                plannedEnd,
            ),
            "x",
        ),
        StudyLogError.INVALID_ARGUMENT,
    )
    assertError(repository.saveTasks(" ", target, emptyList()), StudyLogError.INVALID_ARGUMENT)
    assertError(
        repository.saveTasks(
            owner,
            StudyLogTarget(
                "event/id",
                EventType.STUDY_SESSION,
                null,
                plannedStart,
                plannedEnd,
            ),
            emptyList(),
        ),
        StudyLogError.INVALID_ARGUMENT,
    )
    assertError(repository.deleteLog("owner/id", event), StudyLogError.INVALID_ARGUMENT)
    assertError(repository.deleteLog(owner, "event/id"), StudyLogError.INVALID_ARGUMENT)
  }

  /** Records are isolated by both owner and event identifiers. */
  @Test
  fun records_areIsolatedPerOwnerAndEvent() = runTest {
    repository.saveComment(owner, target, "one").getOrThrow()
    repository
        .saveTasks(
            otherOwner,
            StudyLogTarget(
                event,
                EventType.COURSE,
                "Physics",
                plannedStart,
                plannedEnd,
            ),
            listOf(EventTask("Task")),
        )
        .getOrThrow()
    repository
        .startInterval(
            owner,
            StudyLogTarget(otherEvent, EventType.EXAM, null, plannedStart, plannedEnd),
            start,
        )
        .getOrThrow()

    assertEquals("one", current(owner, event)?.comment)
    assertEquals(listOf(EventTask("Task")), current(otherOwner, event)?.tasks)
    assertEquals(EventType.EXAM, current(owner, otherEvent)?.eventType)
    assertNull(current(otherOwner, otherEvent))
  }
}
