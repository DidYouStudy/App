// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.sample.model.groupsession

import com.android.sample.model.event.EventDetails
import com.android.sample.model.event.EventType
import com.android.sample.model.event.EventsError
import com.android.sample.model.event.EventsException
import com.android.sample.model.event.GroupSession
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/** Checks input validation, storage error translation, decoding, and cancellation behavior. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupSessionsRepositoryFirestoreTest {
  private val firestore: FirebaseFirestore = mock()
  private val auth: FirebaseAuth = mock()
  private val user: FirebaseUser = mock()
  private val collection: CollectionReference = mock()
  private val document: DocumentReference = mock()
  private val repository = GroupSessionsRepositoryFirestore(firestore, auth)

  /** Sets up one authenticated user and one document path. */
  @Before
  fun setUp() {
    whenever(auth.currentUser).thenReturn(user)
    whenever(user.uid).thenReturn("owner")
    whenever(firestore.collection("groupSessions")).thenReturn(collection)
    whenever(collection.document("id")).thenReturn(document)
  }

  /** Blank and slash-containing IDs fail before touching Firestore. */
  @Test
  fun invalidIdsAreRejectedBeforeFirestoreAccess() = runTest {
    val session = session("invalid/id")

    assertError(repository.getSession(" "), EventsError.INVALID_ARGUMENT)
    assertError(repository.getSession("invalid/id"), EventsError.INVALID_ARGUMENT)
    assertError(repository.saveSession("owner", session), EventsError.INVALID_ARGUMENT)
    assertError(repository.saveSession(" ", session("id")), EventsError.INVALID_ARGUMENT)
    assertError(repository.saveSession("invalid/id", session("id")), EventsError.INVALID_ARGUMENT)
    assertError(repository.deleteSession("owner", " "), EventsError.INVALID_ARGUMENT)
    assertError(repository.deleteSession("owner", "invalid/id"), EventsError.INVALID_ARGUMENT)
    assertError(repository.deleteSession("invalid/id", "id"), EventsError.INVALID_ARGUMENT)
    assertError(repository.joinSession("owner", " "), EventsError.INVALID_ARGUMENT)
    assertError(repository.joinSession("owner", "invalid/id"), EventsError.INVALID_ARGUMENT)
    assertError(repository.joinSession("invalid/id", "id"), EventsError.INVALID_ARGUMENT)
    assertError(repository.leaveSession("owner", " "), EventsError.INVALID_ARGUMENT)
    assertError(repository.leaveSession("owner", "invalid/id"), EventsError.INVALID_ARGUMENT)
    assertError(repository.leaveSession("invalid/id", "id"), EventsError.INVALID_ARGUMENT)

    verifyNoInteractions(firestore)
  }

  /** A caller cannot act using a user ID other than the authenticated user's ID. */
  @Test
  fun rejectsUserIdDifferentFromSignedInUser() = runTest {
    val otherUserSession = session("id", createdBy = "other")
    assertError(repository.saveSession("other", otherUserSession), EventsError.PERMISSION_DENIED)
    assertError(repository.deleteSession("other", "id"), EventsError.PERMISSION_DENIED)
    assertError(repository.joinSession("other", "id"), EventsError.PERMISSION_DENIED)
    assertError(repository.leaveSession("other", "id"), EventsError.PERMISSION_DENIED)

    verifyNoInteractions(firestore)
  }

  /** Firestore permission and availability errors map to stable domain categories. */
  @Test
  fun mapsFirestoreErrors() = runTest {
    whenever(document.get())
        .thenReturn(
            Tasks.forException(
                FirebaseFirestoreException(
                    "denied",
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                )
            )
        )
    assertError(repository.getSession("id"), EventsError.PERMISSION_DENIED)

    whenever(document.get())
        .thenReturn(
            Tasks.forException(
                FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
            )
        )
    assertError(repository.getSession("id"), EventsError.STORAGE_FAILURE)
  }

  /** Generated session IDs are nonblank and unique across consecutive calls. */
  @Test
  fun newSessionIdReturnsDistinctNonblankValues() {
    val first = repository.newSessionId()
    val second = repository.newSessionId()

    assertTrue(first.isNotBlank())
    assertTrue(second.isNotBlank())
    assertTrue(first != second)
  }

  /**
   * A permission-denied query listener error emits failure, completes, and removes its listener.
   */
  @Test
  fun observeSessionsClosesOnPermissionDeniedListenerError() = runTest {
    val query: Query = mock()
    val registration: ListenerRegistration = mock()
    whenever(collection.whereGreaterThan(eq("endsAt"), any())).thenReturn(query)
    whenever(query.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(registration)

    val collected = async { repository.observeSessions(Instant.now()).toList() }
    runCurrent()
    val callback = argumentCaptor<EventListener<QuerySnapshot>>()
    verify(query).addSnapshotListener(callback.capture())

    callback.firstValue.onEvent(
        null,
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED),
    )
    runCurrent()

    assertTrue(collected.isCompleted)
    assertEquals(1, collected.await().size)
    assertError(collected.await().single(), EventsError.PERMISSION_DENIED)
    verify(registration).remove()
  }

  /** An unavailable query listener error emits failure, completes, and removes its listener. */
  @Test
  fun observeSessionsClosesOnUnavailableListenerError() = runTest {
    val query: Query = mock()
    val registration: ListenerRegistration = mock()
    whenever(collection.whereGreaterThan(eq("endsAt"), any())).thenReturn(query)
    whenever(query.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(registration)

    val collected = async { repository.observeSessions(Instant.now()).toList() }
    runCurrent()
    val callback = argumentCaptor<EventListener<QuerySnapshot>>()
    verify(query).addSnapshotListener(callback.capture())

    callback.firstValue.onEvent(
        null,
        FirebaseFirestoreException("unavailable", FirebaseFirestoreException.Code.UNAVAILABLE),
    )
    runCurrent()

    assertTrue(collected.isCompleted)
    assertEquals(1, collected.await().size)
    assertError(collected.await().single(), EventsError.STORAGE_FAILURE)
    verify(registration).remove()
  }

  /** Signing out closes observation, while an authenticated state callback keeps it open. */
  @Test
  fun observeSessionsClosesWhenAuthListenerReportsSignOut() = runTest {
    val query: Query = mock()
    val registration: ListenerRegistration = mock()
    whenever(collection.whereGreaterThan(eq("endsAt"), any())).thenReturn(query)
    whenever(query.addSnapshotListener(any<EventListener<QuerySnapshot>>()))
        .thenReturn(registration)

    val collected = async { repository.observeSessions(Instant.now()).toList() }
    runCurrent()
    val authCallback = argumentCaptor<FirebaseAuth.AuthStateListener>()
    verify(auth).addAuthStateListener(authCallback.capture())

    authCallback.firstValue.onAuthStateChanged(auth)
    runCurrent()
    assertFalse(collected.isCompleted)

    whenever(auth.currentUser).thenReturn(null)
    authCallback.firstValue.onAuthStateChanged(auth)
    runCurrent()

    assertTrue(collected.isCompleted)
    assertEquals(1, collected.await().size)
    assertError(collected.await().single(), EventsError.PERMISSION_DENIED)
    verify(registration).remove()
    verify(auth).removeAuthStateListener(authCallback.firstValue)
  }

  /** A present but malformed document reports invalid event data. */
  @Test
  fun rejectsUndecodableStoredDocument() = runTest {
    val snapshot: DocumentSnapshot = mock()
    whenever(snapshot.exists()).thenReturn(true)
    whenever(snapshot.data).thenReturn(emptyMap())
    whenever(document.get()).thenReturn(Tasks.forResult(snapshot))

    assertError(repository.getSession("id"), EventsError.INVALID_EVENT_DATA)
  }

  /** A new session is stored with server creation and edit times, not the phone's. */
  @Test
  fun createStoresServerTimes() = runTest {
    val transaction = runTransactionsWith(storedData = null)

    repository.saveSession("owner", session("id")).getOrThrow()

    val written = argumentCaptor<Map<String, Any?>>()
    verify(transaction).set(eq(document), written.capture())
    val expected =
        GroupSessionFirestoreMapper.encode(session("id")) +
            mapOf(
                GroupSessionFirestoreMapper.CREATED_AT to FieldValue.serverTimestamp(),
                GroupSessionFirestoreMapper.UPDATED_AT to FieldValue.serverTimestamp(),
            )
    assertEquals(expected, written.firstValue)
  }

  /** An edit sends only editable fields, with the server's time as the edit time. */
  @Test
  fun editStoresServerUpdatedAtAndKeepsCreatedAt() = runTest {
    val stored = session("id")
    val transaction = runTransactionsWith(GroupSessionFirestoreMapper.encode(stored))
    val edited =
        stored.copy(
            details = stored.details.copy(description = "Bring notes"),
            createdAt = Instant.parse("2020-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2030-01-01T00:00:00Z"),
        )

    repository.saveSession("owner", edited).getOrThrow()

    val written = argumentCaptor<Map<String, Any>>()
    verify(transaction).update(eq(document), written.capture())
    assertEquals(
        setOf("type", "subject", "startsAt", "endsAt", "location", "description", "updatedAt"),
        written.firstValue.keys,
    )
    assertEquals("Bring notes", written.firstValue["description"])
    assertEquals(
        FieldValue.serverTimestamp(),
        written.firstValue[GroupSessionFirestoreMapper.UPDATED_AT],
    )
  }

  /** Coroutine cancellation escapes instead of becoming a failed result. */
  @Test
  fun propagatesCancellation() = runTest {
    val cancellation = CancellationException("read cancelled")
    whenever(document.get()).thenReturn(Tasks.forException(cancellation))

    try {
      repository.getSession("id")
      throw AssertionError("Cancellation must be thrown, not returned as a failure")
    } catch (caught: CancellationException) {
      assertSame(cancellation, caught)
    }
  }

  /**
   * Runs every Firestore transaction immediately on a mock transaction whose read of [document]
   * returns [storedData], or a missing document when it is null. Returns that mock transaction.
   */
  private fun runTransactionsWith(storedData: Map<String, Any?>?): Transaction {
    val transaction: Transaction = mock()
    val snapshot: DocumentSnapshot = mock()
    whenever(snapshot.exists()).thenReturn(storedData != null)
    whenever(snapshot.data).thenReturn(storedData)
    whenever(transaction.get(document)).thenReturn(snapshot)
    whenever(firestore.runTransaction(any<Transaction.Function<Any?>>())).thenAnswer { invocation ->
      Tasks.forResult(invocation.getArgument<Transaction.Function<Any?>>(0).apply(transaction))
    }
    return transaction
  }

  /** Builds a valid session with the requested ID. */
  private fun session(id: String, createdBy: String = "owner") =
      GroupSession(
          id = id,
          createdBy = createdBy,
          memberIds = listOf(createdBy),
          details =
              EventDetails(
                  type = EventType.COURSE,
                  subject = "Analysis",
                  startsAt = Instant.parse("2026-11-05T10:00:00Z"),
                  endsAt = Instant.parse("2026-11-05T11:00:00Z"),
              ),
          createdAt = Instant.parse("2026-11-04T10:00:00Z"),
      )

  /** Asserts a failed result has the expected domain error. */
  private fun assertError(result: Result<*>, expected: EventsError) {
    assertTrue(result.isFailure)
    assertEquals(expected, (result.exceptionOrNull() as EventsException).error)
  }
}
