// Co-authored-by: Gemini AI Agent
// Co-authored-by: Claude Sonnet 5.5
package com.android.sample.model.preferences

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesRepositoryFirestoreTest {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var collection: CollectionReference
  private lateinit var documentReference: DocumentReference
  private lateinit var cacheQuery: Query
  private lateinit var repository: PreferencesRepositoryFirestore

  @Before
  fun setUp() {
    firestore = mock(FirebaseFirestore::class.java)
    collection = mock(CollectionReference::class.java)
    documentReference = mock(DocumentReference::class.java)
    cacheQuery = mock(Query::class.java)

    `when`(firestore.collection("preferences")).thenReturn(collection)
    `when`(collection.document(any())).thenReturn(documentReference)
    // The save barrier selects the user's document by ID, like EventsRepositoryFirestore.
    `when`(collection.whereEqualTo(FieldPath.documentId(), "user123")).thenReturn(cacheQuery)

    repository = PreferencesRepositoryFirestore(firestore)
  }

  @Test
  fun getPreferences_returnsUserPreferences_whenDocumentExists() = runTest {
    val documentSnapshot = mock(DocumentSnapshot::class.java)
    val expectedPrefs = UserPreferences(userId = "user123", sessionLengthMinutes = 60)

    `when`(documentSnapshot.exists()).thenReturn(true)
    `when`(documentSnapshot.toObject(UserPreferences::class.java)).thenReturn(expectedPrefs)
    `when`(documentReference.get()).thenReturn(Tasks.forResult(documentSnapshot))

    val result = repository.getPreferences("user123")

    assertTrue(result.isSuccess)
    assertEquals(expectedPrefs, result.getOrNull())
  }

  @Test
  fun getPreferences_returnsNull_whenDocumentDoesNotExist() = runTest {
    val documentSnapshot = mock(DocumentSnapshot::class.java)

    `when`(documentSnapshot.exists()).thenReturn(false)
    `when`(documentReference.get()).thenReturn(Tasks.forResult(documentSnapshot))

    val result = repository.getPreferences("user123")

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
  }

  @Test
  fun getPreferences_fallsBackToCache_whenRemoteFails() = runTest {
    val documentSnapshot = mock(DocumentSnapshot::class.java)
    val expectedPrefs = UserPreferences(userId = "user123", sessionLengthMinutes = 45)

    `when`(documentSnapshot.exists()).thenReturn(true)
    `when`(documentSnapshot.toObject(UserPreferences::class.java)).thenReturn(expectedPrefs)
    `when`(documentReference.get()).thenReturn(Tasks.forException(RuntimeException("Offline")))
    `when`(documentReference.get(Source.CACHE)).thenReturn(Tasks.forResult(documentSnapshot))

    val result = repository.getPreferences("user123")

    assertTrue(result.isSuccess)
    assertEquals(expectedPrefs, result.getOrNull())
  }

  @Test
  fun getPreferences_returnsFailure_onFirestoreError() = runTest {
    val exception = RuntimeException("Firestore error")
    `when`(documentReference.get()).thenReturn(Tasks.forException(exception))

    val result = repository.getPreferences("user123")

    assertTrue(result.isFailure)
    assertEquals("Firestore error", result.exceptionOrNull()?.message)
  }

  @Test(expected = CancellationException::class)
  fun getPreferences_rethrowsCancellationException() = runTest {
    val exception = CancellationException("Job cancelled")
    `when`(documentReference.get()).thenReturn(Tasks.forException(exception))

    repository.getPreferences("user123")
  }

  @Test
  fun getPreferences_returnsFailure_whenUserIdIsBlank() = runTest {
    val result = repository.getPreferences("   ")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals("User ID must not be blank", result.exceptionOrNull()?.message)
  }

  /** A completed server acknowledgement and a cache barrier yield a successful save. */
  @Test
  fun savePreferences_succeeds_whenUserIdIsNotBlank() = runTest {
    val prefs = UserPreferences(userId = "user123", sessionLengthMinutes = 45)
    `when`(documentReference.set(prefs)).thenReturn(Tasks.forResult(null))
    `when`(cacheQuery.get(Source.CACHE))
        .thenReturn(Tasks.forResult(mock(QuerySnapshot::class.java)))

    val result = repository.savePreferences(prefs)

    assertTrue(result.isSuccess)
    verify(collection).document("user123")
    verify(documentReference).set(prefs)
  }

  /**
   * A pending server acknowledgement (as when offline) does not block the save: it finishes once
   * the cache barrier does, and `set()` is queued before the barrier read.
   */
  @Test
  fun savePreferences_waitsForLocalCacheOnly() = runTest {
    val prefs = UserPreferences(userId = "user123", sessionLengthMinutes = 45)
    val server = TaskCompletionSource<Void>()
    val cache = TaskCompletionSource<QuerySnapshot>()
    `when`(documentReference.set(prefs)).thenReturn(server.task)
    `when`(cacheQuery.get(Source.CACHE)).thenReturn(cache.task)

    val save = async { repository.savePreferences(prefs) }
    runCurrent()
    // Still waiting for the local cache barrier, not for the server.
    assertFalse(save.isCompleted)

    cache.setResult(mock(QuerySnapshot::class.java))
    runCurrent()

    assertTrue(save.await().isSuccess)
    // The server never acknowledged, yet the save completed.
    assertFalse(server.task.isComplete)
    inOrder(documentReference, cacheQuery) {
      verify(documentReference).set(prefs)
      verify(cacheQuery).get(Source.CACHE)
    }
  }

  /** A write the server already rejected is reported after the cache barrier, not as success. */
  @Test
  fun savePreferences_reportsFailedAcknowledgementAfterCacheBarrier() = runTest {
    val prefs = UserPreferences(userId = "user123")
    val server = TaskCompletionSource<Void>()
    val cache = TaskCompletionSource<QuerySnapshot>()
    val rejection =
        FirebaseFirestoreException("denied", FirebaseFirestoreException.Code.PERMISSION_DENIED)
    `when`(documentReference.set(prefs)).thenReturn(server.task)
    `when`(cacheQuery.get(Source.CACHE)).thenReturn(cache.task)

    val save = async { repository.savePreferences(prefs) }
    runCurrent()
    server.setException(rejection)
    runCurrent()
    // The failure is only reported once the barrier has run.
    assertFalse(save.isCompleted)

    cache.setResult(mock(QuerySnapshot::class.java))
    runCurrent()

    val result = save.await()
    assertTrue(result.isFailure)
    assertSame(rejection, result.exceptionOrNull())
  }

  /** A failing cache barrier read is returned as a failure. */
  @Test
  fun savePreferences_returnsFailure_whenCacheBarrierFails() = runTest {
    val prefs = UserPreferences(userId = "user123")
    val exception = RuntimeException("Cache failed")
    `when`(documentReference.set(prefs)).thenReturn(TaskCompletionSource<Void>().task)
    `when`(cacheQuery.get(Source.CACHE)).thenReturn(Tasks.forException(exception))

    val result = repository.savePreferences(prefs)

    assertTrue(result.isFailure)
    assertSame(exception, result.exceptionOrNull())
  }

  @Test
  fun savePreferences_returnsFailure_whenUserIdIsBlank() = runTest {
    val prefs = UserPreferences(userId = "   ")

    val result = repository.savePreferences(prefs)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals("User ID must not be blank", result.exceptionOrNull()?.message)
  }

  /** An acknowledgement that has already failed is returned as the failure's cause. */
  @Test
  fun savePreferences_returnsFailure_onFailedAcknowledgement() = runTest {
    val prefs = UserPreferences(userId = "user123")
    val exception = RuntimeException("Write failed")
    `when`(documentReference.set(prefs)).thenReturn(Tasks.forException(exception))
    `when`(cacheQuery.get(Source.CACHE))
        .thenReturn(Tasks.forResult(mock(QuerySnapshot::class.java)))

    val result = repository.savePreferences(prefs)

    assertTrue(result.isFailure)
    assertEquals("Write failed", result.exceptionOrNull()?.message)
  }

  /** Cancellation from the barrier read propagates instead of becoming a failure result. */
  @Test(expected = CancellationException::class)
  fun savePreferences_rethrowsCancellationException() = runTest {
    val prefs = UserPreferences(userId = "user123")
    val exception = CancellationException("Job cancelled")
    `when`(documentReference.set(prefs)).thenReturn(TaskCompletionSource<Void>().task)
    `when`(cacheQuery.get(Source.CACHE)).thenReturn(Tasks.forException(exception))

    repository.savePreferences(prefs)
  }
}
