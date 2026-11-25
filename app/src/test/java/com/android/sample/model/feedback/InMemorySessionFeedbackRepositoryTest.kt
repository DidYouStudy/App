// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

/** Runs the feedback repository contract against the in-memory implementation. */
class InMemorySessionFeedbackRepositoryTest : SessionFeedbackRepositoryContractTest() {
  override fun createRepository(): SessionFeedbackRepository = InMemorySessionFeedbackRepository()
}
