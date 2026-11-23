// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
   * INVALID_ARGUMENT.
   */
  @Test
  fun saveDecisions_rejectsInvalidArguments() = runTest {
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
}
