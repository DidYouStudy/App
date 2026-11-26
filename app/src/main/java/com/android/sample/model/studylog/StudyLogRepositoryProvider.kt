// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.android.sample.model.studylog

import androidx.annotation.VisibleForTesting

/**
 * Provides the shared repository for personal session study log.
 *
 * This is in-memory for part 1; part 2 adds Firestore without eagerly creating its default.
 */
object StudyLogRepositoryProvider {
  private var repository: StudyLogRepository? = null

  /** Returns the shared repository instance, creating the default on first use. */
  @Synchronized
  fun getRepository(): StudyLogRepository =
      repository ?: InMemoryStudyLogRepository().also { repository = it }

  /** Replaces the shared repository for tests; null restores lazy default creation. */
  @VisibleForTesting
  @Synchronized
  fun setRepository(newRepository: StudyLogRepository?) {
    repository = newRepository
  }
}
