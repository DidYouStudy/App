// Co-authored-by: Codex AI Agent
package com.android.sample.model.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner

/**
 * Tests the provider lifecycle without resetting its private singleton or initializing Firebase.
 */
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryProviderTest {
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
}
