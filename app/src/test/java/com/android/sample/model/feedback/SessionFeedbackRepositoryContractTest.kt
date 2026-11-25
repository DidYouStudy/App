// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

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

/** Behavior every [SessionFeedbackRepository] implementation must satisfy. */
abstract class SessionFeedbackRepositoryContractTest {
  protected abstract fun createRepository(): SessionFeedbackRepository

  private lateinit var repository: SessionFeedbackRepository
  private val owner = "owner-1"
  private val otherOwner = "owner-2"
  private val event = "event-1"
  private val otherEvent = "event-2"
  private val target = FeedbackTarget(event, EventType.STUDY_SESSION, " Analysis I ")
  private val start = Instant.parse("2026-11-02T13:07:30Z") // 14:07:30 Zurich local time
  private val end = Instant.parse("2026-11-02T13:50:00Z") // 14:50:00 Zurich local time
  private val resume = Instant.parse("2026-11-02T14:00:00Z") // 15:00:00 Zurich local time
  private val resumeEnd = Instant.parse("2026-11-02T14:12:10Z") // 15:12:10 Zurich local time

  @Before
  fun setUp() {
    repository = createRepository()
  }

  private fun Result<*>.error() = (exceptionOrNull() as FeedbackException).error

  private suspend fun current(ownerId: String = owner, eventId: String = event): SessionFeedback? =
      repository.observeFeedback(ownerId, eventId).first().getOrThrow()

  private fun assertError(result: Result<*>, error: FeedbackError) {
    assertEquals(error, result.error())
  }

