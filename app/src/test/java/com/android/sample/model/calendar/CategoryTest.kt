// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Tests validation of user-created categories without a predefined enum or storage dependency. */
class CategoryTest {
  /** Users can rename an arbitrary category without changing its identity or owner. */
  @Test
  fun rename_keepsStableIdentityAndOwner() {
    val category = Category(id = "category-1", ownerId = "user-1", name = "Analysis")
    val renamed = category.copy(name = "Analysis II")

    assertEquals("Analysis II", renamed.name)
    assertEquals(category.id, renamed.id)
    assertEquals(category.ownerId, renamed.ownerId)
  }

  /** Blank identifiers and labels cannot enter the domain through construction or copies. */
  @Test
  fun blankFields_areRejected() {
    val category = Category(id = "category-1", ownerId = "user-1", name = "Analysis")

    for (blank in listOf("", " \t\n")) {
      assertThrows(IllegalArgumentException::class.java) { category.copy(id = blank) }
      assertThrows(IllegalArgumentException::class.java) { category.copy(ownerId = blank) }
      assertThrows(IllegalArgumentException::class.java) { category.copy(name = blank) }
    }
  }
}
