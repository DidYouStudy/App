// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import java.util.Locale

/**
 * Single decision on whether a specific subject should be tracked.
 *
 * @property spelling the latest display name for the subject.
 * @property tracked true if the student wants progress tracked for this subject.
 * @property priority how important the student rates this subject, or null until one is saved. It
 *   is kept when the subject is untracked, so tracking it again restores the earlier rating.
 */
data class SubjectDecision(
    val spelling: String,
    val tracked: Boolean,
    val priority: SubjectPriority? = null,
)

/**
 * Immutable collection of subject tracking decisions for one student.
 *
 * @property decisions map of normalized subject key to its [SubjectDecision].
 */
data class SubjectDecisions(
    val decisions: Map<String, SubjectDecision> = emptyMap(),
) {

  /**
   * Returns true if [subject] is non-null and has a stored decision with [SubjectDecision.tracked]
   * set to true.
   *
   * Subject comparison ignores case and surrounding whitespace.
   */
  fun isTracked(subject: String?): Boolean {
    if (subject == null) return false
    val key = SubjectCatalogFirestoreMapper.key(subject)
    return decisions[key]?.tracked == true
  }

  /**
   * Returns true if a decision has been recorded for [subject].
   *
   * Subject comparison ignores case and surrounding whitespace.
   */
  fun isDecided(subject: String): Boolean {
    val key = SubjectCatalogFirestoreMapper.key(subject)
    return decisions.containsKey(key)
  }

  /**
   * Returns the saved priority of [subject], or null if none was saved or no decision exists.
   *
   * Subject comparison ignores case and surrounding whitespace. The priority is returned even for
   * an untracked subject; callers that only show priorities for tracked subjects should combine it
   * with [isTracked].
   */
  fun priorityOf(subject: String): SubjectPriority? =
      decisions[SubjectCatalogFirestoreMapper.key(subject)]?.priority

  /** Number of subjects the student marked as tracked. */
  val trackedCount: Int
    get() = decisions.values.count { it.tracked }

  /** Number of subjects with a recorded decision, tracked or not. */
  val decidedCount: Int
    get() = decisions.size

  /**
   * Returns subjects from [subjects] for which no decision exists, keeping the first occurrence per
   * subject key.
   */
  fun undecided(subjects: Collection<String>): List<String> {
    val seenKeys = mutableSetOf<String>()
    val result = mutableListOf<String>()
    for (s in subjects) {
      val key = SubjectCatalogFirestoreMapper.key(s)
      if (seenKeys.add(key) && !isDecided(s)) {
        result.add(s)
      }
    }
    return result
  }

  /** List of spellings for all tracked subjects, sorted ignoring case. */
  val trackedSubjects: List<String>
    get() =
        decisions.values
            .filter { it.tracked }
            .map { it.spelling }
            .sortedWith(compareBy { it.lowercase(Locale.ROOT) })
}
