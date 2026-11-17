// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tests [findUsername], which turns any profile lookup outcome into a displayable name or null. */
class UsernameLookupTest {
  private val repository = FakeProfileRepository()

  /** An existing profile yields its username. */
  @Test
  fun findUsername_profileFound_returnsUsername() = runTest {
    repository.setUsername("alice", "alice_s")

    assertEquals("alice_s", repository.findUsername("alice"))
    assertEquals(listOf("alice"), repository.getProfileUserIds)
  }

  /** A missing profile yields null. */
  @Test
  fun findUsername_nullProfile_returnsNull() = runTest {
    repository.enqueue("alice", Result.success(null))

    assertNull(repository.findUsername("alice"))
  }

  /** An empty username yields null. */
  @Test
  fun findUsername_emptyUsername_returnsNull() = runTest {
    repository.setUsername("alice", "")

    assertNull(repository.findUsername("alice"))
  }

  /** A whitespace-only username yields null. */
  @Test
  fun findUsername_whitespaceUsername_returnsNull() = runTest {
    repository.setUsername("alice", "   ")

    assertNull(repository.findUsername("alice"))
  }

  /** A failed lookup result yields null instead of an error. */
  @Test
  fun findUsername_resultFailure_returnsNull() = runTest {
    repository.enqueue("alice", Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE)))

    assertNull(repository.findUsername("alice"))
  }

  /** A thrown non-cancellation exception yields null instead of propagating. */
  @Test
  fun findUsername_thrownException_returnsNull() = runTest {
    repository.throwFor("alice", IllegalStateException("boom"))

    assertNull(repository.findUsername("alice"))
  }

  /** Cancellation is never swallowed, so structured concurrency keeps working. */
  @Test
  fun findUsername_cancellationException_isRethrown() = runTest {
    repository.throwFor("alice", CancellationException("cancelled"))

    val thrown =
        try {
          repository.findUsername("alice")
          null
        } catch (exception: CancellationException) {
          exception
        }

    assertEquals("cancelled", thrown?.message)
  }
}
