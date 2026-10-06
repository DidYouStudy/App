// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.ClearCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner

/**
 * A fake, unsigned token in JWT shape (header.payload.signature). googleid parses the payload of
 * every ID token, so arbitrary strings are rejected.
 */
private const val FAKE_ID_TOKEN =
    "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiJzdHVkZW50QGV4YW1wbGUuY29tIn0.c2ln"

/** Exercises real Google request/token parsing with a mocked platform picker. */
@RunWith(RobolectricTestRunner::class)
class GoogleSignInHelperTest {
  private val manager = mock(CredentialManager::class.java)
  private val activity = mock(Activity::class.java)
  private val helper = GoogleSignInHelper(manager)
  private val requestedContexts = mutableListOf<Context>()
  private val requests = mutableListOf<GetCredentialRequest>()

  @Test
  fun requestsGoogleSignInWithWebClientAndReturnsTokenFromCredentialBundle() = runTest {
    val credential =
        GoogleIdTokenCredential.Builder()
            .setId("student@example.com")
            .setIdToken(FAKE_ID_TOKEN)
            .build()
    // Use the public bundle format rather than mocking Google's parser.
    respondWith(CustomCredential(credential.type, credential.data))
    assertEquals(FAKE_ID_TOKEN, helper.getIdToken(activity, "web-client-id"))
    assertSame(activity, requestedContexts.single())
    val request = requests.single()
    val options = request.credentialOptions
    assertEquals(1, options.size)
    assertTrue(options.single() is GetSignInWithGoogleOption)
    val option = options.single() as GetSignInWithGoogleOption
    assertEquals("web-client-id", option.serverClientId)
    assertNull(option.nonce)
    assertNull(option.hostedDomainFilter)
    assertNull(request.origin)
    verify(manager, never()).clearCredentialState(anyNonNull<ClearCredentialStateRequest>())
  }

  @Test
  fun usesActivitySuppliedForEachRequest() = runTest {
    val credential = GoogleIdTokenCredential.Builder().setId("id").setIdToken(FAKE_ID_TOKEN).build()
    respondWith(credential)
    val nextActivity = mock(Activity::class.java)
    helper.getIdToken(activity, "web-client")
    helper.getIdToken(nextActivity, "web-client")
    assertEquals(2, requestedContexts.size)
    assertSame(activity, requestedContexts[0])
    assertSame(nextActivity, requestedContexts[1])
  }

  @Test
  fun rejectsBlankClientIdBeforeOpeningPicker() = runTest {
    for (clientId in listOf("", " ", "\t\n")) {
      assertTrue(failure { helper.getIdToken(activity, clientId) } is IllegalArgumentException)
    }
    verifyNoInteractions(manager)
  }

  @Test
  fun rejectsNonCustomCredential() = runTest {
    respondWith(PasswordCredential("id", "password"))
    assertEquals(AuthError.UNKNOWN, authFailure { helper.getIdToken(activity, "web-client") }.error)
  }

  @Test
  fun rejectsUnsupportedCustomCredentialType() = runTest {
    respondWith(CustomCredential("unsupported-provider", Bundle()))
    assertEquals(AuthError.UNKNOWN, authFailure { helper.getIdToken(activity, "web-client") }.error)
  }

  @Test
  fun malformedGoogleCredentialReportsParsingFailure() = runTest {
    respondWith(CustomCredential(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL, Bundle()))
    val failure = authFailure { helper.getIdToken(activity, "web-client") }
    assertEquals(AuthError.UNKNOWN, failure.error)
    assertTrue(failure.cause is GoogleIdTokenParsingException)
  }

  @Test
  fun mapsPickerFailuresToAuthErrorsAndRetainsCause() = runTest {
    val cases =
        listOf(
            GetCredentialCancellationException("dismissed") to AuthError.CANCELLED,
            NoCredentialException("no account") to AuthError.NO_CREDENTIAL,
            GetCredentialUnknownException("picker unavailable") to AuthError.UNKNOWN,
        )
    for ((cause, expected) in cases) {
      pickerThrows(cause)
      val failure = authFailure { helper.getIdToken(activity, "web-client") }
      assertEquals(expected, failure.error)
      assertSame(cause, failure.cause)
    }
  }

  @Test
  fun coroutineCancellationPropagatesWithoutWrapping() = runTest {
    val cancellation = CancellationException("screen destroyed")
    pickerThrows(cancellation)
    assertSame(cancellation, failure { helper.getIdToken(activity, "web-client") })
  }

  @Test
  fun clearsProviderSessionWithStandardRequest() = runTest {
    val clearedRequests = mutableListOf<ClearCredentialStateRequest>()
    doAnswer { invocation ->
          clearedRequests += invocation.getArgument<ClearCredentialStateRequest>(0)
          Unit
        }
        .`when`(manager)
        .clearCredentialState(anyNonNull<ClearCredentialStateRequest>())
    helper.clearCredentialState()
    assertEquals(
        ClearCredentialStateRequest.TYPE_CLEAR_CREDENTIAL_STATE,
        clearedRequests.single().requestType,
    )
    verify(manager, never())
        .getCredential(anyNonNull<Context>(), anyNonNull<GetCredentialRequest>())
  }

  @Test
  fun cleanupFailureAndCancellationPropagateWithoutWrapping() = runTest {
    for (cause in
        listOf(
            ClearCredentialUnknownException("cleanup unavailable"),
            CancellationException("cancelled"),
        )) {
      doAnswer { throw cause }
          .`when`(manager)
          .clearCredentialState(anyNonNull<ClearCredentialStateRequest>())
      assertSame(cause, failure { helper.clearCredentialState() })
    }
  }

  // Java Mockito matchers return null placeholders; Kotlin's non-null SDK parameters reject them.
  // The placeholder does not affect matching.
  private inline fun <reified T : Any> anyNonNull(): T = any(T::class.java) ?: matcherPlaceholder()

  // The erased generic cast prevents caller-side Kotlin null checks. These values are used only
  // in intercepted Mockito stubbing/verification calls, never passed to a real SDK method.
  @Suppress("UNCHECKED_CAST") private fun <T> matcherPlaceholder(): T = null as T

  private suspend fun respondWith(credential: Credential) {
    doAnswer { invocation ->
          requestedContexts += invocation.getArgument<Context>(0)
          requests += invocation.getArgument<GetCredentialRequest>(1)
          GetCredentialResponse(credential)
        }
        .`when`(manager)
        .getCredential(anyNonNull<Context>(), anyNonNull<GetCredentialRequest>())
  }

  private suspend fun pickerThrows(cause: Exception) {
    doAnswer { throw cause }
        .`when`(manager)
        .getCredential(anyNonNull<Context>(), anyNonNull<GetCredentialRequest>())
  }

  private suspend fun authFailure(action: suspend () -> Unit): AuthException =
      failure(action) as? AuthException ?: throw AssertionError("Expected an AuthException")

  private suspend fun failure(action: suspend () -> Unit): Throwable {
    try {
      action()
    } catch (failure: Exception) {
      return failure
    }
    throw AssertionError("Expected a failure")
  }
}
