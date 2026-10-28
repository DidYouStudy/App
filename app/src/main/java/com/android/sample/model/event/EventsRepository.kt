// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Stores a user's personal [Event]s without exposing Firestore to ViewModels or screens.
 *
 * Failures are returned as [Result.failure] containing an [EventsException]; cancellation is
 * propagated rather than converted into a failed [Result].
 *
 * Writes complete once the event is stored on the device, without waiting for the server, so they
 * also work offline; the storage syncs them later. Group sessions are not part of this repository.
 */
interface EventsRepository {
  /**
   * Observes the owner's events overlapping `[from, to)`, sorted by start, then end, then ID.
   *
   * An event overlaps when it starts before [to] and ends after [from]: an event crossing midnight
   * appears on both days, and an event ending exactly at [from] does not appear. The flow emits the
   * current list, then again after every change.
   *
   * @return a flow emitting [Result.failure] with [EventsError.INVALID_ARGUMENT] when [ownerId] is
   *   blank or [to] is not after [from].
   */
  fun observeEvents(ownerId: String, from: Instant, to: Instant): Flow<Result<List<Event>>>

  /**
   * Returns a new unique event ID, available offline, to build an [Event] before saving it.
   *
   * Saving again with the same ID replaces the event, so a retried save never creates a duplicate.
   */
  fun newEventId(): String

  /**
   * Looks up one of the owner's events, e.g. for the edit screen.
   *
   * @return [Result.failure] with [EventsError.NOT_FOUND] when the owner has no event with this ID.
   */
  suspend fun getEvent(ownerId: String, eventId: String): Result<Event>

  /**
   * Observes the subjects the owner has used, most recently used first, for the form's suggestions.
   *
   * Subjects are trimmed and compared ignoring case; the spelling of the latest save is kept, and
   * its [Event.updatedAt] decides the order. Deleting events does not remove their subjects. The
   * flow emits the current list, then again after every change.
   *
   * @return a flow emitting [Result.failure] with [EventsError.INVALID_ARGUMENT] when [ownerId] is
   *   blank.
   */
  fun observeSubjects(ownerId: String): Flow<Result<List<String>>>

  /**
   * Returns every event of the owner that was imported from a schedule file (has an
   * [Event.importKey]), whatever its date, so a new import can update or remove them.
   *
   * @return [Result.failure] with [EventsError.INVALID_ARGUMENT] when [ownerId] is blank.
   */
  suspend fun getImportedEvents(ownerId: String): Result<List<Event>>

  /** Creates the event, or replaces the owner's event with the same ID, and records its subject. */
  suspend fun saveEvent(event: Event): Result<Unit>

  /**
   * Saves multiple events atomically or in batch, recording their subjects.
   *
   * @return [Result.success] when all events are saved, or [Result.failure] if validation or
   *   writing fails.
   */
  suspend fun saveEvents(events: List<Event>): Result<Unit>

  /** Deletes the owner's event; deleting an event that does not exist also succeeds. */
  suspend fun deleteEvent(ownerId: String, eventId: String): Result<Unit>
}
