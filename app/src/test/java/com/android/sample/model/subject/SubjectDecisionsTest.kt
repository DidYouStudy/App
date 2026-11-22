// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectDecisionsTest {

  private fun decisions(vararg entries: Pair<String, Boolean>): SubjectDecisions {
    val map = entries.associate { (spelling, tracked) ->
      SubjectCatalogFirestoreMapper.key(spelling) to SubjectDecision(spelling, tracked)
    }
    return SubjectDecisions(map)
  }

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

  @Test
  fun isDecided_returnsTrueForDecidedSubjectsIgnoringCaseAndSpaces() {
    val d = decisions("Analysis I" to false)

    assertTrue(d.isDecided("Analysis I"))
    assertTrue(d.isDecided(" analysis i "))
    assertFalse(d.isDecided("Chemistry"))
  }

  @Test
  fun undecided_filtersDecidedAndDeduplicatesPreservingOrder() {
    val d = decisions("Analysis I" to true)
    val input = listOf("Analysis I", "Physics", " physics ", "Chemistry", "analysis i")

    val result = d.undecided(input)

    assertEquals(listOf("Physics", "Chemistry"), result)
  }

  @Test
  fun trackedSubjects_returnsSortedTrackedSubjectSpellingsIgnoringCase() {
    val d =
        decisions("Physics" to true, "analysis" to true, "Algebra" to true, "Chemistry" to false)

    assertEquals(listOf("Algebra", "analysis", "Physics"), d.trackedSubjects)
  }
}
