// Co-authored-by: Gemini AI Agent
package com.android.sample.model.preferences

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.verify

class PreferencesRepositoryFirestoreTest {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var collection: CollectionReference
  private lateinit var documentReference: DocumentReference
  private lateinit var repository: PreferencesRepositoryFirestore

  @Before
  fun setUp() {
    firestore = mock(FirebaseFirestore::class.java)
    collection = mock(CollectionReference::class.java)
    documentReference = mock(DocumentReference::class.java)

    `when`(firestore.collection("preferences")).thenReturn(collection)
    `when`(collection.document(any())).thenReturn(documentReference)

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
  fun getPreferences_returnsFailure_onFirestoreError() = runTest {
    val exception = RuntimeException("Firestore error")
    `when`(documentReference.get()).thenReturn(Tasks.forException(exception))

    val result = repository.getPreferences("user123")

    assertTrue(result.isFailure)
    assertEquals("Firestore error", result.exceptionOrNull()?.message)
  }

  @Test
  fun getPreferences_returnsFailure_whenUserIdIsBlank() = runTest {
    val result = repository.getPreferences("   ")

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals("User ID must not be blank", result.exceptionOrNull()?.message)
  }

  @Test
  fun savePreferences_succeeds_whenUserIdIsNotBlank() = runTest {
    val prefs = UserPreferences(userId = "user123", sessionLengthMinutes = 45)
    `when`(documentReference.set(prefs)).thenReturn(Tasks.forResult(null))

    val result = repository.savePreferences(prefs)

    assertTrue(result.isSuccess)
    verify(collection).document("user123")
    verify(documentReference).set(prefs)
  }

  @Test
  fun savePreferences_returnsFailure_whenUserIdIsBlank() = runTest {
    val prefs = UserPreferences(userId = "   ")

    val result = repository.savePreferences(prefs)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    assertEquals("User ID must not be blank", result.exceptionOrNull()?.message)
  }

  @Test
  fun savePreferences_returnsFailure_onFirestoreError() = runTest {
    val prefs = UserPreferences(userId = "user123")
    val exception = RuntimeException("Write failed")
    `when`(documentReference.set(prefs)).thenReturn(Tasks.forException(exception))

    val result = repository.savePreferences(prefs)

    assertTrue(result.isFailure)
    assertEquals("Write failed", result.exceptionOrNull()?.message)
  }
}
