// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/** Tests provider default instantiation, override injection, and clearing logic. */
class TrackedSubjectRepositoryProviderTest {

  @Before
  fun setUp() {
    TrackedSubjectRepositoryProvider.setRepository(null)
  }

  @After
  fun tearDown() {
    TrackedSubjectRepositoryProvider.setRepository(null)
  }

  /** Without an override, the provider creates and caches a default in-memory repository. */
  @Test
  fun withoutOverride_createsOneInMemoryRepositoryAndSharesIt() {
    val first = TrackedSubjectRepositoryProvider.getRepository()

    assertTrue(first is InMemoryTrackedSubjectRepository)
    assertSame(first, TrackedSubjectRepositoryProvider.getRepository())
  }

  /** An injected override repository is returned on every call. */
  @Test
  fun override_isReturnedOnEveryCall() {
    val injected = mock(TrackedSubjectRepository::class.java)

    TrackedSubjectRepositoryProvider.setRepository(injected)

    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
  }

  /** Setting an override replaces an existing repository default. */
  @Test
  fun override_replacesAnAlreadyCreatedDefault() {
    TrackedSubjectRepositoryProvider.getRepository()
    val injected = mock(TrackedSubjectRepository::class.java)

    TrackedSubjectRepositoryProvider.setRepository(injected)

    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
  }

  /** Clearing an override restores a default in-memory repository instance. */
  @Test
  fun clearingOverride_restoresAnInMemoryRepository() {
    val injected = mock(TrackedSubjectRepository::class.java)
    TrackedSubjectRepositoryProvider.setRepository(injected)

    TrackedSubjectRepositoryProvider.setRepository(null)

    val restored = TrackedSubjectRepositoryProvider.getRepository()
    assertTrue(restored is InMemoryTrackedSubjectRepository)
    assertNotSame(injected, restored)
  }

  /** Clearing discards the cached default so the next call creates a fresh repository. */
  @Test
  fun clearing_discardsTheCachedDefault() {
    val first = TrackedSubjectRepositoryProvider.getRepository()

    TrackedSubjectRepositoryProvider.setRepository(null)

    val second = TrackedSubjectRepositoryProvider.getRepository()
    assertTrue(second is InMemoryTrackedSubjectRepository)
    assertNotSame(first, second)
  }
}
