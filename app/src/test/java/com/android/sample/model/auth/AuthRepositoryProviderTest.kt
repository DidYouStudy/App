// Co-authored-by: Codex AI Agent
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.RobolectricTestRunner

/**
 * Tests the provider lifecycle without initializing Firebase. The private singleton is cleared
 * around each test because the Robolectric sandbox shares static state between tests.
 */
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryProviderTest {
  @Before fun clearBefore() = setInstance(null)

  @After fun clearAfter() = setInstance(null)

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
        verify(caller, times(2)).applicationContext
        verifyNoInteractions(anotherCaller)
        firebase.verify({ FirebaseAuth.getInstance() }, times(2))
        assertEquals(2, helpers.constructed().size)

        // The helper created by the successful attempt must be the one used for logout cleanup.
        repository.signOut()
        verify(auth).signOut()
        verify(helpers.constructed()[1]).clearCredentialState()
        verifyNoInteractions(helpers.constructed()[0])
      }
    }
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
      set(null, repository)
    }
  }
}
