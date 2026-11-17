// Co-authored-by: Claude Code
package com.android.sample.model.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UsernamePolicyTest {
  // Accepts names within the length bounds and trims surrounding whitespace.
  @Test
  fun validate_acceptsValidNames() {
    assertEquals("abc", UsernamePolicy.validate("abc"))
    assertEquals("Alice_9.x-y", UsernamePolicy.validate("  Alice_9.x-y "))
    assertEquals("a".repeat(20), UsernamePolicy.validate("a".repeat(20)))
  }

  // Rejects names that are too short, too long, start badly or use forbidden characters.
  @Test
  fun validate_rejectsInvalidNames() {
    val invalid = listOf("", "ab", "a".repeat(21), "_abc", ".abc", "a b c", "a/b", "__x__", "héllo")
    for (bad in invalid) {
      assertNull(bad, UsernamePolicy.validate(bad))
    }
  }

  // The reservation key ignores letter case.
  @Test
  fun key_isLowercase() {
    assertEquals("alice", UsernamePolicy.key("ALiCe"))
  }

  // Attempt 0 is the display name with spaces replaced and invalid characters removed.
  @Test
  fun defaultCandidate_firstAttemptSanitizesDisplayName() {
    assertEquals("Jane_Doe", UsernamePolicy.defaultCandidate("Jane Doe", "uid1", 0))
    assertEquals("Jane_Doe", UsernamePolicy.defaultCandidate("  Jane  Doe! ", "uid1", 0))
  }

  // Missing, empty or unusable display names fall back to a generic base.
  @Test
  fun defaultCandidate_fallsBackWhenDisplayNameIsUnusable() {
    for (name in listOf(null, "", "  ", "é", "!!", "ab")) {
      assertEquals("user", UsernamePolicy.defaultCandidate(name, "uid1", 0))
    }
  }

  // Long names are cut to the maximum length.
  @Test
  fun defaultCandidate_truncatesLongNames() {
    assertEquals(20, UsernamePolicy.defaultCandidate("a".repeat(50), "uid1", 0).length)
  }

  // Later attempts add UID characters, stay valid and differ from each other.
  @Test
  fun defaultCandidate_laterAttemptsAreValidAndDistinct() {
    val candidates =
        (0 until UsernamePolicy.DEFAULT_ATTEMPTS).map {
          UsernamePolicy.defaultCandidate("a".repeat(30), "AbCdEf123456", it)
        }
    candidates.forEach { assertNotNull(it, UsernamePolicy.validate(it)) }
    assertEquals(candidates.size, candidates.toSet().size)
    assertEquals("a".repeat(15) + "_AbCd", candidates[1])
  }

  // Different users with the same display name get different fallback candidates.
  @Test
  fun defaultCandidate_differsBetweenUsers() {
    assertNotEquals(
        UsernamePolicy.defaultCandidate("Sam", "uidAAAA1", 1),
        UsernamePolicy.defaultCandidate("Sam", "uidBBBB2", 1),
    )
  }

  // A UID without usable characters still yields a valid candidate.
  @Test
  fun defaultCandidate_handlesUidWithoutAlphanumerics() {
    assertNotNull(UsernamePolicy.validate(UsernamePolicy.defaultCandidate("Sam", "---", 1)))
  }
}
