// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a course schedule (.ics file) into a user's calendar through an [EventsRepository],
 * replacing what an earlier import created.
 *
 * Each imported event keeps the file's `UID` as its [Event.importKey]. On a new import, events with
 * a known key are updated in place (same ID, creation time and tasks), new ones are added, and
 * events of an earlier import that are missing from the file are deleted. Events the user created
 * by hand have no import key and are never touched.
 */
class ScheduleImporter(private val repository: EventsRepository) {

  /**
   * Parses the schedule opened by [openStream] and replaces the owner's previously imported events
   * with its events.
   *
   * New events are saved before stale ones are deleted, so a failure never leaves the calendar
   * emptier than before.
   *
   * @param openStream opens the file; called off the main thread, and the stream is closed here.
   *   Returning null (e.g. the file vanished) counts as a failure.
   * @return [Result.success] with the number of events in the file, or [Result.failure] with an
   *   [EventsException]: [EventsError.INVALID_ARGUMENT] when [ownerId] is blank, the file cannot be
   *   read, or it contains no valid event (nothing is changed then); otherwise the repository's own
   *   failure. Cancellation is propagated.
   */
  suspend fun import(ownerId: String, openStream: () -> InputStream?): Result<Int> {
    if (ownerId.isBlank()) return Result.failure(EventsException(EventsError.INVALID_ARGUMENT))

    val parsed =
        try {
          withContext(Dispatchers.IO) {
            val stream = openStream() ?: throw IOException("Cannot open the schedule file")
            stream.use { IcsParser.parseCalendar(it, ownerId) { repository.newEventId() } }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          return Result.failure(EventsException(EventsError.INVALID_ARGUMENT, e))
        }

    // A file without any usable event is almost certainly the wrong file: tell the user.
    if (parsed.isEmpty()) return Result.failure(EventsException(EventsError.INVALID_ARGUMENT))

    val existing =
        repository.getImportedEvents(ownerId).getOrElse {
          return Result.failure(it)
        }
    val existingByKey = existing.associateBy { it.importKey }

    val incoming = withUniqueKeys(parsed)
    val toSave = incoming.map { event ->
      // Reuse the stored event's identity so the update does not duplicate it.
      existingByKey[event.importKey]?.let {
        event.copy(id = it.id, createdAt = it.createdAt, tasks = it.tasks)
      } ?: event
    }
    repository.saveEvents(toSave).onFailure {
      return Result.failure(it)
    }

    val keptKeys = incoming.map { it.importKey }.toSet()
    for (stale in existing.filter { it.importKey !in keptKeys }) {
      repository.deleteEvent(ownerId, stale.id).onFailure {
        return Result.failure(it)
      }
    }
    return Result.success(toSave.size)
  }

  private companion object {
    /**
     * Gives every event a distinct import key. A missing `UID` is replaced by subject and start;
     * recurring entries sharing a `UID` get their start time appended; remaining exact duplicates
     * collapse to the last one.
     */
    fun withUniqueKeys(events: List<Event>): List<Event> {
      val keyed = events.map {
        it.copy(importKey = it.importKey ?: "${it.subject}@${it.startsAt.epochSecond}")
      }
      val counts = keyed.groupingBy { it.importKey }.eachCount()
      return keyed
          .map {
            if (counts.getValue(it.importKey) > 1) {
              it.copy(importKey = "${it.importKey}@${it.startsAt.epochSecond}")
            } else it
          }
          .associateBy { it.importKey }
          .values
          .toList()
    }
  }
}