  /** Observing an absent record emits null and each relevant mutation emits a new record. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observeFeedback_emitsInitialAndRelevantChangesOnly() = runTest {
    val own = mutableListOf<SessionFeedback?>()
    val otherEventValues = mutableListOf<SessionFeedback?>()
    val otherOwnerValues = mutableListOf<SessionFeedback?>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeFeedback(owner, event).collect { own += it.getOrThrow() }
    }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeFeedback(owner, otherEvent).collect { otherEventValues += it.getOrThrow() }
    }
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeFeedback(otherOwner, event).collect { otherOwnerValues += it.getOrThrow() }
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
        SessionFeedback(event, EventType.STUDY_SESSION, "Analysis I", listOf(StudyInterval(start))),
        current(),
    )
    repository.stopInterval(owner, event, end).getOrThrow()
    repository
        .startInterval(owner, FeedbackTarget(event, EventType.COURSE, " Edited "), end)
        .getOrThrow()
    assertEquals(EventType.STUDY_SESSION, current()?.eventType)
    assertEquals("Analysis I", current()?.subject)
    assertEquals(listOf(StudyInterval(start, end), StudyInterval(end)), current()?.intervals)
  }

  /** Invalid starts reject duplicate, out-of-order, and over-limit intervals without mutation. */
  @Test
  fun startInterval_rejectsInvalidIntervalsWithoutChangingRecord() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    val beforeRunning = current()
    assertError(repository.startInterval(owner, target, resume), FeedbackError.ALREADY_STARTED)
    assertEquals(beforeRunning, current())
    repository.stopInterval(owner, event, end).getOrThrow()
    val beforeOutOfOrder = current()
    assertError(repository.startInterval(owner, target, start), FeedbackError.INVALID_ARGUMENT)
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
        FeedbackError.INVALID_ARGUMENT,
    )
    assertEquals(beforeLimit, current())
  }

  /** Stopping closes the open interval and rejects missing, closed, and invalid stops. */
  @Test
  fun stopInterval_closesAndRejectsInvalidStopsWithoutChangingRecord() = runTest {
    assertError(repository.stopInterval(owner, event, end), FeedbackError.NOT_STARTED)
    assertNull(current())
    repository.startInterval(owner, target, start).getOrThrow()
    val beforeInvalid = current()
    assertError(repository.stopInterval(owner, event, start), FeedbackError.INVALID_ARGUMENT)
    assertEquals(beforeInvalid, current())
    repository.stopInterval(owner, event, end).getOrThrow()
    assertEquals(listOf(StudyInterval(start, end)), current()?.intervals)
    val beforeClosed = current()
    assertError(repository.stopInterval(owner, event, resumeEnd), FeedbackError.NOT_STARTED)
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

    val accented = "é".repeat(FeedbackLimits.COMMENT)
    repository.saveComment(owner, target, accented).getOrThrow()
    assertEquals(accented, current()?.comment)
    val emojiText = "😀".repeat(FeedbackLimits.COMMENT / 2)
    repository.saveComment(owner, target, emojiText).getOrThrow()
    assertEquals(FeedbackLimits.COMMENT, emojiText.length)
    val tooLong = emojiText + "😀"
    assertEquals(FeedbackLimits.COMMENT + 2, tooLong.length)
    assertError(repository.saveComment(owner, target, tooLong), FeedbackError.INVALID_ARGUMENT)
    assertEquals(
        SessionFeedback(event, EventType.STUDY_SESSION, "Analysis I", comment = emojiText),
        current(),
    )
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
    assertError(repository.saveTasksCompleted(owner, target, -1), FeedbackError.INVALID_ARGUMENT)
    assertEquals(before, current())
    assertEquals("note", current()?.comment)
    assertEquals(listOf(StudyInterval(start)), current()?.intervals)
  }

  /** Deleting an existing or missing record succeeds, and an existing observer sees null. */
  @Test
  fun deleteFeedback_removesExistingAndAcceptsMissing() = runTest {
    repository.startInterval(owner, target, start).getOrThrow()
    repository.deleteFeedback(owner, event).getOrThrow()
    assertNull(current())
    repository.deleteFeedback(owner, event).getOrThrow()
    assertNull(current())
  }

  /** Blank or slash-containing paths fail consistently in every repository operation. */
  @Test
  fun operations_rejectInvalidOwnerAndEventIds() = runTest {
    assertError(repository.observeFeedback(" ", event).first(), FeedbackError.INVALID_ARGUMENT)
    assertError(
        repository.observeFeedback(owner, "event/id").first(),
        FeedbackError.INVALID_ARGUMENT,
    )
    assertError(repository.startInterval(" ", target, start), FeedbackError.INVALID_ARGUMENT)
    assertError(
        repository.startInterval(
            owner,
            FeedbackTarget("event/id", EventType.STUDY_SESSION, null),
            start,
        ),
        FeedbackError.INVALID_ARGUMENT,
    )
    assertError(repository.stopInterval("owner/id", event, end), FeedbackError.INVALID_ARGUMENT)
    assertError(repository.stopInterval(owner, " ", end), FeedbackError.INVALID_ARGUMENT)
    assertError(repository.saveComment("owner/id", target, "x"), FeedbackError.INVALID_ARGUMENT)
    assertError(
        repository.saveComment(
            owner,
            FeedbackTarget("event/id", EventType.STUDY_SESSION, null),
            "x",
        ),
        FeedbackError.INVALID_ARGUMENT,
    )
    assertError(repository.saveTasksCompleted(" ", target, 1), FeedbackError.INVALID_ARGUMENT)
    assertError(
        repository.saveTasksCompleted(
            owner,
            FeedbackTarget("event/id", EventType.STUDY_SESSION, null),
            1,
        ),
        FeedbackError.INVALID_ARGUMENT,
    )
    assertError(repository.deleteFeedback("owner/id", event), FeedbackError.INVALID_ARGUMENT)
    assertError(repository.deleteFeedback(owner, "event/id"), FeedbackError.INVALID_ARGUMENT)
  }

  /** Records are isolated by both owner and event identifiers. */
  @Test
  fun records_areIsolatedPerOwnerAndEvent() = runTest {
    repository.saveComment(owner, target, "one").getOrThrow()
    repository
        .saveTasksCompleted(otherOwner, FeedbackTarget(event, EventType.COURSE, "Physics"), 3)
        .getOrThrow()
    repository
        .startInterval(owner, FeedbackTarget(otherEvent, EventType.EXAM, null), start)
        .getOrThrow()

    assertEquals("one", current(owner, event)?.comment)
    assertEquals(3, current(otherOwner, event)?.tasksCompleted)
    assertEquals(EventType.EXAM, current(owner, otherEvent)?.eventType)
    assertNull(current(otherOwner, otherEvent))
  }
}
