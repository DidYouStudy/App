// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import com.android.sample.model.event.EventType
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  private val target = StudyLogTarget(event, EventType.STUDY_SESSION, " Analysis I ")
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

  /** Starting a missing record stores normalized target metadata and one open interval. */
  @Test
  fun startInterval_createsRecordAndKeepsMetadataOnLaterStarts() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    assertEquals(
        StudyLog(event, EventType.STUDY_SESSION, "Analysis I", listOf(StudyInterval(start))),
        current(),
    )
    repository.stopInterval(owner, event, end).getOrThrow()
    repository
        .startInterval(owner, StudyLogTarget(event, EventType.COURSE, " Edited "), end)
        .getOrThrow()
    assertEquals(EventType.STUDY_SESSION, current()?.eventType)
    assertEquals("Analysis I", current()?.subject)
    assertEquals(listOf(StudyInterval(start, end), StudyInterval(end)), current()?.intervals)
  }

  /** A blank subject is normalized to null when a record is first created. */
  @Test
  fun startInterval_storesBlankSubjectAsNull() = runTest {
    repository
        .startInterval(owner, StudyLogTarget(event, EventType.STUDY_SESSION, "   "), start)
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
        StudyLog(event, EventType.STUDY_SESSION, "Analysis I", comment = emojiText),
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

  /** Comment changes preserve interval and task data, and interval changes preserve comments. */
  @Test
  fun saveComment_andIntervals_preserveOtherFields() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    repository.saveTasksCompleted(owner, target, 2).getOrThrow()
    repository.saveComment(owner, target, "note").getOrThrow()
    repository.stopInterval(owner, event, end).getOrThrow()
    assertEquals("note", current()?.comment)
    assertEquals(2, current()?.tasksCompleted)
    assertEquals(listOf(StudyInterval(start, end)), current()?.intervals)
  }

  /** Task counts accept zero and null, while negative counts leave the full record unchanged. */
  @Test
  fun saveTasksCompleted_validatesAndPreservesOtherFields() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    repository.saveComment(owner, target, "note").getOrThrow()
    repository.saveTasksCompleted(owner, target, 0).getOrThrow()
    assertEquals(0, current()?.tasksCompleted)
    repository.saveTasksCompleted(owner, target, null).getOrThrow()
    assertNull(current()?.tasksCompleted)
    val before = current()
    assertError(repository.saveTasksCompleted(owner, target, -1), StudyLogError.INVALID_ARGUMENT)
    assertEquals(before, current())
    assertEquals("note", current()?.comment)
    assertEquals(listOf(StudyInterval(start)), current()?.intervals)
  }

  /** Removing a missing task count creates nothing, while a value creates the record. */
  @Test
  fun saveTasksCompleted_nullOnMissingRecordCreatesNothing() = runTest {
    repository.saveTasksCompleted(owner, target, null).getOrThrow()
    assertNull(current())

    repository.saveTasksCompleted(owner, target, 2).getOrThrow()
    assertEquals(2, current()?.tasksCompleted)
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
            StudyLogTarget("event/id", EventType.STUDY_SESSION, null),
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
            StudyLogTarget("event/id", EventType.STUDY_SESSION, null),
            "x",
        ),
        StudyLogError.INVALID_ARGUMENT,
    )
    assertError(repository.saveTasksCompleted(" ", target, 1), StudyLogError.INVALID_ARGUMENT)
    assertError(
        repository.saveTasksCompleted(
            owner,
            StudyLogTarget("event/id", EventType.STUDY_SESSION, null),
            1,
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
        .saveTasksCompleted(otherOwner, StudyLogTarget(event, EventType.COURSE, "Physics"), 3)
        .getOrThrow()
    repository
        .startInterval(owner, StudyLogTarget(otherEvent, EventType.EXAM, null), start)
        .getOrThrow()

    assertEquals("one", current(owner, event)?.comment)
    assertEquals(3, current(otherOwner, event)?.tasksCompleted)
    assertEquals(EventType.EXAM, current(owner, otherEvent)?.eventType)
    assertNull(current(otherOwner, otherEvent))
  }
}
