// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

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

/** Verifies the default repository, singleton behavior, and test-time repository replacement. */
class EventsRepositoryProviderTest {

  private lateinit var firestore: MockedStatic<FirebaseFirestore>
  private lateinit var auth: MockedStatic<FirebaseAuth>

  /** Supplies Firebase dependencies without initializing a real Firebase app. */
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

  /** Restores the provider's default behavior so repository state cannot leak between tests. */
  @After
  fun tearDown() {
    EventsRepositoryProvider.setRepository(null)
    auth.close()
    firestore.close()
  }

  /** The provider creates an Firestore repository when no override has been configured. */
  @Test
  fun getRepository_defaultsToFirestoreRepository() {
    EventsRepositoryProvider.setRepository(null)

    assertTrue(EventsRepositoryProvider.getRepository() is EventsRepositoryFirestore)
  }

  /** Repeated lookups return the same shared repository instance. */
  @Test
  fun getRepository_returnsTheSameInstanceOnEveryCall() {
    EventsRepositoryProvider.setRepository(null)

    assertSame(EventsRepositoryProvider.getRepository(), EventsRepositoryProvider.getRepository())
  }

  /** A repository configured for tests replaces the provider's shared instance. */
  @Test
  fun setRepository_replacesTheSharedRepository() {
    val replacement = mock(EventsRepository::class.java)

    EventsRepositoryProvider.setRepository(replacement)

    assertSame(replacement, EventsRepositoryProvider.getRepository())
  }

  /** Clearing an override causes the provider to create a fresh default repository. */
  @Test
  fun setRepositoryToNull_makesNextCallCreateAFreshDefault() {
    val first = EventsRepositoryProvider.getRepository()

    EventsRepositoryProvider.setRepository(null)

    assertNotSame(first, EventsRepositoryProvider.getRepository())
  }
}
