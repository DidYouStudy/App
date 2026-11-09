// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.preferences

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * Verifies the provider's production default and its test override: an injected repository is
 * returned without touching Firestore, and clearing it brings back a fresh Firestore repository.
 */
class PreferencesRepositoryProviderTest {
  private lateinit var firebase: MockedStatic<FirebaseFirestore>

  /** Starts from no override and stubs Firestore, so the default needs no Firebase app. */
  @Before
  fun setUp() {
    PreferencesRepositoryProvider.setRepository(null)
    val firestore = mock(FirebaseFirestore::class.java)
    `when`(firestore.collection("preferences")).thenReturn(mock(CollectionReference::class.java))
    firebase = mockStatic(FirebaseFirestore::class.java)
    firebase.`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }.thenReturn(firestore)
  }

  /** Clears the override, so no repository leaks into the next test. */
  @After
  fun tearDown() {
    PreferencesRepositoryProvider.setRepository(null)
    firebase.close()
  }

  /** Production default: one Firestore repository, created on first use and then shared. */
  @Test
  fun withoutOverride_createsOneFirestoreRepositoryAndSharesIt() {
    val first = PreferencesRepositoryProvider.getRepository()

    assertTrue(first is PreferencesRepositoryFirestore)
    assertSame(first, PreferencesRepositoryProvider.getRepository())
    firebase.verify({ FirebaseFirestore.getInstance() }, times(1))
  }

  /** An injected repository is returned on every call, and Firestore is never initialized. */
  @Test
  fun override_isReturnedOnEveryCallWithoutTouchingFirestore() {
    val injected = mock(PreferencesRepository::class.java)

    PreferencesRepositoryProvider.setRepository(injected)

    assertSame(injected, PreferencesRepositoryProvider.getRepository())
    assertSame(injected, PreferencesRepositoryProvider.getRepository())
    firebase.verifyNoInteractions()
  }

  /** An override also replaces a default that was already created. */
  @Test
  fun override_replacesAnAlreadyCreatedDefault() {
    PreferencesRepositoryProvider.getRepository()
    val injected = mock(PreferencesRepository::class.java)

    PreferencesRepositoryProvider.setRepository(injected)

    assertSame(injected, PreferencesRepositoryProvider.getRepository())
  }

  /** Clearing an override restores the production default instead of keeping the fake. */
  @Test
  fun clearingOverride_restoresAFirestoreRepository() {
    val injected = mock(PreferencesRepository::class.java)
    PreferencesRepositoryProvider.setRepository(injected)

    PreferencesRepositoryProvider.setRepository(null)

    val restored = PreferencesRepositoryProvider.getRepository()
    assertTrue(restored is PreferencesRepositoryFirestore)
    assertNotSame(injected, restored)
  }

  /** Clearing also discards a cached default, so the next call builds a fresh one. */
  @Test
  fun clearing_discardsTheCachedDefault() {
    val first = PreferencesRepositoryProvider.getRepository()

    PreferencesRepositoryProvider.setRepository(null)

    val second = PreferencesRepositoryProvider.getRepository()
    assertTrue(second is PreferencesRepositoryFirestore)
    assertNotSame(first, second)
  }
}
