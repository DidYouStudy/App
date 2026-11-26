// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.subject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Unit tests for [SubjectPriority] construction and its bounds. */
class SubjectPriorityTest {

  /** The lowest and highest levels are accepted and keep their value. */
  @Test
  fun init_acceptsBoundaryValues() {
    assertEquals(SubjectPriority.MIN, SubjectPriority(SubjectPriority.MIN).value)
    assertEquals(SubjectPriority.MAX, SubjectPriority(SubjectPriority.MAX).value)
  }

  /** Every level strictly between the bounds is accepted. */
  @Test
  fun init_acceptsEveryLevelInRange() {
    for (level in SubjectPriority.RANGE) {
      assertEquals(level, SubjectPriority(level).value)
    }
  }

  /** Zero, negative values and values above the maximum are rejected. */
  @Test
  fun init_rejectsValuesOutsideRange() {
    val invalid = listOf(SubjectPriority.MIN - 1, 0, -1, Int.MIN_VALUE, SubjectPriority.MAX + 1)
    for (value in invalid) {
      assertThrows(IllegalArgumentException::class.java) { SubjectPriority(value) }
    }
  }

  /** The range spans exactly from [SubjectPriority.MIN] to [SubjectPriority.MAX]. */
  @Test
  fun range_spansMinToMax() {
    assertEquals(SubjectPriority.MIN, SubjectPriority.RANGE.first)
    assertEquals(SubjectPriority.MAX, SubjectPriority.RANGE.last)
  }

  /** [SubjectPriority.ALL] lists one priority per level in ascending order. */
  @Test
  fun all_listsEveryLevelAscending() {
    assertEquals(SubjectPriority.RANGE.toList(), SubjectPriority.ALL.map { it.value })
  }

  /** Two priorities with the same level are equal. */
  @Test
  fun equality_isByLevel() {
    assertEquals(SubjectPriority(3), SubjectPriority(3))
  }
}
