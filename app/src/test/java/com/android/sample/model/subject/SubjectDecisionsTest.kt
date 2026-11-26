// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [SubjectDecisions] container methods and matching logic. */
class SubjectDecisionsTest {

  private fun decisions(vararg entries: Pair<String, Boolean>): SubjectDecisions {
    val map = entries.associate { (spelling, tracked) ->
      SubjectCatalogFirestoreMapper.key(spelling) to SubjectDecision(spelling, tracked)
    }
    return SubjectDecisions(map)
  }

  /**
   * A subject is tracked only when it is decided and its flag is true, ignoring case/whitespace.
   */
  @Test
  fun isTracked_returnsTrueOnlyWhenDecidedAndTracked() {
    val d = decisions("Analysis I" to true, "Physics" to false)

    assertFalse(d.isTracked(null))
    assertFalse(d.isTracked("Chemistry"))
    assertFalse(d.isTracked("Physics"))
    assertTrue(d.isTracked("Analysis I"))
    assertTrue(d.isTracked(" analysis i "))
    assertTrue(d.isTracked("ANALYSIS I"))
  }

  /** Checks whether a decision exists for a given subject ignoring case and surrounding spaces. */
  @Test
  fun isDecided_returnsTrueForDecidedSubjectsIgnoringCaseAndSpaces() {
    val d = decisions("Analysis I" to false)

    assertTrue(d.isDecided("Analysis I"))
    assertTrue(d.isDecided(" analysis i "))
    assertFalse(d.isDecided("Chemistry"))
  }

  /**
   * Filters out decided subjects and returns undecided ones preserving order of first appearance.
   */
  @Test
  fun undecided_filtersDecidedAndDeduplicatesPreservingOrder() {
    val d = decisions("Analysis I" to true)
    val input = listOf("Analysis I", "Physics", " physics ", "Chemistry", "analysis i")

    val result = d.undecided(input)

    assertEquals(listOf("Physics", "Chemistry"), result)
  }

  /** Returns list of spellings for tracked subjects sorted in case-insensitive order. */
  @Test
  fun trackedSubjects_returnsSortedTrackedSubjectSpellingsIgnoringCase() {
    val d =
        decisions("Physics" to true, "analysis" to true, "Algebra" to true, "Chemistry" to false)

    assertEquals(listOf("Algebra", "analysis", "Physics"), d.trackedSubjects)
  }

  private fun withPriority(spelling: String, tracked: Boolean, priority: SubjectPriority?) =
      SubjectCatalogFirestoreMapper.key(spelling) to SubjectDecision(spelling, tracked, priority)

  /** A decision has no priority until one is saved. */
  @Test
  fun decision_priorityDefaultsToNull() {
    assertNull(SubjectDecision("Physics", true).priority)
  }

  /** The priority is returned for a subject ignoring case and surrounding whitespace. */
  @Test
  fun priorityOf_returnsSavedPriorityIgnoringCaseAndSpaces() {
    val d = SubjectDecisions(mapOf(withPriority("Analysis I", true, SubjectPriority(4))))

    assertEquals(SubjectPriority(4), d.priorityOf("Analysis I"))
    assertEquals(SubjectPriority(4), d.priorityOf(" ANALYSIS i "))
  }

  /** The priority is null when none was saved or when the subject has no decision at all. */
  @Test
  fun priorityOf_returnsNullWithoutPriorityOrDecision() {
    val d = decisions("Analysis I" to true)

    assertNull(d.priorityOf("Analysis I"))
    assertNull(d.priorityOf("Chemistry"))
  }

  /** An untracked subject keeps its saved priority, so tracking it again restores the rating. */
  @Test
  fun priorityOf_isKeptForUntrackedSubject() {
    val d = SubjectDecisions(mapOf(withPriority("Physics", false, SubjectPriority(2))))

    assertFalse(d.isTracked("Physics"))
    assertEquals(SubjectPriority(2), d.priorityOf("Physics"))
  }

  /** The tracked count only includes subjects whose flag is true. */
  @Test
  fun trackedCount_countsOnlyTrackedSubjects() {
    val d = decisions("Analysis I" to true, "Physics" to false, "Algebra" to true)

    assertEquals(2, d.trackedCount)
  }

  /** The decided count includes tracked and untracked subjects alike. */
  @Test
  fun decidedCount_countsEveryDecision() {
    val d = decisions("Analysis I" to true, "Physics" to false, "Algebra" to true)

    assertEquals(3, d.decidedCount)
  }

  /** An empty container has no tracked and no decided subjects. */
  @Test
  fun counts_areZeroWhenEmpty() {
    val d = SubjectDecisions()

    assertEquals(0, d.trackedCount)
    assertEquals(0, d.decidedCount)
  }
}
