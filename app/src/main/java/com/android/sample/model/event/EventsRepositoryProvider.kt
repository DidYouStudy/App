// Co-authored-by: Claude Sonnet 5.5
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

/** Provides a shared repository while allowing callers to replace it for tests. */
object EventsRepositoryProvider {
  private var repository: EventsRepository? = null

  /** Returns the shared Firestore repository, creating it on first use. */
  @Synchronized
  fun getRepository(): EventsRepository =
      repository ?: EventsRepositoryFirestore().also { repository = it }

  /** Replaces the shared repository; null makes the next call create a new default. */
  @Synchronized
  fun setRepository(newRepository: EventsRepository?) {
    repository = newRepository
  }
}
