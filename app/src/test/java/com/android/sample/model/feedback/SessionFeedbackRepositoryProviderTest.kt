// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests the shared in-memory repository provider used until Firestore is added. */
class SessionFeedbackRepositoryProviderTest {
  @Before
  fun setUp() {
    SessionFeedbackRepositoryProvider.setRepository(null)
  }

  @After
  fun tearDown() {
    SessionFeedbackRepositoryProvider.setRepository(null)
  }

  /** The default is one shared in-memory repository. */
  @Test
  fun defaultRepository_isSharedInMemoryInstance() {
    val first = SessionFeedbackRepositoryProvider.getRepository()

    assertTrue(first is InMemorySessionFeedbackRepository)
    assertSame(first, SessionFeedbackRepositoryProvider.getRepository())
  }

  /** An injected repository is returned and setRepository returns the same instance. */
  @Test
  fun setRepository_returnsAndSharesInjectedRepository() {
    val injected = InMemorySessionFeedbackRepository()

    assertSame(injected, SessionFeedbackRepositoryProvider.setRepository(injected))
    assertSame(injected, SessionFeedbackRepositoryProvider.getRepository())
  }

  /** Clearing the provider creates a fresh default repository. */
  @Test
  fun clearingRepository_createsFreshDefault() {
    val injected = InMemorySessionFeedbackRepository()
    SessionFeedbackRepositoryProvider.setRepository(injected)

    val replacement = SessionFeedbackRepositoryProvider.setRepository(null)

    assertNotSame(injected, replacement)
    assertTrue(replacement is InMemorySessionFeedbackRepository)
    assertSame(replacement, SessionFeedbackRepositoryProvider.getRepository())
  }
}
