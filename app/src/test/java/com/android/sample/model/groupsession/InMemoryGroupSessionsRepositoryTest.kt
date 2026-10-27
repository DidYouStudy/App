// Co-authored-by: GitHub Copilot
package com.android.sample.model.groupsession

/** Runs the [GroupSessionsRepository] contract against the in-memory implementation. */
class InMemoryGroupSessionsRepositoryTest : GroupSessionsRepositoryContractTest() {
  /** Creates a fresh in-memory implementation for a contract test. */
  override fun createRepository(): GroupSessionsRepository = InMemoryGroupSessionsRepository()
}
