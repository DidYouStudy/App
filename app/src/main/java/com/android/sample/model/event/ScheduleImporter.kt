// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports a course schedule (.ics file) into a user's calendar through an [EventsRepository],
 * replacing what an earlier import created.
 *
 * Each imported event gets an [Event.importKey] made of the file's `UID` and the event's start. On
 * a new import, events with a known key are updated in place (same ID, creation time and tasks),
 * new ones are added, and events of an earlier import that are missing from the file are deleted.
 * An occurrence that moves to another time therefore counts as a new event. Events the user created
 * by hand have no import key and are never touched.
 */
class ScheduleImporter(
    private val repository: EventsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  /**
   * Parses the schedule opened by [openStream] and replaces the owner's previously imported events
   * with its events.
   *
   * New events are saved before stale ones are deleted, so a failure never leaves the calendar
   * emptier than before. If some entries of the file were malformed and skipped, or the file looks
   * cut off (no closing `END:VCALENDAR`), nothing is deleted: the missing entries may be ones that
   * were imported earlier, and removing them would lose their tasks for a mere parsing problem.
   *
   * @param openStream opens the file; called off the main thread, and the stream is closed here.
   *   Returning null (e.g. the file vanished) counts as a failure.
   * @return [Result.success] with an [ImportSummary] (events imported and entries skipped), or
   *   [Result.failure] with an [EventsException]: [EventsError.INVALID_ARGUMENT] when [ownerId] is
   *   blank, the file cannot be read, or it contains no valid event (nothing is changed then);
   *   otherwise the repository's own failure. Cancellation is propagated.
   */
  suspend fun import(ownerId: String, openStream: () -> InputStream?): Result<ImportSummary> {
    if (ownerId.isBlank()) return Result.failure(EventsException(EventsError.INVALID_ARGUMENT))

    val report =
        try {
          withContext(ioDispatcher) {
            val stream = openStream() ?: throw IOException("Cannot open the schedule file")
            stream.use {
              IcsParser.parseCalendarWithReport(it, ownerId) { repository.newEventId() }
            }
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          return Result.failure(EventsException(EventsError.INVALID_ARGUMENT, e))
        }

    // A file without any usable event is almost certainly the wrong file: tell the user.
    if (report.events.isEmpty()) {
      return Result.failure(EventsException(EventsError.INVALID_ARGUMENT))
    }

    val existing =
        repository.getImportedEvents(ownerId).getOrElse {
          return Result.failure(it)
        }
    val existingByKey = existing.associateBy { it.importKey }

    val incoming = withStableKeys(report.events)
    val toSave = incoming.map { event ->
      // Reuse the stored event's identity so the update does not duplicate it.
      existingByKey[event.importKey]?.let {
        event.copy(id = it.id, createdAt = it.createdAt, tasks = it.tasks)
      } ?: event
    }
    repository.saveEvents(toSave).onFailure {
      return Result.failure(it)
    }

    // Only prune when the whole file was understood (see the KDoc).
    if (report.isComplete) {
      val keptKeys = incoming.map { it.importKey }.toSet()
      for (stale in existing.filter { it.importKey !in keptKeys }) {
        repository.deleteEvent(ownerId, stale.id).onFailure {
          return Result.failure(it)
        }
      }
    }
    return Result.success(
        ImportSummary(
            imported = toSave.size,
            skipped = report.skipped,
            incomplete = report.missingCalendarEnd,
        )
    )
  }

  private companion object {
    /**
     * Gives every event a key that depends only on the event itself: its `UID` (or, when missing,
     * its subject) plus its start time. Recurring entries share a `UID`, so the start time is what
     * tells the occurrences apart; because it is always appended, cancelling one occurrence never
     * changes the key of its siblings. Exact duplicates collapse to the last one.
     */
    fun withStableKeys(events: List<Event>): List<Event> =
        events
            .map { it.copy(importKey = "${it.importKey ?: it.subject}@${it.startsAt.epochSecond}") }
            .associateBy { it.importKey }
            .values
            .toList()
  }
}

/**
 * Outcome of an import: [imported] events were stored, [skipped] malformed entries were ignored,
 * and [incomplete] tells that the file lacks its closing `END:VCALENDAR` line (probably cut off).
 */
data class ImportSummary(val imported: Int, val skipped: Int, val incomplete: Boolean = false)
