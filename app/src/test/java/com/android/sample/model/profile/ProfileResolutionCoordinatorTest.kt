// Co-authored-by: Copilot
// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Code
package com.android.sample.model.profile

import com.android.sample.model.auth.UserAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileResolutionCoordinatorTest {
  private val user = UserAccount("user123", "user@example.com", "User", "photo-url")
  private val existingProfile = UserProfile("user123")
  private val createdProfile = UserProfile("user123")

  // Returns an existing profile without attempting to create another one.
  @Test
  fun resolve_returnsExistingProfile_withoutCreatingProfile() = runTest {
    val repository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isSuccess)
    assertEquals(existingProfile, result.getOrNull())
    assertEquals(listOf("user123"), repository.getProfileUserIds)
    assertTrue(repository.createProfileUserIds.isEmpty())
  }

  // Creates and returns a profile when the lookup finds no persisted profile.
  @Test
  fun resolve_createsProfile_whenProfileIsMissing() = runTest {
    val repository =
        FakeProfileRepository(
            getProfileResult = Result.success(null),
            createProfileResult = Result.success(createdProfile),
        )

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isSuccess)
    assertEquals(createdProfile, result.getOrNull())
    assertEquals(listOf("user123"), repository.getProfileUserIds)
    assertEquals(listOf("user123"), repository.createProfileUserIds)
  }

  // Preserves a lookup failure and does not attempt profile creation.
  @Test
  fun resolve_returnsLookupFailure_withoutCreatingProfile() = runTest {
    val failure = ProfileException(ProfileError.FIRESTORE_FAILURE)
    val repository = FakeProfileRepository(getProfileResult = Result.failure(failure))

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isFailure)
    assertSame(failure, result.exceptionOrNull())
    assertTrue(repository.createProfileUserIds.isEmpty())
  }

  // Preserves a profile creation failure after a missing-profile lookup.
  @Test
  fun resolve_returnsCreationFailure_whenProfileCreationFails() = runTest {
    val failure = ProfileException(ProfileError.UNKNOWN)
    val repository =
        FakeProfileRepository(
            getProfileResult = Result.success(null),
            createProfileResult = Result.failure(failure),
        )

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isFailure)
    assertSame(failure, result.exceptionOrNull())
    assertEquals(listOf("user123"), repository.createProfileUserIds)
  }

  // Forwards the authenticated user's UID to both repository operations.
  @Test
  fun resolve_forwardsAuthenticatedUserId() = runTest {
    val repository =
        FakeProfileRepository(
            getProfileResult = Result.success(null),
            createProfileResult = Result.success(createdProfile),
        )
    val differentUser = UserAccount("different-user", null, null, null)

    ProfileResolutionCoordinator(repository).resolve(differentUser)

    assertEquals(listOf("different-user"), repository.getProfileUserIds)
    assertEquals(listOf("different-user"), repository.createProfileUserIds)
  }

  // Rejects a blank authenticated UID before touching the profile repository.
  @Test
  fun resolve_rejectsBlankAuthenticatedUser_withoutCallingRepository() = runTest {
    val repository = FakeProfileRepository()

    val result = ProfileResolutionCoordinator(repository).resolve(user.copy(uid = "  "))

    assertTrue(result.isFailure)
    assertEquals(
        ProfileError.INVALID_USER_ID,
        (result.exceptionOrNull() as ProfileException).error,
    )
    assertTrue(repository.getProfileUserIds.isEmpty())
    assertTrue(repository.createProfileUserIds.isEmpty())
  }

  // Converts an unexpected repository exception into a stable profile failure.
  @Test
  fun resolve_wrapsUnexpectedExceptionAsUnknownProfileFailure() = runTest {
    val failure = IllegalStateException("unexpected repository failure")
    val repository = FakeProfileRepository(getProfileException = failure)

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as ProfileException
    assertEquals(ProfileError.UNKNOWN, exception.error)
    assertSame(failure, exception.cause)
  }

  // Converts an unexpected exception during profile creation into a stable failure.
  @Test
  fun resolve_wrapsUnexpectedCreationExceptionAsUnknownProfileFailure() = runTest {
    val failure = IllegalStateException("unexpected profile creation failure")
    val repository =
        FakeProfileRepository(
            getProfileResult = Result.success(null),
            createProfileException = failure,
        )

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as ProfileException
    assertEquals(ProfileError.UNKNOWN, exception.error)
    assertSame(failure, exception.cause)
    assertEquals(listOf("user123"), repository.createProfileUserIds)
  }

  // Rejects a profile that does not belong to the authenticated user.
  @Test
  fun resolve_rejectsProfileWithDifferentUserId() = runTest {
    val repository =
        FakeProfileRepository(getProfileResult = Result.success(UserProfile("different-user")))

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isFailure)
    assertEquals(
        ProfileError.INVALID_PROFILE_DATA,
        (result.exceptionOrNull() as ProfileException).error,
    )
    assertTrue(repository.createProfileUserIds.isEmpty())
  }

  // Rejects profiles returned by lookup when their UIDs contain only whitespace.
  @Test
  fun resolve_rejectsProfileWithBlankUserIds() = runTest {
    for (blankUid in listOf("  ", "  \n", "  \t")) {
      val repository =
          FakeProfileRepository(getProfileResult = Result.success(UserProfile(blankUid)))

      val result = ProfileResolutionCoordinator(repository).resolve(user)

      assertTrue(result.isFailure)
      assertEquals(
          ProfileError.INVALID_PROFILE_DATA,
          (result.exceptionOrNull() as ProfileException).error,
      )
      assertTrue(repository.createProfileUserIds.isEmpty())
    }
  }

  // Rejects a newly created profile that does not belong to the authenticated user.
  @Test
  fun resolve_rejectsCreatedProfileWithDifferentUserId() = runTest {
    val repository =
        FakeProfileRepository(
            getProfileResult = Result.success(null),
            createProfileResult = Result.success(UserProfile("different-user")),
        )

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isFailure)
    assertEquals(
        ProfileError.INVALID_PROFILE_DATA,
        (result.exceptionOrNull() as ProfileException).error,
    )
  }

  // Rejects newly created profiles when their UIDs contain only whitespace.
  @Test
  fun resolve_rejectsCreatedProfileWithBlankUserIds() = runTest {
    for (blankUid in listOf("  ", "  \n", "  \t")) {
      val repository =
          FakeProfileRepository(
              getProfileResult = Result.success(null),
              createProfileResult = Result.success(UserProfile(blankUid)),
          )

      val result = ProfileResolutionCoordinator(repository).resolve(user)

      assertTrue(result.isFailure)
      assertEquals(
          ProfileError.INVALID_PROFILE_DATA,
          (result.exceptionOrNull() as ProfileException).error,
      )
    }
  }

  // Propagates cancellation from profile lookup instead of converting it to a failed result.
  @Test
  fun resolve_propagatesCancellationFromProfileLookup() = runTest {
    val cancellation = CancellationException("Lookup cancelled")
    val repository = FakeProfileRepository(getProfileException = cancellation)

    try {
      ProfileResolutionCoordinator(repository).resolve(user)
      fail("Expected cancellation to propagate")
    } catch (exception: CancellationException) {
      assertSame(cancellation, exception)
    }
  }

  // Propagates cancellation from profile creation instead of converting it to a failed result.
  @Test
  fun resolve_propagatesCancellationFromProfileCreation() = runTest {
    val cancellation = CancellationException("Creation cancelled")
    val repository =
        FakeProfileRepository(
            getProfileResult = Result.success(null),
            createProfileException = cancellation,
        )

    try {
      ProfileResolutionCoordinator(repository).resolve(user)
      fail("Expected cancellation to propagate")
    } catch (exception: CancellationException) {
      assertSame(cancellation, exception)
    }
  }

  // Throws cancellation returned inside a failed lookup result.
  @Test
  fun resolve_propagatesCancellationFromFailedProfileLookupResult() = runTest {
    val cancellation = CancellationException("Lookup cancelled in result")
    val repository = FakeProfileRepository(getProfileResult = Result.failure(cancellation))

    try {
      ProfileResolutionCoordinator(repository).resolve(user)
      fail("Expected cancellation to propagate")
    } catch (exception: CancellationException) {
      assertSame(cancellation, exception)
    }
  }

  // Throws cancellation returned inside a failed creation result.
  @Test
  fun resolve_propagatesCancellationFromFailedProfileCreationResult() = runTest {
    val cancellation = CancellationException("Creation cancelled in result")
    val repository =
        FakeProfileRepository(
            getProfileResult = Result.success(null),
            createProfileResult = Result.failure(cancellation),
        )

    try {
      ProfileResolutionCoordinator(repository).resolve(user)
      fail("Expected cancellation to propagate")
    } catch (exception: CancellationException) {
      assertSame(cancellation, exception)
    }
  }

  // Gives a new profile the sanitized display name as its default username.
  @Test
  fun resolve_claimsDefaultUsername_forNewProfile() = runTest {
    val named = UserProfile("user123", "User")
    val repository = FakeProfileRepository()
    repository.updateUsernameResults += Result.success(named)

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertEquals(named, result.getOrNull())
    assertEquals(listOf("User"), repository.requestedUsernames)
  }

  // Existing profiles with an empty username get the default on sign-in.
  @Test
  fun resolve_claimsDefaultUsername_forExistingBlankProfile() = runTest {
    val named = UserProfile("user123", "User")
    val repository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))
    repository.updateUsernameResults += Result.success(named)

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertEquals(named, result.getOrNull())
    assertTrue(repository.createProfileUserIds.isEmpty())
  }

  // Profiles that already have a username are not touched.
  @Test
  fun resolve_keepsExistingUsername() = runTest {
    val profile = UserProfile("user123", "Chosen")
    val repository = FakeProfileRepository(getProfileResult = Result.success(profile))

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertEquals(profile, result.getOrNull())
    assertTrue(repository.requestedUsernames.isEmpty())
  }

  // A taken default moves on to the next candidate until one is free.
  @Test
  fun resolve_triesNextCandidate_whenDefaultIsTaken() = runTest {
    val named = UserProfile("user123", "second")
    val repository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))
    repository.updateUsernameResults +=
        Result.failure(ProfileException(ProfileError.USERNAME_TAKEN))
    repository.updateUsernameResults += Result.success(named)

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertEquals(named, result.getOrNull())
    assertEquals(2, repository.requestedUsernames.size)
    assertEquals(
        UsernamePolicy.defaultCandidate(user.displayName, user.uid, 1),
        repository.requestedUsernames[1],
    )
  }

  // Stops after the allowed attempts but still signs the user in with the blank profile.
  @Test
  fun resolve_returnsProfileUnchanged_whenEveryCandidateIsTaken() = runTest {
    val repository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))
    repeat(UsernamePolicy.DEFAULT_ATTEMPTS + 2) {
      repository.updateUsernameResults +=
          Result.failure(ProfileException(ProfileError.USERNAME_TAKEN))
    }

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertEquals(existingProfile, result.getOrNull())
    assertEquals(UsernamePolicy.DEFAULT_ATTEMPTS, repository.requestedUsernames.size)
  }

  // A storage failure while claiming the username never blocks sign-in and is not retried.
  @Test
  fun resolve_returnsProfileUnchanged_whenClaimFails() = runTest {
    val repository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))
    repository.updateUsernameResults +=
        Result.failure(ProfileException(ProfileError.FIRESTORE_FAILURE))

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertEquals(existingProfile, result.getOrNull())
    assertEquals(1, repository.requestedUsernames.size)
  }

  // An unexpected exception from the claim is swallowed the same way.
  @Test
  fun resolve_returnsProfileUnchanged_whenClaimThrows() = runTest {
    val repository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))
    repository.updateUsernameException = IllegalStateException("boom")

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertEquals(existingProfile, result.getOrNull())
  }

  // Ignores a claim result that belongs to a different user.
  @Test
  fun resolve_ignoresClaimResultForAnotherUser() = runTest {
    val repository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))
    repository.updateUsernameResults += Result.success(UserProfile("other", "Name"))

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertEquals(existingProfile, result.getOrNull())
  }

  // Cancellation during the claim propagates, thrown or stored in the result.
  @Test
  fun resolve_propagatesCancellationFromUsernameClaim() = runTest {
    val thrown = CancellationException("thrown")
    val thrownRepository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))
    thrownRepository.updateUsernameException = thrown
    val stored = CancellationException("stored")
    val storedRepository = FakeProfileRepository(getProfileResult = Result.success(existingProfile))
    storedRepository.updateUsernameResults += Result.failure(stored)

    for ((repository, expected) in listOf(thrownRepository to thrown, storedRepository to stored)) {
      try {
        ProfileResolutionCoordinator(repository).resolve(user)
        fail("Expected cancellation to propagate")
      } catch (exception: CancellationException) {
        assertSame(expected, exception)
      }
    }
  }

  private class FakeProfileRepository(
      private val getProfileResult: Result<UserProfile?> = Result.success(null),
      private val createProfileResult: Result<UserProfile> = Result.success(UserProfile("user123")),
      private val createProfileException: Throwable? = null,
      private val getProfileException: Throwable? = null,
  ) : ProfileRepository {
    val getProfileUserIds = mutableListOf<String>()
    val createProfileUserIds = mutableListOf<String>()

    /** Usernames requested through [updateUsername], in call order. */
    val requestedUsernames = mutableListOf<String>()

    /** Outcomes returned by successive [updateUsername] calls; a generic failure when empty. */
    val updateUsernameResults = ArrayDeque<Result<UserProfile>>()
    var updateUsernameException: Throwable? = null

    override suspend fun updateUsername(userId: String, username: String): Result<UserProfile> {
      requestedUsernames += username
      updateUsernameException?.let { throw it }
      return updateUsernameResults.removeFirstOrNull()
          ?: Result.failure(ProfileException(ProfileError.UNKNOWN))
    }

    override suspend fun getProfile(userId: String): Result<UserProfile?> {
      getProfileUserIds += userId
      getProfileException?.let { throw it }
      return getProfileResult
    }

    override suspend fun createProfile(userId: String): Result<UserProfile> {
      createProfileUserIds += userId
      createProfileException?.let { throw it }
      return createProfileResult
    }
  }
}
