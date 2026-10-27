// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
// Co-authored-by: GitHub Copilot
package com.android.sample.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.android.sample.model.auth.AuthError
import com.android.sample.model.auth.AuthException
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import com.android.sample.model.profile.ProfileError
import com.android.sample.model.profile.ProfileRepository
import com.android.sample.model.profile.ProfileResolutionCoordinator
import com.android.sample.model.profile.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Contract tests use a controllable repository, without Firebase or Android services. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = FakeAuthRepository()
  private val profileRepository = FakeProfileRepository()
  private val store = ViewModelStore()
  private lateinit var viewModel: AuthViewModel
  private val alice = UserAccount("alice", "alice@example.com", "Alice", null)
  private val bob = UserAccount("bob", null, null, null)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    viewModel = AuthViewModel(repository, ProfileResolutionCoordinator(profileRepository))
    store.put("auth", viewModel)
  }

  @After
  fun tearDown() {
    store.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun waitsForFirstSessionBeforeReportingSignedOut() =
      runTest(dispatcher) {
        assertEquals(AuthUiState(), viewModel.authUiState.value)
        runCurrent()
        assertTrue(viewModel.authUiState.value.isInitializing)
        session(null)
        assertEquals(AuthUiState(isInitializing = false), viewModel.authUiState.value)
      }

  @Test
  fun observesRestoredSessionAccountChangesAndExternalSignOut() =
      runTest(dispatcher) {
        session(alice)
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.authUiState.value)
        session(bob)
        assertEquals(bob, viewModel.authUiState.value.user)
        session(null)
        assertEquals(AuthUiState(isInitializing = false), viewModel.authUiState.value)
      }

  @Test
  fun brokenSessionFlowEndsStartupAndReportsUnknownInsteadOfCrashing() =
      runTest(dispatcher) {
        val failsAtStartup =
            AuthViewModel(
                SessionOnlyRepository(flow { throw IllegalStateException("private diagnostic") }),
                ProfileResolutionCoordinator(FakeProfileRepository()),
            )
        store.put("failsAtStartup", failsAtStartup)
        runCurrent()
        assertEquals(
            AuthUiState(isInitializing = false, error = AuthError.UNKNOWN),
            failsAtStartup.authUiState.value,
        )
        val failsLater =
            AuthViewModel(
                SessionOnlyRepository(
                    flow {
                      emit(alice)
                      throw IllegalStateException("private diagnostic")
                    }
                ),
                ProfileResolutionCoordinator(FakeProfileRepository()),
            )
        store.put("failsLater", failsLater)
        runCurrent()
        assertEquals(
            AuthUiState(user = alice, isInitializing = false, error = AuthError.UNKNOWN),
            failsLater.authUiState.value,
        )
      }

  @Test
  fun sessionFlowThatCompletesKeepsLastUserWithoutError() =
      runTest(dispatcher) {
        val completed =
            AuthViewModel(
                SessionOnlyRepository(flowOf(alice)),
                ProfileResolutionCoordinator(FakeProfileRepository()),
            )
        store.put("completed", completed)
        runCurrent()
        assertEquals(AuthUiState(user = alice, isInitializing = false), completed.authUiState.value)
      }

  @Test
  fun signInForwardsTokenButWaitsForSessionEventToPublishIdentity() =
      runTest(dispatcher) {
        val result = CompletableDeferred<UserAccount>()
        repository.publishSessions = false
        repository.signIn = { result.await() }
        viewModel.signInWithGoogle { "google-token" }
        assertTrue(viewModel.authUiState.value.isLoading)
        runCurrent()
        assertEquals(listOf("google-token"), repository.tokens)
        result.complete(alice)
        runCurrent()
        assertEquals(AuthUiState(), viewModel.authUiState.value)
        session(alice)
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.authUiState.value)
      }

  // Publishes the resolved profile after an explicit sign-in succeeds.
  @Test
  fun successfulSignIn_resolvesAndPublishesProfile() =
      runTest(dispatcher) {
        val expected = UserProfile("alice")
        profileRepository.getProfileAction = { Result.success(null) }
        profileRepository.createProfileAction = { Result.success(expected) }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()

        assertEquals(ProfileUiState(profile = expected), viewModel.profileUiState.value)
        assertEquals(listOf("alice"), profileRepository.getProfileUserIds)
        assertEquals(listOf("alice"), profileRepository.createProfileUserIds)
      }

  // Cancels A's profile resolution and exposes only B's result after a newer sign-in.
  @Test
  fun newerSignIn_cancelsAndSupersedesPreviousProfileResolution() =
      runTest(dispatcher) {
        val aliceLookup = CompletableDeferred<Result<UserProfile?>>()
        val bobProfile = UserProfile("bob")
        profileRepository.getProfileAction = { userId ->
          if (userId == "alice") {
            try {
              aliceLookup.await()
            } finally {
              profileRepository.cancelledUserIds += userId
            }
          } else {
            Result.success(bobProfile)
          }
        }
        repository.signIn = { token -> if (token == "alice") alice else bob }

        viewModel.signInWithGoogle { "alice" }
        runCurrent()
        assertTrue(viewModel.profileUiState.value.isLoading)

        viewModel.signInWithGoogle { "bob" }
        runCurrent()

        assertEquals(ProfileUiState(profile = bobProfile), viewModel.profileUiState.value)
        assertEquals(listOf("alice"), profileRepository.cancelledUserIds)
      }

  // A late sign-in result for another user cannot cancel the current user's profile lookup.
  @Test
  fun staleSignInResult_doesNotCancelCurrentUsersProfileResolution() =
      runTest(dispatcher) {
        val bobLookup = CompletableDeferred<Result<UserProfile?>>()
        profileRepository.getProfileAction = { userId ->
          if (userId == "bob") {
            try {
              bobLookup.await()
            } catch (exception: CancellationException) {
              profileRepository.cancelledUserIds += userId
              throw exception
            }
          } else {
            Result.success(null)
          }
        }
        session(bob)
        repository.signIn = { bob }

        viewModel.signInWithGoogle { "establish-bob" }
        runCurrent()
        assertTrue(viewModel.profileUiState.value.isLoading)

        val delayedAlice = CompletableDeferred<UserAccount>()
        repository.signIn = { delayedAlice.await() }
        repository.publishSessions = false
        viewModel.signInWithGoogle { "delayed-alice" }
        runCurrent()
        delayedAlice.complete(alice)
        runCurrent()

        assertTrue(viewModel.profileUiState.value.isLoading)
        assertTrue(profileRepository.cancelledUserIds.isEmpty())

        val expected = UserProfile("bob")
        bobLookup.complete(Result.success(expected))
        runCurrent()
        assertEquals(ProfileUiState(profile = expected), viewModel.profileUiState.value)
      }

  // Waits for the matching session event before reading the signed-in user's profile.
  @Test
  fun signInResultWaitsForMatchingSessionBeforeResolvingProfile() =
      runTest(dispatcher) {
        repository.publishSessions = false
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()

        assertTrue(profileRepository.getProfileUserIds.isEmpty())
        assertEquals(ProfileUiState(), viewModel.profileUiState.value)

        session(alice)

        assertEquals(listOf("alice"), profileRepository.getProfileUserIds)
        assertEquals(ProfileUiState(profile = UserProfile("alice")), viewModel.profileUiState.value)
      }

  // Does not read account A's profile when the observed session belongs to account B.
  @Test
  fun mismatchedSignInResultDoesNotResolveWrongProfile() =
      runTest(dispatcher) {
        repository.publishSessions = false
        repository.signIn = { alice }
        session(bob)

        viewModel.signInWithGoogle { "token" }
        runCurrent()

        assertTrue(profileRepository.getProfileUserIds.isEmpty())
        assertEquals(ProfileUiState(), viewModel.profileUiState.value)
      }

  // Clears profile loading when the session changes during an in-flight lookup.
  @Test
  fun sessionChangeDuringLookupCancelsAndClearsProfileResolution() =
      runTest(dispatcher) {
        val lookup = CompletableDeferred<Result<UserProfile?>>()
        profileRepository.getProfileAction = {
          try {
            lookup.await()
          } catch (exception: CancellationException) {
            profileRepository.cancelledUserIds += "alice"
            throw exception
          }
        }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertTrue(viewModel.profileUiState.value.isLoading)

        session(bob)

        assertEquals(ProfileUiState(), viewModel.profileUiState.value)
        assertEquals(listOf("alice"), profileRepository.cancelledUserIds)
      }

  // Clears profile loading when the session changes during profile creation.
  @Test
  fun sessionChangeDuringCreationCancelsAndClearsProfileResolution() =
      runTest(dispatcher) {
        val creation = CompletableDeferred<Result<UserProfile>>()
        profileRepository.getProfileAction = { Result.success(null) }
        profileRepository.createProfileAction = {
          try {
            creation.await()
          } finally {
            profileRepository.cancelledUserIds += "alice"
          }
        }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertTrue(viewModel.profileUiState.value.isLoading)

        session(bob)

        assertEquals(ProfileUiState(), viewModel.profileUiState.value)
        assertEquals(listOf("alice"), profileRepository.cancelledUserIds)
      }

  // Publishes a stable profile error when the coordinator encounters an unexpected exception.
  @Test
  fun unexpectedProfileExceptionBecomesProfileError() =
      runTest(dispatcher) {
        profileRepository.getProfileAction = { throw IllegalStateException("unexpected failure") }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()

        assertEquals(ProfileUiState(error = ProfileError.UNKNOWN), viewModel.profileUiState.value)
      }

  // Keeps a loaded profile when a later sign-in request fails without changing the session.
  @Test
  fun failedSignIn_preservesLoadedProfile() =
      runTest(dispatcher) {
        session(alice)
        profileRepository.getProfileAction = { Result.success(UserProfile("alice")) }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "initial" }
        runCurrent()
        assertEquals(ProfileUiState(profile = UserProfile("alice")), viewModel.profileUiState.value)

        repository.signIn = { throw AuthException(AuthError.NETWORK) }
        viewModel.signInWithGoogle { "retry" }
        runCurrent()

        assertEquals(ProfileUiState(profile = UserProfile("alice")), viewModel.profileUiState.value)
      }

  // Keeps an active profile lookup when a later sign-in request fails.
  @Test
  fun failedSignIn_preservesProfileLookupInProgress() =
      runTest(dispatcher) {
        val lookup = CompletableDeferred<Result<UserProfile?>>()
        profileRepository.getProfileAction = {
          try {
            lookup.await()
          } catch (exception: CancellationException) {
            profileRepository.cancelledUserIds += "alice"
            throw exception
          }
        }
        session(alice)
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "initial" }
        runCurrent()
        assertTrue(viewModel.profileUiState.value.isLoading)

        repository.signIn = { throw AuthException(AuthError.NETWORK) }
        viewModel.signInWithGoogle { "retry" }
        runCurrent()

        assertEquals(ProfileUiState(isLoading = true), viewModel.profileUiState.value)
        assertTrue(profileRepository.cancelledUserIds.isEmpty())

        lookup.complete(Result.success(UserProfile("alice")))
        runCurrent()
        assertEquals(ProfileUiState(profile = UserProfile("alice")), viewModel.profileUiState.value)
      }

  // Reports operation-level lookup cancellation while keeping the authenticated session.
  @Test
  fun lookupCancellationWithUnchangedSession_reportsCancelledProfileError() =
      runTest(dispatcher) {
        profileRepository.getProfileAction = { throw CancellationException("lookup cancelled") }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()

        assertEquals(
            ProfileUiState(error = ProfileError.CANCELLED),
            viewModel.profileUiState.value,
        )
      }

  // Reports operation-level creation cancellation while keeping the authenticated session.
  @Test
  fun creationCancellationWithUnchangedSession_reportsCancelledProfileError() =
      runTest(dispatcher) {
        profileRepository.getProfileAction = { Result.success(null) }
        profileRepository.createProfileAction = {
          throw CancellationException("creation cancelled")
        }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()

        assertEquals(
            ProfileUiState(error = ProfileError.CANCELLED),
            viewModel.profileUiState.value,
        )
      }

  // Preserves an active profile lookup when the same user signs in again.
  @Test
  fun duplicateSignInDuringProfileLookup_preservesActiveResolution() =
      runTest(dispatcher) {
        val lookup = CompletableDeferred<Result<UserProfile?>>()
        profileRepository.getProfileAction = {
          try {
            lookup.await()
          } catch (exception: CancellationException) {
            profileRepository.cancelledUserIds += "alice"
            throw exception
          }
        }
        repository.publishSessions = false
        repository.signIn = { token ->
          assertTrue(token == "first" || token == "second")
          alice
        }
        val expectedProfile = UserProfile("alice")

        viewModel.signInWithGoogle { "first" }
        runCurrent()
        session(alice)
        assertTrue(viewModel.profileUiState.value.isLoading)

        viewModel.signInWithGoogle { "second" }
        runCurrent()

        assertEquals(listOf("first", "second"), repository.tokens)
        assertTrue(viewModel.profileUiState.value.isLoading)
        assertTrue(profileRepository.cancelledUserIds.isEmpty())

        lookup.complete(Result.success(expectedProfile))
        runCurrent()

        assertEquals(ProfileUiState(profile = expectedProfile), viewModel.profileUiState.value)
        assertTrue(profileRepository.cancelledUserIds.isEmpty())
      }

  // Does not publish a profile when the lookup completes after sign-out.
  @Test
  fun lateProfileResolutionAfterSignOut_doesNotPublishStaleProfile() =
      runTest(dispatcher) {
        val lookup = CompletableDeferred<Result<UserProfile?>>()
        profileRepository.getProfileAction = { withContext(NonCancellable) { lookup.await() } }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertTrue(viewModel.profileUiState.value.isLoading)

        viewModel.signOut()
        runCurrent()
        val stateAfterSignOut = viewModel.profileUiState.value
        assertEquals(ProfileUiState(), stateAfterSignOut)

        lookup.complete(Result.success(UserProfile("alice")))
        runCurrent()

        assertEquals(stateAfterSignOut, viewModel.profileUiState.value)
      }

  // Does not publish account A's profile after account B replaces the session.
  @Test
  fun lateProfileResolutionAfterDifferentUserSignIn_doesNotPublishStaleProfile() =
      runTest(dispatcher) {
        val aliceLookup = CompletableDeferred<Result<UserProfile?>>()
        profileRepository.getProfileAction = { userId ->
          if (userId == "alice") {
            withContext(NonCancellable) { aliceLookup.await() }
          } else {
            Result.success(UserProfile("bob"))
          }
        }
        repository.publishSessions = false
        repository.signIn = { token -> if (token == "alice") alice else bob }

        viewModel.signInWithGoogle { "alice" }
        runCurrent()
        session(alice)
        assertTrue(viewModel.profileUiState.value.isLoading)

        viewModel.signInWithGoogle { "bob" }
        runCurrent()
        session(bob)
        val stateAfterAccountSwitch = viewModel.profileUiState.value
        assertEquals(ProfileUiState(), stateAfterAccountSwitch)

        aliceLookup.complete(Result.success(UserProfile("alice")))
        runCurrent()

        assertEquals(stateAfterAccountSwitch, viewModel.profileUiState.value)
      }

  // Clears profile resolution when the session observer fails after resolution starts.
  @Test
  fun sessionFlowFailureAfterProfileResolution_cancelsAndClearsProfileState() =
      runTest(dispatcher) {
        val sessionFailure = CompletableDeferred<Unit>()
        val sessionRepository =
            SessionFailureRepository(
                flow {
                  emit(alice)
                  sessionFailure.await()
                  throw IllegalStateException("session flow failed")
                }
            )
        val failingSessionViewModel =
            AuthViewModel(sessionRepository, ProfileResolutionCoordinator(profileRepository))
        store.put("session-failure", failingSessionViewModel)
        runCurrent()

        val lookup = CompletableDeferred<Result<UserProfile?>>()
        profileRepository.getProfileAction = { lookup.await() }
        failingSessionViewModel.signInWithGoogle { "token" }
        runCurrent()
        assertTrue(failingSessionViewModel.profileUiState.value.isLoading)

        sessionFailure.complete(Unit)
        runCurrent()

        assertEquals(ProfileUiState(), failingSessionViewModel.profileUiState.value)
        assertEquals(AuthError.UNKNOWN, failingSessionViewModel.authUiState.value.error)
      }

  // Cancels an in-flight profile resolution and clears profile state when signing out.
  @Test
  fun signOut_cancelsPendingProfileResolutionAndClearsProfileState() =
      runTest(dispatcher) {
        val lookup = CompletableDeferred<Result<UserProfile?>>()
        profileRepository.getProfileAction = {
          try {
            lookup.await()
          } finally {
            profileRepository.cancelledUserIds += "alice"
          }
        }
        repository.signIn = { alice }

        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertTrue(viewModel.profileUiState.value.isLoading)

        viewModel.signOut()
        runCurrent()

        assertEquals(ProfileUiState(), viewModel.profileUiState.value)
        assertEquals(listOf("alice"), profileRepository.cancelledUserIds)
      }

  @Test
  fun signOutWaitsForSessionEventToClearIdentity() =
      runTest(dispatcher) {
        session(alice)
        repository.publishSessions = false
        val completion = CompletableDeferred<Unit>()
        repository.signOutAction = { completion.await() }
        viewModel.signOut()
        runCurrent()
        assertTrue(viewModel.authUiState.value.isLoading)
        assertEquals(alice, viewModel.authUiState.value.user)
        completion.complete(Unit)
        runCurrent()
        assertEquals(1, repository.signOutCalls)
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.authUiState.value)
        session(null)
        assertEquals(AuthUiState(isInitializing = false), viewModel.authUiState.value)
      }

  @Test
  fun delayedSignInResultDoesNotOverwriteNewerSessionNotification() =
      runTest(dispatcher) {
        repository.publishSessions = false
        val result = CompletableDeferred<UserAccount>()
        repository.signIn = { result.await() }
        viewModel.signInWithGoogle { "token" }
        session(bob)
        result.complete(alice)
        runCurrent()
        assertEquals(AuthUiState(user = bob, isInitializing = false), viewModel.authUiState.value)
      }

  @Test
  fun delayedSignOutCompletionDoesNotEraseNewerSessionNotification() =
      runTest(dispatcher) {
        repository.publishSessions = false
        session(alice)
        val completion = CompletableDeferred<Unit>()
        repository.signOutAction = { completion.await() }
        viewModel.signOut()
        session(null)
        session(bob)
        completion.complete(Unit)
        runCurrent()
        assertEquals(AuthUiState(user = bob, isInitializing = false), viewModel.authUiState.value)
      }

  @Test
  fun ignoresDuplicateAndOppositeActionsWhileSigningIn() =
      runTest(dispatcher) {
        val result = CompletableDeferred<UserAccount>()
        repository.signIn = { result.await() }
        viewModel.signInWithGoogle { "first" }
        viewModel.signInWithGoogle { "duplicate-before-launch" }
        viewModel.signOut()
        runCurrent()
        viewModel.signInWithGoogle { "duplicate-while-suspended" }
        viewModel.signOut()
        runCurrent()
        assertEquals(listOf("first"), repository.tokens)
        assertEquals(0, repository.signOutCalls)
        result.complete(alice)
        runCurrent()
        viewModel.signOut()
        runCurrent()
        assertEquals(1, repository.signOutCalls)
      }

  @Test
  fun ignoresDuplicateAndOppositeActionsWhileSigningOut() =
      runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        repository.signOutAction = { completion.await() }
        viewModel.signOut()
        viewModel.signOut()
        viewModel.signInWithGoogle { "ignored" }
        runCurrent()
        viewModel.signOut()
        viewModel.signInWithGoogle { "also-ignored" }
        runCurrent()
        assertEquals(1, repository.signOutCalls)
        assertTrue(repository.tokens.isEmpty())
        completion.complete(Unit)
        runCurrent()
        assertFalse(viewModel.authUiState.value.isLoading)
      }

  @Test
  fun exposesEveryRepositoryErrorAndPreservesExistingSession() =
      runTest(dispatcher) {
        session(alice)
        val silent = setOf(AuthError.CANCELLED, AuthError.CREDENTIAL_STATE_CLEAR_FAILED)
        for (error in AuthError.entries.filter { it !in silent }) {
          repository.signIn = { throw AuthException(error) }
          viewModel.signInWithGoogle { "token" }
          runCurrent()
          assertEquals(
              AuthUiState(user = alice, isInitializing = false, error = error),
              viewModel.authUiState.value,
          )
          repository.signOutAction = { throw AuthException(error) }
          viewModel.signOut()
          runCurrent()
          assertEquals(
              AuthUiState(user = alice, isInitializing = false, error = error),
              viewModel.authUiState.value,
          )
        }
      }

  @Test
  fun pickerFailuresShareErrorStateAndDismissalShowsNothing() =
      runTest(dispatcher) {
        session(alice)
        viewModel.signInWithGoogle { throw AuthException(AuthError.CANCELLED) }
        runCurrent()
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.authUiState.value)
        viewModel.signInWithGoogle { throw AuthException(AuthError.NO_CREDENTIAL) }
        runCurrent()
        assertEquals(
            AuthUiState(user = alice, isInitializing = false, error = AuthError.NO_CREDENTIAL),
            viewModel.authUiState.value,
        )
        // The repository is never called without a token.
        assertTrue(repository.tokens.isEmpty())
      }

  @Test
  fun unexpectedFailuresBecomeUnknownForBothActions() =
      runTest(dispatcher) {
        repository.signIn = { throw IllegalStateException("private diagnostic") }
        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertEquals(AuthError.UNKNOWN, viewModel.authUiState.value.error)
        assertFalse(viewModel.authUiState.value.isLoading)
        repository.signOutAction = { throw IllegalStateException("private diagnostic") }
        viewModel.signOut()
        runCurrent()
        assertEquals(AuthError.UNKNOWN, viewModel.authUiState.value.error)
        assertFalse(viewModel.authUiState.value.isLoading)
      }

  @Test
  fun retryClearsOldErrorImmediatelyAndCanSucceed() =
      runTest(dispatcher) {
        repository.signIn = { throw AuthException(AuthError.NETWORK) }
        viewModel.signInWithGoogle { "first" }
        runCurrent()
        val result = CompletableDeferred<UserAccount>()
        repository.signIn = { result.await() }
        viewModel.signInWithGoogle { "retry" }
        assertNull(viewModel.authUiState.value.error)
        assertTrue(viewModel.authUiState.value.isLoading)
        runCurrent()
        result.complete(alice)
        runCurrent()
        assertEquals(listOf("first", "retry"), repository.tokens)
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.authUiState.value)
      }

  @Test
  fun sessionEventsPreservePendingLoadingAndReportedError() =
      runTest(dispatcher) {
        val result = CompletableDeferred<UserAccount>()
        repository.signIn = { result.await() }
        viewModel.signInWithGoogle { "token" }
        session(alice)
        assertEquals(
            AuthUiState(user = alice, isInitializing = false, isLoading = true),
            viewModel.authUiState.value,
        )
        result.completeExceptionally(AuthException(AuthError.NETWORK))
        runCurrent()
        session(bob)
        assertEquals(
            AuthUiState(user = bob, isInitializing = false, error = AuthError.NETWORK),
            viewModel.authUiState.value,
        )
      }

  @Test
  fun cleanupFailureAfterSignOutShowsNoErrorAndUserIsSignedOut() =
      runTest(dispatcher) {
        session(alice)
        // An earlier visible error is cleared when sign-out starts.
        repository.signIn = { throw AuthException(AuthError.NETWORK) }
        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertEquals(AuthError.NETWORK, viewModel.authUiState.value.error)
        repository.signOutAction = {
          repository.currentUser.emit(null)
          throw AuthException(AuthError.CREDENTIAL_STATE_CLEAR_FAILED)
        }
        viewModel.signOut()
        runCurrent()
        assertEquals(AuthUiState(isInitializing = false), viewModel.authUiState.value)
      }

  @Test
  fun clearErrorOnlyDismissesErrorAndIsIdempotent() =
      runTest(dispatcher) {
        session(alice)
        repository.signIn = { throw AuthException(AuthError.NETWORK) }
        viewModel.signInWithGoogle { "token" }
        runCurrent()
        val expected = viewModel.authUiState.value.copy(error = null)
        viewModel.clearError()
        viewModel.clearError()
        assertEquals(expected, viewModel.authUiState.value)
      }

  @Test
  fun cancellationFromInsideOperationIsReportedAsUnknownAndAllowsRetry() =
      runTest(dispatcher) {
        session(alice)
        val unknown = AuthUiState(user = alice, isInitializing = false, error = AuthError.UNKNOWN)
        viewModel.signInWithGoogle { throw CancellationException("picker cancelled") }
        runCurrent()
        assertEquals(unknown, viewModel.authUiState.value)
        repository.signIn = { throw CancellationException("task cancelled") }
        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertEquals(unknown, viewModel.authUiState.value)
        repository.signOutAction = { throw CancellationException("task cancelled") }
        viewModel.signOut()
        runCurrent()
        assertEquals(unknown, viewModel.authUiState.value)
        repository.signIn = { bob }
        viewModel.signInWithGoogle { "retry" }
        runCurrent()
        assertEquals(bob, viewModel.authUiState.value.user)
      }

  @Test
  fun clearingViewModelCancelsObservationAndPendingRequest() =
      runTest(dispatcher) {
        val result = CompletableDeferred<UserAccount>()
        var cancelled = false
        repository.signIn = {
          try {
            result.await()
          } finally {
            cancelled = true
          }
        }
        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertEquals(1, repository.currentUser.subscriptionCount.value)
        store.clear()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(0, repository.currentUser.subscriptionCount.value)
        assertFalse(viewModel.authUiState.value.isLoading)
        assertNull(viewModel.authUiState.value.error)
      }

  @Test
  fun factoryInjectsRepositoryAndRejectsUnrelatedTypes() =
      runTest(dispatcher) {
        val factory =
            AuthViewModelFactory(repository, ProfileResolutionCoordinator(profileRepository))
        val created = factory.create(AuthViewModel::class.java)
        store.put("factory", created)
        session(alice)
        assertEquals(alice, created.authUiState.value.user)
        assertThrows(IllegalArgumentException::class.java) {
          factory.create(OtherViewModel::class.java)
        }
        assertThrows(IllegalArgumentException::class.java) { factory.create(ViewModel::class.java) }
      }

  private suspend fun TestScope.session(user: UserAccount?) {
    runCurrent()
    repository.currentUser.emit(user)
    runCurrent()
  }

  private class OtherViewModel : ViewModel()

  /** A repository whose session flow is given by the test; the actions are not used. */
  private class SessionOnlyRepository(override val currentUser: Flow<UserAccount?>) :
      AuthRepository {
    override suspend fun signInWithGoogle(idToken: String): UserAccount = error("Not used")

    override suspend fun signOut() = error("Not used")
  }

  /** A session repository whose sign-in succeeds while its session flow can fail independently. */
  private class SessionFailureRepository(override val currentUser: Flow<UserAccount?>) :
      AuthRepository {
    override suspend fun signInWithGoogle(idToken: String): UserAccount =
        UserAccount(
            uid = "alice",
            email = "alice@example.com",
            displayName = "Alice",
            photoUrl = null,
        )

    override suspend fun signOut() = Unit
  }

  private class FakeAuthRepository : AuthRepository {
    override val currentUser = MutableSharedFlow<UserAccount?>()
    val tokens = mutableListOf<String>()
    var signOutCalls = 0
    var publishSessions = true
    var signIn: suspend (String) -> UserAccount = { error("Configure sign-in in the test") }
    var signOutAction: suspend () -> Unit = {}

    override suspend fun signInWithGoogle(idToken: String): UserAccount {
      tokens += idToken
      val user = signIn(idToken)
      if (publishSessions) currentUser.emit(user)
      return user
    }

    override suspend fun signOut() {
      signOutCalls++
      signOutAction()
      if (publishSessions) currentUser.emit(null)
    }
  }

  private class FakeProfileRepository : ProfileRepository {
    val getProfileUserIds = mutableListOf<String>()
    val createProfileUserIds = mutableListOf<String>()
    val cancelledUserIds = mutableListOf<String>()
    var getProfileAction: suspend (String) -> Result<UserProfile?> = { Result.success(null) }
    var createProfileAction: suspend (String) -> Result<UserProfile> = {
      Result.success(UserProfile(it))
    }

    override suspend fun getProfile(userId: String): Result<UserProfile?> {
      getProfileUserIds += userId
      return getProfileAction(userId)
    }

    override suspend fun createProfile(userId: String): Result<UserProfile> {
      createProfileUserIds += userId
      return createProfileAction(userId)
    }
  }
}
