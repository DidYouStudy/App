// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import androidx.annotation.VisibleForTesting

/** Provides the shared session feedback repository. */
object SessionFeedbackRepositoryProvider {
  private var repository: SessionFeedbackRepository = InMemorySessionFeedbackRepository()

  /** Returns the shared repository instance. */
  @Synchronized fun getRepository(): SessionFeedbackRepository = repository

  /** Replaces the repository for tests; null restores a fresh in-memory repository. */
  @VisibleForTesting
  @Synchronized
  fun setRepository(newRepository: SessionFeedbackRepository?) {
    repository = newRepository ?: InMemorySessionFeedbackRepository()
  }
}
