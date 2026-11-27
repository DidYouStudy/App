// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the shared in-memory repository provider used until Firestore is added. */
class StudyLogRepositoryProviderTest {
  @Before
  fun setUp() {
    StudyLogRepositoryProvider.setRepository(null)
  }

  @After
  fun tearDown() {
    StudyLogRepositoryProvider.setRepository(null)
  }

  /** The default is one shared in-memory repository. */
  @Test
  fun defaultRepository_isSharedInMemoryInstance() {
    val first = StudyLogRepositoryProvider.getRepository()

    assertTrue(first is InMemoryStudyLogRepository)
    assertSame(first, StudyLogRepositoryProvider.getRepository())
  }

  /** An injected repository is returned on every call. */
  @Test
  fun setRepository_sharesInjectedRepository() {
    val injected = InMemoryStudyLogRepository()

    StudyLogRepositoryProvider.setRepository(injected)
    assertSame(injected, StudyLogRepositoryProvider.getRepository())
  }

  /** Clearing the provider creates a fresh default repository. */
  @Test
  fun clearingRepository_createsFreshDefault() {
    val injected = InMemoryStudyLogRepository()
    StudyLogRepositoryProvider.setRepository(injected)

    StudyLogRepositoryProvider.setRepository(null)
    val replacement = StudyLogRepositoryProvider.getRepository()

    assertNotSame(injected, replacement)
    assertTrue(replacement is InMemoryStudyLogRepository)
    assertSame(replacement, StudyLogRepositoryProvider.getRepository())
  }
}
