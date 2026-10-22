// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * [EventsRepository] kept in memory, for ViewModel tests and for building screens before the
 * Firestore implementation exists. Data is lost when the process ends.
 */
class InMemoryEventsRepository : EventsRepository {
  /** Events keyed by owner ID, then by event ID, mirroring `users/{ownerId}/calendarEvents`. */
  private val events = MutableStateFlow<Map<String, Map<String, Event>>>(emptyMap())

  override fun observeEvents(
      ownerId: String,
      from: Instant,
      to: Instant,
  ): Flow<Result<List<Event>>> {
    if (ownerId.isBlank() || !to.isAfter(from)) return flowOf(invalidArgument())
    return events
        .map { byOwner ->
          byOwner[ownerId]
              .orEmpty()
              .values
              .filter { it.startsAt.isBefore(to) && it.endsAt.isAfter(from) }
              .sortedWith(compareBy<Event>({ it.startsAt }, { it.endsAt }, { it.id }))
        }
        .distinctUntilChanged()
        .map { Result.success(it) }
  }

  override fun newEventId(): String = UUID.randomUUID().toString()

  override suspend fun getEvent(ownerId: String, eventId: String): Result<Event> {
    if (ownerId.isBlank() || eventId.isBlank()) return invalidArgument()
    val event =
        events.value[ownerId]?.get(eventId)
            ?: return Result.failure(EventsException(EventsError.NOT_FOUND))
    return Result.success(event)
  }

  override suspend fun saveEvent(event: Event): Result<Unit> {
    events.update { byOwner ->
      byOwner + (event.ownerId to (byOwner[event.ownerId].orEmpty() + (event.id to event)))
    }
    return Result.success(Unit)
  }

  override suspend fun deleteEvent(ownerId: String, eventId: String): Result<Unit> {
    if (ownerId.isBlank() || eventId.isBlank()) return invalidArgument()
    events.update { byOwner ->
      val owned = byOwner[ownerId] ?: return@update byOwner
      byOwner + (ownerId to (owned - eventId))
    }
    return Result.success(Unit)
  }

  /** Failure returned when the caller passes a blank ID or an empty time range. */
  private fun <T> invalidArgument(): Result<T> =
      Result.failure(EventsException(EventsError.INVALID_ARGUMENT))
}
