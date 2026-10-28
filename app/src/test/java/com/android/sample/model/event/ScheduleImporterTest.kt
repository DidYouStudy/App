// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.android.sample.model.event

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleImporterTest {

  private val owner = "user-1"
  private val from = Instant.parse("2026-01-01T00:00:00Z")
  private val to = Instant.parse("2027-01-01T00:00:00Z")

  private fun vevent(uid: String, start: String, summary: String = "Algebra") =
      """
      BEGIN:VEVENT
      UID:$uid
      DTSTAMP:20261010T113334
      SUMMARY:$summary
      CATEGORIES:Cours
      DTSTART;TZID=Europe/Zurich:$start
      DURATION:PT2H
      END:VEVENT
      """
          .trimIndent()

  private fun ics(vararg events: String) =
      "BEGIN:VCALENDAR\nVERSION:2.0\n${events.joinToString("\n")}\nEND:VCALENDAR"

  private fun stream(text: String): () -> InputStream? = {
    ByteArrayInputStream(text.toByteArray())
  }

  /** Records the IDs handed out by [newEventId] so tests can check the importer used them. */
  private class IdRecordingRepository(private val delegate: EventsRepository) :
      EventsRepository by delegate {
    val issuedIds = mutableListOf<String>()

    override fun newEventId(): String = delegate.newEventId().also { issuedIds += it }
  }

  private suspend fun stored(repo: EventsRepository) =
      repo.observeEvents(owner, from, to).first().getOrThrow()

  @Test
  fun import_savesParsedEventsWithRepositoryIds() = runTest {
    val repo = IdRecordingRepository(InMemoryEventsRepository())
    val text = ics(vevent("a@x", "20261015T080000"), vevent("b@x", "20261016T080000"))

    val result = ScheduleImporter(repo).import(owner, stream(text))

    assertEquals(2, result.getOrThrow())
    assertEquals(2, repo.issuedIds.size)
    assertEquals(repo.issuedIds.toSet(), stored(repo).map { it.id }.toSet())
  }

  @Test
  fun import_keepsManuallyAddedEvents() = runTest {
    val repo = InMemoryEventsRepository()
    val manual =
        Event(
            id = "manual",
            ownerId = owner,
            type = EventType.OTHER,
            subject = null,
            startsAt = Instant.parse("2026-10-20T08:00:00Z"),
            endsAt = Instant.parse("2026-10-20T09:00:00Z"),
            createdAt = from,
        )
    repo.saveEvent(manual).getOrThrow()

    ScheduleImporter(repo).import(owner, stream(ics(vevent("a@x", "20261015T080000"))))

    assertTrue(stored(repo).any { it.id == "manual" })
  }

  @Test
  fun import_eventsSharingUid_getDistinctIds() = runTest {
    val repo = InMemoryEventsRepository()
    val text = ics(vevent("rec@x", "20261015T080000"), vevent("rec@x", "20261022T080000"))

    val result = ScheduleImporter(repo).import(owner, stream(text))

    assertEquals(2, result.getOrThrow())
    assertEquals(2, stored(repo).map { it.id }.toSet().size)
  }

  @Test
  fun import_blankOwner_failsInvalidArgument() = runTest {
    val result = ScheduleImporter(InMemoryEventsRepository()).import(" ", stream(ics()))

    assertEquals(EventsError.INVALID_ARGUMENT, (result.exceptionOrNull() as EventsException).error)
  }

  @Test
  fun import_fileWithoutEvents_failsAndSavesNothing() = runTest {
    val repo = InMemoryEventsRepository()

    val result = ScheduleImporter(repo).import(owner, stream("not a calendar"))

    assertTrue(result.isFailure)
    assertEquals(EventsError.INVALID_ARGUMENT, (result.exceptionOrNull() as EventsException).error)
    assertTrue(stored(repo).isEmpty())
  }

  @Test
  fun import_unopenableFile_fails() = runTest {
    val result = ScheduleImporter(InMemoryEventsRepository()).import(owner) { null }

    assertTrue(result.isFailure)
  }

  @Test
  fun import_readError_fails() = runTest {
    val broken =
        object : InputStream() {
          override fun read(): Int = throw IOException("boom")
        }

    val result = ScheduleImporter(InMemoryEventsRepository()).import(owner) { broken }

    assertFalse(result.isSuccess)
  }

  @Test
  fun import_repositoryFailure_isReturned() = runTest {
    val failing =
        object : EventsRepository by InMemoryEventsRepository() {
          override suspend fun saveEvents(events: List<Event>): Result<Unit> =
              Result.failure(EventsException(EventsError.STORAGE_FAILURE))
        }

    val result =
        ScheduleImporter(failing).import(owner, stream(ics(vevent("a@x", "20261015T080000"))))

    assertEquals(EventsError.STORAGE_FAILURE, (result.exceptionOrNull() as EventsException).error)
  }

  @Test
  fun import_sameFileTwice_doesNotDuplicate() = runTest {
    val repo = InMemoryEventsRepository()
    val importer = ScheduleImporter(repo)
    val text = ics(vevent("a@x", "20261015T080000"), vevent("b@x", "20261016T080000"))

    importer.import(owner, stream(text))
    importer.import(owner, stream(text))

    assertEquals(2, stored(repo).size)
  }

  @Test
  fun import_changedEvent_updatesInPlaceKeepingIdCreationAndTasks() = runTest {
    val repo = InMemoryEventsRepository()
    val importer = ScheduleImporter(repo)
    importer.import(owner, stream(ics(vevent("a@x", "20261015T080000", "Old"))))
    val first = stored(repo).single()
    val task = EventTask("Read chapter 3")
    repo.saveEvent(first.copy(tasks = listOf(task))).getOrThrow()

    importer.import(owner, stream(ics(vevent("a@x", "20261015T100000", "New"))))

    val updated = stored(repo).single()
    assertEquals("New", updated.subject)
    assertEquals(Instant.parse("2026-10-15T08:00:00Z"), updated.startsAt)
    assertEquals(first.id, updated.id)
    assertEquals(first.createdAt, updated.createdAt)
    assertEquals(listOf(task), updated.tasks)
  }

  @Test
  fun import_eventMissingFromNewFile_isDeleted() = runTest {
    val repo = InMemoryEventsRepository()
    val importer = ScheduleImporter(repo)
    importer.import(
        owner,
        stream(ics(vevent("a@x", "20261015T080000"), vevent("b@x", "20261016T080000"))),
    )

    importer.import(owner, stream(ics(vevent("b@x", "20261016T080000"))))

    assertEquals(listOf("b@x"), stored(repo).map { it.importKey })
  }

  @Test
  fun import_reimport_keepsManuallyAddedEvents() = runTest {
    val repo = InMemoryEventsRepository()
    val importer = ScheduleImporter(repo)
    val manual =
        Event(
            id = "manual",
            ownerId = owner,
            type = EventType.OTHER,
            subject = null,
            startsAt = Instant.parse("2026-10-20T08:00:00Z"),
            endsAt = Instant.parse("2026-10-20T09:00:00Z"),
            createdAt = from,
        )
    repo.saveEvent(manual).getOrThrow()
    importer.import(owner, stream(ics(vevent("a@x", "20261015T080000"))))

    importer.import(owner, stream(ics(vevent("b@x", "20261016T080000"))))

    assertEquals(setOf("manual", "b@x"), stored(repo).map { it.importKey ?: it.id }.toSet())
  }

  @Test
  fun import_invalidFile_keepsPreviousImport() = runTest {
    val repo = InMemoryEventsRepository()
    val importer = ScheduleImporter(repo)
    importer.import(owner, stream(ics(vevent("a@x", "20261015T080000"))))

    val result = importer.import(owner, stream("not a calendar"))

    assertTrue(result.isFailure)
    assertEquals(listOf("a@x"), stored(repo).map { it.importKey })
  }

  @Test
  fun import_eventWithoutUid_isStillReplacedOnReimport() = runTest {
    val repo = InMemoryEventsRepository()
    val importer = ScheduleImporter(repo)
    val noUid =
        vevent("x", "20261015T080000")
            .lines()
            .filterNot { it.startsWith("UID:") }
            .joinToString("\n")

    importer.import(owner, stream(ics(noUid)))
    importer.import(owner, stream(ics(noUid)))

    assertEquals(1, stored(repo).size)
  }

  @Test
  fun import_otherUsersImportIsUntouched() = runTest {
    val repo = InMemoryEventsRepository()
    val importer = ScheduleImporter(repo)
    importer.import("user-2", stream(ics(vevent("a@x", "20261015T080000"))))

    importer.import(owner, stream(ics(vevent("b@x", "20261016T080000"))))

    assertEquals(1, repo.getImportedEvents("user-2").getOrThrow().size)
  }

  @Test
  fun import_saveFailure_doesNotDeleteExistingEvents() = runTest {
    val inner = InMemoryEventsRepository()
    ScheduleImporter(inner).import(owner, stream(ics(vevent("a@x", "20261015T080000"))))
    val failing =
        object : EventsRepository by inner {
          override suspend fun saveEvents(events: List<Event>): Result<Unit> =
              Result.failure(EventsException(EventsError.STORAGE_FAILURE))
        }

    val result =
        ScheduleImporter(failing).import(owner, stream(ics(vevent("b@x", "20261016T080000"))))

    assertTrue(result.isFailure)
    assertEquals(listOf("a@x"), stored(inner).map { it.importKey })
  }

  @Test
  fun import_lookupFailure_isReturned() = runTest {
    val failing =
        object : EventsRepository by InMemoryEventsRepository() {
          override suspend fun getImportedEvents(ownerId: String): Result<List<Event>> =
              Result.failure(EventsException(EventsError.PERMISSION_DENIED))
        }

    val result =
        ScheduleImporter(failing).import(owner, stream(ics(vevent("a@x", "20261015T080000"))))

    assertEquals(EventsError.PERMISSION_DENIED, (result.exceptionOrNull() as EventsException).error)
  }

  @Test
  fun import_deleteFailure_isReturned() = runTest {
    val inner = InMemoryEventsRepository()
    ScheduleImporter(inner).import(owner, stream(ics(vevent("a@x", "20261015T080000"))))
    val failing =
        object : EventsRepository by inner {
          override suspend fun deleteEvent(ownerId: String, eventId: String): Result<Unit> =
              Result.failure(EventsException(EventsError.STORAGE_FAILURE))
        }

    val result =
        ScheduleImporter(failing).import(owner, stream(ics(vevent("b@x", "20261016T080000"))))

    assertEquals(EventsError.STORAGE_FAILURE, (result.exceptionOrNull() as EventsException).error)
  }
}
