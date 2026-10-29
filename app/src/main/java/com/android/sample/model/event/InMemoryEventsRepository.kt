// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
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
  /** A subject's latest spelling and when it was last used. */
  private data class SubjectUse(val spelling: String, val usedAt: Instant)

  /**
   * One user's data, mirroring `users/{ownerId}/events` and the `users/{ownerId}/meta/subjects`
   * summary document.
   *
   * @property events events keyed by ID.
   * @property subjects subject uses keyed by trimmed, lowercase subject.
   */
  private data class OwnerData(
      val events: Map<String, Event> = emptyMap(),
      val subjects: Map<String, SubjectUse> = emptyMap(),
  )

  /** All users' data keyed by owner ID, updated atomically like a Firestore batch write. */
  private val data = MutableStateFlow<Map<String, OwnerData>>(emptyMap())

  override fun observeEvents(
      ownerId: String,
      from: Instant,
      to: Instant,
  ): Flow<Result<List<Event>>> {
    if (ownerId.isBlank() || !to.isAfter(from)) return flowOf(invalidArgument())
    return data
        .map { byOwner ->
          byOwner[ownerId]
              ?.events
              .orEmpty()
              .values
              .filter { it.startsAt.isBefore(to) && it.endsAt.isAfter(from) }
              .sortedWith(compareBy<Event>({ it.startsAt }, { it.endsAt }, { it.id }))
        }
        .distinctUntilChanged()
        .map { Result.success(it) }
  }

  override fun observeSubjects(ownerId: String): Flow<Result<List<String>>> {
    if (ownerId.isBlank()) return flowOf(invalidArgument())
    return data
        .map { byOwner ->
          byOwner[ownerId]
              ?.subjects
              .orEmpty()
              .values
              .sortedWith(compareByDescending<SubjectUse> { it.usedAt }.thenBy { it.spelling })
              .map { it.spelling }
        }
        .distinctUntilChanged()
        .map { Result.success(it) }
  }

  override fun newEventId(): String = UUID.randomUUID().toString()

  override suspend fun getEvent(ownerId: String, eventId: String): Result<Event> {
    if (ownerId.isBlank() || eventId.isBlank()) return invalidArgument()
    val event =
        data.value[ownerId]?.events?.get(eventId)
            ?: return Result.failure(EventsException(EventsError.NOT_FOUND))
    return Result.success(event)
  }

  override suspend fun getImportedEvents(ownerId: String): Result<List<Event>> {
    if (ownerId.isBlank()) return invalidArgument()
    val imported =
        data.value[ownerId]
            ?.events
            .orEmpty()
            .values
            .filter { it.importKey != null }
            .sortedWith(compareBy<Event>({ it.startsAt }, { it.endsAt }, { it.id }))
    return Result.success(imported)
  }

  override suspend fun saveEvent(event: Event): Result<Unit> {
    data.update { byOwner ->
      val owned = byOwner[event.ownerId] ?: OwnerData()
      val subject = event.subject?.trim()
      val subjects =
          if (subject == null) owned.subjects
          else owned.subjects + (subject.lowercase() to SubjectUse(subject, event.updatedAt))
      byOwner + (event.ownerId to OwnerData(owned.events + (event.id to event), subjects))
    }
    return Result.success(Unit)
  }

  override suspend fun saveEvents(events: List<Event>): Result<Unit> {
    if (events.isEmpty()) return Result.success(Unit)
    if (events.any { it.ownerId != events.first().ownerId }) return invalidArgument()

    data.update { byOwner ->
      var currentMap = byOwner
      for (event in events) {
        val owned = currentMap[event.ownerId] ?: OwnerData()
        val subject = event.subject?.trim()
        val subjects =
            if (subject == null) owned.subjects
            else owned.subjects + (subject.lowercase() to SubjectUse(subject, event.updatedAt))
        currentMap =
            currentMap + (event.ownerId to OwnerData(owned.events + (event.id to event), subjects))
      }
      currentMap
    }
    return Result.success(Unit)
  }

  override suspend fun deleteEvent(ownerId: String, eventId: String): Result<Unit> {
    if (ownerId.isBlank() || eventId.isBlank()) return invalidArgument()
    data.update { byOwner ->
      val owned = byOwner[ownerId] ?: return@update byOwner
      byOwner + (ownerId to owned.copy(events = owned.events - eventId))
    }
    return Result.success(Unit)
  }

  /** Failure returned when the caller passes a blank ID or an empty time range. */
  private fun <T> invalidArgument(): Result<T> =
      Result.failure(EventsException(EventsError.INVALID_ARGUMENT))
}
