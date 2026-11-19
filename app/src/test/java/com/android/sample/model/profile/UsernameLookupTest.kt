// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests [lookupUsername], which turns any profile lookup outcome into a name, a missing name, or an
 * unavailable profile.
 */
class UsernameLookupTest {
  private val repository = FakeProfileRepository()

  /** An existing profile yields its username. */
  @Test
  fun lookupUsername_profileFound_returnsFound() = runTest {
    repository.setUsername("alice", "alice_s")

    assertEquals(UsernameLookup.Found("alice_s"), repository.lookupUsername("alice"))
    assertEquals(listOf("alice"), repository.getProfileUserIds)
  }

  /** A missing profile yields Missing. */
  @Test
  fun lookupUsername_nullProfile_returnsMissing() = runTest {
    repository.enqueue("alice", Result.success(null))

    assertEquals(UsernameLookup.Missing, repository.lookupUsername("alice"))
  }

  /** An empty username yields Missing. */
  @Test
  fun lookupUsername_emptyUsername_returnsMissing() = runTest {
    repository.setUsername("alice", "")

    assertEquals(UsernameLookup.Missing, repository.lookupUsername("alice"))
  }

  /** A whitespace-only username yields Missing. */
  @Test
  fun lookupUsername_whitespaceUsername_returnsMissing() = runTest {
    repository.setUsername("alice", "   ")

    assertEquals(UsernameLookup.Missing, repository.lookupUsername("alice"))
  }

  /** A failed lookup result yields Unavailable instead of an error. */
  @Test
  fun lookupUsername_resultFailure_returnsUnavailable() = runTest {
    repository.enqueue("alice", Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE)))

    assertEquals(UsernameLookup.Unavailable, repository.lookupUsername("alice"))
  }

  /** A thrown non-cancellation exception yields Unavailable instead of propagating. */
  @Test
  fun lookupUsername_thrownException_returnsUnavailable() = runTest {
    repository.throwFor("alice", IllegalStateException("boom"))

    assertEquals(UsernameLookup.Unavailable, repository.lookupUsername("alice"))
  }

  /** Cancellation is never swallowed, so structured concurrency keeps working. */
  @Test
  fun lookupUsername_cancellationException_isRethrown() = runTest {
    repository.throwFor("alice", CancellationException("cancelled"))

    val thrown =
        try {
          repository.lookupUsername("alice")
          null
        } catch (exception: CancellationException) {
          exception
        }

    assertEquals("cancelled", thrown?.message)
  }
}
