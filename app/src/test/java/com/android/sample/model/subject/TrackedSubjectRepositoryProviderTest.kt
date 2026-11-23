// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.google.firebase.auth.FirebaseAuth
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

/** Tests provider default instantiation, override injection, and clearing logic. */
class TrackedSubjectRepositoryProviderTest {
  private lateinit var firebase: MockedStatic<FirebaseFirestore>
  private lateinit var firebaseAuth: MockedStatic<FirebaseAuth>

  @Before
  fun setUp() {
    TrackedSubjectRepositoryProvider.setRepository(null)
    val firestore = mock(FirebaseFirestore::class.java)
    val auth = mock(FirebaseAuth::class.java)
    `when`(firestore.collection("users")).thenReturn(mock(CollectionReference::class.java))
    firebase = mockStatic(FirebaseFirestore::class.java)
    firebase.`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }.thenReturn(firestore)
    firebaseAuth = mockStatic(FirebaseAuth::class.java)
    firebaseAuth.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(auth)
  }

  @After
  fun tearDown() {
    TrackedSubjectRepositoryProvider.setRepository(null)
    firebase.close()
    firebaseAuth.close()
  }

  /** Production default: one Firestore repository, created on first use and then shared. */
  @Test
  fun withoutOverride_createsOneFirestoreRepositoryAndSharesIt() {
    val first = TrackedSubjectRepositoryProvider.getRepository()

    assertTrue(first is TrackedSubjectRepositoryFirestore)
    assertSame(first, TrackedSubjectRepositoryProvider.getRepository())
    firebase.verify({ FirebaseFirestore.getInstance() }, times(1))
  }

  /** An injected repository is returned on every call, and Firestore is never initialized. */
  @Test
  fun override_isReturnedOnEveryCallWithoutTouchingFirestore() {
    val injected = mock(TrackedSubjectRepository::class.java)

    TrackedSubjectRepositoryProvider.setRepository(injected)

    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
    firebase.verifyNoInteractions()
    firebaseAuth.verifyNoInteractions()
  }

  /** An override also replaces a default that was already created. */
  @Test
  fun override_replacesAnAlreadyCreatedDefault() {
    TrackedSubjectRepositoryProvider.getRepository()
    val injected = mock(TrackedSubjectRepository::class.java)

    TrackedSubjectRepositoryProvider.setRepository(injected)

    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
  }

  /** Clearing an override restores the production default instead of keeping the fake. */
  @Test
  fun clearingOverride_restoresAFirestoreRepository() {
    val injected = mock(TrackedSubjectRepository::class.java)
    TrackedSubjectRepositoryProvider.setRepository(injected)

    TrackedSubjectRepositoryProvider.setRepository(null)

    val restored = TrackedSubjectRepositoryProvider.getRepository()
    assertTrue(restored is TrackedSubjectRepositoryFirestore)
    assertNotSame(injected, restored)
  }

  /** Clearing also discards a cached default, so the next call builds a fresh one. */
  @Test
  fun clearing_discardsTheCachedDefault() {
    val first = TrackedSubjectRepositoryProvider.getRepository()

    TrackedSubjectRepositoryProvider.setRepository(null)

    val second = TrackedSubjectRepositoryProvider.getRepository()
    assertTrue(second is TrackedSubjectRepositoryFirestore)
    assertNotSame(first, second)
  }
}
