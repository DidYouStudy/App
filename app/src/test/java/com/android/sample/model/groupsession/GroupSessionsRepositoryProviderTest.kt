// Co-authored-by: GitHub Copilot
package com.android.sample.model.groupsession

import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Verifies that the app-wide group session provider caches its initial in-memory repository. */
class GroupSessionsRepositoryProviderTest {
  @Before
  fun clearBefore() {
    setInstance(null)
  }

  @After
  fun clearAfter() {
    setInstance(null)
  }

  /** The provider returns one shared in-memory implementation for every call. */
  @Test
  fun getRepositoryCreatesAndCachesInMemoryRepository() {
    val first = GroupSessionsRepositoryProvider.getRepository()
    val second = GroupSessionsRepositoryProvider.getRepository()

    assertTrue(first is InMemoryGroupSessionsRepository)
    assertSame(first, second)
  }

  private fun setInstance(repository: GroupSessionsRepository?) {
    GroupSessionsRepositoryProvider::class.java.getDeclaredField("instance").apply {
      isAccessible = true
      set(GroupSessionsRepositoryProvider, repository)
    }
  }
}
