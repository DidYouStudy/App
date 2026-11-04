// Co-authored-by: GitHub Copilot
// Co-authored-by: GitHub Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.groupsession

/** Provides a shared Firestore repository while allowing callers to replace it for tests. */
object GroupSessionsRepositoryProvider {
  private var instance: GroupSessionsRepository? = null

  /** Returns the shared Firestore repository, creating it on first use. */
  @Synchronized
  fun getRepository(): GroupSessionsRepository =
      instance ?: GroupSessionsRepositoryFirestore().also { instance = it }

  /** Replaces the shared repository; null makes the next call create a new default. */
  @Synchronized
  fun setRepository(newRepository: GroupSessionsRepository?) {
    instance = newRepository
  }
}
