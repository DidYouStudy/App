// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/** Checks error translation and cancellation that cannot depend on emulator timing. */
@OptIn(ExperimentalCoroutinesApi::class)
class EventsRepositoryFirestoreTest {
  private val db: FirebaseFirestore = mock()
  private val auth: FirebaseAuth = mock()
  private val user: FirebaseUser = mock()
  private val users: CollectionReference = mock()
  private val owner: DocumentReference = mock()
  private val events: CollectionReference = mock()
  private val document: DocumentReference = mock()
  private val repository = EventsRepositoryFirestore(db, auth)

  /** Injects an owner and a single document path. */
  @Before
  fun setup() {
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.uid).thenReturn("owner")
    whenever(db.collection("users")).thenReturn(users)
    whenever(users.document("owner")).thenReturn(owner)
    whenever(owner.collection("events")).thenReturn(events)
    whenever(events.document("id")).thenReturn(document)
  }

  /** An empty batch succeeds without accessing Firestore. */
  @Test
  fun saveEmptyBatch() = runTest {
    assertTrue(repository.saveEvents(emptyList()).isSuccess)
    verifyNoInteractions(db)
  }

  /** Multiple events are queued together and complete at the local cache barrier. */
  @Test
  fun savesBatchLocally() = runTest {
    val batch: WriteBatch = mock()
    val query: Query = mock()
    val snapshot: QuerySnapshot = mock()
    val second: DocumentReference = mock()
    val meta: CollectionReference = mock()
    val subjects: DocumentReference = mock()
    val start = Instant.parse("2026-01-01T10:00:00Z")
    val event =
        Event("id", "owner", EventType.COURSE, "Analysis", start, start.plusSeconds(3600), start)
    val other = event.copy(id = "second", subject = "Physics")
    whenever(events.document("second")).thenReturn(second)
    whenever(owner.collection("meta")).thenReturn(meta)
    whenever(meta.document("subjects")).thenReturn(subjects)
    whenever(db.batch()).thenReturn(batch)
    whenever(batch.commit()).thenReturn(TaskCompletionSource<Void>().task)
    whenever(events.whereEqualTo(FieldPath.documentId(), "id")).thenReturn(query)
    whenever(query.get(Source.CACHE)).thenReturn(Tasks.forResult(snapshot))

    assertTrue(repository.saveEvents(listOf(event, other)).isSuccess)
    verify(batch).set(eq(document), any<Map<String, Any>>())
    verify(batch).set(eq(second), any<Map<String, Any>>())
    verify(batch)
        .set(eq(subjects), eq(SubjectCatalogFirestoreMapper.encode(event)!!), any<SetOptions>())
    verify(batch)
        .set(eq(subjects), eq(SubjectCatalogFirestoreMapper.encode(other)!!), any<SetOptions>())
    verify(batch).commit()
  }

  /** A later invalid event prevents the entire batch from reaching Firestore. */
  @Test
  fun rejectsLaterInvalidEventBeforeWriting() = runTest {
    val start = Instant.parse("2026-01-01T10:00:00Z")
    val event = Event("id", "owner", EventType.OTHER, null, start, start.plusSeconds(3600), start)

    assertEquals(
        EventsError.INVALID_ARGUMENT,
        repository.saveEvents(listOf(event, event.copy(id = "invalid/id"))).error(),
    )
    verifyNoInteractions(db)
  }

  /** A batch cannot contain events belonging to different owners. */
  @Test
  fun rejectsMixedOwnersBeforeWriting() = runTest {
    val start = Instant.parse("2026-01-01T10:00:00Z")
    val event = Event("id", "owner", EventType.OTHER, null, start, start.plusSeconds(3600), start)

    assertEquals(
        EventsError.INVALID_ARGUMENT,
        repository.saveEvents(listOf(event, event.copy(id = "second", ownerId = "other"))).error(),
    )
    verifyNoInteractions(db)
  }

  /** A change of account during validation cannot make a mixed-owner batch valid. */
  @Test
  fun rejectsMixedOwnersWhenAuthenticationChanges() = runTest {
    val otherUser: FirebaseUser = mock()
    whenever(otherUser.uid).thenReturn("other")
    whenever(auth.currentUser).thenReturn(user, otherUser)
    val start = Instant.parse("2026-01-01T10:00:00Z")
    val event = Event("id", "owner", EventType.OTHER, null, start, start.plusSeconds(3600), start)

    assertEquals(
        EventsError.INVALID_ARGUMENT,
        repository.saveEvents(listOf(event, event.copy(id = "second", ownerId = "other"))).error(),
    )
    verifyNoInteractions(db)
  }

  /** Revalidates a changed task list in a later event before writing anything. */
  @Test
  fun rejectsInvalidNestedTaskBeforeWriting() = runTest {
    val start = Instant.parse("2026-01-01T10:00:00Z")
    val event = Event("id", "owner", EventType.OTHER, null, start, start.plusSeconds(3600), start)
    val invalidTask: EventTask = mock()
    whenever(invalidTask.text).thenReturn("")
    val tasks = mutableListOf<EventTask>()
    val later = event.copy(id = "second", tasks = tasks)
    tasks.add(invalidTask)

    assertEquals(
        EventsError.INVALID_EVENT_DATA,
        repository.saveEvents(listOf(event, later)).error(),
    )
    verifyNoInteractions(db)
  }

  /** Invalid IDs and wrong owners never reach cached storage. */
  @Test
  fun validatesBeforeAccess() = runTest {
    assertEquals(EventsError.INVALID_ARGUMENT, repository.getEvent("owner", "a/b").error())
    assertEquals(EventsError.INVALID_ARGUMENT, repository.getEvent(" ", "id").error())
    assertEquals(EventsError.PERMISSION_DENIED, repository.getEvent("other", "id").error())
    whenever(auth.currentUser).thenReturn(null)
    assertEquals(EventsError.PERMISSION_DENIED, repository.observeSubjects("owner").first().error())
    verifyNoInteractions(db)
  }

  /** Known absence and SDK permission/network failures retain useful categories. */
  @Test
  fun mapsFailures() = runTest {
    val missing: DocumentSnapshot = mock()
    whenever(document.get()).thenReturn(Tasks.forResult(missing))
    assertEquals(EventsError.NOT_FOUND, repository.getEvent("owner", "id").error())
    whenever(document.get())
        .thenReturn(
            Tasks.forException(
                FirebaseFirestoreException(
                    "denied",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                )
            )
        )
    assertEquals(EventsError.PERMISSION_DENIED, repository.getEvent("owner", "id").error())
    whenever(document.get())
        .thenReturn(
            Tasks.forException(
                FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
            )
        )
    assertEquals(EventsError.STORAGE_FAILURE, repository.getEvent("owner", "id").error())
  }

  /** Non-Firebase failures retain their domain category and original diagnostic cause. */
  @Test
  fun mapsUnexpectedReadFailures() = runTest {
    val failures =
        listOf(
            IllegalArgumentException("invalid query") to EventsError.INVALID_ARGUMENT,
            IllegalStateException("unexpected failure") to EventsError.UNKNOWN,
        )
    failures.forEach { (cause, expected) ->
      whenever(document.get()).thenReturn(Tasks.forException(cause))
      val result = repository.getEvent("owner", "id")
      assertEquals(expected, result.error())
      assertSame(cause, result.exceptionOrNull()?.cause)
    }
  }

  /** Cancellation is propagated instead of converted to an ordinary failure result. */
  @Test
  fun propagatesCancellation() = runTest {
    val cancellation = CancellationException("read cancelled")
    whenever(document.get()).thenReturn(Tasks.forException(cancellation))

    try {
      repository.getEvent("owner", "id")
      fail("Cancellation must be thrown, not returned as a failure")
    } catch (caught: CancellationException) {
      assertSame(cancellation, caught)
    }
  }

  /** Equal snapshots do not emit twice, while a changed subject list still emits. */
  @Test
  fun subjectObservationSuppressesDuplicateValues() = runTest {
    val meta: CollectionReference = mock()
    val subjects: DocumentReference = mock()
    val registration: ListenerRegistration = mock()
    val empty: DocumentSnapshot = mock()
    val changed: DocumentSnapshot = mock()
    val start = Instant.parse("2026-01-01T10:00:00Z")
    val event =
        Event("id", "owner", EventType.COURSE, "Physics", start, start.plusSeconds(3600), start)
    whenever(owner.collection("meta")).thenReturn(meta)
    whenever(meta.document("subjects")).thenReturn(subjects)
    whenever(subjects.addSnapshotListener(any<EventListener<DocumentSnapshot>>()))
        .thenReturn(registration)
    whenever(empty.data).thenReturn(null)
    whenever(changed.data).thenReturn(SubjectCatalogFirestoreMapper.encode(event))
    val results = mutableListOf<Result<List<String>>>()
    val collector = launch { repository.observeSubjects("owner").collect { results.add(it) } }
    runCurrent()
    val callback = argumentCaptor<EventListener<DocumentSnapshot>>()
    verify(subjects).addSnapshotListener(callback.capture())

    callback.firstValue.onEvent(empty, null)
    runCurrent()
    callback.firstValue.onEvent(empty, null)
    runCurrent()
    callback.firstValue.onEvent(changed, null)
    runCurrent()

    assertEquals(listOf(emptyList<String>(), listOf("Physics")), results.map { it.getOrThrow() })
    collector.cancelAndJoin()
  }

  /** A cancelled subject collector removes its SDK and authentication listeners. */
  @Test
  fun removesListeners() = runTest {
    val meta: CollectionReference = mock()
    val subjects: DocumentReference = mock()
    val registration: ListenerRegistration = mock()
    whenever(owner.collection("meta")).thenReturn(meta)
    whenever(meta.document("subjects")).thenReturn(subjects)
    whenever(subjects.addSnapshotListener(any<EventListener<DocumentSnapshot>>()))
        .thenReturn(registration)
    val listener = async { repository.observeSubjects("owner").first() }
    runCurrent()
    listener.cancelAndJoin()
    verify(registration).remove()
    verify(auth).removeAuthStateListener(any())
  }

  /** Signing out terminates an active subscription and removes both registered listeners. */
  @Test
  fun signOutClosesObservationAndRemovesListeners() = runTest {
    val meta: CollectionReference = mock()
    val subjects: DocumentReference = mock()
    val registration: ListenerRegistration = mock()
    whenever(owner.collection("meta")).thenReturn(meta)
    whenever(meta.document("subjects")).thenReturn(subjects)
    whenever(subjects.addSnapshotListener(any<EventListener<DocumentSnapshot>>()))
        .thenReturn(registration)
    val collected = async { repository.observeSubjects("owner").toList() }
    runCurrent()
    val callback = argumentCaptor<FirebaseAuth.AuthStateListener>()
    verify(auth).addAuthStateListener(callback.capture())
    callback.firstValue.onAuthStateChanged(auth)
    runCurrent()
    assertFalse(collected.isCompleted)
    whenever(auth.currentUser).thenReturn(null)
    callback.firstValue.onAuthStateChanged(auth)
    runCurrent()
    assertTrue(collected.isCompleted)
    assertEquals(listOf(EventsError.PERMISSION_DENIED), collected.await().map { it.error() })
    verify(registration).remove()
    verify(auth).removeAuthStateListener(callback.firstValue)
  }

  /** An SDK terminal error completes collection and releases both listeners. */
  @Test
  fun closesOnTerminalListenerError() = runTest {
    val meta: CollectionReference = mock()
    val subjects: DocumentReference = mock()
    val registration: ListenerRegistration = mock()
    whenever(owner.collection("meta")).thenReturn(meta)
    whenever(meta.document("subjects")).thenReturn(subjects)
    whenever(subjects.addSnapshotListener(any<EventListener<DocumentSnapshot>>()))
        .thenReturn(registration)
    val collected = async { repository.observeSubjects("owner").toList() }
    runCurrent()
    val callback = argumentCaptor<EventListener<DocumentSnapshot>>()
    verify(subjects).addSnapshotListener(callback.capture())
    callback.firstValue.onEvent(
        null,
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED),
    )
    runCurrent()
    assertEquals(listOf(EventsError.PERMISSION_DENIED), collected.await().map { it.error() })
    verify(registration).remove()
    verify(auth).removeAuthStateListener(any())
  }

  /** A pending server acknowledgement does not block deletion after the cache barrier. */
  @Test
  fun deleteWaitsForLocalCacheOnly() = runTest {
    val batch: WriteBatch = mock()
    val query: Query = mock()
    val snapshot: QuerySnapshot = mock()
    val server = TaskCompletionSource<Void>()
    val cache = TaskCompletionSource<QuerySnapshot>()
    whenever(db.batch()).thenReturn(batch)
    whenever(batch.delete(document)).thenReturn(batch)
    whenever(batch.commit()).thenReturn(server.task)
    whenever(events.whereEqualTo(FieldPath.documentId(), "id")).thenReturn(query)
    whenever(query.get(Source.CACHE)).thenReturn(cache.task)
    val deletion = async { repository.deleteEvent("owner", "id") }
    runCurrent()
    assertFalse(deletion.isCompleted)
    cache.setResult(snapshot)
    runCurrent()
    assertTrue(deletion.await().isSuccess)
    assertFalse(server.task.isComplete)
    inOrder(batch, query) {
      verify(batch).delete(document)
      verify(batch).commit()
      verify(query).get(Source.CACHE)
    }
  }

  /** A rejected server write is reported after local processing instead of returning success. */
  @Test
  fun deleteReportsFailedAcknowledgementAfterCacheBarrier() = runTest {
    val batch: WriteBatch = mock()
    val query: Query = mock()
    val snapshot: QuerySnapshot = mock()
    val server = TaskCompletionSource<Void>()
    val cache = TaskCompletionSource<QuerySnapshot>()
    val rejection =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    whenever(db.batch()).thenReturn(batch)
    whenever(batch.delete(document)).thenReturn(batch)
    whenever(batch.commit()).thenReturn(server.task)
    whenever(events.whereEqualTo(FieldPath.documentId(), "id")).thenReturn(query)
    whenever(query.get(Source.CACHE)).thenReturn(cache.task)
    val deletion = async { repository.deleteEvent("owner", "id") }
    runCurrent()
    server.setException(rejection)
    runCurrent()
    assertFalse(deletion.isCompleted)
    cache.setResult(snapshot)
    runCurrent()
    val result = deletion.await()
    assertEquals(EventsError.PERMISSION_DENIED, result.error())
    assertSame(rejection, result.exceptionOrNull()?.cause)
  }

  /** Extracts the public domain category without depending on SDK messages. */
  private fun Result<*>.error() = (exceptionOrNull() as EventsException).error
}
