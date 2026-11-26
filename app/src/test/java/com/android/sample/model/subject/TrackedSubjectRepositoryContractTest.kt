// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Behavior every [TrackedSubjectRepository] implementation must satisfy. */
abstract class TrackedSubjectRepositoryContractTest {

  protected abstract fun createRepository(): TrackedSubjectRepository

  private lateinit var repository: TrackedSubjectRepository
  private val owner = "user-1"
  private val otherOwner = "user-2"

  @Before
  fun setUp() {
    repository = createRepository()
  }

  private fun Result<*>.error() = (exceptionOrNull() as TrackedSubjectException).error

  private suspend fun current(ownerId: String = owner): SubjectDecisions =
      repository.observeDecisions(ownerId).first().getOrThrow()

  /** An uninitialized user returns an empty [SubjectDecisions] container. */
  @Test
  fun observeDecisions_emitsEmptyInitially() = runTest {
    val decisions = current()
    assertTrue(decisions.decisions.isEmpty())
  }

  /**
   * Saving decisions merges new entries and overwrites existing key flags with trimmed spelling.
   */
  @Test
  fun saveDecisions_savesAndMergesChoices() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true, "Physics" to false)).getOrThrow()

    val d1 = current()
    assertTrue(d1.isTracked("Analysis I"))
    assertFalse(d1.isTracked("Physics"))
    assertTrue(d1.isDecided("Physics"))

    // Overwrite Physics spelling & tracking flag, keep Analysis I decision
    repository.saveDecisions(owner, mapOf(" physics " to true)).getOrThrow()

    val d2 = current()
    assertTrue(d2.isTracked("Analysis I"))
    assertTrue(d2.isTracked("Physics"))
    assertEquals(listOf("Analysis I", "physics"), d2.trackedSubjects)
  }

  /** Passing an empty map to saveDecisions succeeds without modifying existing decisions. */
  @Test
  fun saveDecisions_emptyMapSucceedsWithoutChanges() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    repository.saveDecisions(owner, emptyMap()).getOrThrow()

    assertTrue(current().isTracked("Analysis I"))
  }

  /**
   * Blank owner IDs, blank spellings, or duplicate normalized keys are rejected with
   * INVALID_ARGUMENT and nothing is written.
   */
  @Test
  fun saveDecisions_rejectsInvalidArguments() = runTest {
    repository.saveDecisions(owner, mapOf("Physics" to false)).getOrThrow()

    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.saveDecisions(" ", mapOf("Analysis" to true)).error(),
    )
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.saveDecisions("owner/with/slash", mapOf("Analysis" to true)).error(),
    )
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.saveDecisions(owner, mapOf(" " to true)).error(),
    )
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository
            .saveDecisions(owner, mapOf("Analysis I" to true, " analysis i " to false))
            .error(),
    )

    assertEquals(setOf("Physics"), current().decisions.values.map { it.spelling }.toSet())
  }

  /** Observing decisions with a blank owner ID or invalid path fails with INVALID_ARGUMENT. */
  @Test
  fun observeDecisions_rejectsBlankOwnerId() = runTest {
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.observeDecisions(" ").first().error(),
    )
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.observeDecisions("user/invalid").first().error(),
    )
  }

  /** Subject tracking decisions are strictly isolated per owner ID. */
  @Test
  fun decisions_areIsolatedPerOwner() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    repository.saveDecisions(otherOwner, mapOf("Physics" to true)).getOrThrow()

    assertTrue(current(owner).isTracked("Analysis I"))
    assertFalse(current(owner).isTracked("Physics"))

    assertTrue(current(otherOwner).isTracked("Physics"))
    assertFalse(current(otherOwner).isTracked("Analysis I"))
  }

  /** The decisions flow re-emits updated data whenever decisions change for the owner. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun observeDecisions_reEmitsOnEveryChange() = runTest {
    val emissions = mutableListOf<SubjectDecisions>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeDecisions(owner).collect { emissions += it.getOrThrow() }
    }

    repository.saveDecisions(owner, mapOf("Analysis" to true)).getOrThrow()
    repository.saveDecisions(owner, mapOf("Physics" to false)).getOrThrow()

    assertEquals(3, emissions.size)
    assertTrue(emissions[1].isTracked("Analysis"))
    assertTrue(emissions[2].isDecided("Physics"))
  }

  /** The priority stays null until it is saved, and tracking a subject does not set one. */
  @Test
  fun priority_isNullUntilSaved() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()

    assertNull(current().priorityOf("Analysis I"))
  }

  /** After saving, observing the decisions returns the saved priority. */
  @Test
  fun savePriority_isReturnedWhenObserving() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()

    repository.savePriority(owner, "Analysis I", 4).getOrThrow()

    assertEquals(SubjectPriority(4), current().priorityOf("Analysis I"))
  }

  /** The lowest and the highest levels are accepted. */
  @Test
  fun savePriority_acceptsBoundaryLevels() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()

    repository.savePriority(owner, "Analysis I", SubjectPriority.MIN).getOrThrow()
    assertEquals(SubjectPriority(SubjectPriority.MIN), current().priorityOf("Analysis I"))

    repository.savePriority(owner, "Analysis I", SubjectPriority.MAX).getOrThrow()
    assertEquals(SubjectPriority(SubjectPriority.MAX), current().priorityOf("Analysis I"))
  }

  /** Zero, negative and too-high levels fail with INVALID_ARGUMENT and change nothing. */
  @Test
  fun savePriority_rejectsOutOfRangeLevelsWithoutWriting() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    repository.savePriority(owner, "Analysis I", 3).getOrThrow()

    val invalid = listOf(SubjectPriority.MIN - 1, 0, -1, Int.MIN_VALUE, SubjectPriority.MAX + 1)
    for (level in invalid) {
      assertEquals(
          TrackedSubjectError.INVALID_ARGUMENT,
          repository.savePriority(owner, "Analysis I", level).error(),
      )
    }

    assertEquals(SubjectPriority(3), current().priorityOf("Analysis I"))
  }

  /** An invalid level leaves the decisions untouched when no priority was saved before. */
  @Test
  fun savePriority_rejectedLevelKeepsPriorityNull() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    val before = current()

    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.savePriority(owner, "Analysis I", 0).error(),
    )

    assertEquals(before, current())
  }

  /** Saving a priority changes neither the tracked flag nor the stored spelling. */
  @Test
  fun savePriority_doesNotChangeTrackedFlagOrSpelling() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true, "Physics" to true)).getOrThrow()

    repository.savePriority(owner, "Analysis I", 2).getOrThrow()

    val decisions = current()
    assertTrue(decisions.isTracked("Analysis I"))
    assertTrue(decisions.isTracked("Physics"))
    assertEquals(listOf("Analysis I", "Physics"), decisions.trackedSubjects)
    assertNull(decisions.priorityOf("Physics"))
  }

  /** Saving again replaces the earlier priority. */
  @Test
  fun savePriority_replacesEarlierPriority() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()

    repository.savePriority(owner, "Analysis I", 2).getOrThrow()
    repository.savePriority(owner, "Analysis I", 5).getOrThrow()

    assertEquals(SubjectPriority(5), current().priorityOf("Analysis I"))
  }

  /** A subject explicitly marked as not tracked has no priority and the save fails. */
  @Test
  fun savePriority_failsForUntrackedSubject() = runTest {
    repository.saveDecisions(owner, mapOf("Physics" to false)).getOrThrow()
    val before = current()

    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.savePriority(owner, "Physics", 3).error(),
    )

    assertEquals(before, current())
  }

  /** A subject without any decision, such as a custom subject, fails like an untracked one. */
  @Test
  fun savePriority_failsForSubjectWithoutDecision() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    val before = current()

    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.savePriority(owner, "Custom subject", 3).error(),
    )
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.savePriority("user-without-data", "Analysis I", 3).error(),
    )

    assertEquals(before, current())
  }

  /** Blank or malformed owner IDs and blank subjects fail with INVALID_ARGUMENT. */
  @Test
  fun savePriority_rejectsInvalidArguments() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    val before = current()

    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.savePriority(" ", "Analysis I", 3).error(),
    )
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.savePriority("owner/with/slash", "Analysis I", 3).error(),
    )
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.savePriority(owner, " ", 3).error(),
    )

    assertEquals(before, current())
  }

  /** Spelling and case variants of a subject address the same entry. */
  @Test
  fun savePriority_matchesSubjectIgnoringCaseAndSpaces() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()

    repository.savePriority(owner, " analysis i ", 4).getOrThrow()

    val decisions = current()
    assertEquals(SubjectPriority(4), decisions.priorityOf("Analysis I"))
    assertEquals(listOf("Analysis I"), decisions.trackedSubjects)
  }

  /** Untracking and tracking again, or changing the spelling, keeps the saved priority. */
  @Test
  fun saveDecisions_keepsSavedPriority() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    repository.savePriority(owner, "Analysis I", 4).getOrThrow()

    repository.saveDecisions(owner, mapOf("Analysis I" to false)).getOrThrow()
    assertFalse(current().isTracked("Analysis I"))
    assertEquals(SubjectPriority(4), current().priorityOf("Analysis I"))

    repository.saveDecisions(owner, mapOf(" analysis i " to true)).getOrThrow()
    assertTrue(current().isTracked("Analysis I"))
    assertEquals(SubjectPriority(4), current().priorityOf("Analysis I"))
    assertEquals(listOf("analysis i"), current().trackedSubjects)
  }

  /** A subject that was untracked can no longer be rated until it is tracked again. */
  @Test
  fun savePriority_failsAfterUntracking() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    repository.savePriority(owner, "Analysis I", 4).getOrThrow()
    repository.saveDecisions(owner, mapOf("Analysis I" to false)).getOrThrow()

    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        repository.savePriority(owner, "Analysis I", 1).error(),
    )
    assertEquals(SubjectPriority(4), current().priorityOf("Analysis I"))
  }

  /** Priorities are isolated per owner ID. */
  @Test
  fun savePriority_isIsolatedPerOwner() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    repository.saveDecisions(otherOwner, mapOf("Analysis I" to true)).getOrThrow()

    repository.savePriority(owner, "Analysis I", 5).getOrThrow()

    assertEquals(SubjectPriority(5), current(owner).priorityOf("Analysis I"))
    assertNull(current(otherOwner).priorityOf("Analysis I"))
  }

  /** Saving a priority emits an updated value to observers. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun savePriority_reEmitsToObservers() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    val emissions = mutableListOf<SubjectDecisions>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeDecisions(owner).collect { emissions += it.getOrThrow() }
    }

    repository.savePriority(owner, "Analysis I", 3).getOrThrow()

    assertEquals(2, emissions.size)
    assertNull(emissions[0].priorityOf("Analysis I"))
    assertEquals(SubjectPriority(3), emissions[1].priorityOf("Analysis I"))
  }

  /** Saving the same priority twice does not emit a duplicate value. */
  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun savePriority_sameValueDoesNotReEmit() = runTest {
    repository.saveDecisions(owner, mapOf("Analysis I" to true)).getOrThrow()
    repository.savePriority(owner, "Analysis I", 3).getOrThrow()
    val emissions = mutableListOf<SubjectDecisions>()
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      repository.observeDecisions(owner).collect { emissions += it.getOrThrow() }
    }

    repository.savePriority(owner, "Analysis I", 3).getOrThrow()

    assertEquals(1, emissions.size)
  }
}
