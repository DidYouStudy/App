// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

import com.android.sample.model.event.SubjectCatalogFirestoreMapper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

/** Unit tests for [TrackedSubjectRepositoryFirestore] error handling and local barrier logic. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackedSubjectRepositoryFirestoreTest {

  private lateinit var db: FirebaseFirestore
  private lateinit var auth: FirebaseAuth
  private lateinit var user: FirebaseUser
  private lateinit var users: CollectionReference
  private lateinit var userDoc: DocumentReference
  private lateinit var metaCollection: CollectionReference
  private lateinit var trackedSubjectsDoc: DocumentReference
  private lateinit var repository: TrackedSubjectRepositoryFirestore

  @Before
  fun setup() {
    db = mock(FirebaseFirestore::class.java)
    auth = mock(FirebaseAuth::class.java)
    user = mock(FirebaseUser::class.java)
    users = mock(CollectionReference::class.java)
    userDoc = mock(DocumentReference::class.java)
    metaCollection = mock(CollectionReference::class.java)
    trackedSubjectsDoc = mock(DocumentReference::class.java)

    `when`(auth.currentUser).thenReturn(user)
    `when`(user.uid).thenReturn("user-1")

    `when`(db.collection("users")).thenReturn(users)
    `when`(users.document("user-1")).thenReturn(userDoc)
    `when`(userDoc.collection("meta")).thenReturn(metaCollection)
    `when`(metaCollection.document("trackedSubjects")).thenReturn(trackedSubjectsDoc)

    repository = TrackedSubjectRepositoryFirestore(db, auth)
  }

  /** An empty decision map succeeds without contacting Firestore. */
  @Test
  fun saveEmptyDecisionsBatchSucceedsWithoutFirestoreInteraction() = runTest {
    val result = repository.saveDecisions("user-1", emptyMap())
    assertTrue(result.isSuccess)
    verifyNoInteractions(db)
  }

  /** Saving decisions queues set with SetOptions.merge() and awaits the local cache barrier. */
  @Test
  fun saveDecisionsQueuesSetWithMergeAndWaitsOnCacheBarrier() = runTest {
    val setTask = Tasks.forResult<Void>(null)
    `when`(trackedSubjectsDoc.set(any(), eq(SetOptions.merge()))).thenReturn(setTask)

    val query = mock(Query::class.java)
    `when`(metaCollection.whereEqualTo(any<FieldPath>(), any())).thenReturn(query)
    val querySnapshot = mock(QuerySnapshot::class.java)
    val queryTask = Tasks.forResult(querySnapshot)
    `when`(query.get(any())).thenReturn(queryTask)

    val result = repository.saveDecisions("user-1", mapOf("Analysis I" to true))
    assertTrue(result.isSuccess)
  }

  /** Observing decisions decodes Firestore snapshot and emits SubjectDecisions. */
  @Test
  fun observeDecisions_emitsValidSnapshotData() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    val key = SubjectCatalogFirestoreMapper.key("Analysis I")
    `when`(snapshot.data)
        .thenReturn(
            mapOf("subjects" to mapOf(key to mapOf("spelling" to "Analysis I", "tracked" to true)))
        )

    `when`(trackedSubjectsDoc.addSnapshotListener(any())).thenAnswer { invocation ->
      val listener = invocation.getArgument<EventListener<DocumentSnapshot>>(0)
      listener.onEvent(snapshot, null)
      mock(ListenerRegistration::class.java)
    }

    val result = repository.observeDecisions("user-1").first()
    assertTrue(result.isSuccess)
    assertTrue(result.getOrThrow().isTracked("Analysis I"))
  }

  /** Firestore snapshot errors map to domain PERMISSION_DENIED exception. */
  @Test
  fun observeDecisions_handlesFirestoreError() = runTest {
    `when`(trackedSubjectsDoc.addSnapshotListener(any())).thenAnswer { invocation ->
      val listener = invocation.getArgument<EventListener<DocumentSnapshot>>(0)
      val error =
          FirebaseFirestoreException(
              "Permission denied",
              FirebaseFirestoreException.Code.PERMISSION_DENIED,
          )
      listener.onEvent(null, error)
      mock(ListenerRegistration::class.java)
    }

    val result = repository.observeDecisions("user-1").first()
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as TrackedSubjectException
    assertEquals(TrackedSubjectError.PERMISSION_DENIED, exception.error)
  }

  /** Auth listener emitting user ID mismatch closes flow with PERMISSION_DENIED. */
  @Test
  fun observeDecisions_authStateChangedToOtherUser_emitsPermissionDenied() = runTest {
    var authListener: FirebaseAuth.AuthStateListener? = null
    `when`(auth.addAuthStateListener(any())).thenAnswer { invocation ->
      authListener = invocation.getArgument<FirebaseAuth.AuthStateListener>(0)
      null
    }
    `when`(trackedSubjectsDoc.addSnapshotListener(any()))
        .thenReturn(mock(ListenerRegistration::class.java))

    val flow = repository.observeDecisions("user-1")
    val collectionJob =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          val result = flow.first()
          assertTrue(result.isFailure)
          val exception = result.exceptionOrNull() as TrackedSubjectException
          assertEquals(TrackedSubjectError.PERMISSION_DENIED, exception.error)
        }

    `when`(auth.currentUser).thenReturn(null)
    authListener?.onAuthStateChanged(auth)
    collectionJob.join()
  }

  /** Malformed document snapshot data maps to domain INVALID_DATA exception. */
  @Test
  fun observeDecisions_handlesInvalidData() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(snapshot.data).thenReturn(mapOf("invalid" to "schema"))
    `when`(trackedSubjectsDoc.addSnapshotListener(any())).thenAnswer { invocation ->
      val listener = invocation.getArgument<EventListener<DocumentSnapshot>>(0)
      listener.onEvent(snapshot, null)
      mock(ListenerRegistration::class.java)
    }

    val result = repository.observeDecisions("user-1").first()
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as TrackedSubjectException
    assertEquals(TrackedSubjectError.INVALID_DATA, exception.error)
  }

  /** Observing decisions with a blank owner ID or slash fails with INVALID_ARGUMENT. */
  @Test
  fun observeDecisions_rejectsBlankOrInvalidOwnerId() = runTest {
    val blankResult = repository.observeDecisions(" ").first()
    assertTrue(blankResult.isFailure)
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        (blankResult.exceptionOrNull() as TrackedSubjectException).error,
    )

    val slashResult = repository.observeDecisions("owner/slash").first()
    assertTrue(slashResult.isFailure)
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        (slashResult.exceptionOrNull() as TrackedSubjectException).error,
    )
  }

  /** Saving decisions with invalid parameters rejects with INVALID_ARGUMENT. */
  @Test
  fun saveDecisions_rejectsInvalidParameters() = runTest {
    val blankOwner = repository.saveDecisions(" ", mapOf("Analysis" to true))
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        (blankOwner.exceptionOrNull() as TrackedSubjectException).error,
    )

    val blankSpelling = repository.saveDecisions("user-1", mapOf(" " to true))
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        (blankSpelling.exceptionOrNull() as TrackedSubjectException).error,
    )

    val duplicateKey =
        repository.saveDecisions("user-1", mapOf("Analysis I" to true, " analysis i " to false))
    assertEquals(
        TrackedSubjectError.INVALID_ARGUMENT,
        (duplicateKey.exceptionOrNull() as TrackedSubjectException).error,
    )
  }

  /** Saving decisions when auth user doesn't match owner returns PERMISSION_DENIED. */
  @Test
  fun saveDecisions_rejectsOwnerMismatch() = runTest {
    val result = repository.saveDecisions("other-user", mapOf("Analysis" to true))
    assertTrue(result.isFailure)
    assertEquals(
        TrackedSubjectError.PERMISSION_DENIED,
        (result.exceptionOrNull() as TrackedSubjectException).error,
    )
  }

  /** Failed acknowledgement task surfaces the underlying storage exception. */
  @Test
  fun saveDecisions_handlesAcknowledgementFailure() = runTest {
    val failedTask = mock(Task::class.java) as Task<Void>
    `when`(failedTask.isComplete).thenReturn(true)
    `when`(failedTask.isSuccessful).thenReturn(false)
    `when`(failedTask.exception)
        .thenReturn(
            FirebaseFirestoreException("Storage error", FirebaseFirestoreException.Code.UNAVAILABLE)
        )
    `when`(trackedSubjectsDoc.set(any(), eq(SetOptions.merge()))).thenReturn(failedTask)

    val query = mock(Query::class.java)
    `when`(metaCollection.whereEqualTo(any<FieldPath>(), any())).thenReturn(query)
    `when`(query.get(any())).thenReturn(Tasks.forResult(mock(QuerySnapshot::class.java)))

    val result = repository.saveDecisions("user-1", mapOf("Analysis I" to true))
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as TrackedSubjectException
    assertEquals(TrackedSubjectError.STORAGE_FAILURE, exception.error)
  }

  /** Failed acknowledgement task with null exception returns STORAGE_FAILURE. */
  @Test
  fun saveDecisions_handlesAcknowledgementFailureWithNullException() = runTest {
    val failedTask = mock(Task::class.java) as Task<Void>
    `when`(failedTask.isComplete).thenReturn(true)
    `when`(failedTask.isSuccessful).thenReturn(false)
    `when`(failedTask.exception).thenReturn(null)
    `when`(trackedSubjectsDoc.set(any(), eq(SetOptions.merge()))).thenReturn(failedTask)

    val query = mock(Query::class.java)
    `when`(metaCollection.whereEqualTo(any<FieldPath>(), any())).thenReturn(query)
    `when`(query.get(any())).thenReturn(Tasks.forResult(mock(QuerySnapshot::class.java)))

    val result = repository.saveDecisions("user-1", mapOf("Analysis I" to true))
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as TrackedSubjectException
    assertEquals(TrackedSubjectError.STORAGE_FAILURE, exception.error)
  }

  /** Domain error handles general unexpected exceptions. */
  @Test
  fun observeDecisions_handlesUnexpectedException() = runTest {
    `when`(trackedSubjectsDoc.addSnapshotListener(any()))
        .thenThrow(RuntimeException("Unexpected error"))

    val result = repository.observeDecisions("user-1").first()
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as TrackedSubjectException
    assertEquals(TrackedSubjectError.UNKNOWN, exception.error)
  }

  /** Coroutine cancellation in execute is rethrown without domain mapping. */
  @Test
  fun execute_rethrowsCancellationException() = runTest {
    `when`(trackedSubjectsDoc.set(any(), any())).thenThrow(CancellationException("Cancelled"))

    try {
      repository.saveDecisions("user-1", mapOf("Analysis" to true))
      fail("Expected CancellationException to be rethrown")
    } catch (e: CancellationException) {
      assertEquals("Cancelled", e.message)
    }
  }

  /** Coroutine cancellation in capture is rethrown without domain mapping. */
  @Test
  fun capture_rethrowsCancellationException() = runTest {
    `when`(trackedSubjectsDoc.addSnapshotListener(any())).thenAnswer { invocation ->
      val listener = invocation.getArgument<EventListener<DocumentSnapshot>>(0)
      val snapshot = mock(DocumentSnapshot::class.java)
      `when`(snapshot.data).thenThrow(CancellationException("Cancelled"))
      listener.onEvent(snapshot, null)
      mock(ListenerRegistration::class.java)
    }

    try {
      repository.observeDecisions("user-1").first()
      fail("Expected CancellationException to be rethrown")
    } catch (e: CancellationException) {
      assertEquals("Cancelled", e.message)
    }
  }

  /** IllegalArgumentException in execute is mapped to INVALID_ARGUMENT error. */
  @Test
  fun domainError_mapsIllegalArgumentExceptionToInvalidArgument() = runTest {
    `when`(trackedSubjectsDoc.set(any(), any()))
        .thenThrow(IllegalArgumentException("Invalid argument thrown"))

    val result = repository.saveDecisions("user-1", mapOf("Analysis" to true))
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull() as TrackedSubjectException
    assertEquals(TrackedSubjectError.INVALID_ARGUMENT, exception.error)
    assertTrue(exception.cause is IllegalArgumentException)
  }
}
