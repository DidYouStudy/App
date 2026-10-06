// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import android.net.Uri
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner

/**
 * Firebase is mocked; these tests never initialize Firebase or contact the authentication server.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryFirebaseTest {
  private lateinit var auth: FirebaseAuth
  private lateinit var repository: AuthRepositoryFirebase
  private var cleanup: suspend () -> Unit = {}
  private val account =
      UserAccount("uid", "alice@example.com", "Alice", "https://example.com/photo")

  @Before
  fun setUp() {
    auth = mock(FirebaseAuth::class.java)
    repository = AuthRepositoryFirebase(auth) { cleanup() }
  }

  @Test
  fun sessionFlowIsLazyReportsRestoredUserAndRemovesListenerOnCancellation() = runTest {
    val user = firebaseUser(account)
    `when`(auth.currentUser).thenReturn(user)
    doAnswer { invocation ->
          invocation.getArgument<FirebaseAuth.AuthStateListener>(0).onAuthStateChanged(auth)
          null
        }
        .`when`(auth)
        .addAuthStateListener(any(FirebaseAuth.AuthStateListener::class.java))
    verifyNoInteractions(auth)
    val emissions = mutableListOf<UserAccount?>()
    val collection =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.currentUser.collect { emissions += it }
        }
    runCurrent()
    assertEquals(listOf(account), emissions)
    val captor = ArgumentCaptor.forClass(FirebaseAuth.AuthStateListener::class.java)
    verify(auth).addAuthStateListener(captor.capture())
    `when`(auth.currentUser).thenReturn(null)
    captor.value.onAuthStateChanged(auth)
    runCurrent()
    assertEquals(listOf(account, null), emissions)
    collection.cancelAndJoin()
    verify(auth).removeAuthStateListener(captor.value)
  }

  @Test
  fun signedOutInitialSessionAndDuplicateNotificationsAreHandled() = runTest {
    val emissions = mutableListOf<UserAccount?>()
    val collection =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          repository.currentUser.collect { emissions += it }
        }
    runCurrent()
    val captor = ArgumentCaptor.forClass(FirebaseAuth.AuthStateListener::class.java)
    verify(auth).addAuthStateListener(captor.capture())
    repeat(2) {
      captor.value.onAuthStateChanged(auth)
      runCurrent()
    }
    val initialUser = firebaseUser(account)
    `when`(auth.currentUser).thenReturn(initialUser)
    captor.value.onAuthStateChanged(auth)
    runCurrent()
    // A different SDK object with the same public identity should not produce a duplicate.
    val equivalentUser = firebaseUser(account)
    `when`(auth.currentUser).thenReturn(equivalentUser)
    captor.value.onAuthStateChanged(auth)
    runCurrent()
    assertEquals(listOf(null, account), emissions)
    collection.cancelAndJoin()
    verify(auth).removeAuthStateListener(captor.value)
  }

  @Test
  fun collectorsOwnIndependentListenersAndCanResubscribe() = runTest {
    val first =
        launch(UnconfinedTestDispatcher(testScheduler)) { repository.currentUser.collect {} }
    val second =
        launch(UnconfinedTestDispatcher(testScheduler)) { repository.currentUser.collect {} }
    runCurrent()
    val captor = ArgumentCaptor.forClass(FirebaseAuth.AuthStateListener::class.java)
    verify(auth, times(2)).addAuthStateListener(captor.capture())
    assertNotSame(captor.allValues[0], captor.allValues[1])
    first.cancelAndJoin()
    verify(auth).removeAuthStateListener(captor.allValues[0])
    verify(auth, never()).removeAuthStateListener(captor.allValues[1])
    second.cancelAndJoin()
    val third =
        launch(UnconfinedTestDispatcher(testScheduler)) { repository.currentUser.collect {} }
    runCurrent()
    verify(auth, times(3)).addAuthStateListener(captor.capture())
    third.cancelAndJoin()
    verify(auth, times(3)).removeAuthStateListener(any(FirebaseAuth.AuthStateListener::class.java))
  }

  @Test
  fun rejectsEmptyAndWhitespaceTokensWithoutCallingFirebase() = runTest {
    for (token in listOf("", " ", "\t\n")) {
      val failure = failure { repository.signInWithGoogle(token) }
      assertEquals(AuthError.INVALID_CREDENTIAL, failure.error)
    }
    verifyNoInteractions(auth)
  }

  @Test
  fun exchangesGoogleTokenAndReturnsPublicIdentity() = runTest {
    successfulSignIn(account)
    assertEquals(account, repository.signInWithGoogle("google-id-token"))
    val captor = ArgumentCaptor.forClass(AuthCredential::class.java)
    verify(auth).signInWithCredential(captor.capture())
    val credential = captor.value as GoogleAuthCredential
    assertEquals("google.com", credential.provider)
    assertEquals("google.com", credential.signInMethod)
  }

  @Test
  fun forwardsExactIdTokenToGoogleCredentialExchange() = runTest {
    val credential = mock(AuthCredential::class.java)
    mockStatic(GoogleAuthProvider::class.java).use { provider ->
      provider
          .`when`<AuthCredential> { GoogleAuthProvider.getCredential("exact-token", null) }
          .thenReturn(credential)
      successfulSignIn(account)
      assertEquals(account, repository.signInWithGoogle("exact-token"))
      provider.verify { GoogleAuthProvider.getCredential("exact-token", null) }
      verify(auth).signInWithCredential(credential)
    }
  }

  @Test
  fun firebaseCancellingItsOwnTaskIsReportedAsUnknownFailure() = runTest {
    `when`(auth.signInWithCredential(any(AuthCredential::class.java)))
        .thenReturn(Tasks.forCanceled())
    val failure = failure { repository.signInWithGoogle("token") }
    // The caller was not cancelled, so the UI must see a failure rather than silence.
    assertEquals(AuthError.UNKNOWN, failure.error)
    assertTrue(failure.cause is CancellationException)
  }

  @Test
  fun callerCancellationPropagatesWithoutAnAuthError() = runTest {
    val task = TaskCompletionSource<AuthResult>()
    `when`(auth.signInWithCredential(any(AuthCredential::class.java))).thenReturn(task.task)
    val request = async { repository.signInWithGoogle("token") }
    runCurrent()
    request.cancelAndJoin()
    assertTrue(request.isCancelled)
    assertTrue(request.getCompletionExceptionOrNull() !is AuthException)
  }

  @Test
  fun optionalProfileFieldsMayAllBeAbsent() = runTest {
    val minimal = UserAccount("uid", null, null, null)
    successfulSignIn(minimal)
    assertEquals(minimal, repository.signInWithGoogle("token"))
  }

  @Test
  fun successfulTaskWithoutUserIsUnknownFailure() = runTest {
    val result = mock(AuthResult::class.java)
    `when`(auth.signInWithCredential(any(AuthCredential::class.java)))
        .thenReturn(Tasks.forResult(result))
    assertEquals(AuthError.UNKNOWN, failure { repository.signInWithGoogle("token") }.error)
  }

  @Test
  fun mapsSdkFailuresAndRetainsDiagnosticCause() = runTest {
    val cases =
        listOf(
            FirebaseNetworkException("offline") to AuthError.NETWORK,
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad token") to
                AuthError.INVALID_CREDENTIAL,
            FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled") to
                AuthError.USER_DISABLED,
            FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "deleted") to
                AuthError.INVALID_CREDENTIAL,
            IllegalStateException("unexpected") to AuthError.UNKNOWN,
        )
    for ((cause, expected) in cases) {
      `when`(auth.signInWithCredential(any(AuthCredential::class.java)))
          .thenReturn(Tasks.forException(cause))
      val failure = failure { repository.signInWithGoogle("token") }
      assertEquals(expected, failure.error)
      assertSame(cause, failure.cause)
    }
  }

  @Test
  fun synchronousSdkFailureIsAlsoTranslated() = runTest {
    val cause = IllegalStateException("SDK unavailable")
    `when`(auth.signInWithCredential(any(AuthCredential::class.java))).thenThrow(cause)
    val failure = failure { repository.signInWithGoogle("token") }
    assertEquals(AuthError.UNKNOWN, failure.error)
    assertSame(cause, failure.cause)
  }

  @Test
  fun signOutEndsFirebaseSessionBeforeProviderCleanup() = runTest {
    var cleanupCalls = 0
    cleanup = {
      verify(auth).signOut()
      cleanupCalls++
    }
    repository.signOut()
    assertEquals(1, cleanupCalls)
    verify(auth, times(1)).signOut()
  }

  @Test
  fun cleanupFailureRetainsCauseAndAllowsRetry() = runTest {
    val cause = IllegalStateException("provider unavailable")
    cleanup = {
      verify(auth).signOut()
      throw cause
    }
    val failure = failure { repository.signOut() }
    assertEquals(AuthError.CREDENTIAL_STATE_CLEAR_FAILED, failure.error)
    assertSame(cause, failure.cause)
    cleanup = {}
    repository.signOut()
    verify(auth, times(2)).signOut()
  }

  @Test
  fun cleanupCancellationPropagatesWithoutWrapping() = runTest {
    val cancellation = CancellationException("cancelled")
    cleanup = { throw cancellation }
    try {
      repository.signOut()
      fail("Expected cancellation")
    } catch (actual: CancellationException) {
      assertSame(cancellation, actual)
    }
    cleanup = {}
    repository.signOut()
    verify(auth, times(2)).signOut()
  }

  @Test
  fun signOutWaitsForPendingSignIn() = runTest {
    val task = TaskCompletionSource<AuthResult>()
    `when`(auth.signInWithCredential(any(AuthCredential::class.java))).thenReturn(task.task)
    val signIn = async { repository.signInWithGoogle("token") }
    runCurrent()
    val signOut = async { repository.signOut() }
    runCurrent()
    // Sign-out must not run before the pending sign-in finishes, or the sign-in would undo it.
    verify(auth, never()).signOut()
    val result = mock(AuthResult::class.java)
    val user = firebaseUser(account)
    `when`(result.user).thenReturn(user)
    task.setResult(result)
    runCurrent()
    assertEquals(account, signIn.await())
    signOut.await()
    verify(auth).signOut()
  }

  @Test
  fun signInWaitsForPendingSignOut() = runTest {
    val cleanupDone = CompletableDeferred<Unit>()
    cleanup = { cleanupDone.await() }
    val signOut = async { repository.signOut() }
    runCurrent()
    successfulSignIn(account)
    val signIn = async { repository.signInWithGoogle("token") }
    runCurrent()
    verify(auth, never()).signInWithCredential(any(AuthCredential::class.java))
    cleanupDone.complete(Unit)
    runCurrent()
    signOut.await()
    assertEquals(account, signIn.await())
  }

  private fun firebaseUser(account: UserAccount): FirebaseUser {
    val user = mock(FirebaseUser::class.java)
    `when`(user.uid).thenReturn(account.uid)
    `when`(user.email).thenReturn(account.email)
    `when`(user.displayName).thenReturn(account.displayName)
    `when`(user.photoUrl).thenReturn(account.photoUrl?.let(Uri::parse))
    return user
  }

  private fun successfulSignIn(account: UserAccount) {
    val result = mock(AuthResult::class.java)
    val user = firebaseUser(account)
    `when`(result.user).thenReturn(user)
    `when`(auth.signInWithCredential(any(AuthCredential::class.java)))
        .thenReturn(Tasks.forResult(result))
  }

  private suspend fun failure(action: suspend () -> Unit): AuthException {
    try {
      action()
    } catch (exception: AuthException) {
      return exception
    }
    throw AssertionError("Expected an AuthException")
  }
}
