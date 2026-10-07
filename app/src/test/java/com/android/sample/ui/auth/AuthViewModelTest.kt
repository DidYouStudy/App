// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.android.sample.model.auth.AuthError
import com.android.sample.model.auth.AuthException
import com.android.sample.model.auth.AuthRepository
import com.android.sample.model.auth.UserAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Contract tests use a controllable repository, without Firebase or Android services. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = FakeAuthRepository()
  private val store = ViewModelStore()
  private lateinit var viewModel: AuthViewModel
  private val alice = UserAccount("alice", "alice@example.com", "Alice", null)
  private val bob = UserAccount("bob", null, null, null)

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    viewModel = AuthViewModel(repository)
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
        assertEquals(AuthUiState(), viewModel.uiState.value)
        runCurrent()
        assertTrue(viewModel.uiState.value.isInitializing)
        session(null)
        assertEquals(AuthUiState(isInitializing = false), viewModel.uiState.value)
      }

  @Test
  fun observesRestoredSessionAccountChangesAndExternalSignOut() =
      runTest(dispatcher) {
        session(alice)
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.uiState.value)
        session(bob)
        assertEquals(bob, viewModel.uiState.value.user)
        session(null)
        assertEquals(AuthUiState(isInitializing = false), viewModel.uiState.value)
      }

  @Test
  fun signInForwardsTokenButWaitsForSessionEventToPublishIdentity() =
      runTest(dispatcher) {
        val result = CompletableDeferred<UserAccount>()
        repository.publishSessions = false
        repository.signIn = { result.await() }
        viewModel.signInWithGoogle { "google-token" }
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        assertEquals(listOf("google-token"), repository.tokens)
        result.complete(alice)
        runCurrent()
        assertEquals(AuthUiState(), viewModel.uiState.value)
        session(alice)
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.uiState.value)
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
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals(alice, viewModel.uiState.value.user)
        completion.complete(Unit)
        runCurrent()
        assertEquals(1, repository.signOutCalls)
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.uiState.value)
        session(null)
        assertEquals(AuthUiState(isInitializing = false), viewModel.uiState.value)
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
        assertEquals(AuthUiState(user = bob, isInitializing = false), viewModel.uiState.value)
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
        assertEquals(AuthUiState(user = bob, isInitializing = false), viewModel.uiState.value)
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
        assertFalse(viewModel.uiState.value.isLoading)
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
              viewModel.uiState.value,
          )
          repository.signOutAction = { throw AuthException(error) }
          viewModel.signOut()
          runCurrent()
          assertEquals(
              AuthUiState(user = alice, isInitializing = false, error = error),
              viewModel.uiState.value,
          )
        }
      }

  @Test
  fun pickerFailuresShareErrorStateAndDismissalShowsNothing() =
      runTest(dispatcher) {
        session(alice)
        viewModel.signInWithGoogle { throw AuthException(AuthError.CANCELLED) }
        runCurrent()
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.uiState.value)
        viewModel.signInWithGoogle { throw AuthException(AuthError.NO_CREDENTIAL) }
        runCurrent()
        assertEquals(
            AuthUiState(user = alice, isInitializing = false, error = AuthError.NO_CREDENTIAL),
            viewModel.uiState.value,
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
        assertEquals(AuthError.UNKNOWN, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        repository.signOutAction = { throw IllegalStateException("private diagnostic") }
        viewModel.signOut()
        runCurrent()
        assertEquals(AuthError.UNKNOWN, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
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
        assertNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        result.complete(alice)
        runCurrent()
        assertEquals(listOf("first", "retry"), repository.tokens)
        assertEquals(AuthUiState(user = alice, isInitializing = false), viewModel.uiState.value)
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
            viewModel.uiState.value,
        )
        result.completeExceptionally(AuthException(AuthError.NETWORK))
        runCurrent()
        session(bob)
        assertEquals(
            AuthUiState(user = bob, isInitializing = false, error = AuthError.NETWORK),
            viewModel.uiState.value,
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
        assertEquals(AuthError.NETWORK, viewModel.uiState.value.error)
        repository.signOutAction = {
          repository.currentUser.emit(null)
          throw AuthException(AuthError.CREDENTIAL_STATE_CLEAR_FAILED)
        }
        viewModel.signOut()
        runCurrent()
        assertEquals(AuthUiState(isInitializing = false), viewModel.uiState.value)
      }

  @Test
  fun clearErrorOnlyDismissesErrorAndIsIdempotent() =
      runTest(dispatcher) {
        session(alice)
        repository.signIn = { throw AuthException(AuthError.NETWORK) }
        viewModel.signInWithGoogle { "token" }
        runCurrent()
        val expected = viewModel.uiState.value.copy(error = null)
        viewModel.clearError()
        viewModel.clearError()
        assertEquals(expected, viewModel.uiState.value)
      }

  @Test
  fun cancellationFromInsideOperationIsReportedAsUnknownAndAllowsRetry() =
      runTest(dispatcher) {
        session(alice)
        val unknown = AuthUiState(user = alice, isInitializing = false, error = AuthError.UNKNOWN)
        viewModel.signInWithGoogle { throw CancellationException("picker cancelled") }
        runCurrent()
        assertEquals(unknown, viewModel.uiState.value)
        repository.signIn = { throw CancellationException("task cancelled") }
        viewModel.signInWithGoogle { "token" }
        runCurrent()
        assertEquals(unknown, viewModel.uiState.value)
        repository.signOutAction = { throw CancellationException("task cancelled") }
        viewModel.signOut()
        runCurrent()
        assertEquals(unknown, viewModel.uiState.value)
        repository.signIn = { bob }
        viewModel.signInWithGoogle { "retry" }
        runCurrent()
        assertEquals(bob, viewModel.uiState.value.user)
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
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
      }

  @Test
  fun factoryInjectsRepositoryAndRejectsUnrelatedTypes() =
      runTest(dispatcher) {
        val factory = AuthViewModelFactory(repository)
        val created = factory.create(AuthViewModel::class.java)
        store.put("factory", created)
        session(alice)
        assertEquals(alice, created.uiState.value.user)
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
}
