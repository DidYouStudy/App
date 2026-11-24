// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import androidx.annotation.VisibleForTesting

/** Provides a shared repository while allowing callers to replace it for tests. */
object TrackedSubjectRepositoryProvider {
  private var repository: TrackedSubjectRepository? = null

  /** Returns the shared Firestore repository, creating it on first use. */
  @Synchronized
  fun getRepository(): TrackedSubjectRepository =
      repository ?: TrackedSubjectRepositoryFirestore().also { repository = it }

  /** Replaces the shared repository; null makes the next call create a new default. */
  @VisibleForTesting
  @Synchronized
  fun setRepository(newRepository: TrackedSubjectRepository?) {
    repository = newRepository
  }
}
