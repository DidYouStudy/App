// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.android.sample.model.event

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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
    whenever(document.get()).thenReturn(TaskCompletionSource<DocumentSnapshot>().task)
    val read = async { repository.getEvent("owner", "id") }
    runCurrent()
    read.cancelAndJoin()
    assertTrue(read.isCancelled)
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
