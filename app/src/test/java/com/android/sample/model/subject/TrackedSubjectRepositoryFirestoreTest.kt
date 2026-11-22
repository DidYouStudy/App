// Co-authored-by: Gemini 3.6 Flash
package com.android.sample.model.subject

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

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

  @Test
  fun saveEmptyDecisionsBatchSucceedsWithoutFirestoreInteraction() = runTest {
    val result = repository.saveDecisions("user-1", emptyMap())
    assertTrue(result.isSuccess)
    verifyNoInteractions(db)
  }

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
}
