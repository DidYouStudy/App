// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.feedback

import androidx.annotation.VisibleForTesting

/**
 * Provides the shared repository for personal session feedback.
 *
 * This is in-memory for part 1; part 2 adds Firestore without eagerly creating its default.
 */
object SessionFeedbackRepositoryProvider {
  private var repository: SessionFeedbackRepository? = null

  /** Returns the shared repository instance, creating the default on first use. */
  @Synchronized
  fun getRepository(): SessionFeedbackRepository =
      repository ?: InMemorySessionFeedbackRepository().also { repository = it }

  /** Replaces the shared repository for tests; null restores lazy default creation. */
  @VisibleForTesting
  @Synchronized
  fun setRepository(newRepository: SessionFeedbackRepository?) {
    repository = newRepository
  }
}
