// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.event

/**
 * Gives every screen the same [EventsRepository], so that the calendar, the dashboard and the
 * add-activity flow see the same events.
 *
 * It defaults to an [InMemoryEventsRepository] until the Firestore implementation exists. When it
 * does, call [setRepository] once at app start (for example in `Application.onCreate`) with the
 * offline-capable implementation, or return it from [getRepository] instead. Tests can call
 * [setRepository] with a fake, and with `null` to start from a fresh default.
 */
object EventsRepositoryProvider {
  private var repository: EventsRepository? = null

  /** Returns the shared repository, creating the default one on first use. */
  @Synchronized
  fun getRepository(): EventsRepository =
      repository ?: InMemoryEventsRepository().also { repository = it }

  /** Replaces the shared repository; `null` drops it so the next call creates the default. */
  @Synchronized
  fun setRepository(newRepository: EventsRepository?) {
    repository = newRepository
  }
}
