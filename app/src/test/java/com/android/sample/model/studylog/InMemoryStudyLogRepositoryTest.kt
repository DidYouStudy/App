// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

/** Runs the study log repository contract against the in-memory implementation. */
class InMemoryStudyLogRepositoryTest : StudyLogRepositoryContractTest() {
  override fun createRepository(): StudyLogRepository = InMemoryStudyLogRepository()
}
