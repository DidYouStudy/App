// Co-authored-by: GitHub Copilot
package com.android.sample.model.groupsession

/** App-wide group sessions repository: in memory until the Firestore implementation exists. */
object GroupSessionsRepositoryProvider {
  private var instance: GroupSessionsRepository? = null

  /**
   * Returns the shared in-memory repository, creating it on the first call.
   *
   * Subsequent calls return the same repository instance.
   */
  @Synchronized
  fun getRepository(): GroupSessionsRepository {
    instance?.let {
      return it
    }
    return InMemoryGroupSessionsRepository().also { instance = it }
  }
}
