// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.test.core.app.ApplicationProvider
import com.android.sample.R
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner

/**
 * A fake, unsigned token in JWT shape (header.payload.signature): Google's credential parser reads
 * the payload of every ID token, so arbitrary strings are rejected.
 */
private const val FAKE_ID_TOKEN =
    "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiJzdHVkZW50QGV4YW1wbGUuY29tIn0.c2ln"

/**
 * Tests the provider lifecycle without initializing Firebase. The private singletons are cleared
 * around each test because the Robolectric sandbox shares static state between tests.
 */
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryProviderTest {
  @Before fun clearBefore() = reset()

  @After fun clearAfter() = reset()

  @Test
  fun concurrentCallersWaitForInitializationAndShareOneRepository() {
    val application = ApplicationProvider.getApplicationContext<Context>()
    val caller = mock(Context::class.java)
    `when`(caller.applicationContext).thenReturn(application)
    val auth = mock(FirebaseAuth::class.java)
    val workerCount = 4
    val pool = Executors.newFixedThreadPool(workerCount)
    val workers = mutableListOf<Thread>()
    var pending = emptyList<Future<AuthRepository>>()
    try {
      mockConstruction(GoogleSignInHelper::class.java).use { helpers ->
        mockStatic(FirebaseAuth::class.java).use { firebase ->
          firebase
              .`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
              .thenAnswer {
                // Mockito static mocks are thread-local, so only this thread builds the
                // repository. Workers start while it holds the monitor and must block on it.
                pending =
                    (1..workerCount).map {
                      pool.submit<AuthRepository> {
                        synchronized(workers) { workers += Thread.currentThread() }
                        AuthRepositoryProvider.getRepository(mock(Context::class.java))
                      }
                    }
                awaitBlocked(workers, workerCount)
                auth
              }

          val repository = AuthRepositoryProvider.getRepository(caller)

          assertEquals(workerCount, pending.size)
          pending.forEach { assertSame(repository, it.get(5, TimeUnit.SECONDS)) }
          firebase.verify({ FirebaseAuth.getInstance() }, times(1))
          assertEquals(1, helpers.constructed().size)
        }
      }
    } finally {
      pool.shutdownNow()
    }
  }

  @Test
  fun getRepositoryLocksOnTheProviderObject() {
    val cached = mock(AuthRepository::class.java)
    setInstance(cached)
    val pool = Executors.newSingleThreadExecutor()
    val workers = mutableListOf<Thread>()
    try {
      val pending =
          synchronized(AuthRepositoryProvider) {
            val future =
                pool.submit<AuthRepository> {
                  synchronized(workers) { workers += Thread.currentThread() }
                  AuthRepositoryProvider.getRepository(mock(Context::class.java))
                }
            // Even the cached fast path must wait while another thread holds the monitor.
            awaitBlocked(workers, 1)
            assertFalse(future.isDone)
            future
          }
      assertSame(cached, pending.get(5, TimeUnit.SECONDS))
    } finally {
      pool.shutdownNow()
    }
  }

  @Test
  fun failedInitializationCanRetryAndSuccessfulRepositoryIsSharedWithCleanupWired() = runTest {
    val application = ApplicationProvider.getApplicationContext<Context>()
    val caller = mock(Context::class.java)
    `when`(caller.applicationContext).thenReturn(application)
    val anotherCaller = mock(Context::class.java)
    val auth = mock(FirebaseAuth::class.java)
    val initializationFailure = IllegalStateException("Firebase not initialized")

    mockConstruction(GoogleSignInHelper::class.java).use { helpers ->
      mockStatic(FirebaseAuth::class.java).use { firebase ->
        firebase
            .`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
            .thenThrow(initializationFailure)
            .thenReturn(auth)
        try {
          AuthRepositoryProvider.getRepository(caller)
          fail("Expected initialization failure")
        } catch (actual: IllegalStateException) {
          assertSame(initializationFailure, actual)
        }
        val repository = AuthRepositoryProvider.getRepository(caller)
        assertTrue(repository is AuthRepositoryFirebase)
        assertSame(repository, AuthRepositoryProvider.getRepository(caller))
        assertSame(repository, AuthRepositoryProvider.getRepository(anotherCaller))
        verify(caller, times(1)).applicationContext
        verifyNoInteractions(anotherCaller)
        firebase.verify({ FirebaseAuth.getInstance() }, times(2))
        // Firebase failed before helper construction; the successful retry creates one helper.
        assertEquals(1, helpers.constructed().size)

        repository.signOut()
        verify(auth).signOut()
        verify(helpers.constructed().single()).clearCredentialState()
      }
    }
  }

  @Test
  fun signInPickerAndSignOutCleanupShareOneHelper() = runTest {
    val application = ApplicationProvider.getApplicationContext<Context>()
    val activity = mock(Activity::class.java)
    `when`(activity.applicationContext).thenReturn(application)
    `when`(activity.getString(R.string.default_web_client_id)).thenReturn("web-client")
    val pickerCalls = mutableListOf<List<Any?>>()

    // Records each picker request and returns a token; other calls (sign-out cleanup) do nothing.
    val answer = Answer { invocation ->
      if (invocation.method.name == "getIdToken") {
        pickerCalls += listOf(invocation.mock, invocation.arguments[0], invocation.arguments[1])
        "fake-token"
      } else {
        Unit
      }
    }

    mockConstructionWithAnswer(GoogleSignInHelper::class.java, answer).use { helpers ->
      mockStatic(FirebaseAuth::class.java).use { firebase ->
        val auth = mock(FirebaseAuth::class.java)
        firebase.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(auth)

        assertEquals("fake-token", AuthRepositoryProvider.requestGoogleIdToken(activity))
        assertEquals("fake-token", AuthRepositoryProvider.requestGoogleIdToken(activity))
        AuthRepositoryProvider.getRepository(activity).signOut()

        val helper = helpers.constructed().single()
        assertEquals(List(2) { listOf(helper, activity, "web-client") }, pickerCalls)
        verify(auth).signOut()
        verify(helper).clearCredentialState()
      }
    }
  }

  /** An injected repository is returned without creating FirebaseAuth or a Credential Manager. */
  @Test
  fun injectedRepositoryIsReturnedWithoutTouchingFirebaseOrCredentialManager() {
    val injected = mock(AuthRepository::class.java)
    val caller = mock(Context::class.java)

    mockConstruction(GoogleSignInHelper::class.java).use { helpers ->
      mockStatic(FirebaseAuth::class.java).use { firebase ->
        AuthRepositoryProvider.setRepository(injected)

        assertSame(injected, AuthRepositoryProvider.getRepository(caller))
        assertSame(injected, AuthRepositoryProvider.getRepository(caller))
        firebase.verifyNoInteractions()
        assertTrue(helpers.constructed().isEmpty())
        verifyNoInteractions(caller)
      }
    }
  }

  /** An injected repository also replaces a Firebase repository that was already created. */
  @Test
  fun injectedRepositoryReplacesAnAlreadyCreatedDefault() {
    val application = ApplicationProvider.getApplicationContext<Context>()
    val injected = mock(AuthRepository::class.java)

    mockConstruction(GoogleSignInHelper::class.java).use {
      mockStatic(FirebaseAuth::class.java).use { firebase ->
        firebase
            .`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
            .thenReturn(mock(FirebaseAuth::class.java))
        AuthRepositoryProvider.getRepository(application)

        AuthRepositoryProvider.setRepository(injected)

        assertSame(injected, AuthRepositoryProvider.getRepository(application))
      }
    }
  }

  /** Clearing the injected repository makes the next call build a fresh Firebase repository. */
  @Test
  fun clearingTheInjectedRepositoryRestoresAFreshFirebaseRepository() {
    val application = ApplicationProvider.getApplicationContext<Context>()
    val injected = mock(AuthRepository::class.java)

    mockConstruction(GoogleSignInHelper::class.java).use {
      mockStatic(FirebaseAuth::class.java).use { firebase ->
        firebase
            .`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
            .thenReturn(mock(FirebaseAuth::class.java))
        val cachedDefault = AuthRepositoryProvider.getRepository(application)
        AuthRepositoryProvider.setRepository(injected)

        AuthRepositoryProvider.setRepository(null)

        val restored = AuthRepositoryProvider.getRepository(application)
        assertTrue(restored is AuthRepositoryFirebase)
        assertNotSame(injected, restored)
        assertNotSame(cachedDefault, restored)
      }
    }
  }

  /**
   * An injected Credential Manager answers the account picker, and a repository created afterwards
   * clears credentials through it on sign-out, so sign-in and sign-out stay on one manager.
   */
  @Test
  fun injectedCredentialManagerSuppliesTheTokenAndIsClearedOnSignOut() = runTest {
    val activity = activityWithWebClientId()
    val manager = mock(CredentialManager::class.java)
    val credential =
        GoogleIdTokenCredential.Builder()
            .setId("student@example.com")
            .setIdToken(FAKE_ID_TOKEN)
            .build()
    doAnswer { GetCredentialResponse(CustomCredential(credential.type, credential.data)) }
        .`when`(manager)
        .getCredential(any<Context>(), any<GetCredentialRequest>())

    mockStatic(FirebaseAuth::class.java).use { firebase ->
      val auth = mock(FirebaseAuth::class.java)
      firebase.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(auth)

      AuthRepositoryProvider.setCredentialManager(manager)

      assertEquals(FAKE_ID_TOKEN, AuthRepositoryProvider.requestGoogleIdToken(activity))
      AuthRepositoryProvider.getRepository(activity).signOut()
      verify(auth).signOut()
      verify(manager).clearCredentialState(any<ClearCredentialStateRequest>())
    }
  }

  /** Clearing the injected manager puts the platform Credential Manager back on next use. */
  @Test
  fun clearingTheInjectedCredentialManagerRestoresThePlatformOne() = runTest {
    val activity = activityWithWebClientId()
    val injected = mock(CredentialManager::class.java)
    AuthRepositoryProvider.setCredentialManager(injected)

    AuthRepositoryProvider.setCredentialManager(null)

    mockConstructionWithAnswer(GoogleSignInHelper::class.java, Answer { "platform-token" }).use {
        helpers ->
      assertEquals("platform-token", AuthRepositoryProvider.requestGoogleIdToken(activity))
      // A new helper was built on the platform manager, created from the application context.
      assertEquals(1, helpers.constructed().size)
    }
    verify(activity).applicationContext
    verifyNoInteractions(injected)
  }

  /** An activity whose application context is Robolectric's and whose web client ID is set. */
  private fun activityWithWebClientId(): Activity {
    val activity = mock(Activity::class.java)
    `when`(activity.applicationContext)
        .thenReturn(ApplicationProvider.getApplicationContext<Context>())
    `when`(activity.getString(R.string.default_web_client_id)).thenReturn("web-client")
    return activity
  }

  private fun awaitBlocked(workers: List<Thread>, count: Int) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (
        synchronized(workers) {
          workers.size < count || workers.any { it.state != Thread.State.BLOCKED }
        }
    ) {
      check(System.nanoTime() < deadline) { "Workers never blocked on the provider" }
      Thread.yield()
    }
  }

  private fun setInstance(repository: AuthRepository?) {
    AuthRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(AuthRepositoryProvider, repository)
    }
  }

  private fun reset() {
    setInstance(null)
    AuthRepositoryProvider::class.java.getDeclaredField("helper").apply {
      isAccessible = true
      set(AuthRepositoryProvider, null)
    }
  }
}
