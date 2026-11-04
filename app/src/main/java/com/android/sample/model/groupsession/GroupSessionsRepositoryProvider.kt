// Co-authored-by: GitHub Copilot
package com.android.sample.model.groupsession

/** Provides a shared in-memory repository while allowing callers to replace it for tests. */
object GroupSessionsRepositoryProvider {
  private var instance: GroupSessionsRepository? = null

  /** Returns the shared in-memory repository, creating it on first use. */
  @Synchronized
  fun getRepository(): GroupSessionsRepository =
      instance ?: InMemoryGroupSessionsRepository().also { instance = it }

  /** Replaces the shared repository; null makes the next call create a new default. */
  @Synchronized
  fun setRepository(newRepository: GroupSessionsRepository?) {
    instance = newRepository
  }
}
