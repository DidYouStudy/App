// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.subject

/**
 * How important a tracked subject is to the student, from [MIN] (least) to [MAX] (most).
 *
 * The scheduler can use it to decide which subjects deserve more study time. Construction fails for
 * values outside [RANGE], so an invalid priority can never reach a repository.
 *
 * @property value the priority level, within [RANGE].
 * @throws IllegalArgumentException if [value] is outside [RANGE].
 */
data class SubjectPriority(val value: Int) {

  init {
    require(value in RANGE)
  }

  companion object {
    /** Lowest accepted priority level. */
    const val MIN = 1

    /** Highest accepted priority level; the preferences screen shows it as the "of N" total. */
    const val MAX = 5

    /** All accepted priority levels. */
    val RANGE: IntRange = MIN..MAX

    /**
     * Every selectable priority in ascending order, so a screen can render one choice per level
     * without knowing the bounds.
     */
    val ALL: List<SubjectPriority> = RANGE.map(::SubjectPriority)
  }
}
