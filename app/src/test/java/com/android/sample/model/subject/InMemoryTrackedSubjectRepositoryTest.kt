// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

/** Runs contract tests against [InMemoryTrackedSubjectRepository]. */
class InMemoryTrackedSubjectRepositoryTest : TrackedSubjectRepositoryContractTest() {
  override fun createRepository(): TrackedSubjectRepository = InMemoryTrackedSubjectRepository()
}
