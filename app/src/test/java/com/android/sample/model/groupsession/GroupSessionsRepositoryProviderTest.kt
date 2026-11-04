// Co-authored-by: GitHub Copilot
package com.android.sample.model.groupsession

import com.google.firebase.auth.FirebaseAuth
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

/** Verifies the Firestore default, singleton behavior, and test-time repository replacement. */
class GroupSessionsRepositoryProviderTest {
  private lateinit var firestore: MockedStatic<FirebaseFirestore>
  private lateinit var auth: MockedStatic<FirebaseAuth>

  @Before
  fun setUp() {
    firestore = mockStatic(FirebaseFirestore::class.java)
    auth = mockStatic(FirebaseAuth::class.java)
    firestore
        .`when`<FirebaseFirestore> { FirebaseFirestore.getInstance() }
        .thenReturn(mock(FirebaseFirestore::class.java))
    auth
        .`when`<FirebaseAuth> { FirebaseAuth.getInstance() }
        .thenReturn(mock(FirebaseAuth::class.java))
  }

  @After
  fun tearDown() {
    GroupSessionsRepositoryProvider.setRepository(null)
    auth.close()
    firestore.close()
  }

  /** The provider defaults to its Firestore implementation. */
  @Test
  fun getRepository_defaultsToFirestoreRepository() {
    GroupSessionsRepositoryProvider.setRepository(null)

    assertTrue(GroupSessionsRepositoryProvider.getRepository() is GroupSessionsRepositoryFirestore)
  }

  /** Repeated lookups return the same shared repository instance. */
  @Test
  fun getRepository_returnsTheSameInstanceOnEveryCall() {
    GroupSessionsRepositoryProvider.setRepository(null)

    assertSame(
        GroupSessionsRepositoryProvider.getRepository(),
        GroupSessionsRepositoryProvider.getRepository(),
    )
  }

  /** A test repository replaces the shared instance until cleared. */
  @Test
  fun setRepository_replacesTheSharedRepository() {
    val replacement = mock(GroupSessionsRepository::class.java)

    GroupSessionsRepositoryProvider.setRepository(replacement)

    assertSame(replacement, GroupSessionsRepositoryProvider.getRepository())
  }

  /** Clearing the override causes the next lookup to create a fresh default. */
  @Test
  fun setRepositoryToNull_restoresTheDefault() {
    val first = GroupSessionsRepositoryProvider.getRepository()

    GroupSessionsRepositoryProvider.setRepository(null)

    assertNotSame(first, GroupSessionsRepositoryProvider.getRepository())
  }
}
