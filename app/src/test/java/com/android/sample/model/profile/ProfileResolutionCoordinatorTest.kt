// Co-authored-by: Copilot
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
    assertEquals(ProfileResolution.Existing(existingProfile), result.getOrNull())
    assertEquals(listOf("user123"), repository.getProfileUserIds)
    assertTrue(repository.createProfileUserIds.isEmpty())
  }

  // Creates and returns a profile when the lookup finds no persisted profile.
  @Test
  fun resolve_createsProfile_whenProfileIsMissing() = runTest {
    val repository =
        FakeProfileRepository(
            getProfileResult = Result.success(null),
            createProfileResult = Result.success(ProfileResolution.Created(createdProfile)),
        )

    val result = ProfileResolutionCoordinator(repository).resolve(user)

    assertTrue(result.isSuccess)
    assertEquals(ProfileResolution.Created(createdProfile), result.getOrNull())
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
            createProfileResult = Result.success(ProfileResolution.Created(createdProfile)),
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
            createProfileResult =
                Result.success(ProfileResolution.Created(UserProfile("different-user"))),
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
              createProfileResult =
                  Result.success(ProfileResolution.Created(UserProfile(blankUid))),
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

  private class FakeProfileRepository(
      private val getProfileResult: Result<UserProfile?> = Result.success(null),
      private val createProfileResult: Result<ProfileResolution> =
          Result.success(ProfileResolution.Created(UserProfile("user123"))),
      private val createProfileException: CancellationException? = null,
      private val getProfileException: Throwable? = null,
  ) : ProfileRepository {
    val getProfileUserIds = mutableListOf<String>()
    val createProfileUserIds = mutableListOf<String>()

    override suspend fun getProfile(userId: String): Result<UserProfile?> {
      getProfileUserIds += userId
      getProfileException?.let { throw it }
      return getProfileResult
    }

    override suspend fun createProfile(userId: String): Result<ProfileResolution> {
      createProfileUserIds += userId
      createProfileException?.let { throw it }
      return createProfileResult
    }
  }
}
