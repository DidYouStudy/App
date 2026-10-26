// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

/** Application wiring; screens and ViewModels receive the repository interface. */
object EventsRepositoryProvider {
  private val instance: EventsRepository by lazy { EventsRepositoryFirestore() }

  /** Returns the process-shared repository without changing its authenticated user. */
  fun getRepository(): EventsRepository = instance
}
