// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.event

import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/** Verifies the default repository, singleton behavior, and test-time repository replacement. */
class EventsRepositoryProviderTest {

  /** Restores the provider's default behavior so repository state cannot leak between tests. */
  @After
  fun tearDown() {
    EventsRepositoryProvider.setRepository(null)
  }

  /** The provider creates an in-memory repository when no override has been configured. */
  @Test
  fun getRepository_defaultsToInMemoryRepository() {
    EventsRepositoryProvider.setRepository(null)

    assertTrue(EventsRepositoryProvider.getRepository() is InMemoryEventsRepository)
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
