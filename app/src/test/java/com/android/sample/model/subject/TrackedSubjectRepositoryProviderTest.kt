// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class TrackedSubjectRepositoryProviderTest {

  @Before
  fun setUp() {
    TrackedSubjectRepositoryProvider.setRepository(null)
  }

  @After
  fun tearDown() {
    TrackedSubjectRepositoryProvider.setRepository(null)
  }

  @Test
  fun withoutOverride_createsOneInMemoryRepositoryAndSharesIt() {
    val first = TrackedSubjectRepositoryProvider.getRepository()

    assertTrue(first is InMemoryTrackedSubjectRepository)
    assertSame(first, TrackedSubjectRepositoryProvider.getRepository())
  }

  @Test
  fun override_isReturnedOnEveryCall() {
    val injected = mock(TrackedSubjectRepository::class.java)

    TrackedSubjectRepositoryProvider.setRepository(injected)

    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
  }

  @Test
  fun override_replacesAnAlreadyCreatedDefault() {
    TrackedSubjectRepositoryProvider.getRepository()
    val injected = mock(TrackedSubjectRepository::class.java)

    TrackedSubjectRepositoryProvider.setRepository(injected)

    assertSame(injected, TrackedSubjectRepositoryProvider.getRepository())
  }

  @Test
  fun clearingOverride_restoresAnInMemoryRepository() {
    val injected = mock(TrackedSubjectRepository::class.java)
    TrackedSubjectRepositoryProvider.setRepository(injected)

    TrackedSubjectRepositoryProvider.setRepository(null)

    val restored = TrackedSubjectRepositoryProvider.getRepository()
    assertTrue(restored is InMemoryTrackedSubjectRepository)
    assertNotSame(injected, restored)
  }

  @Test
  fun clearing_discardsTheCachedDefault() {
    val first = TrackedSubjectRepositoryProvider.getRepository()

    TrackedSubjectRepositoryProvider.setRepository(null)

    val second = TrackedSubjectRepositoryProvider.getRepository()
    assertTrue(second is InMemoryTrackedSubjectRepository)
    assertNotSame(first, second)
  }
}
