// Co-authored-by: Copilot
// Co-authored-by: Claude Opus 5.5
package com.android.sample.model.profile

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

class ProfileRepositoryProviderTest {
  @Before
  fun clearBefore() {
    setInstance(null)
  }

  @After
  fun clearAfter() {
    setInstance(null)
  }

  // Returns the same cached repository across repeated calls.
  @Test
  fun getRepository_cachesRepositoryAcrossCalls() {
    val firestore = mock(FirebaseFirestore::class.java)
    val collection = mock(CollectionReference::class.java)
    `when`(firestore.collection("profiles")).thenReturn(collection)

    mockStatic(FirebaseFirestore::class.java).use { firebase ->
      firebase.`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }.thenReturn(firestore)

      val first = ProfileRepositoryProvider.getRepository()
      val second = ProfileRepositoryProvider.getRepository()

      assertSame(first, second)
      firebase.verify({ FirebaseFirestore.getInstance() }, times(1))
    }
  }

  /** Without an override, the production default is the Firestore repository. */
  @Test
  fun getRepository_withoutOverride_returnsFirestoreRepository() = withStubbedFirestore {
    assertTrue(ProfileRepositoryProvider.getRepository() is ProfileRepositoryFirestore)
  }

  /** An injected repository is returned on every call, and Firestore is never initialized. */
  @Test
  fun setRepository_overrideIsReturnedWithoutTouchingFirestore() =
      withStubbedFirestore { firebase ->
        val injected = mock(ProfileRepository::class.java)

        ProfileRepositoryProvider.setRepository(injected)

        assertSame(injected, ProfileRepositoryProvider.getRepository())
        assertSame(injected, ProfileRepositoryProvider.getRepository())
        firebase.verifyNoInteractions()
      }

  /** An override also replaces a default that was already created. */
  @Test
  fun setRepository_replacesAnAlreadyCreatedDefault() = withStubbedFirestore {
    ProfileRepositoryProvider.getRepository()
    val injected = mock(ProfileRepository::class.java)

    ProfileRepositoryProvider.setRepository(injected)

    assertSame(injected, ProfileRepositoryProvider.getRepository())
  }

  /** Clearing the override makes the next call build a fresh Firestore repository. */
  @Test
  fun setRepositoryNull_restoresAFreshFirestoreRepository() = withStubbedFirestore {
    val cachedDefault = ProfileRepositoryProvider.getRepository()
    val injected = mock(ProfileRepository::class.java)
    ProfileRepositoryProvider.setRepository(injected)

    ProfileRepositoryProvider.setRepository(null)

    val restored = ProfileRepositoryProvider.getRepository()
    assertTrue(restored is ProfileRepositoryFirestore)
    assertNotSame(injected, restored)
    assertNotSame(cachedDefault, restored)
  }

  /** Runs [block] with `FirebaseFirestore.getInstance()` returning a mock, never a real app. */
  private fun withStubbedFirestore(block: (MockedStatic<FirebaseFirestore>) -> Unit) {
    val firestore = mock(FirebaseFirestore::class.java)
    `when`(firestore.collection("profiles")).thenReturn(mock(CollectionReference::class.java))
    mockStatic(FirebaseFirestore::class.java).use { firebase ->
      firebase.`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }.thenReturn(firestore)
      block(firebase)
    }
  }

  private fun setInstance(repository: ProfileRepository?) {
    ProfileRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(null, repository)
    }
  }
}
