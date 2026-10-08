// Co-authored-by: Copilot
// Co-authored-by: Codex AI Agent
package com.android.sample.model.profile

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepositoryFirestoreTest {
  private lateinit var firestore: FirebaseFirestore
  private lateinit var collection: CollectionReference
  private lateinit var document: DocumentReference
  private lateinit var repository: ProfileRepositoryFirestore

  @Before
  fun setUp() {
    firestore = mock(FirebaseFirestore::class.java)
    collection = mock(CollectionReference::class.java)
    document = mock(DocumentReference::class.java)
    `when`(firestore.collection("profiles")).thenReturn(collection)
    `when`(collection.document(any())).thenReturn(document)
    repository = ProfileRepositoryFirestore(firestore)
  }

  // Returns the stored profile when the requested document exists.
  @Test
  fun getProfile_returnsProfile_whenDocumentExists() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    val expected = UserProfile("user123")
    `when`(snapshot.exists()).thenReturn(true)
    `when`(snapshot.toObject(UserProfile::class.java)).thenReturn(expected)
    `when`(document.get()).thenReturn(Tasks.forResult(snapshot))

    val result = repository.getProfile("user123")

    assertTrue(result.isSuccess)
    assertEquals(expected, result.getOrNull())
    verify(collection).document("user123")
  }

  // Returns null when the requested profile document does not exist.
  @Test
  fun getProfile_returnsNull_whenDocumentDoesNotExist() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(snapshot.exists()).thenReturn(false)
    `when`(document.get()).thenReturn(Tasks.forResult(snapshot))

    val result = repository.getProfile("user123")

    assertTrue(result.isSuccess)
    assertNull(result.getOrNull())
    verify(collection).document("user123")
  }

  // Rejects a blank user ID without accessing Firestore.
  @Test
  fun getProfile_returnsInvalidUserIdFailure_whenUserIdIsBlank() = runTest {
    val result = repository.getProfile("   ")

    assertProfileError(result, ProfileError.INVALID_USER_ID)
    verify(collection, never()).document(any())
  }

  // Maps an unexpected synchronous read failure to the unknown profile error.
  @Test
  fun getProfile_returnsUnknownFailure_whenFirestoreCallThrowsUnexpectedException() = runTest {
    val exception = IllegalStateException("Read failed")
    `when`(document.get()).thenThrow(exception)

    val result = repository.getProfile("user123")

    assertProfileError(result, ProfileError.UNKNOWN)
    assertEquals(exception, result.exceptionOrNull()?.cause)
  }

  // Maps a failed Firestore read task to the Firestore profile error.
  @Test
  fun getProfile_returnsFirestoreFailure_whenFirestoreTaskFails() = runTest {
    val exception = firestoreException("Read failed")
    `when`(document.get()).thenReturn(Tasks.forException(exception))

    val result = repository.getProfile("user123")

    assertProfileError(result, ProfileError.FIRESTORE_FAILURE)
    assertEquals(exception, result.exceptionOrNull()?.cause)
  }

  // Reports invalid profile data when an existing document cannot be deserialized.
  @Test
  fun getProfile_returnsInvalidProfileData_whenExistingDocumentCannotBeDeserialized() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(snapshot.exists()).thenReturn(true)
    `when`(snapshot.toObject(UserProfile::class.java)).thenReturn(null)
    `when`(document.get()).thenReturn(Tasks.forResult(snapshot))

    val result = repository.getProfile("user123")

    assertProfileError(result, ProfileError.INVALID_PROFILE_DATA)
  }

  // Reports invalid profile data when document conversion throws during a profile read.
  @Test
  fun getProfile_returnsInvalidProfileData_whenDeserializationThrows() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    val exception = IllegalStateException("Malformed profile")
    `when`(snapshot.exists()).thenReturn(true)
    `when`(snapshot.toObject(UserProfile::class.java)).thenThrow(exception)
    `when`(document.get()).thenReturn(Tasks.forResult(snapshot))

    val result = repository.getProfile("user123")

    assertProfileError(result, ProfileError.INVALID_PROFILE_DATA)
    assertEquals(exception, result.exceptionOrNull()?.cause)
  }

  // Rejects a stored profile whose UID does not match the requested document ID.
  @Test
  fun getProfile_returnsInvalidProfileData_whenStoredUidDoesNotMatchRequestedId() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    `when`(snapshot.exists()).thenReturn(true)
    `when`(snapshot.toObject(UserProfile::class.java)).thenReturn(UserProfile("other-user"))
    `when`(document.get()).thenReturn(Tasks.forResult(snapshot))

    val result = repository.getProfile("user123")

    assertProfileError(result, ProfileError.INVALID_PROFILE_DATA)
  }

  // Propagates cancellation thrown synchronously by the Firestore read call.
  @Test
  fun getProfile_propagatesSynchronousCancellationFromFirestoreCall() = runTest {
    val cancellation = CancellationException("Cancelled")
    `when`(document.get()).thenThrow(cancellation)

    try {
      repository.getProfile("user123")
      fail("Expected cancellation to propagate")
    } catch (actual: CancellationException) {
      assertSame(cancellation, actual)
    }
  }

  // Propagates coroutine cancellation while a profile read is pending.
  @Test
  fun getProfile_propagatesCancellation_whileFirestoreTaskIsPending() = runTest {
    val task = TaskCompletionSource<DocumentSnapshot>()
    `when`(document.get()).thenReturn(task.task)

    val request = async { repository.getProfile("user123") }
    runCurrent()
    request.cancelAndJoin()

    assertTrue(request.isCancelled)
  }

  // Propagates cancellation when a failed read task wraps it as the cause.
  @Test
  fun getProfile_propagatesWrappedCancellationFromFailedTask() = runTest {
    val cancellation = CancellationException("Cancelled")
    val wrapped = IllegalStateException("Firestore wrapper", cancellation)
    `when`(document.get()).thenReturn(Tasks.forException(wrapped))

    val request = async { repository.getProfile("user123") }

    request.cancelAndJoin()
    assertTrue(request.isCancelled)
  }

  // Creates and returns a profile when the transaction finds no existing document.
  @Test
  fun createProfile_createsAndReturnsProfile_whenDocumentDoesNotExist() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    val transaction = mock(Transaction::class.java)
    val expected = UserProfile("user123")
    `when`(snapshot.exists()).thenReturn(false)
    `when`(transaction.get(document)).thenReturn(snapshot)
    stubTransaction(transaction)

    val result = repository.createProfile("user123")

    assertTrue(result.isSuccess)
    assertEquals(expected, result.getOrNull())
    verify(transaction).set(document, expected)
    verify(collection).document("user123")
  }

  // Returns an existing profile without overwriting it during creation.
  @Test
  fun createProfile_preservesAndReturnsExistingProfile() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    val transaction = mock(Transaction::class.java)
    val existing = UserProfile("user123")
    `when`(snapshot.exists()).thenReturn(true)
    `when`(snapshot.toObject(UserProfile::class.java)).thenReturn(existing)
    `when`(transaction.get(document)).thenReturn(snapshot)
    stubTransaction(transaction)

    val result = repository.createProfile("user123")

    assertTrue(result.isSuccess)
    assertEquals(existing, result.getOrNull())
    verify(transaction, never()).set(any(), any())
    verify(collection).document("user123")
  }

  // Rejects a blank user ID without accessing Firestore.
  @Test
  fun createProfile_returnsInvalidUserIdFailure_whenUserIdIsBlank() = runTest {
    val result = repository.createProfile("")

    assertProfileError(result, ProfileError.INVALID_USER_ID)
    verify(collection, never()).document(any())
  }

  // Rejects whitespace-only user IDs without accessing Firestore.
  @Test
  fun createProfile_returnsInvalidUserIdFailure_whenUserIdContainsOnlyWhitespace() = runTest {
    listOf(" ", "\t", "\n", "\r\n", " \t\n ").forEach { userId ->
      val result = repository.createProfile(userId)

      assertProfileError(result, ProfileError.INVALID_USER_ID)
    }
    verify(collection, never()).document(any())
  }

  // Maps an unexpected synchronous transaction failure to the unknown profile error.
  @Test
  fun createProfile_returnsUnknownFailure_whenTransactionFailsUnexpectedly() = runTest {
    val exception = IllegalStateException("Transaction failed")
    `when`(firestore.runTransaction<UserProfile>(any())).thenThrow(exception)

    val result = repository.createProfile("user123")

    assertProfileError(result, ProfileError.UNKNOWN)
    assertEquals(exception, result.exceptionOrNull()?.cause)
  }

  // Maps a failed Firestore transaction task to the Firestore profile error.
  @Test
  fun createProfile_returnsFirestoreFailure_whenTransactionTaskFails() = runTest {
    val exception = firestoreException("Transaction failed")
    `when`(firestore.runTransaction<UserProfile>(any())).thenReturn(Tasks.forException(exception))

    val result = repository.createProfile("user123")

    assertProfileError(result, ProfileError.FIRESTORE_FAILURE)
    assertEquals(exception, result.exceptionOrNull()?.cause)
  }

  // Reports invalid profile data without overwriting an existing malformed document.
  @Test
  fun createProfile_returnsInvalidProfileData_whenExistingDocumentCannotBeDeserialized() = runTest {
    val snapshot = mock(DocumentSnapshot::class.java)
    val transaction = mock(Transaction::class.java)
    `when`(snapshot.exists()).thenReturn(true)
    `when`(snapshot.toObject(UserProfile::class.java)).thenReturn(null)
    `when`(transaction.get(document)).thenReturn(snapshot)
    stubTransaction(transaction)

    val result = repository.createProfile("user123")

    assertProfileError(result, ProfileError.INVALID_PROFILE_DATA)
    verify(transaction, never()).set(any(), any())
  }

  // Reports invalid profile data when existing-document conversion throws in the transaction.
  @Test
  fun createProfile_returnsInvalidProfileData_whenExistingDocumentDeserializationThrows() =
      runTest {
        val snapshot = mock(DocumentSnapshot::class.java)
        val transaction = mock(Transaction::class.java)
        val exception = IllegalStateException("Malformed profile")
        `when`(snapshot.exists()).thenReturn(true)
        `when`(snapshot.toObject(UserProfile::class.java)).thenThrow(exception)
        `when`(transaction.get(document)).thenReturn(snapshot)
        stubTransaction(transaction)

        val result = repository.createProfile("user123")

        assertProfileError(result, ProfileError.INVALID_PROFILE_DATA)
        assertEquals(exception, result.exceptionOrNull()?.cause)
        verify(transaction, never()).set(any(), any())
      }

  // Rejects an existing profile whose UID does not match the requested document ID.
  @Test
  fun createProfile_returnsInvalidProfileData_whenExistingStoredUidDoesNotMatchRequestedId() =
      runTest {
        val snapshot = mock(DocumentSnapshot::class.java)
        val transaction = mock(Transaction::class.java)
        `when`(snapshot.exists()).thenReturn(true)
        `when`(snapshot.toObject(UserProfile::class.java)).thenReturn(UserProfile("other-user"))
        `when`(transaction.get(document)).thenReturn(snapshot)
        stubTransaction(transaction)

        val result = repository.createProfile("user123")

        assertProfileError(result, ProfileError.INVALID_PROFILE_DATA)
        verify(transaction, never()).set(any(), any())
      }

  // Propagates cancellation thrown synchronously by the Firestore transaction call.
  @Test
  fun createProfile_propagatesSynchronousCancellationFromFirestoreCall() = runTest {
    val cancellation = CancellationException("Cancelled")
    `when`(firestore.runTransaction<UserProfile>(any())).thenThrow(cancellation)

    try {
      repository.createProfile("user123")
      fail("Expected cancellation to propagate")
    } catch (actual: CancellationException) {
      assertSame(cancellation, actual)
    }
  }

  // Propagates coroutine cancellation while profile creation is pending.
  @Test
  fun createProfile_propagatesCancellation_whileTransactionIsPending() = runTest {
    val task = TaskCompletionSource<UserProfile>()
    `when`(firestore.runTransaction<UserProfile>(any())).thenReturn(task.task)

    val request = async { repository.createProfile("user123") }
    runCurrent()
    request.cancelAndJoin()

    assertTrue(request.isCancelled)
  }

  // Propagates cancellation when a failed transaction task wraps it as the cause.
  @Test
  fun createProfile_propagatesWrappedCancellationFromFailedTask() = runTest {
    val cancellation = CancellationException("Cancelled")
    val wrapped = IllegalStateException("Firestore wrapper", cancellation)
    `when`(firestore.runTransaction<UserProfile>(any())).thenReturn(Tasks.forException(wrapped))

    val request = async { repository.createProfile("user123") }

    request.cancelAndJoin()
    assertTrue(request.isCancelled)
  }

  private fun stubTransaction(transaction: Transaction) {
    `when`(firestore.runTransaction<UserProfile>(any())).thenAnswer { invocation ->
      val function = invocation.getArgument<Transaction.Function<UserProfile>>(0)
      Tasks.forResult(function.apply(transaction))
    }
  }

  private fun assertProfileError(result: Result<*>, expected: ProfileError) {
    assertTrue(result.isFailure)
    val exception = result.exceptionOrNull()
    assertTrue(exception is ProfileException)
    assertEquals(expected, (exception as ProfileException).error)
  }

  private fun firestoreException(message: String) =
      FirebaseFirestoreException(
          message,
          FirebaseFirestoreException.Code.PERMISSION_DENIED,
      )
}
