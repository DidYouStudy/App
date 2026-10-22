// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

/** Runs the [EventsRepository] contract against the in-memory implementation. */
class InMemoryEventsRepositoryTest : EventsRepositoryContractTest() {
  override fun createRepository(): EventsRepository = InMemoryEventsRepository()
}
